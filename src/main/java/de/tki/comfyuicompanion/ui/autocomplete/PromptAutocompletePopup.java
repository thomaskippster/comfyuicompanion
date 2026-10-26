package de.tki.comfyuicompanion.ui.autocomplete;

import de.tki.comfyuicompanion.service.autocomplete.PromptAutocompleteService;
import de.tki.comfyuicompanion.service.autocomplete.PromptCompletionCandidate;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight, non-intrusive popup autocomplete controller that attaches to any Swing
 * {@link JTextComponent} and provides real-time tag, LoRA, embedding, and trigger word completion.
 */
public class PromptAutocompletePopup {

    private final JTextComponent textComponent;
    private final PromptAutocompleteService autocompleteService;
    private final Supplier<Path> modelsDirSupplier;
    private final JPopupMenu popupMenu = new JPopupMenu();
    private final DefaultListModel<PromptCompletionCandidate> listModel = new DefaultListModel<>();
    private final JList<PromptCompletionCandidate> suggestionList = new JList<>(listModel);
    private boolean suppressEvents = false;

    /**
     * Constructs and binds a new autocomplete popup to the specified text component.
     *
     * @param textComponent       target prompt input field
     * @param autocompleteService completion coordinator service
     * @param modelsDirSupplier   supplier resolving the active local models directory
     */
    public PromptAutocompletePopup(JTextComponent textComponent,
                                   PromptAutocompleteService autocompleteService,
                                   Supplier<Path> modelsDirSupplier) {
        this.textComponent = textComponent;
        this.autocompleteService = autocompleteService != null ? autocompleteService : new PromptAutocompleteService();
        this.modelsDirSupplier = modelsDirSupplier != null ? modelsDirSupplier : () -> Path.of("models");
        initPopupUi();
        bindListeners();
    }

    /**
     * Convenience factory method installing default autocomplete support onto a prompt text field.
     *
     * @param textComponent target text component
     * @return installed popup controller
     */
    public static PromptAutocompletePopup install(JTextComponent textComponent) {
        return new PromptAutocompletePopup(textComponent, new PromptAutocompleteService(), () -> Path.of("models"));
    }

    private void initPopupUi() {
        popupMenu.setFocusable(false);
        popupMenu.setBorder(BorderFactory.createLineBorder(new Color(70, 75, 90), 1));

        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFocusable(false);
        suggestionList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        suggestionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PromptCompletionCandidate c) {
                    lbl.setText(" " + c.displayText() + " ");
                }
                lbl.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                return lbl;
            }
        });

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    commitSelectedCandidate();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(suggestionList);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(280, 160));
        popupMenu.add(scroll);
    }

    private void bindListeners() {
        textComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleRefresh(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleRefresh(); }
            @Override public void changedUpdate(DocumentEvent e) {}
        });

        textComponent.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popupMenu.isVisible()) return;
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_DOWN) {
                    int next = Math.min(suggestionList.getSelectedIndex() + 1, listModel.size() - 1);
                    suggestionList.setSelectedIndex(next);
                    suggestionList.ensureIndexIsVisible(next);
                    e.consume();
                } else if (code == KeyEvent.VK_UP) {
                    int prev = Math.max(suggestionList.getSelectedIndex() - 1, 0);
                    suggestionList.setSelectedIndex(prev);
                    suggestionList.ensureIndexIsVisible(prev);
                    e.consume();
                } else if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_TAB) {
                    if (commitSelectedCandidate()) {
                        e.consume();
                    }
                } else if (code == KeyEvent.VK_ESCAPE) {
                    popupMenu.setVisible(false);
                    e.consume();
                }
            }
        });
    }

    private void scheduleRefresh() {
        if (suppressEvents || !textComponent.isShowing() || !textComponent.hasFocus()) {
            return;
        }
        SwingUtilities.invokeLater(this::updateSuggestions);
    }

    private void updateSuggestions() {
        if (suppressEvents || !textComponent.isShowing()) return;
        String text = textComponent.getText();
        int caret = textComponent.getCaretPosition();
        String token = PromptAutocompleteService.extractActiveToken(text, caret);
        if (token.length() < 2) {
            popupMenu.setVisible(false);
            return;
        }

        List<PromptCompletionCandidate> candidates = autocompleteService.querySuggestions(
                token, modelsDirSupplier.get(), 10
        );
        if (candidates.isEmpty()) {
            popupMenu.setVisible(false);
            return;
        }

        listModel.clear();
        for (PromptCompletionCandidate c : candidates) {
            listModel.addElement(c);
        }
        suggestionList.setSelectedIndex(0);
        popupMenu.show(textComponent, 0, textComponent.getHeight());
    }

    private boolean commitSelectedCandidate() {
        PromptCompletionCandidate selected = suggestionList.getSelectedValue();
        if (selected == null) return false;
        suppressEvents = true;
        try {
            String fullText = textComponent.getText();
            int caret = textComponent.getCaretPosition();
            String updated = PromptAutocompleteService.applyCompletion(fullText, caret, selected.insertText());
            textComponent.setText(updated);
            textComponent.setCaretPosition(updated.length());
            popupMenu.setVisible(false);
            return true;
        } finally {
            suppressEvents = false;
        }
    }
}
