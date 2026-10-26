package de.tki.comfyuicompanion.service.impl;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Service managing persistence, encryption, decryption, and migration
 * of companion settings and the encrypted credentials vault.
 */
@Service
public class VaultStorageService {
    private static final Logger logger = LoggerFactory.getLogger(VaultStorageService.class);

    private static final String[] LEGACY_VAULT_KEYS = {
        "hf_token", "models_path", "archive_path",
        "background_mode", "shutdown_after_download", "comfyui_path",
        "python_path", "comfy_launch_command", "comfy_working_dir",
        "restart_after_download", "comfyui_url", "api_token"
    };

    /**
     * Result of an encrypted vault unlock or initialization operation.
     *
     * @param settings decrypted or initialized JSON settings
     * @param masterPassword master password supplied to unlock the vault
     * @param vaultFresh {@code true} if an empty new vault had to be initialized
     */
    public record VaultUnlockResult(JSONObject settings, String masterPassword, boolean vaultFresh) {}

    /**
     * Loads plaintext persistent settings from disk.
     *
     * @param configFile the configuration file
     * @return loaded JSON settings or empty JSON object if file doesn't exist or fails
     */
    public JSONObject loadPersistentSettings(File configFile) {
        if (configFile != null && configFile.exists()) {
            try {
                String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                return new JSONObject(content);
            } catch (Exception e) {
                logger.error("Error loading persistent settings from {}: {}", configFile.getAbsolutePath(), e.getMessage(), e);
            }
        }
        return new JSONObject();
    }

    /**
     * Saves plaintext persistent settings to disk.
     *
     * @param configFile target file
     * @param persistentSettings JSON object to write
     */
    public void savePersistentSettings(File configFile, JSONObject persistentSettings) {
        if (configFile == null || persistentSettings == null) {
            return;
        }
        try {
            Files.writeString(configFile.toPath(), persistentSettings.toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error saving persistent settings to {}: {}", configFile.getAbsolutePath(), e.getMessage(), e);
        }
    }

    /**
     * Unlocks an existing vault or creates a new vault (migrating legacy settings if present).
     *
     * @param vaultFile the vault file
     * @param configFile the plaintext config file for legacy migration
     * @param password master password
     * @param encryptionUtils crypto utility
     * @return unlock result with settings and vault freshness state
     * @throws Exception if password is invalid or vault cannot be decrypted
     */
    public VaultUnlockResult unlock(File vaultFile, File configFile, String password, EncryptionUtils encryptionUtils) throws Exception {
        logger.info("Attempting to unlock vault at: {}", vaultFile.getAbsolutePath());

        if (vaultFile.exists()) {
            String encrypted = Files.readString(vaultFile.toPath(), StandardCharsets.UTF_8);
            try {
                String decrypted = encryptionUtils.decrypt(encrypted, password);
                JSONObject decryptedJson = new JSONObject(decrypted);
                logger.info("Vault unlocked successfully. Entries count: {}", decryptedJson.keySet().size());

                boolean needsSave = sanitizeSettings(decryptedJson);
                if (needsSave) {
                    saveVault(vaultFile, decryptedJson, password, encryptionUtils);
                }
                return new VaultUnlockResult(decryptedJson, password, false);
            } catch (Exception e) {
                logger.error("Failed to unlock vault: {}", e.getMessage(), e);
                throw new Exception("Wrong password or corrupted vault!");
            }
        } else {
            JSONObject migratedSettings = migrateLegacySettingsIfPresent(configFile);
            boolean isFresh = false;
            if (migratedSettings != null) {
                saveVault(vaultFile, migratedSettings, password, encryptionUtils);
            } else {
                logger.info("No vault found, initialized new empty vault at: {}", vaultFile.getAbsolutePath());
                migratedSettings = new JSONObject();
                isFresh = true;
                saveVault(vaultFile, migratedSettings, password, encryptionUtils);
            }

            boolean needsSave = sanitizeSettings(migratedSettings);
            if (needsSave) {
                saveVault(vaultFile, migratedSettings, password, encryptionUtils);
            }

            return new VaultUnlockResult(migratedSettings, password, isFresh);
        }
    }

    /**
     * Encrypts and saves settings to the vault file.
     *
     * @param vaultFile target vault file
     * @param settings settings to encrypt
     * @param masterPassword encryption password
     * @param encryptionUtils crypto utility
     */
    public void saveVault(File vaultFile, JSONObject settings, String masterPassword, EncryptionUtils encryptionUtils) {
        if (masterPassword == null) {
            logger.error("Cannot save: Vault not unlocked.");
            return;
        }
        try {
            String encrypted = encryptionUtils.encrypt(settings.toString(), masterPassword);
            Files.writeString(vaultFile.toPath(), encrypted, StandardCharsets.UTF_8);
            logger.info("Vault saved successfully to: {}", vaultFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error saving vault to {}: {}", vaultFile.getAbsolutePath(), e.getMessage(), e);
        }
    }

    /**
     * Resets the encrypted vault file if present.
     *
     * @param vaultFile file to delete
     */
    public void resetVault(File vaultFile) {
        if (vaultFile != null && vaultFile.exists()) {
            boolean deleted = vaultFile.delete();
            logger.info("Vault file deletion result ({}): {}", vaultFile.getAbsolutePath(), deleted);
        }
    }

    /**
     * Loads pending downloads JSON string from the given file.
     *
     * @param file pending downloads file
     * @return contents or null if not found
     */
    public String loadPendingDownloads(File file) {
        if (file != null && file.exists()) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error("Error loading pending downloads from {}: {}", file.getAbsolutePath(), e.getMessage(), e);
            }
        }
        return null;
    }

    /**
     * Writes pending downloads JSON string to file.
     *
     * @param file target file
     * @param json downloads JSON payload
     */
    public void savePendingDownloads(File file, String json) {
        if (file == null || json == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error saving pending downloads to {}: {}", file.getAbsolutePath(), e.getMessage(), e);
        }
    }

    private boolean sanitizeSettings(JSONObject json) {
        boolean modified = false;
        String url = json.optString("comfyui_url", "");
        if (url.contains(":8000")) {
            json.put("comfyui_url", url.replace(":8000", ":8188"));
            modified = true;
        }
        String token = json.optString("api_token", "");
        if (token.isEmpty()) {
            json.put("api_token", UUID.randomUUID().toString());
            modified = true;
        }
        return modified;
    }

    private JSONObject migrateLegacySettingsIfPresent(File oldFile) {
        if (oldFile == null || !oldFile.exists()) {
            return null;
        }
        try {
            String content = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
            JSONObject oldJson = new JSONObject(content);
            JSONObject legacyVaultSettings = new JSONObject();

            for (String key : LEGACY_VAULT_KEYS) {
                if (oldJson.has(key)) {
                    legacyVaultSettings.put(key, oldJson.get(key));
                    oldJson.remove(key);
                }
            }

            if (!legacyVaultSettings.keySet().isEmpty()) {
                if (oldJson.keySet().isEmpty()) {
                    oldFile.delete();
                } else {
                    Files.writeString(oldFile.toPath(), oldJson.toString(4), StandardCharsets.UTF_8);
                }
                logger.info("Migrated old settings to encrypted vault.");
                return legacyVaultSettings;
            }
        } catch (Exception e) {
            logger.error("Failed to migrate old settings: {}", e.getMessage());
        }
        return null;
    }
}
