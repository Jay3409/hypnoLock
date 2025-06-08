package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.prefs.Preferences;

public class SettingsController {

    @FXML
    private TextField deviceNameField;
    @FXML
    private ComboBox<String> imageComboBox;
    @FXML
    private Label statusLabel;
    @FXML
    private Button connectButton;
    @FXML
    private Button pauseButton;
    @FXML
    private Button unpauseButton;

    private WebSocketManager webSocketManager;
    private Preferences prefs;

    private Timeline autoUnpauseTimeline;
    private Timeline statusUpdateTimeline;
    private LocalDateTime pauseEndTime;

    private enum State { DISCONNECTED, CONNECTED, PAUSED }
    private volatile State currentState = State.DISCONNECTED;


    private static final String DEVICE_NAME_KEY = "DeviceName";
    private static final String SELECTED_IMAGE_KEY = "SelectedImage";
    private static final DateTimeFormatter TIME_FORMATTER_STATUS = DateTimeFormatter.ofPattern("HH:mm:ss");


    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);
        imageComboBox.setItems(FXCollections.observableArrayList("Spiral 1", "Spiral 2"));
        loadSettings();
        setUiState(State.DISCONNECTED, "Disconnected");

        Platform.runLater(this::handleConnectButton);
    }

    public void setWebSocketManager(WebSocketManager manager) {
        this.webSocketManager = manager;
    }

    // --- Event Handlers from UI ---

    @FXML
    private void handleConnectButton() {
        updateStatus("Connecting...", false);
        saveSettings();
        String deviceName = deviceNameField.getText();
        if (deviceName == null || deviceName.trim().isEmpty()) {
            updateStatus("Device name cannot be empty.", true);
            return;
        }
        webSocketManager.start(deviceName, imageComboBox.getValue());
    }

    @FXML
    private void handlePauseButton() {
        TextInputDialog dialog = new TextInputDialog("10");
        dialog.setTitle("Pause Connection");
        dialog.setHeaderText("The connection will be paused.");
        dialog.setContentText("Please enter pause duration (minutes):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(minutesStr -> {
            try {
                long minutes = Long.parseLong(minutesStr);
                if (minutes <= 0) {
                    updateStatus("Pause duration must be positive.", true);
                    return;
                }

                webSocketManager.close();
                setUiState(State.PAUSED, null);

                pauseEndTime = LocalDateTime.now().plusMinutes(minutes);

                if (autoUnpauseTimeline != null) autoUnpauseTimeline.stop();
                autoUnpauseTimeline = new Timeline(new KeyFrame(Duration.minutes(minutes), e -> handleUnpauseButton()));
                autoUnpauseTimeline.play();
                
                if (statusUpdateTimeline != null) statusUpdateTimeline.stop();
                statusUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updatePauseStatus()));
                statusUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
                statusUpdateTimeline.play();
                updatePauseStatus();

            } catch (NumberFormatException e) {
                updateStatus("Invalid number format for minutes.", true);
            }
        });
    }

    @FXML
    private void handleUnpauseButton() {
        if(autoUnpauseTimeline != null) autoUnpauseTimeline.stop();
        if(statusUpdateTimeline != null) statusUpdateTimeline.stop();
        
        updateStatus("Reconnecting...", false);
        handleConnectButton();
    }
    
    // --- Notification Handlers from WebSocketManager ---

    public void notifyConnectionOpened(String deviceName) {
        Platform.runLater(() -> {
            setUiState(State.CONNECTED, "Connected as " + deviceName);
        });
    }

    public void notifyConnectionClosed(String reason) {
        Platform.runLater(() -> {
            // Only act on this if we are not in a PAUSED state.
            // This prevents the UI from flipping to "Disconnected" when we intentionally pause.
            if (currentState != State.PAUSED) {
                setUiState(State.DISCONNECTED, "Disconnected. " + reason);
            }
        });
    }
    
    public void notifyConnectionFailed(String message) {
        Platform.runLater(() -> {
            setUiState(State.DISCONNECTED, message);
        });
    }

    // --- UI and State Management ---

    private void setUiState(State newState, String statusMessage) {
        this.currentState = newState;

        if (statusMessage != null) {
            updateStatus(statusMessage, false);
        }

        connectButton.setVisible(newState == State.DISCONNECTED);
        connectButton.setManaged(newState == State.DISCONNECTED);
        
        pauseButton.setVisible(newState == State.CONNECTED);
        pauseButton.setManaged(newState == State.CONNECTED);
        
        unpauseButton.setVisible(newState == State.PAUSED);
        unpauseButton.setManaged(newState == State.PAUSED);

    }

    private void updatePauseStatus() {
        java.time.Duration remaining = java.time.Duration.between(LocalDateTime.now(), pauseEndTime);
        if (remaining.isNegative() || remaining.isZero()) {
            updateStatus("Paused", false);
        } else {
            long totalSeconds = remaining.toSeconds();
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            String remainingTime = String.format("%d min, %d sec", minutes, seconds);
            
            String statusText = String.format("Paused until %s (%s remaining)",
                pauseEndTime.format(TIME_FORMATTER_STATUS), remainingTime);
            updateStatus(statusText, false);
        }
    }

    public void updateStatus(String text, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText("Status: " + text);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        });
    }

    private void loadSettings() {
        deviceNameField.setText(prefs.get(DEVICE_NAME_KEY, "DESKTOP_DEVICE_1"));
        imageComboBox.setValue(prefs.get(SELECTED_IMAGE_KEY, "Spiral 1"));
    }

    private void saveSettings() {
        prefs.put(DEVICE_NAME_KEY, deviceNameField.getText());
        prefs.put(SELECTED_IMAGE_KEY, imageComboBox.getValue());
    }
}