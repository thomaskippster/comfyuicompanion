package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing ComfyUI workflow templates, architecture-to-template resolution,
 * and generation payload adjustments.
 */
@Service
public class ComfyTemplateService implements IComfyTemplateService {
    private static final Logger logger = LoggerFactory.getLogger(ComfyTemplateService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConfigService configService;
    private final ModelListService modelListService;
    private final IModelArchitectureService modelArchitectureService;
    private final ComfyPayloadModifier payloadModifier;
    private final Map<String, ComfyTemplate> templateCache = new ConcurrentHashMap<>();
    private final Map<String, String> modelToTemplateMap = new ConcurrentHashMap<>();

    public ComfyTemplateService(ConfigService configService, ModelListService modelListService) {
        this(configService, modelListService, null, new ComfyPayloadModifier());
    }

    public ComfyTemplateService(ConfigService configService, ModelListService modelListService,
                                IModelArchitectureService modelArchitectureService) {
        this(configService, modelListService, modelArchitectureService, new ComfyPayloadModifier());
    }

    @Autowired
    public ComfyTemplateService(
            ConfigService configService,
            ModelListService modelListService,
            @Autowired(required = false) IModelArchitectureService modelArchitectureService,
            @Autowired(required = false) ComfyPayloadModifier payloadModifier) {
        this.configService = configService;
        this.modelListService = modelListService;
        this.modelArchitectureService = modelArchitectureService;
        this.payloadModifier = payloadModifier != null ? payloadModifier : new ComfyPayloadModifier();
    }

    @PostConstruct
    public void init() {
        scanTemplates();
    }

    @Override
    public void scanTemplates() {
        File templatesDir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui");
        if (!templatesDir.exists()) {
            boolean created = templatesDir.mkdirs();
            if (created) {
                logger.info("📂 [TemplateService] Created templates directory at: {}", templatesDir.getAbsolutePath());
            }
        }

        File mappingFile = new File(templatesDir, "model_template_mapping.json");
        if (!mappingFile.exists()) {
            writeDefaultMappingFile(mappingFile);
        }
        loadMappingFile(mappingFile);

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
                writeJsonFile(templateFile, DefaultComfyTemplates.getDefaultTemplateForName(dfn));
                logger.info("📂 [TemplateService] Created missing architecture template: {}", dfn);
            }
        }

        File[] files = templatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json")
                && !name.equalsIgnoreCase("model_template_mapping.json")
                && !name.equalsIgnoreCase("model_architecture_mapping.json"));
        if (files == null || files.length == 0 || needsFluxUpgrade || needsLuminaWrite) {
            writeDefaultTemplates(templatesDir);
            files = templatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json")
                    && !name.equalsIgnoreCase("model_template_mapping.json")
                    && !name.equalsIgnoreCase("model_architecture_mapping.json"));
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
                    logger.info("📄 [TemplateService] Loaded template: {} ({})", name, filename);
                } catch (IOException e) {
                    logger.error("❌ [TemplateService] Error reading template file {}: {}", file.getName(), e.getMessage());
                }
            }
        }
    }

    @Override
    public List<ComfyTemplate> getTemplates() {
        List<ComfyTemplate> list = new ArrayList<>(templateCache.values());
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
        if (modelName == null || modelName.trim().isEmpty()) {
            ComfyTemplate def = getTemplateByFilename("sd15_base_api.json");
            return def != null ? def : getTemplateByFilename("template_sd15_api.json");
        }

        String lowerModelName = modelName.toLowerCase();

        // 1. Direct explicit mapping check
        if (modelToTemplateMap.containsKey(lowerModelName)) {
            String targetFilename = modelToTemplateMap.get(lowerModelName);
            ComfyTemplate template = getTemplateByFilename(targetFilename);
            if (template != null) {
                return template;
            }
        }

        // 2. Base model check via ModelListService
        if (modelListService != null) {
            java.util.Optional<de.tki.comfyuicompanion.domain.ModelInfo> modelInfoOpt = modelListService.findByFilename(modelName);
            if (modelInfoOpt.isPresent()) {
                de.tki.comfyuicompanion.domain.ModelInfo info = modelInfoOpt.get();
                if (info.getBase() != null && !info.getBase().isEmpty()) {
                    String lowerBase = info.getBase().toLowerCase();
                    if (lowerBase.contains("flux") || lowerBase.contains("schnell") || lowerBase.contains("dev")) {
                        ComfyTemplate t = getTemplateByFilename("flux_base_api.json");
                        if (t == null) t = getTemplateByFilename("template_flux_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("sdxl") || lowerBase.contains("pony") || lowerBase.contains("illustrious")) {
                        ComfyTemplate t = getTemplateByFilename("sdxl_base_api.json");
                        if (t == null) t = getTemplateByFilename("template_sdxl_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("sd 3") || lowerBase.contains("sd3")) {
                        ComfyTemplate t = getTemplateByFilename("template_sd3_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("wan") || lowerBase.contains("ltx")) {
                        ComfyTemplate t = getTemplateByFilename("template_wan_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("hunyuan")) {
                        ComfyTemplate t = getTemplateByFilename("template_hunyuan_api.json");
                        if (t != null) return t;
                    } else if (lowerBase.contains("1.5") || lowerBase.contains("sd 1.5")) {
                        ComfyTemplate t = getTemplateByFilename("sd15_base_api.json");
                        if (t == null) t = getTemplateByFilename("template_sd15_api.json");
                        if (t != null) return t;
                    }
                }
            }
        }

        // 3. Architecture service detection
        if (modelArchitectureService != null) {
            ModelArchitecture arch = modelArchitectureService.detectArchitecture(modelName);
            if (arch != ModelArchitecture.ARCH_UNKNOWN) {
                String archFilename = getTemplateFilenameForArchitecture(arch);
                ComfyTemplate t = getTemplateByFilename(archFilename);
                if (t == null) {
                    if (arch == ModelArchitecture.ARCH_FLUX) t = getTemplateByFilename("flux_base_api.json");
                    else if (arch == ModelArchitecture.ARCH_LUMINA2) t = getTemplateByFilename("lumina2_base_api.json");
                    else if (arch == ModelArchitecture.ARCH_SDXL) t = getTemplateByFilename("sdxl_base_api.json");
                    else if (arch == ModelArchitecture.ARCH_SD15) t = getTemplateByFilename("sd15_base_api.json");
                }
                if (t != null) return t;
            }
        }

        // 4. Keyword heuristic fallbacks
        if (lowerModelName.contains("flux1-schnell") || lowerModelName.contains("flux_schnell") || lowerModelName.contains("schnell") || lowerModelName.contains("flux") || lowerModelName.contains("dev")) {
            ComfyTemplate t = getTemplateByFilename("flux_base_api.json");
            if (t == null) t = getTemplateByFilename("template_flux_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("xl") || lowerModelName.contains("sdxl") || lowerModelName.contains("juggernaut") || lowerModelName.contains("pony") || lowerModelName.contains("ernie")) {
            ComfyTemplate t = getTemplateByFilename("sdxl_base_api.json");
            if (t == null) t = getTemplateByFilename("template_sdxl_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("longcat") || lowerModelName.contains("lumina") || lowerModelName.contains("z_image") || lowerModelName.contains("z-image") || lowerModelName.contains("acestep") || lowerModelName.contains("firered") || lowerModelName.contains("qwen")) {
            ComfyTemplate t = getTemplateByFilename("lumina2_base_api.json");
            if (t == null) t = getTemplateByFilename("template_lumina2_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("sd3") || lowerModelName.contains("stable_diffusion_3")) {
            ComfyTemplate t = getTemplateByFilename("template_sd3_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("wan") || lowerModelName.contains("ltx")) {
            ComfyTemplate t = getTemplateByFilename("template_wan_api.json");
            if (t != null) return t;
        } else if (lowerModelName.contains("hunyuan")) {
            ComfyTemplate t = getTemplateByFilename("template_hunyuan_api.json");
            if (t != null) return t;
        }

        // 5. Default fallback
        ComfyTemplate def = getTemplateByFilename("sd15_base_api.json");
        return def != null ? def : getTemplateByFilename("template_sd15_api.json");
    }

    @Override
    public String modifyPayload(String templateJson, String modelName, String positivePrompt, String negativePrompt) {
        return payloadModifier.modifyPayload(templateJson, modelName, positivePrompt, negativePrompt);
    }

    @Override
    public JsonNode injectParameters(JsonNode templateTree, String modelName, String positivePrompt, String negativePrompt) {
        return payloadModifier.injectParameters(templateTree, modelName, positivePrompt, negativePrompt);
    }

    @Override
    public String generatePayload(JsonNode templateTree) {
        return payloadModifier.generatePayload(templateTree, objectMapper);
    }

    @Override
    public JsonNode loadTemplateForArchitecture(ModelArchitecture architecture) throws IOException {
        String filename = getTemplateFilenameForArchitecture(architecture);
        File templatesDir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui");
        File templateFile = new File(templatesDir, filename);
        if (!templateFile.exists()) {
            writeJsonFile(templateFile, DefaultComfyTemplates.getDefaultTemplateForName(filename));
        }
        return objectMapper.readTree(templateFile);
    }

    @Override
    public String getTemplateFilenameForArchitecture(ModelArchitecture arch) {
        return switch (arch) {
            case ARCH_FLUX -> "template_flux_api.json";
            case ARCH_SD15 -> "template_sd15_api.json";
            case ARCH_SDXL -> "template_sdxl_api.json";
            case ARCH_SD3 -> "template_sd3_api.json";
            case ARCH_LUMINA2 -> "template_lumina2_api.json";
            case ARCH_WAN -> "template_wan_api.json";
            case ARCH_HUNYUAN -> "template_hunyuan_api.json";
            default -> "template_sd15_api.json";
        };
    }

    private void writeDefaultTemplates(File targetDir) {
        logger.info("💾 [TemplateService] Populating default templates in: {}", targetDir.getAbsolutePath());
        writeJsonFile(new File(targetDir, "sd15_base_api.json"), DefaultComfyTemplates.getSd15DefaultTemplate());
        writeJsonFile(new File(targetDir, "sdxl_base_api.json"), DefaultComfyTemplates.getSdxlDefaultTemplate());
        writeJsonFile(new File(targetDir, "flux_base_api.json"), DefaultComfyTemplates.getFluxDefaultTemplate());
        writeJsonFile(new File(targetDir, "lumina2_base_api.json"), DefaultComfyTemplates.getLumina2DefaultTemplate());

        writeJsonFile(new File(targetDir, "template_sd15_api.json"), DefaultComfyTemplates.getSd15DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_sdxl_api.json"), DefaultComfyTemplates.getSdxlDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_flux_api.json"), DefaultComfyTemplates.getFluxDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_lumina2_api.json"), DefaultComfyTemplates.getLumina2DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_sd3_api.json"), DefaultComfyTemplates.getSd3DefaultTemplate());
        writeJsonFile(new File(targetDir, "template_wan_api.json"), DefaultComfyTemplates.getWanDefaultTemplate());
        writeJsonFile(new File(targetDir, "template_hunyuan_api.json"), DefaultComfyTemplates.getHunyuanDefaultTemplate());
    }

    private void writeJsonFile(File file, String content) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("❌ [TemplateService] Error writing default template file {}: {}", file.getName(), e.getMessage());
        }
    }

    private void writeDefaultMappingFile(File file) {
        String content = """
                {
                  "mappings": {
                    "v1-5-pruned-emaonly.safetensors": "sd15_base_api.json",
                    "sd_xl_base_1.0.safetensors": "sdxl_base_api.json",
                    "flux1-schnell-fp8.safetensors": "flux_base_api.json",
                    "longcat_image_bf16.safetensors": "lumina2_base_api.json",
                    "z_image_turbo_bf16.safetensors": "lumina2_base_api.json",
                    "z_image_bf16.safetensors": "lumina2_base_api.json",
                    "acestep_v1.5_turbo.safetensors": "lumina2_base_api.json"
                  }
                }""";
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("❌ [TemplateService] Error writing default mapping file: {}", e.getMessage());
        }
    }

    private void loadMappingFile(File file) {
        modelToTemplateMap.clear();
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(content);
            if (obj.has("mappings")) {
                JSONObject mappings = obj.getJSONObject("mappings");
                for (String modelKey : mappings.keySet()) {
                    modelToTemplateMap.put(modelKey.toLowerCase(), mappings.getString(modelKey));
                }
            }
        } catch (Exception e) {
            logger.error("❌ [TemplateService] Error loading mapping file: {}", e.getMessage());
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
}
