package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;

import java.util.*;
import java.util.function.Function;

/**
 * Controller responsible for filtering, sorting, and grouping blueprint entries.
 */
public class BlueprintFilterController {

    /**
     * Determines if a blueprint entry represents a video workflow.
     *
     * @param entry the blueprint entry
     * @return true if it is a video workflow
     */
    public static boolean isVideoBlueprint(BlueprintEntry entry) {
        if (entry == null) return false;
        if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
            return true;
        }
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        String name = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";
        String desc = entry.description != null ? entry.description.toLowerCase(Locale.ROOT) : "";

        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion")
                || cat.contains("i2v") || cat.contains("t2v") || cat.contains("v2v")
                || cat.contains("interpolat") || cat.contains("frame")) {
            return true;
        }
        return name.contains("text to video") || name.contains("image to video") || name.contains("video to video")
                || name.contains("video") || name.contains("animatediff") || name.contains("cogvideo")
                || name.contains("wan 2") || name.contains("hunyuanvideo") || name.contains("ltx-video")
                || desc.contains("generates video") || desc.contains("text-to-video") || desc.contains("image-to-video");
    }

    /**
     * Gets the media rank for sorting purposes (video > 3d/audio > other).
     *
     * @param entry the blueprint entry
     * @return an integer representing the media rank
     */
    public static int getBlueprintMediaRank(BlueprintEntry entry) {
        if (entry == null) return 2;
        if (isVideoBlueprint(entry)) {
            return 1;
        }
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) {
            return 2;
        }
        if (cat.contains("3d") || cat.contains("mesh")) {
            return 2;
        }
        return 0;
    }

    /**
     * Returns an order score for categories, lower score is higher priority.
     *
     * @param categoryName the category name
     * @return the order score
     */
    public static int getCategoryOrderScore(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) return 500;
        String cat = categoryName.toLowerCase(Locale.ROOT).trim();

        if (cat.contains("text to image") || cat.contains("txt2img") || cat.contains("text-to-image")) return 10;
        if (cat.contains("image edit") || cat.contains("image to image") || cat.contains("img2img")) return 20;
        if (cat.contains("inpaint") || cat.contains("outpaint")) return 30;
        if (cat.contains("controlnet")) return 40;
        if (cat.contains("pose") || cat.contains("depth")) return 50;
        if (cat.contains("upscal")) return 60;
        if (cat.contains("segmentation")) return 70;
        if (cat.contains("caption")) return 80;
        if (cat.contains("background") || cat.contains("style")) return 85;
        if (cat.contains("image") || cat.contains("face") || cat.contains("photo") || cat.contains("art")) return 90;

        if (cat.contains("text to video") || cat.contains("t2v") || cat.contains("text-to-video")) return 110;
        if (cat.contains("image to video") || cat.contains("i2v") || cat.contains("image-to-video")) return 120;
        if (cat.contains("video to video") || cat.contains("v2v") || cat.contains("video-to-video")) return 130;
        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion")
                || cat.contains("interpolat") || cat.contains("frame")) return 140;

        if (cat.contains("3d") || cat.contains("mesh")) return 210;
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) return 220;
        if (cat.contains("use case") || cat.contains("star")) return 230;

        return 300;
    }

    /**
     * Extracts and sorts categories from the given entries.
     *
     * @param allEntries the list of blueprint entries
     * @return a sorted list of unique category names
     */
    public static List<String> getSortedCategories(List<BlueprintEntry> allEntries) {
        Set<String> categories = new HashSet<>();
        for (BlueprintEntry entry : allEntries) {
            String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
            categories.add(cat);
        }

        List<String> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort((c1, c2) -> {
            int o1 = getCategoryOrderScore(c1);
            int o2 = getCategoryOrderScore(c2);
            if (o1 != o2) return Integer.compare(o1, o2);
            return c1.compareToIgnoreCase(c2);
        });
        return sortedCategories;
    }

    /**
     * Filters entries based on the criteria.
     *
     * @param allEntries       all available entries
     * @param filterText       search text
     * @param selectedCategory selected category or null/"All Categories"
     * @param readyOnly        if true, only shows ready entries
     * @param hideCloud        if true, hides cloud-only workflows
     * @param statusProvider   function to compute the status of an entry
     * @return a filtered and sorted list of entries
     */
    public static List<BlueprintEntry> filterAndSort(List<BlueprintEntry> allEntries, 
                                                     String filterText, 
                                                     String selectedCategory, 
                                                     boolean readyOnly, 
                                                     boolean hideCloud, 
                                                     Function<BlueprintEntry, BlueprintStatus> statusProvider) {
        String filter = filterText == null ? "" : filterText.toLowerCase(Locale.ROOT).trim();
        List<BlueprintEntry> visible = new ArrayList<>();
        
        for (BlueprintEntry e : allEntries) {
            boolean matchesSearch = filter.isEmpty()
                    || (e.name != null && e.name.toLowerCase(Locale.ROOT).contains(filter))
                    || (e.description != null && e.description.toLowerCase(Locale.ROOT).contains(filter));

            if (!matchesSearch) continue;

            BlueprintStatus status = statusProvider.apply(e);
            boolean isReady = e.hasBeenValidated && status.missingCount == 0 && status.presentCount > 0;

            if (readyOnly && !isReady) {
                continue;
            }

            if (hideCloud && e.registryWorkflow.isCloudOnly()) {
                continue;
            }

            if (selectedCategory != null && !"All Categories".equals(selectedCategory)) {
                String entryCat = e.category != null && !e.category.isEmpty() ? e.category : "General";
                if (!selectedCategory.equalsIgnoreCase(entryCat)) {
                    continue;
                }
            }

            visible.add(e);
        }

        visible.sort((e1, e2) -> {
            int rank1 = getBlueprintMediaRank(e1);
            int rank2 = getBlueprintMediaRank(e2);
            if (rank1 != rank2) return Integer.compare(rank1, rank2);

            int catOrder1 = getCategoryOrderScore(e1.category);
            int catOrder2 = getCategoryOrderScore(e2.category);
            if (catOrder1 != catOrder2) return Integer.compare(catOrder1, catOrder2);

            BlueprintStatus s1 = statusProvider.apply(e1);
            BlueprintStatus s2 = statusProvider.apply(e2);

            boolean run1 = e1.hasBeenValidated && s1.missingCount == 0 && s1.presentCount > 0;
            boolean run2 = e2.hasBeenValidated && s2.missingCount == 0 && s2.presentCount > 0;
            if (run1 != run2) return run1 ? -1 : 1;

            double pop1 = e1.registryWorkflow != null ? e1.registryWorkflow.getPopularScore() : 0.0;
            double pop2 = e2.registryWorkflow != null ? e2.registryWorkflow.getPopularScore() : 0.0;
            int popCmp = Double.compare(pop2, pop1);
            if (popCmp != 0) return popCmp;

            String n1 = e1.name != null ? e1.name : "";
            String n2 = e2.name != null ? e2.name : "";
            return n1.compareToIgnoreCase(n2);
        });
        
        return visible;
    }

    /**
     * Groups a sorted list of entries by category, preserving category order.
     * 
     * @param entries the filtered and sorted entries
     * @return a map of category names to their respective entries
     */
    public static Map<String, List<BlueprintEntry>> groupByCategory(List<BlueprintEntry> entries) {
        Map<String, List<BlueprintEntry>> grouped = new TreeMap<>((c1, c2) -> {
            int o1 = getCategoryOrderScore(c1);
            int o2 = getCategoryOrderScore(c2);
            if (o1 != o2) return Integer.compare(o1, o2);
            return c1.compareToIgnoreCase(c2);
        });

        for (BlueprintEntry entry : entries) {
            String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(entry);
        }
        
        return grouped;
    }

    /**
     * Returns the available blueprints for general execution.
     * 
     * @param allEntries all available entries
     * @return a filtered and sorted list of available blueprints
     */
    public static List<BlueprintEntry> getAvailableBlueprints(List<BlueprintEntry> allEntries) {
        List<BlueprintEntry> list = new ArrayList<>();
        for (BlueprintEntry e : allEntries) {
            BlueprintStatus s = e.status;
            if (e.hasBeenValidated && s != null && s.missingCount == 0 && s.presentCount > 0) {
                if (e.registryWorkflow != null && e.registryWorkflow.isCloudOnly()) {
                    continue;
                }
                list.add(e);
            }
        }
        list.sort((e1, e2) -> {
            int r1 = getBlueprintMediaRank(e1);
            int r2 = getBlueprintMediaRank(e2);
            if (r1 != r2) return Integer.compare(r1, r2);
            int o1 = getCategoryOrderScore(e1.category);
            int o2 = getCategoryOrderScore(e2.category);
            if (o1 != o2) return Integer.compare(o1, o2);
            double pop1 = e1.registryWorkflow != null ? e1.registryWorkflow.getPopularScore() : 0.0;
            double pop2 = e2.registryWorkflow != null ? e2.registryWorkflow.getPopularScore() : 0.0;
            int popCmp = Double.compare(pop2, pop1);
            if (popCmp != 0) return popCmp;
            String n1 = e1.name != null ? e1.name : "";
            String n2 = e2.name != null ? e2.name : "";
            return n1.compareToIgnoreCase(n2);
        });
        return list;
    }
}
