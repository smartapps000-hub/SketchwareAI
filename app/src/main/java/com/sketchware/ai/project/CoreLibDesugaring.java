package com.sketchware.ai.project;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.origin.Origin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mod.jbk.build.BuiltInLibraries;

/**
 * Core library desugaring for user projects (v1.8.5): java.time, java.util.stream, java.util.function, Optional,
 * java.nio.file and the Java 9-17 collection/stream additions on Android versions that don't have them, the way
 * Android Studio does it with {@code coreLibraryDesugaring "com.android.tools:desugar_jdk_libs_nio:2.1.5"}.
 * <p>
 * D8 compiles the project with the library's configuration (desugar.json of desugar_jdk_libs_configuration_nio
 * 2.1.5) and reports, as keep rules, which {@code j$} classes the project's code now uses. Only when it uses any,
 * L8 compiles desugar_jdk_libs 2.1.5 for the project's min SDK and shrinks it to those classes; the DEX is cached
 * by min SDK and keep rules, so the next build reuses it. A project that uses none of these APIs (or whose min SDK
 * already has them) gets no extra DEX at all.
 * <p>
 * Files: assets/libs/desugar.zip, extracted by {@code Dp.getBuiltInLibrariesReady()} to cacheDir/libs/desugar.
 */
public final class CoreLibDesugaring {

    public static final String VERSION = "2.1.5";
    static final String LIBRARY_JAR = "desugar_jdk_libs-" + VERSION + ".jar";
    static final String CONFIGURATION_JAR = "desugar_jdk_libs_configuration_nio-" + VERSION + ".jar";
    static final String CONFIGURATION_JSON = "desugar.json";

    private CoreLibDesugaring() {
    }

    public static File directory() {
        return new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "desugar");
    }

    /** Sizes of the three files in assets/libs/desugar.zip: a truncated extraction counts as missing. */
    private static final long LIBRARY_JAR_SIZE = 6113073L, CONFIGURATION_JAR_SIZE = 64007L, CONFIGURATION_JSON_SIZE = 146914L;

    public static boolean available() {
        File d = directory();
        return new File(d, LIBRARY_JAR).length() == LIBRARY_JAR_SIZE
                && new File(d, CONFIGURATION_JAR).length() == CONFIGURATION_JAR_SIZE
                && new File(d, CONFIGURATION_JSON).length() == CONFIGURATION_JSON_SIZE;
    }

    /** The desugared library configuration (JSON) D8 and L8 take. */
    public static String configuration() throws IOException {
        return new String(readAll(new File(directory(), CONFIGURATION_JSON)), StandardCharsets.UTF_8);
    }

    /**
     * The desugared library as one DEX for this min SDK, shrunk to the classes the keep rules name.
     *
     * @param keepRules  what D8's desugared-library keep rule consumer reported for the project's code
     * @param androidJar the platform the project compiles against
     */
    public static File libraryDex(int minApiLevel, String keepRules, File androidJar) throws Exception {
        File cacheDirectory = new File(directory(), "cache");
        String key = minApiLevel + "-" + sha1(VERSION + "\n" + androidJar.length() + "\n" + keepRules);
        File cached = new File(cacheDirectory, key + ".dex");
        if (cached.isFile() && cached.length() > 0) {
            return cached;
        }
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            throw new IOException("Can't create " + cacheDirectory);
        }
        // per-run names: two builds (Run and Export) may compute the same key at once
        String run = key + "." + Thread.currentThread().getId() + "." + System.nanoTime();
        File output = new File(cacheDirectory, run + ".tmp");
        deleteRecursively(output);
        if (!output.mkdirs()) throw new IOException("Can't create " + output);
        List<String> rules = new ArrayList<>(Arrays.asList(keepRules.split("\n")));
        L8.run(L8Command.builder()
                .addDesugaredLibraryConfiguration(configuration())
                .addProgramFiles(new File(directory(), LIBRARY_JAR).toPath(), new File(directory(), CONFIGURATION_JAR).toPath())
                .addLibraryFiles(androidJar.toPath())
                .setMinApiLevel(minApiLevel)
                .setMode(CompilationMode.RELEASE)
                .addProguardConfiguration(rules, Origin.unknown())
                .setOutput(output.toPath(), OutputMode.DexIndexed)
                .build());
        File[] dexes = output.listFiles((dir, name) -> name.endsWith(".dex"));
        if (dexes == null || dexes.length != 1) {
            deleteRecursively(output);
            throw new IOException("L8 wrote " + (dexes == null ? 0 : dexes.length) + " DEX files for the desugared library, expected 1");
        }
        File temporary = new File(cacheDirectory, run + ".dex.tmp");
        temporary.delete();
        if (!dexes[0].renameTo(temporary) || !(temporary.renameTo(cached) || (cached.isFile() && temporary.delete()))) {
            deleteRecursively(output);
            throw new IOException("Can't store the desugared library DEX in " + cacheDirectory);
        }
        deleteRecursively(output);
        return cached;
    }

    private static String sha1(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 10; i++) out.append(String.format("%02x", digest[i] & 0xff));
        return out.toString();
    }

    private static byte[] readAll(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
