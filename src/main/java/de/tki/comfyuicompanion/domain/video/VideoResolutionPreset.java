package de.tki.comfyuicompanion.domain.video;

/**
 * Domain record representing an aspect ratio and resolution preset for video and storyboard pipelines.
 *
 * @param id unique preset identifier (e.g. "720p_16_9", "tiktok_9_16_hd")
 * @param displayName user-friendly label for combo boxes and UI selectors
 * @param width horizontal resolution in pixels
 * @param height vertical resolution in pixels
 * @param aspectRatio aspect ratio label (e.g. "16:9", "9:16", "1:1")
 * @param isVertical true if optimized for TikTok, Instagram Reels, and YouTube Shorts
 * @param description brief explanation of performance and target format
 */
public record VideoResolutionPreset(
        String id,
        String displayName,
        int width,
        int height,
        String aspectRatio,
        boolean isVertical,
        String description
) {
    public static final VideoResolutionPreset HD_16_9 = new VideoResolutionPreset(
            "720p_16_9", "720p HD 16:9 (1280x720) - Landscape Recommended", 1280, 720, "16:9", false, "Standard landscape HD format, ideal for YouTube and desktop viewing.");

    public static final VideoResolutionPreset TIKTOK_9_16_HD = new VideoResolutionPreset(
            "tiktok_9_16_hd", "TikTok / Reels 9:16 (720x1280) - Vertical HD", 720, 1280, "9:16", true, "Crisp vertical HD format for TikTok, Instagram Reels, and YouTube Shorts.");

    public static final VideoResolutionPreset FULL_HD_16_9 = new VideoResolutionPreset(
            "1080p_16_9", "1080p Full HD 16:9 (1920x1080) - High Quality", 1920, 1080, "16:9", false, "High definition 1080p output for premium productions.");

    public static final VideoResolutionPreset TIKTOK_9_16_FULL_HD = new VideoResolutionPreset(
            "tiktok_9_16_fhd", "TikTok / Reels 9:16 (1080x1920) - Full HD Vertical", 1080, 1920, "9:16", true, "Maximum vertical resolution for mobile short-form cinema.");

    public static final VideoResolutionPreset WIDE_576P = new VideoResolutionPreset(
            "576p_16_9", "576p Wide 16:9 (1024x576) - Balanced", 1024, 576, "16:9", false, "Balanced resolution with fast generation speeds on mid-tier GPUs.");

    public static final VideoResolutionPreset TIKTOK_9_16_BALANCED = new VideoResolutionPreset(
            "tiktok_9_16_balanced", "TikTok / Reels 9:16 (576x1024) - Balanced Vertical", 576, 1024, "9:16", true, "Efficient vertical format with reduced VRAM footprint.");

    public static final VideoResolutionPreset DRAFT_480P = new VideoResolutionPreset(
            "480p_16_9", "480p SD 16:9 (832x480) - Fast Draft", 832, 480, "16:9", false, "Fast draft mode for rapid storyboard iteration.");

    public static final VideoResolutionPreset TIKTOK_9_16_DRAFT = new VideoResolutionPreset(
            "tiktok_9_16_draft", "TikTok / Reels 9:16 (480x832) - Fast Vertical Draft", 480, 832, "9:16", true, "Fast vertical draft mode for prototyping mobile scenes.");

    public static final VideoResolutionPreset SQUARE_768 = new VideoResolutionPreset(
            "square_768", "Square 1:1 (768x768) - Social Media", 768, 768, "1:1", false, "Square format for feed posts and avatars.");

    public static final VideoResolutionPreset SQUARE_1024 = new VideoResolutionPreset(
            "square_1024", "Square 1:1 (1024x1024) - High Detail", 1024, 1024, "1:1", false, "High resolution square format for detailed characters and subjects.");

    public static final VideoResolutionPreset CUSTOM = new VideoResolutionPreset(
            "custom", "Custom (Use Spinners)", 0, 0, "Custom", false, "User-defined custom resolution.");
}
