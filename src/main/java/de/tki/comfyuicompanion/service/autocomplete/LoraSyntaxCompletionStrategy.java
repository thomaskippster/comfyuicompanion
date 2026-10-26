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
 * Strategy implementation that provides {@code <lora:filename:1.0>} autocomplete suggestions
 * by inspecting the local {@code loras} model directory.
 */
@Component
public class LoraSyntaxCompletionStrategy implements IPromptCompletionStrategy {

    @Override
    public List<PromptCompletionCandidate> suggest(String activeToken, Path modelsBaseDir) {
        if (activeToken == null || modelsBaseDir == null) {
            return Collections.emptyList();
        }
        String lower = activeToken.trim().toLowerCase(Locale.ROOT);
        boolean explicitPrefix = lower.startsWith("<lora:") || lower.startsWith("lora:");
        if (!explicitPrefix && lower.length() < 3) {
            return Collections.emptyList();
        }

        String filter = lower;
        if (lower.startsWith("<lora:")) {
            filter = lower.substring("<lora:".length());
        } else if (lower.startsWith("lora:")) {
            filter = lower.substring("lora:".length());
        }

        Path lorasDir = resolveSubDir(modelsBaseDir, "loras");
        if (lorasDir == null || !Files.isDirectory(lorasDir)) {
            return Collections.emptyList();
        }

        List<PromptCompletionCandidate> results = new ArrayList<>();
        final String query = filter;
        try (Stream<Path> stream = Files.walk(lorasDir, 2)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(LoraSyntaxCompletionStrategy::isModelWeightFile)
                    .forEach(fileName -> {
                        String stem = stripExtension(fileName);
                        if (query.isEmpty() || stem.toLowerCase(Locale.ROOT).contains(query)) {
                            String syntax = "<lora:" + stem + ":1.0>";
                            results.add(PromptCompletionCandidate.builder()
                                    .insertText(syntax)
                                    .displayText(syntax + " [LoRA]")
                                    .category("LoRA")
                                    .priority(explicitPrefix ? 100 : 75)
                                    .build());
                        }
                    });
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return results;
    }

    private static Path resolveSubDir(Path modelsBaseDir, String folderName) {
        Path direct = modelsBaseDir.resolve(folderName);
        if (Files.isDirectory(direct)) return direct;
        Path nested = modelsBaseDir.resolve("models").resolve(folderName);
        return Files.isDirectory(nested) ? nested : null;
    }

    private static boolean isModelWeightFile(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".safetensors") || l.endsWith(".ckpt") || l.endsWith(".pt");
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
