package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.workflow.ArchitectureProfile;
import de.tki.comfyuicompanion.service.workflow.ArchitectureProfileCatalog;
import de.tki.comfyuicompanion.service.workflow.LoaderTopology;
import de.tki.comfyuicompanion.service.workflow.StandardWorkflowFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enterprise workflow synthesis service constructing LiteGraph visual canvas graphs
 * and runnable ComfyUI API prompts for models downloaded from Hugging Face Hub.
 * Delegates visual graph generation to {@link StandardWorkflowFactory}.
 */
@Component
public class HuggingFaceWorkflowBuilder {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceWorkflowBuilder.class);

    private final ObjectMapper objectMapper;
    private final StandardWorkflowFactory workflowFactory;
    private final ArchitectureProfileCatalog profileCatalog;

    /**
     * Constructs a new HuggingFaceWorkflowBuilder with required dependencies.
     *
     * @param objectMapper    Jackson object mapper
     * @param workflowFactory factory synthesizing generic LiteGraph workflows
     * @param profileCatalog  catalog of architecture generation profiles
     */
    @Autowired
    public HuggingFaceWorkflowBuilder(
            ObjectMapper objectMapper,
            @Autowired(required = false) StandardWorkflowFactory workflowFactory,
            @Autowired(required = false) ArchitectureProfileCatalog profileCatalog) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.profileCatalog = profileCatalog != null ? profileCatalog : new ArchitectureProfileCatalog();
        this.workflowFactory = workflowFactory != null ? workflowFactory : new StandardWorkflowFactory(this.objectMapper);
    }

    /**
     * Convenience constructor supporting callers with single argument.
     *
     * @param objectMapper Jackson object mapper
     */
    public HuggingFaceWorkflowBuilder(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    /**
     * Builds a LiteGraph-compatible GUI workflow JSON structure for the specified model and file.
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the specific model file selected
     * @param userPrompt   optional prompt text entered by the user
     * @return {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode buildGuiWorkflow(HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile, String userPrompt) {
        String filename = selectedFile != null ? selectedFile.filename() : "model.safetensors";
        String folder = selectedFile != null && selectedFile.suggestedFolder() != null
                ? selectedFile.suggestedFolder() : "checkpoints";
        String repoName = (repo != null ? repo.repoId() + " " + repo.modelName() : filename).toLowerCase(Locale.ROOT);

        ModelInfo modelInfo = new ModelInfo(folder, filename, null);
        modelInfo.setBase(repoName);
        modelInfo.setFilename(filename);

        String prompt = (userPrompt != null && !userPrompt.isBlank())
                ? userPrompt.trim()
                : (repo != null ? repo.modelName() : "Masterpiece") + ", 8k resolution, highly detailed, photorealistic";

        ObjectNode workflow = workflowFactory.createWorkflow(modelInfo, prompt);
        log.debug("Synthesized generic LiteGraph GUI workflow for HF model: {}", filename);
        return workflow;
    }

    /**
     * Synthesizes an executable ComfyUI API workflow JSON payload for direct remote generation.
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the selected model file
     * @param userPrompt   custom positive prompt
     * @return runnable JSON string payload for ComfyUI /prompt endpoint
     */
    public String buildApiWorkflowJson(HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile, String userPrompt) {
        String filename = selectedFile != null ? selectedFile.filename() : "model.safetensors";
        String repoName = (repo != null ? repo.repoId() + " " + repo.modelName() : filename).toLowerCase(Locale.ROOT);
        ArchitectureProfile profile = profileCatalog.resolveProfile(repoName);

        boolean isFlux = profile.loaderTopology() != LoaderTopology.CHECKPOINT_SIMPLE || filename.endsWith(".gguf");

        String templateName = isFlux ? "template_flux_api.json" : "template_sdxl_api.json";
        String rawTemplate = DefaultComfyTemplates.getDefaultTemplateForName(templateName);

        try {
            JsonNode root = objectMapper.readTree(rawTemplate);
            ObjectNode promptObj = root.has("prompt") ? (ObjectNode) root.get("prompt") : (ObjectNode) root;

            Iterator<String> nodeIds = promptObj.fieldNames();
            while (nodeIds.hasNext()) {
                String id = nodeIds.next();
                JsonNode node = promptObj.get(id);
                String classType = node.path("class_type").asText("");
                ObjectNode inputs = (ObjectNode) node.path("inputs");

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", filename);
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType) || "UnetLoaderGGUF".equals(classType)) {
                    inputs.put("unet_name", filename);
                } else if ("CLIPTextEncode".equals(classType)) {
                    if (inputs.has("text") && !inputs.get("text").asText("").contains("bad anatomy")) {
                        inputs.put("text", userPrompt != null && !userPrompt.isBlank() ? userPrompt : "Masterpiece, 8k resolution, highly detailed");
                    }
                } else if ("KSampler".equals(classType)) {
                    inputs.put("seed", Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L)));
                }
            }

            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set("prompt", promptObj);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapped);
        } catch (Exception e) {
            log.error("Failed building API workflow JSON for HF model: {}", e.getMessage(), e);
            return rawTemplate;
        }
    }
}
