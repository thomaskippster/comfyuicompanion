package de.tki.comfymodels.exception;

/**
 * Basis-Exception für alle ComfyUI Companion-spezifischen Fehler.
 * Ermöglicht strukturiertes Error-Handling und besseres Debugging.
 */
public class CompanionException extends Exception {
    
    private final String context;
    
    public CompanionException(String message) {
        super(message);
        this.context = null;
    }
    
    public CompanionException(String message, Throwable cause) {
        super(message, cause);
        this.context = null;
    }
    
    public CompanionException(String message, String context) {
        super(message);
        this.context = context;
    }
    
    public CompanionException(String message, String context, Throwable cause) {
        super(message, cause);
        this.context = context;
    }
    
    public String getContext() {
        return context;
    }
    
    @Override
    public String getMessage() {
        if (context != null && !context.isEmpty()) {
            return String.format("[%s] %s", context, super.getMessage());
        }
        return super.getMessage();
    }
}
