package com.thomaskippster.comfyuicompanion.dto;

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
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}
