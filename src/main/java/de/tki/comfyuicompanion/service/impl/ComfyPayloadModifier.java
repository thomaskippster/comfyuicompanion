package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Utility and component for modifying ComfyUI API payload JSON structures,
 * substituting models, positive and negative prompt texts, and randomizing generation seeds.
 */
@Component
public class ComfyPayloadModifier {
    private static final Logger logger = LoggerFactory.getLogger(ComfyPayloadModifier.class);

    private final Random random = new Random();

    /**
     * Injects model, prompt texts, and randomized seeds into a raw JSON template string.
     *
     * @param templateJson   raw template JSON string
     * @param modelName      selected model checkpoint or unet filename
     * @param positivePrompt positive conditioning text
     * @param negativePrompt negative conditioning text
     * @return modified template JSON string
     */
    public String modifyPayload(String templateJson, String modelName, String positivePrompt, String negativePrompt) {
        if (templateJson == null || templateJson.trim().isEmpty()) {
            return templateJson;
        }

        try {
            JSONObject rootObj = new JSONObject(templateJson);
            JSONObject promptObj = rootObj.has("prompt") ? rootObj.getJSONObject("prompt") : rootObj;

            // 1. Set model in CheckpointLoaderSimple / UNetLoader / CLIPLoader / VAELoader
            String lowerModel = modelName != null ? modelName.toLowerCase() : "";
            for (String key : promptObj.keySet()) {
                JSONObject nodeObj = promptObj.getJSONObject(key);
                String classType = nodeObj.optString("class_type", "");
                JSONObject inputs = nodeObj.optJSONObject("inputs");
                if (inputs == null) continue;

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", modelName);
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    inputs.put("unet_name", modelName);
                } else if ("CLIPLoader".equals(classType)) {
                    if (lowerModel.contains("z_image") || lowerModel.contains("acestep") || lowerModel.contains("longcat") || lowerModel.contains("lumina")) {
                        inputs.put("clip_name", "qwen_3_4b.safetensors");
                        inputs.put("type", "lumina2");
                    } else if (lowerModel.contains("wan")) {
                        inputs.put("clip_name", "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors");
                        inputs.put("type", "wan");
                    }
                } else if ("VAELoader".equals(classType)) {
                    if (lowerModel.contains("z_image") || lowerModel.contains("acestep") || lowerModel.contains("longcat") || lowerModel.contains("lumina") || lowerModel.contains("flux")) {
                        inputs.put("vae_name", "ae.safetensors");
                    } else if (lowerModel.contains("wan")) {
                        inputs.put("vae_name", "wan_2.1_vae.safetensors");
                    }
                }
            }

            // 2. Identify Positive and Negative CLIPTextEncode nodes
            List<JSONObject> clipNodes = new ArrayList<>();
            for (String key : promptObj.keySet()) {
                JSONObject nodeObj = promptObj.getJSONObject(key);
                if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                    clipNodes.add(nodeObj);
                }
            }

            JSONObject positiveNode = null;
            JSONObject negativeNode = null;

            if (clipNodes.size() == 1) {
                positiveNode = clipNodes.get(0);
            } else if (clipNodes.size() >= 2) {
                for (JSONObject node : clipNodes) {
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs != null && inputs.has("text")) {
                        String text = inputs.getString("text").toLowerCase();
                        if (text.contains("bad") || text.contains("blurry") || text.contains("low quality") || text.contains("worst") || text.contains("deformed")) {
                            negativeNode = node;
                        } else {
                            positiveNode = node;
                        }
                    }
                }
                if (positiveNode == null) {
                    positiveNode = clipNodes.get(0);
                }
                if (negativeNode == null) {
                    for (JSONObject node : clipNodes) {
                        if (node != positiveNode) {
                            negativeNode = node;
                            break;
                        }
                    }
                }
            }

            if (positiveNode != null) {
                JSONObject inputs = positiveNode.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("text", positivePrompt != null ? positivePrompt : "");
                }
            }

            if (negativeNode != null) {
                JSONObject inputs = negativeNode.optJSONObject("inputs");
                if (inputs != null) {
                    String neg = (negativePrompt != null && !negativePrompt.trim().isEmpty()) ? negativePrompt : "blurry, low quality, worst quality";
                    inputs.put("text", neg);
                }
            }

            // 3. Set Random Seed in samplers to bypass caching
            for (String key : promptObj.keySet()) {
                JSONObject nodeObj = promptObj.getJSONObject(key);
                String classType = nodeObj.optString("class_type", "");
                JSONObject inputs = nodeObj.optJSONObject("inputs");
                if (inputs != null) {
                    if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType) || classType.contains("Sampler")) {
                        long randomSeed = Math.abs(random.nextLong()) % 9007199254740991L;
                        for (String inputKey : inputs.keySet()) {
                            String lowerKey = inputKey.toLowerCase();
                            if (lowerKey.equals("seed") || lowerKey.equals("noise_seed") || lowerKey.endsWith("_seed") || lowerKey.startsWith("seed_")) {
                                Object existingVal = inputs.get(inputKey);
                                if (existingVal instanceof Number) {
                                    inputs.put(inputKey, randomSeed);
                                }
                            }
                        }
                    }
                    if ("RandomNoise".equals(classType)) {
                        long randomSeed = Math.abs(random.nextLong()) % 9007199254740991L;
                        inputs.put("noise_seed", randomSeed);
                    }
                }
            }

            return rootObj.toString(2);
        } catch (Exception e) {
            logger.error("❌ [PayloadModifier] Error modifying payload: {}", e.getMessage());
            return templateJson;
        }
    }

    /**
     * Injects parameters directly into a Jackson {@link JsonNode} tree.
     *
     * @param templateTree   Jackson JsonNode representing the workflow
     * @param modelName      selected model name
     * @param positivePrompt positive conditioning text
     * @param negativePrompt negative conditioning text
     * @return modified template tree
     */
    public JsonNode injectParameters(JsonNode templateTree, String modelName, String positivePrompt, String negativePrompt) {
        if (templateTree == null || !templateTree.isObject()) {
            return templateTree;
        }

        ObjectNode promptObj;
        if (templateTree.has("prompt")) {
            promptObj = (ObjectNode) templateTree.get("prompt");
        } else {
            promptObj = (ObjectNode) templateTree;
        }

        // 1. Model / UNET loader update
        Iterator<String> fieldNames = promptObj.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                ObjectNode nodeObj = (ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                if ("CheckpointLoaderSimple".equals(classType)) {
                    ObjectNode inputs = (ObjectNode) nodeObj.path("inputs");
                    if (inputs != null && !inputs.isMissingNode()) {
                        inputs.put("ckpt_name", modelName);
                    }
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    ObjectNode inputs = (ObjectNode) nodeObj.path("inputs");
                    if (inputs != null && !inputs.isMissingNode()) {
                        inputs.put("unet_name", modelName);
                    }
                }
            }
        }

        // 2. Locate positive and negative CLIPTextEncode nodes
        List<ObjectNode> clipNodes = new ArrayList<>();
        fieldNames = promptObj.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                ObjectNode nodeObj = (ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                if ("CLIPTextEncode".equals(classType)) {
                    clipNodes.add(nodeObj);
                }
            }
        }

        ObjectNode positiveNode = null;
        ObjectNode negativeNode = null;

        if (clipNodes.size() == 1) {
            positiveNode = clipNodes.get(0);
        } else if (clipNodes.size() >= 2) {
            for (ObjectNode node : clipNodes) {
                ObjectNode inputs = (ObjectNode) node.path("inputs");
                if (inputs != null && !inputs.isMissingNode() && inputs.has("text")) {
                    String text = inputs.get("text").asText("").toLowerCase();
                    if (text.contains("bad") || text.contains("blurry") || text.contains("low quality") || text.contains("worst")) {
                        negativeNode = node;
                    } else {
                        positiveNode = node;
                    }
                }
            }
            if (positiveNode == null) {
                positiveNode = clipNodes.get(0);
            }
            if (negativeNode == null) {
                for (ObjectNode node : clipNodes) {
                    if (node != positiveNode) {
                        negativeNode = node;
                        break;
                    }
                }
            }
        }

        if (positiveNode != null) {
            ObjectNode inputs = (ObjectNode) positiveNode.path("inputs");
            if (inputs != null && !inputs.isMissingNode()) {
                inputs.put("text", positivePrompt != null ? positivePrompt : "");
            }
        }

        if (negativeNode != null) {
            ObjectNode inputs = (ObjectNode) negativeNode.path("inputs");
            if (inputs != null && !inputs.isMissingNode()) {
                String neg = (negativePrompt != null && !negativePrompt.trim().isEmpty()) ? negativePrompt : "blurry, low quality, worst quality";
                inputs.put("text", neg);
            }
        }

        // 3. Seed / noise_seed randomization for samplers and noise nodes
        fieldNames = promptObj.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                ObjectNode nodeObj = (ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                ObjectNode inputs = (ObjectNode) nodeObj.path("inputs");
                if (inputs != null && !inputs.isMissingNode()) {
                    if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType) || classType.contains("Sampler")) {
                        long randomSeed = Math.abs(random.nextLong()) % 9007199254740991L;
                        Iterator<String> inputKeys = inputs.fieldNames();
                        while (inputKeys.hasNext()) {
                            String inputKey = inputKeys.next();
                            String lowerKey = inputKey.toLowerCase();
                            if (lowerKey.equals("seed") || lowerKey.equals("noise_seed") || lowerKey.endsWith("_seed") || lowerKey.startsWith("seed_")) {
                                if (inputs.get(inputKey).isNumber()) {
                                    inputs.put(inputKey, randomSeed);
                                }
                            }
                        }
                    }
                    if ("RandomNoise".equals(classType)) {
                        long randomSeed = Math.abs(random.nextLong()) % 9007199254740991L;
                        inputs.put("noise_seed", randomSeed);
                    }
                }
            }
        }

        return templateTree;
    }

    /**
     * Serializes a Jackson JsonNode tree into a clean formatted JSON string with prompt wrapper.
     *
     * @param templateTree Jackson JsonNode tree
     * @param objectMapper Jackson ObjectMapper
     * @return pretty-printed JSON string
     */
    public String generatePayload(JsonNode templateTree, ObjectMapper objectMapper) {
        if (templateTree == null) {
            return "{}";
        }
        ObjectNode root = objectMapper.createObjectNode();
        if (templateTree.has("prompt")) {
            root.set("prompt", templateTree.get("prompt"));
        } else {
            root.set("prompt", templateTree);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            logger.error("❌ [PayloadModifier] Failed to serialize payload: {}", e.getMessage());
            return root.toString();
        }
    }
}
