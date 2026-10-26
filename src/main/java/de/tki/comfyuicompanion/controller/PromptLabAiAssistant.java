package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Assistant component providing on-demand local AI subject completion suggestions
 * and managing model downloads for the Prompt Lab.
 */
public class PromptLabAiAssistant {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabAiAssistant.class);

    private final LocalAIService localAIService;
    private final ExecutorService backgroundExecutor;
    private final Runnable onAiModelDisplayUpdated;

    /**
     * Constructs a new PromptLabAiAssistant.
     *
     * @param localAIService          AI service for local inference
     * @param backgroundExecutor      executor for async download and inference operations
     * @param onAiModelDisplayUpdated callback triggered when model state changes
     */
    public PromptLabAiAssistant(LocalAIService localAIService,
                                ExecutorService backgroundExecutor,
                                Runnable onAiModelDisplayUpdated) {
        this.localAIService = localAIService;
        this.backgroundExecutor = backgroundExecutor;
        this.onAiModelDisplayUpdated = onAiModelDisplayUpdated;
    }

    /**
     * Downloads the active local AI model preset on-demand from Hugging Face for offline prompt suggestions.
     *
     * @param parent        the parent component for dialogs
     * @param promptLabView prompt lab view
     */
    public void downloadLocalGemmaModel(Component parent, PromptLabView promptLabView) {
        LocalGemmaService.LlmModelPreset preset = resolveActivePreset(promptLabView);
        downloadLocalModel(parent, promptLabView, preset);
    }

    /**
     * Downloads a specific local AI model preset on-demand from Hugging Face.
     * Ensures models are never pre-bundled, strictly adhering to licensing and copyright guidelines.
     *
     * @param parent        the parent component for dialogs
     * @param promptLabView prompt lab view
     * @param preset        the targeted model preset to fetch
     */
    public void downloadLocalModel(Component parent, PromptLabView promptLabView, LocalGemmaService.LlmModelPreset preset) {
        if (promptLabView == null || preset == null || localAIService == null || localAIService.getLocalGemmaService() == null) {
            return;
        }
        JButton btnSuggest = promptLabView.getBtnSuggestSubject();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSuggest != null) {
            btnSuggest.setEnabled(false);
            btnSuggest.setText("Downloading...");
            btnSuggest.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("Starting on-demand download of " + preset.getDisplayName() + " (" + preset.getApproxSize() + ") from Hugging Face...\n");
        }

        localAIService.getLocalGemmaService().downloadModel(
                preset,
                (percent, status) -> SwingUtilities.invokeLater(() -> {
                    if (console != null) console.append("Download: " + status + "\n");
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (btnSuggest != null) {
                        btnSuggest.setEnabled(true);
                        btnSuggest.setText("Suggest");
                        btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                    }
                    if (console != null) {
                        console.append("✅ " + preset.getDisplayName() + " downloaded successfully!\n");
                    }
                    JOptionPane.showMessageDialog(parent,
                            preset.getDisplayName() + " downloaded successfully and ready for offline inference!",
                            "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onAiModelDisplayUpdated != null) onAiModelDisplayUpdated.run();
                }),
                (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                    if (btnSuggest != null) {
                        btnSuggest.setEnabled(true);
                        btnSuggest.setText("Suggest");
                        btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                    }
                    if (console != null) console.append("❌ Download failed: " + errorMsg + "\n");
                    JOptionPane.showMessageDialog(parent,
                            "Failed to download " + preset.getDisplayName() + ": " + errorMsg,
                            "Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    /**
     * Generates optimized visual prompt completions based on the user's input and active local LLM model.
     *
     * @param parent        the parent component for dialogs
     * @param promptLabView prompt lab view
     */
    public void suggestSubjectCompletions(Component parent, PromptLabView promptLabView) {
        if (promptLabView == null || localAIService == null || localAIService.getLocalGemmaService() == null) {
            return;
        }

        LocalGemmaService.LlmModelPreset activePreset = resolveActivePreset(promptLabView);
        boolean hasModel = localAIService.isLocalGemmaDownloaded(activePreset);

        if (!hasModel) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "The selected local AI model [" + activePreset.getDisplayName() + "] is not yet downloaded.\n" +
                            "Would you like to download " + activePreset.getDisplayName() + " (" + activePreset.getApproxSize() + " GGUF) now?\n\n" +
                            "Note: To comply with licensing guidelines, models are never pre-bundled and must be fetched on demand.",
                    "Download " + activePreset.getDisplayName() + "?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalModel(parent, promptLabView, activePreset);
            }
            return;
        }

        String currentSubject = promptLabView.getPromptSubjectField() != null ? promptLabView.getPromptSubjectField().getText().trim() : "";
        if (currentSubject.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Please enter a basic prompt or subject first.",
                    "Empty Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JButton btnSuggest = promptLabView.getBtnSuggestSubject();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSuggest != null) {
            btnSuggest.setEnabled(false);
            btnSuggest.setText("Suggesting...");
            btnSuggest.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("Generating prompt completion suggestions using " + activePreset.getDisplayName() + "...\n");
        }

        backgroundExecutor.execute(() -> {
            List<String> suggestions = null;
            StringBuilder errorLogs = new StringBuilder();
            try {
                suggestions = localAIService.getDirectGemmaCompletions(currentSubject);
            } catch (Throwable ex) {
                logger.warn("AI suggestions failed: {}", ex.getMessage(), ex);
                errorLogs.append("AI suggestions failed: ").append(ex.getMessage()).append("\n");
                for (StackTraceElement ste : ex.getStackTrace()) {
                    errorLogs.append("\tat ").append(ste).append("\n");
                }
            }

            final List<String> finalSuggestions = suggestions;
            final String finalErrors = errorLogs.toString();
            SwingUtilities.invokeLater(() -> {
                if (btnSuggest != null) {
                    btnSuggest.setEnabled(true);
                    btnSuggest.setText("Suggest");
                    btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                }

                if (finalSuggestions != null && !finalSuggestions.isEmpty()) {
                    promptLabView.getSuggestionsPanel().populateSuggestions(finalSuggestions, suggestion -> {
                        promptLabView.getPromptSubjectField().setText(suggestion);
                        if (promptLabView.getPromptLabLeftPanel() != null) {
                            promptLabView.getPromptLabLeftPanel().revalidate();
                            promptLabView.getPromptLabLeftPanel().repaint();
                        }
                    });
                    if (promptLabView.getPromptLabLeftPanel() != null) {
                        promptLabView.getPromptLabLeftPanel().revalidate();
                        promptLabView.getPromptLabLeftPanel().repaint();
                    }
                    if (console != null) {
                        console.append("Subject suggestions loaded successfully using " + activePreset.getDisplayName() + ".\n");
                    }
                } else {
                    if (console != null) {
                        console.append("❌ Error: Could not generate suggestions.\n");
                        if (!finalErrors.isEmpty()) console.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(parent,
                            "Could not generate suggestions. Please ensure " + activePreset.getDisplayName() + " is downloaded correctly.",
                            "Suggestions Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }

    private LocalGemmaService.LlmModelPreset resolveActivePreset(PromptLabView promptLabView) {
        if (promptLabView != null && promptLabView.getPromptAiModelCombo() != null) {
            Object selected = promptLabView.getPromptAiModelCombo().getSelectedItem();
            if (selected instanceof LocalGemmaService.LlmModelPreset preset) {
                return preset;
            }
        }
        if (localAIService != null && localAIService.getLocalGemmaService() != null) {
            return localAIService.getLocalGemmaService().getActivePreset();
        }
        return LocalGemmaService.LlmModelPreset.GEMMA_3_4B;
    }
}
