package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ModelInfo;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComfyDiagnosticService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyDiagnosticService.class);

    private final ConfigService configService;
    private final HttpClient httpClient;

    public ComfyDiagnosticService() {
        this(null);
    }

    @Autowired
    public ComfyDiagnosticService(@Autowired(required = false) ConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    /**
     * Checks if a specific model is currently known to ComfyUI.
     * Queries /object_info to see if the model appears in the checkpoint/lora lists.
     */
    public boolean isModelAvailableInComfy(String modelName, String type) {
        String url = configService.getComfyUIUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/object_info"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;

            JSONObject info = new JSONObject(response.body());
            
            String targetClean = modelName.replaceAll("[/\\\\]+", "/").toLowerCase();
            String targetFileName = targetClean.contains("/") ? targetClean.substring(targetClean.lastIndexOf('/') + 1) : targetClean;

            // Iterate over all nodes in object_info for a truly generic check
            for (String node : info.keySet()) {
                if (info.has(node)) {
                    JSONObject nodeInfo = info.getJSONObject(node);
                    if (nodeInfo.has("input")) {
                        JSONObject input = nodeInfo.getJSONObject("input");
                        
                        // Check both required and optional inputs
                        String[] inputTypes = {"required", "optional"};
                        for (String inputType : inputTypes) {
                            if (input.has(inputType)) {
                                JSONObject requiredOrOptional = input.getJSONObject(inputType);
                                for (String key : requiredOrOptional.keySet()) {
                                    Object val = requiredOrOptional.get(key);
                                    if (val instanceof org.json.JSONArray outerArray) {
                                        if (outerArray.length() > 0) {
                                            Object firstElement = outerArray.get(0);
                                            org.json.JSONArray options = null;
                                            if (firstElement instanceof org.json.JSONArray) {
                                                options = (org.json.JSONArray) firstElement;
                                            } else if (firstElement instanceof String) {
                                                options = outerArray;
                                            }
                                            
                                            if (options != null) {
                                                for (int i = 0; i < options.length(); i++) {
                                                    if (options.get(i) instanceof String optStr) {
                                                        String optClean = optStr.replaceAll("[/\\\\]+", "/").toLowerCase();
                                                        String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                                                        
                                                        if (optClean.equals(targetClean) || optFileName.equals(targetFileName)) {
                                                            return true;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[Diagnostic] API Check failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Bulk check for a list of models.
     */
    public List<String> getMissingModels(List<ModelInfo> models) {
        List<String> missing = new ArrayList<>();
        for (ModelInfo info : models) {
            if (!isModelAvailableInComfy(info.getName(), info.getType())) {
                missing.add(info.getName());
            }
        }
        return missing;
    }
}
