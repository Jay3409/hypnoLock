package de.hypno.screenlockerdesktop;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        // ... (existing code unchanged)
    }

    @FXML
    private void handleUnpauseButton() {
        // ... (existing code unchanged)
    }
    
    // --- NEW Handlers for Controller Management ---

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
            handleListControllersButton(); // Automatically list controllers on connect
        });
    }

    public void notifyConnectionClosed(String reason) {
        Platform.runLater(() -> {
            if (currentState != State.DISCONNECTED && currentState != State.PAUSED) {
                setUiState(State.DISCONNECTED, "Disconnected. " + reason);
                controllerListView.getItems().clear(); // Clear the list on disconnect
            }
        });
    }

    public void notifyConnectionFailed(String message) {
        Platform.runLater(() -> {
            setUiState(State.DISCONNECTED, message);
        });
    }
    
    // --- NEW Methods to handle command results from WebSocketManager ---

    public void notifyControllerCommandResult(String command, String result) {
        Platform.runLater(() -> {
            String action = command.equals("add") ? "add" : "remove";
            if ("success".equalsIgnoreCase(result)) {
                updateStatus("Successfully " + (action.equals("add") ? "added" : "removed") + " controller.", false);
                handleListControllersButton(); // Refresh the list on success
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
            // Simple JSON array parsing
            String content = jsonList.substring(1, jsonList.length() - 1).trim();
            if(content.isEmpty()){
                controllerListView.setItems(FXCollections.observableArrayList());
                return;
            }
            String[] users = content.split(",");
            controllerListView.setItems(FXCollections.observableArrayList(Arrays.stream(users)
                .map(u -> u.trim().replace("\"", "")) // remove quotes and whitespace
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

        pauseButton.setVisible(newState == State.CONNECTED);
        pauseButton.setManaged(newState == State.CONNECTED);

        unpauseButton.setVisible(newState == State.PAUSED);
        unpauseButton.setManaged(newState ==  State.PAUSED);

        logoutButton.setVisible(!isDisconnected);
        logoutButton.setManaged(!isDisconnected);
        
        // --- NEW ---
        // Show controller management pane only when connected
        controllerManagementPane.setVisible(newState == State.CONNECTED);
        controllerManagementPane.setManaged(newState == State.CONNECTED);
    }

    private void updatePauseStatus() {
        // ... (existing code unchanged)
    }

    public void updateStatus(String text, boolean isError) {
        // ... (existing code unchanged)
    }

    private void loadSettings() {
        // ... (existing code unchanged)
    }

    private void saveSettings() {
        // ... (existing code unchanged)
    }
}