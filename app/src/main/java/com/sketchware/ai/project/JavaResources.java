package com.sketchware.ai.project;

import com.android.sdklib.build.ApkBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * v1.9.1: the Java resources (files other than classes) the APK packager takes from library jars. sdklib's ApkBuilder
 * stops the whole build with "Duplicate files" when two jars (or a jar and the compiled resources / native libraries)
 * hold a file at the same path outside META-INF, e.g. {@code okhttp3/internal/publicsuffix/publicsuffixes.gz} from two
 * OkHttp versions or {@code DebugProbesKt.bin} from two coroutines versions. Here the first copy is kept: a jar with
 * such a file is packaged from a copy without it, and the build's warnings name the files. Gradle stops with the same
 * error unless a packaging rule picks one; picking the first is what a {@code pickFirst} rule does.
 */
public final class JavaResources {

    private final Map<String, String> seen = new HashMap<>();
    private final File workDirectory;
    private final List<String> duplicates = new ArrayList<>();
    private int copies;

    /** @param workDirectory where jar copies are written (in the build's own folder; deleted by {@link #cleanUp}) */
    public JavaResources(File workDirectory) {
        this.workDirectory = workDirectory;
    }

    /** Paths already in the APK before the jars: the compiled resources (resources.ap_) and the first DEX file. */
    public void seedFromZip(File zip, String owner) {
        if (zip == null || !zip.isFile()) return;
        try (ZipFile file = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;
                if (!seen.containsKey(entry.getName())) seen.put(entry.getName(), owner);
            }
        } catch (Exception ignored) {
            // unreadable: ApkBuilder reports it
        }
    }

    public void seed(String path, String owner) {
        if (!seen.containsKey(path)) seen.put(path, owner);
    }

    /** The native libraries ApkBuilder.addNativeLibraries will add from {@code folder} (lib/&lt;abi&gt;/&lt;name&gt;.so). */
    public void seedNativeFolder(File folder, String owner) {
        File[] abis = folder == null ? null : folder.listFiles();
        if (abis == null) return;
        for (File abi : abis) {
            File[] files = abi.isDirectory() ? abi.listFiles() : null;
            if (files == null) continue;
            for (File so : files) {
                if (so.isFile() && so.getName().endsWith(".so")) seed("lib/" + abi.getName() + "/" + so.getName(), owner);
            }
        }
    }

    /**
     * The jar to give ApkBuilder.addResourcesFromJar for {@code jar}: the jar itself, or (when it holds a file another
     * one already gave) a copy of its packaged files without those. Falls back to the jar itself on any read error.
     */
    public File prepare(File jar, String owner) {
        List<String> packaged = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        try (ZipFile file = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !packaged(name)) continue;
                String first = seen.get(name);
                if (first != null) {
                    dropped.add(name);
                    if (duplicates.size() < 8) duplicates.add(name + " (in " + first + " and " + owner + ")");
                    else if (duplicates.size() == 8) duplicates.add("…");
                } else {
                    packaged.add(name);
                }
            }
            if (dropped.isEmpty()) {
                for (String name : packaged) seen.put(name, owner);
                return jar;
            }
            if (!workDirectory.isDirectory() && !workDirectory.mkdirs()) return jar;
            File copy = new File(workDirectory, (copies++) + "-resources.jar");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(copy))) {
                byte[] buffer = new byte[64 * 1024];
                for (String name : packaged) {
                    ZipEntry entry = file.getEntry(name);
                    out.putNextEntry(new ZipEntry(name));
                    try (InputStream in = file.getInputStream(entry)) {
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                    out.closeEntry();
                }
            }
            for (String name : packaged) seen.put(name, owner);
            return copy;
        } catch (Exception e) {
            return jar;
        }
    }

    /** Whether ApkBuilder packages this jar entry as a Java resource (its own filter, outside META-INF). */
    static boolean packaged(String name) {
        if (name.startsWith("META-INF/")) return false;
        String[] segments = name.split("/");
        if (segments.length == 0) return false;
        for (int i = 0; i < segments.length - 1; i++) {
            if (!ApkBuilder.checkFolderForPackaging(segments[i])) return false;
        }
        String fileName = segments[segments.length - 1];
        return !fileName.isEmpty() && ApkBuilder.checkFileForPackaging(fileName);
    }

    /** The warning for the build log, or null when no file was in two places. */
    public String warning() {
        if (duplicates.isEmpty()) return null;
        StringBuilder text = new StringBuilder("Two libraries hold the same file; the APK has the first copy of: ");
        for (int i = 0; i < duplicates.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(duplicates.get(i));
        }
        return text.append('.').toString();
    }

    public void cleanUp() {
        File[] files = workDirectory.listFiles();
        if (files != null) for (File file : files) //noinspection ResultOfMethodCallIgnored
            file.delete();
        //noinspection ResultOfMethodCallIgnored
        workDirectory.delete();
    }

    /** "material-1.13.0" for …/material-1.13.0/classes.jar, the file name otherwise. */
    public static String ownerOf(File jar) {
        File parent = jar.getParentFile();
        return jar.getName().equals("classes.jar") && parent != null ? parent.getName() : jar.getName();
    }
}
