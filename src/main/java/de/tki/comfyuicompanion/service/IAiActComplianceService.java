package de.tki.comfyuicompanion.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service for ensuring compliance with Article 50 of the European Union Artificial
 * Intelligence Act (Regulation (EU) 2024/1689).
 * <p>
 * Under Article 50(2) of the EU AI Act, providers and deployers of AI systems generating
 * artificial audio, image, video or text content must ensure that the outputs of the AI system
 * are marked in a machine-readable format and detectable as artificially generated or manipulated.
 */
public interface IAiActComplianceService {

    /**
     * Injects machine-readable Article 50 EU AI Act metadata into an existing image file (e.g. PNG).
     *
     * @param imagePath path to the image file to tag
     * @throws IOException if file reading or metadata writing fails
     */
    void injectComplianceMetadata(Path imagePath) throws IOException;

    /**
     * Exports an image to a destination directory with embedded Article 50 machine-readable metadata,
     * optionally overlaying a discreet visual badge ("✦ AI Generated • Art. 50 EU AI Act").
     *
     * @param sourceImage          path to the source image
     * @param destinationDir       directory where the compliant copy should be saved
     * @param applyVisibleWatermark whether to draw a subtle visual compliance label on the image
     * @return the path of the exported, compliant image file
     * @throws IOException if export fails
     */
    Path exportWithCompliance(Path sourceImage, Path destinationDir, boolean applyVisibleWatermark) throws IOException;

    /**
     * Checks if the specified image file already contains EU AI Act compliance metadata.
     *
     * @param imagePath path to the image
     * @return true if Article 50 metadata is present
     */
    boolean hasComplianceMetadata(Path imagePath);

    /**
     * Returns the standardized EU AI Act legal citation and disclosure string.
     *
     * @return human-readable compliance statement
     */
    String getComplianceNotice();
}
