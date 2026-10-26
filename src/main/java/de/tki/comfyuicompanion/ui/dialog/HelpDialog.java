package de.tki.comfyuicompanion.ui.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.service.impl.ConfigService;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog rendering the in-app user guide as themed HTML.
 */
final class HelpDialog {
    private static final Logger logger = LoggerFactory.getLogger(HelpDialog.class);

    private HelpDialog() {
    }

    /**
     * Displays the user guide dialog, themed according to the current look and feel.
     *
     * @param parent        the parent frame
     * @param configService configuration service used to detect dark mode (may be null)
     */
    static void show(JFrame parent, ConfigService configService) {
        JDialog helpDialog = new JDialog(parent, "User Guide & Documentation", true);
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(950, 750);
        helpDialog.setLocationRelativeTo(parent);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }
        });

        Color panelBg = UIManager.getColor("Panel.background");
        Color labelFg = UIManager.getColor("Label.foreground");

        boolean darkMode = configService != null && configService.isDarkMode();
        String bgColor = panelBg != null ? String.format("#%02x%02x%02x", panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue()) : (darkMode ? "#121214" : "#f8fafc");
        String textColor = labelFg != null ? String.format("#%02x%02x%02x", labelFg.getRed(), labelFg.getGreen(), labelFg.getBlue()) : (darkMode ? "#e2e8f0" : "#1e293b");

        String accentColor = darkMode ? "#818cf8" : "#4f46e5";
        String boxBg = darkMode ? "#1a1a24" : "#ffffff";
        String boxBorder = darkMode ? "#2d2d3d" : "#e2e8f0";
        String tipBg = darkMode ? "#222230" : "#f1f5f9";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='padding: 25px; font-family: sans-serif; background-color: ").append(bgColor).append("; color: ").append(textColor).append(";'>");
        sb.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 25px;'>🚀 Companion for ComfyUI</h1>");

        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h2 style='color: ").append(accentColor).append("; margin-top: 0;'>🛠️ Getting Started (Setup)</h2>");
        sb.append("<ol>");
        sb.append("<li><b>Set Directories:</b> Navigate to <i>Settings -> Directories</i> and specify your ComfyUI root and 'models' paths. Choose a 'Cold Archive' path (e.g. on a high-capacity HDD) to offload unused models.</li>");
        sb.append("<li><b>Unlock Vault:</b> Establish a Master Password on first startup. This AES-256 encrypted vault securely stores your sensitive API keys.</li>");
        sb.append("<li><b>Configure API Keys & Services:</b> Go to <i>Settings -> API Keys & Services...</i> to configure Civitai, Hugging Face, ElevenLabs and text-to-speech credentials.</li>");
        sb.append("<li><b>Install Canvas Bridge:</b> Click <i>Settings -> ComfyUI Bridge...</i> to add the 🚀 floating button to your ComfyUI workspace for seamless 1-click canvas syncing.</li>");
        sb.append("</ol></div>");

        sb.append("<h2 style='color: ").append(accentColor).append(";'>✨ Core Features</h2>");
        sb.append("<p><b>📦 Archive Manager:</b> Reclaim SSD space. Move massive, rarely used models (SDXL checkpoints, Flux models, etc.) to your cold storage archive. The app automatically detects when a loaded workflow needs archived models and offers <b>one-click restoration</b>.</p>");
        sb.append("<p><b>🧼 Storage Optimizer:</b> Automatically scans model folders for duplicate files using SHA-256 and Fast AutoV1 Hashing. Clean up redundant copies or instantly merge duplicates into NTFS hardlinks to preserve folder structure while freeing up physical space.</p>");
        sb.append("<p><b>🎨 Prompt Lab & AI Optimization:</b> Draft and refine prompts, select styles, and pick optional environmental contexts (including 'None (Optional)'). Features a powerful <b>Optimize with AI</b> button which detects the target model architecture (SD 1.5, SDXL, Flux) and rewrites prompts to suit that target.</p>");
        sb.append("<p><b>🔍 Model Update Checker & Tracker:</b> Scans your local models and checks Civitai to see if newer versions or upgrades are available. Download and update your models with a single click.</p>");
        sb.append("<p><b>🖼️ Output Gallery:</b> Live monitoring of the ComfyUI output directory. Renders images and videos asynchronously with support for selection checkboxes, bulk deletion, and file actions without slowing down the UI.</p>");
        sb.append("<p><b>📊 Workflow Graph Preview:</b> Displays a visual diagram of the ComfyUI nodes and linkages directly in the companion app whenever a workflow is loaded or synced.</p>");
        sb.append("<p><b>⚡ System Monitoring & Server Lifecycle:</b> Keeps track of real-time CPU, RAM, and VRAM utilization. Automatically cleans up active ports to resolve server startup crashes, streams server logs, and refreshes the browser automatically upon reconnect.</p>");
        sb.append("<p><b>🔌 Automatic App & Node Updater:</b> Keeps ComfyUI and installed Custom Nodes up-to-date with 1-click updates that run `git pull` and set up virtual environments automatically.</p>");

        sb.append("<div style='background-color: ").append(tipBg).append("; padding: 15px; border-radius: 8px; margin-top: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<b>💡 Pro Tips:</b><br>");
        sb.append("• <b>Drag & Drop:</b> Drag any workflow JSON or generated image (.png with metadata) directly into the app window to import its model list.<br>");
        sb.append("• <b>Overnight Queue:</b> Enable 'Shutdown after Queue' in the downloader to automatically turn off the computer when downloads are done.<br>");
        sb.append("• <b>System Tray:</b> Enable 'Stay in Background' so the app minimizes to the system tray instead of closing.<br>");
        sb.append("• <b>Launch Profiles:</b> Select from 10 optimized launch arguments (like CPU Mode, Low VRAM, Beast Mode, or FP8 Flux optimizations) matching your hardware.");
        sb.append("</div>");

        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-top: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h3 style='color: ").append(accentColor).append("; margin-top: 0; margin-bottom: 8px;'>🌐 Project Links & Community</h3>");
        sb.append("<p style='font-size: 13px; line-height: 1.6; margin: 0;'>");
        sb.append("• <b>GitHub Repository:</b> <a href='https://github.com/thomaskippster/comfyuicompanion' style='color: ").append(accentColor).append(";'>https://github.com/thomaskippster/comfyuicompanion</a><br>");
        sb.append("• <b>Pinokio AI App:</b> <a href='https://pinokio.co/apps/github-com-thomaskippster-comfyuicompanion' style='color: ").append(accentColor).append(";'>https://pinokio.co/apps/github-com-thomaskippster-comfyuicompanion</a><br>");
        sb.append("• <b>Pinokio Official:</b> <a href='https://pinokio.co' style='color: ").append(accentColor).append(";'>https://pinokio.co</a>");
        sb.append("</p></div>");

        sb.append("<p style='margin-top: 30px; color: #888; font-style: italic; text-align: center;'>Version 2.0.2 - Open Source Software developed by TKI</p>");
        sb.append("</body></html>");

        editorPane.setText(sb.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        helpDialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> helpDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        helpDialog.add(btnPanel, BorderLayout.SOUTH);

        helpDialog.setVisible(true);
    }
}
