package de.tki.comfymodels.ui;

import de.tki.comfymodels.service.impl.ConfigService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * WrapLayout allows components to be placed in a container and wrapped 
 * to the next line when they don't fit.
 */
class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
            
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);

            int x = 0, y = 0, height = 0;
            for (Component c : target.getComponents()) {
                if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    if (x + d.width > maxWidth) {
                        x = 0; y += height + getVgap(); height = 0;
                    }
                    x += d.width + getHgap();
                    height = Math.max(height, d.height);
                }
            }
            return new Dimension(targetWidth, y + height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
}

class ScrollablePanel extends JPanel implements Scrollable {
    public ScrollablePanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 60;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

public class OutputGalleryPanel extends JPanel {
    private final ConfigService configService;
    private final JPanel galleryPanel;
    private final JLabel pathLabel;
    
    private final Set<Path> selectedFiles = new HashSet<>();
    private final List<Path> loadedFiles = new ArrayList<>();
    private final Map<Path, JPanel> tileMap = new HashMap<>();
    private final Map<Path, JCheckBox> checkboxMap = new HashMap<>();
    
    private JButton deleteBtn;
    private JButton selectAllBtn;
    private JButton clearBtn;

    public OutputGalleryPanel(ConfigService configService) {
        this.configService = configService;
        setLayout(new BorderLayout());
        setOpaque(false);
        
        de.tki.comfymodels.ui.CardPanel headerPanel = new de.tki.comfymodels.ui.CardPanel();
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
        
        deleteBtn = new JButton("Delete Selected (0) 🗑️");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> deleteSelectedFiles());
        buttonPanel.add(deleteBtn);
        
        JButton refreshBtn = new JButton("Refresh 🔄");
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
            deleteBtn.setText("Delete Selected (" + count + ") 🗑️");
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
        
        for (Path file : selectedFiles) {
            try {
                Files.deleteIfExists(file);
                deletedCount++;
            } catch (Exception ex) {
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
        } catch (Exception e) {
            galleryPanel.add(new JLabel("Error loading files: " + e.getMessage()));
        }
    }

    private JPanel createFileTile(Path file) {
        de.tki.comfymodels.ui.CardPanel tile = new de.tki.comfymodels.ui.CardPanel() {
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
            tile.add(new JLabel("<html><center>🎥<br>" + name + "</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);
        } else {
            JLabel imageLabel = new JLabel("⌛ Loading...", SwingConstants.CENTER);
            tile.add(imageLabel, BorderLayout.CENTER);
            
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    ImageIcon rawIcon = new ImageIcon(file.toString());
                    Image rawImage = rawIcon.getImage();
                    if (rawImage != null) {
                        Image scaledImage = rawImage.getScaledInstance(120, 120, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaledImage);
                        SwingUtilities.invokeLater(() -> {
                            imageLabel.setText("");
                            imageLabel.setIcon(icon);
                        });
                    }
                } catch (Exception ignored) {}
            });
            
            JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            tile.add(nameLabel, BorderLayout.SOUTH);
        }
        
        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
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

    private void showMediaViewer(Path file) {
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to open file in external viewer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
