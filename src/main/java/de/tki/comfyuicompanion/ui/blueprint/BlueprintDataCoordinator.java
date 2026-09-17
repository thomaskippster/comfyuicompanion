package de.tki.comfyuicompanion.ui.blueprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyRegistryClient;
import de.tki.comfyuicompanion.service.ILocalModelValidator;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import de.tki.comfyuicompanion.ui.VideoPreviewDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Coordinates model validation, disk inspection, byte size formatting,
 * registry workflow mapping, and asynchronous video preview downloading/playback.
 */
public class BlueprintDataCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintDataCoordinator.class);

    private final ConfigService configService;
    private final LocalModelScanner localModelScanner;
    private final ArchiveService archiveService;
    private final ILocalModelValidator localModelValidator;
    private final IModelArchitectureService modelArchitectureService;
    private final IComfyRegistryClient registryClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BlueprintDataCoordinator(ConfigService configService,
                                    LocalModelScanner localModelScanner,
                                    ArchiveService archiveService,
                                    ILocalModelValidator localModelValidator,
                                    IModelArchitectureService modelArchitectureService,
                                    IComfyRegistryClient registryClient) {
        this.configService = configService;
        this.localModelScanner = localModelScanner;
        this.archiveService = archiveService;
        this.localModelValidator = localModelValidator;
        this.modelArchitectureService = modelArchitectureService;
        this.registryClient = registryClient;
    }

    public List<BlueprintEntry> fetchAndIndexEntries(Consumer<Runnable> asyncModelValidatorCallback) throws Exception {
        if (localModelValidator != null) {
            localModelValidator.scanLocalModels();
        }

        List<ComfyRegistryWorkflow> workflows = registryClient != null
                ? registryClient.fetchWorkflowsAsync().get()
                : Collections.emptyList();

        List<Map<String, Object>> scanResults = Collections.emptyList();
        if (modelArchitectureService != null) {
            try {
                List<Map<String, Object>> res = modelArchitectureService.getBlueprintScanResults();
                if (res != null) {
                    scanResults = res;
                }
            } catch (Exception ignored) {}
        }

        Map<String, Map<String, Object>> scanLookup = new HashMap<>();
        for (Map<String, Object> scan : scanResults) {
            String scanName = (String) scan.get("name");
            String scanFilename = (String) scan.get("filename");
            if (scanName != null) scanLookup.put(scanName.toLowerCase(Locale.ROOT), scan);
            if (scanFilename != null) {
                String fnLower = scanFilename.toLowerCase(Locale.ROOT);
                scanLookup.put(fnLower, scan);
                if (fnLower.endsWith(".json")) {
                    scanLookup.put(fnLower.substring(0, fnLower.length() - 5), scan);
                }
            }
        }

        List<BlueprintEntry> loadedEntries = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (ComfyRegistryWorkflow wf : workflows) {
            String wfId = wf.getId() != null ? wf.getId() : wf.getTitle();
            if (wfId == null || !seenIds.add(wfId)) {
                continue;
            }

            BlueprintEntry entry = new BlueprintEntry(wf);

            Map<String, Object> matchedScan = null;
            if (wfId != null) matchedScan = scanLookup.get(wfId.toLowerCase(Locale.ROOT));
            if (matchedScan == null && wf.getTitle() != null) {
                matchedScan = scanLookup.get(wf.getTitle().toLowerCase(Locale.ROOT));
            }

            List<ModelInfo> resolved = null;
            if (matchedScan != null) {
                Object reqObj = matchedScan.get("requiredModels");
                resolved = extractModelInfos(reqObj, mapper);
            }
            if ((resolved == null || resolved.isEmpty()) && localModelValidator != null) {
                if (wfId != null) resolved = localModelValidator.getCachedModelsForWorkflow(wfId);
                if ((resolved == null || resolved.isEmpty()) && wf.getTitle() != null) {
                    resolved = localModelValidator.getCachedModelsForWorkflow(wf.getTitle());
                }
            }

            if (resolved != null && !resolved.isEmpty()) {
                entry.requiredModels.clear();
                entry.requiredModels.addAll(resolved);
                entry.status = computeStatusInternal(entry);
            } else if (wf.getRequiredModelInfos() != null && !wf.getRequiredModelInfos().isEmpty()) {
                entry.requiredModels.clear();
                entry.requiredModels.addAll(wf.getRequiredModelInfos());
                entry.status = computeStatusInternal(entry);
            } else {
                entry.status = new BlueprintStatus(0, 0, new ArrayList<>());
                if (localModelValidator != null) {
                    localModelValidator.validateWorkflowModelsAsync(wf).thenAccept(v -> {
                        entry.requiredModels.clear();
                        if (wf.getRequiredModelInfos() != null) {
                            entry.requiredModels.addAll(wf.getRequiredModelInfos());
                        }
                        entry.status = computeStatusInternal(entry);
                        if (asyncModelValidatorCallback != null) {
                            asyncModelValidatorCallback.accept(() -> {});
                        }
                    });
                }
            }

            loadedEntries.add(entry);
        }

        return loadedEntries;
    }

    public static List<String> getAlternativeFolders(String folder) {
        List<String> list = new ArrayList<>();
        if (folder == null) return list;
        list.add(folder);

        String lower = folder.toLowerCase(Locale.ROOT);
        if (lower.equals("text_encoders") || lower.equals("clip")) {
            if (!lower.equals("text_encoders")) list.add("text_encoders");
            if (!lower.equals("clip")) list.add("clip");
        } else if (lower.equals("diffusion_models") || lower.equals("unet")) {
            if (!lower.equals("diffusion_models")) list.add("diffusion_models");
            if (!lower.equals("unet")) list.add("unet");
        } else if (lower.equals("loras") || lower.equals("lora")) {
            if (!lower.equals("loras")) list.add("loras");
            if (!lower.equals("lora")) list.add("lora");
        }
        return list;
    }

    public String resolveModelSize(ModelInfo info) {
        if (info == null) return "Unknown";
        if (info.getSize() != null && !info.getSize().isBlank() && !"Unknown".equalsIgnoreCase(info.getSize())) {
            return info.getSize();
        }

        String filename = info.getName();
        if (filename == null || filename.isBlank()) return "Unknown";

        String base = configService != null ? configService.getModelsPath() : null;
        String archive = configService != null ? configService.getArchivePath() : null;
        String type = info.getType() != null ? info.getType() : "checkpoints";
        String folder = info.getSave_path() != null ? info.getSave_path() : type;
        String normalizedFolder = archiveService != null ? archiveService.normalizeFolder(folder) : folder;

        if (base != null && !base.isBlank()) {
            Path p = "root".equals(normalizedFolder)
                    ? Paths.get(base, filename)
                    : Paths.get(base, normalizedFolder, filename);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                try {
                    return formatByteSize(Files.size(p));
                } catch (Exception ignored) {}
            }
        }

        if (archive != null && !archive.isBlank()) {
            Path p = "root".equals(normalizedFolder)
                    ? Paths.get(archive, filename)
                    : Paths.get(archive, normalizedFolder, filename);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                try {
                    return formatByteSize(Files.size(p));
                } catch (Exception ignored) {}
            }
        }

        if (localModelScanner != null) {
            try {
                Optional<Path> cached = localModelScanner.findModelFromCache(filename);
                if (cached.isPresent()) {
                    return formatByteSize(Files.size(cached.get()));
                }
            } catch (Exception ignored) {}
        }

        return "Unknown";
    }

    public String formatByteSize(long bytes) {
        if (bytes <= 0) return "Unknown";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.US, "%.2fGB", mb / 1024.0);
        } else {
            return String.format(Locale.US, "%.0fMB", mb);
        }
    }

    public String getModelStatus(ModelInfo info) {
        if (info == null || info.getName() == null || info.getName().isBlank()) return "Idle";
        String reqName = info.getName().trim();
        String lowerName = reqName.toLowerCase(Locale.ROOT);

        // 1. Fast in-memory check via LocalModelValidator
        if (localModelValidator != null) {
            if (localModelValidator.isModelActive(reqName)) {
                return "✅ Already exists";
            }
            if (localModelValidator.isModelArchived(reqName)) {
                return "📦 Archived";
            }
            Set<String> activeNames = localModelValidator.getActiveLocalModelNames();
            if (activeNames != null && (activeNames.contains(lowerName) || activeNames.contains(baseName(lowerName)))) {
                return "✅ Already exists";
            }
        }

        // 2. Closed source / Cloud API-based services that do not require local model files
        if (lowerName.equals("openai") || lowerName.equals("dall-e") || lowerName.equals("dalle") ||
            lowerName.equals("elevenlabs") || lowerName.equals("gemini") || lowerName.equals("anthropic") ||
            lowerName.equals("claude") || lowerName.equals("openrouter")) {
            return "✅ Already exists";
        }

        // 3. Fast in-memory lookup in LocalModelScanner
        if (localModelScanner != null) {
            Optional<Path> cached = localModelScanner.findModelFromCache(reqName);
            if (cached.isPresent()) {
                return "✅ Already exists";
            }
        }

        // 4. Direct single-path file checks (without recursive tree walk)
        if (configService == null) return "Idle";
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        if (base == null || base.isEmpty()) return "Idle";

        String type = info.getType() != null ? info.getType() : "checkpoints";
        String folder = info.getSave_path() != null ? info.getSave_path() : type;
        String normalizedFolder = archiveService != null ? archiveService.normalizeFolder(folder) : folder;

        Path local = "root".equals(normalizedFolder) ? Paths.get(base, reqName) : Paths.get(base, normalizedFolder, reqName);
        if (Files.exists(local) && Files.isRegularFile(local)) {
            return "✅ Already exists";
        }

        if (archive != null && !archive.trim().isEmpty()) {
            Path archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, reqName) : Paths.get(archive, normalizedFolder, reqName);
            if (Files.exists(archivedPath) && Files.isRegularFile(archivedPath)) {
                return "📦 Archived";
            }
        }

        if (info.getUrl() == null || info.getUrl().equals("MISSING")) {
            return "Idle";
        } else {
            return "✅ Known Good";
        }
    }

    public boolean isModelPresentDeep(ModelInfo req) {
        String status = getModelStatus(req);
        return status.equals("✅ Already exists");
    }

    public BlueprintStatus computeStatusInternal(BlueprintEntry entry) {
        int present = 0, missing = 0;
        List<String> missingNames = new ArrayList<>();

        for (ModelInfo req : entry.requiredModels) {
            if (req.getName() == null || req.getName().isBlank()) continue;
            if (isModelPresentDeep(req)) {
                present++;
            } else {
                missing++;
                missingNames.add(req.getName());
            }
        }
        return new BlueprintStatus(present, missing, missingNames);
    }

    public void playVideoPreview(BlueprintEntry entry, Window parent, JButton triggerBtn) {
        String url = entry.previewPath;
        if (url == null || url.isEmpty()) return;

        if (triggerBtn != null) {
            triggerBtn.setEnabled(false);
            triggerBtn.setText("⏳ Loading Preview...");
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                String ext = ".mp4";
                String lower = url.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".webm")) ext = ".webm";
                else if (lower.endsWith(".mov")) ext = ".mov";

                File cacheDir = getPreviewDiskCacheDir();
                File cachedFile = new File(cacheDir, hashUrl(url) + ext);

                if (cachedFile.exists() && cachedFile.length() > 0) {
                    return cachedFile;
                }

                String nonce = UUID.randomUUID().toString().replaceAll("[0-9-]", "x");
                File tmp = new File(System.getProperty("java.io.tmpdir"), "blueprint_video_" + nonce + ext);
                tmp.deleteOnExit();

                try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                    Files.copy(is, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                try {
                    if (!cachedFile.exists()) {
                        Files.copy(tmp.toPath(), cachedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return cachedFile;
                    }
                } catch (Exception ignored) {}

                return tmp;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(videoFile -> SwingUtilities.invokeLater(() -> {
            if (triggerBtn != null) {
                triggerBtn.setEnabled(true);
                triggerBtn.setText("▶  Play Preview");
            }
            VideoPreviewDialog previewDialog = new VideoPreviewDialog(
                    parent,
                    entry.name,
                    videoFile,
                    configService != null && configService.isDarkMode()
            );
            previewDialog.setLocationRelativeTo(parent);
            previewDialog.setVisible(true);
            previewDialog.toFront();
            previewDialog.requestFocus();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                if (triggerBtn != null) {
                    triggerBtn.setEnabled(true);
                    triggerBtn.setText("▶  Play Preview");
                }
                JOptionPane.showMessageDialog(
                        parent,
                        "Could not open video preview: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()),
                        "Video Preview Error",
                        JOptionPane.ERROR_MESSAGE
                );
            });
            return null;
        });
    }

    private File getPreviewDiskCacheDir() {
        File dir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.home") + File.separator + ".comfyuicompanion", "cache" + File.separator + "previews");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String hashUrl(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    public static String baseName(String nameOrPath) {
        if (nameOrPath == null) return "";
        String s = nameOrPath.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        return s.toLowerCase(Locale.ROOT).trim();
    }

    public static String inferTypeFromFilename(String filename) {
        if (filename == null) return "checkpoints";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("vae") || lower.contains("ae.safetensors") || lower.startsWith("ae")) return "vae";
        if (lower.contains("clip") || lower.contains("t5") || lower.contains("qwen") || lower.contains("encoder")
                || lower.contains("gemma") || lower.contains("llama") || lower.contains("text_encoder") || lower.contains("textencoder")) return "text_encoders";
        if (lower.contains("unet") || lower.contains("diffusion") || lower.contains("turbo") || lower.contains("transformer") || lower.contains("dit")) return "diffusion_models";
        if (lower.contains("lora")) return "loras";
        if (lower.contains("controlnet") || lower.contains("control")) return "controlnet";
        return "checkpoints";
    }

    public static boolean isSupportedModelFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".safetensors") || lower.endsWith(".sft") || lower.endsWith(".ckpt") ||
                lower.endsWith(".pt") || lower.endsWith(".pth") || lower.endsWith(".bin") || lower.endsWith(".onnx");
    }

    public static boolean hasActualModelFiles(List<ModelInfo> models) {
        if (models == null || models.isEmpty()) return false;
        for (ModelInfo m : models) {
            if (isSupportedModelFile(m.getName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static List<ModelInfo> extractModelInfos(Object obj, ObjectMapper mapper) {
        List<ModelInfo> result = new ArrayList<>();
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                if (item instanceof ModelInfo) {
                    result.add((ModelInfo) item);
                } else if (item instanceof Map) {
                    try {
                        ModelInfo info = mapper.convertValue(item, ModelInfo.class);
                        result.add(info);
                    } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }
}
