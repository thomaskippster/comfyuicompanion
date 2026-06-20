package de.tki.comfymodels.domain;

public class Scene {
    private String sceneId;
    private String prompt;
    private int startFrame;
    private int endFrame;
    private String audioPath;
    private String videoPath;
    private String narrationText;

    // AI Optimization Parameters
    private int cfgScale = 8;
    private int steps = 20;
    private int motionBucketId = 127;

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

    public int getCfgScale() {
        return cfgScale;
    }

    public void setCfgScale(int cfgScale) {
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

    @Override
    public String toString() {
        return "Scene " + sceneId + " [" + startFrame + "-" + endFrame + "]: " + prompt + " (CFG: " + cfgScale + ", Steps: " + steps + ", MotionBucket: " + motionBucketId + ")";
    }
}

