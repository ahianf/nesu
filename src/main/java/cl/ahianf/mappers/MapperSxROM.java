package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperSxROM extends Mapper {

    private final Runnable m_mirroringCallback;
    private NameTableMirroring m_mirroring;
    private final boolean m_usesCharacterRAM;
    private byte[] m_characterRAM;

    private int m_modeCHR = 0;
    private int m_modePRG = 3;
    private int m_tempRegister = 0;
    private int m_writeCounter = 0;
    private int m_regPRG = 0;
    private int m_regCHR0 = 0;
    private int m_regCHR1 = 0;

    private int m_firstBankPRG;
    private int m_secondBankPRG;
    private int m_firstBankCHRIdx;
    private int m_secondBankCHRIdx;

    public MapperSxROM(Cartridge cart, Runnable mirroring_cb) {
        super(cart, Mapper.Type.SxROM);
        this.m_mirroringCallback = mirroring_cb;
        this.m_mirroring = NameTableMirroring.Horizontal;

        if (cart.getVROM() == null || cart.getVROM().length == 0) {
            m_usesCharacterRAM = true;
            m_characterRAM = new byte[0x8000];
            System.out.println("Uses character RAM");
        } else {
            System.out.println("Using CHR-ROM");
            m_usesCharacterRAM = false;
            m_firstBankCHRIdx = 0;
            m_secondBankCHRIdx = 0x1000 * m_regCHR1; // Should be 0 initially
        }

        m_firstBankPRG = 0;
        m_secondBankPRG = cart.getROM().length - 0x4000;
    }

    @Override
    public byte readPRG(int addr) {
        byte[] rom = m_cartridge.getROM();
        if (addr < 0xc000) {
            return rom[m_firstBankPRG + (addr & 0x3fff)];
        } else {
            return rom[m_secondBankPRG + (addr & 0x3fff)];
        }
    }

    @Override
    public NameTableMirroring getNameTableMirroring() {
        return m_mirroring;
    }

    @Override
    public void writePRG(int addr, byte value) {
        int val = value & 0xFF;

        if ((val & 0x80) == 0) { // If reset bit NOT set
            m_tempRegister = (m_tempRegister >> 1) | ((val & 1) << 4);
            ++m_writeCounter;

            if (m_writeCounter == 5) {
                if (addr <= 0x9fff) {
                    switch (m_tempRegister & 0x3) {
                        case 0: m_mirroring = NameTableMirroring.OneScreenLower; break;
                        case 1: m_mirroring = NameTableMirroring.OneScreenHigher; break;
                        case 2: m_mirroring = NameTableMirroring.Vertical; break;
                        case 3: m_mirroring = NameTableMirroring.Horizontal; break;
                    }
                    if (m_mirroringCallback != null) m_mirroringCallback.run();

                    m_modeCHR = (m_tempRegister & 0x10) >> 4;
                    m_modePRG = (m_tempRegister & 0xc) >> 2;
                    calculatePRGPointers();

                    if (m_modeCHR == 0) {
                        m_firstBankCHRIdx = 0x1000 * (m_regCHR0 & ~1); // Ignore last bit
                        m_secondBankCHRIdx = m_firstBankCHRIdx + 0x1000;
                    } else {
                        m_firstBankCHRIdx = 0x1000 * m_regCHR0;
                        m_secondBankCHRIdx = 0x1000 * m_regCHR1;
                    }
                } else if (addr <= 0xbfff) {
                    m_regCHR0 = m_tempRegister;
                    m_firstBankCHRIdx = 0x1000 * (m_tempRegister | (1 - m_modeCHR));
                    if (m_modeCHR == 0) {
                        m_firstBankCHRIdx = 0x1000 * (m_tempRegister & ~1);
                        m_secondBankCHRIdx = m_firstBankCHRIdx + 0x1000;
                    }
                } else if (addr <= 0xdfff) {
                    m_regCHR1 = m_tempRegister;
                    if (m_modeCHR == 1) {
                        m_secondBankCHRIdx = 0x1000 * m_tempRegister;
                    }
                } else {
                    if ((m_tempRegister & 0x10) == 0x10) {
                        System.out.println("PRG-RAM activated");
                    }
                    m_tempRegister &= 0xf;
                    m_regPRG = m_tempRegister;
                    calculatePRGPointers();
                }

                m_tempRegister = 0;
                m_writeCounter = 0;
            }
        } else {
            m_tempRegister = 0;
            m_writeCounter = 0;
            m_modePRG = 3;
            calculatePRGPointers();
        }
    }

    private void calculatePRGPointers() {
        if (m_modePRG <= 1) { // 32KB
            m_firstBankPRG = 0x4000 * (m_regPRG & ~1);
            m_secondBankPRG = m_firstBankPRG + 0x4000;
        } else if (m_modePRG == 2) { // Fix first, switch second
            m_firstBankPRG = 0;
            m_secondBankPRG = 0x4000 * m_regPRG;
        } else { // Switch first, fix second
            m_firstBankPRG = 0x4000 * m_regPRG;
            m_secondBankPRG = m_cartridge.getROM().length - 0x4000;
        }
    }

    @Override
    public byte readCHR(int addr) {
        if (m_usesCharacterRAM) {
            if (addr < 0x1000) return m_characterRAM[m_firstBankCHRIdx + addr];
            else return m_characterRAM[m_secondBankCHRIdx + (addr & 0xfff)];
        } else {
            byte[] vrom = m_cartridge.getVROM();
            if (addr < 0x1000) return vrom[m_firstBankCHRIdx + addr];
            else return vrom[m_secondBankCHRIdx + (addr & 0xfff)];
        }
    }

    @Override
    public void writeCHR(int addr, byte value) {
        if (m_usesCharacterRAM) {
            if (addr < 0x1000) m_characterRAM[m_firstBankCHRIdx + addr] = value;
            else m_characterRAM[m_secondBankCHRIdx + (addr & 0xfff)] = value;
        }
    }
}
