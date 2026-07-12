package com.thomaskippster.comfyuicompanion.service.inspector;

import java.util.List;

public class ModelMetadata {
    private String architectureType; // SD1.5, SDXL, FLUX
    private String modelType;        // CHECKPOINT, LORA
    private List<String> triggerWords;

    public String getArchitectureType() {
        return architectureType;
    }

    public void setArchitectureType(String architectureType) {
        this.architectureType = architectureType;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public List<String> getTriggerWords() {
        return triggerWords;
    }

    public void setTriggerWords(List<String> triggerWords) {
        this.triggerWords = triggerWords;
    }
}
