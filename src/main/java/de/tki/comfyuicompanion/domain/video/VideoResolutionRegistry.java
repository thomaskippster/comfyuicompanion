package de.tki.comfyuicompanion.domain.video;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry providing standardized and custom aspect ratios and resolutions
 * for video pipelines, TikTok 9:16 mobile formats, and widescreen cinema.
 */
@Component
public class VideoResolutionRegistry {

    private final List<VideoResolutionPreset> presets;

    public VideoResolutionRegistry() {
        List<VideoResolutionPreset> list = new ArrayList<>();
        list.add(VideoResolutionPreset.HD_16_9);
        list.add(VideoResolutionPreset.TIKTOK_9_16_HD);
        list.add(VideoResolutionPreset.FULL_HD_16_9);
        list.add(VideoResolutionPreset.TIKTOK_9_16_FULL_HD);
        list.add(VideoResolutionPreset.WIDE_576P);
        list.add(VideoResolutionPreset.TIKTOK_9_16_BALANCED);
        list.add(VideoResolutionPreset.DRAFT_480P);
        list.add(VideoResolutionPreset.TIKTOK_9_16_DRAFT);
        list.add(VideoResolutionPreset.SQUARE_768);
        list.add(VideoResolutionPreset.SQUARE_1024);
        list.add(VideoResolutionPreset.CUSTOM);
        this.presets = Collections.unmodifiableList(list);
    }

    /**
     * @return all registered resolution presets
     */
    public List<VideoResolutionPreset> getAllPresets() {
        return presets;
    }

    /**
     * @return display names array suitable for JComboBox models
     */
    public String[] getDisplayNames() {
        return presets.stream()
                .map(VideoResolutionPreset::displayName)
                .toArray(String[]::new);
    }

    /**
     * Resolves a preset by its unique ID.
     */
    public Optional<VideoResolutionPreset> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return presets.stream()
                .filter(p -> p.id().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    /**
     * Resolves a preset matching exact width and height, or falls back to CUSTOM.
     */
    public VideoResolutionPreset findMatching(int width, int height) {
        for (VideoResolutionPreset p : presets) {
            if (p.width() == width && p.height() == height) {
                return p;
            }
        }
        return VideoResolutionPreset.CUSTOM;
    }

    /**
     * Returns the index of the preset matching width and height, or the index of CUSTOM.
     */
    public int findIndex(int width, int height) {
        for (int i = 0; i < presets.size(); i++) {
            VideoResolutionPreset p = presets.get(i);
            if (p.width() == width && p.height() == height) {
                return i;
            }
        }
        return presets.size() - 1; // CUSTOM is last
    }
}
