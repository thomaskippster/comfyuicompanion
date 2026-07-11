package de.tki.comfymodels.exception;

/**
 * Exception für Download-bezogene Fehler.
 * Wird verwendet bei:
 * - Netzwerk-Problemen
 * - Auth-Fehlern (Tokens)
 * - Disk-Space-Mangel
 * - Corrupten Files
 * - LFS Stubs
 */
public class DownloadException extends CompanionException {
    
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
        super(message, String.format("url=%s, file=%s", url, filename));
        this.downloadUrl = url;
        this.modelFilename = filename;
    }
    
    public DownloadException(String message, String url, String filename, Throwable cause) {
        super(message, String.format("url=%s, file=%s", url, filename), cause);
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
