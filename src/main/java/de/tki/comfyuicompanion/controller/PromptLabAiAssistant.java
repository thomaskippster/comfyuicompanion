package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Assistant component providing local Gemma AI subject completion suggestions
 * and managing model downloads for the Prompt Lab.
 */
public class PromptLabAiAssistant {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabAiAssistant.class);

    private final LocalAIService localAIService;
    private final ExecutorService backgroundExecutor;
    private final Runnable onAiModelDisplayUpdated;

    public PromptLabAiAssistant(LocalAIService localAIService,
                                ExecutorService backgroundExecutor,
                                Runnable onAiModelDisplayUpdated) {
        this.localAIService = localAIService;
        this.backgroundExecutor = backgroundExecutor;
        this.onAiModelDisplayUpdated = onAiModelDisplayUpdated;
    }

    /**
     * Downloads the local Gemma model from Hugging Face for offline AI suggestions.
     *
     * @param parent the parent component for dialogs
     * @param promptLabView prompt lab view
     */
    public void downloadLocalGemmaModel(Component parent, PromptLabView promptLabView) {
        if (promptLabView == null) return;
        JButton btnSuggest = promptLabView.getBtnSuggestSubject();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSuggest != null) {
            btnSuggest.setEnabled(false);
            btnSuggest.setText("Downloading...");
            btnSuggest.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("Starting download of Gemma-3-4B GGUF model (3 GB) from Hugging Face...\n");
        }

        localAIService.getLocalGemmaService().downloadModel(
                (percent, status) -> SwingUtilities.invokeLater(() -> {
                    if (console != null) console.append("Download: " + status + "\n");
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (btnSuggest != null) {
                        btnSuggest.setEnabled(true);
                        btnSuggest.setText("Suggest");
                        btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                    }
                    if (console != null) console.append("✅ Local Gemma model downloaded successfully!\n");
                    JOptionPane.showMessageDialog(parent,
                            "Local Gemma model downloaded successfully!",
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
                            "Failed to download Gemma model: " + errorMsg,
                            "Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    /**
     * Uses the local Gemma model to generate subject completion suggestions based on the current prompt.
     *
     * @param parent the parent component for dialogs
     * @param promptLabView prompt lab view
     */
    public void suggestSubjectCompletions(Component parent, PromptLabView promptLabView) {
        if (promptLabView == null) return;

        boolean hasGemma = localAIService != null && localAIService.isLocalGemmaDownloaded();
        if (!hasGemma) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "The local Gemma model is not downloaded.\n" +
                            "Would you like to download the Gemma-3-4B GGUF model (approx. 3 GB) now to get suggestions?",
                    "Download Local Gemma Model?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalGemmaModel(parent, promptLabView);
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
            console.append("Generating prompt completion suggestions using local Gemma...\n");
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
                    JPanel suggPanel = promptLabView.getPromptSubjectSuggestionsPanel();
                    if (suggPanel != null) {
                        suggPanel.removeAll();
                        for (String suggestion : finalSuggestions) {
                            JButton sugBtn = new JButton(suggestion);
                            sugBtn.setHorizontalAlignment(SwingConstants.LEFT);
                            sugBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
                            sugBtn.putClientProperty("FlatLaf.style", "arc: 6; background: $TextField.background; border: 4,8,4,8,$Card.border,1,6");
                            sugBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                            sugBtn.setToolTipText("Click to use: " + suggestion);
                            sugBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                            sugBtn.setPreferredSize(new Dimension(300, 30));
                            sugBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                            sugBtn.addActionListener(e -> {
                                promptLabView.getPromptSubjectField().setText(suggestion);
                                promptLabView.getPromptSubjectSuggestionsWrapper().setVisible(false);
                                promptLabView.getPromptLabLeftPanel().revalidate();
                                promptLabView.getPromptLabLeftPanel().repaint();
                            });
                            suggPanel.add(sugBtn);
                            suggPanel.add(Box.createVerticalStrut(4));
                        }
                    }
                    promptLabView.getPromptSubjectSuggestionsWrapper().setVisible(true);
                    promptLabView.getPromptLabLeftPanel().revalidate();
                    promptLabView.getPromptLabLeftPanel().repaint();
                    if (console != null) console.append("Subject suggestions loaded successfully using local Gemma.\n");
                } else {
                    if (console != null) {
                        console.append("❌ Error: Could not generate suggestions.\n");
                        if (!finalErrors.isEmpty()) console.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(parent,
                            "Could not generate suggestions. Please ensure the local Gemma model is downloaded correctly.",
                            "Suggestions Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }
}
