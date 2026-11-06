package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.ComfyGraphNodeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Enterprise generic workflow builder synthesizing standardized LiteGraph visual canvas workflows
 * driven by declarative {@link ArchitectureProfile} records.
 * Supports standard checkpoints, modular UNETs, and optional LoRA layering.
 */
@Component
public class GenericWorkflowBuilder {

    private final ObjectMapper objectMapper;
    private final ComfyGraphNodeFactory nodeFactory;

    @Autowired
    public GenericWorkflowBuilder(ObjectMapper objectMapper, ComfyGraphNodeFactory nodeFactory) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.nodeFactory = nodeFactory != null ? nodeFactory : new ComfyGraphNodeFactory(this.objectMapper);
    }

    public GenericWorkflowBuilder(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    /**
     * Synthesizes a standard visual canvas workflow for a base model.
     *
     * @param profile    the architectural profile
     * @param modelInfo  target model information
     * @param userPrompt optional custom user prompt
     * @return root {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildWorkflow(ArchitectureProfile profile, ModelInfo modelInfo, String userPrompt) {
        return buildWorkflow(profile, modelInfo, null, userPrompt);
    }

    /**
     * Synthesizes a standard visual canvas workflow with an optional LoRA layer.
     *
     * @param profile    the architectural profile
     * @param modelInfo  target model information
     * @param loraName   optional LoRA model filename to insert
     * @param userPrompt optional custom user prompt
     * @return root {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildWorkflow(ArchitectureProfile profile, ModelInfo modelInfo, String loraName, String userPrompt) {
        String prompt = (userPrompt != null)
                ? userPrompt.trim()
                : "masterpiece, high quality, 8k";
        String negPrompt = (profile.defaultNegativePrompt() != null && !profile.defaultNegativePrompt().isBlank())
                ? profile.defaultNegativePrompt()
                : "text, watermark, blurry, low quality, bad anatomy";

        long seed = Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L));
        boolean hasLora = loraName != null && !loraName.isBlank();

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode links = objectMapper.createArrayNode();

        if (profile.loaderTopology() == LoaderTopology.CHECKPOINT_SIMPLE) {
            String ckptName = resolveModelFilename(modelInfo, profile.defaultCheckpoint());

            // Node 4: CheckpointLoaderSimple
            ObjectNode n4 = nodeFactory.createNode(4, "CheckpointLoaderSimple", 40, hasLora ? 140 : 180, 315, 98, 0);
            ArrayNode n4Outputs = objectMapper.createArrayNode();
            n4Outputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
            n4Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 1, 2, hasLora ? 0 : 3));
            n4Outputs.add(nodeFactory.createOutput("VAE", "VAE", 2, 7));
            n4.set("outputs", n4Outputs);
            n4.set("widgets_values", objectMapper.createArrayNode().add(ckptName));
            nodes.add(n4);

            // Node 6: Positive Prompt
            nodes.add(nodeFactory.createPromptNode(6, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 415, 100,
                    hasLora ? 11 : 2, 4, prompt, hasLora ? 2 : 1));

            // Node 7: Negative Prompt
            nodes.add(nodeFactory.createPromptNode(7, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 415, 340,
                    hasLora ? 12 : 3, 5, negPrompt, hasLora ? 3 : 2));

            // Node 5: EmptyLatentImage
            nodes.add(nodeFactory.createEmptyLatentNode(5, 415, 580, profile.defaultWidth(), profile.defaultHeight(), 1, 6, hasLora ? 4 : 3));

            // Node 3: KSampler
            ObjectNode n3 = nodeFactory.createNode(3, "KSampler", 880, 150, 315, 262, hasLora ? 5 : 4);
            ArrayNode n3Inputs = objectMapper.createArrayNode();
            n3Inputs.add(nodeFactory.createInput("model", "MODEL", hasLora ? 10 : 1));
            n3Inputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
            n3Inputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
            n3Inputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
            n3.set("inputs", n3Inputs);
            ArrayNode n3Outputs = objectMapper.createArrayNode();
            n3Outputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 8));
            n3.set("outputs", n3Outputs);
            ArrayNode n3Widgets = objectMapper.createArrayNode();
            n3Widgets.add(seed)
                    .add("randomize")
                    .add(profile.defaultSteps())
                    .add(profile.defaultCfg())
                    .add(profile.defaultSampler())
                    .add(profile.defaultScheduler())
                    .add(profile.defaultDenoise());
            n3.set("widgets_values", n3Widgets);
            nodes.add(n3);

            // Node 8: VAEDecode
            nodes.add(nodeFactory.createVaeDecodeNode(8, 1240, 150, 8, 7, 9, hasLora ? 6 : 5));

            // Node 9: SaveImage
            nodes.add(nodeFactory.createSaveImageNode(9, 1490, 150, 9, "ComfyUI", hasLora ? 7 : 6));

            if (hasLora) {
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
                n10.set("widgets_values", objectMapper.createArrayNode().add(loraName).add(1.0).add(1.0));
                nodes.add(n10);

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

                root.put("last_node_id", 10);
                root.put("last_link_id", 12);
            } else {
                // Direct links
                links.add(nodeFactory.createLink(1, 4, 0, 3, 0, "MODEL"));
                links.add(nodeFactory.createLink(2, 4, 1, 6, 0, "CLIP"));
                links.add(nodeFactory.createLink(3, 4, 1, 7, 0, "CLIP"));
                links.add(nodeFactory.createLink(4, 6, 0, 3, 1, "CONDITIONING"));
                links.add(nodeFactory.createLink(5, 7, 0, 3, 2, "CONDITIONING"));
                links.add(nodeFactory.createLink(6, 5, 0, 3, 3, "LATENT"));
                links.add(nodeFactory.createLink(7, 4, 2, 8, 1, "VAE"));
                links.add(nodeFactory.createLink(8, 3, 0, 8, 0, "LATENT"));
                links.add(nodeFactory.createLink(9, 8, 0, 9, 0, "IMAGE"));

                root.put("last_node_id", 9);
                root.put("last_link_id", 9);
            }
        } else {
            // Modular UNET + CLIPLoader + VAELoader
            String unetName = resolveModelFilename(modelInfo, profile.defaultUnet());

            // Node 1: UNETLoader
            ObjectNode n1 = nodeFactory.createNode(1, "UNETLoader", 40, 100, 315, 82, 0);
            ArrayNode n1Outputs = objectMapper.createArrayNode();
            n1Outputs.add(nodeFactory.createOutput("MODEL", "MODEL", 0, 1));
            n1.set("outputs", n1Outputs);
            n1.set("widgets_values", objectMapper.createArrayNode().add(unetName).add("default"));
            nodes.add(n1);

            // Node 8: CLIPLoader / DualCLIPLoader
            if (profile.loaderTopology() == LoaderTopology.UNET_DUAL_CLIP) {
                ObjectNode n8 = nodeFactory.createNode(8, "DualCLIPLoader", 40, 240, 315, 106, 7);
                ArrayNode n8Outputs = objectMapper.createArrayNode();
                n8Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 0, 2, 3));
                n8.set("outputs", n8Outputs);
                ArrayNode n8Widgets = objectMapper.createArrayNode();
                n8Widgets.add(profile.defaultClip1()).add(profile.defaultClip2()).add("flux").add("default");
                n8.set("widgets_values", n8Widgets);
                nodes.add(n8);
            } else {
                ObjectNode n8 = nodeFactory.createNode(8, "CLIPLoader", 40, 240, 315, 106, 7);
                ArrayNode n8Outputs = objectMapper.createArrayNode();
                n8Outputs.add(nodeFactory.createOutput("CLIP", "CLIP", 0, 2, 3));
                n8.set("outputs", n8Outputs);
                ArrayNode n8Widgets = objectMapper.createArrayNode();
                n8Widgets.add(profile.defaultClip1())
                        .add(profile.clipType() != null ? profile.clipType() : "default")
                        .add("default");
                n8.set("widgets_values", n8Widgets);
                nodes.add(n8);
            }

            // Node 9: VAELoader
            ObjectNode n9 = nodeFactory.createNode(9, "VAELoader", 40, 400, 315, 58, 8);
            ArrayNode n9Outputs = objectMapper.createArrayNode();
            n9Outputs.add(nodeFactory.createOutput("VAE", "VAE", 0, 7));
            n9.set("outputs", n9Outputs);
            n9.set("widgets_values", objectMapper.createArrayNode().add(profile.defaultVae()));
            nodes.add(n9);

            // Node 2: Positive Prompt
            nodes.add(nodeFactory.createPromptNode(2, "CLIP Text Encode (Positive Prompt)", "#232", "#353", 415, 100, 2, 4, prompt, 1));

            // Node 3: Negative Prompt
            nodes.add(nodeFactory.createPromptNode(3, "CLIP Text Encode (Negative Prompt)", "#322", "#533", 415, 340, 3, 5, negPrompt, 2));

            // Node 4: EmptyLatentImage
            nodes.add(nodeFactory.createEmptyLatentNode(4, 415, 580, profile.defaultWidth(), profile.defaultHeight(), 1, 6, 3));

            // Node 5: KSampler
            ObjectNode n5 = nodeFactory.createNode(5, "KSampler", 880, 150, 315, 262, 4);
            ArrayNode n5Inputs = objectMapper.createArrayNode();
            n5Inputs.add(nodeFactory.createInput("model", "MODEL", 1));
            n5Inputs.add(nodeFactory.createInput("positive", "CONDITIONING", 4));
            n5Inputs.add(nodeFactory.createInput("negative", "CONDITIONING", 5));
            n5Inputs.add(nodeFactory.createInput("latent_image", "LATENT", 6));
            n5.set("inputs", n5Inputs);
            ArrayNode n5Outputs = objectMapper.createArrayNode();
            n5Outputs.add(nodeFactory.createOutput("LATENT", "LATENT", 0, 8));
            n5.set("outputs", n5Outputs);
            ArrayNode n5Widgets = objectMapper.createArrayNode();
            n5Widgets.add(seed)
                    .add("randomize")
                    .add(profile.defaultSteps())
                    .add(profile.defaultCfg())
                    .add(profile.defaultSampler())
                    .add(profile.defaultScheduler())
                    .add(profile.defaultDenoise());
            n5.set("widgets_values", n5Widgets);
            nodes.add(n5);

            // Node 6: VAEDecode
            nodes.add(nodeFactory.createVaeDecodeNode(6, 1240, 150, 8, 7, 9, 5));

            // Node 7: SaveImage
            nodes.add(nodeFactory.createSaveImageNode(7, 1490, 150, 9, "ComfyUI", 6));

            // Modular links
            links.add(nodeFactory.createLink(1, 1, 0, 5, 0, "MODEL"));
            links.add(nodeFactory.createLink(2, 8, 0, 2, 0, "CLIP"));
            links.add(nodeFactory.createLink(3, 8, 0, 3, 0, "CLIP"));
            links.add(nodeFactory.createLink(4, 2, 0, 5, 1, "CONDITIONING"));
            links.add(nodeFactory.createLink(5, 3, 0, 5, 2, "CONDITIONING"));
            links.add(nodeFactory.createLink(6, 4, 0, 5, 3, "LATENT"));
            links.add(nodeFactory.createLink(7, 9, 0, 6, 1, "VAE"));
            links.add(nodeFactory.createLink(8, 5, 0, 6, 0, "LATENT"));
            links.add(nodeFactory.createLink(9, 6, 0, 7, 0, "IMAGE"));

            root.put("last_node_id", 9);
            root.put("last_link_id", 9);
        }

        root.set("nodes", nodes);
        root.set("links", links);

        ObjectNode extra = objectMapper.createObjectNode();
        ObjectNode ds = objectMapper.createObjectNode();
        ds.put("scale", 0.9);
        ds.set("offset", objectMapper.createArrayNode().add(50).add(50));
        extra.set("ds", ds);
        root.set("extra", extra);
        root.put("version", 0.4);

        return root;
    }

    private String resolveModelFilename(ModelInfo modelInfo, String fallback) {
        if (modelInfo != null && modelInfo.getFilename() != null && !modelInfo.getFilename().isBlank()) {
            return modelInfo.getFilename();
        }
        if (modelInfo != null && modelInfo.getName() != null && !modelInfo.getName().isBlank()) {
            return modelInfo.getName();
        }
        return fallback != null ? fallback : "model.safetensors";
    }
}
