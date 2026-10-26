package de.tki.comfyuicompanion.service.autocomplete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying the prompt autocomplete strategies (LoRA syntax, embeddings,
 * Civitai sidecar trigger words, and curated Booru/quality tags) and token replacement.
 */
class PromptAutocompleteServiceTest {

    @TempDir
    Path tempModelsDir;

    @Test
    void shouldSuggestLoRaSyntaxFromLocalLorasDirectory() throws Exception {
        Path lorasDir = Files.createDirectories(tempModelsDir.resolve("loras"));
        Files.writeString(lorasDir.resolve("detail_tweaker_xl.safetensors"), "dummy", StandardCharsets.UTF_8);

        PromptAutocompleteService service = new PromptAutocompleteService();
        List<PromptCompletionCandidate> results = service.querySuggestions("<lora:det", tempModelsDir, 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).insertText()).isEqualTo("<lora:detail_tweaker_xl:1.0>");
        assertThat(results.get(0).category()).isEqualTo("LoRA");
    }

    @Test
    void shouldSuggestEmbeddingFromLocalEmbeddingsDirectory() throws Exception {
        Path embDir = Files.createDirectories(tempModelsDir.resolve("embeddings"));
        Files.writeString(embDir.resolve("EasyNegative.safetensors"), "dummy", StandardCharsets.UTF_8);

        PromptAutocompleteService service = new PromptAutocompleteService();
        List<PromptCompletionCandidate> results = service.querySuggestions("embedding:easy", tempModelsDir, 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).insertText()).isEqualTo("embedding:EasyNegative");
        assertThat(results.get(0).category()).isEqualTo("Embedding");
    }

    @Test
    void shouldSuggestCivitaiTriggerWordsFromSidecarInfo() throws Exception {
        Path lorasDir = Files.createDirectories(tempModelsDir.resolve("loras"));
        String sidecarJson = """
                {
                  "id": 1001,
                  "name": "Cyberpunk Style",
                  "trainedWords": ["cyberpunk_edgerunner_style", "neon_chrome_city"]
                }
                """;
        Files.writeString(lorasDir.resolve("cyber.civitai.info"), sidecarJson, StandardCharsets.UTF_8);

        PromptAutocompleteService service = new PromptAutocompleteService();
        List<PromptCompletionCandidate> results = service.querySuggestions("cyberpunk_edge", tempModelsDir, 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).insertText()).isEqualTo("cyberpunk_edgerunner_style");
        assertThat(results.get(0).category()).isEqualTo("Trigger");
    }

    @Test
    void shouldExtractActiveTokenAndApplyCompletionCleanly() {
        String text = "masterpiece, best quality, cinem";
        String token = PromptAutocompleteService.extractActiveToken(text, text.length());
        assertThat(token).isEqualTo("cinem");

        String updated = PromptAutocompleteService.applyCompletion(text, text.length(), "cinematic lighting");
        assertThat(updated).isEqualTo("masterpiece, best quality, cinematic lighting, ");
    }
}
