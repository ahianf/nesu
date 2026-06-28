package cl.ahianf.audio;

public class Timer {
    public final long period_ns;
    private long leftover_ns = 0;

    public Timer(long period_ns) {
        this.period_ns = period_ns;
    }

    // Clock the timer and return number of periods elapsed
    public int clock(long elapsed_ns) {
        leftover_ns += elapsed_ns;
        if (leftover_ns < period_ns) {
            return 0;
        }

        long cycles = leftover_ns / period_ns;
        leftover_ns = leftover_ns % period_ns;
        return (int) cycles;
    }
}