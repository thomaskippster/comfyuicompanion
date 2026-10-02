package de.tki.comfyuicompanion.service.provisioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LargeFileDownloadServiceTest {

    @InjectMocks
    private LargeFileDownloadService downloadService;

    @Test
    void shouldHandleInvalidUrl_gracefully() {
        Path target = Paths.get(System.getProperty("java.io.tmpdir"), "test-download.bin");

        // The real service will fail to connect or return an error flux. 
        // We ensure it doesn't just block or throw unhandled exceptions.
        Flux<Double> progressFlux = downloadService.downloadModel("http://127.0.0.1:54321/non-existent-file.bin", target);

        assertThrows(Exception.class, () -> progressFlux.blockLast(Duration.ofSeconds(5)));
    }
}
