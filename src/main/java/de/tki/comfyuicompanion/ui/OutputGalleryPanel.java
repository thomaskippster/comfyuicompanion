package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.ThumbnailCache;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Panel for displaying output gallery.
 */
public class OutputGalleryPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(OutputGalleryPanel.class);
    private final ConfigService configService;
    private final JPanel galleryPanel;
    private final JLabel pathLabel;
    
    private final Set<Path> selectedFiles = new HashSet<>();
    private final List<Path> loadedFiles = new ArrayList<>();
    private final Map<Path, JPanel> tileMap = new HashMap<>();
    private final Map<Path, JCheckBox> checkboxMap = new HashMap<>();
    
    private final de.tki.comfyuicompanion.service.SafePathValidator safePathValidator;
    private AssetBridgeCoordinator assetBridgeCoordinator;
    private JButton deleteBtn;
    private JButton selectAllBtn;
    private JButton clearBtn;

    /**
     * Constructs a new OutputGalleryPanel.
     * @param configService config service
     */
    public OutputGalleryPanel(ConfigService configService) {
        this(configService, new de.tki.comfyuicompanion.service.SafePathValidator(), null);
    }

    /**
     * Constructs a new OutputGalleryPanel with safe path validator.
     * @param configService config service
     * @param safePathValidator path validator
     */
    public OutputGalleryPanel(ConfigService configService, de.tki.comfyuicompanion.service.SafePathValidator safePathValidator) {
        this(configService, safePathValidator, null);
    }

    /**
     * Constructs a new OutputGalleryPanel with safe path validator and asset bridge coordinator.
     * @param configService config service
     * @param safePathValidator path validator
     * @param assetBridgeCoordinator asset bridge coordinator
     */
    public OutputGalleryPanel(ConfigService configService, de.tki.comfyuicompanion.service.SafePathValidator safePathValidator, AssetBridgeCoordinator assetBridgeCoordinator) {
        this.configService = configService;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new de.tki.comfyuicompanion.service.SafePathValidator();
        this.assetBridgeCoordinator = assetBridgeCoordinator;
        setLayout(new BorderLayout());
        setOpaque(false);
        
        de.tki.comfyuicompanion.ui.CardPanel headerPanel = new de.tki.comfyuicompanion.ui.CardPanel();
        headerPanel.setLayout(new BorderLayout());
        
        pathLabel = new JLabel("Output Directory: ");
        headerPanel.add(pathLabel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        
        selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> selectAll());
        buttonPanel.add(selectAllBtn);
        
        clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clearSelection());
        buttonPanel.add(clearBtn);
        
        deleteBtn = new JButton("Delete Selected (0)", SvgIconFactory.get(AppIcon.DELETE));
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> deleteSelectedFiles());
        buttonPanel.add(deleteBtn);

        JButton aiActBtn = new JButton("EU AI Act Compliance", SvgIconFactory.get(AppIcon.CHECK, 14));
        aiActBtn.setToolTipText("Export selected images with machine-readable Art. 50 EU AI Act metadata and compliance markings");
        aiActBtn.addActionListener(e -> openAiActExportDialog());
        buttonPanel.add(aiActBtn);
        
        JButton refreshBtn = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH));
        refreshBtn.addActionListener(e -> refresh());
        buttonPanel.add(refreshBtn);
        
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(headerPanel, BorderLayout.NORTH);
        
        galleryPanel = new ScrollablePanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        galleryPanel.setOpaque(false);
        
        JScrollPane scrollPane = new JScrollPane(galleryPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                refresh();
            }
        });

        refresh();
    }

    /**
     * Refreshes the gallery panel.
     */
    public void refresh() {
        galleryPanel.removeAll();
        tileMap.clear();
        checkboxMap.clear();
        loadedFiles.clear();
        selectedFiles.clear();
        updateDeleteButton();
        
        loadFiles(configService.getResolvedOutputDir());
        galleryPanel.revalidate();
        galleryPanel.repaint();
    }

    private void updateDeleteButton() {
        if (deleteBtn != null) {
            int count = selectedFiles.size();
            deleteBtn.setText("Delete Selected (" + count + ")");
            deleteBtn.setEnabled(count > 0);
            if (count > 0) {
                deleteBtn.putClientProperty("Button.background", new Color(180, 50, 50));
                deleteBtn.putClientProperty("Button.foreground", Color.WHITE);
            } else {
                deleteBtn.putClientProperty("Button.background", null);
                deleteBtn.putClientProperty("Button.foreground", null);
            }
        }
    }

    private void selectAll() {
        selectedFiles.addAll(loadedFiles);
        refreshTileVisuals();
        updateDeleteButton();
    }

    private void clearSelection() {
        selectedFiles.clear();
        refreshTileVisuals();
        updateDeleteButton();
    }

    private void refreshTileVisuals() {
        for (Path file : loadedFiles) {
            JPanel tile = tileMap.get(file);
            JCheckBox cb = checkboxMap.get(file);
            if (tile != null) {
                updateTileBorder(tile, file);
                tile.repaint();
            }
            if (cb != null) {
                cb.setSelected(selectedFiles.contains(file));
            }
        }
    }

    private void updateTileBorder(JPanel tile, Path file) {
        if (selectedFiles.contains(file)) {
            tile.setBorder(BorderFactory.createLineBorder(new Color(0, 204, 204), 2));
        } else {
            Color cardBorder = UIManager.getColor("Card.border");
            if (cardBorder == null) cardBorder = UIManager.getColor("Component.borderColor");
            if (cardBorder == null) cardBorder = Color.GRAY;
            tile.setBorder(BorderFactory.createLineBorder(cardBorder, 1));
        }
    }

    private void deleteSelectedFiles() {
        int count = selectedFiles.size();
        if (count == 0) return;
        
        String message = "Are you sure you want to permanently delete " + count + " selected file(s)?";
        int confirm = JOptionPane.showConfirmDialog(this, message, "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        int deletedCount = 0;
        int failedCount = 0;
        StringBuilder failedFiles = new StringBuilder();
        
        String outDirStr = configService != null ? configService.getResolvedOutputDir() : "output";
        Path baseOutputDir = Path.of(outDirStr != null && !outDirStr.isBlank() ? outDirStr : "output").toAbsolutePath().normalize();

        for (Path file : selectedFiles) {
            try {
                if (safePathValidator != null) {
                    safePathValidator.validateWithinBase(baseOutputDir, file);
                }
                Files.deleteIfExists(file);
                deletedCount++;
            } catch (java.io.IOException | RuntimeException ex) {
                failedCount++;
                failedFiles.append(file.getFileName().toString()).append("\n");
            }
        }
        
        selectedFiles.clear();
        refresh();
        
        if (failedCount > 0) {
            JOptionPane.showMessageDialog(this, 
                "Successfully deleted " + deletedCount + " file(s).\nFailed to delete " + failedCount + " file(s):\n" + failedFiles.toString(),
                "Deletion Summary", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Successfully deleted " + deletedCount + " file(s).", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openAiActExportDialog() {
        List<Path> targets = selectedFiles.isEmpty() ? new ArrayList<>(loadedFiles) : new ArrayList<>(selectedFiles);
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No images available for compliance export.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        GalleryComplianceExportDialog dlg = new GalleryComplianceExportDialog(owner, targets, new de.tki.comfyuicompanion.service.impl.AiActComplianceService(), configService);
        dlg.setVisible(true);
    }

    private void loadFiles(String outputDir) {
        pathLabel.setText("Output Directory: " + outputDir);
        File dir = new File(outputDir);
        if (!dir.exists() || !dir.isDirectory()) {
            galleryPanel.add(new JLabel("Output directory not found. Generate an image in ComfyUI first."));
            return;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath(), 1)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".mp4");
                })
                .sorted((p1, p2) -> Long.compare(p2.toFile().lastModified(), p1.toFile().lastModified()))
                .collect(Collectors.toList());

            if (files.isEmpty()) {
                galleryPanel.add(new JLabel("No images or videos found in output directory."));
            } else {
                for (Path file : files) {
                    loadedFiles.add(file);
                    JPanel tile = createFileTile(file);
                    tileMap.put(file, tile);
                    galleryPanel.add(tile);
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            galleryPanel.add(new JLabel("Error loading files: " + e.getMessage()));
        }
    }

    private JPanel createFileTile(Path file) {
        de.tki.comfyuicompanion.ui.CardPanel tile = new de.tki.comfyuicompanion.ui.CardPanel() {
            @Override
            public void updateUI() {
                super.updateUI();
                updateTileBorder(this, file);
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (selectedFiles.contains(file)) {
                    g.setColor(new Color(0, 120, 215, 30));
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
            }
        };
        tile.setLayout(new BorderLayout());
        tile.setCornerRadius(12);
        tile.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        tile.setPreferredSize(new Dimension(130, 160));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        String name = file.getFileName().toString();
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        
        JCheckBox selectCheck = new JCheckBox();
        selectCheck.setOpaque(false);
        selectCheck.setSelected(selectedFiles.contains(file));
        selectCheck.setFocusable(false);
        selectCheck.addActionListener(e -> {
            if (selectCheck.isSelected()) {
                selectedFiles.add(file);
            } else {
                selectedFiles.remove(file);
            }
            updateTileBorder(tile, file);
            tile.repaint();
            updateDeleteButton();
        });
        topPanel.add(selectCheck, BorderLayout.WEST);
        tile.add(topPanel, BorderLayout.NORTH);
        
        checkboxMap.put(file, selectCheck);
        
        if (name.toLowerCase().endsWith(".mp4")) {
            JLabel videoLabel = new JLabel("<html><center>" + name + "</center></html>", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, 32), SwingConstants.CENTER);
            videoLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
            videoLabel.setHorizontalTextPosition(SwingConstants.CENTER);
            tile.add(videoLabel, BorderLayout.CENTER);
        } else {
            JLabel imageLabel = new JLabel("Loading...", SvgIconFactory.get(AppIcon.REFRESH, 16), SwingConstants.CENTER);
            tile.add(imageLabel, BorderLayout.CENTER);
            de.tki.comfyuicompanion.util.ThumbnailCache.loadAsync(file.toFile(), 120, 120, icon -> {
                imageLabel.setText("");
                imageLabel.setIcon(icon);
            });
            
            JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            tile.add(nameLabel, BorderLayout.SOUTH);
        }
        
        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTileContextMenu(tile, file, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTileContextMenu(tile, file, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    showTileContextMenu(tile, file, e.getX(), e.getY());
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.getClickCount() == 2) {
                        showMediaViewer(file);
                    } else {
                        if (selectedFiles.contains(file)) {
                            selectedFiles.remove(file);
                            selectCheck.setSelected(false);
                        } else {
                            selectedFiles.add(file);
                            selectCheck.setSelected(true);
                        }
                        updateTileBorder(tile, file);
                        tile.repaint();
                        updateDeleteButton();
                    }
                }
            }
        });
        
        updateTileBorder(tile, file);
        return tile;
    }

    public void setAssetBridgeCoordinator(AssetBridgeCoordinator assetBridgeCoordinator) {
        this.assetBridgeCoordinator = assetBridgeCoordinator;
    }

    private void showTileContextMenu(Component invoker, Path file, int x, int y) {
        new GalleryContextMenuBuilder(this, assetBridgeCoordinator, target -> {
            selectedFiles.clear();
            selectedFiles.add(target);
            deleteSelectedFiles();
        }).showPopupMenu(invoker, file, x, y);
    }

    private void showMediaViewer(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        Window owner = SwingUtilities.getWindowAncestor(this);

        if (name.endsWith(".mp4")) {
            try {
                VideoPreviewDialog previewDialog = new VideoPreviewDialog(owner, file.getFileName().toString(), file.toFile(),
                        configService != null ? configService.isDarkMode() : ThemeManager.isDarkMode());
                previewDialog.setVisible(true);
                return;
            } catch (Throwable t) {
                logger.debug("Internal VideoPreviewDialog unavailable, falling back to desktop player: {}", t.getMessage());
            }
        } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                try {
                    Desktop.getDesktop().open(file.toFile());
                    return;
                } catch (Exception ex) {
                    logger.debug("Desktop open failed: {}", ex.getMessage());
                }
            }
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.showMetadataDialog(owner, file.toFile());
                return;
            }
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                JOptionPane.showMessageDialog(this,
                        "Opening external viewer is not supported on this system.\nFile: " + file.toAbsolutePath(),
                        "Notice", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (java.io.IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open file in external viewer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
    }
}
