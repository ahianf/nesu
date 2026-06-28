package cl.ahianf.audio;

public class Triangle implements FrameClockable {

    // =============================================================
    // MEMBERS
    // =============================================================
    public final Units.LengthCounter length_counter = new Units.LengthCounter();
    public final Units.LinearCounter linear_counter = new Units.LinearCounter();

    public int seq_idx = 0;

    // OPTIMIZATION: Inlined Divider Logic
    // Replaces: public final Divider sequencer = new Divider(4);
    // Note: Triangle sequencer typically has no configurable period (it's driven by timer),
    // but the Linear Counter/Timer logic in NES is specific.
    // In your previous code, 'sequencer' held the low-level timer period.
    private int timerPeriod = 0;
    private int timerValue = 0;

    public int period = 0;

    // =============================================================
    // METHODS
    // =============================================================

    public void set_period(int p) {
        period = p;
        timerPeriod = p;
    }

    public void clock() {
        // Optimized Divider logic
        if (timerValue == 0) {
            timerValue = timerPeriod;
            // Sequencer event
            seq_idx = (seq_idx + 1) & 31; // Optimization: % 32 -> & 31
        } else {
            timerValue--;
        }
    }

    public byte sample() {
        // Only mute OUTPUT, not the sequencer
        if (length_counter.muted() || linear_counter.counter == 0) {
            return 0;
        }
        return (byte) volume();
    }

    public int volume() {
        if (seq_idx < 16) {
            return 15 - seq_idx;
        } else {
            return seq_idx - 16;
        }
    }

    @Override
    public void quarter_frame_clock() {
        linear_counter.quarter_frame_clock();
    }

    @Override
    public void half_frame_clock() {
        length_counter.half_frame_clock();
    }
}