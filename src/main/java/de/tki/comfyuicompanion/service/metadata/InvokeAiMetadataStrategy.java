package de.tki.comfyuicompanion.service.metadata;

import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader.ExtractedPngWorkflow;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy implementation that extracts generation parameters from {@code invokeai_metadata}
 * or {@code sd-metadata} JSON text chunks embedded in PNG files.
 */
@Component
public class InvokeAiMetadataStrategy implements IImageMetadataStrategy {

    @Override
    public Optional<ExtractedPngWorkflow> extract(Map<String, String> textChunks) {
        if (textChunks == null || textChunks.isEmpty()) {
            return Optional.empty();
        }
        String raw = textChunks.get("invokeai_metadata");
        if (raw == null || raw.isBlank()) {
            raw = textChunks.get("sd-metadata");
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            JSONObject obj = new JSONObject(raw);
            JSONObject imageNode = obj.has("image") && obj.optJSONObject("image") != null
                    ? obj.getJSONObject("image") : obj;

            String positive = imageNode.optString("positive_prompt",
                    imageNode.optString("prompt", null));
            String negative = imageNode.optString("negative_prompt", "");
            Integer steps = imageNode.has("steps") ? imageNode.optInt("steps", 20) : null;
            Double cfg = imageNode.has("cfg_scale") ? imageNode.optDouble("cfg_scale", 7.0) : null;
            Long seed = imageNode.has("seed") ? imageNode.optLong("seed", 42L) : null;
            Integer width = imageNode.has("width") ? imageNode.optInt("width", 1024) : null;
            Integer height = imageNode.has("height") ? imageNode.optInt("height", 1024) : null;
            String rawSampler = imageNode.optString("scheduler", imageNode.optString("sampler", "euler"));

            String modelName = null;
            if (imageNode.optJSONObject("model") != null) {
                modelName = imageNode.getJSONObject("model").optString("name",
                        imageNode.getJSONObject("model").optString("model_name", null));
            } else if (obj.has("model_weights")) {
                modelName = obj.optString("model_weights", null);
            }
            if (modelName != null && !modelName.isBlank() && !modelName.contains(".")) {
                modelName = modelName + ".safetensors";
            }

            String[] mapped = WebUiParametersMetadataStrategy.mapSamplerAndScheduler(rawSampler, null);

            return Optional.of(new ExtractedPngWorkflow(
                    positive, negative, modelName, seed, steps, cfg, 1.0,
                    width, height, 1, mapped[0], mapped[1], null, null
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
