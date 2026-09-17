package de.tki.comfyuicompanion.exception;

/**
 * Exception thrown when interaction with the ComfyUI HTTP/WebSocket API fails.
 * Captures HTTP status code, endpoint details, and failure context.
 */
public class ComfyApiException extends ComfyCompanionException {

    private final Integer statusCode;
    private final String endpoint;

    /**
     * Constructs a ComfyApiException with detail message.
     *
     * @param message Detail error description
     */
    public ComfyApiException(String message) {
        this(message, null, null, null);
    }

    /**
     * Constructs a ComfyApiException with detail message and cause.
     *
     * @param message Detail error description
     * @param cause   Root cause
     */
    public ComfyApiException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    /**
     * Constructs a ComfyApiException with status code and endpoint.
     *
     * @param message    Detail error description
     * @param statusCode HTTP response status code
     * @param endpoint   Target API endpoint
     */
    public ComfyApiException(String message, Integer statusCode, String endpoint) {
        this(message, statusCode, endpoint, null);
    }

    /**
     * Constructs a fully contextualized ComfyApiException.
     *
     * @param message    Detail error description
     * @param statusCode HTTP response status code
     * @param endpoint   Target API endpoint
     * @param cause      Root cause
     */
    public ComfyApiException(String message, Integer statusCode, String endpoint, Throwable cause) {
        super(String.format("%s [endpoint=%s, status=%s]", message, endpoint != null ? endpoint : "n/a", statusCode != null ? statusCode : "n/a"), cause);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
