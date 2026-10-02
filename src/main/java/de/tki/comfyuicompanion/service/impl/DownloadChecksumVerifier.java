package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IModelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service component for SHA-256 checksum calculation, memory-mapped/streaming file hashing,
 * and verifying/registering model hashes in {@link ModelHashRegistry}.
 */
@Component
public class DownloadChecksumVerifier {

    private static final Logger logger = LoggerFactory.getLogger(DownloadChecksumVerifier.class);
    private static final int BUFFER_SIZE = 1024 * 1024; // 1 MB buffer for high-throughput streaming
    private static final long MAX_MMAP_SIZE = 256L * 1024 * 1024; // 256 MB threshold for memory-mapping

    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;

    /**
     * Constructs a new DownloadChecksumVerifier with injected dependencies.
     *
     * @param hashRegistry optional model hash registry
     * @param modelValidator optional model validator
     */
    @Autowired
    public DownloadChecksumVerifier(@Autowired(required = false) ModelHashRegistry hashRegistry,
                                    @Autowired(required = false) IModelValidator modelValidator) {
        this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator;
    }

    /**
     * Calculates the full SHA-256 checksum of a file using memory-mapped I/O for smaller files
     * and high-throughput NIO.2 streaming for large model files.
     *
     * @param file the path of the file to hash
     * @return lowercase hexadecimal SHA-256 string
     * @throws IOException if reading the file fails
     */
    public String calculateSha256(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("File does not exist: " + file);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();

            // Use memory mapping for files under 256MB
            if (size > 0 && size <= MAX_MMAP_SIZE) {
                try {
                    MappedByteBuffer mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
                    digest.update(mappedBuffer);
                    return bytesToHex(digest.digest());
                } catch (IOException e) {
                    logger.debug("Memory-mapping failed for {}, falling back to streaming: {}", file, e.getMessage());
                }
            }

            // High-throughput direct buffer streaming for large model files (multi-GB)
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
            return bytesToHex(digest.digest());
        }
    }

    /**
     * Verifies that the SHA-256 checksum of a file matches the expected checksum.
     *
     * @param file the file to verify
     * @param expectedChecksum expected hexadecimal SHA-256 checksum
     * @return true if checksum matches or if expectedChecksum is blank, false otherwise
     */
    public boolean verifyChecksum(Path file, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return true;
        }
        try {
            String actual = calculateSha256(file);
            return expectedChecksum.trim().equalsIgnoreCase(actual);
        } catch (IOException e) {
            logger.error("Failed to calculate SHA-256 checksum for {}: {}", file, e.getMessage());
            return false;
        }
    }

    /**
     * Registers the hash of a downloaded model file in {@link ModelHashRegistry}.
     *
     * @param file the model file to register
     * @return the calculated or registered hash, or null if registration failed
     */
    public String registerModelHash(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        if (hashRegistry != null) {
            return hashRegistry.getOrCalculateHash(file);
        }
        if (modelValidator != null) {
            return modelValidator.calculateHash(file);
        }
        return null;
    }

    /**
     * Converts a byte array into a lowercase hexadecimal string.
     *
     * @param bytes byte array
     * @return lowercase hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
