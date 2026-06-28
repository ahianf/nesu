package cl.ahianf.audio;


public class Units {

    // =============================================================
    // LENGTH COUNTER
    // =============================================================
    public static class LengthCounter implements FrameClockable {
        public boolean halt = false;
        public boolean enabled = false;
        public int counter = 0;

        public void set_enable(boolean new_value) {
            enabled = new_value;
            if (!enabled) {
                counter = 0;
            }
        }

        public boolean is_enabled() {
            return enabled;
        }

        public void set_from_table(int index) {
            final int[] length_table = {
                    10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
                    12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30,
            };

            if (!enabled) return;

            if (index >= 0 && index < length_table.length) {
                counter = length_table[index];
            }
        }

        @Override
        public void half_frame_clock() {
            if (halt) return;
            if (counter == 0) return;
            --counter;
        }

        public boolean muted() {
            return !enabled || counter == 0;
        }
    }

    // =============================================================
    // LINEAR COUNTER
    // =============================================================
    public static class LinearCounter implements FrameClockable {
        public boolean reload = false;
        public int reloadValue = 0;
        public boolean control = true;
        public int counter = 0;

        public void set_linear(int new_value) {
            reloadValue = new_value;
        }

        @Override
        public void quarter_frame_clock() {
            if (reload) {
                counter = reloadValue;
                if (!control) {
                    reload = false;
                }
            }

            if (counter == 0) return;
            --counter;
        }
    }

    // =============================================================
    // VOLUME (ENVELOPE)
    // =============================================================
    public static class Volume implements FrameClockable {
        public static final int max_volume = 15;

        public final Divider divider = new Divider(0);
        public int fixedVolumeOrPeriod = max_volume;
        public int decayVolume = max_volume;
        public boolean constantVolume = true;
        public boolean isLooping = false;
        public boolean shouldStart = false;

        @Override
        public void quarter_frame_clock() {
            if (shouldStart) {
                shouldStart = false;
                decayVolume = max_volume;
                divider.set_period(fixedVolumeOrPeriod);
                return;
            }

            if (!divider.clock()) {
                return;
            }

            if (decayVolume > 0) {
                --decayVolume;
            } else if (isLooping) {
                decayVolume = max_volume;
            }
        }

        public int get() {
            if (constantVolume) {
                return fixedVolumeOrPeriod;
            }
            return decayVolume;
        }
    }
}
