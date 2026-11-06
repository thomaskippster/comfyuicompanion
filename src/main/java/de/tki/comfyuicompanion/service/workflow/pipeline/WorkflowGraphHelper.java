package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.service.workflow.ComfyCoreNodesCatalog;

import java.util.*;

/**
 * Reusable utility helper providing atomic LiteGraph AST queries, link rewiring, and node mutations.
 */
public final class WorkflowGraphHelper {

    private WorkflowGraphHelper() {
        // Utility class
    }

    /**
     * Determines whether a specific node is considered missing in the current ComfyUI environment.
     *
     * @param node                the node to inspect
     * @param registeredNodes     node types reported by ComfyUI
     * @param taintedSubgraphIds  set of subgraph IDs that contain missing nodes
     * @return true if the node is considered missing and needs sanitization
     */
    public static boolean isNodeMissing(ObjectNode node, Set<String> registeredNodes, Set<String> taintedSubgraphIds) {
        if (node == null) return false;
        String type = node.path("type").asText("");
        if (type.isBlank() || ComfyCoreNodesCatalog.FRONTEND_BUILTIN_NODES.contains(type.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (taintedSubgraphIds != null && taintedSubgraphIds.contains(type)) {
            return true;
        }
        if (ComfyCoreNodesCatalog.isUuid(type)) {
            // A subgraph whose definition contains no missing nodes is completely healthy
            return false;
        }
        if (registeredNodes != null && !registeredNodes.isEmpty()) {
            return !registeredNodes.contains(type);
        }
        if (ComfyCoreNodesCatalog.isCoreNode(type)) {
            return false;
        }
        if (ComfyCoreNodesCatalog.isKnownCustomNode(type)) {
            return true;
        }
        String cnrId = node.path("properties").path("cnr_id").asText("");
        if (!cnrId.isBlank() && !"comfy-core".equalsIgnoreCase(cnrId)) {
            return true;
        }
        return !node.path("properties").path("aux_id").asText("").isBlank();
    }

    /**
     * Identifies any subgraphs that contain missing custom nodes.
     *
     * @param workflow        root LiteGraph object
     * @param registeredNodes registered ComfyUI nodes
     * @return set of tainted subgraph IDs
     */
    public static Set<String> findSubgraphsWithMissingNodes(ObjectNode workflow, Set<String> registeredNodes) {
        Set<String> tainted = new HashSet<>();
        if (workflow == null) return tainted;
        JsonNode subgraphs = workflow.path("definitions").path("subgraphs");
        if (!subgraphs.isArray()) return tainted;
        for (JsonNode sg : subgraphs) {
            String sgId = sg.path("id").asText("");
            JsonNode sgNodes = sg.path("nodes");
            if (!sgId.isBlank() && sgNodes.isArray()) {
                for (JsonNode inner : sgNodes) {
                    if (inner.isObject() && isNodeMissing((ObjectNode) inner, registeredNodes, Collections.emptySet())) {
                        tainted.add(sgId);
                        break;
                    }
                }
            }
        }
        return tainted;
    }

    /**
     * Finds a node by its numeric ID.
     *
     * @param workflow root LiteGraph object
     * @param nodeId   node ID
     * @return matching node or null
     */
    public static ObjectNode findNodeById(ObjectNode workflow, int nodeId) {
        if (workflow == null) return null;
        JsonNode nodes = workflow.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode n : nodes) {
                if (n.isObject() && n.path("id").asInt(-1) == nodeId) {
                    return (ObjectNode) n;
                }
            }
        }
        return null;
    }

    /**
     * Finds a link tuple in the links array by link ID.
     *
     * @param links  all workflow links
     * @param linkId link ID
     * @return link array tuple or null
     */
    public static ArrayNode findLinkTuple(ArrayNode links, int linkId) {
        if (links == null || linkId < 0) return null;
        for (JsonNode l : links) {
            if (l.isArray() && !l.isEmpty() && l.get(0).asInt(-1) == linkId) {
                return (ArrayNode) l;
            }
        }
        return null;
    }

    /**
     * Removes a link from the links array and disconnects it from any referencing node input or output slot.
     *
     * @param workflow root LiteGraph object
     * @param linkId   ID of the link to remove
     */
    public static void removeLinkById(ObjectNode workflow, int linkId) {
        if (workflow == null || linkId < 0) return;
        ArrayNode allLinks = workflow.has("links") && workflow.get("links").isArray() ? (ArrayNode) workflow.get("links") : null;
        if (allLinks != null) {
            for (int i = allLinks.size() - 1; i >= 0; i--) {
                JsonNode l = allLinks.get(i);
                if (l.isArray() && !l.isEmpty() && l.get(0).asInt(-1) == linkId) {
                    allLinks.remove(i);
                }
            }
        }
        ArrayNode nodes = workflow.has("nodes") && workflow.get("nodes").isArray() ? (ArrayNode) workflow.get("nodes") : null;
        if (nodes == null) return;
        for (JsonNode n : nodes) {
            JsonNode inputs = n.get("inputs");
            if (inputs != null && inputs.isArray()) {
                for (JsonNode in : inputs) {
                    if (in.isObject() && in.path("link").asInt(-1) == linkId) {
                        ((ObjectNode) in).putNull("link");
                    }
                }
            }
            JsonNode outputs = n.get("outputs");
            if (outputs != null && outputs.isArray()) {
                for (JsonNode out : outputs) {
                    JsonNode outLinks = out.get("links");
                    if (outLinks != null && outLinks.isArray()) {
                        ArrayNode arr = (ArrayNode) outLinks;
                        for (int i = arr.size() - 1; i >= 0; i--) {
                            if (arr.get(i).asInt(-1) == linkId) {
                                arr.remove(i);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Completely removes a node and all links incident to it.
     *
     * @param workflow root LiteGraph object
     * @param nodeId   node ID to remove
     */
    public static void removeNodeAndAllRemainingLinks(ObjectNode workflow, int nodeId) {
        if (workflow == null || nodeId < 0) return;
        ArrayNode allLinks = workflow.has("links") && workflow.get("links").isArray()
                ? (ArrayNode) workflow.get("links") : null;
        if (allLinks != null) {
            List<Integer> linkIdsToRemove = new ArrayList<>();
            for (JsonNode l : allLinks) {
                if (l.isArray() && l.size() >= 6 && (l.get(1).asInt() == nodeId || l.get(3).asInt() == nodeId)) {
                    linkIdsToRemove.add(l.get(0).asInt());
                }
            }
            linkIdsToRemove.forEach(lid -> removeLinkById(workflow, lid));
        }
        ArrayNode nodes = workflow.has("nodes") && workflow.get("nodes").isArray() ? (ArrayNode) workflow.get("nodes") : null;
        if (nodes != null) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                if (nodes.get(i).path("id").asInt(-1) == nodeId) {
                    nodes.remove(i);
                }
            }
        }
    }

    /**
     * Appends a link ID to a specific output slot of a node if not already present.
     *
     * @param mapper Jackson object mapper
     * @param node   target node
     * @param slot   output slot index
     * @param linkId link ID to add
     */
    public static void addLinkToNodeOutput(ObjectMapper mapper, ObjectNode node, int slot, int linkId) {
        if (node == null || mapper == null || !node.has("outputs") || !node.get("outputs").isArray()) return;
        ArrayNode outputs = (ArrayNode) node.get("outputs");
        if (slot < 0 || slot >= outputs.size() || !outputs.get(slot).isObject()) return;
        ObjectNode outObj = (ObjectNode) outputs.get(slot);
        ArrayNode linksArr = outObj.has("links") && outObj.get("links").isArray()
                ? (ArrayNode) outObj.get("links") : mapper.createArrayNode();
        outObj.set("links", linksArr);
        for (JsonNode existing : linksArr) {
            if (existing.asInt(-1) == linkId) return;
        }
        linksArr.add(linkId);
    }

    /**
     * Finds an input slot with a link that matches the specified output type or wildcard.
     *
     * @param inputs  input slots array
     * @param outType required output data type
     * @return link ID or -1 if no match found
     */
    public static int findMatchingConnectedInputLink(JsonNode inputs, String outType) {
        if (inputs == null || !inputs.isArray()) return -1;
        for (JsonNode in : inputs) {
            int linkId = in.path("link").asInt(-1);
            if (linkId >= 0) {
                String inType = in.path("type").asText("");
                if (inType.equalsIgnoreCase(outType) || "*".equals(inType) || "*".equals(outType)) {
                    return linkId;
                }
            }
        }
        return -1;
    }

    /**
     * Computes the maximum node ID currently present in the workflow.
     *
     * @param workflow root LiteGraph object
     * @return highest node ID found
     */
    public static int getMaxNodeId(ObjectNode workflow) {
        if (workflow == null) return 100;
        int max = workflow.path("last_node_id").asInt(100);
        JsonNode nodes = workflow.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode n : nodes) {
                max = Math.max(max, n.path("id").asInt(0));
            }
        }
        return max;
    }
}
