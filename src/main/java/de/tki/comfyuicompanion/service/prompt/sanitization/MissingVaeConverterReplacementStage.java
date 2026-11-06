package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces missing custom VAE decoders (e.g. VAEDecodeTiled, UltimateVAEDecode, FastVAEDecode)
 * and VAE encoders (e.g. VAEEncodeTiled) with standard ComfyUI core nodes (VAEDecode, VAEEncode).
 * <p>
 * This enables workflows designed for high-resolution tiled decoding or specific custom wrappers
 * to execute reliably on vanilla ComfyUI installations.
 */
@Component
@Order(28)
public class MissingVaeConverterReplacementStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingVaeConverterReplacementStage.class);
    private static final String LATENT = "LATENT";
    private static final String VAE = "VAE";
    private static final String IMAGE = "IMAGE";
    private static final String CORE_VAE_DECODE = "VAEDecode";
    private static final String CORE_VAE_ENCODE = "VAEEncode";

    @Override
    public boolean apply(ApiPromptGraph graph) {
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            String classType = graph.classType(missingId);
            if (classType == null || classType.isBlank()) continue;
            String lower = classType.toLowerCase(Locale.ROOT);

            // Case A: Missing VAE Decoder -> VAEDecode
            if (isEligibleVaeDecoder(graph, missingId, lower) && graph.isRegistered(CORE_VAE_DECODE)) {
                Optional<JSONArray> latentSource = findTypedInputSource(graph, missingId, LATENT, "samples", "latent");
                Optional<JSONArray> vaeSource = findTypedInputSource(graph, missingId, VAE, "vae");
                if (latentSource.isPresent() && vaeSource.isPresent()) {
                    replaceWithVaeDecode(graph, missingId, latentSource.get(), vaeSource.get());
                    mutated = true;
                    continue;
                }
            }

            // Case B: Missing VAE Encoder -> VAEEncode
            if (isEligibleVaeEncoder(graph, missingId, lower) && graph.isRegistered(CORE_VAE_ENCODE)) {
                Optional<JSONArray> imageSource = findTypedInputSource(graph, missingId, IMAGE, "pixels", "image");
                Optional<JSONArray> vaeSource = findTypedInputSource(graph, missingId, VAE, "vae");
                if (imageSource.isPresent() && vaeSource.isPresent()) {
                    replaceWithVaeEncode(graph, missingId, imageSource.get(), vaeSource.get());
                    mutated = true;
                }
            }
        }
        return mutated;
    }

    private boolean isEligibleVaeDecoder(ApiPromptGraph graph, String nodeId, String lowerClassType) {
        boolean nameSuggestsDecoder = (lowerClassType.contains("vae") || lowerClassType.contains("latent"))
                && lowerClassType.contains("decode");
        // Also check if any consumer expects IMAGE and the node receives LATENT + VAE
        if (nameSuggestsDecoder) return true;
        for (ApiPromptGraph.LinkRef consumer : graph.consumersOf(nodeId)) {
            Optional<String> expected = graph.inputType(graph.classType(consumer.consumerId()), consumer.inputName());
            if (expected.isPresent() && IMAGE.equals(expected.get())) {
                return true;
            }
        }
        return false;
    }

    private boolean isEligibleVaeEncoder(ApiPromptGraph graph, String nodeId, String lowerClassType) {
        boolean nameSuggestsEncoder = (lowerClassType.contains("vae") || lowerClassType.contains("pixel"))
                && lowerClassType.contains("encode");
        if (nameSuggestsEncoder) return true;
        for (ApiPromptGraph.LinkRef consumer : graph.consumersOf(nodeId)) {
            Optional<String> expected = graph.inputType(graph.classType(consumer.consumerId()), consumer.inputName());
            if (expected.isPresent() && LATENT.equals(expected.get())) {
                return true;
            }
        }
        return false;
    }

    private Optional<JSONArray> findTypedInputSource(ApiPromptGraph graph, String nodeId, String expectedType, String... preferredNames) {
        Map<String, JSONArray> upstream = graph.linkedInputs(nodeId);
        JSONArray fallback = null;
        for (Map.Entry<String, JSONArray> entry : upstream.entrySet()) {
            JSONArray link = entry.getValue();
            String producerId = String.valueOf(link.get(0));
            String producerType = graph.classType(producerId);
            boolean typeMatches = false;
            if (graph.isRegistered(producerType)) {
                Optional<String> outType = graph.outputType(producerType, link.getInt(1));
                if (outType.isPresent() && expectedType.equals(outType.get())) {
                    typeMatches = true;
                }
            } else {
                // If producer is unregistered, match by input slot name
                String slotName = entry.getKey().toLowerCase(Locale.ROOT);
                for (String pref : preferredNames) {
                    if (slotName.contains(pref)) {
                        typeMatches = true;
                        break;
                    }
                }
            }

            if (typeMatches) {
                String slotName = entry.getKey().toLowerCase(Locale.ROOT);
                for (String pref : preferredNames) {
                    if (slotName.equals(pref)) {
                        return Optional.of(link);
                    }
                }
                if (fallback == null) {
                    fallback = link;
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private void replaceWithVaeDecode(ApiPromptGraph graph, String missingId, JSONArray latentSource, JSONArray vaeSource) {
        String originalType = graph.classType(missingId);
        JSONObject inputs = new JSONObject();
        inputs.put("samples", latentSource);
        inputs.put("vae", vaeSource);

        JSONObject node = graph.node(missingId);
        node.put("class_type", CORE_VAE_DECODE);
        node.put("inputs", inputs);

        log.info("Replaced missing VAE decoder '{}' (id {}) with core '{}'.", originalType, missingId, CORE_VAE_DECODE);
    }

    private void replaceWithVaeEncode(ApiPromptGraph graph, String missingId, JSONArray imageSource, JSONArray vaeSource) {
        String originalType = graph.classType(missingId);
        JSONObject inputs = new JSONObject();
        inputs.put("pixels", imageSource);
        inputs.put("vae", vaeSource);

        JSONObject node = graph.node(missingId);
        node.put("class_type", CORE_VAE_ENCODE);
        node.put("inputs", inputs);

        log.info("Replaced missing VAE encoder '{}' (id {}) with core '{}'.", originalType, missingId, CORE_VAE_ENCODE);
    }
}
