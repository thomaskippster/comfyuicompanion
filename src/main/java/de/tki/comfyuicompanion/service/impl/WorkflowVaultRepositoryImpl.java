package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.event.WorkflowVaultUpdatedEvent;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.IWorkflowService;
import de.tki.comfyuicompanion.service.IWorkflowVaultRepository;
import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enterprise Repository managing local user workflows in My Workflow Vault (isolated in user_workflows/vault/).
 * Handles atomicity, safe filename sanitization, sidecar metadata, and resilient preview image resolution.
 */
@Repository
public class WorkflowVaultRepositoryImpl implements IWorkflowVaultRepository {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowVaultRepositoryImpl.class);

    private final ConfigService configService;
    private final IWorkflowService workflowService;
    private final ComfyModelAnalyzer modelAnalyzer;
    private final de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor modelExtractor;
    private final IModelArchitectureService architectureService;
    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SafePathValidator pathValidator = new SafePathValidator();

    @Autowired
    public WorkflowVaultRepositoryImpl(
            ConfigService configService,
            IWorkflowService workflowService,
            @Autowired(required = false) ComfyModelAnalyzer modelAnalyzer,
            @Autowired(required = false) de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor modelExtractor,
            @Autowired(required = false) IModelArchitectureService architectureService,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.configService = configService;
        this.workflowService = workflowService;
        this.modelAnalyzer = modelAnalyzer;
        this.modelExtractor = modelExtractor;
        this.architectureService = architectureService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public File getVaultDirectory() {
        File baseDir = (configService != null) ? configService.getUserWorkflowsDir() : new File("user_workflows");
        File vaultDir = new File(baseDir, "vault");
        if (!vaultDir.exists()) {
            vaultDir.mkdirs();
        }
        return vaultDir;
    }

    @Override
    public List<BlueprintEntry> loadAllVaultEntries() {
        File vaultDir = getVaultDirectory();
        if (!vaultDir.exists() || !vaultDir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] jsonFiles = vaultDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json")
                && !name.toLowerCase(Locale.ROOT).endsWith(".meta.json"));

        if (jsonFiles == null || jsonFiles.length == 0) {
            return Collections.emptyList();
        }

        List<BlueprintEntry> entries = new ArrayList<>();
        for (File jsonFile : jsonFiles) {
            try {
                String baseName = stripExtension(jsonFile.getName());
                BlueprintEntry entry = buildBlueprintEntryFromFile(vaultDir, jsonFile, baseName);
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (Exception ex) {
                logger.warn("[WorkflowVault] Could not parse vault workflow: {} - {}", jsonFile.getName(), ex.getMessage());
            }
        }

        entries.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private BlueprintEntry buildBlueprintEntryFromFile(File vaultDir, File jsonFile, String baseName) throws IOException {
        File metaFile = new File(vaultDir, baseName + ".meta.json");
        VaultWorkflowMetadata meta = null;
        if (metaFile.exists()) {
            try {
                meta = mapper.readValue(metaFile, VaultWorkflowMetadata.class);
            } catch (Exception ignored) {
                logger.debug("Failed reading meta for {}, scanning dynamically.", baseName);
            }
        }

        File previewFile = null;
        if (meta != null && meta.previewFile() != null && !meta.previewFile().isBlank()) {
            File direct = new File(vaultDir, meta.previewFile());
            if (direct.exists() && direct.length() > 0) {
                previewFile = direct;
            } else if (vaultDir.getParentFile() != null) {
                File parentDirect = new File(vaultDir.getParentFile(), meta.previewFile());
                if (parentDirect.exists() && parentDirect.length() > 0) {
                    previewFile = parentDirect;
                }
            }
        }
        if (previewFile == null) {
            previewFile = findPreviewImage(vaultDir, baseName);
        }
        String previewPath = previewFile != null ? previewFile.getAbsolutePath() : "";

        String title = meta != null && meta.title() != null ? meta.title() : formatTitle(baseName);
        String category = meta != null && meta.category() != null ? meta.category() : "Text to Image";
        String description = meta != null && meta.description() != null ? meta.description() : "";

        de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow regWf = new de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow();
        regWf.setId(baseName);
        regWf.setTitle(title);
        regWf.setCategory(category);
        regWf.setDescription(description);
        regWf.setJsonDownloadUrl(jsonFile.getAbsolutePath());
        regWf.setThumbnailUrl(previewPath);

        Map<String, ModelInfo> mergedModels = new LinkedHashMap<>();
        if (jsonFile != null && jsonFile.exists() && jsonFile.length() > 0) {
            try {
                List<ModelInfo> extracted = null;
                if (modelExtractor != null) {
                    extracted = modelExtractor.extractModels(mapper.readTree(jsonFile));
                } else if (modelAnalyzer != null) {
                    extracted = modelAnalyzer.analyze(Files.readString(jsonFile.toPath()), baseName + ".json");
                }
                if (extracted != null) {
                    for (ModelInfo m : sanitizeVaultModels(extracted, null)) {
                        String key = (m.getFilename() != null && !m.getFilename().isBlank()) ? m.getFilename() : m.getName();
                        if (key != null) mergedModels.put(key.toLowerCase(Locale.ROOT), m);
                    }
                }
            } catch (Exception ex) {
                logger.debug("Workflow JSON model extraction skipped for {}: {}", baseName, ex.getMessage());
            }
        }

        if (meta != null && meta.requiredModels() != null && !meta.requiredModels().isEmpty()) {
            for (ModelInfo metaModel : sanitizeVaultModels(meta.requiredModels(), meta.sourcePlatform())) {
                String key = (metaModel.getFilename() != null && !metaModel.getFilename().isBlank()) ? metaModel.getFilename() : metaModel.getName();
                if (key == null) continue;
                String lowerKey = key.toLowerCase(Locale.ROOT);
                ModelInfo existing = mergedModels.get(lowerKey);
                if (existing == null) {
                    for (ModelInfo c : mergedModels.values()) {
                        String cName = (c.getFilename() != null && !c.getFilename().isBlank()) ? c.getFilename() : c.getName();
                        if (cName != null && (cName.equalsIgnoreCase(key) || (c.getName() != null && c.getName().equalsIgnoreCase(metaModel.getName()))
                                || (LocalModelScanner.normalizeQuantizationStem(cName).equals(LocalModelScanner.normalizeQuantizationStem(key))
                                    && Objects.equals(c.getType(), metaModel.getType())))) {
                            existing = c;
                            break;
                        }
                    }
                }
                if (existing != null) {
                    if ((existing.getUrl() == null || existing.getUrl().isBlank() || "MISSING".equalsIgnoreCase(existing.getUrl()))
                            && metaModel.getUrl() != null && !metaModel.getUrl().isBlank() && !"MISSING".equalsIgnoreCase(metaModel.getUrl())) {
                        existing.setUrl(metaModel.getUrl());
                    }
                    if (existing.getPreviewPath() == null && metaModel.getPreviewPath() != null) {
                        existing.setPreviewPath(metaModel.getPreviewPath());
                    }
                    if (metaModel.getByteSize() > 0 && existing.getByteSize() <= 0) {
                        existing.setByteSize(metaModel.getByteSize());
                        existing.setSize(metaModel.getSize());
                    }
                } else if (!lowerKey.endsWith(".zip") && !lowerKey.endsWith(".json")) {
                    mergedModels.put(lowerKey, metaModel);
                }
            }
        }

        regWf.setRequiredModelInfos(new ArrayList<>(mergedModels.values()));

        BlueprintEntry entry = new BlueprintEntry(regWf);
        entry.hasBeenValidated = true;
        entry.previewPath = previewPath;
        return entry;
    }

    private List<ModelInfo> sanitizeVaultModels(List<ModelInfo> rawModels, String sourcePlatform) {
        if (rawModels == null || rawModels.isEmpty()) return Collections.emptyList();
        List<ModelInfo> cleaned = new ArrayList<>();
        Map<String, ModelInfo> dedupByStem = new LinkedHashMap<>();
        boolean isHf = sourcePlatform != null && sourcePlatform.toUpperCase(Locale.ROOT).contains("HUGGING");

        for (ModelInfo m : rawModels) {
            if (m == null) continue;
            String name = (m.getFilename() != null && !m.getFilename().isBlank()) ? m.getFilename() : m.getName();
            String type = m.getType() != null ? m.getType() : "checkpoints";
            if (name == null || name.isBlank() || name.toLowerCase(Locale.ROOT).endsWith(".json") || name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                continue;
            }
            if (m.getSize() == null || m.getSize().isBlank() || "Unknown".equalsIgnoreCase(m.getSize()) || "Workflow JSON".equalsIgnoreCase(m.getSize())) {
                String est = de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor.estimateKnownModelSize(name);
                if (est != null) m.setSize(est);
            }
            if (m.getUrl() == null || m.getUrl().isBlank() || "MISSING".equalsIgnoreCase(m.getUrl())) {
                String fallbackUrl = de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor.resolveFallbackDownloadUrl(name, type);
                if (fallbackUrl != null) m.setUrl(fallbackUrl);
            }
            String stemKey = isHf ? type.toLowerCase(Locale.ROOT) : LocalModelScanner.normalizeQuantizationStem(name);
            ModelInfo existing = dedupByStem.get(stemKey);
            if (existing == null || prefersCandidate(name, (existing.getFilename() != null ? existing.getFilename() : existing.getName()))) {
                dedupByStem.put(stemKey, m);
            }
        }
        cleaned.addAll(dedupByStem.values());
        return cleaned;
    }

    private boolean prefersCandidate(String candidateName, String existingName) {
        String c = candidateName.toLowerCase(Locale.ROOT);
        String e = existingName != null ? existingName.toLowerCase(Locale.ROOT) : "";
        boolean cSafe = c.endsWith(".safetensors") || c.endsWith(".sft");
        boolean eSafe = e.endsWith(".safetensors") || e.endsWith(".sft");
        if (cSafe && !eSafe) return true;
        if (!cSafe && eSafe) return false;
        return (c.contains("fp16") || c.contains("fp8")) && e.contains("bf16");
    }

    @Override
    public BlueprintEntry saveWorkflowToVault(String baseName, String jsonContent, byte[] imageBytes, String imageExt, VaultWorkflowMetadata metadata) {
        String safeBaseName = sanitizeName(baseName);
        File vaultDir = getVaultDirectory();
        if (!vaultDir.exists()) {
            vaultDir.mkdirs();
        }

        try {
            File jsonFile = new File(vaultDir, safeBaseName + ".json");
            Files.writeString(jsonFile.toPath(), jsonContent);

            String previewPath = "";
            if (imageBytes != null && imageBytes.length > 0 && imageExt != null) {
                String ext = imageExt.startsWith(".") ? imageExt.substring(1) : imageExt;
                File imgFile = new File(vaultDir, safeBaseName + "." + ext);
                Files.write(imgFile.toPath(), imageBytes);
                previewPath = imgFile.getAbsolutePath();
            }

            if (metadata != null) {
                File metaFile = new File(vaultDir, safeBaseName + ".meta.json");
                mapper.writerWithDefaultPrettyPrinter().writeValue(metaFile, metadata);
            }

            logger.info("[WorkflowVault] Saved workflow '{}' to Vault (user_workflows/vault/)", safeBaseName);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new WorkflowVaultUpdatedEvent(this, safeBaseName, WorkflowVaultUpdatedEvent.ChangeType.ADDED));
            }

            return buildBlueprintEntryFromFile(vaultDir, jsonFile, safeBaseName);
        } catch (Exception ex) {
            logger.error("[WorkflowVault] Failed to save workflow to vault: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to save workflow to vault: " + ex.getMessage(), ex);
        }
    }

    @Override
    public BlueprintEntry importFileToVault(File sourceFile) throws Exception {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist");
        }

        String rawName = sourceFile.getName();
        String baseName = sanitizeName(stripExtension(rawName));
        String lower = rawName.toLowerCase(Locale.ROOT);

        File vaultDir = getVaultDirectory();
        byte[] imageBytes = null;
        String imageExt = null;
        String jsonContent;

        if (lower.endsWith(".png") || lower.endsWith(".webp")) {
            jsonContent = workflowService.extractWorkflow(sourceFile);
            imageBytes = Files.readAllBytes(sourceFile.toPath());
            imageExt = lower.endsWith(".webp") ? "webp" : "png";
        } else if (lower.endsWith(".json")) {
            jsonContent = Files.readString(sourceFile.toPath());
            File parent = sourceFile.getParentFile();
            if (parent != null) {
                for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp", ".gif")) {
                    File sib = new File(parent, stripExtension(rawName) + ext);
                    if (sib.exists() && sib.isFile() && sib.length() > 0) {
                        try {
                            imageBytes = Files.readAllBytes(sib.toPath());
                            imageExt = ext.startsWith(".") ? ext.substring(1) : ext;
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type for vault import: " + rawName);
        }

        List<ModelInfo> models = new ArrayList<>();
        if (modelAnalyzer != null) {
            try {
                List<ModelInfo> analyzed = modelAnalyzer.analyze(jsonContent, baseName + ".json");
                if (analyzed != null) models.addAll(analyzed);
            } catch (Exception ignored) {
                logger.debug("Model analysis skipped: {}", ignored.getMessage());
            }
        }

        String architecture = "General";
        if (architectureService != null) {
            try {
                de.tki.comfyuicompanion.domain.ModelArchitecture detected = architectureService.detectArchitecture(baseName);
                if (detected != null && detected != de.tki.comfyuicompanion.domain.ModelArchitecture.ARCH_UNKNOWN) {
                    architecture = detected.getDisplayName();
                }
            } catch (Exception ignored) {}
        }

        VaultWorkflowMetadata meta = new VaultWorkflowMetadata(
                baseName,
                formatTitle(baseName),
                "Imported from " + rawName,
                "Text to Image",
                architecture,
                lower.endsWith(".json") ? "LOCAL_JSON" : "LOCAL_PNG",
                sourceFile.getAbsolutePath(),
                safeBaseNameWithExt(baseName, imageExt),
                models,
                System.currentTimeMillis()
        );

        return saveWorkflowToVault(baseName, jsonContent, imageBytes, imageExt, meta);
    }

    @Override
    public boolean isWorkflowInVault(String titleOrBaseName) {
        if (titleOrBaseName == null || titleOrBaseName.isBlank()) return false;
        String clean = sanitizeName(stripExtension(titleOrBaseName)).toLowerCase(Locale.ROOT);
        File vaultDir = getVaultDirectory();
        File jsonFile = new File(vaultDir, clean + ".json");
        if (jsonFile.exists()) return true;

        File[] allJson = vaultDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json")
                && !name.toLowerCase(Locale.ROOT).endsWith(".meta.json"));
        if (allJson != null) {
            for (File f : allJson) {
                String fBase = stripExtension(f.getName());
                if (sanitizeName(fBase).equalsIgnoreCase(clean)) return true;
                File metaFile = new File(vaultDir, fBase + ".meta.json");
                if (metaFile.exists()) {
                    try {
                        VaultWorkflowMetadata meta = mapper.readValue(metaFile, VaultWorkflowMetadata.class);
                        if (meta != null && (titleOrBaseName.equalsIgnoreCase(meta.title())
                                || titleOrBaseName.equalsIgnoreCase(meta.id())
                                || clean.equalsIgnoreCase(sanitizeName(meta.title() != null ? meta.title() : "")))) {
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }

    @Override
    public boolean removeWorkflowFromVault(String baseName) {
        if (baseName == null || baseName.isBlank()) return false;
        File vaultDir = getVaultDirectory();
        String clean = sanitizeName(stripExtension(baseName));
        String removedBase = null;

        File directCandidate = new File(baseName);
        if (directCandidate.isAbsolute() && directCandidate.exists() && vaultDir.equals(directCandidate.getParentFile())) {
            removedBase = stripExtension(directCandidate.getName());
        } else if (new File(vaultDir, clean + ".json").exists() || new File(vaultDir, clean + ".meta.json").exists()) {
            removedBase = clean;
        } else {
            File[] jsonFiles = vaultDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json")
                    && !name.toLowerCase(Locale.ROOT).endsWith(".meta.json"));
            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    String fBase = stripExtension(jsonFile.getName());
                    if (fBase.equalsIgnoreCase(baseName) || sanitizeName(fBase).equalsIgnoreCase(clean) || formatTitle(fBase).equalsIgnoreCase(baseName)) {
                        removedBase = fBase;
                        break;
                    }
                }
            }
        }

        boolean removed = removedBase != null && deleteVaultWorkflowArtifacts(vaultDir, removedBase);
        if (removed && eventPublisher != null) {
            eventPublisher.publishEvent(new WorkflowVaultUpdatedEvent(this, removedBase, WorkflowVaultUpdatedEvent.ChangeType.REMOVED));
        }
        return removed;
    }

    private boolean deleteVaultWorkflowArtifacts(File vaultDir, String baseName) {
        boolean deletedAny = false;
        File json = new File(vaultDir, baseName + ".json");
        if (json.exists() && json.delete()) deletedAny = true;
        File meta = new File(vaultDir, baseName + ".meta.json");
        if (meta.exists() && meta.delete()) deletedAny = true;
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov"}) {
            File p = new File(vaultDir, baseName + ext);
            if (p.exists()) p.delete();
        }
        File preview = findPreviewImage(vaultDir, baseName);
        if (preview != null && preview.exists() && vaultDir.equals(preview.getParentFile())) {
            preview.delete();
        }
        return deletedAny;
    }

    @Override
    public Optional<File> resolvePreviewImage(String baseName) {
        File vaultDir = getVaultDirectory();
        return Optional.ofNullable(findPreviewImage(vaultDir, sanitizeName(stripExtension(baseName))));
    }

    private File findPreviewImage(File dir, String baseName) {
        if (dir == null || !dir.exists() || baseName == null || baseName.isBlank()) return null;
        String[] exts = {".png", ".jpg", ".jpeg", ".webp", ".gif"};
        String sanitized = sanitizeName(baseName);
        String withSpaces = baseName.replace('_', ' ');
        String withUnderscores = baseName.replace(' ', '_');
        List<String> variants = List.of(baseName, sanitized, withSpaces, withUnderscores);

        for (String var : variants) {
            for (String ext : exts) {
                File f = new File(dir, var + ext);
                if (f.exists() && f.length() > 0) return f;
            }
        }
        if (dir.getParentFile() != null && dir.getParentFile().exists()) {
            for (String var : variants) {
                for (String ext : exts) {
                    File f = new File(dir.getParentFile(), var + ext);
                    if (f.exists() && f.length() > 0) return f;
                }
            }
        }
        return null;
    }

    private String stripExtension(String filename) {
        if (filename == null) return "";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".meta.json")) {
            return filename.substring(0, filename.length() - ".meta.json".length());
        }
        String[] knownExts = {".json", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov", ".zip"};
        for (String ext : knownExts) {
            if (lower.endsWith(ext) && filename.length() > ext.length()) {
                return filename.substring(0, filename.length() - ext.length());
            }
        }
        return filename;
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_").replaceAll("_+", "_");
    }

    private String safeBaseNameWithExt(String baseName, String ext) {
        return ext != null && !ext.isBlank() ? baseName + "." + ext : "";
    }

    private String formatTitle(String baseName) {
        String clean = baseName.replace('_', ' ').replace('-', ' ').trim();
        if (clean.isEmpty()) return "Custom Workflow";
        return Arrays.stream(clean.split("\\s+")).filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
