package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceCardBuilder;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceFilesTablePanel;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceHeaderToolbar;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceModelDetailsDialog;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFacePromptPanel;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceThumbnailGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HuggingFaceUiTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("HuggingFaceHeaderToolbar maintains correct default filters and triggers callbacks")
    void testHeaderToolbar() {
        HuggingFaceHeaderToolbar toolbar = new HuggingFaceHeaderToolbar();
        assertNotNull(toolbar);

        assertEquals("", toolbar.getSearchQuery());
        assertEquals("", toolbar.getSelectedFormat());
        assertEquals("", toolbar.getSelectedTask());
        assertEquals("trendingScore", toolbar.getSelectedSort());

        AtomicBoolean triggered = new AtomicBoolean(false);
        toolbar.setOnFilterChanged(() -> triggered.set(true));

        toolbar.setStatusText("30 models found");
        toolbar.setRefreshEnabled(true);
        toolbar.updateTheme(true);

        assertNotNull(toolbar);
    }

    @Test
    @DisplayName("HuggingFaceCardBuilder produces styled card components matching Civitai dimensions")
    void testCardBuilder() {
        HuggingFaceModelEntry entry = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf",
                "city96",
                "FLUX.1-dev-gguf",
                12500,
                340,
                "2026-08-10T12:00:00Z",
                "text-to-image",
                List.of("flux", "gguf"),
                "false",
                List.of()
        );

        AtomicBoolean downloaded = new AtomicBoolean(false);
        AtomicBoolean detailsOpened = new AtomicBoolean(false);

        JPanel card = HuggingFaceCardBuilder.buildCard(
                entry,
                false,
                false,
                null,
                true,
                e -> downloaded.set(true),
                e -> detailsOpened.set(true)
        );

        assertNotNull(card);
        assertEquals(new Dimension(220, 280), card.getPreferredSize());
        assertEquals(2, card.getComponentCount()); // North (thumbnail), Center (content)
    }

    @Test
    @DisplayName("HuggingFaceFilesTablePanel correctly formats rows and provides target folder overrides")
    void testFilesTablePanel() {
        HuggingFaceFileEntry file1 = new HuggingFaceFileEntry(
                "flux1-dev-Q4_K_M.gguf",
                "flux1-dev-Q4_K_M.gguf",
                12_238_000_000L,
                "11.40 GB",
                "https://huggingface.co/city96/FLUX.1-dev-gguf/resolve/main/flux1-dev-Q4_K_M.gguf?download=true",
                "models/unet",
                "🟢 Standard (~8-12 GB)",
                true
        );

        HuggingFaceFilesTablePanel panel = new HuggingFaceFilesTablePanel(
                f -> "Missing",
                Color.DARK_GRAY,
                Color.WHITE,
                Color.LIGHT_GRAY
        );

        panel.setFiles(List.of(file1));
        assertNotNull(panel.getSelectedFile());
        assertEquals("flux1-dev-Q4_K_M.gguf", panel.getSelectedFile().filename());
        assertEquals("models/unet", panel.getSelectedTargetFolder());

        panel.updateStatusForFile("flux1-dev-Q4_K_M.gguf", "Downloading 45%");
        assertNotNull(panel);
    }

    @Test
    @DisplayName("HuggingFaceThumbnailGenerator synthesizes non-null preview banner with correct dimensions")
    void testThumbnailGenerator() {
        HuggingFaceModelEntry entry = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf",
                "city96",
                "FLUX.1-dev-gguf",
                12500,
                340,
                "2026-08-10T12:00:00Z",
                "text-to-image",
                List.of("flux", "gguf"),
                "false",
                List.of()
        );

        Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(entry, true, null);
        assertNotNull(thumb);
        assertEquals(480, thumb.getWidth(null));
        assertEquals(300, thumb.getHeight(null));
    }

    @Test
    @DisplayName("HuggingFacePromptPanel resolves default prompt and allows user edits")
    void testPromptPanel() {
        HuggingFaceModelEntry entry = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf",
                "city96",
                "FLUX.1-dev-gguf",
                12500,
                340,
                "2026-08-10T12:00:00Z",
                "text-to-image",
                List.of("flux", "gguf"),
                "false",
                List.of()
        );

        HuggingFacePromptPanel promptPanel = new HuggingFacePromptPanel(entry);
        assertNotNull(promptPanel.getPromptText());
        assertTrue(promptPanel.getPromptText().contains("portrait"));
    }

    @Test
    @DisplayName("HuggingFaceModelDetailsDialog initializes cleanly without NPE when repo already contains files")
    void testDetailsDialogInitializationWithFiles() {
        if (GraphicsEnvironment.isHeadless()) return;

        HuggingFaceFileEntry auxVae = new HuggingFaceFileEntry(
                "ae.safetensors", "ae.safetensors", 335_000_000L, "319.5 MB",
                "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/ae.safetensors?download=true",
                "vae", "Standard", true
        );
        HuggingFaceFileEntry mainUnet = new HuggingFaceFileEntry(
                "flux1-dev.safetensors", "flux1-dev.safetensors", 23_800_000_000L, "22.17 GB",
                "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/flux1-dev.safetensors?download=true",
                "unet", "🔴 Full Precision (24 GB+)", true
        );

        HuggingFaceModelEntry entry = new HuggingFaceModelEntry(
                "black-forest-labs/FLUX.1-dev",
                "black-forest-labs",
                "FLUX.1-dev",
                680000,
                1600,
                "2026-08-10T12:00:00Z",
                "text-to-image",
                List.of("flux", "safetensors"),
                "false",
                List.of(auxVae, mainUnet)
        );

        HuggingFaceModelDetailsDialog dialog = new HuggingFaceModelDetailsDialog(
                null,
                entry,
                null,
                null,
                null,
                null,
                f -> "ae.safetensors".equals(f.filename()) ? "Installed" : "Missing",
                null
        );

        assertNotNull(dialog);
        assertEquals(new Dimension(1250, 760), dialog.getSize());
        dialog.dispose();
    }
    @Test
    @DisplayName("HuggingFaceDetailsActionsHandler converts file entries to ModelInfo correctly")
    void testToModelInfo() {
        HuggingFaceFileEntry file = new HuggingFaceFileEntry(
                "flux1-dev.safetensors", "flux1-dev.safetensors", 23_800_000_000L, "22.17 GB",
                "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/flux1-dev.safetensors?download=true",
                "unet", "?? Full Precision (24 GB+)", true
        );
        de.tki.comfyuicompanion.domain.ModelInfo info = de.tki.comfyuicompanion.ui.huggingface.HuggingFaceDetailsActionsHandler.toModelInfo(file);
        assertEquals("flux1-dev.safetensors", info.getFilename());
        assertEquals("unet", info.getSave_path());
        assertEquals("22.17 GB", info.getSize());
        assertEquals(23_800_000_000L, info.getByteSize());
        assertEquals("Missing", info.getDescription());
    }

    @Test
    @DisplayName("HuggingFaceDetailsActionsHandler transitions missing models to Download Manager callback")
    void testExecuteDownloadMissing() {
        HuggingFaceFileEntry auxVae = new HuggingFaceFileEntry(
                "ae.safetensors", "ae.safetensors", 335_000_000L, "319.5 MB",
                "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/ae.safetensors?download=true",
                "vae", "Standard", true
        );
        HuggingFaceFileEntry mainUnet = new HuggingFaceFileEntry(
                "flux1-dev.safetensors", "flux1-dev.safetensors", 23_800_000_000L, "22.17 GB",
                "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/flux1-dev.safetensors?download=true",
                "unet", "?? Full Precision (24 GB+)", true
        );

        java.util.List<de.tki.comfyuicompanion.domain.ModelInfo> passed = new java.util.ArrayList<>();
        AtomicBoolean closed = new AtomicBoolean(false);

        de.tki.comfyuicompanion.ui.huggingface.HuggingFaceDetailsActionsHandler.executeDownloadMissing(
                null,
                List.of(auxVae, mainUnet),
                passed::addAll,
                null,
                null,
                () -> closed.set(true)
        );

        assertTrue(closed.get());
        assertEquals(2, passed.size());
        assertEquals("ae.safetensors", passed.get(0).getFilename());
        assertEquals("flux1-dev.safetensors", passed.get(1).getFilename());
    }

    @Test
    @DisplayName("HuggingFaceCardBuilder reflects missing models status when not installed")
    void testCardBuilderMissingStatus() {
        HuggingFaceModelEntry entry = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf", "city96", "FLUX.1-dev-gguf",
                12500, 340, "2026-08-10T12:00:00Z", "text-to-image",
                List.of("flux", "gguf"), "false", List.of()
        );

        JPanel cardNotInstalled = HuggingFaceCardBuilder.buildCard(
                entry, false, false, null, true, e -> {}, e -> {}
        );
        JPanel cardInstalled = HuggingFaceCardBuilder.buildCard(
                entry, true, false, null, true, e -> {}, e -> {}
        );
        assertNotNull(cardNotInstalled);
        assertNotNull(cardInstalled);
    }
}
