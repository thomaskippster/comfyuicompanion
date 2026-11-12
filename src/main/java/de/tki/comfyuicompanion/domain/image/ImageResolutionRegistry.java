package de.tki.comfyuicompanion.domain.image;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry providing recommended aspect ratios and resolutions for modern Diffusion models
 * (SDXL, SD3/3.5, Flux, SD 1.5). Ensures stable composition within model training bounds.
 */
@Component
public class ImageResolutionRegistry {

    private static final ImageResolutionRegistry INSTANCE = new ImageResolutionRegistry();

    private final List<ImageResolutionPreset> presets;

    /**
     * Constructs a new ImageResolutionRegistry with recommended presets.
     */
    public ImageResolutionRegistry() {
        List<ImageResolutionPreset> list = new ArrayList<>();
        list.add(ImageResolutionPreset.SQUARE_1024);
        list.add(ImageResolutionPreset.LANDSCAPE_16_9);
        list.add(ImageResolutionPreset.PORTRAIT_9_16);
        list.add(ImageResolutionPreset.LANDSCAPE_4_3);
        list.add(ImageResolutionPreset.PORTRAIT_3_4);
        list.add(ImageResolutionPreset.LANDSCAPE_3_2);
        list.add(ImageResolutionPreset.PORTRAIT_2_3);
        list.add(ImageResolutionPreset.CINEMA_21_9);
        list.add(ImageResolutionPreset.TALL_9_21);
        list.add(ImageResolutionPreset.SD15_SQUARE);
        list.add(ImageResolutionPreset.SD15_LANDSCAPE_4_3);
        list.add(ImageResolutionPreset.SD15_PORTRAIT_3_4);
        list.add(ImageResolutionPreset.SD15_LANDSCAPE_16_9);
        list.add(ImageResolutionPreset.FHD_16_9);
        list.add(ImageResolutionPreset.FHD_9_16);
        list.add(ImageResolutionPreset.CUSTOM);
        this.presets = Collections.unmodifiableList(list);
    }

    /**
     * Singleton accessor for UI components where dependency injection is not directly available.
     *
     * @return the shared ImageResolutionRegistry instance
     */
    public static ImageResolutionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieves all available image resolution presets.
     *
     * @return unmodifiable list of presets
     */
    public List<ImageResolutionPreset> getAllPresets() {
        return presets;
    }

    /**
     * Finds a preset matching exact width and height, or falls back to CUSTOM.
     *
     * @param width  horizontal resolution
     * @param height vertical resolution
     * @return matching preset or {@link ImageResolutionPreset#CUSTOM}
     */
    public ImageResolutionPreset findMatching(int width, int height) {
        for (ImageResolutionPreset p : presets) {
            if (p.width() == width && p.height() == height) {
                return p;
            }
        }
        return ImageResolutionPreset.CUSTOM;
    }

    /**
     * Resolves a preset by its unique ID.
     *
     * @param id preset identifier
     * @return Optional containing the preset if found
     */
    public Optional<ImageResolutionPreset> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return presets.stream()
                .filter(p -> p.id().equalsIgnoreCase(id.trim()))
                .findFirst();
    }
}
