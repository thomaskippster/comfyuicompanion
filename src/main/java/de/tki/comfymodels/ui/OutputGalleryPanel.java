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

    public OutputGalleryPanel(ConfigService configService) {
        this.configService = configService;
        setLayout(new BorderLayout());
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        pathLabel = new JLabel("Output Directory: ");
        headerPanel.add(pathLabel, BorderLayout.CENTER);
        
        JButton refreshBtn = new JButton("Refresh 🔄");
        refreshBtn.addActionListener(e -> refresh());
        headerPanel.add(refreshBtn, BorderLayout.EAST);
        
        add(headerPanel, BorderLayout.NORTH);
        
        galleryPanel = new ScrollablePanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(galleryPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
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
        loadFiles(configService.getResolvedOutputDir());
        galleryPanel.revalidate();
        galleryPanel.repaint();
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
                galleryPanel.add(new JLabel("No images or videos found in the output directory."));
            } else {
                for (Path file : files) {
                    galleryPanel.add(createFileTile(file));
                }
            }
        } catch (Exception e) {
            galleryPanel.add(new JLabel("Error loading files: " + e.getMessage()));
        }
    }

    private JPanel createFileTile(Path file) {
        JPanel tile = new JPanel(new BorderLayout());
        tile.setPreferredSize(new Dimension(120, 140));
        tile.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        String name = file.getFileName().toString();
        
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
                        Image scaledImage = rawImage.getScaledInstance(110, 110, Image.SCALE_FAST);
                        ImageIcon icon = new ImageIcon(scaledImage);
                        SwingUtilities.invokeLater(() -> {
                            imageLabel.setText("");
                            imageLabel.setIcon(icon);
                        });
                    }
                } catch (Exception ignored) {}
            });
            
            tile.add(new JLabel(name, SwingConstants.CENTER), BorderLayout.SOUTH);
        }
        
        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showMediaViewer(file);
            }
        });
        
        return tile;
    }

    private void showMediaViewer(Path file) {
        try {
            Desktop.getDesktop().open(file.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open file in external viewer: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
