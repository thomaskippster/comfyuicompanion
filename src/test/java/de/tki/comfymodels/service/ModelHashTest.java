package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ModelValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ModelHashTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCalculateSHA256() throws Exception {
        ModelValidator validator = new ModelValidator();
        Path testFile = tempDir.resolve("test_model.bin");
        byte[] data = "ComfyUI Model Data for Hashing Test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(testFile, data);

        String hash = validator.calculateHash(testFile.toFile());
        
        assertNotNull(hash);
        assertEquals(64, hash.length()); 
        
        // echo -n "ComfyUI Model Data for Hashing Test" | sha256sum
        String expectedHash = "5022ad6b25eac3358b18aa8ef8ee79d06833c43bed545bed1a5d45057d3054e2";
        assertEquals(expectedHash, hash);
    }

    @Test
    public void testEmptyFileHash() throws Exception {
        ModelValidator validator = new ModelValidator();
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.createFile(emptyFile);

        String hash = validator.calculateHash(emptyFile.toFile());
        
        // SHA-256 for empty string
        String expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertEquals(expectedEmptyHash, hash);
    }

    @Test
    public void testFastHash() throws Exception {
        final boolean[] fastHashEnabled = new boolean[1];
        de.tki.comfymodels.service.impl.ConfigService configService = new de.tki.comfymodels.service.impl.ConfigService(null, null) {
            @Override
            public boolean isFastHashEnabled() {
                return fastHashEnabled[0];
            }
        };

        ModelValidator validator = new ModelValidator();
        validator.setConfigService(configService);

        Path testFile = tempDir.resolve("large_test_model.bin");
        byte[] buffer = new byte[1024 * 1024];
        java.util.Arrays.fill(buffer, (byte) 'A');
        
        try (java.io.OutputStream os = new java.io.BufferedOutputStream(Files.newOutputStream(testFile))) {
            for (int i = 0; i < 105; i++) {
                os.write(buffer);
            }
            os.write("EXTRA_BYTES_AT_END".getBytes());
        }

        fastHashEnabled[0] = false;
        String fullHash = validator.calculateHash(testFile.toFile());
        assertNotNull(fullHash);
        assertEquals(64, fullHash.length());

        fastHashEnabled[0] = true;
        String fastHash = validator.calculateHash(testFile.toFile());
        assertNotNull(fastHash);
        assertEquals(8, fastHash.length());

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < 100; i++) {
            digest.update(buffer);
        }
        byte[] hundredMbHash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hundredMbHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String expectedFastHash = hexString.toString().substring(0, 8);
        assertEquals(expectedFastHash, fastHash);
        
        assertNotEquals(fullHash.substring(0, 8), fastHash);
    }
}
