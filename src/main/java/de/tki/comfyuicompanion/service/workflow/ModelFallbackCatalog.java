package de.tki.comfyuicompanion.service.workflow;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Data-driven catalog encapsulating fallback download URL resolution and size estimation
 * rules using immutable Java 21 records and predicate strategies.
 */
public final class ModelFallbackCatalog {

    /**
     * Immutable rule mapping a filename predicate to a download URL.
     *
     * @param matcher predicate evaluated against the lower-cased model filename
     * @param url     authoritative fallback download URL
     */
    public record UrlResolutionRule(Predicate<String> matcher, String url) {
        /**
         * Evaluates whether this rule matches the given lower-cased filename.
         *
         * @param lowerFilename lower-cased filename
         * @return true if matched
         */
        public boolean matches(String lowerFilename) {
            return matcher.test(lowerFilename);
        }
    }

    /**
     * Immutable rule mapping a filename predicate to an estimated size resolver.
     *
     * @param matcher      predicate evaluated against the lower-cased model filename
     * @param sizeResolver function computing the human-readable size estimate
     */
    public record SizeEstimationRule(Predicate<String> matcher, Function<String, String> sizeResolver) {
        /**
         * Evaluates whether this rule matches the given lower-cased filename.
         *
         * @param lowerFilename lower-cased filename
         * @return true if matched
         */
        public boolean matches(String lowerFilename) {
            return matcher.test(lowerFilename);
        }
    }

    private static final List<UrlResolutionRule> URL_RULES = List.of(
            new UrlResolutionRule(fn -> fn.equals("depth_anything_vitl14.pth"),
                    "https://huggingface.co/spaces/LiheYoung/Depth-Anything/resolve/main/checkpoints/depth_anything_vitl14.pth"),
            new UrlResolutionRule(fn -> fn.equals("wan2.2-ti2v-5b-controlnet-depth-v1.safetensors") || fn.contains("controlnet-depth-v1"),
                    "https://huggingface.co/TheDenk/wan2.2-ti2v-5b-controlnet-depth-v1/resolve/main/diffusion_pytorch_model.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("fastwanfullattn") || fn.equals("wan2_2-ti2v-5b-fastwanfullattn_bf16.safetensors"),
                    "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/FastWan/Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("umt5-xxl-enc-bf16.safetensors") || fn.equals("umt5_xxl_fp16.safetensors"),
                    "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/umt5-xxl-enc-bf16.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("wan2_2-ti2v-5b_fp16.safetensors"),
                    "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/Wan2_2-TI2V-5B_fp16.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("wan2_2_vae_bf16.safetensors") || fn.equals("wan_2.1_vae.safetensors"),
                    "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/vae/wan_2.1_vae.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("ae.safetensors"),
                    "https://huggingface.co/receptektas/black-forest-labs-ae_safetensors/resolve/main/ae.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("clip_l.safetensors"),
                    "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("clip_g.safetensors"),
                    "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/text_encoders/clip_g.safetensors"),
            new UrlResolutionRule(fn -> fn.equals("t5xxl_fp16.safetensors"),
                    "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/text_encoders/t5xxl_fp16.safetensors"),
            new UrlResolutionRule(fn -> fn.startsWith("t5xxl_fp8"),
                    "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("sd3.5_large") || fn.contains("sd35_large"),
                    "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/sd3.5_large_fp8_scaled.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("sd3.5_medium") || fn.contains("sd35_medium"),
                    "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/sd3.5_medium_incl_clips_t5xxlfp8scaled.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("hunyuan_video_720"),
                    "https://huggingface.co/Kijai/HunyuanVideo_comfy/resolve/main/hunyuan_video_720_cfgdistill_bf16.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("wan2.1_t2v_1.3b") || fn.contains("wan21_t2v_1.3b"),
                    "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_1.3B_bf16.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("wan2.1_t2v_14b") || fn.contains("wan21_t2v_14b"),
                    "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_14B_bf16.safetensors"),
            new UrlResolutionRule(fn -> fn.contains("umt5_xxl_fp8"),
                    "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp8_e4m3fn.safetensors")
    );

    private static final List<SizeEstimationRule> SIZE_RULES = List.of(
            new SizeEstimationRule(fn -> fn.endsWith(".json"), fn -> "14.2 KB"),
            new SizeEstimationRule(fn -> fn.contains("vae") || fn.startsWith("ae."), fn -> fn.contains("wan") ? "254 MB" : "335 MB"),
            new SizeEstimationRule(fn -> fn.contains("clip_l"), fn -> "246 MB"),
            new SizeEstimationRule(fn -> fn.contains("clip_g"), fn -> "1.39 GB"),
            new SizeEstimationRule(fn -> fn.contains("qwen3.5_9b") || fn.contains("qwen3vl_8b"), fn -> "8.8 GB"),
            new SizeEstimationRule(fn -> fn.contains("qwen3vl_4b") || fn.contains("qwen_3_4b") || fn.contains("qwen_3.5_4b"), fn -> "4.8 GB"),
            new SizeEstimationRule(fn -> fn.contains("umt5"), fn -> fn.contains("fp8") ? "6.27 GB" : "10.6 GB"),
            new SizeEstimationRule(fn -> fn.contains("t5xxl"), fn -> (fn.contains("fp16") || fn.contains("bf16")) ? "9.5 GB" : "4.8 GB"),
            new SizeEstimationRule(fn -> fn.contains("depth_anything"), fn -> fn.contains("vitl") ? "1.34 GB" : (fn.contains("vitb") ? "375 MB" : "670 MB")),
            new SizeEstimationRule(fn -> fn.contains("controlnet") || fn.contains("t2i_adapter"), fn -> (fn.contains("5b") || fn.contains("wan")) ? "2.5 GB" : "1.4 GB"),
            new SizeEstimationRule(fn -> fn.contains("14b"), fn -> (fn.contains("fp8") || fn.contains("int8")) ? "14.3 GB" : "28.6 GB"),
            new SizeEstimationRule(fn -> fn.contains("5b"), fn -> (fn.contains("fp8") || fn.contains("int8")) ? "5.0 GB" : "10.0 GB"),
            new SizeEstimationRule(fn -> fn.contains("1.3b"), fn -> "2.7 GB"),
            new SizeEstimationRule(fn -> fn.contains("upscale") || fn.contains("esrgan") || fn.contains("swinir") || fn.contains("4x"), fn -> "67 MB"),
            new SizeEstimationRule(fn -> fn.contains("lora") || fn.contains("lycoris") || fn.contains("locon"), fn -> "220 MB"),
            new SizeEstimationRule(fn -> fn.contains("int8") || fn.contains("nvfp4") || fn.contains("fp8"), fn -> "6.8 GB"),
            new SizeEstimationRule(fn -> fn.contains("bf16") || fn.contains("fp16"), fn -> "11.9 GB"),
            new SizeEstimationRule(fn -> fn.endsWith(".pth") || fn.endsWith(".pt") || fn.endsWith(".onnx"), fn -> "400 MB"),
            new SizeEstimationRule(fn -> fn.endsWith(".safetensors") || fn.endsWith(".ckpt") || fn.endsWith(".gguf") || fn.endsWith(".sft"), fn -> "2.4 GB")
    );

    private ModelFallbackCatalog() {
    }

    /**
     * Resolves an authoritative Hugging Face download URL for a model filename using the rule catalog.
     *
     * @param filename model filename
     * @return resolved remote URL or null if no rule matched
     */
    public static String resolveUrl(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String fn = filename.toLowerCase(Locale.ROOT);
        for (UrlResolutionRule rule : URL_RULES) {
            if (rule.matches(fn)) {
                return rule.url();
            }
        }
        return null;
    }

    /**
     * Estimates a human-readable model size string based on declarative rule matching.
     *
     * @param filename model filename
     * @return estimated formatted size or null if uncategorized
     */
    public static String estimateSize(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String fn = filename.toLowerCase(Locale.ROOT);
        for (SizeEstimationRule rule : SIZE_RULES) {
            if (rule.matches(fn)) {
                return rule.sizeResolver().apply(fn);
            }
        }
        return null;
    }
}
