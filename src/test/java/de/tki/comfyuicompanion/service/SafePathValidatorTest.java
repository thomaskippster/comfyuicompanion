package de.tki.comfyuicompanion.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SafePathValidatorTest {

    private final SafePathValidator validator = new SafePathValidator();

    @Test
    void shouldValidateWithinBase_whenTargetIsSafe() {
        Path base = Paths.get("safe", "base").toAbsolutePath();
        Path target = base.resolve("child").resolve("file.txt");

        Path result = validator.validateWithinBase(base, target);
        assertThat(result.normalize()).isEqualTo(target.normalize());
    }

    @Test
    void shouldThrowException_whenPathTraverses() {
        Path base = Paths.get("safe", "base").toAbsolutePath();
        Path target = base.resolve("..").resolve("escaped").resolve("file.txt");

        assertThatThrownBy(() -> validator.validateWithinBase(base, target))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldSanitizeFilename_byRemovingPathTraversalTokens() {
        String malicious = "../../etc/passwd";
        String sanitized = validator.sanitizeFilename(malicious);
        assertThat(sanitized).isEqualTo("etc/passwd");
    }

    @Test
    void shouldValidateFilename_whenSafe() {
        String filename = "model_safetensors.safetensors";
        String validated = validator.validateFilename(filename);
        assertThat(validated).isEqualTo(filename);
    }
    
    @Test
    void shouldThrowException_whenFilenameContainsSlashes() {
        String filename = "folder/model.safetensors";
        assertThatThrownBy(() -> validator.validateFilename(filename))
                .isInstanceOf(SecurityException.class);
    }
}
