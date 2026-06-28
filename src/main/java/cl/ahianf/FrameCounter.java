package cl.ahianf;

import cl.ahianf.audio.FrameClockable;
import cl.ahianf.utils.IRQHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class FrameCounter {

    // =============================================================
    // CONSTANTS
    // =============================================================
    public static final int Q1 = 7457;
    public static final int Q2 = 14913;
    public static final int Q3 = 22371;
    public static final int Q4 = 29829;
    public static final int preQ4 = Q4 - 1;
    public static final int postQ4 = Q4 + 1;
    public static final int seq4step_length = postQ4;

    public static final int Q5 = 37281;
    public static final int seq5step_length = Q5 + 1;

    public enum Mode {
        Seq4Step,
        Seq5Step
    }

    // =============================================================
    // MEMBERS
    // =============================================================
    private final List<FrameClockable> frame_slots;
    private final IRQHandle irq;

    public Mode mode = Mode.Seq4Step;
    public int counter = 0;
    public boolean interrupt_inhibit = false;
    public boolean frame_interrupt = false;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public FrameCounter(List<FrameClockable> slots, IRQHandle irq) {
        this.frame_slots = slots;
        this.irq = irq;
    }

    private static final Logger log = LoggerFactory.getLogger(FrameCounter.class);

    // =============================================================
    // METHODS
    // =============================================================

    public void clearFrameInterrupt() {
        if (frame_interrupt) {
            frame_interrupt = false;
            irq.release();
        }
    }

    public void reset(Mode m, boolean irq_inhibit) {
        this.mode = m;
        this.interrupt_inhibit = irq_inhibit;

        // TODO: delay reset by 3-4 cycles?
        if (interrupt_inhibit) {
            clearFrameInterrupt();
        }

        if (mode == Mode.Seq5Step) {
            for (FrameClockable c : frame_slots) {
                c.quarter_frame_clock();
                c.half_frame_clock();
            }
        }
    }

    /**
     * Clocked at APU frequency (half CPU frequency)
     */
    public void clock() {
        counter++;

        switch (counter) {
            case Q1:
                for (FrameClockable c : frame_slots) {
                    c.quarter_frame_clock();
                }
                if (log.isTraceEnabled()) {
                    log.trace("framecounter: Q1 clock");
                }
                break;
            case Q2:
                for (FrameClockable c : frame_slots) {
                    c.quarter_frame_clock();
                    c.half_frame_clock();
                }
                if (log.isTraceEnabled()) {
                    log.trace("framecounter: Q2 clock");
                }
                break;
            case Q3:
                for (FrameClockable c : frame_slots) {
                    c.quarter_frame_clock();
                }
                if (log.isTraceEnabled()) {
                    log.trace("framecounter: Q3 clock");
                }
                break;
            case Q4:
                // Only 4-step
                if (mode != Mode.Seq4Step) break;

                for (FrameClockable c : frame_slots) {
                    c.quarter_frame_clock();
                    c.half_frame_clock();
                }
                if (log.isTraceEnabled()) {
                    log.trace("framecounter: Q4 clock");
                }

                if (!interrupt_inhibit) {
                    irq.pull();
                    frame_interrupt = true;
                }
                break;
            case Q5:
                // Only 5-step
                if (mode != Mode.Seq5Step) break;

                for (FrameClockable c : frame_slots) {
                    c.quarter_frame_clock();
                    c.half_frame_clock();
                }
                if (log.isTraceEnabled()) {
                    log.trace("framecounter: Q5 clock");
                }
                break;
        }

        if ((mode == Mode.Seq4Step && counter == seq4step_length) ||
            (mode == Mode.Seq5Step && counter == seq5step_length)) {
            counter = 0;
        }
    }
}