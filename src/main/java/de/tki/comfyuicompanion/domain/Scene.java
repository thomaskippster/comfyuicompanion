package de.tki.comfyuicompanion.domain;

/**
 * Domain model representing a single scene in the video generation pipeline.
 * Encapsulates script prompts, frame timing, audio/video paths, and grouped value objects
 * for render settings, montage transitions, visual enhancements, and dimensions.
 */
public class Scene {

    /**
     * Value object capturing AI diffusion render and sampling parameters.
     *
     * @param cfgScale       classifier-free guidance scale
     * @param steps          number of diffusion sampling steps
     * @param motionBucketId motion bucket ID for video conditioning
     */
    public record RenderSettings(double cfgScale, int steps, int motionBucketId) {
        public static final RenderSettings DEFAULT = new RenderSettings(1.0, 30, 127);
    }

    /**
     * Value object encapsulating montage, source clips, and transition properties.
     *
     * @param sourceClipPath     optional path to an existing video clip to incorporate
     * @param transitionType     type of transition effect (e.g., "crossfade")
     * @param transitionDuration duration of transition in seconds
     * @param enhanceWithLtx     whether to apply LTX enhancement
     * @param ltxStrength        strength factor for LTX model enhancement
     */
    public record TransitionSettings(String sourceClipPath, String transitionType, double transitionDuration, boolean enhanceWithLtx, double ltxStrength) {
        public static final TransitionSettings DEFAULT = new TransitionSettings(null, "crossfade", 1.0, false, 0.85);
    }

    /**
     * Value object encapsulating visual enhancement adjustments (contrast, brightness).
     *
     * @param contrast   contrast adjustment factor
     * @param brightness brightness offset
     */
    public record VisualEnhancements(double contrast, double brightness) {
        public static final VisualEnhancements DEFAULT = new VisualEnhancements(1.0, 0.0);
    }

    /**
     * Value object encapsulating scene canvas resolution dimensions.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     */
    public record SceneDimensions(int width, int height) {
        public static final SceneDimensions FHD_1080P = new SceneDimensions(1920, 1080);
    }

    private String sceneId;
    private String prompt;
    private int startFrame;
    private int endFrame;
    private String audioPath;
    private String videoPath;
    private String narrationText;
    private String speakerImagePath;

    private RenderSettings renderSettings = RenderSettings.DEFAULT;
    private TransitionSettings transitionSettings = TransitionSettings.DEFAULT;
    private VisualEnhancements visualEnhancements = VisualEnhancements.DEFAULT;
    private SceneDimensions dimensions = SceneDimensions.FHD_1080P;

    /**
     * Default constructor initializing default value objects.
     */
    public Scene() {}

    /**
     * Convenience constructor initializing primary scene identifiers and timing.
     *
     * @param sceneId    unique identifier of the scene
     * @param prompt     visual generation prompt
     * @param startFrame starting frame index
     * @param endFrame   ending frame index
     * @param audioPath  associated audio track file path
     * @param videoPath  rendered video file path
     */
    public Scene(String sceneId, String prompt, int startFrame, int endFrame, String audioPath, String videoPath) {
        this.sceneId = sceneId;
        this.prompt = prompt;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.audioPath = audioPath;
        this.videoPath = videoPath;
    }

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getStartFrame() {
        return startFrame;
    }

    public void setStartFrame(int startFrame) {
        this.startFrame = startFrame;
    }

    public int getEndFrame() {
        return endFrame;
    }

    public void setEndFrame(int endFrame) {
        this.endFrame = endFrame;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getNarrationText() {
        return narrationText;
    }

    public void setNarrationText(String narrationText) {
        this.narrationText = narrationText;
    }

    public String getSpeakerImagePath() {
        return speakerImagePath;
    }

    public void setSpeakerImagePath(String speakerImagePath) {
        this.speakerImagePath = speakerImagePath;
    }

    // --- Value Object Accessors ---

    public RenderSettings getRenderSettings() {
        return renderSettings;
    }

    public void setRenderSettings(RenderSettings renderSettings) {
        this.renderSettings = renderSettings != null ? renderSettings : RenderSettings.DEFAULT;
    }

    public TransitionSettings getTransitionSettings() {
        return transitionSettings;
    }

    public void setTransitionSettings(TransitionSettings transitionSettings) {
        this.transitionSettings = transitionSettings != null ? transitionSettings : TransitionSettings.DEFAULT;
    }

    public VisualEnhancements getVisualEnhancements() {
        return visualEnhancements;
    }

    public void setVisualEnhancements(VisualEnhancements visualEnhancements) {
        this.visualEnhancements = visualEnhancements != null ? visualEnhancements : VisualEnhancements.DEFAULT;
    }

    public SceneDimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(SceneDimensions dimensions) {
        this.dimensions = dimensions != null ? dimensions : SceneDimensions.FHD_1080P;
    }

    // --- Backward Compatible Delegate Getters/Setters ---

    public double getCfgScale() {
        return renderSettings.cfgScale();
    }

    public void setCfgScale(double cfgScale) {
        this.renderSettings = new RenderSettings(cfgScale, renderSettings.steps(), renderSettings.motionBucketId());
    }

    public int getSteps() {
        return renderSettings.steps();
    }

    public void setSteps(int steps) {
        this.renderSettings = new RenderSettings(renderSettings.cfgScale(), steps, renderSettings.motionBucketId());
    }

    public int getMotionBucketId() {
        return renderSettings.motionBucketId();
    }

    public void setMotionBucketId(int motionBucketId) {
        this.renderSettings = new RenderSettings(renderSettings.cfgScale(), renderSettings.steps(), motionBucketId);
    }

    public String getSourceClipPath() {
        return transitionSettings.sourceClipPath();
    }

    public void setSourceClipPath(String sourceClipPath) {
        this.transitionSettings = new TransitionSettings(sourceClipPath, transitionSettings.transitionType(), transitionSettings.transitionDuration(), transitionSettings.enhanceWithLtx(), transitionSettings.ltxStrength());
    }

    public String getTransitionType() {
        return transitionSettings.transitionType();
    }

    public void setTransitionType(String transitionType) {
        this.transitionSettings = new TransitionSettings(transitionSettings.sourceClipPath(), transitionType, transitionSettings.transitionDuration(), transitionSettings.enhanceWithLtx(), transitionSettings.ltxStrength());
    }

    public double getTransitionDuration() {
        return transitionSettings.transitionDuration();
    }

    public void setTransitionDuration(double transitionDuration) {
        this.transitionSettings = new TransitionSettings(transitionSettings.sourceClipPath(), transitionSettings.transitionType(), transitionDuration, transitionSettings.enhanceWithLtx(), transitionSettings.ltxStrength());
    }

    public boolean isEnhanceWithLtx() {
        return transitionSettings.enhanceWithLtx();
    }

    public void setEnhanceWithLtx(boolean enhanceWithLtx) {
        this.transitionSettings = new TransitionSettings(transitionSettings.sourceClipPath(), transitionSettings.transitionType(), transitionSettings.transitionDuration(), enhanceWithLtx, transitionSettings.ltxStrength());
    }

    public double getLtxStrength() {
        return transitionSettings.ltxStrength();
    }

    public void setLtxStrength(double ltxStrength) {
        this.transitionSettings = new TransitionSettings(transitionSettings.sourceClipPath(), transitionSettings.transitionType(), transitionSettings.transitionDuration(), transitionSettings.enhanceWithLtx(), ltxStrength);
    }

    public double getContrast() {
        return visualEnhancements.contrast();
    }

    public void setContrast(double contrast) {
        this.visualEnhancements = new VisualEnhancements(contrast, visualEnhancements.brightness());
    }

    public double getBrightness() {
        return visualEnhancements.brightness();
    }

    public void setBrightness(double brightness) {
        this.visualEnhancements = new VisualEnhancements(visualEnhancements.contrast(), brightness);
    }

    public int getWidth() {
        return dimensions.width();
    }

    public void setWidth(int width) {
        this.dimensions = new SceneDimensions(width, dimensions.height());
    }

    public int getHeight() {
        return dimensions.height();
    }

    public void setHeight(int height) {
        this.dimensions = new SceneDimensions(dimensions.width(), height);
    }

    @Override
    public String toString() {
        String base = "Scene " + sceneId + " [" + startFrame + "-" + endFrame + "]: " + prompt + " (CFG: " + getCfgScale() + ", Steps: " + getSteps() + ", MotionBucket: " + getMotionBucketId() + ")";
        if (getSourceClipPath() != null) {
            base += " [SourceClip: " + getSourceClipPath() + "]";
        }
        return base;
    }
}
