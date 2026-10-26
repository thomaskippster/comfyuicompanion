package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated parser component for transforming raw Civitai JSON nodes into domain
 * {@link CivitaiModelEntry} records.
 * Strictly enforces youth protection standards (JMStV compliance) by rejecting mature
 * or adult content based on the Civitai additive nsfwLevel bitmask.
 */
@Component
public class CivitaiModelJsonParser {

    private static final Logger log = LoggerFactory.getLogger(CivitaiModelJsonParser.class);

    /**
     * Parses a single Civitai model JSON node into a domain {@link CivitaiModelEntry}.
     * Handles rating calculations, resolves thumbnails, and extracts file metadata.
     * Strictly rejects mature/NSFW entries to prevent youth protection violations.
     * Civitai nsfwLevel bitmask:
     * 1 = PG (Safe for Work)
     * 2 = PG-13 (Soft/Mild - SFW)
     * 4 = R (Mature / Adult - NSFW starts here)
     * 8 = X (Explicit Adult - NSFW)
     * 16 = XXX (Overtly sexual / Extreme NSFW)
     * 32 = Blocked
     * Levels 1, 2, and 3 (PG | PG-13) are Safe-For-Work.
     *
     * @param item the raw JSON node from the Civitai API models list or item response
     * @return the parsed model entry, or {@code null} if the item cannot be converted or is NSFW
     */
    public CivitaiModelEntry parseModelEntry(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }

        try {
            int nsfwLevel = item.path("nsfwLevel").asInt(1);
            boolean hasAdultContent = (nsfwLevel & (4 | 8 | 16 | 32)) != 0 || nsfwLevel > 3;
            boolean nsfw = item.path("nsfw").asBoolean(false) || hasAdultContent;
            if (nsfw) {
                // Strictly exclude mature/NSFW models from the application
                return null;
            }

            long id = item.path("id").asLong(0);
            String name = item.path("name").asText("Unnamed Model");
            String type = item.path("type").asText("Checkpoint");
            String creator = item.path("creator").path("username").asText("Civitai Creator");
            String description = item.path("description").asText("");
            boolean poi = item.path("poi").asBoolean(false);

            String commercialUse = "None";
            JsonNode commNode = item.path("allowCommercialUse");
            if (commNode.isTextual()) {
                commercialUse = commNode.asText();
            } else if (commNode.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode elem : commNode) {
                    if (elem.isTextual()) list.add(elem.asText());
                }
                commercialUse = list.isEmpty() ? "None" : String.join(", ", list);
            } else if (commNode.isBoolean()) {
                commercialUse = commNode.asBoolean() ? "Commercial Allowed" : "None";
            }
            boolean allowNoCredit = item.path("allowNoCredit").asBoolean(false);
            boolean allowDerivatives = item.path("allowDerivatives").asBoolean(true);

            int downloadCount = item.path("stats").path("downloadCount").asInt(0);
            double rating = item.path("stats").path("rating").asDouble(0.0);
            if (rating <= 0.0) {
                int thumbsUp = item.path("stats").path("thumbsUpCount").asInt(0);
                int thumbsDown = item.path("stats").path("thumbsDownCount").asInt(0);
                int totalThumbs = thumbsUp + thumbsDown;
                if (totalThumbs > 0) {
                    rating = Math.round((5.0 * thumbsUp / (double) totalThumbs) * 10.0) / 10.0;
                }
            }

            JsonNode versions = item.path("modelVersions");
            if (!versions.isArray() || versions.isEmpty()) {
                return null;
            }

            JsonNode primaryVersion = versions.get(0);
            String baseModel = primaryVersion.path("baseModel").asText("Unknown");
            String downloadUrl = primaryVersion.path("downloadUrl").asText("");

            List<String> triggerWords = new ArrayList<>();
            JsonNode trainedWords = primaryVersion.path("trainedWords");
            if (trainedWords.isArray()) {
                for (JsonNode tw : trainedWords) {
                    if (tw.isTextual() && !tw.asText().isBlank()) {
                        triggerWords.add(tw.asText().trim());
                    }
                }
            }

            String thumbnailUrl = null;
            JsonNode images = primaryVersion.path("images");
            if (images.isArray() && !images.isEmpty()) {
                for (JsonNode img : images) {
                    int imgLevel = img.path("nsfwLevel").asInt(1);
                    boolean imgNsfw = img.path("nsfw").asBoolean(false)
                            || (imgLevel & (4 | 8 | 16 | 32)) != 0
                            || imgLevel > 3;
                    if (!imgNsfw) {
                        thumbnailUrl = img.path("url").asText(null);
                        break;
                    }
                }
            }
            if (thumbnailUrl == null && item.path("images").isArray() && !item.path("images").isEmpty()) {
                for (JsonNode img : item.path("images")) {
                    int imgLevel = img.path("nsfwLevel").asInt(1);
                    boolean imgNsfw = img.path("nsfw").asBoolean(false)
                            || (imgLevel & (4 | 8 | 16 | 32)) != 0
                            || imgLevel > 3;
                    if (!imgNsfw) {
                        thumbnailUrl = img.path("url").asText(null);
                        break;
                    }
                }
            }

            String filename = name.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".safetensors";
            long fileSizeBytes = 0;
            String formattedSize = "Unknown";

            JsonNode files = primaryVersion.path("files");
            if (files.isArray() && !files.isEmpty()) {
                JsonNode primaryFile = files.get(0);
                for (JsonNode f : files) {
                    if (f.path("primary").asBoolean(false)) {
                        primaryFile = f;
                        break;
                    }
                }
                String actualName = primaryFile.path("name").asText("");
                if (!actualName.isBlank()) {
                    filename = actualName;
                }
                String directUrl = primaryFile.path("downloadUrl").asText("");
                if (!directUrl.isBlank()) {
                    downloadUrl = directUrl;
                }
                double sizeKb = primaryFile.path("sizeKB").asDouble(0.0);
                fileSizeBytes = (long) (sizeKb * 1024.0);
                if (sizeKb >= 1024.0 * 1024.0) {
                    formattedSize = String.format("%.2f GB", sizeKb / (1024.0 * 1024.0));
                } else if (sizeKb > 0) {
                    formattedSize = String.format("%.1f MB", sizeKb / 1024.0);
                }
            }

            return new CivitaiModelEntry(
                    id,
                    name,
                    type,
                    creator,
                    baseModel,
                    description,
                    thumbnailUrl,
                    downloadUrl,
                    filename,
                    formattedSize,
                    fileSizeBytes,
                    downloadCount,
                    rating,
                    triggerWords,
                    false, // Always safe / non-nsfw
                    poi,
                    commercialUse,
                    allowNoCredit,
                    allowDerivatives
            );
        } catch (Exception e) {
            log.debug("Skipping unparseable Civitai item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Maps user-facing model type filters to canonical Civitai API types.
     *
     * @param inputType the user-supplied model type filter
     * @return the normalized Civitai type string
     */
    public String mapApiType(String inputType) {
        if (inputType == null) {
            return "";
        }
        return switch (inputType.toLowerCase()) {
            case "checkpoint", "checkpoints" -> "Checkpoint";
            case "lora", "loras", "locon" -> "LORA";
            case "vae" -> "VAE";
            case "controlnet" -> "Controlnet";
            case "upscaler" -> "Upscaler";
            case "textualinversion", "embeddings" -> "TextualInversion";
            case "workflow", "workflows" -> "Workflows";
            default -> inputType;
        };
    }
}
