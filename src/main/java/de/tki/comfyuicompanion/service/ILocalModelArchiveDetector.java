package de.tki.comfyuicompanion.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for auto-discovering secondary or external model archive directories
 * on the local filesystem (e.g. secondary drives, archive storage, custom model repositories).
 */
public interface ILocalModelArchiveDetector {

    /**
     * Discovers all candidate root model archive directories across local drives and system paths.
     *
     * @return list of confirmed archive root directories containing recognized model structures
     */
    List<Path> detectArchiveRoots();

    /**
     * Resolves candidate directories for a specific model folder category (e.g. "clip", "checkpoints", "unet").
     *
     * @param folderName category folder name
     * @return list of existing directories across all detected archives
     */
    List<Path> resolveArchiveDirectoriesForCategory(String folderName);
}
