package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OverlayManager {

    private final List<Stage> overlayStages = new ArrayList<>();
    private final List<Label> messageLabels = new ArrayList<>();

    private Timeline messageTimeline;
    private boolean canHideMessageByKey = false;
    private PauseTransition minDisplayTimer;

    public void showLockOverlay(String currentUsername, String imageName, WebSocketManager webSocketManager) {
        Platform.runLater(() -> {
            if (!overlayStages.isEmpty()) {
                return;
            }

            // --- REFACTORED LOGIC ---

            // 1. Calculate the total virtual bounds of all screens combined
            double minX = 0, minY = 0, maxX = 0, maxY = 0;
            boolean firstScreen = true;
            List<Screen> screens = Screen.getScreens();
            for (Screen screen : screens) {
                Rectangle2D bounds = screen.getVisualBounds();
                if (firstScreen) {
                    minX = bounds.getMinX();
                    minY = bounds.getMinY();
                    maxX = bounds.getMaxX();
                    maxY = bounds.getMaxY();
                    firstScreen = false;
                } else {
                    minX = Math.min(minX, bounds.getMinX());
                    minY = Math.min(minY, bounds.getMinY());
                    maxX = Math.max(maxX, bounds.getMaxX());
                    maxY = Math.max(maxY, bounds.getMaxY());
                }
            }
            double totalWidth = maxX - minX;
            double totalHeight = maxY - minY;

            // 2. Create a single root Pane to hold all screen elements
            Pane root = new Pane();
            // The root pane is transparent; the black background is achieved by the image views
            root.setStyle("-fx-background-color: transparent;");

            // 3. Create and position ImageViews and Labels for each screen
            for (Screen screen : screens) {
                Rectangle2D screenBounds = screen.getVisualBounds();

                // Create a container for each screen's content
                StackPane screenContainer = new StackPane();
                screenContainer.setPrefSize(screenBounds.getWidth(), screenBounds.getHeight());
                // Position this container on the giant root pane
                screenContainer.setLayoutX(screenBounds.getMinX() - minX);
                screenContainer.setLayoutY(screenBounds.getMinY() - minY);

                // Create the background image
                String imagePath = imageName.equals("Spiral 1") ? "spiral1.gif" : "spiral2.gif";
                InputStream imageStream = getClass().getResourceAsStream(imagePath);
                Objects.requireNonNull(imageStream, "Image resource not found: " + imagePath);
                Image gif = new Image(imageStream);
                ImageView imageView = new ImageView(gif);
                imageView.setFitWidth(screenBounds.getWidth());
                imageView.setFitHeight(screenBounds.getHeight());
                imageView.setPreserveRatio(false);
                imageView.setSmooth(true);
                
                // Set a black background on the container itself
                screenContainer.setStyle("-fx-background-color: black;");

                // Create the message label
                Label messageLabel = new Label();
                messageLabel.setFont(new Font("Arial", 48));
                messageLabel.setTextFill(Color.WHITE);
                messageLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 10;");
                messageLabel.setVisible(false);
                messageLabels.add(messageLabel); // Add to the class-level list for later access

                // The StackPane will automatically center the image and the label
                screenContainer.getChildren().addAll(imageView, messageLabel);
                root.getChildren().add(screenContainer);
            }

            // 4. Create a single scene and stage that spans all monitors
            Scene scene = new Scene(root, totalWidth, totalHeight);
            scene.setFill(Color.TRANSPARENT);

            // 5. Set the single key press handler on the single scene
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    hideLockOverlay();
                    webSocketManager.sendMessage(currentUsername, "unlock", null);
                    webSocketManager.sendMessage(currentUsername, "text", "UNLOCK");
                    event.consume();
                    return;
                }

                String character = event.getText();
                if (character != null && !character.isEmpty()) {
                    // This now fires regardless of which monitor has the "focus"
                    webSocketManager.sendMessage(currentUsername, "text", character);
                }

                if (canHideMessageByKey) {
                    hideMessageOnly();
                }
            });

            Stage overlayStage = new Stage();
            overlayStage.initStyle(StageStyle.UNDECORATED);
            overlayStage.initStyle(StageStyle.TRANSPARENT);
            overlayStage.setAlwaysOnTop(true);
            overlayStage.initModality(Modality.APPLICATION_MODAL);
            overlayStage.setScene(scene);

            // Position the single, giant stage
            overlayStage.setX(minX);
            overlayStage.setY(minY);

            overlayStage.show();
            overlayStage.requestFocus();

            // Store the single stage so it can be closed later
            overlayStages.add(overlayStage);
        });
    }

    private void hideMessageOnly() {
        if (messageTimeline != null) messageTimeline.stop();
        if (minDisplayTimer != null) minDisplayTimer.stop();
        for (Label label : messageLabels) {
            label.setVisible(false);
        }
        canHideMessageByKey = false;
    }

    public void hideLockOverlay() {
        Platform.runLater(() -> {
            for (Stage stage : overlayStages) {
                stage.close();
            }
            overlayStages.clear();
            messageLabels.clear();
        });
    }

    public void showMessage(String text) {
        Platform.runLater(() -> {
            if (overlayStages.isEmpty()) return;

            hideMessageOnly();

            for (Label label : messageLabels) {
                label.setText(text);
                label.setVisible(true);
            }
            canHideMessageByKey = false;

            messageTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> hideMessageOnly()));
            messageTimeline.play();
            
            minDisplayTimer = new PauseTransition(Duration.seconds(5));
            minDisplayTimer.setOnFinished(e -> canHideMessageByKey = true);
            minDisplayTimer.play();
        });
    }
}