package de.hypno.screenlockerdesktop;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WebSocketManager implements Runnable {

    private static final String WEBSOCKET_URI = "wss://ws.3409.de:8082";
    private WebSocketClient webSocketClient;
    private final OverlayManager overlayManager;
    private final SettingsController settingsController;

    private String username;
    private String password;
    private String selectedImage;

    private volatile boolean running = false;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");


    public WebSocketManager(OverlayManager overlayManager, SettingsController controller) {
        this.overlayManager = overlayManager;
        this.settingsController = controller;
    }

    private void log(String message) {
        System.out.println(String.format("[%s] [WebSocketManager] %s", LocalTime.now().format(TIME_FORMATTER), message));
    }

    public void start(String username, String password, String selectedImage) {
        log("START called for user: " + username);
        this.username = username;
        this.password = password;
        this.selectedImage = selectedImage;
        this.running = true;
        Thread thread = new Thread(this);
        thread.setName("WebSocketThread");
        thread.setDaemon(true);
        thread.start();
    }

    public void close() {
        log("CLOSE called. Setting running = false and closing client.");
        running = false;
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    @Override
    public void run() {
        try {
            connectWebSocket();
        } catch (URISyntaxException e) {
            log("URISyntaxException: " + e.getMessage());
            settingsController.notifyConnectionFailed("Invalid WebSocket URI.");
        }
    }

    private void connectWebSocket() throws URISyntaxException {
        log("Connecting to " + WEBSOCKET_URI);
        webSocketClient = new WebSocketClient(new URI(WEBSOCKET_URI)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                log("ON_OPEN: Connection established. Sending auth command.");
                sendAuthMessage();
            }

            @Override
            public void onMessage(String message) {
                log("ON_MESSAGE: Received: " + message);
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log(String.format("ON_CLOSE: Code: %d, Reason: '%s', Remote: %b. Current 'running' state is %b.", code, reason, remote, running));
                settingsController.notifyConnectionClosed(reason);
                if (running) {
                    reconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                log("ON_ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }
        };
        webSocketClient.connect();
    }

    private void reconnect() {
        if (!running) {
            log("RECONNECT aborted because 'running' is false.");
            return;
        }
        try {
            log("RECONNECT scheduled in 5 seconds...");
            Thread.sleep(5000);
            log("RECONNECTING now...");
            connectWebSocket();
        } catch (InterruptedException | URISyntaxException e) {
            Thread.currentThread().interrupt();
            log("RECONNECT FAILED: " + e.getMessage());
            settingsController.notifyConnectionFailed("Reconnect failed.");
        }
    }

    private void handleMessage(String message) {
        try {
            String cmd = getJsonValue(message, "cmd");
            String data = getJsonValue(message, "data");

            if (cmd == null) {
                log("Could not parse 'cmd' from message: " + message);
                return;
            }

            switch (cmd) {
                case "auth":
                    if ("success".equals(data)) {
                        log("Authentication successful.");
                        settingsController.notifyConnectionOpened(this.username);
                    } else {
                        log("Authentication failed. Reason: " + data);
                        settingsController.notifyConnectionFailed("Auth failed: " + data);
                        close();
                    }
                    break;
                case "lock":
                    overlayManager.showLockOverlay(this.username, selectedImage, this);
                    break;
                case "unlock":
                    overlayManager.hideLockOverlay();
                    break;
                case "chat":
                    overlayManager.showMessage(data);
                    break;
                case "controlled_users_update":
                    log("Received status update for controlled users: " + data);
                    break;
                // --- NEW COMMANDS ---
                case "add_ctrl":
                    settingsController.notifyControllerCommandResult("add", data);
                    break;
                case "remove_ctrl":
                    settingsController.notifyControllerCommandResult("remove", data);
                    break;
                case "list_ctrl":
                    settingsController.updateControllerList(data);
                    break;
                default:
                    log("Received unknown command: " + cmd);
                    break;
            }
        } catch (Exception e) {
            log("Failed to process incoming message: " + message + ". Error: " + e.getMessage());
        }
    }

    private void sendAuthMessage() {
        sendMessage(this.username, "auth", this.password);
    }

    public void sendMessage(String target, String cmd, Object data) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            String dataJson;
            if (data == null) {
                dataJson = "null";
            } else if (data instanceof String) {
                String escapedData = ((String) data).replace("\\", "\\\\").replace("\"", "\\\"");
                dataJson = "\"" + escapedData + "\"";
            } else {
                dataJson = data.toString();
            }

            String jsonMessage = String.format(
                "{\"target\": \"%s\", \"cmd\": \"%s\", \"data\": %s, \"apiVersion\": 2}",
                target, cmd, dataJson
            );

            log("SENDING message: " + jsonMessage);
            webSocketClient.send(jsonMessage);
        }
    }
    
    // --- NEW PUBLIC METHODS FOR CONTROLLERS ---
    public void addController(String userToAdd) {
        sendMessage(userToAdd, "add_ctrl", null);
    }

    public void removeController(String userToRemove) {
        sendMessage(userToRemove, "remove_ctrl", null);
    }

    public void listControllers() {
        // "target" is our own username for this command
        sendMessage(this.username, "list_ctrl", null);
    }


    /**
     * A simple, robust parser to extract a value from a JSON string.
     * It handles whitespace and distinguishes between quoted strings and other values.
     */
    private String getJsonValue(String json, String key) {
        // This parser needs to handle json arrays for the list_ctrl command.
        // For simplicity, we assume the 'data' value is either a simple value or a well-formed array.
        String keyPattern = "\"" + key + "\":";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) {
            return null;
        }

        int currentIndex = keyIndex + keyPattern.length();

        // Skip leading whitespace to find the start of the value
        while (currentIndex < json.length() && Character.isWhitespace(json.charAt(currentIndex))) {
            currentIndex++;
        }

        if (currentIndex >= json.length()) {
            return null; // No value found
        }

        char startChar = json.charAt(currentIndex);
        if (startChar == '\"') {
            // Value is a quoted string
            int valueStartIndex = currentIndex + 1;
            int valueEndIndex = json.indexOf('\"', valueStartIndex);
            if (valueEndIndex == -1) return null;
            return json.substring(valueStartIndex, valueEndIndex);
        } else if (startChar == '[') {
            // Value is a JSON array
            int arrayEndIndex = json.indexOf(']', currentIndex);
            if (arrayEndIndex == -1) return null;
            return json.substring(currentIndex, arrayEndIndex + 1);
        }
        else {
            // Value is not a quoted string (e.g., number, boolean, null)
            int valueEndIndex = json.indexOf(',', currentIndex);
            if (valueEndIndex == -1) {
                valueEndIndex = json.indexOf('}', currentIndex);
            }
            if (valueEndIndex == -1) return null;
            return json.substring(currentIndex, valueEndIndex).trim();
        }
    }
}