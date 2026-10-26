package de.tki.comfyuicompanion.service.impl;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Intelligent router providing automated ComfyUI folder destination heuristics
 * and VRAM requirement estimations for models hosted on Hugging Face.
 */
@Component
public class HuggingFaceFolderRouter {

    /** Standard ComfyUI model subdirectories offered in UI dropdown overrides. */
    public static final List<String> AVAILABLE_FOLDERS = List.of(
            "unet",
            "checkpoints",
            "clip",
            "vae",
            "loras",
            "controlnet",
            "upscale_models",
            "diffusion_models",
            "text_encoders"
    );

    /**
     * Determines the most appropriate ComfyUI target subdirectory for a given repository file
     * based on its relative path, file name, repository ID, and metadata tags.
     *
     * @param rfilename relative file path in the repository (e.g. "diffusion_models/model.safetensors")
     * @param tags      tags associated with the repository
     * @param repoId    repository identifier (e.g. "city96/FLUX.1-dev-gguf")
     * @return folder name (e.g. "unet", "clip", "vae", "loras", "checkpoints")
     */
    public String determineTargetFolder(String rfilename, List<String> tags, String repoId) {
        String lowerPath = (rfilename != null ? rfilename : "").toLowerCase(Locale.ROOT);
        String lowerRepo = (repoId != null ? repoId : "").toLowerCase(Locale.ROOT);

        // 1. VAE detection
        if (lowerPath.contains("vae") || lowerRepo.contains("-vae") || lowerRepo.endsWith("/vae")) {
            return "vae";
        }

        // 2. Text Encoder / CLIP detection
        if (lowerPath.contains("clip") || lowerPath.contains("t5") || lowerPath.contains("umt5")
                || lowerPath.contains("text_encoder") || lowerPath.contains("text_encoders")
                || lowerPath.contains("llava") || (lowerPath.contains("qwen") && (lowerPath.contains("vl") || lowerPath.contains("encoder")))) {
            return "clip";
        }

        // 3. LoRA detection
        if (lowerPath.contains("lora") || lowerRepo.contains("lora") || hasTag(tags, "lora")) {
            return "loras";
        }

        // 4. ControlNet detection
        if (lowerPath.contains("controlnet") || lowerRepo.contains("controlnet") || hasTag(tags, "controlnet")) {
            return "controlnet";
        }

        // 5. Upscaler detection
        if (lowerPath.contains("upscale") || lowerPath.contains("esrgan") || lowerRepo.contains("esrgan")) {
            return "upscale_models";
        }

        // 6. UNET / Diffusion model detection (GGUF or specialized Flux/Wan/LTX backbones)
        if (lowerPath.endsWith(".gguf")
                || lowerPath.contains("unet")
                || lowerPath.contains("diffusion_models")
                || lowerPath.contains("flux")
                || lowerPath.contains("wan")
                || lowerPath.contains("ltx")
                || lowerRepo.contains("gguf")
                || lowerRepo.contains("wan")
                || lowerRepo.contains("flux")) {
            return "unet";
        }

        // 7. Default fallback: Checkpoints
        return "checkpoints";
    }

    /**
     * Estimates recommended GPU VRAM or quantization tier based on file size and naming conventions.
     *
     * @param filename  model file name
     * @param sizeBytes exact byte size of the file
     * @return concise descriptive badge (e.g. "~8 GB VRAM", "~12-16 GB VRAM")
     */
    public String determineVramHint(String filename, long sizeBytes) {
        String lower = (filename != null ? filename : "").toLowerCase(Locale.ROOT);

        if (lower.contains("q2_k") || lower.contains("q3_k")) {
            return "⚡ Low VRAM (~6 GB)";
        }
        if (lower.contains("q4_0") || lower.contains("q4_k") || lower.contains("nvfp4") || lower.contains("w4a8")) {
            return "🟢 Standard (~8-12 GB)";
        }
        if (lower.contains("q5_k") || lower.contains("q6_k") || lower.contains("fp8") || lower.contains("int8")) {
            return "🟡 Mid-High (~12-16 GB)";
        }
        if (lower.contains("q8_0") || lower.contains("q8_k")) {
            return "🟣 High (~16-20 GB)";
        }
        if (lower.contains("f16") || lower.contains("bf16") || lower.contains("fp16")) {
            return "🔴 Full Precision (24 GB+)";
        }

        // Fallback by file size
        double gb = sizeBytes / (1024.0 * 1024.0 * 1024.0);
        if (gb <= 0) {
            return "Standard";
        } else if (gb < 2.0) {
            return "⚡ Low VRAM (< 6 GB)";
        } else if (gb <= 6.0) {
            return "🟢 Standard (~8 GB)";
        } else if (gb <= 12.0) {
            return "🟡 Mid-High (~12-16 GB)";
        } else {
            return "🟣 High VRAM (16+ GB)";
        }
    }

    private boolean hasTag(List<String> tags, String tagKeyword) {
        if (tags == null) return false;
        String kw = tagKeyword.toLowerCase(Locale.ROOT);
        return tags.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(kw));
    }

    /**
     * Formats byte counts into human-readable strings (e.g. "11.45 GB", "240.5 MB").
     *
     * @param bytes number of bytes
     * @return formatted size string
     */
    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return bytes + " B";
        }
    }
}
