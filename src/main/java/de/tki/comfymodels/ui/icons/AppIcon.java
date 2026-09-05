package de.tki.comfymodels.ui.icons;

/**
 * Semantic enum for application vector icons.
 * Maps application actions, categories, and navigation elements to SVG resource paths.
 */
public enum AppIcon {
    // Navigation Tabs
    DASHBOARD("icons/svg/dashboard.svg"),
    DOWNLOAD_MANAGER("icons/svg/download.svg"),
    GALLERY("icons/svg/image.svg"),
    BLUEPRINT_GALLERY("icons/svg/blueprint.svg"),
    IMAGE_LAB("icons/svg/wand.svg"),
    VIDEO_ARCHITECT("icons/svg/video.svg"),
    SETTINGS("icons/svg/settings.svg"),

    // Storage Tools
    QUICK_CHECK("icons/svg/search.svg"),
    STORAGE_OPTIMIZER("icons/svg/storage.svg"),
    ARCHIVE("icons/svg/archive.svg"),
    DIAGNOSTICS("icons/svg/diagnostics.svg"),

    // Operations & Actions
    LAUNCH("icons/svg/rocket.svg"),
    RESTART("icons/svg/refresh.svg"),
    REFRESH("icons/svg/refresh.svg"),
    BROWSER("icons/svg/globe.svg"),
    SETUP("icons/svg/wrench.svg"),
    ADD("icons/svg/plus.svg"),
    REMOVE("icons/svg/minus.svg"),
    EDIT("icons/svg/edit.svg"),
    DELETE("icons/svg/trash.svg"),
    TRASH("icons/svg/trash.svg"),
    PLAY("icons/svg/play.svg"),
    SEND("icons/svg/rocket.svg"),
    SEARCH("icons/svg/search.svg"),
    DOWNLOAD("icons/svg/download.svg"),
    SUGGEST("icons/svg/sparkles.svg"),
    CLOSE("icons/svg/x.svg"),
    CHECK("icons/svg/check.svg"),
    FOLDER("icons/svg/folder.svg"),
    FILE_TEXT("icons/svg/file-text.svg"),
    GRAPH("icons/svg/chart.svg"),
    FILTER("icons/svg/sliders.svg"),
    PALETTE("icons/svg/palette.svg"),
    AUDIO("icons/svg/mic.svg"),
    AGENT("icons/svg/bot.svg"),
    USER("icons/svg/user.svg"),
    LINK("icons/svg/link.svg"),
    TERMINAL("icons/svg/terminal.svg"),
    LINUX("icons/svg/linux.svg"),
    STOP("icons/svg/square.svg"),
    HELP("icons/svg/help-circle.svg"),
    AI("icons/svg/bot.svg"),
    CIRCLE("icons/svg/circle.svg");

    private final String resourcePath;

    AppIcon(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}
