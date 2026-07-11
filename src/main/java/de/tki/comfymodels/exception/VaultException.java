package de.tki.comfymodels.exception;

/**
 * Exception für Vault-bezogene Fehler.
 * Wird verwendet bei:
 * - Master-Passwort-Fehlern
 * - Encryption-/Decryption-Fehlern
 * - Corrupten Vault-Files
 */
public class VaultException extends CompanionException {
    
    public VaultException(String message) {
        super(message);
    }
    
    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
