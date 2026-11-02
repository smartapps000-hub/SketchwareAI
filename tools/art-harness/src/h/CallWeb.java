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
        System.out.println("WEBCALL " + (checks - fails) + "/" + checks + " ok");
    }
}
