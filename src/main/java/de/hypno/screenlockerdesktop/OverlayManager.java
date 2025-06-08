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
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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

            List<Screen> screens = Screen.getScreens();
            for (Screen screen : screens) {
                String imagePath = imageName.equals("Spiral 1") ? "spiral1.gif" : "spiral2.gif";
                InputStream imageStream = getClass().getResourceAsStream(imagePath);
                Objects.requireNonNull(imageStream, "Image resource not found: " + imagePath);
                Image gif = new Image(imageStream);
                ImageView imageView = new ImageView(gif);

                Label messageLabel = new Label();
                messageLabel.setFont(new Font("Arial", 48));
                messageLabel.setTextFill(Color.WHITE);
                messageLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 10;");
                messageLabel.setVisible(false);
                messageLabels.add(messageLabel);

                StackPane root = new StackPane(imageView, messageLabel);
                root.setAlignment(Pos.CENTER);
                root.setStyle("-fx-background-color: black;");

                Rectangle2D screenBounds = screen.getVisualBounds();
                Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight());
                scene.setFill(Color.TRANSPARENT);

                imageView.fitWidthProperty().bind(scene.widthProperty());
                imageView.fitHeightProperty().bind(scene.heightProperty());
                imageView.setPreserveRatio(false);
                imageView.setSmooth(true);

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
                        // Send typed characters as a broadcast to all controllers
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
                overlayStage.setScene(scene);

                overlayStage.setX(screenBounds.getMinX());
                overlayStage.setY(screenBounds.getMinY());

                overlayStage.show();
                overlayStages.add(overlayStage);
            }
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