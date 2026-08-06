package de.tki.comfymodels.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ComfyRegistryWorkflow {

    private String id;
    private String title;
    private String description;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("media_type")
    private String mediaType;

    @JsonProperty("media_subtype")
    private String mediaSubtype;

    @JsonProperty("json_download_url")
    private String jsonDownloadUrl;

    private String author;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("popular_score")
    private double popularScore;

    @JsonProperty("required_models")
    private List<String> requiredModels = new ArrayList<>();

    // Locally computed field, not populated by Jackson from the API response directly
    private boolean hasAllModelsLocal;

    private List<ModelInfo> requiredModelInfos = new ArrayList<>();

    private String category;

    private List<String> previewCandidates = new ArrayList<>();

    public List<String> getPreviewCandidates() {
        return previewCandidates;
    }

    public void setPreviewCandidates(List<String> previewCandidates) {
        this.previewCandidates = previewCandidates;
    }

    public ComfyRegistryWorkflow() {}


    public List<ModelInfo> getRequiredModelInfos() {
        return requiredModelInfos;
    }

    public void setRequiredModelInfos(List<ModelInfo> requiredModelInfos) {
        this.requiredModelInfos = requiredModelInfos;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getJsonDownloadUrl() {
        return jsonDownloadUrl;
    }

    public void setJsonDownloadUrl(String jsonDownloadUrl) {
        this.jsonDownloadUrl = jsonDownloadUrl;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public double getPopularScore() {
        return popularScore;
    }

    public void setPopularScore(double popularScore) {
        this.popularScore = popularScore;
    }

    public List<String> getRequiredModels() {
        return requiredModels;
    }

    public void setRequiredModels(List<String> requiredModels) {
        this.requiredModels = requiredModels;
    }

    private boolean cloudOnly;

    public boolean isCloudOnly() {
        return cloudOnly;
    }

    public void setCloudOnly(boolean cloudOnly) {
        this.cloudOnly = cloudOnly;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getMediaSubtype() {
        return mediaSubtype;
    }

    public void setMediaSubtype(String mediaSubtype) {
        this.mediaSubtype = mediaSubtype;
    }

    /** True if the preview for this workflow is a video (mp4/webm). */
    public boolean isVideoPreview() {
        if (mediaType != null && mediaType.toLowerCase().contains("video")) return true;
        if (thumbnailUrl != null) {
            String lower = thumbnailUrl.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) return true;
        }
        return false;
    }

    public boolean isHasAllModelsLocal() {
        return hasAllModelsLocal;
    }

    public void setHasAllModelsLocal(boolean hasAllModelsLocal) {
        this.hasAllModelsLocal = hasAllModelsLocal;
    }
}
