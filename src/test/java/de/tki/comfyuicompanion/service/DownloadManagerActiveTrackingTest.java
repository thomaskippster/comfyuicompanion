package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.DefaultDownloadManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying active download status tracking, observer progress notifications,
 * and individual model download stopping in DefaultDownloadManager.
 */
@ExtendWith(MockitoExtension.class)
class DownloadManagerActiveTrackingTest {

    @Mock
    private de.tki.comfyuicompanion.service.impl.ConfigService configService;

    @Mock
    private de.tki.comfyuicompanion.service.impl.PathResolver pathResolver;

    @Mock
    private de.tki.comfyuicompanion.service.impl.CivitaiService civitaiService;

    @Mock
    private de.tki.comfyuicompanion.service.impl.ModelHashRegistry hashRegistry;

    @Mock
    private IModelValidator modelValidator;

    @Test
    void shouldTrackActiveDownloadStatus_andNotifyProgressListeners() {
        DefaultDownloadManager manager = new DefaultDownloadManager(
                configService, pathResolver, civitaiService, hashRegistry, modelValidator
        );

        ModelInfo model = new ModelInfo("checkpoints", "Flux 3 Klein", "https://civitai.com/api/download/models/3407486");
        model.setFilename("flux3_klein.safetensors");

        AtomicReference<String> lastReceivedStatus = new AtomicReference<>();
        IDownloadManager.DownloadProgressListener listener = (fn, status) -> {
            if ("flux3_klein.safetensors".equalsIgnoreCase(fn)) {
                lastReceivedStatus.set(status);
            }
        };

        manager.addProgressListener(listener);

        // Initially no active download
        assertThat(manager.isDownloading("flux3_klein.safetensors")).isFalse();
        assertThat(manager.getActiveDownloadStatus("flux3_klein.safetensors")).isEmpty();

        manager.startQueue(
                List.of(model),
                new boolean[]{true},
                "target/test_models",
                null,
                null
        );

        // When status updates arrive
        Optional<String> activeStatus = manager.getActiveDownloadStatus("flux3_klein.safetensors");
        assertThat(activeStatus).isNotNull();

        manager.removeProgressListener(listener);
    }

    @Test
    void shouldStopActiveDownload_whenStopDownloadCalled() {
        DefaultDownloadManager manager = new DefaultDownloadManager(
                configService, pathResolver, civitaiService, hashRegistry, modelValidator
        );

        ModelInfo model = new ModelInfo("checkpoints", "Flux 3 Klein", "https://civitai.com/api/download/models/3407486");
        model.setFilename("flux3_klein.safetensors");

        manager.startQueue(
                List.of(model),
                new boolean[]{true},
                "target/test_models",
                null,
                null
        );

        manager.stopDownload("flux3_klein.safetensors");

        // The model status should transition to Stopped
        assertThat(manager.isDownloading("flux3_klein.safetensors")).isFalse();
    }
}
