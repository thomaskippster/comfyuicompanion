package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.dialog.LegalNoticeDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dashboard panel displaying concise legal notices, user responsibilities,
 * youth protection compliance, and licensing terms tailored specifically
 * to the entire ComfyUI Companion application.
 */
public class DashboardLegalNoticePanel extends JPanel {

    private final Window parentWindow;
    private final ConfigService configService;

    /**
     * Constructs a new DashboardLegalNoticePanel.
     *
     * @param parentWindow  the owning parent window or frame
     * @param configService configuration service for theme preferences
     */
    public DashboardLegalNoticePanel(Window parentWindow, ConfigService configService) {
        this.parentWindow = parentWindow;
        this.configService = configService;

        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        buildUi();
    }

    private void buildUi() {
        putClientProperty("FlatLaf.style", "arc: 12; background: $Card.background; border: 10,10,10,10,$Card.border,1,12");
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);
        JLabel iconLabel = new JLabel(SvgIconFactory.get(AppIcon.FILE_TEXT, 15));
        JLabel titleLabel = new JLabel("Legal & Application Notices");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.putClientProperty("FlatLaf.styleClass", "h4");
        headerPanel.add(iconLabel);
        headerPanel.add(titleLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Core summary notices
        JEditorPane summaryPane = new JEditorPane();
        summaryPane.setEditable(false);
        summaryPane.setContentType("text/html");
        summaryPane.setOpaque(false);
        summaryPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        boolean isDark = configService != null && configService.isDarkMode();
        String textColor = isDark ? "#d4d4d8" : "#334155";
        String tagColor = isDark ? "#a5b4fc" : "#4338ca";

        String html = "<html><body style='font-family: sans-serif; font-size: 10px; margin: 0; color: " + textColor + ";'>" +
                "<p style='margin: 0 0 4px 0; line-height: 1.25;'><b style='color: " + tagColor + ";'>• Local Execution:</b> 100% on local hardware. User is sole creator & liable for all outputs.</p>" +
                "<p style='margin: 0 0 4px 0; line-height: 1.25;'><b style='color: " + tagColor + ";'>• Multimodal Rights:</b> Respect personality, likeness & voice rights (deepfakes & POI).</p>" +
                "<p style='margin: 0 0 4px 0; line-height: 1.25;'><b style='color: " + tagColor + ";'>• Youth Protection:</b> Permanent safe search (<code>nsfw=false</code>) enforced under JMStV.</p>" +
                "<p style='margin: 0 0 4px 0; line-height: 1.25;'><b style='color: " + tagColor + ";'>• Model & Node Licenses:</b> Comply with creator licenses & execute only trusted Python nodes.</p>" +
                "<p style='margin: 0; line-height: 1.25;'><b style='color: " + tagColor + ";'>• Data Privacy:</b> Zero telemetry, no cloud prompt tracking, strictly local storage.</p>" +
                "</body></html>";

        summaryPane.setText(html);
        summaryPane.setCaretPosition(0);
        add(summaryPane, BorderLayout.CENTER);

        // Action button to view full legal notices
        JButton viewFullBtn = new JButton("View Full Legal Notices & Terms", SvgIconFactory.get(AppIcon.FILE_TEXT, 13));
        viewFullBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        viewFullBtn.setPreferredSize(new Dimension(0, 30));
        viewFullBtn.putClientProperty("JButton.buttonType", "roundRect");
        viewFullBtn.addActionListener(e -> {
            boolean dark = configService != null && configService.isDarkMode();
            LegalNoticeDialog.show(parentWindow, dark);
        });

        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        btnPanel.add(viewFullBtn, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }
}
