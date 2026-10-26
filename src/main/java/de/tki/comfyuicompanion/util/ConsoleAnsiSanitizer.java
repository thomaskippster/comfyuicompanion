package de.tki.comfyuicompanion.util;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing raw terminal output streams by stripping ANSI escape codes
 * and normalizing carriage-return progress overwrites for clean display in Swing text components.
 */
public final class ConsoleAnsiSanitizer {

    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");

    private ConsoleAnsiSanitizer() {}

    /**
     * Removes ANSI color and cursor control escape sequences from the provided log string.
     *
     * @param rawLog raw terminal output string
     * @return sanitized plain text string
     */
    public static String stripAnsiCodes(String rawLog) {
        if (rawLog == null || rawLog.isEmpty()) {
            return "";
        }
        return ANSI_ESCAPE_PATTERN.matcher(rawLog).replaceAll("");
    }
}
