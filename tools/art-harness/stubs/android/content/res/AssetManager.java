package android.content.res;
import java.io.*; import java.util.*; import java.util.zip.*;
/** Assets read from the APK named by -Dh.apk (the shipped APK itself). */
public final class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0, ACCESS_RANDOM = 1, ACCESS_STREAMING = 2, ACCESS_BUFFER = 3;
    private static ZipFile zip;
    private static synchronized ZipFile zip() throws IOException { if (zip == null) zip = new ZipFile(System.getProperty("h.apk")); return zip; }
    public InputStream open(String name) throws IOException { return open(name, ACCESS_STREAMING); }
    public InputStream open(String name, int mode) throws IOException {
        ZipEntry e = zip().getEntry("assets/" + name);
        if (e == null) throw new FileNotFoundException(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = zip().getInputStream(e)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); }
        return new ByteArrayInputStream(out.toByteArray()); // available() = whole asset, as on a phone
    }
    public String[] list(String path) throws IOException {
        String p = "assets/" + (path.isEmpty() ? "" : path + "/");
        TreeSet<String> s = new TreeSet<>();
        for (Enumeration<? extends ZipEntry> en = zip().entries(); en.hasMoreElements(); ) { String n = en.nextElement().getName(); if (n.startsWith(p) && n.length() > p.length()) { String r = n.substring(p.length()); int i = r.indexOf('/'); s.add(i < 0 ? r : r.substring(0, i)); } }
        return s.toArray(new String[0]);
    }
    public void close() {}
}
