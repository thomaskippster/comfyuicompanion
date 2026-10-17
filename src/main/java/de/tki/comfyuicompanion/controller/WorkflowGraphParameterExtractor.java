package de.tki.comfyuicompanion.controller;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses ComfyUI workflow JSON structures to extract generation parameters
 * like dimensions, batch size, sampling steps, CFG scale, sampler, and scheduler.
 */
public class WorkflowGraphParameterExtractor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowGraphParameterExtractor.class);

    /**
     * Immutable data holder representing parameters extracted from a workflow graph.
     *
     * @param width image width
     * @param height image height
     * @param batch batch count
     * @param steps sampling steps
     * @param cfg classifier-free guidance scale
     * @param denoise denoising strength
     * @param sampler sampler algorithm name
     * @param scheduler scheduler algorithm name
     */
    public record ExtractedParameters(
            Integer width,
            Integer height,
            Integer batch,
            Integer steps,
            Double cfg,
            Double denoise,
            String sampler,
            String scheduler
    ) {}

    /**
     * Parses the workflow JSON string and extracts all detectable generation parameters.
     *
     * @param jsonStr the workflow JSON string
     * @return the extracted parameters record
     */
    public ExtractedParameters extractFromWorkflow(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ExtractedParameters(null, null, null, null, null, null, null, null);
        }

        try {
            JSONObject obj = new JSONObject(jsonStr);
            Integer extractedWidth = null;
            Integer extractedHeight = null;
            Integer extractedBatch = null;
            Integer extractedSteps = null;
            Double extractedCfg = null;
            Double extractedDenoise = null;
            String extractedSampler = null;
            String extractedScheduler = null;

            if (obj.has("nodes")) {
                List<JSONObject> allNodes = collectAllNodes(obj);

                for (JSONObject node : allNodes) {
                    String type = node.optString("type", "");
                    JSONArray widgets = node.optJSONArray("widgets_values");
                    JSONObject inputs = node.optJSONObject("inputs");

                    if (type.contains("Empty") || type.contains("Latent")) {
                        if (widgets != null && widgets.length() >= 2) {
                            if (widgets.get(0) instanceof Number num && extractedWidth == null) extractedWidth = num.intValue();
                            if (widgets.get(1) instanceof Number num && extractedHeight == null) extractedHeight = num.intValue();
                            if (widgets.length() >= 3 && widgets.get(2) instanceof Number num && extractedBatch == null) extractedBatch = num.intValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                            if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                            if (inputs.has("batch_size") && extractedBatch == null) extractedBatch = inputs.optInt("batch_size");
                        }
                    }

                    if ("Flux2Scheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedWidth == null) {
                            extractedWidth = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedHeight == null) {
                            extractedHeight = num.intValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                            if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        }
                        if (extractedScheduler == null) extractedScheduler = "simple";
                    }

                    if ("BasicScheduler".equals(type) || "BetaScheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof String s && extractedScheduler == null) {
                            if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedDenoise == null) {
                            extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) {
                                extractedScheduler = inputs.optString("scheduler");
                            }
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    }

                    if ("SDTurboScheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedDenoise == null) {
                            extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    }

                    if ("KSamplerSelect".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && extractedSampler == null) {
                            Object first = widgets.get(0);
                            String s = (first instanceof String str) ? str : first.toString();
                            if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                        }
                        if (inputs != null && inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) {
                            extractedSampler = inputs.optString("sampler_name");
                        }
                    }

                    if ("CFGGuider".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedCfg == null) {
                            extractedCfg = num.doubleValue();
                        }
                        if (inputs != null && inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                    } else if ("FluxGuidance".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedCfg == null) {
                            extractedCfg = num.doubleValue();
                        }
                        if (inputs != null && inputs.has("guidance") && extractedCfg == null) extractedCfg = inputs.optDouble("guidance");
                    }

                    if ("KSampler".equals(type)) {
                        if (widgets != null) {
                            if (widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedSteps == null) extractedSteps = num.intValue();
                            if (widgets.length() > 3 && widgets.get(3) instanceof Number num && extractedCfg == null) extractedCfg = num.doubleValue();
                            if (widgets.length() > 4 && widgets.get(4) instanceof String s && extractedSampler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                            }
                            if (widgets.length() > 5 && widgets.get(5) instanceof String s && extractedScheduler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                            }
                            if (widgets.length() > 6 && widgets.get(6) instanceof Number num && extractedDenoise == null) extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                            if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    } else if ("KSamplerAdvanced".equals(type)) {
                        if (widgets != null) {
                            if (widgets.length() > 3 && widgets.get(3) instanceof Number num && extractedSteps == null) extractedSteps = num.intValue();
                            if (widgets.length() > 4 && widgets.get(4) instanceof Number num && extractedCfg == null) extractedCfg = num.doubleValue();
                            if (widgets.length() > 5 && widgets.get(5) instanceof String s && extractedSampler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                            }
                            if (widgets.length() > 6 && widgets.get(6) instanceof String s && extractedScheduler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                            }
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                            if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        }
                    }
                }
            } else {
                JSONObject promptObj = obj.has("prompt") ? obj.getJSONObject("prompt") : obj;
                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.optJSONObject(key);
                    if (node == null) continue;
                    String classType = node.optString("class_type", "");
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs == null) continue;

                    if (classType.contains("KSampler")) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                        if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                        if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("KSamplerSelect".equals(classType)) {
                        if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                    } else if ("Flux2Scheduler".equals(classType)) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                        if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        if (extractedScheduler == null) extractedScheduler = "simple";
                    } else if ("BasicScheduler".equals(classType) || "BetaScheduler".equals(classType)) {
                        if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("SDTurboScheduler".equals(classType)) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("CFGGuider".equals(classType)) {
                        if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                    } else if ("FluxGuidance".equals(classType)) {
                        if (inputs.has("guidance") && extractedCfg == null) extractedCfg = inputs.optDouble("guidance");
                    } else if (classType.contains("Empty") || classType.contains("Latent")) {
                        if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                        if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        if (inputs.has("batch_size") && extractedBatch == null) extractedBatch = inputs.optInt("batch_size");
                    }
                }
            }
            return new ExtractedParameters(extractedWidth, extractedHeight, extractedBatch, extractedSteps,
                    extractedCfg, extractedDenoise, extractedSampler, extractedScheduler);
        } catch (Exception e) {
            logger.warn("Failed to parse workflow to extract defaults: {}", e.getMessage());
            return new ExtractedParameters(null, null, null, null, null, null, null, null);
        }
    }

    /**
     * Collects all nodes from a workflow graph, including nodes inside subgraphs/groups.
     *
     * @param root The root JSONObject representing the workflow.
     * @return A list of all JSON objects representing nodes.
     */
    public List<JSONObject> collectAllNodes(JSONObject root) {
        List<JSONObject> allNodes = new ArrayList<>();
        if (root == null) return allNodes;

        if (root.has("nodes") && root.get("nodes") instanceof JSONArray nodes) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node != null) {
                    allNodes.add(node);
                }
            }
        }

        if (root.has("definitions") && root.get("definitions") instanceof JSONObject defs) {
            if (defs.has("subgraphs") && defs.get("subgraphs") instanceof JSONArray subgraphs) {
                for (int i = 0; i < subgraphs.length(); i++) {
                    JSONObject sg = subgraphs.optJSONObject(i);
                    if (sg != null && sg.has("nodes") && sg.get("nodes") instanceof JSONArray sgNodes) {
                        for (int j = 0; j < sgNodes.length(); j++) {
                            JSONObject sn = sgNodes.optJSONObject(j);
                            if (sn != null) {
                                allNodes.add(sn);
                            }
                        }
                    }
                }
            }
        }
        return allNodes;
    }
}
