package de.tki.comfymodels.domain;

public class Scene {
    private String sceneId;
    private String prompt;
    private int startFrame;
    private int endFrame;
    private String audioPath;
    private String videoPath;
    private String narrationText;

    // AI Optimization Parameters (Wan 2.2 defaults)
    private double cfgScale = 1.0;
    private int steps = 30;
    private int motionBucketId = 127;

    // Montage Parameters
    private String sourceClipPath;
    private String transitionType = "crossfade";
    private double transitionDuration = 1.0;
    private boolean enhanceWithLtx = false;
    private double ltxStrength = 0.85;

    // Visual Enhancement Parameters (OpenCV / FFmpeg eq filter)
    private double contrast = 1.0;
    private double brightness = 0.0;

    // Dimensions (FHD 1080p Standard)
    private int width = 1920;
    private int height = 1080;

    // Optional scene-specific start/reference image (avoids global config state-bleed)
    private String speakerImagePath;

    public Scene() {}

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

    public double getCfgScale() {
        return cfgScale;
    }

    public void setCfgScale(double cfgScale) {
        this.cfgScale = cfgScale;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public int getMotionBucketId() {
        return motionBucketId;
    }

    public void setMotionBucketId(int motionBucketId) {
        this.motionBucketId = motionBucketId;
    }

    public String getSourceClipPath() {
        return sourceClipPath;
    }

    public void setSourceClipPath(String sourceClipPath) {
        this.sourceClipPath = sourceClipPath;
    }

    public String getTransitionType() {
        return transitionType;
    }

    public void setTransitionType(String transitionType) {
        this.transitionType = transitionType;
    }

    public double getTransitionDuration() {
        return transitionDuration;
    }

    public void setTransitionDuration(double transitionDuration) {
        this.transitionDuration = transitionDuration;
    }

    public boolean isEnhanceWithLtx() {
        return enhanceWithLtx;
    }

    public void setEnhanceWithLtx(boolean enhanceWithLtx) {
        this.enhanceWithLtx = enhanceWithLtx;
    }

    public double getLtxStrength() {
        return ltxStrength;
    }

    public void setLtxStrength(double ltxStrength) {
        this.ltxStrength = ltxStrength;
    }

    public double getContrast() {
        return contrast;
    }

    public void setContrast(double contrast) {
        this.contrast = contrast;
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = brightness;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getSpeakerImagePath() {
        return speakerImagePath;
    }

    public void setSpeakerImagePath(String speakerImagePath) {
        this.speakerImagePath = speakerImagePath;
    }

    @Override
    public String toString() {
        String base = "Scene " + sceneId + " [" + startFrame + "-" + endFrame + "]: " + prompt + " (CFG: " + cfgScale + ", Steps: " + steps + ", MotionBucket: " + motionBucketId + ")";
        if (sourceClipPath != null) {
            base += " [SourceClip: " + sourceClipPath + "]";
        }
        return base;
    }
}

