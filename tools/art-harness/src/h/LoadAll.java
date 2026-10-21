package h;

import java.util.*;

/** Loads (links + verifies) every class of a built app APK on ART, against the android.jar stubs. */
public class LoadAll {
    public static void main(String[] a) throws Exception {
        String apk = a[0];
        ClassLoader cl = (ClassLoader) Class.forName("dalvik.system.PathClassLoader").getConstructor(String.class, ClassLoader.class)
                .newInstance(apk, LoadAll.class.getClassLoader());
        Object dex = Class.forName("dalvik.system.DexFile").getConstructor(String.class).newInstance(apk);
        int ok = 0;
        Map<String, Integer> why = new TreeMap<>();
        List<String> failed = new ArrayList<>();
        @SuppressWarnings("unchecked") Enumeration<String> en = (Enumeration<String>) dex.getClass().getMethod("entries").invoke(dex);
        for (Enumeration<String> e = en; e.hasMoreElements(); ) {
            String n = e.nextElement();
            try {
                Class.forName(n, false, cl);
                ok++;
            } catch (Throwable t) {
                Throwable r = t;
                while (r.getCause() != null) r = r.getCause();
                String k = r.getClass().getSimpleName() + ": " + r.getMessage();
                why.merge(k.length() > 160 ? k.substring(0, 160) : k, 1, Integer::sum);
                failed.add(n);
            }
        }
        System.out.println("LOADED " + ok + " FAILED " + failed.size());
        for (Map.Entry<String, Integer> w : why.entrySet()) System.out.println("  " + w.getValue() + "x " + w.getKey());
        for (String f : failed) if (f.startsWith(a.length > 1 ? a[1] : "")) System.out.println("  app class failed: " + f);
    }
}
