package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.imageio.ImageIO;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

@Component
public class Main extends JFrame {
    private final IModelAnalyzer analyzer;
    private final IDownloadManager downloadManager;
    private final IWorkflowService workflowService;
    private final IModelSearchService searchService;
    private final IModelValidator modelValidator;
    private final de.tki.comfymodels.service.impl.RestBridgeService restBridge;
    private final de.tki.comfymodels.service.impl.ArchiveService archiveService;
    private final de.tki.comfymodels.service.IComfyLifecycleService lifecycleService;
    private final de.tki.comfymodels.service.impl.ComfyDiagnosticService diagnosticService;
    private final de.tki.comfymodels.service.impl.ProfileManager profileManager;
    private final de.tki.comfymodels.service.impl.EnvironmentBootstrapperImpl bootstrapper;
    private final de.tki.comfymodels.service.impl.ComfyProcessController processController;
    private final de.tki.comfymodels.service.impl.CivitaiService civitaiService;
    private final de.tki.comfymodels.service.impl.HuggingFaceService huggingFaceService;
    private JLabel lblComfyVersion;
    private JLabel lblPythonVersion;
    private JLabel lifecycleStatusLabel;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private de.tki.comfymodels.service.impl.LocalAIService localAIService;

    @Autowired
    private ModelListService modelListService;

    @Autowired
    private ModelHashRegistry hashRegistry;

    @Autowired
    private de.tki.comfymodels.service.impl.LocalModelScanner localScanner;

    @Autowired
    private de.tki.comfymodels.service.impl.PathResolver pathResolver;

    @Autowired
    private de.tki.comfymodels.service.impl.VersionService versionService;



    @Autowired
    private de.tki.comfymodels.service.impl.HardwareMonitorService hardwareMonitorService;

    @Autowired
    private de.tki.comfymodels.service.impl.UpdaterService updaterService;

    private JProgressBar progressCpu;
    private JProgressBar progressRam;
    private JProgressBar progressGpu;
    private JProgressBar progressVram;
    private JLabel lblGpuName;
    private JPanel gpuPanel;

    private JCheckBox backgroundCheck;
    private JCheckBox shutdownCheck;
    private JCheckBox restartCheck;
    private JCheckBox darkCheck;
    private JCheckBox fastHashCheck;
    private JCheckBox hideComfyuiCheck;
    private JLabel activeAiModelLabel;
    private JTextArea jsonInputArea;
    private JTextArea consoleOutput; 
    private DefaultTableModel tableModel;
    private JButton launchBtn;
    private JList<de.tki.comfymodels.domain.LaunchProfile> profileList;
    private JLabel statusLabel;
    private JButton downloadButton, pauseButton, stopButton;
    private List<ModelInfo> modelsToDownload;
    private String currentFileName = "input.json";
    private Image appIcon;

    private JTabbedPane mainTabs;
    private de.tki.comfymodels.ui.WorkflowGraphPanel workflowGraphPanel;


    private final java.util.Set<String> comfyCheckpoints = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    private final java.util.Set<String> comfyUnetModels = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    private final java.util.Set<String> comfyClips = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    private final java.util.Set<String> comfyVaes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    private final java.util.Set<String> comfyClipTypes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);
    private final java.util.Set<String> comfyUnetWeightDtypes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(Boolean.TRUE);

    // Prompt Lab Fields
    private static class DropdownItem {
        private final String display;
        private final String value;
        public DropdownItem(String display, String value) {
            this.display = display;
            this.value = value;
        }
        @Override
        public String toString() {
            return display;
        }
        public String getValue() {
            return value;
        }
    }
    private JTextField promptSubjectField;
    private JComboBox<DropdownItem> promptEnvCombo;
    private JComboBox<String> promptModelCombo;
    private JSpinner promptWidthSpinner;
    private JSpinner promptHeightSpinner;
    private JSpinner promptStepsSpinner;
    private JSpinner promptCfgSpinner;
    private JLabel promptPresetLabel;

    private JCheckBox chkPhotorealistic;
    private JCheckBox chkOil;
    private JCheckBox chkEngine;
    private JCheckBox chkAnime;
    private JCheckBox chkFantasy;
    private JCheckBox chkSketch;
    private JTextArea promptAssembleArea;
    private JTextArea promptJsonArea;
    private JTextArea promptLabConsole;
    private JButton btnSendToComfy;
    private JButton btnOptimizePrompt;

    private JButton btnSuggestSubject;
    private JPanel promptSubjectSuggestionsWrapper;
    private JPanel promptSubjectSuggestionsPanel;
    private JPanel promptLabLeftPanel;
    private JLabel promptImagePreviewLabel;
    private JProgressBar promptLabProgressBar;
    private Image currentPreviewImage;
    private JTabbedPane promptLabRightTabbedPane;

    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager,
                IWorkflowService workflowService, IModelSearchService searchService,
                IModelValidator modelValidator, de.tki.comfymodels.service.impl.RestBridgeService restBridge,   
                de.tki.comfymodels.service.impl.ArchiveService archiveService,
                de.tki.comfymodels.service.IComfyLifecycleService lifecycleService,
                de.tki.comfymodels.service.impl.ComfyDiagnosticService diagnosticService,
                de.tki.comfymodels.service.impl.ProfileManager profileManager,
                de.tki.comfymodels.service.impl.EnvironmentBootstrapperImpl bootstrapper,
                de.tki.comfymodels.service.impl.ComfyProcessController processController,
                de.tki.comfymodels.service.impl.CivitaiService civitaiService,
                de.tki.comfymodels.service.impl.HuggingFaceService huggingFaceService) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.restBridge = restBridge;
        this.archiveService = archiveService;
        this.lifecycleService = lifecycleService;
        this.lifecycleService.setOnBrowserLaunched(() -> {
            SwingUtilities.invokeLater(() -> {
                if (mainTabs != null) {
                    for (int i = 0; i < mainTabs.getTabCount(); i++) {
                        if (mainTabs.getTitleAt(i).contains("Model Manager")) {
                            mainTabs.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                refreshPromptLabModels();
            });
        });
        this.diagnosticService = diagnosticService;
        this.profileManager = profileManager;
        this.bootstrapper = bootstrapper;
        this.processController = processController;
        this.civitaiService = civitaiService;
        this.huggingFaceService = huggingFaceService;
    }
    public void launch(String[] args) {
        // Initialize ProfileManager with app storage directory
        Path appData = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
        try { Files.createDirectories(appData); } catch (Exception ignored) {}
        profileManager.init(appData);

        if (de.tki.comfymodels.util.PlatformUtils.isMac()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "ComfyUI Companion");
            System.setProperty("apple.awt.application.appearance", "system");
        }

        // Initialize REST Bridge consumer EARLY
        restBridge.setWorkflowConsumer(workflowJson -> {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[Main] WorkflowConsumer triggered - bringing to front.");
                if (jsonInputArea != null) {
                    jsonInputArea.setText(workflowJson);
                    currentFileName = "remote_workflow.json";
                    analyzeJsonContent();
                    searchMissingOnline();
                }
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            });
        });
        
        // Load settings to get/generate the API Token
        if (configService.isUnlocked()) {
            restBridge.setApiToken(configService.getApiToken());
        }
        restBridge.startServer();

        // Ensure server stops on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            processController.stop();
            downloadManager.stop();
            if (hardwareMonitorService != null) {
                hardwareMonitorService.stop();
            }
        }));

        if (!promptForPassword()) {
            System.exit(0);
        }

        // Apply theme before UI initialization
        setupTheme(configService.isDarkMode());
        
        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
                setupTrayIcon();
                loadSettingsIntoUI(); 
                updateAiModelDisplay();
                
                if (hardwareMonitorService != null) {
                    hardwareMonitorService.start(stats -> SwingUtilities.invokeLater(() -> updateHardwareUI(stats)));
                }
                
                // Add WindowListener to handle background mode
                addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        if (configService.isBackgroundModeEnabled()) {
                            setVisible(false);
                        } else {
                            processController.stop();
                            downloadManager.stop();
                            if (hardwareMonitorService != null) {
                                hardwareMonitorService.stop();
                            }
                            System.exit(0);
                        }
                    }
                });
                
                setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    private void setupTheme(boolean darkMode) {
        try {
            // Global arcs for a modern feel - rounded corners
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 16);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 999);
            UIManager.put("TitlePane.unifiedBackground", true);

            // Clean, highly readable typography (serifenlose Schriftart)
            Font defaultFont = new Font("Segoe UI", Font.PLAIN, 13);
            for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (fontName.equalsIgnoreCase("Inter") || fontName.equalsIgnoreCase("Roboto")) {
                    defaultFont = new Font(fontName, Font.PLAIN, 13);
                    break;
                }
            }
            UIManager.put("defaultFont", defaultFont);

            if (darkMode) {
                // Frosted glass Base Dark Palette (Anthracite)
                Color nodeBg = new javax.swing.plaf.ColorUIResource(24, 26, 32); 
                Color comfySurface = new javax.swing.plaf.ColorUIResource(18, 19, 22); 
                Color comfyAccent = new javax.swing.plaf.ColorUIResource(0, 240, 255); // Turquoise Accent
                Color comfyText = new javax.swing.plaf.ColorUIResource(220, 230, 242); 
                Color comfyBorder = new javax.swing.plaf.ColorUIResource(36, 39, 48);

                UIManager.put("DefaultBackgroundColor", comfySurface);
                UIManager.put("Panel.background", nodeBg);
                UIManager.put("Table.background", comfySurface);
                UIManager.put("TextArea.background", comfySurface);
                UIManager.put("TextField.background", new javax.swing.plaf.ColorUIResource(32, 36, 44));
                UIManager.put("PasswordField.background", new javax.swing.plaf.ColorUIResource(32, 36, 44));
                
                UIManager.put("Label.foreground", comfyText);
                UIManager.put("Table.foreground", comfyText);
                UIManager.put("TextArea.foreground", comfyText);
                
                UIManager.put("Table.selectionBackground", new javax.swing.plaf.ColorUIResource(new Color(0, 240, 255, 60))); 
                UIManager.put("Table.selectionForeground", Color.WHITE);
                UIManager.put("Component.focusedBorderColor", comfyAccent);
                UIManager.put("Separator.foreground", comfyBorder);
                
                UIManager.put("Button.background", new javax.swing.plaf.ColorUIResource(32, 36, 44));
                UIManager.put("Button.foreground", comfyText);
                UIManager.put("Button.focusedBackground", new javax.swing.plaf.ColorUIResource(0, 180, 200)); 
                UIManager.put("Button.hoverBackground", new javax.swing.plaf.ColorUIResource(0, 200, 220));
                UIManager.put("Button.pressedBackground", new javax.swing.plaf.ColorUIResource(20, 21, 24));
                UIManager.put("Button.borderColor", comfyBorder);
                
                UIManager.put("ScrollBar.track", comfySurface);
                UIManager.put("ScrollBar.thumb", new javax.swing.plaf.ColorUIResource(60, 64, 76));
                
                UIManager.put("TabbedPane.selectedBackground", new javax.swing.plaf.ColorUIResource(new Color(0, 240, 255, 40)));
                UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
                UIManager.put("TabbedPane.underlineColor", comfyAccent);

                // ProgressBar custom styles - slim and styled with glowing turquoise
                UIManager.put("ProgressBar.foreground", comfyAccent);
                UIManager.put("ProgressBar.background", new Color(30, 35, 45));
                UIManager.put("ProgressBar.arc", 999);

                // Card panel & UI styling variables
                UIManager.put("Card.background", new Color(30, 34, 42, 176)); 
                UIManager.put("Card.border", new Color(255, 255, 255, 24)); 
                UIManager.put("Card.placeholder", new Color(30, 34, 42, 144)); 
                UIManager.put("Card.placeholderBorder", new Color(255, 255, 255, 18));
                UIManager.put("Toolbar.customBg", new Color(30, 34, 42, 128));
                UIManager.put("SlimStat.titleForeground", new Color(180, 190, 205));
                UIManager.put("SlimStat.valueForeground", new Color(0, 240, 255));
                UIManager.put("SlimStat.barForeground", new Color(0, 240, 255));
                UIManager.put("SlimStat.barBackground", new Color(32, 37, 48));
                UIManager.put("MainTabs.gradientStart", new Color(14, 15, 17));
                UIManager.put("MainTabs.gradientEnd", new Color(24, 28, 38));
                UIManager.put("MainTabs.glowStart", new Color(0, 240, 255, 12));
                UIManager.put("PromptLab.presetForeground", new Color(100, 160, 240));
                
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                // EXHAUSTIVE cleanup of custom overrides
                String[] keysToClear = {
                    "DefaultBackgroundColor", "Panel.background", "Table.background", "TextArea.background",
                    "TextField.background", "PasswordField.background", "Label.foreground",
                    "Table.foreground", "TextArea.foreground", "Table.selectionBackground",
                    "Table.selectionForeground", "Component.focusedBorderColor", "Separator.foreground",
                    "Button.background", "Button.foreground", "Button.focusedBackground",
                    "Button.hoverBackground", "Button.pressedBackground", "Button.borderColor",
                    "ScrollBar.track", "ScrollBar.thumb", "TabbedPane.selectedBackground",
                    "TabbedPane.selectedForeground", "ProgressBar.foreground", "ProgressBar.background"
                };
                for (String key : keysToClear) {
                    UIManager.put(key, null);
                }
                UIManager.setLookAndFeel(new FlatLightLaf());
                
                // Pronounced borders in light mode (white mode)
                java.awt.Color pronouncedBorder = new javax.swing.plaf.ColorUIResource(180, 185, 195);
                UIManager.put("Component.borderColor", pronouncedBorder);
                UIManager.put("Button.borderColor", pronouncedBorder);
                UIManager.put("Separator.foreground", new javax.swing.plaf.ColorUIResource(200, 205, 215));

                // Soft/Accent light tab styles
                UIManager.put("TabbedPane.selectedBackground", new javax.swing.plaf.ColorUIResource(new Color(0, 120, 150, 25)));
                UIManager.put("TabbedPane.selectedForeground", new Color(20, 30, 40));
                UIManager.put("TabbedPane.underlineColor", new Color(0, 120, 150));

                // Card panel & UI styling variables for light mode
                UIManager.put("Card.background", new Color(240, 243, 248, 180)); 
                UIManager.put("Card.border", new Color(0, 0, 0, 24)); 
                UIManager.put("Card.placeholder", new Color(230, 235, 242, 160)); 
                UIManager.put("Card.placeholderBorder", new Color(0, 0, 0, 18));
                UIManager.put("Toolbar.customBg", new Color(240, 243, 248, 128));
                UIManager.put("SlimStat.titleForeground", new Color(90, 100, 110));
                UIManager.put("SlimStat.valueForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barBackground", new Color(225, 230, 240));
                UIManager.put("MainTabs.gradientStart", new Color(245, 247, 250));
                UIManager.put("MainTabs.gradientEnd", new Color(255, 255, 255));
                UIManager.put("MainTabs.glowStart", new Color(0, 120, 150, 8));
                UIManager.put("PromptLab.presetForeground", new Color(30, 100, 200));
            }
            
            // Re-apply global arcs which might be cleared by setLookAndFeel
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 16);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 999);

            // Update text area backgrounds / foregrounds dynamically if already instantiated
            if (consoleOutput != null) {
                if (darkMode) {
                    consoleOutput.setBackground(new Color(25, 25, 25));
                    consoleOutput.setForeground(new Color(0, 220, 0));
                } else {
                    consoleOutput.setBackground(new Color(245, 247, 250));
                    consoleOutput.setForeground(new Color(30, 30, 30));
                }
            }
            if (promptLabConsole != null) {
                if (darkMode) {
                    promptLabConsole.setBackground(new Color(25, 25, 25));
                    promptLabConsole.setForeground(new Color(0, 220, 0));
                } else {
                    promptLabConsole.setBackground(new Color(245, 247, 250));
                    promptLabConsole.setForeground(new Color(30, 30, 30));
                }
            }
            
            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            System.err.println("Theme setup failed: " + e.getMessage());
        }
    }

    private void loadIcon() {
        try (InputStream is = getClass().getResourceAsStream("/icons/app_icon.jpg")) {
            if (is != null) {
                appIcon = javax.imageio.ImageIO.read(is);
                if (appIcon != null) {
                    setIconImage(appIcon);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load app icon: " + e.getMessage());
        }
    }

    private void setupTrayIcon() {
        if (!de.tki.comfymodels.util.PlatformUtils.isSystemTraySupported()) {
            System.err.println("[System-Tray] Not supported on this platform (e.g. Wayland). Background mode disabled.");
            if (backgroundCheck != null) {
                backgroundCheck.setSelected(false);
                backgroundCheck.setEnabled(false);
                backgroundCheck.setToolTipText("System Tray not supported on this OS.");
                configService.setBackgroundModeEnabled(false);
            }
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = appIcon;
        
        // Fallback if appIcon failed to load
        if (trayImage == null) {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = fallback.createGraphics();
            g2.setColor(new Color(255, 204, 0));
            g2.fillRect(0, 0, 16, 16);
            g2.dispose();
            trayImage = fallback;
        }

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show UI");
        showItem.addActionListener(e -> setVisible(true));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> performAppExit());

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(trayImage, "ComfyUI Companion", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> setVisible(true));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
    }

    private boolean promptForPassword() {
        // Read theme before applying
        setupTheme(configService.isDarkMode());

        while (true) {
            boolean vaultExists = configService.hasVault();
            String title = vaultExists ? "Vault Unlock" : "Vault Setup";
            String promptText = vaultExists ? "Enter Vault Password (to unlock API Keys):" : "Set Vault Password (to protect API Keys):";

            de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, title);
            JPanel content = dialog.createContentPanel();
            
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
            
            content.add(new JLabel(promptText), gbc);
            
            JPasswordField pf = new JPasswordField();
            gbc.gridy++;
            gbc.insets = new Insets(10, 0, 10, 0);
            content.add(pf, gbc);

            final JTextField finalField1;
            final JTextField finalField2;
            final JTextField finalField3;
            final JTextField finalField4;
            final JCheckBox finalSymlinkCheck;

            if (!vaultExists) {
                finalField1 = new JTextField(configService.getExtraComfyUIPath());
                finalField2 = new JTextField(configService.getArchivePath());
                finalField3 = new JTextField(configService.getComfyUIPath());
                finalField4 = new JTextField(configService.getPythonPath());
                finalSymlinkCheck = new JCheckBox("Use Symbolic Links on Restore (Saves SSD space)", configService.isUseSymlinksOnRestore());

                // Extra ComfyUI Path
                gbc.gridy++;
                gbc.insets = new Insets(10, 0, 5, 0);
                content.add(new JLabel("Extra ComfyUI Path (contains models, input, output):"), gbc);
                
                gbc.gridy++;
                gbc.insets = new Insets(0, 0, 5, 0);
                JPanel row1 = new JPanel(new BorderLayout(5, 0));
                JButton browse1 = new JButton("Browse...");
                browse1.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                        finalField1.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });
                row1.add(finalField1, BorderLayout.CENTER);
                row1.add(browse1, BorderLayout.EAST);
                content.add(row1, gbc);

                // Archive Path
                gbc.gridy++;
                gbc.insets = new Insets(10, 0, 5, 0);
                content.add(new JLabel("Archive Path (Offload storage):"), gbc);

                gbc.gridy++;
                gbc.insets = new Insets(0, 0, 5, 0);
                JPanel row2 = new JPanel(new BorderLayout(5, 0));
                JButton browse2 = new JButton("Browse...");
                browse2.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                        finalField2.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });
                row2.add(finalField2, BorderLayout.CENTER);
                row2.add(browse2, BorderLayout.EAST);
                content.add(row2, gbc);

                // ComfyUI Main Directory
                gbc.gridy++;
                gbc.insets = new Insets(10, 0, 5, 0);
                content.add(new JLabel("ComfyUI Main Directory (contains main.py):"), gbc);

                gbc.gridy++;
                gbc.insets = new Insets(0, 0, 5, 0);
                JPanel row3 = new JPanel(new BorderLayout(5, 0));
                JButton browse3 = new JButton("Browse...");
                row3.add(finalField3, BorderLayout.CENTER);
                row3.add(browse3, BorderLayout.EAST);
                content.add(row3, gbc);

                // Python Executable Path
                gbc.gridy++;
                gbc.insets = new Insets(10, 0, 5, 0);
                content.add(new JLabel("Python Executable Path:"), gbc);

                gbc.gridy++;
                gbc.insets = new Insets(0, 0, 5, 0);
                JPanel row4 = new JPanel(new BorderLayout(5, 0));
                JButton browse4 = new JButton("Browse...");
                row4.add(finalField4, BorderLayout.CENTER);
                row4.add(browse4, BorderLayout.EAST);
                content.add(row4, gbc);

                browse3.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                        String path = chooser.getSelectedFile().getAbsolutePath();
                        finalField3.setText(path);
                        
                        String discoveredPython = configService.discoverPython(path);
                        if (discoveredPython != null && !discoveredPython.equals("python") && !discoveredPython.equals("python3")) {
                            finalField4.setText(discoveredPython);
                        }
                    }
                });

                browse4.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                        finalField4.setText(chooser.getSelectedFile().getAbsolutePath());
                    }
                });

                // Symlink checkbox
                gbc.gridy++;
                gbc.insets = new Insets(10, 0, 5, 0);
                content.add(finalSymlinkCheck, gbc);
            } else {
                finalField1 = null;
                finalField2 = null;
                finalField3 = null;
                finalField4 = null;
                finalSymlinkCheck = null;
            }

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAction = new JButton(vaultExists ? "Unlock" : "Set Password");
            btnAction.putClientProperty("JButton.buttonType", "accent");
            JButton btnCancel = new JButton("Cancel");
            buttonPanel.add(btnAction);
            buttonPanel.add(btnCancel);
            
            final String[] resultAction = {"CANCEL"};

            if (vaultExists) {
                JButton btnReset = new JButton("Reset Vault");
                buttonPanel.add(btnReset);
                btnReset.addActionListener(e -> {
                    resultAction[0] = "RESET";
                    dialog.dispose();
                });
            }
            
            // Action to perform on unlock/set
            Runnable doAction = () -> {
                resultAction[0] = "OK";
                dialog.setVisible(false);
            };
            
            btnAction.addActionListener(e -> doAction.run());
            pf.addActionListener(e -> doAction.run()); // This handles 'Enter' key
            btnCancel.addActionListener(e -> {
                resultAction[0] = "CANCEL";
                dialog.dispose();
            });

            dialog.add(content, BorderLayout.CENTER);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            
            if (vaultExists) {
                dialog.pack();
            } else {
                dialog.setSize(600, 600);
            }
            dialog.setLocationRelativeTo(null); 
            
            SwingUtilities.invokeLater(() -> pf.requestFocusInWindow());
            dialog.setVisible(true);

            if ("CANCEL".equals(resultAction[0])) {
                return false;
            }

            if ("RESET".equals(resultAction[0])) {
                handleVaultReset();
                continue;
            }

            // If it is "OK"
            String pass = new String(pf.getPassword());
            dialog.dispose();

            if (pass.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try {
                configService.unlock(pass);
                if (!vaultExists) {
                    configService.setModelsPath(finalField1.getText().trim());
                    configService.setArchivePath(finalField2.getText().trim());
                    configService.setComfyUIPath(finalField3.getText().trim());
                    configService.setPythonPath(finalField4.getText().trim());
                    configService.setUseSymlinksOnRestore(finalSymlinkCheck.isSelected());
                }
                configService.autoDiscoverPaths();
                restBridge.setApiToken(configService.getApiToken());
                syncBridgeFiles();
                
                if (configService.isVaultFresh()) {
                    String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                    modelListService.importFromUrl(url);
                }
                
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Unlock Failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);        
            }
        }
    }

    private void handleVaultReset() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Warning: Resetting the vault will delete all your stored API keys.\n" +
            "This action cannot be undone. Do you want to proceed?",
            "Confirm Vault Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            configService.resetVault();
        }
    }

    private JDialog createDialog(String title) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout());
        return dialog;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        return panel;
    }

    private void showLifecycleDialog() {
        JDialog dialog = createDialog("ComfyUI Control");
        dialog.setSize(880, 420);
        dialog.setLocationRelativeTo(this);

        JPanel content = createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("ComfyUI Server Status");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(titleLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(25, 0, 25, 0);
        lifecycleStatusLabel = new JLabel(lifecycleService.getStatus());
        lifecycleStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        lifecycleStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(lifecycleStatusLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 20, 0);
        JPanel controlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton startBtn = new JButton("▶ Start");
        startBtn.setPreferredSize(new Dimension(110, 40));
        startBtn.addActionListener(e -> {
            configService.autoDiscoverPaths();
            startComfyAndReload();
        });
        
        JButton stopBtn = new JButton("⏹ Stop");
        stopBtn.setPreferredSize(new Dimension(110, 40));
        stopBtn.addActionListener(e -> lifecycleService.stop());
        
        JButton restartBtn = new JButton("🔄 Restart");
        restartBtn.setPreferredSize(new Dimension(110, 40));
        restartBtn.addActionListener(e -> {
            configService.autoDiscoverPaths();
            new Thread(() -> {
                lifecycleService.stop();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                startComfyAndReload();
            }).start();
        });

        JButton openBrowserBtn = new JButton("🌐 Open Interface");
        openBrowserBtn.setPreferredSize(new Dimension(150, 40));
        openBrowserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(configService.getComfyUIUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Could not open browser: " + ex.getMessage());
            }
        });
        
        controlButtons.add(startBtn);
        controlButtons.add(stopBtn);
        controlButtons.add(restartBtn);
        controlButtons.add(openBrowserBtn);
        content.add(controlButtons, gbc);

        // Timer to update status
        Timer timer = new Timer(1000, e -> {
            lifecycleStatusLabel.setText(lifecycleService.getStatus());
            if (lifecycleService.isRunning()) {
                lifecycleStatusLabel.setForeground(new Color(0, 150, 0));
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
            } else {
                lifecycleStatusLabel.setForeground(Color.RED);
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
            }
        });
        timer.start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { timer.stop(); }
        });

        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        
        bottom.add(close);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void loadSettingsIntoUI() {
        if (backgroundCheck != null) backgroundCheck.setSelected(configService.isBackgroundModeEnabled());
        if (shutdownCheck != null) shutdownCheck.setSelected(configService.isShutdownAfterDownloadEnabled());
        if (restartCheck != null) restartCheck.setSelected(configService.isRestartAfterDownloadEnabled());
        if (darkCheck != null) darkCheck.setSelected(configService.isDarkMode());
        if (fastHashCheck != null) fastHashCheck.setSelected(configService.isFastHashEnabled());
        if (hideComfyuiCheck != null) hideComfyuiCheck.setSelected(configService.isHideComfyUI());
    }

    private boolean isGeminiKeyConfigured() {
        String key = configService.getGeminiApiKey();
        return key != null && !key.trim().isEmpty();
    }

    private boolean isOllamaConfigured() {
        return configService != null && configService.isUseOllama();
    }

    private void updateAiModelDisplay() {
        new Thread(() -> {
            String model;
            if (configService.isUseOllama()) {
                model = configService.getOllamaModel() + " (Local)";
            } else {
                model = geminiService.discoverBestModel();
            }
            final String finalModel = model;
            SwingUtilities.invokeLater(() -> {
                activeAiModelLabel.setText("Active AI: " + finalModel);
                if (btnOptimizePrompt != null) {
                    if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
                        btnOptimizePrompt.setToolTipText("Download local Gemma model to unlock local optimization.");
                    } else {
                        btnOptimizePrompt.setToolTipText("Optimizes the assembled prompt using local Gemma for better image quality.");
                    }
                }
                if (btnSuggestSubject != null) {
                    if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
                        btnSuggestSubject.setToolTipText("Download local Gemma model to unlock suggestions.");
                    } else {
                        btnSuggestSubject.setToolTipText("Suggest creative expansions for this subject using local Gemma.");
                    }
                }
            });
        }).start();
    }

    private void initUI() {
        loadIcon();
        setTitle("ComfyUI Companion");
        setSize(1450, 950);
        setMinimumSize(new java.awt.Dimension(1200, 800));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);
        
        // Define rootPanel that paints the premium background gradient and radial glow globally
        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                
                Color start = UIManager.getColor("MainTabs.gradientStart");
                Color end = UIManager.getColor("MainTabs.gradientEnd");
                if (start == null) start = new Color(14, 15, 17);
                if (end == null) end = new Color(24, 28, 38);
                GradientPaint bgGrad = new GradientPaint(0, 0, start, 0, h, end);
                g2.setPaint(bgGrad);
                g2.fillRect(0, 0, w, h);
                
                Color glowStart = UIManager.getColor("MainTabs.glowStart");
                if (glowStart == null) glowStart = new Color(0, 240, 255, 12);
                float[] dist = {0.0f, 1.0f};
                Color[] colors = {glowStart, new Color(0, 0, 0, 0)};
                RadialGradientPaint glow = new RadialGradientPaint(
                    new java.awt.geom.Point2D.Float(w * 0.85f, h * 0.15f),
                    Math.max(w, h) * 0.45f,
                    dist,
                    colors
                );
                g2.setPaint(glow);
                g2.fillRect(0, 0, w, h);
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(true);

        // Header Panel with glassmorphism styling
        JPanel headerPanel = new JPanel(new BorderLayout(15, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                
                boolean dark = configService.isDarkMode();
                Color bgColor = dark ? new Color(25, 30, 40, 100) : new Color(255, 255, 255, 120);
                g2.setColor(bgColor);
                g2.fillRect(0, 0, w, h);
                
                g2.setColor(dark ? new Color(0, 255, 204, 40) : new Color(0, 120, 150, 30));
                g2.fillRect(0, h - 1, w, 1);
                
                g2.dispose();
            }
        };
        headerPanel.setOpaque(false);
        headerPanel.setPreferredSize(new Dimension(0, 60));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftHeader.setOpaque(false);
        JLabel logoLabel = new JLabel("🌀");
        logoLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        JLabel titleLabel = new JLabel("ComfyUI Companion") {
            @Override
            public void updateUI() {
                super.updateUI();
                if (configService != null) {
                    setForeground(configService.isDarkMode() ? new Color(0, 255, 204) : new Color(0, 102, 204));
                }
            }
        };
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(configService.isDarkMode() ? new Color(0, 255, 204) : new Color(0, 102, 204));
        
        JLabel tagline = new JLabel("|  Unified AI Model Manager & Prompt Lab");
        tagline.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tagline.setForeground(Color.GRAY);
        
        leftHeader.add(logoLabel);
        leftHeader.add(titleLabel);
        leftHeader.add(tagline);
        headerPanel.add(leftHeader, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        rightHeader.setOpaque(false);
        
        JLabel activeProfileLabel = new JLabel("👤 Profile: Loading...");
        activeProfileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        activeProfileLabel.setForeground(Color.GRAY);
        
        JLabel globalStatusIndicator = new JLabel("Server: Offline 🔴");
        globalStatusIndicator.setFont(new Font("SansSerif", Font.BOLD, 12));
        globalStatusIndicator.setForeground(Color.GRAY);
        
        JButton quickActionBtn = new JButton("▶ Start");
        quickActionBtn.putClientProperty("JButton.buttonType", "roundRect");
        quickActionBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        quickActionBtn.setFocusable(false);
        
        quickActionBtn.addActionListener(e -> {
            boolean running = processController.isRunning();
            if (running) {
                int confirm = JOptionPane.showConfirmDialog(this, "Stop ComfyUI server?", "Stop Server", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    processController.stop();
                }
            } else {
                String activeIdVal = configService.getActiveProfile();
                List<de.tki.comfymodels.domain.LaunchProfile> profilesVal = profileManager.loadProfiles();
                de.tki.comfymodels.domain.LaunchProfile activeProfileVal = profilesVal.stream()
                    .filter(p -> p.id().equals(activeIdVal))
                    .findFirst()
                    .orElse(null);
                if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);
                
                if (activeProfileVal != null) {
                    startComfyUI(activeProfileVal, false, !configService.isHideComfyUI());
                } else {
                    JOptionPane.showMessageDialog(this, "No launch profile available to start ComfyUI.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        rightHeader.add(activeProfileLabel);
        rightHeader.add(globalStatusIndicator);
        rightHeader.add(quickActionBtn);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        Timer headerTimer = new Timer(1000, e -> {
            boolean running = processController.isRunning();
            boolean starting = false;
            if (running) {
                if (processController.isProcessAlive() && !processController.isGuiLineShown()) {
                    starting = true;
                } else if (lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                    starting = true;
                }
            }
            
            if (running) {
                if (starting) {
                    globalStatusIndicator.setText("Server: Starting 🟡");
                    globalStatusIndicator.setForeground(new Color(255, 204, 0));
                    quickActionBtn.setText("⏹ Stop");
                } else {
                    globalStatusIndicator.setText("Server: Running 🟢");
                    globalStatusIndicator.setForeground(new Color(0, 204, 150));
                    quickActionBtn.setText("⏹ Stop");
                }
            } else {
                globalStatusIndicator.setText("Server: Offline 🔴");
                globalStatusIndicator.setForeground(Color.GRAY);
                quickActionBtn.setText("▶ Start");
            }
            
            // Sync active profile name
            String activeIdVal = configService.getActiveProfile();
            List<de.tki.comfymodels.domain.LaunchProfile> profilesVal = profileManager.loadProfiles();
            de.tki.comfymodels.domain.LaunchProfile activeProfileVal = profilesVal.stream()
                .filter(p -> p.id().equals(activeIdVal))
                .findFirst()
                .orElse(null);
            if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);
            String pName = (activeProfileVal != null) ? activeProfileVal.name() : "None";
            activeProfileLabel.setText("👤 Profile: " + pName);
        });
        headerTimer.start();
        
        this.mainTabs = new JTabbedPane();
        mainTabs.setOpaque(false);
        mainTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        mainTabs.putClientProperty("JTabbedPane.tabType", "card");
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);
        mainTabs.putClientProperty("JTabbedPane.tabSeparatorsFullHeight", true);

        // TAB 1: DASHBOARD
        mainTabs.addTab("🏠 Dashboard", createDashboardPanel(mainTabs));

        // TAB 2: MODEL MANAGER
        mainTabs.addTab("📥 Model Manager", createManagerPanel(mainTabs));

        // TAB 3: PROMPT LAB
        mainTabs.addTab("🔬 Prompt Lab", createPromptLabPanel());

        // TAB 4: GALLERY
        mainTabs.addTab("🖼️ Gallery", new de.tki.comfymodels.ui.OutputGalleryPanel(configService));

        // TAB 5: SETTINGS
        mainTabs.addTab("⚙️ Settings", createSettingsPanel());

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(mainTabs, BorderLayout.CENTER);
        setContentPane(rootPanel);
    }

    private static final String DEFAULT_PROMPT_JSON = "{\n" +
            "  \"prompt\": {\n" +
            "    \"3\": {\n" +
            "      \"inputs\": {\n" +
            "        \"seed\": 42,\n" +
            "        \"steps\": 20,\n" +
            "        \"cfg\": 8.0,\n" +
            "        \"sampler_name\": \"euler\",\n" +
            "        \"scheduler\": \"normal\",\n" +
            "        \"denoise\": 1.0,\n" +
            "        \"model\": [\n" +
            "          \"4\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"positive\": [\n" +
            "          \"6\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"negative\": [\n" +
            "          \"7\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"latent_image\": [\n" +
            "          \"5\",\n" +
            "          0\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"KSampler\"\n" +
            "    },\n" +
            "    \"4\": {\n" +
            "      \"inputs\": {\n" +
            "        \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
            "      },\n" +
            "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
            "    },\n" +
            "    \"5\": {\n" +
            "      \"inputs\": {\n" +
            "        \"width\": 512,\n" +
            "        \"height\": 512,\n" +
            "        \"batch_size\": 1\n" +
            "      },\n" +
            "      \"class_type\": \"EmptyLatentImage\"\n" +
            "    },\n" +
            "    \"6\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"cybernetic tiger, neon-lit alley, photorealistic, 8k, unreal engine 5 render\",\n" +
            "        \"clip\": [\n" +
            "          \"4\",\n" +
            "          1\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"7\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"bad hands, text, blurry, worst quality, low quality\",\n" +
            "        \"clip\": [\n" +
            "          \"4\",\n" +
            "          1\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"8\": {\n" +
            "      \"inputs\": {\n" +
            "        \"samples\": [\n" +
            "          \"3\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"vae\": [\n" +
            "          \"4\",\n" +
            "          2\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"VAEDecode\"\n" +
            "    },\n" +
            "    \"9\": {\n" +
            "      \"inputs\": {\n" +
            "        \"filename_prefix\": \"ComfyUI\",\n" +
            "        \"images\": [\n" +
            "          \"8\",\n" +
            "          0\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"SaveImage\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    private JPanel createPromptLabPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Prompt builder controls (Was?, Wo?, Wie?)
        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header Title
        JLabel titleLabel = new JLabel("🔬 Prompt Lab");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        titleLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(titleLabel);
        leftPanel.add(Box.createVerticalStrut(15));

        // Step 1: Subject Definition (Was?)
        JLabel lblSubject = new JLabel("1. Subject Definition (What?)");
        lblSubject.putClientProperty("FlatLaf.styleClass", "h4");
        lblSubject.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSubject);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel subjectRow = new JPanel(new BorderLayout(8, 0));
        subjectRow.setOpaque(false);
        subjectRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        subjectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        promptSubjectField = new JTextField("cybernetic tiger");
        promptSubjectField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subjectRow.add(promptSubjectField, BorderLayout.CENTER);

        btnSuggestSubject = new JButton("✨ Suggest");
        btnSuggestSubject.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSuggestSubject.putClientProperty("Button.background", new Color(138, 43, 226)); // Purple accent for AI
        btnSuggestSubject.putClientProperty("Button.foreground", Color.WHITE);
        if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
            btnSuggestSubject.setToolTipText("Download local Gemma model to unlock suggestions.");
        } else {
            btnSuggestSubject.setToolTipText("Suggest creative expansions for this subject using local Gemma.");
        }
        btnSuggestSubject.addActionListener(e -> suggestSubjectCompletions());
        subjectRow.add(btnSuggestSubject, BorderLayout.EAST);

        leftPanel.add(subjectRow);
        leftPanel.add(Box.createVerticalStrut(5));

        // Hidden suggestions container
        promptSubjectSuggestionsWrapper = new JPanel(new BorderLayout(5, 5));
        promptSubjectSuggestionsWrapper.setOpaque(false);
        promptSubjectSuggestionsWrapper.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptSubjectSuggestionsWrapper.setVisible(false);
        promptSubjectSuggestionsWrapper.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel title = new JLabel("Gemma Suggestions:");
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(Color.GRAY);
        headerPanel.add(title, BorderLayout.CENTER);
        
        JButton btnClose = new JButton("✕");
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnClose.addActionListener(e -> {
            promptSubjectSuggestionsWrapper.setVisible(false);
            promptLabLeftPanel.revalidate();
            promptLabLeftPanel.repaint();
        });
        headerPanel.add(btnClose, BorderLayout.EAST);
        
        promptSubjectSuggestionsWrapper.add(headerPanel, BorderLayout.NORTH);

        promptSubjectSuggestionsPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        promptSubjectSuggestionsPanel.setOpaque(false);

        promptSubjectSuggestionsWrapper.add(promptSubjectSuggestionsPanel, BorderLayout.CENTER);
        leftPanel.add(promptSubjectSuggestionsWrapper);

        promptLabLeftPanel = leftPanel;
        leftPanel.add(Box.createVerticalStrut(15));

        // Step 2: Umgebungs-Kontext (Wo?)
        JLabel lblEnv = new JLabel("2. Environmental Context (Where?)");
        lblEnv.putClientProperty("FlatLaf.styleClass", "h4");
        lblEnv.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblEnv);
        leftPanel.add(Box.createVerticalStrut(5));

        DropdownItem[] envs = {
            new DropdownItem("None (Optional)", ""),
            new DropdownItem("Neon-lit alley", "neon-lit alley"),
            new DropdownItem("Cyberpunk laboratory", "cyberpunk laboratory"),
            new DropdownItem("Steaming jungle", "steaming jungle"),
            new DropdownItem("Ancient temple ruins", "ancient temple ruins"),
            new DropdownItem("Misty mountaintop", "misty mountaintop"),
            new DropdownItem("Futuristic space station", "futuristic space station"),
            new DropdownItem("Cozy library with fireplace", "cozy library with fireplace"),
            new DropdownItem("Sun-drenched beach", "sun-drenched beach"),
            new DropdownItem("Surreal dreamscape", "surreal dreamscape"),
            new DropdownItem("Ruined post-apocalyptic city", "ruined post-apocalyptic city")
        };
        promptEnvCombo = new JComboBox<>(envs);
        promptEnvCombo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        promptEnvCombo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptEnvCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        leftPanel.add(promptEnvCombo);
        leftPanel.add(Box.createVerticalStrut(15));

        // Step 3: Stil-Filter (Wie?)
        JLabel lblStyles = new JLabel("3. Style Filter (How?)");
        lblStyles.putClientProperty("FlatLaf.styleClass", "h4");
        lblStyles.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblStyles);
        leftPanel.add(Box.createVerticalStrut(8));

        JPanel stylesPanel = new JPanel(new GridLayout(0, 2, 8, 8));
        stylesPanel.setOpaque(false);
        stylesPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        
        chkPhotorealistic = new JCheckBox("Photorealistic", true);
        chkOil = new JCheckBox("Oil painting", false);
        chkEngine = new JCheckBox("Unreal Engine 5 Render", true);
        chkAnime = new JCheckBox("Anime Style", false);
        chkFantasy = new JCheckBox("Dark Fantasy / Cinematic", false);
        chkSketch = new JCheckBox("Watercolor Sketch", false);

        stylesPanel.add(chkPhotorealistic);
        stylesPanel.add(chkOil);
        stylesPanel.add(chkEngine);
        stylesPanel.add(chkAnime);
        stylesPanel.add(chkFantasy);
        stylesPanel.add(chkSketch);
        leftPanel.add(stylesPanel);
        leftPanel.add(Box.createVerticalStrut(20));

        // Step 4: Model & Size Settings
        JLabel lblSettings = new JLabel("4. Model & Size Settings");
        lblSettings.putClientProperty("FlatLaf.styleClass", "h4");
        lblSettings.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSettings);
        leftPanel.add(Box.createVerticalStrut(5));

        // Model Selection row with refresh button
        JPanel modelRow = new JPanel(new BorderLayout(5, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        promptModelCombo = new JComboBox<>(new String[]{"v1-5-pruned-emaonly.safetensors"});
        promptModelCombo.setEditable(true);
        promptModelCombo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        modelRow.add(promptModelCombo, BorderLayout.CENTER);

        JButton btnRefreshModels = new JButton("🔄");
        btnRefreshModels.setToolTipText("Refresh models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> refreshPromptLabModels());
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        leftPanel.add(modelRow);
        leftPanel.add(Box.createVerticalStrut(10));

        // Model capability label
        promptPresetLabel = new JLabel("Detected Preset: Stable Diffusion 1.5 (SD 1.5)") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("PromptLab.presetForeground");
                if (c != null) {
                    setForeground(c);
                }
            }
        };
        promptPresetLabel.setFont(new Font("SansSerif", Font.ITALIC | Font.BOLD, 12));
        Color pc = UIManager.getColor("PromptLab.presetForeground");
        if (pc != null) promptPresetLabel.setForeground(pc);
        promptPresetLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(promptPresetLabel);
        leftPanel.add(Box.createVerticalStrut(10));

        // Size Inputs (Width & Height)
        JPanel sizeRow = new JPanel(new GridLayout(1, 2, 10, 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel widthPanel = new JPanel(new BorderLayout(0, 2));
        widthPanel.setOpaque(false);
        JLabel lblWidth = new JLabel("Width");
        lblWidth.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptWidthSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        widthPanel.add(lblWidth, BorderLayout.NORTH);
        widthPanel.add(promptWidthSpinner, BorderLayout.CENTER);

        JPanel heightPanel = new JPanel(new BorderLayout(0, 2));
        heightPanel.setOpaque(false);
        JLabel lblHeight = new JLabel("Height");
        lblHeight.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptHeightSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        heightPanel.add(lblHeight, BorderLayout.NORTH);
        heightPanel.add(promptHeightSpinner, BorderLayout.CENTER);

        sizeRow.add(widthPanel);
        sizeRow.add(heightPanel);

        leftPanel.add(sizeRow);
        leftPanel.add(Box.createVerticalStrut(10));

        // Sampler Settings: Steps & CFG
        JPanel samplerRow = new JPanel(new GridLayout(1, 2, 10, 0));
        samplerRow.setOpaque(false);
        samplerRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        samplerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel stepsPanel = new JPanel(new BorderLayout(0, 2));
        stepsPanel.setOpaque(false);
        JLabel lblSteps = new JLabel("Steps");
        lblSteps.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptStepsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        stepsPanel.add(lblSteps, BorderLayout.NORTH);
        stepsPanel.add(promptStepsSpinner, BorderLayout.CENTER);

        JPanel cfgPanel = new JPanel(new BorderLayout(0, 2));
        cfgPanel.setOpaque(false);
        JLabel lblCfg = new JLabel("CFG Scale");
        lblCfg.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptCfgSpinner = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 30.0, 0.5));
        cfgPanel.add(lblCfg, BorderLayout.NORTH);
        cfgPanel.add(promptCfgSpinner, BorderLayout.CENTER);

        samplerRow.add(stepsPanel);
        samplerRow.add(cfgPanel);

        leftPanel.add(samplerRow);
        leftPanel.add(Box.createVerticalStrut(20));

        // Step 5: Assembler (Die "Antigravity"-Logik)
        JLabel lblAssembled = new JLabel("5. Assembler (The \"Antigravity\" logic)");
        lblAssembled.putClientProperty("FlatLaf.styleClass", "h4");
        lblAssembled.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblAssembled);
        leftPanel.add(Box.createVerticalStrut(5));

        promptAssembleArea = new JTextArea(3, 20);
        promptAssembleArea.setLineWrap(true);
        promptAssembleArea.setWrapStyleWord(true);
        promptAssembleArea.setFont(new Font("SansSerif", Font.BOLD, 14));
        promptAssembleArea.setEditable(false);
        promptAssembleArea.putClientProperty("FlatLaf.style", "background: $TextField.background");
        
        JScrollPane assembleScroll = new JScrollPane(promptAssembleArea);
        assembleScroll.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        assembleScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        leftPanel.add(assembleScroll);
        leftPanel.add(Box.createVerticalStrut(10));

        btnOptimizePrompt = new JButton("✨ Optimize with gemma");
        btnOptimizePrompt.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnOptimizePrompt.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        btnOptimizePrompt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnOptimizePrompt.putClientProperty("Button.background", new Color(58, 117, 196));
        btnOptimizePrompt.putClientProperty("Button.foreground", Color.WHITE);
        btnOptimizePrompt.addActionListener(e -> optimizePromptWithGemma());

        if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
            btnOptimizePrompt.setToolTipText("Download local Gemma model to unlock local optimization.");
        } else {
            btnOptimizePrompt.setToolTipText("Optimizes the assembled prompt using local Gemma for better image quality.");
        }
        leftPanel.add(btnOptimizePrompt);

        leftPanel.add(Box.createVerticalGlue());

        // Document / Item Listeners to update prompt instantly
        promptSubjectField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePromptLabJson(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePromptLabJson(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePromptLabJson(); }
        });
        promptEnvCombo.addActionListener(e -> updatePromptLabJson());
        ActionListener styleChanger = e -> updatePromptLabJson();
        chkPhotorealistic.addActionListener(styleChanger);
        chkOil.addActionListener(styleChanger);
        chkEngine.addActionListener(styleChanger);
        chkAnime.addActionListener(styleChanger);
        chkFantasy.addActionListener(styleChanger);
        chkSketch.addActionListener(styleChanger);

        promptModelCombo.addActionListener(e -> {
            String selected = (String) promptModelCombo.getSelectedItem();
            applyModelPreset(selected);
            updatePromptLabJson();
        });
        promptWidthSpinner.addChangeListener(e -> updatePromptLabJson());
        promptHeightSpinner.addChangeListener(e -> updatePromptLabJson());
        promptStepsSpinner.addChangeListener(e -> updatePromptLabJson());
        promptCfgSpinner.addChangeListener(e -> updatePromptLabJson());
        
        // Initial load of models in background
        refreshPromptLabModels();


        // RIGHT: ComfyUI integration / API payload view
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setOpaque(false);
        rightPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        rightPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Top controls of right panel
        JPanel rightTopPanel = new JPanel(new BorderLayout());
        rightTopPanel.setOpaque(false);
        rightTopPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel apiHeader = new JLabel("ComfyUI API Integration (workflow_api.json)");
        apiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightTopPanel.add(apiHeader, BorderLayout.CENTER);

        rightPanel.add(rightTopPanel, BorderLayout.NORTH);

        // Center JSON/Image area with tabbed pane
        promptLabRightTabbedPane = new JTabbedPane();
        promptLabRightTabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        promptLabRightTabbedPane.setOpaque(false);

        // TAB A: Image Preview
        JPanel imagePreviewTabPanel = new JPanel(new BorderLayout(10, 10));
        imagePreviewTabPanel.setOpaque(false);

        promptImagePreviewLabel = new JLabel("Your generated image will appear here", SwingConstants.CENTER);
        promptImagePreviewLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        promptImagePreviewLabel.setForeground(Color.GRAY);
        promptImagePreviewLabel.putClientProperty("FlatLaf.style", "background: $TextField.background");
        promptImagePreviewLabel.setOpaque(true);
        
        promptImagePreviewLabel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (currentPreviewImage != null) {
                    scaleAndSetImage(currentPreviewImage);
                }
            }
        });

        JScrollPane previewScroll = new JScrollPane(promptImagePreviewLabel);
        previewScroll.setOpaque(false);
        previewScroll.getViewport().setOpaque(false);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        imagePreviewTabPanel.add(previewScroll, BorderLayout.CENTER);

        // Progress bar inside preview panel bottom
        promptLabProgressBar = new JProgressBar();
        promptLabProgressBar.setStringPainted(true);
        promptLabProgressBar.setVisible(false);
        promptLabProgressBar.setPreferredSize(new Dimension(0, 25));
        imagePreviewTabPanel.add(promptLabProgressBar, BorderLayout.SOUTH);

        promptLabRightTabbedPane.addTab("🖼️ Image Preview", imagePreviewTabPanel);

        // TAB B: JSON Workflow
        promptJsonArea = new JTextArea(DEFAULT_PROMPT_JSON);
        promptJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane jsonScroll = new JScrollPane(promptJsonArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        promptLabRightTabbedPane.addTab("📝 JSON Workflow", jsonScroll);

        rightPanel.add(promptLabRightTabbedPane, BorderLayout.CENTER);

        // Bottom panel for console/status and send action
        JPanel rightBottomPanel = new JPanel(new BorderLayout(5, 5));
        rightBottomPanel.setOpaque(false);

        promptLabConsole = new JTextArea(4, 20);
        promptLabConsole.setBackground(configService.isDarkMode() ? new Color(25, 25, 25) : new Color(245, 247, 250));
        promptLabConsole.setForeground(configService.isDarkMode() ? new Color(0, 220, 0) : new Color(30, 30, 30));
        promptLabConsole.setFont(new Font("Monospaced", Font.PLAIN, 12));
        promptLabConsole.setEditable(false);
        promptLabConsole.setText("System: Prompt Lab ready.\n");
        JScrollPane consoleScroll = new JScrollPane(promptLabConsole);
        consoleScroll.setOpaque(false);
        consoleScroll.getViewport().setOpaque(false);
        consoleScroll.setBorder(BorderFactory.createEmptyBorder());
        rightBottomPanel.add(consoleScroll, BorderLayout.CENTER);

        btnSendToComfy = new JButton("🚀 Send to ComfyUI");
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSendToComfy.putClientProperty("Button.background", new Color(255, 204, 0));
        btnSendToComfy.putClientProperty("Button.foreground", Color.BLACK);
        btnSendToComfy.setPreferredSize(new Dimension(0, 45));
        btnSendToComfy.addActionListener(e -> sendPromptToComfyUI());
        
        rightBottomPanel.add(btnSendToComfy, BorderLayout.SOUTH);
        rightPanel.add(rightBottomPanel, BorderLayout.SOUTH);

        // Split panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.3);

        panel.add(splitPane, BorderLayout.CENTER);

        // Load persisted session inputs
        loadPromptLabSession();

        // Run initial update to align values
        updatePromptLabJson();

        return panel;
    }

    private void applyModelPreset(String modelName) {
        if (modelName == null || modelName.isEmpty()) return;
        
        String lower = modelName.toLowerCase();
        if (lower.contains("flux1-schnell") || lower.contains("flux_schnell") || lower.contains("schnell")) {
            promptPresetLabel.setText("Detected Preset: FLUX.1 Schnell (Fast 4-Step)");
            promptWidthSpinner.setValue(1024);
            promptHeightSpinner.setValue(1024);
            promptStepsSpinner.setValue(4);
            promptCfgSpinner.setValue(1.0);
        } else if (lower.contains("flux")) {
            promptPresetLabel.setText("Detected Preset: FLUX.1 (High Quality)");
            promptWidthSpinner.setValue(1024);
            promptHeightSpinner.setValue(1024);
            promptStepsSpinner.setValue(20);
            promptCfgSpinner.setValue(1.0);
        } else if (lower.contains("xl") || lower.contains("juggernaut") || lower.contains("pony")) {
            promptPresetLabel.setText("Detected Preset: Stable Diffusion XL (SDXL)");
            promptWidthSpinner.setValue(1024);
            promptHeightSpinner.setValue(1024);
            promptStepsSpinner.setValue(30);
            promptCfgSpinner.setValue(6.0);
        } else if (lower.contains("ltx")) {
            promptPresetLabel.setText("Detected Preset: LTX-Video / Image Transformer");
            promptWidthSpinner.setValue(768);
            promptHeightSpinner.setValue(512);
            promptStepsSpinner.setValue(20);
            promptCfgSpinner.setValue(3.0);
        } else if (lower.contains("hunyuan")) {
            promptPresetLabel.setText("Detected Preset: Hunyuan 3D / Video");
            promptWidthSpinner.setValue(1024);
            promptHeightSpinner.setValue(1024);
            promptStepsSpinner.setValue(30);
            promptCfgSpinner.setValue(5.0);
        } else if (lower.contains("turbo")) {
            promptPresetLabel.setText("Detected Preset: SD Turbo / fast inference");
            promptWidthSpinner.setValue(512);
            promptHeightSpinner.setValue(512);
            promptStepsSpinner.setValue(8);
            promptCfgSpinner.setValue(1.5);
        } else {
            promptPresetLabel.setText("Detected Preset: Stable Diffusion 1.5 (SD 1.5)");
            promptWidthSpinner.setValue(512);
            promptHeightSpinner.setValue(512);
            promptStepsSpinner.setValue(20);
            promptCfgSpinner.setValue(7.0);
        }
    }

    private String findExactUnetName(String selectedModel) {
        if (selectedModel == null) return "";
        for (String unet : comfyUnetModels) {
            if (modelsMatch(selectedModel, unet)) {
                return unet;
            }
        }
        String clean = selectedModel.replace("\\", "/");
        if (clean.startsWith("diffusion_models/")) {
            clean = clean.substring(17);
        }
        return clean;
    }

    private String findExactCheckpointName(String selectedModel) {
        if (selectedModel == null) return "";
        for (String ckpt : comfyCheckpoints) {
            if (modelsMatch(selectedModel, ckpt)) {
                return ckpt;
            }
        }
        String clean = selectedModel.replace("\\", "/");
        if (clean.startsWith("checkpoints/")) {
            clean = clean.substring(12);
        }
        return clean;
    }

    private String resolveClipType(String clipModel, String selectedModel) {
        if (clipModel == null) return "stable_diffusion";
        String lower = clipModel.toLowerCase();
        String candidate = "stable_diffusion";
        
        if (lower.contains("qwen_3_4b") || lower.contains("lumina2") || lower.contains("lumina-2")) {
            candidate = "lumina2";
        } else if (lower.contains("wan") || lower.contains("qwen_2.5_vl")) {
            candidate = "wan";
        } else if (lower.contains("flux")) {
            candidate = "flux";
        } else if (lower.contains("gemma")) {
            candidate = "lumina2";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            candidate = "sd3";
        } else if (lower.contains("mochi")) {
            candidate = "mochi";
        } else if (lower.contains("ltxv")) {
            candidate = "ltxv";
        } else if (lower.contains("cosmos")) {
            candidate = "cosmos";
        }
        
        // Contextual overrides based on the selected model
        if (selectedModel != null) {
            String selLower = selectedModel.toLowerCase();
            if (selLower.contains("flux") || selLower.contains("schnell")) {
                if ("stable_diffusion".equals(candidate)) {
                    candidate = "flux";
                }
            } else if (selLower.contains("sd3") || selLower.contains("stable_diffusion_3")) {
                if ("stable_diffusion".equals(candidate)) {
                    candidate = "sd3";
                }
            }
        }
        
        if (comfyClipTypes.contains(candidate)) {
            return candidate;
        }
        if (comfyClipTypes.contains("stable_diffusion")) {
            return "stable_diffusion";
        }
        if (!comfyClipTypes.isEmpty()) {
            return comfyClipTypes.iterator().next();
        }
        return candidate;
    }

    private boolean modelsMatch(String modelA, String modelB) {
        if (modelA == null || modelB == null) return false;
        String a = modelA.replace("\\", "/").toLowerCase();
        String b = modelB.replace("\\", "/").toLowerCase();
        
        if (a.startsWith("checkpoints/")) a = a.substring(12);
        if (a.startsWith("diffusion_models/")) a = a.substring(17);
        if (a.startsWith("unet/")) a = a.substring(5);
        
        if (b.startsWith("checkpoints/")) b = b.substring(12);
        if (b.startsWith("diffusion_models/")) b = b.substring(17);
        if (b.startsWith("unet/")) b = b.substring(5);
        
        for (String ext : new String[]{".safetensors", ".ckpt", ".pt", ".bin"}) {
            if (a.endsWith(ext)) a = a.substring(0, a.length() - ext.length());
            if (b.endsWith(ext)) b = b.substring(0, b.length() - ext.length());
        }
        
        return a.equals(b);
    }

    private boolean isDiffusionModel(String modelName) {
        if (modelName == null) return false;
        String clean = modelName.replace("\\", "/");
        if (clean.startsWith("diffusion_models/")) {
            clean = clean.substring(17);
        }
        for (String unet : comfyUnetModels) {
            if (modelsMatch(modelName, unet)) {
                return true;
            }
        }
        String lower = modelName.toLowerCase();
        return lower.contains("diffusion_models") || 
               lower.contains("z_image_turbo") || 
               lower.contains("acestep") || 
               lower.contains("flux-2-klein") || 
               lower.contains("longcat") || 
               lower.contains("wan2.1") || 
               lower.contains("wan2.2");
    }

    private String resolveClipForModel(String modelName) {
        String lower = modelName.toLowerCase();
        String expected = "qwen_3_4b.safetensors";
        if (lower.contains("z_image_turbo") || lower.contains("acestep") || lower.contains("longcat")) {
            expected = "qwen_3_4b.safetensors";
        } else if (lower.contains("wan")) {
            expected = "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors";
        } else if (lower.contains("flux") || lower.contains("schnell")) {
            expected = "t5xxl_fp8_e4m3fn.safetensors";
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                if (clip.toLowerCase().contains("flux")) {
                    return clip;
                }
            }
        }
        
        String expectedClean = expected.replace("\\", "/");
        for (String clip : comfyClips) {
            String clipClean = clip.replace("\\", "/");
            if (clipClean.equalsIgnoreCase(expectedClean) || clipClean.endsWith("/" + expectedClean)) {
                return clip;
            }
        }
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        for (String clip : comfyClips) {
            String clipClean = clip.replace("\\", "/");
            if (clipClean.equalsIgnoreCase(expectedName) || clipClean.endsWith("/" + expectedName)) {
                return clip;
            }
        }
        if (expectedName.contains("qwen")) {
            for (String clip : comfyClips) {
                if (clip.toLowerCase().contains("qwen")) {
                    return clip;
                }
            }
        }
        
        // Strict fallback logic: only return a clip from comfyClips if its resolved type matches the expected type
        String expectedType = resolveClipType(expected, modelName);
        for (String clip : comfyClips) {
            if (resolveClipType(clip, modelName).equals(expectedType)) {
                return clip;
            }
        }
        return expected;
    }

    private String resolveVaeForModel(String modelName) {
        String lower = modelName.toLowerCase();
        String expected = "FLUX1/ae.safetensors";
        if (lower.contains("z_image_turbo") || lower.contains("acestep") || lower.contains("longcat")) {
            expected = "FLUX1/ae.safetensors";
        } else if (lower.contains("wan")) {
            expected = "wan_2.1_vae.safetensors";
        }
        
        String expectedClean = expected.replace("\\", "/");
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedClean) || vaeClean.endsWith("/" + expectedClean)) {
                return vae;
            }
        }
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedName) || vaeClean.endsWith("/" + expectedName)) {
                return vae;
            }
        }
        boolean isExpectedAeOrFlux = expectedName.contains("flux") || expectedName.replace("vae", "").contains("ae");
        if (isExpectedAeOrFlux) {
            for (String vae : comfyVaes) {
                String vaeLower = vae.toLowerCase();
                if (vaeLower.contains("flux") || vaeLower.replace("vae", "").contains("ae")) {
                    return vae;
                }
            }
        }
        // Only fall back to first VAE if it matches compatibility
        if (!comfyVaes.isEmpty()) {
            String candidate = comfyVaes.iterator().next();
            String cLower = candidate.toLowerCase();
            if (expectedName.contains("wan") && cLower.contains("wan")) {
                return candidate;
            }
            if (isExpectedAeOrFlux && (cLower.contains("flux") || cLower.replace("vae", "").contains("ae"))) {
                return candidate;
            }
        }
        return expected;
    }

    private void adaptWorkflowJsonForModel(JSONObject promptObj, String selectedModel) {
        if (promptObj == null || selectedModel == null) return;
        
        // Generic fallback for z_image_turbo_bf16.safetensors to avoid Bad Requests
        if (selectedModel.toLowerCase().contains("z_image_turbo")) {
            String fallback = null;
            for (String u : comfyUnetModels) {
                String ul = u.toLowerCase();
                if (ul.contains("flux") || ul.contains("schnell") || ul.contains("dev")) {
                    fallback = u;
                    break;
                }
            }
            if (fallback != null) {
                selectedModel = fallback;
                final String finalFallback = fallback;
                SwingUtilities.invokeLater(() -> {
                    if (promptModelCombo != null) {
                        promptModelCombo.setSelectedItem(finalFallback);
                    }
                    if (promptLabConsole != null) {
                        promptLabConsole.append("⚠️ Generic fallback triggered for z_image_turbo to avoid RuntimeError. Using: " + finalFallback + "\n");
                    }
                });
            }
        }
        
        boolean isDiff = isDiffusionModel(selectedModel);
        
        if (isDiff) {
            // Find CheckpointLoaderSimple and convert to UNETLoader
            String targetCheckpointNodeId = null;
            for (String key : promptObj.keySet()) {
                JSONObject node = promptObj.getJSONObject(key);
                if (node.has("class_type") && "CheckpointLoaderSimple".equals(node.getString("class_type"))) {
                    targetCheckpointNodeId = key;
                    break;
                }
            }
            
            if (targetCheckpointNodeId != null) {
                JSONObject node = promptObj.getJSONObject(targetCheckpointNodeId);
                node.put("class_type", "UNETLoader");
                
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs == null) {
                    inputs = new JSONObject();
                    node.put("inputs", inputs);
                }
                inputs.remove("ckpt_name");
                
                String unetName = findExactUnetName(selectedModel);
                inputs.put("unet_name", unetName);
                
                // Add weight_dtype dynamically
                if (comfyUnetWeightDtypes != null && !comfyUnetWeightDtypes.isEmpty()) {
                    if (comfyUnetWeightDtypes.contains("default")) {
                        inputs.put("weight_dtype", "default");
                    } else {
                        inputs.put("weight_dtype", comfyUnetWeightDtypes.iterator().next());
                    }
                } else {
                    inputs.put("weight_dtype", "default");
                }
                
                // Create CLIPLoader
                String clipNodeId = targetCheckpointNodeId + "_clip";
                JSONObject clipNode = new JSONObject();
                clipNode.put("class_type", "CLIPLoader");
                JSONObject clipInputs = new JSONObject();
                String clipModel = resolveClipForModel(selectedModel);
                if (clipModel.startsWith("clip/")) clipModel = clipModel.substring(5);
                else if (clipModel.startsWith("clip\\")) clipModel = clipModel.substring(5);
                else if (clipModel.startsWith("text_encoders/")) clipModel = clipModel.substring(14);
                else if (clipModel.startsWith("text_encoders\\")) clipModel = clipModel.substring(14);
                clipInputs.put("clip_name", clipModel);
                
                String clipType = resolveClipType(clipModel, selectedModel);
                clipInputs.put("type", clipType);
                
                clipNode.put("inputs", clipInputs);
                promptObj.put(clipNodeId, clipNode);
                
                // Create VAELoader
                String vaeNodeId = targetCheckpointNodeId + "_vae";
                JSONObject vaeNode = new JSONObject();
                vaeNode.put("class_type", "VAELoader");
                JSONObject vaeInputs = new JSONObject();
                String vaeModel = resolveVaeForModel(selectedModel);
                if (vaeModel.startsWith("vae/")) vaeModel = vaeModel.substring(4);
                else if (vaeModel.startsWith("vae\\")) vaeModel = vaeModel.substring(4);
                vaeInputs.put("vae_name", vaeModel);
                vaeNode.put("inputs", vaeInputs);
                promptObj.put(vaeNodeId, vaeNode);
                
                // Redirect outputs
                for (String key : promptObj.keySet()) {
                    if (key.equals(targetCheckpointNodeId) || key.equals(clipNodeId) || key.equals(vaeNodeId)) {
                        continue;
                    }
                    JSONObject otherNode = promptObj.getJSONObject(key);
                    if (otherNode.has("inputs")) {
                        JSONObject otherInputs = otherNode.getJSONObject("inputs");
                        for (String inputKey : otherInputs.keySet()) {
                            Object val = otherInputs.get(inputKey);
                            if (val instanceof org.json.JSONArray) {
                                org.json.JSONArray link = (org.json.JSONArray) val;
                                if (link.length() == 2 && targetCheckpointNodeId.equals(link.getString(0))) {
                                    int outputIndex = link.getInt(1);
                                    if (outputIndex == 1) {
                                        link.put(0, clipNodeId);
                                        link.put(1, 0);
                                    } else if (outputIndex == 2) {
                                        link.put(0, vaeNodeId);
                                        link.put(1, 0);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                String unetName = findExactUnetName(selectedModel);
                String clipModel = resolveClipForModel(selectedModel);
                if (clipModel.startsWith("clip/")) clipModel = clipModel.substring(5);
                else if (clipModel.startsWith("clip\\")) clipModel = clipModel.substring(5);
                else if (clipModel.startsWith("text_encoders/")) clipModel = clipModel.substring(14);
                else if (clipModel.startsWith("text_encoders\\")) clipModel = clipModel.substring(14);

                String clipType = resolveClipType(clipModel, selectedModel);

                String vaeModel = resolveVaeForModel(selectedModel);
                if (vaeModel.startsWith("vae/")) vaeModel = vaeModel.substring(4);
                else if (vaeModel.startsWith("vae\\")) vaeModel = vaeModel.substring(4);

                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.getJSONObject(key);
                    if (!node.has("class_type")) continue;
                    String classType = node.getString("class_type");
                    
                    if ("UNETLoader".equals(classType)) {
                        JSONObject inputs = node.optJSONObject("inputs");
                        if (inputs != null) {
                            inputs.put("unet_name", unetName);
                            if (!inputs.has("weight_dtype")) {
                                if (comfyUnetWeightDtypes != null && !comfyUnetWeightDtypes.isEmpty()) {
                                    if (comfyUnetWeightDtypes.contains("default")) {
                                        inputs.put("weight_dtype", "default");
                                    } else {
                                        inputs.put("weight_dtype", comfyUnetWeightDtypes.iterator().next());
                                    }
                                } else {
                                    inputs.put("weight_dtype", "default");
                                }
                            }
                        }
                    } else if ("CLIPLoader".equals(classType)) {
                        JSONObject inputs = node.optJSONObject("inputs");
                        if (inputs != null) {
                            inputs.put("clip_name", clipModel);
                            inputs.put("type", clipType);
                        }
                    } else if ("DualCLIPLoader".equals(classType)) {
                        JSONObject inputs = node.optJSONObject("inputs");
                        if (inputs != null) {
                            if ("lumina2".equals(clipType) || "wan".equals(clipType) || "mochi".equals(clipType) || "ltxv".equals(clipType) || "cosmos".equals(clipType)) {
                                node.put("class_type", "CLIPLoader");
                                inputs.remove("clip_name1");
                                inputs.remove("clip_name2");
                                inputs.put("clip_name", clipModel);
                                inputs.put("type", clipType);
                            } else {
                                String clipL = "clip_l.safetensors";
                                for (String c : comfyClips) {
                                    String cl = c.toLowerCase();
                                    if (cl.contains("clip_l") || cl.contains("viit") || cl.contains("l_clip")) {
                                        clipL = c;
                                        break;
                                    }
                                }
                                if (clipL.startsWith("clip/")) clipL = clipL.substring(5);
                                else if (clipL.startsWith("clip\\")) clipL = clipL.substring(5);
                                else if (clipL.startsWith("text_encoders/")) clipL = clipL.substring(14);
                                else if (clipL.startsWith("text_encoders\\")) clipL = clipL.substring(14);

                                inputs.put("clip_name1", clipL);
                                inputs.put("clip_name2", clipModel);
                                inputs.put("type", clipType);
                            }
                        }
                    } else if ("VAELoader".equals(classType)) {
                        JSONObject inputs = node.optJSONObject("inputs");
                        if (inputs != null) {
                            inputs.put("vae_name", vaeModel);
                        }
                    }
                }
            }
        } else {
            String targetUnetNodeId = null;
            for (String key : promptObj.keySet()) {
                JSONObject node = promptObj.getJSONObject(key);
                if (node.has("class_type") && "UNETLoader".equals(node.getString("class_type"))) {
                    targetUnetNodeId = key;
                    break;
                }
            }
            
            if (targetUnetNodeId != null) {
                JSONObject node = promptObj.getJSONObject(targetUnetNodeId);
                node.put("class_type", "CheckpointLoaderSimple");
                
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs == null) {
                    inputs = new JSONObject();
                    node.put("inputs", inputs);
                }
                inputs.remove("unet_name");
                inputs.remove("weight_dtype");
                
                String ckptName = findExactCheckpointName(selectedModel);
                inputs.put("ckpt_name", ckptName);
                
                String clipNodeId = targetUnetNodeId + "_clip";
                String vaeNodeId = targetUnetNodeId + "_vae";
                
                for (String key : promptObj.keySet()) {
                    if (key.equals(targetUnetNodeId)) continue;
                    JSONObject otherNode = promptObj.getJSONObject(key);
                    if (otherNode.has("inputs")) {
                        JSONObject otherInputs = otherNode.getJSONObject("inputs");
                        for (String inputKey : otherInputs.keySet()) {
                            Object val = otherInputs.get(inputKey);
                            if (val instanceof org.json.JSONArray) {
                                org.json.JSONArray link = (org.json.JSONArray) val;
                                if (link.length() == 2) {
                                    String sourceNode = link.getString(0);
                                    if (clipNodeId.equals(sourceNode)) {
                                        link.put(0, targetUnetNodeId);
                                        link.put(1, 1);
                                    } else if (vaeNodeId.equals(sourceNode)) {
                                        link.put(0, targetUnetNodeId);
                                        link.put(1, 2);
                                    }
                                }
                            }
                        }
                    }
                }
                
                promptObj.remove(clipNodeId);
                promptObj.remove(vaeNodeId);
            } else {
                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.getJSONObject(key);
                    if (node.has("class_type") && "CheckpointLoaderSimple".equals(node.getString("class_type"))) {
                        JSONObject inputs = node.optJSONObject("inputs");
                        if (inputs != null) {
                            String ckptName = findExactCheckpointName(selectedModel);
                            inputs.put("ckpt_name", ckptName);
                        }
                    }
                }
            }
        }

        // Auto-adapt KSampler sampler and scheduler for Flux models vs others
        boolean isFlux = selectedModel.toLowerCase().contains("flux") || selectedModel.toLowerCase().contains("schnell");
        for (String key : promptObj.keySet()) {
            JSONObject nodeObj = promptObj.getJSONObject(key);
            String classType = nodeObj.optString("class_type", "");
            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                if (nodeObj.has("inputs")) {
                    JSONObject inputs = nodeObj.getJSONObject("inputs");
                    if (isFlux) {
                        inputs.put("sampler_name", "euler");
                        inputs.put("scheduler", "simple");
                    } else {
                        // Restore scheduler to normal if it was simple for SD 1.5/XL models
                        if ("simple".equals(inputs.optString("scheduler"))) {
                            inputs.put("scheduler", "normal");
                        }
                    }
                }
            }
        }
    }

    private void updatePromptLabJson() {
        if (promptSubjectField == null || promptEnvCombo == null || promptAssembleArea == null || promptJsonArea == null) {
            return;
        }
        
        String selectedModel = (promptModelCombo != null) ? (String) promptModelCombo.getSelectedItem() : null;
        Integer width = (promptWidthSpinner != null) ? (Integer) promptWidthSpinner.getValue() : null;
        Integer height = (promptHeightSpinner != null) ? (Integer) promptHeightSpinner.getValue() : null;
        Integer steps = (promptStepsSpinner != null) ? (Integer) promptStepsSpinner.getValue() : null;
        Double cfg = (promptCfgSpinner != null) ? ((Number) promptCfgSpinner.getValue()).doubleValue() : null;
        
        String subject = promptSubjectField.getText().trim();
        DropdownItem envItem = (DropdownItem) promptEnvCombo.getSelectedItem();
        String env = (envItem != null) ? envItem.getValue() : "";
        
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!subject.isEmpty()) {
            parts.add(subject);
        }
        if (!env.isEmpty()) {
            parts.add(env);
        }
        
        if (chkPhotorealistic.isSelected()) parts.add("photorealistic");
        if (chkOil.isSelected()) parts.add("oil painting");
        if (chkEngine.isSelected()) parts.add("8k, unreal engine 5 render");
        if (chkAnime.isSelected()) parts.add("anime style");
        if (chkFantasy.isSelected()) parts.add("dark fantasy, cinematic lighting");
        if (chkSketch.isSelected()) parts.add("watercolor sketch");
        
        String assembled = String.join(", ", parts);
        promptAssembleArea.setText(assembled);
        
        try {
            String currentJsonStr = promptJsonArea.getText();
            JSONObject mainObj = new JSONObject(currentJsonStr);
            if (mainObj.has("prompt")) {
                JSONObject promptObj = mainObj.getJSONObject("prompt");
                
                String targetId = null;
                for (String key : promptObj.keySet()) {
                    JSONObject nodeObj = promptObj.getJSONObject(key);
                    if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                        if (nodeObj.has("inputs")) {
                            JSONObject inputsObj = nodeObj.getJSONObject("inputs");
                            if (inputsObj.has("text")) {
                                String textVal = inputsObj.getString("text").toLowerCase();
                                if (textVal.contains("bad") || textVal.contains("blurry") || textVal.contains("low quality") || textVal.contains("worst")) {
                                    continue;
                                }
                                targetId = key;
                                break;
                            }
                        }
                    }
                }
                
                if (targetId == null) {
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                            targetId = key;
                            break;
                        }
                    }
                }
                
                if (targetId != null) {
                    JSONObject nodeObj = promptObj.getJSONObject(targetId);
                    if (nodeObj.has("inputs")) {
                        JSONObject inputsObj = nodeObj.getJSONObject("inputs");
                        inputsObj.put("text", assembled);
                    }
                }
                
                // Adapt loaders for the selected model
                if (selectedModel != null && !selectedModel.trim().isEmpty()) {
                    adaptWorkflowJsonForModel(promptObj, selectedModel.trim());
                }

                // Update Latent Image Dimensions
                if (width != null && height != null) {
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        if (nodeObj.has("class_type") && "EmptyLatentImage".equals(nodeObj.getString("class_type"))) {
                            if (nodeObj.has("inputs")) {
                                JSONObject inputs = nodeObj.getJSONObject("inputs");
                                inputs.put("width", width);
                                inputs.put("height", height);
                            }
                        }
                    }
                }

                // Update KSampler Steps & CFG
                if (steps != null && cfg != null) {
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        String classType = nodeObj.optString("class_type", "");
                        if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                            if (nodeObj.has("inputs")) {
                                JSONObject inputs = nodeObj.getJSONObject("inputs");
                                inputs.put("steps", steps);
                                inputs.put("cfg", cfg);
                            }
                        }
                    }
                }

                promptJsonArea.setText(mainObj.toString(2));
            }
        } catch (Exception ex) {
            // ignore manually modified JSON errors
        }
        savePromptLabSession();
    }

    private void updateComfyModelSets(JSONObject info) {
        comfyCheckpoints.clear();
        comfyUnetModels.clear();
        comfyClips.clear();
        comfyVaes.clear();
        comfyClipTypes.clear();
        comfyUnetWeightDtypes.clear();

        if (info.has("CheckpointLoaderSimple")) {
            JSONObject nodeInfo = info.getJSONObject("CheckpointLoaderSimple");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("ckpt_name")) {
                        Object val = required.get("ckpt_name");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyCheckpoints.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("UNETLoader")) {
            JSONObject nodeInfo = info.getJSONObject("UNETLoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("unet_name")) {
                        Object val = required.get("unet_name");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyUnetModels.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                    if (required.has("weight_dtype")) {
                        Object val = required.get("weight_dtype");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyUnetWeightDtypes.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("CLIPLoader")) {
            JSONObject nodeInfo = info.getJSONObject("CLIPLoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("clip_name")) {
                        Object val = required.get("clip_name");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyClips.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                    if (required.has("type")) {
                        Object val = required.get("type");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyClipTypes.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("VAELoader")) {
            JSONObject nodeInfo = info.getJSONObject("VAELoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("vae_name")) {
                        Object val = required.get("vae_name");
                        if (val instanceof org.json.JSONArray) {
                            org.json.JSONArray outerArray = (org.json.JSONArray) val;
                            if (outerArray.length() > 0) {
                                Object firstElement = outerArray.get(0);
                                if (firstElement instanceof org.json.JSONArray) {
                                    org.json.JSONArray options = (org.json.JSONArray) firstElement;
                                    for (int i = 0; i < options.length(); i++) {
                                        comfyVaes.add(options.getString(i));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void refreshPromptLabModels() {
        String comfyUrl = configService.getComfyUIUrl();
        new Thread(() -> {
            java.util.Set<String> apiModels = new java.util.TreeSet<>();
            
            // Query ComfyUI API status & available checkpoints/unets
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(3))
                        .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(comfyUrl + "/object_info"))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JSONObject info = new JSONObject(response.body());
                    updateComfyModelSets(info);
                    for (String ckpt : comfyCheckpoints) {
                        apiModels.add(ckpt.replace("\\", "/"));
                    }
                    for (String unet : comfyUnetModels) {
                        apiModels.add(unet.replace("\\", "/"));
                    }
                }
            } catch (Exception ex) {
                // silent fallback
            }

            java.util.List<String> list = new java.util.ArrayList<>(apiModels);
            if (list.isEmpty()) {
                list.add("v1-5-pruned-emaonly.safetensors");
            }
            
            SwingUtilities.invokeLater(() -> {
                if (promptModelCombo != null) {
                    String selected = (String) promptModelCombo.getSelectedItem();
                    promptModelCombo.removeAllItems();
                    for (String modelName : list) {
                        promptModelCombo.addItem(modelName);
                    }
                    String matched = null;
                    if (selected != null) {
                        for (String m : list) {
                            if (modelsMatch(selected, m)) {
                                matched = m;
                                break;
                            }
                        }
                    }
                    if (matched != null) {
                        promptModelCombo.setSelectedItem(matched);
                    } else if (!list.isEmpty()) {
                        promptModelCombo.setSelectedIndex(0);
                    }
                    if (promptModelCombo.getSelectedItem() != null) {
                        applyModelPreset((String) promptModelCombo.getSelectedItem());
                    }
                    promptLabConsole.append("🔄 Prompt Lab: Available ComfyUI models loaded (" + list.size() + " models fetched).\n");
                }
            });
        }).start();
    }


    private void sendPromptToComfyUI() {
        String comfyUrl = configService.getComfyUIUrl();
        String selectedModelVal = (promptModelCombo != null) ? (String) promptModelCombo.getSelectedItem() : null;
        int widthVal = (promptWidthSpinner != null) ? (int) promptWidthSpinner.getValue() : 512;
        int heightVal = (promptHeightSpinner != null) ? (int) promptHeightSpinner.getValue() : 512;
        int stepsVal = (promptStepsSpinner != null) ? (int) promptStepsSpinner.getValue() : 20;
        double cfgVal = (promptCfgSpinner != null) ? ((Number) promptCfgSpinner.getValue()).doubleValue() : 8.0;
        
        btnSendToComfy.setEnabled(false);
        promptLabConsole.append("Checking connection to ComfyUI and loading models...\n");
        
        new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(4))
                        .build();
                        
                // Step 1: Query ComfyUI status & available checkpoints
                java.net.http.HttpRequest infoRequest = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(comfyUrl + "/object_info"))
                        .GET()
                        .build();
                        
                java.net.http.HttpResponse<String> infoResponse = null;
                boolean connected = false;
                try {
                    infoResponse = client.send(infoRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (infoResponse.statusCode() == 200) {
                        connected = true;
                    }
                } catch (Exception ex) {
                    // Offline, we will try to start it
                }

                if (!connected) {
                    String activeId = configService.getActiveProfile();
                    List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    de.tki.comfymodels.domain.LaunchProfile activeProfile = profiles.stream()
                        .filter(p -> p.id().equals(activeId))
                        .findFirst()
                        .orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) {
                        activeProfile = profiles.get(0);
                    }

                    if (activeProfile != null) {
                        final de.tki.comfymodels.domain.LaunchProfile finalProfile = activeProfile;
                        SwingUtilities.invokeLater(() -> {
                            promptLabConsole.append("🔌 ComfyUI is offline. Starting ComfyUI automatically with profile: " + finalProfile.name() + "...\n");
                        });
                        startComfyUI(activeProfile, false, !configService.isHideComfyUI());
                        
                        // Wait up to 45 seconds for ComfyUI to start
                        for (int i = 1; i <= 45; i++) {
                            final int attempt = i;
                            SwingUtilities.invokeLater(() -> {
                                promptLabConsole.append("⏳ Waiting for ComfyUI to start (Attempt " + attempt + "/45)...\n");
                            });
                            try {
                                Thread.sleep(1000);
                                infoResponse = client.send(infoRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                                if (infoResponse.statusCode() == 200) {
                                    connected = true;
                                    SwingUtilities.invokeLater(() -> {
                                        promptLabConsole.append("🟢 ComfyUI is now online!\n");
                                    });
                                    break;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                if (!connected || infoResponse == null || infoResponse.statusCode() != 200) {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        promptLabConsole.append("❌ Could not connect to ComfyUI. Automatic startup failed or no profiles exist.\n\n");
                        JOptionPane.showMessageDialog(this, 
                            "Could not connect to ComfyUI. Please start ComfyUI manually.", 
                            "Connection Failed", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                // Parse checkpoints and unet models from response
                try {
                    JSONObject info = new JSONObject(infoResponse.body());
                    updateComfyModelSets(info);
                } catch (Exception ex) {
                    // JSON parsing error
                }
                
                java.util.List<String> checkpoints = new java.util.ArrayList<>(comfyCheckpoints);
                java.util.List<String> unetModels = new java.util.ArrayList<>(comfyUnetModels);
                
                if (checkpoints.isEmpty() && unetModels.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        promptLabConsole.append("❌ No checkpoints or UNet models found in ComfyUI.\n\n");
                        JOptionPane.showMessageDialog(this, 
                            "No models (checkpoints or UNets) were found in ComfyUI. Please install a model first!", 
                            "No Models Available", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }
                
                String selectedModel = selectedModelVal;
                boolean isDiff = isDiffusionModel(selectedModel);
                boolean isValid = false;
                String matchedModel = null;
                
                if (selectedModel != null && !selectedModel.trim().isEmpty()) {
                    final String sel = selectedModel;
                    if (isDiff) {
                        for (String u : unetModels) {
                            if (modelsMatch(sel, u)) {
                                isValid = true;
                                matchedModel = u;
                                break;
                            }
                        }
                    } else {
                        for (String c : checkpoints) {
                            if (modelsMatch(sel, c)) {
                                isValid = true;
                                matchedModel = c;
                                break;
                            }
                        }
                    }
                }
                
                if (isValid && matchedModel != null) {
                    selectedModel = matchedModel;
                } else {
                    if (isDiff && !unetModels.isEmpty()) {
                        selectedModel = unetModels.get(0);
                        final String fallback = selectedModel;
                        SwingUtilities.invokeLater(() -> {
                            promptModelCombo.setSelectedItem(fallback);
                            promptLabConsole.append("⚠️ Selected diffusion model not loaded. Falling back to: " + fallback + "\n");
                        });
                    } else if (!checkpoints.isEmpty()) {
                        selectedModel = checkpoints.get(0);
                        final String fallback = selectedModel;
                        SwingUtilities.invokeLater(() -> {
                            promptModelCombo.setSelectedItem(fallback);
                            promptLabConsole.append("⚠️ Selected model not loaded. Falling back to checkpoint: " + fallback + "\n");
                        });
                    }
                }
                
                final String finalModel = selectedModel;
                SwingUtilities.invokeLater(() -> {
                    promptLabConsole.append("Using model: " + finalModel + "\n");
                });
                
                // Parse and update JSON payload
                String rawJson = promptJsonArea.getText().trim();
                JSONObject mainObj = new JSONObject(rawJson);
                if (mainObj.has("prompt")) {
                    JSONObject promptObj = mainObj.getJSONObject("prompt");
                    
                    // Adapt loaders for the selected model
                    adaptWorkflowJsonForModel(promptObj, finalModel);
                    
                    // Update EmptyLatentImage width & height
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        if (nodeObj.has("class_type") && "EmptyLatentImage".equals(nodeObj.getString("class_type"))) {
                            if (nodeObj.has("inputs")) {
                                JSONObject inputs = nodeObj.getJSONObject("inputs");
                                inputs.put("width", widthVal);
                                inputs.put("height", heightVal);
                            }
                        }
                    }

                    // Update KSampler / KSamplerAdvanced steps, cfg, and randomize seed to force execution
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        String classType = nodeObj.optString("class_type", "");
                        if (nodeObj.has("inputs")) {
                            JSONObject inputs = nodeObj.getJSONObject("inputs");
                            
                            // Update steps and cfg for standard samplers
                            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                                inputs.put("steps", stepsVal);
                                inputs.put("cfg", cfgVal);
                            }
                            
                            // Randomize any seed/noise_seed field in any node (including custom samplers) to bypass ComfyUI caching
                            java.util.List<String> inputKeys = new java.util.ArrayList<>(inputs.keySet());
                            long randomSeed = Math.abs(new java.util.Random().nextLong()) % 9007199254740991L;
                            for (String inputKey : inputKeys) {
                                String lowerKey = inputKey.toLowerCase();
                                if (lowerKey.equals("seed") || lowerKey.equals("noise_seed") || lowerKey.endsWith("_seed") || lowerKey.startsWith("seed_")) {
                                    Object existingVal = inputs.get(inputKey);
                                    if (existingVal instanceof Number) {
                                        inputs.put(inputKey, randomSeed);
                                    }
                                }
                            }
                        }
                    }
                    
                    // Update positive CLIPTextEncode
                    String assembledPrompt = promptAssembleArea.getText().trim();
                    String positiveNodeId = null;
                    for (String key : promptObj.keySet()) {
                        JSONObject nodeObj = promptObj.getJSONObject(key);
                        if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                            if (nodeObj.has("inputs")) {
                                JSONObject inputs = nodeObj.getJSONObject("inputs");
                                if (inputs.has("text")) {
                                    String textVal = inputs.getString("text").toLowerCase();
                                    if (textVal.contains("bad") || textVal.contains("blurry") || textVal.contains("low quality") || textVal.contains("worst")) {
                                        continue;
                                    }
                                    positiveNodeId = key;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (positiveNodeId == null) {
                        for (String key : promptObj.keySet()) {
                            JSONObject nodeObj = promptObj.getJSONObject(key);
                            if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                                positiveNodeId = key;
                                break;
                            }
                        }
                    }
                    
                    if (positiveNodeId != null) {
                        JSONObject nodeObj = promptObj.getJSONObject(positiveNodeId);
                        if (nodeObj.has("inputs")) {
                            JSONObject inputs = nodeObj.getJSONObject("inputs");
                            inputs.put("text", assembledPrompt);
                        }
                    }
                }
                
                String updatedJsonPayload = mainObj.toString(2);
                SwingUtilities.invokeLater(() -> {
                    promptJsonArea.setText(updatedJsonPayload);
                    promptLabConsole.append("Sending prompt to ComfyUI...\n");
                });
                
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(comfyUrl + "/prompt"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(updatedJsonPayload))
                        .build();
                        
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                
                SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    if (response.statusCode() == 200) {
                        promptLabConsole.append("Successfully sent! Status Code: 200\n");
                        promptLabConsole.append("Response: " + response.body() + "\n\n");
                        
                        try {
                            JSONObject respObj = new JSONObject(response.body());
                            String promptId = respObj.optString("prompt_id");
                            if (promptId != null && !promptId.isEmpty()) {
                                startPollingPromptStatus(promptId);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to parse prompt response: " + e.getMessage());
                        }
                    } else if (response.statusCode() == 400) {
                        promptLabConsole.append("Error! Status Code: 400\n");
                        promptLabConsole.append("Response: " + response.body() + "\n\n");
                        
                        String errMsg = "ComfyUI reported a validation error (400 Bad Request).\n";
                        try {
                            JSONObject errObj = new JSONObject(response.body());
                            if (errObj.has("error")) {
                                JSONObject subErr = errObj.getJSONObject("error");
                                String type = subErr.optString("type", "");
                                String msg = subErr.optString("message", "");
                                errMsg += "\nType: " + type + "\nMessage: " + msg;
                                if (subErr.has("details")) {
                                    errMsg += "\nDetails: " + subErr.get("details").toString();
                                }
                            } else {
                                errMsg += "\nResponse content:\n" + response.body();
                            }
                        } catch (Exception ex) {
                            errMsg += "\nResponse content:\n" + response.body();
                        }
                        
                        JOptionPane.showMessageDialog(this, errMsg, "Validation Error (400)", JOptionPane.ERROR_MESSAGE);
                    } else {
                        promptLabConsole.append("Error! Status Code: " + response.statusCode() + "\n");
                        promptLabConsole.append("Response: " + response.body() + "\n\n");
                        JOptionPane.showMessageDialog(this, 
                            "ComfyUI reported an error: " + response.statusCode() + "\nResponse: " + response.body(), 
                            "API Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    promptLabConsole.append("Error: " + ex.getMessage() + "\n\n");
                    JOptionPane.showMessageDialog(this, 
                        "Connection to ComfyUI failed:\n" + ex.getMessage(), 
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void optimizePromptWithGemma() {
        if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "The local Gemma model is not downloaded.\n" +
                "Would you like to download the Gemma-2-2B GGUF model (approx. 1.6 GB) now to run optimizations directly in Java?",
                "Download Local Gemma Model?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalGemmaModel();
            }
            return;
        }
        
        String rawPrompt = promptAssembleArea.getText().trim();
        if (rawPrompt.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please create a prompt first (What?, Where?, How?).", 
                "Empty Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String selectedModel = (promptModelCombo != null) ? (String) promptModelCombo.getSelectedItem() : null;
        
        btnOptimizePrompt.setEnabled(false);
        btnOptimizePrompt.setText("✨ Optimizing...");
        promptLabConsole.append("Optimizing prompt with local Gemma...\n");
        
        new Thread(() -> {
            String optimized = null;
            StringBuilder errorLogs = new StringBuilder();
            try {
                promptLabConsole.append("Running direct local Gemma optimizer...\n");
                optimized = localAIService.optimizePromptDirectly(rawPrompt, selectedModel);
            } catch (Throwable ex) {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                errorLogs.append("Local Gemma optimization failed:\n").append(sw.toString()).append("\n");
                System.err.println("Local Gemma optimization failed: " + ex.getMessage());
            }
            
            final String finalOptimized = optimized;
            final String finalErrors = errorLogs.toString();
            SwingUtilities.invokeLater(() -> {
                btnOptimizePrompt.setEnabled(true);
                btnOptimizePrompt.setText("✨ Optimize with gemma");
                if (finalOptimized != null && !finalOptimized.isEmpty()) {
                    promptAssembleArea.setText(finalOptimized);
                    
                    // Manually inject optimized prompt into JSON prompt area
                    try {
                        String currentJsonStr = promptJsonArea.getText();
                        JSONObject mainObj = new JSONObject(currentJsonStr);
                        if (mainObj.has("prompt")) {
                            JSONObject promptObj = mainObj.getJSONObject("prompt");
                            String positiveNodeId = null;
                            for (String key : promptObj.keySet()) {
                                JSONObject nodeObj = promptObj.getJSONObject(key);
                                if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                                    if (nodeObj.has("inputs")) {
                                        JSONObject inputs = nodeObj.getJSONObject("inputs");
                                        if (inputs.has("text")) {
                                            String textVal = inputs.getString("text").toLowerCase();
                                            if (textVal.contains("bad") || textVal.contains("blurry") || textVal.contains("low quality") || textVal.contains("worst")) {
                                                continue;
                                            }
                                            positiveNodeId = key;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (positiveNodeId == null) {
                                for (String key : promptObj.keySet()) {
                                    JSONObject nodeObj = promptObj.getJSONObject(key);
                                    if (nodeObj.has("class_type") && "CLIPTextEncode".equals(nodeObj.getString("class_type"))) {
                                        positiveNodeId = key;
                                        break;
                                    }
                                }
                            }
                            
                            if (positiveNodeId != null) {
                                JSONObject nodeObj = promptObj.getJSONObject(positiveNodeId);
                                if (nodeObj.has("inputs")) {
                                    JSONObject inputs = nodeObj.getJSONObject("inputs");
                                    inputs.put("text", finalOptimized);
                                }
                            }
                            promptJsonArea.setText(mainObj.toString(2));
                        }
                    } catch (Exception ex) {
                        // ignore json error
                    }
                    
                    promptLabConsole.append("Prompt successfully optimized with local Gemma!\n\n");
                } else {
                    promptLabConsole.append("❌ Error: Prompt could not be optimized.\n");
                    if (!finalErrors.isEmpty()) {
                        promptLabConsole.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(this, 
                        "Prompt optimization failed. Please ensure the local Gemma model is downloaded correctly.", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void downloadLocalGemmaModel() {
        btnOptimizePrompt.setEnabled(false);
        btnOptimizePrompt.setText("⏳ Downloading...");
        promptLabConsole.append("Starting download of Gemma-2-2B GGUF model (1.6 GB) from Hugging Face...\n");
        
        localAIService.getLocalGemmaService().downloadModel(
            (percent, status) -> SwingUtilities.invokeLater(() -> {
                promptLabConsole.append("Download: " + status + "\n");
            }),
            () -> SwingUtilities.invokeLater(() -> {
                btnOptimizePrompt.setEnabled(true);
                btnOptimizePrompt.setText("✨ Optimize with gemma");
                promptLabConsole.append("✅ Local Gemma model downloaded successfully! You can now use local Gemma optimization.\n");
                JOptionPane.showMessageDialog(this,
                    "Local Gemma model downloaded successfully!",
                    "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                updateAiModelDisplay();
            }),
            (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                btnOptimizePrompt.setEnabled(true);
                btnOptimizePrompt.setText("✨ Optimize with gemma");
                promptLabConsole.append("❌ Download failed: " + errorMsg + "\n");
                JOptionPane.showMessageDialog(this,
                    "Failed to download Gemma model: " + errorMsg,
                    "Download Failed", JOptionPane.ERROR_MESSAGE);
            })
        );
    }

    private void suggestSubjectCompletions() {
        if (localAIService == null || !localAIService.isLocalGemmaDownloaded()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "The local Gemma model is not downloaded.\n" +
                "Would you like to download the Gemma-2-2B GGUF model (approx. 1.6 GB) now to get suggestions directly in Java?",
                "Download Local Gemma Model?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalGemmaModel();
            }
            return;
        }

        String currentSubject = promptSubjectField.getText().trim();
        if (currentSubject.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a basic subject first (e.g. 'cat', 'cybernetic tiger').", 
                "Empty Subject", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnSuggestSubject.setEnabled(false);
        btnSuggestSubject.setText("✨ Suggesting...");
        promptLabConsole.append("Generating subject completion suggestions using local Gemma...\n");

        new Thread(() -> {
            java.util.List<String> suggestions = null;
            StringBuilder errorLogs = new StringBuilder();
            try {
                promptLabConsole.append("Running direct local Gemma suggestions...\n");
                suggestions = localAIService.getDirectGemmaCompletions(currentSubject);
            } catch (Throwable ex) {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                errorLogs.append("Local Gemma suggestions failed:\n").append(sw.toString()).append("\n");
            }

            final java.util.List<String> finalSuggestions = suggestions;
            final String finalErrors = errorLogs.toString();
            SwingUtilities.invokeLater(() -> {
                btnSuggestSubject.setEnabled(true);
                btnSuggestSubject.setText("✨ Suggest");
                
                if (finalSuggestions != null && !finalSuggestions.isEmpty()) {
                    promptSubjectSuggestionsPanel.removeAll();
                    for (String suggestion : finalSuggestions) {
                        JButton sugBtn = new JButton(suggestion);
                        sugBtn.setHorizontalAlignment(SwingConstants.LEFT);
                        sugBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
                        sugBtn.putClientProperty("FlatLaf.style", "arc: 8; background: $TextField.background; border: 8,12,8,12,$Card.border,1,8");
                        sugBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        sugBtn.setToolTipText("Click to use this suggestion");
                        sugBtn.addActionListener(e -> {
                            promptSubjectField.setText(suggestion);
                            promptSubjectSuggestionsWrapper.setVisible(false);
                            promptLabLeftPanel.revalidate();
                            promptLabLeftPanel.repaint();
                        });
                        promptSubjectSuggestionsPanel.add(sugBtn);
                    }
                    promptSubjectSuggestionsWrapper.setVisible(true);
                    promptLabLeftPanel.revalidate();
                    promptLabLeftPanel.repaint();
                    promptLabConsole.append("Subject suggestions loaded successfully using local Gemma.\n");
                } else {
                    promptLabConsole.append("❌ Error: Could not generate suggestions.\n");
                    if (!finalErrors.isEmpty()) {
                        promptLabConsole.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(Main.this,
                        "Could not generate suggestions. Please ensure the local Gemma model is downloaded correctly.",
                        "Suggestions Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        }).start();
    }

    private void startPollingPromptStatus(String promptId) {
        String comfyUrl = configService.getComfyUIUrl();
        SwingUtilities.invokeLater(() -> {
            if (promptLabRightTabbedPane != null) {
                promptLabRightTabbedPane.setSelectedIndex(0); // Auto-switch to Image Preview tab
            }
            if (promptLabProgressBar != null) {
                promptLabProgressBar.setVisible(true);
                promptLabProgressBar.setIndeterminate(true);
                promptLabProgressBar.setString("Queued...");
            }
            if (promptImagePreviewLabel != null) {
                promptImagePreviewLabel.setText("Generating image... Please wait.");
                promptImagePreviewLabel.setIcon(null);
            }
        });

        new Thread(() -> {
            boolean done = false;
            int pollAttempts = 0;
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();

            while (!done && pollAttempts < 180) { // Timeout after 3 minutes
                try {
                    Thread.sleep(1000);
                    pollAttempts++;

                    // 1. Check history
                    java.net.http.HttpRequest histReq = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(comfyUrl + "/history/" + promptId))
                            .GET()
                            .build();
                    java.net.http.HttpResponse<String> histResp = client.send(histReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                    
                    if (histResp.statusCode() == 200) {
                        JSONObject histObj = new JSONObject(histResp.body());
                        if (histObj.has(promptId)) {
                            JSONObject promptHistory = histObj.getJSONObject(promptId);
                            JSONObject outputs = promptHistory.optJSONObject("outputs");
                            if (outputs != null) {
                                String filename = null;
                                String subfolder = "";
                                String type = "output";

                                for (String nodeKey : outputs.keySet()) {
                                    JSONObject nodeOutputs = outputs.getJSONObject(nodeKey);
                                    if (nodeOutputs.has("images")) {
                                        JSONArray images = nodeOutputs.getJSONArray("images");
                                        if (images.length() > 0) {
                                            JSONObject img = images.getJSONObject(0);
                                            filename = img.optString("filename");
                                            subfolder = img.optString("subfolder", "");
                                            type = img.optString("type", "output");
                                            break;
                                        }
                                    }
                                }

                                if (filename != null) {
                                    final String finalFilename = filename;
                                    final String finalSubfolder = subfolder;
                                    final String finalType = type;
                                    
                                    SwingUtilities.invokeLater(() -> {
                                        loadAndDisplayImage(finalFilename, finalSubfolder, finalType);
                                        if (promptLabProgressBar != null) promptLabProgressBar.setVisible(false);
                                        promptLabConsole.append("Image generated and loaded successfully!\n\n");
                                    });
                                    done = true;
                                    break;
                                }
                            }
                        }
                    }

                    // 2. Check queue position
                    if (!done) {
                        java.net.http.HttpRequest qReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(comfyUrl + "/queue"))
                                .GET()
                                .build();
                        java.net.http.HttpResponse<String> qResp = client.send(qReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (qResp.statusCode() == 200) {
                            JSONObject qObj = new JSONObject(qResp.body());
                            JSONArray pending = qObj.optJSONArray("queue_pending");
                            JSONArray running = qObj.optJSONArray("queue_running");
                            
                            boolean found = false;
                            if (running != null) {
                                for (int i = 0; i < running.length(); i++) {
                                    JSONArray item = running.getJSONArray(i);
                                    if (item.length() > 1 && promptId.equals(item.getString(1))) {
                                        SwingUtilities.invokeLater(() -> {
                                            if (promptLabProgressBar != null) promptLabProgressBar.setString("Generating...");
                                        });
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            if (!found && pending != null) {
                                for (int i = 0; i < pending.length(); i++) {
                                    JSONArray item = pending.getJSONArray(i);
                                    if (item.length() > 1 && promptId.equals(item.getString(1))) {
                                        final int pos = i + 1;
                                        SwingUtilities.invokeLater(() -> {
                                            if (promptLabProgressBar != null) promptLabProgressBar.setString("Queued (Position: " + pos + ")");
                                        });
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error polling ComfyUI status: " + e.getMessage());
                }
            }

            if (!done) {
                SwingUtilities.invokeLater(() -> {
                    if (promptLabProgressBar != null) promptLabProgressBar.setVisible(false);
                    if (promptImagePreviewLabel != null) promptImagePreviewLabel.setText("Generation timed out or failed.");
                    promptLabConsole.append("❌ Generation timed out or failed to load image.\n\n");
                });
            }
        }).start();
    }

    private void loadAndDisplayImage(String filename, String subfolder, String type) {
        String comfyUrl = configService.getComfyUIUrl();
        String imageUrl = comfyUrl + "/view?filename=" + filename + "&subfolder=" + subfolder + "&type=" + type;
        
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(imageUrl);
                Image img = ImageIO.read(url);
                if (img != null) {
                    SwingUtilities.invokeLater(() -> {
                        scaleAndSetImage(img);
                    });
                }
            } catch (Exception ex) {
                System.err.println("Failed to download image: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (promptImagePreviewLabel != null) promptImagePreviewLabel.setText("Failed to load generated image.");
                });
            }
        }).start();
    }

    private void scaleAndSetImage(Image img) {
        currentPreviewImage = img;
        if (img == null || promptImagePreviewLabel == null) return;
        
        int labelWidth = promptImagePreviewLabel.getWidth();
        int labelHeight = promptImagePreviewLabel.getHeight();
        
        if (labelWidth < 50) labelWidth = 500;
        if (labelHeight < 50) labelHeight = 500;
        
        int imgWidth = img.getWidth(null);
        int imgHeight = img.getHeight(null);
        
        if (imgWidth <= 0 || imgHeight <= 0) return;
        
        double ratioX = (double) labelWidth / imgWidth;
        double ratioY = (double) labelHeight / imgHeight;
        double ratio = Math.min(ratioX, ratioY);
        
        int targetWidth = (int) (imgWidth * ratio);
        int targetHeight = (int) (imgHeight * ratio);
        
        targetWidth = Math.max(10, targetWidth);
        targetHeight = Math.max(10, targetHeight);
        
        Image scaled = img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        promptImagePreviewLabel.setIcon(new ImageIcon(scaled));
        promptImagePreviewLabel.setText("");
    }

    private void savePromptLabSession() {
        if (configService == null) return;
        try {
            JSONObject session = new JSONObject();
            if (promptEnvCombo != null) {
                session.put("environment_index", promptEnvCombo.getSelectedIndex());
            }
            session.put("photorealistic", chkPhotorealistic.isSelected());
            session.put("oil_painting", chkOil.isSelected());
            session.put("unreal_engine", chkEngine.isSelected());
            session.put("anime", chkAnime.isSelected());
            session.put("dark_fantasy", chkFantasy.isSelected());
            session.put("watercolor", chkSketch.isSelected());
            if (promptModelCombo != null && promptModelCombo.getSelectedItem() != null) {
                session.put("model", promptModelCombo.getSelectedItem());
            }
            if (promptWidthSpinner != null) {
                session.put("width", promptWidthSpinner.getValue());
            }
            if (promptHeightSpinner != null) {
                session.put("height", promptHeightSpinner.getValue());
            }
            if (promptStepsSpinner != null) {
                session.put("steps", promptStepsSpinner.getValue());
            }
            if (promptCfgSpinner != null) {
                session.put("cfg", promptCfgSpinner.getValue());
            }
            configService.savePromptLabSession(session);
        } catch (Exception e) {
            System.err.println("Failed to save Prompt Lab session: " + e.getMessage());
        }
    }

    private void loadPromptLabSession() {
        if (configService == null) return;
        try {
            JSONObject session = configService.getPromptLabSession();
            if (session == null) return;
            
            if (session.has("environment_index") && promptEnvCombo != null) {
                int idx = session.getInt("environment_index");
                if (idx >= 0 && idx < promptEnvCombo.getItemCount()) {
                    promptEnvCombo.setSelectedIndex(idx);
                }
            }
            if (session.has("photorealistic")) chkPhotorealistic.setSelected(session.getBoolean("photorealistic"));
            if (session.has("oil_painting")) chkOil.setSelected(session.getBoolean("oil_painting"));
            if (session.has("unreal_engine")) chkEngine.setSelected(session.getBoolean("unreal_engine"));
            if (session.has("anime")) chkAnime.setSelected(session.getBoolean("anime"));
            if (session.has("dark_fantasy")) chkFantasy.setSelected(session.getBoolean("dark_fantasy"));
            if (session.has("watercolor")) chkSketch.setSelected(session.getBoolean("watercolor"));
            
            if (session.has("model") && promptModelCombo != null) {
                String model = session.getString("model");
                boolean found = false;
                for (int i = 0; i < promptModelCombo.getItemCount(); i++) {
                    if (model.equals(promptModelCombo.getItemAt(i))) {
                        promptModelCombo.setSelectedIndex(i);
                        found = true;
                        break;
                    }
                }
                if (!found && !model.isEmpty()) {
                    promptModelCombo.addItem(model);
                    promptModelCombo.setSelectedItem(model);
                }
            }
            if (session.has("width") && promptWidthSpinner != null) promptWidthSpinner.setValue(session.getInt("width"));
            if (session.has("height") && promptHeightSpinner != null) promptHeightSpinner.setValue(session.getInt("height"));
            if (session.has("steps") && promptStepsSpinner != null) promptStepsSpinner.setValue(session.getInt("steps"));
            if (session.has("cfg") && promptCfgSpinner != null) {
                Object cfgVal = session.get("cfg");
                if (cfgVal instanceof Number) {
                    promptCfgSpinner.setValue(((Number) cfgVal).doubleValue());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load Prompt Lab session: " + e.getMessage());
        }
    }

    private JPanel createDashboardPanel(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List (Card-like)
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(320, 0));
        leftPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Startprofile");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        leftPanel.add(profilesHeader, BorderLayout.NORTH);

        DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                de.tki.comfymodels.domain.LaunchProfile p = (de.tki.comfymodels.domain.LaunchProfile) value;
                java.awt.Component c = super.getListCellRendererComponent(list, " " + p.name(), index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    ((JLabel) c).setPreferredSize(new Dimension(0, 35));
                    ((JLabel) c).setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
            }
        });

        JScrollPane profileScroll = new JScrollPane(profileList);
        profileScroll.setBorder(BorderFactory.createEmptyBorder());
        profileScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        leftPanel.add(profileScroll, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        profileButtons.setOpaque(false);
        JButton addProfileBtn = new JButton("➕ Add");
        JButton removeProfileBtn = new JButton("➖ Remove");
        profileButtons.add(addProfileBtn);
        profileButtons.add(removeProfileBtn);
        leftPanel.add(profileButtons, BorderLayout.SOUTH);

        addProfileBtn.addActionListener(e -> {
            JTextField nameField = new JTextField();
            JTextField argsField = new JTextField("--listen, --port, 8188");
            Object[] message = { "Profile Name:", nameField, "Arguments (comma-separated):", argsField };
            int option = JOptionPane.showConfirmDialog(this, message, "Add Launch Profile", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
                List<String> args = java.util.Arrays.stream(argsField.getText().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                de.tki.comfymodels.domain.LaunchProfile newProfile = new de.tki.comfymodels.domain.LaunchProfile(
                    java.util.UUID.randomUUID().toString(), nameField.getText().trim(), "User-defined profile.",
                    false, "python", args, new HashMap<>()
                );
                List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                profiles.add(newProfile);
                try { profileManager.saveProfiles(profiles); refreshProfileList(profileListModel); } 
                catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
            }
        });

        removeProfileBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this, "Remove profile '" + selected.name() + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    profiles.removeIf(p -> p.id().equals(selected.id()));
                    try { profileManager.saveProfiles(profiles); refreshProfileList(profileListModel); } 
                    catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
                }
            }
        });

        // RIGHT: Control Center
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        rightPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 20,20,20,20,$Card.border,1,16");
        rightPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 15, 5);
        gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("Overview");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h1");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        rightPanel.add(titleLabel, gbc);

        JLabel statusDisplay = new JLabel("Status: Offline");
        statusDisplay.setFont(new Font("SansSerif", Font.BOLD, 18));
        statusDisplay.setForeground(Color.GRAY);
        gbc.gridy = 1;
        rightPanel.add(statusDisplay, gbc);

        JLabel descLabel = new JLabel("Select a profile to view details.");
        descLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        descLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 2;
        rightPanel.add(descLabel, gbc);

        profileList.addListSelectionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                descLabel.setText("<html><body style='width: 500px;'>" + selected.description() + "</body></html>");
                configService.setActiveProfile(selected.id());
            } else {
                descLabel.setText("Select a profile to view details.");
            }
        });

        refreshProfileList(profileListModel);

        // One-row clean button layout (5 buttons)
        JPanel actionPanel = new JPanel(new GridLayout(1, 5, 12, 12));
        actionPanel.setOpaque(false);
        
        launchBtn = new JButton("🚀 Launch");
        launchBtn.putClientProperty("JButton.buttonType", "accent");
        launchBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        JButton restartBtn = new JButton("🔄 Restart");
        restartBtn.putClientProperty("JButton.buttonType", "roundRect");
        restartBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        restartBtn.setEnabled(false);

        JButton stopBtn = new JButton("⏹ Stop");
        stopBtn.putClientProperty("JButton.buttonType", "roundRect");
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopBtn.setEnabled(false);

        JButton browserBtn = new JButton("🌐 Browser");
        browserBtn.putClientProperty("JButton.buttonType", "roundRect");
        browserBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        browserBtn.setEnabled(false);
        browserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(configService.getComfyUIUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Browser could not be opened: " + ex.getMessage());
            }
        });

        JButton bootstrapBtn = new JButton("🛠️ Setup");
        bootstrapBtn.putClientProperty("JButton.buttonType", "roundRect");
        bootstrapBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        actionPanel.add(launchBtn);
        actionPanel.add(restartBtn);
        actionPanel.add(stopBtn);
        actionPanel.add(browserBtn);
        actionPanel.add(bootstrapBtn);
        
        gbc.gridy = 3; gbc.gridheight = 1; gbc.insets = new Insets(20, 0, 20, 0);
        rightPanel.add(actionPanel, gbc);

        // Environment Versions section
        JPanel versionPanel = new JPanel();
        versionPanel.setOpaque(false);
        versionPanel.setLayout(new BoxLayout(versionPanel, BoxLayout.Y_AXIS));
        
        JLabel versionHeader = new JLabel("Environment Versions");
        versionHeader.putClientProperty("FlatLaf.styleClass", "h4");
        
        lblComfyVersion = new JLabel("ComfyUI: Loading...");
        lblPythonVersion = new JLabel("Python: Loading...");

        versionPanel.add(versionHeader);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        versionPanel.add(lblComfyVersion);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        versionPanel.add(lblPythonVersion);
        
        gbc.gridy = 5; gbc.insets = new Insets(10, 5, 15, 5);
        rightPanel.add(versionPanel, gbc);

        refreshVersions();

        // Console Output
        consoleOutput = new JTextArea();
        consoleOutput.setBackground(configService.isDarkMode() ? new Color(25, 25, 25) : new Color(245, 247, 250));
        consoleOutput.setForeground(configService.isDarkMode() ? new Color(0, 220, 0) : new Color(30, 30, 30));
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        consoleOutput.setEditable(false);
        consoleOutput.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane consoleScroll = new JScrollPane(consoleOutput) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("Component.borderColor");
                if (c != null) {
                    setBorder(BorderFactory.createLineBorder(c, 1));
                }
            }
        };
        Color bc = UIManager.getColor("Component.borderColor");
        consoleScroll.setBorder(BorderFactory.createLineBorder(bc != null ? bc : new Color(60, 60, 60), 1));
        
        JPanel consoleContainer = new JPanel(new BorderLayout(5, 5));
        consoleContainer.setOpaque(false);
        
        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 5));
        consoleToolbar.setOpaque(false);
        
        JTextField logSearchField = new JTextField();
        logSearchField.putClientProperty("JTextField.placeholderText", "🔍 Search console logs...");
        
        JButton clearConsoleBtn = new JButton("Clear Console");
        clearConsoleBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearConsoleBtn.addActionListener(e -> consoleOutput.setText(""));
        
        consoleToolbar.add(logSearchField, BorderLayout.CENTER);
        consoleToolbar.add(clearConsoleBtn, BorderLayout.EAST);
        
        consoleContainer.add(consoleToolbar, BorderLayout.NORTH);
        consoleContainer.add(consoleScroll, BorderLayout.CENTER);

        logSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { highlight(); }

            private void highlight() {
                javax.swing.text.Highlighter h = consoleOutput.getHighlighter();
                h.removeAllHighlights();
                String text = logSearchField.getText();
                if (text == null || text.trim().isEmpty()) return;
                String content = consoleOutput.getText();
                if (content == null || content.isEmpty()) return;
                
                String pattern = text.toLowerCase();
                String lowerContent = content.toLowerCase();
                int index = lowerContent.indexOf(pattern);
                javax.swing.text.Highlighter.HighlightPainter painter = 
                    new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 100, 100));
                while (index >= 0) {
                    try {
                        h.addHighlight(index, index + pattern.length(), painter);
                    } catch (javax.swing.text.BadLocationException ex) {
                        // ignore
                    }
                    index = lowerContent.indexOf(pattern, index + pattern.length());
                }
            }
        });

        gbc.gridy = 6; gbc.weighty = 1.0; gbc.insets = new Insets(5, 5, 5, 5);
        rightPanel.add(consoleContainer, gbc);

        // Timer for status updates
        Timer statusTimer = new Timer(1000, e -> {
            boolean running = processController.isRunning();
            if (running) {
                boolean starting = false;
                if (processController.isProcessAlive() && !processController.isGuiLineShown()) {
                    starting = true;
                } else if (lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                    starting = true;
                }

                if (starting) {
                    statusDisplay.setText("Status: Starting");
                    statusDisplay.setForeground(new Color(255, 204, 0));
                    launchBtn.setEnabled(false);
                    restartBtn.setEnabled(true);
                    stopBtn.setEnabled(true);
                    browserBtn.setEnabled(false);
                } else {
                    statusDisplay.setText("Status: Running");
                    statusDisplay.setForeground(new Color(0, 180, 0));
                    launchBtn.setEnabled(false);
                    restartBtn.setEnabled(true);
                    stopBtn.setEnabled(true);
                    browserBtn.setEnabled(true);
                }
            } else {
                statusDisplay.setText("Status: Offline");
                statusDisplay.setForeground(Color.GRAY);
                launchBtn.setEnabled(true);
                restartBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                browserBtn.setEnabled(false);
            }
            boolean needsSetup = configService.getComfyUIPath().isEmpty() || !new File(configService.getComfyUIPath(), "main.py").exists();
            bootstrapBtn.putClientProperty("FlatLaf.style", needsSetup ? "background: #646400; foreground: #fff" : "");
        });
        statusTimer.start();

        restartBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a profile first."); return; }
            restartBtn.setEnabled(false);
            consoleOutput.append("\n🔄 Restarting ComfyUI...\n");
            new Thread(() -> {
                processController.stop();
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    startComfyUI(selected, false, true);
                });
            }).start();
        });

        bootstrapBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "This will clone ComfyUI and download Python. Proceed?", "Bootstrap", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                bootstrapBtn.setEnabled(false); launchBtn.setEnabled(false);
                consoleOutput.setText("⚙️ Starting Environment Bootstrap...\n");
                Path appRoot = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
                Path comfyTarget = appRoot.resolve("ComfyUI");
                bootstrapper.downloadAndExtractPortablePython(appRoot, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n")))
                    .thenCompose(pythonPath -> {
                        configService.setPythonPath(pythonPath.toString());
                        return bootstrapper.installPip(pythonPath, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n")))
                            .thenCompose(v -> bootstrapper.cloneComfyUI(comfyTarget, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n"))))
                            .thenCompose(v -> bootstrapper.installRequirements(pythonPath, comfyTarget, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n"))));
                    }).thenRun(() -> SwingUtilities.invokeLater(() -> {
                        configService.setComfyUIPath(comfyTarget.toString()); configService.autoDiscoverPaths();
                        syncBridgeFiles();
                        consoleOutput.append("✅ Environment ready!\n");
                        bootstrapBtn.setEnabled(true); launchBtn.setEnabled(true);
                        refreshVersions(); JOptionPane.showMessageDialog(this, "Setup complete!");
                    })).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> { consoleOutput.append("❌ Bootstrap failed: " + ex.getMessage() + "\n"); bootstrapBtn.setEnabled(true); });
                        return null;
                    });
            }
        });

        launchBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a launch profile."); return; }
            startComfyUI(selected, true, true);
        });

        stopBtn.addActionListener(e -> { consoleOutput.append("\n⏹ Stopping ComfyUI process...\n"); processController.stop(); });

        // RIGHT: System Stats & Quick Actions
        JPanel rightPanelEast = new JPanel(new GridBagLayout());
        rightPanelEast.setOpaque(false);
        rightPanelEast.setPreferredSize(new Dimension(340, 0));
        rightPanelEast.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        rightPanelEast.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints eastGbc = new GridBagConstraints();
        eastGbc.fill = GridBagConstraints.HORIZONTAL;
        eastGbc.weightx = 1.0;
        eastGbc.gridx = 0;
        eastGbc.gridy = 0;
        eastGbc.insets = new Insets(5, 5, 15, 5);

        JLabel statsHeader = new JLabel("System Stats");
        statsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(statsHeader, eastGbc);

        // CPU
        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 8, 5);
        progressCpu = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("CPU Load", progressCpu), eastGbc);

        // RAM
        eastGbc.gridy++;
        progressRam = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("RAM Usage", progressRam), eastGbc);

        // GPU Panel wrapper
        eastGbc.gridy++;
        gpuPanel = new JPanel(new GridBagLayout());
        gpuPanel.setOpaque(false);

        GridBagConstraints gpuGbc = new GridBagConstraints();
        gpuGbc.fill = GridBagConstraints.HORIZONTAL;
        gpuGbc.weightx = 1.0;
        gpuGbc.gridx = 0;
        gpuGbc.gridy = 0;
        gpuGbc.insets = new Insets(5, 0, 8, 0);

        lblGpuName = new JLabel("GPU: N/A");
        lblGpuName.setFont(new Font("SansSerif", Font.BOLD, 12));
        gpuPanel.add(lblGpuName, gpuGbc);

        gpuGbc.gridy++;
        progressGpu = new JProgressBar(0, 100);
        gpuPanel.add(createSlimStatPanel("GPU Load", progressGpu), gpuGbc);

        gpuGbc.gridy++;
        gpuGbc.insets = new Insets(5, 0, 5, 0);
        progressVram = new JProgressBar(0, 100);
        gpuPanel.add(createSlimStatPanel("VRAM Usage", progressVram), gpuGbc);

        rightPanelEast.add(gpuPanel, eastGbc);

        // Separator
        eastGbc.gridy++;
        eastGbc.insets = new Insets(15, 5, 15, 5);
        rightPanelEast.add(new JSeparator(), eastGbc);

        // Quick Actions
        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 15, 5);
        JLabel actionsHeader = new JLabel("Quick Actions");
        actionsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(actionsHeader, eastGbc);

        // Buttons
        eastGbc.gridy++;
        eastGbc.insets = new Insets(6, 5, 6, 5);
        JButton updateBtn = new JButton("📥 Update ComfyUI & Nodes");
        updateBtn.putClientProperty("JButton.buttonType", "roundRect");
        updateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateBtn.setPreferredSize(new Dimension(0, 35));
        updateBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.AutoUpdaterDialog dialog = new de.tki.comfymodels.ui.AutoUpdaterDialog(this, updaterService);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(updateBtn, eastGbc);

        eastGbc.gridy++;
        JButton checkUpdatesBtn = new JButton("🔍 Check Model Upgrades");
        checkUpdatesBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkUpdatesBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        checkUpdatesBtn.setPreferredSize(new Dimension(0, 35));
        checkUpdatesBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.ModelUpdateCheckerDialog dialog = new de.tki.comfymodels.ui.ModelUpdateCheckerDialog(
                this, configService, hashRegistry, modelValidator, civitaiService, downloadManager
            );
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(checkUpdatesBtn, eastGbc);

        eastGbc.gridy++;
        JButton storageOptBtn = new JButton("🧼 Storage Optimizer");
        storageOptBtn.putClientProperty("JButton.buttonType", "roundRect");
        storageOptBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        storageOptBtn.setPreferredSize(new Dimension(0, 35));
        storageOptBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.DeduplicationDialog dialog = new de.tki.comfymodels.ui.DeduplicationDialog(
                this, configService, modelValidator, hashRegistry
            );
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(storageOptBtn, eastGbc);

        // Add a vertical glue / weight filler
        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        eastGbc.fill = GridBagConstraints.BOTH;
        rightPanelEast.add(Box.createGlue(), eastGbc);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);
        panel.add(rightPanelEast, BorderLayout.EAST);

        return panel;
    }

    private void startComfyAndReload() {
        new Thread(() -> {
            lifecycleService.start();
            // Wait for health
            for (int i = 0; i < 30; i++) {
                if (lifecycleService.isHealthy()) {
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void showSettingsMenu(JButton parent) {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem pathsItem = new JMenuItem("📁 Directories...");
        pathsItem.addActionListener(e -> showPathsDialog());
        
        JMenuItem apiItem = new JMenuItem("🔑 AI & API Keys...");
        apiItem.addActionListener(e -> showApiKeysDialog());
        
        JMenuItem bridgeItem = new JMenuItem("🚀 ComfyUI Bridge...");
        bridgeItem.addActionListener(e -> showInstallationDialog());
        
        JMenuItem helpItem = new JMenuItem("ℹ Help...");
        helpItem.addActionListener(e -> showHelpDialog());

        JMenuItem exitItem = new JMenuItem("❌ Exit Application");
        exitItem.addActionListener(e -> performAppExit());

        menu.add(pathsItem);
        menu.add(apiItem);
        menu.addSeparator();
        menu.add(bridgeItem);
        menu.addSeparator();
        menu.add(helpItem);
        menu.add(new JSeparator());
        menu.add(exitItem);
        
        menu.show(parent, 0, parent.getHeight());
    }

    private void performAppExit() {
        lifecycleService.stop();
        downloadManager.stop();
        System.exit(0);
    }

    private void showPathsDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Directory Settings");
        dialog.setSize(600, 480);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("Extra ComfyUI Path (contains models, input, output):"), gbc);
        
        gbc.gridy++;
        JPanel row1 = new JPanel(new BorderLayout(5, 0));
        JTextField field1 = new JTextField(configService.getExtraComfyUIPath());
        JButton browse1 = new JButton("Browse...");
        browse1.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field1.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row1.add(field1, BorderLayout.CENTER);
        row1.add(browse1, BorderLayout.EAST);
        panel.add(row1, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Archive Path (Offload storage):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row2 = new JPanel(new BorderLayout(5, 0));
        JTextField field2 = new JTextField(configService.getArchivePath());
        JButton browse2 = new JButton("Browse...");
        browse2.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field2.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row2.add(field2, BorderLayout.CENTER);
        row2.add(browse2, BorderLayout.EAST);
        panel.add(row2, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("ComfyUI Main Directory (contains main.py):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row3 = new JPanel(new BorderLayout(5, 0));
        JTextField field3 = new JTextField(configService.getComfyUIPath());
        JButton browse3 = new JButton("Browse...");
        row3.add(field3, BorderLayout.CENTER);
        row3.add(browse3, BorderLayout.EAST);
        panel.add(row3, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Python Executable Path:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row4 = new JPanel(new BorderLayout(5, 0));
        JTextField field4 = new JTextField(configService.getPythonPath());
        JButton browse4 = new JButton("Browse...");
        row4.add(field4, BorderLayout.CENTER);
        row4.add(browse4, BorderLayout.EAST);
        panel.add(row4, gbc);

        browse3.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                field3.setText(path);
                
                String discoveredPython = configService.discoverPython(path);
                if (discoveredPython != null && !discoveredPython.equals("python") && !discoveredPython.equals("python3")) {
                    field4.setText(discoveredPython);
                }
            }
        });

        browse4.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field4.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        JCheckBox symlinkCheck = new JCheckBox("Use Symbolic Links on Restore (Saves SSD space)", configService.isUseSymlinksOnRestore());
        panel.add(symlinkCheck, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            configService.setModelsPath(field1.getText().trim());
            configService.setArchivePath(field2.getText().trim());
            configService.setComfyUIPath(field3.getText().trim());
            configService.setPythonPath(field4.getText().trim());
            configService.setUseSymlinksOnRestore(symlinkCheck.isSelected());
            configService.setComfyLaunchCommand(""); 
            configService.autoDiscoverPaths();
            syncBridgeFiles();
            statusLabel.setText("Settings updated.");
            analyzeJsonContent();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showDownloadSettingsDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Download Settings");
        dialog.setSize(600, 380);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("Max Parallel Downloads (Active downloads in queue):"), gbc);
        
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel threadModel = new SpinnerNumberModel(configService.getMaxParallelDownloads(), 1, 10, 1);
        JSpinner threadSpinner = new JSpinner(threadModel);
        panel.add(threadSpinner, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Download Speed Limit (KB/s - 0 for unlimited):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JTextField speedField = new JTextField(String.valueOf(configService.getDownloadSpeedLimit()));
        panel.add(speedField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Segments Per File (Multi-segment downloading, 1 to disable):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel segmentModel = new SpinnerNumberModel(configService.getSegmentsPerFile(), 1, 8, 1);
        JSpinner segmentSpinner = new JSpinner(segmentModel);
        panel.add(segmentSpinner, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            try {
                int threads = (Integer) threadSpinner.getValue();
                int segments = (Integer) segmentSpinner.getValue();
                int speedLimit = Integer.parseInt(speedField.getText().trim());
                if (speedLimit < 0) {
                    throw new NumberFormatException();
                }

                configService.setMaxParallelDownloads(threads);
                configService.setDownloadSpeedLimit(speedLimit);
                configService.setSegmentsPerFile(segments);

                statusLabel.setText("Download settings updated.");
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid positive integer for the speed limit.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showApiKeysDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "AI & API Keys");
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        panel.add(new JLabel("Gemini AI API Key:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField geminiField = new JPasswordField(configService.getGeminiApiKey());
        panel.add(geminiField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Hugging Face Access Token:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField hfField = new JPasswordField(configService.getHfToken());
        panel.add(hfField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Civitai API Key:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField civitaiField = new JPasswordField(configService.getCivitaiApiKey());
        panel.add(civitaiField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JCheckBox ollamaCheck = new JCheckBox("Use Local Ollama LLM for Author Identification", configService.isUseOllama());
        panel.add(ollamaCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 20, 5, 0);
        JPanel ollamaRow = new JPanel(new GridLayout(2, 2, 10, 5));
        ollamaRow.setOpaque(false);
        ollamaRow.add(new JLabel("Ollama Server URL:"));
        ollamaRow.add(new JLabel("Ollama Model:"));
        JTextField ollamaUrlField = new JTextField(configService.getOllamaUrl());
        JTextField ollamaModelField = new JTextField(configService.getOllamaModel());
        ollamaRow.add(ollamaUrlField);
        ollamaRow.add(ollamaModelField);
        panel.add(ollamaRow, gbc);

        ollamaUrlField.setEnabled(ollamaCheck.isSelected());
        ollamaModelField.setEnabled(ollamaCheck.isSelected());
        ollamaCheck.addActionListener(e -> {
            ollamaUrlField.setEnabled(ollamaCheck.isSelected());
            ollamaModelField.setEnabled(ollamaCheck.isSelected());
        });

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            configService.setGeminiApiKey(new String(geminiField.getPassword()).trim());
            configService.setHfToken(new String(hfField.getPassword()).trim());
            configService.setCivitaiApiKey(new String(civitaiField.getPassword()).trim());
            configService.setUseOllama(ollamaCheck.isSelected());
            configService.setOllamaUrl(ollamaUrlField.getText().trim());
            configService.setOllamaModel(ollamaModelField.getText().trim());
            statusLabel.setText("API keys updated.");
            updateAiModelDisplay();
            analyzeJsonContent();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void startDownloadQueue() {
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) return;
        boolean[] selected = new boolean[rowCount];
        
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            String url = (String) tableModel.getValueAt(i, 6);
            if (url != null && !url.equals("MISSING")) modelsToDownload.get(i).setUrl(url);
        }

        downloadButton.setEnabled(false);

        // Process archive restores FIRST as they are part of the visual queue
        new Thread(() -> {
            for (int i = 0; i < rowCount; i++) {
                if (selected[i]) {
                    String currentStatus = (String) tableModel.getValueAt(i, 7);
                    if ("📦 Archived".equals(currentStatus)) {
                        final int idx = i;
                        ModelInfo info = modelsToDownload.get(idx);
                        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName());
                        String normalizedFolder = archiveService.normalizeFolder(folder);
                        
                        SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring...", idx, 7));
                        
                        long totalSize = info.getByteSize();
                        long[] currentBytes = {0};
                        long[] lastUpdate = {0};

                        boolean success = archiveService.restoreFromArchiveWithProgress(normalizedFolder, info.getName(), (delta) -> {
                            currentBytes[0] += delta;
                            long now = System.currentTimeMillis();
                            if (totalSize > 0 && (now - lastUpdate[0] > 200)) {
                                lastUpdate[0] = now;
                                int percent = (int) Math.min(100, (currentBytes[0] * 100) / totalSize);
                                SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring (" + percent + "%)...", idx, 7));
                            }
                        });
                        
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                tableModel.setValueAt("✅ Already exists", idx, 7);
                                tableModel.setValueAt(false, idx, 0); // Unselect after restoration
                            } else {
                                tableModel.setValueAt("❌ Restore Failed - Downloading...", idx, 7);
                                // Stay selected for download fallback
                            }
                        });
                    }
                }
            }
            
            // After restores, start the actual download manager for the remaining models
            SwingUtilities.invokeLater(() -> {
                // Update selected array (some might have been unselected by restore)
                for (int i = 0; i < rowCount; i++) {
                    selected[i] = (Boolean) tableModel.getValueAt(i, 0);
                }

                downloadManager.startQueue(modelsToDownload, selected, configService.getModelsPath(),
                    (idx, status) -> SwingUtilities.invokeLater(() -> {
                        String current = (String) tableModel.getValueAt(idx, 7);
                        if (status.startsWith("Skipped") && current != null && (current.equals("✅ Already exists") || current.equals("✅ Finished"))) {
                            return; // Don't overwrite success with skip
                        }
                        tableModel.setValueAt(status, idx, 7);
                    }),
                    () -> SwingUtilities.invokeLater(() -> {
                        downloadButton.setEnabled(true);
                        statusLabel.setText("Queue finished.");
                        
                        triggerPostOperationActions();
                    })
                );
            });
        }).start();
    }

    private void showInstallationDialog() {
        JDialog dialog = new JDialog(this, "ComfyUI Bridge Installation", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(650, 360);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 15, 0);

        // Explanation text
        JTextArea infoArea = new JTextArea(
            "This installer will set up the ComfyUI Companion bridge.\n\n" +
            "1. Select your ComfyUI main directory.\n" +
            "2. Old or conflicting bridge files will be cleaned up.\n" +
            "3. A link or copy of the UI extension will be created.\n\n" +
            "Important: Restart ComfyUI after the installation is finished."
        );
        infoArea.setEditable(false);
        infoArea.setFocusable(false);
        infoArea.setBackground(content.getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        content.add(infoArea, gbc);

        // Path selection header
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        content.add(new JLabel("ComfyUI Main Directory:"), gbc);

        // Path selection row
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JPanel pathRow = new JPanel(new BorderLayout(5, 0));
        JTextField pathField = new JTextField(configService.getComfyUIPath());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
        content.add(pathRow, gbc);

        // Spacer to push everything to the top
        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton syncBtn = new JButton("🔄 Repair / Sync Token");
        syncBtn.setToolTipText("Only updates the API token in your existing ComfyUI extension.");
        syncBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            configService.setComfyUIPath(selectedPath);
            syncBridgeFiles();
            JOptionPane.showMessageDialog(dialog, "API Token synchronized successfully.");
        });
        
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton installBtn = new JButton("🚀 Start Installation");
        installBtn.putClientProperty("JButton.buttonType", "accent");
        installBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            installComfyUIBridge(selectedPath, dialog);
        });
        buttonPanel.add(syncBtn);
        buttonPanel.add(new JSeparator(JSeparator.VERTICAL));
        buttonPanel.add(cancelBtn);
        buttonPanel.add(installBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void installComfyUIBridge(String comfyPath, JDialog parentDialog) {
        Path inputPath = Paths.get(comfyPath);
        if (!Files.exists(inputPath)) {
            JOptionPane.showMessageDialog(parentDialog, "The provided path does not exist: " + comfyPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Systematically search for custom_nodes using PathResolver
        Path customNodesDir = pathResolver.findCustomNodes(inputPath);

        if (customNodesDir == null) {
            String msg = "Could not find 'custom_nodes' folder in the selected directory.\n\n" +
                         "Please ensure you select the folder that contains 'custom_nodes' or the main ComfyUI folder.";
            JOptionPane.showMessageDialog(parentDialog, msg, "Installation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- IMPROVEMENT: THOROUGH CLEANUP ---
        // 1. Delete legacy single-file scripts that might conflict
        String[] conflictingFiles = {"comfyui_to_downloader.py", "comfyui-model-downloader.py"};
        for (String conflict : conflictingFiles) {
            Path conflictFile = customNodesDir.resolve(conflict);
            try {
                if (Files.exists(conflictFile)) {
                    Files.delete(conflictFile);
                    System.out.println("Cleaned up legacy script: " + conflictFile);
                }
            } catch (IOException ignored) {}
        }

        // 2. Search for and remove ANY folder named 'comfyui-model-downloader' to ensure a clean slate
        try (java.util.stream.Stream<Path> walk = Files.walk(customNodesDir, 2)) {
            List<Path> oldDirs = walk
                .filter(p -> Files.isDirectory(p) && (
                    p.getFileName().toString().equalsIgnoreCase("comfyui-model-downloader") || 
                    p.getFileName().toString().equalsIgnoreCase("comfyui-companion") ||
                    p.getFileName().toString().equalsIgnoreCase("comfyuicompanion") ||
                    p.getFileName().toString().equalsIgnoreCase("comfyuidownloader") ||
                    p.getFileName().toString().equalsIgnoreCase("comfymodeldownloader")
                ))
                .collect(Collectors.toList());
            
            for (Path oldDir : oldDirs) {
                System.out.println("Removing existing bridge directory: " + oldDir);
                deleteDirectory(oldDir.toFile());
            }
        } catch (IOException e) {
            System.err.println("Error during bridge cleanup: " + e.getMessage());
        }

        configService.setComfyUIPath(comfyPath);
        
        Path targetDir = customNodesDir.resolve("comfyuicompanion");
        
        try {
            Files.createDirectories(targetDir);

            // Extract from resources
            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());

            // Force immediate config write with current token
            writeExtensionConfig(targetDir.toFile());
            
            if (Files.exists(targetDir.resolve("__init__.py"))) {
                String msg = "🚀 ComfyUI Bridge installed successfully!\n\n" +
                             "Location: " + targetDir.toAbsolutePath() + "\n\n" +
                             "Important:\n" +
                             "1. If ComfyUI is running, you MUST RESTART it.\n" +
                             "2. Check if a 'rocket' icon appears in the ComfyUI menu.";
                JOptionPane.showMessageDialog(parentDialog, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
                parentDialog.dispose();
            } else {
                throw new Exception("Installation failed: __init__.py not found at target!");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentDialog, "Critical failure during installation: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void extractResource(String resourcePath, File destination) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Resource not found: " + resourcePath);
            Files.copy(is, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File findCustomNodesDeep(File dir, int depth) {
        if (depth > 2) return null; // Limit depth to avoid performance issues
        File[] files = dir.listFiles();
        if (files == null) return null;

        // Check immediate children first
        for (File f : files) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase("custom_nodes")) {
                return f;
            }
        }

        // Recurse
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equalsIgnoreCase("node_modules")) {
                File found = findCustomNodesDeep(f, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void writeExtensionConfig(File dir) {
        try {
            File webDir = new File(dir, "web");
            if (!webDir.exists()) webDir.mkdirs();
            JSONObject config = new JSONObject();
            config.put("token", configService.getApiToken());
            Path configFile = new File(webDir, "config.json").toPath();
            Files.writeString(configFile, config.toString(4));
            System.out.println("[Bridge-Sync] Successfully wrote config to: " + configFile.toAbsolutePath());
        } catch (Exception e) { 
            System.err.println("[Bridge-Sync] Failed to write config: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }

    private void loadFile(File file) {
        try {
            currentFileName = file.getName();
            jsonInputArea.setText(workflowService.extractWorkflow(file));
            analyzeJsonContent();
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    private void analyzeJsonContent() {
        String text = jsonInputArea.getText();
        if (text == null || text.isEmpty()) return;
        if (workflowGraphPanel != null) {
            workflowGraphPanel.setWorkflowJson(text);
        }
        modelsToDownload = analyzer.analyze(text, currentFileName);
        tableModel.setRowCount(0);
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = info.getSave_path() != null ? info.getSave_path() : type;
            
            String normalizedFolder = archiveService.normalizeFolder(folder);

            // 1. Primary path check (Standard Models Path)
            Path local = "root".equals(normalizedFolder) ? Paths.get(base, info.getName()) : Paths.get(base, normalizedFolder, info.getName());
            boolean exists = Files.exists(local) && Files.isRegularFile(local);

            // 2. Primary archive check (Standard Archive Path)
            boolean inArchive = false;
            Path archivedPath = null;
            if (archive != null && !archive.trim().isEmpty()) {
                archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, info.getName()) : Paths.get(archive, normalizedFolder, info.getName());
                inArchive = Files.exists(archivedPath) && Files.isRegularFile(archivedPath);
            }
            
            boolean sizeMismatch = false;

            // 3. Robust existence and archive cross-check (Safety Guard)
            if (archive != null && !archive.isEmpty()) {
                try {
                    Path absArchive = Paths.get(archive).toAbsolutePath().normalize();
                    
                    // If 'local' is actually inside the archive, it's NOT a local active model
                    if (exists && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                        exists = false;
                        inArchive = true;
                    }
                    
                    // If we found it in the primary archive location, verify size if known
                    if (inArchive && info.getByteSize() > 0) {
                        if (Files.size(archivedPath) != info.getByteSize()) {
                            inArchive = false; // Size mismatch in archive doesn't count as "found"
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4. Fallback: Recursive search in ARCHIVE if not found at primary archive location
            if (!inArchive && archive != null && !archive.isEmpty()) {
                java.util.Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), info.getByteSize());
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            // 5. Fallback: Recursive search in LOCAL MODELS if not found OR size mismatch at primary location
            if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
                java.util.Optional<Path> foundLocally = localScanner.findModelWithPrefSizeAndType(Paths.get(base), info.getName(), info.getByteSize(), type);
                if (foundLocally.isPresent()) {
                    Path potentialLocal = foundLocally.get();
                    try {
                        long potSize = Files.size(potentialLocal);
                        if (info.getByteSize() <= 0 || potSize == info.getByteSize()) {
                            local = potentialLocal;
                            exists = true;
                            sizeMismatch = false;
                            
                            Path root = Paths.get(base).toAbsolutePath().normalize();
                            Path absPotential = potentialLocal.toAbsolutePath().normalize();
                            
                            if (absPotential.startsWith(root)) {
                                Path rel = root.relativize(absPotential);
                                normalizedFolder = (rel.getParent() != null) ? rel.getParent().toString().replace("\\", "/") : "root";
                            } else {
                                normalizedFolder = "extra/" + potentialLocal.getParent().getFileName();
                            }
                            info.setSave_path(normalizedFolder);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 6. Final Status Determination
            String status;
            if (exists) {
                status = "✅ Already exists";
            } else if (inArchive) {
                status = "📦 Archived";
            } else if (sizeMismatch) {
                status = "🔄 Size Mismatch";
            } else if (info.getUrl().equals("MISSING")) {
                status = "Idle";
            } else {
                status = "✅ Known Good";
            }
            
            // Select for download/restore if NOT exists locally
            boolean isSelected = !exists;
            tableModel.addRow(new Object[]{isSelected, info.getType(), info.getName(), info.getSize(), info.getPopularity(), "models/" + normalizedFolder, info.getUrl(), status});
        }
        
        downloadButton.setEnabled(!modelsToDownload.isEmpty());
        
        // NEW: Automatically fetch missing sizes for remote models in background
        fetchMissingRemoteSizes();
    }

    private void fetchMissingRemoteSizes() {
        if (modelsToDownload == null) return;
        new Thread(() -> {
            for (int i = 0; i < modelsToDownload.size(); i++) {
                final int idx = i;
                ModelInfo info = modelsToDownload.get(idx);
                
                String status = (idx < tableModel.getRowCount()) ? (String) tableModel.getValueAt(idx, 7) : "";
                boolean needsCheck = "Unknown".equals(info.getSize()) || "🔄 Size Mismatch".equals(status);

                if (!info.getUrl().equals("MISSING") && needsCheck) {
                    long size = searchService.getRemoteSize(info.getUrl());
                    if (size > 0) {
                        info.setByteSize(size);
                        String formatted = searchService.formatSize(size);
                        info.setSize(formatted);
                        SwingUtilities.invokeLater(() -> {
                            if (idx < tableModel.getRowCount()) {
                                tableModel.setValueAt(formatted, idx, 3);
                                
                                // Re-verify status with new remote size info
                                String currentStatus = (String) tableModel.getValueAt(idx, 7);
                                if (currentStatus.contains("Already exists") || "🔄 Size Mismatch".equals(currentStatus) || "📦 Archived".equals(currentStatus)) {
                                    String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
                                    String folder = info.getSave_path() != null ? info.getSave_path() : type;
                                    String base = configService.getModelsPath();
                                    String archive = configService.getArchivePath();
                                    
                                    // 1. Re-check local
                                    Path local = Paths.get(base, archiveService.normalizeFolder(folder), info.getName());
                                    if (!Files.exists(local)) {
                                        local = Paths.get(base, type, info.getName());
                                    }
                                    
                                    // If still not found at "standard" locations, try recursive local
                                    if (!Files.exists(local)) {
                                        java.util.Optional<Path> recursiveLocal = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                                        if (recursiveLocal.isPresent()) local = recursiveLocal.get();
                                    }

                                    boolean localExists = Files.exists(local);
                                    boolean localSizeMatch = false;
                                    if (localExists) {
                                        try {
                                            localSizeMatch = (Files.size(local) == size);
                                        } catch (IOException ignored) {}
                                    }

                                    // 2. Re-check archive
                                    boolean inArchive = false;
                                    if (archive != null && !archive.isEmpty()) {
                                        Path archived = Paths.get(archive, archiveService.normalizeFolder(folder), info.getName());
                                        try {
                                            if (Files.exists(archived) && Files.size(archived) == size) {
                                                inArchive = true;
                                            } else {
                                                inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                                            }
                                        } catch (IOException ignored) {}
                                    }

                                    String newStatus;
                                    boolean shouldSelect = false;

                                    if (localSizeMatch) {
                                        newStatus = "✅ Already exists";
                                        shouldSelect = false;
                                    } else if (localExists) {
                                        newStatus = "🔄 Size Mismatch";
                                        shouldSelect = true;
                                    } else if (inArchive) {
                                        newStatus = "📦 Archived";
                                        shouldSelect = true;
                                    } else {
                                        newStatus = "✅ Known Good";
                                        shouldSelect = true;
                                    }

                                    final String finalStatus = newStatus;
                                    final boolean finalSelect = shouldSelect;
                                    SwingUtilities.invokeLater(() -> {
                                        tableModel.setValueAt(finalStatus, idx, 7);
                                        tableModel.setValueAt(finalSelect, idx, 0);
                                    });
                                }
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void searchMissingOnline() {
        if (modelsToDownload == null) return;
        statusLabel.setText("Searching...");
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        searchService.searchOnline(modelsToDownload, selected, jsonInputArea.getText(), currentFileName,
            (idx, status) -> SwingUtilities.invokeLater(() -> {
                String current = (String) tableModel.getValueAt(idx, 7);
                // Protection: Don't overwrite successful detection (local or archive) with search progress/failure
                if (current != null && (current.contains("✅") || current.contains("📦"))) {
                    if (status.contains("No trusted match") || status.startsWith("🔍") || status.startsWith("✨")) {
                        return; 
                    }
                }
                tableModel.setValueAt(status, idx, 7);
            }),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getUrl(), idx, 6);
                
                String base = configService.getModelsPath();
                String archive = configService.getArchivePath();
                long size = info.getByteSize();

                // Re-verify local existence with potential new size info
                boolean exists = false;
                boolean inArchive = false;
                
                if (base != null && !base.isEmpty()) {
                    java.util.Optional<Path> foundLocally = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                    if (foundLocally.isPresent()) {
                        exists = true;
                        // Update target path display
                        try {
                            Path rel = Paths.get(base).relativize(foundLocally.get());
                            tableModel.setValueAt("models/" + rel.getParent().toString().replace("\\", "/"), idx, 5);
                        } catch (Exception ignored) {}
                    }
                }

                if (!exists && archive != null && !archive.isEmpty()) {
                    inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                }

                String newStatus;
                if (exists) {
                    newStatus = "✅ Already exists";
                } else if (inArchive) {
                    newStatus = "📦 Archived";
                } else {
                    newStatus = "✅ Known Good";
                }


                tableModel.setValueAt(newStatus, idx, 7);
                tableModel.setValueAt(!exists, idx, 0); // Keep selected for download/restore if NOT local
            }),
            () -> SwingUtilities.invokeLater(() -> statusLabel.setText("Search finished."))
        );
    }

    private void verifyLocalModels(boolean checkDuplicates) {
        String baseDir = configService.getModelsPath();
        if (baseDir == null || baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please set a models directory first.");
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Invalid models directory.");
            return;
        }

        String taskName = checkDuplicates ? "Verifying models & checking for duplicates" : "Verifying models (Fast Check)";
        statusLabel.setText(taskName + "... please wait.");
        new Thread(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
                Map<String, List<Path>> hashToPaths = new HashMap<>();

                List<Path> allFiles = Files.walk(root.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String relPath = root.toPath().relativize(p).toString().toLowerCase();
                            return !relPath.contains(".venv") && !relPath.contains("archive");
                        })
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin");
                        })
                        .collect(Collectors.toList());

                int total = allFiles.size();
                for (int i = 0; i < total; i++) {
                    Path p = allFiles.get(i);
                    final int current = i + 1;
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Checking (" + current + "/" + total + "): " + p.getFileName()));

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
                    statusLabel.setText("Scan finished. Found " + errors.size() + " issues and " + duplicates.size() + " duplicate sets.");

                    if (errors.isEmpty() && duplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
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
                        int choice = JOptionPane.showOptionDialog(this, scrollPane, "Verification Results - Issues Found", 
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                        if (choice == 1) { // Delete All Corrupted Files
                            int confirm = JOptionPane.showConfirmDialog(this, 
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
                                JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " files.");    
                                statusLabel.setText("Cleanup finished. Deleted " + deletedCount + " files.");  
                            }
                        }
                    }
                    
                    if (!duplicates.isEmpty()) {
                        showDuplicatesDialog(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error during verification: " + e.getMessage()));
            }
        }).start();
    }

    private void showDuplicatesDialog(Map<String, List<Path>> duplicates) {
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
        JOptionPane.showMessageDialog(this, scrollPane, "Duplicates Found", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showArchiveDialog() {
        JDialog dialog = new JDialog(this, "Archive Manager", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(1000, 700);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabs = new JTabbedPane();
        
        // --- TAB 1: ARCHIVE (Models -> Archive) ---
        JPanel archivePanel = new JPanel(new BorderLayout(10, 10));
        archivePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        Map<String, List<ModelInfo>> groupedToArchive = archiveService.getModelsGroupedByFolder();
        String[] columns = {"Select", "Folder", "Name", "Size"};
        DefaultTableModel archiveTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToArchive.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                archiveTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        Map<String, List<ModelInfo>> groupedToRestore = archiveService.getArchivedModelsGroupedByFolder();
        DefaultTableModel restoreTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToRestore.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                restoreTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        JTable archiveTable = new JTable(archiveTableModel);
        archiveTable.setRowHeight(25);
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Enable sorting with custom comparator for human-readable sizes
        javax.swing.table.TableRowSorter<DefaultTableModel> archiveSorter = new javax.swing.table.TableRowSorter<>(archiveTableModel);
        archiveSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        archiveSorter.setSortable(0, false); // Disable sorting for checkbox column to allow "Select All"
        archiveTable.setRowSorter(archiveSorter);

        // Add "Select All" functionality to header
        archiveTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = archiveTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = archiveTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        if (!(Boolean) archiveTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        archiveTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane archiveScroll = new JScrollPane(archiveTable);
        archivePanel.add(archiveScroll, BorderLayout.CENTER);

        JPanel archiveBottom = new JPanel(new BorderLayout());
        JLabel archiveCountLabel = new JLabel("Selected: 0 models");
        archiveTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                if ((Boolean) archiveTableModel.getValueAt(i, 0)) selected++;
            }
            archiveCountLabel.setText("Selected: " + selected + " models");
        });
        
        JButton archiveNowBtn = new JButton("📦 Move to Archive");
        archiveNowBtn.putClientProperty("JButton.buttonType", "accent");
        archiveNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) if ((Boolean) archiveTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Archive", "Archiving", selectedRows, archiveTableModel, restoreTableModel, (folder, name, update) -> {
                try {
                    archiveService.moveToArchiveWithProgress(folder, name, update);
                    return true;
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog, 
                            "Failed to archive '" + name + "':\n" + ex.getMessage(), 
                            "Archive Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return false;
                }
            });
        });

        JPanel archiveActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        archiveActionPanel.add(archiveNowBtn);
        archiveBottom.add(archiveCountLabel, BorderLayout.WEST);
        archiveBottom.add(archiveActionPanel, BorderLayout.EAST);
        archivePanel.add(archiveBottom, BorderLayout.SOUTH);

        // --- TAB 2: RESTORE (Archive -> Models) ---
        JPanel restorePanel = new JPanel(new BorderLayout(10, 10));
        restorePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTable restoreTable = new JTable(restoreTableModel);
        restoreTable.setRowHeight(25);
        restoreTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Enable sorting with custom comparator for human-readable sizes
        javax.swing.table.TableRowSorter<DefaultTableModel> restoreSorter = new javax.swing.table.TableRowSorter<>(restoreTableModel);
        restoreSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        restoreSorter.setSortable(0, false); // Disable sorting for checkbox column to allow "Select All"
        restoreTable.setRowSorter(restoreSorter);

        // Add "Select All" functionality to header
        restoreTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = restoreTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = restoreTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        if (!(Boolean) restoreTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        restoreTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane restoreScroll = new JScrollPane(restoreTable);
        restorePanel.add(restoreScroll, BorderLayout.CENTER);

        JPanel restoreBottom = new JPanel(new BorderLayout());
        JLabel restoreCountLabel = new JLabel("Selected: 0 models");
        restoreTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                if ((Boolean) restoreTableModel.getValueAt(i, 0)) selected++;
            }
            restoreCountLabel.setText("Selected: " + selected + " models");
        });

        JButton restoreNowBtn = new JButton("🚀 Restore from Archive");
        restoreNowBtn.putClientProperty("JButton.buttonType", "accent");
        restoreNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) if ((Boolean) restoreTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Restore", "Restoring", selectedRows, restoreTableModel, archiveTableModel, (folder, name, update) -> {
                return archiveService.restoreFromArchiveWithProgress(folder, name, update);
            });
        });

        JPanel restoreActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        restoreActionPanel.add(restoreNowBtn);
        restoreBottom.add(restoreCountLabel, BorderLayout.WEST);
        restoreBottom.add(restoreActionPanel, BorderLayout.EAST);
        restorePanel.add(restoreBottom, BorderLayout.SOUTH);

        tabs.addTab("📦 Archive Models", archivePanel);
        tabs.addTab("🚀 Restore Models", restorePanel);
        
        dialog.add(tabs, BorderLayout.CENTER);
        
        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        dialogButtons.add(closeBtn);
        dialog.add(dialogButtons, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    private static class ArchiveWorkItem {
        int index;
        String folder, name, size;
        ArchiveWorkItem(int i, String f, String n, String s) { index = i; folder = f; name = n; size = s; }
    }

    private JPanel createManagerPanel(JTabbedPane tabs) {
        JPanel managerPanel = new JPanel(new BorderLayout(10, 10));
        managerPanel.setOpaque(false);
        managerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        toolbar.setOpaque(false);
        toolbar.putClientProperty("FlatLaf.style", "background: $Toolbar.customBg");
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));

        JButton verifyBtn = new JButton("🔍 Quick Check");
        verifyBtn.putClientProperty("JButton.buttonType", "roundRect");
        verifyBtn.addActionListener(e -> verifyLocalModels(false));
        
        JButton optimizeBtn = new JButton("👯 Storage Optimizer");
        optimizeBtn.putClientProperty("JButton.buttonType", "roundRect");
        optimizeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "The storage optimizer calculates SHA-256 hashes for all local models.\n" +
                "This is EXTREMELY slow and resource-intensive for large libraries.\n\n" +
                "Do you want to continue?", "Performance Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                verifyLocalModels(true);
            }
        });

        JButton archiveBtn = new JButton("📦 Archive...");
        archiveBtn.putClientProperty("JButton.buttonType", "roundRect");
        archiveBtn.addActionListener(e -> showArchiveDialog());

        JButton diagnosticBtn = new JButton("🩺 Diagnostics");
        diagnosticBtn.putClientProperty("JButton.buttonType", "roundRect");
        diagnosticBtn.addActionListener(e -> {
            if (modelsToDownload == null || modelsToDownload.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please load a workflow first to perform diagnostics.");
                return;
            }
            List<String> missing = diagnosticService.getMissingModels(modelsToDownload);
            if (missing.isEmpty()) {
                JOptionPane.showMessageDialog(this, "✅ All workflow models are visible to ComfyUI!", "Diagnostics Successful", JOptionPane.INFORMATION_MESSAGE);
            } else {
                String list = String.join("\n- ", missing);
                Object[] options = {"Switch to Overview (Restart)", "Ignore"};
                int choice = JOptionPane.showOptionDialog(this, 
                    "❌ ComfyUI still reports these models as missing:\n- " + list + "\n\n" +
                    "A restart is required for ComfyUI to recognize new models.", 
                    "Diagnostics Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                
                if (choice == 0) tabs.setSelectedIndex(0);
            }
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

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel jsonPanel = new JPanel(new BorderLayout());
        jsonPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        jsonPanel.setOpaque(false);
        jsonPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5), "Workflow (Drag & Drop JSON/PNG)"));
        
        jsonInputArea = new JTextArea();
        setupDragAndDrop(jsonInputArea);
        JScrollPane jsonScroll = new JScrollPane(jsonInputArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        
        JPanel jsonButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        jsonButtons.setOpaque(false);
        JButton loadJsonBtn = new JButton("Load Workflow...");
        loadJsonBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Workflow", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) loadFile(new File(fd.getDirectory(), fd.getFile()));
        });

        JButton importModelListBtn = new JButton("Import Model List...");
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                try {
                    modelListService.importJson(new File(fd.getDirectory(), fd.getFile()));
                    JOptionPane.showMessageDialog(this, "Model list successfully imported! (" + modelListService.getModels().size() + " models)");
                    analyzeJsonContent();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        JButton analyzeBtn = new JButton("Deep Search");
        analyzeBtn.putClientProperty("JButton.buttonType", "accent");
        analyzeBtn.addActionListener(e -> { analyzeJsonContent(); searchMissingOnline(); });
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
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) updateDownloadManagerSelection();
        });
        JTable modelTable = new JTable(tableModel);
        modelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Configure table column model with preferred column widths representing percentages
        modelTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        javax.swing.table.TableColumnModel colModel = modelTable.getColumnModel();
        
        // 0: Select (3% / min 40px)
        javax.swing.table.TableColumn col0 = colModel.getColumn(0);
        col0.setPreferredWidth(40);
        col0.setMinWidth(40);
        col0.setMaxWidth(60);
        
        // 1: Type (10%)
        colModel.getColumn(1).setPreferredWidth(115);
        
        // 2: Name (15%)
        colModel.getColumn(2).setPreferredWidth(172);
        
        // 3: Size (5%)
        colModel.getColumn(3).setPreferredWidth(58);
        
        // 4: AI Source (10%)
        colModel.getColumn(4).setPreferredWidth(115);
        
        // 5: Target Path (10%)
        colModel.getColumn(5).setPreferredWidth(115);
        
        // 6: URL (25%)
        colModel.getColumn(6).setPreferredWidth(288);
        
        // 7: Status (22%)
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
                showCivitaiSearchDialog(modelRow);
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
        
        workflowGraphPanel = new de.tki.comfymodels.ui.WorkflowGraphPanel();
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
        downloadButton.addActionListener(e -> startDownloadQueue());
        
        pauseButton = new JButton("Pause");
        pauseButton.putClientProperty("JButton.buttonType", "roundRect");
        pauseButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        pauseButton.addActionListener(e -> {
            downloadManager.togglePause();
            SwingUtilities.invokeLater(() -> pauseButton.setText(downloadManager.isPaused() ? "Resume" : "Pause"));
        });
        
        stopButton = new JButton("Stop");
        stopButton.putClientProperty("JButton.buttonType", "roundRect");
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        stopButton.addActionListener(e -> downloadManager.stop());
        
        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        managerPanel.add(toolbar, BorderLayout.NORTH);
        managerPanel.add(splitPane, BorderLayout.CENTER);
        managerPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        return managerPanel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        Font btnFont = new Font("SansSerif", Font.BOLD, 14);
        Font checkFont = new Font("SansSerif", Font.PLAIN, 14);

        JPanel grid = new JPanel(new GridLayout(1, 2, 25, 0));
        grid.setOpaque(false);
        
        // Left Column: General & Paths
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 20,20,20,20,$Card.border,1,16");
        left.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel pathsHeader = new JLabel("General & Paths");
        pathsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        pathsHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton pathsBtn = new JButton("📁 Configure Directories...");
        pathsBtn.setFont(btnFont);
        pathsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pathsBtn.setMaximumSize(new Dimension(360, 40));
        pathsBtn.addActionListener(e -> showPathsDialog());

        JButton repairBtn = new JButton("🛠️ Repair Environment Automatically...");
        repairBtn.setFont(btnFont);
        repairBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        repairBtn.setMaximumSize(new Dimension(360, 40));
        repairBtn.addActionListener(e -> triggerEnvironmentRepair());

        JButton downloadSettingsBtn = new JButton("📥 Download Settings...");
        downloadSettingsBtn.setFont(btnFont);
        downloadSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        downloadSettingsBtn.setMaximumSize(new Dimension(360, 40));
        downloadSettingsBtn.addActionListener(e -> showDownloadSettingsDialog());

        JButton bridgeBtn = new JButton("🚀 Install ComfyUI Bridge...");
        bridgeBtn.setFont(btnFont);
        bridgeBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        bridgeBtn.setMaximumSize(new Dimension(360, 40));
        bridgeBtn.addActionListener(e -> showInstallationDialog());

        JPanel checksPanel = new JPanel();
        checksPanel.setOpaque(false);
        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        checksPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        backgroundCheck = new JCheckBox("Run in background");
        backgroundCheck.setFont(checkFont);
        backgroundCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));
        
        shutdownCheck = new JCheckBox("Shutdown after completion");
        shutdownCheck.setFont(checkFont);
        shutdownCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));
        
        restartCheck = new JCheckBox("Restart after completion");
        restartCheck.setFont(checkFont);
        restartCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        restartCheck.addActionListener(e -> configService.setRestartAfterDownloadEnabled(restartCheck.isSelected()));
        
        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.setFont(checkFont);
        darkCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        darkCheck.addActionListener(e -> {
            boolean isDark = darkCheck.isSelected();
            configService.setDarkMode(isDark);
            FlatAnimatedLafChange.showSnapshot();
            setupTheme(isDark);
            revalidate();
            repaint();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });

        fastHashCheck = new JCheckBox("Fast hashing (AutoV1)");
        fastHashCheck.setFont(checkFont);
        fastHashCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fastHashCheck.addActionListener(e -> configService.setFastHashEnabled(fastHashCheck.isSelected()));

        hideComfyuiCheck = new JCheckBox("Hide ComfyUI Web Client (Replacement Mode)");
        hideComfyuiCheck.setFont(checkFont);
        hideComfyuiCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        hideComfyuiCheck.setSelected(configService.isHideComfyUI());
        hideComfyuiCheck.addActionListener(e -> configService.setHideComfyUI(hideComfyuiCheck.isSelected()));

        checksPanel.add(backgroundCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(shutdownCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(restartCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(darkCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(fastHashCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(hideComfyuiCheck);

        left.add(pathsHeader);
        left.add(Box.createVerticalStrut(20));
        left.add(pathsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(repairBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(downloadSettingsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(bridgeBtn);
        left.add(Box.createVerticalStrut(25));
        left.add(checksPanel);
        left.add(Box.createVerticalGlue());

        // Right Column: AI & Help
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 20,20,20,20,$Card.border,1,16");
        right.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel aiHeader = new JLabel("AI & Support");
        aiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        aiHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton apiBtn = new JButton("🔑 AI & API Keys...");
        apiBtn.setFont(btnFont);
        apiBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        apiBtn.setMaximumSize(new Dimension(360, 40));
        apiBtn.addActionListener(e -> showApiKeysDialog());

        JButton helpBtn = new JButton("ℹ Show Help & Instructions");
        helpBtn.setFont(btnFont);
        helpBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        helpBtn.setMaximumSize(new Dimension(360, 40));
        helpBtn.addActionListener(e -> showHelpDialog());

        JButton resetVaultBtn = new JButton("🔒 Reset Secure Vault...");
        resetVaultBtn.setFont(btnFont);
        resetVaultBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        resetVaultBtn.setMaximumSize(new Dimension(360, 40));
        resetVaultBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "This will delete all stored API keys. Continue?", "Reset Vault", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                configService.resetVault();
            }
        });

        right.add(aiHeader);
        right.add(Box.createVerticalStrut(20));
        right.add(apiBtn);
        right.add(Box.createVerticalStrut(10));
        right.add(resetVaultBtn);
        right.add(Box.createVerticalStrut(25));
        right.add(helpBtn);
        right.add(Box.createVerticalGlue());

        grid.add(left);
        grid.add(right);
        panel.add(grid, BorderLayout.CENTER);
        
        return panel;
    }

    private void triggerEnvironmentRepair() {
        if (mainTabs != null) {
            mainTabs.setSelectedIndex(0);
        } else if (getContentPane() instanceof JTabbedPane) {
            ((JTabbedPane) getContentPane()).setSelectedIndex(0);
        } else {
            findAndSelectTab(getContentPane(), 0);
        }
        
        if (consoleOutput != null) {
            consoleOutput.setText("");
        }
        
        new Thread(() -> {
            updaterService.repairEnvironment(log -> SwingUtilities.invokeLater(() -> {
                if (consoleOutput != null) {
                    consoleOutput.append(log);
                    consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
                }
            }));
        }).start();
    }

    private void performFullServiceRestart() {
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("🚀 Requesting restart via Dashboard...");
            });
            
            // Core logic: Stop via process controller
            processController.stop();
            
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            
            // Switch to Dashboard and start ComfyUI again using active profile
            SwingUtilities.invokeLater(() -> {
                if (getContentPane() instanceof JTabbedPane) {
                    ((JTabbedPane) getContentPane()).setSelectedIndex(0);
                }
                
                de.tki.comfymodels.domain.LaunchProfile activeProfile = null;
                if (profileList != null) {
                    activeProfile = profileList.getSelectedValue();
                }
                if (activeProfile == null) {
                    String activeId = configService.getActiveProfile();
                    List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    activeProfile = profiles.stream()
                        .filter(p -> p.id().equals(activeId))
                        .findFirst()
                        .orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) {
                        activeProfile = profiles.get(0);
                    }
                }
                
                if (activeProfile != null) {
                    if (profileList != null) {
                        profileList.setSelectedValue(activeProfile, true);
                    }
                    if (consoleOutput != null) {
                        consoleOutput.append("\n🔄 Restarting ComfyUI...\n");
                    }
                    startComfyUI(activeProfile, false, true);
                } else {
                    statusLabel.setText("⚠️ No launch profile available to start ComfyUI.");
                }
            });
        }).start();
    }

    private void triggerPostOperationActions() {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this, 
                "Operation finished successfully.\nDo you want to restart ComfyUI now?", 
                "Restart ComfyUI?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            
            if (choice == JOptionPane.YES_OPTION) {
                performFullServiceRestart();
            }
            
            if (configService.isShutdownAfterDownloadEnabled()) performSystemShutdown();
        });
    }

    private void refreshProfileList(DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> model) {
        model.clear();
        List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
        
        // Add a default local profile if none exist
        if (profiles.isEmpty()) {
            de.tki.comfymodels.domain.LaunchProfile def = new de.tki.comfymodels.domain.LaunchProfile(
                "default",
                "Default (Local)",
                "Starts ComfyUI with standard local settings (--listen 127.0.0.1).",
                false, "python", 
                List.of("--listen", "127.0.0.1", "--port", "8188"), new HashMap<>()
            );
            profiles.add(def);
            try {
                profileManager.saveProfiles(profiles);
            } catch (IOException ignored) {}
        }

        for (de.tki.comfymodels.domain.LaunchProfile p : profiles) {
            model.addElement(p);
        }

        // Restore active profile selection
        String activeId = configService.getActiveProfile();
        int targetIdx = -1;
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(activeId)) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx == -1 && !model.isEmpty()) {
            targetIdx = 0;
        }
        if (profileList != null && targetIdx != -1) {
            profileList.setSelectedIndex(targetIdx);
        }
    }

    private void refreshVersions() {
        // Local checks (fast)
        String localComfy = versionService.getInstalledComfyVersion(configService.getComfyUIPath());
        String localPython = versionService.getInstalledPythonVersion(configService.getPythonPath());
        String remotePython = versionService.getRemotePythonVersion();

        lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: Fetching...)");
        lblPythonVersion.setText("Python: " + localPython + " (Latest: " + remotePython + ")");

        // Remote async check
        versionService.getRemoteComfyVersionAsync().thenAccept(remoteComfy -> {
            SwingUtilities.invokeLater(() -> {
                if (lblComfyVersion != null) {
                    lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: " + remoteComfy + ")");
                }
            });
        });
    }

    private void updateHardwareUI(de.tki.comfymodels.service.impl.HardwareMonitorService.HardwareStats stats) {
        if (progressCpu != null) {
            progressCpu.setValue((int) stats.cpuLoad);
            JLabel label = (JLabel) progressCpu.getClientProperty("valueLabel");
            if (label != null) {
                label.setText(String.format("%.1f%%", stats.cpuLoad));
            } else {
                progressCpu.setString(String.format("CPU: %.1f%%", stats.cpuLoad));
            }
        }
        if (progressRam != null) {
            double usedGb = stats.ramUsed / (1024.0 * 1024.0 * 1024.0);
            double totalGb = stats.ramTotal / (1024.0 * 1024.0 * 1024.0);
            int pct = stats.ramTotal > 0 ? (int) ((stats.ramUsed * 100) / stats.ramTotal) : 0;
            progressRam.setValue(pct);
            JLabel label = (JLabel) progressRam.getClientProperty("valueLabel");
            if (label != null) {
                label.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            } else {
                progressRam.setString(String.format("RAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            }
        }
        if (stats.hasNvidia) {
            if (gpuPanel != null) gpuPanel.setVisible(true);
            if (progressGpu != null) {
                progressGpu.setValue(stats.gpuUtilization);
                JLabel label = (JLabel) progressGpu.getClientProperty("valueLabel");
                if (label != null) {
                    label.setText(String.format("%d%%", stats.gpuUtilization));
                } else {
                    progressGpu.setString(String.format("GPU Load: %d%%", stats.gpuUtilization));
                }
            }
            if (progressVram != null) {
                double usedGb = stats.vramUsed / (1024.0 * 1024.0 * 1024.0);
                double totalGb = stats.vramTotal / (1024.0 * 1024.0 * 1024.0);
                int pct = stats.vramTotal > 0 ? (int) ((stats.vramUsed * 100) / stats.vramTotal) : 0;
                progressVram.setValue(pct);
                JLabel label = (JLabel) progressVram.getClientProperty("valueLabel");
                if (label != null) {
                    label.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
                } else {
                    progressVram.setString(String.format("VRAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
                }
            }
            if (lblGpuName != null) {
                lblGpuName.setText("GPU: " + stats.gpuName);
            }
        } else {
            if (gpuPanel != null) gpuPanel.setVisible(false);
        }
    }

    public String getWorkspaceWorkflowJson() {
        return jsonInputArea != null ? jsonInputArea.getText() : "";
    }

    public void loadWorkflowExternal(String name, String json) {
        SwingUtilities.invokeLater(() -> {
            if (jsonInputArea != null) {
                jsonInputArea.setText(json);
                currentFileName = name != null ? name + ".json" : "library_workflow.json";
                analyzeJsonContent();
                searchMissingOnline();
                
                // Switch to Model Manager Tab (index 1)
                if (getContentPane() instanceof JTabbedPane) {
                    ((JTabbedPane) getContentPane()).setSelectedIndex(1);
                } else {
                    findAndSelectTab(getContentPane(), 1);
                }
            }
        });
    }

    private void findAndSelectTab(Container container, int index) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JTabbedPane) {
                ((JTabbedPane) child).setSelectedIndex(index);
                return;
            } else if (child instanceof Container) {
                findAndSelectTab((Container) child, index);
            }
        }
    }

    private void showModalProgressDialog(String title, String statusPrefix, List<Integer> selectedRows, 
                                        DefaultTableModel sourceModel, DefaultTableModel targetModel, TaskExecutor executor) {
        JDialog progressDialog = new JDialog(this, title, true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.setSize(500, 180);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel(statusPrefix + "...");
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 30));

        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel, BorderLayout.CENTER);

        // Pre-collect data on EDT
        List<ArchiveWorkItem> workItems = new ArrayList<>();
        long totalBytes = 0;
        for (int row : selectedRows) {
            String sizeStr = (String) sourceModel.getValueAt(row, 3);
            workItems.add(new ArchiveWorkItem(row, (String) sourceModel.getValueAt(row, 1), (String) sourceModel.getValueAt(row, 2), sizeStr));
            totalBytes += parseSizeToBytes(sizeStr);
        }
        final long finalTotalBytes = totalBytes > 0 ? totalBytes : 1; 

        new Thread(() -> {
            int successCount = 0;
            List<Object[]> rowsToMove = new ArrayList<>();
            List<Integer> processedRowIndices = new ArrayList<>();
            long[] bytesProcessedTotal = {0};

            for (int i = 0; i < workItems.size(); i++) {
                ArchiveWorkItem item = workItems.get(i);
                final int fileIndex = i + 1;
                SwingUtilities.invokeLater(() -> label.setText(statusPrefix + " (" + fileIndex + "/" + workItems.size() + "): " + item.name));

                if (executor.execute(item.folder, item.name, (bytesDelta) -> {
                    bytesProcessedTotal[0] += bytesDelta;
                    final long current = bytesProcessedTotal[0];
                    final int percent = (int) Math.min(100, (current * 100) / finalTotalBytes);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "% (" + searchService.formatSize(current) + " / " + searchService.formatSize(finalTotalBytes) + ")");
                    });
                })) {
                    successCount++;
                    processedRowIndices.add(item.index);
                    rowsToMove.add(new Object[]{false, item.folder, item.name, item.size});
                }
            }

            final int finalSuccess = successCount;
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                processedRowIndices.sort((a, b) -> b.compareTo(a));
                for (int r : processedRowIndices) sourceModel.removeRow(r);
                for (Object[] rowData : rowsToMove) targetModel.addRow(rowData);
                
                analyzeJsonContent();
                triggerPostOperationActions();
                
                JOptionPane.showMessageDialog(this, finalSuccess + " models successfully " + title.toLowerCase() + "ed.", 
                    "Operation Complete", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();

        progressDialog.setVisible(true);
    }

    private long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.equals("Unknown") || sizeStr.isEmpty()) return 0;
        try {
            String[] parts = sizeStr.trim().split("\\s+");
            double val = Double.parseDouble(parts[0].replace(",", "."));
            if (parts.length < 2) return (long) val;
            String unit = parts[1].toUpperCase();
            if (unit.contains("GB")) return (long) (val * 1024 * 1024 * 1024);
            if (unit.contains("MB")) return (long) (val * 1024 * 1024);
            if (unit.contains("KB")) return (long) (val * 1024);
            return (long) val;
        } catch (Exception e) { return 0; }
    }

    @FunctionalInterface
    interface TaskExecutor {
        boolean execute(String folder, String name, java.util.function.LongConsumer progressUpdate);
    }

    private void updateDownloadManagerSelection() {
        if (downloadManager == null || tableModel == null) return;
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        downloadManager.updateSelection(selected);
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
                    java.util.List<File> files = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) loadFile(files.get(0));
                } catch (Exception e) {}
            }
        });
    }

    private JPanel createSlimStatPanel(String title, JProgressBar bar) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        
        JLabel titleLbl = new JLabel(title) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.titleForeground");
                if (c != null) {
                    setForeground(c);
                }
            }
        };
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color tc = UIManager.getColor("SlimStat.titleForeground");
        if (tc != null) titleLbl.setForeground(tc);
        
        JLabel valLbl = new JLabel("--") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.valueForeground");
                if (c != null) {
                    setForeground(c);
                }
            }
        };
        valLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color vc = UIManager.getColor("SlimStat.valueForeground");
        if (vc != null) valLbl.setForeground(vc);
        
        // Save references in client properties of the progress bar so we can update the labels in updateHardwareUI
        bar.putClientProperty("titleLabel", titleLbl);
        bar.putClientProperty("valueLabel", valLbl);
        
        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.add(titleLbl, BorderLayout.WEST);
        labelRow.add(valLbl, BorderLayout.EAST);
        
        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 6));
        bar.putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");
        
        p.add(labelRow, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        return p;
    }

    private void performSystemShutdown() {
        de.tki.comfymodels.util.PlatformUtils.shutdownSystem();
    }

    private void showHelpDialog() {
        JDialog helpDialog = new JDialog(this, "User Guide & Documentation", true);
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(950, 750);
        helpDialog.setLocationRelativeTo(this);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        
        java.awt.Color panelBg = javax.swing.UIManager.getColor("Panel.background");
        java.awt.Color labelFg = javax.swing.UIManager.getColor("Label.foreground");
        
        boolean darkMode = configService.isDarkMode();
        String bgColor = panelBg != null ? String.format("#%02x%02x%02x", panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue()) : (darkMode ? "#121214" : "#f8fafc");
        String textColor = labelFg != null ? String.format("#%02x%02x%02x", labelFg.getRed(), labelFg.getGreen(), labelFg.getBlue()) : (darkMode ? "#e2e8f0" : "#1e293b");
        
        String accentColor = darkMode ? "#818cf8" : "#4f46e5";
        String boxBg = darkMode ? "#1a1a24" : "#ffffff";
        String boxBorder = darkMode ? "#2d2d3d" : "#e2e8f0";
        String tipBg = darkMode ? "#222230" : "#f1f5f9";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='padding: 25px; font-family: sans-serif; background-color: ").append(bgColor).append("; color: ").append(textColor).append(";'>");
        sb.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 25px;'>🚀 ComfyUI Companion</h1>");
        
        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h2 style='color: ").append(accentColor).append("; margin-top: 0;'>🛠️ Getting Started (Setup)</h2>");
        sb.append("<ol>");
        sb.append("<li><b>Set Directories:</b> Navigate to <i>Settings -> Directories</i> and specify your ComfyUI root and 'models' paths. Choose a 'Cold Archive' path (e.g. on a high-capacity HDD) to offload unused models.</li>");
        sb.append("<li><b>Unlock Vault:</b> Establish a Master Password on first startup. This AES-256 encrypted vault securely stores your sensitive API keys.</li>");
        sb.append("<li><b>Configure API Keys:</b> Go to <i>Settings -> AI & API Keys</i> to add your Civitai API Key, Hugging Face Token, and Gemini API Key. A Gemini key is highly recommended for prompt optimizations.</li>");
        sb.append("<li><b>Install Canvas Bridge:</b> Click <i>Settings -> ComfyUI Bridge...</i> to add the 🚀 floating button to your ComfyUI workspace for seamless 1-click canvas syncing.</li>");
        sb.append("</ol></div>");

        sb.append("<h2 style='color: ").append(accentColor).append(";'>✨ Core Features</h2>");
        
        sb.append("<p><b>📦 Archive Manager:</b> Reclaim SSD space. Move massive, rarely used models (SDXL checkpoints, Flux models, etc.) to your cold storage archive. The app automatically detects when a loaded workflow needs archived models and offers <b>one-click restoration</b>.</p>");
        
        sb.append("<p><b>🧼 Storage Optimizer:</b> Automatically scans model folders for duplicate files using SHA-256 and Fast AutoV1 Hashing. Clean up redundant copies or instantly merge duplicates into NTFS hardlinks to preserve folder structure while freeing up physical space.</p>");
        
        sb.append("<p><b>🎨 Prompt Lab & AI Optimization:</b> Draft and refine prompts, select styles, and pick optional environmental contexts (including 'None (Optional)'). Features a powerful <b>Optimize with AI</b> button which detects the target model architecture (SD 1.5, SDXL, Flux) and rewrites prompts to suit that target.</p>");

        sb.append("<p><b>🔍 Model Update Checker & Tracker:</b> Scans your local models and checks Civitai to see if newer versions or upgrades are available. Download and update your models with a single click.</p>");

        sb.append("<p><b>🖼️ Output Gallery:</b> Live monitoring of the ComfyUI output directory. Renders images and videos asynchronously with support for selection checkboxes, bulk deletion, and file actions without slowing down the UI.</p>");

        sb.append("<p><b>📊 Workflow Graph Preview:</b> Displays a visual diagram of the ComfyUI nodes and linkages directly in the companion app whenever a workflow is loaded or synced.</p>");

        sb.append("<p><b>⚡ System Monitoring & Server Lifecycle:</b> Keeps track of real-time CPU, RAM, and VRAM utilization. Automatically cleans up active ports to resolve server startup crashes, streams server logs, and refreshes the browser automatically upon reconnect.</p>");

        sb.append("<p><b>🔌 Automatic App & Node Updater:</b> Keeps ComfyUI and installed Custom Nodes up-to-date with 1-click updates that run `git pull` and set up virtual environments automatically.</p>");

        sb.append("<div style='background-color: ").append(tipBg).append("; padding: 15px; border-radius: 8px; margin-top: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<b>💡 Pro Tips:</b><br>");
        sb.append("• <b>Drag & Drop:</b> Drag any workflow JSON or generated image (.png with metadata) directly into the app window to import its model list.<br>");
        sb.append("• <b>Overnight Queue:</b> Enable 'Shutdown after Queue' in the downloader to automatically turn off the computer when downloads are done.<br>");
        sb.append("• <b>System Tray:</b> Enable 'Stay in Background' so the app minimizes to the system tray instead of closing.<br>");
        sb.append("• <b>Launch Profiles:</b> Select from 10 optimized launch arguments (like CPU Mode, Low VRAM, Beast Mode, or FP8 Flux optimizations) matching your hardware.");
        sb.append("</div>");
        
        sb.append("<p style='margin-top: 30px; color: #888; font-style: italic; text-align: center;'>Version 1.1.0 - Open Source Software developed by TKI</p>");
        sb.append("</body></html>");

        editorPane.setText(sb.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane, 
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        helpDialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> helpDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        helpDialog.add(btnPanel, BorderLayout.SOUTH);

        helpDialog.setVisible(true);
    }

    private void syncBridgeFiles() {
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isEmpty()) return;

        Path comfyDir = Paths.get(comfyPath);
        if (!Files.exists(comfyDir)) return;

        Path customNodesDir = pathResolver.findCustomNodes(comfyDir);
        if (customNodesDir == null) return;

        Path targetDir = customNodesDir.resolve("comfyuicompanion");
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            // Always sync latest code files from resources to target
            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());
            
            // Also update config
            writeExtensionConfig(targetDir.toFile());
            configService.updateExtraModelPathsYaml();
            System.out.println("[Bridge-Sync] Successfully synchronized latest bridge code and token.");
        } catch (IOException e) {
            System.err.println("[Bridge-Sync] Failed to sync code: " + e.getMessage());
        }
    }

    private void startComfyUI(de.tki.comfymodels.domain.LaunchProfile selected, boolean clearConsole, boolean openBrowser) {
        if (selected == null) return;
        if (launchBtn != null) launchBtn.setEnabled(false);
        if (clearConsole && consoleOutput != null) {
            consoleOutput.setText("");
        }
        java.util.concurrent.atomic.AtomicBoolean cudaError = new java.util.concurrent.atomic.AtomicBoolean(false);
        processController.start(selected, Paths.get(configService.getComfyUIPath()), configService.getPythonPath(), log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                cudaError.set(true);
            }
            if (log.contains("To see the GUI go to:")) {
                refreshPromptLabModels();
                int idx = log.indexOf("http://");
                if (idx == -1) idx = log.indexOf("https://");
                if (idx != -1) {
                    String url = log.substring(idx).trim();
                    new Thread(() -> {
                        try {
                            if (Desktop.isDesktopSupported() && openBrowser) {
                                Desktop.getDesktop().browse(new java.net.URI(url));
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to open browser automatically: " + e.getMessage());
                        }
                    }).start();
                    SwingUtilities.invokeLater(() -> {
                        if (mainTabs != null) {
                            for (int i = 0; i < mainTabs.getTabCount(); i++) {
                                if (mainTabs.getTitleAt(i).contains("Model Manager")) {
                                    mainTabs.setSelectedIndex(i);
                                    break;
                                }
                            }
                        }
                    });
                }
            }
            SwingUtilities.invokeLater(() -> {
                consoleOutput.append(log + "\n");
                consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
            });
        }).thenAccept(exitCode -> {
            if (exitCode != 0 && cudaError.get()) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(this,
                        "ComfyUI failed to start because CUDA is not enabled/supported on this PyTorch installation.\n" +
                        "Would you like to switch to 'CPU Mode' profile and start ComfyUI on your CPU?",
                        "CUDA Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                        de.tki.comfymodels.domain.LaunchProfile cpuProfile = profiles.stream()
                            .filter(p -> p.id().equals("cpu_mode"))
                            .findFirst().orElse(null);
                        if (cpuProfile != null) {
                            if (profileList != null) {
                                profileList.setSelectedValue(cpuProfile, true);
                            }
                            startComfyUI(cpuProfile, true, true);
                        } else {
                            JOptionPane.showMessageDialog(this, "CPU Mode profile not found. Please add or use a custom profile with '--cpu'.");
                        }
                    }
                });
            }
        });
    }

    private static class CivitaiModel {
        String name;
        String type;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiModel(String name, String type, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.type = type;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name + " (" + type + ")";
        }
    }

    private static class CivitaiVersion {
        String name;
        String downloadUrl;
        String size;
        long byteSize;
        String imageUrl;
        List<String> triggerWords;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiVersion(String name, String downloadUrl, String size, long byteSize, String imageUrl, List<String> triggerWords, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.byteSize = byteSize;
            this.imageUrl = imageUrl;
            this.triggerWords = triggerWords;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name;
        }
    }

    private void showCivitaiSearchDialog(int row) {
        String initialName = (String) tableModel.getValueAt(row, 2);
        String cleanedQuery = initialName.replaceAll("(?i)\\.(safetensors|ckpt|sft|pt|bin)$", "").trim();

        JDialog dialog = new JDialog(this, "Civitai Version Selector", true);
        dialog.setSize(950, 650);
        dialog.setLocationRelativeTo(this);
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
        
        DefaultListModel<CivitaiModel> modelListModel = new DefaultListModel<>();
        JList<CivitaiModel> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(modelList);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        versionPanel.add(new JLabel("Version:"));
        JComboBox<CivitaiVersion> versionCombo = new JComboBox<>();
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
                        com.fasterxml.jackson.databind.JsonNode items = root.get("items");
                        if (items != null && items.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                                String name = item.path("name").asText("Unknown");
                                String type = item.path("type").asText("Unknown");
                                modelListModel.addElement(new CivitaiModel(name, type, item));
                            }
                        }
                    }
                    if (modelListModel.isEmpty()) {
                        modelListModel.addElement(new CivitaiModel("No results found", "", null));
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    modelListModel.addElement(new CivitaiModel("Error: " + ex.getMessage(), "", null));
                });
                return null;
            });
        };

        searchBtn.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());

        modelList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CivitaiModel selectedModel = modelList.getSelectedValue();
            versionCombo.removeAllItems();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("No Preview Image Available");
            
            if (selectedModel == null || selectedModel.rawNode == null) return;
            
            com.fasterxml.jackson.databind.JsonNode versions = selectedModel.rawNode.get("modelVersions");
            if (versions != null && versions.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode v : versions) {
                    String vName = v.path("name").asText("Unknown");
                    String downloadUrl = v.path("downloadUrl").asText("");
                    
                    long byteSize = 0;
                    String sizeStr = "Unknown";
                    com.fasterxml.jackson.databind.JsonNode files = v.get("files");
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
                    com.fasterxml.jackson.databind.JsonNode images = v.get("images");
                    if (images != null && images.isArray() && images.size() > 0) {
                        imgUrl = images.get(0).path("url").asText(null);
                    }
                    
                    List<String> triggers = new ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode trainedWords = v.get("trainedWords");
                    if (trainedWords != null && trainedWords.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode word : trainedWords) {
                            triggers.add(word.asText());
                        }
                    }
                    
                    versionCombo.addItem(new CivitaiVersion(vName, downloadUrl, sizeStr, byteSize, imgUrl, triggers, v));
                }
            }
        });

        versionCombo.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("Loading Preview...");
            
            if (selectedVersion == null) {
                previewLabel.setText("No Preview Image Available");
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("File Size: ").append(selectedVersion.size).append("\n");
            sb.append("Download URL: ").append(selectedVersion.downloadUrl).append("\n\n");
            
            if (!selectedVersion.triggerWords.isEmpty()) {
                sb.append("Trigger Words: ").append(String.join(", ", selectedVersion.triggerWords)).append("\n\n");
            }
            
            String desc = selectedVersion.rawNode.path("description").asText("");
            if (!desc.isEmpty()) {
                desc = desc.replaceAll("<[^>]*>", "");
                sb.append("Description:\n").append(desc);
            }
            
            infoText.setText(sb.toString());
            infoText.setCaretPosition(0);
            
            String imgUrl = selectedVersion.imageUrl;
            if (imgUrl != null && !imgUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(imgUrl);
                        BufferedImage img = javax.imageio.ImageIO.read(url);
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
                }).start();
            } else {
                previewLabel.setText("No Preview Image Available");
            }
        });

        applyBtn.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            CivitaiModel selectedModel = modelList.getSelectedValue();
            if (selectedVersion == null || selectedModel == null) {
                JOptionPane.showMessageDialog(dialog, "Please select a model and a version first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            ModelInfo info = modelsToDownload.get(row);
            
            String targetFileName = "";
            com.fasterxml.jackson.databind.JsonNode files = selectedVersion.rawNode.get("files");
            if (files != null && files.isArray() && files.size() > 0) {
                targetFileName = files.get(0).path("name").asText("");
            }
            if (targetFileName.isEmpty()) {
                targetFileName = info.getName();
            }
            
            info.setName(targetFileName);
            info.setUrl(selectedVersion.downloadUrl);
            info.setSize(selectedVersion.size);
            info.setByteSize(selectedVersion.byteSize);
            
            tableModel.setValueAt(true, row, 0);
            tableModel.setValueAt(targetFileName, row, 2);
            tableModel.setValueAt(selectedVersion.size, row, 3);
            tableModel.setValueAt(selectedVersion.downloadUrl, row, 6);
            tableModel.setValueAt("✅ Known Good", row, 7);
            
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Model version applied! Ready to download.", "Version Applied", JOptionPane.INFORMATION_MESSAGE);
        });

        SwingUtilities.invokeLater(() -> doSearch.run());
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);   
        context.getBean(Main.class).launch(args);
    }

    @Configuration @ComponentScan("de.tki.comfymodels") public static class AppConfig {}
}
