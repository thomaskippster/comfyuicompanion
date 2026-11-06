package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.workflow.ArchitectureProfile;
import de.tki.comfyuicompanion.service.workflow.ArchitectureProfileCatalog;
import de.tki.comfyuicompanion.service.workflow.GenericWorkflowBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Dedicated builder synthesizing ComfyUI GUI-format workflows (LiteGraph schema)
 * for models inspected in the Model Hub.
 * <p>
 * Produces structured canvas graphs (containing node coordinates, widget configurations,
 * and pin link mappings) compatible with {@code app.loadGraphData()}, enabling users
 * to directly edit their prompts and tweak parameters in the ComfyUI web interface.
 * </p>
 */
@Component
public class CivitaiGuiWorkflowBuilder {

    private static final Logger log = LoggerFactory.getLogger(CivitaiGuiWorkflowBuilder.class);

    private final ObjectMapper objectMapper;
    private final IConfigService configService;
    private final ComfyGraphNodeFactory nodeFactory;
    private final CivitaiLocalModelScanner localModelScanner;
    private final CivitaiWorkflowFileResolver workflowFileResolver;
    private final ArchitectureProfileCatalog profileCatalog;
    private final GenericWorkflowBuilder workflowBuilder;

    /**
     * Constructs a new CivitaiGuiWorkflowBuilder with the specified dependencies.
     *
     * @param objectMapper         the object mapper for creating JSON graph trees
     * @param configService        optional configuration service for resolving local model directories
     * @param nodeFactory          optional factory for creating LiteGraph nodes and links
     * @param localModelScanner    scanner for detecting installed checkpoints
     * @param workflowFileResolver resolver for loading real workflows from local storage
     * @param profileCatalog       catalog of architecture generation profiles
     */
    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            IConfigService configService,
            ComfyGraphNodeFactory nodeFactory,
            CivitaiLocalModelScanner localModelScanner,
            CivitaiWorkflowFileResolver workflowFileResolver,
            ArchitectureProfileCatalog profileCatalog) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.configService = configService;
        this.nodeFactory = nodeFactory != null ? nodeFactory : new ComfyGraphNodeFactory(this.objectMapper);
        this.localModelScanner = localModelScanner != null ? localModelScanner : new CivitaiLocalModelScanner(this.configService);
        this.workflowFileResolver = workflowFileResolver != null ? workflowFileResolver : new CivitaiWorkflowFileResolver(this.objectMapper, this.configService, null);
        this.profileCatalog = profileCatalog != null ? profileCatalog : new ArchitectureProfileCatalog();
        this.workflowBuilder = new GenericWorkflowBuilder(this.objectMapper, this.nodeFactory);
    }

    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            IConfigService configService,
            ComfyGraphNodeFactory nodeFactory,
            CivitaiLocalModelScanner localModelScanner,
            CivitaiWorkflowFileResolver workflowFileResolver) {
        this(objectMapper, configService, nodeFactory, localModelScanner, workflowFileResolver, null);
    }

    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            IConfigService configService,
            ComfyGraphNodeFactory nodeFactory,
            CivitaiLocalModelScanner localModelScanner) {
        this(objectMapper, configService, nodeFactory, localModelScanner, null);
    }

    @Autowired
    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) ComfyGraphNodeFactory nodeFactory,
            @Autowired(required = false) CivitaiWorkflowFileResolver workflowFileResolver) {
        this(objectMapper, configService, nodeFactory, null, workflowFileResolver);
    }

    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            IConfigService configService,
            ComfyGraphNodeFactory nodeFactory) {
        this(objectMapper, configService, nodeFactory, null, null);
    }

    /**
     * Constructor supporting legacy callers with 2 arguments.
     *
     * @param objectMapper  the object mapper
     * @param configService configuration service
     */
    public CivitaiGuiWorkflowBuilder(ObjectMapper objectMapper, IConfigService configService) {
        this(objectMapper, configService, null, null, null);
    }

    /**
     * Convenience constructor for testing without Spring context.
     *
     * @param objectMapper the object mapper for creating JSON graph trees
     */
    public CivitaiGuiWorkflowBuilder(ObjectMapper objectMapper) {
        this(objectMapper, null, null, null, null);
    }

    /**
     * Builds a LiteGraph-compatible GUI workflow {@link ObjectNode} for the specified Civitai model entry,
     * using the default trigger words or fallback prompt.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels the list of operational models resolved for this entry
     * @return an {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildGuiWorkflow(CivitaiModelEntry entry, List<ModelInfo> requiredModels) {
        return buildGuiWorkflow(entry, requiredModels, null);
    }

    /**
     * Builds a LiteGraph-compatible GUI workflow {@link ObjectNode} for the specified Civitai model entry,
     * embedding the provided user prompt into the positive prompt node.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels the list of operational models resolved for this entry
     * @param userPrompt     optional custom positive prompt text to inject into the canvas graph
     * @return an {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildGuiWorkflow(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        String typeLower = entry != null && entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";
        String baseLower = entry != null && entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";
        boolean isLora = typeLower.contains("lora") || typeLower.contains("locon");

        String primaryFilename = entry != null && entry.filename() != null ? entry.filename() : "model.safetensors";
        boolean isWorkflow = typeLower.contains("workflow")
                || primaryFilename.toLowerCase(Locale.ROOT).endsWith(".zip")
                || primaryFilename.toLowerCase(Locale.ROOT).endsWith(".json");

        // 1. If entry is a workflow or archive, attempt to load the actual bundled workflow graph
        if (isWorkflow && workflowFileResolver != null) {
            Optional<ObjectNode> realWorkflow = workflowFileResolver.tryResolveRealWorkflow(entry);
            if (realWorkflow.isPresent()) {
                ObjectNode wf = realWorkflow.get();
                if (userPrompt != null && !userPrompt.isBlank()) {
                    workflowFileResolver.injectUserPrompt(wf, userPrompt);
                }
                workflowFileResolver.adaptModelReferences(wf, requiredModels);
                new ComfyWorkflowCompatibilitySanitizer(objectMapper).sanitizeWorkflow(wf, java.util.Collections.emptySet());
                return wf;
            }
        }

        // 2. Determine base model filename and target lora filename for synthetic fallback graph
        String baseModelFilename = primaryFilename;

        if (isLora) {
            String resolvedBase = null;
            if (requiredModels != null) {
                for (ModelInfo info : requiredModels) {
                    String itype = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
                    if (itype.contains("checkpoint") || itype.contains("unet") || itype.contains("diffusion")) {
                        String fn = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
                        if (fn != null && !fn.equalsIgnoreCase(primaryFilename)) {
                            resolvedBase = fn;
                            break;
                        }
                    }
                }
            }
            if (resolvedBase == null || resolvedBase.equalsIgnoreCase(primaryFilename)) {
                resolvedBase = findFallbackCheckpointName(baseLower);
            }
            baseModelFilename = resolvedBase;
        } else if (isWorkflow) {
            // Defensive guard: Workflow archives (.zip, .json) must NEVER be set as ckpt_name in CheckpointLoaderSimple
            String resolvedBase = null;
            if (requiredModels != null) {
                for (ModelInfo info : requiredModels) {
                    String fn = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
                    if (fn != null && !fn.equalsIgnoreCase(primaryFilename)
                            && !fn.toLowerCase(Locale.ROOT).endsWith(".zip")
                            && !fn.toLowerCase(Locale.ROOT).endsWith(".json")
                            && !fn.toLowerCase(Locale.ROOT).endsWith(".png")) {
                        resolvedBase = fn;
                        break;
                    }
                }
            }
            if (resolvedBase == null) {
                resolvedBase = findFallbackCheckpointName(baseLower);
            }
            baseModelFilename = resolvedBase;
        }

        // Architecture-specific dimension and sampling defaults resolved from declarative profile
        String lookupKey = !baseLower.isBlank() ? baseLower : (isLora ? baseModelFilename : primaryFilename);
        ArchitectureProfile profile = profileCatalog.resolveProfile(lookupKey);

        // Positive prompt resolution: custom prompt -> trigger words -> empty string
        String effectivePrompt;
        if (userPrompt != null && !userPrompt.isBlank()) {
            effectivePrompt = userPrompt.trim();
        } else if (entry != null && entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            effectivePrompt = String.join(", ", entry.triggerWords());
        } else {
            effectivePrompt = "";
        }

        ModelInfo baseInfo = new ModelInfo("checkpoints", baseModelFilename, null);
        baseInfo.setBase(baseLower);
        baseInfo.setFilename(baseModelFilename);

        log.debug("Synthesized LiteGraph GUI workflow for model {} (isLora={}) via GenericWorkflowBuilder", primaryFilename, isLora);
        return workflowBuilder.buildWorkflow(profile, baseInfo, isLora ? primaryFilename : null, effectivePrompt);
    }

    /**
     * Builds and serializes the LiteGraph GUI workflow into pretty-printed JSON string format.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels the list of operational models
     * @return pretty-printed JSON string of the LiteGraph diagram
     */
    public String buildGuiWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels) {
        return buildGuiWorkflowJson(entry, requiredModels, null);
    }

    /**
     * Builds and serializes the LiteGraph GUI workflow into pretty-printed JSON string format,
     * including custom user prompt text.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels the list of operational models
     * @param userPrompt     optional custom positive prompt text
     * @return pretty-printed JSON string of the LiteGraph diagram
     */
    public String buildGuiWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        try {
            ObjectNode root = buildGuiWorkflow(entry, requiredModels, userPrompt);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize GUI workflow JSON: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * Determines a fallback checkpoint filename when building workflows for LoRA models
     * that have no explicit base checkpoint attached in their dependencies.
     *
     * @param baseLower the lowercased base architecture string
     * @return a valid checkpoint filename
     */
    private String findFallbackCheckpointName(String baseLower) {
        return (localModelScanner != null ? localModelScanner : new CivitaiLocalModelScanner(configService))
                .findFallbackCheckpointName(baseLower);
    }
}
