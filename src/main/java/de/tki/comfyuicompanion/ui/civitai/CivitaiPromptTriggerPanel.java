package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * UI panel displaying the editable generation prompt and copyable trigger words
 * for models browsed in the Civitai Model Hub.
 */
public class CivitaiPromptTriggerPanel extends JPanel {

    private final JTextArea promptArea;

    /**
     * Constructs a new CivitaiPromptTriggerPanel configured for the given model entry.
     *
     * @param entry the Civitai model entry
     */
    public CivitaiPromptTriggerPanel(CivitaiModelEntry entry) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // Generation Prompt Area
        JPanel promptBox = new JPanel(new BorderLayout(6, 4));
        promptBox.setOpaque(false);
        promptBox.setBorder(BorderFactory.createTitledBorder("Generation Prompt (Sent to ComfyUI)"));

        String initialPrompt;
        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            initialPrompt = String.join(", ", entry.triggerWords()) + ", masterpiece, high quality, highly detailed";
        } else {
            initialPrompt = entry.name() + ", masterpiece, 8k resolution, best quality";
        }
        promptArea = new JTextArea(initialPrompt, 2, 40);
        promptArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setPreferredSize(new Dimension(500, 55));
        promptBox.add(promptScroll, BorderLayout.CENTER);
        add(promptBox);

        // Trigger words (if model provides them)
        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            add(Box.createVerticalStrut(8));
            JPanel triggerBox = new JPanel(new BorderLayout(8, 4));
            triggerBox.setOpaque(false);
            triggerBox.setBorder(BorderFactory.createTitledBorder("Trigger Words (LoRA / Concept)"));

            String joined = String.join(", ", entry.triggerWords());
            JTextField txtTriggers = new JTextField(joined);
            txtTriggers.setEditable(false);
            txtTriggers.setFont(new Font("Monospaced", Font.PLAIN, 11));
            triggerBox.add(txtTriggers, BorderLayout.CENTER);

            JButton copyBtn = new JButton("Copy", SvgIconFactory.get(AppIcon.CHECK, 12));
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(joined), null);
                copyBtn.setText("Copied! ✅");
                Timer t = new Timer(1500, evt -> copyBtn.setText("Copy"));
                t.setRepeats(false);
                t.start();
            });
            triggerBox.add(copyBtn, BorderLayout.EAST);
            add(triggerBox);
        }
    }

    /**
     * Retrieves the current prompt text entered by the user.
     *
     * @return current trimmed prompt text
     */
    public String getPromptText() {
        return promptArea != null ? promptArea.getText().trim() : "";
    }
}
