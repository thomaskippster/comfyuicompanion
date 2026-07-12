package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ModelArchitectureAnalyzer {

    private final ObjectMapper objectMapper;

    public ModelArchitectureAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Analyzes the safetensors JSON header heuristically to determine the model architecture,
     * the model type, and potential trigger words.
     *
     * @param headerJson The raw JSON string from the safetensors header
     * @return ModelMetadata containing the classified architecture and type
     * @throws IOException If parsing fails
     */
    public ModelMetadata analyze(String headerJson) throws IOException {
        SafetensorsHeader header = objectMapper.readValue(headerJson, SafetensorsHeader.class);
        ModelMetadata result = new ModelMetadata();

        Map<String, String> meta = header.getMetadata();
        Map<String, TensorInfo> tensors = header.getTensors();

        // 1. Determine Model Type (CHECKPOINT vs LORA)
        // LoRAs typically prefix all their trained layers with "lora_unet_" or "lora_te_"
        boolean isLora = tensors.keySet().stream().anyMatch(k -> k.startsWith("lora_unet_") || k.startsWith("lora_te_"));
        result.setModelType(isLora ? "LORA" : "CHECKPOINT");

        // 2. Determine Architecture Heuristically
        String arch = "UNKNOWN";

        // A. Fast Metadata Check
        if (meta != null) {
            String baseVersion = meta.getOrDefault("ss_base_model_version", "").toLowerCase();
            String modelName = meta.getOrDefault("ss_sd_model_name", "").toLowerCase();
            String modelspecArch = meta.getOrDefault("modelspec.architecture", "").toLowerCase();

            if (baseVersion.contains("sdxl") || modelName.contains("pony") || modelName.contains("illustrious") || modelspecArch.contains("sdxl")) {
                arch = "SDXL";
            } else if (baseVersion.contains("sd1") || modelspecArch.contains("stable-diffusion-v1")) {
                arch = "SD1.5";
            }
        }

        // B. Deep Tensor Key Analysis (Fallback or Overrides)
        boolean hasFluxBlocks = false;
        boolean hasSD15Blocks = false;

        for (String key : tensors.keySet()) {
            if (key.contains("double_blocks") || key.contains("single_blocks") || key.contains("lora_unet_double_blocks")) {
                hasFluxBlocks = true;
                break; // FLUX is highly distinct, no need to keep checking
            }
            if (key.contains("down_blocks") || key.contains("up_blocks") || key.contains("cross_attention")) {
                hasSD15Blocks = true;
            }
        }

        if (hasFluxBlocks) {
            arch = "FLUX";
        } else if ("UNKNOWN".equals(arch)) {
            // If meta was empty and it wasn't FLUX, guess based on classic UNet structures
            if (hasSD15Blocks) {
                arch = "SD1.5"; // or SDXL, but defaults to classic SD architecture
            } else {
                arch = "SD1.5"; // Ultimate generic fallback
            }
        }

        result.setArchitectureType(arch);

        // 3. Extract Trigger Words and Style (from standard LoRA metadata spec and tag frequency)
        List<String> triggers = new ArrayList<>();
        if (meta != null) {
            String triggerPhrase = meta.get("modelspec.trigger_phrase");
            if (triggerPhrase != null && !triggerPhrase.isEmpty()) {
                triggers.add(triggerPhrase);
            }
            
            String tags = meta.get("ss_tag_frequency");
            if (tags != null && !tags.isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode tagsNode = objectMapper.readTree(tags);
                    int animeScore = 0;
                    int photoScore = 0;
                    
                    if (tagsNode.isObject()) {
                        java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> datasets = tagsNode.fields();
                        while (datasets.hasNext()) {
                            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> dataset = datasets.next();
                            if (dataset.getValue().isObject()) {
                                java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> tagEntries = dataset.getValue().fields();
                                while (tagEntries.hasNext()) {
                                    Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> tagEntry = tagEntries.next();
                                    String tag = tagEntry.getKey().toLowerCase();
                                    int freq = tagEntry.getValue().asInt(0);
                                    
                                    // Add very frequent tags as fallback triggers if none existed
                                    if (triggers.isEmpty() && freq > 50) {
                                        triggers.add(tag);
                                    }
                                    
                                    // Heuristic Scoring
                                    if (tag.contains("1girl") || tag.contains("masterpiece") || tag.contains("anime") || tag.contains("manga")) {
                                        animeScore += freq;
                                    } else if (tag.contains("realistic") || tag.contains("photography") || tag.contains("raw photo") || tag.contains("8k")) {
                                        photoScore += freq;
                                    }
                                }
                            }
                        }
                    }
                    
                    if (animeScore > photoScore * 2 && animeScore > 10) {
                        result.setStyle("Anime");
                    } else if (photoScore > animeScore * 2 && photoScore > 10) {
                        result.setStyle("Photorealistic");
                    } else {
                        result.setStyle("Generic/Mixed");
                    }
                    
                } catch (Exception e) {
                    // Ignore parse errors, just fallback
                    if (triggers.isEmpty()) {
                        triggers.add("Parsed tags available (unreadable)");
                    }
                }
            }
        }
        result.setTriggerWords(triggers);

        return result;
    }
}
