package de.tki.comfyuicompanion.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * Enterprise-grade global uncaught exception handler for Companion for ComfyUI.
 * Catches unhandled runtime exceptions across the Swing Event Dispatch Thread (EDT),
 * background worker threads, and Java 21 Virtual Threads, ensuring full diagnostic
 * logging via SLF4J and user notification in graphical environments.
 */
public class GlobalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalUncaughtExceptionHandler.class);

    /**
     * Registers this handler as the default uncaught exception handler for all threads.
     */
    public static void register() {
        Thread.setDefaultUncaughtExceptionHandler(new GlobalUncaughtExceptionHandler());
        logger.info("🛡️ [Resilience] GlobalUncaughtExceptionHandler successfully registered.");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String threadName = t != null ? t.getName() : "Unknown-Thread";
        logger.error("💥 [CRITICAL] Uncaught exception in thread '{}': {}", threadName, e.getMessage(), e);

        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    String msg = e.getMessage() != null && !e.getMessage().isBlank()
                            ? e.getMessage()
                            : e.getClass().getSimpleName();
                    JOptionPane.showMessageDialog(
                            null,
                            "An unexpected error occurred in [" + threadName + "]:\n" + msg + "\n\nDetails have been logged for diagnosis.",
                            "Unexpected Application Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } catch (Throwable ignored) {
                    // Prevent secondary failure during error reporting
                }
            });
        }
    }
}
