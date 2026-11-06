package de.tki.comfyuicompanion.service.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper component responsible for recursively flattening ComfyUI 1.0+ subgraph definitions
 * into top-level graph nodes and links, while preserving outer widget overrides and
 * backfilling named widget parameters.
 */
public final class SubgraphFlattener {

    private SubgraphFlattener() {
        // Utility class
    }

    /**
     * Recursively flattens subgraphs embedded in a ComfyUI UI-format workflow JSON
     * into top-level nodes and links.
     *
     * @param uiWorkflow the UI-format workflow JSON object
     * @return the flattened UI-format workflow JSON object
     */
    public static JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        if (uiWorkflow == null || !uiWorkflow.has("definitions")
                || !uiWorkflow.getJSONObject("definitions").has("subgraphs")) {
            return uiWorkflow;
        }

        JSONArray subgraphs = uiWorkflow.getJSONObject("definitions").getJSONArray("subgraphs");
        Map<String, JSONObject> subgraphDefs = new HashMap<>();
        for (int i = 0; i < subgraphs.length(); i++) {
            JSONObject sg = subgraphs.getJSONObject(i);
            subgraphDefs.put(sg.getString("id"), sg);
        }

        JSONArray mainNodes = uiWorkflow.optJSONArray("nodes");
        if (mainNodes == null) {
            return uiWorkflow;
        }
        JSONArray mainLinks = uiWorkflow.optJSONArray("links");
        if (mainLinks == null) {
            mainLinks = new JSONArray();
            uiWorkflow.put("links", mainLinks);
        }

        boolean flattenedAny = true;
        int safetyCounter = 0;
        int idOffset = 10000;

        while (flattenedAny && safetyCounter < 10) {
            flattenedAny = false;
            safetyCounter++;

            JSONArray newNodes = new JSONArray();
            for (int i = 0; i < mainNodes.length(); i++) {
                JSONObject node = mainNodes.getJSONObject(i);
                String type = node.getString("type");

                if (subgraphDefs.containsKey(type)) {
                    flattenedAny = true;
                    JSONObject sgDef = subgraphDefs.get(type);
                    expandSingleSubgraphNode(node, sgDef, mainLinks, newNodes, idOffset);
                    mainLinks = removeVirtualAndSubgraphLinks(mainLinks, node.getInt("id"));
                    uiWorkflow.put("links", mainLinks);
                    idOffset += 10000;
                } else {
                    newNodes.put(node);
                }
            }
            mainNodes = newNodes;
            uiWorkflow.put("nodes", mainNodes);
        }

        return uiWorkflow;
    }

    /**
     * Backfills missing input parameters on an already-converted API prompt object
     * using {@code widgets_values_named} from the workflow's subgraph definitions.
     *
     * @param promptObj      the API prompt dictionary mapping node ID strings to node objects
     * @param definitionsObj the {@code definitions} JSON object containing {@code subgraphs}
     */
    public static void backfillFromSubgraphDefinitions(JSONObject promptObj, JSONObject definitionsObj) {
        if (promptObj == null || definitionsObj == null) {
            return;
        }
        JSONArray subgraphs = definitionsObj.optJSONArray("subgraphs");
        if (subgraphs == null) {
            return;
        }

        for (int i = 0; i < subgraphs.length(); i++) {
            JSONObject sg = subgraphs.optJSONObject(i);
            if (sg == null) continue;
            JSONArray sgNodes = sg.optJSONArray("nodes");
            if (sgNodes == null) continue;

            for (int j = 0; j < sgNodes.length(); j++) {
                JSONObject sgNode = sgNodes.optJSONObject(j);
                if (sgNode == null) continue;
                JSONObject namedWidgets = sgNode.optJSONObject("widgets_values_named");
                if (namedWidgets == null || namedWidgets.isEmpty()) continue;

                int baseId = sgNode.optInt("id", -1);
                String sgType = sgNode.optString("type", "");
                if (baseId < 0 || sgType.isEmpty()) continue;

                for (int mult = 0; mult <= 10; mult++) {
                    String candidateKey = String.valueOf(baseId + mult * 10000);
                    JSONObject apiNode = promptObj.optJSONObject(candidateKey);
                    if (apiNode != null && sgType.equals(apiNode.optString("class_type", ""))) {
                        JSONObject apiInputs = apiNode.optJSONObject("inputs");
                        if (apiInputs == null) {
                            apiInputs = new JSONObject();
                            apiNode.put("inputs", apiInputs);
                        }
                        for (String wk : namedWidgets.keySet()) {
                            if (!apiInputs.has(wk) && !namedWidgets.isNull(wk)) {
                                apiInputs.put(wk, namedWidgets.get(wk));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void expandSingleSubgraphNode(JSONObject outerNode, JSONObject sgDef,
                                                 JSONArray mainLinks, JSONArray newNodes, int idOffset) {
        int subgraphNodeId = outerNode.getInt("id");
        Map<Integer, Object[]> inputSlotLinks = new HashMap<>();
        Map<Integer, List<Object[]>> outputSlotLinks = new HashMap<>();

        JSONArray nodeInputsArr = outerNode.optJSONArray("inputs");
        JSONArray nodeOutputsArr = outerNode.optJSONArray("outputs");
        JSONArray sgInputsArr = sgDef.optJSONArray("inputs");
        JSONArray sgOutputsArr = sgDef.optJSONArray("outputs");

        collectBoundaryLinks(subgraphNodeId, mainLinks, nodeInputsArr, nodeOutputsArr,
                sgInputsArr, sgOutputsArr, inputSlotLinks, outputSlotLinks);

        JSONArray sgNodes = sgDef.getJSONArray("nodes");
        Map<Integer, Integer> nodeIdMap = new HashMap<>();
        Map<Integer, JSONObject> internalNodesByOldId = new HashMap<>();

        for (int j = 0; j < sgNodes.length(); j++) {
            JSONObject internalNode = new JSONObject(sgNodes.getJSONObject(j).toString());
            int oldId = internalNode.getInt("id");
            int newId = oldId + idOffset;
            internalNode.put("id", newId);
            nodeIdMap.put(oldId, newId);
            internalNodesByOldId.put(oldId, internalNode);

            JSONArray nodeInputs = internalNode.optJSONArray("inputs");
            if (nodeInputs != null) {
                for (int k = 0; k < nodeInputs.length(); k++) {
                    JSONObject inputObj = nodeInputs.getJSONObject(k);
                    if (inputObj.has("link") && !inputObj.isNull("link")) {
                        int oldLink = inputObj.getInt("link");
                        inputObj.put("link", oldLink + idOffset * 10);
                    }
                }
            }
            newNodes.put(internalNode);
        }

        propagateOuterWidgetsToInternalNodes(outerNode, sgDef, inputSlotLinks, internalNodesByOldId);
        appendInternalLinks(sgDef.optJSONArray("links"), nodeIdMap, mainLinks, idOffset);
        remapSubgraphInputs(sgInputsArr, inputSlotLinks, mainLinks, idOffset);
        remapSubgraphOutputs(sgOutputsArr, outputSlotLinks, mainLinks, idOffset);
    }

    private static void propagateOuterWidgetsToInternalNodes(JSONObject outerNode, JSONObject sgDef,
                                                             Map<Integer, Object[]> inputSlotLinks,
                                                             Map<Integer, JSONObject> internalNodesByOldId) {
        JSONArray sgInputs = sgDef.optJSONArray("inputs");
        JSONArray sgLinks = sgDef.optJSONArray("links");
        if (sgInputs == null || sgLinks == null) {
            return;
        }
        JSONObject outerNamed = outerNode.optJSONObject("widgets_values_named");
        JSONArray outerValues = outerNode.optJSONArray("widgets_values");

        for (int slotIdx = 0; slotIdx < sgInputs.length(); slotIdx++) {
            if (inputSlotLinks.containsKey(slotIdx)) {
                continue; // Connected via external link; link takes precedence over widget
            }
            JSONObject sgInput = sgInputs.optJSONObject(slotIdx);
            if (sgInput == null) continue;
            String sgInputName = sgInput.optString("name", "");

            Object overrideVal = null;
            if (outerNamed != null && !sgInputName.isEmpty() && outerNamed.has(sgInputName) && !outerNamed.isNull(sgInputName)) {
                overrideVal = outerNamed.get(sgInputName);
            } else if (outerValues != null && slotIdx < outerValues.length() && !outerValues.isNull(slotIdx)) {
                overrideVal = outerValues.get(slotIdx);
            }
            if (overrideVal == null) continue;

            for (int j = 0; j < sgLinks.length(); j++) {
                int[] linkInfo = parseSubgraphLinkEndpoints(sgLinks.get(j));
                if (linkInfo == null || linkInfo[1] != -10 || linkInfo[2] != slotIdx) {
                    continue;
                }
                int targetOldId = linkInfo[3];
                int targetSlot = linkInfo[4];
                JSONObject targetNode = internalNodesByOldId.get(targetOldId);
                if (targetNode == null) continue;

                JSONArray targetInputs = targetNode.optJSONArray("inputs");
                if (targetInputs != null && targetSlot >= 0 && targetSlot < targetInputs.length()) {
                    String targetInputName = targetInputs.getJSONObject(targetSlot).optString("name", "");
                    if (!targetInputName.isEmpty()) {
                        JSONObject targetNamed = targetNode.optJSONObject("widgets_values_named");
                        if (targetNamed == null) {
                            targetNamed = new JSONObject();
                            targetNode.put("widgets_values_named", targetNamed);
                        }
                        targetNamed.put(targetInputName, overrideVal);
                    }
                }
            }
        }
    }

    private static int[] parseSubgraphLinkEndpoints(Object itemObj) {
        if (itemObj instanceof JSONArray link && link.length() >= 5) {
            return new int[]{link.getInt(0), link.getInt(1), link.getInt(2), link.getInt(3), link.getInt(4)};
        } else if (itemObj instanceof JSONObject link) {
            return new int[]{
                    link.getInt("id"),
                    link.getInt("origin_id"),
                    link.getInt("origin_slot"),
                    link.getInt("target_id"),
                    link.getInt("target_slot")
            };
        }
        return null;
    }

    private static void collectBoundaryLinks(int subgraphNodeId, JSONArray mainLinks,
                                             JSONArray nodeInputsArr, JSONArray nodeOutputsArr,
                                             JSONArray sgInputsArr, JSONArray sgOutputsArr,
                                             Map<Integer, Object[]> inputSlotLinks,
                                             Map<Integer, List<Object[]>> outputSlotLinks) {
        for (int j = 0; j < mainLinks.length(); j++) {
            JSONArray link = mainLinks.optJSONArray(j);
            if (link == null || link.length() < 6) continue;
            int linkId = link.getInt(0);
            int originId = link.getInt(1);
            int originSlot = link.getInt(2);
            int targetId = link.getInt(3);
            int targetSlot = link.getInt(4);
            String linkType = link.getString(5);

            if (targetId == subgraphNodeId) {
                int targetSgSlot = resolveSlotIndexByName(targetSlot, nodeInputsArr, sgInputsArr);
                inputSlotLinks.put(targetSgSlot, new Object[]{originId, originSlot, linkType, linkId});
            }
            if (originId == subgraphNodeId) {
                int originSgSlot = resolveSlotIndexByName(originSlot, nodeOutputsArr, sgOutputsArr);
                outputSlotLinks.computeIfAbsent(originSgSlot, k -> new ArrayList<>())
                        .add(new Object[]{targetId, targetSlot, linkType, linkId});
            }
        }
    }

    private static int resolveSlotIndexByName(int defaultSlot, JSONArray outerSlots, JSONArray sgSlots) {
        if (outerSlots != null && defaultSlot < outerSlots.length() && sgSlots != null) {
            String slotName = outerSlots.getJSONObject(defaultSlot).optString("name", "");
            for (int s = 0; s < sgSlots.length(); s++) {
                if (slotName.equals(sgSlots.getJSONObject(s).optString("name", ""))) {
                    return s;
                }
            }
        }
        return defaultSlot;
    }

    private static void appendInternalLinks(JSONArray sgLinks, Map<Integer, Integer> nodeIdMap,
                                            JSONArray mainLinks, int idOffset) {
        if (sgLinks == null) return;
        for (int j = 0; j < sgLinks.length(); j++) {
            Object itemObj = sgLinks.get(j);
            int[] ep = parseSubgraphLinkEndpoints(itemObj);
            if (ep == null) continue;
            String linkType = (itemObj instanceof JSONArray arr) ? arr.getString(5) : ((JSONObject) itemObj).getString("type");

            int newLinkId = ep[0] + idOffset * 10;
            int newOriginId = ep[1] == -10 ? -10 : nodeIdMap.getOrDefault(ep[1], ep[1]);
            int newTargetId = ep[3] == -20 ? -20 : nodeIdMap.getOrDefault(ep[3], ep[3]);
            if (newOriginId == -10 && newTargetId == -20) continue;

            JSONArray newLink = new JSONArray();
            newLink.put(newLinkId).put(newOriginId).put(ep[2]).put(newTargetId).put(ep[4]).put(linkType);
            mainLinks.put(newLink);
        }
    }

    private static void remapSubgraphInputs(JSONArray sgInputs, Map<Integer, Object[]> inputSlotLinks,
                                            JSONArray mainLinks, int idOffset) {
        if (sgInputs == null) return;
        for (int slotIdx = 0; slotIdx < sgInputs.length(); slotIdx++) {
            JSONObject sgInput = sgInputs.getJSONObject(slotIdx);
            JSONArray linkIds = sgInput.optJSONArray("linkIds");
            Object[] extLinkInfo = inputSlotLinks.get(slotIdx);
            if (linkIds == null || extLinkInfo == null) continue;

            int extOriginId = (int) extLinkInfo[0];
            int extOriginSlot = (int) extLinkInfo[1];
            for (int k = 0; k < linkIds.length(); k++) {
                int newIntLinkId = linkIds.getInt(k) + idOffset * 10;
                for (int m = 0; m < mainLinks.length(); m++) {
                    JSONArray l = mainLinks.optJSONArray(m);
                    if (l != null && l.length() >= 6 && l.getInt(0) == newIntLinkId) {
                        l.put(1, extOriginId);
                        l.put(2, extOriginSlot);
                        break;
                    }
                }
            }
        }
    }

    private static void remapSubgraphOutputs(JSONArray sgOutputs, Map<Integer, List<Object[]>> outputSlotLinks,
                                             JSONArray mainLinks, int idOffset) {
        if (sgOutputs == null) return;
        for (int slotIdx = 0; slotIdx < sgOutputs.length(); slotIdx++) {
            JSONObject sgOutput = sgOutputs.getJSONObject(slotIdx);
            JSONArray linkIds = sgOutput.optJSONArray("linkIds");
            List<Object[]> extLinkInfos = outputSlotLinks.get(slotIdx);
            if (linkIds == null || extLinkInfos == null) continue;

            for (int k = 0; k < linkIds.length(); k++) {
                int newIntLinkId = linkIds.getInt(k) + idOffset * 10;
                int internalOriginId = -1;
                int internalOriginSlot = -1;
                for (int m = 0; m < mainLinks.length(); m++) {
                    JSONArray l = mainLinks.optJSONArray(m);
                    if (l != null && l.length() >= 6 && l.getInt(0) == newIntLinkId) {
                        internalOriginId = l.getInt(1);
                        internalOriginSlot = l.getInt(2);
                        break;
                    }
                }
                if (internalOriginId != -1) {
                    for (Object[] extLinkInfo : extLinkInfos) {
                        int extLinkId = (int) extLinkInfo[3];
                        for (int m = 0; m < mainLinks.length(); m++) {
                            JSONArray l = mainLinks.optJSONArray(m);
                            if (l != null && l.length() >= 6 && l.getInt(0) == extLinkId) {
                                l.put(1, internalOriginId);
                                l.put(2, internalOriginSlot);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static JSONArray removeVirtualAndSubgraphLinks(JSONArray mainLinks, int subgraphNodeId) {
        JSONArray cleanLinks = new JSONArray();
        for (int j = 0; j < mainLinks.length(); j++) {
            JSONArray l = mainLinks.optJSONArray(j);
            if (l == null || l.length() < 6) continue;
            int originId = l.getInt(1);
            int targetId = l.getInt(3);
            if (originId == subgraphNodeId || targetId == subgraphNodeId || originId == -10 || targetId == -20) {
                continue;
            }
            cleanLinks.put(l);
        }
        return cleanLinks;
    }
}
