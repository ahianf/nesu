package cl.ahianf;

import java.util.List;

import cl.ahianf.audio.APU;
import cl.ahianf.audio.AudioPlayer;
import cl.ahianf.input.Controller;
import cl.ahianf.cpu.CPU;
import cl.ahianf.mappers.Mapper;
import cl.ahianf.memory.Cartridge;
import cl.ahianf.memory.MainBus;
import cl.ahianf.ppu.PPU;


import cl.ahianf.ppu.VirtualScreen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.Batch;


public class Emulator {

    // =============================================================
    // CONSTANTS
    // =============================================================
    public static final int NESVideoWidth = 256;
    public static final int NESVideoHeight = 240;

    // Timing
    private static final long CPU_CLOCK_PERIOD_NS = 559;

    // =============================================================
    // MEMBERS
    // =============================================================

    // Components
    private CPU m_cpu;
    private AudioPlayer m_audioPlayer;
    private PPU m_ppu;
    private APU m_apu;
    private final Cartridge m_cartridge;
    private Mapper m_mapper; // Reference, not unique_ptr

    private final Controller m_controller1;
    private final Controller m_controller2;
    private final byte[] m_oamDmaPage;
    private MainBus m_bus;

    // Video
    private VirtualScreen m_emulatorScreen;
    private float m_screenScale;

    // Execution State
    private long m_lastWakeup;
    private long mElapsedTime; // Accumulator for synchronization
    private boolean m_paused = false;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public Emulator() {
        this.m_screenScale = 3.0f;
        this.m_lastWakeup = 0;
        this.mElapsedTime = 0;

        // 1. Initialize Input
        m_controller1 = new Controller();
        m_controller2 = new Controller();
        m_oamDmaPage = new byte[256];

        // 2. Initialize Audio
        m_audioPlayer = new AudioPlayer();

        // 3. Initialize Screen and PPU
        m_emulatorScreen = new VirtualScreen();
        m_emulatorScreen.create(NESVideoWidth, NESVideoHeight, m_screenScale, 0xFFFFFFFF);
        m_ppu = new PPU(m_emulatorScreen);

        // 4. Resolve CPU/APU/bus cycle.
        m_cpu = new CPU(null);
        m_apu = new APU(m_audioPlayer, m_cpu.createIRQHandler(), this::DMCDMA);
        m_bus = new MainBus(m_ppu, m_apu, m_controller1, m_controller2, this::OAMDMA);
        m_cpu.connectBus(m_bus);

        m_ppu.setInterruptCallback(() -> m_cpu.nmiInterrupt());

        m_cartridge = new Cartridge();
    }

    // =============================================================
    // CONTROL METHODS
    // =============================================================

    /**
     * Replaces the setup phase of `Emulator::run`.
     */
    public void loadRom(String romPath) {
        if (!m_cartridge.loadFromFile(romPath)) {
            System.out.println("[ERROR]Failed to load ROM: " + romPath);
            return;
        }

        // Create Mapper
        m_mapper = Mapper.createMapper(
                Mapper.Type.fromInt(m_cartridge.getMapper() & 0xFF),
                m_cartridge,
                m_cpu.createIRQHandler(),
                () -> m_ppu.updateMirroring()
        );

        if (m_mapper == null) {
            System.out.println("[ERROR]Creating Mapper failed. Probably unsupported.");
            return;
        }

        // Attach Mapper
        if (!m_bus.setMapper(m_mapper) || !m_ppu.setMapper(m_mapper)) {
            System.out.println("[ERROR]Failed to set Mapper on buses.");
            return;
        }

        // Reset Components
        m_cpu.reset();
        m_ppu.reset();

        // Audio Start
        m_audioPlayer.start();

        // Timing Init
        m_lastWakeup = System.nanoTime();
        mElapsedTime = 0;
    }

    /**
     * Replaces the `while (m_window.isOpen())` loop.
     * Call this from SimpleNESGame.render().
     */
    public void updateFrame(Batch batch) {
        // Handle Input (Polling replacement)
        handleInput();

        // Assumed true for desktop
        if (!m_paused) {
            long now = System.nanoTime();

            // Handle massive time jumps (e.g. debugging / window move)
            if (now - m_lastWakeup > 1000000000L) { // > 1 second lag
                m_lastWakeup = now;
                mElapsedTime = 0;
            }

            mElapsedTime += (now - m_lastWakeup);
            m_lastWakeup = now;

            // Catch up logic
            while (mElapsedTime > CPU_CLOCK_PERIOD_NS) {
                // PPU (3x CPU speed)
                m_ppu.step();
                m_ppu.step();
                m_ppu.step();

                // CPU
                m_cpu.step();

                // APU
                m_apu.step();

                mElapsedTime -= CPU_CLOCK_PERIOD_NS;
            }

            // Draw
            m_emulatorScreen.draw(batch, 0, 0);
        } else {
            // Sleep slightly to save CPU if paused
            try {
                Thread.sleep(16);
            } catch (Exception e) {
            }
        }
    }

    private void handleInput() {
        // Pause Toggle (F2)
        if (Gdx.input.isKeyJustPressed(Keys.F2)) {
            m_paused = !m_paused;
            if (!m_paused) {
                m_lastWakeup = System.nanoTime();
                System.out.println("Unpaused.");
            } else {
                System.out.println("Paused.");
            }
        }

        // Step Frame (F3) - Only when paused
        if (m_paused && Gdx.input.isKeyJustPressed(Keys.F3)) {
            // 29781 CPU cycles is approx one frame (NTSC)
            for (int i = 0; i < 29781; ++i) {
                m_ppu.step();
                m_ppu.step();
                m_ppu.step();
                m_cpu.step();
                m_apu.step();
            }
        }

        // Log Levels
        if (Gdx.input.isKeyJustPressed(Keys.F4)) {
            // Log.setLevel(Info); 
            System.out.println("Log level set to Info (Stub)");
        }
        if (Gdx.input.isKeyJustPressed(Keys.F5)) {
            // Log.setLevel(InfoVerbose);
            System.out.println("Log level set to Verbose (Stub)");
        }

        // Focus handling handled by LibGDX ApplicationListener usually, 
        // but we can check if window is active if needed.
    }

    // =============================================================
    // CALLBACKS (DMA)
    // =============================================================

    // OAMDMA(Byte page)
    private void OAMDMA(byte page) {
        m_cpu.skipOAMDMACycles();
        m_bus.copyPage(page, m_oamDmaPage);
        m_ppu.doDMA(m_oamDmaPage);
    }

    // DMCDMA(Address addr)
    private byte DMCDMA(int addr) {
        m_cpu.skipDMCDMACycles();
        return m_bus.read(addr);
    }

    // =============================================================
    // CONFIGURATION
    // =============================================================

    public void setVideoHeight(int height) {
        m_screenScale = (float) height / NESVideoHeight;
        System.out.println("Scale: " + m_screenScale + " set.");
        if (m_emulatorScreen != null) m_emulatorScreen.setPixelScale(m_screenScale);
    }

    public void setVideoWidth(int width) {
        m_screenScale = (float) width / NESVideoWidth;
        System.out.println("Scale: " + m_screenScale + " set.");
        if (m_emulatorScreen != null) m_emulatorScreen.setPixelScale(m_screenScale);
    }

    public void setVideoScale(float scale) {
        m_screenScale = scale;
        System.out.println("Scale: " + m_screenScale + " set.");
        if (m_emulatorScreen != null) m_emulatorScreen.setPixelScale(m_screenScale);
    }

    public void setKeys(List<Integer> p1, List<Integer> p2) {
        m_controller1.setKeyBindings(p1);
        m_controller2.setKeyBindings(p2);
    }
}
