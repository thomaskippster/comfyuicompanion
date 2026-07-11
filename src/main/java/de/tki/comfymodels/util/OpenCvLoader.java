package de.tki.comfymodels.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.metaloom.video4j.Video4j;

public class OpenCvLoader {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OpenCvLoader.class);

    private static boolean initialized = false;
    private static boolean success = false;

    /**
     * Initializes OpenCV. First, it tries loading via nu.pattern.OpenCV (highly
     * compatible across OS platforms including Windows). If that fails, it falls
     * back to Video4j.init() (typically used on Linux).
     *
     * @return true if OpenCV was successfully loaded/initialized, false otherwise.
     */
    public static synchronized boolean load() {
        if (initialized) {
            return success;
        }
        initialized = true;

        int javaVersion = getJavaMajorVersion();

        if (javaVersion < 12) {
            try {
                nu.pattern.OpenCV.loadShared();
                logger.info("✅ [OpenCvLoader] OpenCV loaded successfully via loadShared()");
                success = true;
            } catch (Throwable ignored) {}
        }

        if (!success) {
            try {
                nu.pattern.OpenCV.loadLocally();
                logger.info("✅ [OpenCvLoader] OpenCV loaded successfully via loadLocally()");
                success = true;
            } catch (Throwable t) {
                logger.error("❌ [OpenCvLoader] loadLocally() failed: " + t.getMessage());
            }
        }

        if (!success) {
            try {
                Video4j.init();
                logger.info("✅ [OpenCvLoader] OpenCV initialized successfully via Video4j.init()");
                success = true;
            } catch (Throwable t) {
                logger.error("❌ [OpenCvLoader] Video4j.init() failed: " + t.getMessage());
            }
        }

        return success;
    }

    private static int getJavaMajorVersion() {
        String version = System.getProperty("java.version");
        if (version == null) return 8;
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.split("\\.")[1]);
            } else {
                String major = version.split("\\.")[0];
                if (major.contains("-")) {
                    major = major.substring(0, major.indexOf("-"));
                }
                return Integer.parseInt(major);
            }
        } catch (Exception e) {
            return 8;
        }
    }
}
