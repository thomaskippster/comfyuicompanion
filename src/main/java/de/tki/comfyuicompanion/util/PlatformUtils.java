package de.tki.comfyuicompanion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;

/**
 * Platform and operating system utility class providing OS detection,
 * system shutdown helpers, process termination, and command line parsing.
 */
public class PlatformUtils {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PlatformUtils.class);

    private static String OS_NAME = System.getProperty("os.name").toLowerCase();

    private static ProcessTracker processTracker;

    /**
     * Sets the process tracker instance used for monitored process execution.
     *
     * @param tracker the process tracker to monitor subprocesses
     */
    public static void setProcessTracker(ProcessTracker tracker) {
        processTracker = tracker;
    }

    /**
     * Overrides the detected OS name for automated unit testing.
     *
     * @param osName the OS name string to simulate
     */
    public static void setOsNameForTesting(String osName) {
        OS_NAME = osName.toLowerCase();
    }

    /**
     * Checks if the host operating system is Windows.
     *
     * @return true if running on Microsoft Windows
     */
    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    /**
     * Checks if the host operating system is macOS.
     *
     * @return true if running on Apple macOS
     */
    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    /**
     * Checks if the host operating system is Linux or Unix-like.
     *
     * @return true if running on Linux, Unix, or AIX
     */
    public static boolean isLinux() {
        return OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix");
    }

    /**
     * Initiates a timed system shutdown using platform-specific commands.
     */
    public static void shutdownSystem() {
        String[] command = getShutdownCommand();
        if (command == null) {
            logger.error("Shutdown not supported on this OS: " + OS_NAME);
            return;
        }

        try {
            if (processTracker != null) {
                processTracker.start(new ProcessBuilder(command));
            } else {
                new ProcessBuilder(command).start();
            }
        } catch (IOException e) {
            logger.error("Error executing shutdown command: " + e.getMessage());
        }
    }

    /**
     * Returns the appropriate system shutdown command array for the current OS.
     *
     * @return array of command arguments, or null if unsupported
     */
    public static String[] getShutdownCommand() {
        if (isWindows()) {
            return new String[]{"shutdown", "/s", "/t", "60"};
        } else if (isMac()) {
            return new String[]{"osascript", "-e", "tell app \"System Events\" to shut down"};
        } else if (isLinux()) {
            return new String[]{"shutdown", "-h", "+1"};
        }
        return null;
    }

    /**
     * Terminates running processes matching the specified executable name.
     *
     * @param name process or executable name to kill
     */
    public static void killProcessByName(String name) {
        try {
            if (isWindows()) {
                if (!name.endsWith(".exe")) name += ".exe";
                Runtime.getRuntime().exec("taskkill /F /IM " + name + " /T");
            } else {
                // Linux/Mac
                Runtime.getRuntime().exec("pkill -f " + name);
            }
        } catch (IOException e) {
            logger.error("Failed to kill process " + name + ": " + e.getMessage());
        }
    }

    /**
     * Verifies if the system tray is supported on the current desktop environment.
     *
     * @return true if system tray is fully supported
     */
    public static boolean isSystemTraySupported() {
        try {
            return java.awt.SystemTray.isSupported();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses a command line string into a list of arguments, handling quotation marks.
     *
     * @param commandLine the raw command line string
     * @return list of parsed arguments
     */
    public static java.util.List<String> parseCommandLine(String commandLine) {
        java.util.List<String> args = new java.util.ArrayList<>();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return args;
        }
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        int len = commandLine.length();
        for (int i = 0; i < len; i++) {
            char c = commandLine.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }
}
