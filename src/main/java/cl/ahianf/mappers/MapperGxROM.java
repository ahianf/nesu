package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperGxROM extends Mapper {

    private NameTableMirroring m_mirroring;
    private final Runnable m_mirroringCallback;
    private int prgbank;
    private int chrbank;

    public MapperGxROM(Cartridge cart, Runnable mirroring_cb) {
        super(cart, Mapper.Type.GxROM);
        this.m_mirroring = NameTableMirroring.Vertical;
        this.m_mirroringCallback = mirroring_cb;
    }

    @Override
    public byte readPRG(int addr) {
        if (addr >= 0x8000) {
            int index = (prgbank * 0x8000) + (addr & 0x7fff);
            return m_cartridge.getROM()[index];
        }
        return 0;
    }

    @Override
    public void writePRG(int addr, byte value) {
        if (addr >= 0x8000) {
            int val = value & 0xFF;
            prgbank = (val & 0x30) >> 4;
            chrbank = (val & 0x3);
            m_mirroring = NameTableMirroring.Vertical;
            if (m_mirroringCallback != null) m_mirroringCallback.run();
        }
    }

    @Override
    public byte readCHR(int addr) {
        if (addr <= 0x1FFF) {
            int index = (chrbank * 0x2000) + addr;
            return m_cartridge.getVROM()[index];
        }
        return 0;
    }

    @Override
    public void writeCHR(int addr, byte value) {
        // GxROM uses CHR ROM; writes are ignored.
    }

    @Override
    public NameTableMirroring getNameTableMirroring() {
        return m_mirroring;
    }
}
