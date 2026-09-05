package de.tki.comfymodels.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import de.tki.comfymodels.service.impl.ProcessTracker;

public class PlatformUtils {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PlatformUtils.class);

    private static String OS_NAME = System.getProperty("os.name").toLowerCase();


    private static ProcessTracker processTracker;

    public static void setProcessTracker(ProcessTracker tracker) {
        processTracker = tracker;
    }

    public static void setOsNameForTesting(String osName) {
        OS_NAME = osName.toLowerCase();
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix");
    }

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

    public static boolean isSystemTraySupported() {
        try {
            return java.awt.SystemTray.isSupported();
        } catch (Exception e) {
            return false;
        }
    }

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
