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
public class ExtraModelPathsYamlSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(ExtraModelPathsYamlSynchronizer.class);

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

                Map<String, Object> companionSection = new LinkedHashMap<>();
                companionSection.put("base_path", normalizedModelsPath);
                companionSection.put("checkpoints", "checkpoints");
                companionSection.put("configs", "configs");
                companionSection.put("vae", "vae");
                companionSection.put("loras", "loras\nlycoris");
                companionSection.put("upscale_models", "upscale_models\nrealesrgan");
                companionSection.put("controlnet", "controlnet");
                companionSection.put("clip", "clip\ntext_encoders");
                companionSection.put("clip_vision", "clip_vision");
                companionSection.put("style_models", "style_models");
                companionSection.put("hypernetworks", "hypernetworks");
                companionSection.put("embeddings", "embeddings");
                companionSection.put("diffusers", "diffusers");
                companionSection.put("gligen", "gligen");
                companionSection.put("unet", "unet\ndiffusion_models");
                companionSection.put("audio_encoders", "audio_encoders");
                companionSection.put("vae_approx", "vae_approx");
                companionSection.put("photomaker", "photomaker");
                companionSection.put("ipadapter", "ipadapter");
                companionSection.put("onnx", "onnx");
                companionSection.put("llm", "llm");
                companionSection.put("model_patches", "model_patches");
                companionSection.put("latent_upscale_models", "latent_upscale_models");

                data.put("comfyui_companion", companionSection);

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
}
