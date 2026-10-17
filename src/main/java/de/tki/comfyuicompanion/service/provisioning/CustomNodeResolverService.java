package de.tki.comfyuicompanion.service.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.ICustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.CustomNodeInstaller;
import de.tki.comfyuicompanion.service.impl.CustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.util.ConfigConstants;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Enterprise Service for analyzing workflow JSONs for missing ComfyUI Custom Nodes,
 * querying ComfyUI's object_info endpoint, and resolving known Git repositories
 * for 1-click installation via ComfyUI-Manager's extension mappings.
 */
@Service
public class CustomNodeResolverService {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeResolverService.class);

    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final ConfigService configService;
    private final SafePathValidator safePathValidator;
    private final ICustomNodeManagerBridgeService managerBridgeService;

    // Registry of well-known community custom node packages (fallback)
    private static final Map<String, CustomNodePackage> KNOWN_CUSTOM_NODES = new HashMap<>();

    static {
        register("ImpactPack", "https://github.com/ltdrdata/ComfyUI-Impact-Pack",
                "FaceDetailer", "BboxDetectorSEGS", "SAMDetectorCombined", "DetailerForEach", "ToDetailerPipe");
        register("UltimateSDUpscale", "https://github.com/ssitu/ComfyUI_UltimateSDUpscale",
                "UltimateSDUpscale", "UltimateSDUpscaleNoUpscale");
        register("ReactorFaceSwap", "https://github.com/Gourieff/comfyui-reactor-node",
                "ReActorFaceSwap", "ReActorFastFaceSwap", "ReActorRestoreFace");
        register("AdvancedControlNet", "https://github.com/Kosinkadink/ComfyUI-Advanced-ControlNet",
                "ControlNetApplyAdvanced", "TimestepKeyframe", "ControlNetLoaderAdvanced");
        register("IPAdapterPlus", "https://github.com/cubiq/ComfyUI_IPAdapter_plus",
                "IPAdapterUnifiedLoader", "IPAdapterApply", "IPAdapterFaceID", "IPAdapterAdvanced");
        register("AnimateDiffEvolved", "https://github.com/Kosinkadink/ComfyUI-AnimateDiff-Evolved",
                "AnimateDiffLoaderWithContext", "ADE_AnimateDiffLoaderWithContext", "ADE_StandardUniformViewOptions");
        register("Comfyroll", "https://github.com/Suzie1/ComfyUI_Comfyroll_CustomNodes",
                "CR Prompt Text", "CR Image Input Switch", "CR Text", "CR Conditioning Input Switch");
        register("KJNodes", "https://github.com/kijai/ComfyUI-KJNodes",
                "ColorMatch", "SetGetNode", "GetNode", "SetNode", "ImageBatchTest");
        register("ControlNetAux", "https://github.com/Fannovel16/comfy_controlnet_preprocessors",
                "LineArtPreprocessor", "DWPreprocessor", "Zoe-DepthMapPreprocessor", "CannyEdgePreprocessor");
    }

    private static void register(String name, String gitUrl, String... nodeTypes) {
        CustomNodePackage pkg = new CustomNodePackage(name, gitUrl, Arrays.asList(nodeTypes), List.of());
        for (String type : nodeTypes) {
            KNOWN_CUSTOM_NODES.put(type.toLowerCase(Locale.ROOT), pkg);
        }
    }

    /**
     * Definition of a custom node package containing metadata and node class names.
     */
    public record CustomNodePackage(String packageName, String gitUrl, List<String> nodeClasses, List<String> pipDependencies) {
        public CustomNodePackage(String packageName, String gitUrl, List<String> nodeClasses) {
            this(packageName, gitUrl, nodeClasses, List.of());
        }
    }

    /**
     * Report produced after analyzing a workflow for missing nodes.
     */
    public record NodeResolutionReport(
            Set<String> requiredNodeTypes,
            Set<String> missingNodeTypes,
            List<CustomNodePackage> recommendedPackages
    ) {}

    public CustomNodeResolverService(ObjectMapper objectMapper,
                                     WebClient.Builder webClientBuilder,
                                     ConfigService configService,
                                     SafePathValidator safePathValidator) {
        this(objectMapper, webClientBuilder, configService, safePathValidator, null);
    }

    @Autowired
    public CustomNodeResolverService(ObjectMapper objectMapper,
                                     WebClient.Builder webClientBuilder,
                                     @Autowired(required = false) ConfigService configService,
                                     @Autowired(required = false) SafePathValidator safePathValidator,
                                     @Autowired(required = false) ICustomNodeManagerBridgeService managerBridgeService) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
        this.configService = configService;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
        this.managerBridgeService = managerBridgeService != null ? managerBridgeService
                : new CustomNodeManagerBridgeService(configService, null, new CustomNodeInstaller(configService, this.safePathValidator, null));
    }

    /**
     * Extracts all node types from a workflow JSON (supporting both GUI graph format and API prompt format).
     *
     * @param workflowJson workflow JSON text
     * @return set of extracted node types
     */
    public Set<String> extractNodeTypes(String workflowJson) {
        Set<String> types = new LinkedHashSet<>();
        if (workflowJson == null || workflowJson.isBlank()) {
            return types;
        }

        try {
            JsonNode root = objectMapper.readTree(workflowJson);

            // 1. GUI Format: root.nodes = [...]
            if (root.has("nodes") && root.get("nodes").isArray()) {
                for (JsonNode node : root.get("nodes")) {
                    if (node.has("type")) {
                        types.add(node.get("type").asText());
                    }
                }
            }

            // 2. API Format: map of { nodeId: { "class_type": "..." } }
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode val = entry.getValue();
                    if (val.isObject() && val.has("class_type")) {
                        types.add(val.get("class_type").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse node types from workflow: {}", e.getMessage());
        }

        return types;
    }

    /**
     * Queries ComfyUI's /object_info endpoint to get the set of currently registered nodes.
     *
     * @return mono emitting the set of registered node class names
     */
    public Mono<Set<String>> getRegisteredComfyUiNodes() {
        String baseUrl = configService != null ? configService.getComfyUIUrl() : ConfigConstants.DEFAULT_COMFYUI_URL;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ConfigConstants.DEFAULT_COMFYUI_URL;
        }
        String cleanUrl = baseUrl.replaceAll("/+$", "");

        return webClient.get()
                .uri(cleanUrl + "/object_info")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(json -> {
                    Set<String> registered = new HashSet<>();
                    if (json.isObject()) {
                        Iterator<String> it = json.fieldNames();
                        while (it.hasNext()) {
                            registered.add(it.next());
                        }
                    }
                    return registered;
                })
                .onErrorReturn(Collections.emptySet());
    }

    /**
     * Analyzes a workflow against ComfyUI registered nodes and produces a resolution report,
     * looking up missing nodes in ComfyUI-Manager's extension mappings.
     *
     * @param workflowJson workflow JSON content
     * @return mono emitting the node resolution report
     */
    public Mono<NodeResolutionReport> analyzeWorkflowNodes(String workflowJson) {
        Set<String> required = extractNodeTypes(workflowJson);
        return getRegisteredComfyUiNodes().map(registered -> {
            Set<String> missing = new LinkedHashSet<>();
            Set<CustomNodePackage> packages = new LinkedHashSet<>();

            for (String req : required) {
                // Ignore standard basic nodes that are universally built into ComfyUI
                if (isCoreNode(req)) {
                    continue;
                }
                if (!registered.isEmpty() && !registered.contains(req)) {
                    missing.add(req);

                    // 1. Resolve via ComfyUI-Manager Bridge
                    Optional<CustomNodePackage> resolvedPkg = Optional.empty();
                    if (managerBridgeService != null) {
                        resolvedPkg = managerBridgeService.resolveNodePackage(req);
                    }

                    // 2. Fallback to static mapping
                    if (resolvedPkg.isEmpty()) {
                        resolvedPkg = Optional.ofNullable(KNOWN_CUSTOM_NODES.get(req.toLowerCase(Locale.ROOT)));
                    }

                    resolvedPkg.ifPresent(packages::add);
                }
            }

            return new NodeResolutionReport(required, missing, new ArrayList<>(packages));
        });
    }

    /**
     * Clones a custom node repository directly into ComfyUI's custom_nodes directory.
     *
     * @param gitUrl git repository URL
     * @param customNodesDir destination custom_nodes directory
     * @return completable future completing with true if installed
     */
    public CompletableFuture<Boolean> installCustomNode(String gitUrl, Path customNodesDir) {
        CustomNodePackage pkg = new CustomNodePackage(extractRepoName(gitUrl), gitUrl, List.of(), List.of());
        if (managerBridgeService != null) {
            return managerBridgeService.installPackageAsync(pkg, msg -> logger.info("[CustomNodeInstall] {}", msg));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                safePathValidator.validateWithinBase(customNodesDir, customNodesDir);
                Files.createDirectories(customNodesDir);

                String repoName = extractRepoName(gitUrl);
                Path targetDir = customNodesDir.resolve(repoName);
                safePathValidator.validateWithinBase(customNodesDir, targetDir);

                if (Files.exists(targetDir)) {
                    logger.info("Custom node directory '{}' already exists. Skipping clone.", targetDir);
                    return true;
                }

                logger.info("Cloning custom node from '{}' to '{}'...", gitUrl, targetDir);
                try (Git git = Git.cloneRepository()
                        .setURI(gitUrl)
                        .setDirectory(targetDir.toFile())
                        .call()) {
                    logger.info("Custom node package '{}' successfully installed.", repoName);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Failed to install custom node from {}: {}", gitUrl, e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Installs a list of resolved custom node packages with live console progress reporting.
     *
     * @param packages list of packages to install
     * @param progress progress consumer
     * @return future completing with true if all installed successfully
     */
    public CompletableFuture<Boolean> installPackagesAsync(List<CustomNodePackage> packages, Consumer<String> progress) {
        if (managerBridgeService != null) {
            return managerBridgeService.installPackagesAsync(packages, progress);
        }
        return CompletableFuture.completedFuture(false);
    }

    public ICustomNodeManagerBridgeService getManagerBridgeService() {
        return managerBridgeService;
    }

    private String extractRepoName(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) return "custom_node";
        String clean = gitUrl.trim();
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith(".git")) clean = clean.substring(0, clean.length() - 4);
        int lastSlash = clean.lastIndexOf('/');
        return lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
    }

    private boolean isCoreNode(String nodeType) {
        return switch (nodeType) {
            case "KSampler", "KSamplerAdvanced", "CheckpointLoaderSimple", "CLIPTextEncode",
                 "VAEDecode", "VAEEncode", "EmptyLatentImage", "SaveImage", "PreviewImage",
                 "LoadImage", "CLIPLoader", "VAELoader", "UNETLoader", "DualCLIPLoader",
                 "ConditioningCombine", "ConditioningAverage", "LatentUpscale" -> true;
            default -> false;
        };
    }
}
