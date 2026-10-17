package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoLipSyncService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneMediaPostProcessorTest {

    @Mock
    private ConfigService configService;

    @Mock
    private SceneSimulationService simulationService;

    @Mock
    private MediaTranscodingService mediaTranscodingService;

    @Mock
    private IVideoLipSyncService lipSyncService;

    private SceneMediaPostProcessor postProcessor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configService.getFfmpegPath()).thenReturn("ffmpeg");
        postProcessor = new SceneMediaPostProcessor(
                configService,
                simulationService,
                mediaTranscodingService,
                null,
                lipSyncService
        );
    }

    @Test
    void postProcessMedia_shouldInvokeLipSync_whenLipSyncEnabledAndAudioPresent() throws Exception {
        File rawImage = Files.createFile(tempDir.resolve("scene_raw.png")).toFile();
        File audio = Files.createFile(tempDir.resolve("scene_audio.wav")).toFile();
        File finalVideo = tempDir.resolve("scene_final.mp4").toFile();

        Scene scene = new Scene("1", "A talking scene");
        scene.setAudioPath(audio.getAbsolutePath());

        when(configService.isAudioLipSyncEnabled()).thenReturn(true);
        when(configService.getLipSyncEngine()).thenReturn("LivePortrait");

        File generatedSyncedVideo = Files.createFile(tempDir.resolve("lip_synced.mp4")).toFile();
        when(lipSyncService.applyLipSync(eq(rawImage), eq(audio), eq(finalVideo), eq(scene)))
                .thenReturn(CompletableFuture.completedFuture(generatedSyncedVideo));

        postProcessor.postProcessMedia(rawImage, scene, finalVideo, 5.0f, 24.0f, null);

        verify(lipSyncService).applyLipSync(eq(rawImage), eq(audio), eq(finalVideo), eq(scene));
        verify(mediaTranscodingService, never()).convertImageToVideo(any(), any(), anyFloat(), anyFloat(), any(), anyInt(), anyInt());
    }

    @Test
    void postProcessMedia_shouldFallbackToTranscoding_whenLipSyncDisabled() throws Exception {
        File rawImage = Files.createFile(tempDir.resolve("raw2.png")).toFile();
        File audio = Files.createFile(tempDir.resolve("audio2.wav")).toFile();
        File finalVideo = tempDir.resolve("final2.mp4").toFile();

        Scene scene = new Scene("2", "Standard image scene");
        scene.setAudioPath(audio.getAbsolutePath());

        when(configService.isAudioLipSyncEnabled()).thenReturn(false);

        postProcessor.postProcessMedia(rawImage, scene, finalVideo, 5.0f, 24.0f, null);

        verify(lipSyncService, never()).applyLipSync(any(), any(), any(), any());
        verify(mediaTranscodingService).convertImageToVideo(eq(rawImage), eq(audio), anyFloat(), anyFloat(), eq(finalVideo), anyInt(), anyInt());
    }
}
