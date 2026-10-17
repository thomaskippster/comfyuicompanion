package de.tki.comfyuicompanion.service.impl.architecture;

import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;

/**
 * Catalog of built-in inference defaults (VAE, CLIP type, scheduler, sampler) per model architecture.
 */
public final class StandardModelDefaultsCatalog {

    private static final String DEFAULT_VAE = "vae-ft-mse-840000-ema-pruned.safetensors";

    /**
     * Returns the standard defaults for an architecture.
     *
     * @param arch the architecture; {@code null} is treated as {@link ModelArchitecture#ARCH_UNKNOWN}
     * @return a new {@link ModelDefaults} instance, never {@code null}
     */
    public ModelDefaults defaultsFor(ModelArchitecture arch) {
        ModelArchitecture effective = arch == null ? ModelArchitecture.ARCH_UNKNOWN : arch;
        String name = effective.name();
        return switch (effective) {
            case ARCH_FLUX -> new ModelDefaults("FLUX1/ae.safetensors", "flux", "normal", "euler", name);
            case ARCH_SDXL -> new ModelDefaults("sdxl_vae.safetensors", "stable_diffusion", "normal", "euler", name);
            case ARCH_SD3 -> new ModelDefaults("sd3_vae.safetensors", "sd3", "normal", "euler", name);
            case ARCH_LUMINA2 -> new ModelDefaults("ae.safetensors", "longcat_image", "simple", "euler", name);
            case ARCH_WAN -> new ModelDefaults("wan_2.1_vae.safetensors", "wan", "normal", "euler", name);
            default -> new ModelDefaults(DEFAULT_VAE, "stable_diffusion", "normal", "euler", name);
        };
    }
}
