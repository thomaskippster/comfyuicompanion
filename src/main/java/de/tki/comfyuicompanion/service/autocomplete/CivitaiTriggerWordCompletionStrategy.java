package de.tki.comfyuicompanion.service.autocomplete;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Strategy implementation that scans local {@code .civitai.info} sidecar metadata files
 * and suggests model-specific {@code trainedWords} (trigger words) matching the active token.
 */
@Component
public class CivitaiTriggerWordCompletionStrategy implements IPromptCompletionStrategy {

    @Override
    public List<PromptCompletionCandidate> suggest(String activeToken, Path modelsBaseDir) {
        if (activeToken == null || modelsBaseDir == null || !Files.isDirectory(modelsBaseDir)) {
            return Collections.emptyList();
        }
        String query = activeToken.trim().toLowerCase(Locale.ROOT);
        if (query.length() < 2) {
            return Collections.emptyList();
        }

        Set<String> matchedTriggers = new LinkedHashSet<>();
        for (String sub : new String[]{"loras", "checkpoints"}) {
            Path dir = modelsBaseDir.resolve(sub);
            if (!Files.isDirectory(dir)) {
                dir = modelsBaseDir.resolve("models").resolve(sub);
            }
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> stream = Files.walk(dir, 2)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".civitai.info"))
                        .forEach(infoFile -> collectTriggersFromInfoFile(infoFile, query, matchedTriggers));
            } catch (Exception ignored) {
                // Ignore inaccessible subdirectories
            }
        }

        List<PromptCompletionCandidate> candidates = new ArrayList<>();
        for (String trigger : matchedTriggers) {
            boolean startsWith = trigger.toLowerCase(Locale.ROOT).startsWith(query);
            candidates.add(PromptCompletionCandidate.builder()
                    .insertText(trigger)
                    .displayText(trigger + " [Trigger]")
                    .category("Trigger")
                    .priority(startsWith ? 90 : 80)
                    .build());
        }
        return candidates;
    }

    private static void collectTriggersFromInfoFile(Path infoFile, String query, Set<String> out) {
        try {
            String raw = Files.readString(infoFile, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(raw);
            JSONArray trainedWords = obj.optJSONArray("trainedWords");
            if (trainedWords == null) return;
            for (int i = 0; i < trainedWords.length(); i++) {
                String word = trainedWords.optString(i, "").trim();
                if (!word.isEmpty() && word.toLowerCase(Locale.ROOT).contains(query)) {
                    out.add(word);
                }
            }
        } catch (Exception ignored) {
            // Skip malformed sidecar JSON
        }
    }
}
