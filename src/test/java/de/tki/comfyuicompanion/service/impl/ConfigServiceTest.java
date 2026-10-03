package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private EncryptionUtils encryptionUtils;

    @Mock
    private PathResolver pathResolver;

    @Mock
    private ExtraModelPathsYamlSynchronizer yamlSynchronizer;

    @Mock
    private ComfyAutoDiscoveryService autoDiscoveryService;

    @InjectMocks
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        System.setProperty("comfyuicompanion.appdata", System.getProperty("java.io.tmpdir") + "/comfyuicompanion-test");
    }

    @Test
    void shouldReturnDefaultValues_whenVaultFresh() throws Exception {
        assertThat(configService.getComfyUIPath()).isNotNull();
        assertThat(configService.getMaxParallelDownloads()).isEqualTo(3);
        assertThat(configService.isHideComfyUI()).isTrue();
        assertThat(configService.isCivitaiGalleryEnabled()).isFalse();
    }

    @Test
    void shouldUnlock_whenValidPassword() throws Exception {
        String encrypted = "$GCM$v1$MOCKENCRYPTED";
        File vault = configService.getFileInAppData(de.tki.comfyuicompanion.util.ConfigConstants.VAULT_FILE);
        java.nio.file.Files.createDirectories(vault.toPath().getParent());
        java.nio.file.Files.writeString(vault.toPath(), encrypted);

        when(encryptionUtils.decrypt(anyString(), anyString())).thenReturn("{\"comfyui_url\":\"http://127.0.0.1:8188\"}");

        configService.unlock("mypassword");

        assertThat(configService.getComfyUIUrl()).isEqualTo("http://127.0.0.1:8188");
        assertThat(configService.isUnlocked()).isTrue();
    }
}
