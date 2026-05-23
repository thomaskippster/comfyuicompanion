package de.tki.comfymodels.ui;

import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class DeduplicationDialog extends JDialog {
    private final ConfigService configService;
    private final IModelValidator modelValidator;
    private final ModelHashRegistry hashRegistry;
    
    private final JTextArea logArea;
    private final JButton scanBtn;
    private final JButton deduplicateBtn;
    private final Map<String, List<Path>> duplicateGroups = new HashMap<>();
    
    public DeduplicationDialog(Frame owner, ConfigService configService, IModelValidator modelValidator, ModelHashRegistry hashRegistry) {
        super(owner, "SHA-256 Deduplication Finder & Storage Optimizer", true);
        this.configService = configService;
        this.modelValidator = modelValidator;
        this.hashRegistry = hashRegistry;

        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        scanBtn = new JButton("🔍 Scan for Duplicates");
        deduplicateBtn = new JButton("🔗 Safe Delete & Hardlink");
        deduplicateBtn.setEnabled(false);
        topPanel.add(scanBtn);
        topPanel.add(deduplicateBtn);
        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        logArea.setText("Press 'Scan for Duplicates' to scan the models directory.\n" +
                "This will:\n" +
                " 1. Group files by exact byte size.\n" +
                " 2. Calculate fast hashes for matching sizes.\n" +
                " 3. Compute full SHA-256 for identical fast hashes to ensure absolute safety.\n" +
                " 4. Replace duplicates with NTFS hardlinks to reclaim SSD space.");

        scanBtn.addActionListener(e -> startScan());
        deduplicateBtn.addActionListener(e -> runDeduplication());
    }

    private void startScan() {
        scanBtn.setEnabled(false);
        deduplicateBtn.setEnabled(false);
        duplicateGroups.clear();
        logArea.setText("🔍 Scanning models directory...\n");

        String baseDir = configService.getModelsPath();
        if (baseDir == null || baseDir.isEmpty()) {
            logArea.append("❌ Models directory is not configured in Settings.\n");
            scanBtn.setEnabled(true);
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            logArea.append("❌ Models directory does not exist: " + baseDir + "\n");
            scanBtn.setEnabled(true);
            return;
        }

        new Thread(() -> {
            try {
                // 1. Walk files
                List<Path> files = Files.walk(root.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".sft") || name.endsWith(".pth") || name.endsWith(".pt") || name.endsWith(".bin");
                        })
                        .collect(Collectors.toList());

                updateLog("Found " + files.size() + " model files. Grouping by size...");

                // 2. Group by size
                Map<Long, List<Path>> sizeGroups = new HashMap<>();
                for (Path p : files) {
                    long size = Files.size(p);
                    sizeGroups.computeIfAbsent(size, k -> new ArrayList<>()).add(p);
                }

                // Filter sizes with > 1 files
                List<List<Path>> candidates = sizeGroups.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList());

                if (candidates.isEmpty()) {
                    updateLog("✅ No duplicate sizes found. Library is fully optimized!");
                    SwingUtilities.invokeLater(() -> scanBtn.setEnabled(true));
                    return;
                }

                updateLog("Found " + candidates.size() + " size-matching groups. Computing hashes...");

                // Enable fast hash temporarily for scanning if not already enabled
                boolean wasFastHashEnabled = configService.isFastHashEnabled();
                
                int checked = 0;
                long totalCandidates = candidates.stream().mapToInt(List::size).sum();

                // Group by hash
                Map<String, List<Path>> tempHashGroups = new HashMap<>();

                for (List<Path> pathList : candidates) {
                    for (Path p : pathList) {
                        checked++;
                        final int cur = checked;
                        SwingUtilities.invokeLater(() -> logArea.append("Hashing (" + cur + "/" + totalCandidates + "): " + p.getFileName() + "\n"));

                        String hash = modelValidator.calculateHash(p.toFile());
                        if (hash != null) {
                            tempHashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        }
                    }
                }

                // Filter exact duplicates by verifying with full SHA-256
                Map<String, List<Path>> exactDuplicates = new HashMap<>();
                for (Map.Entry<String, List<Path>> entry : tempHashGroups.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        Map<String, List<Path>> fullHashGroups = new HashMap<>();
                        for (Path p : entry.getValue()) {
                            SwingUtilities.invokeLater(() -> logArea.append("Verifying full SHA-256: " + p.getFileName() + "\n"));
                            String fullHash = modelValidator.calculateFullSha256(p.toFile());
                            if (fullHash != null) {
                                fullHashGroups.computeIfAbsent(fullHash, k -> new ArrayList<>()).add(p);
                            }
                        }
                        for (Map.Entry<String, List<Path>> fullEntry : fullHashGroups.entrySet()) {
                            if (fullEntry.getValue().size() > 1) {
                                exactDuplicates.put(fullEntry.getKey(), fullEntry.getValue());
                            }
                        }
                    }
                }

                if (exactDuplicates.isEmpty()) {
                    updateLog("✅ No exact duplicates found after hash checks.");
                } else {
                    duplicateGroups.putAll(exactDuplicates);
                    StringBuilder sb = new StringBuilder("\n👯 Found duplicates:\n\n");
                    long totalSavings = 0;

                    for (Map.Entry<String, List<Path>> entry : exactDuplicates.entrySet()) {
                        sb.append("SHA-256: ").append(entry.getKey().substring(0, 10)).append("...\n");
                        long size = Files.size(entry.getValue().get(0));
                        totalSavings += size * (entry.getValue().size() - 1);
                        
                        for (int i = 0; i < entry.getValue().size(); i++) {
                            Path p = entry.getValue().get(i);
                            sb.append(i == 0 ? "  [KEEP] " : "  [DUP]  ").append(p.toAbsolutePath()).append("\n");
                        }
                        sb.append("\n");
                    }
                    sb.append(String.format("Potential savings: %.2f GB\n", totalSavings / (1024.0 * 1024.0 * 1024.0)));
                    sb.append("Press 'Safe Delete & Hardlink' to optimize these files.");
                    updateLog(sb.toString());
                    SwingUtilities.invokeLater(() -> deduplicateBtn.setEnabled(true));
                }

            } catch (Exception ex) {
                updateLog("❌ Error: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> scanBtn.setEnabled(true));
            }
        }).start();
    }

    private void runDeduplication() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "This will delete all duplicate copies and replace them with NTFS hardlinks to the kept file.\n" +
                "This keeps the file visible in all folders, but stores it only ONCE on disk.\n\n" +
                "Do you want to proceed?", "Confirm Deduplication", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) return;

        scanBtn.setEnabled(false);
        deduplicateBtn.setEnabled(false);
        logArea.setText("⚙️ Optimizing files...\n");

        new Thread(() -> {
            long totalSaved = 0;
            int count = 0;
            try {
                for (Map.Entry<String, List<Path>> entry : duplicateGroups.entrySet()) {
                    List<Path> paths = entry.getValue();
                    Path keptFile = paths.get(0);
                    long size = Files.size(keptFile);

                    for (int i = 1; i < paths.size(); i++) {
                        Path dupFile = paths.get(i);
                        updateLog("Optimizing duplicate: " + dupFile.getFileName());

                        // Delete duplicate
                        Files.delete(dupFile);

                        // Create hardlink
                        Files.createLink(dupFile, keptFile);

                        // Unregister from hash cache so it updates
                        hashRegistry.unregister(dupFile.toFile());

                        totalSaved += size;
                        count++;
                    }
                }
                updateLog(String.format("\n✅ Success! Deduplicated %d files.\nSaved %.2f GB of SSD storage!", count, totalSaved / (1024.0 * 1024.0 * 1024.0)));
            } catch (Exception ex) {
                updateLog("❌ Error during deduplication: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> scanBtn.setEnabled(true));
            }
        }).start();
    }

    private void updateLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
