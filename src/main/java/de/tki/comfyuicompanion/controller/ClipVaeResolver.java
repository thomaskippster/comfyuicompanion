package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IModelArchitectureService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Strategy-driven resolver responsible for model name matching, CLIP type inference,
 * slot-aware multi-CLIP resolution, and selecting compatible CLIP and VAE models.
 */
public class ClipVaeResolver {

    /**
     * Immutable strategy rule defining how to resolve CLIP encoders and types for a model family.
     *
     * @param modelMatcher        predicate testing whether a lower-cased model name belongs to this family
     * @param clipType            ComfyUI CLIPLoader type identifier
     * @param primaryTokens       ordered tokens to match primary/highest-slot encoders in {@code comfyClips}
     * @param fallbackTokens      secondary fallback tokens to match in {@code comfyClips}
     * @param excludedTokens      tokens to exclude when matching fallback encoders
     * @param defaultClipFilename default encoder filename when no local CLIP matches
     */
    public record ClipArchitectureStrategy(
            Predicate<String> modelMatcher,
            String clipType,
            List<String> primaryTokens,
            List<String> fallbackTokens,
            List<String> excludedTokens,
            String defaultClipFilename
    ) {
    }

    private static final List<ClipArchitectureStrategy> CLIP_STRATEGIES = List.of(
            new ClipArchitectureStrategy(
                    m -> m.contains("z_image") || m.contains("z-image") || m.contains("acestep") || m.contains("longcat") || m.contains("lumina"),
                    "lumina2",
                    List.of("gemma", "qwen_3_4b", "lumina"),
                    List.of(),
                    List.of(),
                    "gemma4_e2b_it_bf16.safetensors"
            ),
            new ClipArchitectureStrategy(
                    m -> m.contains("wan"),
                    "wan",
                    List.of("qwen_2.5_vl", "umt5"),
                    List.of(),
                    List.of(),
                    "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors"
            ),
            new ClipArchitectureStrategy(
                    m -> m.contains("flux-2-klein") || m.contains("flux2-klein") || m.contains("klein"),
                    "flux2",
                    List.of("mistral", "flux2", "klein"),
                    List.of(),
                    List.of(),
                    "mistral_3_small_flux2_bf16.safetensors"
            ),
            new ClipArchitectureStrategy(
                    m -> m.contains("flux") || m.contains("schnell") || m.contains("dev"),
                    "flux",
                    List.of("t5xxl", "t5-xxl", "t5_xxl", "t5_fp8", "t5_fp16", "t5xxl_fp8"),
                    List.of("t5"),
                    List.of("umt5", "gemma", "qwen"),
                    "t5xxl_fp8_e4m3fn.safetensors"
            ),
            new ClipArchitectureStrategy(
                    m -> m.contains("sd3") || m.contains("stable_diffusion_3"),
                    "sd3",
                    List.of("t5xxl", "t5-xxl", "t5_xxl", "t5_fp8", "t5_fp16"),
                    List.of("clip_g", "sd3"),
                    List.of(),
                    "t5xxl_fp8_e4m3fn.safetensors"
            )
    );

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
        String a = stripModelFolderPrefix(modelA.replace("\\", "/").toLowerCase(Locale.ROOT));
        String b = stripModelFolderPrefix(modelB.replace("\\", "/").toLowerCase(Locale.ROOT));

        if (a.equals(b)) return true;
        if (a.endsWith("/" + b) || b.endsWith("/" + a)) return true;

        String nameA = a.contains("/") ? a.substring(a.lastIndexOf('/') + 1) : a;
        String nameB = b.contains("/") ? b.substring(b.lastIndexOf('/') + 1) : b;
        return nameA.equalsIgnoreCase(nameB);
    }

    private String stripModelFolderPrefix(String path) {
        if (path.startsWith("checkpoints/")) return path.substring(12);
        if (path.startsWith("diffusion_models/")) return path.substring(17);
        if (path.startsWith("unet/")) return path.substring(5);
        return path;
    }

    /**
     * Resolves the appropriate CLIP type string based on the given clip model name and selected model.
     *
     * @param clipModel      the clip model name
     * @param selectedModel  the selected model name
     * @param comfyClipTypes available clip types from ComfyUI
     * @return the resolved CLIP type string
     */
    public String resolveClipType(String clipModel, String selectedModel, Set<String> comfyClipTypes) {
        String lower = clipModel != null ? clipModel.toLowerCase(Locale.ROOT) : "";

        if (lower.contains("gemma") || lower.contains("qwen_3_4b") || lower.contains("lumina2") || lower.contains("lumina-2")) {
            return "lumina2";
        } else if (lower.contains("wan") || lower.contains("qwen_2.5_vl") || lower.contains("umt5")) {
            return "wan";
        } else if (lower.contains("t5xxl") || lower.contains("t5-xxl") || lower.contains("t5_xxl") || lower.contains("t5_fp8") || lower.contains("t5_fp16")) {
            if (selectedModel != null && (selectedModel.toLowerCase(Locale.ROOT).contains("sd3") || selectedModel.toLowerCase(Locale.ROOT).contains("stable_diffusion_3"))) {
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
            String selLower = selectedModel.toLowerCase(Locale.ROOT);
            ClipArchitectureStrategy matchedStrategy = findStrategy(selLower);
            if (matchedStrategy != null) {
                candidate = matchedStrategy.clipType();
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
     * Resolves the appropriate CLIP model filename for a specific slot in any single- or multi-CLIP loader node.
     *
     * @param selectedModel the selected model name
     * @param slotIndex     zero-based index of the CLIP input slot (e.g., 0 for clip_name1, 1 for clip_name2, 2 for clip_name3)
     * @param totalSlots    total number of CLIP input slots on the loader node
     * @param comfyClips    available clip models from ComfyUI
     * @return the resolved CLIP model filename for the requested slot
     */
    public String resolveClipForSlot(String selectedModel, int slotIndex, int totalSlots, Set<String> comfyClips) {
        if (totalSlots <= 1) {
            return resolveClipForModel(selectedModel, comfyClips);
        }
        if (slotIndex == 0) {
            return findMatchingClipByToken(comfyClips, List.of("clip_l"), List.of(), "clip_l.safetensors");
        }
        if (totalSlots >= 3 && slotIndex == 1) {
            return findMatchingClipByToken(comfyClips, List.of("clip_g"), List.of(), "clip_g.safetensors");
        }
        return resolveClipForModel(selectedModel, comfyClips);
    }

    /**
     * Resolves the appropriate primary/text-encoder CLIP model filename for the given selected model.
     *
     * @param selectedModel the selected model name
     * @param comfyClips    available clip models from ComfyUI
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

        String lower = selectedModel.toLowerCase(Locale.ROOT);
        ClipArchitectureStrategy strategy = findStrategy(lower);
        if (strategy != null) {
            String primary = findMatchingClipByToken(comfyClips, strategy.primaryTokens(), strategy.excludedTokens(), null);
            if (primary != null) return primary;
            if (!strategy.fallbackTokens().isEmpty()) {
                String fallback = findMatchingClipByToken(comfyClips, strategy.fallbackTokens(), strategy.excludedTokens(), null);
                if (fallback != null) return fallback;
            }
            return strategy.defaultClipFilename();
        }

        return findMatchingClipByToken(comfyClips, List.of("clip_l", "sd15", "sdxl"), List.of(), "clip_l.safetensors");
    }

    private ClipArchitectureStrategy findStrategy(String lowerModelName) {
        for (ClipArchitectureStrategy strategy : CLIP_STRATEGIES) {
            if (strategy.modelMatcher().test(lowerModelName)) {
                return strategy;
            }
        }
        return null;
    }

    private String findMatchingClipByToken(Set<String> comfyClips, List<String> requiredTokens, List<String> excludedTokens, String fallbackDefault) {
        if (comfyClips != null) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase(Locale.ROOT);
                boolean excluded = excludedTokens.stream().anyMatch(cLower::contains);
                if (!excluded && requiredTokens.stream().anyMatch(cLower::contains)) {
                    return clip;
                }
            }
        }
        return fallbackDefault;
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
        String expected = "ae.safetensors";
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(modelName);
            if (defaults != null && defaults.vaeName != null) {
                expected = defaults.vaeName;
            }
        } else {
            String lower = modelName.toLowerCase(Locale.ROOT);
            if (lower.contains("wan")) {
                expected = "wan_2.1_vae.safetensors";
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
                String vaeLower = vae.toLowerCase(Locale.ROOT);
                if (vaeLower.contains("flux") || vaeLower.replace("vae", "").contains("ae")) {
                    return vae;
                }
            }
        }
        if (!comfyVaes.isEmpty()) {
            String candidate = comfyVaes.iterator().next();
            String cLower = candidate.toLowerCase(Locale.ROOT);
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
        String lower = modelName.toLowerCase(Locale.ROOT);
        return lower.contains("diffusion_models") || lower.contains("z_image") || lower.contains("z-image")
                || lower.contains("acestep") || lower.contains("flux") || lower.contains("lumina") || lower.contains("wan");
    }
}
