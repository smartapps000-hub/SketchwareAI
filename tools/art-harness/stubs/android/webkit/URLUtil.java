package android.webkit;

/** Harness stand-in (v1.9.5): the URL's last path segment, like Android's URLUtil.guessFileName without a header. */
public final class URLUtil {
    private URLUtil() {
    }

    public static String guessFileName(String url, String contentDisposition, String mimeType) {
        String path = url == null ? "" : url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? "downloadfile.bin" : name;
    }
}
