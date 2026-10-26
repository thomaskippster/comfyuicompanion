package de.tki.comfyuicompanion.service;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Enterprise service interface responsible for inspecting downloaded assets,
 * detecting bundled ComfyUI custom nodes within archives (.zip), and automatically
 * extracting, installing, and configuring them in ComfyUI's custom_nodes environment.
 */
public interface ICustomNodePostDownloadHandler {

    /**
     * Determines whether the given file is an archive (.zip) containing ComfyUI custom node definitions.
     *
     * @param archivePath path to the archive file on disk
     * @return {@code true} if the archive contains custom node Python modules, {@code false} otherwise
     */
    boolean isCustomNodeArchive(Path archivePath);

    /**
     * Unpacks and installs a custom node package from the downloaded archive into ComfyUI's
     * custom_nodes directory, installs Python requirements if present, and extracts any included workflows.
     *
     * @param targetFile     path to the downloaded archive
     * @param statusConsumer progress/status update sink
     * @return {@code true} if a custom node was detected and successfully deployed, {@code false} otherwise
     */
    boolean processDownloadedArchive(Path targetFile, Consumer<String> statusConsumer);
}
