package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.EncryptionUtils;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VaultIntegrationTest {

    @TempDir
    Path tempDir;

    private final EncryptionUtils encryptionUtils = new EncryptionUtils();

    @Test
    public void testVaultSaveAndLoad() throws Exception {
        // Setup ConfigService with temp directory
        ConfigService configService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        String password = "1234";
        String testKey = "4711";

        // 1. Initial unlock (creates new vault)
        configService.unlock(password);
        
        // 2. Set key and save
        configService.setCivitaiApiKey(testKey);
        configService.save();

        // 3. Create NEW instance to simulate restart
        ConfigService newConfigService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        // 4. Unlock with correct password
        newConfigService.unlock(password);
        
        // 5. Verify key and dark mode
        assertEquals(testKey, newConfigService.getCivitaiApiKey(), "Key should be 4711 after loading");
        assertTrue(newConfigService.isDarkMode(), "Default should be true");
        
        newConfigService.setDarkMode(false);
        
        // 5a. Reload again to verify dark mode persistence
        ConfigService restartService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        restartService.unlock(password);
        assertFalse(restartService.isDarkMode(), "Dark mode should be false after reload");

        // 6. Verify wrong password fails
        ConfigService wrongPassService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        
        assertThrows(Exception.class, () -> {
            wrongPassService.unlock("wrong_password");
        }, "Unlock should fail with wrong password");
    }

    @Test
    public void testVaultResetAndReunlockPreservesPersistentSettings() throws Exception {
        // 1. Setup ConfigService with temp directory
        ConfigService configService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };

        // 2. Set persistent settings (e.g. dark_mode = false) and verify they are saved
        configService.setDarkMode(false);
        assertFalse(configService.isDarkMode(), "Dark mode should be set to false");

        // 3. Unlock vault for the first time
        String password = "my_secure_password";
        configService.unlock(password);
        configService.setCivitaiApiKey("my-civitai-key");
        
        // 4. Reset vault
        configService.resetVault();
        assertFalse(configService.hasVault(), "Vault file should be deleted after reset");

        // 5. Re-unlock vault
        ConfigService reconfigService = new ConfigService(encryptionUtils, new PathResolver()) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        reconfigService.unlock(password);

        // 6. Verify vault is unlocked, fresh/empty (doesn't contain key), and dark_mode is still false!
        assertTrue(reconfigService.isUnlocked(), "Vault should be unlocked after re-unlock");
        assertEquals("", reconfigService.getCivitaiApiKey(), "API key should be empty since vault was reset");
        assertFalse(reconfigService.isDarkMode(), "Dark mode persistent setting should be preserved as false!");
        assertTrue(new File(tempDir.toFile(), "app_settings.json").exists(), "app_settings.json must exist and not be deleted!");
    }

    @Test
    public void testGcmAndLegacyCbcCompatibility() throws Exception {
        String secret = "sensitive_api_token_12345";
        String password = "master_vault_password";

        // 1. New encryption must use GCM format
        String encryptedGcm = encryptionUtils.encrypt(secret, password);
        assertTrue(encryptedGcm.startsWith("$GCM$v1$"), "Modern cipher must start with $GCM$v1$ prefix");
        
        // 2. Decrypt GCM
        String decryptedGcm = encryptionUtils.decrypt(encryptedGcm, password);
        assertEquals(secret, decryptedGcm);

        // 3. Encrypt legacy CBC manually with legacy static salt to simulate existing user vault
        byte[] legacySalt = "ComfyUI-Vault-Salt-2026".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.spec.IvParameterSpec ivspec = new javax.crypto.spec.IvParameterSpec(iv);

        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), legacySalt, 65536, 256);
        javax.crypto.SecretKey tmp = factory.generateSecret(spec);
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(tmp.getEncoded(), "AES");

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, ivspec);
        byte[] encryptedBytes = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
        String legacyCiphertext = java.util.Base64.getEncoder().encodeToString(combined);

        // 4. Decrypt legacy CBC via modern EncryptionUtils
        String decryptedLegacy = encryptionUtils.decrypt(legacyCiphertext, password);
        assertEquals(secret, decryptedLegacy, "Legacy CBC vault ciphertext must be seamlessly decrypted");
    }
}
