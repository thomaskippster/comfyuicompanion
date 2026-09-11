package com.thomaskippster.comfyuicompanion.service.provisioning;

import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;
import com.thomaskippster.comfyuicompanion.service.inspector.ModelArchitectureAnalyzer;
import com.thomaskippster.comfyuicompanion.service.inspector.ModelMetadata;
import com.thomaskippster.comfyuicompanion.service.inspector.SafetensorsInspectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ResourceGapAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ResourceGapAnalyzer.class);

    private final SafetensorsInspectorService inspectorService;
    private final ModelArchitectureAnalyzer architectureAnalyzer;
    private final HuggingFaceClient huggingFaceClient;
    private final LargeFileDownloadService downloadService;
    private final ComfyHttpClient comfyHttpClient;

    private final com.thomaskippster.comfyuicompanion.service.graph.GraphMutationService graphMutationService;
    private final de.tki.comfymodels.service.SafePathValidator safePathValidator;

    @Value("${comfyui.models.path:models}")
    private String modelsBasePath;

    @org.springframework.beans.factory.annotation.Autowired
    public ResourceGapAnalyzer(SafetensorsInspectorService inspectorService,
                               ModelArchitectureAnalyzer architectureAnalyzer,
                               HuggingFaceClient huggingFaceClient,
                               LargeFileDownloadService downloadService,
                               ComfyHttpClient comfyHttpClient,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) com.thomaskippster.comfyuicompanion.service.graph.GraphMutationService graphMutationService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) de.tki.comfymodels.service.SafePathValidator safePathValidator) {
        this.inspectorService = inspectorService;
        this.architectureAnalyzer = architectureAnalyzer;
        this.huggingFaceClient = huggingFaceClient;
        this.downloadService = downloadService;
        this.comfyHttpClient = comfyHttpClient;
        this.graphMutationService = graphMutationService;
        this.safePathValidator = safePathValidator;
    }

    /**
     * Steuert den gesamten Flow nicht-blockierend und reaktiv:
     * Gap erkannt -> Hugging Face reaktiv gesucht -> Modell gestreamt -> /cmfc/refresh-models getriggert -> Graph asynchron ausgeführt.
     */
    public Mono<String> resolveGapAndExecute(GenerationIntent intent, ComfyWorkflow workflow, String clientId) {
        MissingResourceReport report = analyze(intent);

        if (!report.isMissing()) {
            logger.info("Auto-Provisioning: Alle Ressourcen vorhanden. Führe Graph direkt aus.");
            return comfyHttpClient.triggerWorkflow(workflow, clientId);
        }

        logger.info("Auto-Provisioning: Lücke erkannt. Suche reaktiv auf Hugging Face nach '{}' (Architektur: {})", 
                report.getKeyword(), report.getArchitecture());

        return huggingFaceClient.searchModelsReactive(report.getKeyword(), report.getArchitecture())
                .flatMap(recommendations -> {
                    if (recommendations == null || recommendations.isEmpty()) {
                        return Mono.error(new RuntimeException("Auto-Provisioning fehlgeschlagen: Kein passendes Modell auf Hugging Face gefunden."));
                    }

                    ModelRecommendation topPick = recommendations.get(0);
                    String rawName = topPick.getName();
                    String safeModelName = safePathValidator != null
                            ? safePathValidator.sanitizeFilename(rawName)
                            : (rawName != null ? rawName.replaceAll("[^a-zA-Z0-9._-]", "_") : "model");

                    Path basePath = Paths.get(modelsBasePath).toAbsolutePath().normalize();
                    Path targetPath = basePath.resolve("checkpoints").resolve(safeModelName + "_" + report.getArchitecture() + ".safetensors");
                    if (safePathValidator != null) {
                        safePathValidator.validateWithinBase(basePath, targetPath);
                    }

                    try {
                        Files.createDirectories(targetPath.getParent());
                    } catch (IOException e) {
                        return Mono.error(new RuntimeException("Konnte Modell-Ordner nicht erstellen", e));
                    }

                    logger.info("Auto-Provisioning: Starte Download von {} nach {}", topPick.getDownloadUrl(), targetPath);

                    return downloadService.downloadModel(topPick.getDownloadUrl(), targetPath)
                            .filter(progress -> progress % 10.0 < 1.0)
                            .doOnNext(progress -> logger.info("Download Fortschritt: {}%", String.format("%.1f", progress)))
                            .then(Mono.defer(() -> {
                                logger.info("Auto-Provisioning: Download abgeschlossen. Triggere ComfyUI Hot-Reload (/cmfc/refresh-models).");
                                return comfyHttpClient.triggerModelRefresh();
                            }))
                            .then(Mono.defer(() -> {
                                logger.info("Auto-Provisioning: Hot-Reload erfolgreich. Tausche Checkpoint im Graphen generisch aus.");
                                if (graphMutationService != null) {
                                    graphMutationService.swapCheckpoint(workflow, targetPath.getFileName().toString());
                                } else {
                                    workflow.getNodes().values().stream()
                                            .filter(node -> "CheckpointLoaderSimple".equals(node.getClassType()))
                                            .forEach(node -> node.getInputs().put("ckpt_name", targetPath.getFileName().toString()));
                                }
                                return comfyHttpClient.triggerWorkflow(workflow, clientId);
                            }));
                });
    }

    public MissingResourceReport analyze(GenerationIntent intent) {
        MissingResourceReport report = new MissingResourceReport();
        report.setMissing(false);

        String keyword = intent.getSuggestedCheckpointKeyword();
        if (keyword == null || keyword.trim().isEmpty()) {
            return report;
        }

        String targetArchitecture = intent.getArchitecture();
        Path checkpointsDir = Paths.get(modelsBasePath, "checkpoints");

        if (Files.exists(checkpointsDir)) {
            try (Stream<Path> paths = Files.walk(checkpointsDir)) {
                // Finde irgendein Modell, das die geforderte Architektur unterstützt
                Path foundModel = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".safetensors"))
                        .filter(p -> isMatchingArchitecture(p, targetArchitecture))
                        .findFirst()
                        .orElse(null);

                if (foundModel != null) {
                    // Speichere den relativen Pfad für ComfyUI
                    String relativePath = checkpointsDir.relativize(foundModel).toString();
                    report.setResolvedModelName(relativePath);
                    return report; // Wir haben lokal das beste passende Modell gefunden!
                }
            } catch (IOException e) {
                logger.error("Fehler beim Suchen im Checkpoint-Verzeichnis: {}", checkpointsDir, e);
            }
        }

        report.setMissing(true);
        report.setType(MissingResourceReport.ResourceType.CHECKPOINT);
        report.setArchitecture(targetArchitecture);
        report.setKeyword(keyword);

        return report;
    }

    private boolean isMatchingArchitecture(Path modelPath, String targetArch) {
        try {
            String headerJson = inspectorService.readHeaderJson(modelPath);
            ModelMetadata meta = architectureAnalyzer.analyze(headerJson);
            return targetArch != null && targetArch.equalsIgnoreCase(meta.getArchitectureType());
        } catch (Exception e) {
            return modelPath.getFileName().toString().toUpperCase().contains(targetArch.toUpperCase());
        }
    }
}
