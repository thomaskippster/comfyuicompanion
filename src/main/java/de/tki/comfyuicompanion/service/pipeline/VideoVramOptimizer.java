package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.HardwareTier;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.IVideoVramOptimizer;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

/**
 * Enterprise implementation of {@link IVideoVramOptimizer} that dynamically transforms
 * ComfyUI workflows for 16 GB VRAM GPUs and triggers GPU memory purges.
 */
@Service
public class VideoVramOptimizer implements IVideoVramOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(VideoVramOptimizer.class);
    private static final long SIXTEEN_GB_BYTES = 17L * 1024L * 1024L * 1024L; // Up to ~16.5 GB allowance

    private final ConfigService configService;
    private final IHardwareProfileService hardwareProfileService;
    private final HttpClient httpClient;

    /**
     * Constructs a VideoVramOptimizer with default configuration and client.
     */
    public VideoVramOptimizer() {
        this(null, null, null);
    }

    /**
     * Constructs a VideoVramOptimizer with injected dependencies.
     *
     * @param configService          configuration service for optimization toggles
     * @param hardwareProfileService hardware profile service for VRAM size detection
     * @param httpClient             HTTP client for REST communications
     */
    @Autowired
    public VideoVramOptimizer(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) IHardwareProfileService hardwareProfileService,
            @Autowired(required = false) HttpClient httpClient) {
        this.configService = configService;
        this.hardwareProfileService = hardwareProfileService;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean is16GbOptimizationActive() {
        if (configService != null && !configService.is16GbVramOptimizationEnabled()) {
            return false;
        }
        if (hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            if (profile != null) {
                if (profile.tier() == HardwareTier.CPU_ONLY || profile.tier() == HardwareTier.BUDGET) {
                    return true;
                }
                if (profile.vramBytes() > 0 && profile.vramBytes() <= SIXTEEN_GB_BYTES) {
                    return true;
                }
            }
        }
        // Default to active on standard mid-range desktop profiles to prevent unexpected OOMs
        return true;
    }

    @Override
    public JSONObject optimizeWorkflowFor16Gb(JSONObject workflowJson) {
        if (workflowJson == null) {
            return null;
        }

        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        int modifiedNodes = 0;

        for (String nodeId : new ArrayList<>(promptObj.keySet())) {
            JSONObject node = promptObj.optJSONObject(nodeId);
            if (node == null) continue;

            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            // 1. Offload heavy text encoders (T5-XXL / UMT5 / Llama) to CPU (saves 5-9 GB VRAM)
            if ("CLIPLoader".equals(classType) || "DualCLIPLoader".equals(classType) || "LTXAVTextEncoderLoader".equals(classType)) {
                String currentDevice = inputs.optString("device", "default");
                if (!"cpu".equalsIgnoreCase(currentDevice)) {
                    inputs.put("device", "cpu");
                    modifiedNodes++;
                    logger.info("🧠 [16GB-Opt] Offloaded text encoder '{}' (node {}) to CPU to save ~6-8 GB VRAM.", classType, nodeId);
                }
            }

            // 2. Prevent OOM crashes during VAE decoding at the end of video generation
            if ("VAEDecode".equals(classType)) {
                node.put("class_type", "VAEDecodeTiled");
                inputs.put("tile_size", 512);
                inputs.put("overlap", 64);
                inputs.put("temporal_size", 64);
                inputs.put("temporal_overlap", 8);
                modifiedNodes++;
                logger.info("🛡️ [16GB-Opt] Upgraded VAEDecode (node {}) to VAEDecodeTiled (512px tiles) to prevent VRAM explosion.", nodeId);
            }

            // 3. Ensure FP8 weight precision on UNETLoader if not using GGUF
            if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                String unetName = inputs.optString("unet_name", "");
                String weightDtype = inputs.optString("weight_dtype", "default");
                if (!unetName.toLowerCase().contains("gguf") && ("default".equalsIgnoreCase(weightDtype) || "fp16".equalsIgnoreCase(weightDtype))) {
                    inputs.put("weight_dtype", "fp8_e4m3fn");
                    modifiedNodes++;
                    logger.info("⚡ [16GB-Opt] Enforced fp8_e4m3fn weight quantization on UNETLoader (node {}).", nodeId);
                }
            }
        }

        if (modifiedNodes > 0) {
            logger.info("✅ [16GB-Opt] Successfully injected 16 GB VRAM optimizations across {} workflow nodes.", modifiedNodes);
        }
        return workflowJson;
    }

    @Override
    public boolean purgeComfyVram(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            return false;
        }
        String cleanUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        try {
            JSONObject body = new JSONObject();
            body.put("unload_models", true);
            body.put("free_memory", true);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl + "/free"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("🧹 [16GB-Opt] ComfyUI VRAM Purge executed successfully (Status: {}). Full GPU memory released.", response.statusCode());
                return true;
            } else {
                logger.warn("⚠️ [16GB-Opt] ComfyUI /free returned non-200 status: {}", response.statusCode());
                return false;
            }
        } catch (Exception ex) {
            logger.warn("⚠️ [16GB-Opt] Could not purge ComfyUI VRAM: {}", ex.getMessage());
            return false;
        }
    }
}
