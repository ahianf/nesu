package cl.ahianf;


import cl.ahianf.input.KeybindingsParser;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Input.Keys;

public class SimpleNESGame extends ApplicationAdapter {
    private Emulator emulator;
    private SpriteBatch batch;

    private final String romPath;
    private final String keyConfPath;
    private final float initialScale;

    // Default Keybindings (Matches main.cpp defaults)
    private final List<Integer> p1Keys = new ArrayList<>(Arrays.asList(
            Keys.J, Keys.K, Keys.SHIFT_RIGHT, Keys.ENTER, Keys.W, Keys.S, Keys.A, Keys.D
    ));

    private final List<Integer> p2Keys = new ArrayList<>(Arrays.asList(
            Keys.NUMPAD_5, Keys.NUMPAD_6, Keys.NUMPAD_8, Keys.NUMPAD_9,
            Keys.UP, Keys.DOWN, Keys.LEFT, Keys.RIGHT
    ));

    public SimpleNESGame(String romPath, String keyConfPath, float scale) {
        this.romPath = romPath;
        this.keyConfPath = keyConfPath;
        this.initialScale = scale;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();

        // Parse config file if exists, overwriting defaults
        if (Gdx.files.internal(keyConfPath).exists()) {
            KeybindingsParser.parseControllerConf(keyConfPath, p1Keys, p2Keys);
        }

        emulator = new Emulator();
        emulator.setKeys(p1Keys, p2Keys);

        emulator.setVideoScale(initialScale);

        // Load ROM
        // Note: modify Emulator.run() to split initialization from the loop
        // Here we assume Emulator has a method 'init(romPath)' or we call 'run'
        // in a non-blocking way.
        // Based on previous translation, Emulator.run() blocked.
        // **IMPORTANT**: You must edit Emulator.java to allow initialization without entering the while loop.
        emulator.loadRom(romPath);
    }

    @Override
    public void render() {
        // Clear Screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();

        // Run emulation for one frame (approx)
        // This replaces the 'while(window.isOpen)' loop in main.cpp
        emulator.updateFrame(batch);

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        // emulator.dispose();
    }
}