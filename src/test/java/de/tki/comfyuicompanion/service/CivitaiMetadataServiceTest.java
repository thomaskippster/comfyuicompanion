package de.tki.comfyuicompanion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.impl.CivitaiMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying dynamic Civitai metadata discovery, caching, and fallback behavior.
 */
@ExtendWith(MockitoExtension.class)
class CivitaiMetadataServiceTest {

    @Mock
    private IConfigService configService;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CivitaiMetadataService metadataService;

    @BeforeEach
    void setUp() {
        metadataService = new CivitaiMetadataService(configService, objectMapper, httpClient);
    }

    @Test
    void shouldDiscoverActiveBaseModelsDynamically_whenCivitaiReturnsEnums() {
        String enumsJson = """
                {
                    "ActiveBaseModel": ["Flux.1 D", "Flux.1 S", "Flux.3 Klein", "SDXL 1.0", "Wan Video 14B t2v"],
                    "ModelType": ["Checkpoint", "LORA", "VAE", "Controlnet"]
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(enumsJson);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));

        List<String> baseModels = metadataService.getActiveBaseModels().join();

        assertThat(baseModels).isNotNull();
        assertThat(baseModels).contains("Flux.1 D", "Flux.3 Klein", "SDXL 1.0", "Wan Video 14B t2v");

        // Verify caching: second call should not re-query HTTP client
        List<String> cachedBaseModels = metadataService.getActiveBaseModels().join();
        assertThat(cachedBaseModels).isEqualTo(baseModels);
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldDiscoverModelTypesDynamically_whenCivitaiReturnsEnums() {
        String enumsJson = """
                {
                    "ActiveBaseModel": ["Flux.1 D"],
                    "ModelType": ["Checkpoint", "LORA", "Controlnet", "Upscaler"]
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(enumsJson);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(httpResponse));

        List<String> types = metadataService.getModelTypes().join();

        assertThat(types).contains("Checkpoint", "LORA", "Controlnet", "Upscaler");
    }

    @Test
    void shouldFallbackToCuratedDefaults_whenCivitaiEnumsEndpointFails() {
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IOException("Connection timed out")));

        List<String> baseModels = metadataService.getActiveBaseModels().join();

        assertThat(baseModels).isNotEmpty();
        assertThat(baseModels).contains("Flux.1 D", "SDXL 1.0", "SD 1.5");
    }
}
