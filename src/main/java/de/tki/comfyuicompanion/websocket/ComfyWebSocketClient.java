package de.tki.comfyuicompanion.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance WebSocket client for connecting to the ComfyUI WebSocket server.
 * Provides bounded timeouts for connection attempts, asynchronous execution,
 * and resilient reconnection capabilities with exponential backoff.
 */
@Service
public class ComfyWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(ComfyWebSocketClient.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private final String baseUrl;
    private final ComfyWebSocketHandler handler;
    private final int timeoutSeconds;

    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "comfy-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocketSession currentSession;
    private volatile String activeClientId;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public ComfyWebSocketClient() {
        this("ws://127.0.0.1:8188/ws", DEFAULT_TIMEOUT_SECONDS, null);
    }

    @Autowired
    public ComfyWebSocketClient(
            @Value("${comfyui.ws.url:ws://127.0.0.1:8188/ws}") String baseUrl,
            @Value("${comfyui.ws.timeout:10}") int timeoutSeconds,
            @Autowired(required = false) ComfyWebSocketHandler handler) {
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "ws://127.0.0.1:8188/ws";
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.handler = handler;
    }

    public ComfyWebSocketClient(
            String baseUrl,
            ComfyWebSocketHandler handler) {
        this(baseUrl, DEFAULT_TIMEOUT_SECONDS, handler);
    }

    /**
     * Connects synchronously to the ComfyUI WebSocket endpoint with a bounded timeout.
     *
     * @param clientId The unique client identifier used when triggering the workflow.
     */
    public void connect(String clientId) {
        try {
            connectAsync(clientId).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Timed out after {}s waiting to connect to ComfyUI WebSocket at {}", timeoutSeconds, baseUrl);
            throw new RuntimeException("WebSocket connection timed out for clientId: " + clientId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WebSocket connection interrupted for clientId: " + clientId, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to connect to ComfyUI WebSocket at " + baseUrl, e.getCause());
        }
    }

    /**
     * Connects asynchronously to the ComfyUI WebSocket endpoint.
     *
     * @param clientId The unique client identifier
     * @return CompletableFuture completing with the established WebSocketSession
     */
    public CompletableFuture<WebSocketSession> connectAsync(String clientId) {
        this.activeClientId = clientId;
        if (!isConnecting.compareAndSet(false, true)) {
            logger.debug("Connection attempt already in progress for ComfyUI WebSocket");
            CompletableFuture<WebSocketSession> future = new CompletableFuture<>();
            if (currentSession != null && currentSession.isOpen()) {
                future.complete(currentSession);
            } else {
                future.completeExceptionally(new IllegalStateException("Connection already in progress"));
            }
            return future;
        }

        disconnectCurrentSession();

        StandardWebSocketClient client = new StandardWebSocketClient();
        String uriStr = baseUrl + "?clientId=" + clientId;
        logger.info("Connecting to ComfyUI WebSocket at {} (timeout: {}s)...", uriStr, timeoutSeconds);

        return client.execute(handler, uriStr)
                .toCompletableFuture()
                .thenApply(session -> {
                    this.currentSession = session;
                    this.isConnecting.set(false);
                    this.reconnectAttempts.set(0);
                    logger.info("Successfully connected to ComfyUI WebSocket: {}", session.getId());
                    return session;
                })
                .exceptionally(throwable -> {
                    this.isConnecting.set(false);
                    logger.warn("Failed to connect to ComfyUI WebSocket: {}. Scheduling reconnect...", throwable.getMessage());
                    scheduleReconnect();
                    throw new CompletionException(throwable);
                });
    }

    /**
     * Schedules a reconnection attempt with exponential backoff.
     */
    public void scheduleReconnect() {
        if (activeClientId == null || isConnected()) {
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            logger.error("Maximum reconnection attempts ({}) reached for ComfyUI WebSocket. Giving up.", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        long delaySeconds = Math.min((long) Math.pow(2, attempt), 30L);
        logger.info("Scheduling WebSocket reconnect attempt {}/{} in {} seconds...", attempt, MAX_RECONNECT_ATTEMPTS, delaySeconds);

        reconnectExecutor.schedule(() -> {
            try {
                if (!isConnected() && activeClientId != null) {
                    connectAsync(activeClientId);
                }
            } catch (Exception e) {
                logger.debug("Scheduled reconnect failed: {}", e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Checks whether the current WebSocket session is active and open.
     *
     * @return true if connected and open, false otherwise
     */
    public boolean isConnected() {
        return currentSession != null && currentSession.isOpen();
    }

    /**
     * Closes the active session if open.
     */
    public synchronized void disconnect() {
        this.activeClientId = null;
        disconnectCurrentSession();
    }

    private synchronized void disconnectCurrentSession() {
        if (currentSession != null && currentSession.isOpen()) {
            try {
                logger.debug("Closing existing ComfyUI WebSocket session {}", currentSession.getId());
                currentSession.close();
            } catch (IOException e) {
                logger.debug("Error closing WebSocket session: {}", e.getMessage());
            } finally {
                currentSession = null;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        disconnect();
        reconnectExecutor.shutdownNow();
    }
}
