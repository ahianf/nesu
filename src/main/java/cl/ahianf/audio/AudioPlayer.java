package cl.ahianf.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;


public class AudioPlayer {

    // =============================================================
    // CONSTANTS & CONFIG
    // =============================================================
    public static final int OUTPUT_SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 4096; // Adjust based on latency needs

    // =============================================================
    // MEMBERS
    // =============================================================
    private AudioDevice device;
    private final float[] outputBuffer;

    private final float[] ringBuffer;
    private int readIndex = 0;
    private int writeIndex = 0;
    private final int capacity;

    private boolean initialized = false;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public AudioPlayer() {
        // Size calculation from C++: 4 * input_rate * (120ms / 100)
        // We simplify to a reasonable fixed buffer for LibGDX (e.g., ~0.5s of audio)
        this.capacity = OUTPUT_SAMPLE_RATE;
        this.ringBuffer = new float[capacity];
        this.outputBuffer = new float[BUFFER_SIZE];
    }

    public boolean start() {
        try {
            // LibGDX Audio Creation
            this.device = Gdx.audio.newAudioDevice(OUTPUT_SAMPLE_RATE, true); // true = Mono
            this.initialized = true;

            // Start a thread to drain the ring buffer into the AudioDevice
            Thread audioThread = new Thread(this::audioLoop);
            audioThread.setDaemon(true);
            audioThread.start();

            return true;
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to initialize AudioDevice: " + e.getMessage());
            return false;
        }
    }

    public void dispose() {
        if (device != null) device.dispose();
        initialized = false;
    }

    // =============================================================
    // PUBLIC API (Called by APU)
    // =============================================================

    /**
     * Pushes a single sample into the ring buffer.
     * Called by APU every audio tick.
     */
    public synchronized void pushSample(float sample) {
        if (!initialized) return;

        int nextWrite = (writeIndex + 1) % capacity;
        if (nextWrite != readIndex) { // If not full
            ringBuffer[writeIndex] = sample;
            writeIndex = nextWrite;
        }
        // If full, drop sample (overrun)
    }

    // =============================================================
    // INTERNAL LOOP (Consumer)
    // =============================================================

    private void audioLoop() {
        while (initialized) {
            int available = availableToRead();

            // Wait until we have enough data to fill a chunk
            if (available < BUFFER_SIZE) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                }
                continue;
            }

            // Read from RingBuffer to OutputBuffer
            synchronized (this) {
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    outputBuffer[i] = ringBuffer[readIndex];
                    readIndex = (readIndex + 1) % capacity;
                }
            }

            // Blocking write to hardware
            device.writeSamples(outputBuffer, 0, BUFFER_SIZE);
        }
    }

    private synchronized int availableToRead() {
        if (writeIndex >= readIndex) {
            return writeIndex - readIndex;
        } else {
            return capacity - (readIndex - writeIndex);
        }
    }
}