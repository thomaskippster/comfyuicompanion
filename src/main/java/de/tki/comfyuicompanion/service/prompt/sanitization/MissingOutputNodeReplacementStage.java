package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces missing custom image saving or preview nodes (e.g. SaveImageWebp, Image Saver Simple,
 * CR Image Output, PreviewImage variants) with registered ComfyUI core output nodes (SaveImage, PreviewImage).
 * <p>
 * Ensures that workflows retain a functional output node so ComfyUI does not reject the prompt
 * with "Prompt has no outputs" or "Class not found".
 */
@Component
@Order(25)
public class MissingOutputNodeReplacementStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingOutputNodeReplacementStage.class);
    private static final String IMAGE = "IMAGE";
    private static final String CORE_SAVE_IMAGE = "SaveImage";
    private static final String CORE_PREVIEW_IMAGE = "PreviewImage";

    @Override
    public boolean apply(ApiPromptGraph graph) {
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            String classType = graph.classType(missingId);
            if (!isEligibleOutputNode(graph, missingId, classType)) continue;

            Optional<JSONArray> imageSource = findImageSource(graph, missingId);
            if (imageSource.isEmpty()) continue;

            String targetClass = selectOutputClass(graph, classType);
            if (!graph.isRegistered(targetClass)) {
                // If the selected target is not registered, try the other core output node
                String fallbackClass = CORE_SAVE_IMAGE.equals(targetClass) ? CORE_PREVIEW_IMAGE : CORE_SAVE_IMAGE;
                if (graph.isRegistered(fallbackClass)) {
                    targetClass = fallbackClass;
                } else {
                    continue;
                }
            }

            replaceWithCoreOutputNode(graph, missingId, targetClass, imageSource.get());
            mutated = true;
        }
        return mutated;
    }

    private boolean isEligibleOutputNode(ApiPromptGraph graph, String missingId, String classType) {
        if (classType == null || classType.isBlank()) return false;
        String lower = classType.toLowerCase(Locale.ROOT);
        if (lower.contains("compar") || lower.contains("diff") || lower.contains("detail") || lower.contains("eval")) {
            return false;
        }

        boolean nameMatches = lower.contains("save") || lower.contains("saver")
                || lower.contains("preview") || lower.contains("display")
                || lower.contains("output");

        if (!nameMatches) {
            return false;
        }

        return findImageSource(graph, missingId).isPresent();
    }

    private Optional<JSONArray> findImageSource(ApiPromptGraph graph, String nodeId) {
        Map<String, JSONArray> upstream = graph.linkedInputs(nodeId);
        // Look for upstream link whose producer output type is IMAGE
        for (Map.Entry<String, JSONArray> entry : upstream.entrySet()) {
            JSONArray link = entry.getValue();
            String producerId = String.valueOf(link.get(0));
            String producerType = graph.classType(producerId);
            if (graph.isRegistered(producerType)) {
                Optional<String> outType = graph.outputType(producerType, link.getInt(1));
                if (outType.isPresent() && IMAGE.equals(outType.get())) {
                    return Optional.of(link);
                }
            } else {
                // If producer is also missing, check if the input name suggests an image
                String inName = entry.getKey().toLowerCase(Locale.ROOT);
                if (inName.contains("image") || inName.contains("pixel")) {
                    return Optional.of(link);
                }
            }
        }
        return Optional.empty();
    }

    private String selectOutputClass(ApiPromptGraph graph, String originalClassType) {
        String lower = originalClassType.toLowerCase(Locale.ROOT);
        if (lower.contains("preview") || lower.contains("display")) {
            return graph.isRegistered(CORE_PREVIEW_IMAGE) ? CORE_PREVIEW_IMAGE : CORE_SAVE_IMAGE;
        }
        return CORE_SAVE_IMAGE;
    }

    private void replaceWithCoreOutputNode(ApiPromptGraph graph, String missingId, String targetClass, JSONArray imageSource) {
        String originalType = graph.classType(missingId);
        Map<String, Object> literals = graph.literalInputs(missingId);

        String prefix = "ComfyUI";
        for (Map.Entry<String, Object> entry : literals.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if ((key.contains("prefix") || key.contains("filename") || key.equals("path")) && entry.getValue() instanceof String s && !s.isBlank()) {
                prefix = s;
                break;
            }
        }

        JSONObject newInputs = new JSONObject();
        newInputs.put("images", imageSource);
        if (CORE_SAVE_IMAGE.equals(targetClass)) {
            newInputs.put("filename_prefix", prefix);
        }

        JSONObject node = graph.node(missingId);
        node.put("class_type", targetClass);
        node.put("inputs", newInputs);

        log.info("Replaced missing output node '{}' (id {}) with core '{}' (images=[{}, {}], prefix='{}').",
                originalType, missingId, targetClass, imageSource.get(0), imageSource.getInt(1), prefix);
    }
}
