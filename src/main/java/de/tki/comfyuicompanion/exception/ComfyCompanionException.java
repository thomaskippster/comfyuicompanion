package de.tki.comfyuicompanion.exception;

/**
 * Abstract base runtime exception for the ComfyUI Companion application.
 * All domain-specific and operational exceptions extend this class, enabling
 * structured and centralized exception handling.
 */
public abstract class ComfyCompanionException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message The detail message
     */
    protected ComfyCompanionException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause   The root cause
     */
    protected ComfyCompanionException(String message, Throwable cause) {
        super(message, cause);
    }
}
