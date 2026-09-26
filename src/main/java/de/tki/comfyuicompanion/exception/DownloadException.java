package de.tki.comfyuicompanion.exception;

/**
 * Exception for download-related errors (network issues, auth tokens, disk space, corrupt files).
 */
public class DownloadException extends ComfyCompanionException {
    
    private final String downloadUrl;
    private final String modelFilename;
    
    /**
     * Constructs a DownloadException with a detail message.
     *
     * @param message the detail error description
     */
    public DownloadException(String message) {
        super(message);
        this.downloadUrl = null;
        this.modelFilename = null;
    }
    
    /**
     * Constructs a DownloadException with a detail message and cause.
     *
     * @param message the detail error description
     * @param cause   the root cause
     */
    public DownloadException(String message, Throwable cause) {
        super(message, cause);
        this.downloadUrl = null;
        this.modelFilename = null;
    }
    
    /**
     * Constructs a DownloadException with contextual URL and filename.
     *
     * @param message  the detail error description
     * @param url      the download URL
     * @param filename the model filename
     */
    public DownloadException(String message, String url, String filename) {
        super(String.format("[url=%s, file=%s] %s", url, filename, message));
        this.downloadUrl = url;
        this.modelFilename = filename;
    }
    
    /**
     * Constructs a fully contextualized DownloadException.
     *
     * @param message  the detail error description
     * @param url      the download URL
     * @param filename the model filename
     * @param cause    the root cause
     */
    public DownloadException(String message, String url, String filename, Throwable cause) {
        super(String.format("[url=%s, file=%s] %s", url, filename, message), cause);
        this.downloadUrl = url;
        this.modelFilename = filename;
    }
    
    /** @return the download URL */
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    /** @return the model filename */
    public String getModelFilename() {
        return modelFilename;
    }
}
