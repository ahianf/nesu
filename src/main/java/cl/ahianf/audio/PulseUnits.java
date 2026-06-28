package cl.ahianf.audio;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PulseUnits {


    private static final Logger log = LoggerFactory.getLogger(PulseUnits.class);


    // =============================================================
    // PULSE DUTY
    // =============================================================
    public static class PulseDuty {
        public enum Type {
            SEQ_12_5(0),
            SEQ_25(1),
            SEQ_50(2),
            SEQ_25_INV(3);

            public final int val;

            private static final Map<Integer, Type> BY_VALUE = new HashMap<>();

            static {
                for (Type t : values()) {
                    BY_VALUE.put(t.val, t);
                }
            }

            Type(int v) {
                this.val = v;
            }

            public static Type fromValue(int v) {
                Type t = BY_VALUE.get(v);
                if (t == null) {
                    throw new IllegalArgumentException("Unknown Type value: " + v);
                }
                return t;
            }
        }


        // Standard NES Duty Cycles
        private static final boolean[] SEQUENCES = {
                false, false, false, false, false, false, false, true, // 12.5%
                false, false, false, false, false, false, true, true, // 25%
                false, false, false, false, true, true, true, true, // 50%
                true, true, true, true, true, true, false, false // 25% negated
        };

        public static boolean active(Type type, int idx) {
            // idx usually goes 0-7
            return SEQUENCES[type.val * 8 + (idx & 7)];
        }
    }

    // =============================================================
    // SWEEP
    // =============================================================
    public static class Sweep implements FrameClockable {
        private final Pulse pulse;
        private final boolean ones_complement;

        public int period = 0;
        public boolean enabled = false;
        public boolean reload = false;
        public boolean negate = false;
        public int shift = 0;

        public final Divider divider = new Divider(0);

        public Sweep(Pulse pulse, boolean ones_complement) {
            this.pulse = pulse;
            this.ones_complement = ones_complement;
        }

        @Override
        public void half_frame_clock() {
            if (reload) {
                divider.set_period(period);
                reload = false;
                return;
            }

            if (!enabled) return;

            if (!divider.clock()) return;

            if (shift > 0) {
                int current = pulse.period;
                int target = calculate_target(current);

                if (!is_muted(pulse.period, target)) {
                    if (log.isTraceEnabled()) {
                        log.trace("[APU] Sweep update: {} -> {}", current, target);
                    }
                    pulse.set_period(target);
                }
            }
        }

        public static boolean is_muted(int current, int target) {
            return current < 8 || target > 0x7FF;
        }

        public int calculate_target(int current) {
            int amt = current >> shift;
            if (!negate) {
                return current + amt;
            }

            if (ones_complement) {
                return Math.max(0, current - amt - 1);
            }

            return Math.max(0, current - amt);
        }
    }
}