package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IFileSystemLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enterprise implementation of {@link IFileSystemLinkService} that creates symbolic links,
 * NTFS junctions, and hard links while dynamically detecting OS privileges.
 */
@Service
public class FileSystemLinkService implements IFileSystemLinkService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemLinkService.class);

    private final boolean isWindows;
    private final AtomicBoolean probedSymlinkAllowed = new AtomicBoolean(false);
    private final AtomicBoolean hasProbed = new AtomicBoolean(false);

    /**
     * Default constructor detecting the operating system.
     */
    public FileSystemLinkService() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        this.isWindows = os.contains("win");
    }

    @Override
    public boolean isWindows() {
        return isWindows;
    }

    @Override
    public boolean isSymlinkCreationAllowed() {
        if (hasProbed.get()) {
            return probedSymlinkAllowed.get();
        }
        boolean allowed = probeSymlinkCapability();
        probedSymlinkAllowed.set(allowed);
        hasProbed.set(true);
        return allowed;
    }

    private boolean probeSymlinkCapability() {
        if (!isWindows) {
            return true; // POSIX systems permit symlinks for standard users
        }
        try {
            Path tempDir = Files.createTempDirectory("companion_symlink_probe");
            Path targetFile = Files.createTempFile(tempDir, "target", ".tmp");
            Path linkFile = tempDir.resolve("link.tmp");
            try {
                Files.createSymbolicLink(linkFile, targetFile);
                return true;
            } finally {
                Files.deleteIfExists(linkFile);
                Files.deleteIfExists(targetFile);
                Files.deleteIfExists(tempDir);
            }
        } catch (Exception e) {
            logger.debug("Symlink capability probe denied (Developer Mode / Admin required): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public LinkResult createDirectoryLink(Path linkPath, Path targetPath) {
        if (linkPath == null || targetPath == null) {
            return LinkResult.failure("Link or target path cannot be null.");
        }
        if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
            return LinkResult.failure("Target directory does not exist: " + targetPath);
        }

        try {
            Files.deleteIfExists(linkPath);
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

        if (isWindows) {
            // Windows: Prioritize NTFS Junctions as they do NOT require Administrator rights or Developer Mode
            LinkResult junctionResult = createNtfsJunction(linkPath, targetPath);
            if (junctionResult.success()) {
                return junctionResult;
            }
            logger.debug("NTFS Junction creation failed ({}), attempting standard directory symlink...", junctionResult.message());
        }

        try {
            Files.createSymbolicLink(linkPath, targetPath);
            return LinkResult.success("SYMLINK", "Successfully created directory symbolic link.");
        } catch (Exception ex) {
            logger.warn("Failed to create directory symbolic link from '{}' to '{}': {}", linkPath, targetPath, ex.getMessage());
            return LinkResult.failure("Directory link creation failed: " + ex.getMessage());
        }
    }

    @Override
    public LinkResult createFileLink(Path linkPath, Path targetPath) {
        if (linkPath == null || targetPath == null) {
            return LinkResult.failure("Link or target path cannot be null.");
        }
        if (!Files.exists(targetPath) || Files.isDirectory(targetPath)) {
            return LinkResult.failure("Target file does not exist or is a directory: " + targetPath);
        }

        try {
            Files.deleteIfExists(linkPath);
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

        // 1. Try standard symbolic link first
        try {
            Files.createSymbolicLink(linkPath, targetPath);
            return LinkResult.success("SYMLINK", "Successfully created file symbolic link.");
        } catch (FileSystemException | SecurityException symlinkEx) {
            logger.debug("Symbolic link creation failed ({}), attempting NTFS hard link fallback...", symlinkEx.getMessage());
        } catch (Exception otherEx) {
            logger.debug("Unexpected error creating symlink: {}", otherEx.getMessage());
        }

        // 2. Fallback: Hard link (works on Windows & Linux without admin privileges if on same volume)
        try {
            if (Files.getFileStore(linkPath.getParent()).equals(Files.getFileStore(targetPath))) {
                Files.createLink(linkPath, targetPath);
                return LinkResult.success("HARDLINK", "Successfully created NTFS/POSIX hard link (no admin rights required).");
            } else {
                return LinkResult.failure("Cannot create hard link across different disk drives or volumes.");
            }
        } catch (Exception hardlinkEx) {
            logger.warn("Failed to create hard link between '{}' and '{}': {}", linkPath, targetPath, hardlinkEx.getMessage());
            return LinkResult.failure("Failed to create file link: " + hardlinkEx.getMessage());
        }
    }

    private LinkResult createNtfsJunction(Path linkPath, Path targetPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", "mklink", "/J",
                    linkPath.toAbsolutePath().toString(),
                    targetPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(" ");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0 && Files.exists(linkPath)) {
                logger.info("Created NTFS Junction: {} -> {}", linkPath, targetPath);
                return LinkResult.success("JUNCTION", "Successfully created NTFS Junction without administrator rights.");
            } else {
                return LinkResult.failure("mklink /J exited with code " + exitCode + ": " + output.toString().trim());
            }
        } catch (Exception e) {
            return LinkResult.failure("Failed to invoke mklink: " + e.getMessage());
        }
    }
}
