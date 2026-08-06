package de.tki.comfymodels.ui;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.EnvironmentBootstrapperImpl;
import de.tki.comfymodels.service.impl.ProfileManager;
import de.tki.comfymodels.domain.LaunchProfile;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EnvironmentInstallerDialog extends StandardDialog {
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final ConfigService configService;
    private final ProfileManager profileManager;
    private final Runnable onSuccess;

    private JProgressBar progressBar;
    private JTextArea logArea;
    private JButton startBtn;
    private JButton closeBtn;

    public EnvironmentInstallerDialog(JFrame parent, EnvironmentBootstrapperImpl bootstrapper, ConfigService configService, ProfileManager profileManager, Runnable onSuccess) {
        super(parent, "1-Click Environment Setup");
        this.setModal(true);
        this.bootstrapper = bootstrapper;
        this.configService = configService;
        this.profileManager = profileManager;
        this.onSuccess = onSuccess;

        setSize(650, 450);
        setLocationRelativeTo(parent);
        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Top info
        JPanel infoPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Standalone ComfyUI Installation");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        JLabel descLabel = new JLabel("<html>This will download a fresh, isolated copy of ComfyUI along with a dedicated Portable Python environment.<br>This ensures no conflicts with system Python installations.</html>");
        descLabel.setForeground(Color.GRAY);
        infoPanel.add(titleLabel, BorderLayout.NORTH);
        infoPanel.add(descLabel, BorderLayout.CENTER);

        // Center logs and progress
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);

        centerPanel.add(progressBar, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startBtn = new JButton("Start Installation");
        startBtn.putClientProperty("JButton.buttonType", "roundRect");
        startBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        startBtn.setBackground(new Color(40, 120, 40));
        startBtn.setForeground(Color.WHITE);

        closeBtn = new JButton("Cancel");
        closeBtn.putClientProperty("JButton.buttonType", "roundRect");

        startBtn.addActionListener(e -> startInstallation());
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(closeBtn);
        buttonPanel.add(startBtn);

        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startInstallation() {
        startBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Installing...");
        logArea.setText("⚙️ Starting Standalone Environment Setup...\n");

        Path appRoot = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
        Path comfyTarget = appRoot.resolve("ComfyUI");

        CompletableFuture.supplyAsync(() -> {
            try {
                java.nio.file.Files.createDirectories(appRoot);
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create directories: " + e.getMessage());
            }
        }).thenCompose(v -> {
            progressBar.setString("Step 1/4: Portable Python");
            return bootstrapper.downloadAndExtractPortablePython(appRoot, this::log);
        }).thenCompose(pythonPath -> {
            progressBar.setString("Step 2/4: PIP & Dependencies");
            return bootstrapper.installPip(pythonPath, this::log)
                .thenApply(v -> pythonPath);
        }).thenCompose(pythonPath -> {
            progressBar.setString("Step 3/4: Clone ComfyUI");
            return bootstrapper.cloneComfyUI(comfyTarget, this::log)
                .thenApply(v -> pythonPath);
        }).thenCompose(pythonPath -> {
            progressBar.setString("Step 4/4: PyTorch & Requirements");
            return bootstrapper.installRequirements(pythonPath, comfyTarget, this::log)
                .thenApply(v -> pythonPath);
        }).thenAccept(pythonPath -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    String newProfileId = UUID.randomUUID().toString();
                    LaunchProfile standaloneProfile = new LaunchProfile(
                        newProfileId,
                        "Standalone Environment",
                        "Isolated ComfyUI installation with Portable Python",
                        false,
                        pythonPath.toString(),
                        java.util.List.of("--listen", "127.0.0.1", "--port", "8188"),
                        new java.util.HashMap<>()
                    );

                    java.util.List<LaunchProfile> profiles = new java.util.ArrayList<>(profileManager.loadProfiles());
                    profiles.removeIf(p -> p.name().equals("Standalone Environment"));
                    profiles.add(standaloneProfile);
                    profileManager.saveProfiles(profiles);

                    configService.setComfyUIPath(comfyTarget.toString());
                    configService.setPythonPath(pythonPath.toString());
                    configService.setActiveProfile(newProfileId);
                    configService.autoDiscoverPaths();

                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Done");
                    log("✅ Standalone setup complete! Profile registered and set as active.");
                    
                    JOptionPane.showMessageDialog(this, "Installation completed successfully!\nClick Launch to start ComfyUI.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                    dispose();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception ex) {
                    log("❌ Error finalizing profile: " + ex.getMessage());
                    enableCloseBtn();
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setString("Failed");
                log("❌ Setup failed: " + ex.getMessage());
                enableCloseBtn();
            });
            return null;
        });
    }

    private void enableCloseBtn() {
        closeBtn.setEnabled(true);
        closeBtn.setText("Close");
    }
}
