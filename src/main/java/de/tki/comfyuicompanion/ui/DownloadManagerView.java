package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
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
 * missing custom node installation, and delegates user interactions to a listener/controller.
 */
@Component
public class DownloadManagerView extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManagerView.class);

    public interface DownloadManagerListener {
        void onVerifyLocalModels(boolean deepCheck);
        void onShowArchiveDialog();
        default void onShowStorageCleanerDialog() {}
        void onRunDiagnostics();
        void onLoadWorkflowFile(File file);
        void onImportModelListFile(File file);
        void onAnalyzeJsonContent();
        default void onCheckMissingCustomNodes() {}
        default void onShowApiKeysDialog() {}
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
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        initUI();
    }

    public void setListener(DownloadManagerListener listener) {
        this.listener = listener;
    }

    private void initUI() {
        CardPanel toolbarCard = new CardPanel();
        toolbarCard.setLayout(new BorderLayout());
        
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.setOpaque(false);

        JButton verifyBtn = new JButton("Verify Models", SvgIconFactory.get(AppIcon.CHECK));
        verifyBtn.putClientProperty("JButton.buttonType", "roundRect");
        verifyBtn.addActionListener(e -> {
            if (listener != null) listener.onVerifyLocalModels(false);
        });

        JButton optimizeBtn = new JButton("Deep Check", SvgIconFactory.get(AppIcon.SETTINGS));
        optimizeBtn.putClientProperty("JButton.buttonType", "roundRect");
        optimizeBtn.setToolTipText("Performs thorough hash-based verification of downloaded models");
        optimizeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Deep verification hashes every local model to detect corruptions.\n" +
                "This might take several minutes depending on your disk speed.\n" +
                "Do you want to continue?", "Performance Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (listener != null) listener.onVerifyLocalModels(true);
            }
        });

        JButton cleanerBtn = new JButton("Duplicate & Cold Cleaner", SvgIconFactory.get(AppIcon.STORAGE_OPTIMIZER));
        cleanerBtn.putClientProperty("JButton.buttonType", "roundRect");
        cleanerBtn.setToolTipText("Scan for duplicate models across folders and cold models (>30 days unused) to archive or clean up");
        cleanerBtn.addActionListener(e -> {
            if (listener != null) listener.onShowStorageCleanerDialog();
        });

        JButton archiveBtn = new JButton("Archive...", SvgIconFactory.get(AppIcon.ARCHIVE));
        archiveBtn.putClientProperty("JButton.buttonType", "roundRect");
        archiveBtn.addActionListener(e -> {
            if (listener != null) listener.onShowArchiveDialog();
        });

        JButton diagnosticBtn = new JButton("Diagnostics", SvgIconFactory.get(AppIcon.DIAGNOSTICS));
        diagnosticBtn.putClientProperty("JButton.buttonType", "roundRect");
        diagnosticBtn.addActionListener(e -> {
            if (listener != null) listener.onRunDiagnostics();
        });

        JLabel toolsLabel = new JLabel("Storage Tools: ");
        toolsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        toolbar.add(toolsLabel);
        toolbar.add(verifyBtn);
        toolbar.add(optimizeBtn);
        toolbar.add(cleanerBtn);
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
        JButton loadJsonBtn = new JButton("Load Workflow...", SvgIconFactory.get(AppIcon.FILE_TEXT));
        loadJsonBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Workflow", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                if (listener != null) listener.onLoadWorkflowFile(file);
            }
        });

        JButton importModelListBtn = new JButton("Import Model List...", SvgIconFactory.get(AppIcon.FOLDER));
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) SwingUtilities.getWindowAncestor(this), "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                File file = new File(fd.getDirectory(), fd.getFile());
                if (listener != null) listener.onImportModelListFile(file);
            }
        });

        JButton missingNodesBtn = new JButton("Missing Nodes...", SvgIconFactory.get(AppIcon.SETTINGS));
        missingNodesBtn.setToolTipText("Check for missing custom nodes in this workflow and install via ComfyUI-Manager");
        missingNodesBtn.addActionListener(e -> {
            if (listener != null) listener.onCheckMissingCustomNodes();
        });

        JButton analyzeBtn = new JButton("Deep Search", SvgIconFactory.get(AppIcon.SEARCH));
        analyzeBtn.putClientProperty("JButton.buttonType", "accent");
        analyzeBtn.addActionListener(e -> {
            if (listener != null) listener.onAnalyzeJsonContent();
        });

        jsonButtons.add(loadJsonBtn);
        jsonButtons.add(importModelListBtn);
        jsonButtons.add(missingNodesBtn);
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
        modelTable.setRowHeight(26);

        DownloadManagerTableCellRenderer cellRenderer = new DownloadManagerTableCellRenderer();
        modelTable.setDefaultRenderer(String.class, cellRenderer);
        modelTable.setDefaultRenderer(Object.class, cellRenderer);

        modelTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel colModel = modelTable.getColumnModel();
        
        TableColumn col0 = colModel.getColumn(0);
        col0.setPreferredWidth(40);
        col0.setMinWidth(40);
        col0.setMaxWidth(40);

        TableColumn col1 = colModel.getColumn(1);
        col1.setPreferredWidth(85);
        col1.setMinWidth(60);

        TableColumn col2 = colModel.getColumn(2);
        col2.setPreferredWidth(180);
        col2.setMinWidth(120);

        TableColumn col3 = colModel.getColumn(3);
        col3.setPreferredWidth(65);
        col3.setMinWidth(50);

        TableColumn col4 = colModel.getColumn(4);
        col4.setPreferredWidth(120);
        col4.setMinWidth(80);

        TableColumn col5 = colModel.getColumn(5);
        col5.setPreferredWidth(120);
        col5.setMinWidth(80);

        TableColumn col6 = colModel.getColumn(6);
        col6.setPreferredWidth(120);
        col6.setMinWidth(80);

        TableColumn col7 = colModel.getColumn(7);
        col7.setPreferredWidth(130);
        col7.setMinWidth(80);

        modelTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = modelTable.rowAtPoint(e.getPoint());
                int col = modelTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 4) {
                    if (listener != null) listener.onShowCivitaiSearchDialog(row);
                } else if (row >= 0 && (col == 7 || e.getClickCount() == 2)) {
                    String status = (String) modelTable.getValueAt(row, 7);
                    if (status != null && (status.contains("Token Required") || status.contains("API Key Required") || status.contains("Auth Error"))) {
                        if (listener != null) listener.onShowApiKeysDialog();
                    }
                }
            }
        });

        CardPanel tablePanel = new CardPanel();
        tablePanel.setLayout(new BorderLayout());
        JScrollPane tableScroll = new JScrollPane(modelTable);
        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Models Table", tablePanel);
        workflowGraphPanel = new WorkflowGraphPanel();
        tabbedPane.addTab("Workflow Graph", workflowGraphPanel);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tabbedPane);
        splitPane.setDividerLocation(150);

        CardPanel bottomPanel = new CardPanel();
        bottomPanel.setLayout(new BorderLayout(10, 10));
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        progressPanel.setOpaque(false);
        statusLabel = new JLabel("Ready");
        activeAiModelLabel = new JLabel("Active AI Model: None");
        activeAiModelLabel.setForeground(ThemeManager.getTextSecondaryColor());
        progressPanel.add(statusLabel);
        progressPanel.add(activeAiModelLabel);
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionButtons.setOpaque(false);
        downloadButton = new JButton("Download Models", SvgIconFactory.get(AppIcon.DOWNLOAD));
        downloadButton.putClientProperty("JButton.buttonType", "accent");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> {
            if (listener != null) listener.onStartDownloadQueue();
        });

        pauseButton = new JButton("Pause", SvgIconFactory.get(AppIcon.PAUSE));
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> {
            if (listener != null) listener.onTogglePause();
        });

        stopButton = new JButton("Stop", SvgIconFactory.get(AppIcon.STOP));
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
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
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

