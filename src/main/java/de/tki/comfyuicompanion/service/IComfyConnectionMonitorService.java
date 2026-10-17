package de.tki.comfyuicompanion.service;

import java.util.function.Consumer;

/**
 * Service interface for continuously monitoring ComfyUI HTTP server connectivity
 * and health via background heartbeat checks.
 */
public interface IComfyConnectionMonitorService {

    /**
     * Enumeration representing the health and connection state of the ComfyUI server.
     */
    enum ConnectionState {
        /** Server is online and responding successfully to heartbeat pings. */
        CONNECTED,
        /** Connection was recently lost or is actively attempting reconnection. */
        CONNECTING,
        /** Server is unreachable or offline. */
        DISCONNECTED
    }

    /**
     * Returns the current connection state.
     *
     * @return active connection state
     */
    ConnectionState getState();

    /**
     * Returns the detected device or system summary from the last heartbeat ping.
     *
     * @return status description, or empty string if not available
     */
    String getSystemSummary();

    /**
     * Registers a listener to receive state transitions.
     *
     * @param listener consumer receiving the new connection state
     */
    void addStateListener(Consumer<ConnectionState> listener);

    /**
     * Triggers an immediate asynchronous heartbeat check.
     */
    void checkNow();

    /**
     * Starts the heartbeat monitoring background scheduler.
     */
    void start();

    /**
     * Stops the heartbeat monitoring background scheduler.
     */
    void stop();
}
