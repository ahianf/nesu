package cl.ahianf.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Cartridge {

    // --- Constants (mirror C++ enums / magic numbers) ---
    public static final byte MIRROR_HORIZONTAL = 0;
    public static final byte MIRROR_VERTICAL = 1;
    public static final byte MIRROR_FOUR_SCREEN = 2;

    // --- Fields (1:1 with C++) ---
    private byte[] mPrgRom;
    private byte[] mChrRom;

    private byte nameTableMirroring;
    private byte mapperNumber;
    private boolean extendedRam;
    private boolean chrRam;

    public Cartridge() {
        nameTableMirroring = 0;
        mapperNumber = 0;
        extendedRam = false;
        chrRam = false;
    }

    public boolean loadFromFile(String path) {
        byte[] romFile;

        try {
            romFile = Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            System.out.println("Could not open ROM file from path: " + path);
            return false;
        }

        System.out.println("Reading ROM from path: " + path);

        // --- Header ---
        if (romFile.length < 0x10) {
            System.out.println("Reading iNES header failed.");
            return false;
        }

        byte[] header = Arrays.copyOfRange(romFile, 0, 0x10);

        if (!new String(header, 0, 4, StandardCharsets.US_ASCII).equals("NES\u001A")) {
            System.out.println("Not a valid iNES image. Magic number: "
                               + Integer.toHexString(header[0] & 0xFF) + " "
                               + Integer.toHexString(header[1] & 0xFF) + " "
                               + Integer.toHexString(header[2] & 0xFF) + " "
                               + Integer.toHexString(header[3] & 0xFF));
            return false;
        }

        System.out.println("Reading header, it dictates:");

        int prgBanks = header[4] & 0xFF;
        System.out.println("16KB PRG-ROM Banks: " + prgBanks);
        if (prgBanks == 0) {
            System.out.println("ROM has no PRG-ROM banks. Loading ROM failed.");
            return false;
        }

        int chrBanks = header[5] & 0xFF;
        System.out.println("8KB CHR-ROM Banks: " + chrBanks);

        // --- Name table mirroring ---
        if ((header[6] & 0x08) != 0) {
            nameTableMirroring = MIRROR_FOUR_SCREEN;
            System.out.println("Name Table Mirroring: FourScreen");
        } else {
            nameTableMirroring = (byte) (header[6] & 0x01);
            System.out.println(
                    "Name Table Mirroring: " +
                    (nameTableMirroring == MIRROR_HORIZONTAL ? "Horizontal" : "Vertical")
            );
        }

        // --- Mapper number ---
        mapperNumber = (byte) (((header[6] >> 4) & 0x0F) | (header[7] & 0xF0));
        System.out.println("Mapper #: " + (mapperNumber & 0xFF));

        // --- Extended RAM ---
        extendedRam = (header[6] & 0x02) != 0;
        System.out.println("Extended (CPU) RAM: " + extendedRam);

        // --- Trainer ---
        if ((header[6] & 0x04) != 0) {
            System.out.println("Trainer is not supported.");
            return false;
        }

        // --- PAL / NTSC ---
        if (((header[10] & 0x03) == 0x02) || ((header[10] & 0x01) != 0)) {
            System.out.println("PAL ROM not supported.");
            return false;
        } else {
            System.out.println("ROM is NTSC compatible.");
        }

        // --- PRG-ROM ---
        int prgSize = 0x4000 * prgBanks;
        int offset = 0x10;

        if (romFile.length < offset + prgSize) {
            System.out.println("Reading PRG-ROM from image file failed.");
            return false;
        }

        mPrgRom = Arrays.copyOfRange(romFile, offset, offset + prgSize);
        offset += prgSize;

        // --- CHR-ROM / CHR-RAM ---
        if (chrBanks > 0) {
            int chrSize = 0x2000 * chrBanks;
            if (romFile.length < offset + chrSize) {
                System.out.println("Reading CHR-ROM from image file failed.");
                return false;
            }
            mChrRom = Arrays.copyOfRange(romFile, offset, offset + chrSize);
        } else {
            chrRam = true;
            System.out.println("Cartridge with CHR-RAM.");
        }

        return true;
    }

    // --- Accessors (match C++) ---

    public byte[] getROM() {
        return mPrgRom;
    }

    public byte[] getVROM() {
        return mChrRom;
    }

    public byte getMapper() {
        return mapperNumber;
    }

    public byte getNameTableMirroring() {
        return nameTableMirroring;
    }

    public boolean isExtendedRam() {
        return extendedRam;
    }

    // --- Test harness ---
    public static void main(String[] args) {
        var c = new Cartridge();
        c.loadFromFile("/Users/ahian/Downloads/smb.nes");
    }
}
