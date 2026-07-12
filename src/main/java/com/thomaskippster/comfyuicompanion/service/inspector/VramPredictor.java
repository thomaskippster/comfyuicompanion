package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class VramPredictor {

    private final ObjectMapper objectMapper;

    public VramPredictor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Iterates over all tensors in the safetensors header and computes
     * the exact combined size of the raw model weights in bytes.
     *
     * @param headerJson The parsed JSON string from the safetensors file
     * @return The exact size of all weights in bytes
     * @throws IOException If parsing fails
     */
    public long calculateWeightsSizeInBytes(String headerJson) throws IOException {
        SafetensorsHeader header = objectMapper.readValue(headerJson, SafetensorsHeader.class);
        Map<String, TensorInfo> tensors = header.getTensors();
        
        long totalBytes = 0;

        for (TensorInfo info : tensors.values()) {
            if (info == null || info.getShape() == null || info.getDtype() == null) {
                continue;
            }

            // Calculate product of all dimensions in the shape array
            long elements = 1;
            for (Long dim : info.getShape()) {
                elements *= dim;
            }

            // Map the Safetensors dtype string to actual byte size
            long bytesPerElement = getBytesForDtype(info.getDtype());
            totalBytes += (elements * bytesPerElement);
        }

        return totalBytes;
    }

    /**
     * Checks whether the model fits into the given VRAM footprint.
     * Automatically adds a 20% calculation overhead for KV cache and CUDA working memory.
     *
     * @param computedBytes The raw weight bytes calculated by calculateWeightsSizeInBytes
     * @param availableVramBytes The available hardware VRAM in bytes
     * @return true if the model is predicted to fit without OOM
     */
    public boolean fitsInHardware(long computedBytes, long availableVramBytes) {
        // Add 20% overhead margin (1.2 multiplier)
        long requiredVram = (long) (computedBytes * 1.2);
        return requiredVram <= availableVramBytes;
    }

    /**
     * Resolves safetensors dtypes to their physical byte footprint.
     */
    private long getBytesForDtype(String dtype) {
        String upper = dtype.toUpperCase();
        switch (upper) {
            case "F64":
            case "I64":
                return 8;
            case "F32":
            case "I32":
                return 4;
            case "F16":
            case "BF16":
            case "I16":
                return 2;
            case "F8_E4M3":
            case "F8_E5M2":
            case "I8":
            case "U8":
            case "BOOL":
                return 1;
            default:
                // Conservative fallback if a new/unknown dtype emerges
                return 2;
        }
    }
}
