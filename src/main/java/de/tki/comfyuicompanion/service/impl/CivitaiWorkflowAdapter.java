package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Enterprise service component responsible for adapting LiteGraph workflow structures,
 * substituting model references with locally installed assets, and reconciling input files.
 */
@Component
public class CivitaiWorkflowAdapter {

    private static final Logger log = LoggerFactory.getLogger(CivitaiWorkflowAdapter.class);

    private final ObjectMapper objectMapper;
    private final IConfigService configService;
    private final CivitaiLocalModelScanner localModelScanner;

    @Autowired
    public CivitaiWorkflowAdapter(
            ObjectMapper objectMapper,
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) CivitaiLocalModelScanner localModelScanner) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.configService = configService;
        this.localModelScanner = localModelScanner;
    }

    /**
     * Adapts model and input asset references in a LiteGraph workflow to match installed models and local inputs.
     *
     * @param workflow       the LiteGraph workflow root node
     * @param requiredModels list of operational models resolved for this entry
     */
    public void adaptModelReferences(ObjectNode workflow, List<ModelInfo> requiredModels) {
        if (workflow == null || requiredModels == null || requiredModels.isEmpty()) {
            return;
        }
        JsonNode nodesNode = workflow.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            return;
        }

        String installedCheckpoint = null;
        String installedUnet = null;
        String installedVae = null;
        String installedClip = null;
        List<String> installedClips = new ArrayList<>();

        for (ModelInfo info : requiredModels) {
            String type = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
            String fn = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
            if (fn == null || fn.endsWith(".zip") || fn.endsWith(".json")) {
                continue;
            }

            if (type.contains("checkpoint")) {
                if (installedCheckpoint == null && isCheckpointedSafetensors(fn)) installedCheckpoint = resolveComfyWidgetPath(fn, "checkpoints");
            } else if (type.contains("unet") || type.contains("diffusion")) {
                if (installedUnet == null) installedUnet = resolveComfyWidgetPath(fn, "unet", "diffusion_models");
            } else if (type.contains("vae")) {
                if (installedVae == null) installedVae = resolveComfyWidgetPath(fn, "vae");
            } else if (type.contains("clip") || type.contains("text_encoders")) {
                String clipPath = resolveComfyWidgetPath(fn, "text_encoders", "clip");
                if (installedClip == null) installedClip = clipPath;
                if (!installedClips.contains(clipPath)) installedClips.add(clipPath);
            }
        }

        List<ObjectNode> ckptNodesToSplit = new ArrayList<>();
        List<JsonNode> targetNodes = collectAllNodes(workflow, nodesNode);

        for (JsonNode node : targetNodes) {
            adaptSingleNode(node, installedCheckpoint, installedUnet, installedVae, installedClip, installedClips, ckptNodesToSplit);
        }

        if (!ckptNodesToSplit.isEmpty()) {
            List<String> clipsToUse = resolveEffectiveClipsForSplit(installedUnet, installedClips);
            ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(objectMapper);
            for (ObjectNode cn : ckptNodesToSplit) {
                sanitizer.splitCheckpointLoaderIntoComponentLoaders(workflow, cn, installedUnet, clipsToUse, installedVae);
            }
        }
    }

    private List<JsonNode> collectAllNodes(ObjectNode workflow, JsonNode rootNodes) {
        List<JsonNode> targetNodes = new ArrayList<>();
        if (rootNodes.isArray()) rootNodes.forEach(targetNodes::add);
        JsonNode subgraphs = workflow.path("definitions").path("subgraphs");
        if (subgraphs.isArray()) {
            for (JsonNode sg : subgraphs) {
                JsonNode sgNodes = sg.path("nodes");
                if (sgNodes.isArray()) sgNodes.forEach(targetNodes::add);
            }
        } else if (subgraphs.isObject()) {
            subgraphs.elements().forEachRemaining(sg -> {
                JsonNode sgNodes = sg.path("nodes");
                if (sgNodes.isArray()) sgNodes.forEach(targetNodes::add);
            });
        }
        return targetNodes;
    }

    private void adaptSingleNode(JsonNode node, String installedCheckpoint, String installedUnet,
                                 String installedVae, String installedClip, List<String> installedClips,
                                 List<ObjectNode> ckptNodesToSplit) {
        String nodeType = node.path("type").asText("");
        JsonNode widgets = node.get("widgets_values");
        if (widgets == null || !widgets.isArray() || widgets.isEmpty()) {
            return;
        }

        ArrayNode arr = (ArrayNode) widgets;
        if (nodeType.equalsIgnoreCase("LoadImage") && arr.size() > 0) {
            String currentImg = arr.get(0).asText("");
            if (!isInputFileLocallyPresent(currentImg)) {
                findFallbackInputImage().ifPresent(fallbackImg -> {
                    arr.set(0, objectMapper.getNodeFactory().textNode(fallbackImg));
                    syncNamedWidget(node, currentImg, fallbackImg);
                    log.info("Adapted LoadImage node {} to available '{}'", node.path("id").asText(), fallbackImg);
                });
            }
            return;
        }

        for (int i = 0; i < arr.size(); i++) {
            String val = arr.get(i).asText("");
            if (isModelExtension(val)) {
                adaptWidgetModelValue(node, nodeType, arr, i, val, installedCheckpoint, installedUnet, installedVae, installedClip, installedClips, ckptNodesToSplit);
            }
        }
    }

    private void adaptWidgetModelValue(JsonNode node, String nodeType, ArrayNode arr, int index, String val,
                                       String installedCheckpoint, String installedUnet, String installedVae,
                                       String installedClip, List<String> installedClips, List<ObjectNode> ckptNodesToSplit) {
        String valLower = val.toLowerCase(Locale.ROOT);
        String simpleName = extractSimpleFilename(val);
        String nodeTypeLower = nodeType != null ? nodeType.toLowerCase(Locale.ROOT) : "";

        // 1. Reconcile subfolder path or stripped filename (e.g. FLUX.1/ae.safetensors -> FLUX1/ae.safetensors)
        if (localModelScanner != null && !isModelLocallyAvailable(val)) {
            Optional<String> relPath = localModelScanner.findComfyRelativeModelPath(simpleName);
            if (relPath.isPresent()) {
                String rel = relPath.get();
                arr.set(index, objectMapper.getNodeFactory().textNode(rel));
                syncNamedWidget(node, val, rel);
                log.info("Adapted node {} model widget [{}] from '{}' to subfolder path '{}'", node.path("id").asText(), index, val, rel);
                return;
            } else if (localModelScanner.isExactModelInstalled(simpleName)) {
                arr.set(index, objectMapper.getNodeFactory().textNode(simpleName));
                syncNamedWidget(node, val, simpleName);
                log.info("Adapted node {} model widget [{}] from '{}' to base filename '{}'", node.path("id").asText(), index, val, simpleName);
                return;
            }
        }

        // 2. CheckpointLoaderSimple / AIO split
        if (nodeType.equalsIgnoreCase("CheckpointLoaderSimple")) {
            boolean validSafetensors = isCheckpointedSafetensors(val) || isCheckpointedSafetensors(simpleName);
            if (!validSafetensors || (!isModelLocallyAvailable(val) && !isModelLocallyAvailable(simpleName))) {
                boolean isFlux = (valLower.contains("flux") || (installedUnet != null && installedUnet.toLowerCase(Locale.ROOT).contains("flux")));
                if (isFlux && installedUnet != null && installedVae != null && !installedClips.isEmpty()) {
                    ckptNodesToSplit.add((ObjectNode) node);
                    return;
                } else if (installedCheckpoint != null && isCheckpointedSafetensors(installedCheckpoint)) {
                    arr.set(index, objectMapper.getNodeFactory().textNode(installedCheckpoint));
                    syncNamedWidget(node, val, installedCheckpoint);
                    return;
                } else if (localModelScanner != null) {
                    String fallback = localModelScanner.findFallbackCheckpointName(valLower);
                    if (fallback != null && isCheckpointedSafetensors(fallback)) {
                        arr.set(index, objectMapper.getNodeFactory().textNode(fallback));
                        syncNamedWidget(node, val, fallback);
                        return;
                    }
                }
            }
            return;
        }

        // 3. UNET / Diffusion model adaptation
        if (nodeTypeLower.contains("unet") || nodeTypeLower.contains("diffusion")) {
            if (installedUnet != null && !isModelLocallyAvailable(val) && !isModelLocallyAvailable(simpleName)) {
                arr.set(index, objectMapper.getNodeFactory().textNode(installedUnet));
                syncNamedWidget(node, val, installedUnet);
                log.info("Adapted UNET/diffusion node {} to installed '{}'", node.path("id").asText(), installedUnet);
                return;
            }
        }

        // 4. CLIP / Text Encoder adaptation
        if (nodeTypeLower.contains("clip") || nodeTypeLower.contains("vision") || nodeTypeLower.contains("encoder")) {
            if (localModelScanner != null && !isModelLocallyAvailable(val) && !isModelLocallyAvailable(simpleName)) {
                String adapted = resolveClipFallback(valLower, installedClip);
                if (adapted != null) {
                    arr.set(index, objectMapper.getNodeFactory().textNode(adapted));
                    syncNamedWidget(node, val, adapted);
                    log.info("Adapted text encoder node {} to installed '{}'", node.path("id").asText(), adapted);
                    return;
                }
            }
        }

        // 5. VAE adaptation
        if (nodeTypeLower.contains("vae") || simpleName.startsWith("ae.") || simpleName.equals("ae.safetensors")) {
            if (installedVae != null && !isModelLocallyAvailable(val) && !isModelLocallyAvailable(simpleName)) {
                arr.set(index, objectMapper.getNodeFactory().textNode(installedVae));
                syncNamedWidget(node, val, installedVae);
                log.info("Adapted VAE node {} to installed '{}'", node.path("id").asText(), installedVae);
                return;
            }
        }

        // 6. Checkpoint selector / loader adaptation (e.g. SeargeModelSelector)
        if (installedCheckpoint != null && !isModelLocallyAvailable(val) && !isModelLocallyAvailable(simpleName)) {
            if ((index == 0 && (valLower.contains("base") || valLower.contains("checkpoint") || valLower.contains("model")))
                    || valLower.contains("sd_xl_base") || valLower.contains("v1-5")) {
                arr.set(index, objectMapper.getNodeFactory().textNode(installedCheckpoint));
                syncNamedWidget(node, val, installedCheckpoint);
                log.info("Adapted checkpoint selector node {} [{}] to installed '{}'", node.path("id").asText(), index, installedCheckpoint);
                return;
            }
        }
    }

    private String extractSimpleFilename(String pathOrVal) {
        if (pathOrVal == null || pathOrVal.isBlank()) return "";
        String s = pathOrVal.replace('\\', '/').trim();
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    private String resolveClipFallback(String valLower, String installedClip) {
        if (valLower.contains("qwen")) {
            var localQwen = localModelScanner.findLocalModelMatching("qwen", "text_encoders", "clip");
            if (localQwen != null) return resolveComfyWidgetPath(localQwen.getFilename(), "text_encoders", "clip");
        } else if (valLower.contains("t5")) {
            var localT5 = localModelScanner.findLocalModelMatching("t5", "text_encoders", "clip");
            if (localT5 != null) return resolveComfyWidgetPath(localT5.getFilename(), "text_encoders", "clip");
        }
        return installedClip;
    }

    private void syncNamedWidget(JsonNode node, String oldVal, String newVal) {
        JsonNode named = node.get("widgets_values_named");
        if (named != null && named.isObject()) {
            ObjectNode obj = (ObjectNode) named;
            obj.fieldNames().forEachRemaining(k -> {
                if (obj.path(k).asText("").equalsIgnoreCase(oldVal)) {
                    obj.put(k, newVal);
                }
            });
        }
    }

    private boolean isModelExtension(String val) {
        if (val == null) return false;
        String lower = val.toLowerCase(Locale.ROOT);
        return lower.endsWith(".safetensors") || lower.endsWith(".ckpt") || lower.endsWith(".gguf") || lower.endsWith(".pt") || lower.endsWith(".pth");
    }

    private static boolean isCheckpointedSafetensors(String fn) {
        if (fn == null || fn.isBlank()) return false;
        String lower = fn.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".safetensors") || lower.endsWith(".ckpt")) && !lower.endsWith(".gguf");
    }

    private List<String> resolveEffectiveClipsForSplit(String installedUnet, List<String> installedClips) {
        if (localModelScanner != null && installedUnet != null && (installedUnet.toLowerCase(Locale.ROOT).contains("klein") || installedUnet.toLowerCase(Locale.ROOT).contains("flux-2"))) {
            var qwen = localModelScanner.findLocalModelMatching("qwen", "text_encoders", "clip");
            if (qwen != null) return List.of(resolveComfyWidgetPath(qwen.getFilename(), "text_encoders", "clip"));
        }
        return installedClips;
    }

    private String resolveComfyWidgetPath(String filename, String... folders) {
        if (localModelScanner == null || filename == null || filename.isBlank()) return filename;
        return localModelScanner.findComfyRelativeModelPath(filename, folders).orElse(filename);
    }

    private boolean isModelLocallyAvailable(String filename) {
        return localModelScanner != null && filename != null && !filename.isBlank()
                && localModelScanner.isExactModelInstalled(filename, "checkpoints", "unet", "diffusion_models", "clip", "text_encoders", "vae", "loras");
    }

    private boolean isInputFileLocallyPresent(String filename) {
        if (filename == null || filename.isBlank() || configService == null) return false;
        String inputDirStr = configService.getResolvedInputDetailDir();
        return inputDirStr != null && !inputDirStr.isBlank() && Files.isRegularFile(Paths.get(inputDirStr, filename));
    }

    private Optional<String> findFallbackInputImage() {
        if (configService == null) return Optional.empty();
        try {
            List<Path> candidateDirs = new ArrayList<>();
            String inputDirStr = configService.getResolvedInputDetailDir();
            if (inputDirStr != null && !inputDirStr.isBlank()) candidateDirs.add(Paths.get(inputDirStr));
            String comfyPath = configService.getComfyUIPath();
            if (comfyPath != null && !comfyPath.isBlank()) candidateDirs.add(Paths.get(comfyPath, "input"));

            for (Path dir : candidateDirs) {
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> stream = Files.list(dir)) {
                    Optional<String> found = stream.filter(Files::isRegularFile).filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
                    }).map(p -> p.getFileName().toString()).findFirst();
                    if (found.isPresent()) return found;
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }
}
