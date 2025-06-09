package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class SettingsController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> imageComboBox;
    @FXML private Label statusLabel;
    @FXML private Button connectButton;
    @FXML private Button pauseButton;
    @FXML private Button unpauseButton;
    @FXML private Button logoutButton;

    // --- NEW UI Elements for Controller Management ---
    @FXML private TitledPane controllerManagementPane;
    @FXML private ListView<String> controllerListView;
    @FXML private TextField controllerUsernameField;
    @FXML private Button addControllerButton;
    @FXML private Button removeControllerButton;
    @FXML private Button listControllersButton;


    private WebSocketManager webSocketManager;
    private Preferences prefs;

    private Timeline autoUnpauseTimeline;
    private Timeline statusUpdateTimeline;
    private LocalDateTime pauseEndTime;

    private enum State { DISCONNECTED, CONNECTED, PAUSED }
    private volatile State currentState = State.DISCONNECTED;

    private static final String USERNAME_KEY = "Username";
    private static final String PASSWORD_KEY = "UserPassword";
    private static final String SELECTED_IMAGE_KEY = "SelectedImage";
    private static final DateTimeFormatter TIME_FORMATTER_STATUS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER_PAUSE = DateTimeFormatter.ofPattern("HH:mm");


    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);
        imageComboBox.setItems(FXCollections.observableArrayList("Spiral 1", "Spiral 2"));
        loadSettings();
        setUiState(State.DISCONNECTED, "Disconnected");

        if (!usernameField.getText().isEmpty() && !passwordField.getText().isEmpty()) {
            Platform.runLater(this::handleConnectButton);
        }
    }

    public void setWebSocketManager(WebSocketManager manager) {
        this.webSocketManager = manager;
    }

    @FXML
    private void handleConnectButton() {
        updateStatus("Connecting...", false);
        saveSettings();
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            updateStatus("Username and Password cannot be empty.", true);
            return;
        }
        webSocketManager.start(username, password, imageComboBox.getValue());
    }

    @FXML
    private void handleLogoutButton() {
        if (autoUnpauseTimeline != null) autoUnpauseTimeline.stop();
        if (statusUpdateTimeline != null) statusUpdateTimeline.stop();
        webSocketManager.close();
        prefs.remove(USERNAME_KEY);
        prefs.remove(PASSWORD_KEY);
        usernameField.clear();
        passwordField.clear();
        controllerListView.getItems().clear(); // Clear the list on logout
        setUiState(State.DISCONNECTED, "Logged out. Credentials cleared.");
    }

    @FXML
    private void handlePauseButton() {
        TextInputDialog dialog = new TextInputDialog(LocalTime.now().plusHours(1).format(TIME_FORMATTER_PAUSE));
        dialog.setTitle("Pause Connection");
        dialog.setHeaderText("The connection will be paused and automatically resume at the specified time.");
        dialog.setContentText("Enter unpause time (HH:mm):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(timeStr -> {
            try {
                LocalTime unpauseTime = LocalTime.parse(timeStr, TIME_FORMATTER_PAUSE);
                pauseEndTime = unpauseTime.atDate(LocalDateTime.now().toLocalDate());

                // If the time is in the past, assume it's for the next day
                if (pauseEndTime.isBefore(LocalDateTime.now())) {
                    pauseEndTime = pauseEndTime.plusDays(1);
                }

                webSocketManager.close();
                setUiState(State.PAUSED, null); // Status will be set by updatePauseStatus
                startPauseTimelines();

            } catch (DateTimeParseException e) {
                updateStatus("Invalid time format. Please use HH:mm.", true);
            }
        });
    }

    @FXML
    private void handleUnpauseButton() {
        if (autoUnpauseTimeline != null) autoUnpauseTimeline.stop();
        if (statusUpdateTimeline != null) statusUpdateTimeline.stop();

        updateStatus("Reconnecting...", false);
        webSocketManager.start(usernameField.getText(), passwordField.getText(), imageComboBox.getValue());
    }

    private void startPauseTimelines() {
        // --- THIS IS THE FIX ---
        // We use the fully-qualified name for java.time.Duration to resolve the
        // conflict with javafx.util.Duration, which is imported for the Timelines.
        java.time.Duration timeUntilUnpause = java.time.Duration.between(LocalDateTime.now(), pauseEndTime);
        Duration delay = Duration.millis(timeUntilUnpause.toMillis());

        autoUnpauseTimeline = new Timeline(new KeyFrame(delay, e -> Platform.runLater(this::handleUnpauseButton)));
        autoUnpauseTimeline.play();

        // Timeline to update the status label every second
        statusUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updatePauseStatus()));
        statusUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        statusUpdateTimeline.play();
        updatePauseStatus(); // Initial update
    }
    
    @FXML
    private void handleAddControllerButton() {
        String userToAdd = controllerUsernameField.getText();
        if (userToAdd == null || userToAdd.trim().isEmpty()) {
            updateStatus("Enter a username to add as a controller.", true);
            return;
        }
        updateStatus("Adding " + userToAdd + "...", false);
        webSocketManager.addController(userToAdd);
        controllerUsernameField.clear();
    }
    
    @FXML
    private void handleRemoveControllerButton() {
        String userToRemove = controllerListView.getSelectionModel().getSelectedItem();
        if (userToRemove == null) {
            updateStatus("Select a controller from the list to remove.", true);
            return;
        }
        updateStatus("Removing " + userToRemove + "...", false);
        webSocketManager.removeController(userToRemove);
    }
    
    @FXML
    private void handleListControllersButton() {
        updateStatus("Refreshing controller list...", false);
        webSocketManager.listControllers();
    }

    public void notifyConnectionOpened(String username) {
        Platform.runLater(() -> {
            setUiState(State.CONNECTED, "Connected as " + username);
            handleListControllersButton();
        });
    }

    public void notifyConnectionClosed(String reason) {
        Platform.runLater(() -> {
            if (currentState != State.PAUSED) {
                setUiState(State.DISCONNECTED, "Disconnected. " + reason);
                controllerListView.getItems().clear();
            }
        });
    }

    public void notifyConnectionFailed(String message) {
        Platform.runLater(() -> {
            setUiState(State.DISCONNECTED, message);
        });
    }
    
    public void notifyControllerCommandResult(String command, String result) {
        Platform.runLater(() -> {
            String action = command.equals("add") ? "add" : "remove";
            if ("success".equalsIgnoreCase(result)) {
                updateStatus("Successfully " + (action.equals("add") ? "added" : "removed") + " controller.", false);
                handleListControllersButton();
            } else {
                updateStatus("Failed to " + action + " controller.", true);
            }
        });
    }
    
    public void updateControllerList(String jsonList) {
        Platform.runLater(() -> {
            if (jsonList == null || !jsonList.startsWith("[") || !jsonList.endsWith("]")) {
                updateStatus("Failed to parse controller list.", true);
                return;
            }
            String content = jsonList.substring(1, jsonList.length() - 1).trim();
            if(content.isEmpty()){
                controllerListView.setItems(FXCollections.observableArrayList());
                return;
            }
            String[] users = content.split(",");
            controllerListView.setItems(FXCollections.observableArrayList(Arrays.stream(users)
                .map(u -> u.trim().replace("\"", ""))
                .collect(Collectors.toList())));
            updateStatus("Controller list updated.", false);
        });
    }


    private void setUiState(State newState, String statusMessage) {
        this.currentState = newState;

        if (statusMessage != null) {
            updateStatus(statusMessage, false);
        }

        boolean isDisconnected = (newState == State.DISCONNECTED);
        connectButton.setVisible(isDisconnected);
        connectButton.setManaged(isDisconnected);

        boolean isConnected = (newState == State.CONNECTED);
        pauseButton.setVisible(isConnected);
        pauseButton.setManaged(isConnected);

        boolean isPaused = (newState == State.PAUSED);
        unpauseButton.setVisible(isPaused);
        unpauseButton.setManaged(isPaused);

        logoutButton.setVisible(!isDisconnected);
        logoutButton.setManaged(!isDisconnected);
        
        controllerManagementPane.setVisible(isConnected);
        controllerManagementPane.setManaged(isConnected);
    }

    private void updatePauseStatus() {
        if (currentState == State.PAUSED && pauseEndTime != null) {
            String formattedTime = pauseEndTime.format(TIME_FORMATTER_PAUSE);
            updateStatus("Paused until " + formattedTime, false);
        }
    }

    public void updateStatus(String text, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(String.format("[%s] %s", TIME_FORMATTER_STATUS.format(LocalDateTime.now()), text));
            if (isError) {
                statusLabel.setStyle("-fx-text-fill: red;");
            } else {
                statusLabel.setStyle("-fx-text-fill: black;");
            }
        });
    }

    private void loadSettings() {
        usernameField.setText(prefs.get(USERNAME_KEY, ""));
        passwordField.setText(new String(Base64.getDecoder().decode(prefs.get(PASSWORD_KEY, ""))));
        imageComboBox.setValue(prefs.get(SELECTED_IMAGE_KEY, "Spiral 1"));
    }

    private void saveSettings() {
        prefs.put(USERNAME_KEY, usernameField.getText());
        prefs.put(PASSWORD_KEY, Base64.getEncoder().encodeToString(passwordField.getText().getBytes()));
        prefs.put(SELECTED_IMAGE_KEY, imageComboBox.getValue());
    }
}