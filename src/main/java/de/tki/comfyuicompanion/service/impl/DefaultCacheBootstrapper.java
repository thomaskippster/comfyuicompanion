package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.IDefaultCacheBootstrapper;
import de.tki.comfyuicompanion.service.SafePathValidator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Enterprise bootstrapper service responsible for seeding pre-computed, non-sensitive
 * cache artifacts from classpath resources (/default_cache/...) into the user's
 * application directory upon startup, avoiding heavy cold-start CPU scanning loops.
 */
@Service
@Order(1)
/**
 * Represents the default cache bootstrapper class.
 */
public class DefaultCacheBootstrapper implements IDefaultCacheBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCacheBootstrapper.class);

    private final IConfigService configService;
    private final SafePathValidator pathValidator;

    /**
     * Represents a bundled cache resource mapped to its relative target path.
     * @param classpathResource the source path within the classpath
     * @param relativeTargetPath the destination path relative to the app data base directory
     */
    private static final String REGISTRY_INDEX_URL = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/index.json";

    private static final List<String> SCHEMA_FILES = List.of(
            "templates/comfyui/blueprint_scan_results.json",
            "templates/comfyui/model_resolved_defaults.json",
            "templates/comfyui/registry_models_cache.json",
            "templates/comfyui/cloud_only_cache.json",
            "templates/comfyui/model_architecture_mapping.json",
            "cache/registry_index.json"
    );

    /**
     * Constructs a new DefaultCacheBootstrapper.
     *
     * @param configService the configuration service to retrieve app data paths
     * @param pathValidator the validator to ensure safe path resolutions
     */
    @Autowired
    public DefaultCacheBootstrapper(@Autowired(required = false) IConfigService configService,
                                   @Autowired(required = false) SafePathValidator pathValidator) {
        this.configService = configService;
        this.pathValidator = pathValidator != null ? pathValidator : new SafePathValidator();
    }

    /**
     * Initializes the default cache resources by bootstrapping them into the configured paths.
     */
    @PostConstruct
    public void init() {
        bootstrapDefaultCache();
    }

    /**
     * Bootstraps the default cache by copying resources to both the user's home directory
     * and the configured application data path if they differ.
     */
    @Override
    public synchronized void bootstrapDefaultCache() {
        // 1. Seed user home storage ~/.comfyui-companion
        Path userHomeAppData = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
        bootstrapDefaultCache(userHomeAppData);

        // 2. Also seed configured app data path if different
        if (configService != null && configService.getAppDataPath() != null) {
            Path configuredAppData = Paths.get(configService.getAppDataPath());
            if (!configuredAppData.toAbsolutePath().normalize().equals(userHomeAppData.toAbsolutePath().normalize())) {
                bootstrapDefaultCache(configuredAppData);
            }
        }
    }

    /**
     * Bootstraps the default cache resources into the specified target base directory.
     *
     * @param targetBaseDirectory the base directory where cache resources should be seeded
     */
    @Override
    public synchronized void bootstrapDefaultCache(Path targetBaseDirectory) {
        if (targetBaseDirectory == null) {
            return;
        }

        Path normalizedBase = targetBaseDirectory.toAbsolutePath().normalize();
        for (String relativePath : SCHEMA_FILES) {
            try {
                Path targetPath = pathValidator.validateWithinBase(normalizedBase, normalizedBase.resolve(relativePath));

                // Idempotency: Skip copying if file already exists and has content
                if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
                    logger.debug("Default cache artifact already present: {}", targetPath);
                    continue;
                }

                if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                    Files.createDirectories(targetPath.getParent());
                }

                String content;
                if ("cache/registry_index.json".equals(relativePath)) {
                    content = fetchRemoteRegistryIndex();
                } else if ("templates/comfyui/model_architecture_mapping.json".equals(relativePath)) {
                    content = "{\"sd15\":\"sd15\",\"sdxl\":\"sdxl\",\"flux\":\"flux\",\"wan\":\"wan\"}";
                } else if (relativePath.endsWith("defaults.json")) {
                    content = "{}";
                } else {
                    content = "[]";
                }

                Files.writeString(targetPath, content, java.nio.charset.StandardCharsets.UTF_8);
                logger.info("⚡ [Bootstrapper] Initialized dynamic cache file: {}", targetPath.getFileName());
            } catch (Exception e) {
                logger.error("❌ [Bootstrapper] Failed to seed cache file '{}': {}", relativePath, e.getMessage(), e);
            }
        }
    }

    private String fetchRemoteRegistryIndex() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(REGISTRY_INDEX_URL))
                    .timeout(java.time.Duration.ofSeconds(6))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                return resp.body();
            }
        } catch (Exception e) {
            logger.warn("Remote registry index download skipped (offline or timeout): {}", e.getMessage());
        }
        return "[]";
    }
}
