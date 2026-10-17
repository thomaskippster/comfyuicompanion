package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service component responsible for scanning, verifying, and matching local model files
 * (checkpoints, diffusion models, UNETs, VAEs, text encoders) on the filesystem.
 */
@Component
public class CivitaiLocalModelScanner {

    private static final Logger log = LoggerFactory.getLogger(CivitaiLocalModelScanner.class);

    private final IConfigService configService;

    /**
     * Constructs a new CivitaiLocalModelScanner with the given configuration service.
     *
     * @param configService the configuration service providing base model paths
     */
    @Autowired
    public CivitaiLocalModelScanner(@Autowired(required = false) IConfigService configService) {
        this.configService = configService;
    }

    /**
     * Checks if the physical file for the given {@link ModelInfo} exists on disk and sets its status.
     *
     * @param info the model info to inspect and update
     */
    public void checkAndSetModelStatus(ModelInfo info) {
        boolean present = isModelFilePresent(info);
        info.setPopularity(present ? "installed" : "missing");
        info.setDescription(present ? "✅ Already exists" : "Missing");
    }

    /**
     * Determines whether the physical model file described by {@link ModelInfo} exists on disk.
     *
     * @param info the model metadata
     * @return {@code true} if the file exists, {@code false} otherwise
     */
    public boolean isModelFilePresent(ModelInfo info) {
        if (info == null) return false;
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
        if (folder.startsWith("models/") || folder.startsWith("models\\")) {
            folder = folder.substring(7);
        }

        Path target = Path.of(baseDir != null ? baseDir : "models", folder, info.getFilename() != null ? info.getFilename() : info.getName());
        return Files.exists(target);
    }

    /**
     * Searches local model directories (checkpoints, diffusion_models, unet) for the first existing checkpoint file.
     *
     * @return an {@link Optional} containing the {@link ModelInfo} for the first found checkpoint, or empty if none exist
     */
    public Optional<ModelInfo> findFirstLocalCheckpointInfo() {
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        String[] candidateDirs = {"checkpoints", "diffusion_models", "unet"};

        for (String sub : candidateDirs) {
            Path dir = Path.of(baseDir != null ? baseDir : "models", sub);
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.walk(dir, 3)) {
                    Optional<Path> match = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".sft");
                            })
                            .findFirst();

                    if (match.isPresent()) {
                        Path file = match.get();
                        String name = file.getFileName().toString();
                        long sizeBytes = Files.size(file);
                        String formattedSize = formatBytes(sizeBytes);

                        ModelInfo info = new ModelInfo(sub, name, "LOCAL");
                        info.setFilename(name);
                        info.setSize(formattedSize);
                        info.setByteSize(sizeBytes);
                        info.setPopularity("installed");
                        info.setDescription("✅ Already exists");
                        return Optional.of(info);
                    }
                } catch (IOException ex) {
                    log.debug("Error while inspecting {} for local checkpoints: {}", sub, ex.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the filename of the first locally installed checkpoint, or empty if none are found.
     *
     * @return optional containing the filename of the installed checkpoint
     */
    public Optional<String> findFirstLocalCheckpointName() {
        return findFirstLocalCheckpointInfo().map(ModelInfo::getFilename);
    }

    /**
     * Finds a local model file in any of the specified directories matching the given name substring.
     *
     * @param lowerPattern search substring
     * @param folders candidate subfolders inside models directory (e.g. "vae", "text_encoders")
     * @return ModelInfo if found, null otherwise
     */
    public ModelInfo findLocalModelMatching(String lowerPattern, String... folders) {
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        for (String folder : folders) {
            Path dir = Path.of(baseDir != null ? baseDir : "models", folder);
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.walk(dir, 3)) {
                    Optional<Path> match = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains(lowerPattern.toLowerCase(Locale.ROOT)))
                            .findFirst();

                    if (match.isPresent()) {
                        Path file = match.get();
                        String name = file.getFileName().toString();
                        long sizeBytes = Files.size(file);
                        String formattedSize = formatBytes(sizeBytes);

                        ModelInfo info = new ModelInfo(folder, name, "LOCAL");
                        info.setFilename(name);
                        info.setSize(formattedSize);
                        info.setByteSize(sizeBytes);
                        info.setPopularity("installed");
                        info.setDescription("✅ Already exists");
                        return info;
                    }
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    /**
     * Determines a fallback checkpoint filename when building workflows for LoRA models
     * that have no explicit base checkpoint attached in their dependencies.
     *
     * @param baseLower the lowercased base architecture string
     * @return a valid checkpoint filename
     */
    public String findFallbackCheckpointName(String baseLower) {
        if (configService != null) {
            String modelsPath = configService.getModelsPath();
            Path checkpointsDir = Path.of(modelsPath != null ? modelsPath : "models", "checkpoints");
            if (Files.exists(checkpointsDir) && Files.isDirectory(checkpointsDir)) {
                try (Stream<Path> stream = Files.walk(checkpointsDir, 3)) {
                    Optional<Path> match = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.endsWith(".safetensors") || name.endsWith(".ckpt");
                            })
                            .findFirst();
                    if (match.isPresent()) {
                        return match.get().getFileName().toString();
                    }
                } catch (IOException ex) {
                    log.debug("Could not inspect checkpoints folder for fallback: {}", ex.getMessage());
                }
            }
        }
        if (baseLower != null && baseLower.contains("flux")) {
            return "flux1-dev-fp8.safetensors";
        } else if (baseLower != null && (baseLower.contains("sdxl") || baseLower.contains("pony"))) {
            return "sd_xl_base_1.0.safetensors";
        }
        return "v1-5-pruned-emaonly.safetensors";
    }

    /**
     * Creates a {@link ModelInfo} descriptor for a remote model that needs to be downloaded.
     *
     * @param filename      target filename
     * @param folder        target destination folder
     * @param formattedSize human-readable size
     * @param url           remote download URL
     * @param desc          description or reference note
     * @return a new ModelInfo instance
     */
    public ModelInfo createRemoteModelInfo(String filename, String folder, String formattedSize, String url, String desc) {
        ModelInfo info = new ModelInfo(folder, filename, url);
        info.setFilename(filename);
        info.setSize(formattedSize);
        info.setDescription("Missing");
        info.setPopularity("missing");
        info.setReference(desc);
        return info;
    }

    /**
     * Formats byte size into human readable string (GB, MB, B).
     *
     * @param bytes byte size
     * @return formatted size string
     */
    public String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return bytes + " B";
        }
    }
}
