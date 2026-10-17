package de.tki.comfyuicompanion.service.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.InflaterInputStream;

/**
 * Memory-efficient streaming reader for ComfyUI PNG metadata chunks ({@code tEXt}, {@code zTXt}, {@code iTXt}).
 * Extracts embedded {@code prompt} (API format) and {@code workflow} (UI format) JSON payloads
 * along with generation parameters without decoding pixel data into heap memory.
 */
@Service
public class ComfyPngMetadataReader {
    private static final Logger logger = LoggerFactory.getLogger(ComfyPngMetadataReader.class);
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * Immutable record holding extracted ComfyUI workflow metadata and generation parameters from a PNG file.
     *
     * @param positivePrompt extracted positive prompt text
     * @param negativePrompt extracted negative prompt text
     * @param modelName      extracted checkpoint or UNET model name
     * @param seed           extracted generation seed
     * @param steps          extracted sampling steps
     * @param cfg            extracted CFG scale
     * @param denoise        extracted denoise strength
     * @param width          extracted image width
     * @param height         extracted image height
     * @param batchSize      extracted batch size
     * @param samplerName    extracted sampler name
     * @param scheduler      extracted scheduler name
     * @param promptJson     raw ComfyUI API prompt JSON string
     * @param workflowJson   raw ComfyUI UI workflow JSON string
     */
    public record ExtractedPngWorkflow(
            String positivePrompt,
            String negativePrompt,
            String modelName,
            Long seed,
            Integer steps,
            Double cfg,
            Double denoise,
            Integer width,
            Integer height,
            Integer batchSize,
            String samplerName,
            String scheduler,
            String promptJson,
            String workflowJson
    ) {}

    /**
     * Reads ComfyUI metadata chunks from a PNG file and parses the generation parameters.
     *
     * @param pngFile the PNG file to inspect
     * @return an {@link Optional} containing the extracted workflow metadata if present
     */
    public static Optional<ExtractedPngWorkflow> readMetadata(File pngFile) {
        if (pngFile == null || !pngFile.exists() || !pngFile.isFile()) {
            return Optional.empty();
        }
        try {
            Map<String, String> textChunks = readPngTextChunks(pngFile);
            String promptStr = textChunks.get("prompt");
            String workflowStr = textChunks.get("workflow");
            if ((promptStr == null || promptStr.isBlank()) && (workflowStr == null || workflowStr.isBlank())) {
                return Optional.empty();
            }
            return Optional.of(parseMetadataJson(promptStr, workflowStr));
        } catch (Exception e) {
            logger.warn("Failed to read PNG metadata from {}: {}", pngFile.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Streams through PNG chunks without loading image pixels into memory and returns all text chunks.
     *
     * @param pngFile the PNG file
     * @return map of keyword to text payload
     * @throws IOException if an I/O error occurs or the file is not a valid PNG
     */
    public static Map<String, String> readPngTextChunks(File pngFile) throws IOException {
        Map<String, String> chunks = new HashMap<>();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(pngFile), 16384))) {
            byte[] sig = new byte[8];
            dis.readFully(sig);
            for (int i = 0; i < 8; i++) {
                if (sig[i] != PNG_SIGNATURE[i]) {
                    throw new IOException("Not a valid PNG file");
                }
            }

            while (true) {
                int length;
                try {
                    length = dis.readInt();
                } catch (EOFException eof) {
                    break;
                }
                if (length < 0 || length > 64 * 1024 * 1024) {
                    break;
                }
                byte[] typeBytes = new byte[4];
                dis.readFully(typeBytes);
                String chunkType = new String(typeBytes, StandardCharsets.US_ASCII);

                if ("tEXt".equals(chunkType) || "iTXt".equals(chunkType) || "zTXt".equals(chunkType)) {
                    byte[] data = new byte[length];
                    dis.readFully(data);
                    parseTextChunk(chunkType, data, chunks);
                } else {
                    skipFully(dis, length);
                }
                // Skip 4-byte CRC
                skipFully(dis, 4);

                if ("IEND".equals(chunkType)) {
                    break;
                }
            }
        }
        return chunks;
    }

    private static void skipFully(DataInputStream dis, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = dis.skip(remaining);
            if (skipped <= 0) {
                if (dis.read() == -1) {
                    throw new EOFException();
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static void parseTextChunk(String type, byte[] data, Map<String, String> out) {
        int nullIdx = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        if (nullIdx <= 0) return;
        String keyword = new String(data, 0, nullIdx, StandardCharsets.ISO_8859_1);

        try {
            if ("tEXt".equals(type)) {
                String value = new String(data, nullIdx + 1, data.length - nullIdx - 1, StandardCharsets.UTF_8);
                out.put(keyword, value);
            } else if ("zTXt".equals(type) && nullIdx + 2 < data.length) {
                try (InflaterInputStream iis = new InflaterInputStream(
                        new java.io.ByteArrayInputStream(data, nullIdx + 2, data.length - nullIdx - 2));
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    iis.transferTo(bos);
                    out.put(keyword, bos.toString(StandardCharsets.UTF_8));
                }
            } else if ("iTXt".equals(type) && nullIdx + 3 <= data.length) {
                int pos = nullIdx + 1;
                int compressionFlag = data[pos++] & 0xFF;
                pos++; // compression method
                while (pos < data.length && data[pos] != 0) pos++; // language tag
                pos++;
                while (pos < data.length && data[pos] != 0) pos++; // translated keyword
                pos++;
                if (pos < data.length) {
                    if (compressionFlag == 0) {
                        out.put(keyword, new String(data, pos, data.length - pos, StandardCharsets.UTF_8));
                    } else {
                        try (InflaterInputStream iis = new InflaterInputStream(
                                new java.io.ByteArrayInputStream(data, pos, data.length - pos));
                             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            iis.transferTo(bos);
                            out.put(keyword, bos.toString(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not decode PNG chunk {}: {}", keyword, e.getMessage());
        }
    }

    /**
     * Parses ComfyUI {@code prompt} (API format) and {@code workflow} (UI format) JSON strings into parameters.
     *
     * @param promptStr   API prompt JSON string (nullable)
     * @param workflowStr UI workflow JSON string (nullable)
     * @return extracted workflow record
     */
    public static ExtractedPngWorkflow parseMetadataJson(String promptStr, String workflowStr) {
        String positive = null;
        String negative = null;
        String model = null;
        Long seed = null;
        Integer steps = null;
        Double cfg = null;
        Double denoise = null;
        Integer width = null;
        Integer height = null;
        Integer batch = null;
        String sampler = null;
        String scheduler = null;

        if (promptStr != null && !promptStr.isBlank()) {
            try {
                JSONObject root = new JSONObject(promptStr);
                JSONObject promptObj = root.has("prompt") && root.optJSONObject("prompt") != null
                        ? root.getJSONObject("prompt") : root;

                Set<String> positiveNodes = new HashSet<>();
                Set<String> negativeNodes = new HashSet<>();

                // First pass: inspect sampler, latent, and model loader nodes
                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.optJSONObject(key);
                    if (node == null) continue;
                    String classType = node.optString("class_type", "");
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs == null) continue;

                    collectLinkedConditioningNode(inputs.opt("positive"), positiveNodes);
                    collectLinkedConditioningNode(inputs.opt("negative"), negativeNodes);

                    if (classType.contains("KSampler") || classType.equals("SamplerCustom") || classType.equals("SamplerCustomAdvanced")) {
                        if (inputs.has("seed") && seed == null) seed = inputs.optLong("seed");
                        if (inputs.has("noise_seed") && seed == null) seed = inputs.optLong("noise_seed");
                        if (inputs.has("steps") && steps == null && inputs.opt("steps") instanceof Number n) steps = n.intValue();
                        if (inputs.has("cfg") && cfg == null && inputs.opt("cfg") instanceof Number n) cfg = n.doubleValue();
                        if (inputs.has("denoise") && denoise == null && inputs.opt("denoise") instanceof Number n) denoise = n.doubleValue();
                        if (inputs.has("sampler_name") && sampler == null) sampler = inputs.optString("sampler_name", null);
                        if (inputs.has("scheduler") && scheduler == null) scheduler = inputs.optString("scheduler", null);
                    } else if ("RandomNoise".equals(classType) && inputs.has("noise_seed") && seed == null) {
                        seed = inputs.optLong("noise_seed");
                    } else if ("BasicScheduler".equals(classType) || "Flux2Scheduler".equals(classType)
                            || "BetaScheduler".equals(classType) || "SDTurboScheduler".equals(classType)) {
                        if (inputs.has("steps") && steps == null && inputs.opt("steps") instanceof Number n) steps = n.intValue();
                        if (inputs.has("scheduler") && scheduler == null) scheduler = inputs.optString("scheduler", null);
                        if (inputs.has("denoise") && denoise == null && inputs.opt("denoise") instanceof Number n) denoise = n.doubleValue();
                    } else if ("CFGGuider".equals(classType) && inputs.has("cfg") && cfg == null && inputs.opt("cfg") instanceof Number n) {
                        cfg = n.doubleValue();
                    } else if ("FluxGuidance".equals(classType) && inputs.has("guidance") && cfg == null && inputs.opt("guidance") instanceof Number n) {
                        cfg = n.doubleValue();
                    } else if ("KSamplerSelect".equals(classType) && inputs.has("sampler_name") && sampler == null) {
                        sampler = inputs.optString("sampler_name", null);
                    } else if (classType.contains("EmptyLatentImage") || classType.contains("EmptySD3LatentImage")
                            || classType.contains("EmptyFlux2LatentImage") || (classType.contains("Empty") && classType.contains("Latent"))) {
                        if (inputs.has("width") && width == null && inputs.opt("width") instanceof Number n) width = n.intValue();
                        if (inputs.has("height") && height == null && inputs.opt("height") instanceof Number n) height = n.intValue();
                        if (inputs.has("batch_size") && batch == null && inputs.opt("batch_size") instanceof Number n) batch = n.intValue();
                    }

                    if (model == null) {
                        for (String mKey : new String[]{"ckpt_name", "unet_name", "model_name"}) {
                            if (inputs.has(mKey) && inputs.opt(mKey) instanceof String s && !s.isBlank()) {
                                model = s;
                                break;
                            }
                        }
                    }
                }

                // Second pass: resolve positive and negative prompts from CLIPTextEncode or text nodes
                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.optJSONObject(key);
                    if (node == null) continue;
                    String classType = node.optString("class_type", "");
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs == null) continue;

                    String text = extractTextFromInputs(inputs);
                    if (text == null || text.isBlank()) continue;

                    if (negativeNodes.contains(key)) {
                        if (negative == null) negative = text;
                    } else if (positiveNodes.contains(key)) {
                        if (positive == null) positive = text;
                    } else if (classType.contains("CLIPTextEncode") || classType.contains("TextEncode") || classType.contains("PrimitiveString")) {
                        String metaTitle = node.optJSONObject("_meta") != null
                                ? node.optJSONObject("_meta").optString("title", "").toLowerCase() : "";
                        if (metaTitle.contains("negative") && negative == null) {
                            negative = text;
                        } else if (positive == null) {
                            positive = text;
                        } else if (negative == null) {
                            negative = text;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to parse API prompt JSON from PNG: {}", e.getMessage());
            }
        }

        return new ExtractedPngWorkflow(
                positive, negative, model, seed, steps, cfg, denoise,
                width, height, batch, sampler, scheduler, promptStr, workflowStr
        );
    }

    private static void collectLinkedConditioningNode(Object linkObj, Set<String> targetSet) {
        if (linkObj instanceof JSONArray arr && !arr.isEmpty()) {
            targetSet.add(String.valueOf(arr.get(0)));
        }
    }

    private static String extractTextFromInputs(JSONObject inputs) {
        for (String key : new String[]{"text", "prompt", "value", "string", "clip_l", "t5xxl"}) {
            Object val = inputs.opt(key);
            if (val instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }
}
