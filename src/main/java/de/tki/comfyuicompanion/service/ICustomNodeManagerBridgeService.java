package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Enterprise Service interface for interacting with ComfyUI-Manager's node database,
 * resolving missing workflow custom node types to remote Git repositories, and executing
 * automated installation including Python pip dependencies.
 */
public interface ICustomNodeManagerBridgeService {

    /**
     * Ensures the extension-node-map and custom-node-list databases are loaded and indexed.
     *
     * @return future completed when index is ready
     */
    CompletableFuture<Void> ensureDatabaseLoadedAsync();

    /**
     * Checks if the manager database is loaded into memory.
     *
     * @return true if database index is available
     */
    boolean isDatabaseLoaded();

    /**
     * Resolves a custom node class name (e.g. "FaceDetailer") to its containing package.
     *
     * @param nodeClassName the Python class name of the node
     * @return optional containing the package details if known
     */
    Optional<CustomNodePackage> resolveNodePackage(String nodeClassName);

    /**
     * Resolves multiple custom node class names to their containing packages, grouped by package.
     *
     * @param nodeClassNames collection of node class names
     * @return map of package to list of matching node class names
     */
    Map<CustomNodePackage, List<String>> resolvePackagesForNodes(Collection<String> nodeClassNames);

    /**
     * Installs a custom node package into ComfyUI's custom_nodes folder and runs pip install for requirements.
     *
     * @param pkg custom node package to install
     * @param progress callback for live console and status updates
     * @return future completing with true if installed successfully
     */
    CompletableFuture<Boolean> installPackageAsync(CustomNodePackage pkg, Consumer<String> progress);

    /**
     * Installs multiple custom node packages sequentially.
     *
     * @param packages list of packages to install
     * @param progress callback for live console and status updates
     * @return future completing with true if all installed successfully
     */
    CompletableFuture<Boolean> installPackagesAsync(List<CustomNodePackage> packages, Consumer<String> progress);
}
