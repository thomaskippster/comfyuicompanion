package de.tki.comfyuicompanion.service;

/**
 * Service interface for managing the lifecycle of the ComfyUI background process.
 * Provides capabilities to start, stop, restart, and monitor the health of the 
 * ComfyUI engine.
 */
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
     * 
     * @return The current status as a string.
     */
    String getStatus();

    /**
     * Returns true if the ComfyUI process is currently running.
     * 
     * @return true if running, false otherwise.
     */
    boolean isRunning();
    
    /**
     * Returns true if the managed process is physically alive.
     * 
     * @return true if the process is alive, false otherwise.
     */
    boolean isProcessAlive();
    
    /**
     * Performs a health check by pinging the ComfyUI API.
     * 
     * @return true if the API is responsive, false otherwise.
     */
    boolean isHealthy();

    /**
     * Resets the ComfyUI installation by cleaning up the existing directory and re-running the bootstrap setup.
     */
    void fixSetup();

    /**
     * Registers a callback to be run when the browser is automatically launched by the service.
     * 
     * @param callback The callback to execute when the browser launches.
     */
    void setOnBrowserLaunched(Runnable callback);

    /**
     * Returns true if the "To see the GUI go to:" line has been detected in the logs.
     * 
     * @return true if the GUI URL has been logged, false otherwise.
     */
    boolean isGuiLineShown();
}
