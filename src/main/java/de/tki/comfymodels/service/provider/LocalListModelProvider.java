package de.tki.comfymodels.service.provider;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ModelListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ModelProviderStrategy implementation querying the local curated model list.
 */
@Component
public class LocalListModelProvider implements ModelProviderStrategy {

    private final ModelListService modelListService;

    @Autowired
    public LocalListModelProvider(ModelListService modelListService) {
        this.modelListService = modelListService;
    }

    @Override
    public String getProviderName() {
        return "LOCAL_LIST";
    }

    @Override
    public boolean supports(String source) {
        return "LOCAL_LIST".equalsIgnoreCase(source);
    }

    @Override
    public CompletableFuture<List<ModelInfo>> searchModels(String query) {
        if (modelListService == null || query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<ModelInfo> matched = modelListService.getModels().stream()
                .filter(m -> (m.getName() != null && m.getName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                        || (m.getFilename() != null && m.getFilename().toLowerCase(Locale.ROOT).contains(lowerQuery)))
                .toList();
        return CompletableFuture.completedFuture(matched);
    }

    @Override
    public Optional<String> findDownloadUrlByHash(String hash) {
        return Optional.empty();
    }

    @Override
    public Optional<ModelInfo> checkForUpdate(ModelInfo current) {
        return Optional.empty();
    }
}
