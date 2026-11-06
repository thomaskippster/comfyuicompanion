package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.ICustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Enterprise implementation of {@link ICustomNodeManagerBridgeService}.
 * Bridges workflow analysis with ComfyUI-Manager's extension mappings and enables
 * 1-click installation of missing custom node packages.
 */
@Service
public class CustomNodeManagerBridgeService implements ICustomNodeManagerBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeManagerBridgeService.class);

    private final ConfigService configService;
    private final CustomNodeDatabaseLoader databaseLoader;
    private final CustomNodeInstaller installer;

    private final Map<String, CustomNodePackage> nodeClassMap = new ConcurrentHashMap<>();
    private volatile boolean isLoaded = false;
    private CompletableFuture<Void> loadingFuture;

    @Autowired
    public CustomNodeManagerBridgeService(@Autowired(required = false) ConfigService configService,
                                          @Autowired(required = false) CustomNodeDatabaseLoader databaseLoader,
                                          @Autowired(required = false) CustomNodeInstaller installer) {
        this.configService = configService;
        this.databaseLoader = databaseLoader != null ? databaseLoader : new CustomNodeDatabaseLoader();
        this.installer = installer != null ? installer : new CustomNodeInstaller(configService, null, null);
    }

    @Override
    public synchronized CompletableFuture<Void> ensureDatabaseLoadedAsync() {
        if (isLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        if (loadingFuture != null && !loadingFuture.isDone()) {
            return loadingFuture;
        }

        loadingFuture = CompletableFuture.runAsync(() -> {
            try {
                String comfyPath = configService != null ? configService.getComfyUIPath() : null;
                Map<String, CustomNodePackage> loaded = databaseLoader.loadNodeClassMap(comfyPath);
                nodeClassMap.clear();
                nodeClassMap.putAll(loaded);
                isLoaded = true;
                logger.info("CustomNodeManagerBridgeService successfully initialized with {} mapped nodes.", nodeClassMap.size());
            } catch (Exception e) {
                logger.error("Error loading ComfyUI-Manager database: {}", e.getMessage(), e);
            }
        });

        return loadingFuture;
    }

    @Override
    public boolean isDatabaseLoaded() {
        return isLoaded;
    }

    @Override
    public Optional<CustomNodePackage> resolveNodePackage(String nodeClassName) {
        if (nodeClassName == null || nodeClassName.isBlank()) {
            return Optional.empty();
        }
        if (!isLoaded) {
            try {
                ensureDatabaseLoadedAsync().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.debug("Database load wait interrupted or timed out: {}", e.getMessage());
            }
        }

        String lowerKey = nodeClassName.toLowerCase(Locale.ROOT).trim();
        CustomNodePackage pkg = nodeClassMap.get(lowerKey);
        if (pkg == null) {
            pkg = resolveFuzzy(lowerKey);
        }
        return Optional.ofNullable(pkg);
    }

    private CustomNodePackage resolveFuzzy(String lowerKey) {
        if (lowerKey.contains("rgthree")) {
            CustomNodePackage p = nodeClassMap.get("rgthreeseed");
            if (p != null) return p;
            return new CustomNodePackage("rgthree-comfy", "https://github.com/rgthree/rgthree-comfy", List.of(lowerKey), List.of());
        }
        if (lowerKey.contains("mikey") || lowerKey.contains("ratio select")) {
            CustomNodePackage p = nodeClassMap.get("empty latent ratio select sdxl");
            if (p != null) return p;
            return new CustomNodePackage("mikey_nodes", "https://github.com/bash-j/mikey_nodes", List.of(lowerKey), List.of());
        }

        // Try stripping parenthetical suffixes: e.g. "Seed (rgthree)" -> "seed"
        int paren = lowerKey.indexOf('(');
        if (paren > 0) {
            String stripped = lowerKey.substring(0, paren).trim();
            CustomNodePackage p = nodeClassMap.get(stripped);
            if (p != null) return p;
        }

        // Try removing spaces: e.g. "empty latent ratio select sdxl" -> "emptylatentratioselectsdxl"
        String noSpaces = lowerKey.replaceAll("[\\s_-]+", "");
        for (Map.Entry<String, CustomNodePackage> entry : nodeClassMap.entrySet()) {
            if (entry.getKey().replaceAll("[\\s_-]+", "").equals(noSpaces)) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Override
    public Map<CustomNodePackage, List<String>> resolvePackagesForNodes(Collection<String> nodeClassNames) {
        Map<CustomNodePackage, List<String>> result = new LinkedHashMap<>();
        if (nodeClassNames == null || nodeClassNames.isEmpty()) {
            return result;
        }

        for (String nodeClass : nodeClassNames) {
            resolveNodePackage(nodeClass).ifPresent(pkg -> {
                result.computeIfAbsent(pkg, k -> new ArrayList<>()).add(nodeClass);
            });
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> installPackageAsync(CustomNodePackage pkg, Consumer<String> progress) {
        return CompletableFuture.supplyAsync(() -> installer.installPackage(pkg, progress));
    }

    @Override
    public CompletableFuture<Boolean> installPackagesAsync(List<CustomNodePackage> packages, Consumer<String> progress) {
        return CompletableFuture.supplyAsync(() -> {
            Consumer<String> out = progress != null ? progress : s -> {};
            if (packages == null || packages.isEmpty()) {
                out.accept("No packages specified for installation.");
                return true;
            }

            out.accept("🚀 Starting installation of " + packages.size() + " custom node package(s)...");
            boolean allSucceeded = true;
            for (CustomNodePackage pkg : packages) {
                boolean ok = installer.installPackage(pkg, progress);
                if (!ok) {
                    allSucceeded = false;
                }
            }
            return allSucceeded;
        });
    }
}
