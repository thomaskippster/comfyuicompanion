package de.tki.comfyuicompanion.service.workflow;

/**
 * Enumeration defining the structural topology for loading model weights in a ComfyUI visual workflow graph.
 */
public enum LoaderTopology {

    /**
     * Monolithic all-in-one checkpoint loading via {@code CheckpointLoaderSimple} (e.g. SD 1.5, SDXL, Pony).
     */
    CHECKPOINT_SIMPLE,

    /**
     * Modular loading with separate UNET, single text encoder CLIPLoader, and VAE (e.g. Flux.2/Klein, Wan 2.1).
     */
    UNET_SINGLE_CLIP,

    /**
     * Modular loading with separate UNET, dual text encoder DualCLIPLoader, and VAE (e.g. Flux.1 Dev/Schnell).
     */
    UNET_DUAL_CLIP
}
