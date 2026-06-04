package de.tki.comfymodels.service.impl;

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
import java.util.function.BiConsumer;

@Service
public class LocalGemmaService {

    private final ConfigService configService;
    private LlamaModel model = null;
    private Timer unloadTimer = null;
    private final Object lock = new Object();
    private static final String MODEL_DOWNLOAD_URL = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf";
    private static final String MODEL_FILENAME = "gemma-2-2b-it-Q4_K_M.gguf";
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    @Autowired
    public LocalGemmaService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Gets the location where the local Gemma model should be stored.
     */
    public File getModelFile() {
        String modelsPathStr = configService.getModelsPath();
        Path path;
        if (modelsPathStr != null && !modelsPathStr.trim().isEmpty()) {
            path = Paths.get(modelsPathStr, "llm", MODEL_FILENAME);
        } else {
            path = Paths.get(configService.getAppDataPath(), "models", "llm", MODEL_FILENAME);
        }
        return path.toFile();
    }

    /**
     * Checks if the local Gemma GGUF model file exists and is of correct size.
     */
    public boolean isModelDownloaded() {
        File file = getModelFile();
        return file.exists() && file.isFile() && file.length() > 500_000_000L; // Basic check: > 500 MB
    }

    /**
     * Downloads the Gemma GGUF model in a background thread, updating progress.
     */
    public void downloadModel(BiConsumer<Double, String> progressListener, Runnable onFinished, BiConsumer<String, Exception> onError) {
        new Thread(() -> {
            File targetFile = getModelFile();
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            File tempFile = new File(parentDir, MODEL_FILENAME + ".download");
            
            try {
                System.out.println("Downloading Gemma GGUF from: " + MODEL_DOWNLOAD_URL);
                URL url = new URL(MODEL_DOWNLOAD_URL);
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
                
                System.out.println("Gemma model download completed successfully.");
                onFinished.run();
                
            } catch (Exception e) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                onError.accept("Download failed: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Initializes and loads the Gemma model in memory.
     */
    private void ensureModelLoaded() throws IOException {
        synchronized (lock) {
            resetUnloadTimer();
            if (model == null) {
                File modelFile = getModelFile();
                if (!modelFile.exists()) {
                    throw new IOException("Gemma model file not found at: " + modelFile.getAbsolutePath());
                }

                System.out.println("Loading Gemma model from: " + modelFile.getAbsolutePath());
                
                ModelParameters modelParams = new ModelParameters()
                    .setModel(modelFile.getAbsolutePath())
                    .setCtxSize(2048)
                    .setGpuLayers(0); // Run on CPU to prevent VRAM conflict with ComfyUI
                
                model = new LlamaModel(modelParams);
                System.out.println("Gemma model loaded successfully into RAM.");
            }
            startUnloadTimer();
        }
    }

    /**
     * Runs inference on the local Gemma model.
     */
    public String generateCompletion(String systemInstruction, String userPrompt, float temperature, int maxTokens) throws IOException {
        ensureModelLoaded();
        
        // Build instruction format for Gemma-2-Instruct
        StringBuilder promptBuilder = new StringBuilder();
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
    }

    /**
     * Unloads the model to release RAM.
     */
    public void unloadModel() {
        synchronized (lock) {
            if (model != null) {
                System.out.println("Unloading Gemma model from memory...");
                model.close();
                model = null;
                System.gc(); // Encourage JVM to free memory immediately
                System.out.println("Gemma model unloaded.");
            }
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
