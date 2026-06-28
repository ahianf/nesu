package cl.ahianf.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.badlogic.gdx.Gdx;

public class Controller {

    // =============================================================
    // CONSTANTS & ENUMS
    // =============================================================
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_SELECT = 2;
    public static final int BUTTON_START = 3;
    public static final int BUTTON_UP = 4;
    public static final int BUTTON_DOWN = 5;
    public static final int BUTTON_LEFT = 6;
    public static final int BUTTON_RIGHT = 7;
    public static final int TOTAL_BUTTONS = 8;

    // =============================================================
    // MEMBERS
    // =============================================================
    private boolean m_strobe;
    private int m_keyStates;
    private List<Integer> m_keyBindings;

    public Controller() {
        m_keyStates = 0;
        m_strobe = false;
        // Default bindings (Will be overwritten by parser)
        m_keyBindings = new ArrayList<>(Collections.nCopies(TOTAL_BUTTONS, 0));
    }

    public void setKeyBindings(List<Integer> keys) {
        if (keys.size() == TOTAL_BUTTONS) {
            this.m_keyBindings = new ArrayList<>(keys);
        }
    }

    public void strobe(byte b) {
        m_strobe = ((b & 1) == 1);

        if (!m_strobe) {
            m_keyStates = 0;
            int shift = 0;

            for (int button = 0; button < TOTAL_BUTTONS; ++button) {
                int keyCode = m_keyBindings.get(button);
                boolean pressed = Gdx.input.isKeyPressed(keyCode);

                if (pressed) {
                    m_keyStates |= (1 << shift);
                }
                ++shift;
            }
        }
    }

    public byte read() {
        byte ret;
        if (m_strobe) {
            int keyCode = m_keyBindings.get(BUTTON_A);
            ret = Gdx.input.isKeyPressed(keyCode) ? (byte)1 : 0;
        } else {
            ret = (byte) (m_keyStates & 1);
            m_keyStates >>= 1;
        }
        return (byte) (ret | 0x40);
    }
}