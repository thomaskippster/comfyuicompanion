package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;

/**
 * UI panel providing an editable generation prompt text area and prompt templates
 * for Hugging Face models sent to ComfyUI.
 */
public class HuggingFacePromptPanel extends JPanel {

    private final JTextArea promptArea;

    /**
     * Constructs a new HuggingFacePromptPanel initialized with architecture-aware prompt defaults.
     *
     * @param entry the Hugging Face repository entry
     */
    public HuggingFacePromptPanel(HuggingFaceModelEntry entry) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        JPanel promptBox = new JPanel(new BorderLayout(6, 4));
        promptBox.setOpaque(false);
        promptBox.setBorder(BorderFactory.createTitledBorder("Generation Prompt (Sent to ComfyUI)"));

        String initialPrompt = resolveDefaultPrompt(entry);
        promptArea = new JTextArea(initialPrompt, 2, 40);
        promptArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);

        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setPreferredSize(new Dimension(500, 55));
        promptBox.add(promptScroll, BorderLayout.CENTER);

        // Actions toolbar: Copy and Reset
        JPanel btnCol = new JPanel(new GridLayout(2, 1, 2, 2));
        btnCol.setOpaque(false);

        JButton copyBtn = new JButton("Copy", SvgIconFactory.get(AppIcon.CHECK, 12));
        copyBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(getPromptText()), null);
            copyBtn.setText("Copied! ✅");
            Timer t = new Timer(1500, evt -> copyBtn.setText("Copy"));
            t.setRepeats(false);
            t.start();
        });
        btnCol.add(copyBtn);

        JButton resetBtn = new JButton("Reset", SvgIconFactory.get(AppIcon.REFRESH, 12));
        resetBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        resetBtn.addActionListener(e -> promptArea.setText(initialPrompt));
        btnCol.add(resetBtn);

        promptBox.add(btnCol, BorderLayout.EAST);
        add(promptBox);
    }

    /**
     * Retrieves the current prompt text entered by the user.
     *
     * @return trimmed prompt text
     */
    public String getPromptText() {
        return promptArea != null ? promptArea.getText().trim() : "";
    }

    private static String resolveDefaultPrompt(HuggingFaceModelEntry entry) {
        if (entry == null) {
            return "a masterpiece portrait, 8k resolution, highly detailed, photorealistic";
        }
        String lower = (entry.repoId() + " " + entry.modelName()).toLowerCase(Locale.ROOT);
        if (lower.contains("flux")) {
            return "cinematic portrait of a futuristic explorer in an alien bioluminescent forest, 8k resolution, photorealistic, intricate details, natural lighting";
        } else if (lower.contains("wan") || lower.contains("ltx") || "video".equalsIgnoreCase(entry.pipelineTag())) {
            return "a cinematic drone camera sweeping over majestic snowy mountains at sunrise, dramatic golden hour lighting, fluid motion, 4k ultra-detailed";
        } else if (lower.contains("anime") || lower.contains("pony")) {
            return "1girl, solo, anime aesthetic, vibrant colors, detailed eyes, masterpiece, best quality";
        } else {
            return entry.modelName() + ", masterpiece, high quality, highly detailed, 8k resolution, photorealistic";
        }
    }
}
