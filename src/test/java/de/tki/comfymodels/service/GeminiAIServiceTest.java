package de.tki.comfymodels.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
public class GeminiAIServiceTest {

    private ConfigServiceStub configService;
    private GeminiAIService geminiService;
    private WireMockRuntimeInfo wmRuntimeInfo;

    private static class ConfigServiceStub extends ConfigService {
        private String apiKey = "mock-key";
        public ConfigServiceStub() { super(null, null); }
        @Override public String getGeminiApiKey() { return apiKey; }
        public void setGeminiApiKey(String key) { this.apiKey = key; }
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        this.wmRuntimeInfo = wmRuntimeInfo;
        this.configService = new ConfigServiceStub();
        
        this.geminiService = new GeminiAIService() {
            @Override
            protected String getApiBaseUrl() {
                return wmRuntimeInfo.getHttpBaseUrl();
            }
        };
        ReflectionTestUtils.setField(geminiService, "configService", configService);
    }


    @Test
    void testOptimizePrompt_Success() throws Exception {
        // GIVEN
        String rawPrompt = "cybernetic tiger";
        String expectedOptimized = "A majestic cybernetic tiger with neon-lit circuitry glowing in a cyberpunk laboratory.";

        String jsonResponse = new JSONObject()
                .put("candidates", new org.json.JSONArray().put(
                        new JSONObject().put("content", new JSONObject().put("parts", new org.json.JSONArray().put(
                                new JSONObject().put("text", expectedOptimized)
                        )))
                )).toString();

        stubFor(post(urlEqualTo("/v1beta/models/gemini-1.5-flash:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        // WHEN
        String result = geminiService.optimizePrompt(rawPrompt);

        // THEN
        assertEquals(expectedOptimized, result);

        // Verify request payload contains rawPrompt
        verify(postRequestedFor(urlEqualTo("/v1beta/models/gemini-1.5-flash:generateContent?key=mock-key"))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", containing(rawPrompt)))
        );
    }

    @Test
    void testOptimizePrompt_ApiError() {
        // GIVEN
        stubFor(post(urlEqualTo("/v1beta/models/gemini-1.5-flash:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Invalid Request")));

        // WHEN & THEN
        Exception exception = assertThrows(IOException.class, () -> {
            geminiService.optimizePrompt("test");
        });
        assertTrue(exception.getMessage().contains("Gemini API Error (status 400)"));
    }

    @Test
    void testOptimizePrompt_MissingApiKey() {
        // GIVEN
        configService.setGeminiApiKey("");

        // WHEN & THEN
        Exception exception = assertThrows(IOException.class, () -> {
            geminiService.optimizePrompt("test");
        });
        assertEquals("Gemini API key is not configured.", exception.getMessage());
    }
}
