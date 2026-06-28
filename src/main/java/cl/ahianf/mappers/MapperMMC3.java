package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;
import cl.ahianf.utils.IRQHandle;

public class MapperMMC3 extends Mapper {

    // --- MMC3 Internal State ---
    private int m_targetRegister = 0;
    private boolean m_prgBankMode = false;      // 0: $8000 swappable, 1: $C000 swappable
    private boolean m_chrInversion = false;     // 0: 2x 2KB banks at $0000, 1: at $1000

    private final int[] m_bankRegister = new int[8];

    // --- IRQ State (Critical for SMB3 Status Bar) ---
    private boolean m_irqEnabled = false;
    private int m_irqCounter = 0;
    private int m_irqLatch = 0;
    private boolean m_irqReloadPending = false;
    private final IRQHandle m_irq;

    // --- Memory ---
    private final byte[] m_prgRam;          // 8KB WRAM (Used for SMB3 Map Decompression)
    private boolean m_prgRamEnabled = true; // Controlled by $A001
    private boolean m_prgRamWrites = true;  // Controlled by $A001

    // --- Pointers ---
    private int m_prgBank0, m_prgBank1, m_prgBank2, m_prgBank3;
    private final int[] m_chrBanks = new int[8];

    // --- Mirroring ---
    private NameTableMirroring m_mirroring;
    private final Runnable m_mirroringCallback;

    public MapperMMC3(Cartridge cart, IRQHandle irq, Runnable mirroring_cb) {
        super(cart, Mapper.Type.MMC3);
        this.m_irq = irq;
        this.m_mirroringCallback = mirroring_cb;
        this.m_mirroring = NameTableMirroring.Horizontal;

        // Initialize 8KB WRAM
        m_prgRam = new byte[8 * 1024];

        // Default Bank Setup
        int romSize = cart.getROM().length;
        m_prgBank0 = 0;
        m_prgBank1 = 0x2000;
        m_prgBank2 = romSize - 0x4000; // Fixed to second last bank
        m_prgBank3 = romSize - 0x2000; // Fixed to last bank

        updateBanks();
    }

    // =============================================================
    // CPU MEMORY (PRG)
    // =============================================================

    @Override
    public byte readPRG(int addr) {
        // WRAM ($6000 - $7FFF)
        if (addr >= 0x6000 && addr <= 0x7FFF) {
            if (m_prgRamEnabled) {
                return m_prgRam[addr & 0x1FFF];
            }
            return 0; // Open bus behavior (usually returns last byte on bus)
        }

        byte[] rom = m_cartridge.getROM();
        int offset = addr & 0x1FFF; // 8KB offsets

        if (addr >= 0x8000 && addr <= 0x9FFF) return rom[m_prgBank0 + offset];
        if (addr >= 0xA000 && addr <= 0xBFFF) return rom[m_prgBank1 + offset];
        if (addr >= 0xC000 && addr <= 0xDFFF) return rom[m_prgBank2 + offset];
        if (addr >= 0xE000) return rom[m_prgBank3 + offset];

        return 0;
    }

    @Override
    public boolean handlesPRGRAM() {
        return true;
    }

    @Override
    public void writePRG(int addr, byte value) {
        int val = value & 0xFF;

        // WRAM Writes ($6000 - $7FFF)
        if (addr >= 0x6000 && addr <= 0x7FFF) {
            if (m_prgRamEnabled && m_prgRamWrites) {
                m_prgRam[addr & 0x1FFF] = value;
            }
            return;
        }

        // Registers ($8000 - $FFFF)
        switch (addr & 0xE001) { // Mask to identify ranges and Odd/Even

            // Bank Select
            case 0x8000: // Even
                m_targetRegister = val & 0x07;
                m_prgBankMode = (val & 0x40) != 0;
                m_chrInversion = (val & 0x80) != 0;
                updateBanks();
                break;

            // Bank Data
            case 0x8001: // Odd
                m_bankRegister[m_targetRegister] = val;
                updateBanks();
                break;

            // Mirroring
            case 0xA000: // Even
                if ((m_cartridge.getNameTableMirroring() & 0x8) != 0) {
                    m_mirroring = NameTableMirroring.FourScreen;
                } else if ((val & 0x01) != 0) {
                    m_mirroring = NameTableMirroring.Horizontal;
                } else {
                    m_mirroring = NameTableMirroring.Vertical;
                }
                if (m_mirroringCallback != null) m_mirroringCallback.run();
                break;

            // PRG RAM Protect (CRITICAL FOR SMB3)
            case 0xA001: // Odd
                m_prgRamEnabled = (val & 0x80) != 0;
                m_prgRamWrites = (val & 0x40) == 0; // 0 = Allow writes
                break;

            // IRQ Latch
            case 0xC000: // Even
                m_irqLatch = val;
                break;

            // IRQ Reload
            case 0xC001: // Odd
                m_irqReloadPending = true;
                break;

            // IRQ Disable
            case 0xE000: // Even
                m_irqEnabled = false;
                m_irq.release(); // Acknowledge/Clear any pending interrupt
                break;

            // IRQ Enable
            case 0xE001: // Odd
                m_irqEnabled = true;
                break;
        }
    }

    private void updateBanks() {
        // --- CHR Banking (PPU Graphics) ---
        // 2KB Banks (x2) and 1KB Banks (x4)
        if (m_chrInversion) {
            // Inverted: 1KB banks at $0000, 2KB banks at $1000
            m_chrBanks[0] = m_bankRegister[2] * 0x0400; // 1KB
            m_chrBanks[1] = m_bankRegister[3] * 0x0400; // 1KB
            m_chrBanks[2] = m_bankRegister[4] * 0x0400; // 1KB
            m_chrBanks[3] = m_bankRegister[5] * 0x0400; // 1KB
            m_chrBanks[4] = (m_bankRegister[0] & 0xFE) * 0x0400; // 2KB
            m_chrBanks[5] = m_chrBanks[4] + 0x0400;
            m_chrBanks[6] = (m_bankRegister[1] & 0xFE) * 0x0400; // 2KB
            m_chrBanks[7] = m_chrBanks[6] + 0x0400;
        } else {
            // Standard: 2KB banks at $0000, 1KB banks at $1000
            m_chrBanks[0] = (m_bankRegister[0] & 0xFE) * 0x0400; // 2KB
            m_chrBanks[1] = m_chrBanks[0] + 0x0400;
            m_chrBanks[2] = (m_bankRegister[1] & 0xFE) * 0x0400; // 2KB
            m_chrBanks[3] = m_chrBanks[2] + 0x0400;
            m_chrBanks[4] = m_bankRegister[2] * 0x0400; // 1KB
            m_chrBanks[5] = m_bankRegister[3] * 0x0400; // 1KB
            m_chrBanks[6] = m_bankRegister[4] * 0x0400; // 1KB
            m_chrBanks[7] = m_bankRegister[5] * 0x0400; // 1KB
        }

        // --- PRG Banking (CPU Code) ---
        int romSize = m_cartridge.getROM().length;
        // Ensure banks are masked to ROM size to prevent IndexOutOfBounds
        int mask = romSize - 1;

        // R6 and R7 set 8KB banks
        int r6 = (m_bankRegister[6] * 0x2000); // 8KB
        int r7 = (m_bankRegister[7] * 0x2000); // 8KB
        int lastBank = romSize - 0x2000;
        int secondLastBank = romSize - 0x4000;

        if (m_prgBankMode) {
            // $8000 fixed to second-last, $C000 swappable
            m_prgBank0 = secondLastBank;
            m_prgBank1 = r7;
            m_prgBank2 = r6;
            m_prgBank3 = lastBank;
        } else {
            // $8000 swappable, $C000 fixed to second-last
            m_prgBank0 = r6;
            m_prgBank1 = r7;
            m_prgBank2 = secondLastBank;
            m_prgBank3 = lastBank;
        }
    }

    // =============================================================
    // PPU MEMORY (CHR)
    // =============================================================

    @Override
    public byte readCHR(int addr) {
        if (addr < 0x2000) {
            int bank = addr / 0x0400; // Divide by 1KB
            int offset = addr % 0x0400;

            // Check VROM bounds to prevent crashes on bad headers
            byte[] vrom = m_cartridge.getVROM();
            int finalAddr = m_chrBanks[bank] + offset;
            if (vrom != null && finalAddr < vrom.length) {
                return vrom[finalAddr];
            }
        }
        return 0;
    }

    @Override
    public void writeCHR(int addr, byte value) {
        // MMC3 typically uses CHR-ROM, so writes are ignored
        // unless it's a board with CHR-RAM (rare for MMC3)
    }

    // =============================================================
    // SCANLINE COUNTING (IRQ)
    // =============================================================

    @Override
    public void scanlineIRQ() {
        if (m_irqReloadPending) {
            m_irqCounter = m_irqLatch;
            m_irqReloadPending = false;
        } else if (m_irqCounter == 0) {
            m_irqCounter = m_irqLatch;
        } else {
            m_irqCounter--;
        }

        // IRQ Fired when counter transitions to 0
        if (m_irqCounter == 0 && m_irqEnabled) {
            m_irq.pull();
        }
    }

    @Override
    public NameTableMirroring getNameTableMirroring() {
        return m_mirroring;
    }
}
