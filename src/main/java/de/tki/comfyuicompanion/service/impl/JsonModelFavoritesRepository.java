package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IModelFavoritesRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, NIO.2 atomic JSON repository implementation for persisting bookmarked/favorite models.
 */
@Repository
public class JsonModelFavoritesRepository implements IModelFavoritesRepository {

    private static final Logger logger = LoggerFactory.getLogger(JsonModelFavoritesRepository.class);
    private final Path storageFile;
    private final Set<String> favorites = new LinkedHashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Default constructor resolving storage path inside the user home application directory.
     */
    @Autowired
    public JsonModelFavoritesRepository() {
        this(Path.of(System.getProperty("user.home"), ".comfyui-companion", "model_favorites.json"));
    }

    /**
     * Constructor with explicit storage file path for testing and custom configurations.
     *
     * @param storageFile target JSON file path
     */
    public JsonModelFavoritesRepository(Path storageFile) {
        this.storageFile = storageFile;
        loadFromDisk();
    }

    @Override
    public boolean isFavorite(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) return false;
        String norm = normalize(modelKey);
        lock.readLock().lock();
        try {
            return favorites.contains(norm);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean toggleFavorite(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) return false;
        String norm = normalize(modelKey);
        lock.writeLock().lock();
        try {
            boolean added;
            if (favorites.contains(norm)) {
                favorites.remove(norm);
                added = false;
            } else {
                favorites.add(norm);
                added = true;
            }
            saveToDisk();
            return added;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void addFavorite(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) return;
        String norm = normalize(modelKey);
        lock.writeLock().lock();
        try {
            if (favorites.add(norm)) {
                saveToDisk();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeFavorite(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) return;
        String norm = normalize(modelKey);
        lock.writeLock().lock();
        try {
            if (favorites.remove(norm)) {
                saveToDisk();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getAllFavorites() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadFromDisk() {
        lock.writeLock().lock();
        try {
            if (storageFile == null || !Files.isRegularFile(storageFile)) {
                return;
            }
            String raw = Files.readString(storageFile, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.optJSONArray("favorites");
            if (arr != null) {
                favorites.clear();
                for (int i = 0; i < arr.length(); i++) {
                    String val = arr.optString(i, "").trim();
                    if (!val.isEmpty()) {
                        favorites.add(normalize(val));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read model favorites from {}: {}", storageFile, e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveToDisk() {
        if (storageFile == null) return;
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (String item : favorites) {
                arr.put(item);
            }
            root.put("favorites", arr);
            Path tmp = storageFile.resolveSibling(storageFile.getFileName().toString() + ".tmp");
            Files.writeString(tmp, root.toString(2), StandardCharsets.UTF_8);
            Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            logger.warn("Failed to persist model favorites to {}: {}", storageFile, e.getMessage());
        }
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
