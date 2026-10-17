package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory component responsible for creating low-level LiteGraph nodes, input slots,
 * output slots, and link definitions for ComfyUI canvas workflows.
 */
@Component
public class ComfyGraphNodeFactory {

    private final ObjectMapper objectMapper;

    /**
     * Constructs a new ComfyGraphNodeFactory with the specified Jackson {@link ObjectMapper}.
     *
     * @param objectMapper the object mapper for creating graph JSON structures
     */
    @Autowired
    public ComfyGraphNodeFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Creates a new LiteGraph node definition.
     *
     * @param id     unique numeric identifier
     * @param type   ComfyUI class type
     * @param x      X canvas coordinate
     * @param y      Y canvas coordinate
     * @param width  node width
     * @param height node height
     * @param order  execution order
     * @return an {@link ObjectNode} representing the node structure
     */
    public ObjectNode createNode(int id, String type, double x, double y, double width, double height, int order) {
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

    /**
     * Creates an input slot object for a LiteGraph node.
     *
     * @param name input pin name
     * @param type data type (e.g. MODEL, CLIP, LATENT)
     * @param link link ID connected to this input
     * @return an {@link ObjectNode} representing the input pin
     */
    public ObjectNode createInput(String name, String type, int link) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("name", name);
        input.put("type", type);
        input.put("link", link);
        return input;
    }

    /**
     * Creates an output slot object for a LiteGraph node.
     *
     * @param name      output pin name
     * @param type      data type
     * @param slotIndex output slot index
     * @param linkIds   link IDs connected to this output
     * @return an {@link ObjectNode} representing the output pin
     */
    public ObjectNode createOutput(String name, String type, int slotIndex, int... linkIds) {
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

    /**
     * Creates a connection link array in LiteGraph format.
     *
     * @param linkId     unique numeric link ID
     * @param originId   source node ID
     * @param originSlot source output pin index
     * @param targetId   destination node ID
     * @param targetSlot destination input pin index
     * @param type       data type string
     * @return an {@link ArrayNode} representing the link tuple
     */
    public ArrayNode createLink(int linkId, int originId, int originSlot, int targetId, int targetSlot, String type) {
        ArrayNode link = objectMapper.createArrayNode();
        link.add(linkId);
        link.add(originId);
        link.add(originSlot);
        link.add(targetId);
        link.add(targetSlot);
        link.add(type);
        return link;
    }

    /**
     * Creates a CLIP text encoder prompt node with custom titles and coloring.
     *
     * @param id node ID
     * @param title display title
     * @param color header color
     * @param bgcolor body background color
     * @param x X coordinate
     * @param y Y coordinate
     * @param clipLink link ID for CLIP input
     * @param conditioningLink link ID for CONDITIONING output
     * @param text prompt text content
     * @param order execution order
     * @return the configured ObjectNode
     */
    public ObjectNode createPromptNode(int id, String title, String color, String bgcolor,
                                       double x, double y, int clipLink, int conditioningLink,
                                       String text, int order) {
        ObjectNode node = createNode(id, "CLIPTextEncode", x, y, 425, 180, order);
        node.put("title", title);
        node.put("color", color);
        node.put("bgcolor", bgcolor);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(createInput("clip", "CLIP", clipLink));
        node.set("inputs", inputs);
        ArrayNode outputs = objectMapper.createArrayNode();
        outputs.add(createOutput("CONDITIONING", "CONDITIONING", 0, conditioningLink));
        node.set("outputs", outputs);
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add(text != null ? text : "");
        node.set("widgets_values", widgets);
        return node;
    }

    /**
     * Creates an EmptyLatentImage node.
     *
     * @param id node ID
     * @param x X coordinate
     * @param y Y coordinate
     * @param width image width
     * @param height image height
     * @param batchSize batch size
     * @param latentLink output link ID for LATENT
     * @param order execution order
     * @return the configured ObjectNode
     */
    public ObjectNode createEmptyLatentNode(int id, double x, double y, int width, int height,
                                           int batchSize, int latentLink, int order) {
        ObjectNode node = createNode(id, "EmptyLatentImage", x, y, 315, 106, order);
        ArrayNode outputs = objectMapper.createArrayNode();
        outputs.add(createOutput("LATENT", "LATENT", 0, latentLink));
        node.set("outputs", outputs);
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add(width);
        widgets.add(height);
        widgets.add(batchSize);
        node.set("widgets_values", widgets);
        return node;
    }

    /**
     * Creates a VAEDecode node.
     *
     * @param id node ID
     * @param x X coordinate
     * @param y Y coordinate
     * @param samplesLink input LATENT link ID
     * @param vaeLink input VAE link ID
     * @param imageLink output IMAGE link ID
     * @param order execution order
     * @return the configured ObjectNode
     */
    public ObjectNode createVaeDecodeNode(int id, double x, double y, int samplesLink,
                                         int vaeLink, int imageLink, int order) {
        ObjectNode node = createNode(id, "VAEDecode", x, y, 210, 46, order);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(createInput("samples", "LATENT", samplesLink));
        inputs.add(createInput("vae", "VAE", vaeLink));
        node.set("inputs", inputs);
        ArrayNode outputs = objectMapper.createArrayNode();
        outputs.add(createOutput("IMAGE", "IMAGE", 0, imageLink));
        node.set("outputs", outputs);
        node.set("widgets_values", objectMapper.createArrayNode());
        return node;
    }

    /**
     * Creates a SaveImage output node.
     *
     * @param id node ID
     * @param x X coordinate
     * @param y Y coordinate
     * @param imageLink input IMAGE link ID
     * @param prefix filename prefix
     * @param order execution order
     * @return the configured ObjectNode
     */
    public ObjectNode createSaveImageNode(int id, double x, double y, int imageLink,
                                          String prefix, int order) {
        ObjectNode node = createNode(id, "SaveImage", x, y, 500, 500, order);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(createInput("images", "IMAGE", imageLink));
        node.set("inputs", inputs);
        node.set("outputs", objectMapper.createArrayNode());
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add(prefix != null ? prefix : "ComfyUI");
        node.set("widgets_values", widgets);
        return node;
    }
}
