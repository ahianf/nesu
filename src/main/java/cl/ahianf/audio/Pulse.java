package cl.ahianf.audio;

public class Pulse implements FrameClockable {

    public enum Type {
        Pulse1,
        Pulse2
    }

    // =============================================================
    // MEMBERS
    // =============================================================
    public final Units.Volume volume = new Units.Volume();
    public final Units.LengthCounter length_counter = new Units.LengthCounter();
    public final PulseUnits.Sweep sweep;

    public int seq_idx = 0;
    public PulseUnits.PulseDuty.Type seq_type = PulseUnits.PulseDuty.Type.SEQ_50;

    // OPTIMIZATION: Inlined Divider Logic
    // Replaces: public final Divider sequencer = new Divider(0);
    private int timerPeriod = 0;
    private int timerValue = 0;

    public int period = 0; // External view of period
    public final Type type;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public Pulse(Type type) {
        this.type = type;
        this.sweep = new PulseUnits.Sweep(this, type == Type.Pulse1);
    }

    // =============================================================
    // METHODS
    // =============================================================

    public void set_period(int p) {
        period = p;
        // Inlined sequencer.set_period(p)
        timerPeriod = p;
    }

    /**
     * Clocked at half the cpu freq
     * OPTIMIZATION: Logic inlined to remove method call overhead
     */
    public void clock() {
        // Original Divider.clock() logic:
        // if (counter == 0) { counter = period; return true; } else { counter--; return false; }

        if (timerValue == 0) {
            timerValue = timerPeriod;
            // The sequencer fired, step the duty cycle
            // C++: seq_idx = (8 + (seq_idx - 1)) % 8;
            seq_idx = (seq_idx - 1 + 8) & 7; // Optimization: % 8 -> & 7
        } else {
            timerValue--;
        }
    }

    public byte sample() {
        if (length_counter.muted()) {
            return 0;
        }

        if (sweep.is_muted(period, sweep.calculate_target(period))) {
            return 0;
        }

        if (!PulseUnits.PulseDuty.active(seq_type, seq_idx)) {
            return 0;
        }

        return (byte) volume.get();
    }

    // Proxy methods for FrameClockable to route to components
    @Override
    public void quarter_frame_clock() {
        volume.quarter_frame_clock();
    }

    @Override
    public void half_frame_clock() {
        length_counter.half_frame_clock();
        sweep.half_frame_clock();
    }
}