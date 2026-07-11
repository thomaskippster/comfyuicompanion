package de.tki.comfymodels.ui;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import de.tki.comfymodels.service.impl.Video4jEditorService;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.json.JSONArray;
import org.json.JSONObject;
import de.tki.comfymodels.service.impl.Gemma4Service;
import de.tki.comfymodels.service.impl.LocalTTSService;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class VideoArchitectTab extends JFXPanel {

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final Gemma4Service gemma4Service;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LocalTTSService ttsService;
    private final de.tki.comfymodels.service.IComfyLifecycleService lifecycleService;

    // JavaFX Scene & Root
    private javafx.scene.Scene fxScene;
    private BorderPane rootNode;

    // UI Elements
    private TextArea masterScriptArea;
    private Button deconstructBtn;
    private Button autoPilotBtn;
    private TextField speakerImageField;
    private ListView<Scene> timelineListView;
    private Button generateVideoBtn;
    private Button masterRenderBtn;
    private ProgressBar renderProgressBar;
    private Label statusLabel;



    @Autowired
    public VideoArchitectTab(ConfigService configService,
                             ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService,
                             Gemma4Service gemma4Service,
                             de.tki.comfymodels.service.IComfyLifecycleService lifecycleService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.gemma4Service = gemma4Service;
        this.lifecycleService = lifecycleService;

        Platform.runLater(this::initFX);
    }

    private void initFX() {
        try {
            rootNode = new BorderPane();
            rootNode.setPadding(new Insets(15));
            rootNode.getStyleClass().add("root");

            // Apply style sheet
            String cssPath = getClass().getResource("/css/video-architect.css").toExternalForm();
            rootNode.getStylesheets().add(cssPath);

            // Synchronize starting theme
            if (!configService.isDarkMode()) {
                rootNode.getStyleClass().add("light-theme");
            }

            SplitPane mainSplitPane = new SplitPane();
            mainSplitPane.getItems().addAll(createLeftSidebar(), createRightArea());
            mainSplitPane.setDividerPositions(0.40);

            rootNode.setCenter(mainSplitPane);

            fxScene = new javafx.scene.Scene(rootNode);
            setScene(fxScene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private VBox createLeftSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("glass-panel");
        sidebar.setPadding(new Insets(15));

        Label titleLabel = new Label("🎬 Video Architect");
        titleLabel.getStyleClass().add("label-title");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px;");

        Label subtitleLabel = new Label("Compose and generate multi-scene AI videos.");
        subtitleLabel.getStyleClass().add("label-muted");
        subtitleLabel.setWrapText(true);

        // Global Settings
        Label speakerLabel = new Label("Global Start Image (Consistency)");
        speakerLabel.setStyle("-fx-font-weight: bold;");

        HBox speakerBox = new HBox(5);
        speakerImageField = new TextField(configService.getSpeakerImagePath());
        HBox.setHgrow(speakerImageField, Priority.ALWAYS);
        Button browseSpeaker = new Button("Browse...");
        browseSpeaker.setOnAction(e -> {
            File f = chooseFile("Select Start Image", "*.png", "*.jpg", "*.jpeg", "*.webp");
            if (f != null) {
                speakerImageField.setText(f.getAbsolutePath());
                configService.setSpeakerImagePath(f.getAbsolutePath());
            }
        });
        speakerBox.getChildren().addAll(speakerImageField, browseSpeaker);

        Separator sep0 = new Separator();

        // Writer's Room
        Label writerTitle = new Label("The Writer's Room");
        writerTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        masterScriptArea = new TextArea();
        masterScriptArea.setText("Ein Raketenstart von einer Startrampe in der Wüste. Mehrere Personen betrachten den Start aus der Ferne von einem Bahnhof. Es scheint die Sonne. Alle schwitzen von der Hitze.");
        masterScriptArea.setPromptText("Enter your master video idea here... (e.g. A cyberpunk car driving through rain, then stops at a neon diner...)");
        masterScriptArea.setPrefRowCount(4);
        masterScriptArea.setWrapText(true);

        deconstructBtn = new Button("Agent: Deconstruct Script");
        deconstructBtn.getStyleClass().add("primary-button");
        deconstructBtn.setMaxWidth(Double.MAX_VALUE);
        deconstructBtn.setOnAction(e -> handleDeconstructScript());

        autoPilotBtn = new Button("🤖 Agent: Auto-Pilot (End-to-End)");
        autoPilotBtn.getStyleClass().add("primary-button");
        autoPilotBtn.setMaxWidth(Double.MAX_VALUE);
        autoPilotBtn.setOnAction(e -> handleAutoPilot());

        VBox writerBox = new VBox(10, writerTitle, masterScriptArea, deconstructBtn, autoPilotBtn);

        sidebar.getChildren().addAll(
            titleLabel, subtitleLabel, 
            speakerLabel, speakerBox, 
            sep0, 
            writerBox
        );

        return sidebar;
    }

    private VBox createRightArea() {
        VBox box = new VBox(15);
        box.getStyleClass().add("glass-panel");
        box.setPadding(new Insets(15));
        HBox.setHgrow(box, Priority.ALWAYS);

        Label timelineTitle = new Label("Timeline");
        timelineTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        timelineListView = new ListView<>();
        VBox.setVgrow(timelineListView, Priority.ALWAYS);
        timelineListView.setCellFactory(listView -> new SceneListCell());



        HBox listActions = new HBox(10);
        Button deleteBtn = new Button("Delete Selected");
        deleteBtn.setOnAction(e -> handleDeleteScene());
        Button clearListBtn = new Button("Clear Timeline");
        clearListBtn.setOnAction(e -> timelineListView.getItems().clear());
        listActions.getChildren().addAll(deleteBtn, clearListBtn);

        Separator sep = new Separator();

        generateVideoBtn = new Button("1. Generate All Scenes");
        generateVideoBtn.getStyleClass().add("primary-button");
        generateVideoBtn.setMaxWidth(Double.MAX_VALUE);
        generateVideoBtn.setPrefHeight(40);
        generateVideoBtn.setOnAction(e -> handleVideoGeneration(null));

        masterRenderBtn = new Button("2. Stitch Videos (Master Render)");
        masterRenderBtn.setMaxWidth(Double.MAX_VALUE);
        masterRenderBtn.setPrefHeight(40);
        masterRenderBtn.setOnAction(e -> handleMasterRender());

        renderProgressBar = new ProgressBar(0);
        renderProgressBar.setMaxWidth(Double.MAX_VALUE);
        renderProgressBar.setVisible(false);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-custom-accent-color;");

        box.getChildren().addAll(
            timelineTitle, timelineListView, listActions,
            sep,
            generateVideoBtn, masterRenderBtn,
            renderProgressBar, statusLabel
        );
        return box;
    }

    private File chooseFile(String title, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", extensions));
        return chooser.showOpenDialog(fxScene.getWindow());
    }

    private void handleAutoPilot() {
        String idea = masterScriptArea.getText().trim();
        if (idea.isEmpty()) {
            updateStatus("Master script is empty! Agent needs a concept.");
            return;
        }

        setButtonsDisabled(true);
        if (deconstructBtn != null) deconstructBtn.setDisable(true);
        if (autoPilotBtn != null) autoPilotBtn.setDisable(true);
        updateStatus("Agent Auto-Pilot engaged. Phase 1: Script Deconstruction...");

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                // PHASE 1: Director Agent
                updateMessage("Director Agent: Generating storyboard JSON...");
                String jsonStr = gemma4Service.generateScript(idea);
                
                jsonStr = jsonStr.replace("\u0000", "");
                if (jsonStr.startsWith("```json")) jsonStr = jsonStr.substring(7);
                if (jsonStr.startsWith("```")) jsonStr = jsonStr.substring(3);
                if (jsonStr.endsWith("```")) jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
                jsonStr = jsonStr.trim();
                
                JSONArray arr = new JSONArray(jsonStr);
                
                List<Scene> generatedScenes = new ArrayList<>();
                Platform.runLater(() -> timelineListView.getItems().clear());
                
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Scene scene = new Scene();
                    scene.setSceneId(obj.optString("scene_id", "S" + (i+1)));
                    scene.setPrompt(obj.optString("visual_prompt", ""));
                    scene.setNarrationText(obj.optString("narration_text", ""));
                    int dur = obj.optInt("duration_seconds", 5);
                    scene.setStartFrame(0);
                    scene.setEndFrame(dur * 24);
                    scene.setCfgScale(1.0);
                    scene.setSteps(30);
                    
                    generatedScenes.add(scene);
                    Platform.runLater(() -> timelineListView.getItems().add(scene));
                }
                
                // PHASE 2: Generation Agent (ComfyUI)
                updateMessage("Checking ComfyUI server status...");
                if (!lifecycleService.isHealthy()) {
                    updateMessage("ComfyUI server is offline. Attempting to start...");
                    lifecycleService.start();
                    int maxWait = 90;
                    boolean healthy = false;
                    for (int w = 0; w < maxWait; w++) {
                        if (lifecycleService.isHealthy()) {
                            healthy = true;
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    if (!healthy) {
                        throw new Exception("ComfyUI server could not be started or is not healthy. Aborting Auto-Pilot.");
                    }
                }

                // PHASE 1.5: Narration Agent (TTS) -- generate one audio per scene
                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < generatedScenes.size(); ni++) {
                    Scene ns = generatedScenes.get(ni);
                    updateMessage("Narration Agent (Scene " + (ni + 1) + "/" + generatedScenes.size() + "): " + ns.getSceneId());
                    if (generateNarrationAudio(ns, narrationDir)) narrationOk++;
                }
                System.out.println("[VideoArchitect] Narration: " + narrationOk + "/" + generatedScenes.size() + " scenes have audio.");

                updateMessage("Generation Agent: Processing " + generatedScenes.size() + " scenes...");
                updateProgress(0, generatedScenes.size());
                
                for (int i = 0; i < generatedScenes.size(); i++) {
                    Scene scene = generatedScenes.get(i);
                    final int currentIdx = i + 1;
                    
                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;
                    
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        updateMessage("Generation Agent (Scene " + currentIdx + "/" + generatedScenes.size() + "): " + scene.getSceneId() + (attempt > 1 ? " (Attempt " + attempt + ")" : ""));
                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                updateMessage("Scene " + scene.getSceneId() + " generated invalid file. Retrying...");
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            updateMessage("Scene " + scene.getSceneId() + " failed: " + e.getMessage() + ". Retrying...");
                            Thread.sleep(2000);
                        }
                    }
                    
                    if (!success) {
                        throw new Exception("Generation Agent failed on Scene: " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }
                    
                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }
                    
                    Platform.runLater(() -> timelineListView.refresh());
                    updateProgress(currentIdx, generatedScenes.size());
                }

                // PHASE 3: Editor Agent (FFmpeg)
                updateMessage("Editor Agent: Stitching video segments via FFmpeg...");
                updateProgress(0.5, 1.0);
                
                File exportFile = video4jEditorService.executeMasterRender(generatedScenes);

                updateProgress(1.0, 1.0);
                updateMessage("Auto-Pilot Complete! Master Video saved as: " + exportFile.getName());
                return null;
            }
        };

        renderProgressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        renderProgressBar.setVisible(true);

        task.setOnSucceeded(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            if (deconstructBtn != null) deconstructBtn.setDisable(false);
            if (autoPilotBtn != null) autoPilotBtn.setDisable(false);
            updateStatus("Auto-Pilot successful! Sequence completed.");
        });

        task.setOnFailed(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            if (deconstructBtn != null) deconstructBtn.setDisable(false);
            if (autoPilotBtn != null) autoPilotBtn.setDisable(false);
            
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            updateStatus("Auto-Pilot Failed: " + errorMsg);
            
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Agent Auto-Pilot Error");
                alert.setHeaderText("End-to-End Workflow Failed");
                alert.setContentText("Error details:\n" + errorMsg);
                alert.showAndWait();
            });
        });

        new Thread(task).start();
    }

    private void handleDeconstructScript() {
        String idea = masterScriptArea.getText().trim();
        if (idea.isEmpty()) {
            updateStatus("Master script is empty!");
            return;
        }

        setButtonsDisabled(true);
        deconstructBtn.setDisable(true);
        updateStatus("Agent is analyzing script...");

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Agent generating storyboard JSON...");
                String jsonStr = gemma4Service.generateScript(idea);
                
                // Cleanup json string if Gemma added markdown formatting
                jsonStr = jsonStr.replace("\u0000", "");
                if (jsonStr.startsWith("```json")) {
                    jsonStr = jsonStr.substring(7);
                }
                if (jsonStr.startsWith("```")) {
                    jsonStr = jsonStr.substring(3);
                }
                if (jsonStr.endsWith("```")) {
                    jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
                }
                jsonStr = jsonStr.trim();
                
                JSONArray arr = new JSONArray(jsonStr);
                
                Platform.runLater(() -> {
                    timelineListView.getItems().clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Scene scene = new Scene();
                        scene.setSceneId(obj.optString("scene_id", "S" + (i+1)));
                        scene.setPrompt(obj.optString("visual_prompt", ""));
                        scene.setNarrationText(obj.optString("narration_text", ""));
                        int dur = obj.optInt("duration_seconds", 5);
                        scene.setStartFrame(0);
                        scene.setEndFrame(dur * 24); // 24 FPS default assumption
                        scene.setCfgScale(1.0);
                        scene.setSteps(30);
                        
                        timelineListView.getItems().add(scene);
                    }
                    updateStatus("Script successfully deconstructed into " + arr.length() + " scenes!");
                });
                
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setButtonsDisabled(false);
            deconstructBtn.setDisable(false);
        });

        task.setOnFailed(e -> {
            setButtonsDisabled(false);
            deconstructBtn.setDisable(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            updateStatus("Agent failed: " + errorMsg);
            
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Agent Error");
                alert.setHeaderText("Failed to deconstruct script");
                alert.setContentText(errorMsg);
                alert.showAndWait();
            });
        });

        new Thread(task).start();
    }

    private void handleDeleteScene() {
        int index = timelineListView.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            Scene removed = timelineListView.getItems().remove(index);
            updateStatus("Deleted Scene: " + removed.getSceneId());
        } else {
            updateStatus("No scene selected to delete!");
        }
    }

    private void handleVideoGeneration(javax.swing.JLabel swingStatusLabel) {
        List<Scene> scenes = new java.util.ArrayList<>(timelineListView.getItems());
        if (scenes.isEmpty()) {
            updateStatus("Timeline is empty! Add scenes first.");
            if (swingStatusLabel != null) SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Timeline is empty!"));
            return;
        }

        setButtonsDisabled(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                // Pre-flight check
                updateMessage("Checking ComfyUI server status...");
                if (!lifecycleService.isHealthy()) {
                    updateMessage("ComfyUI server is offline. Attempting to start...");
                    lifecycleService.start();
                    int maxWait = 90;
                    boolean healthy = false;
                    for (int w = 0; w < maxWait; w++) {
                        if (lifecycleService.isHealthy()) {
                            healthy = true;
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    if (!healthy) {
                        throw new Exception("ComfyUI server could not be started or is not healthy. Aborting video generation.");
                    }
                }

                updateMessage("Starting ComfyUI generation for " + scenes.size() + " scenes...");
                updateProgress(0, scenes.size());
                
                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    final int currentIdx = i + 1;
                    
                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        updateMessage("Generating Scene (" + currentIdx + "/" + scenes.size() + "): " + scene.getSceneId() + (attempt > 1 ? " (Attempt " + attempt + ")" : "") + "...");
                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                updateMessage("Scene " + scene.getSceneId() + " generated invalid file. Retrying...");
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            updateMessage("Scene " + scene.getSceneId() + " failed: " + e.getMessage() + ". Retrying...");
                            Thread.sleep(2000);
                        }
                    }
                    
                    if (!success) {
                        throw new Exception("Could not successfully generate video for Scene " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }

                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }

                    final String msg = "Completed Scene " + scene.getSceneId() + "! Saved as " + (output != null ? output.getName() : "unknown");
                    Platform.runLater(() -> timelineListView.refresh());
                    updateProgress(currentIdx, scenes.size());
                    updateMessage(msg);
                }

                // Generate narration audio for every scene that has narration_text.
                File narrationDir2 = new File("output", "narration");
                if (!narrationDir2.exists()) narrationDir2.mkdirs();
                int narrationOk2 = 0;
                for (Scene s : scenes) {
                    if (generateNarrationAudio(s, narrationDir2)) narrationOk2++;
                }
                System.out.println("[VideoArchitect] Narration: " + narrationOk2 + "/" + scenes.size() + " scenes have audio.");
                updateMessage("All scenes generated successfully!");
                return null;
            }
        };

        renderProgressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        
        if (swingStatusLabel != null) {
            task.messageProperty().addListener((obs, oldVal, newVal) -> {
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText(newVal));
            });
        }
        
        renderProgressBar.setVisible(true);

        task.setOnSucceeded(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            if (swingStatusLabel != null) SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Done"));
            updateStatus("All " + scenes.size() + " scene videos generated! Click 'Stitch Videos' to combine.");
        });

        task.setOnFailed(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Generation failed: " + errorMsg);
            if (swingStatusLabel != null) {
                final String swingError = errorMsg;
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Generation failed: " + swingError));
            }
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Generation Error");
                alert.setHeaderText("Video Generation Failed");
                alert.setContentText("Error details:\n" + errorMsg);
                alert.showAndWait();
            });
        });

        new Thread(task).start();
    }

    private void handleMasterRender() {
        List<Scene> scenes = new ArrayList<>(timelineListView.getItems());
        if (scenes.isEmpty()) {
            updateStatus("Timeline is empty!");
            return;
        }

        setButtonsDisabled(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Checking scene videos...");
                updateProgress(0.1, 1.0);

                java.util.List<Scene> needsRegen = new java.util.ArrayList<>();
                for (Scene scene : scenes) {
                    File vf = scene.getVideoPath() == null ? null : new File(scene.getVideoPath());
                    if (vf == null || !vf.exists() || vf.length() < 1024) {
                        // Try to find the file in common well-known locations.
                        java.io.File[] roots = new java.io.File[]{
                                new java.io.File(System.getProperty("user.dir")),
                                new java.io.File(new java.io.File("").getAbsolutePath()),
                                new java.io.File(configService.getResolvedOutputDir()),
                                new java.io.File(new java.io.File(configService.getResolvedOutputDir()), "video")
                        };
                        String[] patterns = new String[]{
                                "scene_" + scene.getSceneId() + "_final.mp4",
                                "scene_" + scene.getSceneId() + "_simulated.mp4",
                                "videoarchitect_" + scene.getSceneId() + "_00001_.mp4",
                                "videoarchitect_" + scene.getSceneId() + ".mp4"
                        };
                        File found = null;
                        outer:
                        for (java.io.File r : roots) {
                            if (r == null || !r.isDirectory()) continue;
                            for (String p : patterns) {
                                File candidate = new java.io.File(r, p);
                                if (candidate.exists() && candidate.length() >= 1024) { found = candidate; break outer; }
                            }
                        }
                        if (found != null) {
                            System.out.println("[VideoArchitect] Re-attached scene " + scene.getSceneId() + " video: " + found.getAbsolutePath());
                            scene.setVideoPath(found.getAbsolutePath());
                        } else {
                            needsRegen.add(scene);
                        }
                    }
                }
                if (!needsRegen.isEmpty()) {
                    updateMessage("Regenerating " + needsRegen.size() + " missing scene(s)...");
                    for (int ri = 0; ri < needsRegen.size(); ri++) {
                        Scene s = needsRegen.get(ri);
                        updateMessage("Regenerating scene " + (ri + 1) + "/" + needsRegen.size() + ": " + s.getSceneId() + "...");
                        File regen = comfyPipelineService.generateSceneStrict(s).join();
                        if (regen != null && regen.exists() && regen.length() > 1024) {
                            s.setVideoPath(regen.getAbsolutePath());
                        } else {
                            throw new Exception("Could not regenerate video for scene: " + s.getSceneId() + ". Please Generate All Scenes first.");
                        }
                    }
                }

                updateMessage("Stitching video segments via FFmpeg...");
                updateProgress(0.5, 1.0);
                
                File exportFile = video4jEditorService.executeMasterRender(scenes);

                updateProgress(1.0, 1.0);
                updateMessage("Stitching Complete! Saved as " + exportFile.getName());
                return null;
            }
        };

        renderProgressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        renderProgressBar.setVisible(true);

        task.setOnSucceeded(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
        });

        task.setOnFailed(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Render Failed: " + errorMsg);
            
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Render Error");
                alert.setHeaderText("Master Render Failed");
                alert.setContentText("Error details:\n" + errorMsg);
                alert.showAndWait();
            });
        });

        new Thread(task).start();
    }

    public void updateTheme(boolean darkMode) {
        Platform.runLater(() -> {
            if (rootNode != null) {
                rootNode.getStyleClass().remove("light-theme");
                if (!darkMode) {
                    rootNode.getStyleClass().add("light-theme");
                }
            }
        });
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void setButtonsDisabled(boolean disabled) {
        Platform.runLater(() -> {
            if (generateVideoBtn != null) generateVideoBtn.setDisable(disabled);
            if (masterRenderBtn != null) masterRenderBtn.setDisable(disabled);
        });
    }
    private boolean generateNarrationAudio(Scene scene, File outputDir) {
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
            System.err.println("TTS generation failed for scene " + scene.getSceneId() + ": " + ttsEx.getMessage());
        }
        return false;
    }


    private static class SceneListCell extends ListCell<Scene> {
        private final VBox card = new VBox(5);
        private final Label titleLabel = new Label();
        private final Label detailsLabel = new Label();
        private final Label promptLabel = new Label();
        private final Label pathLabel = new Label();

        public SceneListCell() {
            card.getStyleClass().add("scene-card");
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -fx-custom-accent-color;");
            detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-custom-text-muted;");
            promptLabel.setStyle("-fx-font-size: 12px; -fx-wrap-text: true;");
            pathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-custom-text-muted;");
            
            card.getChildren().addAll(new HBox(10, titleLabel, detailsLabel), promptLabel, pathLabel);
        }

        @Override
        protected void updateItem(Scene item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                titleLabel.setText("Scene: " + item.getSceneId());
                detailsLabel.setText(String.format("Frames: %d | CFG: %.1f | Steps: %d",
                    (item.getEndFrame() - item.getStartFrame()), item.getCfgScale(), item.getSteps()));
                String narration = item.getNarrationText();
                String prompt = item.getPrompt();
                if (narration != null && !narration.isEmpty()) {
                    promptLabel.setText("Prompt: " + prompt + "\nNarration: " + narration);
                } else {
                    promptLabel.setText("Prompt: " + prompt);
                }
                String audioInfo = (item.getAudioPath() != null) ? " | Audio: " + item.getAudioPath() : "";
                pathLabel.setText("Video: " + (item.getVideoPath() != null ? item.getVideoPath() : "Not generated") + audioInfo);
                setGraphic(card);
            }
        }
    }
}
