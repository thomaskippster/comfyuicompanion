package de.tki.comfyuicompanion.service.autocomplete;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Strategy implementation that provides {@code embedding:name} autocomplete suggestions
 * from the local {@code embeddings} model directory.
 */
@Component
public class EmbeddingCompletionStrategy implements IPromptCompletionStrategy {

    @Override
    public List<PromptCompletionCandidate> suggest(String activeToken, Path modelsBaseDir) {
        if (activeToken == null || modelsBaseDir == null) {
            return Collections.emptyList();
        }
        String lower = activeToken.trim().toLowerCase(Locale.ROOT);
        boolean explicitPrefix = lower.startsWith("embedding:");
        if (!explicitPrefix && lower.length() < 3) {
            return Collections.emptyList();
        }

        String query = explicitPrefix ? lower.substring("embedding:".length()) : lower;
        Path embDir = modelsBaseDir.resolve("embeddings");
        if (!Files.isDirectory(embDir)) {
            embDir = modelsBaseDir.resolve("models").resolve("embeddings");
        }
        if (!Files.isDirectory(embDir)) {
            return Collections.emptyList();
        }

        List<PromptCompletionCandidate> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(embDir, 2)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String l = n.toLowerCase(Locale.ROOT);
                        return l.endsWith(".safetensors") || l.endsWith(".pt") || l.endsWith(".bin");
                    })
                    .forEach(fileName -> {
                        int dot = fileName.lastIndexOf('.');
                        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
                        if (query.isEmpty() || stem.toLowerCase(Locale.ROOT).contains(query)) {
                            String syntax = "embedding:" + stem;
                            results.add(PromptCompletionCandidate.builder()
                                    .insertText(syntax)
                                    .displayText(syntax + " [Embedding]")
                                    .category("Embedding")
                                    .priority(explicitPrefix ? 95 : 70)
                                    .build());
                        }
                    });
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return results;
    }
}
