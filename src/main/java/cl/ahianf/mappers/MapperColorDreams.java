package cl.ahianf.mappers;


import cl.ahianf.memory.Cartridge;

public class MapperColorDreams extends Mapper {

    private final NameTableMirroring m_mirroring;
    private final Runnable m_mirroringCallback;
    private int prgbank;
    private int chrbank;

    public MapperColorDreams(Cartridge cart, Runnable mirroring_cb) {
        super(cart, Mapper.Type.ColorDreams);
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
            prgbank = (val) & 0x3;
            chrbank = (val >> 4) & 0xF;
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
        // Do nothing
    }

    @Override
    public NameTableMirroring getNameTableMirroring() {
        return m_mirroring;
    }
}