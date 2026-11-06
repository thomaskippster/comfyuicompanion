package de.tki.comfyuicompanion.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioSettingsTest {

    @Test
    void testDefaultValues() {
        AudioSettings settings = AudioSettings.DEFAULT;
        assertNull(settings.narrationAudioPath());
        assertNull(settings.sfxAudioPath());
        assertEquals("", settings.sfxPrompt());
        assertEquals(1.0, settings.voiceVolume());
        assertEquals(0.5, settings.sfxVolume());
    }

    @Test
    void testImmutabilityAndWitherMethods() {
        AudioSettings s1 = AudioSettings.DEFAULT;
        AudioSettings s2 = s1.withNarrationAudioPath("C:/voice.wav");
        AudioSettings s3 = s2.withSfxAudioPath("C:/rocket_roar.wav");
        AudioSettings s4 = s3.withSfxPrompt("Deafening rocket ignition roar");
        AudioSettings s5 = s4.withVolumes(1.2, 0.7);

        assertNull(s1.narrationAudioPath());
        assertEquals("C:/voice.wav", s2.narrationAudioPath());
        assertNull(s2.sfxAudioPath());

        assertEquals("C:/voice.wav", s5.narrationAudioPath());
        assertEquals("C:/rocket_roar.wav", s5.sfxAudioPath());
        assertEquals("Deafening rocket ignition roar", s5.sfxPrompt());
        assertEquals(1.2, s5.voiceVolume());
        assertEquals(0.7, s5.sfxVolume());
    }

    @Test
    void testSceneAudioSettingsDelegation() {
        Scene scene = new Scene("S1", "Rocket launch");
        assertEquals("", scene.getSfxPrompt());
        assertEquals(1.0, scene.getVoiceVolume());
        assertEquals(0.5, scene.getSfxVolume());

        scene.setAudioPath("output/narration/narration_1.wav");
        assertEquals("output/narration/narration_1.wav", scene.getAudioPath());
        assertEquals("output/narration/narration_1.wav", scene.getAudioSettings().narrationAudioPath());

        scene.setSfxPrompt("Deafening engine blast");
        assertEquals("Deafening engine blast", scene.getSfxPrompt());

        scene.setSfxAudioPath("output/sfx/sfx_1.wav");
        assertEquals("output/sfx/sfx_1.wav", scene.getSfxAudioPath());

        scene.setVoiceVolume(0.9);
        scene.setSfxVolume(0.4);
        assertEquals(0.9, scene.getVoiceVolume());
        assertEquals(0.4, scene.getSfxVolume());
    }
}
