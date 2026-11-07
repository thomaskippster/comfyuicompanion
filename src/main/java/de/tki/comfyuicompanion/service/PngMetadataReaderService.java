package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ComfyGenerationMetadata;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/**
 * Service for extracting ComfyUI generation parameters, prompts, seeds, and workflow graphs
 * embedded in PNG text chunks (tEXt, zTXt, iTXt) and CivitAI parameter blocks.
 */
@Service
public class PngMetadataReaderService {

    private static final Logger log = LoggerFactory.getLogger(PngMetadataReaderService.class);

    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Pattern STEP_PATTERN = Pattern.compile("Steps:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAMPLER_PATTERN = Pattern.compile("Sampler:\\s*([^,\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CFG_PATTERN = Pattern.compile("CFG scale:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEED_PATTERN = Pattern.compile("Seed:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE_PATTERN = Pattern.compile("Size:\\s*(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_PATTERN = Pattern.compile("Model:\\s*([^,\\n]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Reads all text chunks from a PNG file.
     *
     * @param pngFile target PNG file
     * @return map of chunk keywords to string values (e.g. "prompt", "workflow", "parameters")
     */
    public Map<String, String> readPngTextChunks(File pngFile) {
        Map<String, String> result = new HashMap<>();
        if (pngFile == null || !pngFile.exists() || !pngFile.isFile() || pngFile.length() < 8) {
            return result;
        }

        try (InputStream in = new FileInputStream(pngFile);
             DataInputStream dis = new DataInputStream(in)) {

            byte[] sig = new byte[8];
            dis.readFully(sig);
            for (int i = 0; i < 8; i++) {
                if (sig[i] != PNG_SIGNATURE[i]) {
                    return result; // Not a PNG
                }
            }

            while (in.available() > 0) {
                int length = dis.readInt();
                byte[] typeBytes = new byte[4];
                dis.readFully(typeBytes);
                String chunkType = new String(typeBytes, StandardCharsets.US_ASCII);

                if ("IEND".equals(chunkType)) {
                    break;
                }

                if (length < 0 || length > 50 * 1024 * 1024) {
                    // Safety check against corrupted or oversized chunks
                    break;
                }

                byte[] data = new byte[length];
                dis.readFully(data);
                dis.readInt(); // CRC

                try {
                    switch (chunkType) {
                        case "tEXt" -> parseTextChunk(data, result);
                        case "zTXt" -> parseZtxtChunk(data, result);
                        case "iTXt" -> parseItxtChunk(data, result);
                        default -> {}
                    }
                } catch (Exception ex) {
                    log.debug("Skipped unparseable PNG chunk {}: {}", chunkType, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.debug("Failed reading PNG chunks from {}: {}", pngFile.getName(), ex.getMessage());
        }

        return result;
    }

    /**
     * Extracts structured generation metadata from a PNG file.
     *
     * @param pngFile the output PNG image
     * @return extracted generation parameters
     */
    public ComfyGenerationMetadata extractMetadata(File pngFile) {
        if (pngFile == null || !pngFile.getName().toLowerCase().endsWith(".png")) {
            return ComfyGenerationMetadata.empty();
        }

        Map<String, String> chunks = readPngTextChunks(pngFile);
        String promptJson = chunks.get("prompt");
        String workflowJson = chunks.get("workflow");
        String parameters = chunks.get("parameters");

        if (promptJson != null && !promptJson.isBlank()) {
            try {
                ComfyGenerationMetadata meta = parseComfyPromptJson(promptJson, workflowJson);
                if (meta.hasParameters()) {
                    return meta;
                }
            } catch (Exception ex) {
                log.debug("Failed to parse prompt JSON: {}", ex.getMessage());
            }
        }

        if (parameters != null && !parameters.isBlank()) {
            return parseParametersBlock(parameters, promptJson, workflowJson);
        }

        return new ComfyGenerationMetadata(null, null, null, null, null, null, null, null, null, null, promptJson, workflowJson);
    }

    private ComfyGenerationMetadata parseComfyPromptJson(String promptJson, String rawWorkflow) {
        JSONObject root = new JSONObject(promptJson);

        String positivePrompt = null;
        String negativePrompt = null;
        String modelName = null;
        Long seed = null;
        Integer steps = null;
        Double cfg = null;
        String sampler = null;
        String scheduler = null;
        Integer width = null;
        Integer height = null;

        String positiveNodeId = null;
        String negativeNodeId = null;

        // Pass 1: Find KSampler or Sampler node to extract sampling params and conditioning links
        for (String id : root.keySet()) {
            JSONObject node = root.optJSONObject(id);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if (classType.contains("Sampler")) {
                if (seed == null && inputs.has("seed") && inputs.opt("seed") instanceof Number n) {
                    seed = n.longValue();
                } else if (seed == null && inputs.has("noise_seed") && inputs.opt("noise_seed") instanceof Number n) {
                    seed = n.longValue();
                }

                if (steps == null && inputs.has("steps") && inputs.opt("steps") instanceof Number n) {
                    steps = n.intValue();
                }
                if (cfg == null && inputs.has("cfg") && inputs.opt("cfg") instanceof Number n) {
                    cfg = n.doubleValue();
                }
                if (sampler == null && inputs.has("sampler_name") && inputs.opt("sampler_name") instanceof String s) {
                    sampler = s;
                }
                if (scheduler == null && inputs.has("scheduler") && inputs.opt("scheduler") instanceof String s) {
                    scheduler = s;
                }

                if (inputs.opt("positive") instanceof JSONArray arr && arr.length() > 0) {
                    positiveNodeId = String.valueOf(arr.get(0));
                }
                if (inputs.opt("negative") instanceof JSONArray arr && arr.length() > 0) {
                    negativeNodeId = String.valueOf(arr.get(0));
                }
            }

            // Model loaders
            if (classType.contains("CheckpointLoader") || classType.contains("UNETLoader")) {
                if (inputs.has("ckpt_name") && inputs.opt("ckpt_name") instanceof String s) {
                    modelName = s;
                } else if (inputs.has("unet_name") && inputs.opt("unet_name") instanceof String s) {
                    modelName = s;
                }
            }

            // Latent dimensions
            if (classType.contains("EmptyLatentImage") || classType.contains("EmptySD3LatentImage")) {
                if (inputs.has("width") && inputs.opt("width") instanceof Number n) {
                    width = n.intValue();
                }
                if (inputs.has("height") && inputs.opt("height") instanceof Number n) {
                    height = n.intValue();
                }
            }
        }

        // Pass 2: Extract prompt texts
        if (positiveNodeId != null && root.has(positiveNodeId)) {
            JSONObject pNode = root.optJSONObject(positiveNodeId);
            if (pNode != null && pNode.optJSONObject("inputs") != null) {
                positivePrompt = pNode.getJSONObject("inputs").optString("text", null);
            }
        }
        if (negativeNodeId != null && root.has(negativeNodeId)) {
            JSONObject nNode = root.optJSONObject(negativeNodeId);
            if (nNode != null && nNode.optJSONObject("inputs") != null) {
                negativePrompt = nNode.getJSONObject("inputs").optString("text", null);
            }
        }

        // Fallback: If no links directly traced, scan for CLIPTextEncode nodes
        if (positivePrompt == null || positivePrompt.isBlank()) {
            for (String id : root.keySet()) {
                JSONObject node = root.optJSONObject(id);
                if (node == null) continue;
                String classType = node.optString("class_type", "");
                if (classType.equals("CLIPTextEncode")) {
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs != null && inputs.has("text")) {
                        String t = inputs.getString("text").trim();
                        if (!t.isEmpty()) {
                            if (positivePrompt == null) {
                                positivePrompt = t;
                            } else if (negativePrompt == null) {
                                negativePrompt = t;
                            }
                        }
                    }
                }
            }
        }

        return new ComfyGenerationMetadata(
                positivePrompt, negativePrompt, modelName, seed, steps, cfg, sampler, scheduler, width, height,
                promptJson, rawWorkflow
        );
    }

    private ComfyGenerationMetadata parseParametersBlock(String params, String rawPrompt, String rawWorkflow) {
        String positive = null;
        String negative = null;
        Long seed = null;
        Integer steps = null;
        Double cfg = null;
        String sampler = null;
        String model = null;
        Integer width = null;
        Integer height = null;

        String[] parts = params.split("\\nNegative prompt:", 2);
        if (parts.length > 1) {
            positive = parts[0].trim();
            String[] negParts = parts[1].split("\\nSteps:", 2);
            negative = negParts[0].trim();
        } else {
            String[] promptParts = params.split("\\nSteps:", 2);
            positive = promptParts[0].trim();
        }

        Matcher m = STEP_PATTERN.matcher(params);
        if (m.find()) steps = Integer.parseInt(m.group(1));

        m = SAMPLER_PATTERN.matcher(params);
        if (m.find()) sampler = m.group(1).trim();

        m = CFG_PATTERN.matcher(params);
        if (m.find()) cfg = Double.parseDouble(m.group(1));

        m = SEED_PATTERN.matcher(params);
        if (m.find()) seed = Long.parseLong(m.group(1));

        m = MODEL_PATTERN.matcher(params);
        if (m.find()) model = m.group(1).trim();

        m = SIZE_PATTERN.matcher(params);
        if (m.find()) {
            width = Integer.parseInt(m.group(1));
            height = Integer.parseInt(m.group(2));
        }

        return new ComfyGenerationMetadata(positive, negative, model, seed, steps, cfg, sampler, "normal", width, height, rawPrompt, rawWorkflow);
    }

    private void parseTextChunk(byte[] data, Map<String, String> out) {
        int nullIdx = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        if (nullIdx > 0 && nullIdx < data.length - 1) {
            String key = new String(data, 0, nullIdx, StandardCharsets.ISO_8859_1).trim();
            String value = new String(data, nullIdx + 1, data.length - nullIdx - 1, StandardCharsets.UTF_8).trim();
            out.put(key, value);
        }
    }

    private void parseZtxtChunk(byte[] data, Map<String, String> out) {
        int nullIdx = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        if (nullIdx > 0 && nullIdx + 2 < data.length) {
            String key = new String(data, 0, nullIdx, StandardCharsets.ISO_8859_1).trim();
            // data[nullIdx + 1] is compression method (0 = deflate)
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data, nullIdx + 2, data.length - nullIdx - 2);
                 InflaterInputStream iis = new InflaterInputStream(bais)) {
                String value = new String(iis.readAllBytes(), StandardCharsets.UTF_8).trim();
                out.put(key, value);
            } catch (Exception ex) {
                log.debug("Failed inflating zTXt chunk for {}: {}", key, ex.getMessage());
            }
        }
    }

    private void parseItxtChunk(byte[] data, Map<String, String> out) {
        int nullIdx = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        if (nullIdx > 0 && nullIdx + 5 < data.length) {
            String key = new String(data, 0, nullIdx, StandardCharsets.UTF_8).trim();
            boolean compressed = data[nullIdx + 1] == 1;

            int textStart = nullIdx + 3;
            // Skip language tag
            while (textStart < data.length && data[textStart] != 0) textStart++;
            textStart++;
            // Skip translated keyword
            while (textStart < data.length && data[textStart] != 0) textStart++;
            textStart++;

            if (textStart < data.length) {
                try {
                    String value;
                    if (compressed) {
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(data, textStart, data.length - textStart);
                             InflaterInputStream iis = new InflaterInputStream(bais)) {
                            value = new String(iis.readAllBytes(), StandardCharsets.UTF_8).trim();
                        }
                    } else {
                        value = new String(data, textStart, data.length - textStart, StandardCharsets.UTF_8).trim();
                    }
                    out.put(key, value);
                } catch (Exception ex) {
                    log.debug("Failed parsing iTXt chunk for {}: {}", key, ex.getMessage());
                }
            }
        }
    }
}
