package de.tki.comfyuicompanion.exception;

/**
 * Exception for vault-related security and encryption operations.
 */
public class VaultException extends ComfyCompanionException {
    
    /**
     * Constructs a VaultException with a detail message.
     *
     * @param message the detail error description
     */
    public VaultException(String message) {
        super(message);
    }
    
    /**
     * Constructs a VaultException with a detail message and cause.
     *
     * @param message the detail error description
     * @param cause   the root cause
     */
    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
