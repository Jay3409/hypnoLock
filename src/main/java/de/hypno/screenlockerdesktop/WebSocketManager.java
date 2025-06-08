package de.hypno.screenlockerdesktop;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WebSocketManager implements Runnable {

    private static final String WEBSOCKET_URI = "wss://ws.3409.de:8081";
    private WebSocketClient webSocketClient;
    private final OverlayManager overlayManager;
    private final SettingsController settingsController;
    private String deviceName;
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

    public void start(String deviceName, String selectedImage) {
        log("START called for device: " + deviceName);
        this.deviceName = deviceName;
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
                log("ON_OPEN: Connection established. Sending handshake.");
                settingsController.notifyConnectionOpened(deviceName); // <-- NEW: Specific event
                webSocketClient.send("barkbarkwoofඞ" + deviceName);
            }

            @Override
            public void onMessage(String message) {
                log("ON_MESSAGE: Received: " + message);
                handleMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log(String.format("ON_CLOSE: Code: %d, Reason: '%s', Remote: %b. Current 'running' state is %b.", code, reason, remote, running));
                settingsController.notifyConnectionClosed(reason); // <-- NEW: Specific event
                if (running) {
                    reconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                log("ON_ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                // The 'onClose' method is usually called immediately after an error.
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

    // ... (handleMessage and sendMessage methods are unchanged)
    private void handleMessage(String message) {
        String[] parts = message.split("ඞ");
        if (parts.length < 2) return;

        String command = parts[0];
        String targetDevice = parts[1];

        if (deviceName.equals(targetDevice)) {
            switch (command) {
                case "lock":
                    overlayManager.showLockOverlay(deviceName, selectedImage, this);
                    break;
                case "unlock":
                    overlayManager.hideLockOverlay();
                    break;
                case "CHAT":
                    if (parts.length >= 3) {
                        String chatMessage = parts[2];
                        overlayManager.showMessage(chatMessage);
                    }
                    break;
            }
        }
    }

    public void sendMessage(String message) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            log("SENDING message: " + message);
            webSocketClient.send(message);
        }
    }
}