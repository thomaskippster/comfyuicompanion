package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalTTSService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles asynchronous background execution for Video Architect tasks,
 * including script deconstruction, scene generation, TTS narration, and FFmpeg video stitching.
 */
public class VideoArchitectExecutionHandler {
    private static final Logger logger = LoggerFactory.getLogger(VideoArchitectExecutionHandler.class);

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final LocalTTSService ttsService;
    private final Gemma4Service gemma4Service;
    private final IComfyLifecycleService lifecycleService;
    private final VideoScriptParser scriptParser;
    private final IVideoPromptOptimizer promptOptimizer;
    private final VideoBatchRenderCoordinator renderCoordinator;

    public VideoArchitectExecutionHandler(ConfigService configService,
                                         ComfyPipelineService comfyPipelineService,
                                         Video4jEditorService video4jEditorService,
                                         LocalTTSService ttsService,
                                         Gemma4Service gemma4Service,
                                         IComfyLifecycleService lifecycleService,
                                         VideoScriptParser scriptParser,
                                         IVideoPromptOptimizer promptOptimizer) {
        this(configService, comfyPipelineService, video4jEditorService, ttsService, gemma4Service,
                lifecycleService, scriptParser, promptOptimizer,
                new VideoBatchRenderCoordinator(configService, comfyPipelineService, video4jEditorService, lifecycleService));
    }

    public VideoArchitectExecutionHandler(ConfigService configService,
                                         ComfyPipelineService comfyPipelineService,
                                         Video4jEditorService video4jEditorService,
                                         LocalTTSService ttsService,
                                         Gemma4Service gemma4Service,
                                         IComfyLifecycleService lifecycleService,
                                         VideoScriptParser scriptParser,
                                         IVideoPromptOptimizer promptOptimizer,
                                         VideoBatchRenderCoordinator renderCoordinator) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.ttsService = ttsService;
        this.gemma4Service = gemma4Service;
        this.lifecycleService = lifecycleService;
        this.scriptParser = scriptParser;
        this.promptOptimizer = promptOptimizer;
        this.renderCoordinator = renderCoordinator;
    }

    public void deconstructScript(Component parent,
                                  String idea,
                                  int width,
                                  int height,
                                  double cfg,
                                  int steps,
                                  int motion,
                                  DefaultListModel<Scene> timelineListModel,
                                  JList<Scene> timelineListView,
                                  JProgressBar progressBar,
                                  Consumer<Boolean> buttonsDisabledSetter,
                                  Consumer<String> consoleLogger,
                                  Consumer<Scene> previewUpdater,
                                  Runnable storyboardJsonUpdater) {
        deconstructScript(parent, idea, null, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    /**
     * Deconstructs a master video idea into cinematic storyboard scenes using Gemma AI
     * with model-specific kinetic directives.
     */
    public void deconstructScript(Component parent,
                                  String idea,
                                  String targetModel,
                                  int width,
                                  int height,
                                  double cfg,
                                  int steps,
                                  int motion,
                                  DefaultListModel<Scene> timelineListModel,
                                  JList<Scene> timelineListView,
                                  JProgressBar progressBar,
                                  Consumer<Boolean> buttonsDisabledSetter,
                                  Consumer<String> consoleLogger,
                                  Consumer<Scene> previewUpdater,
                                  Runnable storyboardJsonUpdater) {
        if (idea == null || idea.trim().isEmpty()) {
            consoleLogger.accept("Master script is empty!");
            return;
        }

        if (gemma4Service != null && !gemma4Service.isGemmaAvailable()) {
            String modelName = gemma4Service.getActiveModelDisplayName();
            String approxSize = gemma4Service.getActivePreset().getApproxSize();
            int choice = JOptionPane.showConfirmDialog(
                    parent,
                    "The local " + modelName + " AI model is not downloaded yet.\n\n" +
                            "Would you like to download the " + modelName + " model (" + approxSize + ") now to unlock intelligent AI script deconstruction, visual prompt engineering, and scene timing?\n\n" +
                            "(Select 'No' to use standard rule-based segmentation)",
                    modelName + " Storyboard Deconstruction",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                downloadGemmaAndDeconstruct(parent, idea, targetModel, width, height, cfg, steps, motion,
                        timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                        consoleLogger, previewUpdater, storyboardJsonUpdater);
                return;
            } else if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
        }

        executeDeconstructScriptTask(parent, idea, targetModel, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, buttonsDisabledSetter, consoleLogger,
                previewUpdater, storyboardJsonUpdater);
    }

    private void downloadGemmaAndDeconstruct(Component parent,
                                             String idea,
                                             String targetModel,
                                             int width,
                                             int height,
                                             double cfg,
                                             int steps,
                                             int motion,
                                             DefaultListModel<Scene> timelineListModel,
                                             JList<Scene> timelineListView,
                                             JProgressBar progressBar,
                                             Consumer<Boolean> buttonsDisabledSetter,
                                             Consumer<String> consoleLogger,
                                             Consumer<Scene> previewUpdater,
                                             Runnable storyboardJsonUpdater) {
        String modelName = gemma4Service != null ? gemma4Service.getActiveModelDisplayName() : "LLM";
        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        consoleLogger.accept("Downloading " + modelName + " model from Hugging Face...");

        gemma4Service.downloadGemmaModel(
                (percent, msg) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setValue((int) (percent * 100));
                    consoleLogger.accept(msg);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    consoleLogger.accept("✅ " + modelName + " model downloaded successfully! Starting AI script deconstruction...");
                    executeDeconstructScriptTask(parent, idea, targetModel, width, height, cfg, steps, motion,
                            timelineListModel, timelineListView, buttonsDisabledSetter,
                            consoleLogger, previewUpdater, storyboardJsonUpdater);
                }),
                (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("❌ " + modelName + " download failed: " + errorMsg);
                    JOptionPane.showMessageDialog(parent, "Could not download " + modelName + " AI model:\n" + errorMsg, modelName + " Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    private void executeDeconstructScriptTask(Component parent,
                                              String idea,
                                              String targetModel,
                                              int width,
                                              int height,
                                              double cfg,
                                              int steps,
                                              int motion,
                                              DefaultListModel<Scene> timelineListModel,
                                              JList<Scene> timelineListView,
                                              Consumer<Boolean> buttonsDisabledSetter,
                                              Consumer<String> consoleLogger,
                                              Consumer<Scene> previewUpdater,
                                              Runnable storyboardJsonUpdater) {
        buttonsDisabledSetter.accept(true);
        boolean usingLlm = gemma4Service != null && gemma4Service.isGemmaAvailable();
        String modelName = gemma4Service != null ? gemma4Service.getActiveModelDisplayName() : "AI";
        consoleLogger.accept(usingLlm ? "🤖 " + modelName + ": Analyzing script and generating storyboard..." : "Analyzing script...");

        Thread.ofVirtual().start(() -> {
            try {
                String jsonStr = null;
                if (gemma4Service != null && gemma4Service.isGemmaAvailable()) {
                    try {
                        jsonStr = gemma4Service.generateScript(idea, targetModel, width, height, 5);
                    } catch (Exception ex) {
                        logger.warn("LLM script generation threw: {}. Falling back to rule-based parser.", ex.getMessage());
                    }
                }

                List<Scene> scenes = scriptParser.parseStoryboardJson(jsonStr, idea, width, height, promptOptimizer);
                for (Scene sc : scenes) {
                    sc.setWidth(width);
                    sc.setHeight(height);
                    sc.setCfgScale(cfg);
                    sc.setSteps(steps);
                    sc.setMotionBucketId(motion);
                }

                SwingUtilities.invokeLater(() -> {
                    buttonsDisabledSetter.accept(false);
                    timelineListModel.clear();
                    for (Scene sc : scenes) {
                        timelineListModel.addElement(sc);
                    }
                    storyboardJsonUpdater.run();
                    if (!scenes.isEmpty()) {
                        timelineListView.setSelectedIndex(0);
                        previewUpdater.accept(scenes.get(0));
                    }
                    boolean usedLlm = usingLlm && !scenes.isEmpty();
                    consoleLogger.accept(usedLlm ? "✅ " + modelName + ": Successfully deconstructed script into " + scenes.size() + " scenes!"
                            : "Script deconstructed into " + scenes.size() + " scenes.");
                });
            } catch (Exception e) {
                logger.error("Deconstruct script task failed", e);
                SwingUtilities.invokeLater(() -> {
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Agent failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(parent, "Failed to deconstruct script:\n" + e.getMessage(), "Agent Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    public void executeAutoPilot(Component parent,
                                 String idea,
                                 String speakerPath,
                                 int width,
                                 int height,
                                 double cfg,
                                 int steps,
                                 int motion,
                                 DefaultListModel<Scene> timelineListModel,
                                 JList<Scene> timelineListView,
                                 JProgressBar progressBar,
                                 Consumer<Boolean> buttonsDisabledSetter,
                                 Consumer<String> consoleLogger,
                                 Consumer<Scene> previewUpdater,
                                 Runnable storyboardJsonUpdater) {
        executeAutoPilot(parent, idea, null, speakerPath, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    /**
     * Executes the autonomous end-to-end video pipeline including model-specific Gemma storyboard
     * deconstruction, TTS generation, ComfyUI video rendering, and assembly.
     */
    public void executeAutoPilot(Component parent,
                                 String idea,
                                 String targetModel,
                                 String speakerPath,
                                 int width,
                                 int height,
                                 double cfg,
                                 int steps,
                                 int motion,
                                 DefaultListModel<Scene> timelineListModel,
                                 JList<Scene> timelineListView,
                                 JProgressBar progressBar,
                                 Consumer<Boolean> buttonsDisabledSetter,
                                 Consumer<String> consoleLogger,
                                 Consumer<Scene> previewUpdater,
                                 Runnable storyboardJsonUpdater) {
        if (idea == null || idea.trim().isEmpty()) {
            consoleLogger.accept("Master script is empty! Agent needs a concept.");
            return;
        }

        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        consoleLogger.accept("Agent Auto-Pilot engaged. Phase 1: Script Deconstruction...");

        Thread.ofVirtual().start(() -> {
            try {
                // PHASE 1: Director Agent
                consoleLogger.accept("Director Agent: Generating storyboard JSON...");
                String jsonStr = null;
                try {
                    if (gemma4Service != null) {
                        jsonStr = gemma4Service.generateScript(idea, targetModel, width, height, 5);
                    }
                } catch (Exception ex) {
                    logger.warn("LLM script generation threw: {}. Using fallback deconstruction.", ex.getMessage());
                }

                List<Scene> generatedScenes = scriptParser.parseStoryboardJson(jsonStr, idea, width, height, promptOptimizer);
                if (generatedScenes.isEmpty()) {
                    throw new Exception("Unable to deconstruct script into timeline scenes.");
                }

                for (Scene sc : generatedScenes) {
                    sc.setWidth(width);
                    sc.setHeight(height);
                    sc.setCfgScale(cfg);
                    sc.setSteps(steps);
                    sc.setMotionBucketId(motion);
                    sc.setSpeakerImagePath(speakerPath != null && !speakerPath.trim().isEmpty() ? speakerPath.trim() : null);
                }

                SwingUtilities.invokeLater(() -> {
                    timelineListModel.clear();
                    for (Scene sc : generatedScenes) {
                        timelineListModel.addElement(sc);
                    }
                    storyboardJsonUpdater.run();
                });

                // PHASE 1.5: Narration Agent (TTS)
                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < generatedScenes.size(); ni++) {
                    Scene ns = generatedScenes.get(ni);
                    final int currNi = ni + 1;
                    consoleLogger.accept("Narration Agent (Scene " + currNi + "/" + generatedScenes.size() + "): " + ns.getSceneId());
                    if (generateNarrationAudio(ns, narrationDir)) narrationOk++;
                }
                consoleLogger.accept("Narration: " + narrationOk + "/" + generatedScenes.size() + " scenes have audio ready.");

                // PHASE 2: Generation Agent (ComfyUI)
                consoleLogger.accept("Checking ComfyUI server status...");
                ensureComfyUiHealthy();

                for (int i = 0; i < generatedScenes.size(); i++) {
                    Scene scene = generatedScenes.get(i);
                    final int currentIdx = i + 1;
                    final int totalScenes = generatedScenes.size();

                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;

                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        final int att = attempt;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue((int) (((double) currentIdx / totalScenes) * 100));
                        });
                        consoleLogger.accept("Generation Agent (Scene " + currentIdx + "/" + totalScenes + "): " + scene.getSceneId() + (att > 1 ? " (Attempt " + att + ")" : ""));

                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            Thread.sleep(2000);
                        }
                    }

                    if (!success) {
                        throw new Exception("Generation Agent failed on Scene: " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }

                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }
                    SwingUtilities.invokeLater(() -> {
                        timelineListView.repaint();
                        previewUpdater.accept(scene);
                    });
                }

                // PHASE 3: Editor Agent (FFmpeg)
                consoleLogger.accept("Editor Agent: Stitching video segments via FFmpeg...");
                File exportFile = video4jEditorService.executeMasterRender(generatedScenes);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Auto-Pilot Complete! Master Video saved as: " + exportFile.getName());
                    JOptionPane.showMessageDialog(parent, "Master video render complete!\nSaved to: " + exportFile.getAbsolutePath(), "Auto-Pilot Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                logger.error("Auto-Pilot task failed", ex);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Auto-Pilot Failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(parent, "Auto-Pilot failed:\n" + ex.getMessage(), "Auto-Pilot Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    public void executeVideoGeneration(Component parent,
                                       List<Scene> scenes,
                                       String speakerPath,
                                       int width,
                                       int height,
                                       JList<Scene> timelineListView,
                                       JProgressBar progressBar,
                                       Consumer<Boolean> buttonsDisabledSetter,
                                       Consumer<String> consoleLogger,
                                       Consumer<Scene> previewUpdater) {
        renderCoordinator.executeVideoGeneration(parent, scenes, speakerPath, width, height,
                timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater);
    }

    public void executeMasterRender(Component parent,
                                    List<Scene> scenes,
                                    JProgressBar progressBar,
                                    Consumer<Boolean> buttonsDisabledSetter,
                                    Consumer<String> consoleLogger) {
        renderCoordinator.executeMasterRender(parent, scenes, progressBar, buttonsDisabledSetter, consoleLogger);
    }

    public boolean generateNarrationAudio(Scene scene, File outputDir) {
        if (ttsService == null) {
            return false;
        }
        String narration = scene.getNarrationText();
        if (narration == null || narration.trim().isEmpty()) {
            return false;
        }
        if (!outputDir.exists()) outputDir.mkdirs();
        File audioFile = new File(outputDir, "narration_" + scene.getSceneId() + ".wav");
        if (audioFile.exists() && audioFile.length() > 0) {
            scene.setAudioPath(audioFile.getAbsolutePath());
            return true;
        }
        try {
            ttsService.generateSpeech(narration, audioFile.getAbsolutePath());
            if (audioFile.exists() && audioFile.length() > 0) {
                scene.setAudioPath(audioFile.getAbsolutePath());
                return true;
            }
        } catch (Exception ttsEx) {
            logger.error("TTS generation failed for scene {}: {}", scene.getSceneId(), ttsEx.getMessage());
        }
        return false;
    }

    private void ensureComfyUiHealthy() throws Exception {
        renderCoordinator.ensureComfyUiHealthy();
    }
}
