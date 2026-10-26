package de.tki.comfyuicompanion.domain;

/**
 * Defines the standard directory names used by ComfyUI to store various model types.
 */
public enum ModelFolder {
    CHECKPOINTS("checkpoints"),
    LORAS("loras"),
    VAE("vae"),
    CLIP("clip"),
    CONTROLNET("controlnet"),
    UPSCALE_MODELS("upscale_models"),
    VAEECODE("vae_approx"),
    UNET("unet"),
    CLIP_VISION("clip_vision"),
    STYLE_MODELS("style_models"),
    HYPERNETWORKS("hypernetworks"),
    PHOTOMAKER("photomaker"),
    IP_ADAPTER("ipadapter"),
    ONNX("onnx"),
    LLM("llm"),
    DIFFUSION_MODELS("diffusion_models"),
    TEXT_ENCODERS("text_encoders"),
    GLIGEN("gligen"),
    EMBEDDINGS("embeddings"),
    AUDIO_ENCODERS("audio_encoders"),
    DIFFUSERS("diffusers"),
    CONFIGS("configs"),
    MODEL_PATCHES("model_patches"),
    LATENT_UPSCALE_MODELS("latent_upscale_models"),
    ARCHIVE("archive"),
    WORKFLOWS("workflows");

    private final String defaultFolderName;

    /**
     * Constructs a ModelFolder with its corresponding ComfyUI directory name.
     *
     * @param defaultFolderName the relative directory name
     */
    ModelFolder(String defaultFolderName) {
        this.defaultFolderName = defaultFolderName;
    }

    public String getDefaultFolderName() {
        return defaultFolderName;
    }

    public static ModelFolder fromString(String text) {
        if (text == null) return CHECKPOINTS;
        for (ModelFolder b : ModelFolder.values()) {
            if (b.defaultFolderName.equalsIgnoreCase(text) || b.name().equalsIgnoreCase(text)) {
                return b;
            }
        }
        
        // Handle common variations
        String lower = text.toLowerCase();
        if (lower.contains("checkpoint")) return CHECKPOINTS;
        if (lower.contains("lora") || lower.contains("locon")) return LORAS;
        if (lower.contains("vae")) return VAE;
        if (lower.contains("controlnet")) return CONTROLNET;
        if (lower.contains("upscale") || lower.contains("esrgan")) return UPSCALE_MODELS;
        if (lower.contains("embedding") || lower.contains("textualinversion") || lower.contains("textual inversion") || lower.contains("textual_inversion")) return EMBEDDINGS;
        if (lower.contains("text_encoder") || lower.contains("textencoder")) return TEXT_ENCODERS;
        if (lower.contains("diffusion")) return DIFFUSION_MODELS;
        if (lower.contains("unet")) return UNET;
        if (lower.contains("workflow")) return WORKFLOWS;
        
        return CHECKPOINTS; // Default fallback
    }
}
