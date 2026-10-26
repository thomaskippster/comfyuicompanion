package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
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

    /**
     * Constructs a new CivitaiGuiWorkflowBuilder with the specified dependencies.
     *
     * @param objectMapper  the object mapper for creating JSON graph trees
     * @param configService optional configuration service for resolving local model directories
     * @param nodeFactory   optional factory for creating LiteGraph nodes and links
     */
    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            IConfigService configService,
            ComfyGraphNodeFactory nodeFactory,
            CivitaiLocalModelScanner localModelScanner) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.configService = configService;
        this.nodeFactory = nodeFactory != null ? nodeFactory : new ComfyGraphNodeFactory(this.objectMapper);
        this.localModelScanner = localModelScanner != null ? localModelScanner : new CivitaiLocalModelScanner(this.configService);
    }

    @Autowired
    public CivitaiGuiWorkflowBuilder(
            ObjectMapper objectMapper,
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) ComfyGraphNodeFactory nodeFactory) {
        this(objectMapper, configService, nodeFactory, null);
    }

    /**
     * Constructor supporting legacy callers with 2 arguments.
     *
     * @param objectMapper  the object mapper
     * @param configService configuration service
     */
    public CivitaiGuiWorkflowBuilder(ObjectMapper objectMapper, IConfigService configService) {
        this(objectMapper, configService, null);
    }

    /**
     * Convenience constructor for testing without Spring context.
     *
     * @param objectMapper the object mapper for creating JSON graph trees
     */
    public CivitaiGuiWorkflowBuilder(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
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

        // Determine base model filename and target lora filename
        String primaryFilename = entry != null && entry.filename() != null ? entry.filename() : "model.safetensors";
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
        }

        // Architecture-specific dimension and sampling defaults
        int width = 1024;
        int height = 1024;
        int steps = 20;
        double cfg = 7.0;
        String samplerName = "euler";
        String scheduler = "normal";

        if (baseLower.contains("1.5") || baseLower.contains("sd 1.5") || (isLora && (baseModelFilename.contains("1-5") || baseModelFilename.contains("1.5")))) {
            width = 512;
            height = 512;
            steps = 20;
            cfg = 7.0;
        } else if (baseLower.contains("flux")) {
            width = 1024;
            height = 1024;
            steps = 20;
            cfg = 1.0;
            samplerName = "euler";
            scheduler = "simple";
        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            width = 1024;
            height = 1024;
            steps = 25;
            cfg = 7.0;
        } else if (baseLower.contains("wan")) {
            width = 832;
            height = 480;
            steps = 30;
            cfg = 6.0;
        }

        // Positive prompt resolution: custom prompt -> trigger words -> empty string
        String effectivePrompt;
        if (userPrompt != null && !userPrompt.isBlank()) {
            effectivePrompt = userPrompt.trim();
        } else if (entry != null && entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            effectivePrompt = String.join(", ", entry.triggerWords());
        } else {
            effectivePrompt = "";
        }

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode links = objectMapper.createArrayNode();

        long seed = Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L));

        if (!isLora) {
            buildCheckpointGraph(nodes, links, baseModelFilename, effectivePrompt, width, height, steps, cfg, samplerName, scheduler, seed);
            root.put("last_node_id", 9);
            root.put("last_link_id", 9);
        } else {
            buildLoraGraph(nodes, links, baseModelFilename, primaryFilename, effectivePrompt, width, height, steps, cfg, samplerName, scheduler, seed);
            root.put("last_node_id", 10);
            root.put("last_link_id", 12);
        }

        root.set("nodes", nodes);
        root.set("links", links);
        root.set("groups", objectMapper.createArrayNode());
        root.set("config", objectMapper.createObjectNode());

        ObjectNode extra = objectMapper.createObjectNode();
        ObjectNode ds = objectMapper.createObjectNode();
        ds.put("scale", 1.0);
        ArrayNode offset = objectMapper.createArrayNode();
        offset.add(0);
        offset.add(0);
        ds.set("offset", offset);
        extra.set("ds", ds);
        root.set("extra", extra);

        root.put("version", 0.4);

        log.debug("Synthesized LiteGraph GUI workflow for model {} (isLora={})", primaryFilename, isLora);
        return root;
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

    private void buildCheckpointGraph(ArrayNode nodes, ArrayNode links, String ckptName, String starterPrompt,
                                      int width, int height, int steps, double cfg, String sampler, String scheduler, long seed) {
        // Node 4: CheckpointLoaderSimple
        ObjectNode n4 = nodeFactory.createNode(4, "CheckpointLoaderSimple", 40, 180, 315, 98, 0);
        ArrayNode n4Outputs = objectMapper.createArrayNode();
        n4Outputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
        n4Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 1, 2, 3));
        n4Outputs.add(nodeFactory.createOutput("VAE", "VAE", 2, 7));
        n4.set("outputs", n4Outputs);
        ArrayNode n4Widgets = objectMapper.createArrayNode();
        n4Widgets.add(ckptName);
        n4.set("widgets_values", n4Widgets);
        nodes.add(n4);

        // Node 6: Positive Prompt
        nodes.add(nodeFactory.createPromptNode(6, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 415, 100, 2, 4, starterPrompt, 1));

        // Node 7: Negative Prompt
        nodes.add(nodeFactory.createPromptNode(7, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 415, 340, 3, 5, "text, watermark, blurry, low quality, bad anatomy", 2));

        // Node 5: EmptyLatentImage
        nodes.add(nodeFactory.createEmptyLatentNode(5, 415, 580, width, height, 1, 6, 3));

        // Node 3: KSampler
        ObjectNode n3 = nodeFactory.createNode(3, "KSampler", 880, 150, 315, 262, 4);
        ArrayNode n3Inputs = objectMapper.createArrayNode();
        n3Inputs.add(nodeFactory.createInput("model", "MODEL", 1));
        n3Inputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
        n3Inputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
        n3Inputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
        n3.set("inputs", n3Inputs);
        ArrayNode n3Outputs = objectMapper.createArrayNode();
        n3Outputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 8));
        n3.set("outputs", n3Outputs);
        ArrayNode n3Widgets = objectMapper.createArrayNode();
        n3Widgets.add(seed);
        n3Widgets.add("randomize");
        n3Widgets.add(steps);
        n3Widgets.add(cfg);
        n3Widgets.add(sampler);
        n3Widgets.add(scheduler);
        n3Widgets.add(1.0);
        n3.set("widgets_values", n3Widgets);
        nodes.add(n3);

        // Node 8: VAEDecode
        nodes.add(nodeFactory.createVaeDecodeNode(8, 1240, 150, 8, 7, 9, 5));

        // Node 9: SaveImage
        nodes.add(nodeFactory.createSaveImageNode(9, 1490, 150, 9, "ComfyUI", 6));

        // Links
        links.add(nodeFactory.createLink(1, 4, 0, 3, 0, "MODEL"));
        links.add(nodeFactory.createLink(2, 4, 1, 6, 0, "CLIP"));
        links.add(nodeFactory.createLink(3, 4, 1, 7, 0, "CLIP"));
        links.add(nodeFactory.createLink(4, 6, 0, 3, 1, "CONDITIONING"));
        links.add(nodeFactory.createLink(5, 7, 0, 3, 2, "CONDITIONING"));
        links.add(nodeFactory.createLink(6, 5, 0, 3, 3, "LATENT"));
        links.add(nodeFactory.createLink(7, 4, 2, 8, 1, "VAE"));
        links.add(nodeFactory.createLink(8, 3, 0, 8, 0, "LATENT"));
        links.add(nodeFactory.createLink(9, 8, 0, 9, 0, "IMAGE"));
    }

    private void buildLoraGraph(ArrayNode nodes, ArrayNode links, String ckptName, String loraName, String starterPrompt,
                                int width, int height, int steps, double cfg, String sampler, String scheduler, long seed) {
        // Node 4: CheckpointLoaderSimple
        ObjectNode n4 = nodeFactory.createNode(4, "CheckpointLoaderSimple", 40, 140, 315, 98, 0);
        ArrayNode n4Outputs = objectMapper.createArrayNode();
        n4Outputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
        n4Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 1, 2));
        n4Outputs.add(nodeFactory.createOutput("VAE", "VAE", 2, 7));
        n4.set("outputs", n4Outputs);
        ArrayNode n4Widgets = objectMapper.createArrayNode();
        n4Widgets.add(ckptName);
        n4.set("widgets_values", n4Widgets);
        nodes.add(n4);

        // Node 10: LoraLoader
        ObjectNode n10 = nodeFactory.createNode(10, "LoraLoader", 40, 320, 315, 126, 1);
        ArrayNode n10Inputs = objectMapper.createArrayNode();
        n10Inputs.add(nodeFactory.createInput("model", "MODEL", 1));
        n10Inputs.add(nodeFactory.createInput("clip", "CLIP", 2));
        n10.set("inputs", n10Inputs);
        ArrayNode n10Outputs = objectMapper.createArrayNode();
        n10Outputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 10));
        n10Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 1, 11, 12));
        n10.set("outputs", n10Outputs);
        ArrayNode n10Widgets = objectMapper.createArrayNode();
        n10Widgets.add(loraName);
        n10Widgets.add(1.0); // strength_model
        n10Widgets.add(1.0); // strength_clip
        n10.set("widgets_values", n10Widgets);
        nodes.add(n10);

        // Node 6: Positive Prompt
        nodes.add(nodeFactory.createPromptNode(6, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 415, 100, 11, 4, starterPrompt, 2));

        // Node 7: Negative Prompt
        nodes.add(nodeFactory.createPromptNode(7, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 415, 340, 12, 5, "text, watermark, blurry, low quality, bad anatomy", 3));

        // Node 5: EmptyLatentImage
        nodes.add(nodeFactory.createEmptyLatentNode(5, 415, 580, width, height, 1, 6, 4));

        // Node 3: KSampler
        ObjectNode n3 = nodeFactory.createNode(3, "KSampler", 880, 150, 315, 262, 5);
        ArrayNode n3Inputs = objectMapper.createArrayNode();
        n3Inputs.add(nodeFactory.createInput("model", "MODEL", 10));
        n3Inputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
        n3Inputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
        n3Inputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
        n3.set("inputs", n3Inputs);
        ArrayNode n3Outputs = objectMapper.createArrayNode();
        n3Outputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 8));
        n3.set("outputs", n3Outputs);
        ArrayNode n3Widgets = objectMapper.createArrayNode();
        n3Widgets.add(seed);
        n3Widgets.add("randomize");
        n3Widgets.add(steps);
        n3Widgets.add(cfg);
        n3Widgets.add(sampler);
        n3Widgets.add(scheduler);
        n3Widgets.add(1.0);
        n3.set("widgets_values", n3Widgets);
        nodes.add(n3);

        // Node 8: VAEDecode
        nodes.add(nodeFactory.createVaeDecodeNode(8, 1240, 150, 8, 7, 9, 6));

        // Node 9: SaveImage
        nodes.add(nodeFactory.createSaveImageNode(9, 1490, 150, 9, "ComfyUI", 7));

        // Links
        links.add(nodeFactory.createLink(1, 4, 0, 10, 0, "MODEL"));
        links.add(nodeFactory.createLink(2, 4, 1, 10, 1, "CLIP"));
        links.add(nodeFactory.createLink(4, 6, 0, 3, 1, "CONDITIONING"));
        links.add(nodeFactory.createLink(5, 7, 0, 3, 2, "CONDITIONING"));
        links.add(nodeFactory.createLink(6, 5, 0, 3, 3, "LATENT"));
        links.add(nodeFactory.createLink(7, 4, 2, 8, 1, "VAE"));
        links.add(nodeFactory.createLink(8, 3, 0, 8, 0, "LATENT"));
        links.add(nodeFactory.createLink(9, 8, 0, 9, 0, "IMAGE"));
        links.add(nodeFactory.createLink(10, 10, 0, 3, 0, "MODEL"));
        links.add(nodeFactory.createLink(11, 10, 1, 6, 0, "CLIP"));
        links.add(nodeFactory.createLink(12, 10, 1, 7, 0, "CLIP"));
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
