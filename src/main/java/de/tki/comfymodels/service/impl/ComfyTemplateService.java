package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ComfyTemplate;
import de.tki.comfymodels.domain.ModelArchitecture;
import de.tki.comfymodels.service.IComfyTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ComfyTemplateService implements IComfyTemplateService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyTemplateService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigService configService;
    private final ModelListService modelListService;
    private final Map<String, ComfyTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, String> modelToTemplateMap = new ConcurrentHashMap<>();

    @Autowired
    public ComfyTemplateService(ConfigService configService, ModelListService modelListService) {
        this.configService = configService;
        this.modelListService = modelListService;
    }

    @PostConstruct
    public void init() {
        scanTemplates();
    }

    @Override
    public void scanTemplates() {
        File templatesDir = new File(configService.getAppDataPath(), "templates/comfyui");
        if (!templatesDir.exists()) {
            boolean created = templatesDir.mkdirs();
            if (created) {
                logger.info("📂 [TemplateService] Created templates directory at: " + templatesDir.getAbsolutePath());
            }
        }

        // Load mapping file
        File mappingFile = new File(templatesDir, "model_template_mapping.json");
        if (!mappingFile.exists()) {
            writeDefaultMappingFile(mappingFile);
        }
        loadMappingFile(mappingFile);

        // Force-upgrade older flux template if it uses CheckpointLoaderSimple
        File fluxFile = new File(templatesDir, "flux_base_api.json");
        boolean needsFluxUpgrade = false;
        if (fluxFile.exists()) {
            try {
                String currentFluxContent = Files.readString(fluxFile.toPath(), StandardCharsets.UTF_8);
                if (currentFluxContent.contains("CheckpointLoaderSimple")) {
                    needsFluxUpgrade = true;
                    logger.info("🔄 [TemplateService] Old Flux template detected. Forcing upgrade...");
                }
            } catch (IOException ignored) {}
        }

        // Check for missing Lumina2 template or older version without CFGNorm
        File luminaFile = new File(templatesDir, "lumina2_base_api.json");
        boolean needsLuminaWrite = !luminaFile.exists();
        if (luminaFile.exists()) {
            try {
                String currentLuminaContent = Files.readString(luminaFile.toPath(), StandardCharsets.UTF_8);
                if (!currentLuminaContent.contains("CFGNorm")) {
                    needsLuminaWrite = true;
                    logger.info("🔄 [TemplateService] Old Lumina2 template without CFGNorm detected. Forcing upgrade...");
                }
            } catch (IOException ignored) {}
        }

        // Initialize default templates if they are missing
        String[] defaultFileNames = {
            "template_sd15_api.json",
            "template_sdxl_api.json",
            "template_flux_api.json",
            "template_lumina2_api.json",
            "template_sd3_api.json",
            "template_wan_api.json",
            "template_hunyuan_api.json"
        };
        for (String dfn : defaultFileNames) {
            File templateFile = new File(templatesDir, dfn);
            if (!templateFile.exists()) {
                writeJsonFile(templateFile, getDefaultTemplateForName(dfn));
                logger.info("📂 [TemplateService] Created missing architecture template: " + dfn);
            }
        }

        // Initialize default templates if the directory is empty, needs flux upgrade, or needs lumina template
        File[] files = templatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json") && !name.equalsIgnoreCase("model_template_mapping.json") && !name.equalsIgnoreCase("model_architecture_mapping.json"));
        if (files == null || files.length == 0 || needsFluxUpgrade || needsLuminaWrite) {
            writeDefaultTemplates(templatesDir);
            files = templatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json") && !name.equalsIgnoreCase("model_template_mapping.json") && !name.equalsIgnoreCase("model_architecture_mapping.json"));
        }

        templateCache.clear();
        if (files != null) {
            for (File file : files) {
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    String filename = file.getName();
                    String name = formatTemplateName(filename);
                    ComfyTemplate template = new ComfyTemplate(name, filename, content, file.getAbsolutePath());
                    templateCache.put(filename.toLowerCase(), template);
                    logger.info("📄 [TemplateService] Loaded template: " + name + " (" + filename + ")");
                } catch (IOException e) {
                    logger.error("❌ [TemplateService] Error reading template file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public List<ComfyTemplate> getTemplates() {
        List<ComfyTemplate> list = new ArrayList<>(templateCache.values());
        // Sort templates by name for UI consistency
        list.sort((t1, t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
        return Collections.unmodifiableList(list);
    }

    @Override
    public ComfyTemplate getTemplateByName(String name) {
        if (name == null) return null;
        for (ComfyTemplate template : templateCache.values()) {
            if (template.getName().equalsIgnoreCase(name)) {
                return template;
            }
        }
        return null;
    }

    @Override
    public ComfyTemplate getTemplateByFilename(String filename) {
        if (filename == null) return null;
        return templateCache.get(filename.toLowerCase());
    }

    @Override
    public ComfyTemplate determineTemplateForModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return getTemplateByFilename("sd15_base_api.json");
        }

        String lowerModelName = modelName.toLowerCase();
        
        // 1. Direct match in mapping config file (exact name or contains check)
        for (Map.Entry<String, String> entry : modelToTemplateMap.entrySet()) {
            String key = entry.getKey();
            if (lowerModelName.equals(key) || lowerModelName.endsWith("/" + key) || lowerModelName.endsWith("\\" + key)) {
                ComfyTemplate t = getTemplateByFilename(entry.getValue());
                if (t != null) return t;
            }
        }
        
        // 2. Metadata lookup (from ModelListService)
        if (modelListService != null) {
            java.util.Optional<de.tki.comfymodels.domain.ModelInfo> infoOpt = modelListService.findByFilename(modelName);
            if (infoOpt.isPresent()) {
                String base = infoOpt.get().getBase();
                if (base != null && !base.isEmpty()) {
                    String lowerBase = base.toLowerCase();
                    if (lowerBase.contains("flux")) {
                        ComfyTemplate t = getTemplateByFilename("flux_base_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("xl") || lowerBase.contains("sdxl")) {
                        ComfyTemplate t = getTemplateByFilename("sdxl_base_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("lumina")) {
                        ComfyTemplate t = getTemplateByFilename("lumina2_base_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("1.5") || lowerBase.contains("sd 1.5")) {
                        ComfyTemplate t = getTemplateByFilename("sd15_base_api.json");
                        if (t != null) return t;
                    }
                }
            }
        }
        
        // 3. Naming convention fallbacks (checking keywords in the modelName string)
        if (lowerModelName.contains("flux1-schnell") || lowerModelName.contains("flux_schnell") || lowerModelName.contains("schnell")) {
            ComfyTemplate t = getTemplateByFilename("flux_base_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("flux")) {
            ComfyTemplate t = getTemplateByFilename("flux_base_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("xl") || lowerModelName.contains("sdxl") || lowerModelName.contains("juggernaut") || lowerModelName.contains("pony")) {
            ComfyTemplate t = getTemplateByFilename("sdxl_base_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("longcat") || lowerModelName.contains("lumina")) {
            ComfyTemplate t = getTemplateByFilename("lumina2_base_api.json");
            if (t != null) return t;
        }
        
        // 4. Default fallback
        return getTemplateByFilename("sd15_base_api.json");
    }

    @Override
    public String modifyPayload(String templateJson, String modelName, String positivePrompt, String negativePrompt) {
        if (templateJson == null || templateJson.trim().isEmpty()) {
            return templateJson;
        }

        try {
            org.json.JSONObject rootObj = new org.json.JSONObject(templateJson);
            org.json.JSONObject promptObj;
            if (rootObj.has("prompt")) {
                promptObj = rootObj.getJSONObject("prompt");
            } else {
                promptObj = rootObj;
            }

            // 1. Set model in CheckpointLoaderSimple / UNetLoader
            for (String key : promptObj.keySet()) {
                org.json.JSONObject nodeObj = promptObj.getJSONObject(key);
                String classType = nodeObj.optString("class_type", "");
                if ("CheckpointLoaderSimple".equals(classType)) {
                    org.json.JSONObject inputs = nodeObj.optJSONObject("inputs");
                    if (inputs != null) {
                        inputs.put("ckpt_name", modelName);
                    }
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    org.json.JSONObject inputs = nodeObj.optJSONObject("inputs");
                    if (inputs != null) {
                        inputs.put("unet_name", modelName);
                    }
                }
            }

            // 2. Identify Positive and Negative CLIPTextEncode nodes
            java.util.List<org.json.JSONObject> clipNodes = new java.util.ArrayList<>();
            for (String key : promptObj.keySet()) {
                org.json.JSONObject nodeObj = promptObj.getJSONObject(key);
                if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                    clipNodes.add(nodeObj);
                }
            }

            org.json.JSONObject positiveNode = null;
            org.json.JSONObject negativeNode = null;

            if (clipNodes.size() == 1) {
                positiveNode = clipNodes.get(0);
            } else if (clipNodes.size() >= 2) {
                // Try to find negative by checking keywords in existing text
                for (org.json.JSONObject node : clipNodes) {
                    org.json.JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs != null && inputs.has("text")) {
                        String text = inputs.getString("text").toLowerCase();
                        if (text.contains("bad") || text.contains("blurry") || text.contains("low quality") || text.contains("worst") || text.contains("deformed")) {
                            negativeNode = node;
                        } else {
                            positiveNode = node;
                        }
                    }
                }
                // Fallback if positive node was not identified
                if (positiveNode == null) {
                    positiveNode = clipNodes.get(0);
                }
                // Fallback if negative node was not identified and there is a second node
                if (negativeNode == null) {
                    for (org.json.JSONObject node : clipNodes) {
                        if (node != positiveNode) {
                            negativeNode = node;
                            break;
                        }
                    }
                }
            }

            // Set Positive Prompt
            if (positiveNode != null) {
                org.json.JSONObject inputs = positiveNode.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("text", positivePrompt != null ? positivePrompt : "");
                }
            }

            // Set Negative Prompt
            if (negativeNode != null) {
                org.json.JSONObject inputs = negativeNode.optJSONObject("inputs");
                if (inputs != null) {
                    String neg = (negativePrompt != null && !negativePrompt.trim().isEmpty()) ? negativePrompt : "blurry, low quality, worst quality";
                    inputs.put("text", neg);
                }
            }

            // 3. Set Random Seed in samplers to bypass caching
            for (String key : promptObj.keySet()) {
                org.json.JSONObject nodeObj = promptObj.getJSONObject(key);
                String classType = nodeObj.optString("class_type", "");
                org.json.JSONObject inputs = nodeObj.optJSONObject("inputs");
                if (inputs != null) {
                    if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType) || classType.contains("Sampler")) {
                        long randomSeed = Math.abs(new java.util.Random().nextLong()) % 9007199254740991L;
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
                        long randomSeed = Math.abs(new java.util.Random().nextLong()) % 9007199254740991L;
                        inputs.put("noise_seed", randomSeed);
                    }
                }
            }

            return rootObj.toString(2);
        } catch (Exception e) {
            logger.error("❌ [TemplateService] Error modifying payload: " + e.getMessage());
            return templateJson;
        }
    }


    private void writeDefaultMappingFile(File file) {
        String content = "{\n" +
                "  \"mappings\": {\n" +
                "    \"v1-5-pruned-emaonly.safetensors\": \"sd15_base_api.json\",\n" +
                "    \"sd_xl_base_1.0.safetensors\": \"sdxl_base_api.json\",\n" +
                "    \"flux1-schnell-fp8.safetensors\": \"flux_base_api.json\",\n" +
                "    \"longcat_image_bf16.safetensors\": \"lumina2_base_api.json\"\n" +
                "  }\n" +
                "}";
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("❌ [TemplateService] Error writing default mapping file: " + e.getMessage());
        }
    }

    private void loadMappingFile(File file) {
        modelToTemplateMap.clear();
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            org.json.JSONObject obj = new org.json.JSONObject(content);
            if (obj.has("mappings")) {
                org.json.JSONObject mappings = obj.getJSONObject("mappings");
                for (String modelKey : mappings.keySet()) {
                    modelToTemplateMap.put(modelKey.toLowerCase(), mappings.getString(modelKey));
                }
            }
        } catch (Exception e) {
            logger.error("❌ [TemplateService] Error loading mapping file: " + e.getMessage());
        }
    }

    private String formatTemplateName(String filename) {
        String base = filename;
        if (base.toLowerCase().endsWith(".json")) {
            base = base.substring(0, base.length() - 5);
        }
        if (base.toLowerCase().endsWith("_api")) {
            base = base.substring(0, base.length() - 4);
        }
        String[] parts = base.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.equalsIgnoreCase("sd15")) {
                sb.append("SD 1.5 ");
            } else if (part.equalsIgnoreCase("sdxl")) {
                sb.append("SDXL ");
            } else if (part.equalsIgnoreCase("flux")) {
                sb.append("FLUX.1 ");
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void writeDefaultTemplates(File targetDir) {
        logger.info("💾 [TemplateService] Populating default templates in: " + targetDir.getAbsolutePath());
        writeJsonFile(new File(targetDir, "sd15_base_api.json"), getSd15DefaultTemplate());
        writeJsonFile(new File(targetDir, "sdxl_base_api.json"), getSdxlDefaultTemplate());
        writeJsonFile(new File(targetDir, "flux_base_api.json"), getFluxDefaultTemplate());
        writeJsonFile(new File(targetDir, "lumina2_base_api.json"), getLumina2DefaultTemplate());

        writeJsonFile(new File(targetDir, "template_sd15_api.json"), getSd15DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_sdxl_api.json"), getSdxlDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_flux_api.json"), getFluxDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_lumina2_api.json"), getLumina2DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_sd3_api.json"), getSd3DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_wan_api.json"), getWanDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_hunyuan_api.json"), getHunyuanDefaultTemplate());
    }

    private void writeJsonFile(File file, String content) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("❌ [TemplateService] Error writing default template file " + file.getName() + ": " + e.getMessage());
        }
    }

    private String getSd15DefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 42,\n" +
                "        \"steps\": 20,\n" +
                "        \"cfg\": 7.0,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"normal\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"KSampler\"\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"inputs\": {\n" +
                "        \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 512,\n" +
                "        \"height\": 512,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptyLatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"beautiful scenery, mountain, sunset, hyperrealistic, 8k\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"bad hands, blurry, worst quality, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"4\",\n" +
                "          2\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_SD15\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private String getSdxlDefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 42,\n" +
                "        \"steps\": 30,\n" +
                "        \"cfg\": 6.0,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"normal\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"KSampler\"\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"inputs\": {\n" +
                "        \"ckpt_name\": \"sd_xl_base_1.0.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptyLatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"cinematic shot of a majestic lion in the savanna, golden hour, highly detailed\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"extra limbs, deformed, blurry, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"4\",\n" +
                "          2\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_SDXL\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private String getFluxDefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"1\": {\n" +
                "      \"inputs\": {\n" +
                "        \"unet_name\": \"flux1-schnell-fp8.safetensors\",\n" +
                "        \"weight_dtype\": \"default\"\n" +
                "      },\n" +
                "      \"class_type\": \"UNETLoader\"\n" +
                "    },\n" +
                "    \"2\": {\n" +
                "      \"inputs\": {\n" +
                "        \"clip_name1\": \"clip_l.safetensors\",\n" +
                "        \"clip_name2\": \"t5xxl_fp16.safetensors\",\n" +
                "        \"type\": \"flux\"\n" +
                "      },\n" +
                "      \"class_type\": \"DualCLIPLoader\"\n" +
                "    },\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"vae_name\": \"ae.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"VAELoader\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptyLatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"a cute red panda wearing a tiny wizard hat, digital art, high quality\",\n" +
                "        \"clip\": [\n" +
                "          \"2\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"31\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_Flux\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    },\n" +
                "    \"11\": {\n" +
                "      \"inputs\": {\n" +
                "        \"model\": [\n" +
                "          \"1\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"conditioning\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"BasicGuider\"\n" +
                "    },\n" +
                "    \"17\": {\n" +
                "      \"inputs\": {\n" +
                "        \"steps\": 4,\n" +
                "        \"scheduler\": \"simple\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"1\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"BasicScheduler\"\n" +
                "    },\n" +
                "    \"31\": {\n" +
                "      \"inputs\": {\n" +
                "        \"noise\": [\n" +
                "          \"32\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"guider\": [\n" +
                "          \"11\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"sampler\": [\n" +
                "          \"33\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"sigmas\": [\n" +
                "          \"17\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SamplerCustomAdvanced\"\n" +
                "    },\n" +
                "    \"32\": {\n" +
                "      \"inputs\": {\n" +
                "        \"noise_seed\": 42\n" +
                "      },\n" +
                "      \"class_type\": \"RandomNoise\"\n" +
                "    },\n" +
                "    \"33\": {\n" +
                "      \"inputs\": {\n" +
                "        \"sampler_name\": \"euler\"\n" +
                "      },\n" +
                "      \"class_type\": \"KSamplerSelect\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private String getLumina2DefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"1\": {\n" +
                "      \"class_type\": \"UNETLoader\",\n" +
                "      \"inputs\": {\n" +
                "        \"unet_name\": \"longcat_image_bf16.safetensors\",\n" +
                "        \"weight_dtype\": \"default\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"2\": {\n" +
                "      \"class_type\": \"CLIPLoader\",\n" +
                "      \"inputs\": {\n" +
                "        \"clip_name\": \"qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors\",\n" +
                "        \"type\": \"longcat_image\",\n" +
                "        \"device\": \"default\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"3\": {\n" +
                "      \"class_type\": \"VAELoader\",\n" +
                "      \"inputs\": {\n" +
                "        \"vae_name\": \"ae.safetensors\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"class_type\": \"CLIPTextEncode\",\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"beautiful scenery, mountain, sunset, hyperrealistic, 8k\",\n" +
                "        \"clip\": [\n" +
                "          \"2\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"class_type\": \"CLIPTextEncode\",\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"bad hands, blurry, worst quality, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"2\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"class_type\": \"EmptySD3LatentImage\",\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      }\n" +
                "    },\n" +
                "    \"10\": {\n" +
                "      \"class_type\": \"CFGNorm\",\n" +
                "      \"inputs\": {\n" +
                "        \"strength\": 1.0,\n" +
                "        \"pre_cfg\": false,\n" +
                "        \"model\": [\n" +
                "          \"1\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"11\": {\n" +
                "      \"class_type\": \"FluxGuidance\",\n" +
                "      \"inputs\": {\n" +
                "        \"guidance\": 4.0,\n" +
                "        \"conditioning\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"12\": {\n" +
                "      \"class_type\": \"FluxGuidance\",\n" +
                "      \"inputs\": {\n" +
                "        \"guidance\": 4.0,\n" +
                "        \"conditioning\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"class_type\": \"KSampler\",\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 8117347940921812,\n" +
                "        \"steps\": 20,\n" +
                "        \"cfg\": 4.0,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"simple\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"10\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"11\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"12\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"class_type\": \"VAEDecode\",\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"class_type\": \"SaveImage\",\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_LongCat\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    @Override
    public JsonNode loadTemplateForArchitecture(ModelArchitecture architecture) throws IOException {
        String filename = getTemplateFilenameForArchitecture(architecture);
        File templatesDir = new File(configService.getAppDataPath(), "templates/comfyui");
        File templateFile = new File(templatesDir, filename);
        if (!templateFile.exists()) {
            writeJsonFile(templateFile, getDefaultTemplateForName(filename));
        }
        return objectMapper.readTree(templateFile);
    }

    @Override
    public String getTemplateFilenameForArchitecture(ModelArchitecture arch) {
        switch (arch) {
            case ARCH_FLUX: return "template_flux_api.json";
            case ARCH_SD15: return "template_sd15_api.json";
            case ARCH_SDXL: return "template_sdxl_api.json";
            case ARCH_SD3: return "template_sd3_api.json";
            case ARCH_LUMINA2: return "template_lumina2_api.json";
            case ARCH_WAN: return "template_wan_api.json";
            case ARCH_HUNYUAN: return "template_hunyuan_api.json";
            default: return "template_sd15_api.json";
        }
    }

    private String getDefaultTemplateForName(String name) {
        switch (name) {
            case "template_sd15_api.json": return getSd15DefaultTemplate();
            case "template_sdxl_api.json": return getSdxlDefaultTemplate();
            case "template_flux_api.json": return getFluxDefaultTemplate();
            case "template_lumina2_api.json": return getLumina2DefaultTemplate();
            case "template_sd3_api.json": return getSd3DefaultTemplate();
            case "template_wan_api.json": return getWanDefaultTemplate();
            case "template_hunyuan_api.json": return getHunyuanDefaultTemplate();
            default: return getSd15DefaultTemplate();
        }
    }

    private String getSd3DefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 42,\n" +
                "        \"steps\": 28,\n" +
                "        \"cfg\": 4.5,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"normal\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"KSampler\"\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"inputs\": {\n" +
                "        \"ckpt_name\": \"sd3_medium.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptySD3LatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"beautiful scenery, mountain, sunset, hyperrealistic, 8k\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"bad hands, blurry, worst quality, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"4\",\n" +
                "          1\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"4\",\n" +
                "          2\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_SD3\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private String getWanDefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 42,\n" +
                "        \"steps\": 20,\n" +
                "        \"cfg\": 5.0,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"normal\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"KSampler\"\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"inputs\": {\n" +
                "        \"unet_name\": \"wan2.1_hybrid.safetensors\",\n" +
                "        \"weight_dtype\": \"default\"\n" +
                "      },\n" +
                "      \"class_type\": \"UNETLoader\"\n" +
                "    },\n" +
                "    \"4_clip\": {\n" +
                "      \"inputs\": {\n" +
                "        \"clip_name\": \"umt5_xxl.safetensors\",\n" +
                "        \"type\": \"wan\"\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPLoader\"\n" +
                "    },\n" +
                "    \"4_vae\": {\n" +
                "      \"inputs\": {\n" +
                "        \"vae_name\": \"wan_2.1_vae.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"VAELoader\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptySD3LatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"beautiful scenery, mountain, sunset, hyperrealistic, 8k\",\n" +
                "        \"clip\": [\n" +
                "          \"4_clip\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"bad hands, blurry, worst quality, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"4_clip\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"4_vae\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_Wan\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    private String getHunyuanDefaultTemplate() {
        return "{\n" +
                "  \"prompt\": {\n" +
                "    \"3\": {\n" +
                "      \"inputs\": {\n" +
                "        \"seed\": 42,\n" +
                "        \"steps\": 20,\n" +
                "        \"cfg\": 6.0,\n" +
                "        \"sampler_name\": \"euler\",\n" +
                "        \"scheduler\": \"normal\",\n" +
                "        \"denoise\": 1.0,\n" +
                "        \"model\": [\n" +
                "          \"4\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"positive\": [\n" +
                "          \"6\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"negative\": [\n" +
                "          \"7\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"latent_image\": [\n" +
                "          \"5\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"KSampler\"\n" +
                "    },\n" +
                "    \"4\": {\n" +
                "      \"inputs\": {\n" +
                "        \"unet_name\": \"hunyuan_dit.safetensors\",\n" +
                "        \"weight_dtype\": \"default\"\n" +
                "      },\n" +
                "      \"class_type\": \"UNETLoader\"\n" +
                "    },\n" +
                "    \"4_clip\": {\n" +
                "      \"inputs\": {\n" +
                "        \"clip_name\": \"t5xxl.safetensors\",\n" +
                "        \"type\": \"hunyuan\"\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPLoader\"\n" +
                "    },\n" +
                "    \"4_vae\": {\n" +
                "      \"inputs\": {\n" +
                "        \"vae_name\": \"hunyuan_vae.safetensors\"\n" +
                "      },\n" +
                "      \"class_type\": \"VAELoader\"\n" +
                "    },\n" +
                "    \"5\": {\n" +
                "      \"inputs\": {\n" +
                "        \"width\": 1024,\n" +
                "        \"height\": 1024,\n" +
                "        \"batch_size\": 1\n" +
                "      },\n" +
                "      \"class_type\": \"EmptyLatentImage\"\n" +
                "    },\n" +
                "    \"6\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"beautiful scenery, mountain, sunset, hyperrealistic, 8k\",\n" +
                "        \"clip\": [\n" +
                "          \"4_clip\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"7\": {\n" +
                "      \"inputs\": {\n" +
                "        \"text\": \"bad hands, blurry, worst quality, low quality\",\n" +
                "        \"clip\": [\n" +
                "          \"4_clip\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"CLIPTextEncode\"\n" +
                "    },\n" +
                "    \"8\": {\n" +
                "      \"inputs\": {\n" +
                "        \"samples\": [\n" +
                "          \"3\",\n" +
                "          0\n" +
                "        ],\n" +
                "        \"vae\": [\n" +
                "          \"4_vae\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"VAEDecode\"\n" +
                "    },\n" +
                "    \"9\": {\n" +
                "      \"inputs\": {\n" +
                "        \"filename_prefix\": \"ComfyUI_Hunyuan\",\n" +
                "        \"images\": [\n" +
                "          \"8\",\n" +
                "          0\n" +
                "        ]\n" +
                "      },\n" +
                "      \"class_type\": \"SaveImage\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    @Override
    public JsonNode injectParameters(JsonNode templateTree, String modelName, String positivePrompt, String negativePrompt) {
        if (templateTree == null || !templateTree.isObject()) {
            return templateTree;
        }

        com.fasterxml.jackson.databind.node.ObjectNode promptObj;
        if (templateTree.has("prompt")) {
            promptObj = (com.fasterxml.jackson.databind.node.ObjectNode) templateTree.get("prompt");
        } else {
            promptObj = (com.fasterxml.jackson.databind.node.ObjectNode) templateTree;
        }

        // 1. Model / UNET loader update
        java.util.Iterator<String> fieldNames = promptObj.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode nodeObj = (com.fasterxml.jackson.databind.node.ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                if ("CheckpointLoaderSimple".equals(classType)) {
                    com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) nodeObj.path("inputs");
                    if (!inputs.isMissingNode()) {
                        inputs.put("ckpt_name", modelName);
                    }
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) nodeObj.path("inputs");
                    if (!inputs.isMissingNode()) {
                        inputs.put("unet_name", modelName);
                    }
                }
            }
        }

        // 2. Positive & Negative prompts via CLIPTextEncode heuristic
        java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> clipNodes = new java.util.ArrayList<>();
        fieldNames = promptObj.fieldNames();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode nodeObj = (com.fasterxml.jackson.databind.node.ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                if ("CLIPTextEncode".equals(classType)) {
                    clipNodes.add(nodeObj);
                }
            }
        }

        com.fasterxml.jackson.databind.node.ObjectNode positiveNode = null;
        com.fasterxml.jackson.databind.node.ObjectNode negativeNode = null;

        if (clipNodes.size() == 1) {
            positiveNode = clipNodes.get(0);
        } else if (clipNodes.size() >= 2) {
            // Find negative by checking keywords in existing text
            for (com.fasterxml.jackson.databind.node.ObjectNode node : clipNodes) {
                com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) node.path("inputs");
                if (inputs != null && !inputs.isMissingNode() && inputs.has("text")) {
                    String text = inputs.get("text").asText().toLowerCase();
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
                for (com.fasterxml.jackson.databind.node.ObjectNode node : clipNodes) {
                    if (node != positiveNode) {
                        negativeNode = node;
                        break;
                    }
                }
            }
        }

        if (positiveNode != null) {
            com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) positiveNode.path("inputs");
            if (inputs != null && !inputs.isMissingNode()) {
                inputs.put("text", positivePrompt != null ? positivePrompt : "");
            }
        }

        if (negativeNode != null) {
            com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) negativeNode.path("inputs");
            if (inputs != null && !inputs.isMissingNode()) {
                String neg = (negativePrompt != null && !negativePrompt.trim().isEmpty()) ? negativePrompt : "blurry, low quality, worst quality";
                inputs.put("text", neg);
            }
        }

        // 3. Seed / noise_seed randomization for samplers and noise nodes
        fieldNames = promptObj.fieldNames();
        java.util.Random rand = new java.util.Random();
        while (fieldNames.hasNext()) {
            String nodeId = fieldNames.next();
            JsonNode nodeNode = promptObj.get(nodeId);
            if (nodeNode.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode nodeObj = (com.fasterxml.jackson.databind.node.ObjectNode) nodeNode;
                String classType = nodeObj.path("class_type").asText("");
                com.fasterxml.jackson.databind.node.ObjectNode inputs = (com.fasterxml.jackson.databind.node.ObjectNode) nodeObj.path("inputs");
                if (inputs != null && !inputs.isMissingNode()) {
                    if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType) || classType.contains("Sampler")) {
                        long randomSeed = Math.abs(rand.nextLong()) % 9007199254740991L;
                        java.util.Iterator<String> inputKeys = inputs.fieldNames();
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
                        long randomSeed = Math.abs(rand.nextLong()) % 9007199254740991L;
                        inputs.put("noise_seed", randomSeed);
                    }
                }
            }
        }

        return templateTree;
    }

    @Override
    public String generatePayload(JsonNode templateTree) {
        if (templateTree == null) {
            return "{}";
        }
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        if (templateTree.has("prompt")) {
            root.set("prompt", templateTree.get("prompt"));
        } else {
            root.set("prompt", templateTree);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            logger.error("❌ [TemplateService] Failed to serialize payload: " + e.getMessage());
            return root.toString();
        }
    }
}
