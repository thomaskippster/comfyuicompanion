package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Utility and strategy helper for identifying ComfyUI workflow JSON files inside Hugging Face
 * repositories, filtering out non-workflow weight dumps, deduplicating multi-quantization
 * model variants, and fetching remote ComfyUI workflow definitions.
 */
public final class HuggingFaceWorkflowAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceWorkflowAnalyzer.class);

    private static final Set<String> EXCLUDED_JSON_BASENAMES = Set.of(
            "config.json",
            "adapter_config.json",
            "model_index.json",
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "vocab.json",
            "merges.json",
            "added_tokens.json",
            "preprocessor_config.json",
            "scheduler_config.json",
            "generation_config.json",
            "quantize_config.json",
            "configuration.json",
            "params.json",
            "hparams.json",
            "manifest.json",
            "versions.json",
            "weights.json",
            "hf_remote_info.json",
            "inference.json",
            "fastvideo_inference.json",
            "run-summary.json",
            "base-model-files.json",
            "shared-copy-plan.json",
            "anima_comparison.json",
            "conversion.json",
            "ort_config.json"
    );

    private static final List<String> EXCLUDED_FOLDER_PREFIXES = List.of(
            "provenance/",
            "conversion/",
            "training/",
            "model_cards/",
            "diffusers/",
            "scheduler/",
            "transformer/",
            "peft/",
            "peft_",
            "encoder/",
            "text_encoder/",
            "text_encoders/",
            "vae/",
            "unet/",
            "reports/",
            "loras/",
            "checkpoints/",
            "clip/"
    );

    private HuggingFaceWorkflowAnalyzer() {
        // Utility class
    }

    /**
     * Determines whether a repository relative file path represents a ComfyUI workflow JSON file
     * rather than a model configuration or metadata artifact.
     *
     * @param rfilename relative file path inside the Hugging Face repository
     * @return true if the file is a candidate ComfyUI workflow JSON
     */
    public static boolean isWorkflowFile(String rfilename) {
        if (rfilename == null || rfilename.isBlank()) {
            return false;
        }
        String normalized = rfilename.replace('\\', '/').trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json") || lower.startsWith(".")) {
            return false;
        }

        for (String prefix : EXCLUDED_FOLDER_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return false;
            }
        }

        String baseName = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        if (baseName.startsWith(".") || EXCLUDED_JSON_BASENAMES.contains(baseName)) {
            return false;
        }

        if (baseName.endsWith("_config.json")
                || baseName.endsWith(".index.json")
                || baseName.endsWith(".receipt.json")
                || baseName.endsWith("-audit.json")
                || baseName.endsWith("_audit.json")) {
            return false;
        }

        // Check if located in a subdir that is just a quantization config folder (e.g. "2step-bf16/config.json")
        if (baseName.equals("sampling_config.json") || baseName.contains("safetensors")) {
            return false;
        }

        return true;
    }

    /**
     * Checks whether a Hugging Face repository JSON node contains at least one ComfyUI workflow JSON file.
     *
     * @param siblingsArray the "siblings" array node from the Hugging Face API
     * @return true if at least one workflow JSON file exists
     */
    public static boolean hasWorkflowInSiblings(JsonNode siblingsArray) {
        if (siblingsArray == null || !siblingsArray.isArray()) {
            return false;
        }
        for (JsonNode sib : siblingsArray) {
            String rfn = sib.path("rfilename").asText("");
            if (isWorkflowFile(rfn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filters a repository's file list down to the single set of operational files required
     * to run the currently selected variant, avoiding forcing downloads of multiple redundant
     * quantization variants (e.g. int4 vs int8 vs bf16 of the same model role).
     *
     * @param allFiles      all files listed in the repository
     * @param selectedFile  the file currently highlighted by the user (may be null)
     * @param statusChecker function returning "Installed" when a file is present locally
     * @return deduplicated list of operational files needed for a single generation run
     */
    public static List<HuggingFaceFileEntry> selectOperationalFilesForSingleRun(
            List<HuggingFaceFileEntry> allFiles,
            HuggingFaceFileEntry selectedFile,
            Function<HuggingFaceFileEntry, String> statusChecker) {
        if (allFiles == null || allFiles.isEmpty()) {
            return List.of();
        }

        // Exclude workflow JSON files from required model weights count
        List<HuggingFaceFileEntry> weightFiles = allFiles.stream()
                .filter(f -> f != null && !isWorkflowFile(f.rfilename()) && !isWorkflowFile(f.filename()))
                .toList();

        if (weightFiles.isEmpty()) {
            return List.of();
        }

        // Group weight files by functional role (primary model, clip/text_encoder, vae, lora, other)
        Map<String, List<HuggingFaceFileEntry>> byRole = new LinkedHashMap<>();
        for (HuggingFaceFileEntry file : weightFiles) {
            String role = normalizeRole(file.suggestedFolder());
            byRole.computeIfAbsent(role, k -> new ArrayList<>()).add(file);
        }

        String selectedRole = (selectedFile != null && !isWorkflowFile(selectedFile.filename()))
                ? normalizeRole(selectedFile.suggestedFolder())
                : null;

        List<HuggingFaceFileEntry> requiredForRun = new ArrayList<>();
        for (Map.Entry<String, List<HuggingFaceFileEntry>> entry : byRole.entrySet()) {
            String role = entry.getKey();
            List<HuggingFaceFileEntry> candidates = entry.getValue();
            if (candidates.isEmpty()) {
                continue;
            }

            // 1. If the user explicitly selected a file of this role, require that specific file
            if (selectedFile != null && role.equals(selectedRole) && candidates.contains(selectedFile)) {
                requiredForRun.add(selectedFile);
                continue;
            }

            // 2. Otherwise, if any candidate in this role is already installed locally, pick the installed one
            HuggingFaceFileEntry installedCandidate = null;
            if (statusChecker != null) {
                for (HuggingFaceFileEntry cand : candidates) {
                    if ("Installed".equalsIgnoreCase(statusChecker.apply(cand))) {
                        installedCandidate = cand;
                        break;
                    }
                }
            }
            if (installedCandidate != null) {
                requiredForRun.add(installedCandidate);
            } else {
                // 3. Pick the preferred candidate in this role (preferring .safetensors and latest version)
                requiredForRun.add(selectPreferredCandidate(candidates));
            }
        }

        return requiredForRun;
    }

    private static HuggingFaceFileEntry selectPreferredCandidate(List<HuggingFaceFileEntry> candidates) {
        HuggingFaceFileEntry best = candidates.get(0);
        int bestScore = scoreCandidate(best);
        for (int i = 1; i < candidates.size(); i++) {
            HuggingFaceFileEntry cand = candidates.get(i);
            int score = scoreCandidate(cand);
            if (score > bestScore) {
                best = cand;
                bestScore = score;
            }
        }
        return best;
    }

    private static int scoreCandidate(HuggingFaceFileEntry file) {
        String fn = file.filename() != null ? file.filename().toLowerCase(Locale.ROOT) : "";
        int score = 0;
        if (fn.endsWith(".safetensors") || fn.endsWith(".sft")) score += 300;
        else if (fn.endsWith(".gguf")) score += 200;
        else if (fn.endsWith(".ckpt")) score += 100;
        else score += 10;

        if (fn.contains("fp16") || fn.contains("bf16") || fn.contains("fp8")) score += 30;
        else if (!fn.contains("fp32")) score += 20;

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|[_.-])v(\\d+)(?:[_.-]|$)").matcher(fn);
        if (m.find()) {
            try {
                score += Integer.parseInt(m.group(1)) * 5;
            } catch (NumberFormatException ignored) {}
        }
        return score;
    }

    /**
     * Normalizes a suggested model folder into a canonical functional role bucket.
     *
     * @param suggestedFolder folder name (e.g. "checkpoints", "unet", "clip")
     * @return canonical role key
     */
    public static String normalizeRole(String suggestedFolder) {
        if (suggestedFolder == null || suggestedFolder.isBlank()) {
            return "primary";
        }
        String lower = suggestedFolder.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("models/")) {
            lower = lower.substring(7);
        }
        return switch (lower) {
            case "checkpoints", "unet", "diffusion_models" -> "primary";
            case "clip", "text_encoders" -> "clip";
            case "vae" -> "vae";
            case "loras", "lora" -> "lora";
            case "controlnet" -> "controlnet";
            case "upscale_models" -> "upscale";
            case "workflows" -> "workflow";
            default -> lower;
        };
    }

    /**
     * Attempts to fetch and parse an actual ComfyUI workflow JSON file from the given Hugging Face repository.
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the currently selected file entry
     * @param httpClient   HTTP client for downloading the JSON
     * @param hfToken      optional Hugging Face Bearer token
     * @param objectMapper Jackson mapper
     * @return optional containing the parsed workflow JSON object if available
     */
    public static Optional<ObjectNode> tryFetchRemoteWorkflowJson(
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry selectedFile,
            HttpClient httpClient,
            String hfToken,
            ObjectMapper objectMapper) {
        if (repo == null || httpClient == null || objectMapper == null) {
            return Optional.empty();
        }

        HuggingFaceFileEntry targetWfFile = null;
        if (selectedFile != null && (isWorkflowFile(selectedFile.rfilename()) || isWorkflowFile(selectedFile.filename()))) {
            targetWfFile = selectedFile;
        } else if (repo.files() != null) {
            // Prefer UI-format workflow (not ending in .api.json) first
            for (HuggingFaceFileEntry f : repo.files()) {
                if (isWorkflowFile(f.rfilename()) && !f.rfilename().toLowerCase(Locale.ROOT).endsWith(".api.json")) {
                    targetWfFile = f;
                    break;
                }
            }
            if (targetWfFile == null) {
                for (HuggingFaceFileEntry f : repo.files()) {
                    if (isWorkflowFile(f.rfilename()) || isWorkflowFile(f.filename())) {
                        targetWfFile = f;
                        break;
                    }
                }
            }
        }

        if (targetWfFile == null || targetWfFile.downloadUrl() == null || !targetWfFile.downloadUrl().startsWith("http")) {
            return Optional.empty();
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetWfFile.downloadUrl()))
                    .header("User-Agent", "ComfyCompanion-Java/2.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (hfToken != null && !hfToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + hfToken.trim());
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body() != null) {
                JsonNode tree = objectMapper.readTree(resp.body());
                if (tree != null && tree.isObject()) {
                    if (tree.has("workflow") && tree.get("workflow").isObject()) {
                        return Optional.of((ObjectNode) tree.get("workflow"));
                    }
                    return Optional.of((ObjectNode) tree);
                }
            }
        } catch (Exception e) {
            log.debug("Could not fetch remote workflow JSON from {}: {}", targetWfFile.downloadUrl(), e.getMessage());
        }
        return Optional.empty();
    }
}
