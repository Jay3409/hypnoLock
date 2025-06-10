package de.hypno.screenlockerdesktop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages system audio for muting and unmuting across different platforms.
 */
public class AudioManager {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    
    // Stores the volume level (0-100) for restoration on macOS and Linux.
    private int lastVolume = -1;
    
    // For Windows, the mute command is a toggle. This flag tracks if the app initiated the mute.
    private boolean appMuted = false;

    /**
     * Mutes the system's master audio output.
     * On Linux and macOS, it stores the current volume before muting.
     * On Windows, it toggles the system mute state.
     */
    public void mute() {
        try {
            if (OS_NAME.contains("win")) {
                // Windows: This uses PowerShell to simulate pressing the 'Mute' media key.
                // NOTE: This is a TOGGLE and may not be reliable on all systems. A more
                // robust solution would require external libraries like JNA.
                executeCommand("powershell", "-c", "(New-Object -ComObject WScript.Shell).SendKeys([char]173)");
                appMuted = true;

            } else if (OS_NAME.contains("mac")) {
                // macOS: Use osascript to get the current volume, then mute.
                String getVolumeScript = "output volume of (get volume settings)";
                String volumeStr = executeCommandAndGetOutput("osascript", "-e", getVolumeScript);
                if (volumeStr != null && !volumeStr.trim().isEmpty()) {
                    lastVolume = Integer.parseInt(volumeStr.trim());
                }
                executeCommand("osascript", "-e", "set volume with output muted");

            } else if (OS_NAME.contains("nix") || OS_NAME.contains("nux")) {
                // Linux: Use amixer to get the current volume from the Master channel.
                String output = executeCommandAndGetOutput("amixer", "sget", "Master");
                if (output != null) {
                    // Regex to find a volume percentage like "[80%]"
                    Pattern pattern = Pattern.compile("\\[(\\d+)%\\]");
                    Matcher matcher = pattern.matcher(output);
                    if (matcher.find()) {
                        lastVolume = Integer.parseInt(matcher.group(1));
                    }
                }
                // Mute the Master channel.
                executeCommand("amixer", "-q", "sset", "Master", "mute");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error muting audio: " + e.getMessage());
            appMuted = false; // Ensure state is correct on failure.
        }
    }

    /**
     * Unmutes the system's master audio output.
     * On Linux and macOS, it restores the previously stored volume level.
     * On Windows, it toggles the mute state back if the app was the one to mute it.
     */
    public void unmute() {
        try {
            if (OS_NAME.contains("win")) {
                // Windows: Only toggle mute back if we were the one who initiated it.
                if (appMuted) {
                    executeCommand("powershell", "-c", "(New-Object -ComObject WScript.Shell).SendKeys([char]173)");
                }
            } else if (OS_NAME.contains("mac")) {
                // macOS: First, unmute the audio.
                executeCommand("osascript", "-e", "set volume without output muted");
                // Then, restore the volume level if we have a saved state.
                if (lastVolume != -1) {
                     executeCommand("osascript", "-e", "set volume output volume " + lastVolume);
                }
            } else if (OS_NAME.contains("nix") || OS_NAME.contains("nux")) {
                // Linux: First, unmute the Master channel.
                executeCommand("amixer", "-q", "sset", "Master", "unmute");
                // Then, restore the volume level if we have one saved.
                 if (lastVolume != -1) {
                     executeCommand("amixer", "-q", "sset", "Master", lastVolume + "%");
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error unmuting audio: " + e.getMessage());
        } finally {
            // Reset state regardless of success or failure.
            appMuted = false;
            lastVolume = -1;
        }
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        new ProcessBuilder(command).start().waitFor();
    }

    private String executeCommandAndGetOutput(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        process.waitFor();
        return output.toString();
    }
}