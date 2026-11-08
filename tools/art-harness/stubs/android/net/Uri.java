package android.net;

/** Path-only stand-in for android.net.Uri (Dp.buildApk names extra dex files with fromFile/parse + getLastPathSegment). */
public class Uri {
    private final String s;

    private Uri(String s) { this.s = s; }

    public static Uri fromFile(java.io.File f) { return new Uri("file://" + f.getAbsolutePath()); }
    public static Uri parse(String s) { return new Uri(s); }

    public String getPath() {
        String p = s;
        int i = p.indexOf("://");
        if (i >= 0) { p = p.substring(i + 3); int j = p.indexOf('/'); p = j >= 0 ? p.substring(j) : ""; }
        int q = p.indexOf('?'); if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#'); if (h >= 0) p = p.substring(0, h);
        return p;
    }

    public String getLastPathSegment() {
        String p = getPath();
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int i = p.lastIndexOf('/');
        return p.isEmpty() ? null : p.substring(i + 1);
    }

    public String getScheme() { int i = s.indexOf(':'); return i > 0 ? s.substring(0, i) : null; }

    public String getHost() {
        int i = s.indexOf("://");
        if (i < 0) return null;
        String rest = s.substring(i + 3);
        int at = rest.indexOf('@'); if (at >= 0) rest = rest.substring(at + 1);
        int end = rest.length();
        for (int k = 0; k < rest.length(); k++) {
            char c = rest.charAt(k);
            if (c == '/' || c == ':' || c == '?' || c == '#') { end = k; break; }
        }
        String host = rest.substring(0, end);
        return host.isEmpty() ? null : host;
    }

    @Override public String toString() { return s; }
}
