package cl.ahianf.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import cl.ahianf.FrameCounter;
import cl.ahianf.utils.IRQHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APU {

    // =============================================================
    // CONSTANTS (Registers)
    // =============================================================
    public static final int APU_SQ1_VOL = 0x4000;
    public static final int APU_SQ1_SWEEP = 0x4001;
    public static final int APU_SQ1_LO = 0x4002;
    public static final int APU_SQ1_HI = 0x4003;
    public static final int APU_SQ2_VOL = 0x4004;
    public static final int APU_SQ2_SWEEP = 0x4005;
    public static final int APU_SQ2_LO = 0x4006;
    public static final int APU_SQ2_HI = 0x4007;
    public static final int APU_TRI_LINEAR = 0x4008;
    public static final int APU_TRI_LO = 0x400a;
    public static final int APU_TRI_HI = 0x400b;
    public static final int APU_NOISE_VOL = 0x400c;
    public static final int APU_NOISE_LO = 0x400e;
    public static final int APU_NOISE_HI = 0x400f;
    public static final int APU_DMC_FREQ = 0x4010;
    public static final int APU_DMC_RAW = 0x4011;
    public static final int APU_DMC_START = 0x4012;
    public static final int APU_DMC_LEN = 0x4013;
    public static final int APU_CONTROL = 0x4015;
    public static final int APU_FRAME_CONTROL = 0x4017;

    // =============================================================
    // AUDIO TIMING CONSTANTS
    // =============================================================
    private static final double CPU_FREQ = 1789773.0;  // NTSC CPU: ~1.79 MHz
    private static final double SAMPLE_RATE = 44100.0;  // Standard audio rate
    private static final double CYCLES_PER_SAMPLE = CPU_FREQ / SAMPLE_RATE;  // ~40.58 CPU cycles

    // =============================================================
    // MEMBERS
    // =============================================================
    public final Pulse pulse1;
    public final Pulse pulse2;
    public final Triangle triangle;
    public final Noise noise;
    public final DMC dmc;
    public final FrameCounter frame_counter;

    private final AudioPlayer audioPlayer;
    private boolean divideByTwo = false;

    // Audio downsampling state
    private double cycleAccumulator = 0.0;

    // High-pass filter state (removes DC offset)
    private float filterPrevSample = 0.0f;
    private float filterPrevOutput = 0.0f;

    private static final Logger log = LoggerFactory.getLogger(APU.class);

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public APU(AudioPlayer player, IRQHandle irq, Function<Integer, Byte> dmcDma) {
        this.audioPlayer = player;

        pulse1 = new Pulse(Pulse.Type.Pulse1);
        pulse2 = new Pulse(Pulse.Type.Pulse2);
        triangle = new Triangle();
        noise = new Noise();
        dmc = new DMC(irq, dmcDma);

        List<FrameClockable> slots = new ArrayList<>(Arrays.asList(
                pulse1, pulse2, triangle, noise, dmc
        ));
        this.frame_counter = new FrameCounter(slots, irq);
    }

    // =============================================================
    // EXECUTION
    // =============================================================

    /**
     * Called once per CPU cycle.
     * Runs APU channel emulation and generates audio samples at 44.1kHz.
     */
    public void step() {
        runChannelEmulation();
        generateAudioSample();
    }

    /**
     * Runs the emulation logic for all APU channels.
     * Must run every CPU cycle to keep state accurate.
     */
    private void runChannelEmulation() {
        // Triangle channel: clocked every CPU cycle
        triangle.clock();

        // Other channels: clocked every other CPU cycle (APU clock at ~894kHz)
        if (divideByTwo) {
            pulse1.clock();
            pulse2.clock();
            noise.clock();
            dmc.clock();
            frame_counter.clock();
        }
        divideByTwo = !divideByTwo;
    }

    /**
     * Generates audio samples at 44.1kHz by downsampling from CPU frequency.
     *
     * The NES CPU runs at ~1.79MHz, but we need audio at 44.1kHz.
     * This means we generate one sample approximately every 40.58 CPU cycles.
     * We accumulate cycles until we reach this threshold, then generate a sample.
     */
    private void generateAudioSample() {
        cycleAccumulator += 1.0;

        // Check if we've accumulated enough cycles for the next sample
        while (cycleAccumulator >= CYCLES_PER_SAMPLE) {
            cycleAccumulator -= CYCLES_PER_SAMPLE;

            // Mix all channels into a single sample
            float rawSample = mixChannels();

            // Apply high-pass filter to remove DC offset
            float filteredSample = applyHighPassFilter(rawSample);

            // Clamp to valid audio range and send to output
            float finalSample = clampSample(filteredSample);
            audioPlayer.pushSample(finalSample);
        }
    }

    /**
     * Mixes all APU channels into a single audio sample.
     * Uses the standard NES linear approximation mixing formulas.
     */
    private float mixChannels() {
        byte b4 = pulse1.sample();
        int p1 = b4 & 0xFF;
        byte b3 = pulse2.sample();
        int p2 = b3 & 0xFF;
        byte b2 = triangle.sample();
        int tri = b2 & 0xFF;
        byte b1 = noise.sample();
        int noi = b1 & 0xFF;
        byte b = this.dmc.sample();
        int dmcSample = b & 0xFF;

        return mix(p1, p2, tri, noi, dmcSample);
    }

    /**
     * Clamps an audio sample to the valid range [-1.0, 1.0].
     */
    private float clampSample(float sample) {
        if (sample > 1.0f) return 1.0f;
        if (sample < -1.0f) return -1.0f;
        return sample;
    }

    // =============================================================
    // MIXING LOGIC
    // =============================================================

    /**
     * Standard NES linear approximation mixing.
     * Returns a value roughly between 0.0 and 1.0.
     */
    private float mix(int pulse1, int pulse2, int triangle, int noise, int dmc) {
        float pulseOut = calculatePulseMix(pulse1, pulse2);
        float tndOut = calculateTNDMix(triangle, noise, dmc);
        return pulseOut + tndOut;
    }

    /**
     * Mixes the two pulse channels using the standard NES formula.
     */
    private float calculatePulseMix(int pulse1, int pulse2) {
        float sum = (float) pulse1 + (float) pulse2;
        if (sum > 0) {
            return 95.88f / ((8128.0f / sum) + 100.0f);
        }
        return 0.0f;
    }

    /**
     * Mixes Triangle, Noise, and DMC channels using the standard NES formula.
     */
    private float calculateTNDMix(int triangle, int noise, int dmc) {
        float t = (float) triangle / 8227.0f;
        float n = (float) noise / 12241.0f;
        float d = (float) dmc / 22638.0f;

        float sum = t + n + d;
        if (sum > 0) {
            return 159.79f / ((1.0f / sum) + 100.0f);
        }
        return 0.0f;
    }

    /**
     * Applies a first-order high-pass filter to remove DC offset.
     *
     * This centers the audio around 0.0 instead of 0.3-0.5.
     * Uses the formula: y[n] = alpha * (y[n-1] + x[n] - x[n-1])
     * Alpha = 0.996 gives a cutoff frequency around 90Hz at 44.1kHz sample rate.
     */
    private float applyHighPassFilter(float sample) {
        float alpha = 0.996f;
        float output = alpha * (filterPrevOutput + sample - filterPrevSample);

        filterPrevSample = sample;
        filterPrevOutput = output;

        return output;
    }

    // =============================================================
    // STATUS AND REGISTER OPERATIONS
    // =============================================================

    public byte readStatus() {
        boolean last_frame_interrupt = frame_counter.frame_interrupt;
        frame_counter.clearFrameInterrupt();
        boolean dmc_interrupt = dmc.interrupt;
        dmc.clear_interrupt();

        int res = (pulse1.length_counter.muted() ? 0 : 1) |
                  (pulse2.length_counter.muted() ? 0 : 1) << 1 |
                  (triangle.length_counter.muted() ? 0 : 1) << 2 |
                  (noise.length_counter.muted() ? 0 : 1) << 3 |
                  (!dmc.has_more_samples() ? 0 : 1) << 4 |
                  (last_frame_interrupt ? 1 : 0) << 6 |
                  (dmc_interrupt ? 1 : 0) << 7;

        return (byte) res;
    }

    public void writeRegister(int addr, byte value) {
        int v = value & 0xFF;
        switch (addr) {
            case APU_SQ1_VOL:
                pulse1.volume.fixedVolumeOrPeriod = v & 0xf;
                pulse1.volume.constantVolume = (v & (1 << 4)) != 0;
                pulse1.volume.isLooping = pulse1.length_counter.halt = (v & (1 << 5)) != 0;
                pulse1.seq_type = PulseUnits.PulseDuty.Type.fromValue(v >> 6);
                break;

            case APU_SQ1_SWEEP:
                pulse1.sweep.enabled = (v & (1 << 7)) != 0;
                pulse1.sweep.period = (v >> 4) & 0x7;
                pulse1.sweep.negate = (v & (1 << 3)) != 0;
                pulse1.sweep.shift = v & 0x7;
                pulse1.sweep.reload = true;
                break;

            case APU_SQ1_LO:
                pulse1.set_period((pulse1.period & 0xff00) | v);
                break;

            case APU_SQ1_HI:
                int new_period = (pulse1.period & 0x00ff) | ((v & 0x7) << 8);
                pulse1.length_counter.set_from_table(v >> 3);
                pulse1.seq_idx = 0;
                pulse1.volume.shouldStart = true;
                pulse1.set_period(new_period);
                break;

            case APU_SQ2_VOL:
                pulse2.volume.fixedVolumeOrPeriod = v & 0xf;
                pulse2.volume.constantVolume = (v & (1 << 4)) != 0;
                pulse2.volume.isLooping = pulse2.length_counter.halt = (v & (1 << 5)) != 0;
                pulse2.seq_type = PulseUnits.PulseDuty.Type.fromValue(v >> 6);
                break;

            case APU_SQ2_SWEEP:
                pulse2.sweep.enabled = (v & (1 << 7)) != 0;
                pulse2.sweep.period = (v >> 4) & 0x7;
                pulse2.sweep.negate = (v & (1 << 3)) != 0;
                pulse2.sweep.shift = v & 0x7;
                pulse2.sweep.reload = true;
                break;

            case APU_SQ2_LO:
                pulse2.set_period((pulse2.period & 0xff00) | v);
                break;

            case APU_SQ2_HI:
                new_period = (pulse2.period & 0x00ff) | ((v & 0x7) << 8);
                pulse2.length_counter.set_from_table(v >> 3);
                pulse2.seq_idx = 0;
                pulse2.set_period(new_period);
                pulse2.volume.shouldStart = true;
                break;

            case APU_TRI_LINEAR:
                triangle.linear_counter.set_linear(v & 0x7f);
                triangle.linear_counter.reload = true;
                triangle.linear_counter.control = triangle.length_counter.halt = (v >> 7) != 0;
                break;

            case APU_TRI_LO:
                triangle.set_period((triangle.period & 0xff00) | v);
                break;

            case APU_TRI_HI:
                new_period = (triangle.period & 0x00ff) | ((v & 0x7) << 8);
                triangle.length_counter.set_from_table(v >> 3);
                triangle.set_period(new_period);
                triangle.linear_counter.reload = true;
                break;

            case APU_NOISE_VOL:
                noise.volume.fixedVolumeOrPeriod = v & 0xf;
                noise.volume.constantVolume = (v & (1 << 4)) != 0;
                noise.volume.isLooping = noise.length_counter.halt = (v & (1 << 5)) != 0;
                break;

            case APU_NOISE_LO:
                noise.mode = (v & (1 << 7));
                noise.set_period_from_table(v & 0xf);
                break;

            case APU_NOISE_HI:
                noise.length_counter.set_from_table(v >> 3);
                noise.volume.divider.reset();
                break;

            case APU_DMC_FREQ:
                dmc.irqEnable = v >> 7 != 0;
                dmc.loop = ((v >> 6) & 1) != 0;
                dmc.set_rate(v & 0xf);
                break;

            case APU_DMC_RAW:
                dmc.volume = v & 0x7f;
                break;

            case APU_DMC_START:
                dmc.sample_begin = 0xc000 | (v << 6);
                break;

            case APU_DMC_LEN:
                dmc.sample_length = (v << 4) | 1;
                break;

            case APU_CONTROL:
                pulse1.length_counter.set_enable((v & 0x1) != 0);
                pulse2.length_counter.set_enable((v & 0x2) != 0);
                triangle.length_counter.set_enable((v & 0x4) != 0);
                noise.length_counter.set_enable((v & 0x8) != 0);
                dmc.control(v & 0x10);
                break;

            case APU_FRAME_CONTROL:
                FrameCounter.Mode m = ((v >> 7) == 1) ? FrameCounter.Mode.Seq5Step : FrameCounter.Mode.Seq4Step;
                boolean inhibit = ((v >> 6) & 1) == 1;
                frame_counter.reset(m, inhibit);
                break;
        }
    }
}