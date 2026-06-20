package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyTemplate;
import de.tki.comfymodels.domain.ModelArchitecture;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

public interface IComfyTemplateService {
    void scanTemplates();
    List<ComfyTemplate> getTemplates();
    ComfyTemplate getTemplateByName(String name);
    ComfyTemplate getTemplateByFilename(String filename);
    ComfyTemplate determineTemplateForModel(String modelName);
    String modifyPayload(String templateJson, String modelName, String positivePrompt, String negativePrompt);
    
    // Dynamically load templates as Jackson JsonNode trees
    JsonNode loadTemplateForArchitecture(ModelArchitecture architecture) throws IOException;
    String getTemplateFilenameForArchitecture(ModelArchitecture arch);
    
    // Inject runtime settings into the template tree
    JsonNode injectParameters(JsonNode templateTree, String modelName, String positivePrompt, String negativePrompt);

    // Wrap the template tree into the expected payload root format and serialize
    String generatePayload(JsonNode templateTree);
}
