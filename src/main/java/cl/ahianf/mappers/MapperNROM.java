package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;


public class MapperNROM extends Mapper {

    private final boolean m_oneBank;
    private final boolean m_usesCharacterRAM;
    private byte[] m_characterRAM;

    public MapperNROM(Cartridge cart) {
        super(cart, Mapper.Type.NROM);

        if (cart.getROM().length == 0x4000) {
            m_oneBank = true;
        } else {
            m_oneBank = false;
        }

        if (cart.getVROM() == null || cart.getVROM().length == 0) {
            m_usesCharacterRAM = true;
            m_characterRAM = new byte[0x2000];
            System.out.println("Uses character RAM");
        } else {
            m_usesCharacterRAM = false;
        }
    }

    @Override
    public byte readPRG(int addr) {
        if (!m_oneBank) {
            return m_cartridge.getROM()[addr - 0x8000];
        } else {
            return m_cartridge.getROM()[(addr - 0x8000) & 0x3fff];
        }
    }

    @Override
    public void writePRG(int addr, byte value) {
        // NROM has no PRG registers; writes are ignored by the cartridge.
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
