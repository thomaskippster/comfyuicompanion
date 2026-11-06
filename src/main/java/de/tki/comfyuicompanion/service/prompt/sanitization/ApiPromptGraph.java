package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Type-aware view over a ComfyUI API prompt graph ({@code {"<id>": {"class_type": ..., "inputs": {...}}}})
 * combined with the live {@code /object_info} registry of the running ComfyUI server.
 * <p>
 * All structural decisions of the sanitization stages are derived from the input and output type
 * declarations published by ComfyUI itself, never from hardcoded node names.
 */
public final class ApiPromptGraph {

    /** ComfyUI pseudo type used for list-based (dropdown) widget inputs. */
    public static final String COMBO = "COMBO";

    private static final Set<String> PRIMITIVE_TYPES = Set.of("INT", "FLOAT", "STRING", "BOOLEAN", COMBO);

    private final JSONObject prompt;
    private final JSONObject objectInfo;

    /**
     * Reference from a consumer input slot to an output slot of a producer node.
     *
     * @param consumerId  id of the node consuming the value
     * @param inputName   name of the consumer input receiving the link
     * @param outputSlot  output slot index of the producer node
     */
    public record LinkRef(String consumerId, String inputName, int outputSlot) {
    }

    /**
     * Creates a graph view.
     *
     * @param prompt     mutable API prompt dictionary
     * @param objectInfo ComfyUI {@code /object_info} registry
     */
    public ApiPromptGraph(JSONObject prompt, JSONObject objectInfo) {
        this.prompt = prompt != null ? prompt : new JSONObject();
        this.objectInfo = objectInfo != null ? objectInfo : new JSONObject();
    }

    /** @return the underlying mutable prompt dictionary */
    public JSONObject prompt() {
        return prompt;
    }

    /** @return {@code true} if a usable node registry is available */
    public boolean hasRegistry() {
        return !objectInfo.isEmpty();
    }

    /**
     * @param classType node class name
     * @return {@code true} if ComfyUI has registered the given node class
     */
    public boolean isRegistered(String classType) {
        return classType != null && objectInfo.has(classType);
    }

    /** @return snapshot of all node ids in the prompt */
    public List<String> nodeIds() {
        return new ArrayList<>(prompt.keySet());
    }

    /**
     * @param nodeId node id
     * @return the node object or {@code null}
     */
    public JSONObject node(String nodeId) {
        return prompt.optJSONObject(nodeId);
    }

    /**
     * @param nodeId node id
     * @return the node's class type, or an empty string
     */
    public String classType(String nodeId) {
        JSONObject node = node(nodeId);
        return node == null ? "" : node.optString("class_type", "").trim();
    }

    /**
     * Returns the inputs object of a node, creating it if absent.
     *
     * @param nodeId node id
     * @return the mutable inputs object
     */
    public JSONObject inputs(String nodeId) {
        JSONObject node = node(nodeId);
        if (node == null) return new JSONObject();
        JSONObject inputs = node.optJSONObject("inputs");
        if (inputs == null) {
            inputs = new JSONObject();
            node.put("inputs", inputs);
        }
        return inputs;
    }

    /** @return ids of all nodes whose class type is not registered in ComfyUI */
    public List<String> missingNodeIds() {
        List<String> result = new ArrayList<>();
        if (!hasRegistry()) return result;
        for (String id : nodeIds()) {
            String ct = classType(id);
            if (!ct.isEmpty() && !isRegistered(ct)) result.add(id);
        }
        return result;
    }

    /** @return distinct class types of all unregistered nodes */
    public Set<String> missingClassTypes() {
        Set<String> result = new LinkedHashSet<>();
        missingNodeIds().forEach(id -> result.add(classType(id)));
        return result;
    }

    /**
     * @param value raw input value
     * @return {@code true} if the value is an API link {@code ["<nodeId>", <slot>]}
     */
    public static boolean isLink(Object value) {
        return value instanceof JSONArray arr && arr.length() == 2
                && (arr.get(0) instanceof String || arr.get(0) instanceof Number)
                && arr.get(1) instanceof Number;
    }

    /**
     * Collects all consumer input slots linked to any output of the given node.
     *
     * @param producerId producer node id
     * @return list of link references
     */
    public List<LinkRef> consumersOf(String producerId) {
        List<LinkRef> refs = new ArrayList<>();
        for (String id : nodeIds()) {
            JSONObject inputs = inputs(id);
            for (String name : inputs.keySet()) {
                Object v = inputs.get(name);
                if (isLink(v)) {
                    JSONArray link = (JSONArray) v;
                    if (producerId.equals(String.valueOf(link.get(0)))) {
                        refs.add(new LinkRef(id, name, link.getInt(1)));
                    }
                }
            }
        }
        return refs;
    }

    /**
     * @param nodeId node id
     * @return all linked inputs of the node keyed by input name
     */
    public Map<String, JSONArray> linkedInputs(String nodeId) {
        Map<String, JSONArray> result = new LinkedHashMap<>();
        JSONObject inputs = inputs(nodeId);
        for (String name : inputs.keySet()) {
            Object v = inputs.get(name);
            if (isLink(v)) result.put(name, (JSONArray) v);
        }
        return result;
    }

    /**
     * @param nodeId node id
     * @return all literal (non-link) inputs of the node keyed by input name
     */
    public Map<String, Object> literalInputs(String nodeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        JSONObject inputs = inputs(nodeId);
        for (String name : inputs.keySet()) {
            Object v = inputs.get(name);
            if (!isLink(v) && v != JSONObject.NULL) result.put(name, v);
        }
        return result;
    }

    /**
     * Resolves the raw specification array {@code [type, options]} of a node input.
     *
     * @param classType node class name
     * @param inputName input name
     * @return the specification if declared as required or optional input
     */
    public Optional<JSONArray> inputSpec(String classType, String inputName) {
        JSONObject def = objectInfo.optJSONObject(classType);
        JSONObject input = def != null ? def.optJSONObject("input") : null;
        if (input == null) return Optional.empty();
        for (String section : List.of("required", "optional")) {
            JSONObject sec = input.optJSONObject(section);
            if (sec != null && sec.optJSONArray(inputName) != null) {
                return Optional.of(sec.getJSONArray(inputName));
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the declared ComfyUI type of a node input, mapping list-based widgets to {@link #COMBO}.
     *
     * @param classType node class name
     * @param inputName input name
     * @return the declared type
     */
    public Optional<String> inputType(String classType, String inputName) {
        return inputSpec(classType, inputName).map(spec -> {
            Object type = spec.opt(0);
            if (type instanceof JSONArray) return COMBO;
            return String.valueOf(type);
        });
    }

    /**
     * Resolves the declared ComfyUI type of a node output slot.
     *
     * @param classType node class name
     * @param slot      output slot index
     * @return the declared output type
     */
    public Optional<String> outputType(String classType, int slot) {
        JSONObject def = objectInfo.optJSONObject(classType);
        JSONArray outputs = def != null ? def.optJSONArray("output") : null;
        if (outputs == null || slot < 0 || slot >= outputs.length()) return Optional.empty();
        return Optional.of(String.valueOf(outputs.get(slot)));
    }

    /**
     * @param type ComfyUI type name
     * @return {@code true} if the type is a widget primitive that can be inlined as a literal
     */
    public static boolean isPrimitiveType(String type) {
        return type != null && PRIMITIVE_TYPES.contains(type);
    }

    /**
     * Returns the class names of all registered node classes, used for capability discovery.
     *
     * @return registered class names
     */
    public Set<String> registeredClassTypes() {
        return objectInfo.keySet();
    }

    /**
     * @param classType node class name
     * @return names of the required inputs declared by the node class
     */
    public Set<String> requiredInputNames(String classType) {
        JSONObject def = objectInfo.optJSONObject(classType);
        JSONObject input = def != null ? def.optJSONObject("input") : null;
        JSONObject req = input != null ? input.optJSONObject("required") : null;
        return req != null ? req.keySet() : Set.of();
    }

    /**
     * @param classType node class name
     * @return number of declared outputs, or {@code -1} if unknown
     */
    public int outputCount(String classType) {
        JSONObject def = objectInfo.optJSONObject(classType);
        JSONArray outputs = def != null ? def.optJSONArray("output") : null;
        return outputs != null ? outputs.length() : -1;
    }

    /**
     * @param classType node class name
     * @return {@code true} if ComfyUI declares the node class as an output (terminal) node
     */
    public boolean isOutputNode(String classType) {
        JSONObject def = objectInfo.optJSONObject(classType);
        return def != null && def.optBoolean("output_node", false);
    }

    /**
     * Rewires a consumer input to a new producer output.
     *
     * @param ref        consumer reference to rewire
     * @param producerId new producer node id
     * @param slot       new producer output slot
     */
    public void rewire(LinkRef ref, String producerId, int slot) {
        inputs(ref.consumerId()).put(ref.inputName(), new JSONArray().put(producerId).put(slot));
    }

    /**
     * Removes a node from the prompt.
     *
     * @param nodeId node id
     */
    public void remove(String nodeId) {
        prompt.remove(nodeId);
    }
}
