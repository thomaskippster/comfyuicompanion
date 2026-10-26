package de.tki.comfyuicompanion.service.impl;

import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.LogFormat;
import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Enterprise configurator for LLM GPU acceleration, CUDA library discovery,
 * and hardware-aware parameter tuning for local model execution.
 */
@Component
public class LlmGpuConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(LlmGpuConfigurator.class);
    private static final int FULL_GPU_LAYERS = 999;
    private static final int CONTEXT_SIZE = 2048;
    private static final int BATCH_SIZE = 512;
    private static final int UBATCH_SIZE = 512;

    private final IHardwareProfileService hardwareProfileService;

    public LlmGpuConfigurator() {
        this(null);
    }

    @Autowired
    public LlmGpuConfigurator(@Autowired(required = false) IHardwareProfileService hardwareProfileService) {
        this.hardwareProfileService = hardwareProfileService;
    }

    /**
     * Pre-initializes native llama libraries with CUDA discovery, safe temp extraction,
     * and redirect of native logging to SLF4J.
     */
    public static void preInitializeNativeLlama() {
        discoverAndConfigureCudaLibrary();
        String oldTmp = System.getProperty("java.io.tmpdir");
        try {
            Path customTmp = Files.createTempDirectory("llama_tmp");
            System.setProperty("java.io.tmpdir", customTmp.toAbsolutePath().toString());
            Class.forName("de.kherud.llama.LlamaModel");
            initializeNativeLogging();
        } catch (Throwable t) {
            logger.error("Failed to pre-initialize LlamaModel native libraries", t);
        } finally {
            if (oldTmp != null) {
                System.setProperty("java.io.tmpdir", oldTmp);
            }
        }
    }

    /**
     * Attempts to discover an external CUDA-compiled native library for llama.cpp
     * and configures the system property if found.
     */
    public static void discoverAndConfigureCudaLibrary() {
        String existing = System.getProperty("de.kherud.llama.lib.path");
        if (existing != null && !existing.isBlank()) {
            logger.info("Using configured llama.lib.path: {}", existing);
            return;
        }

        List<Path> candidates = List.of(
                Paths.get("lib", "cuda"),
                Paths.get("native", "cuda"),
                Paths.get(System.getProperty("user.home"), ".comfyuicompanion", "native", "cuda")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate.resolve("jllama.dll")) || Files.exists(candidate.resolve("libjllama.so"))) {
                String absPath = candidate.toAbsolutePath().toString();
                System.setProperty("de.kherud.llama.lib.path", absPath);
                logger.info("⚡ Discovered external CUDA native library at: {}", absPath);
                return;
            }
        }
    }

    /**
     * Initializes native llama.cpp logging to redirect native messages to SLF4J.
     */
    public static void initializeNativeLogging() {
        try {
            LlamaModel.setLogger(LogFormat.TEXT, (level, msg) -> {
                String trimmed = msg != null ? msg.trim() : "";
                if (!trimmed.isEmpty()) {
                    logger.debug("[llama.native] {}", trimmed);
                }
            });
        } catch (Throwable t) {
            logger.trace("Native llama logging could not be attached: {}", t.getMessage());
        }
    }

    /**
     * Creates model parameters configured for 100% full GPU offloading.
     *
     * @param modelFile model GGUF file
     * @return optimized ModelParameters
     */
    public ModelParameters createFullGpuParameters(File modelFile) {
        HardwareProfile profile = hardwareProfileService != null ? hardwareProfileService.getHardwareProfile() : null;
        String gpuName = profile != null ? profile.gpuName() : "Dedicated GPU";
        long vramMb = profile != null ? (profile.vramBytes() / (1024 * 1024)) : 0;
        int cpuThreads = Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        logger.info("🚀 Configuring FULL GPU acceleration for '{}' (VRAM: {} MB, gpuLayers={}, flashAttn=true, ubatch={})",
                gpuName, vramMb, FULL_GPU_LAYERS, UBATCH_SIZE);

        return new ModelParameters()
                .setModel(modelFile.getAbsolutePath())
                .setCtxSize(CONTEXT_SIZE)
                .setGpuLayers(FULL_GPU_LAYERS)
                .setMainGpu(0)
                .enableFlashAttn()
                .setBatchSize(BATCH_SIZE)
                .setUbatchSize(UBATCH_SIZE)
                .setThreads(cpuThreads)
                .setThreadsBatch(cpuThreads);
    }

    /**
     * Creates fallback model parameters for CPU execution.
     *
     * @param modelFile model GGUF file
     * @return CPU-optimized ModelParameters
     */
    public ModelParameters createCpuFallbackParameters(File modelFile) {
        int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        logger.info("ℹ️ Configuring CPU fallback execution (threads={})", cpuThreads);

        return new ModelParameters()
                .setModel(modelFile.getAbsolutePath())
                .setCtxSize(CONTEXT_SIZE)
                .setGpuLayers(0)
                .setThreads(cpuThreads)
                .setThreadsBatch(cpuThreads);
    }
}
