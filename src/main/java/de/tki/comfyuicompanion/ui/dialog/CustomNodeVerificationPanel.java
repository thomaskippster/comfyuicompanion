package de.tki.comfyuicompanion.ui.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Enterprise UI component for verifying required custom nodes against the running
 * ComfyUI environment and providing seamless 1-click installation with live console output.
 */
public class CustomNodeVerificationPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeVerificationPanel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SafePathValidator PATH_VALIDATOR = new SafePathValidator();

    private final JLabel statusLabel;
    private final JButton btnInstall;
    private final ConfigService configService;
    private final Window parentWindow;

    /**
     * Constructs a new CustomNodeVerificationPanel.
     *
     * @param parentWindow  owner window for modal installation dialog
     * @param configService application configuration service
     * @param textSecondary secondary text color for theme
     */
    public CustomNodeVerificationPanel(Window parentWindow, ConfigService configService, Color textSecondary) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        this.parentWindow = parentWindow;
        this.configService = configService;
        setOpaque(false);

        statusLabel = new JLabel("Checking custom nodes...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(textSecondary);

        btnInstall = new JButton("Install Missing Nodes", SvgIconFactory.get(AppIcon.DOWNLOAD, 12));
        btnInstall.setFont(new Font("SansSerif", Font.BOLD, 10));
        btnInstall.putClientProperty("Button.arc", 999);
        btnInstall.setBackground(new Color(33, 150, 243));
        btnInstall.setForeground(Color.WHITE);
        btnInstall.setVisible(false);

        add(statusLabel);
        add(btnInstall);
    }

    /**
     * Inspects a workflow JSON asynchronously and updates UI status and install button.
     *
     * @param workflowJsonSupplier supplier yielding the workflow JSON string (or null/blank)
     */
    public void verifyWorkflowAsync(Supplier<String> workflowJsonSupplier) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = workflowJsonSupplier != null ? workflowJsonSupplier.get() : null;
                if (json == null || json.isBlank()) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Available after workflow fetch"));
                    return;
                }

                CustomNodeResolverService resolver = new CustomNodeResolverService(
                        MAPPER,
                        WebClient.builder(),
                        configService,
                        PATH_VALIDATOR
                );

                resolver.analyzeWorkflowNodes(json).subscribe(report -> SwingUtilities.invokeLater(() -> {
                    if (report.missingNodeTypes().isEmpty()) {
                        statusLabel.setText("All required custom nodes installed ✅");
                        statusLabel.setForeground(new Color(34, 197, 130));
                        btnInstall.setVisible(false);
                    } else {
                        statusLabel.setText("⚠️ " + report.missingNodeTypes().size() + " missing custom node(s)");
                        statusLabel.setForeground(new Color(244, 67, 54));
                        btnInstall.setVisible(true);

                        // Clear previous action listeners
                        for (var al : btnInstall.getActionListeners()) {
                            btnInstall.removeActionListener(al);
                        }

                        btnInstall.addActionListener(evt -> {
                            Map<CustomNodePackage, List<String>> pkgMap = new LinkedHashMap<>();
                            Set<String> unmapped = new LinkedHashSet<>(report.missingNodeTypes());

                            for (String missingNode : report.missingNodeTypes()) {
                                resolver.resolvePackage(missingNode).ifPresent(pkg -> {
                                    pkgMap.computeIfAbsent(pkg, k -> new ArrayList<>()).add(missingNode);
                                    unmapped.remove(missingNode);
                                });
                            }
                            if (!unmapped.isEmpty() && resolver.getManagerBridgeService() != null) {
                                Map<CustomNodePackage, List<String>> bridgeMap =
                                        resolver.getManagerBridgeService().resolvePackagesForNodes(unmapped);
                                for (Map.Entry<CustomNodePackage, List<String>> entry : bridgeMap.entrySet()) {
                                    pkgMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                                    unmapped.removeAll(entry.getValue());
                                }
                            }
                            // Guarantee: Unresolved nodes are still shown so the user sees exactly what is missing
                            for (String missingNode : unmapped) {
                                CustomNodePackage placeholder = new CustomNodePackage(
                                        missingNode,
                                        "",
                                        List.of(missingNode),
                                        List.of()
                                );
                                pkgMap.put(placeholder, List.of(missingNode));
                            }
                            new MissingCustomNodesDialog(parentWindow, pkgMap, resolver.getManagerBridgeService(), () -> {
                                statusLabel.setText("Installed ✅ (Restart ComfyUI to load)");
                                statusLabel.setForeground(new Color(34, 197, 130));
                                btnInstall.setVisible(false);
                            }).setVisible(true);
                        });
                    }
                }), ex -> SwingUtilities.invokeLater(() -> {
                    logger.debug("Custom node verification error: {}", ex.getMessage());
                    statusLabel.setText("Check skipped");
                }));
            } catch (Exception ex) {
                logger.debug("Custom node analysis exception: {}", ex.getMessage());
                SwingUtilities.invokeLater(() -> statusLabel.setText("Check skipped"));
            }
        });
    }
}
