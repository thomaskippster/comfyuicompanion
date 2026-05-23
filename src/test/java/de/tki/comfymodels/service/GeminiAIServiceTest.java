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
    void testGenerateImageNanoBanana_SuccessTextOnly() throws Exception {
        // GIVEN
        String prompt = "A cute banana running in space";
        int seed = 42;
        byte[] expectedImageBytes = new byte[]{1, 2, 3, 4, 5};
        String base64Image = Base64.getEncoder().encodeToString(expectedImageBytes);

        String jsonResponse = new JSONObject()
                .put("candidates", new org.json.JSONArray().put(
                        new JSONObject().put("content", new JSONObject().put("parts", new org.json.JSONArray().put(
                                new JSONObject().put("inlineData", new JSONObject()
                                        .put("mimeType", "image/png")
                                        .put("data", base64Image)
                                )
                        )))
                )).toString();

        stubFor(post(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        // WHEN
        byte[] result = geminiService.generateImageNanoBanana(prompt, null, null, seed);

        // THEN
        assertArrayEquals(expectedImageBytes, result);

        // Verify request payload
        verify(postRequestedFor(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", equalTo(prompt)))
                .withRequestBody(matchingJsonPath("$.generationConfig.responseModalities[0]", equalTo("IMAGE")))
                .withRequestBody(matchingJsonPath("$.generationConfig.seed", equalTo("42")))
        );
    }

    @Test
    void testGenerateImageNanoBanana_SuccessWithImage() throws Exception {
        // GIVEN
        String prompt = "Transform this cat to a banana";
        byte[] inputImage = new byte[]{9, 8, 7};
        String inputMime = "image/jpeg";
        int seed = 100;
        
        byte[] expectedImageBytes = new byte[]{10, 11, 12};
        String base64Image = Base64.getEncoder().encodeToString(expectedImageBytes);

        String jsonResponse = new JSONObject()
                .put("candidates", new org.json.JSONArray().put(
                        new JSONObject().put("content", new JSONObject().put("parts", new org.json.JSONArray().put(
                                new JSONObject().put("inlineData", new JSONObject()
                                        .put("mimeType", "image/png")
                                        .put("data", base64Image)
                                )
                        )))
                )).toString();

        stubFor(post(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        // WHEN
        byte[] result = geminiService.generateImageNanoBanana(prompt, inputImage, inputMime, seed);

        // THEN
        assertArrayEquals(expectedImageBytes, result);

        // Verify request payload
        verify(postRequestedFor(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", equalTo(prompt)))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[1].inlineData.mimeType", equalTo("image/jpeg")))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[1].inlineData.data", equalTo(Base64.getEncoder().encodeToString(inputImage))))
                .withRequestBody(matchingJsonPath("$.generationConfig.seed", equalTo("100")))
        );
    }

    @Test
    void testGenerateImageNanoBanana_ApiError() {
        // GIVEN
        stubFor(post(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Invalid Request")));

        // WHEN & THEN
        Exception exception = assertThrows(IOException.class, () -> {
            geminiService.generateImageNanoBanana("test", null, null, -1);
        });
        assertTrue(exception.getMessage().contains("Gemini API Error (status 400)"));
    }

    @Test
    void testGenerateImageNanoBanana_RateLimitError() {
        // GIVEN
        stubFor(post(urlEqualTo("/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=mock-key"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("Resource Exhausted")));

        // WHEN & THEN
        Exception exception = assertThrows(IOException.class, () -> {
            geminiService.generateImageNanoBanana("test", null, null, -1);
        });
        assertTrue(exception.getMessage().contains("Ratenbegrenzung überschritten"));
        assertTrue(exception.getMessage().contains("HTTP 429"));
    }

    @Test
    void testGenerateImageNanoBanana_MissingApiKey() {
        // GIVEN
        configService.setGeminiApiKey("");

        // WHEN & THEN
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            geminiService.generateImageNanoBanana("test", null, null, -1);
        });
        assertEquals("Gemini API Key is not set.", exception.getMessage());
    }
}
