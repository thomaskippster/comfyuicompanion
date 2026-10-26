package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enterprise workflow synthesis service constructing LiteGraph visual canvas graphs
 * and runnable ComfyUI API prompts for models downloaded from Hugging Face Hub.
 */
@Component
public class HuggingFaceWorkflowBuilder {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceWorkflowBuilder.class);

    private final ObjectMapper objectMapper;
    private final ComfyGraphNodeFactory nodeFactory;

    /**
     * Constructs a new HuggingFaceWorkflowBuilder with required dependencies.
     *
     * @param objectMapper Jackson object mapper
     * @param nodeFactory  factory for creating low-level LiteGraph nodes and links
     */
    @Autowired
    public HuggingFaceWorkflowBuilder(
            ObjectMapper objectMapper,
            @Autowired(required = false) ComfyGraphNodeFactory nodeFactory) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.nodeFactory = nodeFactory != null ? nodeFactory : new ComfyGraphNodeFactory(this.objectMapper);
    }

    /**
     * Convenience constructor supporting callers with single argument.
     *
     * @param objectMapper Jackson object mapper
     */
    public HuggingFaceWorkflowBuilder(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    /**
     * Builds a LiteGraph-compatible GUI workflow JSON structure for the specified model and file.
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the specific model file selected
     * @param userPrompt   optional prompt text entered by the user
     * @return {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildGuiWorkflow(HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile, String userPrompt) {
        String filename = selectedFile != null ? selectedFile.filename() : "model.safetensors";
        String repoName = (repo != null ? repo.repoId() + " " + repo.modelName() : filename).toLowerCase(Locale.ROOT);
        boolean isGguf = filename.toLowerCase(Locale.ROOT).endsWith(".gguf");
        boolean isFlux = repoName.contains("flux") || isGguf;

        String prompt = (userPrompt != null && !userPrompt.isBlank())
                ? userPrompt.trim()
                : (repo != null ? repo.modelName() : "Masterpiece") + ", 8k resolution, highly detailed, photorealistic";

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode links = objectMapper.createArrayNode();

        long seed = Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L));

        if (isFlux) {
            buildFluxGraph(nodes, links, filename, isGguf, prompt, seed);
            root.put("last_node_id", 9);
            root.put("last_link_id", 10);
        } else {
            buildStandardCheckpointGraph(nodes, links, filename, prompt, seed);
            root.put("last_node_id", 7);
            root.put("last_link_id", 7);
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

        log.debug("Synthesized LiteGraph GUI workflow for HF model: {}", filename);
        return root;
    }

    private void buildFluxGraph(ArrayNode nodes, ArrayNode links, String filename, boolean isGguf, String prompt, long seed) {
        // Node 1: Model Loader
        String loaderType = isGguf ? "UnetLoaderGGUF" : "UNETLoader";
        ObjectNode loader = nodeFactory.createNode(1, loaderType, 50, 150, 310, 100, 1);
        ArrayNode loaderOutputs = objectMapper.createArrayNode();
        loaderOutputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
        loader.set("outputs", loaderOutputs);
        ArrayNode loaderWidgets = objectMapper.createArrayNode();
        loaderWidgets.add(filename);
        loader.set("widgets_values", loaderWidgets);
        nodes.add(loader);

        // Node 2: DualCLIPLoader
        ObjectNode clip = nodeFactory.createNode(2, "DualCLIPLoader", 50, 300, 310, 110, 2);
        ArrayNode clipOutputs = objectMapper.createArrayNode();
        clipOutputs.add(nodeFactory.createOutput("CLIP", "CLIP", 0, 2));
        clip.set("outputs", clipOutputs);
        ArrayNode clipWidgets = objectMapper.createArrayNode();
        clipWidgets.add("t5xxl_fp8_e4m3fn.safetensors");
        clipWidgets.add("clip_l.safetensors");
        clipWidgets.add("flux");
        clip.set("widgets_values", clipWidgets);
        nodes.add(clip);

        // Node 3: VAELoader
        ObjectNode vae = nodeFactory.createNode(3, "VAELoader", 50, 460, 310, 80, 3);
        ArrayNode vaeOutputs = objectMapper.createArrayNode();
        vaeOutputs.add(nodeFactory.createOutput("VAE", "VAE", 0, 3));
        vae.set("outputs", vaeOutputs);
        ArrayNode vaeWidgets = objectMapper.createArrayNode();
        vaeWidgets.add("ae.safetensors");
        vae.set("widgets_values", vaeWidgets);
        nodes.add(vae);

        // Node 4: Positive Prompt
        nodes.add(nodeFactory.createPromptNode(4, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 420, 150, 2, 4, prompt, 4));

        // Node 5: Negative Prompt
        nodes.add(nodeFactory.createPromptNode(5, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 420, 380, 2, 5, "", 5));

        // Node 6: Empty Latent
        nodes.add(nodeFactory.createEmptyLatentNode(6, 420, 580, 1024, 1024, 1, 6, 6));

        // Node 7: KSampler
        ObjectNode sampler = nodeFactory.createNode(7, "KSampler", 900, 150, 310, 260, 7);
        ArrayNode sampInputs = objectMapper.createArrayNode();
        sampInputs.add(nodeFactory.createInput("model", "MODEL", 1));
        sampInputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
        sampInputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
        sampInputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
        sampler.set("inputs", sampInputs);
        ArrayNode sampOutputs = objectMapper.createArrayNode();
        sampOutputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 7));
        sampler.set("outputs", sampOutputs);

        ArrayNode sampWidgets = objectMapper.createArrayNode();
        sampWidgets.add(seed);
        sampWidgets.add("randomize");
        sampWidgets.add(20);
        sampWidgets.add(1.0);
        sampWidgets.add("euler");
        sampWidgets.add("simple");
        sampWidgets.add(1.0);
        sampler.set("widgets_values", sampWidgets);
        nodes.add(sampler);

        // Node 8: VAEDecode
        nodes.add(nodeFactory.createVaeDecodeNode(8, 1260, 150, 7, 3, 8, 8));

        // Node 9: SaveImage
        nodes.add(nodeFactory.createSaveImageNode(9, 1520, 150, 8, "ComfyUI_HF_Flux", 9));

        // Link mappings
        links.add(nodeFactory.createLink(1, 1, 0, 7, 0, "MODEL"));
        links.add(nodeFactory.createLink(2, 2, 0, 4, 0, "CLIP"));
        links.add(nodeFactory.createLink(3, 3, 0, 8, 1, "VAE"));
        links.add(nodeFactory.createLink(4, 4, 0, 7, 1, "CONDITIONING"));
        links.add(nodeFactory.createLink(5, 5, 0, 7, 2, "CONDITIONING"));
        links.add(nodeFactory.createLink(6, 6, 0, 7, 3, "LATENT"));
        links.add(nodeFactory.createLink(7, 7, 0, 8, 0, "LATENT"));
        links.add(nodeFactory.createLink(8, 8, 0, 9, 0, "IMAGE"));
    }

    private void buildStandardCheckpointGraph(ArrayNode nodes, ArrayNode links, String filename, String prompt, long seed) {
        // Node 4: CheckpointLoaderSimple
        ObjectNode loader = nodeFactory.createNode(4, "CheckpointLoaderSimple", 50, 150, 310, 100, 1);
        ArrayNode loaderOutputs = objectMapper.createArrayNode();
        loaderOutputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
        loaderOutputs.add(nodeFactory.createOutput("CLIP", "CLIP", 1, 2));
        loaderOutputs.add(nodeFactory.createOutput("VAE", "VAE", 2, 3));
        loader.set("outputs", loaderOutputs);
        ArrayNode loaderWidgets = objectMapper.createArrayNode();
        loaderWidgets.add(filename);
        loader.set("widgets_values", loaderWidgets);
        nodes.add(loader);

        // Node 6: Positive Prompt
        nodes.add(nodeFactory.createPromptNode(6, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 420, 150, 2, 4, prompt, 2));

        // Node 7: Negative Prompt
        nodes.add(nodeFactory.createPromptNode(7, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 420, 380, 2, 5, "blurry, low quality, deformed, extra limbs", 3));

        // Node 5: EmptyLatentImage
        nodes.add(nodeFactory.createEmptyLatentNode(5, 420, 580, 1024, 1024, 1, 6, 4));

        // Node 3: KSampler
        ObjectNode sampler = nodeFactory.createNode(3, "KSampler", 900, 150, 310, 260, 5);
        ArrayNode sampInputs = objectMapper.createArrayNode();
        sampInputs.add(nodeFactory.createInput("model", "MODEL", 1));
        sampInputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
        sampInputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
        sampInputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
        sampler.set("inputs", sampInputs);
        ArrayNode sampOutputs = objectMapper.createArrayNode();
        sampOutputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 7));
        sampler.set("outputs", sampOutputs);

        ArrayNode sampWidgets = objectMapper.createArrayNode();
        sampWidgets.add(seed);
        sampWidgets.add("randomize");
        sampWidgets.add(20);
        sampWidgets.add(7.0);
        sampWidgets.add("euler");
        sampWidgets.add("normal");
        sampWidgets.add(1.0);
        sampler.set("widgets_values", sampWidgets);
        nodes.add(sampler);

        // Node 8: VAEDecode
        nodes.add(nodeFactory.createVaeDecodeNode(8, 1260, 150, 7, 3, 8, 6));

        // Node 9: SaveImage
        nodes.add(nodeFactory.createSaveImageNode(9, 1520, 150, 8, "ComfyUI_HF_Output", 7));

        // Links
        links.add(nodeFactory.createLink(1, 4, 0, 3, 0, "MODEL"));
        links.add(nodeFactory.createLink(2, 4, 1, 6, 0, "CLIP"));
        links.add(nodeFactory.createLink(3, 4, 2, 8, 1, "VAE"));
        links.add(nodeFactory.createLink(4, 6, 0, 3, 1, "CONDITIONING"));
        links.add(nodeFactory.createLink(5, 7, 0, 3, 2, "CONDITIONING"));
        links.add(nodeFactory.createLink(6, 5, 0, 3, 3, "LATENT"));
        links.add(nodeFactory.createLink(7, 3, 0, 8, 0, "LATENT"));
        links.add(nodeFactory.createLink(8, 8, 0, 9, 0, "IMAGE"));
    }

    /**
     * Synthesizes an executable ComfyUI API workflow JSON payload for direct remote generation.
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the selected model file
     * @param userPrompt   custom positive prompt
     * @return runnable JSON string payload for ComfyUI /prompt endpoint
     */
    public String buildApiWorkflowJson(HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile, String userPrompt) {
        String filename = selectedFile != null ? selectedFile.filename() : "model.safetensors";
        String repoName = (repo != null ? repo.repoId() + " " + repo.modelName() : filename).toLowerCase(Locale.ROOT);
        boolean isFlux = repoName.contains("flux") || filename.endsWith(".gguf");

        String templateName = isFlux ? "template_flux_api.json" : "template_sdxl_api.json";
        String rawTemplate = DefaultComfyTemplates.getDefaultTemplateForName(templateName);

        try {
            JsonNode root = objectMapper.readTree(rawTemplate);
            ObjectNode promptObj = root.has("prompt") ? (ObjectNode) root.get("prompt") : (ObjectNode) root;

            Iterator<String> nodeIds = promptObj.fieldNames();
            while (nodeIds.hasNext()) {
                String id = nodeIds.next();
                JsonNode node = promptObj.get(id);
                String classType = node.path("class_type").asText("");
                ObjectNode inputs = (ObjectNode) node.path("inputs");

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", filename);
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType) || "UnetLoaderGGUF".equals(classType)) {
                    inputs.put("unet_name", filename);
                } else if ("CLIPTextEncode".equals(classType)) {
                    if (inputs.has("text") && !inputs.get("text").asText("").contains("bad anatomy")) {
                        inputs.put("text", userPrompt != null && !userPrompt.isBlank() ? userPrompt : "Masterpiece, 8k resolution, highly detailed");
                    }
                } else if ("KSampler".equals(classType)) {
                    inputs.put("seed", Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L)));
                }
            }

            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set("prompt", promptObj);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapped);
        } catch (Exception e) {
            log.error("Failed building API workflow JSON for HF model: {}", e.getMessage(), e);
            return rawTemplate;
        }
    }
}
