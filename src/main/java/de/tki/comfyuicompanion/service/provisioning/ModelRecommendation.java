package de.tki.comfyuicompanion.service.provisioning;

/**
 * Represents a recommended model for provisioning.
 */
public class ModelRecommendation {
    private String repositoryId;
    private String name;
    private String downloadUrl;
    private int downloads;

    /**
     * Gets the repository ID.
     * @return The repository ID.
     */
    public String getRepositoryId() { return repositoryId; }

    /**
     * Sets the repository ID.
     * @param repositoryId The repository ID.
     */
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    /**
     * Gets the model name.
     * @return The name.
     */
    public String getName() { return name; }

    /**
     * Sets the model name.
     * @param name The name.
     */
    public void setName(String name) { this.name = name; }

    /**
     * Gets the download URL for the model.
     * @return The download URL.
     */
    public String getDownloadUrl() { return downloadUrl; }

    /**
     * Sets the download URL for the model.
     * @param downloadUrl The download URL.
     */
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    /**
     * Gets the number of downloads.
     * @return The download count.
     */
    public int getDownloads() { return downloads; }

    /**
     * Sets the number of downloads.
     * @param downloads The download count.
     */
    public void setDownloads(int downloads) { this.downloads = downloads; }
}
