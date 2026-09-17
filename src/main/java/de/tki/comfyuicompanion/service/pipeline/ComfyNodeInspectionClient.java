package de.tki.comfyuicompanion.service.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client component responsible for inspecting ComfyUI node definitions,
 * checking class availability, and querying available models from {@code /object_info}.
 */
@Component
public class ComfyNodeInspectionClient {
    private static final Logger logger = LoggerFactory.getLogger(ComfyNodeInspectionClient.class);

    private final HttpClient httpClient;

    public ComfyNodeInspectionClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    @Autowired
    public ComfyNodeInspectionClient(@Autowired(required = false) HttpClient httpClient) {
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Queries {@code /object_info} to extract selectable input options for a specific node and input parameter.
     *
     * @param serverUrl base ComfyUI URL
     * @param nodeClass class name of the target node (e.g. "UNETLoader")
     * @param inputName name of the input property (e.g. "unet_name")
     * @return list of available option values
     */
    public List<String> fetchObjectInfoOptions(String serverUrl, String nodeClass, String inputName) {
        List<String> optionsList = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                if (info.has(nodeClass)) {
                    JSONObject nodeInfo = info.getJSONObject(nodeClass);
                    JSONObject input = nodeInfo.optJSONObject("input");
                    if (input != null) {
                        JSONObject required = input.optJSONObject("required");
                        if (required != null) {
                            Object val = required.opt(inputName);
                            if (val instanceof JSONArray outerArray && outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                JSONArray options = null;
                                if (firstElement instanceof JSONArray jsonArray) {
                                    options = jsonArray;
                                } else if (firstElement instanceof String firstStr && firstStr.equalsIgnoreCase("COMBO")
                                        && outerArray.length() > 1 && outerArray.get(1) instanceof JSONObject configObj) {
                                    options = configObj.optJSONArray("options");
                                } else if (firstElement instanceof String && !"COMBO".equalsIgnoreCase((String) firstElement)) {
                                    options = outerArray;
                                }
                                if (options != null) {
                                    for (int i = 0; i < options.length(); i++) {
                                        Object optVal = options.get(i);
                                        if (optVal instanceof String s && !s.equalsIgnoreCase("COMBO")) {
                                            optionsList.add(s);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [NodeInspection] Failed to fetch options for {}/{}: {}", nodeClass, inputName, e.getMessage());
        }
        return optionsList;
    }

    /**
     * Checks if a custom node class type is registered and available on the ComfyUI server.
     *
     * @param serverUrl base ComfyUI URL
     * @param nodeClass class name (e.g. "VHS_VideoCombine")
     * @return {@code true} if present in object info
     */
    public boolean isNodeClassAvailable(String serverUrl, String nodeClass) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                return info.has(nodeClass);
            }
        } catch (Exception e) {
            logger.error("⚠️ [NodeInspection] Failed to check node class availability: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Retrieves the list of available diffusion checkpoints from CheckpointLoaderSimple.
     *
     * @param serverUrl base ComfyUI URL
     * @return list of checkpoint filenames
     */
    public List<String> fetchAvailableCheckpoints(String serverUrl) {
        return fetchObjectInfoOptions(serverUrl, "CheckpointLoaderSimple", "ckpt_name");
    }

    /**
     * Resolves the best matching model name from a list of available models.
     *
     * @param expected  expected model name or path
     * @param available list of available model filenames
     * @return resolved exact model name or normalized expected name
     */
    public String findExactModelName(String expected, List<String> available) {
        if (expected == null) return "";
        String expectedClean = expected.replaceAll("[/\\\\]+", "/").toLowerCase();
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;

        if (available != null) {
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (avClean.equals(expectedClean)) {
                    return av;
                }
            }
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                String avName = avClean.contains("/") ? avClean.substring(avClean.lastIndexOf('/') + 1) : avClean;
                if (avName.equals(expectedName)) {
                    return av;
                }
            }
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (avClean.endsWith("/" + expectedName) || avClean.contains(expectedName)) {
                    return av;
                }
            }
        }
        return expected.replaceAll("[/\\\\]+", "/");
    }
}
