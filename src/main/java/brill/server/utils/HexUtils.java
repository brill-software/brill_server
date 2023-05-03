package brill.server.utils;

import java.nio.charset.StandardCharsets;

public class HexUtils {
    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    /**
     * Converts an array of bytes to a hex string.
     * 
     * @param bytes Bytes to convert.
     * @return Hex character string.
     */
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * Converts a hex string to an array of bytes.
     * 
     * @param hex Hex string to convert.
     * @return Byte array.
     */
    public static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int index = i * 2;
            int val = Integer.parseInt(hex.substring(index, index + 2), 16);
            result[i] = (byte)val;
        }
        return result;
    }
}