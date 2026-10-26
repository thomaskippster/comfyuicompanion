package de.tki.comfyuicompanion.ui.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.ui.StandardDialog;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog presenting comprehensive, application-specific legal notices:
 * local workflow architecture and sole user liability, multimodal rights (deepfakes & voice synthesis),
 * youth protection compliance (JMStV / mandatory NSFW safe search), third-party model & custom node licensing,
 * local Python execution security, and zero-telemetry data privacy.
 */
public final class LegalNoticeDialog {
    private static final Logger logger = LoggerFactory.getLogger(LegalNoticeDialog.class);

    private LegalNoticeDialog() {
        // Utility class
    }

    /**
     * Displays the legal notice dialog using default theme settings.
     *
     * @param parent the parent component or window
     */
    public static void show(Component parent) {
        show(parent, isDarkTheme());
    }

    /**
     * Displays the legal notice and terms of service modal dialog.
     *
     * @param parent   the parent component or window
     * @param darkMode whether dark mode styling should be applied
     */
    public static void show(Component parent, boolean darkMode) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        StandardDialog dialog = new StandardDialog(owner, "⚖️ Legal Notices & Application Terms");
        dialog.setSize(780, 680);
        dialog.setMinimumSize(new Dimension(580, 540));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(owner);

        boolean isDark = isDarkTheme() || darkMode;

        // Ensure robust contrast by using coordinated color palettes
        String bgColor = isDark ? "#18181b" : "#f8fafc";
        String textColor = isDark ? "#f4f4f5" : "#0f172a";
        String boxBg = isDark ? "#27272a" : "#ffffff";
        String boxBorder = isDark ? "#3f3f46" : "#cbd5e1";
        String accentColor = isDark ? "#a5b4fc" : "#3730a3";
        String warnTitle = isDark ? "#fde047" : "#854d0e";
        String alertBg = isDark ? "#422006" : "#fefce8";
        String alertBorder = isDark ? "#ca8a04" : "#facc15";
        String alertText = isDark ? "#fef08a" : "#713f12";

        Color awtBg = isDark ? new Color(24, 24, 27) : new Color(248, 250, 252);
        dialog.getContentPane().setBackground(awtBg);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setBackground(awtBg);
        editorPane.setOpaque(true);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }
        });

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='padding: 20px; font-family: sans-serif; background-color: ")
                .append(bgColor).append("; color: ").append(textColor).append(";'>");

        html.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 18px;'>⚖️ Legal Notices & Application Terms</h1>");

        // Section 1: Local Utility & Sole User Responsibility
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>1. Local Architecture & Sole User Responsibility</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'><b>ComfyUI Companion</b> is an open, neutral desktop companion and workflow manager for the local ComfyUI engine. " +
                "All model execution and media generation (image diffusion, video rendering, and voice synthesis) occur exclusively on the user's local hardware. " +
                "<b>The user is the sole producer, publisher, and creator of all generated content.</b> " +
                "Full legal liability for prompts, outputs, copyright/trademark compliance, and licensing rests exclusively with the individual user. " +
                "The application disclaims all liability for user creations and does not promote rights violations.</p>");
        html.append("</div>");

        // Section 2: Multimodal Generation, Likeness & Voice Rights (Deepfakes & POI)
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>2. Likeness, Voice & Personality Rights (Deepfakes & POI)</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'>The application supports multimodal pipelines, including character LoRAs, face swapping, and voice synthesis (e.g. Qwen-TTS). " +
                "Generating or distributing synthetic portrayals, voices, or likenesses of real individuals without their explicit prior consent infringes upon personality rights, privacy laws, and the right to one's own likeness. " +
                "The user must ensure all necessary consents and authorizations are obtained before generating or sharing media depicting real persons.</p>");
        html.append("</div>");

        // Section 3: Youth Protection & Strict NSFW Filtering (JMStV)
        html.append("<div style='background-color: ").append(alertBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(alertBorder).append(";'>");
        html.append("<h3 style='color: ").append(warnTitle).append("; margin-top: 0; margin-bottom: 8px;'>3. Youth Protection & Strict NSFW Safe Search (JMStV)</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(alertText).append(";'>When querying external model catalogs (such as CivitAI), the application strictly enforces youth protection under applicable law (including the German Interstate Treaty on the Protection of Minors in the Media - JMStV). " +
                "Because distributing unverified adult or pornographic material without a certified, closed Age Verification System (AVS) is prohibited, " +
                "the application permanently enforces <code>nsfw=false</code> across all catalog queries. Explicit adult and NSFW models cannot be searched, previewed, or downloaded.</p>");
        html.append("</div>");

        // Section 4: Third-Party Model & Custom Node Licenses
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>4. Third-Party Model & Custom Node Licensing</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'>Models (checkpoints, UNet, LoRAs, VAEs) and ComfyUI custom nodes are created by third parties and governed by their individual licenses (e.g., CreativeML OpenRAIL, Apache 2.0, non-commercial restrictions, attribution requirements, or no-derivative clauses). " +
                "ComfyUI Companion displays metadata from the CivitAI API for informational awareness. " +
                "Users are solely responsible for verifying and complying with the respective creator's license conditions when generating media or using models commercially.</p>");
        html.append("</div>");

        // Section 5: Local Python Execution, Node Safety & Privacy
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>5. Local Execution Safety & Zero Telemetry Privacy</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'>ComfyUI custom nodes execute arbitrary Python code locally with the operating system privileges of the current user. Users must only install trusted custom nodes and execute verified blueprints. " +
                "<br><br><b>Privacy Guarantee:</b> ComfyUI Companion does not collect personal data, track prompt histories, or transmit telemetry to remote servers. All configuration files, hashes, model paths, and generated files remain strictly local.</p>");
        html.append("</div>");

        // Section 6: Optional Provenance & Content Transparency (C2PA)
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>6. Content Authenticity & Optional Provenance Exporter</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'>While ComfyUI Companion as a local UI client is not classified as a high-risk AI provider under EU Regulation 2024/1689, " +
                "it provides an optional <b>Compliance Exporter</b> in the Gallery tab. Users can voluntarily embed standardized IPTC/C2PA provenance metadata or watermark badges into generated images to transparently designate media as AI-generated when publishing or sharing outputs.</p>");
        html.append("</div>");

        // Section 7: Developer Information & Privacy Policy
        html.append("<div style='background-color: ").append(boxBg).append("; padding: 14px; border-radius: 8px; margin-bottom: 14px; border: 1px solid ").append(boxBorder).append(";'>");
        html.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>7. Developer Information & Privacy Policy</h3>");
        html.append("<p style='font-size: 13px; line-height: 1.5; margin: 0; color: ").append(textColor).append(";'><b>Developer (About):</b><br>" +
                "Thomas Kipp<br>" +
                "Project Repository: <a href='https://github.com/thomaskippster/comfyuicompanion' style='color: ").append(accentColor).append(";'>https://github.com/thomaskippster/comfyuicompanion</a><br>" +
                "Pinokio AI App: <a href='https://pinokio.co/apps/github-com-thomaskippster-comfyuicompanion' style='color: ").append(accentColor).append(";'>https://pinokio.co/apps/github-com-thomaskippster-comfyuicompanion</a><br><br>" +
                "<b>Privacy Policy (according to GDPR):</b><br>" +
                "This application is a locally executing desktop software. It does not collect, process, or transmit personal data (such as IP addresses, usage analytics, or telemetry) to the developer or third-party tracking services. " +
                "When utilizing external integrations (e.g. CivitAI or Hugging Face APIs) to download models, your IP address is directly transmitted to these respective third-party providers. Please refer to the privacy policies of these providers for information on their data processing.</p>");
        html.append("</div>");

        html.append("</body></html>");

        editorPane.setText(html.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(awtBg);
        JButton closeBtn = new JButton("Understood / Close");
        closeBtn.putClientProperty("JButton.buttonType", "accent");
        closeBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(closeBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static boolean isDarkTheme() {
        Color panelBg = UIManager.getColor("Panel.background");
        if (panelBg != null) {
            double luminance = (0.299 * panelBg.getRed() + 0.587 * panelBg.getGreen() + 0.114 * panelBg.getBlue()) / 255.0;
            return luminance < 0.5;
        }
        return false;
    }
}
