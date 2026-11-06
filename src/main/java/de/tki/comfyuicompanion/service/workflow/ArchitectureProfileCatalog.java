package de.tki.comfyuicompanion.service.workflow;

import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enterprise catalog maintaining predefined and dynamically registered architecture profiles.
 * Optionally integrates with {@link IModelArchitectureService} to leverage live model defaults and architecture detection.
 */
@Component
public class ArchitectureProfileCatalog {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureProfileCatalog.class);

    private final List<ArchitectureProfile> profiles = new CopyOnWriteArrayList<>();
    private final ArchitectureProfile defaultProfile;
    private final IModelArchitectureService architectureService;

    @Autowired
    public ArchitectureProfileCatalog(@Autowired(required = false) IModelArchitectureService architectureService) {
        this.architectureService = architectureService;

        // 1. Stable Diffusion 1.5
        ArchitectureProfile sd15 = new ArchitectureProfile(
                "sd15",
                List.of("1.5", "1-5", "sd 1", "sd1", "arch_sd15"),
                LoaderTopology.CHECKPOINT_SIMPLE,
                512, 512, 20, 7.0, "euler", "normal", 1.0,
                null, null, null, null, null,
                "v1-5-pruned-emaonly.safetensors",
                "blurry, low quality, bad anatomy, text, watermark"
        );

        // 2. Stable Diffusion XL (Default fallback)
        ArchitectureProfile sdxl = new ArchitectureProfile(
                "sdxl",
                List.of("sdxl", "xl", "pony", "illustrious", "arch_sdxl"),
                LoaderTopology.CHECKPOINT_SIMPLE,
                1024, 1024, 25, 7.0, "euler", "normal", 1.0,
                null, null, null, null, null,
                "sd_xl_base_1.0.safetensors",
                "blurry, low quality, bad anatomy, text, watermark"
        );

        // 3. Stable Diffusion 3
        ArchitectureProfile sd3 = new ArchitectureProfile(
                "sd3",
                List.of("sd3", "sd 3", "stable-diffusion-3", "arch_sd3"),
                LoaderTopology.CHECKPOINT_SIMPLE,
                1024, 1024, 28, 4.5, "euler", "sgm_uniform", 1.0,
                null, null, null, null, null,
                "sd3_medium.safetensors",
                "blurry, low quality"
        );

        // 4. Flux.1 (Dev / Schnell)
        ArchitectureProfile flux1 = new ArchitectureProfile(
                "flux1",
                List.of("flux.1", "flux1", "flux-schnell", "flux-dev", "arch_flux"),
                LoaderTopology.UNET_DUAL_CLIP,
                1024, 1024, 20, 1.0, "euler", "simple", 1.0,
                "flux", "flux1-dev.safetensors", "t5xxl_fp8_e4m3fn.safetensors", "clip_l.safetensors", "ae.safetensors",
                null,
                ""
        );

        // 5. Flux.2 / Klein
        ArchitectureProfile flux2Klein = new ArchitectureProfile(
                "flux2_klein",
                List.of("flux-2", "flux2", "klein"),
                LoaderTopology.UNET_SINGLE_CLIP,
                1024, 1024, 20, 1.0, "euler", "simple", 1.0,
                "flux2", "flux-2-klein-base-9b-fp8.safetensors", "qwen_3_8b_fp8mixed.safetensors", null, "flux2-vae.safetensors",
                null,
                ""
        );

        // 6. Wan 2.1 (T2V / Video)
        ArchitectureProfile wan21 = new ArchitectureProfile(
                "wan21",
                List.of("wan", "wan2.1", "wan-2.1", "arch_wan"),
                LoaderTopology.UNET_SINGLE_CLIP,
                832, 480, 30, 6.0, "uni_pc", "simple", 1.0,
                "wan", "wan2.1_t2v_14B_fp8.safetensors", "umt5_xxl_fp8.safetensors", null, "wan_2.1_vae.safetensors",
                null,
                ""
        );

        // 7. Lumina2
        ArchitectureProfile lumina2 = new ArchitectureProfile(
                "lumina2",
                List.of("lumina", "lumina2", "longcat", "arch_lumina2"),
                LoaderTopology.UNET_SINGLE_CLIP,
                1024, 1024, 25, 4.0, "euler", "simple", 1.0,
                "longcat_image", "lumina2_model.safetensors", "qwen_2.5_7b.safetensors", null, "ae.safetensors",
                null,
                ""
        );

        // 8. Hunyuan
        ArchitectureProfile hunyuan = new ArchitectureProfile(
                "hunyuan",
                List.of("hunyuan", "arch_hunyuan"),
                LoaderTopology.UNET_DUAL_CLIP,
                1024, 1024, 30, 6.0, "euler", "normal", 1.0,
                "hunyuan", "hunyuan_model.safetensors", "clip_l.safetensors", "t5xxl_fp8_e4m3fn.safetensors", "ae.safetensors",
                null,
                ""
        );

        profiles.addAll(List.of(sd15, sdxl, sd3, flux1, flux2Klein, wan21, lumina2, hunyuan));
        this.defaultProfile = sdxl;
    }

    public ArchitectureProfileCatalog() {
        this(null);
    }

    /**
     * Resolves the matching architecture profile for an architecture identifier or model name.
     * Integrates with {@link IModelArchitectureService} when available to inherit live model defaults.
     *
     * @param architectureOrName architecture key or model file name
     * @return the resolved profile, or the default SDXL profile if no keyword matched
     */
    public ArchitectureProfile resolveProfile(String architectureOrName) {
        if (architectureOrName == null || architectureOrName.isBlank()) {
            return defaultProfile;
        }

        ModelDefaults defaults = null;
        String detectedArchName = null;

        if (architectureService != null) {
            try {
                defaults = architectureService.getDefaultsForModel(architectureOrName);
                ModelArchitecture detected = architectureService.detectArchitecture(architectureOrName);
                if (detected != null && detected != ModelArchitecture.ARCH_UNKNOWN) {
                    detectedArchName = detected.name().toLowerCase();
                }
            } catch (Exception ex) {
                log.debug("ModelArchitectureService lookup exception: {}", ex.getMessage());
            }
        }

        ArchitectureProfile base = matchProfile(detectedArchName != null ? detectedArchName : architectureOrName);

        if (defaults != null) {
            return base.withDefaults(defaults.vaeName, defaults.clipType, defaults.scheduler, defaults.samplerName);
        }
        return base;
    }

    private ArchitectureProfile matchProfile(String key) {
        for (ArchitectureProfile p : profiles) {
            if (p.supports(key)) {
                return p;
            }
        }
        return defaultProfile;
    }

    /**
     * Registers a new custom architecture profile into the catalog.
     *
     * @param profile the profile to register
     */
    public void registerProfile(ArchitectureProfile profile) {
        if (profile != null) {
            profiles.add(0, profile);
        }
    }

    /**
     * Returns an unmodifiable list of all currently registered profiles.
     */
    public List<ArchitectureProfile> getAllProfiles() {
        return List.copyOf(profiles);
    }

    /**
     * Finds a profile by its exact ID.
     */
    public Optional<ArchitectureProfile> findById(String id) {
        return profiles.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst();
    }
}
