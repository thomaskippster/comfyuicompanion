package de.tki.comfyuicompanion.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComfyModelTest {

    @Test
    public void testExhaustivePatternMatchingOnSealedHierarchy() {
        ComfyModel model = new ComfyModel.CheckpointModel("flux1-schnell.safetensors",
                ModelFolder.CHECKPOINTS, ModelArchitecture.ARCH_FLUX, null);

        String typeCategory = switch (model) {
            case ComfyModel.CheckpointModel c -> "Checkpoint: " + c.architecture().getDisplayName();
            case ComfyModel.LoraModel l -> "LoRA: " + l.defaultStrength();
            case ComfyModel.VaeModel v -> "VAE: " + v.filename();
            case ComfyModel.ControlNetModel cn -> "ControlNet: " + cn.controlType();
            case ComfyModel.EmbeddingModel e -> "Embedding: " + e.triggerWord();
        };

        assertEquals("Checkpoint: FLUX", typeCategory);
        assertEquals("flux1-schnell.safetensors", model.filename());
        assertEquals(ModelFolder.CHECKPOINTS, model.folder());
    }

    @Test
    public void testLoraAndVaeRecords() {
        ComfyModel.LoraModel lora = new ComfyModel.LoraModel("detailer.safetensors",
                ModelFolder.LORAS, ModelArchitecture.ARCH_SDXL, 0.8);
        assertEquals(0.8, lora.defaultStrength());
        assertEquals(ModelFolder.LORAS, lora.folder());

        ComfyModel.VaeModel vae = new ComfyModel.VaeModel("ae.safetensors",
                ModelFolder.VAE, ModelArchitecture.ARCH_FLUX);
        assertEquals("ae.safetensors", vae.filename());
    }
}
