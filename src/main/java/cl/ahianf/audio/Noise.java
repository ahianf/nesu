package cl.ahianf.audio;

public class Noise implements FrameClockable {

    public enum Mode {
        Bit1(0), // Standard
        Bit6(1); // Mode flag set

        public final int val;
        Mode(int v) { val = v; }
    }

    // =============================================================
    // MEMBERS
    // =============================================================
    public final Units.Volume volume = new Units.Volume();
    public final Units.LengthCounter length_counter = new Units.LengthCounter();

    // OPTIMIZATION: Inlined Divider
    // Replaces: public final Divider divider = new Divider(4);
    private int timerPeriod = 4;
    private int timerValue = 0;

    public int mode = 0;
    public int period = 0;
    public int shift_register = 1;

    // =============================================================
    // METHODS
    // =============================================================

    public void set_period_from_table(int idx) {
        final int[] periods = {
                4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068,
        };
        if (idx >= 0 && idx < periods.length) {
            timerPeriod = periods[idx];
            period = periods[idx];
            // Divider behavior on set_period usually doesn't reset counter immediately
            // in all NES implementations, but we stick to previous Divider logic if it did.
            // Previous 'Divider' class didn't reset on set_period, only updated internal 'period'.
        }
    }

    // Clocked at the cpu freq
    public void clock() {
        // 1. Divider Logic
        if (timerPeriod == 0) return;

        boolean fire = false;
        if (timerValue == 0) {
            timerValue = timerPeriod;
            fire = true;
        } else {
            timerValue--;
        }

        if (!fire) return;

        // 2. Shift Register Logic
        int tapBit = (mode == 1) ? 6 : 1;
        int bit0 = shift_register & 1;
        int bitTap = (shift_register >> tapBit) & 1;
        int feedback = bit0 ^ bitTap;
        shift_register = (shift_register >> 1) | (feedback << 14);
    }

    public byte sample() {
        if (length_counter.muted()) {
            return 0;
        }
        if ((shift_register & 1) == 0) {
            return (byte)volume.get();
        }
        return 0;
    }

    @Override
    public void quarter_frame_clock() {
        volume.quarter_frame_clock();
    }

    @Override
    public void half_frame_clock() {
        length_counter.half_frame_clock();
    }
}