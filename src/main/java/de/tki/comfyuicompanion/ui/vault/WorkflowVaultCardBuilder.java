package de.tki.comfyuicompanion.ui.vault;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintCardBuilder;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintPreviewCache;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Builds individual visual card panels for workflows displayed in My Workflow Vault.
 * Features local image rendering, model readiness badges, integrated model download triggers,
 * inspection details, and vault removal. Omits any 'Send to Image Lab' button by design.
 */
public class WorkflowVaultCardBuilder {

    private static final int CARD_W = 240;
    private static final int CARD_H = 315;

    /**
     * Constructs a customized vault workflow card.
     *
     * @param entry              blueprint entry
     * @param status             computed model status
     * @param previewCache       preview image cache
     * @param showDetailsAction  callback when details or card are clicked
     * @param downloadAction     callback when 'Download Missing' is clicked
     * @param removeAction       callback when 'Remove from Vault' is clicked
     * @return the card panel
     */
    @SuppressWarnings("deprecation")
    public static JPanel buildVaultCard(
            BlueprintEntry entry,
            BlueprintStatus status,
            BlueprintPreviewCache previewCache,
            Consumer<BlueprintEntry> showDetailsAction,
            Consumer<BlueprintEntry> downloadAction,
            Consumer<BlueprintEntry> removeAction) {

        Color[] palette = new Color[]{new Color(15, 23, 42), new Color(30, 58, 138), new Color(59, 130, 246)};
        JPanel baseCard = BlueprintCardBuilder.buildCard(
                entry,
                status,
                palette,
                previewCache,
                new Color(24, 26, 34),
                new Color(48, 52, 68),
                new Color(0, 210, 190),
                new Color(228, 230, 240),
                new Color(130, 138, 165),
                "",
                showDetailsAction,
                null
        );

        JPanel actionFooter = new JPanel(new BorderLayout(6, 0));
        actionFooter.setOpaque(false);
        actionFooter.setBorder(new EmptyBorder(4, 8, 8, 8));

        boolean isReady = status != null && status.missingCount == 0;

        if (!isReady) {
            int missingCount = status != null ? status.missingCount : 1;
            JButton btnDownload = new JButton("Download Missing (" + missingCount + ")", SvgIconFactory.get(AppIcon.DOWNLOAD, 13));
            btnDownload.setBackground(new Color(217, 119, 6));
            btnDownload.setForeground(Color.WHITE);
            btnDownload.setFont(new Font("SansSerif", Font.BOLD, 11));
            btnDownload.putClientProperty("Button.arc", 8);
            btnDownload.addActionListener(e -> {
                if (downloadAction != null) downloadAction.accept(entry);
            });
            actionFooter.add(btnDownload, BorderLayout.CENTER);
        } else {
            JButton btnDetails = new JButton("Inspect Details", SvgIconFactory.get(AppIcon.QUICK_CHECK, 13));
            btnDetails.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btnDetails.putClientProperty("Button.arc", 8);
            btnDetails.addActionListener(e -> {
                if (showDetailsAction != null) showDetailsAction.accept(entry);
            });
            actionFooter.add(btnDetails, BorderLayout.CENTER);
        }

        JButton btnRemove = new JButton(SvgIconFactory.get(AppIcon.TRASH, 13));
        btnRemove.setToolTipText("Remove from My Workflow Vault");
        btnRemove.putClientProperty("Button.arc", 8);
        btnRemove.setPreferredSize(new Dimension(28, 26));
        btnRemove.addActionListener(e -> {
            if (removeAction != null) removeAction.accept(entry);
        });
        actionFooter.add(btnRemove, BorderLayout.EAST);

        baseCard.setPreferredSize(new Dimension(CARD_W, CARD_H));
        baseCard.setMinimumSize(new Dimension(180, CARD_H));
        baseCard.add(actionFooter, BorderLayout.SOUTH);

        return baseCard;
    }
}
