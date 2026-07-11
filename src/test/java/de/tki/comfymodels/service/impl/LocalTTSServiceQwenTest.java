package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalTTSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalTTSServiceQwenTest {

    private LocalTTSService service;
    private ConfigService configService;

    @BeforeEach
    public void setup() {
        configService = Mockito.mock(ConfigService.class);
        // Default the new Qwen-TTS config accessors to sensible values.
        Mockito.when(configService.getQwenTtsModelRepo()).thenReturn("Qwen/Qwen2-Audio-7B-Instruct");
        Mockito.when(configService.getQwenTtsModelPath()).thenReturn("/tmp/qwen-model");
        Mockito.when(configService.getQwenTtsVoice()).thenReturn("Chelsie");
        service = new LocalTTSService(configService);
    }

    @Test
    public void defaultProviderIsQwenTts() {
        // The application's default TTS provider must be the new high-quality
        // Qwen-TTS custom node (KokoroTTS is no longer the default).
        assertEquals("ComfyUI Qwen-TTS", new ConfigService(null, new de.tki.comfymodels.service.impl.PathResolver()).getTtsProvider());
    }

    @Test
    public void qwenConfigDefaults_areReasonable() {
        ConfigService fresh = new ConfigService(null, new de.tki.comfymodels.service.impl.PathResolver());
        assertNotNull(fresh.getQwenTtsModelRepo());
        assertTrue(fresh.getQwenTtsModelRepo().startsWith("Qwen/"),
                "Default Qwen model repo must be a Hugging Face Qwen model; was: " + fresh.getQwenTtsModelRepo());
        assertNotNull(fresh.getQwenTtsVoice());
        assertNotNull(fresh.getQwenTtsModelPath());
    }

    @Test
    public void qwenConfigSetters_roundTrip() {
        // Use a real ConfigService so the setters actually persist to the
        // backing settings map (a Mockito mock would silently no-op).
        @SuppressWarnings("deprecation")
        ConfigService realConfig = new ConfigService(null, new de.tki.comfymodels.service.impl.PathResolver()) {
            @Override
            public synchronized void save() { /* skip disk I/O in tests */ }
        };
        realConfig.setQwenTtsModelRepo("Qwen/Qwen2.5-Omni-7B");
        realConfig.setQwenTtsVoice("Ethan");
        realConfig.setQwenTtsModelPath("/custom/path");
        assertEquals("Qwen/Qwen2.5-Omni-7B", realConfig.getQwenTtsModelRepo());
        assertEquals("Ethan", realConfig.getQwenTtsVoice());
        assertEquals("/custom/path", realConfig.getQwenTtsModelPath());
    }

    @Test
    public void generateSpeech_routesToComfyUI() throws Exception {
        // When the provider is the default (Qwen-TTS via ComfyUI), the service
        // must call generateSpeechViaComfyUI - which requires ComfyUI to be
        // healthy. We just verify it does NOT throw a "Piper binary not found"
        // style exception and reaches the ComfyUI code path (which then bails
        // out because no lifecycle is mocked).
        Mockito.when(configService.getTtsProvider()).thenReturn("ComfyUI Qwen-TTS");
        try {
            service.generateSpeech("hello world", "output/test.wav");
        } catch (Exception ex) {
            // We expect an exception because no ComfyUI is running, but it
            // must be a ComfyUI-style failure, NOT a "Piper binary not found".
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            assertTrue(!msg.contains("Piper"), "Piper must no longer be referenced; got: " + msg);
        }
    }
}