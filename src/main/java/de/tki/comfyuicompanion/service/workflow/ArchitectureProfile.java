package de.tki.comfyuicompanion.service.workflow;

import java.util.List;
import java.util.Locale;

/**
 * Immutable profile defining generation parameters, loader topologies, and matching keywords for an architecture.
 *
 * @param id                    unique profile identifier (e.g. "sdxl", "flux1")
 * @param matchKeywords         keywords used to match model names or architecture tags
 * @param loaderTopology        the structural loader pattern to use
 * @param defaultWidth          default generation width in pixels
 * @param defaultHeight         default generation height in pixels
 * @param defaultSteps          default sampling steps
 * @param defaultCfg            default CFG guidance scale
 * @param defaultSampler        default sampler algorithm name (e.g. "euler", "uni_pc")
 * @param defaultScheduler      default noise scheduler (e.g. "normal", "simple")
 * @param defaultDenoise        default denoise strength
 * @param clipType              optional CLIP type tag passed to CLIPLoader (e.g. "flux2", "wan")
 * @param defaultUnet           fallback UNET filename when modular loading is used
 * @param defaultClip1          fallback primary CLIP/text encoder filename
 * @param defaultClip2          fallback secondary CLIP filename (for dual CLIP loader)
 * @param defaultVae            fallback VAE filename
 * @param defaultCheckpoint     fallback checkpoint filename (for simple checkpoint loader)
 * @param defaultNegativePrompt default negative conditioning text
 */
public record ArchitectureProfile(
        String id,
        List<String> matchKeywords,
        LoaderTopology loaderTopology,
        int defaultWidth,
        int defaultHeight,
        int defaultSteps,
        double defaultCfg,
        String defaultSampler,
        String defaultScheduler,
        double defaultDenoise,
        String clipType,
        String defaultUnet,
        String defaultClip1,
        String defaultClip2,
        String defaultVae,
        String defaultCheckpoint,
        String defaultNegativePrompt
) {
    /**
     * Determines whether this profile supports the given architecture identifier or model name.
     *
     * @param architectureOrName architecture key or model file name
     * @return true if any keyword matches
     */
    public boolean supports(String architectureOrName) {
        if (architectureOrName == null || matchKeywords == null) return false;
        String lower = architectureOrName.toLowerCase(Locale.ROOT);
        return matchKeywords.stream().anyMatch(lower::contains);
    }

    /**
     * Derives a new immutable profile overriding inference defaults (VAE, CLIP, scheduler, sampler).
     *
     * @param vae       optional VAE override
     * @param clip      optional CLIP type override
     * @param scheduler optional scheduler override
     * @param sampler   optional sampler algorithm override
     * @return new {@link ArchitectureProfile} instance
     */
    public ArchitectureProfile withDefaults(String vae, String clip, String scheduler, String sampler) {
        return new ArchitectureProfile(
                this.id,
                this.matchKeywords,
                this.loaderTopology,
                this.defaultWidth,
                this.defaultHeight,
                this.defaultSteps,
                this.defaultCfg,
                sampler != null && !sampler.isBlank() ? sampler : this.defaultSampler,
                scheduler != null && !scheduler.isBlank() ? scheduler : this.defaultScheduler,
                this.defaultDenoise,
                clip != null && !clip.isBlank() ? clip : this.clipType,
                this.defaultUnet,
                this.defaultClip1,
                this.defaultClip2,
                vae != null && !vae.isBlank() ? vae : this.defaultVae,
                this.defaultCheckpoint,
                this.defaultNegativePrompt
        );
    }
}
