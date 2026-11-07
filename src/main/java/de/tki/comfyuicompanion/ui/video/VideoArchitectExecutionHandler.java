package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService.LlmModelPreset;
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
    private final de.tki.comfyuicompanion.service.ILocalAudioService audioService;

    public VideoArchitectExecutionHandler(ConfigService configService,
                                         ComfyPipelineService comfyPipelineService,
                                         Video4jEditorService video4jEditorService,
                                         LocalTTSService ttsService,
                                         Gemma4Service gemma4Service,
                                         IComfyLifecycleService lifecycleService,
                                         VideoScriptParser scriptParser,
                                         IVideoPromptOptimizer promptOptimizer) {
        this(configService, comfyPipelineService, video4jEditorService, ttsService, gemma4Service,
                lifecycleService, scriptParser, promptOptimizer, null, null);
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
        this(configService, comfyPipelineService, video4jEditorService, ttsService, gemma4Service,
                lifecycleService, scriptParser, promptOptimizer, renderCoordinator, null);
    }

    public VideoArchitectExecutionHandler(ConfigService configService,
                                          ComfyPipelineService comfyPipelineService,
                                          Video4jEditorService video4jEditorService,
                                          LocalTTSService ttsService,
                                          Gemma4Service gemma4Service,
                                          IComfyLifecycleService lifecycleService,
                                          VideoScriptParser scriptParser,
                                          IVideoPromptOptimizer promptOptimizer,
                                          VideoBatchRenderCoordinator renderCoordinator,
                                          de.tki.comfyuicompanion.service.ILocalAudioService audioService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.ttsService = ttsService;
        this.gemma4Service = gemma4Service;
        this.lifecycleService = lifecycleService;
        this.scriptParser = scriptParser;
        this.promptOptimizer = promptOptimizer;
        this.renderCoordinator = renderCoordinator != null ? renderCoordinator
                : new VideoBatchRenderCoordinator(configService, comfyPipelineService, video4jEditorService, lifecycleService);
        this.audioService = audioService != null ? audioService
                : new de.tki.comfyuicompanion.service.impl.LocalAudioService(configService, lifecycleService, null);
    }

    public void deconstructScript(Component parent, String idea, int width, int height, double cfg,
                                  int steps, int motion, DefaultListModel<Scene> timelineListModel,
                                  JList<Scene> timelineListView, JProgressBar progressBar,
                                  Consumer<Boolean> buttonsDisabledSetter, Consumer<String> consoleLogger,
                                  Consumer<Scene> previewUpdater, Runnable storyboardJsonUpdater) {
        deconstructScript(parent, idea, null, null, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    public void deconstructScript(Component parent, String idea, String targetModel, int width, int height,
                                  double cfg, int steps, int motion, DefaultListModel<Scene> timelineListModel,
                                  JList<Scene> timelineListView, JProgressBar progressBar,
                                  Consumer<Boolean> buttonsDisabledSetter, Consumer<String> consoleLogger,
                                  Consumer<Scene> previewUpdater, Runnable storyboardJsonUpdater) {
        deconstructScript(parent, idea, targetModel, null, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    /**
     * Deconstructs a master video idea into cinematic storyboard scenes using the specified script LLM
     * with model-specific kinetic directives.
     */
    public void deconstructScript(Component parent, String idea, String targetModel, String scriptLlm,
                                  int width, int height, double cfg, int steps, int motion,
                                  DefaultListModel<Scene> timelineListModel, JList<Scene> timelineListView,
                                  JProgressBar progressBar, Consumer<Boolean> buttonsDisabledSetter,
                                  Consumer<String> consoleLogger, Consumer<Scene> previewUpdater,
                                  Runnable storyboardJsonUpdater) {
        if (idea == null || idea.trim().isEmpty()) {
            consoleLogger.accept("Master script is empty!");
            return;
        }

        LlmModelPreset preset = resolveLlmPreset(scriptLlm);
        String modelName = preset.getDisplayName();

        boolean unattended = de.tki.comfyuicompanion.util.PlatformUtils.isUnattended();

        if (gemma4Service != null && !isPresetDownloaded(preset)) {
            if (unattended) {
                consoleLogger.accept("Local AI model not downloaded. Proceeding with rule-based storyboard segmentation.");
                executeDeconstructScriptTask(parent, preset, idea, targetModel, width, height, cfg, steps, motion,
                        timelineListModel, timelineListView, buttonsDisabledSetter, consoleLogger,
                        previewUpdater, storyboardJsonUpdater);
                return;
            }

            int choice = JOptionPane.showConfirmDialog(
                    parent,
                    "The local " + modelName + " AI model is not downloaded yet.\n\n" +
                            "Would you like to download " + modelName + " (" + preset.getApproxSize() + ") now to unlock intelligent AI script deconstruction?\n\n" +
                            "(Select 'No' to use standard rule-based segmentation)",
                    modelName + " Storyboard Deconstruction",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                downloadLlmAndDeconstruct(parent, preset, idea, targetModel, width, height, cfg, steps, motion,
                        timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                        consoleLogger, previewUpdater, storyboardJsonUpdater);
                return;
            } else if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
        }

        executeDeconstructScriptTask(parent, preset, idea, targetModel, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, buttonsDisabledSetter, consoleLogger,
                previewUpdater, storyboardJsonUpdater);
    }

    private LlmModelPreset resolveLlmPreset(String scriptLlm) {
        if (scriptLlm != null && !scriptLlm.isBlank()) {
            if (configService != null) {
                configService.setLocalLlmModel(scriptLlm);
            }
            return LlmModelPreset.fromId(scriptLlm);
        }
        if (gemma4Service != null && gemma4Service.getActivePreset() != null) {
            return gemma4Service.getActivePreset();
        }
        return LlmModelPreset.GEMMA_3_4B;
    }

    private boolean isPresetDownloaded(LlmModelPreset preset) {
        if (gemma4Service == null || gemma4Service.getLocalGemmaService() == null) {
            return false;
        }
        return gemma4Service.getLocalGemmaService().isModelDownloaded(preset);
    }

    private void downloadLlmAndDeconstruct(Component parent, LlmModelPreset preset, String idea,
                                           String targetModel, int width, int height, double cfg,
                                           int steps, int motion, DefaultListModel<Scene> timelineListModel,
                                           JList<Scene> timelineListView, JProgressBar progressBar,
                                           Consumer<Boolean> buttonsDisabledSetter,
                                           Consumer<String> consoleLogger, Consumer<Scene> previewUpdater,
                                           Runnable storyboardJsonUpdater) {
        String modelName = preset.getDisplayName();
        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        consoleLogger.accept("Downloading " + modelName + " model from Hugging Face...");

        gemma4Service.getLocalGemmaService().downloadModel(
                preset,
                (percent, msg) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setValue((int) (percent * 100));
                    consoleLogger.accept(msg);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    consoleLogger.accept("✅ " + modelName + " model downloaded successfully! Starting AI script deconstruction...");
                    executeDeconstructScriptTask(parent, preset, idea, targetModel, width, height, cfg, steps, motion,
                            timelineListModel, timelineListView, buttonsDisabledSetter,
                            consoleLogger, previewUpdater, storyboardJsonUpdater);
                }),
                (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("❌ " + modelName + " download failed: " + errorMsg);
                    JOptionPane.showMessageDialog(parent, "Could not download " + modelName + " AI model:\n" + errorMsg,
                            modelName + " Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    private void executeDeconstructScriptTask(Component parent, LlmModelPreset preset, String idea,
                                              String targetModel, int width, int height, double cfg,
                                              int steps, int motion, DefaultListModel<Scene> timelineListModel,
                                              JList<Scene> timelineListView, Consumer<Boolean> buttonsDisabledSetter,
                                              Consumer<String> consoleLogger, Consumer<Scene> previewUpdater,
                                              Runnable storyboardJsonUpdater) {
        buttonsDisabledSetter.accept(true);
        boolean usingLlm = isPresetDownloaded(preset);
        String modelName = preset.getDisplayName();
        consoleLogger.accept(usingLlm ? "🤖 " + modelName + ": Analyzing script and generating storyboard..." : "Analyzing script...");

        Thread.ofVirtual().start(() -> {
            try {
                String jsonStr = null;
                if (usingLlm && gemma4Service != null) {
                    try {
                        jsonStr = gemma4Service.generateScript(idea, targetModel, width, height, 5, preset);
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

    public void executeAutoPilot(Component parent, String idea, String speakerPath, int width, int height,
                                 double cfg, int steps, int motion, DefaultListModel<Scene> timelineListModel,
                                 JList<Scene> timelineListView, JProgressBar progressBar,
                                 Consumer<Boolean> buttonsDisabledSetter, Consumer<String> consoleLogger,
                                 Consumer<Scene> previewUpdater, Runnable storyboardJsonUpdater) {
        executeAutoPilot(parent, idea, null, null, speakerPath, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    public void executeAutoPilot(Component parent, String idea, String targetModel, String speakerPath,
                                 int width, int height, double cfg, int steps, int motion,
                                 DefaultListModel<Scene> timelineListModel, JList<Scene> timelineListView,
                                 JProgressBar progressBar, Consumer<Boolean> buttonsDisabledSetter,
                                 Consumer<String> consoleLogger, Consumer<Scene> previewUpdater,
                                 Runnable storyboardJsonUpdater) {
        executeAutoPilot(parent, idea, targetModel, null, speakerPath, width, height, cfg, steps, motion,
                timelineListModel, timelineListView, progressBar, buttonsDisabledSetter,
                consoleLogger, previewUpdater, storyboardJsonUpdater);
    }

    /**
     * Executes the autonomous end-to-end video pipeline including model-specific Gemma storyboard
     * deconstruction, TTS generation, ComfyUI video rendering, and assembly.
     */
    public void executeAutoPilot(Component parent, String idea, String targetModel, String scriptLlm,
                                 String speakerPath, int width, int height, double cfg, int steps, int motion,
                                 DefaultListModel<Scene> timelineListModel, JList<Scene> timelineListView,
                                 JProgressBar progressBar, Consumer<Boolean> buttonsDisabledSetter,
                                 Consumer<String> consoleLogger, Consumer<Scene> previewUpdater,
                                 Runnable storyboardJsonUpdater) {
        if (idea == null || idea.trim().isEmpty()) {
            consoleLogger.accept("Master script is empty! Agent needs a concept.");
            return;
        }

        LlmModelPreset preset = resolveLlmPreset(scriptLlm);
        String modelName = preset.getDisplayName();

        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        consoleLogger.accept("Agent Auto-Pilot engaged (" + modelName + "). Phase 1: Script Deconstruction...");

        Thread.ofVirtual().start(() -> {
            try {
                // PHASE 1: Director Agent
                consoleLogger.accept("Director Agent: Generating storyboard JSON with " + modelName + "...");
                String jsonStr = null;
                try {
                    if (gemma4Service != null && isPresetDownloaded(preset)) {
                        jsonStr = gemma4Service.generateScript(idea, targetModel, width, height, 5, preset);
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
                String resolvedBase = (configService != null && configService.getResolvedOutputDir() != null && !configService.getResolvedOutputDir().isBlank())
                        ? configService.getResolvedOutputDir() : "output";
                File narrationDir = new File(resolvedBase, "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < generatedScenes.size(); ni++) {
                    Scene ns = generatedScenes.get(ni);
                    final int currNi = ni + 1;
                    consoleLogger.accept("Narration Agent (Scene " + currNi + "/" + generatedScenes.size() + "): " + ns.getSceneId());
                    if (generateNarrationAudio(ns, narrationDir)) narrationOk++;
                }
                consoleLogger.accept("Narration: " + narrationOk + "/" + generatedScenes.size() + " scenes have audio ready.");

                // PHASE 1.6: Sound Effects & Ambient Audio Agent (SFX)
                File sfxDir = new File(resolvedBase, "sfx");
                if (!sfxDir.exists()) sfxDir.mkdirs();
                int sfxOk = 0;
                for (int si = 0; si < generatedScenes.size(); si++) {
                    Scene ss = generatedScenes.get(si);
                    final int currSi = si + 1;
                    consoleLogger.accept("Audio Agent (Scene " + currSi + "/" + generatedScenes.size() + "): Synthesizing atmospheric SFX for " + ss.getSceneId() + "...");
                    if (generateSfxAudio(ss, sfxDir)) sfxOk++;
                }
                consoleLogger.accept("Sound Effects: " + sfxOk + "/" + generatedScenes.size() + " atmospheric tracks ready.");

                // PHASE 2: Generation Agent (ComfyUI via Two-Phase VRAM-Optimized Pipeline)
                consoleLogger.accept("Generation Agent: Rendering scenes via Two-Phase pipeline (Flux Keyframe -> Wan 2.1 I2V)...");
                renderCoordinator.executeVideoGenerationSync(parent, generatedScenes, speakerPath, width, height,
                        timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater);

                if (renderCoordinator.isCancellationRequested()) {
                    consoleLogger.accept("🛑 Auto-Pilot stopped by user before video stitching.");
                    return;
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
                    if (!de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                        JOptionPane.showMessageDialog(parent, "Auto-Pilot failed:\n" + ex.getMessage(), "Auto-Pilot Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        });
    }

    public void executeVideoGeneration(Component parent, List<Scene> scenes, String speakerPath,
                                       int width, int height, JList<Scene> timelineListView,
                                       JProgressBar progressBar, Consumer<Boolean> buttonsDisabledSetter,
                                       Consumer<String> consoleLogger, Consumer<Scene> previewUpdater) {
        renderCoordinator.executeVideoGeneration(parent, scenes, speakerPath, width, height,
                timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater);
    }

    public void executeMasterRender(Component parent, List<Scene> scenes, JProgressBar progressBar,
                                    Consumer<Boolean> buttonsDisabledSetter, Consumer<String> consoleLogger) {
        renderCoordinator.executeMasterRender(parent, scenes, progressBar, buttonsDisabledSetter, consoleLogger);
    }

    /**
     * Signals cancellation to the underlying batch render coordinator.
     */
    public void cancelExecution() {
        if (renderCoordinator != null) {
            renderCoordinator.cancelExecution();
        }
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

    public boolean generateSfxAudio(Scene scene, File outputDir) {
        if (audioService == null) {
            return false;
        }
        String prompt = scene.getSfxPrompt();
        if (prompt == null || prompt.isBlank()) {
            if (promptOptimizer != null) {
                prompt = promptOptimizer.inferSfxPrompt(scene.getPrompt(), scene.getNarrationText());
                scene.setSfxPrompt(prompt);
            }
        }
        if (prompt == null || prompt.isBlank()) {
            prompt = "Cinematic atmospheric soundscape";
        }
        if (!outputDir.exists()) outputDir.mkdirs();
        File sfxFile = new File(outputDir, "sfx_" + scene.getSceneId() + ".wav");
        if (sfxFile.exists() && sfxFile.length() > 512) {
            scene.setSfxAudioPath(sfxFile.getAbsolutePath());
            return true;
        }
        try {
            int duration = 5;
            if (scene.getEndFrame() > scene.getStartFrame()) {
                duration = Math.max(1, (scene.getEndFrame() - scene.getStartFrame()) / 24);
            }
            boolean ok = audioService.generateSoundEffect(prompt, duration, sfxFile);
            if (ok && sfxFile.exists() && sfxFile.length() > 512) {
                scene.setSfxAudioPath(sfxFile.getAbsolutePath());
                return true;
            }
        } catch (Exception ex) {
            logger.error("SFX generation failed for scene {}: {}", scene.getSceneId(), ex.getMessage());
        }
        return false;
    }
}
