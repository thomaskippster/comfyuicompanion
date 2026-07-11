package de.tki.comfymodels.util;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared, Spring-managed executor for background work.
 *
 * <p>Replaces the dozens of raw {@code new Thread(...).start()} sites that previously
 * littered the codebase. Centralising the pool gives us:
 * <ul>
 *   <li>A single lifecycle owner (Spring's bean container) so no orphaned threads
 *       on application shutdown.</li>
 *   <li>Daemon threads: JVM exit is no longer blocked by in-flight work.</li>
 *   <li>Named threads for easier debugging in stack traces and profilers.</li>
 *   <li>A bounded core size so we do not unboundedly spawn threads for I/O-bound tasks.</li>
 * </ul>
 *
 * <p>Use {@link #execute(Runnable)} for fire-and-forget work,
 * {@link #schedule(Runnable, long, TimeUnit)} for delayed work,
 * and {@link #submit(Callable)} when a return value is needed.
 */
@Component
public class BackgroundExecutor {

    private final ThreadPoolExecutor pool;
    private final ScheduledExecutorService scheduler;

    public BackgroundExecutor() {
        // Sized to comfortably cover the ~22 raw-thread sites in Main.java plus the
        // rest of the services. Tasks here are I/O-bound (file polling, HTTP, network),
        // so a generous pool is correct.
        int coreSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        this.pool = new ThreadPoolExecutor(
                coreSize,
                coreSize * 2,
                60L,
                TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                namedFactory("comfy-bg"));

        // Small dedicated scheduler for delayed/periodic tasks. Sharing the main pool
        // would be wrong because a saturated pool would delay scheduled tasks.
        this.scheduler = new ScheduledThreadPoolExecutor(2, namedFactory("comfy-bg-sched"));
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    System.err.println("[BackgroundExecutor] Uncaught exception in " + thread.getName() + ": " + throwable));
            return t;
        };
    }

    /** Fire-and-forget execution on a worker thread. */
    public void execute(Runnable task) {
        pool.execute(task);
    }

    /** Submit a callable and return a future for its result. */
    public <T> Future<T> submit(Callable<T> task) {
        return pool.submit(task);
    }

    /** Schedule a task to run after {@code delay} has elapsed. */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(task, delay, unit);
    }

    /**
     * Schedule a task to run repeatedly with a fixed delay between the end of one
     * execution and the start of the next.
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    /** Best-effort shutdown triggered by Spring on application close. */
    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        scheduler.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
