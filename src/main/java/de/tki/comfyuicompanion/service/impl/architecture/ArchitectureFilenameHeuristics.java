package de.tki.comfyuicompanion.service.impl.architecture;

import de.tki.comfyuicompanion.domain.ModelArchitecture;

/**
 * Stateless, keyword based fallback classifier that guesses a {@link ModelArchitecture}
 * purely from a model file name. It is used when no explicit mapping rule matched.
 */
public final class ArchitectureFilenameHeuristics {

    /**
     * Guesses the architecture of a model from keywords contained in its file name.
     *
     * @param filename the model file name (path segments are not stripped by this method)
     * @return the detected architecture, or {@link ModelArchitecture#ARCH_UNKNOWN} if no keyword matched
     */
    public ModelArchitecture detect(String filename) {
        if (filename == null) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        String lower = filename.toLowerCase();
        if (containsAny(lower, "flux", "schnell", "dev")) {
            return ModelArchitecture.ARCH_FLUX;
        }
        if (containsAny(lower, "sdxl", "xl", "pony", "juggernaut", "ernie")) {
            return ModelArchitecture.ARCH_SDXL;
        }
        if (containsAny(lower, "sd3", "stable_diffusion_3")) {
            return ModelArchitecture.ARCH_SD3;
        }
        if (containsAny(lower, "hunyuan")) {
            return ModelArchitecture.ARCH_HUNYUAN;
        }
        if (containsAny(lower, "wan", "ltx")) {
            return ModelArchitecture.ARCH_WAN;
        }
        if (containsAny(lower, "longcat", "lumina", "qwen", "firered", "z_image", "acestep")) {
            return ModelArchitecture.ARCH_LUMINA2;
        }
        if (containsAny(lower, "sd15", "1.5", "v1-5")) {
            return ModelArchitecture.ARCH_SD15;
        }
        return ModelArchitecture.ARCH_UNKNOWN;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
