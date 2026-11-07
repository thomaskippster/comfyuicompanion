package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.CardPanel;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Visual asset picker dialog presenting a responsive thumbnail grid of recent generations
 * from the ComfyUI output directory. Allows 1-click selection of images for video keyframes,
 * scene start images, speaker references, or remixing.
 */
public class RecentGenerationsAssetPickerDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(RecentGenerationsAssetPickerDialog.class);

    private final ConfigService configService;
    private final JPanel gridPanel;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final JButton selectBtn;

    private final List<File> allFiles = new ArrayList<>();
    private File selectedFile = null;
    private JPanel selectedTile = null;

    public RecentGenerationsAssetPickerDialog(Window owner, ConfigService configService, String dialogTitle) {
        super(owner, dialogTitle != null ? dialogTitle : "Select Image from Recent Generations", ModalityType.APPLICATION_MODAL);
        this.configService = configService;

        setSize(780, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // Header with search & browse
        CardPanel headerCard = new CardPanel();
        headerCard.setLayout(new BorderLayout(10, 10));
        headerCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);

        JLabel searchIcon = new JLabel(SvgIconFactory.get(AppIcon.SEARCH, 16));
        searchRow.add(searchIcon, BorderLayout.WEST);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search recent generation files...");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterThumbnails(searchField.getText().trim());
            }
        });
        searchRow.add(searchField, BorderLayout.CENTER);

        JButton browseExternalBtn = new JButton("Browse File...", SvgIconFactory.get(AppIcon.FOLDER, 14));
        browseExternalBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        browseExternalBtn.addActionListener(e -> onBrowseExternal());
        searchRow.add(browseExternalBtn, BorderLayout.EAST);

        headerCard.add(searchRow, BorderLayout.CENTER);
        add(headerCard, BorderLayout.NORTH);

        // Center Grid
        gridPanel = new ScrollableGridPanel(new DialogWrapLayout(FlowLayout.LEFT, 10, 10));
        gridPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        add(scrollPane, BorderLayout.CENTER);

        // Footer
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(new EmptyBorder(8, 16, 14, 16));

        statusLabel = new JLabel("Loading recent generations...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footerPanel.add(statusLabel, BorderLayout.WEST);

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnGroup.setOpaque(false);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            selectedFile = null;
            dispose();
        });
        btnGroup.add(cancelBtn);

        selectBtn = new JButton("Use Selected Image", SvgIconFactory.get(AppIcon.CHECK, 14));
        selectBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        selectBtn.setEnabled(false);
        selectBtn.putClientProperty("Button.background", ThemeManager.getAccentColor());
        selectBtn.putClientProperty("Button.foreground", Color.WHITE);
        selectBtn.addActionListener(e -> {
            if (selectedFile != null) {
                dispose();
            }
        });
        btnGroup.add(selectBtn);

        footerPanel.add(btnGroup, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);

        loadRecentFiles();
    }

    /**
     * @return the selected file, or {@code null} if cancelled
     */
    public File getSelectedFile() {
        return selectedFile;
    }

    private void onBrowseExternal() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Image File");
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp)", "png", "jpg", "jpeg", "webp"));
        if (configService != null && configService.getResolvedOutputDir() != null) {
            File dir = new File(configService.getResolvedOutputDir());
            if (dir.exists()) chooser.setCurrentDirectory(dir);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            this.selectedFile = chooser.getSelectedFile();
            dispose();
        }
    }

    private void loadRecentFiles() {
        String outputDirStr = configService != null ? configService.getResolvedOutputDir() : "output";
        File dir = new File(outputDirStr != null && !outputDirStr.isBlank() ? outputDirStr : "output");
        if (!dir.exists() || !dir.isDirectory()) {
            statusLabel.setText("Output directory not found: " + dir.getAbsolutePath());
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            List<File> list = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(dir.toPath(), 1)) {
                list = stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
                        })
                        .sorted((p1, p2) -> Long.compare(p2.toFile().lastModified(), p1.toFile().lastModified()))
                        .limit(60)
                        .map(Path::toFile)
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                log.warn("Failed scanning output directory for assets: {}", ex.getMessage());
            }
            return list;
        }).thenAccept(files -> SwingUtilities.invokeLater(() -> {
            allFiles.clear();
            allFiles.addAll(files);
            filterThumbnails("");
            statusLabel.setText(allFiles.size() + " recent generation(s) found.");
        }));
    }

    private void filterThumbnails(String query) {
        gridPanel.removeAll();
        selectedFile = null;
        selectedTile = null;
        selectBtn.setEnabled(false);

        String lcQuery = query.toLowerCase();
        List<File> matched = allFiles.stream()
                .filter(f -> lcQuery.isEmpty() || f.getName().toLowerCase().contains(lcQuery))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            JLabel emptyLbl = new JLabel("No matching generation images found.");
            emptyLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
            gridPanel.add(emptyLbl);
        } else {
            for (File file : matched) {
                JPanel tile = createTile(file);
                gridPanel.add(tile);
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JPanel createTile(File file) {
        CardPanel tile = new CardPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (file.equals(selectedFile)) {
                    g.setColor(new Color(0, 180, 215, 45));
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
            }
        };
        tile.setLayout(new BorderLayout(4, 4));
        tile.setCornerRadius(10);
        tile.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        tile.setPreferredSize(new Dimension(135, 165));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel thumbLabel = new JLabel("Loading...", SwingConstants.CENTER);
        thumbLabel.setPreferredSize(new Dimension(120, 110));
        tile.add(thumbLabel, BorderLayout.CENTER);

        CompletableFuture.runAsync(() -> {
            try {
                if (file.exists() && file.length() > 0) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                    ImageIcon rawIcon = new ImageIcon(bytes);
                    Image raw = rawIcon.getImage();
                    if (raw != null) {
                        Image scaled = raw.getScaledInstance(120, 110, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaled);
                        SwingUtilities.invokeLater(() -> {
                            thumbLabel.setText("");
                            thumbLabel.setIcon(icon);
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        });

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel nameLbl = new JLabel(truncate(file.getName(), 16), SwingConstants.CENTER);
        nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        nameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLbl.setToolTipText(file.getName());
        infoPanel.add(nameLbl);

        String dateStr = new SimpleDateFormat("MM-dd HH:mm").format(new Date(file.lastModified()));
        JLabel dateLbl = new JLabel(dateStr, SwingConstants.CENTER);
        dateLbl.setFont(new Font("SansSerif", Font.PLAIN, 9));
        dateLbl.setForeground(Color.GRAY);
        dateLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.add(dateLbl);

        tile.add(infoPanel, BorderLayout.SOUTH);

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (selectedTile != null) {
                    selectedTile.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                    selectedTile.repaint();
                }
                selectedFile = file;
                selectedTile = tile;
                tile.setBorder(BorderFactory.createLineBorder(new Color(0, 204, 204), 2));
                tile.repaint();
                selectBtn.setEnabled(true);
                statusLabel.setText("Selected: " + file.getName());

                if (e.getClickCount() == 2) {
                    dispose();
                }
            }
        });

        return tile;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }

    private static class ScrollableGridPanel extends JPanel implements javax.swing.Scrollable {
        public ScrollableGridPanel(LayoutManager layout) { super(layout); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 72; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static class DialogWrapLayout extends FlowLayout {
        public DialogWrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container target) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth <= 0 && target.getParent() != null) {
                    targetWidth = target.getParent().getWidth();
                }
                if (targetWidth <= 0) targetWidth = 720;
                Insets insets = target.getInsets();
                int hInsets = insets != null ? insets.left + insets.right : 0;
                int vInsets = insets != null ? insets.top + insets.bottom : 0;
                int maxWidth = Math.max(1, targetWidth - (hInsets + getHgap() * 2));

                int x = 0, y = 0, height = 0;
                for (Component c : target.getComponents()) {
                    if (c.isVisible()) {
                        Dimension d = c.getPreferredSize();
                        if (x + d.width > maxWidth && x > 0) {
                            x = 0;
                            y += height + getVgap();
                            height = 0;
                        }
                        x += d.width + getHgap();
                        height = Math.max(height, d.height);
                    }
                }
                return new Dimension(targetWidth, y + height + vInsets + getVgap() * 2);
            }
        }
    }
}
