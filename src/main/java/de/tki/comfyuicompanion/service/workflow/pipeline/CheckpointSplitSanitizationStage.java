package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline stage decomposing uninstalled monolithic All-In-One checkpoints into modular
 * {@code UNETLoader}, {@code CLIPLoader}/{@code DualCLIPLoader}, and {@code VAELoader} components.
 */
@Component
@Order(10)
public class CheckpointSplitSanitizationStage implements WorkflowSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(CheckpointSplitSanitizationStage.class);

    @Override
    public boolean process(WorkflowSanitizationContext context) {
        ObjectNode workflow = context.workflow();
        if (workflow == null || !workflow.has("nodes") || !workflow.get("nodes").isArray()) {
            return false;
        }

        List<ModelInfo> models = context.installedModels();
        if (models == null || models.isEmpty()) {
            return false;
        }

        ArrayNode nodes = (ArrayNode) workflow.get("nodes");
        List<ObjectNode> ckptNodes = new ArrayList<>();
        for (JsonNode n : nodes) {
            if (n.isObject() && "CheckpointLoaderSimple".equals(n.path("type").asText(""))) {
                ckptNodes.add((ObjectNode) n);
            }
        }

        boolean mutated = false;
        for (ObjectNode ckpt : ckptNodes) {
            String requested = getRequestedModelFilename(ckpt);
            if (requested != null && !isModelLocallyPresent(requested, models)) {
                String matchingUnet = findMatchingUnet(requested, models);
                List<String> matchingClips = findMatchingClips(requested, models);
                String matchingVae = findMatchingVae(requested, models);

                if (matchingUnet != null && !matchingClips.isEmpty() && matchingVae != null) {
                    splitCheckpoint(workflow, ckpt, matchingUnet, matchingClips, matchingVae, context.objectMapper());
                    mutated = true;
                }
            }
        }
        return mutated;
    }

    /**
     * Decomposes a CheckpointLoaderSimple node into component UNETLoader, CLIPLoader, and VAELoader nodes.
     *
     * @param workflow       the root LiteGraph workflow
     * @param ckptNode       the CheckpointLoaderSimple node
     * @param installedUnet  relative filename of the UNET
     * @param installedClips relative filenames of the CLIP models
     * @param installedVae   relative filename of the VAE
     * @param objectMapper   Jackson object mapper
     */
    public void splitCheckpoint(
            ObjectNode workflow,
            ObjectNode ckptNode,
            String installedUnet,
            List<String> installedClips,
            String installedVae,
            ObjectMapper objectMapper
    ) {
        if (workflow == null || ckptNode == null || installedUnet == null || installedClips == null || installedClips.isEmpty() || installedVae == null) {
            return;
        }

        ArrayNode nodes = (ArrayNode) workflow.get("nodes");
        ArrayNode links = workflow.has("links") && workflow.get("links").isArray()
                ? (ArrayNode) workflow.get("links") : objectMapper.createArrayNode();

        int ckptId = ckptNode.path("id").asInt(1);
        int maxNodeId = WorkflowGraphHelper.getMaxNodeId(workflow);
        int clipNodeId = maxNodeId + 1;
        int vaeNodeId = maxNodeId + 2;
        workflow.put("last_node_id", vaeNodeId);

        ArrayNode origOutputs = ckptNode.has("outputs") && ckptNode.get("outputs").isArray()
                ? (ArrayNode) ckptNode.get("outputs") : objectMapper.createArrayNode();
        ArrayNode modelLinks = copySlotLinks(origOutputs, 0, objectMapper);
        ArrayNode clipLinks = copySlotLinks(origOutputs, 1, objectMapper);
        ArrayNode vaeLinks = copySlotLinks(origOutputs, 2, objectMapper);

        ckptNode.put("type", "UNETLoader");
        if (ckptNode.has("properties") && ckptNode.get("properties").isObject()) {
            ((ObjectNode) ckptNode.get("properties")).put("cnr_id", "comfy-core").put("Node name for S&R", "UNETLoader");
        }
        ckptNode.set("widgets_values", objectMapper.createArrayNode().add(installedUnet).add("default"));
        ckptNode.set("outputs", createSingleOutput("MODEL", modelLinks, objectMapper));

        ObjectNode clipNode = objectMapper.createObjectNode().put("id", clipNodeId).put("mode", 0);
        clipNode.set("inputs", objectMapper.createArrayNode());
        boolean isFlux2OrKlein = installedUnet.toLowerCase(Locale.ROOT).matches(".*(flux-2|flux2|klein).*");
        String clipPrimary = installedClips.get(0);
        boolean useSingleClip = installedClips.size() == 1 || clipPrimary.toLowerCase(Locale.ROOT).contains("qwen");
        clipNode.put("type", useSingleClip ? "CLIPLoader" : "DualCLIPLoader");
        ArrayNode clipWv = objectMapper.createArrayNode();
        if (useSingleClip) {
            clipWv.add(clipPrimary).add(isFlux2OrKlein ? "flux2" : "flux").add("default");
        } else {
            clipWv.add(installedClips.get(0)).add(installedClips.get(1)).add("flux").add("default");
        }
        clipNode.set("widgets_values", clipWv);
        clipNode.set("outputs", createSingleOutput("CLIP", clipLinks, objectMapper));
        nodes.add(clipNode);

        ObjectNode vaeNode = objectMapper.createObjectNode().put("id", vaeNodeId).put("type", "VAELoader").put("mode", 0);
        vaeNode.set("inputs", objectMapper.createArrayNode());
        vaeNode.set("widgets_values", objectMapper.createArrayNode().add(installedVae));
        vaeNode.set("outputs", createSingleOutput("VAE", vaeLinks, objectMapper));
        nodes.add(vaeNode);

        for (JsonNode l : links) {
            if (l.isArray() && l.size() >= 6 && l.get(1).asInt() == ckptId) {
                ArrayNode linkArr = (ArrayNode) l;
                int origSlot = linkArr.get(2).asInt();
                if (origSlot == 1) {
                    linkArr.set(1, objectMapper.getNodeFactory().numberNode(clipNodeId));
                    linkArr.set(2, objectMapper.getNodeFactory().numberNode(0));
                } else if (origSlot == 2) {
                    linkArr.set(1, objectMapper.getNodeFactory().numberNode(vaeNodeId));
                    linkArr.set(2, objectMapper.getNodeFactory().numberNode(0));
                }
            }
        }

        log.info("Split missing CheckpointLoaderSimple (node {}) into UNETLoader ('{}'), {} ({}), and VAELoader ('{}').",
                ckptId, installedUnet, clipNode.path("type").asText(), clipNodeId, installedVae);
    }

    private String getRequestedModelFilename(ObjectNode ckptNode) {
        JsonNode wv = ckptNode.get("widgets_values");
        if (wv != null && wv.isArray() && !wv.isEmpty()) {
            return wv.get(0).asText("");
        }
        return null;
    }

    private boolean isModelLocallyPresent(String requested, List<ModelInfo> models) {
        String reqNorm = requested.replace('\\', '/').toLowerCase(Locale.ROOT);
        String reqBase = reqNorm.substring(reqNorm.lastIndexOf('/') + 1);
        return models.stream().anyMatch(m -> {
            String mName = m.getName() != null ? m.getName().toLowerCase(Locale.ROOT) : "";
            String mFile = m.getFilename() != null ? m.getFilename().replace('\\', '/').toLowerCase(Locale.ROOT) : "";
            return mFile.endsWith(reqBase) || mName.equalsIgnoreCase(reqBase);
        });
    }

    private String findMatchingUnet(String requested, List<ModelInfo> models) {
        String reqLower = requested.toLowerCase(Locale.ROOT);
        boolean isFluxKlein = reqLower.contains("flux") || reqLower.contains("klein");
        return models.stream()
                .filter(m -> "diffusion_models".equalsIgnoreCase(m.getType()) || "unet".equalsIgnoreCase(m.getType()))
                .filter(m -> !isFluxKlein || m.getName().toLowerCase(Locale.ROOT).contains("klein") || m.getName().toLowerCase(Locale.ROOT).contains("flux"))
                .map(ModelInfo::getFilename)
                .findFirst()
                .orElse(null);
    }

    private List<String> findMatchingClips(String requested, List<ModelInfo> models) {
        List<String> clips = new ArrayList<>();
        String reqLower = requested.toLowerCase(Locale.ROOT);
        boolean isFluxKlein = reqLower.contains("flux") || reqLower.contains("klein");
        for (ModelInfo m : models) {
            if ("text_encoders".equalsIgnoreCase(m.getType()) || "clip".equalsIgnoreCase(m.getType())) {
                String name = m.getName() != null ? m.getName().toLowerCase(Locale.ROOT) : "";
                if (isFluxKlein && (name.contains("qwen") || name.contains("clip_l") || name.contains("t5"))) {
                    clips.add(m.getFilename());
                } else if (!isFluxKlein) {
                    clips.add(m.getFilename());
                }
            }
        }
        return clips;
    }

    private String findMatchingVae(String requested, List<ModelInfo> models) {
        return models.stream()
                .filter(m -> "vae".equalsIgnoreCase(m.getType()))
                .map(ModelInfo::getFilename)
                .findFirst()
                .orElse(null);
    }

    private ArrayNode copySlotLinks(ArrayNode outputs, int slot, ObjectMapper objectMapper) {
        return outputs.size() > slot && outputs.get(slot).has("links") && outputs.get(slot).get("links").isArray()
                ? (ArrayNode) outputs.get(slot).get("links").deepCopy() : objectMapper.createArrayNode();
    }

    private ArrayNode createSingleOutput(String type, ArrayNode links, ObjectMapper objectMapper) {
        ObjectNode out = objectMapper.createObjectNode().put("name", type).put("type", type);
        out.set("links", links);
        return objectMapper.createArrayNode().add(out);
    }
}
