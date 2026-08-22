package de.tki.comfymodels.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtraComfyUIPathResolutionTest {

    @TempDir
    Path tempDir;

    private ConfigService configService;
    private PathResolver pathResolver;

    @BeforeEach
    public void setup() {
        pathResolver = new PathResolver();
        configService = new ConfigService(new EncryptionUtils(), pathResolver) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }

            @Override
            public boolean isUnlocked() {
                return true;
            }

            @Override
            public void save() {
                // Avoid encryption overhead in test
            }
        };
    }

    @Test
    public void testModelsPathResolvedAsSubfolderOfExtraComfyUIPath() {
        Path extraDataPath = tempDir.resolve("comfyuidata");
        configService.setExtraComfyUIPath(extraDataPath.toString());

        assertEquals(extraDataPath.toString(), configService.getExtraComfyUIPath());
        assertEquals(extraDataPath.resolve("models").toAbsolutePath().toString(), configService.getModelsPath());
        assertEquals(extraDataPath.resolve("input").toAbsolutePath().toString(), configService.getResolvedInputDetailDir());
        assertEquals(extraDataPath.resolve("output").toAbsolutePath().toString(), configService.getResolvedOutputDir());
    }

    @Test
    public void testModelsPathAvoidsDoubleNestingIfAlreadyEndsWithModels() {
        Path extraModelsPath = tempDir.resolve("comfyuidata").resolve("models");
        configService.setExtraComfyUIPath(extraModelsPath.toString());

        assertEquals(extraModelsPath.toAbsolutePath().toString(), configService.getModelsPath());
    }

    @Test
    public void testEnsureExtraComfyUIDirectoriesCreatesModelsInputOutput() {
        Path extraDataPath = tempDir.resolve("comfyuidata_created");
        configService.setExtraComfyUIPath(extraDataPath.toString());

        assertTrue(Files.exists(extraDataPath.resolve("models")), "models directory should be created");
        assertTrue(Files.exists(extraDataPath.resolve("input")), "input directory should be created");
        assertTrue(Files.exists(extraDataPath.resolve("output")), "output directory should be created");
    }

    @Test
    public void testUpdateExtraModelPathsYamlUsesModelsSubfolderAsBasePath() throws IOException {
        Path comfyRoot = tempDir.resolve("ComfyUI");
        Files.createDirectories(comfyRoot);
        configService.setComfyUIPath(comfyRoot.toString());

        Path extraDataPath = tempDir.resolve("comfyuidata");
        configService.setExtraComfyUIPath(extraDataPath.toString());

        configService.updateExtraModelPathsYaml();

        Path yamlFile = comfyRoot.resolve("extra_model_paths.yaml");
        assertTrue(Files.exists(yamlFile), "extra_model_paths.yaml should be created");

        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(yamlFile)) {
            Map<String, Object> rootMap = yaml.load(is);
            assertNotNull(rootMap);
            assertTrue(rootMap.containsKey("comfyui_companion"));

            @SuppressWarnings("unchecked")
            Map<String, Object> companionMap = (Map<String, Object>) rootMap.get("comfyui_companion");
            String basePath = (String) companionMap.get("base_path");

            String expectedBasePath = extraDataPath.resolve("models").toAbsolutePath().toString().replace("\\", "/");
            assertEquals(expectedBasePath, basePath, "base_path in extra_model_paths.yaml must point to the models subfolder");
        }
    }
}
