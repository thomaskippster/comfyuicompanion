package de.tki.comfyuicompanion.service.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.ICustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.CustomNodeInstaller;
import de.tki.comfyuicompanion.service.impl.CustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.workflow.ComfyCoreNodesCatalog;
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
                "ColorMatch", "SetGetNode", "GetNode", "SetNode", "ImageBatchTest", "ConditioningMultiCombine");
        register("ControlNetAux", "https://github.com/Fannovel16/comfy_controlnet_preprocessors",
                "LineArtPreprocessor", "DWPreprocessor", "Zoe-DepthMapPreprocessor", "CannyEdgePreprocessor");
        register("LivePortrait", "https://github.com/kijai/ComfyUI-LivePortrait",
                "LivePortraitLoadCropperModel", "LivePortraitCropper", "LivePortraitPredict", "LivePortraitAudioDriven", "LivePortraitLoadModel");
        register("Wav2Lip", "https://github.com/Shakker-Labs/ComfyUI-Wav2Lip",
                "Wav2Lip", "Wav2LipPreprocessor");
        register("SadTalker", "https://github.com/OpenTalker/SadTalker",
                "SadTalker", "SadTalkerLoader");
        register("ComfyUI-WanVideoWrapper", "https://github.com/kijai/ComfyUI-WanVideoWrapper",
                "WanVideoSampler", "WanVideoDecode", "WanVideoBlockSwap", "WanVideoLoraSelectMulti",
                "WanVideoTorchCompileSettings", "WanVideoModelLoader", "WanVideoTextEncode",
                "WanVideoImageToVideo", "WanVideoControlNet", "WanVideoPrompt", "WanVideoControlNetLoader",
                "WanVideoVAELoader", "WanVideoTextEncodeCached");
        register("cg-use-everywhere", "https://github.com/chrisgoringe/cg-use-everywhere",
                "Anything Everywhere", "Seed Everywhere", "Anything Everywhere?", "Anything Everywhere3",
                "Prompts Everywhere", "Use Everywhere Server");
        register("comfyui-custom-scripts", "https://github.com/pythongosssss/ComfyUI-Custom-Scripts",
                "PlaySound|pysssss", "ShowText|pysssss", "ConstrainImage|pysssss", "MathExpression|pysssss",
                "PresetText|pysssss", "CheckpointLoaderSimple|pysssss");
        register("crt-nodes", "https://github.com/PGCRT/CRT-Nodes",
                "FancyNoteNode", "FancyTimerNode", "CRTOptions", "CRTSwitch");
        register("ComfyUI-Chibi-Nodes", "https://github.com/chibiace/ComfyUI-Chibi-Nodes",
                "Textbox", "ChibiNode");
        register("ComfyUI-Video-Helper-Suite", "https://github.com/Kosinkadink/ComfyUI-Video-Helper-Suite",
                "VHS_LoadVideo", "VHS_VideoCombine", "VHS_LoadImages", "VHS_SplitMasks", "VHS_VideoCombine_adv");
        register("rgthree-comfy", "https://github.com/rgthree/rgthree-comfy",
                "Seed (rgthree)", "RgthreeSeed", "Context (rgthree)", "RgthreeContext",
                "Context Big (rgthree)", "RgthreeBigContext", "Reroute (rgthree)", "RgthreeReroute",
                "Power Lora Loader (rgthree)", "RgthreePowerLoraLoader", "Display Any (rgthree)", "RgthreeDisplayAny");
        register("mikey_nodes", "https://github.com/bash-j/mikey_nodes",
                "Empty Latent Ratio Select SDXL", "Empty Latent Ratio Custom SDXL",
                "Empty Latent Ratio Select SDXL (Mikey)", "Save Image Ratio SDXL");
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
     * Resolves a known fallback custom node package by node class name.
     *
     * @param nodeType node class name
     * @return optional containing the package if known
     */
    public Optional<CustomNodePackage> findKnownPackage(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(KNOWN_CUSTOM_NODES.get(nodeType.toLowerCase(Locale.ROOT)));
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
            Set<String> localSubgraphIds = new HashSet<>();

            // 1a. Check LiteGraph Subgraphs definitions.subgraphs
            if (root.has("definitions") && root.get("definitions").has("subgraphs") && root.get("definitions").get("subgraphs").isArray()) {
                for (JsonNode sg : root.get("definitions").get("subgraphs")) {
                    if (sg.has("id")) {
                        localSubgraphIds.add(sg.get("id").asText());
                    }
                    if (sg.has("nodes") && sg.get("nodes").isArray()) {
                        for (JsonNode sn : sg.get("nodes")) {
                            if (sn.has("type")) {
                                types.add(sn.get("type").asText());
                            }
                        }
                    }
                }
            }

            // 1. GUI Format: root.nodes = [...]
            if (root.has("nodes") && root.get("nodes").isArray()) {
                for (JsonNode node : root.get("nodes")) {
                    if (node.has("type")) {
                        String nodeType = node.get("type").asText();
                        if (!localSubgraphIds.contains(nodeType)) {
                            types.add(nodeType);
                        }
                    }
                }
            }

            // 2. API Format: map of { nodeId: { "class_type": "..." } }
            if (root.isObject()) {
                for (Map.Entry<String, JsonNode> entry : root.properties()) {
                    JsonNode val = entry.getValue();
                    if (val.isObject() && val.has("class_type")) {
                        String classType = val.get("class_type").asText();
                        if (!localSubgraphIds.contains(classType)) {
                            types.add(classType);
                        }
                    }
                }
            }

            types.removeAll(localSubgraphIds);
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
     * Resolves a custom node package via ComfyUI-Manager bridge or static mappings.
     *
     * @param nodeType node class name
     * @return resolved package optional
     */
    public Optional<CustomNodePackage> resolvePackage(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return Optional.empty();
        }
        Optional<CustomNodePackage> resolved = findKnownPackage(nodeType);
        if (resolved.isEmpty() && managerBridgeService != null) {
            resolved = managerBridgeService.resolveNodePackage(nodeType);
        }
        return resolved;
    }

    /**
     * Scans the local ComfyUI custom_nodes directory for installed package folder names.
     *
     * @return set of lowercase installed custom node directory names
     */
    public Set<String> scanLocalInstalledCustomNodes() {
        Set<String> installed = new HashSet<>();
        String comfyPath = configService != null ? configService.getComfyUIPath() : null;
        if (comfyPath == null || comfyPath.isBlank()) {
            return installed;
        }
        File customNodesDir = new File(comfyPath, "custom_nodes");
        if (customNodesDir.exists() && customNodesDir.isDirectory()) {
            File[] files = customNodesDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        installed.add(f.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return installed;
    }

    /**
     * Analyzes a workflow against ComfyUI registered nodes and produces a resolution report,
     * looking up missing nodes in ComfyUI-Manager's extension mappings or local custom_nodes disk directory.
     *
     * @param workflowJson workflow JSON content
     * @return mono emitting the node resolution report
     */
    public Mono<NodeResolutionReport> analyzeWorkflowNodes(String workflowJson) {
        Set<String> required = extractNodeTypes(workflowJson);
        return getRegisteredComfyUiNodes().map(registered -> {
            Set<String> missing = new LinkedHashSet<>();
            Set<CustomNodePackage> packages = new LinkedHashSet<>();
            Set<String> localPacks = scanLocalInstalledCustomNodes();

            for (String req : required) {
                // Ignore standard basic nodes that are universally built into ComfyUI
                if (isCoreNode(req)) {
                    continue;
                }

                boolean isInstalled = false;
                if (!registered.isEmpty()) {
                    isInstalled = registered.contains(req);
                } else {
                    // When ComfyUI is offline, inspect local custom_nodes directory
                    Optional<CustomNodePackage> resolvedPkg = resolvePackage(req);
                    if (resolvedPkg.isPresent()) {
                        String repoName = extractRepoName(resolvedPkg.get().gitUrl()).toLowerCase(Locale.ROOT);
                        String pkgName = resolvedPkg.get().packageName().toLowerCase(Locale.ROOT);
                        isInstalled = localPacks.contains(repoName) || localPacks.contains(pkgName);
                    }
                }

                if (!isInstalled) {
                    missing.add(req);
                    resolvePackage(req).ifPresent(packages::add);
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
        return ComfyCoreNodesCatalog.isCoreNode(nodeType);
    }
}
