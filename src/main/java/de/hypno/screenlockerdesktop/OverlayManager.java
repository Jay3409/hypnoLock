package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
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
    private final List<Label> clickHintLabels = new ArrayList<>();
    private final List<Label> keystrokeDisplayLabels = new ArrayList<>();
    private final StringBuilder keystrokeHistory = new StringBuilder();

    private Timeline messageTimeline;
    private boolean canHideMessageByKey = false;
    private PauseTransition minDisplayTimer;
    // --- NEW: Timer to clear keystroke history after a period of inactivity ---
    private PauseTransition keystrokeClearTimer;

    public void showLockOverlay(String currentUsername, String imageName, WebSocketManager webSocketManager) {
        Platform.runLater(() -> {
            if (!overlayStages.isEmpty()) {
                return;
            }

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
            root.setStyle("-fx-background-color: transparent;");

            // --- NEW: Initialize the timer for clearing keystrokes ---
            keystrokeClearTimer = new PauseTransition(Duration.seconds(5));
            keystrokeClearTimer.setOnFinished(e -> clearKeystrokeHistory());

            // 3. Create and position ImageViews and Labels for each screen
            for (Screen screen : screens) {
                Rectangle2D screenBounds = screen.getVisualBounds();
                StackPane screenContainer = new StackPane();
                screenContainer.setPrefSize(screenBounds.getWidth(), screenBounds.getHeight());
                screenContainer.setLayoutX(screenBounds.getMinX() - minX);
                screenContainer.setLayoutY(screenBounds.getMinY() - minY);

                String imagePath = imageName.equals("Spiral 1") ? "spiral1.gif" : "spiral2.gif";
                InputStream imageStream = getClass().getResourceAsStream(imagePath);
                Objects.requireNonNull(imageStream, "Image resource not found: " + imagePath);
                Image gif = new Image(imageStream);
                ImageView imageView = new ImageView(gif);
                imageView.setFitWidth(screenBounds.getWidth());
                imageView.setFitHeight(screenBounds.getHeight());
                imageView.setPreserveRatio(false);
                imageView.setSmooth(true);

                screenContainer.setStyle("-fx-background-color: black;");

                Label messageLabel = new Label();
                messageLabel.setFont(new Font("Arial", 48));
                messageLabel.setTextFill(Color.WHITE);
                messageLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 10;");
                messageLabel.setVisible(false);
                messageLabels.add(messageLabel);

                Label clickHintLabel = new Label("Click anywhere to focus");
                clickHintLabel.setFont(new Font("Arial", 32));
                clickHintLabel.setTextFill(Color.YELLOW);
                clickHintLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-padding: 15; -fx-border-radius: 10; -fx-background-radius: 10;");
                // --- CHANGED: Now visible by default ---
                clickHintLabel.setVisible(true);
                clickHintLabels.add(clickHintLabel);
                StackPane.setAlignment(clickHintLabel, Pos.CENTER);

                Label keystrokeDisplayLabel = new Label();
                keystrokeDisplayLabel.setFont(new Font("Monospaced", 20));
                keystrokeDisplayLabel.setTextFill(Color.WHITE);
                keystrokeDisplayLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 5;");
                keystrokeDisplayLabels.add(keystrokeDisplayLabel);
                StackPane.setAlignment(keystrokeDisplayLabel, Pos.BOTTOM_CENTER);
                keystrokeDisplayLabel.setPadding(new Insets(0, 0, 50, 0));


                screenContainer.getChildren().addAll(imageView, messageLabel, clickHintLabel, keystrokeDisplayLabel);
                root.getChildren().add(screenContainer);
            }

            // 4. Create a single scene and stage that spans all monitors
            Scene scene = new Scene(root, totalWidth, totalHeight);
            scene.setFill(Color.TRANSPARENT);

            // --- NEW: Hide hint on mouse press ---
            scene.setOnMousePressed(event -> hideClickHint());

            // 5. Set the single key press handler on the single scene
            scene.setOnKeyPressed(event -> {
                // --- NEW: Hide the hint on the first keypress ---
                hideClickHint();

                String keyText;
                if (event.getText() != null && !event.getText().isEmpty()) {
                    keyText = event.getText();
                } else {
                    keyText = "[" + event.getCode().toString() + "]";
                }

                keystrokeHistory.append(keyText);
                if (keystrokeHistory.length() > 80) {
                    keystrokeHistory.delete(0, keystrokeHistory.length() - 80);
                }
                for (Label ksLabel : keystrokeDisplayLabels) {
                    ksLabel.setText(keystrokeHistory.toString());
                }

                // --- NEW: Restart the 5-second timer to clear history on every keystroke ---
                keystrokeClearTimer.playFromStart();

                if (event.getCode() == KeyCode.ESCAPE) {
                    hideLockOverlay();
                    webSocketManager.sendMessage(currentUsername, "unlock", null);
                    webSocketManager.sendMessage(currentUsername, "text", "UNLOCK");
                    event.consume();
                    return;
                }

                String character = event.getText();
                if (character != null && !character.isEmpty()) {
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

            overlayStage.setX(minX);
            overlayStage.setY(minY);

            // The focus property listener has been removed as it was not reliable.
            
            overlayStage.show();
            overlayStage.requestFocus();

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
            if (keystrokeClearTimer != null) {
                keystrokeClearTimer.stop();
            }
            for (Stage stage : overlayStages) {
                stage.close();
            }
            overlayStages.clear();
            messageLabels.clear();
            clickHintLabels.clear();
            keystrokeDisplayLabels.clear();
            keystrokeHistory.setLength(0);
        });
    }

    public void showMessage(String text) {
        Platform.runLater(() -> {
            if (overlayStages.isEmpty()) return;
            
            // --- NEW: Clear keystroke history when a chat message arrives ---
            clearKeystrokeHistory();

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

    /**
     * Hides the "Click anywhere" hint label on all screens.
     */
    private void hideClickHint() {
        for (Label hintLabel : clickHintLabels) {
            if (hintLabel.isVisible()) {
                hintLabel.setVisible(false);
            }
        }
    }

    /**
     * Clears the keystroke history buffer and the corresponding labels.
     */
    private void clearKeystrokeHistory() {
        if (keystrokeClearTimer != null) {
            keystrokeClearTimer.stop();
        }
        keystrokeHistory.setLength(0);
        for (Label ksLabel : keystrokeDisplayLabels) {
            ksLabel.setText("");
        }
    }
}