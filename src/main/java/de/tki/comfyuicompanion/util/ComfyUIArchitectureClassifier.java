package de.tki.comfyuicompanion.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IArchitectureClassifier;
import de.tki.comfyuicompanion.service.impl.ArchitectureClassificationService;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter and legacy facade for architecture classification, delegating to
 * {@link ArchitectureClassificationService} while maintaining backwards compatibility.
 */
@Component
public class ComfyUIArchitectureClassifier implements IArchitectureClassifier {

    private final ArchitectureClassificationService delegate;

    public ComfyUIArchitectureClassifier() {
        this(new ArchitectureClassificationService(null));
    }

    @Autowired
    public ComfyUIArchitectureClassifier(@Autowired(required = false) LocalGemmaService localGemmaService) {
        this(new ArchitectureClassificationService(localGemmaService));
    }

    public ComfyUIArchitectureClassifier(ArchitectureClassificationService delegate) {
        this.delegate = delegate != null ? delegate : new ArchitectureClassificationService(null);
    }

    @Override
    public Set<String> extractNodeTypes(String jsonString) throws JsonProcessingException {
        return delegate.extractNodeTypes(jsonString);
    }

    @Override
    public Set<String> extractNodeTypes(JsonNode rootNode) {
        return delegate.extractNodeTypes(rootNode);
    }

    public String buildSystemPrompt() {
        return delegate.buildSystemPrompt();
    }

    public String buildUserPrompt(Set<String> nodeTypes) {
        return delegate.buildUserPrompt(nodeTypes);
    }

    @Override
    public CompletableFuture<ModelArchitecture> classifyArchitectureAsync(Set<String> nodeTypes) {
        return delegate.classifyArchitectureAsync(nodeTypes);
    }

    public CompletableFuture<ModelArchitecture> classifyWorkflowAsync(Set<String> nodeTypes) {
        return delegate.classifyArchitectureAsync(nodeTypes);
    }

    public ModelArchitecture classifyWorkflow(Set<String> nodeTypes) {
        return delegate.classifyArchitectureAsync(nodeTypes).join();
    }

    @Override
    public ModelArchitecture classifySync(String jsonWorkflow) {
        return delegate.classifySync(jsonWorkflow);
    }

    @Override
    public CompletableFuture<ModelArchitecture> classifyModelFileName(String modelFilename) {
        return delegate.classifyModelFileName(modelFilename);
    }

    public CompletableFuture<ModelArchitecture> classifyModelAsync(String modelFilename) {
        return delegate.classifyModelFileName(modelFilename);
    }

    public ModelArchitecture classifyModel(String modelFilename) {
        return delegate.classifyFilenameSync(modelFilename);
    }

    @Override
    public ModelArchitecture classifyFilenameSync(String modelFilename) {
        return delegate.classifyFilenameSync(modelFilename);
    }

    public ModelArchitecture parseInnerArchitectureResponse(String generatedText) {
        return delegate.parseInnerArchitectureResponse(generatedText);
    }
}
