package de.tki.comfymodels;

import de.tki.comfymodels.controller.PromptLabController;
import de.tki.comfymodels.service.PromptBlueprintApiService;
import de.tki.comfymodels.ui.PromptLabView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class PromptLabWorkflowExtractionTest {

    private PromptLabController controller;
    private PromptLabView view;

    @BeforeAll
    static void initHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() {
        controller = new PromptLabController();
        view = new PromptLabView();
        controller.setView(view);
    }

    @Test
    void testExtractZImageTurboWorkflow() throws Exception {
        File file = new File("workflows/z-image-turbo_text_to_image.json");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        controller.populateUiFromWorkflow(content);

        assertEquals(1024, ((Number) view.getPromptWidthSpinner().getValue()).intValue());
        assertEquals(1024, ((Number) view.getPromptHeightSpinner().getValue()).intValue());
        assertEquals(1, ((Number) view.getPromptBatchSizeSpinner().getValue()).intValue());
        assertEquals(8, ((Number) view.getPromptStepsSpinner().getValue()).intValue());
        assertEquals(1.0, ((Number) view.getPromptCfgSpinner().getValue()).doubleValue(), 0.001);
        assertEquals(1.0, ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue(), 0.001);
        assertEquals("res_multistep", view.getPromptSamplerCombo().getSelectedItem());
        assertEquals("simple", view.getPromptSchedulerCombo().getSelectedItem());
    }

    @Test
    void testExtractZImageWorkflow() throws Exception {
        File file = new File("workflows/z-image_text_to_image.json");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        controller.populateUiFromWorkflow(content);

        assertEquals(1024, ((Number) view.getPromptWidthSpinner().getValue()).intValue());
        assertEquals(1024, ((Number) view.getPromptHeightSpinner().getValue()).intValue());
        assertEquals(1, ((Number) view.getPromptBatchSizeSpinner().getValue()).intValue());
        assertEquals(25, ((Number) view.getPromptStepsSpinner().getValue()).intValue());
        assertEquals(4.0, ((Number) view.getPromptCfgSpinner().getValue()).doubleValue(), 0.001);
        assertEquals(1.0, ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue(), 0.001);
        assertEquals("res_multistep", view.getPromptSamplerCombo().getSelectedItem());
        assertEquals("simple", view.getPromptSchedulerCombo().getSelectedItem());
    }

    @Test
    void testExtractFlux2Klein9BTextToImageWorkflow() throws Exception {
        File file = new File("workflows/flux2_klein_9b_text_to_image.json");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        controller.populateUiFromWorkflow(content);

        assertEquals(1024, ((Number) view.getPromptWidthSpinner().getValue()).intValue());
        assertEquals(1024, ((Number) view.getPromptHeightSpinner().getValue()).intValue());
        assertEquals(1, ((Number) view.getPromptBatchSizeSpinner().getValue()).intValue());
        assertEquals(20, ((Number) view.getPromptStepsSpinner().getValue()).intValue());
        assertEquals(5.0, ((Number) view.getPromptCfgSpinner().getValue()).doubleValue(), 0.001);
        assertEquals("euler", view.getPromptSamplerCombo().getSelectedItem());
        assertEquals("simple", view.getPromptSchedulerCombo().getSelectedItem());
    }

    @Test
    void testExtractQwenImageEdit2509Workflow() throws Exception {
        File file = new File("workflows/qwen_image_edit_2509.json");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        controller.populateUiFromWorkflow(content);

        assertEquals(4, ((Number) view.getPromptStepsSpinner().getValue()).intValue());
        assertEquals(1.0, ((Number) view.getPromptCfgSpinner().getValue()).doubleValue(), 0.001);
        assertEquals("euler", view.getPromptSamplerCombo().getSelectedItem());
        assertEquals("simple", view.getPromptSchedulerCombo().getSelectedItem());
        assertEquals(1.0, ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue(), 0.001);
    }

    @Test
    void testExtractFlux2Klein9BImageEditWorkflow() throws Exception {
        File file = new File("workflows/flux2_klein_9b_image_edit.json");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        controller.populateUiFromWorkflow(content);

        assertEquals(1024, ((Number) view.getPromptWidthSpinner().getValue()).intValue());
        assertEquals(1024, ((Number) view.getPromptHeightSpinner().getValue()).intValue());
        assertEquals(1, ((Number) view.getPromptBatchSizeSpinner().getValue()).intValue());
        assertEquals(20, ((Number) view.getPromptStepsSpinner().getValue()).intValue());
        assertEquals(5.0, ((Number) view.getPromptCfgSpinner().getValue()).doubleValue(), 0.001);
        assertEquals("euler", view.getPromptSamplerCombo().getSelectedItem());
        assertEquals("simple", view.getPromptSchedulerCombo().getSelectedItem());
    }
}
