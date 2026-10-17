package de.tki.comfyuicompanion.domain;

import java.util.List;

/**
 * Immutable record representing a model listing retrieved from Civitai API.
 * Encapsulates metadata, preview URLs, target filenames, download attributes,
 * and legal licensing properties (commercial use, person of interest / deepfake flags).
 *
 * @param id                 unique model identifier on Civitai
 * @param name               display name of the model
 * @param type               type of the model (e.g. Checkpoint, LORA, VAE, ControlNet)
 * @param creator            username of the creator
 * @param baseModel          base architecture (e.g. Flux.1 D, SDXL 1.0, SD 1.5, Pony)
 * @param description        HTML or markdown description of the model
 * @param thumbnailUrl       URL to the primary preview image
 * @param downloadUrl        direct download URL for the primary file
 * @param filename           canonical filename (e.g. model.safetensors)
 * @param formattedSize      human-readable file size (e.g. "2.4 GB")
 * @param fileSizeBytes      exact file size in bytes
 * @param downloadCount      total download count
 * @param rating             average user rating (0.0 to 5.0)
 * @param triggerWords       list of trained trigger words (especially for LoRAs)
 * @param nsfw               whether the model contains adult content (blocked app-wide)
 * @param poi                whether model depicts a Person of Interest (real human / personality rights)
 * @param allowCommercialUse commercial permission string (e.g. "None", "Image", "Rent", "Sell")
 * @param allowNoCredit      whether use without attribution/credit is allowed
 * @param allowDerivatives   whether creating derivative models is allowed
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
        boolean nsfw,
        boolean poi,
        String allowCommercialUse,
        boolean allowNoCredit,
        boolean allowDerivatives
) {
    /**
     * Compact constructor ensuring non-null collections and default license strings.
     */
    public CivitaiModelEntry {
        if (triggerWords == null) {
            triggerWords = List.of();
        }
        if (allowCommercialUse == null || allowCommercialUse.isBlank()) {
            allowCommercialUse = "None";
        }
    }

    /**
     * Backward-compatible 15-parameter constructor for existing tests and call sites.
     */
    public CivitaiModelEntry(
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
            boolean nsfw) {
        this(id, name, type, creator, baseModel, description, thumbnailUrl, downloadUrl,
                filename, formattedSize, fileSizeBytes, downloadCount, rating, triggerWords, nsfw,
                false, "None", false, true);
    }

    /**
     * Determines whether commercial usage is permitted according to model metadata.
     *
     * @return true if commercial use is explicitly allowed
     */
    public boolean isCommercialAllowed() {
        if (allowCommercialUse == null) {
            return false;
        }
        String val = allowCommercialUse.trim().toLowerCase();
        return !val.equals("none") && !val.isBlank() && !val.equals("false");
    }

    /**
     * Formats a concise commercial badge label for UI cards.
     *
     * @return commercial license badge label
     */
    public String commercialBadgeLabel() {
        if (!isCommercialAllowed()) {
            return "Non-Commercial";
        }
        if (allowCommercialUse.equalsIgnoreCase("Image")) {
            return "Commercial (Images)";
        }
        return "Commercial OK";
    }

    /**
     * Indicates whether the model depicts a real person, requiring personality rights compliance.
     *
     * @return true if the model depicts a Person of Interest
     */
    public boolean isRealPerson() {
        return poi;
    }
}
