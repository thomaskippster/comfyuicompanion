package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.ILocalModelArchiveDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Enterprise service component responsible for locating, reading, and extracting actual
 * ComfyUI workflows bundled within downloaded Civitai assets (.json, .png, and .zip archives).
 */
@Component
public class CivitaiWorkflowFileResolver {

    private static final Logger log = LoggerFactory.getLogger(CivitaiWorkflowFileResolver.class);

    private final ObjectMapper objectMapper;
    private final IConfigService configService;
    private final PathResolver pathResolver;
    private final CivitaiLocalModelScanner localModelScanner;
    private final ILocalModelArchiveDetector archiveDetector;
    private final CivitaiWorkflowAdapter workflowAdapter;
    private final Map<Path, Optional<ObjectNode>> parsedWorkflowCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Primary constructor injecting all operational dependencies.
     *
     * @param objectMapper      JSON object mapper
     * @param configService     configuration service for paths
     * @param pathResolver      optional path resolver for extra paths
     * @param localModelScanner optional scanner for resolving subfolder model paths
     * @param archiveDetector   optional archive detector for discovering storage roots
     * @param workflowAdapter   optional adapter for substituting models in workflows
     */
    @Autowired
    public CivitaiWorkflowFileResolver(
            ObjectMapper objectMapper,
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) PathResolver pathResolver,
            @Autowired(required = false) CivitaiLocalModelScanner localModelScanner,
            @Autowired(required = false) ILocalModelArchiveDetector archiveDetector,
            @Autowired(required = false) CivitaiWorkflowAdapter workflowAdapter) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.localModelScanner = localModelScanner != null ? localModelScanner : (configService != null ? new CivitaiLocalModelScanner(configService) : null);
        this.archiveDetector = archiveDetector;
        this.workflowAdapter = workflowAdapter != null ? workflowAdapter : new CivitaiWorkflowAdapter(this.objectMapper, configService, this.localModelScanner);
    }

    /**
     * Backward-compatible constructor for earlier call sites and tests.
     */
    public CivitaiWorkflowFileResolver(
            ObjectMapper objectMapper,
            IConfigService configService,
            PathResolver pathResolver,
            CivitaiLocalModelScanner localModelScanner) {
        this(objectMapper, configService, pathResolver, localModelScanner, null, null);
    }

    /**
     * Convenience constructor supporting earlier 3-argument calls.
     */
    public CivitaiWorkflowFileResolver(
            ObjectMapper objectMapper,
            IConfigService configService,
            PathResolver pathResolver) {
        this(objectMapper, configService, pathResolver, null, null, null);
    }

    /**
     * Attempts to resolve and parse a real ComfyUI LiteGraph workflow from local storage
     * for the given Civitai model entry.
     *
     * @param entry the model entry
     * @return optional containing the parsed LiteGraph root node if found
     */
    public Optional<ObjectNode> tryResolveRealWorkflow(CivitaiModelEntry entry) {
        if (entry == null) {
            return Optional.empty();
        }
        for (Path file : findCandidateWorkflowFiles(entry)) {
            Optional<ObjectNode> wf = loadWorkflowFromFile(file);
            if (wf.isPresent()) {
                ensureWorkflowInModelsDir(file);
                return wf;
            }
        }
        return Optional.empty();
    }

    private void ensureWorkflowInModelsDir(Path sourceFile) {
        try {
            String baseModels = configService != null ? configService.getModelsPath() : null;
            if (baseModels != null && !baseModels.isBlank()) {
                Path targetDir = Paths.get(baseModels, "workflows");
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(sourceFile.getFileName());
                if (!Files.exists(target)) {
                    Files.copy(sourceFile, target);
                    log.info("Mirrored workflow asset from {} into workflows repository {}", sourceFile, target);
                }
            }
        } catch (Exception ex) {
            log.debug("Workflow directory mirroring skipped: {}", ex.getMessage());
        }
    }

    /**
     * Extracts and parses a ComfyUI LiteGraph workflow from a local file (.json, .png, or .zip archive).
     *
     * @param file the path to the candidate workflow file
     * @return optional containing the parsed LiteGraph JSON root node
     */
    public Optional<ObjectNode> loadWorkflowFromFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Path normalized = file.toAbsolutePath().normalize();
        Optional<ObjectNode> cached = parsedWorkflowCache.get(normalized);
        if (cached != null) {
            return cached.map(ObjectNode::deepCopy);
        }
        String nameLower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Optional<ObjectNode> loaded = Optional.empty();
        if (nameLower.endsWith(".json")) loaded = loadJsonWorkflow(file);
        else if (nameLower.endsWith(".png")) loaded = loadPngWorkflow(file);
        else if (nameLower.endsWith(".zip")) loaded = loadZipWorkflow(file);
        parsedWorkflowCache.put(normalized, loaded);
        return loaded.map(ObjectNode::deepCopy);
    }

    /**
     * Injects custom user prompt text into the first positive prompt node in a LiteGraph workflow.
     *
     * @param workflow   the LiteGraph workflow root node
     * @param userPrompt the custom prompt text to inject
     */
    public void injectUserPrompt(ObjectNode workflow, String userPrompt) {
        if (workflow == null || userPrompt == null || userPrompt.isBlank()) {
            return;
        }
        JsonNode nodesNode = workflow.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            return;
        }

        for (JsonNode node : nodesNode) {
            String type = node.path("type").asText("");
            String title = node.path("title").asText("");
            if (type.equalsIgnoreCase("CLIPTextEncode") || type.toLowerCase(Locale.ROOT).contains("prompt")
                    || title.toLowerCase(Locale.ROOT).contains("positive") || title.toLowerCase(Locale.ROOT).contains("prompt")) {
                JsonNode widgets = node.get("widgets_values");
                if (widgets != null && widgets.isArray() && !widgets.isEmpty()) {
                    ((ArrayNode) widgets).set(0, objectMapper.getNodeFactory().textNode(userPrompt.trim()));
                    log.debug("Injected user prompt into workflow node {} ({})", node.path("id").asText(), type);
                    return;
                }
            }
        }
    }

    /**
     * Adapts model and input asset references in a LiteGraph workflow to match installed models and local inputs.
     *
     * @param workflow       the LiteGraph workflow root node
     * @param requiredModels list of operational models resolved for this entry
     */
    public void adaptModelReferences(ObjectNode workflow, List<ModelInfo> requiredModels) {
        if (workflowAdapter != null) {
            workflowAdapter.adaptModelReferences(workflow, requiredModels);
        }
    }

    private List<Path> findCandidateWorkflowFiles(CivitaiModelEntry entry) {
        List<Path> candidates = new ArrayList<>();
        String filename = entry.filename();
        Set<Path> candidateDirs = new LinkedHashSet<>();

        candidateDirs.add(Paths.get("user_workflows", "vault"));
        String baseModels = configService != null ? configService.getModelsPath() : null;
        if (baseModels != null && !baseModels.isBlank()) {
            candidateDirs.add(Paths.get(baseModels, "workflows"));
        }
        String comfyPath = configService != null ? configService.getComfyUIPath() : null;
        if (comfyPath != null && !comfyPath.isBlank()) {
            candidateDirs.add(Paths.get(comfyPath, "user", "default", "workflows"));
        }

        List<String> variants = new ArrayList<>();
        if (filename != null && !filename.isBlank()) {
            String cleanFn = extractSimpleFilename(filename);
            variants.add(cleanFn);
            if (!cleanFn.equalsIgnoreCase(filename)) variants.add(filename);
            if (cleanFn.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                variants.add(cleanFn.substring(0, cleanFn.length() - 4) + ".json");
            } else if (cleanFn.toLowerCase(Locale.ROOT).endsWith(".json")) {
                variants.add(cleanFn.substring(0, cleanFn.length() - 5) + ".zip");
            }
        }
        if (entry.name() != null && !entry.name().isBlank()) {
            String baseTitle = entry.name().replaceAll("[^a-zA-Z0-9_.-]", "_");
            variants.add(baseTitle + ".json");
            variants.add(baseTitle + ".zip");
        }

        if (!variants.isEmpty()) {
            for (Path dir : candidateDirs) {
                if (Files.isDirectory(dir)) {
                    for (String v : variants) {
                        Path direct = dir.resolve(v);
                        if (Files.isRegularFile(direct)) {
                            candidates.add(direct);
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private Optional<Path> tryDownloadWorkflowBundle(CivitaiModelEntry entry, Path wfDir, String filename) {
        try {
            Files.createDirectories(wfDir);
            String safeName = Paths.get(filename).getFileName().toString();
            Path target = wfDir.resolve(safeName);
            String dlUrl = entry.downloadUrl();
            String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
            if (apiKey != null && !apiKey.isBlank() && dlUrl.contains("civitai.com") && !dlUrl.contains("token=")) {
                dlUrl = dlUrl + (dlUrl.contains("?") ? "&token=" : "?token=") + apiKey.trim();
            }
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest.Builder req = HttpRequest.newBuilder().uri(URI.create(dlUrl)).header("User-Agent", "ComfyUI-Companion/2.0").timeout(Duration.ofSeconds(20)).GET();
            HttpResponse<InputStream> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && !ct.contains("text/html") && !ct.contains("problem+json")) {
                try (InputStream in = resp.body()) { Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING); }
                if (Files.size(target) > 100) return Optional.of(target);
                Files.deleteIfExists(target);
            }
        } catch (Exception ex) { log.debug("On-demand Civitai workflow fetch skipped: {}", ex.getMessage()); }
        return Optional.empty();
    }

    private Optional<ObjectNode> loadJsonWorkflow(Path jsonPath) {
        try {
            JsonNode tree = objectMapper.readTree(jsonPath.toFile());
            if (isValidLiteGraph(tree)) {
                log.info("Loaded real LiteGraph workflow from JSON file: {}", jsonPath);
                return Optional.of((ObjectNode) tree);
            }
            if (tree.has("workflow") && isValidLiteGraph(tree.get("workflow"))) {
                log.info("Loaded real LiteGraph workflow from JSON wrapper: {}", jsonPath);
                return Optional.of((ObjectNode) tree.get("workflow"));
            }
        } catch (Exception e) {
            log.debug("Failed reading JSON workflow from {}: {}", jsonPath, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<ObjectNode> loadPngWorkflow(Path pngPath) {
        try {
            var extracted = ComfyPngMetadataReader.readMetadata(pngPath.toFile());
            if (extracted.isPresent() && extracted.get().workflowJson() != null) {
                JsonNode tree = objectMapper.readTree(extracted.get().workflowJson());
                if (isValidLiteGraph(tree)) {
                    log.info("Extracted real LiteGraph workflow from PNG metadata: {}", pngPath);
                    return Optional.of((ObjectNode) tree);
                }
            }
        } catch (Exception e) {
            log.debug("Failed reading PNG workflow metadata from {}: {}", pngPath, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<ObjectNode> loadZipWorkflow(Path zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            List<ZipEntry> jsonEntries = new ArrayList<>();
            List<ZipEntry> pngEntries = new ArrayList<>();

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("test") || name.contains("example") || name.contains("sample") || name.contains("installer")) {
                    continue;
                }
                if (name.endsWith(".json")) {
                    jsonEntries.add(entry);
                } else if (name.endsWith(".png")) {
                    pngEntries.add(entry);
                }
            }

            jsonEntries.sort((a, b) -> {
                int diff = Integer.compare(scoreEntry(b.getName()), scoreEntry(a.getName()));
                return diff != 0 ? diff : b.getName().compareToIgnoreCase(a.getName());
            });

            for (ZipEntry entry : jsonEntries) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    JsonNode tree = objectMapper.readTree(in);
                    if (isValidLiteGraph(tree)) return Optional.of((ObjectNode) tree);
                } catch (Exception ignored) {}
            }
            for (ZipEntry entry : pngEntries) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    var extracted = ComfyPngMetadataReader.readMetadataFromStream(in);
                    if (extracted.isPresent() && extracted.get().workflowJson() != null) {
                        JsonNode tree = objectMapper.readTree(extracted.get().workflowJson());
                        if (isValidLiteGraph(tree)) return Optional.of((ObjectNode) tree);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("Failed scanning ZIP archive {} for workflows: {}", zipPath, e.getMessage());
        }
        return Optional.empty();
    }

    private int scoreEntry(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        int score = (lower.contains("workflow/") || lower.contains("workflows/")) ? 200 : (lower.contains("workflow") ? 100 : 0);
        return lower.contains("reborn") ? score + 50 : score;
    }

    private boolean isValidLiteGraph(JsonNode tree) {
        return tree != null && tree.isObject() && tree.has("nodes") && tree.get("nodes").isArray();
    }

    private String extractSimpleFilename(String pathOrVal) {
        if (pathOrVal == null || pathOrVal.isBlank()) return "";
        String s = pathOrVal.trim();
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return slash >= 0 ? s.substring(slash + 1) : s;
    }
}
