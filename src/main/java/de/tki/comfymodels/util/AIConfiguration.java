package de.tki.comfymodels.util;

public class AIConfiguration {
    private String prompt;
    private int cfgScale;
    private int steps;
    private int motionBucketId;

    public AIConfiguration() {}

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public int getCfgScale() { return cfgScale; }
    public void setCfgScale(int cfgScale) { this.cfgScale = cfgScale; }
    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }
    public int getMotionBucketId() { return motionBucketId; }
    public void setMotionBucketId(int motionBucketId) { this.motionBucketId = motionBucketId; }
}
