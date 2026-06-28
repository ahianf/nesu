package cl.ahianf.utils;

public class BitUtils {

    private BitUtils() {
    }

    public static int u16(int low, int high) {
        return (low & 0xFF) | ((high & 0xFF) << 8);
    }
}
