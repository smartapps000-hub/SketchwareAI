package h;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static h.Main.OUT;
import static h.R.*;

/**
 * v1.9.8: the built-in library cache (cacheDir/libs) repairs itself. On a fixture that uses Material (graphics-shapes
 * comes with it), with the shipped APK's classes:
 * A. same app version, damaged cache: graphics-shapes' classes.jar deleted, Material's DEX cut off, a Material res file
 *    emptied, then a normal Run build;
 * B. in-place update: BuiltInCache.onAppStart with an older, then a newer version name (the cache is moved aside, the
 *    next build unpacks everything again), then a Run build;
 * C. a file vanishing during the build (after the "built-in libraries" step): the build fails, the library is unpacked
 *    again and the build starts over once (Main.buildWithRecovery = DesignActivity's v1.9.8 failure handling);
 * D. the failure matcher (BuildDoctor.builtInLibraryOf) on real and look-alike log lines;
 * E. Build Doctor's diagnosis of such a log.
 * Prints CACHE CHECK lines and "CACHETEST n/n ok".
 */
public final class CacheTest {
    private static int ok, total;

    static void check(String name, boolean result, Object detail) {
        total++;
        if (result) ok++;
        OUT.println("CACHE CHECK " + (result ? "ok  " : "FAIL") + " " + name + (detail == null ? "" : " - " + detail));
    }

    static void run(String id) throws Throwable {
        Class<?> builtIn = c("mod.jbk.build.BuiltInLibraries");
        Class<?> cache = c("com.sketchware.ai.project.BuiltInCache");
        Class<?> doctor = c("com.sketchware.ai.doctor.BuildDoctor");
        File assets = (File) get(builtIn, "EXTRACTED_COMPILE_ASSETS_PATH");
        File libs = new File(assets, "libs"), dexs = new File(assets, "dexs");
        String shapes = (String) get(builtIn, "ANDROIDX_GRAPHICS_SHAPES");
        String material = (String) get(builtIn, "MATERIAL");
        File compiledLibs = new File(Main.CTX.getCacheDir(), "compiledLibs");

        // 0. the "installed" version starts (fresh process: its cache folders are moved aside once), a first build unpacks
        call(cache, "onAppStart", Main.CTX, "198|v1.9.7.1-6e5d022-minApi26");
        OUT.println("CACHETEST build 0 (first extraction)");
        Main.buildWithRecovery(id);
        File shapesJar = new File(libs, shapes + "/classes.jar");
        long shapesJarSize = entrySize(new File(assets, "libs.zip"), shapes + "/classes.jar");
        check("0 graphics-shapes classes.jar unpacked", shapesJar.length() == shapesJarSize, shapesJar.length());
        check("0 compiled Material resources cached", new File(compiledLibs, material + ".zip").isFile(), null);

        // A. damaged cache, same version
        File materialDex = new File(dexs, material + ".dex");
        long materialDexSize = materialDex.length();
        File materialRes = firstFile(new File(libs, material + "/res/values"));
        long materialResSize = materialRes.length();
        check("A files to damage exist", shapesJar.delete() && materialDexSize > 1000 && materialResSize > 0, materialRes);
        try (RandomAccessFile cut = new RandomAccessFile(materialDex, "rw")) {
            cut.setLength(materialDexSize / 2);
        }
        new FileOutputStream(materialRes).close();
        long compiledBefore = new File(compiledLibs, material + ".zip").lastModified();
        OUT.println("CACHETEST build A (damaged cache: " + shapesJar + " deleted, " + materialDex.getName()
                + " cut to " + materialDex.length() + " bytes, " + materialRes.getName() + " emptied)");
        Main.buildWithRecovery(id);
        check("A graphics-shapes classes.jar back", shapesJar.length() == shapesJarSize, shapesJar.length());
        check("A Material DEX back to its size", materialDex.length() == materialDexSize, materialDex.length());
        check("A Material res file back", materialRes.length() == materialResSize, materialRes.length());
        File compiled = new File(compiledLibs, material + ".zip");
        check("A compiled Material resources made again", compiled.isFile() && compiled.lastModified() >= compiledBefore, null);
        String report = (String) call(c("com.sketchware.ai.project.BuildWarnings"), "report");
        check("A repair noted in the log, not a warning", report.contains("Built-in library " + material + " was unpacked again")
                && report.contains("Built-in library " + shapes + " was unpacked again")
                && !report.contains("warning: Built-in"), firstLines(report));
        check("A no staged leftovers", leftovers(libs).isEmpty() && leftovers(dexs).isEmpty(), leftovers(libs));

        // B. in-place update
        boolean same = (Boolean) call(cache, "onAppStart", Main.CTX, "198|v1.9.7.1-6e5d022-minApi26");
        check("B same version keeps the cache", !same && shapesJar.isFile() && materialDex.isFile(), "retired " + same);
        boolean updated = (Boolean) call(cache, "onAppStart", Main.CTX, "199|v1.9.8-test-minApi26");
        check("B new version moves the cache aside", updated && !libs.exists() && !dexs.exists()
                && !new File(assets, "libs.zip").exists() && !compiledLibs.exists(), list(Main.CTX.getCacheDir()));
        check("B android.jar kept", new File(assets, "android.jar").isFile(), null);
        long until = System.currentTimeMillis() + 120_000;
        while (!stale(Main.CTX.getCacheDir()).isEmpty() && System.currentTimeMillis() < until) Thread.sleep(500);
        check("B stale folders deleted in the background", stale(Main.CTX.getCacheDir()).isEmpty(), stale(Main.CTX.getCacheDir()));
        OUT.println("CACHETEST build B (after an in-place update)");
        Main.buildWithRecovery(id);
        check("B everything unpacked again", shapesJar.length() == shapesJarSize && materialDex.length() == materialDexSize, null);

        // C. a file vanishes during the build
        File shapesDex = new File(dexs, shapes + ".dex");
        Main.AFTER_LIBRARIES = () -> {
            OUT.println("CACHETEST removing " + shapesJar + " and " + shapesDex + " after the built-in libraries step");
            shapesJar.delete();
            shapesDex.delete();
        };
        OUT.println("CACHETEST build C (files vanish mid-build)");
        Main.buildWithRecovery(id);
        check("C build finished after the restart", Main.AFTER_LIBRARIES == null, "hook " + (Main.AFTER_LIBRARIES == null ? "used" : "never fired a failure"));
        check("C files back", shapesJar.length() == shapesJarSize && shapesDex.isFile(), null);
        String reportC = (String) call(c("com.sketchware.ai.project.BuildWarnings"), "report");
        check("C restart noted", reportC.contains("started over"), firstLines(reportC));
        Main.AFTER_LIBRARIES = null;

        // D. matcher
        String[][] cases = {
                {"java.nio.file.NoSuchFileException: /data/user/0/com.sketchware.ai/cache/libs/libs/graphics-shapes-1.0.1/classes.jar", "graphics-shapes-1.0.1"},
                {"Packaging the APK failed: java.io.FileNotFoundException: /data/user/0/com.sketchware.ai/cache/libs/dexs/material-1.13.0.dex (No such file or directory)", "material-1.13.0"},
                {"Error in /data/user/0/com.sketchware.ai/cache/libs/libs/appcompat-1.7.1/classes.jar: java.util.zip.ZipException: zip END header not found", "appcompat-1.7.1"},
                {"java.nio.file.NoSuchFileException: /storage/emulated/0/.sketchware/libs/local_libs/okhttp_V_4.12.0/classes.jar\n"
                        + "  classpath: /data/user/0/com.sketchware.ai/cache/libs/libs/core-1.13.1/classes.jar", null},
                {"java.nio.file.NoSuchFileException: /data/user/0/com.sketchware.ai/cache/libs/libs/../classes.jar", null},
                {"java.nio.file.NoSuchFileException: /storage/emulated/0/.sketchware/libs/local_libs/okhttp_V_4.12.0/classes.jar", null},
                {"java.util.zip.ZipException: error in opening zip file /data/user/0/com.sketchware.ai/cache/libs/libs.zip", null},
                {"/data/user/0/com.sketchware.ai/cache/libs/libs/material-1.13.0/res/values/values.xml:12: error: resource attr/x not found.", null},
                {"NoSuchFileException: /data/user/0/com.sketchware.ai/cache/libs/libs/core-1.13.1.tmp-123/classes.jar", null},
        };
        for (String[] t : cases) {
            Object got = call(doctor, "builtInLibraryOf", t[0]);
            check("D " + (t[1] == null ? "ignores" : "finds " + t[1]) + ": " + t[0].substring(0, Math.min(70, t[0].length())),
                    t[1] == null ? got == null : t[1].equals(got), got);
        }

        Object dots = call(cache, "recover", assets, "java.io.FileNotFoundException: /x/cache/libs/dexs/...dex (No such file)");
        check("D recover never acts on a name outside the archives", dots == null && libs.isDirectory()
                && shapesJar.isFile(), dots);

        // E. diagnosis of such a log
        String log = "com.android.tools.r8.CompilationFailedException: Compilation failed to complete\n"
                + "Caused by: java.nio.file.NoSuchFileException: /data/user/0/com.sketchware.ai/cache/libs/libs/graphics-shapes-1.0.1/classes.jar\n"
                + "\tat mod.jbk.build.compiler.dex.DexCompiler.d8(DexCompiler.java:98)\n\n\nBuild map:\n"
                + "    ok      Prepare project files   0.2 s\n    FAILED  DEX   3.1 s\n    -       DEX merge\n";
        Object d = call(doctor, "diagnose", log);
        check("E code BUILT_IN_CACHE", "BUILT_IN_CACHE".equals(get(d, "code")), get(d, "code"));
        check("E names the library", String.valueOf(get(d, "title")).contains("graphics-shapes-1.0.1"), get(d, "title"));
        check("E build again can help", Boolean.TRUE.equals(get(d, "retryCanHelp")), null);
        check("E stage DEX", "DEX".equals(get(d, "stage")), get(d, "stage"));

        OUT.println("CACHETEST " + ok + "/" + total + (ok == total ? " ok" : " FAILED"));
    }

    static long entrySize(File zip, String name) throws Exception {
        try (ZipFile z = new ZipFile(zip)) {
            ZipEntry e = z.getEntry(name);
            return e == null ? -1 : e.getSize();
        }
    }

    static File firstFile(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) if (f.isFile() && f.length() > 0) return f;
        }
        throw new IllegalStateException("no file in " + dir);
    }

    static List<String> leftovers(File dir) {
        List<String> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.getName().contains(".tmp-") || f.getName().contains(".old-")) out.add(f.getName());
        return out;
    }

    static List<String> stale(File cacheDir) {
        List<String> out = new ArrayList<>();
        File[] files = cacheDir.listFiles();
        if (files != null) for (File f : files) if (f.getName().startsWith("builtin-stale-")) out.add(f.getName());
        return out;
    }

    static String list(File dir) {
        String[] names = dir.list();
        return names == null ? "[]" : java.util.Arrays.toString(names);
    }

    static String firstLines(String text) {
        String t = text.replace('\n', ' ');
        return t.length() > 400 ? t.substring(0, 400) + "..." : t;
    }
}
