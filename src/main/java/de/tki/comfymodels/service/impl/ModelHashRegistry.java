package de.tki.comfymodels.service.impl;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ModelHashRegistry {
    private final String HASH_FILE = "model_hashes.json";
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final Map<String, String> hashToPath = new HashMap<>();
    private boolean dirty = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ModelHashRegistry-Flusher");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private ConfigService configService;

    @Autowired
    private IModelValidator validator;

    private static class CacheEntry {
        String hash;
        long size;
        long lastModified;

        CacheEntry(String hash, long size, long lastModified) {
            this.hash = hash;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    @PostConstruct
    public synchronized void load() {
        try {
            File file = configService.getFileInAppData(HASH_FILE);
            if (file.exists()) {
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
            System.err.println("Error loading hash registry: " + e.getMessage());
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

    private synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public synchronized void save() {
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
                Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            dirty = false;
        } catch (Exception e) {
            System.err.println("Error saving hash registry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized String getOrCalculateHash(File file) {
        String path = file.getAbsolutePath();
        long currentSize = file.length();
        long currentLm = file.lastModified();

        if (cache.containsKey(path)) {
            CacheEntry entry = cache.get(path);
            if (entry.size == currentSize && entry.lastModified == currentLm) {
                return entry.hash;
            }
        }

        String hash = validator.calculateHash(file);
        if (hash != null) {
            cache.put(path, new CacheEntry(hash, currentSize, currentLm));
            hashToPath.put(hash, path);
            dirty = true;
        }
        return hash;
    }

    public synchronized Optional<String> findPathByHash(String hash) {
        return Optional.ofNullable(hashToPath.get(hash));
    }

    public synchronized void unregister(File file) {
        String path = file.getAbsolutePath();
        CacheEntry entry = cache.remove(path);
        if (entry != null) {
            hashToPath.remove(entry.hash);
            dirty = true;
        }
    }
}
