package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IModelValidator;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ModelHashRegistry;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles the verification and duplicate detection of local model files,
 * including batch validation, SHA-256 hash collision checks, and cleanup prompts.
 */
public class ModelVerificationHandler {

    private final DownloadManagerView view;
    private final ConfigService configService;
    private final IModelValidator modelValidator;
    private final ModelHashRegistry hashRegistry;
    private final BackgroundExecutor backgroundExecutor;

    public ModelVerificationHandler(DownloadManagerView view,
                                    ConfigService configService,
                                    IModelValidator modelValidator,
                                    ModelHashRegistry hashRegistry,
                                    BackgroundExecutor backgroundExecutor) {
        this.view = view;
        this.configService = configService;
        this.modelValidator = modelValidator;
        this.hashRegistry = hashRegistry;
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : new BackgroundExecutor();
    }

    /**
     * Verifies models on disk against corruption and optionally calculates hashes for duplicate detection.
     *
     * @param checkDuplicates true to calculate SHA-256 hashes and identify duplicate models
     */
    public void verifyLocalModels(boolean checkDuplicates) {
        String baseDir = configService.getModelsPath();
        if (baseDir == null || baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Please set a models directory first.");
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            JOptionPane.showMessageDialog(view, "Invalid models directory.");
            return;
        }

        String taskName = checkDuplicates ? "Verifying models & checking for duplicates" : "Verifying models (Fast Check)";
        if (view != null && view.getStatusLabel() != null) {
            view.getStatusLabel().setText(taskName + "... please wait.");
        }

        backgroundExecutor.execute(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
                Map<String, List<Path>> hashToPaths = new HashMap<>();

                List<Path> allFiles;
                try (Stream<Path> pathStream = Files.walk(root.toPath())) {
                    allFiles = pathStream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String relPath = root.toPath().relativize(p).toString().toLowerCase();
                                return !relPath.contains(".venv") && !relPath.contains("archive");
                            })
                            .filter(p -> {
                                String n = p.getFileName().toString().toLowerCase();
                                return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt")
                                        || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin") || n.endsWith(".onnx");
                            })
                            .collect(Collectors.toList());
                }

                int total = allFiles.size();
                for (int i = 0; i < total; i++) {
                    Path p = allFiles.get(i);
                    final int current = i + 1;
                    SwingUtilities.invokeLater(() -> {
                        if (view != null && view.getStatusLabel() != null) {
                            view.getStatusLabel().setText("Checking (" + current + "/" + total + "): " + p.getFileName());
                        }
                    });

                    IModelValidator.ValidationResult res = modelValidator.validateFile(p.toFile());
                    if (!res.ok) {
                        errors.add(res);
                    } else if (checkDuplicates) {
                        String hash = hashRegistry.getOrCalculateHash(p.toFile());
                        if (hash != null) {
                            hashToPaths.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        }
                    }
                }

                Map<String, List<Path>> duplicates = checkDuplicates ? hashToPaths.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();

                SwingUtilities.invokeLater(() -> {
                    if (view != null && view.getStatusLabel() != null) {
                        view.getStatusLabel().setText("Scan finished. Found " + errors.size() + " issues and " + duplicates.size() + " duplicate sets.");
                    }

                    if (errors.isEmpty() && duplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(view, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    if (!errors.isEmpty()) {
                        StringBuilder sb = new StringBuilder("The following " + errors.size() + " files appear to be corrupted or invalid:\n\n");
                        for (IModelValidator.ValidationResult err : errors) {
                            sb.append("- ").append(new File(err.filePath).getName())
                                    .append(" (").append(err.message).append(")\n")
                                    .append("  Path: ").append(err.filePath).append("\n\n");
                        }

                        JTextArea textArea = new JTextArea(sb.toString());
                        textArea.setEditable(false);
                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(800, 500));

                        Object[] options = {"OK", "Delete All Corrupted Files"};
                        int choice = JOptionPane.showOptionDialog(view, scrollPane, "Verification Results - Issues Found",
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                        if (choice == 1) {
                            int confirm = JOptionPane.showConfirmDialog(view,
                                    "Are you sure you want to delete these " + errors.size() + " files?\nThis action cannot be undone.",
                                    "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                            if (confirm == JOptionPane.YES_OPTION) {
                                int deletedCount = 0;
                                for (IModelValidator.ValidationResult err : errors) {
                                    File f = new File(err.filePath);
                                    if (f.exists() && f.delete()) {
                                        deletedCount++;
                                    }
                                }
                                JOptionPane.showMessageDialog(view, "Deleted " + deletedCount + " files.");
                                if (view != null && view.getStatusLabel() != null) {
                                    view.getStatusLabel().setText("Cleanup finished. Deleted " + deletedCount + " files.");
                                }
                            }
                        }
                    }

                    if (!duplicates.isEmpty()) {
                        showDuplicatesDialog(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(view, "Error during verification: " + e.getMessage()));
            }
        });
    }

    public void showDuplicatesDialog(Map<String, List<Path>> duplicates) {
        StringBuilder sb = new StringBuilder("Storage Optimizer - Duplicate Models Found:\n\n");
        sb.append("The following files have identical content (SHA-256 match).\n");
        sb.append("You might want to delete redundant copies to save space.\n\n");

        for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
            sb.append("SHA-256: ").append(entry.getKey()).append("\n");
            for (Path p : entry.getValue()) {
                sb.append("  -> ").append(p.toAbsolutePath()).append("\n");
            }
            sb.append("\n");
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        JOptionPane.showMessageDialog(view, scrollPane, "Duplicates Found", JOptionPane.INFORMATION_MESSAGE);
    }
}
