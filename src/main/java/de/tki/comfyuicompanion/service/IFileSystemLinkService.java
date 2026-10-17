package de.tki.comfyuicompanion.service;

import java.nio.file.Path;

/**
 * Service for creating filesystem links (symbolic links, hard links, and NTFS junctions)
 * with automatic permission probing and graceful non-admin fallbacks.
 */
public interface IFileSystemLinkService {

    /**
     * Link creation result detail.
     *
     * @param success whether a link or junction was successfully established
     * @param linkType the established link type ("SYMLINK", "JUNCTION", "HARDLINK", or "NONE")
     * @param message informative status or error message
     */
    record LinkResult(boolean success, String linkType, String message) {
        public static LinkResult success(String linkType, String message) {
            return new LinkResult(true, linkType, message);
        }

        public static LinkResult failure(String message) {
            return new LinkResult(false, "NONE", message);
        }
    }

    /**
     * Checks if creating standard symbolic links is permitted in the current OS environment
     * without requiring elevated administrator privileges (e.g. Windows Developer Mode active or POSIX OS).
     *
     * @return true if symlink creation is allowed, false if restricted
     */
    boolean isSymlinkCreationAllowed();

    /**
     * Checks if the host operating system is Windows.
     *
     * @return true on Windows systems
     */
    boolean isWindows();

    /**
     * Creates a directory link. On Windows, seamlessly utilizes an NTFS Junction
     * (which does not require Administrator privileges or Developer Mode) as a fallback
     * when standard directory symlinks are restricted.
     *
     * @param linkPath   the link path to create
     * @param targetPath the existing target directory
     * @return result record describing the link established
     */
    LinkResult createDirectoryLink(Path linkPath, Path targetPath);

    /**
     * Creates a file link. Attempts a symbolic link first; if permission is denied,
     * falls back to a hard link on the same volume (which requires no admin rights).
     *
     * @param linkPath   the link path to create
     * @param targetPath the existing target file
     * @return result record describing the link established
     */
    LinkResult createFileLink(Path linkPath, Path targetPath);
}
