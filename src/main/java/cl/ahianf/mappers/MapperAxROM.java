package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperAxROM extends Mapper {

    private NameTableMirroring m_mirroring;
    private final Runnable m_mirroringCallback;
    private int m_prgBank;
    private byte[] m_characterRAM;

    public MapperAxROM(Cartridge cart, Runnable mirroring_cb) {
        super(cart, Mapper.Type.AxROM);
        this.m_mirroring = NameTableMirroring.OneScreenLower;
        this.m_mirroringCallback = mirroring_cb;
        this.m_prgBank = 0;

        if (cart.getROM().length >= 0x8000) {
            System.out.println("Using PRG-ROM OK");
        }

        // C++: cart.getVROM().size() == 0
        // Check if VROM is empty
        if (cart.getVROM() == null || cart.getVROM().length == 0) {
            m_characterRAM = new byte[0x2000];
            System.out.println("Uses Character RAM OK");
        }
    }

    @Override
    public byte readPRG(int addr) {
        if (addr >= 0x8000) {
            int index = (m_prgBank * 0x8000) + (addr & 0x7FFF);
            // Safety check for array bounds
            byte[] rom = m_cartridge.getROM();
            if (index < rom.length) {
                return rom[index];
            }
        }
        return 0;
    }

    @Override
    public void writePRG(int addr, byte value) {
        if (addr >= 0x8000) {
            int val = value & 0xFF;
            m_prgBank = val & 0x07;
            m_mirroring = ((val & 0x10) != 0) ? NameTableMirroring.OneScreenHigher : NameTableMirroring.OneScreenLower;
            if (m_mirroringCallback != null) {
                m_mirroringCallback.run();
            }
        }
    }

    @Override
    public byte readCHR(int addr) {
        if (addr < 0x2000) {
            // If m_characterRAM is active (AxROM uses CHR RAM if no CHR ROM present)
            if (m_characterRAM != null) {
                return m_characterRAM[addr];
            } else {
                // Return from VROM if it exists (though AxROM usually implies RAM)
                byte[] vrom = m_cartridge.getVROM();
                if (vrom != null && addr < vrom.length) {
                    return vrom[addr];
                }
            }
        }
        return 0;
    }

    @Override
    public void writeCHR(int addr, byte value) {
        if (addr < 0x2000 && m_characterRAM != null) {
            m_characterRAM[addr] = value;
        }
    }

    @Override
    public NameTableMirroring getNameTableMirroring() {
        return m_mirroring;
    }
}
