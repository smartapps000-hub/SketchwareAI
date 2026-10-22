package android.util;

/** Framework stand-in (v1.8.4): kellinwood's ZipSigner finds android.util.Base64 by reflection to sign. */
public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;

    public static byte[] encode(byte[] input, int flags) {
        java.util.Base64.Encoder e = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder()
                : (flags & NO_WRAP) != 0 ? java.util.Base64.getEncoder()
                : java.util.Base64.getMimeEncoder(76, (flags & CRLF) != 0 ? new byte[]{'\r', '\n'} : new byte[]{'\n'});
        if ((flags & NO_PADDING) != 0) e = e.withoutPadding();
        byte[] out = e.encode(input);
        if ((flags & NO_WRAP) == 0 && (flags & URL_SAFE) == 0) {
            byte[] withNewline = java.util.Arrays.copyOf(out, out.length + 1);
            withNewline[out.length] = '\n';
            return withNewline;
        }
        return out;
    }

    public static String encodeToString(byte[] input, int flags) {
        return new String(encode(input, flags), java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static byte[] decode(String str, int flags) {
        return java.util.Base64.getMimeDecoder().decode(str);
    }

    public static byte[] decode(byte[] input, int flags) {
        return java.util.Base64.getMimeDecoder().decode(input);
    }
}
