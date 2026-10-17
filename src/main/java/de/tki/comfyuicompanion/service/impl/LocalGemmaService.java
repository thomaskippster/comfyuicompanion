package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.InferenceParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import jakarta.annotation.PreDestroy;

@Service
/**
 * Represents the local gemma service class.
 */
public class LocalGemmaService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalGemmaService.class);

    static {
        String oldTmp = System.getProperty("java.io.tmpdir");
        try {
            Path customTmp = Files.createTempDirectory("llama_tmp");
            System.setProperty("java.io.tmpdir", customTmp.toAbsolutePath().toString());
            // Force initialization of LlamaModel (and LlamaLoader) so it extracts the DLL to the unique folder
            Class.forName("de.kherud.llama.LlamaModel");
        } catch (Throwable t) {
            logger.error("Failed to pre-initialize LlamaModel native libraries", t);
        } finally {
            if (oldTmp != null) {
                System.setProperty("java.io.tmpdir", oldTmp);
            }
        }
    }

    private final ConfigService configService;
    private LlamaModel model = null;
    private Timer unloadTimer = null;
    private final Object lock = new Object();
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Supported GGUF LLM models for local execution.
     */
    public enum LlmModelPreset {
        GEMMA_3_4B(
                "google_gemma-3-4b-it-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf",
                "Gemma 3 4B Instruct"
        ),
        QWEN_2_5_7B(
                "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                "Qwen 2.5 7B Instruct"
        );

        private final String filename;
        private final String downloadUrl;
        private final String displayName;

        LlmModelPreset(String filename, String downloadUrl, String displayName) {
            this.filename = filename;
            this.downloadUrl = downloadUrl;
            this.displayName = displayName;
        }

        public String getFilename() { return filename; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getDisplayName() { return displayName; }
    }

    private final ExecutorService gemmaExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LocalGemma-Worker");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @Autowired
    public LocalGemmaService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Gets the actively configured LLM model preset.
     */
    public LlmModelPreset getActivePreset() {
        if (configService != null && "qwen-2.5-7b".equalsIgnoreCase(configService.getLocalLlmModel())) {
            return LlmModelPreset.QWEN_2_5_7B;
        }
        return LlmModelPreset.GEMMA_3_4B;
    }

    /**
     * Gets the location where the active local GGUF model should be stored.
     */
    public File getModelFile() {
        LlmModelPreset preset = getActivePreset();
        String modelsPathStr = configService != null ? configService.getModelsPath() : null;
        Path path;
        if (modelsPathStr != null && !modelsPathStr.trim().isEmpty()) {
            path = Paths.get(modelsPathStr, "llm", preset.getFilename());
        } else {
            String appData = configService != null ? configService.getAppDataPath() : System.getProperty("user.home");
            path = Paths.get(appData, "models", "llm", preset.getFilename());
        }
        return path.toFile();
    }

    /**
     * Checks if the local GGUF model file exists and is of correct size.
     */
    public boolean isModelDownloaded() {
        File file = getModelFile();
        return file.exists() && file.isFile() && file.length() > 500_000_000L; // Basic check: > 500 MB
    }

    /**
     * Downloads the active GGUF model in a background thread, updating progress.
     */
    public void downloadModel(BiConsumer<Double, String> progressListener, Runnable onFinished, BiConsumer<String, Exception> onError) {
        gemmaExecutor.submit(() -> {
            LlmModelPreset preset = getActivePreset();
            File targetFile = getModelFile();
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            File tempFile = new File(parentDir, preset.getFilename() + ".download");
            
            try {
                logger.info("Downloading LLM GGUF ({}) from: {}", preset.getDisplayName(), preset.getDownloadUrl());
                URL url = new URL(preset.getDownloadUrl());
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                
                long totalBytes = connection.getContentLengthLong();
                
                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long bytesRead = 0;
                    int count;
                    long lastUpdateTime = System.currentTimeMillis();
                    
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        bytesRead += count;
                        
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 1000 && totalBytes > 0) {
                            double percent = (double) bytesRead / totalBytes;
                            double mbRead = (double) bytesRead / (1024 * 1024);
                            double mbTotal = (double) totalBytes / (1024 * 1024);
                            String status = String.format("Downloaded %.1f / %.1f MB (%.1f%%)", mbRead, mbTotal, percent * 100);
                            progressListener.accept(percent, status);
                            lastUpdateTime = now;
                        }
                    }
                }

                // Rename temp file to final file
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                tempFile.renameTo(targetFile);
                
                logger.info("Gemma model download completed successfully.");
                onFinished.run();
                
            } catch (Exception e) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                onError.accept("Download failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Initializes and loads the Gemma model in memory.
     * Note: Must be called from the gemmaExecutor thread.
     */
    private void ensureModelLoaded() throws IOException {
        synchronized (lock) {
            resetUnloadTimer();
            if (model == null) {
                File modelFile = getModelFile();
                if (!modelFile.exists()) {
                    throw new IOException("Gemma model file not found at: " + modelFile.getAbsolutePath());
                }

                logger.info("Loading Gemma model from: " + modelFile.getAbsolutePath());
                
                // Attempt GPU acceleration first (offload all layers to GPU via CUDA / Vulkan / Metal)
                boolean gpuLoaded = false;
                try {
                    int gpuLayers = 99; // 99 layers offloads all transformer layers to GPU
                    logger.info("Attempting to load Gemma model with GPU acceleration (gpuLayers=" + gpuLayers + ")...");
                    
                    ModelParameters modelParams = new ModelParameters()
                        .setModel(modelFile.getAbsolutePath())
                        .setCtxSize(2048)
                        .setGpuLayers(gpuLayers);
                    
                    model = new LlamaModel(modelParams);
                    gpuLoaded = true;
                    logger.info("✅ Gemma model loaded successfully with GPU acceleration.");
                } catch (Throwable t) {
                    logger.warn("⚠️ GPU acceleration could not be initialized (" + t.getMessage() + "). Falling back to CPU...");
                    if (model != null) {
                        try { model.close(); } catch (Exception ignored) {}
                        model = null;
                    }
                }
                
                // Fallback to CPU execution if GPU loading was not successful
                if (!gpuLoaded || model == null) {
                    try {
                        ModelParameters modelParams = new ModelParameters()
                            .setModel(modelFile.getAbsolutePath())
                            .setCtxSize(2048)
                            .setGpuLayers(0); // CPU fallback
                        
                        model = new LlamaModel(modelParams);
                        logger.info("✅ Gemma model loaded successfully into RAM (CPU mode).");
                    } catch (Throwable t) {
                        logger.error("❌ Failed to load Gemma model on CPU: " + t.getMessage(), t);
                        throw new IOException("Failed to load Gemma model: " + t.getMessage(), t);
                    }
                }
            }
            startUnloadTimer();
        }
    }

    /**
     * Runs inference on the local Gemma model.
     */
    public String generateCompletion(String systemInstruction, String userPrompt, float temperature, int maxTokens) throws IOException {
        try {
            return gemmaExecutor.submit(() -> {
                ensureModelLoaded();
                
                // Build instruction format based on active model architecture (Qwen ChatML vs Gemma)
                boolean isQwen = getActivePreset() == LlmModelPreset.QWEN_2_5_7B
                        || getModelFile().getName().toLowerCase().contains("qwen");
                StringBuilder promptBuilder = new StringBuilder();

                if (isQwen) {
                    if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                        promptBuilder.append("<|im_start|>system\n").append(systemInstruction.trim()).append("<|im_end|>\n");
                    }
                    promptBuilder.append("<|im_start|>user\n").append(userPrompt.trim()).append("<|im_end|>\n");
                    promptBuilder.append("<|im_start|>assistant\n");
                } else {
                    if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                        promptBuilder.append("<start_of_turn>user\n")
                                     .append(systemInstruction.trim())
                                     .append("\n\n")
                                     .append(userPrompt.trim())
                                     .append("<end_of_turn>\n")
                                     .append("<start_of_turn>model\n");
                    } else {
                        promptBuilder.append("<start_of_turn>user\n")
                                     .append(userPrompt.trim())
                                     .append("<end_of_turn>\n")
                                     .append("<start_of_turn>model\n");
                    }
                }
                
                synchronized (lock) {
                    resetUnloadTimer();
                    
                    InferenceParameters inferParams = new InferenceParameters(promptBuilder.toString())
                        .setTemperature(temperature)
                        .setNPredict(maxTokens);
                    
                    StringBuilder response = new StringBuilder();
                    try {
                        for (de.kherud.llama.LlamaOutput output : model.generate(inferParams)) {
                            response.append(output.toString());
                        }
                    } finally {
                        startUnloadTimer();
                    }
                    
                    return response.toString().trim();
                }
            }).get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new IOException(cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for Gemma completion", e);
        }
    }

    /**
     * Synchronously unloads the LLM from memory/VRAM immediately, releasing GPU memory
     * before ComfyUI launches diffusion workloads.
     */
    public void unloadModelSync() {
        synchronized (lock) {
            resetUnloadTimer();
            if (model != null) {
                try {
                    model.close();
                    logger.info("🧹 [LocalLLM] Model unloaded synchronously. 100% of GPU/System memory released.");
                } catch (Exception e) {
                    logger.warn("Error closing LlamaModel: {}", e.getMessage());
                } finally {
                    model = null;
                }
            }
        }
    }

    /**
     * Unloads the model to release RAM.
     */
    public void unloadModel() {
        gemmaExecutor.submit(() -> {
            boolean wasLoaded;
            synchronized (lock) {
                wasLoaded = model != null;
                if (model != null) {
                    logger.info("Unloading Gemma model from memory...");
                    model.close();
                    model = null;
                }
            }
            if (wasLoaded) {
                logger.info("Gemma model unloaded.");
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            if (unloadTimer != null) {
                unloadTimer.cancel();
                unloadTimer = null;
            }
            if (model != null) {
                model.close();
                model = null;
            }
        }
        gemmaExecutor.shutdown();
        try {
            if (!gemmaExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                gemmaExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            gemmaExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startUnloadTimer() {
        synchronized (lock) {
            if (unloadTimer != null) {
                unloadTimer.cancel();
            }
            unloadTimer = new Timer(true);
            unloadTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    unloadModel();
                }
            }, INACTIVITY_TIMEOUT_MS);
        }
    }

    private void resetUnloadTimer() {
        synchronized (lock) {
            if (unloadTimer != null) {
                unloadTimer.cancel();
                unloadTimer = null;
            }
        }
    }
}
