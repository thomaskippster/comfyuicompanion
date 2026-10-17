package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IComfyConnectionMonitorService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Monitors the live connection health and status of the ComfyUI server by executing
 * periodic heartbeat requests against the /system_stats endpoint using Virtual Threads.
 */
public class ComfyConnectionMonitorService implements IComfyConnectionMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(ComfyConnectionMonitorService.class);
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);
    private static final long CHECK_INTERVAL_MS = 3000;

    private final ConfigService configService;
    private final HttpClient httpClient;
    private final List<Consumer<ConnectionState>> listeners = new CopyOnWriteArrayList<>();

    private final AtomicReference<ConnectionState> currentState = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<String> systemSummary = new AtomicReference<>("");
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread monitorThread;

    /**
     * Constructs the monitor service with the given configuration provider.
     *
     * @param configService configuration service for retrieving the ComfyUI URL
     */
    public ComfyConnectionMonitorService(ConfigService configService) {
        this(configService, HttpClient.newBuilder()
                .connectTimeout(PING_TIMEOUT)
                .build());
    }

    /**
     * Constructs the monitor service with an explicit HTTP client (for unit testing).
     *
     * @param configService configuration service
     * @param httpClient custom HTTP client
     */
    public ComfyConnectionMonitorService(ConfigService configService, HttpClient httpClient) {
        this.configService = configService;
        this.httpClient = httpClient;
    }

    @Override
    public ConnectionState getState() {
        return currentState.get();
    }

    @Override
    public String getSystemSummary() {
        return systemSummary.get();
    }

    @Override
    public void addStateListener(Consumer<ConnectionState> listener) {
        if (listener != null) {
            listeners.add(listener);
            listener.accept(currentState.get());
        }
    }

    @Override
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            monitorThread = Thread.ofVirtual().name("comfy-heartbeat-monitor").start(this::runHeartbeatLoop);
            logger.info("🟢 [ComfyConnectionMonitor] Background heartbeat monitor started.");
        }
    }

    @Override
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (monitorThread != null) {
                monitorThread.interrupt();
            }
            logger.info("🔴 [ComfyConnectionMonitor] Background heartbeat monitor stopped.");
        }
    }

    @Override
    public void checkNow() {
        Thread.ofVirtual().name("comfy-heartbeat-oneshot").start(this::performCheck);
    }

    private void runHeartbeatLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            performCheck();
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Executes a single heartbeat request against the ComfyUI server.
     */
    public void performCheck() {
        String baseUrl = configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188";
        if (baseUrl == null || baseUrl.isBlank()) {
            updateState(ConnectionState.DISCONNECTED, "");
            return;
        }

        try {
            String cleanUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cleanUrl + "/system_stats"))
                    .timeout(PING_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                consecutiveFailures.set(0);
                String summary = parseSystemSummary(response.body());
                updateState(ConnectionState.CONNECTED, summary);
            } else {
                handleFailure();
            }
        } catch (Exception e) {
            handleFailure();
        }
    }

    private void handleFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        ConnectionState current = currentState.get();
        if (failures <= 2 && (current == ConnectionState.CONNECTED || current == ConnectionState.CONNECTING)) {
            updateState(ConnectionState.CONNECTING, "Reconnecting...");
        } else {
            updateState(ConnectionState.DISCONNECTED, "");
        }
    }

    private String parseSystemSummary(String jsonResponse) {
        try {
            JSONObject obj = new JSONObject(jsonResponse);
            if (obj.has("devices")) {
                JSONArray devices = obj.getJSONArray("devices");
                if (devices.length() > 0) {
                    JSONObject dev = devices.getJSONObject(0);
                    return dev.optString("name", "GPU/CPU");
                }
            }
            if (obj.has("system")) {
                JSONObject sys = obj.getJSONObject("system");
                return sys.optString("os", "Online");
            }
        } catch (Exception ignored) {}
        return "Connected";
    }

    private void updateState(ConnectionState newState, String summary) {
        this.systemSummary.set(summary);
        ConnectionState oldState = currentState.getAndSet(newState);
        if (oldState != newState) {
            logger.debug("ComfyUI connection state changed: {} -> {}", oldState, newState);
            for (Consumer<ConnectionState> listener : listeners) {
                try {
                    if (SwingUtilities.isEventDispatchThread()) {
                        listener.accept(newState);
                    } else {
                        SwingUtilities.invokeLater(() -> listener.accept(newState));
                    }
                } catch (Exception e) {
                    logger.error("Error notifying connection state listener: {}", e.getMessage());
                }
            }
        }
    }
}
