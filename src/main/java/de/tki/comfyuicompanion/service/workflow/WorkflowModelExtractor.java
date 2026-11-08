package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.CivitaiLocalModelScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise service component extracting all concrete model dependencies
 * (.safetensors, .gguf, .ckpt) directly from ComfyUI workflow JSON structures.
 * Supports both LiteGraph canvas schemas and ComfyUI API prompt graph representations,
 * including subgraphs and Comfy.org node properties.
 */
@Component
public class WorkflowModelExtractor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowModelExtractor.class);

    private static final Pattern MODEL_FILE_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_\\-\\.\\/\\\\\\:]+\\.(?:safetensors|sft|ckpt|pth|pt|bin|gguf))",
            Pattern.CASE_INSENSITIVE
    );

    private final CivitaiLocalModelScanner localModelScanner;

    @Autowired
    public WorkflowModelExtractor(@Autowired(required = false) CivitaiLocalModelScanner localModelScanner) {
        this.localModelScanner = localModelScanner;
    }

    /**
     * Extracts all unique model dependencies referenced across all nodes in the given workflow JSON.
     *
     * @param workflowRoot the parsed JSON root of the workflow
     * @return list of populated and locally validated {@link ModelInfo} records
     */
    public List<ModelInfo> extractModels(JsonNode workflowRoot) {
        if (workflowRoot == null || workflowRoot.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ModelInfo> discoveredModels = new LinkedHashMap<>();

        if (workflowRoot.has("nodes") && workflowRoot.get("nodes").isArray()) {
            extractFromLiteGraph(workflowRoot.get("nodes"), discoveredModels);
        } else {
            extractFromApiGraph(workflowRoot, discoveredModels);
        }

        // Scan subgraphs in definitions.subgraphs (standard in modern ComfyUI workflows)
        JsonNode subgraphs = workflowRoot.path("definitions").path("subgraphs");
        if (subgraphs.isArray()) {
            for (JsonNode sg : subgraphs) {
                if (sg.has("nodes") && sg.get("nodes").isArray()) {
                    extractFromLiteGraph(sg.get("nodes"), discoveredModels);
                }
            }
        } else if (subgraphs.isObject()) {
            subgraphs.elements().forEachRemaining(sg -> {
                if (sg.has("nodes") && sg.get("nodes").isArray()) {
                    extractFromLiteGraph(sg.get("nodes"), discoveredModels);
                }
            });
        }

        List<ModelInfo> result = new ArrayList<>(discoveredModels.values());
        log.debug("Extracted {} concrete model dependencies from workflow", result.size());
        return result;
    }

    private void extractFromLiteGraph(JsonNode nodesArray, Map<String, ModelInfo> discovered) {
        for (JsonNode node : nodesArray) {
            String nodeType = node.path("type").asText("");
            String nodeTitle = node.path("title").asText("");

            // 1. Inspect properties.models (Comfy.org modern metadata with download URLs)
            JsonNode modelsProp = node.path("properties").path("models");
            if (modelsProp.isArray()) {
                for (JsonNode m : modelsProp) {
                    String mName = m.path("name").asText("");
                    String mUrl = m.path("url").asText(null);
                    String mDir = m.path("directory").asText("");
                    if (!mName.isBlank()) {
                        String simpleName = extractSimpleFilename(mName);
                        String folder = !mDir.isBlank() ? mDir : determineModelFolder(nodeType, nodeTitle, simpleName, mUrl);
                        registerDiscoveredModel(simpleName, folder, mUrl, discovered);
                    }
                }
            }

            // 2. Inspect widgets_values array
            JsonNode widgets = node.get("widgets_values");
            if (widgets != null && widgets.isArray()) {
                for (JsonNode w : widgets) {
                    scanTextForModels(w.asText(""), nodeType, nodeTitle, discovered);
                }
            }

            // 3. Inspect widgets_values_named map
            JsonNode namedWidgets = node.get("widgets_values_named");
            if (namedWidgets != null && namedWidgets.isObject()) {
                namedWidgets.properties().forEach(entry ->
                        scanTextForModels(entry.getValue().asText(""), nodeType, nodeTitle, discovered)
                );
            }

            // 4. Inspect inline subgraphs if nested on node
            if (node.has("subgraph") && node.get("subgraph").has("nodes") && node.get("subgraph").get("nodes").isArray()) {
                extractFromLiteGraph(node.get("subgraph").get("nodes"), discovered);
            }
        }
    }

    private void extractFromApiGraph(JsonNode apiRoot, Map<String, ModelInfo> discovered) {
        apiRoot.properties().forEach(entry -> {
            JsonNode nodeObj = entry.getValue();
            if (nodeObj != null && nodeObj.isObject()) {
                String nodeType = nodeObj.path("class_type").asText("");
                String nodeTitle = nodeObj.path("_meta").path("title").asText("");
                JsonNode inputs = nodeObj.get("inputs");
                if (inputs != null && inputs.isObject()) {
                    inputs.properties().forEach(inputEntry ->
                            scanTextForModels(inputEntry.getValue().asText(""), nodeType, nodeTitle, discovered)
                    );
                }
            }
        });
    }

    private void scanTextForModels(String text, String nodeType, String nodeTitle, Map<String, ModelInfo> discovered) {
        if (text == null || text.isBlank() || text.length() > 50000) {
            return;
        }

        Matcher matcher = MODEL_FILE_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawMatch = matcher.group(1).trim();
            String simpleFilename = extractSimpleFilename(rawMatch);

            if (simpleFilename.equalsIgnoreCase("workflow.json")
                    || simpleFilename.endsWith(".png")
                    || simpleFilename.endsWith(".jpg")
                    || simpleFilename.endsWith(".zip")) {
                continue;
            }

            String downloadUrl = null;
            if (rawMatch.startsWith("http://") || rawMatch.startsWith("https://")) {
                downloadUrl = rawMatch;
            }

            String folder = determineModelFolder(nodeType, nodeTitle, simpleFilename, downloadUrl);
            registerDiscoveredModel(simpleFilename, folder, downloadUrl, discovered);
        }
    }

    private void registerDiscoveredModel(String simpleFilename, String folder, String downloadUrl, Map<String, ModelInfo> discovered) {
        String resolvedUrl = (downloadUrl != null && !downloadUrl.isBlank())
                ? downloadUrl
                : resolveFallbackDownloadUrl(simpleFilename, folder);
        if (resolvedUrl != null && resolvedUrl.contains("huggingface.co") && resolvedUrl.contains("/blob/")) {
            resolvedUrl = resolvedUrl.replace("/blob/", "/resolve/");
        }
        ModelInfo existing = discovered.get(simpleFilename);
        if (existing == null) {
            ModelInfo info = new ModelInfo(folder, simpleFilename, resolvedUrl);
            info.setFilename(simpleFilename);
            if (localModelScanner != null) {
                localModelScanner.checkAndSetModelStatus(info);
            }
            if (info.getSize() == null || "Unknown".equalsIgnoreCase(info.getSize()) || "Workflow JSON".equalsIgnoreCase(info.getSize())) {
                String estSize = estimateKnownModelSize(simpleFilename);
                if (estSize != null) info.setSize(estSize);
            }
            discovered.put(simpleFilename, info);
        } else {
            if (resolvedUrl != null && (existing.getUrl() == null || existing.getUrl().isBlank())) {
                existing.setUrl(resolvedUrl);
            }
            // If previous type was generic fallback, improve with more authoritative folder
            if ("checkpoints".equals(existing.getType()) && !"checkpoints".equals(folder)) {
                existing.setType(folder);
                if (localModelScanner != null) {
                    localModelScanner.checkAndSetModelStatus(existing);
                }
            }
            if (existing.getSize() == null || "Unknown".equalsIgnoreCase(existing.getSize()) || "Workflow JSON".equalsIgnoreCase(existing.getSize())) {
                String estSize = estimateKnownModelSize(simpleFilename);
                if (estSize != null) existing.setSize(estSize);
            }
        }
    }

    /**
     * Estimates a realistic model size string based on known architecture and filename conventions
     * when physical disk size or remote HTTP Content-Length is not yet available.
     *
     * @param filename model filename
     * @return estimated formatted size (e.g., "2.5 GB") or null if uncategorized
     */
    public static String estimateKnownModelSize(String filename) {
        if (filename == null || filename.isBlank()) return null;
        String fn = filename.toLowerCase(Locale.ROOT);
        if (fn.endsWith(".json")) return "14.2 KB";
        if (fn.contains("vae") || fn.startsWith("ae.")) return fn.contains("wan") ? "254 MB" : "335 MB";
        if (fn.contains("clip_l")) return "246 MB";
        if (fn.contains("qwen3.5_9b") || fn.contains("qwen3vl_8b")) return "8.8 GB";
        if (fn.contains("qwen3vl_4b") || fn.contains("qwen_3_4b") || fn.contains("qwen_3.5_4b")) return "4.8 GB";
        if (fn.contains("umt5")) return fn.contains("fp8") ? "6.27 GB" : "10.6 GB";
        if (fn.contains("t5xxl")) return (fn.contains("fp16") || fn.contains("bf16")) ? "9.5 GB" : "4.8 GB";
        if (fn.contains("depth_anything")) return fn.contains("vitl") ? "1.34 GB" : (fn.contains("vitb") ? "375 MB" : "670 MB");
        if (fn.contains("controlnet") || fn.contains("t2i_adapter")) return (fn.contains("5b") || fn.contains("wan")) ? "2.5 GB" : "1.4 GB";
        if (fn.contains("14b")) return (fn.contains("fp8") || fn.contains("int8")) ? "14.3 GB" : "28.6 GB";
        if (fn.contains("5b")) return (fn.contains("fp8") || fn.contains("int8")) ? "5.0 GB" : "10.0 GB";
        if (fn.contains("1.3b")) return "2.7 GB";
        if (fn.contains("upscale") || fn.contains("esrgan") || fn.contains("swinir") || fn.contains("4x")) return "67 MB";
        if (fn.contains("lora") || fn.contains("lycoris") || fn.contains("locon")) return "220 MB";
        if (fn.contains("int8") || fn.contains("nvfp4") || fn.contains("fp8")) return "6.8 GB";
        if (fn.contains("bf16") || fn.contains("fp16")) return "11.9 GB";
        if (fn.endsWith(".pth") || fn.endsWith(".pt") || fn.endsWith(".onnx")) return "400 MB";
        if (fn.endsWith(".safetensors") || fn.endsWith(".ckpt") || fn.endsWith(".gguf") || fn.endsWith(".sft")) return "2.4 GB";
        return null;
    }

    /**
     * Resolves an authoritative Hugging Face download URL for well-known ComfyUI base models,
     * preprocessors, and text encoders when the workflow JSON does not embed an explicit URL.
     *
     * @param filename model filename
     * @param folder   target model folder
     * @return resolved remote URL or null if unknown
     */
    public static String resolveFallbackDownloadUrl(String filename, String folder) {
        if (filename == null || filename.isBlank()) return null;
        String fn = filename.toLowerCase(Locale.ROOT);
        if (fn.equals("depth_anything_vitl14.pth")) {
            return "https://huggingface.co/spaces/LiheYoung/Depth-Anything/resolve/main/checkpoints/depth_anything_vitl14.pth";
        }
        if (fn.equals("wan2.2-ti2v-5b-controlnet-depth-v1.safetensors") || fn.contains("controlnet-depth-v1")) {
            return "https://huggingface.co/TheDenk/wan2.2-ti2v-5b-controlnet-depth-v1/resolve/main/diffusion_pytorch_model.safetensors";
        }
        if (fn.contains("fastwanfullattn") || fn.equals("wan2_2-ti2v-5b-fastwanfullattn_bf16.safetensors")) {
            return "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/FastWan/Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors";
        }
        if (fn.equals("umt5-xxl-enc-bf16.safetensors") || fn.equals("umt5_xxl_fp16.safetensors")) {
            return "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/umt5-xxl-enc-bf16.safetensors";
        }
        if (fn.equals("wan2_2-ti2v-5b_fp16.safetensors")) {
            return "https://huggingface.co/Kijai/WanVideo_comfy/resolve/main/Wan2_2-TI2V-5B_fp16.safetensors";
        }
        if (fn.equals("wan2_2_vae_bf16.safetensors") || fn.equals("wan_2.1_vae.safetensors")) {
            return "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/vae/wan_2.1_vae.safetensors";
        }
        if (fn.equals("ae.safetensors")) {
            return "https://huggingface.co/receptektas/black-forest-labs-ae_safetensors/resolve/main/ae.safetensors";
        }
        if (fn.equals("clip_l.safetensors")) {
            return "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors";
        }
        if (fn.equals("clip_g.safetensors")) {
            return "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/text_encoders/clip_g.safetensors";
        }
        if (fn.equals("t5xxl_fp16.safetensors")) {
            return "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/text_encoders/t5xxl_fp16.safetensors";
        }
        if (fn.startsWith("t5xxl_fp8")) {
            return "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors";
        }
        if (fn.contains("sd3.5_large") || fn.contains("sd35_large")) {
            return "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/sd3.5_large_fp8_scaled.safetensors";
        }
        if (fn.contains("sd3.5_medium") || fn.contains("sd35_medium")) {
            return "https://huggingface.co/Comfy-Org/stable-diffusion-3.5-fp8/resolve/main/sd3.5_medium_incl_clips_t5xxlfp8scaled.safetensors";
        }
        if (fn.contains("hunyuan_video_720")) {
            return "https://huggingface.co/Kijai/HunyuanVideo_comfy/resolve/main/hunyuan_video_720_cfgdistill_bf16.safetensors";
        }
        if (fn.contains("wan2.1_t2v_1.3b") || fn.contains("wan21_t2v_1.3b")) {
            return "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_1.3B_bf16.safetensors";
        }
        if (fn.contains("wan2.1_t2v_14b") || fn.contains("wan21_t2v_14b")) {
            return "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_14B_bf16.safetensors";
        }
        if (fn.contains("umt5_xxl_fp8")) {
            return "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp8_e4m3fn.safetensors";
        }
        return null;
    }

    private String extractSimpleFilename(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return "";
        }
        String cleaned = pathOrUrl.trim();
        int queryIdx = cleaned.indexOf('?');
        if (queryIdx >= 0) {
            cleaned = cleaned.substring(0, queryIdx);
        }
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
    }

    /**
     * Semantically determines the appropriate ComfyUI storage folder for an extracted model,
     * considering node semantics, filename tokens, and source download URLs.
     *
     * @param nodeType    the type name of the node hosting the model
     * @param nodeTitle   optional user title of the node
     * @param filename    the extracted model filename
     * @param downloadUrl optional URL source for the model
     * @return folder name (e.g., 'unet', 'clip', 'vae', 'checkpoints', 'loras')
     */
    public String determineModelFolder(String nodeType, String nodeTitle, String filename, String downloadUrl) {
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            String lowerUrl = downloadUrl.toLowerCase(Locale.ROOT);
            if (lowerUrl.contains("/diffusion_models/") || lowerUrl.contains("/diffusion/")) return "unet";
            if (lowerUrl.contains("/vae/")) return "vae";
            if (lowerUrl.contains("/loras/") || lowerUrl.contains("/lora/")) return "loras";
            if (lowerUrl.contains("/text_encoders/") || lowerUrl.contains("/clip/")) return "clip";
            if (lowerUrl.contains("/checkpoints/")) return "checkpoints";
        }
        return determineModelFolder(nodeType, nodeTitle, filename);
    }

    /**
     * Semantically determines the appropriate ComfyUI storage folder for an extracted model.
     *
     * @param nodeType  the type name of the node hosting the model
     * @param nodeTitle optional user title of the node
     * @param filename  the extracted model filename
     * @return folder name (e.g., 'unet', 'clip', 'vae', 'checkpoints', 'loras')
     */
    public String determineModelFolder(String nodeType, String nodeTitle, String filename) {
        String nt = nodeType != null ? nodeType.toLowerCase(Locale.ROOT) : "";
        String fn = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String ttl = nodeTitle != null ? nodeTitle.toLowerCase(Locale.ROOT) : "";

        // 1. Prioritize authoritative node type semantics
        if (nt.contains("vaeloader") || nt.equals("vae") || nt.contains("autoencoder")) {
            return "vae";
        }
        if (nt.contains("lora") || nt.contains("locon") || nt.contains("lycoris")) {
            return "loras";
        }
        if (nt.contains("controlnet") || nt.contains("t2i_adapter")) {
            return "controlnet";
        }
        if (nt.contains("cliploader") || nt.contains("dualcliploader") || nt.contains("triplecliploader")
                || nt.contains("text_encoder") || nt.contains("t5loader")) {
            return "clip";
        }
        if (nt.contains("unetloader") || nt.contains("diffusionloader") || nt.contains("unet")) {
            return "unet";
        }
        if (nt.contains("checkpointloader")) {
            return "checkpoints";
        }

        // 2. Prioritize filename markers for composite/subgraph nodes
        if (fn.equals("ae.safetensors") || fn.startsWith("ae.") || fn.contains("vae") || fn.contains("autoencoder")
                || fn.contains("decoder") || fn.contains("encoder_small")) {
            return "vae";
        }
        if (fn.contains("lora") || fn.contains("locon") || fn.contains("lycoris") || fn.contains("darkbrush")) {
            return "loras";
        }
        if (fn.contains("controlnet") || fn.contains("t2i_adapter")) {
            return "controlnet";
        }
        if (fn.contains("prompt_upsampler") || fn.contains("pe_t2i") || fn.contains("pe_i2i")
                || fn.contains("pe_") || fn.contains("vl_")
                || fn.contains("clip") || fn.contains("t5") || fn.contains("text_encoder")
                || fn.contains("llama") || fn.contains("bert") || fn.contains("gemma")) {
            return "clip";
        }
        if (fn.contains("unet") || fn.contains("diffusion") || fn.contains("dit")
                || fn.contains("transformer") || fn.contains("base") || fn.contains("klein")
                || fn.contains("turbo") || fn.contains("flux") || fn.contains("krea") || fn.contains("z_image")
                || fn.contains("convrot")) {
            return "unet";
        }
        if (fn.contains("qwen")) {
            return "clip";
        }

        // 3. Fallback to combined node heuristics
        String combined = (nt + " " + ttl + " " + fn);
        if (combined.contains("vae")) return "vae";
        if (combined.contains("lora")) return "loras";
        if (combined.contains("clip")) return "clip";
        if (combined.contains("unet")) return "unet";

        return "checkpoints";
    }
}
