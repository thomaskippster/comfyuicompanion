package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.ICustomNodeManagerBridgeService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enterprise dialog presenting missing custom nodes required by an imported workflow
 * and enabling seamless 1-click installation with live console logging.
 */
public class MissingCustomNodesDialog extends JDialog {

    private final ICustomNodeManagerBridgeService bridgeService;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextArea logArea;
    private final JButton btnInstall;
    private final JButton btnClose;
    private final List<CustomNodePackage> packages = new ArrayList<>();
    private final Runnable onInstallationCompleted;

    /**
     * Constructs a new MissingCustomNodesDialog.
     *
     * @param owner parent window
     * @param missingPackagesMap map of packages to their missing node class names
     * @param bridgeService service to execute installation
     * @param onInstallationCompleted callback run when installation completes
     */
    public MissingCustomNodesDialog(Window owner,
                                    Map<CustomNodePackage, List<String>> missingPackagesMap,
                                    ICustomNodeManagerBridgeService bridgeService,
                                    Runnable onInstallationCompleted) {
        super(owner, "Missing Custom Nodes Detected", ModalityType.APPLICATION_MODAL);
        this.bridgeService = bridgeService;
        this.onInstallationCompleted = onInstallationCompleted;

        setSize(850, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));

        // Header Panel
        JPanel topPanel = new JPanel(new BorderLayout(10, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        JLabel titleLbl = new JLabel("Workflow Requires Missing Custom Nodes");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel descLbl = new JLabel("The following community node packages were resolved via ComfyUI-Manager. Install them now in 1 click.");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLbl.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        titleBox.add(titleLbl);
        titleBox.add(descLbl);
        topPanel.add(titleBox, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Center: Split Pane with Table and Console Log
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(200);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));

        String[] columns = {"Select", "Package", "Repository URL", "Missing Nodes", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);

        if (missingPackagesMap != null) {
            for (Map.Entry<CustomNodePackage, List<String>> entry : missingPackagesMap.entrySet()) {
                CustomNodePackage pkg = entry.getKey();
                List<String> nodes = entry.getValue();
                packages.add(pkg);
                tableModel.addRow(new Object[]{
                        Boolean.TRUE,
                        pkg.packageName(),
                        pkg.gitUrl(),
                        nodes.size() + " (" + String.join(", ", nodes) + ")",
                        "Ready to install"
                });
            }
        }

        JScrollPane tableScroll = new JScrollPane(table);
        splitPane.setTopComponent(tableScroll);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        Color bg = UIManager.getColor("TextArea.background");
        Color fg = UIManager.getColor("TextArea.foreground");
        if (bg != null) logArea.setBackground(bg);
        if (fg != null) logArea.setForeground(fg);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Installation Console"));
        splitPane.setBottomComponent(logScroll);

        add(splitPane, BorderLayout.CENTER);

        // Bottom Actions
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        btnInstall = new JButton("Install Selected Packages", SvgIconFactory.get(AppIcon.DOWNLOAD));
        btnInstall.setBackground(new Color(33, 150, 243));
        btnInstall.setForeground(Color.WHITE);
        btnInstall.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnInstall.putClientProperty("Button.arc", 999);

        btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());

        bottomPanel.add(btnInstall);
        bottomPanel.add(btnClose);
        add(bottomPanel, BorderLayout.SOUTH);

        btnInstall.addActionListener(e -> runInstallation());
    }

    private void runInstallation() {
        List<CustomNodePackage> toInstall = new ArrayList<>();
        List<Integer> selectedRows = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean checked = (Boolean) tableModel.getValueAt(i, 0);
            if (checked != null && checked) {
                toInstall.add(packages.get(i));
                selectedRows.add(i);
            }
        }

        if (toInstall.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one package to install.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnInstall.setEnabled(false);
        btnClose.setEnabled(false);
        for (int row : selectedRows) {
            tableModel.setValueAt("Installing...", row, 4);
        }
        logArea.setText("Starting 1-click installation of " + toInstall.size() + " custom node package(s)...\n");

        bridgeService.installPackagesAsync(toInstall, msg -> SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        })).thenAccept(allSuccess -> SwingUtilities.invokeLater(() -> {
            btnInstall.setEnabled(true);
            btnClose.setEnabled(true);
            for (int row : selectedRows) {
                tableModel.setValueAt(allSuccess ? "Installed ✅" : "Failed / Incomplete ⚠️", row, 4);
            }
            if (allSuccess) {
                logArea.append("\n🎉 All custom nodes installed successfully! Restart ComfyUI to load the new nodes.\n");
                if (onInstallationCompleted != null) {
                    onInstallationCompleted.run();
                }
            }
        }));
    }
}
