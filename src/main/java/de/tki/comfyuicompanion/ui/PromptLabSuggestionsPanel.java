package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

/**
 * Encapsulated UI component managing the AI prompt completion suggestions container,
 * header controls, scroll container, and interactive suggestion buttons in the Image Lab.
 */
public class PromptLabSuggestionsPanel extends JPanel {

    private final JPanel listPanel;
    private final JScrollPane scrollPane;
    private Runnable onCloseAction;

    /**
     * Constructs a new PromptLabSuggestionsPanel with hidden default visibility.
     */
    public PromptLabSuggestionsPanel() {
        super(new BorderLayout(5, 5));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel sugTitle = new JLabel("AI Suggestions:");
        sugTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        sugTitle.setForeground(Color.GRAY);
        headerPanel.add(sugTitle, BorderLayout.CENTER);

        JButton btnClose = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 10));
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnClose.setToolTipText("Close suggestions");
        btnClose.addActionListener(e -> {
            setVisible(false);
            if (onCloseAction != null) {
                onCloseAction.run();
            }
        });
        headerPanel.add(btnClose, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        scrollPane = new JScrollPane(listPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the callback invoked when the suggestions close button is clicked.
     *
     * @param onCloseAction action runnable
     */
    public void setOnCloseAction(Runnable onCloseAction) {
        this.onCloseAction = onCloseAction;
    }

    /**
     * Populates the panel with interactive suggestion buttons.
     *
     * @param suggestions list of suggested prompt strings
     * @param onSelected  callback invoked when a suggestion button is clicked
     */
    public void populateSuggestions(List<String> suggestions, Consumer<String> onSelected) {
        listPanel.removeAll();
        if (suggestions != null && !suggestions.isEmpty()) {
            for (String suggestion : suggestions) {
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
                    if (onSelected != null) {
                        onSelected.accept(suggestion);
                    }
                    setVisible(false);
                });
                listPanel.add(sugBtn);
                listPanel.add(Box.createVerticalStrut(4));
            }
            setVisible(true);
        } else {
            setVisible(false);
        }
        revalidate();
        repaint();
    }

    /**
     * Returns the internal vertical list container.
     *
     * @return JPanel containing suggestion items
     */
    public JPanel getListPanel() {
        return listPanel;
    }
}
