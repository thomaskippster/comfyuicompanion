package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronizer responsible for reading and updating ComfyUI {@code extra_model_paths.yaml}
 * and ComfyUI Desktop {@code extra_models_config.yaml} files.
 */
@Component
/**
 * Represents the extra model paths yaml synchronizer class.
 */
public class ExtraModelPathsYamlSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(ExtraModelPathsYamlSynchronizer.class);

    private final de.tki.comfyuicompanion.service.ILocalModelArchiveDetector archiveDetector;

    public ExtraModelPathsYamlSynchronizer() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExtraModelPathsYamlSynchronizer(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            de.tki.comfyuicompanion.service.ILocalModelArchiveDetector archiveDetector) {
        this.archiveDetector = archiveDetector;
    }

    /**
     * Loads extra model paths from existing YAML files into the provided {@link PathResolver}.
     *
     * @param comfyRoot         the root directory of ComfyUI
     * @param isTestEnvironment whether the runtime is currently within a test suite
     * @param pathResolver      the path resolver to receive model folder mappings
     */
    public void loadExtraModelPaths(String comfyRoot, boolean isTestEnvironment, PathResolver pathResolver) {
        pathResolver.clearExtraModelPaths();
        if (comfyRoot == null || comfyRoot.trim().isEmpty()) return;

        Path extraPathsFile = Paths.get(comfyRoot).resolve("extra_model_paths.yaml");
        if (!Files.exists(extraPathsFile) && !isTestEnvironment) {
            String userHome = System.getProperty("user.home");
            extraPathsFile = Paths.get(userHome, "AppData/Roaming/ComfyUI/extra_models_config.yaml");
        }

        if (Files.exists(extraPathsFile)) {
            logger.info("📄 [Config] Loading extra model paths from: {}", extraPathsFile.toAbsolutePath());
            try (InputStream is = new FileInputStream(extraPathsFile.toFile())) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null) {
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> section = (Map<String, Object>) entry.getValue();
                            String basePathStr = (String) section.get("base_path");
                            if (basePathStr == null) continue;

                            Path yamlDir = extraPathsFile.getParent();
                            Path basePath = Paths.get(basePathStr);
                            if (!basePath.isAbsolute()) {
                                basePath = yamlDir.resolve(basePath).toAbsolutePath().normalize();
                            }

                            logger.info("📂 [Config] Section '{}' base_path: {}", entry.getKey(), basePath);

                            for (Map.Entry<String, Object> config : section.entrySet()) {
                                if ("base_path".equals(config.getKey())) continue;

                                String type = config.getKey();
                                Object value = config.getValue();

                                if (value instanceof String stringValue) {
                                    String[] folders = stringValue.split("\\R");
                                    for (String folder : folders) {
                                        String trimmed = folder.trim();
                                        if (!trimmed.isEmpty()) {
                                            Path fullPath = basePath.resolve(trimmed).toAbsolutePath().normalize();
                                            if (!Files.exists(fullPath)) {
                                                logger.debug("   ℹ️ [Config] Path does not exist yet: {}", fullPath);
                                            }
                                            pathResolver.addExtraModelPath(type, fullPath);
                                            logger.info("   -> Mapping [{}] to: {}", type, fullPath);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error parsing extra_model_paths.yaml: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Updates or creates the companion entry inside {@code extra_model_paths.yaml}.
     *
     * @param comfyRoot  the root directory of ComfyUI
     * @param modelsPath the absolute directory containing models
     */
    public void updateExtraModelPathsYaml(String comfyRoot, String modelsPath) {
        if (modelsPath == null || modelsPath.isEmpty()) return;

        // Normalize backslashes to forward slashes to prevent escape issues in YAML
        String normalizedModelsPath = modelsPath.replace("\\", "/");

        List<Path> targetYamlFiles = new ArrayList<>();
        if (comfyRoot != null && !comfyRoot.isEmpty()) {
            File comfyDir = new File(comfyRoot);
            if (comfyDir.exists() && comfyDir.isDirectory()) {
                targetYamlFiles.add(Paths.get(comfyRoot).resolve("extra_model_paths.yaml"));
            }
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path appDataDir = Paths.get(userHome, "AppData", "Roaming", "ComfyUI");
            if (Files.exists(appDataDir) && Files.isDirectory(appDataDir)) {
                targetYamlFiles.add(appDataDir.resolve("extra_models_config.yaml"));
            }
        }

        if (targetYamlFiles.isEmpty()) return;

        for (Path yamlPath : targetYamlFiles) {
            Map<String, Object> data = null;
            try {
                Yaml yaml = new Yaml();
                if (Files.exists(yamlPath)) {
                    try (InputStream is = new FileInputStream(yamlPath.toFile())) {
                        data = yaml.load(is);
                    } catch (Exception ex) {
                        logger.warn("Failed to load existing YAML at {}: {}", yamlPath, ex.getMessage(), ex);
                    }
                }

                if (data == null) {
                    data = new LinkedHashMap<>();
                }

                data.put("comfyui_companion", buildModelFolderMapping(normalizedModelsPath));

                if (archiveDetector != null) {
                    List<Path> archiveRoots = archiveDetector.detectArchiveRoots();
                    int archiveIndex = 1;
                    for (Path archiveRoot : archiveRoots) {
                        String normArchive = archiveRoot.toString().replace("\\", "/");
                        if (!normArchive.equalsIgnoreCase(normalizedModelsPath)) {
                            String sectionKey = "comfyui_archive_" + (archiveIndex++);
                            data.put(sectionKey, buildModelFolderMapping(normArchive));
                        }
                    }
                }

                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                options.setPrettyFlow(true);
                Yaml yamlDump = new Yaml(options);

                String yamlContent = yamlDump.dump(data);
                Files.writeString(yamlPath, yamlContent, StandardCharsets.UTF_8);
                logger.info("📄 [Config] Successfully updated YAML at: {}", yamlPath.toAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to update YAML at {}: {}", yamlPath, e.getMessage(), e);
            }
        }
    }
    private Map<String, Object> buildModelFolderMapping(String basePath) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("base_path", basePath.replace("\\", "/"));
        section.put("checkpoints", "checkpoints");
        section.put("configs", "configs");
        section.put("vae", "vae");
        section.put("loras", "loras\nlycoris");
        section.put("upscale_models", "upscale_models\nrealesrgan");
        section.put("controlnet", "controlnet");
        section.put("clip", "clip\ntext_encoders");
        section.put("text_encoders", "text_encoders\nclip");
        section.put("clip_vision", "clip_vision");
        section.put("style_models", "style_models");
        section.put("hypernetworks", "hypernetworks");
        section.put("embeddings", "embeddings");
        section.put("diffusers", "diffusers");
        section.put("gligen", "gligen");
        section.put("unet", "unet\ndiffusion_models\ncheckpoints");
        section.put("diffusion_models", "diffusion_models\nunet\ncheckpoints");
        section.put("audio_encoders", "audio_encoders");
        section.put("vae_approx", "vae_approx");
        section.put("photomaker", "photomaker");
        section.put("ipadapter", "ipadapter");
        section.put("onnx", "onnx");
        section.put("llm", "llm");
        section.put("model_patches", "model_patches");
        section.put("latent_upscale_models", "latent_upscale_models");
        return section;
    }
}
