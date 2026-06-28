package cl.ahianf.audio;

import cl.ahianf.utils.IRQHandle;

import java.util.function.Function;

public class DMC implements FrameClockable {

    // =============================================================
    // MEMBERS
    // =============================================================
    public boolean irqEnable = false;
    public boolean loop = false;
    public int volume = 0;

    public boolean change_enabled = false;
    public final Divider change_rate = new Divider(0);

    public int sample_begin = 0; // Address (16-bit)
    public int sample_length = 0;

    public int remaining_bytes = 0;
    public int current_address = 0; // Address (16-bit)

    public int sample_buffer = 0; // Byte

    public int shifter = 0;
    public int remaining_bits = 0;
    public boolean silenced = false;

    public boolean interrupt = false;

    private final IRQHandle irq;
    private final Function<Integer, Byte> dma; // Maps Address -> Byte

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public DMC(IRQHandle irq, Function<Integer, Byte> dma) {
        this.irq = irq;
        this.dma = dma;
    }

    // =============================================================
    // CONTROL METHODS
    // =============================================================

    public void set_irq_enable(boolean enable) {
        irqEnable = enable;
        if (!irqEnable) {
            interrupt = false;
            irq.release();
        }
    }

    public void set_rate(int idx) {
        final int[] rate = { 428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54 };
        if (idx >= 0 && idx < rate.length) {
            change_rate.set_period(rate[idx]);
            change_rate.reset();
        }
    }

    public void control(boolean enable) {
        change_enabled = enable; // int/bool conversion handled by logic
        if (!enable) {
            remaining_bytes = 0;
        } else if (remaining_bytes == 0) {
            // restart
            current_address = sample_begin;
            remaining_bytes = sample_length;
        }
    }

    // For compatibility with APU.java which passes int
    public void control(int val) {
        control(val != 0);
    }

    public void clear_interrupt() {
        irq.release();
        interrupt = false;
    }

    public byte sample() {
        return (byte) volume;
    }

    public boolean has_more_samples() {
        return remaining_bytes > 0;
    }

    // =============================================================
    // EXECUTION
    // =============================================================

    public void clock() {
        if (!change_enabled) return;

        if (!change_rate.clock()) return;

        int delta = pop_delta();
        if (silenced) return;

        if (delta == 1 && volume <= 125) {
            volume += 2;
        } else if (delta == 0 && volume >= 2) {
            volume -= 2;
        }
    }

    private int pop_delta() {
        if (remaining_bits == 0) {
            remaining_bits = 8;
            if (load_sample()) {
                shifter = sample_buffer;
                silenced = false;
            } else {
                silenced = true;
            }
        } else {
            remaining_bits--;
        }

        int rv = shifter & 0x1;
        shifter >>= 1;
        return rv;
    }

    private boolean load_sample() {
        if (remaining_bytes == 0) {
            if (!loop) {
                if (irqEnable) {
                    interrupt = true;
                    irq.pull();
                }
                return false;
            }

            current_address = sample_begin;
            remaining_bytes = sample_length;
        } else {
            remaining_bytes -= 1;
        }

        // DMA Read
        // We accept the Byte from Function, convert to unsigned int
        Byte b = dma.apply(current_address);
        sample_buffer = b != null ? b & 0xFF : 0;

        // Wrap around 0xFFFF to 0x8000 (standard NES behavior)
        if (current_address == 0xFFFF) {
            current_address = 0x8000;
        } else {
            current_address += 1;
        }
        return true;
    }
}