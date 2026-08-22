package de.tki.comfymodels.service.impl;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring-managed registry of every {@link Process} spawned by the application.
 *
 * <p>Replaces the ~60 bare {@code ProcessBuilder.start()} call sites that previously had
 * no lifecycle tracking. When the application shuts down, {@link #destroyAll()} walks
 * the registry and {@code destroyForcibly()} every process, so ffmpeg / python / conda
 * children never outlive the JVM.
 *
 * <p>The registry is concurrent so producers (service code) and the consumer
 * (shutdown hook) never block each other.
 */
@Component
public class ProcessTracker {

    private final Set<Process> processes = ConcurrentHashMap.newKeySet();

    /** Register a process so it can be destroyed on shutdown. */
    public void register(Process process) {
        if (process != null) {
            processes.add(process);
        }
    }

    /** Remove a process from the registry. Call after a normal exit. */
    public void unregister(Process process) {
        if (process != null) {
            processes.remove(process);
        }
    }

    /** Remove all terminated processes from the registry. */
    public void cleanupTerminated() {
        processes.removeIf(p -> !p.isAlive());
    }


    /**
     * Start a {@link ProcessBuilder} and register the resulting process in one call.
     * Use this everywhere in place of {@code pb.start()}.
     */
    public Process start(ProcessBuilder pb) throws IOException {
        Process p = pb.start();
        register(p);
        return p;
    }

    /** Number of currently tracked live processes. */
    public int trackedCount() {
        return processes.size();
    }

    /**
     * Forcibly destroy every tracked process. Called by Spring on application close
     * AND explicitly from the JVM shutdown hook to be safe.
     */
    @PreDestroy
    public void destroyAll() {
        for (Process p : processes) {
            try {
                if (p.isAlive()) {
                    p.destroy();
                    try {
                        if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                            p.destroyForcibly();
                        }
                    } catch (InterruptedException e) {
                        p.destroyForcibly();
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (Exception ignored) {
                // best-effort: we are tearing down, don't propagate
            } finally {
                processes.remove(p);
            }
        }
    }
}
