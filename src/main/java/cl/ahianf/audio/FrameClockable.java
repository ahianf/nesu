package cl.ahianf.audio;


public interface FrameClockable {
    // Called every quarter frame (approx 4 times per frame)
    // Drives: Envelopes and Linear Counter
    default void quarter_frame_clock() {}

    // Called every half frame (approx 2 times per frame)
    // Drives: Length Counters and Sweep Units
    default void half_frame_clock() {}
}