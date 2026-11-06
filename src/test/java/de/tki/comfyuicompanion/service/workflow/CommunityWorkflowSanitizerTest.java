package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.CivitaiWorkflowFileResolver;
import de.tki.comfyuicompanion.service.impl.ComfyWorkflowCompatibilitySanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommunityWorkflowSanitizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Sanitizes community Flux2 Klein workflow, converting image savers, inlining parameters, and splitting AIO checkpoint")
    void shouldSanitizeCommunityFlux2KleinWorkflow() throws Exception {
        Path jsonPath = Paths.get("target/zip_inspect/Flux2k_4B_AIO_T2I_v1.0/FLUX2K_4B_AIO_TXT2IMG_v1.0.json");
        if (!Files.exists(jsonPath)) {
            return; // Skip if test archive not unpacked in workspace
        }

        CivitaiWorkflowFileResolver resolver = new CivitaiWorkflowFileResolver(mapper, null, null);
        Optional<ObjectNode> optWorkflow = resolver.loadWorkflowFromFile(jsonPath);
        assertTrue(optWorkflow.isPresent(), "Workflow JSON must be resolved from file");
        ObjectNode workflow = optWorkflow.get();

        // 1. Simulate installed component models for splitting
        List<ModelInfo> requiredModels = List.of(
                createModel("unet", "flux2-klein-base-4b-full.safetensors"),
                createModel("text_encoders", "qwen_2.5_vl_7b_fp8_scaled.safetensors"),
                createModel("vae", "FLUX.1/ae.safetensors")
        );

        resolver.adaptModelReferences(workflow, requiredModels);

        // 2. Run Compatibility Sanitizer
        ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(mapper);
        sanitizer.sanitizeWorkflow(workflow, Collections.emptySet());

        // 3. Assert that custom image savers were converted to core SaveImage
        ArrayNode nodes = (ArrayNode) workflow.get("nodes");
        boolean hasSaveImage = false;
        boolean hasImageSaverSimple = false;
        boolean hasVramCleanup = false;
        boolean hasEasyInt = false;

        for (int i = 0; i < nodes.size(); i++) {
            String type = nodes.get(i).path("type").asText();
            if ("SaveImage".equals(type)) hasSaveImage = true;
            if ("Image Saver Simple".equals(type) || "Easy Prompt Saver".equals(type)) hasImageSaverSimple = true;
            if ("VRAMCleanup".equals(type) || "RAMCleanup".equals(type)) hasVramCleanup = true;
            if ("easy int".equals(type)) hasEasyInt = true;
        }

        assertTrue(hasSaveImage, "Must have converted custom image saver to native SaveImage");
        assertFalse(hasImageSaverSimple, "Image Saver Simple must have been replaced");
        assertFalse(hasVramCleanup, "RAM/VRAM cleanup nodes must have been removed");
        assertFalse(hasEasyInt, "easy int parameter node must have been inlined and removed");
    }

    private ModelInfo createModel(String type, String filename) {
        ModelInfo info = new ModelInfo(type, filename, "http://example.com/" + filename);
        info.setFilename(filename);
        return info;
    }
}
