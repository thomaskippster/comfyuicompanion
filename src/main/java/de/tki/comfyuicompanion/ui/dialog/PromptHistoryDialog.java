package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.domain.PromptHistoryEntry;
import de.tki.comfyuicompanion.service.IPromptHistoryService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Modal dialog displaying the history of recent prompt execution sessions,
 * allowing users to review parameters and restore previous prompts into Image Lab.
 */
public class PromptHistoryDialog extends JDialog {

    private final IPromptHistoryService historyService;
    private final Consumer<PromptHistoryEntry> onRecall;
    private final DefaultListModel<PromptHistoryEntry> listModel = new DefaultListModel<>();
    private final JList<PromptHistoryEntry> historyList = new JList<>(listModel);
    private final JTextArea previewArea = new JTextArea();
    private final JLabel detailsLabel = new JLabel("Select an entry to view full parameters");

    /**
     * Constructs the Prompt History dialog.
     *
     * @param owner parent window
     * @param historyService service providing prompt history
     * @param onRecall callback invoked when an entry is selected for restoration
     */
    public PromptHistoryDialog(Window owner, IPromptHistoryService historyService, Consumer<PromptHistoryEntry> onRecall) {
        super(owner, "Prompt History & Quick-Recall", ModalityType.APPLICATION_MODAL);
        this.historyService = historyService;
        this.onRecall = onRecall;

        setSize(750, 480);
        setLocationRelativeTo(owner);
        initUI();
        loadHistory();
    }

    private void initUI() {
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Left: List of prompts
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer(new PromptHistoryCellRenderer());
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updatePreview(historyList.getSelectedValue());
            }
        });
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && historyList.getSelectedValue() != null) {
                    restoreSelected();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(historyList);
        listScroll.setPreferredSize(new Dimension(320, 0));

        // Right: Details & Preview
        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        detailsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        rightPanel.add(detailsLabel, BorderLayout.NORTH);

        previewArea.setEditable(false);
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rightPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        splitPane.setDividerLocation(320);
        contentPane.add(splitPane, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottomBar = new JPanel(new BorderLayout());
        JButton btnClear = new JButton("Clear History", SvgIconFactory.get(AppIcon.TRASH, 14));
        btnClear.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all prompt history?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                if (historyService != null) historyService.clearHistory();
                listModel.clear();
                previewArea.setText("");
                detailsLabel.setText("History cleared.");
            }
        });
        bottomBar.add(btnClear, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnClose = new JButton("Cancel");
        btnClose.addActionListener(e -> dispose());
        rightActions.add(btnClose);

        JButton btnRestore = new JButton("Restore Prompt", SvgIconFactory.get(AppIcon.CHECK, 14));
        btnRestore.putClientProperty("FlatLaf.styleClass", "accent");
        btnRestore.addActionListener(e -> restoreSelected());
        rightActions.add(btnRestore);

        bottomBar.add(rightActions, BorderLayout.EAST);
        contentPane.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private void loadHistory() {
        listModel.clear();
        if (historyService != null) {
            List<PromptHistoryEntry> entries = historyService.getHistory();
            for (PromptHistoryEntry entry : entries) {
                listModel.addElement(entry);
            }
        }
        if (!listModel.isEmpty()) {
            historyList.setSelectedIndex(0);
        }
    }

    private void updatePreview(PromptHistoryEntry entry) {
        if (entry == null) {
            detailsLabel.setText("No selection");
            previewArea.setText("");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateStr = sdf.format(new Date(entry.timestamp()));
        detailsLabel.setText(String.format(Locale.US, "Time: %s  |  Model: %s  |  %dx%d  |  Steps: %d, CFG: %.1f",
                dateStr, entry.model().isEmpty() ? "Default" : entry.model(),
                entry.width(), entry.height(), entry.steps(), entry.cfg()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== POSITIVE PROMPT ===\n");
        sb.append(entry.positivePrompt()).append("\n\n");
        if (entry.negativePrompt() != null && !entry.negativePrompt().isBlank()) {
            sb.append("=== NEGATIVE PROMPT ===\n");
            sb.append(entry.negativePrompt()).append("\n\n");
        }
        sb.append("=== PARAMETERS ===\n");
        sb.append("Resolution: ").append(entry.width()).append(" x ").append(entry.height()).append("\n");
        sb.append("Steps: ").append(entry.steps()).append("  |  CFG: ").append(entry.cfg()).append("\n");
        sb.append("Sampler: ").append(entry.sampler()).append("  |  Scheduler: ").append(entry.scheduler()).append("\n");

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private void restoreSelected() {
        PromptHistoryEntry selected = historyList.getSelectedValue();
        if (selected != null) {
            if (onRecall != null) {
                onRecall.accept(selected);
            }
            dispose();
        }
    }

    private static class PromptHistoryCellRenderer extends DefaultListCellRenderer {
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof PromptHistoryEntry entry) {
                String time = timeFormat.format(new Date(entry.timestamp()));
                setText(String.format("<html><b>[%s]</b> %s</html>", time, escapeHtml(entry.getDisplayTitle())));
                setToolTipText(entry.positivePrompt());
            }
            return this;
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
