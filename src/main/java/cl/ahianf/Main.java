package cl.ahianf;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "nesu", mixinStandardHelpOptions = true, version = "nesu 0.0.1",
        description = "A simple NES emulator, written in Java")
public class Main implements Runnable {


    @Option(names = {"-s", "--scale"}, description = "Set video scale. Default: 3.")
    float scale = 3.0f;

    @Option(names = {"-w", "--width"}, description = "Set screen width")
    int customWidth = -1;

    @Option(names = {"-H", "--height"}, description = "Set screen height")
    int customHeight = -1;

    @Option(names = {"-C", "--conf"}, description = "Set keybindings file path")
    String keybindingsPath = "keybindings.conf";

    @Parameters(index = "0", description = "Path to the .nes ROM file", arity = "1")
    String romPath;

    // =============================================================
    // MAIN ENTRY POINT
    // =============================================================

    public static void main(String[] args) {
        // Picocli handles parsing and calling run()
        int exitCode = new CommandLine(new Main()).execute(args);
        // Note: Lwjgl3Application starts its own thread, so we don't necessarily need System.exit
        // unless validation fails.
    }

    // =============================================================
    // EXECUTION LOGIC
    // =============================================================

    @Override
    public void run() {
        // --- Resolution Calculation (From your previous logic) ---
        int baseWidth = 256;
        int baseHeight = 240;
        int finalWidth, finalHeight;

        if (customWidth != -1) {
            finalWidth = customWidth;
            finalHeight = (int) (customWidth * (240.0 / 256.0));
        } else if (customHeight != -1) {
            finalHeight = customHeight;
            finalWidth = (int) (customHeight * (256.0 / 240.0));
        } else {
            finalWidth = (int) (baseWidth * scale);
            finalHeight = (int) (baseHeight * scale);
        }

        // --- LibGDX Configuration ---
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("nesu: " + romPath.split("/")[romPath.split("/").length - 1]);
        config.setWindowedMode(finalWidth, finalHeight);
        config.useVsync(true);
        config.setForegroundFPS(60);

        // --- Launch Game ---
        // We pass the parsed fields directly to the game constructor
        new Lwjgl3Application(
                new SimpleNESGame(romPath, keybindingsPath, scale),
                config
        );
    }
}