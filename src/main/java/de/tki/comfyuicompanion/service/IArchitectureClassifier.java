package de.tki.comfyuicompanion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.ModelArchitecture;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for classifying ComfyUI workflow JSON structures and model filenames
 * into defined neural network architectures using local LLM intelligence.
 */
public interface IArchitectureClassifier {

    /**
     * Extracts unique node type class names from a ComfyUI JSON string.
     *
     * @param jsonString Serialized workflow JSON
     * @return Set of unique node class_type names
     * @throws JsonProcessingException if JSON parsing fails
     */
    Set<String> extractNodeTypes(String jsonString) throws JsonProcessingException;

    /**
     * Extracts unique node type class names from a Jackson JsonNode tree.
     *
     * @param rootNode Root JSON node
     * @return Set of unique node class_type names
     */
    Set<String> extractNodeTypes(JsonNode rootNode);

    /**
     * Asynchronously classifies workflow node types into a ModelArchitecture.
     *
     * @param nodeTypes Set of node class_type names
     * @return CompletableFuture completing with the detected ModelArchitecture
     */
    CompletableFuture<ModelArchitecture> classifyArchitectureAsync(Set<String> nodeTypes);

    /**
     * Synchronously classifies a workflow JSON string with a timeout.
     *
     * @param jsonWorkflow Workflow JSON string
     * @return Detected ModelArchitecture or ARCH_UNKNOWN
     */
    ModelArchitecture classifySync(String jsonWorkflow);

    /**
     * Asynchronously classifies a model filename into a ModelArchitecture.
     *
     * @param filename Name of the model file
     * @return CompletableFuture completing with the detected ModelArchitecture
     */
    CompletableFuture<ModelArchitecture> classifyModelFileName(String filename);

    /**
     * Synchronously classifies a model filename with a timeout.
     *
     * @param filename Name of the model file
     * @return Detected ModelArchitecture or ARCH_UNKNOWN
     */
    ModelArchitecture classifyFilenameSync(String filename);
}
