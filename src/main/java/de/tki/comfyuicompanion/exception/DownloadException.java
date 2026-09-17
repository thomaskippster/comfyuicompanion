package de.tki.comfyuicompanion.exception;

/**
 * Exception for download-related errors (network issues, auth tokens, disk space, corrupt files).
 */
public class DownloadException extends ComfyCompanionException {
    
    private final String downloadUrl;
    private final String modelFilename;
    
    public DownloadException(String message) {
        super(message);
        this.downloadUrl = null;
        this.modelFilename = null;
    }
    
    public DownloadException(String message, Throwable cause) {
        super(message, cause);
        this.downloadUrl = null;
        this.modelFilename = null;
    }
    
    public DownloadException(String message, String url, String filename) {
        super(String.format("[url=%s, file=%s] %s", url, filename, message));
        this.downloadUrl = url;
        this.modelFilename = filename;
    }
    
    public DownloadException(String message, String url, String filename, Throwable cause) {
        super(String.format("[url=%s, file=%s] %s", url, filename, message), cause);
        this.downloadUrl = url;
        this.modelFilename = filename;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public String getModelFilename() {
        return modelFilename;
    }
}
