package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import java.awt.Component;

/**
 * Manages tab visibility and tab ordering within the main application window
 * based on user settings and feature toggles.
 */
public final class MainWindowTabManager {

    private MainWindowTabManager() {
        // Utility class
    }

    /**
     * Updates the main tab panel visibility and restores the previously selected tab if possible.
     *
     * @param uiComponents  the main UI components containing tab containers
     * @param configService the configuration service providing tab enablement states
     */
    public static void updateTabVisibility(MainWindowBuilder.MainWindowComponents uiComponents, ConfigService configService) {
        if (uiComponents == null || uiComponents.mainTabs == null || configService == null) {
            return;
        }
        Component selectedComp = uiComponents.mainTabs.getSelectedComponent();
        uiComponents.mainTabs.removeAll();

        if (uiComponents.dashboardPanel != null) {
            uiComponents.mainTabs.addTab("Dashboard", SvgIconFactory.get(AppIcon.DASHBOARD, 16), uiComponents.dashboardPanel);
        }
        if (uiComponents.downloadManagerPanel != null) {
            uiComponents.mainTabs.addTab("Download Manager", SvgIconFactory.get(AppIcon.DOWNLOAD_MANAGER, 16), uiComponents.downloadManagerPanel);
        }
        if (uiComponents.galleryPanel != null) {
            uiComponents.mainTabs.addTab("Gallery", SvgIconFactory.get(AppIcon.GALLERY, 16), uiComponents.galleryPanel);
        }
        if (configService.isBlueprintGalleryEnabled() && uiComponents.blueprintGalleryWrapper != null) {
            uiComponents.mainTabs.addTab("Blueprint Gallery", SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY, 16), uiComponents.blueprintGalleryWrapper);
        }
        if (configService.isPromptLabEnabled() && uiComponents.promptLabPanel != null) {
            uiComponents.mainTabs.addTab("Image Lab", SvgIconFactory.get(AppIcon.IMAGE_LAB, 16), uiComponents.promptLabPanel);
        }
        if (configService.isVideoArchitectEnabled() && uiComponents.videoArchitectWrapper != null) {
            uiComponents.mainTabs.addTab("Video Architect (Beta)", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, 16), uiComponents.videoArchitectWrapper);
        }
        if (uiComponents.settingsPanel != null) {
            uiComponents.mainTabs.addTab("Settings", SvgIconFactory.get(AppIcon.SETTINGS, 16), uiComponents.settingsPanel);
        }

        if (selectedComp != null) {
            int index = uiComponents.mainTabs.indexOfComponent(selectedComp);
            uiComponents.mainTabs.setSelectedIndex(index != -1 ? index : 0);
        }
    }
}
