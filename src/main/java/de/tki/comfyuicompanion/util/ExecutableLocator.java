package de.tki.comfyuicompanion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * Utility responsible for locating platform-specific executable binaries on the filesystem.
 * Searches application-local directories, PATH environment variables, Conda environments,
 * and portable runtimes like Pinokio.
 */
public final class ExecutableLocator {
    private static final Logger logger = LoggerFactory.getLogger(ExecutableLocator.class);

    private ExecutableLocator() {
        // Utility class
    }

    /**
     * Resolves the absolute path of an executable binary by name.
     *
     * @param name           the base name of the executable (e.g. "ffmpeg", "piper")
     * @param configuredPath an optional explicitly configured path or empty string
     * @param pythonPath     an optional path to the active Python executable for Conda/venv searching
     * @return the absolute path of the executable if found, or {@code null} if not found
     */
    public static String findExecutablePath(String name, String configuredPath, String pythonPath) {
        // 1. Explicitly configured path in application settings
        if (configuredPath != null && !configuredPath.isEmpty()) {
            File f = new File(configuredPath);
            if (f.exists() && f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        // 2. Relative path to execution directory of the JAR or classes
        File baseDir = resolveBaseDirectory();
        String baseDirPath = baseDir.getAbsolutePath();
        String[] localDirs = {
            baseDirPath + "/tools/bin",
            baseDirPath + "/tools/ffmpeg",
            baseDirPath + "/tools/tts",
            baseDirPath + "/tools"
        };
        for (String dir : localDirs) {
            File dirFile = new File(dir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                File f = new File(dirFile, name);
                if (f.exists() && f.isFile() && f.canExecute()) return f.getAbsolutePath();
                File fExe = new File(dirFile, name + ".exe");
                if (fExe.exists() && fExe.isFile() && fExe.canExecute()) return fExe.getAbsolutePath();
            }
        }

        // 3. Fallback to system PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                File f = new File(dir, name);
                if (f.exists() && f.isFile() && f.canExecute()) return f.getAbsolutePath();
                File fExe = new File(dir, name + ".exe");
                if (fExe.exists() && fExe.isFile() && fExe.canExecute()) return fExe.getAbsolutePath();
            }
        }

        // 4. Fallback to Conda / Python environment directories
        if (pythonPath != null && !pythonPath.isEmpty()) {
            File pythonFile = new File(pythonPath);
            if (pythonFile.exists()) {
                File envDir = pythonFile.getParentFile();
                if (envDir != null) {
                    File libBin = new File(envDir, "Library/bin");
                    File fLib = new File(libBin, name + ".exe");
                    if (fLib.exists() && fLib.canExecute()) return fLib.getAbsolutePath();
                    File fLibNo = new File(libBin, name);
                    if (fLibNo.exists() && fLibNo.canExecute()) return fLibNo.getAbsolutePath();

                    File scripts = new File(envDir, "Scripts");
                    File fScripts = new File(scripts, name + ".exe");
                    if (fScripts.exists() && fScripts.canExecute()) return fScripts.getAbsolutePath();
                    File fScriptsNoExe = new File(scripts, name);
                    if (fScriptsNoExe.exists() && fScriptsNoExe.canExecute()) return fScriptsNoExe.getAbsolutePath();

                    File fSame = new File(envDir, name + ".exe");
                    if (fSame.exists() && fSame.canExecute()) return fSame.getAbsolutePath();
                    File fSameNoExe = new File(envDir, name);
                    if (fSameNoExe.exists() && fSameNoExe.canExecute()) return fSameNoExe.getAbsolutePath();
                }
            }
        }

        // 5. Pinokio binary locations
        String userHome = System.getProperty("user.home");
        String[] pinokioBins = {
            userHome + "/AppData/Roaming/pinokio/bin",
            userHome + "/.local/share/pinokio/bin",
            userHome + "/pinokio/bin"
        };
        for (String pBin : pinokioBins) {
            File pBinDir = new File(pBin);
            if (pBinDir.exists() && pBinDir.isDirectory()) {
                File f = new File(pBinDir, name);
                if (f.exists() && f.canExecute()) return f.getAbsolutePath();
                File fExe = new File(pBinDir, name + ".exe");
                if (fExe.exists() && fExe.canExecute()) return fExe.getAbsolutePath();
            }
        }

        return null;
    }

    private static File resolveBaseDirectory() {
        File baseDir = null;
        try {
            java.net.URL location = ExecutableLocator.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File codeSourceFile = new File(location.toURI());
                File parent = codeSourceFile.getParentFile();
                while (parent != null) {
                    if (new File(parent, "tools").exists() || new File(parent, "pom.xml").exists()) {
                        baseDir = parent;
                        break;
                    }
                    parent = parent.getParentFile();
                }
                if (baseDir == null) {
                    baseDir = codeSourceFile.getParentFile();
                }
            }
        } catch (Exception ignored) {}

        if (baseDir == null) {
            try {
                baseDir = Paths.get("").toAbsolutePath().toFile();
            } catch (Exception ignored) {}
        }
        if (baseDir == null) {
            baseDir = new File(System.getProperty("user.dir"));
        }
        return baseDir;
    }
}
