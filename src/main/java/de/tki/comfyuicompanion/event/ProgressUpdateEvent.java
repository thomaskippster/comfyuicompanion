package de.tki.comfyuicompanion.event;

/**
 * Event published when ComfyUI reports progress for a rendering workflow.
 * Encapsulates current and max step counters, as well as optional prompt and client identifiers
 * for targeted request scoping.
 */
public class ProgressUpdateEvent {
    private final int value;
    private final int max;
    private final String promptId;
    private final String clientId;

    /**
     * Constructs a legacy ProgressUpdateEvent without specific request scoping.
     *
     * @param value Current progress step count
     * @param max   Maximum progress step count
     */
    public ProgressUpdateEvent(int value, int max) {
        this(value, max, null, null);
    }

    /**
     * Constructs a fully scoped ProgressUpdateEvent.
     *
     * @param value    Current progress step count
     * @param max      Maximum progress step count
     * @param promptId Identifier of the executing prompt
     * @param clientId Identifier of the associated client session
     */
    public ProgressUpdateEvent(int value, int max, String promptId, String clientId) {
        this.value = value;
        this.max = max;
        this.promptId = promptId;
        this.clientId = clientId;
    }

    /**
     * Gets the current progress value.
     *
     * @return current progress step
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets the maximum progress value.
     *
     * @return maximum step count
     */
    public int getMax() {
        return max;
    }

    /**
     * Gets the executing prompt ID, if available.
     *
     * @return prompt ID or null
     */
    public String getPromptId() {
        return promptId;
    }

    /**
     * Gets the client identifier, if available.
     *
     * @return client ID or null
     */
    public String getClientId() {
        return clientId;
    }
}
