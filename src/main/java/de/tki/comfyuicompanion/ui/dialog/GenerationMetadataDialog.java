package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.domain.ComfyGenerationMetadata;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.function.Consumer;

/**
 * Inspection dialog presenting extracted ComfyUI generation parameters
 * (Prompt, Seed, Model, Steps, CFG, Sampler) with 1-click apply action to Image Lab.
 */
public class GenerationMetadataDialog extends JDialog {

    public GenerationMetadataDialog(Window owner,
                                    File imageFile,
                                    ComfyGenerationMetadata metadata,
                                    Consumer<ComfyGenerationMetadata> onApplyToImageLab) {
        super(owner, "Generation Metadata: " + (imageFile != null ? imageFile.getName() : "Image"), ModalityType.APPLICATION_MODAL);
        setSize(620, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header info with thumbnail
        JPanel headerPanel = new JPanel(new BorderLayout(14, 0));
        headerPanel.setOpaque(false);
        if (imageFile != null && imageFile.exists()) {
            JLabel thumbLabel = new JLabel();
            thumbLabel.setPreferredSize(new Dimension(80, 80));
            thumbLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            CompletableFutureLoadThumb(imageFile, thumbLabel);
            headerPanel.add(thumbLabel, BorderLayout.WEST);
        }

        JPanel fileInfoPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        fileInfoPanel.setOpaque(false);
        JLabel nameLbl = new JLabel(imageFile != null ? imageFile.getName() : "Unknown Image");
        nameLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        fileInfoPanel.add(nameLbl);

        String dimText = (metadata.width() != null && metadata.height() != null)
                ? metadata.width() + " × " + metadata.height() + " px" : "Dimensions not embedded";
        long sizeKb = imageFile != null ? imageFile.length() / 1024 : 0;
        fileInfoPanel.add(new JLabel("Size: " + sizeKb + " KB  ·  " + dimText));

        String modelText = metadata.modelName() != null ? metadata.modelName() : "Unknown Model / Checkpoint";
        JLabel modelLbl = new JLabel("🧠 Model: " + modelText);
        modelLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fileInfoPanel.add(modelLbl);
        headerPanel.add(fileInfoPanel, BorderLayout.CENTER);

        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Parameter Badges Grid
        JPanel paramGrid = new JPanel(new GridLayout(2, 3, 10, 8));
        paramGrid.setOpaque(false);
        paramGrid.setBorder(BorderFactory.createTitledBorder("Sampling & Generation Parameters"));

        paramGrid.add(createBadge("Seed", metadata.seed() != null ? String.valueOf(metadata.seed()) : "-"));
        paramGrid.add(createBadge("Steps", metadata.steps() != null ? String.valueOf(metadata.steps()) : "-"));
        paramGrid.add(createBadge("CFG Scale", metadata.cfg() != null ? String.valueOf(metadata.cfg()) : "-"));
        paramGrid.add(createBadge("Sampler", metadata.sampler() != null ? metadata.sampler() : "-"));
        paramGrid.add(createBadge("Scheduler", metadata.scheduler() != null ? metadata.scheduler() : "-"));
        paramGrid.add(createBadge("Resolution", dimText));
        mainPanel.add(paramGrid);
        mainPanel.add(Box.createVerticalStrut(14));

        // Positive Prompt Section
        JPanel posPanel = new JPanel(new BorderLayout(0, 4));
        posPanel.setOpaque(false);
        JPanel posHeader = new JPanel(new BorderLayout());
        posHeader.setOpaque(false);
        JLabel posLbl = new JLabel("✨ Positive Prompt:");
        posLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        posHeader.add(posLbl, BorderLayout.WEST);

        JButton btnCopyPos = new JButton("Copy", SvgIconFactory.get(AppIcon.FILE_TEXT, 12));
        btnCopyPos.setFont(new Font("SansSerif", Font.PLAIN, 11));
        posHeader.add(btnCopyPos, BorderLayout.EAST);
        posPanel.add(posHeader, BorderLayout.NORTH);

        JTextArea posArea = new JTextArea(metadata.positivePrompt() != null ? metadata.positivePrompt() : "No positive prompt embedded in image.");
        posArea.setLineWrap(true);
        posArea.setWrapStyleWord(true);
        posArea.setRows(5);
        posArea.setEditable(false);
        posPanel.add(new JScrollPane(posArea), BorderLayout.CENTER);
        btnCopyPos.addActionListener(e -> copyToClipboard(posArea.getText()));
        mainPanel.add(posPanel);
        mainPanel.add(Box.createVerticalStrut(12));

        // Negative Prompt Section
        JPanel negPanel = new JPanel(new BorderLayout(0, 4));
        negPanel.setOpaque(false);
        JPanel negHeader = new JPanel(new BorderLayout());
        negHeader.setOpaque(false);
        JLabel negLbl = new JLabel("🚫 Negative Prompt:");
        negLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        negHeader.add(negLbl, BorderLayout.WEST);

        JButton btnCopyNeg = new JButton("Copy", SvgIconFactory.get(AppIcon.FILE_TEXT, 12));
        btnCopyNeg.setFont(new Font("SansSerif", Font.PLAIN, 11));
        negHeader.add(btnCopyNeg, BorderLayout.EAST);
        negPanel.add(negHeader, BorderLayout.NORTH);

        JTextArea negArea = new JTextArea(metadata.negativePrompt() != null ? metadata.negativePrompt() : "None");
        negArea.setLineWrap(true);
        negArea.setWrapStyleWord(true);
        negArea.setRows(3);
        negArea.setEditable(false);
        negPanel.add(new JScrollPane(negArea), BorderLayout.CENTER);
        btnCopyNeg.addActionListener(e -> copyToClipboard(negArea.getText()));
        mainPanel.add(negPanel);

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBorder(new EmptyBorder(0, 10, 10, 20));

        if (onApplyToImageLab != null && metadata.hasParameters()) {
            JButton applyBtn = new JButton("🚀 Apply to Image Lab", SvgIconFactory.get(AppIcon.IMAGE_LAB, 14));
            applyBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
            applyBtn.putClientProperty("Button.background", ThemeManager.getAccentColor());
            applyBtn.putClientProperty("Button.foreground", Color.WHITE);
            applyBtn.addActionListener(e -> {
                onApplyToImageLab.accept(metadata);
                dispose();
            });
            footer.add(applyBtn);
        }

        JButton copyAllBtn = new JButton("Copy All Info");
        copyAllBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        copyAllBtn.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            if (metadata.positivePrompt() != null) sb.append(metadata.positivePrompt()).append("\n");
            if (metadata.negativePrompt() != null && !metadata.negativePrompt().isBlank()) {
                sb.append("Negative prompt: ").append(metadata.negativePrompt()).append("\n");
            }
            sb.append("Steps: ").append(metadata.steps() != null ? metadata.steps() : 20)
                    .append(", Sampler: ").append(metadata.sampler() != null ? metadata.sampler() : "euler")
                    .append(", CFG scale: ").append(metadata.cfg() != null ? metadata.cfg() : 7.0)
                    .append(", Seed: ").append(metadata.seed() != null ? metadata.seed() : 0)
                    .append(", Size: ").append(metadata.width() != null ? metadata.width() : 1024).append("x").append(metadata.height() != null ? metadata.height() : 1024)
                    .append(", Model: ").append(metadata.modelName() != null ? metadata.modelName() : "unknown");
            copyToClipboard(sb.toString());
            JOptionPane.showMessageDialog(this, "Generation metadata copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });
        footer.add(copyAllBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);

        add(footer, BorderLayout.SOUTH);
    }

    private JPanel createBadge(String title, String value) {
        JPanel p = new JPanel(new GridLayout(2, 1));
        p.setOpaque(false);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        titleLbl.setForeground(Color.GRAY);
        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        p.add(titleLbl);
        p.add(valLbl);
        return p;
    }

    private void CompletableFutureLoadThumb(File f, JLabel target) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ImageIcon icon = new ImageIcon(f.getAbsolutePath());
                Image img = icon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(img)));
            } catch (Exception ignored) {
            }
        });
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
