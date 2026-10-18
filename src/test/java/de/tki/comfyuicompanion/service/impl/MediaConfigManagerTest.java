package de.tki.comfyuicompanion.service.impl;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MediaConfigManagerTest {

    private MediaConfigManager mediaConfigManager;
    private JSONObject settings;
    private AtomicBoolean saved;

    @BeforeEach
    void setUp() {
        mediaConfigManager = new MediaConfigManager();
        settings = new JSONObject();
        saved = new AtomicBoolean(false);
    }

    @Test
    void testAudioLipSyncDefaultsAndModifications() {
        assertThat(mediaConfigManager.isAudioLipSyncEnabled(settings)).isFalse();
        assertThat(mediaConfigManager.getLipSyncEngine(settings)).isEqualTo("Auto");

        mediaConfigManager.setAudioLipSyncEnabled(settings, true, () -> saved.set(true));
        assertThat(mediaConfigManager.isAudioLipSyncEnabled(settings)).isTrue();
        assertThat(saved.get()).isTrue();

        saved.set(false);
        mediaConfigManager.setLipSyncEngine(settings, "SadTalker", () -> saved.set(true));
        assertThat(mediaConfigManager.getLipSyncEngine(settings)).isEqualTo("SadTalker");
        assertThat(saved.get()).isTrue();
    }

    @Test
    void testLocalLlmModelAndVideoOptimization() {
        assertThat(mediaConfigManager.getLocalLlmModel(settings)).isEqualTo("gemma-3-4b");
        assertThat(mediaConfigManager.is16GbVramOptimizationEnabled(settings)).isTrue();
        assertThat(mediaConfigManager.isLastFrameChainingEnabled(settings)).isFalse();
        assertThat(mediaConfigManager.isTwoPhaseRenderingEnabled(settings)).isFalse();

        mediaConfigManager.setLocalLlmModel(settings, "qwen-2.5-7b", () -> saved.set(true));
        assertThat(mediaConfigManager.getLocalLlmModel(settings)).isEqualTo("qwen-2.5-7b");
        assertThat(saved.get()).isTrue();
    }
}
