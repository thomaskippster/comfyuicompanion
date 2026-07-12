package com.thomaskippster.comfyuicompanion.service.provisioning;

public class MissingResourceReport {

    public enum ResourceType {
        CHECKPOINT,
        LORA
    }

    private boolean isMissing;
    private ResourceType type;
    private String architecture;
    private String keyword;

    public boolean isMissing() {
        return isMissing;
    }

    public void setMissing(boolean missing) {
        isMissing = missing;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    private String resolvedModelName;

    public String getResolvedModelName() {
        return resolvedModelName;
    }

    public void setResolvedModelName(String resolvedModelName) {
        this.resolvedModelName = resolvedModelName;
    }
}
