package cl.ahianf.mappers;


import cl.ahianf.utils.IRQHandle;
import cl.ahianf.memory.Cartridge;

public abstract class Mapper {

    // =============================================================
    // ENUMS (from Mapper.h)
    // =============================================================

    public enum NameTableMirroring {
        Horizontal(0),
        Vertical(1),
        FourScreen(8),
        OneScreenLower(9),  // Assigned explicit values for clarity
        OneScreenHigher(10);

        private final int value;

        NameTableMirroring(int value) {
            this.value = value;
        }
    }

    public enum Type {
        NROM(0),
        SxROM(1),
        UxROM(2),
        CNROM(3),
        MMC3(4),
        AxROM(7),
        ColorDreams(11),
        GxROM(66);

        private final int id;

        Type(int id) {
            this.id = id;
        }

        public static Type fromInt(int id) {
            for (Type t : values()) {
                if (t.id == id) return t;
            }
            return null;
        }
    }

    // =============================================================
    // MEMBERS & CONSTRUCTOR
    // =============================================================

    final protected Cartridge m_cartridge;
    final protected Type m_type;

    public Mapper(Cartridge cart, Type t) {
        this.m_cartridge = cart;
        this.m_type = t;
    }

    // =============================================================
    // ABSTRACT METHODS (Interface for Subclasses)
    // =============================================================

    /**
     * Write to Program ROM/RAM (CPU Memory Map)
     */
    public abstract void writePRG(int addr, byte value);

    /**
     * Read from Program ROM/RAM (CPU Memory Map)
     */
    public abstract byte readPRG(int addr);

    /**
     * Read from Character ROM/RAM (PPU Memory Map)
     */
    public abstract byte readCHR(int addr);

    /**
     * Write to Character ROM/RAM (PPU Memory Map)
     */
    public abstract void writeCHR(int addr, byte value);

    // =============================================================
    // COMMON METHODS
    // =============================================================

    public NameTableMirroring getNameTableMirroring() {
        // Cast int from cartridge to Enum
        int mirrorMode = m_cartridge.getNameTableMirroring();
        // Simple mapping logic (or use a helper in the Enum)
        if (mirrorMode == 0) return NameTableMirroring.Horizontal;
        if (mirrorMode == 1) return NameTableMirroring.Vertical;
        return NameTableMirroring.FourScreen; // Default fallback
    }

    public boolean hasExtendedRAM() {
        return m_cartridge.isExtendedRam();
    }

    public boolean handlesPRGRAM() {
        return false;
    }

    /**
     * Optional method for Scanline IRQs (used by MMC3)
     */
    public void scanlineIRQ() {
        // Default implementation does nothing
    }

    // =============================================================
    // FACTORY METHOD (from Mapper.cpp)
    // =============================================================

    /**
     * Creates the specific Mapper instance based on the Mapper Type ID.
     * * @param mapperT     The enum type of the mapper
     *
     * @param cart        The cartridge data
     * @param irq         Handle to trigger CPU interrupts
     * @param mirroringCb Callback to update PPU mirroring
     * @return A specific Mapper subclass or null if not supported
     */
    public static Mapper createMapper(Type mapperT,
                                      Cartridge cart,
                                      IRQHandle irq,
                                      Runnable mirroringCb) {

        if (mapperT == null) {
            return null;
        }

        switch (mapperT) {
            case NROM:
                return new MapperNROM(cart);
            case SxROM:
                return new MapperSxROM(cart, mirroringCb);
            case UxROM:
                return new MapperUxROM(cart);
            case CNROM:
                return new MapperCNROM(cart);
            case MMC3:
                return new MapperMMC3(cart, irq, mirroringCb);
            case AxROM:
                return new MapperAxROM(cart, mirroringCb);
            case ColorDreams:
                return new MapperColorDreams(cart, mirroringCb);
            case GxROM:
                return new MapperGxROM(cart, mirroringCb);
            default:
                // LOG(Error) << "Unsupported Mapper";
                return null;
        }
    }
}
