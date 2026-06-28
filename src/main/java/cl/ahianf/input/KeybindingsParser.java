package cl.ahianf.input;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;

public class KeybindingsParser {

    private static final String[] BUTTON_STRINGS = {"A", "B", "Select", "Start", "Up", "Down", "Left", "Right"};

    // Mapping SFML-style string names to LibGDX Key Codes
    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("A", Keys.A);
        KEY_MAP.put("B", Keys.B);
        KEY_MAP.put("C", Keys.C);
        KEY_MAP.put("D", Keys.D);
        KEY_MAP.put("E", Keys.E);
        KEY_MAP.put("F", Keys.F);
        KEY_MAP.put("G", Keys.G);
        KEY_MAP.put("H", Keys.H);
        KEY_MAP.put("I", Keys.I);
        KEY_MAP.put("J", Keys.J);
        KEY_MAP.put("K", Keys.K);
        KEY_MAP.put("L", Keys.L);
        KEY_MAP.put("M", Keys.M);
        KEY_MAP.put("N", Keys.N);
        KEY_MAP.put("O", Keys.O);
        KEY_MAP.put("P", Keys.P);
        KEY_MAP.put("Q", Keys.Q);
        KEY_MAP.put("R", Keys.R);
        KEY_MAP.put("S", Keys.S);
        KEY_MAP.put("T", Keys.T);
        KEY_MAP.put("U", Keys.U);
        KEY_MAP.put("V", Keys.V);
        KEY_MAP.put("W", Keys.W);
        KEY_MAP.put("X", Keys.X);
        KEY_MAP.put("Y", Keys.Y);
        KEY_MAP.put("Z", Keys.Z);

        KEY_MAP.put("Num0", Keys.NUM_0);
        KEY_MAP.put("Num1", Keys.NUM_1);
        KEY_MAP.put("Num2", Keys.NUM_2);
        KEY_MAP.put("Num3", Keys.NUM_3);
        KEY_MAP.put("Num4", Keys.NUM_4);
        KEY_MAP.put("Num5", Keys.NUM_5);
        KEY_MAP.put("Num6", Keys.NUM_6);
        KEY_MAP.put("Num7", Keys.NUM_7);
        KEY_MAP.put("Num8", Keys.NUM_8);
        KEY_MAP.put("Num9", Keys.NUM_9);

        KEY_MAP.put("Escape", Keys.ESCAPE);
        KEY_MAP.put("LControl", Keys.CONTROL_LEFT);
        KEY_MAP.put("LShift", Keys.SHIFT_LEFT);
        KEY_MAP.put("LAlt", Keys.ALT_LEFT);
        // LibGDX doesn't distinguish System keys well, mapping to meta/sym
        KEY_MAP.put("LSystem", Keys.SYM);
        KEY_MAP.put("RControl", Keys.CONTROL_RIGHT);
        KEY_MAP.put("RShift", Keys.SHIFT_RIGHT);
        KEY_MAP.put("RAlt", Keys.ALT_RIGHT);
        KEY_MAP.put("RSystem", Keys.SYM);

        KEY_MAP.put("Menu", Keys.MENU);
        KEY_MAP.put("LBracket", Keys.LEFT_BRACKET);
        KEY_MAP.put("RBracket", Keys.RIGHT_BRACKET);
        KEY_MAP.put("SemiColon", Keys.SEMICOLON);
        KEY_MAP.put("Comma", Keys.COMMA);
        KEY_MAP.put("Period", Keys.PERIOD);
        KEY_MAP.put("Quote", Keys.APOSTROPHE);
        KEY_MAP.put("Slash", Keys.SLASH);
        KEY_MAP.put("BackSlash", Keys.BACKSLASH);
        KEY_MAP.put("Tilde", Keys.GRAVE);
        KEY_MAP.put("Equal", Keys.EQUALS);
        KEY_MAP.put("Dash", Keys.MINUS);
        KEY_MAP.put("Space", Keys.SPACE);
        KEY_MAP.put("Return", Keys.ENTER);
        KEY_MAP.put("BackSpace", Keys.BACKSPACE);
        KEY_MAP.put("Tab", Keys.TAB);
        KEY_MAP.put("PageUp", Keys.PAGE_UP);
        KEY_MAP.put("PageDown", Keys.PAGE_DOWN);
        KEY_MAP.put("End", Keys.END);
        KEY_MAP.put("Home", Keys.HOME);
        KEY_MAP.put("Insert", Keys.INSERT);
        KEY_MAP.put("Delete", Keys.FORWARD_DEL);

        KEY_MAP.put("Add", Keys.PLUS);
        KEY_MAP.put("Subtract", Keys.MINUS);
        KEY_MAP.put("Multiply", Keys.STAR);
        KEY_MAP.put("Divide", Keys.SLASH);

        KEY_MAP.put("Left", Keys.LEFT);
        KEY_MAP.put("Right", Keys.RIGHT);
        KEY_MAP.put("Up", Keys.UP);
        KEY_MAP.put("Down", Keys.DOWN);

        KEY_MAP.put("Numpad0", Keys.NUMPAD_0);
        KEY_MAP.put("Numpad1", Keys.NUMPAD_1);
        KEY_MAP.put("Numpad2", Keys.NUMPAD_2);
        KEY_MAP.put("Numpad3", Keys.NUMPAD_3);
        KEY_MAP.put("Numpad4", Keys.NUMPAD_4);
        KEY_MAP.put("Numpad5", Keys.NUMPAD_5);
        KEY_MAP.put("Numpad6", Keys.NUMPAD_6);
        KEY_MAP.put("Numpad7", Keys.NUMPAD_7);
        KEY_MAP.put("Numpad8", Keys.NUMPAD_8);
        KEY_MAP.put("Numpad9", Keys.NUMPAD_9);

        KEY_MAP.put("F1", Keys.F1);
        KEY_MAP.put("F2", Keys.F2);
        KEY_MAP.put("F3", Keys.F3);
        KEY_MAP.put("F4", Keys.F4);
        KEY_MAP.put("F5", Keys.F5);
        KEY_MAP.put("F6", Keys.F6);
        KEY_MAP.put("F7", Keys.F7);
        KEY_MAP.put("F8", Keys.F8);
        KEY_MAP.put("F9", Keys.F9);
        KEY_MAP.put("F10", Keys.F10);
        KEY_MAP.put("F11", Keys.F11);
        KEY_MAP.put("F12", Keys.F12);

        // Pause not always present, mapping to generic
        KEY_MAP.put("Pause", Keys.UNKNOWN);
    }

    private enum ParseState {
        Player1, Player2, None
    }

    public static void parseControllerConf(String filepath, List<Integer> p1, List<Integer> p2) {
        ParseState state = ParseState.None;
        int lineNo = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.equals("[Player1]")) {
                    state = ParseState.Player1;
                } else if (line.equals("[Player2]")) {
                    state = ParseState.Player2;
                } else if (state == ParseState.Player1 || state == ParseState.Player2) {
                    if (!line.contains("=")) {
                        System.out.println("[ERROR]Invalid line in key configuration at Line " + lineNo);
                        continue;
                    }

                    String[] parts = line.split("=");
                    String buttonName = parts[0].trim();
                    String keyName = parts[1].trim();

                    int buttonIndex = -1;
                    for (int i = 0; i < BUTTON_STRINGS.length; i++) {
                        if (BUTTON_STRINGS[i].equals(buttonName)) {
                            buttonIndex = i;
                            break;
                        }
                    }

                    Integer keyCode = KEY_MAP.get(keyName);

                    if (buttonIndex == -1 || keyCode == null) {
                        System.out.println("[ERROR]Invalid key in configuration file at Line " + lineNo);
                        continue;
                    }

                    if (state == ParseState.Player1) {
                        p1.set(buttonIndex, keyCode);
                    } else {
                        p2.set(buttonIndex, keyCode);
                    }
                } else {
                    System.out.println("[ERROR]Invalid line outside section at Line " + lineNo);
                }
            }
        } catch (IOException e) {
            System.out.println("[ERROR]Could not read keybinding file: " + filepath);
        }
    }
}