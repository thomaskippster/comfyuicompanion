package de.tki.comfymodels.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared, Spring-managed executor utilizing Java 21 Virtual Threads for non-blocking,
 * ultra-lightweight asynchronous background operations.
 */
@Component
public class BackgroundExecutor {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundExecutor.class);

    private final ExecutorService virtualExecutor;
    private final ScheduledExecutorService scheduler;

    public BackgroundExecutor() {
        // Java 21 Virtual Threads: lightweight, high-throughput execution for I/O operations
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Virtual-thread backed scheduler for periodic background jobs
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("comfy-bg-sched-", 1).factory());
    }

    /** Fire-and-forget execution on a virtual thread. */
    public void execute(Runnable task) {
        if (task == null) return;
        virtualExecutor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.error("[BackgroundExecutor] Unhandled exception in virtual task: {}", t.getMessage(), t);
            }
        });
    }

    /** Submit a callable and return a future for its result. */
    public <T> Future<T> submit(Callable<T> task) {
        return virtualExecutor.submit(task);
    }

    /** Schedule a task to run after {@code delay} has elapsed on a virtual thread. */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(() -> execute(task), delay, unit);
    }

    /**
     * Schedule a task to run repeatedly with a fixed delay between the end of one
     * execution and the start of the next.
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(() -> execute(task), initialDelay, delay, unit);
    }

    /** Graceful shutdown triggered by Spring container close. */
    @PreDestroy
    public void shutdown() {
        virtualExecutor.shutdown();
        scheduler.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualExecutor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
