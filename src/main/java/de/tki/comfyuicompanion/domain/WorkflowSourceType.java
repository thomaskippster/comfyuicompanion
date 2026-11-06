package de.tki.comfyuicompanion.domain;

import java.awt.Color;

/**
 * Enumeration of supported workflow source origins within the application.
 * Defines metadata such as display names and theme-consistent accent colors.
 *
 * @author Senior Java Developer
 */
public enum WorkflowSourceType {

    /**
     * Official curated workflows from ComfyUI registry.
     */
    COMFY_ORG("ComfyUI", new Color(59, 130, 246)),

    /**
     * Community workflows downloaded or browsed from Civitai.
     */
    CIVITAI("Civitai", new Color(245, 158, 11)),

    /**
     * Open source workflows hosted on HuggingFace Hub repositories.
     */
    HUGGING_FACE("HuggingFace", new Color(234, 179, 8)),

    /**
     * Locally managed workflows stored in the user's personal vault.
     */
    VAULT("My Vault", new Color(16, 185, 129));

    private final String displayName;
    private final Color badgeColor;

    WorkflowSourceType(String displayName, Color badgeColor) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    /**
     * Gets the human-readable display label for this source.
     *
     * @return display label
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the representative accent badge color for UI indicators.
     *
     * @return badge color
     */
    public Color getBadgeColor() {
        return badgeColor;
    }
}
