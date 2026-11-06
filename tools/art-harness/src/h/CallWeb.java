package h;

import java.lang.reflect.Method;

/** v1.9.4: SketchWeb's MIME filter and desktop user agent from fixture 931's built APK on ART. WEBCALL n/n ok. */
public class CallWeb {
    static int checks, fails;

    static void check(String what, boolean ok, Object got) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got);
    }

    public static void main(String[] a) throws Exception {
        Class<?> web = Class.forName(a.length > 0 ? a[0] : "com.fixture.web.SketchWeb");
        Method mime = web.getMethod("mimeAllowed", String.class, String.class);
        String filter = "application/pdf, image/*";
        check("pdf passes", (Boolean) mime.invoke(null, "application/pdf", filter), true);
        check("PNG passes image/*", (Boolean) mime.invoke(null, "IMAGE/PNG", filter), true);
        check("parameters ignored", (Boolean) mime.invoke(null, "application/pdf; charset=binary", filter), true);
        check("zip filtered out", !(Boolean) mime.invoke(null, "application/zip", filter), false);
        check("null type filtered out", !(Boolean) mime.invoke(null, null, filter), false);
        check("empty filter allows all", (Boolean) mime.invoke(null, "application/zip", ""), true);
        check("*/* allows all", (Boolean) mime.invoke(null, "video/mp4", "*/*"), true);
        Method ua = web.getMethod("desktopUserAgent", String.class);
        String mobile = "Mozilla/5.0 (Linux; Android 15; Pixel 8 Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36";
        String desktop = (String) ua.invoke(null, mobile);
        check("desktop UA keeps Chrome's version, no Android / Mobile / wv", desktop.equals(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Safari/537.36"), desktop);
        // v1.9.5: file endings in the filter, every file for * / */*, names from Content-Disposition, MIME from the ending
        Method mime3 = web.getMethod("mimeAllowed", String.class, String.class, String.class);
        check(".mp3 ending passes the song", (Boolean) mime3.invoke(null, "application/octet-stream", "song.mp3", ".mp3"), true);
        check("mp3 without a dot passes by type", (Boolean) mime3.invoke(null, "audio/mpeg", "track", "mp3"), true);
        check(".mp3 filters an apk out", !(Boolean) mime3.invoke(null, "application/vnd.android.package-archive", "app.apk", ".mp3"), false);
        check("* allows an apk", (Boolean) mime3.invoke(null, "application/vnd.android.package-archive", "app.apk", "*"), true);
        check("mixed list .apk, application/pdf", (Boolean) mime3.invoke(null, "application/pdf", "a.pdf", ".apk, application/pdf")
                && (Boolean) mime3.invoke(null, "x", "b.APK", ".apk, application/pdf"), true);
        Method name = web.getMethod("fileName", String.class, String.class, String.class);
        String n1 = (String) name.invoke(null, "https://x.org/dl?id=7", "attachment; filename=\"Report 2026.pdf\"", "application/pdf");
        check("quoted filename", "Report 2026.pdf".equals(n1), n1);
        String n2 = (String) name.invoke(null, "https://x.org/dl", "attachment; filename=\"x.bin\"; filename*=UTF-8''na%C3%AFve%20song.mp3", null);
        check("filename* (UTF-8) wins", "na\u00efve song.mp3".equals(n2), n2);
        String n5 = (String) name.invoke(null, "https://x.org/a", "attachment; filename*=\"UTF-8''a%20b.txt\"", null);
        check("quoted filename*", "a b.txt".equals(n5), n5);
        String n6 = (String) name.invoke(null, "https://x.org/a", "attachment; filename*=ISO-8859-1''caf%E9.txt", null);
        check("filename* in ISO-8859-1", "caf\u00e9.txt".equals(n6), n6);
        String n3 = (String) name.invoke(null, "https://x.org/files/movie.mp4?token=1", null, "video/mp4");
        check("no header: the URL's name", "movie.mp4".equals(n3), n3);
        String n4 = (String) name.invoke(null, "https://x.org/a", "attachment; filename=\"../../evil.apk\"", null);
        check("no folders from the server", n4 != null && !n4.contains("/"), n4);
        Method type = web.getMethod("mimeType", String.class, String.class);
        check("octet-stream + .mp3 -> audio/mpeg", "audio/mpeg".equals(type.invoke(null, "application/octet-stream", "a.mp3")), type.invoke(null, "application/octet-stream", "a.mp3"));
        check("server type kept", "application/pdf".equals(type.invoke(null, "application/pdf", "a.mp3")), type.invoke(null, "application/pdf", "a.mp3"));
        System.out.println("WEBCALL " + (checks - fails) + "/" + checks + " ok");
    }
}
