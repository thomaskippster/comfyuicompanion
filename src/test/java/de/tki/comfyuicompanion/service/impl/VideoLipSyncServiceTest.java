package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.pipeline.ComfyJobPollingService;
import de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient;
import de.tki.comfyuicompanion.service.pipeline.MediaTranscodingService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoLipSyncServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ComfyNodeInspectionClient inspectionClient;

    @Mock
    private ComfyJobPollingService jobPollingService;

    @Mock
    private MediaTranscodingService mediaTranscodingService;

    private VideoLipSyncService lipSyncService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configService.getComfyUIUrl()).thenReturn("http://127.0.0.1:8188");
        when(configService.getLipSyncEngine()).thenReturn("Auto");
        when(configService.getResolvedInputDetailDir()).thenReturn(tempDir.resolve("input").toString());
        when(configService.getResolvedOutputDir()).thenReturn(tempDir.resolve("output").toString());

        lipSyncService = new VideoLipSyncService(
                configService,
                inspectionClient,
                jobPollingService,
                mediaTranscodingService,
                null
        );
    }

    @Test
    void isLipSyncAvailable_shouldReturnTrue_whenLivePortraitNodePresent() {
        when(inspectionClient.isNodeClassAvailable("http://127.0.0.1:8188", "LivePortraitCropper")).thenReturn(true);

        boolean available = lipSyncService.isLipSyncAvailable("http://127.0.0.1:8188");

        assertThat(available).isTrue();
    }

    @Test
    void isLipSyncAvailable_shouldReturnFalse_whenNoNodesPresent() {
        when(inspectionClient.isNodeClassAvailable(anyString(), anyString())).thenReturn(false);

        boolean available = lipSyncService.isLipSyncAvailable("http://127.0.0.1:8188");

        assertThat(available).isFalse();
    }

    @Test
    void getAvailableLipSyncEngines_shouldDetectEngines() {
        when(inspectionClient.isNodeClassAvailable("http://127.0.0.1:8188", "LivePortraitCropper")).thenReturn(true);
        when(inspectionClient.isNodeClassAvailable("http://127.0.0.1:8188", "SadTalker")).thenReturn(false);
        when(inspectionClient.isNodeClassAvailable("http://127.0.0.1:8188", "Wav2Lip")).thenReturn(true);

        List<String> engines = lipSyncService.getAvailableLipSyncEngines("http://127.0.0.1:8188");

        assertThat(engines).contains("Auto", "LivePortrait", "Wav2Lip");
    }

    @Test
    void buildLipSyncPrompt_shouldBuildLivePortraitNodes() {
        Scene scene = new Scene("1", "A talking avatar");

        JSONObject prompt = lipSyncService.buildLipSyncPrompt("LivePortrait", "speaker.png", "narration.wav", scene);

        assertThat(prompt.has("1")).isTrue();
        assertThat(prompt.getJSONObject("1").getString("class_type")).isEqualTo("LoadImage");
        assertThat(prompt.has("2")).isTrue();
        assertThat(prompt.getJSONObject("2").getString("class_type")).isEqualTo("LoadAudio");
        assertThat(prompt.has("3")).isTrue();
        assertThat(prompt.getJSONObject("3").getString("class_type")).isEqualTo("LivePortraitLoadCropperModel");
        assertThat(prompt.has("5")).isTrue();
        assertThat(prompt.getJSONObject("5").getString("class_type")).isEqualTo("LivePortraitAudioDriven");
    }

    @Test
    void buildLipSyncPrompt_shouldBuildSadTalkerNodes() {
        Scene scene = new Scene("2", "SadTalker avatar");

        JSONObject prompt = lipSyncService.buildLipSyncPrompt("SadTalker", "portrait.png", "audio.wav", scene);

        assertThat(prompt.has("3")).isTrue();
        assertThat(prompt.getJSONObject("3").getString("class_type")).isEqualTo("SadTalkerLoader");
        assertThat(prompt.has("4")).isTrue();
        assertThat(prompt.getJSONObject("4").getString("class_type")).isEqualTo("SadTalker");
    }

    @Test
    void buildLipSyncPrompt_shouldBuildWav2LipNodes() {
        Scene scene = new Scene("3", "Wav2Lip video");

        JSONObject prompt = lipSyncService.buildLipSyncPrompt("Wav2Lip", "clip.mp4", "audio.wav", scene);

        assertThat(prompt.has("3")).isTrue();
        assertThat(prompt.getJSONObject("3").getString("class_type")).isEqualTo("Wav2LipPreprocessor");
        assertThat(prompt.has("4")).isTrue();
        assertThat(prompt.getJSONObject("4").getString("class_type")).isEqualTo("Wav2Lip");
    }

    @Test
    void applyLipSync_shouldThrowException_whenMediaFileMissing() {
        File missingMedia = new File(tempDir.toFile(), "nonexistent.png");
        File audio = tempDir.resolve("audio.wav").toFile();
        File output = tempDir.resolve("out.mp4").toFile();

        assertThatThrownBy(() -> lipSyncService.applyLipSync(missingMedia, audio, output, new Scene("1", "Test")).join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyLipSync_shouldThrowException_whenAudioFileMissing() throws IOException {
        File media = Files.createFile(tempDir.resolve("portrait.png")).toFile();
        File missingAudio = new File(tempDir.toFile(), "nonexistent.wav");
        File output = tempDir.resolve("out.mp4").toFile();

        assertThatThrownBy(() -> lipSyncService.applyLipSync(media, missingAudio, output, new Scene("1", "Test")).join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyLipSync_shouldExecuteComfyUiJob_whenNodeIsAvailable() throws Exception {
        File media = Files.createFile(tempDir.resolve("face.png")).toFile();
        File audio = Files.createFile(tempDir.resolve("speech.wav")).toFile();
        File output = tempDir.resolve("synced.mp4").toFile();
        Scene scene = new Scene("10", "Narration scene");

        when(inspectionClient.isNodeClassAvailable("http://127.0.0.1:8188", "LivePortraitCropper")).thenReturn(true);
        when(jobPollingService.submitPrompt(anyString(), any(JSONObject.class))).thenReturn("prompt-12345");

        File generatedComfyFile = Files.createFile(tempDir.resolve("comfy_lipsync.mp4")).toFile();
        Files.writeString(generatedComfyFile.toPath(), "video-payload");

        ComfyJobPollingService.ComfyOutputRef ref = new ComfyJobPollingService.ComfyOutputRef("comfy_lipsync.mp4", "", "output");
        when(jobPollingService.pollHistoryForOutput(eq("http://127.0.0.1:8188"), eq("prompt-12345"))).thenReturn(ref);
        when(jobPollingService.resolveOutputFile(anyString(), eq(ref), eq("10"))).thenReturn(generatedComfyFile);

        CompletableFuture<File> future = lipSyncService.applyLipSync(media, audio, output, scene, "LivePortrait");
        File result = future.join();

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(Files.readString(result.toPath())).isEqualTo("video-payload");
    }

    @Test
    void applyLipSync_shouldFallbackToTranscoding_whenComfyUiNodeMissing() throws Exception {
        File media = Files.createFile(tempDir.resolve("fallback_face.png")).toFile();
        File audio = Files.createFile(tempDir.resolve("fallback_speech.wav")).toFile();
        File output = tempDir.resolve("fallback_synced.mp4").toFile();
        Scene scene = new Scene("20", "Fallback test");

        when(inspectionClient.isNodeClassAvailable(anyString(), anyString())).thenReturn(false);

        File transcodedVideo = Files.createFile(tempDir.resolve("transcoded.mp4")).toFile();
        when(mediaTranscodingService.convertImageToVideo(eq(media), eq(audio), anyFloat(), anyFloat(), eq(output), anyInt(), anyInt()))
                .thenReturn(transcodedVideo);

        CompletableFuture<File> future = lipSyncService.applyLipSync(media, audio, output, scene);
        File result = future.join();

        assertThat(result).isEqualTo(transcodedVideo);
        verify(mediaTranscodingService).convertImageToVideo(eq(media), eq(audio), anyFloat(), anyFloat(), eq(output), anyInt(), anyInt());
    }
}
