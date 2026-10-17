package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.PromptHistoryEntry;

import java.util.List;

/**
 * Service managing the persistent history and recall of recent Image Lab prompt executions.
 */
public interface IPromptHistoryService {

    /**
     * Records a new prompt session into history, maintaining the rolling cap (default 25).
     *
     * @param entry the prompt execution details to record
     */
    void recordPrompt(PromptHistoryEntry entry);

    /**
     * Retrieves the chronological list of recent prompt entries, most recent first.
     *
     * @return unmodifiable list of recent prompt entries
     */
    List<PromptHistoryEntry> getHistory();

    /**
     * Clears all recorded prompt history.
     */
    void clearHistory();
}
