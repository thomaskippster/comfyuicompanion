package de.tki.comfyuicompanion.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link GlobalUncaughtExceptionHandler}.
 */
class GlobalUncaughtExceptionHandlerTest {

    @Test
    @DisplayName("uncaughtException: should handle standard runtime exceptions gracefully")
    void shouldHandleStandardExceptionWithoutThrowing() {
        GlobalUncaughtExceptionHandler handler = new GlobalUncaughtExceptionHandler();
        Thread thread = new Thread(() -> {}, "test-worker-thread");
        RuntimeException ex = new RuntimeException("Simulated background error");

        assertThatCode(() -> handler.uncaughtException(thread, ex)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("uncaughtException: should handle null thread or message without throwing")
    void shouldHandleNullThreadAndMessageGracefully() {
        GlobalUncaughtExceptionHandler handler = new GlobalUncaughtExceptionHandler();
        RuntimeException ex = new RuntimeException((String) null);

        assertThatCode(() -> handler.uncaughtException(null, ex)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("register: should set default uncaught exception handler on JVM")
    void shouldRegisterAsDefaultHandler() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        try {
            GlobalUncaughtExceptionHandler.register();
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isInstanceOf(GlobalUncaughtExceptionHandler.class);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }
}
