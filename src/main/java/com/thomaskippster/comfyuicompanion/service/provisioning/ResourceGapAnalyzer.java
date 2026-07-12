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

    @Value("${comfyui.models.path:models}")
    private String modelsBasePath;

    public ResourceGapAnalyzer(SafetensorsInspectorService inspectorService,
                               ModelArchitectureAnalyzer architectureAnalyzer,
                               HuggingFaceClient huggingFaceClient,
                               LargeFileDownloadService downloadService,
                               ComfyHttpClient comfyHttpClient) {
        this.inspectorService = inspectorService;
        this.architectureAnalyzer = architectureAnalyzer;
        this.huggingFaceClient = huggingFaceClient;
        this.downloadService = downloadService;
        this.comfyHttpClient = comfyHttpClient;
    }

    /**
     * Steuert den gesamten Flow: Gap erkannt -> Hugging Face gesucht -> 
     * Modell gestreamt -> /refresh_models getriggert -> Graph asynchron an /prompt gesendet.
     */
    public Mono<String> resolveGapAndExecute(GenerationIntent intent, ComfyWorkflow workflow, String clientId) {
        MissingResourceReport report = analyze(intent);

        if (!report.isMissing()) {
            logger.info("Auto-Provisioning: Alle Ressourcen vorhanden. Führe Graph direkt aus.");
            return comfyHttpClient.triggerWorkflow(workflow, clientId);
        }

        logger.info("Auto-Provisioning: Lücke erkannt. Suche auf Hugging Face nach '{}' (Architektur: {})", 
                report.getKeyword(), report.getArchitecture());

        List<ModelRecommendation> recommendations = huggingFaceClient.searchModels(report.getKeyword(), report.getArchitecture());
        
        if (recommendations.isEmpty()) {
            return Mono.error(new RuntimeException("Auto-Provisioning fehlgeschlagen: Kein passendes Modell auf Hugging Face gefunden."));
        }

        ModelRecommendation topPick = recommendations.get(0); // Bestes Modell (nach Downloads sortiert)
        Path targetPath = Paths.get(modelsBasePath, "checkpoints", topPick.getName() + "_" + report.getArchitecture() + ".safetensors");
        
        // Verzeichnisse sicherstellen
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            return Mono.error(new RuntimeException("Konnte Modell-Ordner nicht erstellen", e));
        }

        logger.info("Auto-Provisioning: Starte Download von {} nach {}", topPick.getDownloadUrl(), targetPath);

        // Reaktive Pipeline: Download (Flux) -> Refresh (Mono) -> Execute (Mono)
        return downloadService.downloadModel(topPick.getDownloadUrl(), targetPath)
                // Loggt alle 10% den Fortschritt, um die Konsole nicht zu fluten
                .filter(progress -> progress % 10.0 < 1.0) 
                .doOnNext(progress -> logger.info("Download Fortschritt: {}%", String.format("%.1f", progress)))
                .then(Mono.defer(() -> {
                    logger.info("Auto-Provisioning: Download abgeschlossen. Triggere ComfyUI Hot-Reload (/refresh_models).");
                    return comfyHttpClient.triggerModelRefresh();
                }))
                .then(Mono.defer(() -> {
                    logger.info("Auto-Provisioning: Hot-Reload erfolgreich. Sende verzögerten Ursprungs-Graphen an ComfyUI.");
                    // Update den CheckpointLoaderSimple Node im Graphen mit dem echten neuen Dateinamen
                    workflow.getNodes().values().stream()
                            .filter(node -> "CheckpointLoaderSimple".equals(node.getClassType()))
                            .forEach(node -> node.getInputs().put("ckpt_name", targetPath.getFileName().toString()));
                            
                    return comfyHttpClient.triggerWorkflow(workflow, clientId);
                }));
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
