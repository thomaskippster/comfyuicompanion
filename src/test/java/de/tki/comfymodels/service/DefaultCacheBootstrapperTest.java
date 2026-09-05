package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.DefaultCacheBootstrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultCacheBootstrapperTest {

    @TempDir
    Path tempDir;

    private DefaultCacheBootstrapper bootstrapper;
    private ConfigService configService;

    @BeforeEach
    public void setup() {
        configService = new ConfigService(null, null) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        bootstrapper = new DefaultCacheBootstrapper(configService, new SafePathValidator());
    }

    @Test
    public void testBootstrapDefaultCacheSeedsAllExpectedFiles() throws IOException {
        bootstrapper.bootstrapDefaultCache(tempDir);

        Path blueprints = tempDir.resolve("templates/comfyui/blueprint_scan_results.json");
        Path defaults = tempDir.resolve("templates/comfyui/model_resolved_defaults.json");
        Path registryModels = tempDir.resolve("templates/comfyui/registry_models_cache.json");
        Path cloudOnly = tempDir.resolve("templates/comfyui/cloud_only_cache.json");
        Path mapping = tempDir.resolve("templates/comfyui/model_architecture_mapping.json");
        Path registryIndex = tempDir.resolve("cache/registry_index.json");

        assertTrue(Files.exists(blueprints), "blueprint_scan_results.json should be seeded");
        assertTrue(Files.size(blueprints) > 0, "blueprint_scan_results.json should not be empty");

        assertTrue(Files.exists(defaults), "model_resolved_defaults.json should be seeded");
        assertTrue(Files.size(defaults) > 0, "model_resolved_defaults.json should not be empty");

        assertTrue(Files.exists(registryModels), "registry_models_cache.json should be seeded");
        assertTrue(Files.size(registryModels) > 0, "registry_models_cache.json should not be empty");

        assertTrue(Files.exists(cloudOnly), "cloud_only_cache.json should be seeded");
        assertTrue(Files.size(cloudOnly) > 0, "cloud_only_cache.json should not be empty");

        assertTrue(Files.exists(mapping), "model_architecture_mapping.json should be seeded");
        assertTrue(Files.size(mapping) > 0, "model_architecture_mapping.json should not be empty");

        assertTrue(Files.exists(registryIndex), "registry_index.json should be seeded");
        assertTrue(Files.size(registryIndex) > 0, "registry_index.json should not be empty");
    }

    @Test
    public void testBootstrapDefaultCacheIsIdempotent() throws IOException {
        Path blueprints = tempDir.resolve("templates/comfyui/blueprint_scan_results.json");
        Files.createDirectories(blueprints.getParent());
        String customContent = "[{\"custom\": true}]";
        Files.writeString(blueprints, customContent);

        bootstrapper.bootstrapDefaultCache(tempDir);

        assertEquals(customContent, Files.readString(blueprints), "Existing cache file must not be overwritten");
    }
}
