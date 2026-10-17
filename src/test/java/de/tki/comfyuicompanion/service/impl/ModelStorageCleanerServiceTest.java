package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.impl.ModelStorageCleanerService.CandidateType;
import de.tki.comfyuicompanion.service.impl.ModelStorageCleanerService.StorageScanReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModelStorageCleanerService} verifying duplicate and cold model detection.
 */
public class ModelStorageCleanerServiceTest {

    @TempDir
    Path tempModelsDir;

    @Test
    public void testDetectsDuplicatesAcrossFoldersAndColdModels() throws Exception {
        Path ckptDir = Files.createDirectories(tempModelsDir.resolve("checkpoints"));
        Path unetDir = Files.createDirectories(tempModelsDir.resolve("unet"));
        Path lorasDir = Files.createDirectories(tempModelsDir.resolve("loras"));

        byte[] duplicatePayload = "dummy-model-weights-identical-size".getBytes(StandardCharsets.UTF_8);
        Path copy1 = ckptDir.resolve("shared_model.safetensors");
        Path copy2 = unetDir.resolve("shared_model.safetensors");
        Files.write(copy1, duplicatePayload);
        Files.write(copy2, duplicatePayload);

        Path coldModel = lorasDir.resolve("ancient_style_lora.safetensors");
        Files.write(coldModel, "cold-lora-payload-12345".getBytes(StandardCharsets.UTF_8));
        FileTime fortyFiveDaysAgo = FileTime.from(Instant.now().minus(45, ChronoUnit.DAYS));
        BasicFileAttributeView view = Files.getFileAttributeView(coldModel, BasicFileAttributeView.class);
        view.setTimes(fortyFiveDaysAgo, fortyFiveDaysAgo, fortyFiveDaysAgo);

        ModelStorageCleanerService service = new ModelStorageCleanerService();
        StorageScanReport report = service.scanModelsDirectory(tempModelsDir, 30, null, Set.of());

        assertThat(report.duplicateCount()).isEqualTo(1);
        assertThat(report.coldModelCount()).isEqualTo(1);
        assertThat(report.candidates())
                .extracting(ModelStorageCleanerService.CleanupCandidate::type)
                .containsExactlyInAnyOrder(CandidateType.DUPLICATE, CandidateType.COLD_MODEL);
    }
}
