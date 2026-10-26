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
        LlmGpuConfigurator.preInitializeNativeLlama();
    }

    private final ConfigService configService;
    private final LlmGpuConfigurator gpuConfigurator;
    private final LlmModelDownloadService downloadService;
    private LlamaModel model = null;
    private String loadedModelPath = null;
    private Timer unloadTimer = null;
    private final Object lock = new Object();
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Supported GGUF LLM models for local prompt enhancement and image generation suggestions.
     * All models are downloaded strictly on-demand directly from Hugging Face (never bundled with application).
     */
    public enum LlmModelPreset {
        QWEN_2_5_1_5B("qwen-2.5-1.5b", "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                "Qwen-2.5-1.5B", "~1.1 GB", "Ultra Fast", ChatTemplate.CHATML),
        LLAMA_3_2_3B("llama-3.2-3b", "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                "Llama-3.2-3B", "~2.0 GB", "Creative", ChatTemplate.LLAMA3),
        PHI_3_5_MINI("phi-3.5-mini", "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                "Phi-3.5-mini", "~2.3 GB", "Reasoning", ChatTemplate.CHATML),
        GEMMA_3_4B("gemma-3-4b", "google_gemma-3-4b-it-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf",
                "Gemma-3-4B", "~3.3 GB", "Balanced (Default)", ChatTemplate.GEMMA),
        QWEN_2_5_7B("qwen-2.5-7b", "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
                "Qwen-2.5-7B", "~4.7 GB", "Pro Quality", ChatTemplate.CHATML);

        private final String id;
        private final String filename;
        private final String downloadUrl;
        private final String displayName;
        private final String approxSize;
        private final String description;
        private final ChatTemplate chatTemplate;

        LlmModelPreset(String id, String filename, String downloadUrl, String displayName, String approxSize, String description, ChatTemplate chatTemplate) {
            this.id = id;
            this.filename = filename;
            this.downloadUrl = downloadUrl;
            this.displayName = displayName;
            this.approxSize = approxSize;
            this.description = description;
            this.chatTemplate = chatTemplate;
        }

        public String getId() { return id; }
        public String getFilename() { return filename; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getDisplayName() { return displayName; }
        public String getApproxSize() { return approxSize; }
        public String getDescription() { return description; }
        public ChatTemplate getChatTemplate() { return chatTemplate; }

        @Override
        public String toString() {
            return displayName + " (" + approxSize + " - " + description + ")";
        }

        /**
         * Resolves a preset by identifier with fallback to GEMMA_3_4B.
         *
         * @param id preset identifier
         * @return matching LlmModelPreset
         */
        public static LlmModelPreset fromId(String id) {
            if (id != null && !id.trim().isEmpty()) {
                String clean = id.trim().toLowerCase();
                for (LlmModelPreset p : values()) {
                    if (p.id.equalsIgnoreCase(clean) || p.name().equalsIgnoreCase(clean)) {
                        return p;
                    }
                }
                if (clean.contains("qwen") && clean.contains("1.5")) return QWEN_2_5_1_5B;
                if (clean.contains("qwen")) return QWEN_2_5_7B;
                if (clean.contains("llama")) return LLAMA_3_2_3B;
                if (clean.contains("phi")) return PHI_3_5_MINI;
                if (clean.contains("gemma")) return GEMMA_3_4B;
            }
            return GEMMA_3_4B;
        }
    }

    private final ExecutorService gemmaExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("LocalGemma-Worker-", 1).factory()
    );

    public LocalGemmaService(ConfigService configService) {
        this(configService, new LlmGpuConfigurator(), new LlmModelDownloadService());
    }

    @Autowired
    public LocalGemmaService(ConfigService configService,
                             @Autowired(required = false) LlmGpuConfigurator gpuConfigurator,
                             @Autowired(required = false) LlmModelDownloadService downloadService) {
        this.configService = configService;
        this.gpuConfigurator = gpuConfigurator != null ? gpuConfigurator : new LlmGpuConfigurator();
        this.downloadService = downloadService != null ? downloadService : new LlmModelDownloadService();
    }

    /**
     * Gets the actively configured LLM model preset.
     */
    public LlmModelPreset getActivePreset() {
        if (configService != null) {
            return LlmModelPreset.fromId(configService.getLocalLlmModel());
        }
        return LlmModelPreset.GEMMA_3_4B;
    }

    /**
     * Gets the location where the active local GGUF model should be stored.
     */
    public File getModelFile() {
        return getModelFile(getActivePreset());
    }

    /**
     * Gets the storage file location for a specific model preset.
     *
     * @param preset model preset
     * @return target file on disk
     */
    public File getModelFile(LlmModelPreset preset) {
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
     * Checks if the active local GGUF model file exists and is of valid size.
     */
    public boolean isModelDownloaded() {
        return isModelDownloaded(getActivePreset());
    }

    /**
     * Checks if a specific local GGUF model file exists and is of valid size.
     *
     * @param preset model preset to check
     * @return true if downloaded and valid
     */
    public boolean isModelDownloaded(LlmModelPreset preset) {
        if (preset == null) return false;
        File file = getModelFile(preset);
        return file.exists() && file.isFile() && file.length() > 500_000_000L;
    }

    /**
     * Downloads the active GGUF model in a background thread, updating progress.
     */
    public void downloadModel(BiConsumer<Double, String> progressListener, Runnable onFinished, BiConsumer<String, Exception> onError) {
        downloadModel(getActivePreset(), progressListener, onFinished, onError);
    }

    /**
     * Downloads a specific GGUF model preset on-demand in a background thread, updating progress.
     *
     * @param preset           model preset to download
     * @param progressListener callback for download percentage and status text
     * @param onFinished       callback executed on successful download
     * @param onError          callback executed on download failure
     */
    public void downloadModel(LlmModelPreset preset, BiConsumer<Double, String> progressListener, Runnable onFinished, BiConsumer<String, Exception> onError) {
        gemmaExecutor.submit(() -> {
            File targetFile = getModelFile(preset);
            downloadService.downloadModelFile(preset.getDisplayName(), preset.getDownloadUrl(), targetFile, progressListener, onFinished, onError);
        });
    }

    /**
     * Initializes and loads the configured LLM model in memory.
     * Note: Must be called from the gemmaExecutor thread.
     */
    private void ensureModelLoaded() throws IOException {
        synchronized (lock) {
            resetUnloadTimer();
            File modelFile = getModelFile();
            if (model != null && (loadedModelPath == null || !loadedModelPath.equals(modelFile.getAbsolutePath()))) {
                unloadModelSync();
            }
            if (model == null) {
                if (!modelFile.exists()) {
                    throw new IOException(getActivePreset().getDisplayName() + " model file not found at: " + modelFile.getAbsolutePath());
                }

                logger.info("Loading {} model from: {}", getActivePreset().getDisplayName(), modelFile.getAbsolutePath());
                
                // Attempt FULL GPU acceleration first (100% of layers offloaded to GPU via CUDA / Tensor Cores)
                boolean gpuLoaded = false;
                try {
                    ModelParameters modelParams = gpuConfigurator.createFullGpuParameters(modelFile);
                    model = new LlamaModel(modelParams);
                    loadedModelPath = modelFile.getAbsolutePath();
                    gpuLoaded = true;
                    logger.info("✅ {} model loaded successfully with FULL GPU acceleration.", getActivePreset().getDisplayName());
                } catch (Throwable t) {
                    logger.warn("⚠️ Full GPU acceleration could not be initialized ({}). Falling back to CPU...", t.getMessage());
                    if (model != null) {
                        try { model.close(); } catch (Exception ignored) {
                            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                        }
                        model = null;
                        loadedModelPath = null;
                    }
                }
                
                // Fallback to CPU execution if GPU loading was not successful
                if (!gpuLoaded || model == null) {
                    try {
                        ModelParameters modelParams = gpuConfigurator.createCpuFallbackParameters(modelFile);
                        model = new LlamaModel(modelParams);
                        loadedModelPath = modelFile.getAbsolutePath();
                        logger.info("✅ {} model loaded successfully into RAM (CPU mode).", getActivePreset().getDisplayName());
                    } catch (Throwable t) {
                        logger.error("❌ Failed to load {} model on CPU: {}", getActivePreset().getDisplayName(), t.getMessage(), t);
                        throw new IOException("Failed to load " + getActivePreset().getDisplayName() + " model: " + t.getMessage(), t);
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
                
                LlmModelPreset activePreset = getActivePreset();
                String prompt = activePreset.getChatTemplate().format(systemInstruction, userPrompt);
                
                synchronized (lock) {
                    resetUnloadTimer();
                    
                    InferenceParameters inferParams = new InferenceParameters(prompt)
                        .setTemperature(temperature)
                        .setNPredict(maxTokens)
                        .setCachePrompt(true);
                    
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
                    loadedModelPath = null;
                }
            }
        }
    }

    /**
     * Unloads the model to release RAM.
     */
    public void unloadModel() {
        gemmaExecutor.submit(this::unloadModelSync);
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
