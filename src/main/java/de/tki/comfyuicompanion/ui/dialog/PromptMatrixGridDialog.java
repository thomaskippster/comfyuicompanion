package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.controller.PromptLabMatrixEngine;
import de.tki.comfyuicompanion.controller.PromptLabMatrixEngine.MatrixCell;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Interactive Batch / Prompt Matrix XY-Grid dialog for the Image Lab.
 * Allows expanding {@code {optionA|optionB}} prompt variations, CFG sweeps, and multi-seed runs
 * and viewing generated results side-by-side in a comparison grid.
 */
public class PromptMatrixGridDialog extends JDialog {

    private final JTextField promptTemplateField;
    private final JTextField cfgSweepField;
    private final JSpinner seedCountSpinner;
    private final JPanel gridContainer;
    private final JLabel statusLabel;
    private final JButton btnRunMatrix;
    private final List<CellView> cellViews = new ArrayList<>();
    private final Consumer<Image> onSelectPreviewImage;

    /**
     * Visual representation of a single cell in the XY comparison grid.
     */
    public static class CellView extends JPanel {
        private final JLabel imageLabel;
        private final JLabel titleLabel;
        private Image fullImage;

        /**
         * Constructs a new CellView for a matrix cell.
         *
         * @param cell           the matrix cell definition
         * @param onImageClicked callback invoked when the user clicks a completed cell image
         */
        public CellView(MatrixCell cell, Consumer<Image> onImageClicked) {
            super(new BorderLayout(4, 4));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(65, 72, 92), 1),
                    new EmptyBorder(6, 6, 6, 6)
            ));
            setPreferredSize(new Dimension(240, 260));

            titleLabel = new JLabel(cell.label());
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            titleLabel.setToolTipText(cell.positivePrompt());
            add(titleLabel, BorderLayout.NORTH);

            imageLabel = new JLabel("⏳ Queued...", SwingConstants.CENTER);
            imageLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
            imageLabel.setOpaque(true);
            imageLabel.setBackground(new Color(24, 26, 34));
            imageLabel.setForeground(new Color(160, 170, 190));
            imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (fullImage != null && onImageClicked != null) {
                        onImageClicked.accept(fullImage);
                    }
                }
            });
            add(imageLabel, BorderLayout.CENTER);
        }

        /**
         * Updates the status text shown inside the cell before its image arrives.
         *
         * @param statusText status message
         */
        public void setStatus(String statusText) {
            SwingUtilities.invokeLater(() -> {
                if (fullImage == null) {
                    imageLabel.setText(statusText);
                }
            });
        }

        /**
         * Displays the generated image scaled inside this matrix cell.
         *
         * @param img the generated image
         */
        public void setCompletedImage(Image img) {
            if (img == null) return;
            this.fullImage = img;
            SwingUtilities.invokeLater(() -> {
                int w = Math.max(180, imageLabel.getWidth() > 0 ? imageLabel.getWidth() : 220);
                int h = Math.max(180, imageLabel.getHeight() > 0 ? imageLabel.getHeight() : 220);
                int iw = img.getWidth(null);
                int ih = img.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double scale = Math.min((double) w / iw, (double) h / ih);
                    Image scaled = img.getScaledInstance(
                            Math.max(1, (int) (iw * scale)),
                            Math.max(1, (int) (ih * scale)),
                            Image.SCALE_SMOOTH
                    );
                    imageLabel.setIcon(new ImageIcon(scaled));
                    imageLabel.setText("");
                }
            });
        }
    }

    /**
     * Constructs a new PromptMatrixGridDialog.
     *
     * @param owner                parent window
     * @param initialPrompt        initial positive prompt from Image Lab
     * @param defaultCfg           current CFG scale from Image Lab
     * @param defaultSteps         current step count from Image Lab
     * @param onRunCells           callback executing the list of matrix cells and updating their {@link CellView}s
     * @param onSelectPreviewImage callback invoked when a cell image is clicked to send it to the main preview
     */
    public PromptMatrixGridDialog(Window owner,
                                  String initialPrompt,
                                  double defaultCfg,
                                  int defaultSteps,
                                  BiConsumer<List<MatrixCell>, List<CellView>> onRunCells,
                                  Consumer<Image> onSelectPreviewImage) {
        super(owner instanceof Frame f ? f : null, "Batch / Prompt Matrix XY-Grid", false);
        this.onSelectPreviewImage = onSelectPreviewImage;
        setSize(980, 700);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel topConfig = new JPanel(new BorderLayout(8, 8));
        JLabel helpLbl = new JLabel("Use {option1|option2|option3} in your prompt and/or comma-separated CFG values to compare variations side-by-side:");
        helpLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        topConfig.add(helpLbl, BorderLayout.NORTH);

        String seedPrompt = (initialPrompt != null && !initialPrompt.isBlank())
                ? initialPrompt
                : "{cyberpunk|steampunk|watercolor} cat, detailed, 8k";
        promptTemplateField = new JTextField(seedPrompt);
        promptTemplateField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        topConfig.add(promptTemplateField, BorderLayout.CENTER);

        JPanel paramsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        paramsRow.add(new JLabel("CFG Sweep (CSV):"));
        cfgSweepField = new JTextField(String.valueOf(defaultCfg), 10);
        paramsRow.add(cfgSweepField);

        paramsRow.add(new JLabel("Seeds per Variation:"));
        seedCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));
        paramsRow.add(seedCountSpinner);

        btnRunMatrix = new JButton("Run Matrix Batch", SvgIconFactory.get(AppIcon.SEND, 14));
        btnRunMatrix.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRunMatrix.addActionListener(e -> triggerMatrixRun(defaultCfg, defaultSteps, onRunCells));
        paramsRow.add(btnRunMatrix);

        topConfig.add(paramsRow, BorderLayout.SOUTH);
        root.add(topConfig, BorderLayout.NORTH);

        gridContainer = new JPanel(new GridLayout(0, 3, 10, 10));
        gridContainer.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(gridContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready. Click 'Run Matrix Batch' to generate comparison grid.");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.add(statusLabel, BorderLayout.WEST);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    /**
     * Builds the matrix cells from the current dialog inputs and triggers execution immediately.
     *
     * @param defaultCfg   fallback CFG scale
     * @param defaultSteps sampling steps
     * @param onRunCells   execution callback
     */
    public void triggerMatrixRun(double defaultCfg,
                                 int defaultSteps,
                                 BiConsumer<List<MatrixCell>, List<CellView>> onRunCells) {
        List<MatrixCell> cells = PromptLabMatrixEngine.buildMatrixCells(
                promptTemplateField.getText(),
                cfgSweepField.getText(),
                defaultCfg,
                defaultSteps,
                (int) seedCountSpinner.getValue(),
                System.currentTimeMillis() % 1_000_000_000L
        );

        gridContainer.removeAll();
        cellViews.clear();

        int cols = Math.min(4, Math.max(2, (int) Math.ceil(Math.sqrt(cells.size()))));
        gridContainer.setLayout(new GridLayout(0, cols, 10, 10));

        for (MatrixCell cell : cells) {
            CellView cv = new CellView(cell, onSelectPreviewImage);
            cellViews.add(cv);
            gridContainer.add(cv);
        }

        statusLabel.setText("Starting matrix batch (" + cells.size() + " variations)...");
        gridContainer.revalidate();
        gridContainer.repaint();

        if (onRunCells != null) {
            onRunCells.accept(cells, new ArrayList<>(cellViews));
        }
    }

    /**
     * Updates the footer status label on the Swing Event Dispatch Thread.
     *
     * @param message status text to display
     */
    public void setStatusText(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /**
     * Updates the enabled state and label of the Run Matrix Batch button.
     *
     * @param running true while a batch is actively executing
     */
    public void setRunningState(boolean running) {
        SwingUtilities.invokeLater(() -> {
            btnRunMatrix.setEnabled(!running);
            btnRunMatrix.setText(running ? "Running..." : "Run Matrix Batch");
        });
    }
}
