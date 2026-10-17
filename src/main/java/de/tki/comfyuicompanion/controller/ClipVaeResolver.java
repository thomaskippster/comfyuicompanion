package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IModelArchitectureService;

import java.util.Set;

/**
 * Helper component responsible for model name matching, CLIP type inference,
 * and selecting compatible CLIP and VAE models based on model architectures and filenames.
 */
public class ClipVaeResolver {

    private final IModelArchitectureService modelArchitectureService;

    /**
     * Constructs a new {@link ClipVaeResolver}.
     *
     * @param modelArchitectureService optional architecture service providing model defaults
     */
    public ClipVaeResolver(IModelArchitectureService modelArchitectureService) {
        this.modelArchitectureService = modelArchitectureService;
    }

    /**
     * Checks whether two model names refer to the same logical model file, ignoring directory prefixes.
     *
     * @param modelA the first model name
     * @param modelB the second model name
     * @return true if the models match
     */
    public boolean modelsMatch(String modelA, String modelB) {
        if (modelA == null || modelB == null) return false;
        String a = modelA.replace("\\", "/").toLowerCase();
        String b = modelB.replace("\\", "/").toLowerCase();

        if (a.startsWith("checkpoints/")) a = a.substring(12);
        if (a.startsWith("diffusion_models/")) a = a.substring(17);
        if (a.startsWith("unet/")) a = a.substring(5);

        if (b.startsWith("checkpoints/")) b = b.substring(12);
        if (b.startsWith("diffusion_models/")) b = b.substring(17);
        if (b.startsWith("unet/")) b = b.substring(5);

        if (a.equals(b)) return true;
        if (a.endsWith("/" + b) || b.endsWith("/" + a)) return true;

        String nameA = a.contains("/") ? a.substring(a.lastIndexOf('/') + 1) : a;
        String nameB = b.contains("/") ? b.substring(b.lastIndexOf('/') + 1) : b;
        return nameA.equalsIgnoreCase(nameB);
    }

    /**
     * Resolves the appropriate CLIP type string based on the given clip model name and selected model.
     *
     * @param clipModel     the clip model name
     * @param selectedModel the selected model name
     * @param comfyClipTypes available clip types from ComfyUI
     * @return the resolved CLIP type string
     */
    public String resolveClipType(String clipModel, String selectedModel, Set<String> comfyClipTypes) {
        String lower = clipModel != null ? clipModel.toLowerCase() : "";

        if (lower.contains("gemma") || lower.contains("qwen_3_4b") || lower.contains("lumina2") || lower.contains("lumina-2")) {
            return "lumina2";
        } else if (lower.contains("wan") || lower.contains("qwen_2.5_vl") || lower.contains("umt5")) {
            return "wan";
        } else if (lower.contains("t5xxl") || lower.contains("t5-xxl") || lower.contains("t5_xxl") || lower.contains("t5_fp8") || lower.contains("t5_fp16")) {
            if (selectedModel != null && (selectedModel.toLowerCase().contains("sd3") || selectedModel.toLowerCase().contains("stable_diffusion_3"))) {
                return "sd3";
            }
            return "flux";
        } else if (lower.contains("mistral") || lower.contains("flux2") || lower.contains("klein")) {
            return "flux2";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            return "sd3";
        } else if (lower.contains("clip_l") || lower.contains("clip_g") || lower.contains("vi-clip") || lower.contains("sd15") || lower.contains("sdxl")) {
            return "stable_diffusion";
        } else if (lower.contains("mochi")) {
            return "mochi";
        } else if (lower.contains("ltxv") || lower.contains("ltx")) {
            return "ltxv";
        } else if (lower.contains("cosmos")) {
            return "cosmos";
        }

        String candidate = "stable_diffusion";
        if (selectedModel != null) {
            String selLower = selectedModel.toLowerCase();
            if (selLower.contains("flux-2-klein") || selLower.contains("flux2-klein") || selLower.contains("klein")) {
                candidate = "flux2";
            } else if (selLower.contains("flux") || selLower.contains("schnell")) {
                candidate = "flux";
            } else if (selLower.contains("sd3") || selLower.contains("stable_diffusion_3")) {
                candidate = "sd3";
            } else if (selLower.contains("longcat") || selLower.contains("lumina") || selLower.contains("acestep") || selLower.contains("z_image") || selLower.contains("z-image")) {
                candidate = "lumina2";
            } else if (selLower.contains("wan")) {
                candidate = "wan";
            } else if (modelArchitectureService != null) {
                IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(selectedModel);
                if (defaults != null && defaults.clipType != null) {
                    candidate = defaults.clipType;
                }
            }
        }

        if (comfyClipTypes.contains(candidate)) {
            return candidate;
        }
        if (comfyClipTypes.contains("stable_diffusion") && candidate.equals("stable_diffusion")) {
            return "stable_diffusion";
        }
        if (!comfyClipTypes.isEmpty() && !comfyClipTypes.contains(candidate) && !candidate.equals("flux")
                && !candidate.equals("lumina2") && !candidate.equals("wan") && !candidate.equals("sd3") && !candidate.equals("flux2")) {
            return comfyClipTypes.iterator().next();
        }
        return candidate;
    }

    /**
     * Resolves the appropriate CLIP model filename for the given selected model.
     *
     * @param selectedModel the selected model name
     * @param comfyClips available clip models from ComfyUI
     * @return the resolved CLIP model filename
     */
    public String resolveClipForModel(String selectedModel, Set<String> comfyClips) {
        if (selectedModel == null) return "clip_l.safetensors";
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(selectedModel);
            if (defaults != null && defaults.clipType != null) {
                String candidate = defaults.clipType;
                for (String clip : comfyClips) {
                    String clipClean = clip.replace("\\", "/");
                    if (clipClean.equalsIgnoreCase(candidate) || clipClean.endsWith("/" + candidate)) {
                        return clip;
                    }
                }
            }
        }

        String lower = selectedModel.toLowerCase();
        if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("gemma") || cLower.contains("qwen_3_4b") || cLower.contains("lumina")) {
                    return clip;
                }
            }
            return "gemma4_e2b_it_bf16.safetensors";
        } else if (lower.contains("wan")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("qwen_2.5_vl") || cLower.contains("umt5")) {
                    return clip;
                }
            }
            return "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors";
        } else if (lower.contains("flux-2-klein") || lower.contains("flux2-klein") || lower.contains("klein")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("mistral") || cLower.contains("flux2") || cLower.contains("klein")) {
                    return clip;
                }
            }
            return "mistral_3_small_flux2_bf16.safetensors";
        } else if (lower.contains("flux") || lower.contains("schnell") || lower.contains("dev")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("t5_xxl") ||
                        cLower.contains("t5_fp8") || cLower.contains("t5_fp16") || cLower.contains("t5xxl_fp8")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5") && !cLower.contains("umt5") && !cLower.contains("gemma") && !cLower.contains("qwen")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("clip_g") || cLower.contains("sd3")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        }

        for (String clip : comfyClips) {
            String cLower = clip.toLowerCase();
            if (cLower.contains("clip_l") || cLower.contains("sd15") || cLower.contains("sdxl")) {
                return clip;
            }
        }
        return "clip_l.safetensors";
    }

    /**
     * Resolves the appropriate VAE model filename for the given selected model.
     *
     * @param modelName the selected model name
     * @param comfyVaes available VAE models from ComfyUI
     * @return the resolved VAE model filename
     */
    public String resolveVaeForModel(String modelName, Set<String> comfyVaes) {
        if (modelName == null) return "ae.safetensors";
        String expected = "FLUX1/ae.safetensors";
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(modelName);
            if (defaults != null && defaults.vaeName != null) {
                expected = defaults.vaeName;
            }
        } else {
            String lower = modelName.toLowerCase();
            if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
                expected = "ae.safetensors";
            } else if (lower.contains("wan")) {
                expected = "wan_2.1_vae.safetensors";
            } else if (lower.contains("flux")) {
                expected = "FLUX1/ae.safetensors";
            }
        }

        String expectedClean = expected.replace("\\", "/");
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedClean) || vaeClean.endsWith("/" + expectedClean)) {
                return vae;
            }
        }
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedName) || vaeClean.endsWith("/" + expectedName)) {
                return vae;
            }
        }
        boolean isExpectedAeOrFlux = expectedName.contains("flux") || expectedName.replace("vae", "").contains("ae");
        if (isExpectedAeOrFlux) {
            for (String vae : comfyVaes) {
                String vaeLower = vae.toLowerCase();
                if (vaeLower.contains("flux") || vaeLower.replace("vae", "").contains("ae")) {
                    return vae;
                }
            }
        }
        if (!comfyVaes.isEmpty()) {
            String candidate = comfyVaes.iterator().next();
            String cLower = candidate.toLowerCase();
            if (expectedName.contains("wan") && cLower.contains("wan")) {
                return candidate;
            }
            if (isExpectedAeOrFlux && (cLower.contains("flux") || cLower.replace("vae", "").contains("ae"))) {
                return candidate;
            }
        }
        return expected;
    }

    /**
     * Determines whether the given model name refers to a diffusion model.
     *
     * @param modelName name or path of the model
     * @return true if it is a diffusion model
     */
    public boolean isDiffusionModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        return lower.contains("diffusion_models") || lower.contains("z_image") || lower.contains("z-image")
                || lower.contains("acestep") || lower.contains("flux") || lower.contains("lumina") || lower.contains("wan");
    }
}
