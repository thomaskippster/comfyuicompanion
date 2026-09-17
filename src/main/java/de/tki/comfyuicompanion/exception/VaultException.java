package de.tki.comfyuicompanion.exception;

/**
 * Exception for vault-related security and encryption operations.
 */
public class VaultException extends ComfyCompanionException {
    
    public VaultException(String message) {
        super(message);
    }
    
    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
