package de.tki.comfymodels.service;

public interface IComfyLifecycleService {
    /**
     * Starts the ComfyUI process using the configured command and working directory.
     */
    void start();

    /**
     * Stops the ComfyUI process forcefully.
     */
    void stop();

    /**
     * Restarts the ComfyUI process (Stop -> Start).
     */
    void restart();

    /**
     * Returns the current status of the ComfyUI process.
     */
    String getStatus();

    /**
     * Returns true if the ComfyUI process is currently running.
     */
    boolean isRunning();
    
    /**
     * Returns true if the managed process is physically alive.
     */
    boolean isProcessAlive();
    
    /**
     * Performs a health check by pinging the ComfyUI API.
     */
    boolean isHealthy();

    /**
     * Resets the ComfyUI installation by cleaning up the existing directory and re-running the bootstrap setup.
     */
    void fixSetup();
}
