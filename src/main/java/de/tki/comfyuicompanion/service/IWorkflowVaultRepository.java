package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Enterprise repository interface for managing curated user workflows, paired local previews,
 * and sidecar metadata in My Workflow Vault (stored in {@code user_workflows/}).
 */
public interface IWorkflowVaultRepository {

    /**
     * Loads and indexes all workflow entries currently present in the Vault directory.
     *
     * @return list of vault blueprint entries
     */
    List<BlueprintEntry> loadAllVaultEntries();

    /**
     * Saves or updates a workflow in the Vault with optional local image bytes and metadata sidecar.
     *
     * @param baseName     the sanitized base file name (without extension)
     * @param jsonContent  the workflow JSON string
     * @param imageBytes   optional raw image bytes for the thumbnail (can be null)
     * @param imageExt     image extension (e.g., "png", "jpg", "webp")
     * @param metadata     rich sidecar metadata describing the entry
     * @return the created or updated BlueprintEntry
     */
    BlueprintEntry saveWorkflowToVault(String baseName, String jsonContent, byte[] imageBytes, String imageExt, VaultWorkflowMetadata metadata);

    /**
     * Imports an existing file (JSON or image with embedded workflow) into the Vault.
     *
     * @param sourceFile source file to import
     * @return the resulting BlueprintEntry
     * @throws Exception if extraction or saving fails
     */
    BlueprintEntry importFileToVault(File sourceFile) throws Exception;

    /**
     * Checks whether a workflow with the given title or base name already exists in the Vault.
     *
     * @param titleOrBaseName title or filename to check
     * @return true if present in Vault, false otherwise
     */
    boolean isWorkflowInVault(String titleOrBaseName);

    /**
     * Removes a workflow and its associated preview and metadata from the Vault.
     *
     * @param baseName the base filename or title of the workflow to delete
     * @return true if successfully removed, false otherwise
     */
    boolean removeWorkflowFromVault(String baseName);

    /**
     * Returns the base directory where Vault workflows are persisted.
     *
     * @return Vault directory
     */
    File getVaultDirectory();

    /**
     * Resolves the local preview image file for a given vault entry if present.
     *
     * @param baseName base name of the workflow
     * @return optional file pointing to the preview image
     */
    Optional<File> resolvePreviewImage(String baseName);
}
