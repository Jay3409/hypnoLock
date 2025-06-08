package de.hypno.screenlockerdesktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.net.URL;

public class MainApp extends Application {

    private static Scene scene;
    private static WebSocketManager webSocketManager;
    private TrayIcon trayIcon;

    @Override
    public void start(Stage stage) throws IOException {
        // Prevent the application from exiting when the last window is closed
        Platform.setImplicitExit(false);

        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("SettingsWindow.fxml"));
        Parent root = fxmlLoader.load();
        SettingsController controller = fxmlLoader.getController();

        // Create the managers
        OverlayManager overlayManager = new OverlayManager();
        webSocketManager = new WebSocketManager(overlayManager, controller);
        controller.setWebSocketManager(webSocketManager);
        
        // Basic window setup
        scene = new Scene(root, 400, 400);
        stage.setTitle("Screen Locker Settings");
        stage.setScene(scene);
        
        // Add the system tray icon
        setupSystemTray(stage);
        
        stage.show();

        // When the 'X' is clicked on the settings window, just hide it.
        stage.setOnCloseRequest(event -> {
            event.consume(); // Consume the event to prevent closing
            stage.hide();
        });
    }

    private void setupSystemTray(Stage stage) {
        // Check if SystemTray is supported
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }

        // Create the popup menu for the tray icon
        final PopupMenu popup = new PopupMenu();
        
        // Using an existing gif for the icon, a 16x16 png would be better
        URL imageURL = MainApp.class.getResource("spiral1.gif");
        java.awt.Image image = Toolkit.getDefaultToolkit().getImage(imageURL);

        trayIcon = new TrayIcon(image, "Screen Locker");
        final SystemTray tray = SystemTray.getSystemTray();

        // Create menu items
        MenuItem showItem = new MenuItem("Show Settings");
        MenuItem exitItem = new MenuItem("Exit");

        // Add items to the popup menu
        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        // Add Action Listeners
        showItem.addActionListener(e -> Platform.runLater(stage::show));

        exitItem.addActionListener(e -> {
            webSocketManager.close();
            Platform.exit();
            tray.remove(trayIcon);
            System.exit(0);
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}