package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
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

    // Use Lists to manage multiple stages and labels for multiple monitors
    private final List<Stage> overlayStages = new ArrayList<>();
    private final List<Label> messageLabels = new ArrayList<>();
    
    private Timeline messageTimeline;
    private boolean canHideMessageByKey = false;
    private PauseTransition minDisplayTimer;

    public void showLockOverlay(String deviceName, String imageName, WebSocketManager webSocketManager) {
        Platform.runLater(() -> {
            // If stages already exist, don't create more.
            if (!overlayStages.isEmpty()) {
                return;
            }

            // Get a list of all screens
            List<Screen> screens = Screen.getScreens();
            
            // Loop through each screen and create an overlay for it
            for (Screen screen : screens) {
                // Each stage needs its own nodes. Create new ones for each screen.
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
                messageLabels.add(messageLabel); // Add to list to manage later

                StackPane root = new StackPane(imageView, messageLabel);
                root.setAlignment(Pos.CENTER);
                root.setStyle("-fx-background-color: black;");

                Rectangle2D screenBounds = screen.getVisualBounds();
                Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight());
                scene.setFill(Color.TRANSPARENT);
                
                // Bind image view to the scene size to make it fill the screen
                imageView.fitWidthProperty().bind(scene.widthProperty());
                imageView.fitHeightProperty().bind(scene.heightProperty());
                imageView.setPreserveRatio(false);
                imageView.setSmooth(true);

                // --- MODIFICATION START ---
                // Handle key presses on the overlay for this specific scene
                scene.setOnKeyPressed(event -> {
                    // If the user presses Escape, send the unlock command
                    if (event.getCode() == KeyCode.ESCAPE) {
                        webSocketManager.sendMessage("unlockඞ" + deviceName);
                        webSocketManager.sendMessage("textඞ" + deviceName+"ඞ--- PANIC BUTTON ---");
                        
                        event.consume(); // Mark the event as handled
                        return; // Exit the handler
                    }
                    
                    // Original logic for sending typed characters
                    String character = event.getText();
                    if (character != null && !character.isEmpty()) {
                        String messageToSend = "textඞ" + deviceName + "ඞ" + character;
                        webSocketManager.sendMessage(messageToSend);
                    }
                    
                    // Original logic for hiding the on-screen message
                    if (canHideMessageByKey) {
                        hideMessageOnly();
                    }
                });
                // --- MODIFICATION END ---


                Stage overlayStage = new Stage();
                overlayStage.initStyle(StageStyle.UNDECORATED);
                overlayStage.initStyle(StageStyle.TRANSPARENT);
                overlayStage.setAlwaysOnTop(true);
                overlayStage.setScene(scene);

                // Position the stage on the correct monitor
                overlayStage.setX(screenBounds.getMinX());
                overlayStage.setY(screenBounds.getMinY());

                overlayStage.show();
                overlayStages.add(overlayStage); // Add the new stage to our list
            }
        });
    }
    
    private void hideMessageOnly() {
        if (messageTimeline != null) messageTimeline.stop();
        if (minDisplayTimer != null) minDisplayTimer.stop();
        // Hide the message on all overlays
        for (Label label : messageLabels) {
            label.setVisible(false);
        }
        canHideMessageByKey = false;
    }

    public void hideLockOverlay() {
        Platform.runLater(() -> {
            // Close all overlay stages
            for (Stage stage : overlayStages) {
                stage.close();
            }
            // Clear the lists for the next time
            overlayStages.clear();
            messageLabels.clear();
        });
    }

    public void showMessage(String text) {
        Platform.runLater(() -> {
            if (overlayStages.isEmpty()) return;
            
            hideMessageOnly(); // Hide previous message if any

            // Show the new message on all overlays
            for (Label label : messageLabels) {
                label.setText(text);
                label.setVisible(true);
            }
            canHideMessageByKey = false;

            // Timer to hide message after 10 seconds
            messageTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> hideMessageOnly()));
            messageTimeline.play();
            
            // Timer to allow hiding by key after 5 seconds
            minDisplayTimer = new PauseTransition(Duration.seconds(5));
            minDisplayTimer.setOnFinished(e -> canHideMessageByKey = true);
            minDisplayTimer.play();
        });
    }
}