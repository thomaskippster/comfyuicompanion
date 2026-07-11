package de.tki.comfymodels.service.impl;

import java.util.List;

/**
 * Pure-function recommender that picks the best Qwen-TTS Hugging Face model
 * for the user's available VRAM. The catalogue covers the most common Qwen
 * TTS / audio checkpoints; the recommender returns the highest-quality model
 * that fits comfortably into the given VRAM budget (target: ~75% of VRAM
 * so the OS and other GPU workloads have headroom).
 *
 * <p>This class has no I/O and no side effects. Tests can construct it
 * directly with any VRAM value.
 */
public final class QwenTtsModelRecommender {

    /**
     * A single Qwen-TTS model recommendation. The {@code hfRepo} value is
     * what gets stored in {@code qwen_tts_model_repo}.
     */
    public static final class ModelSpec {
        public final String name;        // human-readable name
        public final String hfRepo;      // Hugging Face repo id
        public final String quantization; // "FP16", "FP8", "INT4", "BF16"
        public final long approxBytes;   // approximate VRAM requirement in bytes
        public final String description; // human-readable description

        public ModelSpec(String name, String hfRepo, String quantization,
                         long approxBytes, String description) {
            this.name = name;
            this.hfRepo = hfRepo;
            this.quantization = quantization;
            this.approxBytes = approxBytes;
            this.description = description;
        }

        @Override
        public String toString() {
            return name + " (" + quantization + ", ~" + (approxBytes / (1024L * 1024L)) + " MB): " + description;
        }
    }

    /**
     * Model catalogue, ordered from smallest to largest VRAM requirement.
     * Each spec is a self-contained checkpoint that the ComfyUI-Qwen-TTS
     * custom node can load. Numbers are conservative estimates including
     * KV cache and a small headroom for context.
     */
    public static final List<ModelSpec> CATALOG = List.of(
        new ModelSpec("Qwen2-Audio-1.5B (FP16)",
            "Qwen/Qwen2-Audio-1.5B-Instruct",
            "FP16",
            3L * 1024L * 1024L * 1024L,                  // ~3 GB
            "Kleinstes Modell. Geringste Qualität, aber extrem schnell."),
        new ModelSpec("Qwen2.5-Omni-3B (FP16)",
            "Qwen/Qwen2.5-Omni-3B",
            "FP16",
            6L * 1024L * 1024L * 1024L,                  // ~6 GB
            "Mittelklasse. Gute Qualität für 8-12 GB VRAM."),
        new ModelSpec("Qwen2-Audio-7B (FP8)",
            "Qwen/Qwen2-Audio-7B-Instruct-fp8",
            "FP8",
            7L * 1024L * 1024L * 1024L,                  // ~7 GB
            "High-quality 7B Modell in FP8-Quantisierung. Passt komfortabel in 12-16 GB."),
        new ModelSpec("Qwen2.5-Omni-7B (FP8)",
            "Qwen/Qwen2.5-Omni-7B-fp8",
            "FP8",
            8L * 1024L * 1024L * 1024L,                  // ~8 GB
            "Multimodales 7B Modell (FP8). Sehr gute Qualität bei moderatem VRAM."),
        new ModelSpec("Qwen2-Audio-7B (BF16)",
            "Qwen/Qwen2-Audio-7B-Instruct",
            "BF16",
            14L * 1024L * 1024L * 1024L,                 // ~14 GB
            "Volles 7B Modell. Braucht mindestens 16 GB VRAM - knapp."),
        new ModelSpec("Qwen2.5-Omni-7B (BF16)",
            "Qwen/Qwen2.5-Omni-7B",
            "BF16",
            15L * 1024L * 1024L * 1024L,                 // ~15 GB
            "Multimodales 7B in voller Präzision. Maximum an Qualität, benötigt 20+ GB.")
    );

    /**
     * Picks the best model for the given VRAM budget. Returns the highest-quality
     * model whose approximate memory requirement is at most 75% of the budget.
     * If even the smallest model does not fit, returns the smallest entry as a
     * graceful fallback (the user will get an out-of-memory error from the runtime,
     * which is preferable to a silent NPE).
     */
    public static ModelSpec recommend(long vramBytes) {
        if (vramBytes <= 0) {
            // No GPU detected: return the smallest, safest option.
            return CATALOG.get(0);
        }
        long budget = (long) (vramBytes * 0.75);
        ModelSpec best = CATALOG.get(0);
        for (ModelSpec spec : CATALOG) {
            if (spec.approxBytes <= budget) {
                best = spec;
            } else {
                break;
            }
        }
        return best;
    }

    /**
     * Formats a VRAM byte count as a human-readable GiB string.
     */
    public static String formatVram(long vramBytes) {
        if (vramBytes <= 0) return "unbekannt";
        double gib = vramBytes / 1024.0 / 1024.0 / 1024.0;
        return String.format(java.util.Locale.US, "%.1f GiB", gib);
    }
}