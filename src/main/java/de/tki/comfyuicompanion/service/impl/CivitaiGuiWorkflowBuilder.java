package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * Constructs a new CivitaiGuiWorkflowBuilder with the specified Jackson {@link ObjectMapper}.
     *
     * @param objectMapper the object mapper for creating JSON graph trees
     */
    @Autowired
    public CivitaiGuiWorkflowBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Builds a LiteGraph-compatible GUI workflow {@link ObjectNode} for the specified Civitai model entry.
     *
     * @param entry          the Civitai model entry
     * @param requiredModels the list of operational models resolved for this entry
     * @return an {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildGuiWorkflow(CivitaiModelEntry entry, List<ModelInfo> requiredModels) {
        String typeLower = entry != null && entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";
        String baseLower = entry != null && entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";
        boolean isLora = typeLower.contains("lora") || typeLower.contains("locon");

        // Determine base model filename and target lora filename
        String primaryFilename = entry != null && entry.filename() != null ? entry.filename() : "model.safetensors";
        String baseModelFilename = primaryFilename;

        if (isLora && requiredModels != null) {
            for (ModelInfo info : requiredModels) {
                String itype = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
                if (itype.contains("checkpoint") || itype.contains("unet") || itype.contains("diffusion")) {
                    baseModelFilename = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
                    break;
                }
            }
        }

        // Architecture-specific dimension and sampling defaults
        int width = 1024;
        int height = 1024;
        int steps = 20;
        double cfg = 7.0;
        String samplerName = "euler";
        String scheduler = "normal";

        if (baseLower.contains("1.5") || baseLower.contains("sd 1.5")) {
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

        // Positive prompt starter (pre-fill trigger words if present)
        String starterPrompt = "";
        if (entry != null && entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            starterPrompt = String.join(", ", entry.triggerWords());
        }

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode links = objectMapper.createArrayNode();

        long seed = Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L));

        if (!isLora) {
            buildCheckpointGraph(nodes, links, baseModelFilename, starterPrompt, width, height, steps, cfg, samplerName, scheduler, seed);
            root.put("last_node_id", 9);
            root.put("last_link_id", 9);
        } else {
            buildLoraGraph(nodes, links, baseModelFilename, primaryFilename, starterPrompt, width, height, steps, cfg, samplerName, scheduler, seed);
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
        try {
            ObjectNode root = buildGuiWorkflow(entry, requiredModels);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize GUI workflow JSON: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private void buildCheckpointGraph(ArrayNode nodes, ArrayNode links, String ckptName, String starterPrompt,
                                      int width, int height, int steps, double cfg, String sampler, String scheduler, long seed) {
        // Node 4: CheckpointLoaderSimple
        ObjectNode n4 = createNode(4, "CheckpointLoaderSimple", 40, 180, 315, 98, 0);
        ArrayNode n4Outputs = objectMapper.createArrayNode();
        n4Outputs.add(createOutput("MODEL", "MODEL", 0, 1));
        n4Outputs.add(createOutput("CLIP", "CLIP", 1, 2, 3));
        n4Outputs.add(createOutput("VAE", "VAE", 2, 7));
        n4.set("outputs", n4Outputs);
        ArrayNode n4Widgets = objectMapper.createArrayNode();
        n4Widgets.add(ckptName);
        n4.set("widgets_values", n4Widgets);
        nodes.add(n4);

        // Node 6: Positive Prompt
        ObjectNode n6 = createNode(6, "CLIPTextEncode", 415, 100, 425, 180, 1);
        n6.put("title", "CLIP Text Encode (Positive Prompt)");
        n6.put("color", "#232");
        n6.put("bgcolor", "#353");
        ArrayNode n6Inputs = objectMapper.createArrayNode();
        n6Inputs.add(createInput("clip", "CLIP", 2));
        n6.set("inputs", n6Inputs);
        ArrayNode n6Outputs = objectMapper.createArrayNode();
        n6Outputs.add(createOutput("CONDITIONING", "CONDITIONING", 0, 4));
        n6.set("outputs", n6Outputs);
        ArrayNode n6Widgets = objectMapper.createArrayNode();
        n6Widgets.add(starterPrompt);
        n6.set("widgets_values", n6Widgets);
        nodes.add(n6);

        // Node 7: Negative Prompt
        ObjectNode n7 = createNode(7, "CLIPTextEncode", 415, 340, 425, 180, 2);
        n7.put("title", "CLIP Text Encode (Negative Prompt)");
        n7.put("color", "#322");
        n7.put("bgcolor", "#533");
        ArrayNode n7Inputs = objectMapper.createArrayNode();
        n7Inputs.add(createInput("clip", "CLIP", 3));
        n7.set("inputs", n7Inputs);
        ArrayNode n7Outputs = objectMapper.createArrayNode();
        n7Outputs.add(createOutput("CONDITIONING", "CONDITIONING", 0, 5));
        n7.set("outputs", n7Outputs);
        ArrayNode n7Widgets = objectMapper.createArrayNode();
        n7Widgets.add("text, watermark, blurry, low quality, bad anatomy");
        n7.set("widgets_values", n7Widgets);
        nodes.add(n7);

        // Node 5: EmptyLatentImage
        ObjectNode n5 = createNode(5, "EmptyLatentImage", 415, 580, 315, 106, 3);
        ArrayNode n5Outputs = objectMapper.createArrayNode();
        n5Outputs.add(createOutput("LATENT", "LATENT", 0, 6));
        n5.set("outputs", n5Outputs);
        ArrayNode n5Widgets = objectMapper.createArrayNode();
        n5Widgets.add(width);
        n5Widgets.add(height);
        n5Widgets.add(1);
        n5.set("widgets_values", n5Widgets);
        nodes.add(n5);

        // Node 3: KSampler
        ObjectNode n3 = createNode(3, "KSampler", 880, 150, 315, 262, 4);
        ArrayNode n3Inputs = objectMapper.createArrayNode();
        n3Inputs.add(createInput("model", "MODEL", 1));
        n3Inputs.add(createInput("positive", "CONDITIONING", 4));
        n3Inputs.add(createInput("negative", "CONDITIONING", 5));
        n3Inputs.add(createInput("latent_image", "LATENT", 6));
        n3.set("inputs", n3Inputs);
        ArrayNode n3Outputs = objectMapper.createArrayNode();
        n3Outputs.add(createOutput("LATENT", "LATENT", 0, 8));
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
        ObjectNode n8 = createNode(8, "VAEDecode", 1240, 150, 210, 46, 5);
        ArrayNode n8Inputs = objectMapper.createArrayNode();
        n8Inputs.add(createInput("samples", "LATENT", 8));
        n8Inputs.add(createInput("vae", "VAE", 7));
        n8.set("inputs", n8Inputs);
        ArrayNode n8Outputs = objectMapper.createArrayNode();
        n8Outputs.add(createOutput("IMAGE", "IMAGE", 0, 9));
        n8.set("outputs", n8Outputs);
        n8.set("widgets_values", objectMapper.createArrayNode());
        nodes.add(n8);

        // Node 9: SaveImage
        ObjectNode n9 = createNode(9, "SaveImage", 1490, 150, 500, 500, 6);
        ArrayNode n9Inputs = objectMapper.createArrayNode();
        n9Inputs.add(createInput("images", "IMAGE", 9));
        n9.set("inputs", n9Inputs);
        n9.set("outputs", objectMapper.createArrayNode());
        ArrayNode n9Widgets = objectMapper.createArrayNode();
        n9Widgets.add("ComfyUI");
        n9.set("widgets_values", n9Widgets);
        nodes.add(n9);

        // Links
        links.add(createLink(1, 4, 0, 3, 0, "MODEL"));
        links.add(createLink(2, 4, 1, 6, 0, "CLIP"));
        links.add(createLink(3, 4, 1, 7, 0, "CLIP"));
        links.add(createLink(4, 6, 0, 3, 1, "CONDITIONING"));
        links.add(createLink(5, 7, 0, 3, 2, "CONDITIONING"));
        links.add(createLink(6, 5, 0, 3, 3, "LATENT"));
        links.add(createLink(7, 4, 2, 8, 1, "VAE"));
        links.add(createLink(8, 3, 0, 8, 0, "LATENT"));
        links.add(createLink(9, 8, 0, 9, 0, "IMAGE"));
    }

    private void buildLoraGraph(ArrayNode nodes, ArrayNode links, String ckptName, String loraName, String starterPrompt,
                                int width, int height, int steps, double cfg, String sampler, String scheduler, long seed) {
        // Node 4: CheckpointLoaderSimple
        ObjectNode n4 = createNode(4, "CheckpointLoaderSimple", 40, 140, 315, 98, 0);
        ArrayNode n4Outputs = objectMapper.createArrayNode();
        n4Outputs.add(createOutput("MODEL", "MODEL", 0, 1));
        n4Outputs.add(createOutput("CLIP", "CLIP", 1, 2));
        n4Outputs.add(createOutput("VAE", "VAE", 2, 7));
        n4.set("outputs", n4Outputs);
        ArrayNode n4Widgets = objectMapper.createArrayNode();
        n4Widgets.add(ckptName);
        n4.set("widgets_values", n4Widgets);
        nodes.add(n4);

        // Node 10: LoraLoader
        ObjectNode n10 = createNode(10, "LoraLoader", 40, 320, 315, 126, 1);
        ArrayNode n10Inputs = objectMapper.createArrayNode();
        n10Inputs.add(createInput("model", "MODEL", 1));
        n10Inputs.add(createInput("clip", "CLIP", 2));
        n10.set("inputs", n10Inputs);
        ArrayNode n10Outputs = objectMapper.createArrayNode();
        n10Outputs.add(createOutput("MODEL", "MODEL", 0, 10));
        n10Outputs.add(createOutput("CLIP", "CLIP", 1, 11, 12));
        n10.set("outputs", n10Outputs);
        ArrayNode n10Widgets = objectMapper.createArrayNode();
        n10Widgets.add(loraName);
        n10Widgets.add(1.0); // strength_model
        n10Widgets.add(1.0); // strength_clip
        n10.set("widgets_values", n10Widgets);
        nodes.add(n10);

        // Node 6: Positive Prompt
        ObjectNode n6 = createNode(6, "CLIPTextEncode", 415, 100, 425, 180, 2);
        n6.put("title", "CLIP Text Encode (Positive Prompt)");
        n6.put("color", "#232");
        n6.put("bgcolor", "#353");
        ArrayNode n6Inputs = objectMapper.createArrayNode();
        n6Inputs.add(createInput("clip", "CLIP", 11));
        n6.set("inputs", n6Inputs);
        ArrayNode n6Outputs = objectMapper.createArrayNode();
        n6Outputs.add(createOutput("CONDITIONING", "CONDITIONING", 0, 4));
        n6.set("outputs", n6Outputs);
        ArrayNode n6Widgets = objectMapper.createArrayNode();
        n6Widgets.add(starterPrompt);
        n6.set("widgets_values", n6Widgets);
        nodes.add(n6);

        // Node 7: Negative Prompt
        ObjectNode n7 = createNode(7, "CLIPTextEncode", 415, 340, 425, 180, 3);
        n7.put("title", "CLIP Text Encode (Negative Prompt)");
        n7.put("color", "#322");
        n7.put("bgcolor", "#533");
        ArrayNode n7Inputs = objectMapper.createArrayNode();
        n7Inputs.add(createInput("clip", "CLIP", 12));
        n7.set("inputs", n7Inputs);
        ArrayNode n7Outputs = objectMapper.createArrayNode();
        n7Outputs.add(createOutput("CONDITIONING", "CONDITIONING", 0, 5));
        n7.set("outputs", n7Outputs);
        ArrayNode n7Widgets = objectMapper.createArrayNode();
        n7Widgets.add("text, watermark, blurry, low quality, bad anatomy");
        n7.set("widgets_values", n7Widgets);
        nodes.add(n7);

        // Node 5: EmptyLatentImage
        ObjectNode n5 = createNode(5, "EmptyLatentImage", 415, 580, 315, 106, 4);
        ArrayNode n5Outputs = objectMapper.createArrayNode();
        n5Outputs.add(createOutput("LATENT", "LATENT", 0, 6));
        n5.set("outputs", n5Outputs);
        ArrayNode n5Widgets = objectMapper.createArrayNode();
        n5Widgets.add(width);
        n5Widgets.add(height);
        n5Widgets.add(1);
        n5.set("widgets_values", n5Widgets);
        nodes.add(n5);

        // Node 3: KSampler
        ObjectNode n3 = createNode(3, "KSampler", 880, 150, 315, 262, 5);
        ArrayNode n3Inputs = objectMapper.createArrayNode();
        n3Inputs.add(createInput("model", "MODEL", 10));
        n3Inputs.add(createInput("positive", "CONDITIONING", 4));
        n3Inputs.add(createInput("negative", "CONDITIONING", 5));
        n3Inputs.add(createInput("latent_image", "LATENT", 6));
        n3.set("inputs", n3Inputs);
        ArrayNode n3Outputs = objectMapper.createArrayNode();
        n3Outputs.add(createOutput("LATENT", "LATENT", 0, 8));
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
        ObjectNode n8 = createNode(8, "VAEDecode", 1240, 150, 210, 46, 6);
        ArrayNode n8Inputs = objectMapper.createArrayNode();
        n8Inputs.add(createInput("samples", "LATENT", 8));
        n8Inputs.add(createInput("vae", "VAE", 7));
        n8.set("inputs", n8Inputs);
        ArrayNode n8Outputs = objectMapper.createArrayNode();
        n8Outputs.add(createOutput("IMAGE", "IMAGE", 0, 9));
        n8.set("outputs", n8Outputs);
        n8.set("widgets_values", objectMapper.createArrayNode());
        nodes.add(n8);

        // Node 9: SaveImage
        ObjectNode n9 = createNode(9, "SaveImage", 1490, 150, 500, 500, 7);
        ArrayNode n9Inputs = objectMapper.createArrayNode();
        n9Inputs.add(createInput("images", "IMAGE", 9));
        n9.set("inputs", n9Inputs);
        n9.set("outputs", objectMapper.createArrayNode());
        ArrayNode n9Widgets = objectMapper.createArrayNode();
        n9Widgets.add("ComfyUI");
        n9.set("widgets_values", n9Widgets);
        nodes.add(n9);

        // Links
        links.add(createLink(1, 4, 0, 10, 0, "MODEL"));
        links.add(createLink(2, 4, 1, 10, 1, "CLIP"));
        links.add(createLink(4, 6, 0, 3, 1, "CONDITIONING"));
        links.add(createLink(5, 7, 0, 3, 2, "CONDITIONING"));
        links.add(createLink(6, 5, 0, 3, 3, "LATENT"));
        links.add(createLink(7, 4, 2, 8, 1, "VAE"));
        links.add(createLink(8, 3, 0, 8, 0, "LATENT"));
        links.add(createLink(9, 8, 0, 9, 0, "IMAGE"));
        links.add(createLink(10, 10, 0, 3, 0, "MODEL"));
        links.add(createLink(11, 10, 1, 6, 0, "CLIP"));
        links.add(createLink(12, 10, 1, 7, 0, "CLIP"));
    }

    private ObjectNode createNode(int id, String type, double x, double y, double width, double height, int order) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);

        ArrayNode pos = objectMapper.createArrayNode();
        pos.add(x);
        pos.add(y);
        node.set("pos", pos);

        ArrayNode size = objectMapper.createArrayNode();
        size.add(width);
        size.add(height);
        node.set("size", size);

        node.set("flags", objectMapper.createObjectNode());
        node.put("order", order);
        node.put("mode", 0);
        node.set("inputs", objectMapper.createArrayNode());
        node.set("outputs", objectMapper.createArrayNode());

        ObjectNode props = objectMapper.createObjectNode();
        props.put("Node name for S&R", type);
        node.set("properties", props);

        return node;
    }

    private ObjectNode createInput(String name, String type, int link) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("name", name);
        input.put("type", type);
        input.put("link", link);
        return input;
    }

    private ObjectNode createOutput(String name, String type, int slotIndex, int... linkIds) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("name", name);
        output.put("type", type);
        output.put("slot_index", slotIndex);
        ArrayNode links = objectMapper.createArrayNode();
        for (int link : linkIds) {
            links.add(link);
        }
        output.set("links", links);
        return output;
    }

    private ArrayNode createLink(int linkId, int originId, int originSlot, int targetId, int targetSlot, String type) {
        ArrayNode link = objectMapper.createArrayNode();
        link.add(linkId);
        link.add(originId);
        link.add(originSlot);
        link.add(targetId);
        link.add(targetSlot);
        link.add(type);
        return link;
    }
}
