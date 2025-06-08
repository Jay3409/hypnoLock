module de.hypno.screenlockerdesktop {
    // Required for JavaFX UI Controls
    requires javafx.controls;

    // Required for FXML loading
    requires javafx.fxml;

    // Required for the Preferences API (saving settings)
    requires java.prefs;

    // Required for the WebSocket library
    requires org.java_websocket;

    // THIS IS THE NEW LINE: Required for System Tray (AWT/Desktop)
    requires java.desktop;

    // Opens your package to the FXML library so it can access the controller
    opens de.hypno.screenlockerdesktop to javafx.fxml;

    // Exports your main package
    exports de.hypno.screenlockerdesktop;
}