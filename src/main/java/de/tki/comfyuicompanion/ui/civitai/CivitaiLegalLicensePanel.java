package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.ui.dialog.LegalNoticeDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * UI panel displaying legal notices, commercial license terms, attribution constraints,
 * and Person-of-Interest (POI / § 22 KUG) acknowledgments for Civitai models.
 */
public class CivitaiLegalLicensePanel extends JPanel {

    private final CivitaiModelEntry entry;
    private final Window owner;
    private final boolean darkMode;
    private JCheckBox poiConfirmCheck;

    /**
     * Constructs a new CivitaiLegalLicensePanel for the given model entry.
     *
     * @param entry    the Civitai model metadata entry
     * @param owner    parent dialog window for modal alerts
     * @param darkMode whether dark mode styling should be applied
     */
    public CivitaiLegalLicensePanel(CivitaiModelEntry entry, Window owner, boolean darkMode) {
        this.entry = entry;
        this.owner = owner;
        this.darkMode = darkMode;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        buildUi();
    }

    private void buildUi() {
        Color borderCol = darkMode ? new Color(60, 65, 82) : new Color(220, 224, 232);
        Color boxBg = darkMode ? new Color(24, 27, 36) : new Color(255, 255, 255);
        Color textMuted = darkMode ? new Color(160, 168, 185) : new Color(90, 100, 115);

        JPanel licenseBox = new JPanel(new BorderLayout(8, 4));
        licenseBox.setOpaque(true);
        licenseBox.setBackground(boxBg);
        licenseBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderCol, 1),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);

        // Commercial use line
        String commText = entry.isCommercialAllowed()
                ? "💼 Commercial Use: Allowed (" + entry.allowCommercialUse() + ")"
                : "🚫 Commercial Use: Not permitted (Personal / non-commercial use only)";
        JLabel commLabel = new JLabel(commText);
        commLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        commLabel.setForeground(entry.isCommercialAllowed() ? new Color(16, 185, 129) : new Color(239, 68, 68));
        leftCol.add(commLabel);

        // Attribution line
        String attrText = entry.allowNoCredit()
                ? "• Attribution: Not required"
                : "• Attribution: Required for " + entry.creator();
        JLabel attrLabel = new JLabel(attrText);
        attrLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        attrLabel.setForeground(textMuted);
        leftCol.add(Box.createVerticalStrut(2));
        leftCol.add(attrLabel);

        // Derivatives line
        String derivText = "• Derivative works / Remixing: " + (entry.allowDerivatives() ? "Allowed" : "Restricted / Not allowed");
        JLabel derivLabel = new JLabel(derivText);
        derivLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        derivLabel.setForeground(textMuted);
        leftCol.add(Box.createVerticalStrut(1));
        leftCol.add(derivLabel);

        // User responsibility reminder
        JLabel noteLabel = new JLabel("Note: Model license metadata provided by CivitAI API for informational purposes. Compliance rests with the user.");
        noteLabel.setFont(new Font("SansSerif", Font.ITALIC, 9));
        noteLabel.setForeground(textMuted);
        leftCol.add(Box.createVerticalStrut(2));
        leftCol.add(noteLabel);

        licenseBox.add(leftCol, BorderLayout.CENTER);

        JButton legalBtn = new JButton("⚖️ Legal Notices");
        legalBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        legalBtn.setToolTipText("View legal notices, liability disclaimer, and CivitAI licensing terms");
        legalBtn.addActionListener(e -> LegalNoticeDialog.show(owner, darkMode));
        licenseBox.add(legalBtn, BorderLayout.EAST);

        add(licenseBox);

        // POI / Personality rights warning if model depicts a real human
        if (entry.poi()) {
            add(Box.createVerticalStrut(6));
            JPanel poiBox = new JPanel();
            poiBox.setLayout(new BoxLayout(poiBox, BoxLayout.Y_AXIS));
            poiBox.setOpaque(true);
            Color alertBg = darkMode ? new Color(45, 30, 15) : new Color(254, 243, 199);
            Color alertBorder = darkMode ? new Color(146, 64, 14) : new Color(245, 158, 11);
            poiBox.setBackground(alertBg);
            poiBox.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(alertBorder, 1),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            JLabel poiWarn = new JLabel("<html><b>⚠️ Person of Interest (Personality & Likeness Rights)</b><br>" +
                    "<span style='font-size: 10px;'>This model depicts a real person. Generating or distributing likenesses of real persons " +
                    "without explicit consent infringes upon personality rights.</span></html>");
            poiWarn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            poiBox.add(poiWarn);

            poiConfirmCheck = new JCheckBox("I confirm that I will not infringe upon personality rights and assume full legal responsibility.");
            poiConfirmCheck.setOpaque(false);
            poiConfirmCheck.setFont(new Font("SansSerif", Font.BOLD, 10));
            poiBox.add(Box.createVerticalStrut(4));
            poiBox.add(poiConfirmCheck);

            add(poiBox);
        }

        // Adult/NSFW warning if flagged
        if (entry.nsfw()) {
            add(Box.createVerticalStrut(6));
            JPanel nsfwBox = new JPanel(new BorderLayout());
            nsfwBox.setOpaque(true);
            nsfwBox.setBackground(darkMode ? new Color(60, 20, 20) : new Color(254, 226, 226));
            nsfwBox.setBorder(BorderFactory.createLineBorder(new Color(239, 68, 68), 1));
            JLabel nsfwLbl = new JLabel("🚫 NSFW content is disabled in this application to ensure youth protection.");
            nsfwLbl.setForeground(new Color(220, 38, 38));
            nsfwLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            nsfwBox.add(nsfwLbl, BorderLayout.CENTER);
            add(nsfwBox);
        }
    }

    /**
     * Verifies that all legal requirements and acknowledgments are met before proceeding
     * with model download or ComfyUI generation.
     *
     * @return true if execution is permitted, false if blocked by legal validation
     */
    public boolean isReadyToProceed() {
        if (entry.nsfw()) {
            JOptionPane.showMessageDialog(owner,
                    "NSFW models are permanently disabled in this application for youth protection reasons.",
                    "NSFW Content Blocked", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (entry.poi() && poiConfirmCheck != null && !poiConfirmCheck.isSelected()) {
            JOptionPane.showMessageDialog(owner,
                    "This model depicts a real person (Person of Interest).\n\n" +
                    "Please acknowledge personality rights and check the confirmation box\n" +
                    "before proceeding with download or generation.",
                    "Legal Acknowledgment Required", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        return true;
    }
}
