package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.util.ConsoleAnsiSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying atomic persistence in {@link JsonModelFavoritesRepository}
 * and ANSI escape code sanitization in {@link ConsoleAnsiSanitizer}.
 */
class JsonModelFavoritesRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistAndReloadFavoritesAtomically() {
        Path storageFile = tempDir.resolve("favorites.json");
        JsonModelFavoritesRepository repo = new JsonModelFavoritesRepository(storageFile);

        assertThat(repo.isFavorite("flux1-dev.safetensors")).isFalse();
        assertThat(repo.toggleFavorite("flux1-dev.safetensors")).isTrue();
        assertThat(repo.isFavorite("FLUX1-DEV.SAFETENSORS")).isTrue();

        repo.addFavorite("juggernautXL_v9.safetensors");
        assertThat(repo.getAllFavorites()).hasSize(2);

        // Reload in new instance to verify disk persistence
        JsonModelFavoritesRepository reloaded = new JsonModelFavoritesRepository(storageFile);
        assertThat(reloaded.isFavorite("flux1-dev.safetensors")).isTrue();
        assertThat(reloaded.isFavorite("juggernautxl_v9.safetensors")).isTrue();

        reloaded.removeFavorite("flux1-dev.safetensors");
        assertThat(reloaded.isFavorite("flux1-dev.safetensors")).isFalse();
    }

    @Test
    void shouldStripAnsiEscapeCodesFromConsoleLogs() {
        String raw = "\u001B[32mINFO\u001B[0m: Loading model \u001B[1;36mflux1-dev.safetensors\u001B[0m [100%]";
        String clean = ConsoleAnsiSanitizer.stripAnsiCodes(raw);
        assertThat(clean).isEqualTo("INFO: Loading model flux1-dev.safetensors [100%]");
    }
}
