package de.tki.comfyuicompanion.service.pipeline.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Enterprise registry discovering and coordinating {@link IVideoModelStrategy} implementations.
 * Enables dynamic resolution of video diffusion pipelines based on hardware capabilities,
 * model availability, and user selection.
 */
@Component
public class VideoModelStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(VideoModelStrategyRegistry.class);

    private final List<IVideoModelStrategy> strategies = new CopyOnWriteArrayList<>();

    public VideoModelStrategyRegistry() {
        this(List.of());
    }

    @Autowired
    public VideoModelStrategyRegistry(List<IVideoModelStrategy> strategyList) {
        if (strategyList != null && !strategyList.isEmpty()) {
            List<IVideoModelStrategy> sorted = new ArrayList<>(strategyList);
            sorted.sort(Comparator.comparingInt(IVideoModelStrategy::getPriority).reversed());
            this.strategies.addAll(sorted);
            for (IVideoModelStrategy s : sorted) {
                log.info("Registered video model strategy: {} [{}] (Priority: {})",
                        s.getModelId(), s.getDisplayName(), s.getPriority());
            }
        }
    }

    /**
     * @return unmodifiable list of all registered video model strategies in descending priority order
     */
    public List<IVideoModelStrategy> getAllStrategies() {
        return Collections.unmodifiableList(strategies);
    }

    /**
     * @return array of human-readable display names suitable for JComboBox models
     */
    public String[] getDisplayNames() {
        return strategies.stream()
                .filter(s -> !(s instanceof ImageDiffusionFallbackStrategy))
                .map(IVideoModelStrategy::getDisplayName)
                .toArray(String[]::new);
    }

    /**
     * Resolves a strategy by its unique ID or display name.
     */
    public Optional<IVideoModelStrategy> findById(String idOrDisplayName) {
        if (idOrDisplayName == null || idOrDisplayName.isBlank()) {
            return Optional.empty();
        }
        String clean = idOrDisplayName.trim().toLowerCase();
        return strategies.stream()
                .filter(s -> s.getModelId().equalsIgnoreCase(clean)
                        || s.getDisplayName().equalsIgnoreCase(idOrDisplayName.trim())
                        || s.getDisplayName().toLowerCase().contains(clean))
                .findFirst();
    }

    /**
     * Dynamically determines the best video model strategy based on user preference,
     * available models, and node capabilities.
     *
     * @param requestedModel optional model requested by user or scene configuration
     * @param serverUrl active ComfyUI URL
     * @param availableUnets list of UNETs
     * @param availableCheckpoints list of checkpoints
     * @param availableClips list of CLIP models
     * @param availableVaes list of VAE models
     * @param nodeClassChecker predicate testing if a node class is present
     * @return best matching strategy (guaranteed non-null fallback)
     */
    public IVideoModelStrategy resolveBestStrategy(
            String requestedModel,
            String serverUrl,
            List<String> availableUnets,
            List<String> availableCheckpoints,
            List<String> availableClips,
            List<String> availableVaes,
            Function<String, Boolean> nodeClassChecker) {

        // 1. If user requested a specific model, verify and use it if available
        if (requestedModel != null && !requestedModel.isBlank()) {
            Optional<IVideoModelStrategy> specific = findById(requestedModel);
            if (specific.isPresent() && specific.get().isAvailable(serverUrl, availableUnets, availableCheckpoints, availableClips, availableVaes, nodeClassChecker)) {
                log.info("🎯 [VideoModelStrategyRegistry] Selected requested strategy: {}", specific.get().getDisplayName());
                return specific.get();
            }
        }

        // 2. Otherwise pick the highest-priority strategy whose prerequisites are met
        for (IVideoModelStrategy s : strategies) {
            if (!(s instanceof ImageDiffusionFallbackStrategy)
                    && s.isAvailable(serverUrl, availableUnets, availableCheckpoints, availableClips, availableVaes, nodeClassChecker)) {
                log.info("⚡ [VideoModelStrategyRegistry] Auto-detected best available video strategy: {}", s.getDisplayName());
                return s;
            }
        }

        // 3. Fallback to image diffusion fallback
        return strategies.stream()
                .filter(s -> s instanceof ImageDiffusionFallbackStrategy)
                .findFirst()
                .orElse(strategies.isEmpty() ? null : strategies.get(strategies.size() - 1));
    }
}
