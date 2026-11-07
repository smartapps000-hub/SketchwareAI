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
        // v1.9.6: names from the link (query parameter, path), never ".bin"; endings from the first bytes; ad hosts
        String n7 = (String) name.invoke(null, "https://cdn.x.org/media/Endhayya%20Saami", null, "application/octet-stream");
        check("octet-stream, no ending: no .bin", "Endhayya Saami".equals(n7), n7);
        String n8 = (String) name.invoke(null, "https://x.org/download.php?id=5&file=song.mp3", null, "application/octet-stream");
        check("file= query parameter", "song.mp3".equals(n8), n8);
        String n9 = (String) name.invoke(null, "https://x.org/get?x=1&title=My%20Song.m4a", null, null);
        check("title= query parameter, decoded", "My Song.m4a".equals(n9), n9);
        String n10 = (String) name.invoke(null, "https://x.org/v/abc", null, "video/mp4");
        check("no ending: from the MIME type", "abc.mp4".equals(n10), n10);
        String n11 = (String) name.invoke(null, "https://x.org/d?name=report&id=2", null, "application/pdf");
        check("query value without an ending is skipped", "d.pdf".equals(n11), n11);
        String n12 = (String) name.invoke(null, "https://x.org/", null, null);
        check("no name at all", "download".equals(n12), n12);
        // review fixes: an empty server name falls back to the link; endings Android doesn't know need one case;
        // a script's ending is replaced by the type's; + in a query value is a space; no % # ? in names; 150 chars
        String n13 = (String) name.invoke(null, "https://x.org/files/song.mp3", "attachment; filename=\"\"", null);
        check("empty filename= falls back to the link", "song.mp3".equals(n13), n13);
        String n14 = (String) name.invoke(null, "https://x.org/a/Mr.Bean", null, "audio/mpeg");
        check("Mr.Bean has no ending", "Mr.Bean.mp3".equals(n14), n14);
        String n15 = (String) name.invoke(null, "https://x.org/a/Invoice%2012.2026", null, "application/pdf");
        check("digits are no ending", "Invoice 12.2026.pdf".equals(n15), n15);
        String n16 = (String) name.invoke(null, "https://x.org/download.php?id=4", null, "application/pdf");
        check("download.php sent as PDF", "download.pdf".equals(n16), n16);
        String n17 = (String) name.invoke(null, "https://x.org/get?file=My+Song.mp3", null, null);
        check("+ in a query value is a space", "My Song.mp3".equals(n17), n17);
        String n18 = (String) name.invoke(null, "https://x.org/a", "attachment; filename=\"Track #1 100%?.mp3\"", null);
        check("no # % ? in a name", "Track _1 100__.mp3".equals(n18), n18);
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 40; i++) longName.append("abcdef");
        String n19 = (String) name.invoke(null, "https://x.org/a", "attachment; filename=\"" + longName + ".mp3\"", null);
        check("at most 150 characters, ending kept", n19.length() == 150 && n19.endsWith(".mp3"), n19.length());
        String n20 = (String) name.invoke(null, "https://x.org/x.xapk", null, "application/octet-stream");
        check("lower-case unknown ending kept", "x.xapk".equals(n20), n20);
        String n21 = (String) name.invoke(null, "https://x.org/a/Still%20D.R.E", null, "audio/mpeg");
        check("one letter is no ending", "Still D.R.E.mp3".equals(n21), n21);
        String n22 = (String) name.invoke(null, "https://x.org/a/song.feat", null, "audio/mpeg");
        check("unknown ending + audio type: .mp3 added", "song.feat.mp3".equals(n22), n22);
        Method withExt = web.getMethod("withMimeExtension", String.class, String.class);
        check("octet-stream adds nothing", "x".equals(withExt.invoke(null, "x", "application/octet-stream")), withExt.invoke(null, "x", "application/octet-stream"));
        check("audio/mpeg adds .mp3", "x.mp3".equals(withExt.invoke(null, "x", "audio/mpeg")), withExt.invoke(null, "x", "audio/mpeg"));
        Method bytes = web.getMethod("extensionFromBytes", byte[].class, int.class);
        Object[][] magic = {
                {"ID3", new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0}, "mp3"},
                {"MPEG frame", new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x64}, "mp3"},
                {"AAC ADTS", new byte[]{(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80}, "aac"},
                {"ftyp isom", new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}, "mp4"},
                {"ftyp M4A", new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '}, "m4a"},
                {"PDF", new byte[]{'%', 'P', 'D', 'F', '-', '1'}, "pdf"},
                {"ZIP / APK", new byte[]{'P', 'K', 3, 4, 20, 0}, "zip"},
                {"PNG", new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10}, "png"},
                {"JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, "jpg"},
                {"WebP", new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, "webp"},
                {"SVG", "<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(), "svg"},
                {"text", "hello world".getBytes(), null}};
        for (Object[] m : magic) {
            byte[] b = (byte[]) m[1];
            Object got = bytes.invoke(null, b, b.length);
            check("bytes " + m[0], m[2] == null ? got == null : m[2].equals(got), got);
        }
        Method ad = web.getMethod("isAdHost", String.class);
        check("ad subdomain blocked", (Boolean) ad.invoke(null, "pagead2.googlesyndication.com"), true);
        check("ad domain blocked", (Boolean) ad.invoke(null, "DoubleClick.net"), true);
        check("google.com allowed", !(Boolean) ad.invoke(null, "www.google.com"), false);
        check("look-alike allowed", !(Boolean) ad.invoke(null, "notdoubleclick.net"), false);
        check("null host allowed", !(Boolean) ad.invoke(null, (Object) null), false);
        System.out.println("WEBCALL " + (checks - fails) + "/" + checks + " ok");
    }
}
