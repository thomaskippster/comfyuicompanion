package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.service.IModelValidator;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ModelHashRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ModelHashRegistry.class);
    private static final String HASH_FILE = "model_hashes.json";

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, String> hashToPath = new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private volatile boolean dirty = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Thread.ofVirtual().name("ModelHashRegistry-Flusher").unstarted(r);
        return t;
    });

    private ConfigService configService;
    private IModelValidator validator;

    public ModelHashRegistry() {}

    @Autowired
    public ModelHashRegistry(ConfigService configService, IModelValidator validator) {
        this.configService = configService;
        this.validator = validator;
    }

    private static class CacheEntry {
        final String hash;
        final long size;
        final long lastModified;

        CacheEntry(String hash, long size, long lastModified) {
            this.hash = hash;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    @PostConstruct
    public void load() {
        if (configService == null) {
            logger.warn("ConfigService not injected yet in ModelHashRegistry");
            return;
        }

        try {
            File file = configService.getFileInAppData(HASH_FILE);
            if (file != null && file.exists()) {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                for (String path : json.keySet()) {
                    JSONObject entry = json.getJSONObject(path);
                    String hash = entry.getString("hash");
                    long size = entry.optLong("size", 0);
                    long lm = entry.optLong("lastModified", 0);

                    if (new File(path).exists()) {
                        cache.put(path, new CacheEntry(hash, size, lm));
                        hashToPath.put(hash, path);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error loading hash registry: {}", e.getMessage(), e);
        }

        // Schedule periodic save check every 5 seconds
        scheduler.scheduleWithFixedDelay(this::saveIfDirty, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        saveIfDirty();
    }

    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public void save() {
        synchronized (saveLock) {
            if (configService == null) return;
            try {
                JSONObject json = new JSONObject();
                for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("hash", e.getValue().hash);
                    obj.put("size", e.getValue().size);
                    obj.put("lastModified", e.getValue().lastModified);
                    json.put(e.getKey(), obj);
                }

                File targetFile = configService.getFileInAppData(HASH_FILE).getAbsoluteFile();
                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                Path targetPath = targetFile.toPath();
                Path tempPath = targetPath.getParent().resolve(HASH_FILE + ".tmp");
                Files.writeString(tempPath, json.toString(2), StandardCharsets.UTF_8);
                try {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception moveEx) {
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                dirty = false;
            } catch (Exception e) {
                logger.error("Error saving hash registry: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves cached hash or calculates it without holding a global lock during heavy I/O.
     */
    public String getOrCalculateHash(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        String path = file.getAbsolutePath();
        long currentSize = file.length();
        long currentLm = file.lastModified();

        CacheEntry entry = cache.get(path);
        if (entry != null && entry.size == currentSize && entry.lastModified == currentLm) {
            return entry.hash;
        }

        if (validator == null) {
            logger.warn("IModelValidator not configured in ModelHashRegistry");
            return null;
        }

        // Heavy I/O is performed without holding a global lock
        String hash = validator.calculateHash(file);
        if (hash != null) {
            cache.put(path, new CacheEntry(hash, currentSize, currentLm));
            hashToPath.put(hash, path);
            dirty = true;
        }
        return hash;
    }

    public Optional<String> findPathByHash(String hash) {
        if (hash == null) return Optional.empty();
        return Optional.ofNullable(hashToPath.get(hash));
    }

    public void unregister(File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        CacheEntry entry = cache.remove(path);
        if (entry != null) {
            hashToPath.remove(entry.hash);
            dirty = true;
        }
    }
}
