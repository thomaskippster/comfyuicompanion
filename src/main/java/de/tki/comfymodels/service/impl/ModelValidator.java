package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IModelValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;

@Service
public class ModelValidator implements IModelValidator {

    private static final Logger logger = LoggerFactory.getLogger(ModelValidator.class);
    private static final int BUFFER_SIZE = 2 * 1024 * 1024; // 2MB direct buffer for memory-efficient I/O

    private ConfigService configService;

    public ModelValidator() {}

    @Autowired
    public ModelValidator(ConfigService configService) {
        this.configService = configService;
    }

    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public ValidationResult validateFile(File file) {
        if (file == null) {
            return new ValidationResult(false, "File is null", "");
        }
        String path = file.getAbsolutePath();

        if (!file.exists()) {
            return new ValidationResult(false, "File does not exist", path);
        }
        if (file.length() == 0) {
            return new ValidationResult(false, "Empty file (0 bytes)", path);
        }

        // Check for LFS Stubs (common when downloading from HF without proper LFS setup)
        if (file.length() < 1024) {
            try {
                String content = Files.readString(file.toPath());
                if (content.contains("version https://git-lfs.github.com/spec/v1")) {
                    return new ValidationResult(false, "Hugging Face LFS Stub (Not the actual model)", path);
                }
            } catch (Exception e) {
                logger.debug("Could not inspect short file as text: {}", e.getMessage());
            }
        }

        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".safetensors") || name.endsWith(".sft")) {
            return validateSafetensors(file);
        }

        // For other files, check if they are readable and have a sane size
        if (file.length() < 1024 * 1024 && !name.endsWith(".yaml") && !name.endsWith(".json")) {
            return new ValidationResult(false, "Suspiciously small for a model file", path);
        }

        return new ValidationResult(true, "OK", path);
    }

    @Override
    public String calculateHash(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        boolean useFastHash = configService != null && configService.isFastHashEnabled();
        long limit = useFastHash ? 100L * 1024 * 1024 : -1L; // 100MB limit if fast hash enabled

        try {
            String fullHash = hashFileChannel(file, limit);
            if (fullHash != null && useFastHash) {
                return fullHash.substring(0, Math.min(8, fullHash.length()));
            }
            return fullHash;
        } catch (Exception e) {
            logger.error("Failed to calculate SHA-256 hash for {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    @Override
    public String calculateFullSha256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try {
            return hashFileChannel(file, -1L);
        } catch (Exception e) {
            logger.error("Failed to calculate full SHA-256 for {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Efficient zero-allocation streaming SHA-256 computation using NIO.2 FileChannel.
     */
    private String hashFileChannel(File file, long maxBytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            long totalRead = 0;
            while (true) {
                buffer.clear();
                if (maxBytes > 0 && totalRead + BUFFER_SIZE > maxBytes) {
                    int remaining = (int) (maxBytes - totalRead);
                    if (remaining <= 0) break;
                    buffer.limit(remaining);
                }

                int bytesRead = channel.read(buffer);
                if (bytesRead == -1) break;

                buffer.flip();
                digest.update(buffer);
                totalRead += bytesRead;

                if (maxBytes > 0 && totalRead >= maxBytes) {
                    break;
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Hash computation failed on FileChannel", e);
        }
    }

    private ValidationResult validateSafetensors(File file) {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < 8) {
                return new ValidationResult(false, "File too small to have a header", file.getAbsolutePath());
            }

            ByteBuffer headerLenBuffer = ByteBuffer.allocate(8);
            headerLenBuffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(headerLenBuffer);
            headerLenBuffer.flip();
            long headerLen = headerLenBuffer.getLong();

            if (headerLen <= 0 || headerLen > fileSize - 8) {
                return new ValidationResult(false, "Invalid header length: " + headerLen, file.getAbsolutePath());
            }

            // Validate that the header is valid JSON without allocating more than 100MB
            if (headerLen < 100 * 1024 * 1024) {
                ByteBuffer headerBuffer = ByteBuffer.allocate((int) headerLen);
                channel.read(headerBuffer);
                headerBuffer.flip();
                String headerJson = StandardCharsets.UTF_8.decode(headerBuffer).toString();

                try {
                    JSONObject json = new JSONObject(headerJson);
                    long maxOffset = 0;

                    for (String key : json.keySet()) {
                        if ("__metadata__".equals(key)) continue;

                        JSONObject tensorInfo = json.optJSONObject(key);
                        if (tensorInfo != null && tensorInfo.has("data_offsets")) {
                            JSONArray offsets = tensorInfo.getJSONArray("data_offsets");
                            if (offsets.length() == 2) {
                                long start = offsets.getLong(0);
                                long end = offsets.getLong(1);
                                maxOffset = Math.max(maxOffset, end);

                                String dtype = tensorInfo.optString("dtype", "");
                                if (!dtype.isEmpty()) {
                                    int elementSize = getElementSize(dtype);
                                    if (elementSize > 1) {
                                        long length = end - start;
                                        if (length % elementSize != 0) {
                                            return new ValidationResult(false,
                                                    String.format("Tensor '%s' length (%d) is not a multiple of element size (%d) for dtype %s",
                                                            key, length, elementSize, dtype), file.getAbsolutePath());
                                        }
                                    }
                                }
                            }
                        }
                    }

                    long expectedSize = 8 + headerLen + maxOffset;
                    if (fileSize < expectedSize) {
                        return new ValidationResult(false,
                                String.format("File is truncated. Expected at least %d bytes but found %d", expectedSize, fileSize),
                                file.getAbsolutePath());
                    }

                } catch (Exception e) {
                    return new ValidationResult(false, "Header is not valid JSON or has invalid structure: " + e.getMessage(), file.getAbsolutePath());
                }
            }

            return new ValidationResult(true, "OK", file.getAbsolutePath());
        } catch (Exception e) {
            return new ValidationResult(false, "Error reading file: " + e.getMessage(), file.getAbsolutePath());
        }
    }

    private int getElementSize(String dtype) {
        if (dtype == null) return 1;
        return switch (dtype.toUpperCase(Locale.ROOT)) {
            case "F64", "I64", "U64" -> 8;
            case "F32", "I32", "U32" -> 4;
            case "F16", "BF16", "I16", "U16" -> 2;
            case "I8", "U8", "BOOL" -> 1;
            default -> 1;
        };
    }
}
