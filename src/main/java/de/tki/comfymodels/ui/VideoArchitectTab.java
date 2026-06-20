package de.tki.comfymodels.ui;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.LocalAIService;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import de.tki.comfymodels.service.impl.Gemma4Service;
import de.tki.comfymodels.service.impl.LocalTTSService;
import de.tki.comfymodels.service.impl.VideoEditorEngine;
import de.tki.comfymodels.service.impl.DependencyService;
import de.tki.comfymodels.service.impl.Video4jEditorService;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
public class VideoArchitectTab extends JFXPanel {

    private final ConfigService configService;
    private final GeminiAIService geminiService;
    private final LocalAIService localAIService;
    private final Gemma4Service gemma4Service;
    private final ComfyPipelineService comfyPipelineService;
    private final VideoEditorEngine videoEditorEngine;
    private final LocalTTSService localTTSService;
    private final Video4jEditorService video4jEditorService;
    private final DependencyService dependencyService;

    // JavaFX Scene & Root
    private javafx.scene.Scene fxScene;
    private BorderPane rootNode;

    // JavaFX UI Elements
    private TextArea videoIdeaTextArea;
    private TextField speakerImageField;
    private ListView<Scene> timelineListView;
    private Button generateVideoBtn;
    private ProgressBar renderProgressBar;
    private Button parseIdeaBtn;
    private Button masterRenderBtn;

    // Detail Editor Fields
    private TextField sceneIdField;
    private TextArea scenePromptArea;
    private Spinner<Integer> startFrameSpinner;
    private Spinner<Integer> endFrameSpinner;
    private TextField audioPathField;
    private TextField videoPathField;
    private Slider brightnessSlider;
    private Slider contrastSlider;
    private javafx.scene.image.ImageView previewImageView;
    private Label statusLabel;

    @Autowired
    public VideoArchitectTab(ConfigService configService, GeminiAIService geminiService, 
                             LocalAIService localAIService, Gemma4Service gemma4Service,
                             ComfyPipelineService comfyPipelineService,
                             VideoEditorEngine videoEditorEngine,
                             LocalTTSService localTTSService,
                             Video4jEditorService video4jEditorService,
                             DependencyService dependencyService) {
        this.configService = configService;
        this.geminiService = geminiService;
        this.localAIService = localAIService;
        this.gemma4Service = gemma4Service;
        this.comfyPipelineService = comfyPipelineService;
        this.videoEditorEngine = videoEditorEngine;
        this.localTTSService = localTTSService;
        this.video4jEditorService = video4jEditorService;
        this.dependencyService = dependencyService;

        // Initialize JavaFX Toolkit & UI
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

            // Left Sidebar: Video Idea & Main Actions
            VBox leftSidebar = createLeftSidebar();
            
            // Right Side: Split between ListView (Timeline) & Detail Editor
            SplitPane rightSplitPane = new SplitPane();
            rightSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);

            VBox timelineBox = createTimelineBox();
            VBox detailBox = createDetailEditorBox();
            
            rightSplitPane.getItems().addAll(timelineBox, detailBox);
            rightSplitPane.setDividerPositions(0.45);

            SplitPane mainSplitPane = new SplitPane();
            mainSplitPane.getItems().addAll(leftSidebar, rightSplitPane);
            mainSplitPane.setDividerPositions(0.35);

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
        HBox.setHgrow(sidebar, Priority.ALWAYS);

        Label titleLabel = new Label("🎬 Video Architect");
        titleLabel.getStyleClass().add("label-title");

        Label subtitleLabel = new Label("Define your story, optimize timing, and compose media");
        subtitleLabel.getStyleClass().add("label-muted");

        Label ideaLabel = new Label("Video Idea");
        ideaLabel.setStyle("-fx-font-weight: bold;");

        videoIdeaTextArea = new TextArea();
        videoIdeaTextArea.setPromptText("Enter your video idea or script here... (e.g. A futuristic city transitioning from night to day with upbeat synth music)");
        videoIdeaTextArea.setWrapText(true);
        videoIdeaTextArea.setPrefRowCount(8);
        VBox.setVgrow(videoIdeaTextArea, Priority.ALWAYS);

        parseIdeaBtn = new Button("Generate Scenes from Idea");
        parseIdeaBtn.setMaxWidth(Double.MAX_VALUE);
        parseIdeaBtn.setOnAction(e -> generateScenesFromIdea());

        Label speakerLabel = new Label("Start Image / Template (Consistency)");
        speakerLabel.setStyle("-fx-font-weight: bold;");

        HBox speakerBox = new HBox(5);
        speakerImageField = new TextField(configService.getSpeakerImagePath());
        HBox.setHgrow(speakerImageField, Priority.ALWAYS);
        Button browseSpeaker = new Button("Browse...");
        browseSpeaker.setOnAction(e -> {
            File f = chooseFile("Select Start Image / Template", "*.png", "*.jpg", "*.jpeg", "*.webp");
            if (f != null) {
                speakerImageField.setText(f.getAbsolutePath());
                configService.setSpeakerImagePath(f.getAbsolutePath());
            }
        });
        speakerBox.getChildren().addAll(speakerImageField, browseSpeaker);

        Separator sep = new Separator();

        generateVideoBtn = new Button("Generate");
        generateVideoBtn.getStyleClass().add("primary-button");
        generateVideoBtn.setMaxWidth(Double.MAX_VALUE);
        generateVideoBtn.setPrefHeight(40);
        generateVideoBtn.setOnAction(e -> handleVideoGeneration());

        masterRenderBtn = new Button("Master Render");
        masterRenderBtn.setMaxWidth(Double.MAX_VALUE);
        masterRenderBtn.setPrefHeight(40);
        masterRenderBtn.setOnAction(e -> handleMasterRender());

        renderProgressBar = new ProgressBar(0);
        renderProgressBar.setMaxWidth(Double.MAX_VALUE);
        renderProgressBar.setVisible(false);

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-custom-accent-color;");

        sidebar.getChildren().addAll(
            titleLabel, subtitleLabel, sep,
            ideaLabel, videoIdeaTextArea, parseIdeaBtn,
            speakerLabel, speakerBox,
            new Separator(),
            generateVideoBtn, masterRenderBtn, renderProgressBar, statusLabel
        );

        return sidebar;
    }

    private VBox createTimelineBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("glass-panel");
        box.setPadding(new Insets(12));

        Label timelineTitle = new Label("Timeline Scenes");
        timelineTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        timelineListView = new ListView<>();
        VBox.setVgrow(timelineListView, Priority.ALWAYS);

        // Customize Cells
        timelineListView.setCellFactory(listView -> new SceneListCell());

        // Selection Listener
        timelineListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadSceneIntoEditor(newVal);
            }
        });

        box.getChildren().addAll(timelineTitle, timelineListView);
        return box;
    }

    private VBox createDetailEditorBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("glass-panel");
        box.setPadding(new Insets(12));

        Label editorTitle = new Label("Scene Editor");
        editorTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(5));

        // Column Constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(20);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(80);
        grid.getColumnConstraints().addAll(col1, col2);

        // Scene ID
        grid.add(new Label("Scene ID:"), 0, 0);
        sceneIdField = new TextField();
        grid.add(sceneIdField, 1, 0);

        // Prompt
        grid.add(new Label("Prompt:"), 0, 1);
        scenePromptArea = new TextArea();
        scenePromptArea.setPrefRowCount(2);
        scenePromptArea.setWrapText(true);
        grid.add(scenePromptArea, 1, 1);

        // Timing (Frames)
        grid.add(new Label("Timing:"), 0, 2);
        HBox timings = new HBox(10);
        timings.setAlignment(Pos.CENTER_LEFT);
        startFrameSpinner = new Spinner<>(0, 10000, 0, 10);
        startFrameSpinner.setEditable(true);
        endFrameSpinner = new Spinner<>(0, 10000, 100, 10);
        endFrameSpinner.setEditable(true);
        timings.getChildren().addAll(new Label("Start Frame:"), startFrameSpinner, new Label("End Frame:"), endFrameSpinner);
        grid.add(timings, 1, 2);

        // Audio Path
        grid.add(new Label("Audio Path:"), 0, 3);
        HBox audioBox = new HBox(5);
        audioPathField = new TextField();
        HBox.setHgrow(audioPathField, Priority.ALWAYS);
        Button browseAudio = new Button("Browse...");
        browseAudio.setOnAction(e -> {
            File f = chooseFile("Select Audio File", "*.mp3", "*.wav", "*.ogg");
            if (f != null) audioPathField.setText(f.getAbsolutePath());
        });
        audioBox.getChildren().addAll(audioPathField, browseAudio);
        grid.add(audioBox, 1, 3);

        // Video Path
        grid.add(new Label("Video Path:"), 0, 4);
        HBox videoBox = new HBox(5);
        videoPathField = new TextField();
        HBox.setHgrow(videoPathField, Priority.ALWAYS);
        Button browseVideo = new Button("Browse...");
        browseVideo.setOnAction(e -> {
            File f = chooseFile("Select Video/Image Output File", "*.mp4", "*.mkv", "*.gif", "*.png");
            if (f != null) videoPathField.setText(f.getAbsolutePath());
        });
        videoBox.getChildren().addAll(videoPathField, browseVideo);
        grid.add(videoBox, 1, 4);

        // Brightness and Contrast Sliders
        grid.add(new Label("Brightness:"), 0, 5);
        brightnessSlider = new Slider(-100, 100, 0);
        brightnessSlider.setShowTickLabels(true);
        brightnessSlider.setShowTickMarks(true);
        grid.add(brightnessSlider, 1, 5);

        grid.add(new Label("Contrast:"), 0, 6);
        contrastSlider = new Slider(0.5, 3.0, 1.0);
        contrastSlider.setShowTickLabels(true);
        contrastSlider.setShowTickMarks(true);
        grid.add(contrastSlider, 1, 6);

        // Preview ImageView
        grid.add(new Label("Enhance Preview:"), 0, 7);
        HBox previewBox = new HBox(15);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewImageView = new javafx.scene.image.ImageView();
        previewImageView.setFitWidth(160);
        previewImageView.setFitHeight(120);
        previewImageView.setPreserveRatio(true);
        previewImageView.setStyle("-fx-border-color: -fx-custom-border-color; -fx-border-width: 1px;");
        
        Button previewBtn = new Button("Update Preview");
        previewBtn.setOnAction(e -> updateVisualPreview());
        previewBox.getChildren().addAll(previewBtn, previewImageView);
        grid.add(previewBox, 1, 7);

        // Action Buttons
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button addBtn = new Button("Add Scene");
        addBtn.setOnAction(e -> handleAddScene());

        Button updateBtn = new Button("Update Selected");
        updateBtn.setOnAction(e -> handleUpdateScene());

        Button deleteBtn = new Button("Delete Selected");
        deleteBtn.setOnAction(e -> handleDeleteScene());

        Button clearBtn = new Button("Clear Timeline");
        clearBtn.setOnAction(e -> {
            timelineListView.getItems().clear();
            clearEditor();
        });

        actions.getChildren().addAll(addBtn, updateBtn, deleteBtn, clearBtn);

        box.getChildren().addAll(editorTitle, grid, actions);
        return box;
    }

    private File chooseFile(String title, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Media Files", extensions));
        return chooser.showOpenDialog(fxScene.getWindow());
    }

    private void loadSceneIntoEditor(Scene scene) {
        sceneIdField.setText(scene.getSceneId());
        scenePromptArea.setText(scene.getPrompt());
        startFrameSpinner.getValueFactory().setValue(scene.getStartFrame());
        endFrameSpinner.getValueFactory().setValue(scene.getEndFrame());
        audioPathField.setText(scene.getAudioPath() != null ? scene.getAudioPath() : "");
        videoPathField.setText(scene.getVideoPath() != null ? scene.getVideoPath() : "");
        
        // Reset sliders to defaults on load
        brightnessSlider.setValue(0);
        contrastSlider.setValue(1.0);
        
        // Auto update preview if video exists
        if (scene.getVideoPath() != null && !scene.getVideoPath().trim().isEmpty() && new File(scene.getVideoPath()).exists()) {
            updateVisualPreview();
        } else {
            previewImageView.setImage(null);
        }
    }

    private void clearEditor() {
        sceneIdField.clear();
        scenePromptArea.clear();
        startFrameSpinner.getValueFactory().setValue(0);
        endFrameSpinner.getValueFactory().setValue(100);
        audioPathField.clear();
        videoPathField.clear();
        brightnessSlider.setValue(0);
        contrastSlider.setValue(1.0);
        previewImageView.setImage(null);
    }

    private void updateVisualPreview() {
        Scene selected = timelineListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("No scene selected to preview!");
            return;
        }
        
        String inputVideo = videoPathField.getText().trim();
        if (inputVideo.isEmpty() || !new File(inputVideo).exists()) {
            updateStatus("Please set a valid Video Path first!");
            return;
        }

        String originalVideo = selected.getVideoPath();
        int originalStart = selected.getStartFrame();
        
        selected.setVideoPath(inputVideo);
        try {
            selected.setStartFrame(startFrameSpinner.getValue());
        } catch (Exception ignored) {}

        double alpha = contrastSlider.getValue();
        double beta = brightnessSlider.getValue();

        new Thread(() -> {
            try {
                javafx.scene.image.Image previewImg = video4jEditorService.applyBasicEnhancement(selected, alpha, beta);
                Platform.runLater(() -> {
                    if (previewImg != null) {
                        previewImageView.setImage(previewImg);
                        updateStatus("Visual preview updated!");
                    } else {
                        updateStatus("Could not generate visual preview.");
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> updateStatus("Preview failed: " + ex.getMessage()));
            } finally {
                selected.setVideoPath(originalVideo);
                selected.setStartFrame(originalStart);
            }
        }).start();
    }

    private void handleAddScene() {
        String id = sceneIdField.getText().trim();
        if (id.isEmpty()) {
            id = "S" + (timelineListView.getItems().size() + 1);
        }
        Scene scene = new Scene(
            id,
            scenePromptArea.getText(),
            startFrameSpinner.getValue(),
            endFrameSpinner.getValue(),
            audioPathField.getText(),
            videoPathField.getText()
        );
        timelineListView.getItems().add(scene);
        clearEditor();
        updateStatus("Added Scene: " + id);
    }

    private void handleUpdateScene() {
        Scene selected = timelineListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            updateStatus("No scene selected to update!");
            return;
        }
        selected.setSceneId(sceneIdField.getText());
        selected.setPrompt(scenePromptArea.getText());
        selected.setStartFrame(startFrameSpinner.getValue());
        selected.setEndFrame(endFrameSpinner.getValue());
        selected.setAudioPath(audioPathField.getText());
        selected.setVideoPath(videoPathField.getText());

        int index = timelineListView.getSelectionModel().getSelectedIndex();
        timelineListView.getItems().set(index, selected);
        updateStatus("Updated Scene: " + selected.getSceneId());
    }

    private void handleDeleteScene() {
        int index = timelineListView.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            Scene removed = timelineListView.getItems().remove(index);
            clearEditor();
            updateStatus("Deleted Scene: " + removed.getSceneId());
        } else {
            updateStatus("No scene selected to delete!");
        }
    }

    private void handleVideoGeneration() {
        handleVideoGeneration(null);
    }

    private void handleVideoGeneration(javax.swing.JLabel swingStatusLabel) {
        List<Scene> scenes = new java.util.ArrayList<>(timelineListView.getItems());
        if (scenes.isEmpty()) {
            updateStatus("Timeline is empty! Add scenes first.");
            if (swingStatusLabel != null) {
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Timeline is empty!"));
            }
            return;
        }

        setButtonsDisabled(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Starting ComfyUI generation for " + scenes.size() + " scenes...");
                updateProgress(0, scenes.size());
                
                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    final int currentIdx = i + 1;
                    updateMessage("Generating Scene (" + currentIdx + "/" + scenes.size() + "): " + scene.getSceneId() + "...");
                    
                    File output = comfyPipelineService.generateScene(scene).join();
                    
                    if (output != null && output.length() < 1024) {
                        Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                            alert.setTitle("Simulation Fallback");
                            alert.setHeaderText("ComfyUI Connection Offline");
                            alert.setContentText("Could not generate video for Scene " + scene.getSceneId() + " via ComfyUI. A simulated dummy file was created instead.\n\nNote: If FFmpeg is not installed/configured in your system PATH, final video stitching will fail.");
                            alert.getDialogPane().setMinWidth(450);
                            alert.show();
                        });
                    }

                    final String msg = "Completed Scene " + scene.getSceneId() + "! Saved as " + output.getName();
                    Platform.runLater(() -> {
                        timelineListView.refresh();
                    });
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
            if (swingStatusLabel != null) {
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Done"));
            }
            updateStatus("All " + scenes.size() + " scene videos generated! Click 'Master Render' to stitch final video.");
        });

        task.setOnFailed(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            Throwable ex = task.getException();
            if (ex != null) {
                System.err.println("🔴 [VideoArchitectTab] Video Generation Failed: " + ex.getMessage());
                ex.printStackTrace();
            }
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            if (ex != null && ex.getCause() != null) {
                errorMsg = ex.getCause().getMessage();
            }
            statusLabel.setText("Generation failed: " + errorMsg);
            if (swingStatusLabel != null) {
                final String swingError = errorMsg;
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Generation failed: " + swingError));
            }

            final String finalMsg = errorMsg;
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Generation Error");
                alert.setHeaderText("Video Generation Failed");
                alert.setContentText("Error details:\n" + finalMsg);
                alert.getDialogPane().setMinWidth(450);
                alert.showAndWait();
            });
        });

        task.setOnCancelled(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            statusLabel.setText("Generation cancelled");
            if (swingStatusLabel != null) {
                SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Cancelled"));
            }
        });

        new Thread(task).start();
    }

    private void generateScenesFromIdea() {
        String idea = videoIdeaTextArea.getText().trim();
        if (idea.isEmpty()) {
            updateStatus("Please write a Video Idea first!");
            return;
        }

        setButtonsDisabled(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Local Gemma parsing idea into scenes...");
                updateProgress(0.1, 1.0);

                String response;
                try {
                    response = gemma4Service.generateScript(idea);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    updateMessage("Local Gemma failed. Attempting local AI fallback...");
                    
                    // Fallback to local AI (Gemma)
                    String promptText = "You are a professional video timeline planner. Convert the following video idea into a structured list of scenes. " +
                            "Return your response ONLY as a valid JSON array of objects. Do not include markdown code block formatting. " +
                            "Each scene object MUST have these properties: 'scene_id', 'visual_prompt', 'duration_seconds', and 'narration_text'. " +
                            "IMPORTANT: Ensure the 'visual_prompt' describes only pure visual elements and NEVER contains any text, labels, overlays, typography, or subtitles.\n\n" +
                            "Video Idea:\n" + idea;
                    String gemmaResponse = getLocalAIResponse(promptText);
                    if (gemmaResponse == null || gemmaResponse.trim().isEmpty()) {
                        gemmaResponse = "[{\"scene_id\":\"S1\",\"visual_prompt\":\"A beautiful panoramic view based on: " + idea + "\",\"duration_seconds\":3,\"narration_text\":\"Welcome to our story.\"}," +
                                "{\"scene_id\":\"S2\",\"visual_prompt\":\"A detailed close-up shot developing: " + idea + "\",\"duration_seconds\":4,\"narration_text\":\"The mystery deepens.\"}]";
                    }
                    response = gemmaResponse;
                }

                if (response == null || response.trim().isEmpty()) {
                    throw new RuntimeException("Empty response from script generator.");
                }

                String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();
                cleanJson = cleanJson.replace("\u0000", "").replace("\0", "");
                validateGemmaResponse(cleanJson);
                
                JSONArray arr = new JSONArray(cleanJson);
                List<Scene> generated = new ArrayList<>();
                int frameCounter = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int duration = obj.optInt("duration_seconds", 3);
                    int start = frameCounter;
                    int end = start + (duration * 30);
                    frameCounter = end;

                    Scene sc = new Scene(
                        obj.optString("scene_id", obj.optString("sceneId", "S" + (i + 1))),
                        obj.optString("visual_prompt", obj.optString("prompt", "")),
                        start,
                        end,
                        "",
                        ""
                    );
                    sc.setNarrationText(obj.optString("narration_text", ""));
                    generated.add(sc);
                }

                updateProgress(0.4, 1.0);

                // Generate audio files via LocalTTSService
                frameCounter = 0;
                for (int i = 0; i < generated.size(); i++) {
                    Scene scene = generated.get(i);
                    final int sceneNum = i + 1;
                    updateMessage("Generating local TTS audio for " + scene.getSceneId() + " (" + sceneNum + "/" + generated.size() + ")...");
                    
                    scene.setStartFrame(frameCounter);
                    if (scene.getNarrationText() != null && !scene.getNarrationText().trim().isEmpty()) {
                        String outputPath = "output/audio/scene_" + scene.getSceneId() + ".wav";
                        try {
                            localTTSService.generateSpeech(scene.getNarrationText(), outputPath);
                            scene.setAudioPath(outputPath);
                            
                            double duration = getWavDuration(new File(outputPath));
                            if (duration > 0) {
                                  int frames = (int) Math.round(duration * 30.0);
                                  scene.setEndFrame(frameCounter + frames);
                            } else {
                                  scene.setEndFrame(frameCounter + 150);
                            }
                        } catch (Exception ex) {
                            System.err.println("⚠️ [VideoArchitectTab] Local TTS generation failed for " + scene.getSceneId() + ": " + ex.getMessage());
                            scene.setEndFrame(frameCounter + 150);
                        }
                        
                        Platform.runLater(() -> timelineListView.refresh());
                    } else {
                        scene.setEndFrame(frameCounter + 90);
                    }
                    frameCounter = scene.getEndFrame();
                    
                    double progressFraction = 0.4 + (0.6 * ((double) sceneNum / generated.size()));
                    updateProgress(progressFraction, 1.0);
                }

                Platform.runLater(() -> {
                    timelineListView.getItems().setAll(generated);
                });

                updateMessage("Gemma generated " + generated.size() + " scenes with audio!");
                updateProgress(1.0, 1.0);
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
            renderProgressBar.setProgress(0.0);
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            
            Throwable ex = task.getException();
            if (ex != null) {
                System.err.println("🔴 [VideoArchitectTab] Scene Generation Failed: " + ex.getMessage());
                ex.printStackTrace();
            }
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Scene generation failed: " + errorMsg);

            // Fallback to static scenes if everything fails
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Scene Generation Error");
                alert.setHeaderText("Scene Generation Failed");
                alert.setContentText("Error details:\n" + errorMsg + "\n\nGenerating basic static fallback scenes.");
                alert.getDialogPane().setMinWidth(450);
                alert.showAndWait();

                List<Scene> fallbackList = new ArrayList<>();
                Scene s1 = new Scene("S1", "Panoramic shot: " + idea, 0, 90, "", "");
                s1.setNarrationText("Narrator introduction");
                Scene s2 = new Scene("S2", "Close-up action: " + idea, 90, 210, "", "");
                s2.setNarrationText("Narration body");
                fallbackList.add(s1);
                fallbackList.add(s2);
                timelineListView.getItems().setAll(fallbackList);
            });
        });

        task.setOnCancelled(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            statusLabel.setText("Scene generation cancelled");
        });

        new Thread(task).start();
    }

    private void validateGemmaResponse(String response) throws Exception {
        JSONArray arr = new JSONArray(response);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            if (!obj.has("scene_id") || !obj.has("visual_prompt") || 
                !obj.has("duration_seconds") || !obj.has("narration_text")) {
                throw new Exception("Scene object at index " + i + " is missing required Gemma4 fields.");
            }
        }
    }

    private String getGeminiResponse(String promptText) throws Exception {
        // Simple direct invoke since we don't want to duplicate full request logic
        JSONObject payload = new JSONObject();
        JSONArray contents = new JSONArray();
        contents.put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", promptText))));
        payload.put("contents", contents);

        String model = geminiService.getActiveModel();
        if (model == null || "None".equals(model)) {
            model = geminiService.discoverBestModel();
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + configService.getGeminiApiKey()))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return new JSONObject(response.body()).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text").trim();
        }
        return null;
    }

    private String getLocalAIResponse(String promptText) {
        try {
            return localAIService.generateText(promptText, 0.7f, 512);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateStatus(String text) {
        statusLabel.setText(text);
    }

    private void setButtonsDisabled(boolean disabled) {
        Platform.runLater(() -> {
            if (parseIdeaBtn != null) parseIdeaBtn.setDisable(disabled);
            if (generateVideoBtn != null) generateVideoBtn.setDisable(disabled);
            if (masterRenderBtn != null) masterRenderBtn.setDisable(disabled);
        });
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


    private void handleMasterRender() {
        List<Scene> scenes = new ArrayList<>(timelineListView.getItems());
        if (scenes.isEmpty()) {
            updateStatus("Timeline is empty! Add scenes first.");
            return;
        }

        setButtonsDisabled(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Starting Master Render...");
                updateProgress(0.05, 1.0);

                // Phase 1: Ensure all scenes have audio
                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    String aPath = scene.getAudioPath();
                    if (aPath == null || aPath.trim().isEmpty() || !new File(aPath).exists()) {
                        updateMessage("Generating missing audio for " + scene.getSceneId() + " (" + (i+1) + "/" + scenes.size() + ")...");
                        if (scene.getNarrationText() != null && !scene.getNarrationText().trim().isEmpty()) {
                            String outputPath = "output/audio/scene_" + scene.getSceneId() + ".wav";
                            try {
                                localTTSService.generateSpeech(scene.getNarrationText(), outputPath);
                                scene.setAudioPath(outputPath);
                                Platform.runLater(() -> timelineListView.refresh());
                            } catch (Exception ex) {
                                System.err.println("⚠️ [VideoArchitectTab] Master Render - Local TTS generation failed for " + scene.getSceneId() + ": " + ex.getMessage());
                            }
                        }
                    }
                    updateProgress(0.05 + (0.15 * ((double)(i+1) / scenes.size())), 1.0);
                }

                // Phase 2: Ensure all scenes have video (generate via ComfyUI if missing)
                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    String vPath = scene.getVideoPath();
                    if (vPath == null || vPath.trim().isEmpty() || !new File(vPath).exists() || new File(vPath).length() < 1024) {
                        updateMessage("Generating video via ComfyUI for " + scene.getSceneId() + " (" + (i+1) + "/" + scenes.size() + ")...");
                        try {
                            File videoFile = comfyPipelineService.generateScene(scene).join();
                            Platform.runLater(() -> timelineListView.refresh());
                        } catch (Exception ex) {
                            System.err.println("⚠️ [VideoArchitectTab] Master Render - Video generation failed for " + scene.getSceneId() + ": " + ex.getMessage());
                        }
                    }
                    updateProgress(0.2 + (0.2 * ((double)(i+1) / scenes.size())), 1.0);
                }

                updateProgress(0.4, 1.0);
                updateMessage("Stitching video segments and audio tracks via FFmpeg...");
                
                File exportFile = video4jEditorService.executeMasterRender(scenes);

                updateProgress(1.0, 1.0);
                updateMessage("Master Render Complete! Saved as " + exportFile.getName());
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
            setButtonsDisabled(false);
            Throwable ex = task.getException();
            if (ex != null) {
                System.err.println("🔴 [VideoArchitectTab] Master Render Failed: " + ex.getMessage());
                ex.printStackTrace();
            }
            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Master Render Failed: " + errorMsg);
            renderProgressBar.setProgress(0.0);
            renderProgressBar.setVisible(false);

            final String finalMsg = errorMsg;
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Render Error");
                alert.setHeaderText("Master Render Failed");
                alert.setContentText("Stitching/Rendering process failed.\n\nError details:\n" + finalMsg + "\n\nPlease ensure that FFmpeg is installed and added to your system environment variables (PATH) so that segments can be merged.");
                alert.getDialogPane().setMinWidth(450);
                alert.showAndWait();
            });
        });

        task.setOnCancelled(e -> {
            renderProgressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            renderProgressBar.setVisible(false);
            setButtonsDisabled(false);
            statusLabel.setText("Master Render cancelled");
        });

        new Thread(task).start();
    }

    public void startGenerationWithParams(String prompt, int cfg, int steps, int motionBucket, javax.swing.JLabel swingStatusLabel) {
        if (swingStatusLabel != null) {
            SwingUtilities.invokeLater(() -> swingStatusLabel.setText("Starting..."));
        }

        Platform.runLater(() -> {
            if (timelineListView.getItems().isEmpty()) {
                Scene tempScene = new Scene("S1", prompt, 0, 90, "", "");
                tempScene.setCfgScale(cfg);
                tempScene.setSteps(steps);
                tempScene.setMotionBucketId(motionBucket);
                timelineListView.getItems().add(tempScene);
            } else {
                for (Scene scene : timelineListView.getItems()) {
                    scene.setPrompt(prompt);
                    scene.setCfgScale(cfg);
                    scene.setSteps(steps);
                    scene.setMotionBucketId(motionBucket);
                }
            }
            timelineListView.refresh();
            handleVideoGeneration(swingStatusLabel);
        });
    }

    private double getWavDuration(File file) {
        if (file == null || !file.exists()) return 0.0;
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            double durationInSeconds = (double) frames / format.getFrameRate();
            return durationInSeconds;
        } catch (Exception e) {
            System.err.println("⚠️ [VideoArchitectTab] Could not read audio duration for " + file.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    // Custom Cell Factory for Scenic Cards
    private static class SceneListCell extends ListCell<Scene> {
        private final VBox card = new VBox(5);
        private final Label titleLabel = new Label();
        private final Label frameLabel = new Label();
        private final Label promptLabel = new Label();
        private final Label pathsLabel = new Label();

        public SceneListCell() {
            card.getStyleClass().add("scene-card");
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -fx-custom-accent-color;");
            frameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-custom-text-muted;");
            promptLabel.setStyle("-fx-font-size: 12px; -fx-wrap-text: true;");
            pathsLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -fx-custom-text-muted; -fx-wrap-text: true;");
            
            card.getChildren().addAll(
                new HBox(10, titleLabel, frameLabel),
                promptLabel,
                pathsLabel
            );
        }

        @Override
        protected void updateItem(Scene item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                titleLabel.setText("Scene: " + item.getSceneId());
                frameLabel.setText("Frames: " + item.getStartFrame() + " - " + item.getEndFrame());
                promptLabel.setText("Prompt: " + item.getPrompt());
                
                StringBuilder paths = new StringBuilder();
                if (item.getAudioPath() != null && !item.getAudioPath().isEmpty()) {
                    paths.append("Audio: ").append(item.getAudioPath()).append("\n");
                }
                if (item.getVideoPath() != null && !item.getVideoPath().isEmpty()) {
                    paths.append("Video: ").append(item.getVideoPath());
                }
                pathsLabel.setText(paths.toString().trim());
                
                setGraphic(card);
            }
        }
    }
}
