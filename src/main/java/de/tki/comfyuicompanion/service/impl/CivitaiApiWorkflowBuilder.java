package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service component responsible for synthesizing executable ComfyUI API-format workflow JSON payloads
 * for Civitai models, handling LoRA loader injection, prompt texts, and seed randomization.
 */
@Component
public class CivitaiApiWorkflowBuilder {

    private static final Logger log = LoggerFactory.getLogger(CivitaiApiWorkflowBuilder.class);

    private final ObjectMapper objectMapper;
    private final CivitaiLocalModelScanner modelScanner;

    /**
     * Constructs a new CivitaiApiWorkflowBuilder with required dependencies.
     *
     * @param objectMapper Jackson object mapper for JSON operations
     * @param modelScanner scanner for resolving fallback local checkpoints
     */
    @Autowired
    public CivitaiApiWorkflowBuilder(ObjectMapper objectMapper, CivitaiLocalModelScanner modelScanner) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.modelScanner = modelScanner;
    }

    /**
     * Generates a runnable ComfyUI API workflow JSON payload for the specified Civitai model entry.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels operational models resolved for this entry
     * @param userPrompt     optional custom positive prompt text
     * @return pretty-printed JSON string of the ComfyUI API workflow
     */
    public String generateWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        String baseLower = entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";
        String typeLower = entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";

        String templateName;
        if (baseLower.contains("flux")) {
            templateName = "template_flux_api.json";
        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            templateName = "template_sdxl_api.json";
        } else if (baseLower.contains("wan")) {
            templateName = "template_wan_api.json";
        } else if (baseLower.contains("hunyuan")) {
            templateName = "template_hunyuan_api.json";
        } else if (baseLower.contains("sd 3") || baseLower.contains("sd3")) {
            templateName = "template_sd3_api.json";
        } else {
            templateName = "template_sd15_api.json";
        }

        String rawTemplate = DefaultComfyTemplates.getDefaultTemplateForName(templateName);

        try {
            JsonNode root = objectMapper.readTree(rawTemplate);
            ObjectNode promptObj = root.has("prompt") ? (ObjectNode) root.get("prompt") : (ObjectNode) root;

            // 1. Identify and configure base model / UNET loader
            String activeModelName = entry.filename();
            if (typeLower.contains("lora") || typeLower.contains("locon")) {
                if (requiredModels != null) {
                    for (ModelInfo info : requiredModels) {
                        String infoType = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
                        if (infoType.contains("checkpoint") || infoType.contains("unet") || infoType.contains("diffusion")) {
                            String candidate = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
                            if (candidate != null && !candidate.equalsIgnoreCase(entry.filename())) {
                                activeModelName = candidate;
                                break;
                            }
                        }
                    }
                }
                // Defensive guard: A LoRA filename must NEVER be loaded as a base checkpoint in CheckpointLoaderSimple
                if (activeModelName.equalsIgnoreCase(entry.filename())) {
                    activeModelName = modelScanner != null
                            ? modelScanner.findFirstLocalCheckpointName().orElse("v1-5-pruned-emaonly.safetensors")
                            : "v1-5-pruned-emaonly.safetensors";
                    log.warn("LoRA model '{}' had no distinct base checkpoint resolved in dependencies. Falling back to checkpoint '{}'.",
                            entry.filename(), activeModelName);
                }
            }

            String ckptNodeId = null;
            Iterator<String> nodeIds = promptObj.fieldNames();
            while (nodeIds.hasNext()) {
                String id = nodeIds.next();
                JsonNode node = promptObj.get(id);
                String classType = node.path("class_type").asText("");
                ObjectNode inputs = (ObjectNode) node.path("inputs");

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", activeModelName);
                    ckptNodeId = id;
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    inputs.put("unet_name", activeModelName);
                    ckptNodeId = id;
                }
            }

            // 2. If LoRA: Inject LoraLoader node and rewire connections
            if ((typeLower.contains("lora") || typeLower.contains("locon")) && ckptNodeId != null) {
                injectLoraNode(promptObj, entry.filename(), ckptNodeId, baseLower.contains("flux"));
            }

            // 3. Positive Prompt assembly
            String effectivePrompt;
            if (userPrompt != null && !userPrompt.isBlank()) {
                effectivePrompt = userPrompt.trim();
            } else if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
                effectivePrompt = String.join(", ", entry.triggerWords()) + ", masterpiece, high quality, 8k wallpaper";
            } else {
                effectivePrompt = entry.name() + ", masterpiece, high quality, highly detailed";
            }

            // Inject positive and negative prompt text
            injectPromptTexts(promptObj, effectivePrompt, "blurry, low quality, deformed, extra limbs, bad anatomy");

            // Randomize seeds
            randomizeSeeds(promptObj);

            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set("prompt", promptObj);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapped);

        } catch (Exception ex) {
            log.error("Failed synthesizing ComfyUI workflow JSON: {}", ex.getMessage(), ex);
            return rawTemplate;
        }
    }

    private void injectLoraNode(ObjectNode promptObj, String loraFilename, String sourceNodeId, boolean isFlux) {
        String loraNodeId = "99";
        while (promptObj.has(loraNodeId)) {
            loraNodeId = String.valueOf(Integer.parseInt(loraNodeId) + 1);
        }

        ObjectNode loraNode = objectMapper.createObjectNode();
        loraNode.put("class_type", "LoraLoader");

        ObjectNode loraInputs = objectMapper.createObjectNode();
        loraInputs.put("lora_name", loraFilename);
        loraInputs.put("strength_model", 0.85);
        loraInputs.put("strength_clip", 0.85);

        ArrayNode modelLink = objectMapper.createArrayNode();
        modelLink.add(sourceNodeId);
        modelLink.add(0);
        loraInputs.set("model", modelLink);

        ArrayNode clipLink = objectMapper.createArrayNode();
        clipLink.add(isFlux ? "2" : sourceNodeId);
        clipLink.add(isFlux ? 0 : 1);
        loraInputs.set("clip", clipLink);

        loraNode.set("inputs", loraInputs);
        promptObj.set(loraNodeId, loraNode);

        // Rewire downstream model and clip references
        final String finalLoraId = loraNodeId;
        promptObj.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals(finalLoraId)) return;
            JsonNode node = entry.getValue();
            if (node.has("inputs")) {
                ObjectNode inputs = (ObjectNode) node.get("inputs");
                // Rewire model
                if (inputs.has("model") && inputs.get("model").isArray()) {
                    ArrayNode arr = (ArrayNode) inputs.get("model");
                    if (arr.size() >= 2 && sourceNodeId.equals(arr.get(0).asText())) {
                        arr.set(0, finalLoraId);
                    }
                }
                // Rewire clip
                if (inputs.has("clip") && inputs.get("clip").isArray()) {
                    ArrayNode arr = (ArrayNode) inputs.get("clip");
                    if (arr.size() >= 2 && (sourceNodeId.equals(arr.get(0).asText()) || (isFlux && "2".equals(arr.get(0).asText())))) {
                        arr.set(0, finalLoraId);
                        arr.set(1, 1);
                    }
                }
            }
        });
    }

    private void injectPromptTexts(ObjectNode promptObj, String positive, String negative) {
        promptObj.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            if ("CLIPTextEncode".equals(node.path("class_type").asText())) {
                ObjectNode inputs = (ObjectNode) node.path("inputs");
                if (inputs != null && inputs.has("text")) {
                    String existing = inputs.path("text").asText().toLowerCase(Locale.ROOT);
                    if (existing.contains("bad") || existing.contains("blurry") || existing.contains("deformed")) {
                        inputs.put("text", negative);
                    } else {
                        inputs.put("text", positive);
                    }
                }
            }
        });
    }

    private void randomizeSeeds(ObjectNode promptObj) {
        promptObj.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            if (node.has("inputs")) {
                ObjectNode inputs = (ObjectNode) node.get("inputs");
                if (inputs.has("seed")) {
                    inputs.put("seed", Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L)));
                }
                if (inputs.has("noise_seed")) {
                    inputs.put("noise_seed", Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L)));
                }
            }
        });
    }
}
