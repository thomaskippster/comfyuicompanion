package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoVramOptimizer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Service component responsible for synthesizing optimized ComfyUI execution graphs
 * specifically for the Wan 2.2 5B / FastWan model family (Wan2_2-TI2V-5B).
 * Tailored for 16 GB VRAM GPUs (e.g. RTX 5080/4080) with 6-8 sampling steps and CFG 1.0.
 */
@Component
public class FastWanWorkflowBuilder {

    private static final Logger log = LoggerFactory.getLogger(FastWanWorkflowBuilder.class);

    public static final String DEFAULT_FASTWAN_MODEL = "Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors";
    public static final String DEFAULT_WAN_CLIP = "umt5_xxl_fp8_e4m3fn_scaled.safetensors";
    public static final String DEFAULT_WAN_VAE = "wan_2.1_vae.safetensors";

    private final ComfyNodeInspectionClient inspectionClient;
    private final IVideoVramOptimizer vramOptimizer;
    private final de.tki.comfyuicompanion.service.IFileSystemLinkService fileSystemLinkService;

    public FastWanWorkflowBuilder() {
        this(null, null, null);
    }

    public FastWanWorkflowBuilder(ComfyNodeInspectionClient inspectionClient, IVideoVramOptimizer vramOptimizer) {
        this(inspectionClient, vramOptimizer, null);
    }

    @Autowired
    public FastWanWorkflowBuilder(
            @Autowired(required = false) ComfyNodeInspectionClient inspectionClient,
            @Autowired(required = false) IVideoVramOptimizer vramOptimizer,
            @Autowired(required = false) de.tki.comfyuicompanion.service.IFileSystemLinkService fileSystemLinkService) {
        this.inspectionClient = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient();
        this.vramOptimizer = vramOptimizer != null ? vramOptimizer : new VideoVramOptimizer();
        this.fileSystemLinkService = fileSystemLinkService != null ? fileSystemLinkService : new de.tki.comfyuicompanion.service.impl.FileSystemLinkService();
    }

    /**
     * Synthesizes a native ComfyUI video execution graph for FastWan 5B.
     *
     * @param serverUrl active ComfyUI endpoint URL
     * @param scene target scene parameters
     * @param speakerImage optional keyframe / consistency image
     * @param seed random generation seed
     * @param filenamePrefix output filename prefix
     * @param availableUnets list of UNETs reported by ComfyUI
     * @param availableCheckpoints list of checkpoints reported by ComfyUI
     * @param availableClips list of text encoders reported by ComfyUI
     * @param availableVaes list of VAEs reported by ComfyUI
     * @return executable API prompt payload
     */
    public JSONObject generateFastWanWorkflowJson(
            String serverUrl,
            Scene scene,
            String speakerImage,
            long seed,
            String filenamePrefix,
            List<String> availableUnets,
            List<String> availableCheckpoints,
            List<String> availableClips,
            List<String> availableVaes) {

        String promptText = scene.getPrompt() != null ? scene.getPrompt() : "";
        String cleanSpeakerImage = (speakerImage == null || speakerImage.trim().isEmpty()) ? null : speakerImage;
        JSONObject workflowJson = new JSONObject();

        String unetName = resolveFastWanModel(availableUnets, availableCheckpoints);
        String clipName = resolveClip(availableClips);
        String vaeName = resolveVae(availableVaes);

        int width = scene.getWidth() > 0 ? scene.getWidth() : 832;
        int height = scene.getHeight() > 0 ? scene.getHeight() : 480;
        width = Math.max(256, (width / 16) * 16);
        height = Math.max(256, (height / 16) * 16);

        float duration = 5.0f;
        if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
        }
        float fps = 16.0f;

        // FastWan 5B full attention is optimized for 6 sampling steps and CFG 1.0
        int steps = (scene.getSteps() > 0 && scene.getSteps() <= 12) ? scene.getSteps() : 6;
        double cfg = (scene.getCfgScale() > 0 && scene.getCfgScale() <= 3.0) ? scene.getCfgScale() : 1.0;

        String baseNegative = "blurry, out of focus, distorted, abstract blobs, static, artifacts, watermark, low quality, worst quality";
        String negativePrompt = (scene.getNegativePrompt() != null && !scene.getNegativePrompt().isBlank())
                ? (scene.getNegativePrompt().trim() + ", " + baseNegative)
                : baseNegative;

        // 1. Model & Conditioning Loaders
        workflowJson.put("10", makeNode("VAELoader", new JSONObject().put("vae_name", vaeName)));
        workflowJson.put("11", makeNode("CLIPLoader", new JSONObject().put("clip_name", clipName).put("type", "wan").put("device", "default")));
        workflowJson.put("12", makeNode("UNETLoader", new JSONObject().put("unet_name", unetName).put("weight_dtype", "default")));

        // 2. Prompt Encoding
        workflowJson.put("20", makeNode("CLIPTextEncode", new JSONObject().put("text", negativePrompt).put("clip", link("11", 0))));
        workflowJson.put("21", makeNode("CLIPTextEncode", new JSONObject().put("text", promptText).put("clip", link("11", 0))));

        // 3. Duration & Frame Math
        workflowJson.put("30", makeNode("PrimitiveFloat", new JSONObject().put("value", duration)));
        workflowJson.put("31", makeNode("PrimitiveFloat", new JSONObject().put("value", fps)));
        workflowJson.put("32", makeNode("ComfyMathExpression", new JSONObject().put("expression", "floor(a * b + 1)").put("values.a", link("30", 0)).put("values.b", link("31", 0))));

        // 4. Source Image Conditioning (Image-to-Video) or Blank Latents (Text-to-Video)
        JSONObject wanInputs = new JSONObject()
                .put("width", width)
                .put("height", height)
                .put("length", link("32", 1))
                .put("batch_size", 1)
                .put("positive", link("21", 0))
                .put("negative", link("20", 0))
                .put("vae", link("10", 0));

        if (cleanSpeakerImage != null) {
            workflowJson.put("40", makeNode("LoadImage", new JSONObject().put("image", cleanSpeakerImage)));
            wanInputs.put("start_image", link("40", 0));
        }

        // 5. Wan Video Conditioning & Diffusion Sampling
        workflowJson.put("50", makeNode("WanImageToVideo", wanInputs));

        workflowJson.put("60", makeNode("KSampler", new JSONObject()
                .put("seed", seed)
                .put("steps", steps)
                .put("cfg", cfg)
                .put("sampler_name", "euler")
                .put("scheduler", "simple")
                .put("denoise", 1.0)
                .put("model", link("12", 0))
                .put("positive", link("50", 0))
                .put("negative", link("50", 1))
                .put("latent_image", link("50", 2))));

        // 6. Decode & Video Export
        workflowJson.put("70", makeNode("VAEDecode", new JSONObject().put("samples", link("60", 0)).put("vae", link("10", 0))));
        workflowJson.put("80", makeNode("CreateVideo", new JSONObject().put("fps", link("31", 0)).put("images", link("70", 0))));
        workflowJson.put("90", makeNode("SaveVideo", new JSONObject().put("filename_prefix", "video/" + filenamePrefix).put("format", "auto").put("codec", "auto").put("video", link("80", 0))));

        if (vramOptimizer != null && vramOptimizer.is16GbOptimizationActive()) {
            vramOptimizer.optimizeWorkflowFor16Gb(workflowJson);
        }

        log.info("⚡ [FastWanWorkflowBuilder] Synthesized FastWan 5B execution graph ({} steps, CFG {}, {}x{}).", steps, cfg, width, height);
        return workflowJson;
    }

    /**
     * Resolves the exact FastWan 5B diffusion model filename from available options.
     */
    public String resolveFastWanModel(List<String> unets, List<String> checkpoints) {
        if (unets != null) {
            for (String u : unets) {
                String low = u.toLowerCase();
                if (low.contains("fastwan") || (low.contains("wan2_2") && low.contains("5b")) || low.contains("wan2.2-ti2v-5b")) {
                    return u;
                }
            }
        }
        if (checkpoints != null) {
            for (String c : checkpoints) {
                String low = c.toLowerCase();
                if (low.contains("fastwan") || (low.contains("wan2_2") && low.contains("5b")) || low.contains("wan2.2-ti2v-5b")) {
                    return c;
                }
            }
        }
        return DEFAULT_FASTWAN_MODEL;
    }

    private String resolveClip(List<String> clips) {
        if (clips != null) {
            for (String c : clips) {
                String low = c.toLowerCase();
                if (low.contains("umt5") || low.contains("wan")) {
                    return c;
                }
            }
        }
        return DEFAULT_WAN_CLIP;
    }

    private String resolveVae(List<String> vaes) {
        if (vaes != null) {
            for (String v : vaes) {
                String low = v.toLowerCase();
                if (low.contains("wan_2.2") || low.contains("wan2.2") || low.contains("wan_2.1") || low.contains("wan2.1") || low.contains("wan")) {
                    return v;
                }
            }
        }
        return DEFAULT_WAN_VAE;
    }

    /**
     * Ensures a filesystem link exists between checkpoints and diffusion_models
     * using IFileSystemLinkService so that the model can be loaded by UNETLoader without duplicating 10 GB.
     *
     * @param comfyPath the active ComfyUI directory
     */
    public void ensureDiffusionModelLinked(String comfyPath) {
        if (comfyPath == null || comfyPath.isBlank()) return;
        Path ckptPath = Path.of(comfyPath, "models", "checkpoints", DEFAULT_FASTWAN_MODEL);
        Path diffPath = Path.of(comfyPath, "models", "diffusion_models", DEFAULT_FASTWAN_MODEL);

        if (Files.exists(ckptPath) && !Files.exists(diffPath)) {
            var result = fileSystemLinkService.createFileLink(diffPath, ckptPath);
            if (result.success()) {
                log.info("🔗 [FastWanWorkflowBuilder] Established {} link for FastWan 5B model in diffusion_models folder.", result.linkType());
            } else {
                log.warn("⚠️ [FastWanWorkflowBuilder] Could not link FastWan model via {}: {}", result.linkType(), result.message());
            }
        }
    }

    private static JSONObject makeNode(String classType, JSONObject inputs) {
        JSONObject node = new JSONObject();
        node.put("class_type", classType);
        node.put("inputs", inputs);
        return node;
    }

    private static JSONArray link(String nodeId, int outputIndex) {
        return new JSONArray().put(nodeId).put(outputIndex);
    }
}
