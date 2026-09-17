package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph;
import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph.VisualLink;
import de.tki.comfyuicompanion.domain.graph.ParsedWorkflowGraph.VisualNode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for parsing raw ComfyUI workflow JSON formats
 * (standard UI graph format and headless API execution format) into neutral visual graph models.
 */
@Service
public class WorkflowGraphParser {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowGraphParser.class);

    /**
     * Parses a raw ComfyUI JSON string into a structured ParsedWorkflowGraph.
     *
     * @param workflowJson Raw workflow JSON string
     * @return ParsedWorkflowGraph containing parsed nodes and connections
     */
    public ParsedWorkflowGraph parse(String workflowJson) {
        List<VisualNode> nodes = new ArrayList<>();
        List<VisualLink> links = new ArrayList<>();

        if (workflowJson == null || workflowJson.trim().isEmpty()) {
            return new ParsedWorkflowGraph(nodes, links);
        }

        try {
            JSONObject root = new JSONObject(workflowJson);

            if (root.has("nodes")) {
                parseStandardUiFormat(root, nodes, links);
            } else {
                parseApiFormat(root, nodes, links);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse workflow graph JSON: {}", e.getMessage());
        }

        return new ParsedWorkflowGraph(nodes, links);
    }

    private void parseStandardUiFormat(JSONObject root, List<VisualNode> nodes, List<VisualLink> links) {
        JSONArray nodesArray = root.getJSONArray("nodes");
        Map<Long, VisualNode> nodeMap = new HashMap<>();

        for (int i = 0; i < nodesArray.length(); i++) {
            JSONObject n = nodesArray.getJSONObject(i);
            VisualNode vn = new VisualNode();
            vn.id = n.getLong("id");
            vn.type = n.optString("type", "Unknown");
            vn.title = n.optString("title", vn.type);

            if (n.has("pos")) {
                JSONArray pos = n.getJSONArray("pos");
                vn.x = pos.getDouble(0);
                vn.y = pos.getDouble(1);
            } else {
                vn.x = 0;
                vn.y = 0;
            }

            if (n.has("size")) {
                JSONArray size = n.getJSONArray("size");
                vn.width = size.getDouble(0);
                vn.height = size.getDouble(1);
                if (vn.width < 100) vn.width = 120;
                if (vn.height < 40) vn.height = 40;
            }

            nodes.add(vn);
            nodeMap.put(vn.id, vn);
        }

        if (root.has("links")) {
            JSONArray linksArray = root.getJSONArray("links");
            for (int i = 0; i < linksArray.length(); i++) {
                JSONArray l = linksArray.getJSONArray(i);
                if (l.length() >= 5) {
                    VisualLink vl = new VisualLink();
                    vl.id = l.getLong(0);
                    vl.originId = l.getLong(1);
                    vl.originSlot = l.getInt(2);
                    vl.targetId = l.getLong(3);
                    vl.targetSlot = l.getInt(4);
                    links.add(vl);
                }
            }
        } else {
            // Fallback link construction if no links array exists
            for (VisualNode vn : nodes) {
                for (int i = 0; i < nodesArray.length(); i++) {
                    JSONObject n = nodesArray.getJSONObject(i);
                    if (n.getLong("id") == vn.id) {
                        JSONObject inputs = n.optJSONObject("inputs");
                        if (inputs != null) {
                            for (String key : inputs.keySet()) {
                                Object val = inputs.get(key);
                                if (val instanceof JSONArray linkArr && linkArr.length() == 2) {
                                    try {
                                        long originId = Long.parseLong(linkArr.getString(0));
                                        VisualLink vl = new VisualLink();
                                        vl.originId = originId;
                                        vl.targetId = vn.id;
                                        vl.originSlot = 0;
                                        vl.targetSlot = 0;
                                        links.add(vl);
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private void parseApiFormat(JSONObject root, List<VisualNode> nodes, List<VisualLink> links) {
        int idx = 0;
        for (String key : root.keySet()) {
            try {
                long id = Long.parseLong(key);
                JSONObject n = root.getJSONObject(key);
                VisualNode vn = new VisualNode();
                vn.id = id;
                vn.type = n.optString("class_type", "Unknown");
                vn.title = vn.type;

                vn.x = (idx % 4) * 250;
                vn.y = (idx / 4) * 150;
                idx++;

                nodes.add(vn);
            } catch (NumberFormatException ignored) {}
        }

        for (VisualNode vn : nodes) {
            JSONObject n = root.getJSONObject(String.valueOf(vn.id));
            JSONObject inputs = n.optJSONObject("inputs");
            if (inputs != null) {
                for (String inputKey : inputs.keySet()) {
                    Object val = inputs.get(inputKey);
                    if (val instanceof JSONArray arr && arr.length() == 2) {
                        try {
                            long originId = Long.parseLong(arr.getString(0));
                            VisualLink vl = new VisualLink();
                            vl.originId = originId;
                            vl.targetId = vn.id;
                            vl.originSlot = arr.getInt(1);
                            vl.targetSlot = 0;
                            links.add(vl);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }
}
