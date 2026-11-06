package de.tki.comfyuicompanion.ui.vault;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Top ingestion zone panel supporting drag & drop, clipboard paste (Ctrl+V),
 * and JFileChooser browsing for ComfyUI workflow files (.json) and images with embedded workflows (.png, .webp).
 */
public class VaultIngestionDropZonePanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(VaultIngestionDropZonePanel.class);

    private final Consumer<File> onFileImportRequested;
    private final Consumer<String> onRawJsonPasted;
    private boolean isDragOver = false;
    private JLabel lblTitle;
    private JLabel lblSubtitle;

    /**
     * Constructs a new ingestion drop zone panel.
     *
     * @param onFileImportRequested callback invoked when a valid file is dropped, chosen, or pasted
     * @param onRawJsonPasted       callback invoked when raw JSON text is pasted
     */
    public VaultIngestionDropZonePanel(Consumer<File> onFileImportRequested, Consumer<String> onRawJsonPasted) {
        this.onFileImportRequested = onFileImportRequested;
        this.onRawJsonPasted = onRawJsonPasted;

        setLayout(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 12, 16));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        setPreferredSize(new Dimension(1100, 68));

        buildUI();
        setupDragAndDrop();
        setupKeyboardPaste();
    }

    private void buildUI() {
        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setOpaque(false);

        boolean dark = de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        lblTitle = new JLabel(
                "Drop Workflow Files (.json) or Images with Embedded Workflow (.png, .webp) Here  —  or Paste (Ctrl+V)",
                SvgIconFactory.get(AppIcon.DOWNLOAD, 16),
                SwingConstants.LEFT
        );
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblTitle.setForeground(dark ? new Color(228, 230, 240) : new Color(15, 23, 42));

        lblSubtitle = new JLabel(
                "Automatically extracts embedded ComfyUI workflow JSON & preview thumbnail, verifies local models, and resolves missing model downloads."
        );
        lblSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblSubtitle.setForeground(dark ? new Color(140, 150, 175) : new Color(100, 116, 139));

        textBlock.add(lblTitle);
        textBlock.add(Box.createVerticalStrut(4));
        textBlock.add(lblSubtitle);
        add(textBlock, BorderLayout.WEST);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonsPanel.setOpaque(false);

        JButton btnPaste = new JButton("Paste Image / Workflow (Ctrl+V)", SvgIconFactory.get(AppIcon.FILE_TEXT, 14));
        btnPaste.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnPaste.putClientProperty("Button.arc", 10);
        btnPaste.addActionListener(e -> handleClipboardPaste());

        JButton btnFileChooser = new JButton("Browse Files... (.json, .png, .webp)", SvgIconFactory.get(AppIcon.FOLDER, 14));
        btnFileChooser.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnFileChooser.setBackground(new Color(0, 150, 136));
        btnFileChooser.setForeground(Color.WHITE);
        btnFileChooser.putClientProperty("Button.arc", 10);
        btnFileChooser.addActionListener(e -> openFileChooser());

        buttonsPanel.add(btnPaste);
        buttonsPanel.add(btnFileChooser);
        add(buttonsPanel, BorderLayout.EAST);
    }

    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                isDragOver = true;
                repaint();
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                isDragOver = false;
                repaint();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                isDragOver = false;
                repaint();
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable t = dtde.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        if (files != null && !files.isEmpty() && onFileImportRequested != null) {
                            for (File f : files) {
                                onFileImportRequested.accept(f);
                            }
                            dtde.dropComplete(true);
                            return;
                        }
                    }
                    dtde.dropComplete(false);
                } catch (Exception ex) {
                    logger.error("Drop failed: {}", ex.getMessage());
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void setupKeyboardPaste() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        KeyStroke ks = KeyStroke.getKeyStroke("control V");
        im.put(ks, "pasteWorkflow");
        am.put("pasteWorkflow", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                handleClipboardPaste();
            }
        });
    }

    private void openFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select ComfyUI Workflow or Image with Embedded Workflow");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "ComfyUI Workflows & Images (*.json, *.png, *.webp)", "json", "png", "webp"
        ));

        int res = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
        if (res == JFileChooser.APPROVE_OPTION && onFileImportRequested != null) {
            File[] selected = chooser.getSelectedFiles();
            if (selected != null) {
                for (File f : selected) {
                    onFileImportRequested.accept(f);
                }
            }
        }
    }

    private void handleClipboardPaste() {
        try {
            Transferable clipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (clipboard == null) return;

            if (clipboard.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) clipboard.getTransferData(DataFlavor.javaFileListFlavor);
                if (files != null && onFileImportRequested != null) {
                    for (File f : files) onFileImportRequested.accept(f);
                    return;
                }
            }

            if (clipboard.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) clipboard.getTransferData(DataFlavor.imageFlavor);
                if (img != null) {
                    File tempPng = File.createTempFile("pasted_workflow_", ".png");
                    tempPng.deleteOnExit();
                    BufferedImage bImg = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = bImg.createGraphics();
                    g2.drawImage(img, 0, 0, null);
                    g2.dispose();
                    ImageIO.write(bImg, "png", tempPng);
                    if (onFileImportRequested != null) onFileImportRequested.accept(tempPng);
                    return;
                }
            }

            if (clipboard.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) clipboard.getTransferData(DataFlavor.stringFlavor);
                if (text != null && text.trim().startsWith("{") && text.contains("\"") && onRawJsonPasted != null) {
                    onRawJsonPasted.accept(text);
                }
            }
        } catch (Exception ex) {
            logger.warn("Clipboard paste inspection failed: {}", ex.getMessage());
        }
    }

    public void updateTheme(boolean darkMode) {
        if (lblTitle != null) {
            lblTitle.setForeground(darkMode ? new Color(228, 230, 240) : new Color(15, 23, 42));
        }
        if (lblSubtitle != null) {
            lblSubtitle.setForeground(darkMode ? new Color(140, 150, 175) : new Color(100, 116, 139));
        }
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateTheme(de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean dark = de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        Color bg = dark
                ? (isDragOver ? new Color(30, 45, 65) : new Color(22, 28, 40))
                : (isDragOver ? new Color(224, 242, 254) : new Color(255, 255, 255));
        Color border = dark
                ? (isDragOver ? new Color(0, 240, 220) : new Color(0, 180, 165))
                : (isDragOver ? new Color(14, 165, 233) : new Color(13, 148, 136));

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

        Stroke dashed = new BasicStroke(isDragOver ? 2.0f : 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6, 4}, 0);
        g2.setStroke(dashed);
        g2.setColor(border);
        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
        g2.dispose();
    }
}
