package de.tki.comfyuicompanion.domain;

/**
 * Sealed domain hierarchy representing distinct categories of ComfyUI models.
 * <p>
 * Enables Java 21 exhaustive pattern matching in switch expressions without requiring default branches.
 */
public sealed interface ComfyModel permits
        ComfyModel.CheckpointModel,
        ComfyModel.LoraModel,
        ComfyModel.VaeModel,
        ComfyModel.ControlNetModel,
        ComfyModel.EmbeddingModel {

    /**
     * The filename of the model on disk.
     *
     * @return the model filename
     */
    String filename();

    /**
     * The target subfolder inside the models root directory.
     *
     * @return the model folder enum
     */
    ModelFolder folder();

    /**
     * The architecture of this model (e.g. FLUX, SDXL, Wan).
     *
     * @return the model architecture
     */
    ModelArchitecture architecture();

    /**
     * Primary checkpoint or base diffusion model.
     *
     * @param filename     the filename
     * @param folder       the target model folder
     * @param architecture the model architecture
     * @param configName   optional associated YAML configuration
     */
    record CheckpointModel(String filename, ModelFolder folder, ModelArchitecture architecture, String configName)
            implements ComfyModel {}

    /**
     * Low-Rank Adaptation (LoRA) model weights.
     *
     * @param filename     the filename
     * @param folder       the target model folder
     * @param architecture the model architecture
     * @param defaultStrength recommended default strength
     */
    record LoraModel(String filename, ModelFolder folder, ModelArchitecture architecture, double defaultStrength)
            implements ComfyModel {}

    /**
     * Variational Autoencoder (VAE) weights.
     *
     * @param filename     the filename
     * @param folder       the target model folder
     * @param architecture the model architecture
     */
    record VaeModel(String filename, ModelFolder folder, ModelArchitecture architecture)
            implements ComfyModel {}

    /**
     * ControlNet or T2I-Adapter model.
     *
     * @param filename     the filename
     * @param folder       the target model folder
     * @param architecture the model architecture
     * @param controlType  the type of control (e.g. canny, depth, pose)
     */
    record ControlNetModel(String filename, ModelFolder folder, ModelArchitecture architecture, String controlType)
            implements ComfyModel {}

    /**
     * Textual inversion embedding.
     *
     * @param filename     the filename
     * @param folder       the target model folder
     * @param architecture the model architecture
     * @param triggerWord  the trigger word activating the embedding
     */
    record EmbeddingModel(String filename, ModelFolder folder, ModelArchitecture architecture, String triggerWord)
            implements ComfyModel {}
}
