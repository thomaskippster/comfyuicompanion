package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ModelInfo;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility detector responsible for inspecting workflow definitions and identifying
 * cloud-only dependencies, proprietary API keys, or absent local processing models.
 */
public final class WorkflowCloudDetector {

    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "\"(api[-_\\s]?key|secret[-_\\s]?key|token|auth[-_\\s]?token|access[-_\\s]?token)\"",
            Pattern.CASE_INSENSITIVE
    );

    private WorkflowCloudDetector() {
        // Private constructor for utility class
    }

    /**
     * Determines whether a given workflow JSON definition operates purely in the cloud
     * or requires cloud API keys rather than local compute and local checkpoint weights.
     *
     * @param jsonText the raw JSON string of the workflow
     * @param requiredModels list of models parsed or associated with the workflow
     * @return true if the workflow is cloud-only or requires remote API credentials
     */
    public static boolean checkCloudOnly(String jsonText, List<ModelInfo> requiredModels) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return false;
        }

        String lowerJson = jsonText.toLowerCase(Locale.ROOT);

        // 1. Cloud-specific Node Names
        boolean hasCloudNodes = lowerJson.contains("openai") || lowerJson.contains("gemini")
                || lowerJson.contains("claude") || lowerJson.contains("anthropic")
                || lowerJson.contains("midjourney") || lowerJson.contains("replicate")
                || lowerJson.contains("runpod") || lowerJson.contains("chatgpt")
                || lowerJson.contains("geminiapi") || lowerJson.contains("openaisampler")
                || lowerJson.contains("replicatesampler");

        // 2. Request for API Keys, Secrets, or Tokens
        boolean requiresApiKey = API_KEY_PATTERN.matcher(lowerJson).find();

        // 3. Model designations vs File Extensions
        boolean hasLocalModelFiles = false;
        if (requiredModels != null && !requiredModels.isEmpty()) {
            for (ModelInfo info : requiredModels) {
                if (info.getName() != null) {
                    String nameLower = info.getName().toLowerCase(Locale.ROOT);
                    if (nameLower.endsWith(".safetensors") || nameLower.endsWith(".sft")
                            || nameLower.endsWith(".ckpt") || nameLower.endsWith(".pth")
                            || nameLower.endsWith(".pt") || nameLower.endsWith(".bin")
                            || nameLower.endsWith(".onnx")) {
                        hasLocalModelFiles = true;
                        break;
                    }
                }
            }
        }

        // 4. Missing standard local processing nodes
        boolean hasLocalNodes = lowerJson.contains("\"ksampler\"")
                || lowerJson.contains("\"checkpointloadersimple\"")
                || lowerJson.contains("\"checkpointloader\"")
                || lowerJson.contains("\"vaedecode\"")
                || lowerJson.contains("\"cliptextencode\"");

        return hasCloudNodes || requiresApiKey || (!hasLocalModelFiles && !hasLocalNodes);
    }
}
