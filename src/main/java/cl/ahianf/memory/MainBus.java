package cl.ahianf.memory;


import cl.ahianf.input.Controller;
import cl.ahianf.mappers.Mapper;
import cl.ahianf.audio.APU;
import cl.ahianf.ppu.PPU;

import java.util.function.Consumer;


public class MainBus {

    // =============================================================
    // CONSTANTS (Memory Map)
    // =============================================================
    public static final int PPU_CTRL = 0x2000;
    public static final int PPU_MASK = 0x2001;
    public static final int PPU_STATUS = 0x2002;
    public static final int OAM_ADDR = 0x2003;
    public static final int OAM_DATA = 0x2004;
    public static final int PPU_SCROL = 0x2005;
    public static final int PPU_ADDR = 0x2006;
    public static final int PPU_DATA = 0x2007;

    public static final int APU_REGISTER_START = 0x4000;
    public static final int APU_REGISTER_END = 0x4013;
    public static final int OAM_DMA = 0x4014;
    public static final int APU_CONTROL_AND_STATUS = 0x4015;
    public static final int JOY1 = 0x4016;
    public static final int JOY2_AND_FRAME_CONTROL = 0x4017;

    // =============================================================
    // MEMBERS
    // =============================================================
    private final byte[] m_RAM; // 2KB internal RAM
    private byte[] m_extRAM; // Cartridge RAM

    // Callbacks and Device References
    private final Consumer<Byte> m_dmaCallback;
    private Mapper m_mapper;
    private final PPU m_ppu;
    private final APU m_apu;
    private final Controller m_controller1;
    private final Controller m_controller2;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public MainBus(PPU ppu, APU apu, Controller ctrl1, Controller ctrl2, Consumer<Byte> dma) {
        this.m_ppu = ppu;
        this.m_apu = apu;
        this.m_controller1 = ctrl1;
        this.m_controller2 = ctrl2;
        this.m_dmaCallback = dma;

        this.m_RAM = new byte[0x800]; // 2048 bytes
        // m_extRAM initialized only if needed by mapper
    }

    // =============================================================
    // METHODS
    // =============================================================

    private int normalize_mirror(int addr) {
        // 0x2008 - 0x3fff are mirrors of 0x2000 - 0x2007
        if (addr >= 0x2008 && addr < APU_REGISTER_START) {
            return addr & 0x2007;
        }
        return addr;
    }

    public byte read(int addr) {
        // Handle java signed int/short issues by masking address if it comes in purely
        addr &= 0xFFFF;

        if (addr < 0x2000) {
            return m_RAM[addr & 0x7ff];
        } else if (addr < 0x4020) {
            addr = normalize_mirror(addr);
            switch (addr) {
                case PPU_STATUS:
                    return m_ppu.getStatus();
                case PPU_DATA:
                    return m_ppu.getData();
                case JOY1:
                    return m_controller1.read();
                case JOY2_AND_FRAME_CONTROL:
                    return m_controller2.read();
                case OAM_DATA:
                    return m_ppu.getOAMData();
                case APU_CONTROL_AND_STATUS:
                    return m_apu.readStatus();
                default:
                    // LOG(InfoVerbose) << "Read access attempt at: " << std::hex << +addr << std::endl;
                    return 0;
            }
        } else if (addr < 0x6000) {
            // LOG(InfoVerbose) << "Expansion ROM read attempted. This is currently unsupported" << std::endl;
            return 0;
        } else if (addr < 0x8000) {
            if (m_mapper != null && m_mapper.handlesPRGRAM()) {
                return m_mapper.readPRG(addr);
            }
            if (m_mapper != null && m_mapper.hasExtendedRAM() && m_extRAM != null) {
                return m_extRAM[addr - 0x6000];
            }
            return 0;
        } else {
            if (m_mapper != null) return m_mapper.readPRG(addr);
            return 0;
        }
    }

    public void write(int addr, byte value) {
        addr &= 0xFFFF;

        if (addr < 0x2000) {
            m_RAM[addr & 0x7ff] = value;
        } else if (addr < 0x4020) {
            addr = normalize_mirror(addr);
            switch (addr) {
                case PPU_CTRL:
                    m_ppu.control(value);
                    break;
                case PPU_MASK:
                    m_ppu.setMask(value);
                    break;
                case OAM_ADDR:
                    m_ppu.setOAMAddress(value);
                    break;
                case OAM_DATA:
                    m_ppu.setOAMData(value);
                    break;
                case PPU_ADDR:
                    m_ppu.setDataAddress(value);
                    break;
                case PPU_SCROL:
                    m_ppu.setScroll(value);
                    break;
                case PPU_DATA:
                    m_ppu.setData(value);
                    break;
                case OAM_DMA:
                    m_dmaCallback.accept(value);
                    break;
                case JOY1:
                    m_controller1.strobe(value);
                    m_controller2.strobe(value);
                    break;
                case APU_CONTROL_AND_STATUS:
                case JOY2_AND_FRAME_CONTROL:
                    m_apu.writeRegister(addr, value);
                    break;
                default:
                    if (addr >= APU_REGISTER_START && addr <= APU_REGISTER_END) {
                        m_apu.writeRegister(addr, value);
                    } else {
                        // LOG(InfoVerbose) ...
                    }
                    break;
            }
        } else if (addr < 0x6000) {
            // Expansion ROM access
        } else if (addr < 0x8000) {
            if (m_mapper != null && m_mapper.handlesPRGRAM()) {
                m_mapper.writePRG(addr, value);
                return;
            }
            if (m_mapper != null && m_mapper.hasExtendedRAM() && m_extRAM != null) {
                m_extRAM[addr - 0x6000] = value;
            }
        } else {
            if (m_mapper != null) m_mapper.writePRG(addr, value);
        }
    }

    public void copyPage(byte page, byte[] dest) {
        if (dest.length < 256) {
            throw new IllegalArgumentException("DMA destination must fit one CPU page");
        }

        int addr = (page & 0xFF) << 8;

        if (addr < 0x2000) {
            System.arraycopy(m_RAM, addr & 0x7ff, dest, 0, 256);
            return;
        } else if (addr < 0x8000) {
            if (m_mapper != null && !m_mapper.handlesPRGRAM() && m_mapper.hasExtendedRAM() && m_extRAM != null) {
                System.arraycopy(m_extRAM, addr - 0x6000, dest, 0, 256);
                return;
            }
        }

        for (int i = 0; i < 256; i++) {
            dest[i] = read(addr + i);
        }
    }

    public boolean setMapper(Mapper mapper) {
        this.m_mapper = mapper;
        if (mapper == null) {
            System.out.println("[ERROR] Mapper pointer is nullptr");
            return false;
        }
        if (mapper.hasExtendedRAM()) {
            this.m_extRAM = new byte[0x2000];
        }
        return true;
    }
}
