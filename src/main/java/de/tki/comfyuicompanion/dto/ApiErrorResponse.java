package de.tki.comfyuicompanion.dto;

import java.time.Instant;

/**
 * Standardized immutable error response object for REST API exceptions.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    /**
     * Factory method to create a new ApiErrorResponse.
     *
     * @param status  The HTTP status code.
     * @param error   The short error description.
     * @param message The detailed error message.
     * @param path    The request path where the error occurred.
     * @return A newly created ApiErrorResponse containing the provided details and the current timestamp.
     */
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}
