package de.tki.comfymodels.ui;

import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

/**
 * View component for the Download Manager tab.
 * Encapsulates UI layout, toolbar, workflow graph tab, model table, progress controls,
 * and delegates user interactions to a listener/controller.
 */
@Component
public class DownloadManagerView extends JPanel {

    public interface DownloadManagerListener {
        void onVerifyLocalModels(boolean deepCheck);
        void onShowArchiveDialog();
        void onRunDiagnostics();
        void onLoadWorkflowFile(File file);
        void onImportModelListFile(File file);
        void onAnalyzeJsonContent();
        void onShowCivitaiSearchDialog(int modelRowIndex);
        void onUpdateDownloadManagerSelection();
        void onUpdateDownloadButtonsState();
        void onStartDownloadQueue();
        void onTogglePause();
        void onStopDownloadQueue();
    }

    private DownloadManagerListener listener;

    private JTextArea jsonInputArea;
    private DefaultTableModel tableModel;
    private JTable modelTable;
    private WorkflowGraphPanel workflowGraphPanel;
    private JLabel statusLabel;
    private JLabel activeAiModelLabel;
    private JButton downloadButton;
    private JButton pauseButton;
    private JButton stopButton;

    public DownloadManagerView() {
        initUI();
    }

    public void setListener(DownloadManagerListener listener) {
        this.listener = listener;
    }

    private void initUI() {
        setOpaque(false); // Relies on APP_BG_COLOR from root
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        CardPanel toolbarCard = new CardPanel();
        toolbarCard.setLayout(new BorderLayout());
        toolbarCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        toolbar.setOpaque(false);

        JButton verifyBtn = new JButton("🔍 Quick Check");
        verifyBtn.putClientProperty("JButton.buttonType", "roundRect");
        verifyBtn.addActionListener(e -> {
            if (listener != null) listener.onVerifyLocalModels(false);
        });
        
        JButton optimizeBtn = new JButton("👯 Storage Optimizer");
        optimizeBtn.putClientProperty("JButton.buttonType", "roundRect");
        optimizeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "The storage optimizer calculates SHA-256 hashes for all local models.\n" +
                "This is EXTREMELY slow and resource-intensive for large libraries.\n\n" +
                "Do you want to continue?", "Performance Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (listener != null) listener.onVerifyLocalModels(true);
            }
        });

        JButton archiveBtn = new JButton("📦 Archive...");
        archiveBtn.putClientProperty("JButton.buttonType", "roundRect");
        archiveBtn.addActionListener(e -> {
            if (listener != null) listener.onShowArchiveDialog();
        });

        JButton diagnosticBtn = new JButton("🩺 Diagnostics");
        diagnosticBtn.putClientProperty("JButton.buttonType", "roundRect");
        diagnosticBtn.addActionListener(e -> {
            if (listener != null) listener.onRunDiagnostics();
        });

        JLabel toolsLabel = new JLabel("Storage Tools: ");
        toolsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        toolbar.add(toolsLabel);
        toolbar.add(verifyBtn);
        toolbar.add(optimizeBtn);
        toolbar.add(archiveBtn);
        
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        toolbar.add(sep);
        
        toolbar.add(diagnosticBtn);
        toolbarCard.add(toolbar, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        CardPanel jsonPanel = new CardPanel();
        jsonPanel.setLayout(new BorderLayout());
        
        JLabel jsonTitle = new JLabel("Workflow (Drag & Drop JSON/PNG)");
        jsonTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        jsonTitle.setForeground(ThemeManager.getTextColor());
        jsonPanel.add(jsonTitle, BorderLayout.NORTH);
        
        jsonInputArea = new JTextArea();
        setupDragAndDrop(jsonInputArea);
        JScrollPane jsonScroll = new JScrollPane(jsonInputArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        
        JPanel jsonButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        jsonButtons.setOpaque(false);
        JButton loadJsonBtn = new JButton("Load Workflow...");
        loadJsonBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Workflow", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                if (listener != null) listener.onLoadWorkflowFile(file);
            }
        });

        JButton importModelListBtn = new JButton("Import Model List...");
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                if (listener != null) listener.onImportModelListFile(file);
            }
        });

        JButton analyzeBtn = new JButton("Deep Search");
        analyzeBtn.putClientProperty("JButton.buttonType", "accent");
        analyzeBtn.addActionListener(e -> {
            if (listener != null) listener.onAnalyzeJsonContent();
        });

        jsonButtons.add(loadJsonBtn);
        jsonButtons.add(importModelListBtn);
        jsonButtons.add(analyzeBtn);
        jsonPanel.add(jsonScroll, BorderLayout.CENTER);
        jsonPanel.add(jsonButtons, BorderLayout.SOUTH);

        String[] columnNames = {"Select", "Type", "Name", "Size", "AI Source", "Target Path", "URL", "Status"}; 
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }   
            @Override public boolean isCellEditable(int r, int c) { 
                if (c == 0) {
                    String status = (String) getValueAt(r, 7);
                    return status != null && !status.contains("Already exists");
                }
                return false; 
            }
        };
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                if (listener != null) listener.onUpdateDownloadManagerSelection();
            }
            if (listener != null) listener.onUpdateDownloadButtonsState();
        });

        modelTable = new JTable(tableModel);
        modelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        modelTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel colModel = modelTable.getColumnModel();
        
        TableColumn col0 = colModel.getColumn(0);
        col0.setPreferredWidth(40);
        col0.setMinWidth(40);
        col0.setMaxWidth(60);
        
        colModel.getColumn(1).setPreferredWidth(115);
        colModel.getColumn(2).setPreferredWidth(172);
        colModel.getColumn(3).setPreferredWidth(58);
        colModel.getColumn(4).setPreferredWidth(115);
        colModel.getColumn(5).setPreferredWidth(115);
        colModel.getColumn(6).setPreferredWidth(288);
        colModel.getColumn(7).setPreferredWidth(253);

        JPopupMenu modelTablePopup = new JPopupMenu();
        JMenuItem searchCivitaiItem = new JMenuItem("Search on Civitai & select version...");
        modelTablePopup.add(searchCivitaiItem);

        modelTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = modelTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < modelTable.getRowCount()) {
                        modelTable.setRowSelectionInterval(row, row);
                        modelTablePopup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        searchCivitaiItem.addActionListener(e -> {
            int selectedRow = modelTable.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = modelTable.convertRowIndexToModel(selectedRow);
                if (listener != null) listener.onShowCivitaiSearchDialog(modelRow);
            }
        });

        JScrollPane tableScroll = new JScrollPane(modelTable);
        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        tablePanel.setOpaque(false);
        tablePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5), "Detected Models"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        JTabbedPane workflowInputTabs = new JTabbedPane();
        workflowInputTabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        workflowInputTabs.putClientProperty("JTabbedPane.tabType", "card");
        workflowInputTabs.setOpaque(false);
        
        workflowInputTabs.addTab("📝 JSON Source", jsonPanel);
        
        workflowGraphPanel = new WorkflowGraphPanel();
        workflowGraphPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        workflowGraphPanel.setOpaque(false);
        setupDragAndDrop(workflowGraphPanel);
        workflowInputTabs.addTab("📊 Visual Graph", workflowGraphPanel);
        
        splitPane.setOpaque(false);
        splitPane.setTopComponent(workflowInputTabs);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(350);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        statusLabel = new JLabel("Ready");
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        progressPanel.setOpaque(false);
        activeAiModelLabel = new JLabel("Active AI: Loading...");
        activeAiModelLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        activeAiModelLabel.setForeground(Color.GRAY);
        progressPanel.add(statusLabel);
        progressPanel.add(activeAiModelLabel);
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        actionButtons.setOpaque(false);
        downloadButton = new JButton("Start queue");
        downloadButton.putClientProperty("JButton.buttonType", "accent");
        downloadButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> {
            if (listener != null) listener.onStartDownloadQueue();
        });
        
        pauseButton = new JButton("Pause");
        pauseButton.putClientProperty("JButton.buttonType", "roundRect");
        pauseButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> {
            if (listener != null) listener.onTogglePause();
        });
        
        stopButton = new JButton("Stop");
        stopButton.putClientProperty("JButton.buttonType", "roundRect");
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> {
            if (listener != null) listener.onStopDownloadQueue();
        });
        
        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        CardPanel mainCard = new CardPanel();
        mainCard.setLayout(new BorderLayout(10, 10));
        mainCard.add(splitPane, BorderLayout.CENTER);
        mainCard.add(bottomPanel, BorderLayout.SOUTH);

        add(toolbarCard, BorderLayout.NORTH);
        add(mainCard, BorderLayout.CENTER);
    }

    private void setupDragAndDrop(java.awt.Component area) {
        new DropTarget(area, new DropTargetListener() {
            public void dragEnter(DropTargetDragEvent dtde) {}
            public void dragOver(DropTargetDragEvent dtde) {}
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            public void dragExit(DropTargetEvent dte) {}
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty() && listener != null) {
                        listener.onLoadWorkflowFile(files.get(0));
                    }
                } catch (Exception e) {}
            }
        });
    }

    // Getters
    public JTextArea getJsonInputArea() { return jsonInputArea; }
    public DefaultTableModel getTableModel() { return tableModel; }
    public JTable getModelTable() { return modelTable; }
    public WorkflowGraphPanel getWorkflowGraphPanel() { return workflowGraphPanel; }
    public JLabel getStatusLabel() { return statusLabel; }
    public JLabel getActiveAiModelLabel() { return activeAiModelLabel; }
    public JButton getDownloadButton() { return downloadButton; }
    public JButton getPauseButton() { return pauseButton; }
    public JButton getStopButton() { return stopButton; }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
    }
}
