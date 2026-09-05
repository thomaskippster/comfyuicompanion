package com.thomaskippster.comfyuicompanion.controller;

import com.thomaskippster.comfyuicompanion.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleIllegalArgumentException: should return 400 Bad Request with details")
    void shouldHandleIllegalArgumentException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/generate").build()
        );
        IllegalArgumentException ex = new IllegalArgumentException("Prompt darf nicht leer sein.");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleIllegalArgumentException(ex, exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).isEqualTo("Prompt darf nicht leer sein.");
        assertThat(body.path()).isEqualTo("/api/v1/generate");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleSecurityException: should return 403 Forbidden with details")
    void shouldHandleSecurityException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/image/../forbidden.png").build()
        );
        SecurityException ex = new SecurityException("Path traversal attempt detected!");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleSecurityException(ex, exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.error()).isEqualTo("Forbidden");
        assertThat(body.message()).isEqualTo("Path traversal attempt detected!");
        assertThat(body.path()).isEqualTo("/api/v1/image/../forbidden.png");
    }

    @Test
    @DisplayName("handleGenericException: should return 500 Internal Server Error without leaking sensitive traces")
    void shouldHandleGenericException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/status/123").build()
        );
        RuntimeException ex = new RuntimeException("Database timeout on internal cluster");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericException(ex, exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Internal Server Error");
        assertThat(body.message()).isEqualTo("Ein interner Serverfehler ist aufgetreten.");
        assertThat(body.path()).isEqualTo("/api/v1/status/123");
    }
}
