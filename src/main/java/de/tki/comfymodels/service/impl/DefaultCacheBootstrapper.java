package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IConfigService;
import de.tki.comfymodels.service.IDefaultCacheBootstrapper;
import de.tki.comfymodels.service.SafePathValidator;
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
public class DefaultCacheBootstrapper implements IDefaultCacheBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCacheBootstrapper.class);

    private final IConfigService configService;
    private final SafePathValidator pathValidator;

    public record BundledCacheResource(String classpathResource, String relativeTargetPath) {}

    private static final List<BundledCacheResource> BUNDLED_RESOURCES = List.of(
            new BundledCacheResource("/default_cache/templates/comfyui/blueprint_scan_results.json", "templates/comfyui/blueprint_scan_results.json"),
            new BundledCacheResource("/default_cache/templates/comfyui/model_resolved_defaults.json", "templates/comfyui/model_resolved_defaults.json"),
            new BundledCacheResource("/default_cache/templates/comfyui/registry_models_cache.json", "templates/comfyui/registry_models_cache.json"),
            new BundledCacheResource("/default_cache/templates/comfyui/cloud_only_cache.json", "templates/comfyui/cloud_only_cache.json"),
            new BundledCacheResource("/default_cache/templates/comfyui/model_architecture_mapping.json", "templates/comfyui/model_architecture_mapping.json"),
            new BundledCacheResource("/default_cache/cache/registry_index.json", "cache/registry_index.json")
    );

    @Autowired
    public DefaultCacheBootstrapper(@Autowired(required = false) IConfigService configService,
                                   @Autowired(required = false) SafePathValidator pathValidator) {
        this.configService = configService;
        this.pathValidator = pathValidator != null ? pathValidator : new SafePathValidator();
    }

    @PostConstruct
    public void init() {
        bootstrapDefaultCache();
    }

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

    @Override
    public synchronized void bootstrapDefaultCache(Path targetBaseDirectory) {
        if (targetBaseDirectory == null) {
            return;
        }

        Path normalizedBase = targetBaseDirectory.toAbsolutePath().normalize();
        for (BundledCacheResource item : BUNDLED_RESOURCES) {
            try {
                Path targetPath = pathValidator.validateWithinBase(normalizedBase, normalizedBase.resolve(item.relativeTargetPath()));

                // Idempotency: Skip copying if file already exists and has content
                if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
                    logger.debug("Default cache artifact already present: {}", targetPath);
                    continue;
                }

                // Ensure parent directory exists safely
                if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                    Files.createDirectories(targetPath.getParent());
                }

                try (InputStream is = getClass().getResourceAsStream(item.classpathResource())) {
                    if (is == null) {
                        logger.warn("Bundled cache resource not found in classpath: {}", item.classpathResource());
                        continue;
                    }

                    Path tempFile = Files.createTempFile(targetPath.getParent(), "cache_bootstrap_", ".tmp");
                    try {
                        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        try {
                            Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception moveEx) {
                            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        logger.info("⚡ [Bootstrapper] Initialized default cache: {} -> {}", item.classpathResource(), targetPath.getFileName());
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                }
            } catch (Exception e) {
                logger.error("❌ [Bootstrapper] Failed to seed default cache resource '{}': {}", item.classpathResource(), e.getMessage(), e);
            }
        }
    }
}
