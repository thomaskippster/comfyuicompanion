package de.tki.comfymodels.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class VideoArchitectTab extends JFXPanel {
    private static final Logger logger = LoggerFactory.getLogger(VideoArchitectTab.class);

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final Gemma4Service gemma4Service;
    @Autowired(required = false)
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
    private Button addSceneBtn;
    private Button editSceneBtn;
    private Button moveUpBtn;
    private Button moveDownBtn;
    private Button playVideoBtn;
    private Button deleteBtn;
    private Button clearListBtn;
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

        this.setOpaque(false);
        this.setBackground(new java.awt.Color(0, 0, 0, 0));

        try {
            Platform.setImplicitExit(false);
        } catch (Throwable ignored) {}

        ThemeManager.registerObserver(() -> {
            updateTheme(ThemeManager.isDarkMode());
        });

        Platform.runLater(this::initFX);
    }

    private void initFX() {
        try {
            rootNode = new BorderPane();
            rootNode.setPadding(new Insets(15));
            rootNode.getStyleClass().add("root");

            // Apply style sheet safely
            try {
                java.net.URL cssRes = getClass().getResource("/css/video-architect.css");
                if (cssRes != null) {
                    rootNode.getStylesheets().add(cssRes.toExternalForm());
                } else {
                    logger.warn("CSS resource /css/video-architect.css not found.");
                }
            } catch (Exception ex) {
                logger.warn("Failed to load video-architect.css: {}", ex.getMessage());
            }

            // Synchronize starting theme
            boolean isDark = ThemeManager.isDarkMode();
            if (!isDark) {
                rootNode.getStyleClass().add("light-theme");
            }

            SplitPane mainSplitPane = new SplitPane();
            mainSplitPane.getItems().addAll(createLeftSidebar(), createRightArea());
            mainSplitPane.setDividerPositions(0.38);

            rootNode.setCenter(mainSplitPane);

            fxScene = new javafx.scene.Scene(rootNode);
            fxScene.setFill(isDark ? javafx.scene.paint.Color.rgb(10, 11, 14) : javafx.scene.paint.Color.rgb(245, 245, 250));
            setScene(fxScene);
        } catch (Throwable e) {
            logger.error("Error initializing Video Architect JavaFX UI: {}", e.getMessage(), e);
        }
    }

    private VBox createLeftSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("glass-panel");
        sidebar.setPadding(new Insets(15));

        HBox titleBox = new HBox(8);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label("🎬 Video Architect");
        titleLabel.getStyleClass().add("label-title");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px;");

        Label betaBadge = new Label("BETA");
        betaBadge.setStyle("-fx-background-color: rgba(234, 179, 8, 0.18); -fx-text-fill: #eab308; " +
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 7px; " +
                "-fx-background-radius: 10px; -fx-border-color: rgba(234, 179, 8, 0.45); -fx-border-radius: 10px;");

        titleBox.getChildren().addAll(titleLabel, betaBadge);

        Label subtitleLabel = new Label("Compose and generate multi-scene AI videos with synchronized narration and editing (Beta feature).");
        subtitleLabel.getStyleClass().add("label-muted");
        subtitleLabel.setWrapText(true);

        // Global Settings
        Label speakerLabel = new Label("Global Start Image (Consistency)");
        speakerLabel.setStyle("-fx-font-weight: bold;");

        HBox speakerBox = new HBox(5);
        speakerImageField = new TextField(configService != null ? configService.getSpeakerImagePath() : "");
        HBox.setHgrow(speakerImageField, Priority.ALWAYS);
        Button browseSpeaker = new Button("Browse...");
        browseSpeaker.setOnAction(e -> {
            File f = chooseFile("Select Start Image", "*.png", "*.jpg", "*.jpeg", "*.webp");
            if (f != null) {
                speakerImageField.setText(f.getAbsolutePath());
                if (configService != null) {
                    configService.setSpeakerImagePath(f.getAbsolutePath());
                }
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
        masterScriptArea.setPrefRowCount(5);
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
        writerBox.getStyleClass().add("card-panel");

        sidebar.getChildren().addAll(
            titleBox, subtitleLabel, 
            speakerLabel, speakerBox, 
            sep0, 
            writerBox
        );

        return sidebar;
    }

    private VBox createRightArea() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card-panel");
        box.setPadding(new Insets(15));
        HBox.setHgrow(box, Priority.ALWAYS);

        Label timelineTitle = new Label("Timeline & Scene Sequence");
        timelineTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        timelineListView = new ListView<>();
        VBox.setVgrow(timelineListView, Priority.ALWAYS);
        timelineListView.setCellFactory(listView -> new SceneListCell());
        timelineListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Scene selected = timelineListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showEditSceneDialog(selected, false);
                }
            }
        });

        // Timeline management buttons
        HBox listActions = new HBox(8);
        listActions.setAlignment(Pos.CENTER_LEFT);

        addSceneBtn = new Button("➕ Add Scene");
        addSceneBtn.setOnAction(e -> showEditSceneDialog(null, true));

        editSceneBtn = new Button("✏️ Edit");
        editSceneBtn.setOnAction(e -> {
            Scene selected = timelineListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditSceneDialog(selected, false);
            } else {
                updateStatus("Please select a scene to edit.");
            }
        });

        moveUpBtn = new Button("⬆ Up");
        moveUpBtn.setOnAction(e -> handleMoveScene(-1));

        moveDownBtn = new Button("⬇ Down");
        moveDownBtn.setOnAction(e -> handleMoveScene(1));

        playVideoBtn = new Button("▶ Play Video");
        playVideoBtn.setOnAction(e -> handlePreviewSceneVideo());

        deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> handleDeleteScene());

        clearListBtn = new Button("Clear All");
        clearListBtn.getStyleClass().add("danger-button");
        clearListBtn.setOnAction(e -> timelineListView.getItems().clear());

        listActions.getChildren().addAll(
            addSceneBtn, editSceneBtn, moveUpBtn, moveDownBtn, playVideoBtn, deleteBtn, clearListBtn
        );

        Separator sep = new Separator();

        generateVideoBtn = new Button("1. Generate All Scenes");
        generateVideoBtn.getStyleClass().add("primary-button");
        generateVideoBtn.setMaxWidth(Double.MAX_VALUE);
        generateVideoBtn.setPrefHeight(40);
        generateVideoBtn.setOnAction(e -> handleVideoGeneration(null));

        masterRenderBtn = new Button("2. Stitch Videos (Master Render)");
        masterRenderBtn.getStyleClass().add("primary-button");
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
        return chooser.showOpenDialog(fxScene != null ? fxScene.getWindow() : null);
    }

    private void handleMoveScene(int direction) {
        int index = timelineListView.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            updateStatus("Select a scene to reorder.");
            return;
        }
        int newIndex = index + direction;
        if (newIndex >= 0 && newIndex < timelineListView.getItems().size()) {
            Scene scene = timelineListView.getItems().remove(index);
            timelineListView.getItems().add(newIndex, scene);
            timelineListView.getSelectionModel().select(newIndex);
            updateStatus("Moved scene " + scene.getSceneId() + " to position " + (newIndex + 1));
        }
    }

    private void handlePreviewSceneVideo() {
        Scene selected = timelineListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("Select a scene to preview.");
            return;
        }
        String path = selected.getVideoPath();
        if (path == null || path.trim().isEmpty()) {
            updateStatus("Scene " + selected.getSceneId() + " has no generated video yet.");
            return;
        }
        File vf = new File(path);
        if (!vf.exists()) {
            updateStatus("Video file not found at: " + path);
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(vf);
                updateStatus("Opening video for scene: " + selected.getSceneId());
            } else {
                updateStatus("System desktop open not supported. Video: " + path);
            }
        } catch (Exception ex) {
            logger.error("Failed to open video file: {}", ex.getMessage());
            updateStatus("Failed to open video: " + ex.getMessage());
        }
    }

    private void showEditSceneDialog(Scene targetScene, boolean isNew) {
        Dialog<Scene> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Add New Scene" : "Edit Scene: " + targetScene.getSceneId());
        dialog.setHeaderText(isNew ? "Define Scene Parameters" : "Edit Scene Parameters");

        try {
            java.net.URL cssRes = getClass().getResource("/css/video-architect.css");
            if (cssRes != null) {
                dialog.getDialogPane().getStylesheets().add(cssRes.toExternalForm());
            }
            if (!ThemeManager.isDarkMode()) {
                dialog.getDialogPane().getStyleClass().add("light-theme");
            }
        } catch (Exception ignored) {}

        ButtonType saveButtonType = new ButtonType(isNew ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField sceneIdField = new TextField();
        sceneIdField.setPromptText("S1");
        if (isNew) {
            int nextId = timelineListView.getItems().size() + 1;
            sceneIdField.setText("S" + nextId);
        } else if (targetScene != null) {
            sceneIdField.setText(targetScene.getSceneId());
        }

        TextArea promptArea = new TextArea();
        promptArea.setPromptText("Visual prompt for ComfyUI generation...");
        promptArea.setPrefRowCount(3);
        promptArea.setWrapText(true);
        if (targetScene != null && targetScene.getPrompt() != null) {
            promptArea.setText(targetScene.getPrompt());
        }

        TextArea narrationArea = new TextArea();
        narrationArea.setPromptText("Voiceover / narration text for TTS audio...");
        narrationArea.setPrefRowCount(2);
        narrationArea.setWrapText(true);
        if (targetScene != null && targetScene.getNarrationText() != null) {
            narrationArea.setText(targetScene.getNarrationText());
        }

        int currentDuration = 5;
        if (targetScene != null && targetScene.getEndFrame() > targetScene.getStartFrame()) {
            currentDuration = Math.max(1, (targetScene.getEndFrame() - targetScene.getStartFrame()) / 24);
        }
        Spinner<Integer> durationSpinner = new Spinner<>(1, 60, currentDuration);
        durationSpinner.setEditable(true);

        grid.add(new Label("Scene ID:"), 0, 0);
        grid.add(sceneIdField, 1, 0);
        grid.add(new Label("Visual Prompt:"), 0, 1);
        grid.add(promptArea, 1, 1);
        grid.add(new Label("Narration Text:"), 0, 2);
        grid.add(narrationArea, 1, 2);
        grid.add(new Label("Duration (seconds):"), 0, 3);
        grid.add(durationSpinner, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Scene sc = isNew ? new Scene() : targetScene;
                sc.setSceneId(sceneIdField.getText().trim().isEmpty() ? "S1" : sceneIdField.getText().trim());
                sc.setPrompt(promptArea.getText().trim());
                sc.setNarrationText(narrationArea.getText().trim());
                int dur = durationSpinner.getValue();
                sc.setStartFrame(0);
                sc.setEndFrame(dur * 24);
                sc.setCfgScale(1.0);
                sc.setSteps(30);
                return sc;
            }
            return null;
        });

        Optional<Scene> result = dialog.showAndWait();
        result.ifPresent(sc -> {
            if (isNew) {
                timelineListView.getItems().add(sc);
                updateStatus("Added new Scene: " + sc.getSceneId());
            } else {
                timelineListView.refresh();
                updateStatus("Updated Scene: " + sc.getSceneId());
            }
        });
    }

    /**
     * Parses the Gemma / LLM storyboard response or gracefully falls back to text segmentation.
     */
    private List<Scene> parseStoryboardJson(String rawResponse, String originalIdea) {
        List<Scene> scenes = new ArrayList<>();
        if (rawResponse != null && !rawResponse.trim().isEmpty()) {
            try {
                String clean = rawResponse.replaceAll("```json", "").replaceAll("```", "").replace("\u0000", "").trim();
                int startArr = clean.indexOf('[');
                int endArr = clean.lastIndexOf(']');
                
                if (startArr >= 0 && endArr > startArr) {
                    clean = clean.substring(startArr, endArr + 1);
                    JSONArray arr = new JSONArray(clean);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Scene scene = parseSceneObject(obj, i + 1);
                        if (scene != null) {
                            scenes.add(scene);
                        }
                    }
                } else {
                    int startObj = clean.indexOf('{');
                    int endObj = clean.lastIndexOf('}');
                    if (startObj >= 0 && endObj > startObj) {
                        JSONObject rootObj = new JSONObject(clean.substring(startObj, endObj + 1));
                        JSONArray arr = rootObj.optJSONArray("scenes");
                        if (arr == null) arr = rootObj.optJSONArray("storyboard");
                        if (arr == null) arr = rootObj.optJSONArray("timeline");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                Scene scene = parseSceneObject(obj, i + 1);
                                if (scene != null) {
                                    scenes.add(scene);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("JSON parsing failed on Gemma response: {}. Falling back to rule-based segmentation.", ex.getMessage());
            }
        }

        if (scenes.isEmpty() && originalIdea != null && !originalIdea.trim().isEmpty()) {
            scenes = splitScriptFallback(originalIdea);
        }

        return scenes;
    }

    private Scene parseSceneObject(JSONObject obj, int fallbackIndex) {
        String id = obj.optString("scene_id", obj.optString("id", "S" + fallbackIndex));
        String prompt = obj.optString("visual_prompt", obj.optString("prompt", obj.optString("description", "")));
        String narration = obj.optString("narration_text", obj.optString("narration", obj.optString("voiceover", "")));
        int dur = obj.optInt("duration_seconds", obj.optInt("duration", 5));
        if (dur <= 0) dur = 5;

        if (prompt.isEmpty() && narration.isEmpty()) return null;

        Scene scene = new Scene();
        scene.setSceneId(id);
        scene.setPrompt(prompt.isEmpty() ? narration : prompt);
        scene.setNarrationText(narration.isEmpty() ? prompt : narration);
        scene.setStartFrame(0);
        scene.setEndFrame(dur * 24);
        scene.setCfgScale(1.0);
        scene.setSteps(30);
        return scene;
    }

    /**
     * Smart rule-based fallback that decomposes user text into coherent visual scenes.
     */
    private List<Scene> splitScriptFallback(String idea) {
        List<Scene> fallbackScenes = new ArrayList<>();
        String[] sentences = idea.split("(?<=[.!?\\n])\\s+");
        int count = 1;
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 2) {
                Scene sc = new Scene();
                sc.setSceneId("S" + count++);
                sc.setPrompt("Cinematic shot, 4k resolution, high quality, " + trimmed);
                sc.setNarrationText(trimmed);
                int duration = Math.min(8, Math.max(3, (trimmed.split("\\s+").length / 2) + 2));
                sc.setStartFrame(0);
                sc.setEndFrame(duration * 24);
                sc.setCfgScale(1.0);
                sc.setSteps(30);
                fallbackScenes.add(sc);
            }
        }
        if (fallbackScenes.isEmpty() && !idea.trim().isEmpty()) {
            Scene single = new Scene("S1", "Cinematic shot, " + idea.trim(), 0, 120, "", "");
            single.setNarrationText(idea.trim());
            fallbackScenes.add(single);
        }
        return fallbackScenes;
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
                String jsonStr = null;
                try {
                    jsonStr = gemma4Service.generateScript(idea);
                } catch (Exception ex) {
                    logger.warn("Gemma script generation threw exception: {}. Using fallback deconstruction.", ex.getMessage());
                }

                List<Scene> generatedScenes = parseStoryboardJson(jsonStr, idea);
                if (generatedScenes.isEmpty()) {
                    throw new Exception("Unable to deconstruct script into timeline scenes.");
                }

                Platform.runLater(() -> {
                    timelineListView.getItems().clear();
                    timelineListView.getItems().addAll(generatedScenes);
                });

                // PHASE 1.5: Narration Agent (TTS) -- generate audio before video so ComfyPipeline can mux it
                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < generatedScenes.size(); ni++) {
                    Scene ns = generatedScenes.get(ni);
                    updateMessage("Narration Agent (Scene " + (ni + 1) + "/" + generatedScenes.size() + "): " + ns.getSceneId());
                    if (generateNarrationAudio(ns, narrationDir)) narrationOk++;
                }
                logger.info("[VideoArchitect] Narration: {}/{} scenes have audio.", narrationOk, generatedScenes.size());

                // PHASE 2: Generation Agent (ComfyUI)
                updateMessage("Checking ComfyUI server status...");
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
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

        if (gemma4Service != null && !gemma4Service.isGemmaAvailable()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Gemma AI Storyboard Deconstruction");
            confirm.setHeaderText("Gemma-3-4B AI Model Required");
            confirm.setContentText("The local Gemma-3-4B AI model is not downloaded yet.\n\n" +
                    "Would you like to download the Gemma-3-4B model (~3 GB) now to unlock intelligent AI script deconstruction, visual prompt engineering, and scene timing?\n\n" +
                    "(Click 'Cancel' to use the standard rule-based segmentation)");

            Optional<ButtonType> opt = confirm.showAndWait();
            if (opt.isPresent() && opt.get() == ButtonType.OK) {
                downloadGemmaAndDeconstruct(idea);
                return;
            }
        }

        executeDeconstructScriptTask(idea);
    }

    private void downloadGemmaAndDeconstruct(String idea) {
        setButtonsDisabled(true);
        if (deconstructBtn != null) deconstructBtn.setDisable(true);
        renderProgressBar.setProgress(0);
        renderProgressBar.setVisible(true);
        updateStatus("Downloading Gemma-3-4B model from Hugging Face...");

        gemma4Service.downloadGemmaModel(
            (percent, msg) -> Platform.runLater(() -> {
                renderProgressBar.setProgress(percent);
                updateStatus(msg);
            }),
            () -> Platform.runLater(() -> {
                renderProgressBar.setVisible(false);
                updateStatus("✅ Gemma model downloaded successfully! Starting AI script deconstruction...");
                executeDeconstructScriptTask(idea);
            }),
            (errorMsg, ex) -> Platform.runLater(() -> {
                renderProgressBar.setVisible(false);
                setButtonsDisabled(false);
                if (deconstructBtn != null) deconstructBtn.setDisable(false);
                updateStatus("❌ Gemma download failed: " + errorMsg);
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Gemma Download Failed");
                errorAlert.setHeaderText("Could not download Gemma AI model");
                errorAlert.setContentText(errorMsg);
                errorAlert.showAndWait();
            })
        );
    }

    private void executeDeconstructScriptTask(String idea) {
        setButtonsDisabled(true);
        if (deconstructBtn != null) deconstructBtn.setDisable(true);
        boolean usingGemma = gemma4Service != null && gemma4Service.isGemmaAvailable();
        updateStatus(usingGemma ? "🤖 Gemma AI: Analyzing script and generating storyboard..." : "Analyzing script...");

        javafx.concurrent.Task<List<Scene>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Scene> call() throws Exception {
                String jsonStr = null;
                if (gemma4Service != null && gemma4Service.isGemmaAvailable()) {
                    updateMessage("🤖 Gemma AI: Composing cinematic scenes and narration...");
                    try {
                        jsonStr = gemma4Service.generateScript(idea);
                    } catch (Exception ex) {
                        logger.warn("Gemma script generation threw: {}. Falling back to rule-based parser.", ex.getMessage());
                    }
                }

                return parseStoryboardJson(jsonStr, idea);
            }
        };

        task.setOnSucceeded(e -> {
            setButtonsDisabled(false);
            if (deconstructBtn != null) deconstructBtn.setDisable(false);
            List<Scene> scenes = task.getValue();
            timelineListView.getItems().clear();
            timelineListView.getItems().addAll(scenes);
            boolean usedGemma = usingGemma && scenes != null && !scenes.isEmpty();
            updateStatus(usedGemma ? "✅ Gemma AI: Successfully deconstructed script into " + scenes.size() + " scenes!" 
                                   : "Script deconstructed into " + scenes.size() + " scenes.");
        });

        task.setOnFailed(e -> {
            setButtonsDisabled(false);
            if (deconstructBtn != null) deconstructBtn.setDisable(false);
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
        List<Scene> scenes = new ArrayList<>(timelineListView.getItems());
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
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
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

                // Generate narration audio BEFORE video generation so ComfyPipeline can mux audio directly into MP4
                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < scenes.size(); ni++) {
                    Scene s = scenes.get(ni);
                    updateMessage("Generating narration (" + (ni + 1) + "/" + scenes.size() + "): " + s.getSceneId());
                    if (generateNarrationAudio(s, narrationDir)) narrationOk++;
                }
                logger.info("[VideoArchitect] Narration audio ready for {}/{} scenes.", narrationOk, scenes.size());

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

                List<Scene> needsRegen = new ArrayList<>();
                for (Scene scene : scenes) {
                    File vf = scene.getVideoPath() == null ? null : new File(scene.getVideoPath());
                    if (vf == null || !vf.exists() || vf.length() < 1024) {
                        File[] roots = new File[]{
                                new File(System.getProperty("user.dir")),
                                new File(new File("").getAbsolutePath()),
                                new File(configService != null ? configService.getResolvedOutputDir() : "output"),
                                new File(new File(configService != null ? configService.getResolvedOutputDir() : "output"), "video")
                        };
                        String[] patterns = new String[]{
                                "scene_" + scene.getSceneId() + "_final.mp4",
                                "scene_" + scene.getSceneId() + "_simulated.mp4",
                                "videoarchitect_" + scene.getSceneId() + "_00001_.mp4",
                                "videoarchitect_" + scene.getSceneId() + ".mp4"
                        };
                        File found = null;
                        outer:
                        for (File r : roots) {
                            if (r == null || !r.isDirectory()) continue;
                            for (String p : patterns) {
                                File candidate = new File(r, p);
                                if (candidate.exists() && candidate.length() >= 1024) { found = candidate; break outer; }
                            }
                        }
                        if (found != null) {
                            logger.info("[VideoArchitect] Re-attached scene {} video: {}", scene.getSceneId(), found.getAbsolutePath());
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
            if (fxScene != null) {
                fxScene.setFill(darkMode ? javafx.scene.paint.Color.rgb(10, 11, 14) : javafx.scene.paint.Color.rgb(245, 245, 250));
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
            if (addSceneBtn != null) addSceneBtn.setDisable(disabled);
            if (editSceneBtn != null) editSceneBtn.setDisable(disabled);
            if (moveUpBtn != null) moveUpBtn.setDisable(disabled);
            if (moveDownBtn != null) moveDownBtn.setDisable(disabled);
            if (deleteBtn != null) deleteBtn.setDisable(disabled);
            if (clearListBtn != null) clearListBtn.setDisable(disabled);
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
            logger.error("TTS generation failed for scene {}: {}", scene.getSceneId(), ttsEx.getMessage());
        }
        return false;
    }

    private static class SceneListCell extends ListCell<Scene> {
        private final VBox card = new VBox(4);
        private final Label titleLabel = new Label();
        private final Label statusBadge = new Label();
        private final Label detailsLabel = new Label();
        private final Label promptLabel = new Label();
        private final Label pathLabel = new Label();

        public SceneListCell() {
            card.getStyleClass().add("scene-card");
            card.setPadding(new Insets(6, 10, 6, 10));
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -fx-custom-accent-color;");
            statusBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-custom-text-muted;");
            promptLabel.setStyle("-fx-font-size: 12px; -fx-wrap-text: true;");
            pathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-custom-text-muted;");

            HBox headerBox = new HBox(8, titleLabel, statusBadge, detailsLabel);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(headerBox, promptLabel, pathLabel);
        }

        @Override
        protected void updateItem(Scene item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                titleLabel.setText("Scene: " + item.getSceneId());
                int durSecs = Math.max(1, (item.getEndFrame() - item.getStartFrame()) / 24);
                detailsLabel.setText(String.format("(%ds, %d frames | CFG: %.1f | Steps: %d)",
                    durSecs, (item.getEndFrame() - item.getStartFrame()), item.getCfgScale(), item.getSteps()));

                boolean hasVideo = item.getVideoPath() != null && new File(item.getVideoPath()).exists();
                boolean hasAudio = item.getAudioPath() != null && new File(item.getAudioPath()).exists();

                if (hasVideo && hasAudio) {
                    statusBadge.setText("🎬 Video & Audio Ready");
                    statusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.18); -fx-text-fill: #10b981; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 8px; " +
                            "-fx-background-radius: 10px; -fx-border-color: rgba(16, 185, 129, 0.4); -fx-border-radius: 10px;");
                } else if (hasVideo) {
                    statusBadge.setText("🎬 Video Ready");
                    statusBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.18); -fx-text-fill: #10b981; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 8px; " +
                            "-fx-background-radius: 10px; -fx-border-color: rgba(16, 185, 129, 0.4); -fx-border-radius: 10px;");
                } else if (hasAudio) {
                    statusBadge.setText("🎙️ Audio Ready");
                    statusBadge.setStyle("-fx-background-color: rgba(59, 130, 246, 0.18); -fx-text-fill: #3b82f6; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 8px; " +
                            "-fx-background-radius: 10px; -fx-border-color: rgba(59, 130, 246, 0.4); -fx-border-radius: 10px;");
                } else {
                    statusBadge.setText("⏳ Pending");
                    statusBadge.setStyle("-fx-background-color: rgba(112, 138, 144, 0.18); -fx-text-fill: #708a90; " +
                            "-fx-font-size: 10px; -fx-padding: 2px 8px; " +
                            "-fx-background-radius: 10px; -fx-border-color: rgba(112, 138, 144, 0.4); -fx-border-radius: 10px;");
                }

                String narration = item.getNarrationText();
                String prompt = item.getPrompt();
                if (narration != null && !narration.isEmpty()) {
                    promptLabel.setText("Visual: " + prompt + "\nNarration: " + narration);
                } else {
                    promptLabel.setText("Visual: " + prompt);
                }
                String audioInfo = hasAudio ? " | Audio: " + new File(item.getAudioPath()).getName() : "";
                pathLabel.setText("File: " + (hasVideo ? item.getVideoPath() : "Not generated") + audioInfo);
                setGraphic(card);
            }
        }
    }
}
