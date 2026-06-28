package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperUxROM extends Mapper {

    private final boolean m_usesCharacterRAM;
    private final int m_lastBankPtr; // Index in ROM
    private int m_selectPRG;
    private byte[] m_characterRAM;

    public MapperUxROM(Cartridge cart) {
        super(cart, Mapper.Type.UxROM);
        this.m_selectPRG = 0;

        if (cart.getVROM() == null || cart.getVROM().length == 0) {
            m_usesCharacterRAM = true;
            m_characterRAM = new byte[0x2000];
            System.out.println("Uses character RAM");
        } else {
            m_usesCharacterRAM = false;
        }

        m_lastBankPtr = cart.getROM().length - 0x4000;
    }

    @Override
    public byte readPRG(int addr) {
        byte[] rom = m_cartridge.getROM();
        if (addr < 0xc000) {
            int bankOffset = m_selectPRG << 14; // 16KB * select
            return rom[bankOffset | ((addr - 0x8000) & 0x3fff)];
        } else {
            return rom[m_lastBankPtr + (addr & 0x3fff)];
        }
    }

    @Override
    public void writePRG(int addr, byte value) {
        m_selectPRG = value & 0xFF;
    }

    @Override
    public byte readCHR(int addr) {
        if (m_usesCharacterRAM) {
            return m_characterRAM[addr];
        } else {
            return m_cartridge.getVROM()[addr];
        }
    }

    @Override
    public void writeCHR(int addr, byte value) {
        if (m_usesCharacterRAM) {
            m_characterRAM[addr] = value;
        }
    }
}
