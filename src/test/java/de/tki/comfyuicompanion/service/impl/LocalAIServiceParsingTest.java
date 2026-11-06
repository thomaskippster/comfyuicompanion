package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LocalAIService} validating resilient JSON array parsing
 * when local LLM outputs are truncated mid-string or contain control characters.
 */
class LocalAIServiceParsingTest {

    private final LocalAIService service = new LocalAIService();

    @Test
    @DisplayName("Should parse valid JSON array of 4 strings")
    void shouldParseValidJsonArray() {
        String raw = """
                [
                  "Cinematic portrait of a cyberpunk samurai under neon rain, 85mm f/1.4 lens.",
                  "Atmospheric wide shot of a misty bamboo forest at dusk with golden rays.",
                  "Editorial concept art of a mech pilot in weathered brass armor.",
                  "Dynamic low-angle action shot of a katana strike with sparks."
                ]""";

        List<String> result = service.parseCompletionsJsonArray(raw);
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).startsWith("Cinematic portrait");
        assertThat(result.get(3)).startsWith("Dynamic low-angle");
    }

    @Test
    @DisplayName("Should recover all 4 prompts when 4th string is truncated at maxTokens without closing quote or bracket")
    void shouldRecoverTruncatedJsonArrayAtEof() {
        String truncated = "[\n" +
                "  \"Variation 1: A cinematic close-up portrait illuminated by warm golden hour rim lighting and shallow depth of field.\",\n" +
                "  \"Variation 2: An atmospheric wide shot bathed in moody volumetric fog with rain-slicked cobblestone reflections.\",\n" +
                "  \"Variation 3: High-end editorial concept art featuring weathered brass textures and dramatic chiaroscuro shadows.\",\n" +
                "  \"Variation 4: A dynamic wide-angle perspective capturing intricate macro details, dramatic motion blur, and vibrant neon";

        List<String> result = service.parseCompletionsJsonArray(truncated);
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).startsWith("Variation 1:");
        assertThat(result.get(1)).startsWith("Variation 2:");
        assertThat(result.get(2)).startsWith("Variation 3:");
        assertThat(result.get(3)).startsWith("Variation 4:").endsWith(".");
    }

    @Test
    @DisplayName("Should handle null bytes, unescaped newlines, and inner quotes inside LLM output")
    void shouldHandleNullBytesAndUnescapedQuotes() {
        String malformed = "```json\n[\n" +
                "  \"First prompt with \u0000 null byte and\nraw newline inside string.\",\n" +
                "  \"Second prompt with unescaped \"inner quote\" in the middle of the sentence.\"\n" +
                "]\n```";

        List<String> result = service.parseCompletionsJsonArray(malformed);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).doesNotContain("\u0000");
        assertThat(result.get(1)).contains("'inner quote'");
    }
}
