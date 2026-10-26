package de.tki.comfyuicompanion.service.metadata;

import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader.ExtractedPngWorkflow;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for extracting generation parameters and workflow metadata
 * from decoded PNG text chunks across diverse image generation ecosystems.
 */
public interface IImageMetadataStrategy {

    /**
     * Attempts to parse the provided PNG text chunks into a normalized {@link ExtractedPngWorkflow}.
     *
     * @param textChunks map of PNG chunk keywords to decoded UTF-8 strings
     * @return an {@link Optional} containing the extracted workflow if the format matches
     */
    Optional<ExtractedPngWorkflow> extract(Map<String, String> textChunks);
}
