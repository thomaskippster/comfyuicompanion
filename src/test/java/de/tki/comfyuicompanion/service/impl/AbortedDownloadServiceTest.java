package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.AbortedDownloadItem;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import de.tki.comfyuicompanion.service.SafePathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Enterprise unit tests verifying {@link AbortedDownloadService} scanning,
 * segment aggregation, deletion, and resumption capabilities.
 */
@ExtendWith(MockitoExtension.class)
class AbortedDownloadServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private PathResolver pathResolver;

    @Mock
    private IDownloadManager downloadManager;

    @Mock
    private ICivitaiHubService civitaiHubService;

    @Mock
    private CivitaiService civitaiService;

    @Mock
    private IHuggingFaceHubService huggingFaceHubService;

    @TempDir
    Path tempModelsDir;

    private AbortedDownloadService service;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getModelsPath()).thenReturn(tempModelsDir.toString());
        lenient().when(pathResolver.resolve(anyString())).thenAnswer(inv -> Path.of((String) inv.getArgument(0)));
        service = new AbortedDownloadService(
                configService,
                pathResolver,
                new SafePathValidator(),
                downloadManager,
                civitaiHubService,
                civitaiService,
                huggingFaceHubService
        );
    }

    @Test
    @DisplayName("Should scan model directories, aggregate segmented parts, and ignore completed files")
    void testScanAbortedDownloads() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Path loraDir = tempModelsDir.resolve("loras");
        Files.createDirectories(ckptDir);
        Files.createDirectories(loraDir);

        // 1. Single segment partial file (.cmfd)
        Files.write(ckptDir.resolve("flux1_dev.safetensors.cmfd"), new byte[10_000]);

        // 2. Multi-segment partial files (.part0, .part1)
        Files.write(ckptDir.resolve("pony_v6.safetensors.part0"), new byte[4_000]);
        Files.write(ckptDir.resolve("pony_v6.safetensors.part1"), new byte[6_000]);

        // 3. Temporary download file (.tmp)
        Files.write(loraDir.resolve("detail_slider.safetensors.tmp"), new byte[2_500]);

        // 4. Completed model file (must NOT be returned as aborted)
        Files.write(ckptDir.resolve("complete_model.safetensors"), new byte[50_000]);

        List<AbortedDownloadItem> items = service.scanAbortedDownloads(tempModelsDir.toString());

        assertThat(items).hasSize(3);

        AbortedDownloadItem fluxItem = items.stream()
                .filter(i -> "flux1_dev.safetensors".equals(i.targetFileName()))
                .findFirst().orElseThrow();
        assertThat(fluxItem.sizeBytes()).isEqualTo(10_000L);
        assertThat(fluxItem.folderName()).isEqualTo("checkpoints");

        AbortedDownloadItem ponyItem = items.stream()
                .filter(i -> "pony_v6.safetensors".equals(i.targetFileName()))
                .findFirst().orElseThrow();
        assertThat(ponyItem.sizeBytes()).isEqualTo(10_000L); // Aggregated 4_000 + 6_000
        assertThat(ponyItem.fileExtensionType()).contains("segments");

        AbortedDownloadItem sliderItem = items.stream()
                .filter(i -> "detail_slider.safetensors".equals(i.targetFileName()))
                .findFirst().orElseThrow();
        assertThat(sliderItem.sizeBytes()).isEqualTo(2_500L);
        assertThat(sliderItem.folderName()).isEqualTo("loras");
    }

    @Test
    @DisplayName("Should safely delete selected aborted download files and their associated segments")
    void testDeleteAbortedDownloads() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);

        Path part0 = ckptDir.resolve("pony_v6.safetensors.part0");
        Path part1 = ckptDir.resolve("pony_v6.safetensors.part1");
        Files.write(part0, new byte[4_000]);
        Files.write(part1, new byte[6_000]);

        List<AbortedDownloadItem> items = service.scanAbortedDownloads(tempModelsDir.toString());
        assertThat(items).hasSize(1);

        long freed = service.deleteAbortedDownloads(items);

        assertThat(freed).isEqualTo(10_000L);
        assertThat(Files.exists(part0)).isFalse();
        assertThat(Files.exists(part1)).isFalse();
    }

    @Test
    @DisplayName("Should detect if an aborted download exists for a target model file")
    void testHasAndFindAbortedDownload() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);

        Path cmfd = ckptDir.resolve("dreamshaper.safetensors.cmfd");
        Files.write(cmfd, new byte[15_000]);

        boolean exists = service.hasAbortedDownload(tempModelsDir.toString(), "checkpoints", "dreamshaper.safetensors");
        assertThat(exists).isTrue();

        Optional<AbortedDownloadItem> found = service.findAbortedDownload(tempModelsDir.toString(), "checkpoints", "dreamshaper.safetensors");
        assertThat(found).isPresent();
        assertThat(found.get().sizeBytes()).isEqualTo(15_000L);
        assertThat(found.get().targetFileName()).isEqualTo("dreamshaper.safetensors");

        boolean missing = service.hasAbortedDownload(tempModelsDir.toString(), "checkpoints", "non_existing.safetensors");
        assertThat(missing).isFalse();
    }

    @Test
    @DisplayName("Should resume download by submitting to download manager queue")
    void testResumeDownload() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);
        Path cmfd = ckptDir.resolve("sd_xl_base.safetensors.cmfd");
        Files.write(cmfd, new byte[5_000]);

        AbortedDownloadItem item = new AbortedDownloadItem(
                cmfd,
                "sd_xl_base.safetensors",
                "checkpoints",
                5_000L,
                java.time.Instant.now(),
                ".cmfd",
                "https://civitai.com/api/download/models/12345"
        );

        List<String> progressUpdates = new ArrayList<>();
        CompletableFuture<Boolean> future = service.resumeDownload(item, progressUpdates::add);

        verify(downloadManager, timeout(2000)).startQueue(any(), any(), any(), any(), any());
        assertThat(progressUpdates).isNotEmpty();
    }
    @Test
    @DisplayName("Should exclude models currently being actively downloaded from aborted scans")
    void testScanAbortedDownloadsExcludesActiveDownloads() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);

        Files.write(ckptDir.resolve("active_model.safetensors.part0"), new byte[5_000]);
        Files.write(ckptDir.resolve("active_model.safetensors.part1"), new byte[5_000]);
        Files.write(ckptDir.resolve("aborted_model.safetensors.cmfd"), new byte[8_000]);

        when(downloadManager.isDownloading("active_model.safetensors")).thenReturn(true);
        when(downloadManager.isDownloading("aborted_model.safetensors")).thenReturn(false);

        List<AbortedDownloadItem> items = service.scanAbortedDownloads(tempModelsDir.toString());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).targetFileName()).isEqualTo("aborted_model.safetensors");
        assertThat(items.get(0).sizeBytes()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("Should protect actively downloading files from being deleted")
    void testDeleteAbortedDownloadsProtectsActiveDownloads() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);

        Path activePart = ckptDir.resolve("active_stream.safetensors.part0");
        Files.write(activePart, new byte[12_000]);

        AbortedDownloadItem item = new AbortedDownloadItem(
                activePart,
                "active_stream.safetensors",
                "checkpoints",
                12_000L,
                java.time.Instant.now(),
                ".part0",
                null
        );

        when(downloadManager.isDownloading("active_stream.safetensors")).thenReturn(true);

        long freed = service.deleteAbortedDownloads(List.of(item));

        assertThat(freed).isEqualTo(0L);
        assertThat(Files.exists(activePart)).isTrue();
    }

    @Test
    @DisplayName("Should fallback to Hugging Face Hub if Civitai resolution fails")
    void testResumeDownloadResolvesFromHuggingFace() throws IOException {
        Path unetDir = tempModelsDir.resolve("unet");
        Files.createDirectories(unetDir);
        Path cmfd = unetDir.resolve("flux_q4.gguf.cmfd");
        Files.write(cmfd, new byte[5_000]);

        AbortedDownloadItem item = new AbortedDownloadItem(
                cmfd,
                "flux_q4.gguf",
                "unet",
                5_000L,
                java.time.Instant.now(),
                ".cmfd",
                null
        );

        when(civitaiHubService.queryModels(anyString(), any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        HuggingFaceFileEntry hfFile = new HuggingFaceFileEntry(
                "flux_q4.gguf",
                "flux_q4.gguf",
                1_000_000L,
                "1.0 MB",
                "https://huggingface.co/city96/flux/resolve/main/flux_q4.gguf?download=true",
                "unet",
                "~8 GB VRAM",
                true
        );
        HuggingFaceModelEntry hfRepo = new HuggingFaceModelEntry(
                "city96/flux",
                "city96",
                "flux",
                100,
                50,
                "2026-10-24",
                "text-to-image",
                List.of("gguf"),
                "false",
                List.of(hfFile)
        );

        when(huggingFaceHubService.searchRepositories(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(hfRepo)));
        when(huggingFaceHubService.getRepositoryFiles("city96/flux"))
                .thenReturn(CompletableFuture.completedFuture(List.of(hfFile)));

        List<String> progress = new ArrayList<>();
        service.resumeDownload(item, progress::add);

        verify(downloadManager, timeout(2000)).startQueue(any(), any(), any(), any(), any());
    }
}
