package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Service component responsible for inspecting ComfyUI workflow definitions
 * and extracting recommended model defaults such as VAE, CLIP type, sampler, and scheduler.
 */
@Component
public class WorkflowDefaultsExtractor {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowDefaultsExtractor.class);

    /**
     * Inspects a workflow JSON string and populates the given defaults map.
     *
     * @param rawJson                  the raw JSON representation of the workflow
     * @param filename                 the filename of the workflow being analyzed
     * @param mapper                   Jackson ObjectMapper for JSON parsing
     * @param architectureDetector     function resolving the architecture for a model name
     * @param standardDefaultsSupplier function supplying fallback defaults for an architecture
     * @param resolvedDefaultsMap      target map to receive discovered model defaults
     */
    public void extractDefaultsFromWorkflow(
            String rawJson,
            String filename,
            ObjectMapper mapper,
            Function<String, ModelArchitecture> architectureDetector,
            Function<ModelArchitecture, ModelDefaults> standardDefaultsSupplier,
            Map<String, ModelDefaults> resolvedDefaultsMap) {
        try {
            JsonNode root = mapper.readTree(rawJson);
            extractVideoModels(root, resolvedDefaultsMap);

            List<String> modelNames = new ArrayList<>();
            List<String> vaeNames = new ArrayList<>();
            List<String> clipTypes = new ArrayList<>();
            List<String> schedulers = new ArrayList<>();
            List<String> samplerNames = new ArrayList<>();

            extractValues(root, modelNames, vaeNames, clipTypes, schedulers, samplerNames);

            for (String modelName : modelNames) {
                String fName = new File(modelName).getName();
                String cleanModelName = fName.toLowerCase().trim();

                String vaeName = vaeNames.isEmpty() ? null : vaeNames.get(0);
                String clipType = clipTypes.isEmpty() ? null : clipTypes.get(0);
                String scheduler = schedulers.isEmpty() ? null : schedulers.get(0);
                String samplerName = samplerNames.isEmpty() ? null : samplerNames.get(0);

                ModelArchitecture arch = architectureDetector.apply(fName);
                ModelDefaults archDefaults = standardDefaultsSupplier.apply(arch);

                if (vaeName == null) vaeName = archDefaults.vaeName;
                if (clipType == null) clipType = archDefaults.clipType;
                if (scheduler == null) scheduler = archDefaults.scheduler;
                if (samplerName == null) samplerName = archDefaults.samplerName;

                ModelDefaults defaults = new ModelDefaults(vaeName, clipType, scheduler, samplerName, arch.name());
                resolvedDefaultsMap.put(cleanModelName, defaults);
            }
        } catch (Exception e) {
            logger.error("⚠️ [DefaultsExtractor] Failed to extract defaults from workflow {}: {}", filename, e.getMessage());
        }
    }

    /**
     * Identifies dedicated video generation nodes (e.g. Wan, LTX-Video) and stores specialized defaults.
     *
     * @param root                the JSON root of the workflow
     * @param resolvedDefaultsMap the destination map for resolved model defaults
     */
    public void extractVideoModels(JsonNode root, Map<String, ModelDefaults> resolvedDefaultsMap) {
        if (root == null) return;

        boolean isVideoBlueprint = false;
        List<String> unets = new ArrayList<>();
        List<String> vaes = new ArrayList<>();
        List<String> clips = new ArrayList<>();
        List<String> loras = new ArrayList<>();

        // Find nodes in API format
        if (root.isObject() && !root.has("nodes")) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode node = field.getValue();
                if (node.isObject() && node.has("class_type")) {
                    String classType = node.path("class_type").asText();
                    if ("WanImageToVideo".equalsIgnoreCase(classType) || classType.toLowerCase().startsWith("ltxv")) {
                        isVideoBlueprint = true;
                    }
                    JsonNode inputs = node.path("inputs");
                    if (inputs.isObject()) {
                        if ("UNETLoader".equalsIgnoreCase(classType)) {
                            String unet = inputs.path("unet_name").asText();
                            if (!unet.isEmpty()) unets.add(unet);
                        } else if ("VAELoader".equalsIgnoreCase(classType)) {
                            String vae = inputs.path("vae_name").asText();
                            if (!vae.isEmpty()) vaes.add(vae);
                        } else if ("CLIPLoader".equalsIgnoreCase(classType)) {
                            String clip = inputs.path("clip_name").asText();
                            if (!clip.isEmpty()) clips.add(clip);
                        } else if ("LoraLoader".equalsIgnoreCase(classType)) {
                            String lora = inputs.path("lora_name").asText();
                            if (!lora.isEmpty()) loras.add(lora);
                        }
                    }
                }
            }
        }

        // Find nodes in UI/Workflow format
        JsonNode nodesArray = root.path("nodes");
        if (nodesArray.isArray()) {
            for (JsonNode node : nodesArray) {
                String type = node.path("type").asText();
                if ("WanImageToVideo".equalsIgnoreCase(type) || type.toLowerCase().startsWith("ltxv")) {
                    isVideoBlueprint = true;
                }
                JsonNode widgets = node.path("widgets_values");
                if (widgets.isArray() && widgets.size() > 0) {
                    if ("UNETLoader".equalsIgnoreCase(type)) {
                        String unet = widgets.get(0).asText();
                        if (!unet.isEmpty()) unets.add(unet);
                    } else if ("VAELoader".equalsIgnoreCase(type)) {
                        String vae = widgets.get(0).asText();
                        if (!vae.isEmpty()) vaes.add(vae);
                    } else if ("CLIPLoader".equalsIgnoreCase(type)) {
                        String clip = widgets.get(0).asText();
                        if (!clip.isEmpty()) clips.add(clip);
                    } else if ("LoraLoader".equalsIgnoreCase(type)) {
                        String lora = widgets.get(0).asText();
                        if (!lora.isEmpty()) loras.add(lora);
                    }
                }
            }
        }

        for (String unet : unets) {
            String lower = unet.toLowerCase();
            if (lower.contains("wan") || lower.contains("ltx")) {
                isVideoBlueprint = true;
                break;
            }
        }

        if (isVideoBlueprint) {
            boolean isWan = false;
            for (String unet : unets) {
                if (unet.toLowerCase().contains("wan")) {
                    isWan = true;
                    break;
                }
            }
            if (!isWan) {
                for (String lora : loras) {
                    if (lora.toLowerCase().contains("wan")) {
                        isWan = true;
                        break;
                    }
                }
            }

            if (isWan) {
                String highUnet = null;
                String lowUnet = null;
                for (String u : unets) {
                    if (u.toLowerCase().contains("high")) {
                        highUnet = u;
                    } else if (u.toLowerCase().contains("low")) {
                        lowUnet = u;
                    } else if (highUnet == null) {
                        highUnet = u;
                    } else if (lowUnet == null) {
                        lowUnet = u;
                    }
                }

                String highLora = null;
                String lowLora = null;
                for (String l : loras) {
                    if (l.toLowerCase().contains("high")) {
                        highLora = l;
                    } else if (l.toLowerCase().contains("low")) {
                        lowLora = l;
                    } else if (highLora == null) {
                        highLora = l;
                    } else if (lowLora == null) {
                        lowLora = l;
                    }
                }

                String clip = clips.isEmpty() ? null : clips.get(0);
                String vae = vaes.isEmpty() ? null : vaes.get(0);

                if (highUnet != null) {
                    resolvedDefaultsMap.put("video_wan_high_unet", new ModelDefaults(highUnet, null, null, null, "ARCH_WAN"));
                }
                if (lowUnet != null) {
                    resolvedDefaultsMap.put("video_wan_low_unet", new ModelDefaults(lowUnet, null, null, null, "ARCH_WAN"));
                }
                if (highLora != null) {
                    resolvedDefaultsMap.put("video_wan_high_lora", new ModelDefaults(highLora, null, null, null, "ARCH_WAN"));
                }
                if (lowLora != null) {
                    resolvedDefaultsMap.put("video_wan_low_lora", new ModelDefaults(lowLora, null, null, null, "ARCH_WAN"));
                }
                if (clip != null) {
                    resolvedDefaultsMap.put("video_wan_clip", new ModelDefaults(clip, null, null, null, "ARCH_WAN"));
                }
                if (vae != null) {
                    resolvedDefaultsMap.put("video_wan_vae", new ModelDefaults(vae, null, null, null, "ARCH_WAN"));
                }

                logger.info("📹 [DefaultsExtractor] Extracted Wan video defaults: HighUnet={}, LowUnet={}, HighLora={}, LowLora={}, Clip={}, Vae={}",
                        highUnet, lowUnet, highLora, lowLora, clip, vae);
            }
        }
    }

    /**
     * Recursively traverses JSON nodes to identify model filenames, VAEs, CLIP types, schedulers, and samplers.
     */
    public void extractValues(JsonNode node,
                              List<String> modelNames,
                              List<String> vaeNames,
                              List<String> clipTypes,
                              List<String> schedulers,
                              List<String> samplerNames) {
        if (node == null) return;
        if (node.isTextual()) {
            String val = node.asText().trim();
            String lower = val.toLowerCase();
            if (lower.endsWith(".safetensors") || lower.endsWith(".sft") || lower.endsWith(".ckpt")) {
                if (lower.contains("vae") || lower.equals("ae.safetensors") || lower.endsWith("/ae.safetensors") || lower.endsWith("\\ae.safetensors")) {
                    vaeNames.add(val);
                } else if (!lower.contains("clip") && !lower.contains("t5") && !lower.contains("encoder") && !lower.contains("lora")) {
                    modelNames.add(val);
                }
            } else {
                if (List.of("longcat_image", "wan", "flux", "sd3", "lumina2", "ltxv", "cosmos", "mochi", "stable_diffusion").contains(lower)) {
                    clipTypes.add(val);
                } else if (List.of("simple", "normal", "karras", "exponential", "sgm_uniform", "ddim_uniform").contains(lower)) {
                    schedulers.add(val);
                } else if (List.of("euler", "euler_ancestral", "heun", "dpm_2", "uni_pc", "ddim", "ipndf", "deis").contains(lower)) {
                    samplerNames.add(val);
                }
            }
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                extractValues(child, modelNames, vaeNames, clipTypes, schedulers, samplerNames);
            }
        }
    }
}
