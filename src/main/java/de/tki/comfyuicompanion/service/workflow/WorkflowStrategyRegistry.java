package de.tki.comfyuicompanion.service.workflow;

import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Enterprise Service Registry coordinating and resolving {@link IWorkflowSourceStrategy}
 * implementations dynamically based on {@link WorkflowSourceType}.
 *
 * @author Senior Java Developer
 */
@Component
public class WorkflowStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStrategyRegistry.class);
    private final Map<WorkflowSourceType, IWorkflowSourceStrategy> strategies = new EnumMap<>(WorkflowSourceType.class);

    /**
     * Constructs the registry by automatically discovering and registering all available strategy beans.
     *
     * @param strategyList list of detected strategy beans
     */
    @Autowired
    public WorkflowStrategyRegistry(List<IWorkflowSourceStrategy> strategyList) {
        if (strategyList != null) {
            for (IWorkflowSourceStrategy strategy : strategyList) {
                strategies.put(strategy.getSourceType(), strategy);
                log.info("Registered workflow source strategy: {} -> {}",
                        strategy.getSourceType(), strategy.getClass().getSimpleName());
            }
        }
    }

    /**
     * Retrieves the strategy responsible for handling the given workflow source type.
     *
     * @param sourceType source origin type
     * @return registered strategy
     * @throws IllegalArgumentException if no strategy is registered for the specified type
     */
    public IWorkflowSourceStrategy getStrategy(WorkflowSourceType sourceType) {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        return Optional.ofNullable(strategies.get(sourceType))
                .orElseThrow(() -> new IllegalArgumentException("No workflow strategy registered for " + sourceType));
    }

    /**
     * Resolves the strategy for a given workflow descriptor.
     *
     * @param descriptor workflow descriptor
     * @return registered strategy
     */
    public IWorkflowSourceStrategy getStrategy(WorkflowDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return getStrategy(descriptor.sourceType());
    }
}
