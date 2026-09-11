package com.thomaskippster.comfyuicompanion.service.graph;

import com.thomaskippster.comfyuicompanion.domain.graph.ComfyNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphMutationService {

    /**
     * Updates the text for positive and negative prompts generically.
     * Tries linked nodes from the primary sampler first, then falls back to any text-encoding nodes.
     */
    public void updatePrompt(ComfyWorkflow workflow, String positiveText, String negativeText) {
        if (workflow == null || workflow.getNodes() == null) return;

        ComfyNode sampler = findSamplerNode(workflow);
        boolean updatedViaSampler = false;

        if (sampler != null && sampler.getInputs() != null) {
            boolean posUpdated = updateTextEncodeFromLink(workflow, sampler, "positive", positiveText);
            boolean negUpdated = updateTextEncodeFromLink(workflow, sampler, "negative", negativeText);
            updatedViaSampler = posUpdated || negUpdated;
        }

        if (!updatedViaSampler) {
            // Fallback: Locate all prompt/text encoding nodes in topological or dictionary order
            boolean posSet = false;
            for (ComfyNode node : workflow.getNodes().values()) {
                if (isTextEncodeNode(node)) {
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

    private boolean updateTextEncodeFromLink(ComfyWorkflow workflow, ComfyNode sourceNode, String inputKey, String newText) {
        Object link = sourceNode.getInputs().get(inputKey);
        if (link instanceof List<?> linkList && !linkList.isEmpty()) {
            String targetNodeId = String.valueOf(linkList.get(0));
            ComfyNode targetNode = workflow.getNodes().get(targetNodeId);
            if (targetNode != null && targetNode.getInputs() != null) {
                if (targetNode.getInputs().containsKey("text")) {
                    targetNode.getInputs().put("text", newText);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Generically updates the seed across any sampler or seed generator node.
     */
    public void setSeed(ComfyWorkflow workflow, long newSeed) {
        if (workflow == null || workflow.getNodes() == null) return;

        ComfyNode sampler = findSamplerNode(workflow);
        if (sampler != null && sampler.getInputs() != null) {
            if (sampler.getInputs().containsKey("seed")) {
                sampler.getInputs().put("seed", newSeed);
                return;
            } else if (sampler.getInputs().containsKey("noise_seed")) {
                sampler.getInputs().put("noise_seed", newSeed);
                return;
            }
        }

        // Generic fallback: Search any node in the graph that contains a seed input parameter
        for (ComfyNode node : workflow.getNodes().values()) {
            if (node.getInputs() != null) {
                if (node.getInputs().containsKey("seed")) {
                    node.getInputs().put("seed", newSeed);
                    return;
                } else if (node.getInputs().containsKey("noise_seed")) {
                    node.getInputs().put("noise_seed", newSeed);
                    return;
                }
            }
        }
    }

    /**
     * Swaps the checkpoint or diffusion model across standard loaders (CheckpointLoaderSimple, UNETLoader, etc.).
     */
    public void swapCheckpoint(ComfyWorkflow workflow, String modelName) {
        if (workflow == null || workflow.getNodes() == null) return;

        for (ComfyNode node : workflow.getNodes().values()) {
            if (node.getInputs() != null) {
                if (node.getInputs().containsKey("ckpt_name")) {
                    node.getInputs().put("ckpt_name", modelName);
                    return;
                } else if (node.getInputs().containsKey("unet_name")) {
                    node.getInputs().put("unet_name", modelName);
                    return;
                }
            }
        }
    }

    /**
     * Identifies sampler nodes dynamically by class name or signature.
     */
    public ComfyNode findSamplerNode(ComfyWorkflow workflow) {
        if (workflow == null || workflow.getNodes() == null) return null;

        // 1. Direct match on standard or advanced samplers
        for (String type : List.of("KSampler", "KSamplerAdvanced", "SamplerCustomAdvanced", "KSamplerSelect")) {
            ComfyNode node = findNodeByClass(workflow, type);
            if (node != null) return node;
        }

        // 2. Heuristic match: any node ending with "Sampler" or containing positive/negative links
        for (ComfyNode node : workflow.getNodes().values()) {
            if (node.getClassType() != null && node.getClassType().toLowerCase().contains("sampler")) {
                return node;
            }
            if (node.getInputs() != null && node.getInputs().containsKey("positive") && node.getInputs().containsKey("negative")) {
                return node;
            }
        }
        return null;
    }

    private boolean isTextEncodeNode(ComfyNode node) {
        if (node == null || node.getInputs() == null) return false;
        if (node.getClassType() != null && node.getClassType().toLowerCase().contains("textencode")) {
            return true;
        }
        return node.getInputs().containsKey("text");
    }

    public ComfyNode findNodeByClass(ComfyWorkflow workflow, String classType) {
        if (workflow == null || workflow.getNodes() == null || classType == null) return null;
        for (ComfyNode node : workflow.getNodes().values()) {
            if (classType.equalsIgnoreCase(node.getClassType())) {
                return node;
            }
        }
        return null;
    }
}
