package de.tki.comfyuicompanion.service.inspector;

import java.util.List;

/**
 * Represents metadata for a model used in the application.
 */
public class ModelMetadata {
    private String architectureType; // SD1.5, SDXL, FLUX
    private String modelType;        // CHECKPOINT, LORA
    private List<String> triggerWords;
    private String style; // z.B. Anime, Photorealistic

    /**
     * Gets the architecture type of the model.
     * @return The architecture type.
     */
    public String getArchitectureType() { return architectureType; }

    /**
     * Sets the architecture type of the model.
     * @param architectureType The architecture type.
     */
    public void setArchitectureType(String architectureType) { this.architectureType = architectureType; }

    /**
     * Gets the model type.
     * @return The model type.
     */
    public String getModelType() { return modelType; }

    /**
     * Sets the model type.
     * @param modelType The model type.
     */
    public void setModelType(String modelType) { this.modelType = modelType; }

    /**
     * Gets the trigger words associated with this model.
     * @return The trigger words.
     */
    public List<String> getTriggerWords() { return triggerWords; }

    /**
     * Sets the trigger words for this model.
     * @param triggerWords The trigger words.
     */
    public void setTriggerWords(List<String> triggerWords) { this.triggerWords = triggerWords; }

    /**
     * Gets the style of the model.
     * @return The style.
     */
    public String getStyle() { return style; }

    /**
     * Sets the style of the model.
     * @param style The style.
     */
    public void setStyle(String style) { this.style = style; }
}
