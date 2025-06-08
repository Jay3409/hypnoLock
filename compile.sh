#!/bin/bash

# This command now also runs the dependency-plugin, creating the 
# target/dependencies folder containing all the needed JARs.
mvn clean install

# This jpackage command now has a complete module path, pointing to both
# your application's JAR and the folder with all its dependencies.
jpackage --type app-image \
  --app-version 1.0.0 \
  --dest target/dist \
  --name "Screen Locker" \
  --module-path "target/dependencies:target/screenlockerdesktop-1.0-SNAPSHOT.jar" \
  --add-modules de.hypno.screenlockerdesktop,javafx.controls,javafx.fxml,org.java_websocket,jdk.crypto.ec \
  --module de.hypno.screenlockerdesktop/de.hypno.screenlockerdesktop.MainApp \
  --vendor "Hypno" \
  --icon package/linux/icon.png
