package cl.ahianf.audio;

public class Constants {
    public static final float max_volume_f = 15.0f;
    public static final int max_volume = 15;

    // NES CPU clock period (approx 1.789773 MHz for NTSC)
    // 559 nanoseconds
    public static final long cpu_clock_period_ns = 559;
    public static final double cpu_clock_period_s = 559e-9;

    // APU clocks every second CPU cycle
    public static final long apu_clock_period_ns = cpu_clock_period_ns * 2;
    public static final double apu_clock_period_s = cpu_clock_period_s * 2;
}
