package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Detailed modal inspection dialog for a Civitai model entry.
 * Presents preview imagery, full descriptions, LoRA trigger words with clipboard integration,
 * target download directory info, and external web navigation.
 */
public class CivitaiModelDetailsDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelDetailsDialog.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final CivitaiModelEntry entry;
    private final boolean isInstalled;
    private final Consumer<CivitaiModelEntry> onDownload;

    /**
     * Constructs a new CivitaiModelDetailsDialog.
     *
     * @param owner       the parent window
     * @param entry       the Civitai model entry to inspect
     * @param isInstalled whether the model is locally present
     * @param onDownload  callback when user requests to download
     */
    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     boolean isInstalled,
                                     Consumer<CivitaiModelEntry> onDownload) {
        super(owner, entry.name(), ModalityType.APPLICATION_MODAL);
        this.entry = entry;
        this.isInstalled = isInstalled;
        this.onDownload = onDownload;

        setSize(780, 680);
        setMinimumSize(new Dimension(650, 520));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        buildUi();
    }

    private void buildUi() {
        // --- Header (North) ---
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setBorder(new EmptyBorder(16, 20, 12, 20));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel titleLbl = new JLabel(entry.name());
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleBlock.add(titleLbl);
        titleBlock.add(Box.createVerticalStrut(4));

        String subText = String.format("By %s  •  Type: %s  •  Base: %s  •  Size: %s  •  ⭐ %.1f",
                entry.creator(), entry.type(),
                entry.baseModel() != null && !entry.baseModel().isBlank() ? entry.baseModel() : "General",
                entry.formattedSize(), entry.rating());
        JLabel subLbl = new JLabel(subText);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(Color.GRAY);
        titleBlock.add(subLbl);

        headerPanel.add(titleBlock, BorderLayout.CENTER);

        JButton civitaiWebBtn = new JButton("Open in Civitai ↗");
        civitaiWebBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        civitaiWebBtn.addActionListener(e -> openCivitaiWebPage());
        headerPanel.add(civitaiWebBtn, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // --- Content (Center) ---
        JPanel bodyPanel = new JPanel(new BorderLayout(16, 12));
        bodyPanel.setBorder(new EmptyBorder(0, 20, 12, 20));

        // Preview thumbnail (Left)
        JLabel previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(280, 360));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(new Color(60, 64, 75)));
        previewLabel.setText("Loading preview...");
        loadAsyncPreview(entry.thumbnailUrl(), previewLabel);
        bodyPanel.add(previewLabel, BorderLayout.WEST);

        // Details tabs / info (Center)
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));

        // Trigger words block (if available)
        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            JPanel triggerBlock = new JPanel(new BorderLayout(8, 6));
            triggerBlock.setBorder(BorderFactory.createTitledBorder("Trigger Words (LoRA)"));

            String joinedTriggers = String.join(", ", entry.triggerWords());
            JTextArea triggerArea = new JTextArea(joinedTriggers);
            triggerArea.setLineWrap(true);
            triggerArea.setWrapStyleWord(true);
            triggerArea.setEditable(false);
            triggerArea.setOpaque(false);
            triggerArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            JScrollPane triggerScroll = new JScrollPane(triggerArea);
            triggerScroll.setPreferredSize(new Dimension(300, 60));
            triggerBlock.add(triggerScroll, BorderLayout.CENTER);

            JButton copyTriggerBtn = new JButton("Copy Trigger Words", SvgIconFactory.get(AppIcon.CHECK, 14));
            copyTriggerBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(joinedTriggers), null);
                copyTriggerBtn.setText("Copied! ✅");
                Timer t = new Timer(2000, evt -> copyTriggerBtn.setText("Copy Trigger Words"));
                t.setRepeats(false);
                t.start();
            });
            triggerBlock.add(copyTriggerBtn, BorderLayout.SOUTH);

            detailsPanel.add(triggerBlock);
            detailsPanel.add(Box.createVerticalStrut(10));
        }

        // Description area
        JPanel descBlock = new JPanel(new BorderLayout());
        descBlock.setBorder(BorderFactory.createTitledBorder("Description"));

        JEditorPane descPane = new JEditorPane();
        descPane.setContentType("text/html");
        descPane.setEditable(false);
        descPane.setText(cleanHtmlDescription(entry.description()));
        descPane.setCaretPosition(0);

        JScrollPane descScroll = new JScrollPane(descPane);
        descBlock.add(descScroll, BorderLayout.CENTER);
        detailsPanel.add(descBlock);

        bodyPanel.add(detailsPanel, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);

        // --- Actions Bar (South) ---
        JPanel southBar = new JPanel(new BorderLayout(10, 0));
        southBar.setBorder(new EmptyBorder(10, 20, 16, 20));

        ModelFolder folder = ModelFolder.fromString(entry.type());
        String folderName = folder != null ? folder.getDefaultFolderName() : "models";
        JLabel pathInfo = new JLabel(String.format("Target: models/%s/%s", folderName, entry.filename()));
        pathInfo.setFont(new Font("Monospaced", Font.PLAIN, 11));
        pathInfo.setForeground(Color.GRAY);
        southBar.add(pathInfo, BorderLayout.WEST);

        JPanel buttonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonGroup.add(closeBtn);

        JButton downloadBtn;
        if (isInstalled) {
            downloadBtn = new JButton("Re-download Model", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        } else {
            downloadBtn = new JButton("Download to ComfyUI", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
            downloadBtn.setBackground(new Color(59, 130, 246));
        }
        downloadBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        downloadBtn.addActionListener(e -> {
            if (onDownload != null) {
                onDownload.accept(entry);
            }
            dispose();
        });
        buttonGroup.add(downloadBtn);

        southBar.add(buttonGroup, BorderLayout.EAST);
        add(southBar, BorderLayout.SOUTH);
    }

    private void openCivitaiWebPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://civitai.com/models/" + entry.id()));
            }
        } catch (Exception ex) {
            logger.warn("Could not launch web browser: {}", ex.getMessage());
            JOptionPane.showMessageDialog(this, "URL: https://civitai.com/models/" + entry.id(), "Civitai URL", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadAsyncPreview(String url, JLabel target) {
        if (url == null || url.isBlank()) {
            target.setText("No preview image available");
            return;
        }

        Thread.ofVirtual().name("Civitai-DetailPreview").start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<InputStream> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    try (InputStream in = res.body()) {
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) {
                            int targetW = 280;
                            int targetH = 360;
                            Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                            SwingUtilities.invokeLater(() -> {
                                target.setText(null);
                                target.setIcon(new ImageIcon(scaled));
                            });
                        }
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> target.setText("Preview unavailable"));
            }
        });
    }

    private String cleanHtmlDescription(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "<html><body style='font-family:sans-serif; color:gray;'><i>No description provided by creator.</i></body></html>";
        }
        return "<html><body style='font-family:sans-serif; margin:8px;'>" + rawHtml + "</body></html>";
    }
}
