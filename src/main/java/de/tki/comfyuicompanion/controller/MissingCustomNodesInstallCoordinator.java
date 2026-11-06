package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.ICustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import de.tki.comfyuicompanion.ui.dialog.MissingCustomNodesDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinator offering a 1-click installation of custom node packages for node class types that could
 * not be replaced by ComfyUI core constructs. Packages are resolved through the ComfyUI-Manager node
 * database; if no package can be resolved, a plain warning listing the class types is shown instead.
 */
@Component
public class MissingCustomNodesInstallCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MissingCustomNodesInstallCoordinator.class);

    private final ICustomNodeManagerBridgeService bridgeService;

    /**
     * Creates the coordinator.
     *
     * @param bridgeService ComfyUI-Manager bridge; may be {@code null} when unavailable
     */
    @Autowired
    public MissingCustomNodesInstallCoordinator(@Autowired(required = false) ICustomNodeManagerBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    /**
     * Resolves the packages providing the given node class types asynchronously and presents the
     * installation dialog on the Event Dispatch Thread.
     *
     * @param parent           component used to locate the owner window
     * @param missingNodeTypes unresolved node class types
     */
    public void offerInstallation(java.awt.Component parent, Collection<String> missingNodeTypes) {
        if (missingNodeTypes == null || missingNodeTypes.isEmpty()) return;
        List<String> nodeTypes = List.copyOf(missingNodeTypes);
        if (bridgeService == null) {
            if (de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                log.warn("Unattended mode: missing custom nodes {} and no bridge service available.", nodeTypes);
                return;
            }
            SwingUtilities.invokeLater(() -> showFallbackWarning(parent, nodeTypes));
            return;
        }

        CompletableFuture<Void> dbReady = bridgeService.ensureDatabaseLoadedAsync();
        (dbReady != null ? dbReady : CompletableFuture.<Void>completedFuture(null))
                .thenApply(ignored -> bridgeService.resolvePackagesForNodes(nodeTypes))
                .whenComplete((packages, error) -> {
                    if (error != null) {
                        log.warn("Could not resolve custom node packages for {}: {}", nodeTypes, error.getMessage());
                    }
                    if (de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                        log.warn("Unattended mode: skipping modal dialog for missing nodes: {}. Packages resolved: {}",
                                nodeTypes, packages != null ? packages.keySet() : 0);
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (error != null || packages == null || packages.isEmpty()) {
                            showFallbackWarning(parent, nodeTypes);
                        } else {
                            showInstallDialog(parent, packages);
                        }
                    });
                });
    }

    /**
     * Programmatically installs packages for the given node class types without displaying GUI dialogs.
     *
     * @param missingNodeTypes collection of missing node class types
     * @param progressCallback consumer receiving progress notifications
     * @return future completing with true if all installations succeeded
     */
    public CompletableFuture<Boolean> installMissingPackagesAsync(Collection<String> missingNodeTypes, java.util.function.Consumer<String> progressCallback) {
        if (missingNodeTypes == null || missingNodeTypes.isEmpty() || bridgeService == null) {
            return CompletableFuture.completedFuture(false);
        }
        List<String> nodeTypes = List.copyOf(missingNodeTypes);
        CompletableFuture<Void> dbReady = bridgeService.ensureDatabaseLoadedAsync();
        return (dbReady != null ? dbReady : CompletableFuture.<Void>completedFuture(null))
                .thenCompose(ignored -> {
                    Map<CustomNodePackage, List<String>> packages = bridgeService.resolvePackagesForNodes(nodeTypes);
                    if (packages == null || packages.isEmpty()) {
                        log.warn("No custom node packages found in manager database for: {}", nodeTypes);
                        return CompletableFuture.completedFuture(false);
                    }
                    java.util.List<CompletableFuture<Boolean>> futures = new java.util.ArrayList<>();
                    for (CustomNodePackage pkg : packages.keySet()) {
                        futures.add(bridgeService.installPackageAsync(pkg, progressCallback));
                    }
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> futures.stream().allMatch(f -> Boolean.TRUE.equals(f.join())));
                });
    }

    private void showInstallDialog(java.awt.Component parent, Map<CustomNodePackage, List<String>> packages) {
        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        new MissingCustomNodesDialog(owner, packages, bridgeService, () ->
                JOptionPane.showMessageDialog(owner,
                        "Custom node installation complete. Please restart ComfyUI to load the new nodes.",
                        "Installation Complete", JOptionPane.INFORMATION_MESSAGE)
        ).setVisible(true);
    }

    private void showFallbackWarning(java.awt.Component parent, List<String> nodeTypes) {
        StringBuilder msg = new StringBuilder("The selected workflow requires custom nodes that are not installed in ComfyUI\n")
                .append("and could not be replaced automatically:\n\n");
        nodeTypes.forEach(n -> msg.append("  - ").append(n).append('\n'));
        msg.append("\nPlease install the missing custom node extensions in ComfyUI and restart the server.");
        JOptionPane.showMessageDialog(parent, msg.toString(), "Missing Custom Nodes", JOptionPane.WARNING_MESSAGE);
    }
}
