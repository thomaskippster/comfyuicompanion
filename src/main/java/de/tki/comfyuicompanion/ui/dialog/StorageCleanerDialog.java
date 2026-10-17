package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ModelHashRegistry;
import de.tki.comfyuicompanion.service.impl.ModelStorageCleanerService;
import de.tki.comfyuicompanion.service.impl.ModelStorageCleanerService.CleanupCandidate;
import de.tki.comfyuicompanion.service.impl.ModelStorageCleanerService.StorageScanReport;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import de.tki.comfyuicompanion.service.SafePathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interactive dialog for the Intelligent Storage & Duplicate Cleaner.
 * Displays duplicate models across subfolders (e.g. {@code checkpoints/} and {@code unet/})
 * and cold models unused for more than 30 days, allowing 1-click archiving or duplicate removal.
 */
public class StorageCleanerDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(StorageCleanerDialog.class);

    private final ConfigService configService;
    private final ArchiveService archiveService;
    private final ModelHashRegistry hashRegistry;
    private final BackgroundExecutor backgroundExecutor;
    private final ModelStorageCleanerService cleanerService = new ModelStorageCleanerService();
    private final Runnable onCleanupCompleted;

    private final DefaultTableModel tableModel;
    private final JLabel summaryLabel;
    private final JSpinner coldDaysSpinner;
    private final JButton btnArchiveSelected;
    private final JButton btnDeleteDuplicates;
    private List<CleanupCandidate> currentCandidates = new ArrayList<>();

    /**
     * Constructs and initializes the StorageCleanerDialog.
     *
     * @param owner              parent window
     * @param configService      configuration service
     * @param archiveService     archive service for moving cold models to archive
     * @param hashRegistry       model hash registry
     * @param backgroundExecutor background executor
     * @param onCleanupCompleted optional callback invoked after archiving or deleting models
     */
    public StorageCleanerDialog(Window owner,
                                ConfigService configService,
                                ArchiveService archiveService,
                                ModelHashRegistry hashRegistry,
                                BackgroundExecutor backgroundExecutor,
                                Runnable onCleanupCompleted) {
        super(owner instanceof Frame f ? f : null, "Intelligent Storage & Duplicate Cleaner", true);
        this.configService = configService;
        this.archiveService = archiveService;
        this.hashRegistry = hashRegistry;
        this.backgroundExecutor = backgroundExecutor;
        this.onCleanupCompleted = onCleanupCompleted;

        setSize(940, 560);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel header = new JPanel(new BorderLayout(8, 6));
        summaryLabel = new JLabel("Scanning local model directories for duplicates and cold models...");
        summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        header.add(summaryLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.add(new JLabel("Cold Threshold (Days):"));
        coldDaysSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 365, 5));
        filterPanel.add(coldDaysSpinner);

        JButton btnRescan = new JButton("Rescan", SvgIconFactory.get(AppIcon.REFRESH, 14));
        btnRescan.addActionListener(e -> runScan());
        filterPanel.add(btnRescan);
        header.add(filterPanel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        String[] cols = {"Select", "Category", "Model Name", "Folder", "Size", "Details / Reason"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(240);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(250);

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        btnArchiveSelected = new JButton("Move Selected to Archive", SvgIconFactory.get(AppIcon.ARCHIVE, 14));
        btnArchiveSelected.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnArchiveSelected.addActionListener(e -> archiveSelectedCandidates());
        leftActions.add(btnArchiveSelected);

        btnDeleteDuplicates = new JButton("Delete Selected Duplicates", SvgIconFactory.get(AppIcon.CLOSE, 14));
        btnDeleteDuplicates.addActionListener(e -> deleteSelectedDuplicates());
        leftActions.add(btnDeleteDuplicates);
        footer.add(leftActions, BorderLayout.WEST);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        runScan();
    }

    private void runScan() {
        String modelsPath = configService != null ? configService.getModelsPath() : "";
        int coldDays = (int) coldDaysSpinner.getValue();
        summaryLabel.setText("⏳ Scanning " + modelsPath + "...");
        tableModel.setRowCount(0);

        backgroundExecutor.execute(() -> {
            StorageScanReport report = cleanerService.scanModelsDirectory(
                    Paths.get(modelsPath), coldDays, hashRegistry, Collections.emptySet()
            );
            SwingUtilities.invokeLater(() -> {
                currentCandidates = report.candidates();
                tableModel.setRowCount(0);
                for (CleanupCandidate c : currentCandidates) {
                    tableModel.addRow(new Object[]{
                            Boolean.TRUE,
                            c.type().getDisplayName(),
                            c.fileName(),
                            c.relativeFolder(),
                            ModelStorageCleanerService.formatSize(c.sizeBytes()),
                            c.reason()
                    });
                }
                summaryLabel.setText(String.format(
                        "Found %d Duplicate(s) & %d Cold Model(s) — Reclaimable SSD Space: %s",
                        report.duplicateCount(),
                        report.coldModelCount(),
                        ModelStorageCleanerService.formatSize(report.reclaimableBytesTotal())
                ));
            });
        });
    }

    private void archiveSelectedCandidates() {
        List<CleanupCandidate> selected = getSelectedCandidates();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No models selected.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Move " + selected.size() + " selected model(s) to Archive (" + configService.getArchivePath() + ")?",
                "Confirm Archive",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        btnArchiveSelected.setEnabled(false);
        backgroundExecutor.execute(() -> {
            int moved = 0;
            for (CleanupCandidate c : selected) {
                try {
                    archiveService.moveToArchive(c.relativeFolder(), c.fileName());
                    moved++;
                } catch (Exception ex) {
                    // Fallback direct move if custom subfolder
                    try {
                        java.nio.file.Path target = Paths.get(configService.getArchivePath(), c.relativeFolder(), c.fileName());
                        Files.createDirectories(target.getParent());
                        Files.move(c.fullPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        moved++;
                    } catch (Exception ignored) {}
                }
            }
            final int finalMoved = moved;
            SwingUtilities.invokeLater(() -> {
                btnArchiveSelected.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Successfully archived " + finalMoved + " model(s).");
                if (onCleanupCompleted != null) onCleanupCompleted.run();
                runScan();
            });
        });
    }

    private void deleteSelectedDuplicates() {
        List<CleanupCandidate> selected = getSelectedCandidates();
        if (selected.isEmpty()) return;
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Permanently delete " + selected.size() + " selected model file(s) from disk?\n(Tip: Use 'Move Selected to Archive' for safe non-destructive storage cleanup.)",
                "Confirm Permanent Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        btnDeleteDuplicates.setEnabled(false);
        backgroundExecutor.execute(() -> {
            SafePathValidator validator = new SafePathValidator();
            int deleted = 0;
            for (CleanupCandidate c : selected) {
                try {
                    if (c.fullPath() != null && Files.isRegularFile(c.fullPath())) {
                        validator.validateFilename(c.fileName());
                        if (Files.deleteIfExists(c.fullPath())) {
                            deleted++;
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to delete candidate model file {}: {}", c.fullPath(), ex.getMessage());
                }
            }
            final int finalDeleted = deleted;
            SwingUtilities.invokeLater(() -> {
                btnDeleteDuplicates.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Deleted " + finalDeleted + " file(s).");
                if (onCleanupCompleted != null) onCleanupCompleted.run();
                runScan();
            });
        });
    }

    private List<CleanupCandidate> getSelectedCandidates() {
        List<CleanupCandidate> list = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount() && i < currentCandidates.size(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                list.add(currentCandidates.get(i));
            }
        }
        return list;
    }
}
