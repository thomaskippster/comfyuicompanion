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
        configService.setGeminiApiKey(testKey);
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
        assertEquals(testKey, newConfigService.getGeminiApiKey(), "Key should be 4711 after loading");
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
        configService.setGeminiApiKey("my-gemini-key");
        
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
        assertEquals("", reconfigService.getGeminiApiKey(), "API key should be empty since vault was reset");
        assertFalse(reconfigService.isDarkMode(), "Dark mode persistent setting should be preserved as false!");
        assertTrue(new File(tempDir.toFile(), "app_settings.json").exists(), "app_settings.json must exist and not be deleted!");
    }
}
