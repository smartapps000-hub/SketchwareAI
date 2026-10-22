package h;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;

import static h.R.*;

/**
 * CoreLibDesugaring.libraryDex (L8) from the shipped APK, outside a build.
 * L8Race <min sdk> <keep rules file> <android.jar> race : two threads ask for the same DEX at once (a Run build and an
 *                                                         export at the same time), cache empty for that key
 * L8Race <min sdk> <keep rules file> <android.jar> one  : one call (with ART_OPTS=-Xmx... to see what heap L8 needs)
 * Prints each result, the DEX header and what is left in the cache folder (no *.tmp may remain).
 */
public class L8Race {
    public static void main(String[] a) throws Throwable {
        try {
            set(c("com.besome.sketch.SketchApplication"), "mApplicationContext", Main.CTX);
            final int minApi = Integer.parseInt(a[0]);
            final String rules = new String(java.nio.file.Files.readAllBytes(new File(a[1]).toPath()), "UTF-8");
            final File jar = new File(a[2]);
            int threads = "race".equals(a[3]) ? 2 : 1;
            final Method m = c("com.sketchware.ai.project.CoreLibDesugaring").getMethod("libraryDex", int.class, String.class, File.class);
            final File[] out = new File[threads];
            final Throwable[] err = new Throwable[threads];
            Thread[] t = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                final int n = i;
                t[i] = new Thread(() -> {
                    try {
                        out[n] = (File) m.invoke(null, minApi, rules, jar);
                    } catch (Throwable e) {
                        err[n] = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
                    }
                });
            }
            long start = System.currentTimeMillis();
            for (Thread x : t) x.start();
            for (Thread x : t) x.join();
            Main.OUT.println("time " + (System.currentTimeMillis() - start) + " ms, max heap " + Runtime.getRuntime().maxMemory() / 1048576 + " MB");
            for (int i = 0; i < threads; i++) {
                if (err[i] != null) {
                    Main.OUT.println("thread " + i + " FAILED: " + err[i]);
                    err[i].printStackTrace(Main.OUT);
                } else {
                    Main.OUT.println("thread " + i + " -> " + out[i] + " (" + out[i].length() + " bytes, header " + header(out[i]) + ")");
                }
            }
            File cache = out[0] != null ? out[0].getParentFile() : null;
            if (cache != null) {
                String[] names = cache.list();
                java.util.Arrays.sort(names);
                for (String name : names) Main.OUT.println("cache: " + name);
            }
        } catch (Throwable e) {
            e.printStackTrace(Main.OUT);
            System.exit(1);
        }
        System.exit(0);
    }

    private static String header(File f) throws Exception {
        byte[] b = new byte[8];
        FileInputStream in = new FileInputStream(f);
        try {
            in.read(b);
        } finally {
            in.close();
        }
        return new String(b, 0, 7, "ISO-8859-1").replace('\n', ' ');
    }
}
