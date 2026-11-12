package de.tki.comfyuicompanion.domain.image;

/**
 * Domain record representing an aspect ratio and resolution preset for image diffusion models.
 *
 * @param id          unique preset identifier (e.g. "1_1_1024", "16_9_1344")
 * @param displayName user-friendly label for combo boxes and UI selectors
 * @param width       horizontal resolution in pixels (or 0 for custom)
 * @param height      vertical resolution in pixels (or 0 for custom)
 * @param aspectRatio aspect ratio label (e.g. "1:1", "16:9", "9:16", "4:3")
 * @param description brief explanation of recommended model usage and Megapixel footprint
 */
public record ImageResolutionPreset(
        String id,
        String displayName,
        int width,
        int height,
        String aspectRatio,
        String description
) {
    public static final ImageResolutionPreset SQUARE_1024 = new ImageResolutionPreset(
            "1_1_1024", "1:1 Square (1024 x 1024) [~1.0 MP - Recommended]", 1024, 1024, "1:1",
            "Optimal standard square format for modern models (SDXL, SD 3.5, Flux, Hunyuan, Wan).");

    public static final ImageResolutionPreset LANDSCAPE_16_9 = new ImageResolutionPreset(
            "16_9_1344", "16:9 Landscape (1344 x 768) [~1.0 MP - Recommended]", 1344, 768, "16:9",
            "Optimal 16:9 widescreen format within ~1 MP training budget. Prevents duplicate artifacts.");

    public static final ImageResolutionPreset PORTRAIT_9_16 = new ImageResolutionPreset(
            "9_16_768", "9:16 Portrait (768 x 1344) [~1.0 MP - Recommended]", 768, 1344, "9:16",
            "Optimal 9:16 vertical smartphone format within ~1 MP training budget.");

    public static final ImageResolutionPreset LANDSCAPE_4_3 = new ImageResolutionPreset(
            "4_3_1152", "4:3 Landscape (1152 x 896) [~1.0 MP]", 1152, 896, "4:3",
            "Standard 4:3 landscape ratio.");

    public static final ImageResolutionPreset PORTRAIT_3_4 = new ImageResolutionPreset(
            "3_4_896", "3:4 Portrait (896 x 1152) [~1.0 MP]", 896, 1152, "3:4",
            "Standard 3:4 portrait ratio.");

    public static final ImageResolutionPreset LANDSCAPE_3_2 = new ImageResolutionPreset(
            "3_2_1216", "3:2 Landscape (1216 x 832) [~1.0 MP]", 1216, 832, "3:2",
            "Classic 35mm photography landscape ratio.");

    public static final ImageResolutionPreset PORTRAIT_2_3 = new ImageResolutionPreset(
            "2_3_832", "2:3 Portrait (832 x 1216) [~1.0 MP]", 832, 1216, "2:3",
            "Classic 35mm photography portrait ratio.");

    public static final ImageResolutionPreset CINEMA_21_9 = new ImageResolutionPreset(
            "21_9_1536", "21:9 Cinema (1536 x 640) [~1.0 MP]", 1536, 640, "21:9",
            "Cinematic ultrawide format.");

    public static final ImageResolutionPreset TALL_9_21 = new ImageResolutionPreset(
            "9_21_640", "9:21 Ultra-Tall (640 x 1536) [~1.0 MP]", 640, 1536, "9:21",
            "Ultra-tall vertical banner or wallpaper format.");

    public static final ImageResolutionPreset SD15_SQUARE = new ImageResolutionPreset(
            "sd15_512", "1:1 SD 1.5 Square (512 x 512) [Legacy]", 512, 512, "1:1",
            "Standard native square format for SD 1.5 checkpoints.");

    public static final ImageResolutionPreset SD15_LANDSCAPE_4_3 = new ImageResolutionPreset(
            "sd15_4_3", "4:3 SD 1.5 Landscape (512 x 384) [Legacy]", 512, 384, "4:3",
            "Standard landscape format for SD 1.5 checkpoints.");

    public static final ImageResolutionPreset SD15_PORTRAIT_3_4 = new ImageResolutionPreset(
            "sd15_3_4", "3:4 SD 1.5 Portrait (384 x 512) [Legacy]", 384, 512, "3:4",
            "Standard portrait format for SD 1.5 checkpoints.");

    public static final ImageResolutionPreset SD15_LANDSCAPE_16_9 = new ImageResolutionPreset(
            "sd15_16_9", "16:9 SD 1.5 Landscape (768 x 432) [Legacy]", 768, 432, "16:9",
            "Widescreen format for SD 1.5 checkpoints.");

    public static final ImageResolutionPreset FHD_16_9 = new ImageResolutionPreset(
            "fhd_1920", "16:9 Full HD (1920 x 1080) [High-Res 2MP]", 1920, 1080, "16:9",
            "Full HD direct generation. Notice: Can trigger duplication artifacts without Hi-Res fix.");

    public static final ImageResolutionPreset FHD_9_16 = new ImageResolutionPreset(
            "fhd_1080", "9:16 Full HD (1080 x 1920) [High-Res 2MP]", 1080, 1920, "9:16",
            "Full HD vertical direct generation. Notice: Can trigger duplication artifacts without Hi-Res fix.");

    public static final ImageResolutionPreset CUSTOM = new ImageResolutionPreset(
            "custom", "Custom (Manual Spinners)", 0, 0, "Custom",
            "Freely user-defined custom width and height.");

    @Override
    public String toString() {
        return displayName;
    }
}
