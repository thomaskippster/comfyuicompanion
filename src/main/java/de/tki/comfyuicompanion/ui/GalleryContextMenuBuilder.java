package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.service.PngMetadataReaderService;
import de.tki.comfyuicompanion.ui.dialog.GenerationMetadataDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Builds and displays context popup menus for media items rendered within the output gallery.
 */
public class GalleryContextMenuBuilder {

    private static final Logger logger = LoggerFactory.getLogger(GalleryContextMenuBuilder.class);

    private final Component parentComponent;
    private final AssetBridgeCoordinator assetBridgeCoordinator;
    private final Consumer<Path> deleteAction;

    /**
     * Constructs a new GalleryContextMenuBuilder.
     *
     * @param parentComponent        parent component for modal dialog parenting
     * @param assetBridgeCoordinator coordinator for cross-tab asset operations
     * @param deleteAction           callback invoked when user requests single-file deletion
     */
    public GalleryContextMenuBuilder(Component parentComponent,
                                     AssetBridgeCoordinator assetBridgeCoordinator,
                                     Consumer<Path> deleteAction) {
        this.parentComponent = parentComponent;
        this.assetBridgeCoordinator = assetBridgeCoordinator;
        this.deleteAction = deleteAction;
    }

    /**
     * Builds and shows the context popup menu at the specified invoker coordinates.
     *
     * @param invoker target component that invoked the popup
     * @param file    target media path
     * @param x       X coordinate for display
     * @param y       Y coordinate for display
     */
    public void showPopupMenu(Component invoker, Path file, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        File f = file.toFile();
        String name = file.getFileName().toString().toLowerCase();
        boolean isPng = name.endsWith(".png");
        boolean isImage = isPng || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");

        if (isPng) {
            JMenuItem metaItem = new JMenuItem("Inspect Generation Metadata...", SvgIconFactory.get(AppIcon.QUICK_CHECK, 14));
            metaItem.setFont(new Font("SansSerif", Font.BOLD, 12));
            metaItem.addActionListener(e -> {
                Window owner = SwingUtilities.getWindowAncestor(parentComponent);
                if (assetBridgeCoordinator != null) {
                    assetBridgeCoordinator.showMetadataDialog(owner, f);
                } else {
                    var meta = new PngMetadataReaderService().extractMetadata(f);
                    new GenerationMetadataDialog(owner, f, meta, null).setVisible(true);
                }
            });
            menu.add(metaItem);
            menu.addSeparator();
        }

        if (isImage) {
            populateImageSubmenus(menu, f);
        }

        JMenuItem copyPathItem = new JMenuItem("Copy File Path", SvgIconFactory.get(AppIcon.FILE_TEXT, 14));
        copyPathItem.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(file.toAbsolutePath().toString()), null));
        menu.add(copyPathItem);

        JMenuItem openFolderItem = new JMenuItem("Open in File Explorer", SvgIconFactory.get(AppIcon.FOLDER, 14));
        openFolderItem.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    File parent = f.getParentFile();
                    if (parent != null && parent.exists()) {
                        Desktop.getDesktop().open(parent);
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to open file explorer: {}", ex.getMessage());
            }
        });
        menu.add(openFolderItem);

        menu.addSeparator();

        JMenuItem deleteItem = new JMenuItem("Delete File", SvgIconFactory.get(AppIcon.DELETE, 14));
        deleteItem.addActionListener(e -> {
            if (deleteAction != null) {
                deleteAction.accept(file);
            }
        });
        menu.add(deleteItem);

        menu.show(invoker, x, y);
    }

    private void populateImageSubmenus(JPopupMenu menu, File f) {
        JMenu imageLabMenu = new JMenu("Send to Image Lab");
        imageLabMenu.setIcon(SvgIconFactory.get(AppIcon.IMAGE_LAB, 14));

        JMenuItem remixItem = new JMenuItem("Apply Prompt & Sampling Parameters", SvgIconFactory.get(AppIcon.LAUNCH, 12));
        remixItem.addActionListener(e -> {
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.sendToImageLab(f, true);
            }
        });
        imageLabMenu.add(remixItem);

        JMenuItem previewItem = new JMenuItem("Load as Canvas Preview", SvgIconFactory.get(AppIcon.GALLERY, 12));
        previewItem.addActionListener(e -> {
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.sendToImageLab(f, false);
            }
        });
        imageLabMenu.add(previewItem);
        menu.add(imageLabMenu);

        JMenu videoMenu = new JMenu("Send to Video Architect");
        videoMenu.setIcon(SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, 14));

        JMenuItem speakerItem = new JMenuItem("Set as Start / Speaker Image", SvgIconFactory.get(AppIcon.LAUNCH, 12));
        speakerItem.addActionListener(e -> {
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.sendToVideoArchitectAsSpeaker(f);
            }
        });
        videoMenu.add(speakerItem);

        JMenuItem newSceneItem = new JMenuItem("Add as New Video Scene", SvgIconFactory.get(AppIcon.ADD, 12));
        newSceneItem.addActionListener(e -> {
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.sendToVideoArchitectAsNewScene(f, null);
            }
        });
        videoMenu.add(newSceneItem);

        JMenuItem keyframeItem = new JMenuItem("Assign to Selected Scene", SvgIconFactory.get(AppIcon.EDIT, 12));
        keyframeItem.addActionListener(e -> {
            if (assetBridgeCoordinator != null) {
                assetBridgeCoordinator.assignKeyframeToSelectedScene(f);
            }
        });
        videoMenu.add(keyframeItem);

        menu.add(videoMenu);
        menu.addSeparator();
    }
}
