package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

/**
 * Service for managing ComfyUI workflow templates.
 * Provides capabilities to scan, load, modify, and inject runtime parameters into templates.
 */
public interface IComfyTemplateService {
    
    /**
     * Scans the template directory for available ComfyUI templates.
     */
    void scanTemplates();
    
    /**
     * Retrieves the list of all available templates.
     *
     * @return A list of {@link ComfyTemplate} objects.
     */
    List<ComfyTemplate> getTemplates();
    
    /**
     * Retrieves a specific template by its name.
     *
     * @param name The name of the template.
     * @return The corresponding {@link ComfyTemplate}, or null if not found.
     */
    ComfyTemplate getTemplateByName(String name);
    
    /**
     * Retrieves a specific template by its filename.
     *
     * @param filename The filename of the template.
     * @return The corresponding {@link ComfyTemplate}, or null if not found.
     */
    ComfyTemplate getTemplateByFilename(String filename);
    
    /**
     * Determines the most appropriate template for a given model name.
     *
     * @param modelName The name of the model.
     * @return The matched {@link ComfyTemplate}.
     */
    ComfyTemplate determineTemplateForModel(String modelName);
    
    /**
     * Modifies a raw template payload with the given runtime parameters.
     *
     * @param templateJson The original template JSON string.
     * @param modelName The model name to inject.
     * @param positivePrompt The positive prompt to inject.
     * @param negativePrompt The negative prompt to inject.
     * @return The modified template JSON string.
     */
    String modifyPayload(String templateJson, String modelName, String positivePrompt, String negativePrompt);
    
    /**
     * Dynamically loads a template for a specific architecture as a Jackson JsonNode tree.
     *
     * @param architecture The target model architecture.
     * @return The parsed JSON tree of the template.
     * @throws IOException If the template file cannot be read or parsed.
     */
    JsonNode loadTemplateForArchitecture(ModelArchitecture architecture) throws IOException;
    
    /**
     * Gets the corresponding template filename for a specific architecture.
     *
     * @param arch The target model architecture.
     * @return The template filename.
     */
    String getTemplateFilenameForArchitecture(ModelArchitecture arch);
    
    /**
     * Injects runtime settings into the template tree.
     *
     * @param templateTree The JSON tree of the template.
     * @param modelName The model name to inject.
     * @param positivePrompt The positive prompt to inject.
     * @param negativePrompt The negative prompt to inject.
     * @return The updated JSON tree.
     */
    JsonNode injectParameters(JsonNode templateTree, String modelName, String positivePrompt, String negativePrompt);

    /**
     * Wraps the template tree into the expected payload root format and serializes it.
     *
     * @param templateTree The JSON tree to serialize.
     * @return The final serialized payload string.
     */
    String generatePayload(JsonNode templateTree);
}
