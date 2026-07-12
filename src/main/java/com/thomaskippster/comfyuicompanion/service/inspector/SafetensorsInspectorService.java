package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class SafetensorsInspectorService {

    private static final long MAX_HEADER_SIZE = 100 * 1024 * 1024; // 100 MB limit
    private final ObjectMapper objectMapper;

    public SafetensorsInspectorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the JSON header of a .safetensors file without loading the multi-gigabyte tensors into RAM.
     * Uses java.nio FileChannel for maximum zero-copy performance.
     *
     * @param modelPath Path to the .safetensors file
     * @return The extracted JSON header as a UTF-8 String
     * @throws IOException If a file read error occurs or the header size exceeds the security limit
     */
    public String readHeaderJson(Path modelPath) throws IOException {
        try (FileChannel channel = FileChannel.open(modelPath, StandardOpenOption.READ)) {
            // 1. Read the first 8 bytes to get the Little-Endian length integer
            ByteBuffer lengthBuffer = ByteBuffer.allocate(8);
            lengthBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            int bytesRead = channel.read(lengthBuffer);
            if (bytesRead < 8) {
                throw new IOException("File is too small to be a valid .safetensors file.");
            }

            lengthBuffer.flip();
            long headerLength = lengthBuffer.getLong();

            // 2. Security Boundary: Deny requests that indicate abnormally large headers
            if (headerLength <= 0 || headerLength > MAX_HEADER_SIZE) {
                throw new IOException("Invalid safetensors header length: " + headerLength + ". Potential corruption or DoS attempt.");
            }

            // 3. Read exact header payload into memory
            ByteBuffer jsonBuffer = ByteBuffer.allocate((int) headerLength);
            int jsonBytesRead = channel.read(jsonBuffer);
            
            if (jsonBytesRead < headerLength) {
                throw new IOException("Incomplete safetensors header data. Expected " + headerLength + " bytes, but read " + jsonBytesRead);
            }

            // 4. Decode bytes directly to UTF-8
            jsonBuffer.flip();
            return StandardCharsets.UTF_8.decode(jsonBuffer).toString();
        }
    }

    /**
     * Helper to parse the raw JSON string into a structured, type-safe POJO.
     */
    public SafetensorsHeader parseHeader(String json) throws IOException {
        return objectMapper.readValue(json, SafetensorsHeader.class);
    }
}
