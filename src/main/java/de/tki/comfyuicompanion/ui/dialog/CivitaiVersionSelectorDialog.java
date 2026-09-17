package de.tki.comfyuicompanion.ui.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.CivitaiService;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog component for searching Civitai models, browsing available versions,
 * previewing model imagery, and applying selected version details to download queue items.
 */
public class CivitaiVersionSelectorDialog {

    private final DownloadManagerView view;
    private final CivitaiService civitaiService;
    private final BackgroundExecutor backgroundExecutor;

    public CivitaiVersionSelectorDialog(DownloadManagerView view, CivitaiService civitaiService, BackgroundExecutor backgroundExecutor) {
        this.view = view;
        this.civitaiService = civitaiService;
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : new BackgroundExecutor();
    }

    /**
     * Opens the version selector dialog for a specific row in the download table.
     *
     * @param row              the row index in the download table
     * @param modelsToDownload the list of models queued for download
     */
    public void showDialog(int row, List<ModelInfo> modelsToDownload) {
        if (view == null || row < 0 || modelsToDownload == null || row >= modelsToDownload.size()) {
            return;
        }

        DefaultTableModel tableModel = view.getTableModel();
        String initialName = (String) tableModel.getValueAt(row, 2);
        String cleanedQuery = initialName.replaceAll("(?i)\\.(safetensors|ckpt|sft|pt|bin)$", "").trim();

        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), "Civitai Version Selector", true);
        dialog.setSize(950, 650);
        dialog.setLocationRelativeTo(view);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel searchBar = new JPanel(new BorderLayout(5, 0));
        JTextField searchField = new JTextField(cleanedQuery);
        JButton searchBtn = new JButton("Search");
        searchBtn.putClientProperty("JButton.buttonType", "accent");
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);
        leftPanel.add(searchBar, BorderLayout.NORTH);

        DefaultListModel<CivitaiModelItem> modelListModel = new DefaultListModel<>();
        JList<CivitaiModelItem> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(modelList);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        versionPanel.add(new JLabel("Version:"));
        JComboBox<CivitaiVersionItem> versionCombo = new JComboBox<>();
        versionPanel.add(versionCombo);
        rightPanel.add(versionPanel, BorderLayout.NORTH);

        JPanel detailsPanel = new JPanel(new BorderLayout(5, 5));
        JTextArea infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        JScrollPane infoScroll = new JScrollPane(infoText);
        infoScroll.setPreferredSize(new Dimension(350, 180));
        detailsPanel.add(infoScroll, BorderLayout.NORTH);

        JLabel previewLabel = new JLabel("No Preview Image Available", JLabel.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        previewLabel.setPreferredSize(new Dimension(350, 350));
        detailsPanel.add(previewLabel, BorderLayout.CENTER);

        rightPanel.add(detailsPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(420);
        dialog.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton applyBtn = new JButton("Apply Selected Version");
        applyBtn.putClientProperty("JButton.buttonType", "accent");

        bottomPanel.add(cancelBtn);
        bottomPanel.add(applyBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        Runnable doSearch = () -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) return;
            searchBtn.setEnabled(false);
            civitaiService.searchModelsRaw(q).thenAccept(root -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    if (root != null) {
                        JsonNode items = root.get("items");
                        if (items != null && items.isArray()) {
                            for (JsonNode item : items) {
                                String name = item.path("name").asText("Unknown");
                                String type = item.path("type").asText("Unknown");
                                modelListModel.addElement(new CivitaiModelItem(name, type, item));
                            }
                        }
                    }
                    if (modelListModel.isEmpty()) {
                        modelListModel.addElement(new CivitaiModelItem("No results found", "", null));
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    modelListModel.addElement(new CivitaiModelItem("Error: " + ex.getMessage(), "", null));
                });
                return null;
            });
        };

        searchBtn.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());

        modelList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CivitaiModelItem selectedModel = modelList.getSelectedValue();
            versionCombo.removeAllItems();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("No Preview Image Available");

            if (selectedModel == null || selectedModel.rawNode() == null) return;

            JsonNode versions = selectedModel.rawNode().get("modelVersions");
            if (versions != null && versions.isArray()) {
                for (JsonNode v : versions) {
                    String vName = v.path("name").asText("Unknown");
                    String downloadUrl = v.path("downloadUrl").asText("");

                    long byteSize = 0;
                    String sizeStr = "Unknown";
                    JsonNode files = v.get("files");
                    if (files != null && files.isArray() && files.size() > 0) {
                        double sizeKb = files.get(0).path("sizeKB").asDouble(0);
                        byteSize = (long) (sizeKb * 1024);
                        double sizeMb = sizeKb / 1024.0;
                        if (sizeMb > 1024) {
                            sizeStr = String.format("%.2f GB", sizeMb / 1024.0);
                        } else {
                            sizeStr = String.format("%.2f MB", sizeMb);
                        }
                    }

                    String imgUrl = null;
                    JsonNode images = v.get("images");
                    if (images != null && images.isArray() && images.size() > 0) {
                        imgUrl = images.get(0).path("url").asText(null);
                    }

                    List<String> triggers = new ArrayList<>();
                    JsonNode trainedWords = v.get("trainedWords");
                    if (trainedWords != null && trainedWords.isArray()) {
                        for (JsonNode word : trainedWords) {
                            triggers.add(word.asText());
                        }
                    }

                    versionCombo.addItem(new CivitaiVersionItem(vName, downloadUrl, sizeStr, byteSize, imgUrl, triggers, v));
                }
            }
        });

        versionCombo.addActionListener(e -> {
            CivitaiVersionItem selectedVersion = (CivitaiVersionItem) versionCombo.getSelectedItem();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("Loading Preview...");

            if (selectedVersion == null) {
                previewLabel.setText("No Preview Image Available");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("File Size: ").append(selectedVersion.size()).append("\n");
            sb.append("Download URL: ").append(selectedVersion.downloadUrl()).append("\n\n");

            if (!selectedVersion.triggerWords().isEmpty()) {
                sb.append("Trigger Words: ").append(String.join(", ", selectedVersion.triggerWords())).append("\n\n");
            }

            String desc = selectedVersion.rawNode().path("description").asText("");
            if (!desc.isEmpty()) {
                desc = desc.replaceAll("<[^>]*>", "");
                sb.append("Description:\n").append(desc);
            }

            infoText.setText(sb.toString());
            infoText.setCaretPosition(0);

            String imgUrl = selectedVersion.imageUrl();
            if (imgUrl != null && !imgUrl.isEmpty()) {
                backgroundExecutor.execute(() -> {
                    try {
                        BufferedImage img = javax.imageio.ImageIO.read(URI.create(imgUrl).toURL());
                        if (img != null) {
                            int lblWidth = previewLabel.getWidth();
                            int lblHeight = previewLabel.getHeight();
                            if (lblWidth <= 0) lblWidth = 350;
                            if (lblHeight <= 0) lblHeight = 350;

                            double ratio = Math.min((double) lblWidth / img.getWidth(), (double) lblHeight / img.getHeight());
                            int w = (int) (img.getWidth() * ratio);
                            int h = (int) (img.getHeight() * ratio);
                            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);

                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setIcon(new ImageIcon(scaled));
                                    previewLabel.setText("");
                                }
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setText("Failed to load image preview");
                                }
                            });
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (versionCombo.getSelectedItem() == selectedVersion) {
                                previewLabel.setText("Preview Image unavailable");
                            }
                        });
                    }
                });
            } else {
                previewLabel.setText("No Preview Image Available");
            }
        });

        applyBtn.addActionListener(e -> {
            CivitaiVersionItem selectedVersion = (CivitaiVersionItem) versionCombo.getSelectedItem();
            CivitaiModelItem selectedModel = modelList.getSelectedValue();
            if (selectedVersion == null || selectedModel == null) {
                JOptionPane.showMessageDialog(dialog, "Please select a model and a version first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ModelInfo info = modelsToDownload.get(row);

            String targetFileName = "";
            JsonNode files = selectedVersion.rawNode().get("files");
            if (files != null && files.isArray() && files.size() > 0) {
                targetFileName = files.get(0).path("name").asText("");
            }
            if (targetFileName.isEmpty()) {
                targetFileName = info.getName();
            }

            info.setName(targetFileName);
            info.setUrl(selectedVersion.downloadUrl());
            info.setSize(selectedVersion.size());
            info.setByteSize(selectedVersion.byteSize());

            tableModel.setValueAt(true, row, 0);
            tableModel.setValueAt(targetFileName, row, 2);
            tableModel.setValueAt(selectedVersion.size(), row, 3);
            tableModel.setValueAt(selectedVersion.downloadUrl(), row, 6);
            tableModel.setValueAt("✅ Known Good", row, 7);

            dialog.dispose();
            JOptionPane.showMessageDialog(view, "Model version applied! Ready to download.", "Version Applied", JOptionPane.INFORMATION_MESSAGE);
        });

        SwingUtilities.invokeLater(doSearch);
        dialog.setVisible(true);
    }

    public record CivitaiModelItem(String name, String type, JsonNode rawNode) {
        @Override
        public String toString() {
            return name + " (" + type + ")";
        }
    }

    public record CivitaiVersionItem(String name, String downloadUrl, String size, long byteSize,
                                     String imageUrl, List<String> triggerWords, JsonNode rawNode) {
        @Override
        public String toString() {
            return name;
        }
    }
}
