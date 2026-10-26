package de.tki.comfyuicompanion.service.metadata;

import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader.ExtractedPngWorkflow;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy implementation that extracts generation metadata from standard {@code parameters}
 * text chunks produced by external WebUI generators and Civitai image downloads.
 * Automatically normalizes sampler/scheduler names and synthesizes an executable ComfyUI API graph.
 */
@Component
public class WebUiParametersMetadataStrategy implements IImageMetadataStrategy {

    private static final Pattern KV_PATTERN = Pattern.compile("([A-Za-z0-9 _-]+):\\s*(\"[^\"]*\"|[^,]+)");
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public Optional<ExtractedPngWorkflow> extract(Map<String, String> textChunks) {
        if (textChunks == null || textChunks.isEmpty()) {
            return Optional.empty();
        }
        String raw = firstNonBlank(
                textChunks.get("parameters"),
                textChunks.get("Parameters"),
                textChunks.get("Comment"),
                textChunks.get("Description")
        );
        if (raw == null || !raw.contains("Steps:")) {
            return Optional.empty();
        }
        return Optional.of(parseParametersBlock(raw));
    }

    /**
     * Parses a multi-line {@code parameters} block into a normalized {@link ExtractedPngWorkflow}.
     *
     * @param raw the raw parameters string
     * @return normalized workflow record with synthesized ComfyUI API graph
     */
    public ExtractedPngWorkflow parseParametersBlock(String raw) {
        String normalized = raw.replace("\r\n", "\n").trim();
        int stepsIdx = normalized.lastIndexOf("\nSteps:");
        if (stepsIdx < 0 && normalized.startsWith("Steps:")) {
            stepsIdx = 0;
        }

        String promptPart = stepsIdx > 0 ? normalized.substring(0, stepsIdx).trim() : "";
        String kvLine = stepsIdx >= 0 ? normalized.substring(stepsIdx).trim() : normalized;

        String positive = promptPart;
        String negative = "";
        int negIdx = findNegativePromptSplit(promptPart);
        if (negIdx >= 0) {
            positive = promptPart.substring(0, negIdx).trim();
            negative = promptPart.substring(negIdx + "Negative prompt:".length()).trim();
        }

        Map<String, String> kvMap = parseKeyValuePairs(kvLine);

        Integer steps = parseIntSafe(kvMap.get("steps"));
        Double cfg = parseDoubleSafe(kvMap.get("cfg scale"));
        Long seed = parseLongSafe(kvMap.get("seed"));
        Double denoise = parseDoubleSafe(kvMap.get("denoising strength"));
        String rawSampler = kvMap.get("sampler");
        String rawScheduler = kvMap.get("schedule type");
        String modelName = firstNonBlank(kvMap.get("model"), kvMap.get("checkpoint"));
        if (modelName != null && !modelName.contains(".")) {
            modelName = modelName + ".safetensors";
        }

        Integer width = null;
        Integer height = null;
        String sizeVal = kvMap.get("size");
        if (sizeVal != null) {
            Matcher sm = SIZE_PATTERN.matcher(sizeVal);
            if (sm.find()) {
                width = parseIntSafe(sm.group(1));
                height = parseIntSafe(sm.group(2));
            }
        }

        String[] mappedSamplerScheduler = mapSamplerAndScheduler(rawSampler, rawScheduler);
        String comfySampler = mappedSamplerScheduler[0];
        String comfyScheduler = mappedSamplerScheduler[1];

        String synthesizedJson = synthesizeComfyApiPrompt(
                positive, negative, modelName, seed, steps, cfg, denoise, width, height, comfySampler, comfyScheduler
        );

        return new ExtractedPngWorkflow(
                positive,
                negative,
                modelName,
                seed,
                steps,
                cfg,
                denoise,
                width,
                height,
                1,
                comfySampler,
                comfyScheduler,
                synthesizedJson,
                null
        );
    }

    private static int findNegativePromptSplit(String promptPart) {
        String lower = promptPart.toLowerCase(Locale.ROOT);
        return lower.indexOf("negative prompt:");
    }

    private static Map<String, String> parseKeyValuePairs(String kvLine) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = KV_PATTERN.matcher(kvLine);
        while (m.find()) {
            String key = m.group(1).trim().toLowerCase(Locale.ROOT);
            String val = m.group(2).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1).trim();
            }
            map.put(key, val);
        }
        return map;
    }

    /**
     * Maps human-readable WebUI sampler and schedule names to ComfyUI sampler/scheduler identifiers.
     *
     * @param rawSampler   raw sampler label (e.g. "DPM++ 2M Karras", "Euler a")
     * @param rawScheduler explicit schedule type if present
     * @return two-element array: [comfySampler, comfyScheduler]
     */
    public static String[] mapSamplerAndScheduler(String rawSampler, String rawScheduler) {
        String s = rawSampler != null ? rawSampler.trim().toLowerCase(Locale.ROOT) : "euler";
        String sched = rawScheduler != null && !rawScheduler.isBlank()
                ? rawScheduler.trim().toLowerCase(Locale.ROOT)
                : null;

        if (sched == null || "automatic".equals(sched)) {
            if (s.contains("karras")) sched = "karras";
            else if (s.contains("exponential")) sched = "exponential";
            else if (s.contains("sgm uniform") || s.contains("sgm_uniform")) sched = "sgm_uniform";
            else if (s.contains("beta")) sched = "beta";
            else if (s.contains("simple")) sched = "simple";
            else sched = "normal";
        }

        String clean = s.replace("karras", "")
                .replace("exponential", "")
                .replace("sgm uniform", "")
                .replace("automatic", "")
                .trim();

        String mappedSampler = switch (clean) {
            case "euler a", "euler ancestral" -> "euler_ancestral";
            case "euler" -> "euler";
            case "dpm++ 2m", "dpm++ 2m++" -> "dpmpp_2m";
            case "dpm++ 2m sde", "dpm++ 2m sde heun" -> "dpmpp_2m_sde";
            case "dpm++ sde" -> "dpmpp_sde";
            case "dpm++ 3m sde" -> "dpmpp_3m_sde";
            case "dpm++ 2s a" -> "dpmpp_2s_ancestral";
            case "heun" -> "heun";
            case "ddim" -> "ddim";
            case "unipc" -> "uni_pc";
            case "lms" -> "lms";
            case "lcm" -> "lcm";
            default -> clean.replaceAll("[^a-z0-9_]+", "_");
        };

        return new String[]{mappedSampler, sched};
    }

    private static String synthesizeComfyApiPrompt(String positive, String negative, String model,
                                                   Long seed, Integer steps, Double cfg, Double denoise,
                                                   Integer width, Integer height, String sampler, String scheduler) {
        JSONObject root = new JSONObject();
        root.put("1", new JSONObject()
                .put("class_type", "CheckpointLoaderSimple")
                .put("inputs", new JSONObject().put("ckpt_name", model != null ? model : "v1-5-pruned-emaonly.safetensors")));
        root.put("2", new JSONObject()
                .put("class_type", "CLIPTextEncode")
                .put("inputs", new JSONObject().put("text", positive != null ? positive : "").put("clip", new JSONArray().put("1").put(1))));
        root.put("3", new JSONObject()
                .put("class_type", "CLIPTextEncode")
                .put("inputs", new JSONObject().put("text", negative != null ? negative : "").put("clip", new JSONArray().put("1").put(1))));
        root.put("4", new JSONObject()
                .put("class_type", "EmptyLatentImage")
                .put("inputs", new JSONObject()
                        .put("width", width != null ? width : 1024)
                        .put("height", height != null ? height : 1024)
                        .put("batch_size", 1)));
        root.put("5", new JSONObject()
                .put("class_type", "KSampler")
                .put("inputs", new JSONObject()
                        .put("seed", seed != null ? seed : 42L)
                        .put("steps", steps != null ? steps : 20)
                        .put("cfg", cfg != null ? cfg : 7.0)
                        .put("denoise", denoise != null ? denoise : 1.0)
                        .put("sampler_name", sampler != null ? sampler : "euler")
                        .put("scheduler", scheduler != null ? scheduler : "normal")
                        .put("model", new JSONArray().put("1").put(0))
                        .put("positive", new JSONArray().put("2").put(0))
                        .put("negative", new JSONArray().put("3").put(0))
                        .put("latent_image", new JSONArray().put("4").put(0))));
        root.put("6", new JSONObject()
                .put("class_type", "VAEDecode")
                .put("inputs", new JSONObject()
                        .put("samples", new JSONArray().put("5").put(0))
                        .put("vae", new JSONArray().put("1").put(2))));
        root.put("7", new JSONObject()
                .put("class_type", "SaveImage")
                .put("inputs", new JSONObject()
                        .put("filename_prefix", "ComfyUI_Remix")
                        .put("images", new JSONArray().put("6").put(0))));
        return root.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Integer parseIntSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
