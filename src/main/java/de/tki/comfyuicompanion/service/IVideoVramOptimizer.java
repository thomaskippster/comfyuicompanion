package de.tki.comfyuicompanion.service;

import org.json.JSONObject;

/**
 * Service interface for optimizing ComfyUI workflow execution graphs on memory-constrained
 * hardware (such as 16 GB VRAM GPUs) and managing VRAM eviction between pipeline passes.
 */
public interface IVideoVramOptimizer {

    /**
     * Determines whether aggressive 16 GB VRAM optimizations should be applied
     * based on detected hardware profile and configuration.
     *
     * @return true if 16 GB optimizations are active, false otherwise
     */
    boolean is16GbOptimizationActive();

    /**
     * Optimizes a ComfyUI execution graph for 16 GB VRAM GPUs by:
     * 1. Offloading text encoders (CLIPLoader, DualCLIPLoader) to system RAM/CPU (saves 5-9 GB VRAM).
     * 2. Replacing standard VAEDecode with VAEDecodeTiled to prevent end-of-generation OOM crashes.
     * 3. Enforcing FP8 or GGUF precision on UNETLoader nodes.
     * 4. Ensuring safe aspect ratio and resolution bounds.
     *
     * @param workflowJson the original workflow JSON
     * @return the transformed, memory-optimized workflow JSON
     */
    JSONObject optimizeWorkflowFor16Gb(JSONObject workflowJson);

    /**
     * Sends a memory eviction request (POST /free) to the ComfyUI server to completely
     * unload models and release GPU VRAM between generation passes.
     *
     * @param serverUrl the active ComfyUI server base URL
     * @return true if memory was successfully purged, false otherwise
     */
    boolean purgeComfyVram(String serverUrl);
}
