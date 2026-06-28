package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperCNROM extends Mapper {

    private final boolean m_oneBank;
    private int m_selectCHR;

    public MapperCNROM(Cartridge cart) {
        super(cart, Mapper.Type.CNROM);

        // 0x4000 = 16KB (1 PRG Bank)
        if (cart.getROM().length == 0x4000) {
            m_oneBank = true;
        } else {
            m_oneBank = false;
        }
    }

    @Override
    public byte readPRG(int addr) {
        if (!m_oneBank) {
            return m_cartridge.getROM()[addr - 0x8000];
        } else {
            // Mirrored
            return m_cartridge.getROM()[(addr - 0x8000) & 0x3fff];
        }
    }

    @Override
    public void writePRG(int addr, byte value) {
        m_selectCHR = value & 0xFF & 0x3;
    }

    @Override
    public byte readCHR(int addr) {
        // VROM access with bank selection
        int index = addr | (m_selectCHR << 13);
        byte[] vrom = m_cartridge.getVROM();
        if (index < vrom.length) {
            return vrom[index];
        }
        return 0;
    }

    @Override
    public void writeCHR(int addr, byte value) {
        // CNROM uses CHR ROM; writes are ignored.
    }
}
