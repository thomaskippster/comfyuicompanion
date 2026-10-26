package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader;
import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader.ExtractedPngWorkflow;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Handles Drag & Drop and file import of ComfyUI-generated PNG images into the Image Lab,
 * restoring embedded prompts, seeds, sampling parameters, models, and workflow graphs.
 */
public class PromptLabImageDropHandler {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabImageDropHandler.class);

    /**
     * Installs drag-and-drop listeners on the specified Image Lab view components so users
     * can drop PNG images directly from the OS file explorer or Output Gallery.
     *
     * @param view       the PromptLabView instance
     * @param controller the PromptLabController instance (nullable)
     */
    public static void installDropTargets(PromptLabView view, PromptLabView.PromptLabController controller) {
        if (view == null) return;
        attachDropTarget(view, view, controller);
        if (view.getPromptImagePreviewLabel() != null) {
            attachDropTarget(view.getPromptImagePreviewLabel(), view, controller);
        }
        if (view.getPromptLabLeftPanel() != null) {
            attachDropTarget(view.getPromptLabLeftPanel(), view, controller);
        }
        if (view.getPromptJsonArea() != null) {
            attachDropTarget(view.getPromptJsonArea(), view, controller);
        }
    }

    private static void attachDropTarget(JComponent target, PromptLabView view, PromptLabView.PromptLabController controller) {
        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.rejectDrop();
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        File first = droppedFiles.get(0);
                        PromptLabView.PromptLabController activeCtrl = controller != null ? controller : view.getController();
                        importFromPngFile(first, view, activeCtrl, true);
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    logger.warn("Drag & drop PNG import failed: {}", ex.getMessage());
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    /**
     * Opens a file chooser dialog allowing the user to pick a ComfyUI PNG image to remix.
     *
     * @param view       the PromptLabView instance
     * @param controller the PromptLabController instance
     */
    public static void promptAndImportPng(PromptLabView view, PromptLabView.PromptLabController controller) {
        if (view == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Remix / Re-Load Workflow from ComfyUI PNG Image");
        chooser.setFileFilter(new FileNameExtensionFilter("ComfyUI PNG Images (*.png)", "png"));
        if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            PromptLabView.PromptLabController activeCtrl = controller != null ? controller : view.getController();
            importFromPngFile(chooser.getSelectedFile(), view, activeCtrl, true);
        }
    }

    /**
     * Extracts ComfyUI PNG metadata from the given file and populates the Image Lab UI controls.
     *
     * @param file       the PNG file to inspect
     * @param view       the PromptLabView instance
     * @param controller the PromptLabController instance (nullable)
     * @param showDialog whether to display a warning dialog if no ComfyUI metadata is found
     * @return {@code true} if metadata was successfully extracted and applied
     */
    public static boolean importFromPngFile(File file,
                                            PromptLabView view,
                                            PromptLabView.PromptLabController controller,
                                            boolean showDialog) {
        if (file == null || !file.exists() || view == null) return false;
        PromptLabView.PromptLabController activeCtrl = controller != null ? controller : view.getController();

        // Always display the dropped image in preview if readable
        try {
            Image img = ImageIO.read(file);
            if (img != null) {
                SwingUtilities.invokeLater(() -> view.scaleAndSetImage(img));
            }
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

        Optional<ExtractedPngWorkflow> optMeta = ComfyPngMetadataReader.readMetadata(file);
        if (optMeta.isEmpty()) {
            if (showDialog) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        view,
                        "No embedded ComfyUI workflow/prompt metadata was found in:\n" + file.getName(),
                        "No ComfyUI PNG Metadata Found",
                        JOptionPane.INFORMATION_MESSAGE
                ));
            }
            return false;
        }

        ExtractedPngWorkflow meta = optMeta.get();
        SwingUtilities.invokeLater(() -> applyExtractedMetadataToView(file, meta, view, activeCtrl));
        return true;
    }

    private static void applyExtractedMetadataToView(File file,
                                                     ExtractedPngWorkflow meta,
                                                     PromptLabView view,
                                                     PromptLabView.PromptLabController controller) {
        // 1. Restore workflow JSON first so UI spinner updates can synchronize onto it
        if (meta.promptJson() != null && !meta.promptJson().isBlank() && view.getPromptJsonArea() != null) {
            try {
                JSONObject raw = new JSONObject(meta.promptJson());
                JSONObject wrapped = raw.has("prompt") ? raw : new JSONObject().put("prompt", raw);
                view.getPromptJsonArea().setText(wrapped.toString(2));
            } catch (Exception ignored) {
                view.getPromptJsonArea().setText(meta.promptJson());
            }
        } else if (meta.workflowJson() != null && !meta.workflowJson().isBlank() && view.getPromptJsonArea() != null) {
            view.getPromptJsonArea().setText(meta.workflowJson());
        }

        if (controller instanceof PromptLabController plc) {
            String activeGraphJson = (meta.promptJson() != null && !meta.promptJson().isBlank())
                    ? meta.promptJson()
                    : meta.workflowJson();
            if (activeGraphJson != null && !activeGraphJson.isBlank()) {
                plc.setCurrentBlueprintGuiJson(activeGraphJson);
                plc.populateUiFromWorkflow(activeGraphJson);
            }
        }

        // 2. Restore positive & negative prompts
        if (meta.positivePrompt() != null && !meta.positivePrompt().isBlank() && view.getPromptSubjectField() != null) {
            view.getPromptSubjectField().setText(meta.positivePrompt());
        }
        if (meta.negativePrompt() != null && view.getPromptNegativeField() != null) {
            view.getPromptNegativeField().setText(meta.negativePrompt());
        }

        // 3. Restore dimensions & sampling parameters
        if (meta.width() != null && meta.width() >= 64 && view.getPromptWidthSpinner() != null) {
            view.getPromptWidthSpinner().setValue(meta.width());
        }
        if (meta.height() != null && meta.height() >= 64 && view.getPromptHeightSpinner() != null) {
            view.getPromptHeightSpinner().setValue(meta.height());
        }
        if (meta.batchSize() != null && meta.batchSize() >= 1 && view.getPromptBatchSizeSpinner() != null) {
            view.getPromptBatchSizeSpinner().setValue(meta.batchSize());
        }
        if (meta.steps() != null && meta.steps() >= 1 && view.getPromptStepsSpinner() != null) {
            view.getPromptStepsSpinner().setValue(meta.steps());
        }
        if (meta.cfg() != null && meta.cfg() >= 0.0 && view.getPromptCfgSpinner() != null) {
            view.getPromptCfgSpinner().setValue(meta.cfg());
        }
        if (meta.denoise() != null && meta.denoise() >= 0.0 && meta.denoise() <= 1.0 && view.getPromptDenoiseSpinner() != null) {
            view.getPromptDenoiseSpinner().setValue(meta.denoise());
        }
        if (meta.samplerName() != null && !meta.samplerName().isBlank() && view.getPromptSamplerCombo() != null) {
            selectOrAddComboItem(view.getPromptSamplerCombo(), meta.samplerName());
        }
        if (meta.scheduler() != null && !meta.scheduler().isBlank() && view.getPromptSchedulerCombo() != null) {
            selectOrAddComboItem(view.getPromptSchedulerCombo(), meta.scheduler());
        }

        // 4. Select or add model in combo box without triggering blueprint template overwrite
        if (meta.modelName() != null && !meta.modelName().isBlank() && view.getPromptModelCombo() != null) {
            JComboBox<String> combo = view.getPromptModelCombo();
            combo.setEnabled(true);
            if (controller instanceof PromptLabController plc) {
                plc.runWithComboUpdateSuppressed(() -> selectOrAddComboItem(combo, meta.modelName()));
            } else {
                selectOrAddComboItem(combo, meta.modelName());
            }
        }

        if (view.getPromptPresetLabel() != null) {
            String seedInfo = meta.seed() != null ? " | Seed: " + meta.seed() : "";
            view.getPromptPresetLabel().setText("🎨 Remixed from PNG: " + file.getName() + seedInfo);
        }

        if (view.getBtnSendToComfy() != null) {
            view.getBtnSendToComfy().setEnabled(true);
            view.getBtnSendToComfy().setToolTipText("Send the remixed workflow prompt to ComfyUI.");
        }

        if (controller != null) {
            controller.onUpdatePromptLabJson();
        }

        if (view.getPromptLabConsole() != null) {
            view.getPromptLabConsole().append(String.format(
                    "📥 Remixed workflow from PNG '%s' (Model: %s, Steps: %s, CFG: %s, Seed: %s)\n",
                    file.getName(),
                    meta.modelName() != null ? meta.modelName() : "embedded",
                    meta.steps() != null ? meta.steps() : "default",
                    meta.cfg() != null ? meta.cfg() : "default",
                    meta.seed() != null ? meta.seed() : "random"
            ));
        }
    }

    private static void selectOrAddComboItem(JComboBox<String> combo, String target) {
        if (combo == null || target == null || target.isBlank()) return;
        String clean = target.trim();
        for (int i = 0; i < combo.getItemCount(); i++) {
            String item = combo.getItemAt(i);
            if (item != null && (item.equalsIgnoreCase(clean) || item.toLowerCase().contains(clean.toLowerCase()))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.addItem(clean);
        combo.setSelectedItem(clean);
    }
}
