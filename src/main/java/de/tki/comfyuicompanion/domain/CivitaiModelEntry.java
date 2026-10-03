package de.tki.comfyuicompanion.domain;

import java.util.List;

/**
 * Immutable record representing a model listing retrieved from Civitai API.
 * Encapsulates metadata, preview URLs, target filenames, and download attributes.
 *
 * @param id             unique model identifier on Civitai
 * @param name           display name of the model
 * @param type           type of the model (e.g. Checkpoint, LORA, VAE, ControlNet)
 * @param creator        username of the creator
 * @param baseModel      base architecture (e.g. Flux.1 D, SDXL 1.0, SD 1.5, Pony)
 * @param description    HTML or markdown description of the model
 * @param thumbnailUrl   URL to the primary preview image
 * @param downloadUrl    direct download URL for the primary file
 * @param filename       canonical filename (e.g. model.safetensors)
 * @param formattedSize  human-readable file size (e.g. "2.4 GB")
 * @param fileSizeBytes  exact file size in bytes
 * @param downloadCount  total download count
 * @param rating         average user rating (0.0 to 5.0)
 * @param triggerWords   list of trained trigger words (especially for LoRAs)
 * @param nsfw           whether the model contains adult content
 */
public record CivitaiModelEntry(
        long id,
        String name,
        String type,
        String creator,
        String baseModel,
        String description,
        String thumbnailUrl,
        String downloadUrl,
        String filename,
        String formattedSize,
        long fileSizeBytes,
        int downloadCount,
        double rating,
        List<String> triggerWords,
        boolean nsfw
) {
    /**
     * Compact constructor ensuring non-null trigger words list.
     */
    public CivitaiModelEntry {
        if (triggerWords == null) {
            triggerWords = List.of();
        }
    }
}
