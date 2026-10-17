package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.PromptHistoryEntry;
import de.tki.comfyuicompanion.service.IPromptHistoryService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service that stores and manages the history of recent Image Lab prompt sessions,
 * persisting them to the application data directory.
 */
public class PromptHistoryService implements IPromptHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(PromptHistoryService.class);
    private static final int MAX_HISTORY_CAP = 25;
    private static final String HISTORY_FILENAME = "prompt_history.json";

    private final File historyFile;
    private final List<PromptHistoryEntry> history = new ArrayList<>();
    private boolean loaded = false;

    /**
     * Constructs the history service using ConfigService to resolve the storage location.
     *
     * @param configService the configuration provider
     */
    public PromptHistoryService(ConfigService configService) {
        this(configService != null ? configService.getFileInAppData(HISTORY_FILENAME) : new File(HISTORY_FILENAME));
    }

    /**
     * Constructs the history service with an explicit storage file (for testing).
     *
     * @param historyFile file where history JSON is persisted
     */
    public PromptHistoryService(File historyFile) {
        this.historyFile = historyFile;
    }

    @Override
    public synchronized void recordPrompt(PromptHistoryEntry entry) {
        if (entry == null) return;
        ensureLoaded();

        // Avoid exact duplicate at the top
        if (!history.isEmpty()) {
            PromptHistoryEntry top = history.get(0);
            if (isSamePrompt(top, entry)) {
                return;
            }
        }

        history.add(0, entry);
        while (history.size() > MAX_HISTORY_CAP) {
            history.remove(history.size() - 1);
        }

        saveToDiskAsync();
    }

    @Override
    public synchronized List<PromptHistoryEntry> getHistory() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    @Override
    public synchronized void clearHistory() {
        ensureLoaded();
        history.clear();
        saveToDiskAsync();
    }

    private boolean isSamePrompt(PromptHistoryEntry a, PromptHistoryEntry b) {
        return java.util.Objects.equals(a.positivePrompt(), b.positivePrompt())
                && java.util.Objects.equals(a.negativePrompt(), b.negativePrompt())
                && java.util.Objects.equals(a.model(), b.model());
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (historyFile == null || !historyFile.exists()) return;

        try {
            String content = Files.readString(historyFile.toPath(), StandardCharsets.UTF_8);
            if (content.isBlank()) return;

            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                history.add(new PromptHistoryEntry(
                        obj.optLong("timestamp", System.currentTimeMillis()),
                        obj.optString("positivePrompt", ""),
                        obj.optString("negativePrompt", ""),
                        obj.optString("model", ""),
                        obj.optInt("width", 512),
                        obj.optInt("height", 512),
                        obj.optInt("steps", 20),
                        obj.optDouble("cfg", 7.0),
                        obj.optString("sampler", "euler"),
                        obj.optString("scheduler", "normal")
                ));
            }
        } catch (Exception e) {
            logger.warn("Failed to load prompt history from {}: {}", historyFile.getAbsolutePath(), e.getMessage());
        }
    }

    private void saveToDiskAsync() {
        final List<PromptHistoryEntry> snapshot = new ArrayList<>(history);
        Thread.ofVirtual().name("prompt-history-saver").start(() -> {
            try {
                if (historyFile == null) return;
                File parent = historyFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                JSONArray arr = new JSONArray();
                for (PromptHistoryEntry item : snapshot) {
                    JSONObject obj = new JSONObject();
                    obj.put("timestamp", item.timestamp());
                    obj.put("positivePrompt", item.positivePrompt());
                    obj.put("negativePrompt", item.negativePrompt());
                    obj.put("model", item.model());
                    obj.put("width", item.width());
                    obj.put("height", item.height());
                    obj.put("steps", item.steps());
                    obj.put("cfg", item.cfg());
                    obj.put("sampler", item.sampler());
                    obj.put("scheduler", item.scheduler());
                    arr.put(obj);
                }

                Files.writeString(historyFile.toPath(), arr.toString(2), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("Failed to persist prompt history: {}", e.getMessage());
            }
        });
    }
}
