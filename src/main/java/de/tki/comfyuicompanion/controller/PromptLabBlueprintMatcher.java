package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;

import java.util.List;
import java.util.Locale;

/**
 * Stateless helper that matches and classifies Prompt Lab blueprint entries by name and category.
 * Contains no UI or I/O dependencies, which keeps it trivially unit-testable.
 */
public class PromptLabBlueprintMatcher {

    /**
     * Finds a matching blueprint entry in the gallery tab matching the given selected name.
     *
     * @param blueprintGalleryTab the blueprint gallery tab
     * @param selectedName        the selected model or blueprint name
     * @return the matched BlueprintEntry, or null if no match was found
     */
    public BlueprintGalleryTab.BlueprintEntry findBlueprintMatch(BlueprintGalleryTab blueprintGalleryTab, String selectedName) {
        if (blueprintGalleryTab == null || selectedName == null || selectedName.isBlank()) {
            return null;
        }
        List<BlueprintGalleryTab.BlueprintEntry> blueprints = blueprintGalleryTab.getAvailableBlueprints();
        for (BlueprintGalleryTab.BlueprintEntry e : blueprints) {
            if (e.name.equalsIgnoreCase(selectedName) || (e.filename != null && e.filename.equalsIgnoreCase(selectedName))) {
                return e;
            }
        }
        for (BlueprintGalleryTab.BlueprintEntry e : blueprints) {
            if (isBlueprintNameMatch(e.name, selectedName) || (e.filename != null && isBlueprintNameMatch(e.filename, selectedName))) {
                return e;
            }
        }
        return null;
    }

    /**
     * Compares two blueprint names using fuzzy matching rules.
     *
     * @param name1 first name
     * @param name2 second name
     * @return true if both names refer to the same blueprint
     */
    public boolean isBlueprintNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String clean2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) return true;

        String[] words1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        String[] words2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        int matchCount = 0;
        for (String w1 : words1) {
            if (w1.length() < 3) continue;
            for (String w2 : words2) {
                if (w1.equals(w2)) {
                    matchCount++;
                    break;
                }
            }
        }
        return matchCount >= Math.min(words1.length, words2.length);
    }

    /**
     * Determines whether a given blueprint entry is suitable for the Prompt Lab.
     *
     * @param e the blueprint entry
     * @return true if the blueprint is supported in Prompt Lab
     */
    public boolean isSupportedPromptLabBlueprint(BlueprintGalleryTab.BlueprintEntry e) {
        if (e == null || e.category == null) return false;
        String cat = e.category.toLowerCase(Locale.ROOT);
        String name = e.name != null ? e.name.toLowerCase(Locale.ROOT) : "";
        String desc = e.description != null ? e.description.toLowerCase(Locale.ROOT) : "";

        if (cat.contains("cloud") || name.contains("cloud") || desc.contains("cloud api")
                || name.contains("replicate") || name.contains("fal.ai") || name.contains("dall-e") || name.contains("openai")) {
            return false;
        }

        return cat.contains("text-to-image") || cat.contains("image lab") || cat.contains("flux") || cat.contains("sdxl") || cat.contains("sd 1.5")
                || name.contains("text to image") || name.contains("t2i") || name.contains("sdxl") || name.contains("flux") || name.contains("lumina")
                || name.contains("img2img") || name.contains("i2i") || name.contains("inpaint") || name.contains("image edit");
    }

    /**
     * Determines whether a blueprint entry represents an image-to-image or editing pipeline requiring an input image.
     *
     * @param entry the blueprint entry
     * @return true if an input image is needed
     */
    public boolean isImageEditBlueprint(BlueprintGalleryTab.BlueprintEntry entry) {
        if (entry == null) return false;
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        String name = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";
        return cat.contains("img2img") || cat.contains("inpaint") || cat.contains("edit")
                || name.contains("img2img") || name.contains("inpaint") || name.contains("i2i") || name.contains("image edit");
    }

    /**
     * Checks whether a model name refers to a video model.
     *
     * @param modelName the model name
     * @return true if it is a video model
     */
    public boolean isVideoModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase(Locale.ROOT);
        return lower.contains("video") || lower.contains("animatediff") || lower.contains("svd")
                || lower.contains("wan") || lower.contains("cogvideo") || lower.contains("ltx");
    }
}
