package de.tki.comfyuicompanion.service.workflow;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enterprise catalog and registry identifying standard ComfyUI core nodes,
 * frontend built-in types, and heuristics for detecting third-party custom nodes.
 */
public final class ComfyCoreNodesCatalog {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Complete set of standard ComfyUI built-in nodes loaded from resources.
     */
    private static final Set<String> BUILTIN_NODES = loadBuiltinNodes();

    /**
     * Frontend built-in nodes that are handled client-side in the ComfyUI canvas without backend execution.
     */
    public static final Set<String> FRONTEND_BUILTIN_NODES = Set.of(
            "note", "markdownnote", "reroute", "primitivenode",
            "primitiveint", "primitivefloat", "primitivestring",
            "primitivestringmultiline", "primitiveboolean"
    );

    /**
     * Standard ComfyUI core backend node types (fallback and quick references).
     */
    public static final Set<String> CORE_NODES = Set.of(
            "CheckpointLoaderSimple", "UNETLoader", "UnetLoaderGGUF", "CLIPLoader",
            "DualCLIPLoader", "VAELoader", "CLIPTextEncode", "KSampler", "KSamplerAdvanced",
            "VAEDecode", "VAEDecodeTiled", "VAEEncode", "VAEEncodeTiled", "EmptyLatentImage",
            "SaveImage", "PreviewImage", "LoadImage", "LoadImageMask", "LoraLoader",
            "LoraLoaderModelOnly", "ConditioningCombine", "ConditioningAverage",
            "ConditioningSetArea", "ConditioningSetMask", "InpaintModelConditioning",
            "ConditioningZeroOut", "EmptySD3LatentImage", "ModelSamplingAuraFlow",
            "LatentUpscale", "LatentUpscaleBy", "ImageScale", "ImageScaleBy",
            "ImageUpscaleWithModel", "UpscaleModelLoader", "EmptyFlux2LatentImage",
            "Flux2Scheduler", "CFGGuider", "SamplerCustomAdvanced", "RandomNoise",
            "KSamplerSelect", "BasicScheduler", "SDTurboScheduler", "ModelSamplingFlux",
            "ModelSamplingSD3", "ModelSamplingContinuousEDM", "ControlNetLoader",
            "ControlNetApplyAdvanced", "ApplyInstantID", "CLIPVisionLoader",
            "CLIPVisionEncode", "CropImage", "ImagePadForOutpaint"
    );

    /**
     * Common node replacements from community node types to native core nodes.
     */
    public static final Map<String, String> CORE_REPLACEMENTS = Map.of(
            "ImageSaverSimple", "SaveImage",
            "Easy Prompt Saver", "SaveImage",
            "SaveImageWithMetadata", "SaveImage",
            "Save Image with Generation Metadata", "SaveImage",
            "SimpleReadableMetadataSaveTextSG", "SaveImage",
            "easy int", "PrimitiveInt",
            "easy float", "PrimitiveFloat",
            "easy string", "PrimitiveString"
    );

    private ComfyCoreNodesCatalog() {
        // Static catalog utility
    }

    private static Set<String> loadBuiltinNodes() {
        Set<String> set = new HashSet<>();
        try (InputStream is = ComfyCoreNodesCatalog.class.getResourceAsStream("/comfyui/builtin_core_nodes.txt")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            set.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Logically fall back to CORE_NODES
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Checks whether the given node type represents a native ComfyUI core node or frontend widget.
     *
     * @param type the node type or class name
     * @return true if recognized as core
     */
    public static boolean isCoreNode(String type) {
        if (type == null || type.isBlank()) return true;
        if (isUuid(type)) return true;
        return BUILTIN_NODES.contains(type)
                || CORE_NODES.contains(type)
                || FRONTEND_BUILTIN_NODES.contains(type.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks whether the given string represents a valid UUID identifier.
     *
     * @param value the string to test
     * @return true if formatted as a standard UUID
     */
    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    /**
     * Determines whether the given node type matches known third-party custom node signatures.
     * Subgraphs (UUIDs) and core selectors are excluded to avoid erroneous classification.
     *
     * @param type the node class type
     * @return true if detected as a custom node
     */
    public static boolean isKnownCustomNode(String type) {
        if (type == null || type.isBlank()) return false;
        if (isUuid(type)) return false;

        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("resolution")) return false;

        return lower.contains("easy ") || lower.contains("easyuse")
                || lower.contains("cleanup") || lower.contains("ramcleanup") || lower.contains("vramcleanup")
                || lower.contains("saver") || lower.contains("metadata")
                || lower.contains("selector") || lower.contains("loramanager")
                || lower.contains("vhs_") || lower.contains("videonode")
                || lower.contains("joinstring") || lower.contains("scope seed")
                || lower.contains("get date time");
    }

    /**
     * Resolves a core equivalent for a known custom node, or returns null if no direct 1:1 replacement exists.
     *
     * @param type the custom node type
     * @return the replacement core node type or null
     */
    public static String resolveCoreReplacement(String type) {
        if (type == null) return null;
        return CORE_REPLACEMENTS.get(type);
    }
}
