package cl.ahianf.ppu;

import cl.ahianf.mappers.Mapper;

import java.util.Arrays;
import static cl.ahianf.ppu.Palette.COLORS;

public class PPU {

    // =============================================================
    // CONSTANTS
    // =============================================================
    public static final int ScanlineCycleLength = 341;
    public static final int ScanlineEndCycle = 340;
    public static final int VisibleScanlines = 240;
    public static final int ScanlineVisibleDots = 256;
    public static final int FrameEndScanline = 261;

    private enum State {
        PreRender,
        Render,
        PostRender,
        VerticalBlank
    }

    private enum CharacterPage {
        Low,
        High
    }

    // =============================================================
    // MEMBERS
    // =============================================================

    private final VirtualScreen m_screen;
    private Runnable m_vblankCallback;

    // OAM (Object Attribute Memory)
    private final byte[] m_spriteMemory;
    private final int[] m_scanlineSprites;
    private final int[] m_scanlineSpriteX;
    private final int[] m_scanlineSpriteP0;
    private final int[] m_scanlineSpriteP1;
    private final int[] m_scanlineSpriteAttribute;
    private int m_scanlineSpritesCount;

    // Screen Buffer
    private final int[] m_pictureBuffer;

    // Registers & Internal State
    private State m_pipelineState;
    private int m_cycle;
    private int m_scanline;
    private boolean m_evenFrame;
    private boolean m_vblank;
    private boolean m_sprZeroHit;
    private boolean m_spriteOverflow;

    // Loopy's VRAM Registers
    private int m_dataAddress;
    private int m_tempAddress;
    private int m_fineXScroll;
    private boolean m_firstWrite;
    private int m_dataBuffer;
    private int m_openBus;
    private int m_spriteDataAddress;

    // Control Flags
    private boolean m_longSprites;
    private boolean m_generateInterrupt;
    private boolean m_greyscaleMode;
    private boolean m_showSprites;
    private boolean m_showBackground;
    private boolean m_hideEdgeSprites;
    private boolean m_hideEdgeBackground;

    private CharacterPage m_bgPage;
    private CharacterPage m_sprPage;
    private int m_dataAddrIncrement;

    // --- OPTIMIZATION CACHE ---
    // Used to prevent re-fetching VRAM data for every pixel in a tile
    private int m_bgCacheAddress = -1;
    private int m_bgCacheP0;
    private int m_bgCacheP1;
    private int m_bgCacheAttribute;

    private int NameTable0, NameTable1, NameTable2, NameTable3;
    private final byte[] m_palette;
    private final byte[] m_RAM; // Video RAM (2KB usually)
    private Mapper m_mapper;


    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public PPU(VirtualScreen screen) {
        this.m_screen = screen;

        this.m_spriteMemory = new byte[64 * 4];
        this.m_scanlineSprites = new int[8];
        this.m_scanlineSpriteX = new int[8];
        this.m_scanlineSpriteP0 = new int[8];
        this.m_scanlineSpriteP1 = new int[8];
        this.m_scanlineSpriteAttribute = new int[8];
        this.m_pictureBuffer = new int[VisibleScanlines * 256];
        Arrays.fill(this.m_pictureBuffer, 0xFF00FFFF);

        m_palette = new byte[0x20]; // 32 bytes for palette
        m_RAM = new byte[0x800];    // 2KB internal VRAM
        m_mapper = null;

        reset();
    }

    public void reset() {
        m_longSprites = m_generateInterrupt = m_greyscaleMode = m_vblank = m_spriteOverflow = false;
        m_showBackground = m_showSprites = m_evenFrame = m_firstWrite = true;
        m_bgPage = m_sprPage = CharacterPage.Low;
        m_dataAddress = m_cycle = m_scanline = m_spriteDataAddress = m_fineXScroll = m_tempAddress = m_openBus = 0;
        m_dataAddrIncrement = 1;
        m_pipelineState = State.PreRender;
        m_scanlineSpritesCount = 0;

        // Reset cache
        m_bgCacheAddress = -1;
    }

    public void setInterruptCallback(Runnable cb) {
        this.m_vblankCallback = cb;
    }

    // =============================================================
    // MAIN LOOP (STEP)
    // =============================================================
    public void step() {
        switch (m_pipelineState) {
            case PreRender:
                if (m_cycle == 1) {
                    m_vblank = m_sprZeroHit = m_spriteOverflow = false;
                    m_bgCacheAddress = -1; // Invalidate cache
                } else if (m_cycle == ScanlineVisibleDots + 2 && m_showBackground && m_showSprites) {
                    m_dataAddress &= ~0x41f;
                    m_dataAddress |= (m_tempAddress & 0x41f);
                } else if (m_cycle > 280 && m_cycle <= 304 && m_showBackground && m_showSprites) {
                    m_dataAddress &= ~0x7be0;
                    m_dataAddress |= (m_tempAddress & 0x7be0);
                }

                if (m_cycle >= ScanlineEndCycle - ((!m_evenFrame && m_showBackground && m_showSprites) ? 1 : 0)) {
                    m_pipelineState = State.Render;
                    m_cycle = m_scanline = 0;
                    evaluateSpritesForScanline(0);
                }

                // MMC3 IRQ on PreRender line
                if (m_cycle == 260 && m_showBackground && m_showSprites) {
                    scanlineIRQ();
                }
                break;

            case Render:
                // Optimization: Localize loop logic
                if (m_cycle > 0 && m_cycle <= ScanlineVisibleDots) {
                    int x = m_cycle - 1;
                    int y = m_scanline;

                    int bgColor = 0;
                    int sprColor = 0;
                    boolean bgOpaque = false;
                    boolean sprOpaque = false;
                    boolean spriteForeground = false;

                    // Cache fields locally to avoid 'this.' overhead in hot path
                    boolean showBg = m_showBackground;
                    boolean showSpr = m_showSprites;

                    // --- Background Rendering ---
                    if (showBg) {
                        int x_fine = (m_fineXScroll + x) & 7; // Optimization: % 8 -> & 7

                        if (!m_hideEdgeBackground || x >= 8) {
                            // OPTIMIZATION: Check Cache
                            // The VRAM address (m_dataAddress) is constant for 8 pixels (one tile).
                            // We only fetch from the Bus if the address changed.
                            if (m_dataAddress != m_bgCacheAddress) {
                                m_bgCacheAddress = m_dataAddress;

                                // 1. Fetch Tile
                                int addr = 0x2000 | (m_dataAddress & 0x0FFF);
                                int tile = read(addr) & 0xFF;

                                // 2. Fetch Pattern
                                addr = (tile * 16) + ((m_dataAddress >> 12) & 0x7);
                                addr |= (m_bgPage == CharacterPage.High ? 1 : 0) << 12;
                                m_bgCacheP0 = read(addr) & 0xFF;
                                m_bgCacheP1 = read(addr + 8) & 0xFF;

                                // 3. Fetch Attribute
                                addr = 0x23C0 | (m_dataAddress & 0x0C00) | ((m_dataAddress >> 4) & 0x38) | ((m_dataAddress >> 2) & 0x07);
                                m_bgCacheAttribute = read(addr) & 0xFF;
                            }

                            // Use Cached Data
                            int shiftBit = 7 ^ x_fine;
                            int p0Pixel = (m_bgCacheP0 >> shiftBit) & 1;
                            int p1Pixel = (m_bgCacheP1 >> shiftBit) & 1;

                            bgColor = p0Pixel | (p1Pixel << 1);
                            bgOpaque = (bgColor != 0);

                            // Attribute Calculation
                            int shift = ((m_dataAddress >> 4) & 4) | (m_dataAddress & 2);
                            bgColor |= ((m_bgCacheAttribute >> shift) & 0x3) << 2;
                        }

                        // Coarse X Increment
                        if (x_fine == 7) {
                            if ((m_dataAddress & 0x001F) == 31) {
                                m_dataAddress &= ~0x001F;
                                m_dataAddress ^= 0x0400;
                            } else {
                                m_dataAddress += 1;
                            }
                            // Address changed, invalidate cache for next pixel
                            m_bgCacheAddress = -1;
                        }
                    }

                    // --- Sprite Rendering ---
                    if (showSpr && (!m_hideEdgeSprites || x >= 8)) {
                        for (int k = 0; k < m_scanlineSpritesCount; k++) {
                            int i = m_scanlineSprites[k];
                            int spr_x = m_scanlineSpriteX[k];

                            int diff = x - spr_x;
                            if (diff < 0 || diff >= 8) continue; // Optimization: fast fail

                            int attribute = m_scanlineSpriteAttribute[k];
                            int x_shift = diff & 7;
                            if ((attribute & 0x40) == 0) x_shift ^= 7; // No Flip H

                            sprColor = (m_scanlineSpriteP0[k] >> x_shift) & 1;
                            sprColor |= ((m_scanlineSpriteP1[k] >> x_shift) & 1) << 1;

                            if (sprColor == 0) continue; // Transparent

                            sprOpaque = true;
                            sprColor |= 0x10;
                            sprColor |= (attribute & 0x3) << 2;
                            spriteForeground = ((attribute & 0x20) == 0);

                            // Sprite-0 hit
                            if (!m_sprZeroHit && showBg && i == 0 && sprOpaque && bgOpaque && x != 255) {
                                m_sprZeroHit = true;
                            }
                            break; // Priority: highest sprite wins
                        }
                    }

                    // --- Pixel Selection ---
                    int paletteAddr = bgColor;
                    if ((!bgOpaque && sprOpaque) || (bgOpaque && sprOpaque && spriteForeground)) {
                        paletteAddr = sprColor;
                    } else if (!bgOpaque && !sprOpaque) {
                        paletteAddr = 0;
                    }

                    int colorIndex = readPalette(paletteAddr) & 0xFF;
                    // Write to buffer (no function call)
                    m_pictureBuffer[m_scanline * 256 + x] = COLORS[colorIndex];

                } else if (m_cycle == ScanlineVisibleDots + 1 && m_showBackground) {
                    if ((m_dataAddress & 0x7000) != 0x7000) {
                        m_dataAddress += 0x1000;
                    } else {
                        m_dataAddress &= ~0x7000;
                        int y = (m_dataAddress & 0x03E0) >> 5;
                        if (y == 29) {
                            y = 0;
                            m_dataAddress ^= 0x0800;
                        } else if (y == 31) {
                            y = 0;
                        } else {
                            y += 1;
                        }
                        m_dataAddress = (m_dataAddress & ~0x03E0) | (y << 5);
                    }
                } else if (m_cycle == ScanlineVisibleDots + 2 && m_showBackground && m_showSprites) {
                    m_dataAddress &= ~0x41f;
                    m_dataAddress |= (m_tempAddress & 0x41f);
                }

                // MMC3 IRQ Trigger
                if (m_cycle == 260 && m_showBackground && m_showSprites) {
                    scanlineIRQ();
                }

                // Sprite Evaluation (Next Scanline)
                if (m_cycle >= ScanlineEndCycle) {
                    evaluateSpritesForScanline(m_scanline + 1);
                    ++m_scanline;
                    m_cycle = 0;
                    m_bgCacheAddress = -1; // Invalidate background cache for new scanline
                }

                if (m_scanline >= VisibleScanlines) m_pipelineState = State.PostRender;
                break;

            case PostRender:
                if (m_cycle >= ScanlineEndCycle) {
                    ++m_scanline;
                    m_cycle = 0;
                    m_pipelineState = State.VerticalBlank;

                    // === OPTIMIZATION: BULK TRANSFER ===
                    // Replaces 61,440 individual setPixel calls with one bulk array copy.
                    // NOTE: You MUST add 'setFrameBuffer(int[] pixels)' to VirtualScreen.java
                    // as detailed in the optimization plan.
                    m_screen.setFrameBuffer(m_pictureBuffer);
                }
                break;

            case VerticalBlank:
                if (m_cycle == 1 && m_scanline == VisibleScanlines + 1) {
                    m_vblank = true;
                    if (m_generateInterrupt && m_vblankCallback != null) {
                        m_vblankCallback.run();
                    }
                }
                if (m_cycle >= ScanlineEndCycle) {
                    ++m_scanline;
                    m_cycle = 0;
                }
                if (m_scanline >= FrameEndScanline) {
                    m_pipelineState = State.PreRender;
                    m_scanline = 0;
                    m_evenFrame = !m_evenFrame;
                }
                break;
        }
        ++m_cycle;
    }

    // =============================================================
    // API METHODS
    // =============================================================

    private void evaluateSpritesForScanline(int scanline) {
        m_scanlineSpritesCount = 0;
        if (scanline >= VisibleScanlines) {
            return;
        }

        int range = m_longSprites ? 16 : 8;

        for (int spriteIndex = 0; spriteIndex < 64; ++spriteIndex) {
            int base = spriteIndex * 4;
            int top = (m_spriteMemory[base] & 0xFF) + 1;
            int yOffset = scanline - top;

            if (yOffset < 0 || yOffset >= range) {
                continue;
            }

            if (m_scanlineSpritesCount >= 8) {
                m_spriteOverflow = true;
                break;
            }

            int tile = m_spriteMemory[base + 1] & 0xFF;
            int attribute = m_spriteMemory[base + 2] & 0xFF;
            if ((attribute & 0x80) != 0) {
                yOffset ^= (range - 1);
            }

            int addr;
            if (!m_longSprites) {
                addr = tile * 16 + yOffset;
                if (m_sprPage == CharacterPage.High) addr += 0x1000;
            } else {
                yOffset = (yOffset & 7) | ((yOffset & 8) << 1);
                addr = (tile >> 1) * 32 + yOffset;
                addr |= (tile & 1) << 12;
            }

            int slot = m_scanlineSpritesCount++;
            m_scanlineSprites[slot] = spriteIndex;
            m_scanlineSpriteX[slot] = m_spriteMemory[base + 3] & 0xFF;
            m_scanlineSpriteP0[slot] = read(addr) & 0xFF;
            m_scanlineSpriteP1[slot] = read(addr + 8) & 0xFF;
            m_scanlineSpriteAttribute[slot] = attribute;
        }
    }

    public void doDMA(byte[] pageData) {
        int dstPos = (m_spriteDataAddress & 0xFF);
        for (int i = 0; i < 256; i++) {
            m_spriteMemory[(dstPos + i) & 0xFF] = pageData[i];
        }
    }

    public void control(byte ctrl) {
        int c = ctrl & 0xFF;
        m_openBus = c;
        m_generateInterrupt = (c & 0x80) != 0;
        m_longSprites = (c & 0x20) != 0;
        m_bgPage = ((c & 0x10) != 0) ? CharacterPage.High : CharacterPage.Low;
        m_sprPage = ((c & 0x8) != 0) ? CharacterPage.High : CharacterPage.Low;
        m_dataAddrIncrement = ((c & 0x4) != 0) ? 0x20 : 1;
        m_tempAddress &= ~0xc00;
        m_tempAddress |= (c & 0x3) << 10;
    }

    public void setMask(byte mask) {
        int m = mask & 0xFF;
        m_openBus = m;
        m_greyscaleMode = (m & 0x1) != 0;
        m_hideEdgeBackground = (m & 0x2) == 0;
        m_hideEdgeSprites = (m & 0x4) == 0;
        m_showBackground = (m & 0x8) != 0;
        m_showSprites = (m & 0x10) != 0;
    }

    public byte getStatus() {
        int status = (m_openBus & 0x1F) |
                     (m_spriteOverflow ? 1 : 0) << 5 |
                     (m_sprZeroHit ? 1 : 0) << 6 |
                     (m_vblank ? 1 : 0) << 7;
        m_vblank = false;
        m_firstWrite = true;
        m_openBus = status;
        return (byte) status;
    }

    public void setDataAddress(byte addr) {
        int val = addr & 0xFF;
        m_openBus = val;
        if (m_firstWrite) {
            m_tempAddress &= ~0xff00;
            m_tempAddress |= (val & 0x3f) << 8;
            m_firstWrite = false;
        } else {
            m_tempAddress &= ~0xff;
            m_tempAddress |= val;
            m_dataAddress = m_tempAddress;
            m_firstWrite = true;
        }
        m_bgCacheAddress = -1; // Invalidate on address change
    }

    public byte getData() {
        int addr = m_dataAddress;
        byte data = read(addr);
        m_dataAddress = (m_dataAddress + m_dataAddrIncrement) & 0xFFFF;
        if ((addr & 0x3FFF) < 0x3F00) {
            byte temp = data;
            data = (byte) m_dataBuffer;
            m_dataBuffer = temp & 0xFF;
        } else {
            m_dataBuffer = read(addr - 0x1000) & 0xFF;
        }
        m_openBus = data & 0xFF;
        m_bgCacheAddress = -1; // Invalidate
        return data;
    }

    public void setData(byte data) {
        m_openBus = data & 0xFF;
        write(m_dataAddress, data);
        m_dataAddress = (m_dataAddress + m_dataAddrIncrement) & 0xFFFF;
        m_bgCacheAddress = -1; // Invalidate
    }

    public void setOAMAddress(byte addr) {
        m_openBus = addr & 0xFF;
        m_spriteDataAddress = addr & 0xFF;
    }

    public byte getOAMData() {
        byte data = m_spriteMemory[m_spriteDataAddress];
        m_openBus = data & 0xFF;
        return data;
    }

    public void setOAMData(byte value) {
        m_openBus = value & 0xFF;
        m_spriteMemory[m_spriteDataAddress] = value;
        m_spriteDataAddress = (m_spriteDataAddress + 1) & 0xFF;
    }

    public void setScroll(byte scroll) {
        int val = scroll & 0xFF;
        m_openBus = val;
        if (m_firstWrite) {
            m_tempAddress &= ~0x1f;
            m_tempAddress |= (val >> 3) & 0x1f;
            m_fineXScroll = val & 0x7;
            m_firstWrite = false;
        } else {
            m_tempAddress &= ~0x73e0;
            m_tempAddress |= ((val & 0x7) << 12) | ((val & 0xf8) << 2);
            m_firstWrite = true;
        }
        m_bgCacheAddress = -1; // Invalidate
    }


    public byte read(int addr) {
        // PictureBus is limited to 0x3fff
        addr = addr & 0x3fff;

        if (addr < 0x2000) {
            return m_mapper.readCHR(addr);
        } else if (addr <= 0x3eff) {
            // Name tables up to 0x3000, then mirrored up to 0x3eff
            int normalizedAddr = addr;
            if (addr >= 0x3000) {
                normalizedAddr -= 0x1000;
            }

            // Calculate index offset within the specific Nametable (0-0x3FF)
            int index = normalizedAddr & 0x3ff;

            // If FourScreen mirroring (NameTable0 out of bounds means mapped elsewhere)
            if (NameTable0 >= m_RAM.length) {
                return m_mapper.readCHR(normalizedAddr);
            } else if (normalizedAddr < 0x2400) { // NT0
                return m_RAM[NameTable0 + index];
            } else if (normalizedAddr < 0x2800) { // NT1
                return m_RAM[NameTable1 + index];
            } else if (normalizedAddr < 0x2c00) { // NT2
                return m_RAM[NameTable2 + index];
            } else { // NT3
                return m_RAM[NameTable3 + index];
            }
        } else if (addr <= 0x3fff) {
            // Palette Memory
            int paletteAddr = addr & 0x1f;
            return readPalette(paletteAddr);
        }
        return 0;
    }

    public void write(int addr, byte value) {
        addr = addr & 0x3fff;

        if (addr < 0x2000) {
            m_mapper.writeCHR(addr, value);
        } else if (addr <= 0x3eff) {
            int normalizedAddr = addr;
            if (addr >= 0x3000) {
                normalizedAddr -= 0x1000;
            }

            int index = normalizedAddr & 0x3ff;

            if (NameTable0 >= m_RAM.length) {
                m_mapper.writeCHR(normalizedAddr, value);
            } else if (normalizedAddr < 0x2400) { // NT0
                m_RAM[NameTable0 + index] = value;
            } else if (normalizedAddr < 0x2800) { // NT1
                m_RAM[NameTable1 + index] = value;
            } else if (normalizedAddr < 0x2c00) { // NT2
                m_RAM[NameTable2 + index] = value;
            } else { // NT3
                m_RAM[NameTable3 + index] = value;
            }
        } else if (addr <= 0x3fff) {
            int palette = addr & 0x1f;
            m_palette[mirrorPaletteAddress(palette)] = (byte) (value & 0x3F);
        }
    }

    public byte readPalette(int paletteAddr) {
        int color = m_palette[mirrorPaletteAddress(paletteAddr)] & 0x3F;
        if (m_greyscaleMode) {
            color &= 0x30;
        }
        return (byte) color;
    }

    private int mirrorPaletteAddress(int paletteAddr) {
        paletteAddr &= 0x1F;
        if ((paletteAddr & 0x13) == 0x10) {
            paletteAddr &= 0x0F;
        }
        return paletteAddr;
    }

    // =============================================================
    // MIRRORING & MAPPER
    // =============================================================

    public void updateMirroring() {
        if (m_mapper == null) return;

        switch (m_mapper.getNameTableMirroring()) {
            case Horizontal:
                NameTable0 = NameTable1 = 0;
                NameTable2 = NameTable3 = 0x400;
                System.out.println("[VERBOSE] Horizontal Name Table mirroring set. (Vertical Scrolling)");
                break;
            case Vertical:
                NameTable0 = NameTable2 = 0;
                NameTable1 = NameTable3 = 0x400;
                System.out.println("[VERBOSE] Vertical Name Table mirroring set. (Horizontal Scrolling)");
                break;
            case OneScreenLower:
                NameTable0 = NameTable1 = NameTable2 = NameTable3 = 0;
                System.out.println("[VERBOSE] Single Screen mirroring set with lower bank.");
                break;
            case OneScreenHigher:
                NameTable0 = NameTable1 = NameTable2 = NameTable3 = 0x400;
                System.out.println("[VERBOSE] Single Screen mirroring set with higher bank.");
                break;
            case FourScreen:
                // Point out of bounds to indicate external handling
                NameTable0 = m_RAM.length;
                System.out.println("[VERBOSE] FourScreen mirroring.");
                break;
            default:
                NameTable0 = NameTable1 = NameTable2 = NameTable3 = 0;
                System.out.println("[ERROR] Unsupported Name Table mirroring : " + m_mapper.getNameTableMirroring());
        }
    }

    public boolean setMapper(Mapper mapper) {
        if (mapper == null) {
            System.out.println("[ERROR] Mapper argument is nullptr");
            return false;
        }

        m_mapper = mapper;
        updateMirroring();
        return true;
    }

    public void scanlineIRQ() {
        if (m_mapper != null) {
            m_mapper.scanlineIRQ();
        }
    }
}
