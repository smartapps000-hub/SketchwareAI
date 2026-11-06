package android.webkit;

import java.util.HashMap;
import java.util.Locale;

/** Harness stand-in (v1.9.5, SketchWeb's download filter): a few entries of Android's extension table. */
public class MimeTypeMap {
    private static final MimeTypeMap INSTANCE = new MimeTypeMap();
    private static final HashMap<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put("mp3", "audio/mpeg");
        TYPES.put("mp4", "video/mp4");
        TYPES.put("pdf", "application/pdf");
        TYPES.put("apk", "application/vnd.android.package-archive");
        TYPES.put("zip", "application/zip");
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("txt", "text/plain");
    }

    public static MimeTypeMap getSingleton() {
        return INSTANCE;
    }

    public String getMimeTypeFromExtension(String extension) {
        return extension == null ? null : TYPES.get(extension.toLowerCase(Locale.US));
    }
}
