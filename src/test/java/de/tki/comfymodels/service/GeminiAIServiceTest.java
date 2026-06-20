package de.tki.comfymodels.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
public class GeminiAIServiceTest {

    private ConfigServiceStub configService;
    private LocalGemmaServiceStub localGemmaService;
    private GeminiAIService geminiService;
    private WireMockRuntimeInfo wmRuntimeInfo;

    private static class ConfigServiceStub extends ConfigService {
        private String apiKey = "mock-key";
        public ConfigServiceStub() { super(null, null); }
        @Override public String getGeminiApiKey() { return apiKey; }
        public void setGeminiApiKey(String key) { this.apiKey = key; }
    }

    private static class LocalGemmaServiceStub extends LocalGemmaService {
        private boolean downloaded = true;
        private String completionResponse = "mock-completion";

        public LocalGemmaServiceStub() { super(null); }

        @Override
        public boolean isModelDownloaded() {
            return downloaded;
        }

        @Override
        public String generateCompletion(String systemInstruction, String userPrompt, float temperature, int maxTokens) throws IOException {
            return completionResponse;
        }
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        this.wmRuntimeInfo = wmRuntimeInfo;
        this.configService = new ConfigServiceStub();
        this.localGemmaService = new LocalGemmaServiceStub();
        
        this.geminiService = new GeminiAIService() {
            @Override
            protected String getApiBaseUrl() {
                return wmRuntimeInfo.getHttpBaseUrl();
            }
        };
        ReflectionTestUtils.setField(geminiService, "configService", configService);
        ReflectionTestUtils.setField(geminiService, "localGemmaService", localGemmaService);
    }

    @Test
    void testOptimizePrompt_Success() throws Exception {
        // GIVEN
        String rawPrompt = "cybernetic tiger";
        String expectedOptimized = "A majestic cybernetic tiger with neon-lit circuitry glowing in a cyberpunk laboratory.";
        
        com.github.tomakehurst.wiremock.client.WireMock.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.post(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/v1beta/models/gemini-1.5-flash:generateContent")
            ).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + expectedOptimized + "\"}]}}]}")
            )
        );

        // WHEN
        String result = geminiService.optimizePrompt(rawPrompt);

        // THEN
        assertEquals(expectedOptimized, result);
    }

    @Test
    void testOptimizePrompt_LocalGemmaNotDownloaded() {
        // GIVEN
        configService.setGeminiApiKey("");
        localGemmaService.downloaded = false;

        // WHEN & THEN
        Exception exception = assertThrows(IOException.class, () -> {
            geminiService.optimizePrompt("test");
        });
        assertEquals("Neither Gemini API key is configured nor local Gemma model is downloaded.", exception.getMessage());
    }

    @Test
    void testOptimizePrompt_WorksWithoutApiKey() throws Exception {
        // GIVEN
        configService.setGeminiApiKey("");
        localGemmaService.downloaded = true;
        localGemmaService.completionResponse = "optimized-without-key";

        // WHEN
        String result = geminiService.optimizePrompt("test");

        // THEN
        assertEquals("optimized-without-key", result);
    }
}
