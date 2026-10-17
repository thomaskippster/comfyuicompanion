package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.service.IAiActComplianceService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Modal dialog enabling batch export of generated images equipped with Article 50
 * EU AI Act compliant machine-readable metadata and optional discreet visual watermarks.
 */
public class GalleryComplianceExportDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(GalleryComplianceExportDialog.class);

    private final List<Path> filesToExport;
    private final IAiActComplianceService complianceService;
    private final ConfigService configService;

    private JCheckBox chkMetadata;
    private JCheckBox chkWatermark;
    private JTextField txtTargetDir;
    private JButton btnExport;

    /**
     * Constructs a new GalleryComplianceExportDialog.
     *
     * @param owner             parent window
     * @param filesToExport     list of image files to export
     * @param complianceService service providing metadata and watermarking routines
     * @param configService     application configuration service
     */
    public GalleryComplianceExportDialog(Window owner,
                                         List<Path> filesToExport,
                                         IAiActComplianceService complianceService,
                                         ConfigService configService) {
        super(owner, "🇪🇺 EU AI Act Export & Compliance (Art. 50)", ModalityType.APPLICATION_MODAL);
        this.filesToExport = filesToExport;
        this.complianceService = complianceService;
        this.configService = configService;

        setSize(580, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        buildUi();
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Info Header Box
        JPanel infoBox = new JPanel(new BorderLayout(8, 6));
        infoBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(79, 70, 229, 120), 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        JLabel infoLbl = new JLabel("<html><b>Disclosure Requirement under Art. 50(2) EU AI Act</b><br>" +
                "<span style='font-size: 11px;'>" +
                "Artificially generated media must be machine-readably identifiable as such. " +
                "Pure AI images lack human creative authorship under copyright law (OLG Duesseldorf 2026) " +
                "and are in the public domain. This export utility embeds standardized C2PA/IPTC metadata.</span></html>");
        infoBox.add(infoLbl, BorderLayout.CENTER);
        content.add(infoBox);

        content.add(Box.createVerticalStrut(14));

        JLabel countLbl = new JLabel("Selected files for export: " + filesToExport.size());
        countLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        content.add(countLbl);

        content.add(Box.createVerticalStrut(10));

        // Checkbox options
        chkMetadata = new JCheckBox("Embed machine-readable Art. 50 metadata (IPTC DigitalSourceType & Public Domain status)", true);
        chkMetadata.setFont(new Font("SansSerif", Font.PLAIN, 12));
        content.add(chkMetadata);

        chkWatermark = new JCheckBox("Add discreet visual watermark badge (\"✦ AI Generated • Art. 50 EU AI Act\")", true);
        chkWatermark.setFont(new Font("SansSerif", Font.PLAIN, 12));
        content.add(chkWatermark);

        content.add(Box.createVerticalStrut(14));

        // Target directory row
        content.add(new JLabel("Target Directory:"));
        content.add(Box.createVerticalStrut(4));
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        String defaultDir = configService != null ? configService.getResolvedOutputDir() + File.separator + "eu_ai_act_export" : "output/eu_ai_act_export";
        txtTargetDir = new JTextField(defaultDir);
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(txtTargetDir.getText().trim());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtTargetDir.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        dirRow.add(txtTargetDir, BorderLayout.CENTER);
        dirRow.add(btnBrowse, BorderLayout.EAST);
        content.add(dirRow);

        add(content, BorderLayout.CENTER);

        // Buttons South
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());
        btnPanel.add(btnCancel);

        btnExport = new JButton("Export with Compliance");
        btnExport.putClientProperty("JButton.buttonType", "accent");
        btnExport.addActionListener(e -> performExport());
        btnPanel.add(btnExport);

        add(btnPanel, BorderLayout.SOUTH);
    }

    private void performExport() {
        String targetDirStr = txtTargetDir.getText().trim();
        if (targetDirStr.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please select a target directory.", "Missing Information", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path targetDir = Path.of(targetDirStr);
        boolean applyWatermark = chkWatermark.isSelected();
        btnExport.setEnabled(false);
        btnExport.setText("Exporting... ⏳");

        Thread.ofVirtual().name("ComplianceExport").start(() -> {
            int successCount = 0;
            int errorCount = 0;
            for (Path file : filesToExport) {
                try {
                    if (complianceService != null) {
                        complianceService.exportWithCompliance(file, targetDir, applyWatermark);
                    }
                    successCount++;
                } catch (Exception ex) {
                    errorCount++;
                    logger.error("Failed to export compliance image: {}", ex.getMessage());
                }
            }

            final int ok = successCount;
            final int err = errorCount;
            SwingUtilities.invokeLater(() -> {
                btnExport.setEnabled(true);
                btnExport.setText("Export with Compliance");
                dispose();

                String message = String.format("Export completed!\n\nSuccessfully tagged: %d file(s)\nErrors: %d\n\nTarget: %s",
                        ok, err, targetDir.toAbsolutePath());
                JOptionPane.showMessageDialog(getOwner(), message, "EU AI Act Export", JOptionPane.INFORMATION_MESSAGE);
            });
        });
    }
}
