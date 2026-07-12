package com.thomaskippster.comfyuicompanion.service.graph;

import com.thomaskippster.comfyuicompanion.domain.graph.ComfyNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphMutationService {

    /**
     * Updates the text for the positive and negative prompts.
     * It intelligently tries to find the CLIPTextEncode nodes by checking what is linked to the KSampler.
     */
    public void updatePrompt(ComfyWorkflow workflow, String positiveText, String negativeText) {
        ComfyNode sampler = findNodeByClass(workflow, "KSampler");
        if (sampler == null) {
            sampler = findNodeByClass(workflow, "KSamplerAdvanced");
        }
        
        if (sampler != null && sampler.getInputs() != null) {
            updateTextEncodeFromSamplerLink(workflow, sampler, "positive", positiveText);
            updateTextEncodeFromSamplerLink(workflow, sampler, "negative", negativeText);
        } else {
            // Fallback: If no sampler is found, just update the first two CLIPTextEncode nodes
            boolean posSet = false;
            for (ComfyNode node : workflow.getNodes().values()) {
                if ("CLIPTextEncode".equals(node.getClassType())) {
                    if (!posSet) {
                        node.getInputs().put("text", positiveText);
                        posSet = true;
                    } else {
                        node.getInputs().put("text", negativeText);
                        break;
                    }
                }
            }
        }
    }

    private void updateTextEncodeFromSamplerLink(ComfyWorkflow workflow, ComfyNode sampler, String inputKey, String newText) {
        Object link = sampler.getInputs().get(inputKey);
        if (link instanceof List) {
            List<?> linkList = (List<?>) link;
            if (!linkList.isEmpty()) {
                String targetNodeId = String.valueOf(linkList.get(0));
                ComfyNode textNode = workflow.getNodes().get(targetNodeId);
                if (textNode != null && "CLIPTextEncode".equals(textNode.getClassType())) {
                    textNode.getInputs().put("text", newText);
                }
            }
        }
    }

    /**
     * Updates the seed of the KSampler.
     */
    public void setSeed(ComfyWorkflow workflow, long newSeed) {
        ComfyNode sampler = findNodeByClass(workflow, "KSampler");
        if (sampler != null) {
            sampler.getInputs().put("seed", newSeed);
        } else {
            ComfyNode samplerAdv = findNodeByClass(workflow, "KSamplerAdvanced");
            if (samplerAdv != null) {
                samplerAdv.getInputs().put("noise_seed", newSeed);
            }
        }
    }

    /**
     * Swaps the model used in the CheckpointLoaderSimple.
     */
    public void swapCheckpoint(ComfyWorkflow workflow, String modelName) {
        ComfyNode loader = findNodeByClass(workflow, "CheckpointLoaderSimple");
        if (loader != null) {
            loader.getInputs().put("ckpt_name", modelName);
        }
    }

    private ComfyNode findNodeByClass(ComfyWorkflow workflow, String classType) {
        if (workflow.getNodes() == null) return null;
        for (ComfyNode node : workflow.getNodes().values()) {
            if (classType.equals(node.getClassType())) {
                return node;
            }
        }
        return null;
    }
}
