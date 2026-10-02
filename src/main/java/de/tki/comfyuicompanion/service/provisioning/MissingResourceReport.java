package de.tki.comfyuicompanion.service.provisioning;

/**
 * Represents a report detailing a missing resource during provisioning.
 */
public class MissingResourceReport {

    /**
     * Defines the types of resources that can be missing.
     */
    public enum ResourceType {
        CHECKPOINT,
        LORA
    }

    private boolean isMissing;
    private ResourceType type;
    private String architecture;
    private String keyword;
    private String resolvedModelName;

    /**
     * Checks if the resource is marked as missing.
     * @return True if missing, false otherwise.
     */
    public boolean isMissing() { return isMissing; }

    /**
     * Sets the missing status of the resource.
     * @param missing The missing status.
     */
    public void setMissing(boolean missing) { isMissing = missing; }

    /**
     * Gets the type of the missing resource.
     * @return The resource type.
     */
    public ResourceType getType() { return type; }

    /**
     * Sets the type of the missing resource.
     * @param type The resource type.
     */
    public void setType(ResourceType type) { this.type = type; }

    /**
     * Gets the architecture associated with the missing resource.
     * @return The architecture.
     */
    public String getArchitecture() { return architecture; }

    /**
     * Sets the architecture for the missing resource.
     * @param architecture The architecture.
     */
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    /**
     * Gets the keyword representing the missing resource.
     * @return The keyword.
     */
    public String getKeyword() { return keyword; }

    /**
     * Sets the keyword representing the missing resource.
     * @param keyword The keyword.
     */
    public void setKeyword(String keyword) { this.keyword = keyword; }

    /**
     * Gets the resolved model name.
     * @return The resolved model name.
     */
    public String getResolvedModelName() { return resolvedModelName; }

    /**
     * Sets the resolved model name.
     * @param resolvedModelName The resolved model name.
     */
    public void setResolvedModelName(String resolvedModelName) { this.resolvedModelName = resolvedModelName; }
}
