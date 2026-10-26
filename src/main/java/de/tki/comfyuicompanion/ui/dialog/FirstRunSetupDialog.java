package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Guided First-Run Setup Wizard assisting users with ComfyUI verification,
 * automatic model directory discovery, and initial workflow downloads.
 */
public class FirstRunSetupDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(FirstRunSetupDialog.class);

    private final ConfigService configService;
    private final Runnable onPathsUpdated;
    private final Runnable onOpenInstaller;
    private final Runnable onOpenBlueprintGallery;

    private JTextField txtModelsDir;
    private JLabel lblDetectedInfo;
    private JComboBox<CandidateModelDir> cmbCandidates;

    /**
     * Represents a candidate model storage directory discovered on disk.
     *
     * @param basePath   the base comfyuidata directory path
     * @param modelCount the number of model weight files detected inside
     * @param freeGb     available disk space in gigabytes
     */
    public record CandidateModelDir(String basePath, int modelCount, long freeGb) {
        @Override
        public String toString() {
            if (modelCount > 0) {
                return basePath + "  (" + modelCount + " models found · " + freeGb + " GB free)";
            }
            return basePath + "  (Empty / New · " + freeGb + " GB free)";
        }
    }

    /**
     * Constructs a new FirstRunSetupDialog.
     *
     * @param owner                  the parent window
     * @param configService          the configuration service
     * @param comfyInstalled         whether ComfyUI was verified as installed
     * @param onPathsUpdated         callback invoked after model paths are updated
     * @param onOpenInstaller        callback invoked when the user requests ComfyUI installation
     * @param onOpenBlueprintGallery callback invoked to navigate to the Blueprint Gallery tab
     */
    public FirstRunSetupDialog(Window owner,
                               ConfigService configService,
                               boolean comfyInstalled,
                               Runnable onPathsUpdated,
                               Runnable onOpenInstaller,
                               Runnable onOpenBlueprintGallery) {
        super(owner, "Welcome — Initial Environment & Model Setup", ModalityType.APPLICATION_MODAL);
        this.configService = configService;
        this.onPathsUpdated = onPathsUpdated;
        this.onOpenInstaller = onOpenInstaller;
        this.onOpenBlueprintGallery = onOpenBlueprintGallery;
        initUI(comfyInstalled);
    }

    private void initUI(boolean comfyInstalled) {
        setSize(680, 470);
        setMinimumSize(new Dimension(620, 440));
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(15, 15));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 15, 24));

        JLabel title = new JLabel("Welcome to Companion for ComfyUI");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(6));

        JLabel subtitle = new JLabel("<html>No local AI models were found in your currently configured model directory.<br>"
                + "Use this quick setup to link an existing model folder or download your first workflow.</html>");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(18));

        // Step 1: ComfyUI Installation Status
        JPanel step1 = new JPanel(new BorderLayout(10, 6));
        step1.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Step 1: ComfyUI Core Status"),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        step1.setAlignmentX(Component.LEFT_ALIGNMENT);
        step1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));

        String comfyPath = configService != null ? configService.getComfyUIPath() : "";
        JLabel lblComfy = new JLabel(comfyInstalled
                ? "✅ ComfyUI verified at: " + comfyPath
                : "⚠️ ComfyUI was not found in the default directory.");
        lblComfy.setFont(new Font("SansSerif", Font.BOLD, 12));
        step1.add(lblComfy, BorderLayout.CENTER);

        if (!comfyInstalled && onOpenInstaller != null) {
            JButton btnInstall = new JButton("Install ComfyUI...", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
            btnInstall.addActionListener(e -> {
                dispose();
                onOpenInstaller.run();
            });
            step1.add(btnInstall, BorderLayout.EAST);
        }
        content.add(step1);
        content.add(Box.createVerticalStrut(12));

        // Step 2: Model Directory Discovery
        JPanel step2 = new JPanel();
        step2.setLayout(new BoxLayout(step2, BoxLayout.Y_AXIS));
        step2.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Step 2: Model Storage Directory (comfyuidata)"),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        step2.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<CandidateModelDir> candidates = scanCandidateDirectories(configService);
        CandidateModelDir bestCandidate = candidates.stream()
                .filter(c -> c.modelCount() > 0)
                .findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));

        JLabel lblScan = new JLabel(bestCandidate != null && bestCandidate.modelCount() > 0
                ? "💡 Existing model directory detected on your system:"
                : "Select where downloaded models, inputs, and outputs should be stored:");
        lblScan.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblScan.setAlignmentX(Component.LEFT_ALIGNMENT);
        step2.add(lblScan);
        step2.add(Box.createVerticalStrut(6));

        cmbCandidates = new JComboBox<>(candidates.toArray(new CandidateModelDir[0]));
        cmbCandidates.setAlignmentX(Component.LEFT_ALIGNMENT);
        cmbCandidates.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        step2.add(cmbCandidates);
        step2.add(Box.createVerticalStrut(8));

        JPanel customRow = new JPanel(new BorderLayout(8, 0));
        customRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        customRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        String initialPath = bestCandidate != null ? bestCandidate.basePath()
                : (configService != null ? configService.getExtraComfyUIPath() : "");
        txtModelsDir = new JTextField(initialPath);
        customRow.add(txtModelsDir, BorderLayout.CENTER);

        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Model Storage Directory (comfyuidata)");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!txtModelsDir.getText().isBlank()) {
                chooser.setCurrentDirectory(new File(txtModelsDir.getText()));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                txtModelsDir.setText(selected.getAbsolutePath());
                updateDetectedLabel(selected.toPath());
            }
        });
        customRow.add(btnBrowse, BorderLayout.EAST);
        step2.add(customRow);
        step2.add(Box.createVerticalStrut(6));

        lblDetectedInfo = new JLabel(" ");
        lblDetectedInfo.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lblDetectedInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        step2.add(lblDetectedInfo);

        cmbCandidates.addActionListener(e -> {
            CandidateModelDir sel = (CandidateModelDir) cmbCandidates.getSelectedItem();
            if (sel != null) {
                txtModelsDir.setText(sel.basePath());
                updateDetectedLabel(Paths.get(sel.basePath()));
            }
        });
        if (bestCandidate != null) {
            cmbCandidates.setSelectedItem(bestCandidate);
            updateDetectedLabel(Paths.get(bestCandidate.basePath()));
        }

        content.add(step2);
        add(content, BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        JButton btnSkip = new JButton("Skip for Now");
        btnSkip.addActionListener(e -> dispose());

        JButton btnGallery = new JButton("Open Blueprint Gallery", SvgIconFactory.get(AppIcon.GALLERY, 14));
        btnGallery.setToolTipText("Browse workflows and download required models");
        btnGallery.addActionListener(e -> {
            applySelectedPath();
            dispose();
            if (onOpenBlueprintGallery != null) {
                onOpenBlueprintGallery.run();
            }
        });

        JButton btnApply = new JButton("Save & Apply Path", SvgIconFactory.get(AppIcon.CHECK, 14));
        btnApply.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnApply.addActionListener(e -> {
            applySelectedPath();
            dispose();
        });

        footer.add(btnSkip);
        footer.add(btnGallery);
        footer.add(btnApply);
        add(footer, BorderLayout.SOUTH);
    }

    private void updateDetectedLabel(Path baseDir) {
        if (baseDir == null) return;
        Path modelsSub = (baseDir.getFileName() != null && baseDir.getFileName().toString().equalsIgnoreCase("models"))
                ? baseDir : baseDir.resolve("models");
        int count = countModelFiles(modelsSub);
        if (count > 0) {
            lblDetectedInfo.setText("✅ Found " + count + " model file(s) in " + modelsSub);
        } else {
            lblDetectedInfo.setText("ℹ️ No models installed in this folder yet. You can download models from the Blueprint Gallery.");
        }
    }

    private void applySelectedPath() {
        if (configService == null) return;
        String chosen = txtModelsDir.getText().trim();
        if (!chosen.isEmpty()) {
            configService.setExtraComfyUIPath(chosen);
            logger.info("Updated extra ComfyUI path via Setup Wizard: {}", chosen);
            if (onPathsUpdated != null) {
                onPathsUpdated.run();
            }
        }
    }

    /**
     * Scans common directory locations on the system for existing model libraries.
     *
     * @param configService configuration service providing current paths
     * @return ordered list of discovered candidate directories
     */
    public static List<CandidateModelDir> scanCandidateDirectories(ConfigService configService) {
        Set<String> candidatePaths = new LinkedHashSet<>();
        if (configService != null && configService.getExtraComfyUIPath() != null && !configService.getExtraComfyUIPath().isBlank()) {
            candidatePaths.add(Paths.get(configService.getExtraComfyUIPath()).toAbsolutePath().normalize().toString());
        }
        candidatePaths.add(Paths.get(System.getProperty("user.home"), "comfyuidata").toAbsolutePath().normalize().toString());

        String[] commonLocations = {
                "C:\\AI\\comfyuidata", "D:\\AI\\comfyuidata", "E:\\AI\\comfyuidata",
                "C:\\comfyuidata", "D:\\comfyuidata"
        };
        for (String loc : commonLocations) {
            Path p = Paths.get(loc);
            if (Files.exists(p) && Files.isDirectory(p)) {
                candidatePaths.add(p.toAbsolutePath().normalize().toString());
            }
        }

        List<CandidateModelDir> result = new ArrayList<>();
        for (String pathStr : candidatePaths) {
            try {
                Path base = Paths.get(pathStr);
                Path modelsDir = (base.getFileName() != null && base.getFileName().toString().equalsIgnoreCase("models"))
                        ? base : base.resolve("models");
                int count = countModelFiles(modelsDir);
                File spaceRef = Files.exists(base) ? base.toFile() : new File(System.getProperty("user.home"));
                long freeGb = spaceRef.getUsableSpace() / (1024L * 1024L * 1024L);
                result.add(new CandidateModelDir(pathStr, count, freeGb));
            } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
        }
        result.sort((a, b) -> Integer.compare(b.modelCount(), a.modelCount()));
        return result;
    }

    /**
     * Counts supported model weight files inside a directory up to depth 3.
     *
     * @param modelsDir the models directory to inspect
     * @return number of detected model files
     */
    public static int countModelFiles(Path modelsDir) {
        if (modelsDir == null || !Files.exists(modelsDir) || !Files.isDirectory(modelsDir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(modelsDir, 3)) {
            return (int) walk.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".safetensors") || n.endsWith(".ckpt") || n.endsWith(".sft") || n.endsWith(".gguf");
            }).count();
        } catch (Exception e) {
            return 0;
        }
    }
}
