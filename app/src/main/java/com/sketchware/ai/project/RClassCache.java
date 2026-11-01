package com.sketchware.ai.project;

import android.os.Build;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mod.jbk.util.LogUtil;

/**
 * v1.9.0 build cache for the R classes of a Run build.
 * <p>
 * aapt2 writes one R.java per package of the project and of every library with resources, each with all resource ids
 * of the build (non-transitive R is off): for an AppCompat / Material 3 project 28 files of 2.5 MB. ECJ compiled and D8
 * dexed all of them at every build (fixture 908 on the ART harness: 121 s of Java and 343 s of DEX in the
 * interpreter, nearly all of it R), although they only change when the project's resources or libraries change.
 * <p>
 * Their compiled classes and their DEX are kept per project, keyed by the SHA-1 of every R.java (path and content),
 * the Java level, the min SDK and the IDE build. When the key matches, ECJ compiles the project's own sources against
 * the kept classes (the R sources are left out) and D8 dexes only the project's own classes; the kept R DEX is added
 * next to them (files {@code r-classes*.dex}, merged or packaged like the project's DEX). Same classes, same DEX: the
 * cache only skips work whose output is already there. Not used for exports, with ProGuard or StringFog on, below
 * Android 8 (Dx), or when the generated sources hold anything but R.java; Build Settings can switch it off.
 * <p>
 * Android may delete files of the cache folder one by one when storage runs low: each marker lists every kept file
 * with its size, and an entry whose files don't all match is deleted and built again. Only the
 * {@link #PROJECTS_KEPT} most recently built projects keep entries.
 */
public final class RClassCache {

    private static final String TAG = "RClassCache";
    private static final String VERSION = "rclass-2";
    /** Projects whose cache entries are kept (the most recently built ones); older ones are deleted. */
    static final int PROJECTS_KEPT = 8;

    /** cacheDir/build-cache/r/&lt;sc_id&gt;/&lt;key&gt;: classes/, dex/ and the markers classes.ok, dex.ok */
    private final File entry;
    /** Package folders of the R.java files (com/fixture/m3), whose R.class and R$*.class are the R classes. */
    private final List<String> packages;
    private final int minSdk;
    /** Verified state of the markers (null: not checked yet). */
    private Boolean classesOk;
    private Boolean dexOk;

    private RClassCache(File entry, List<String> packages, int minSdk) {
        this.entry = entry;
        this.packages = packages;
        this.minSdk = minSdk;
    }

    /**
     * The cache for this build, or null when it isn't used.
     *
     * @param gen     the folder aapt2 wrote the R.java files to
     * @param ide     the IDE build (a new IDE build starts a new cache: its ECJ or D8 may differ)
     * @param enabled the project's setting
     */
    public static RClassCache open(File cacheRoot, String scId, File gen, String javaLevel, int minSdk, String ide,
                                   boolean debugBuild, boolean enabled) {
        if (!enabled || !debugBuild || Build.VERSION.SDK_INT < 26 || cacheRoot == null || gen == null || !gen.isDirectory()) {
            return null;
        }
        try {
            List<File> sources = new ArrayList<>();
            if (!collect(gen, sources)) return null;   // something besides R.java: compiled as before
            if (sources.isEmpty()) return null;
            List<String> packages = new ArrayList<>();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            update(digest, VERSION + "|" + javaLevel + "|" + minSdk + "|" + ide + "|" + sources.size());
            byte[] buffer = new byte[256 * 1024];
            String root = gen.getCanonicalPath();
            for (File source : sources) {
                String relative = source.getCanonicalPath().substring(root.length() + 1).replace(File.separatorChar, '/');
                if (relative.indexOf('/') < 0) return null;   // an R.java without a package: compiled as before
                String folder = relative.substring(0, relative.length() - "/R.java".length());
                packages.add(folder);
                update(digest, "|" + relative + "|" + source.length() + "|");
                try (InputStream in = new FileInputStream(source)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
                }
            }
            String key = hex(digest.digest());
            File project = new File(new File(new File(cacheRoot, "build-cache"), "r"), scId);
            if (project.isDirectory()) //noinspection ResultOfMethodCallIgnored
                project.setLastModified(System.currentTimeMillis());
            return new RClassCache(new File(project, key), packages, minSdk);
        } catch (Exception e) {
            LogUtil.e(TAG, "R class cache not used", e);
            return null;
        }
    }

    /** The R.java files under {@code dir} (sorted); false when the folder holds another source file. */
    private static boolean collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return true;
        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        Collections.sort(sorted, (a, b) -> a.getName().compareTo(b.getName()));
        for (File file : sorted) {
            if (file.isDirectory()) {
                if (!collect(file, out)) return false;
            } else if (file.getName().endsWith(".java")) {
                if (!file.getName().equals("R.java")) return false;
                out.add(file);
            }
        }
        return true;
    }

    public boolean hasClasses() {
        if (classesOk == null) classesOk = verified("classes.ok", "classes");
        return classesOk;
    }

    public boolean hasDex() {
        if (dexOk == null) dexOk = hasClasses() && verified("dex.ok", "dex");
        return dexOk;
    }

    /**
     * Whether marker {@code name} lists exactly the files under {@code folder} with their sizes. A marker whose files
     * don't match (partly deleted by the system) is removed with the entry's content, so the build makes it again.
     */
    private boolean verified(String name, String folder) {
        File marker = new File(entry, name);
        if (!marker.isFile()) return false;
        try {
            String expected = new String(java.nio.file.Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8);
            if (!expected.isEmpty() && expected.equals(manifest(new File(entry, folder)))) return true;
        } catch (IOException e) {
            LogUtil.e(TAG, "Couldn't read " + marker, e);
        }
        LogUtil.d(TAG, "Kept R " + folder + " incomplete: built again");
        delete(entry);
        return false;
    }

    /** "relative/path<TAB>size" of every file under {@code dir}, sorted, one per line ("" when there is none). */
    static String manifest(File dir) {
        List<String> lines = new ArrayList<>();
        listFiles(dir, "", lines);
        Collections.sort(lines);
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(line).append('\n');
        return out.toString();
    }

    private static void listFiles(File dir, String prefix, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) listFiles(file, prefix + file.getName() + "/", out);
            else out.add(prefix + file.getName() + "\t" + file.length());
        }
    }

    private static void writeMarker(File marker, File folder) throws IOException {
        String manifest = manifest(folder);
        if (manifest.isEmpty()) throw new IOException("nothing to keep in " + folder);
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(manifest.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Folder of the kept R classes (for ECJ's classpath). */
    public String classesPath() {
        return new File(entry, "classes").getAbsolutePath();
    }

    /** Whether {@code classFile} under {@code classesRoot} is one of the R classes. */
    public boolean isRClass(File classFile, File classesRoot) {
        String name = classFile.getName();
        if (!name.equals("R.class") && !(name.startsWith("R$") && name.endsWith(".class"))) return false;
        String root = classesRoot.getAbsolutePath();
        String parent = classFile.getParentFile() == null ? "" : classFile.getParentFile().getAbsolutePath();
        if (!parent.startsWith(root)) return false;
        String folder = parent.length() == root.length() ? "" : parent.substring(root.length() + 1).replace(File.separatorChar, '/');
        return packages.contains(folder);
    }

    /**
     * After a compile with the R sources: keeps their classes. Written to a temporary folder first and renamed, so a
     * half-written entry is never used; other entries of the project are deleted (one entry per project).
     */
    public void storeClasses(File classesRoot) {
        if (hasClasses()) return;
        File temp = new File(entry.getParentFile(), "." + entry.getName() + "-" + Thread.currentThread().getId() + "-" + System.nanoTime());
        try {
            File out = new File(temp, "classes");
            int copied = 0;
            for (String folder : packages) {
                File source = new File(classesRoot, folder.replace('/', File.separatorChar));
                File[] files = source.listFiles();
                if (files == null) continue;
                File target = new File(out, folder.replace('/', File.separatorChar));
                for (File file : files) {
                    if (file.isFile() && isRClass(file, classesRoot)) {
                        copy(file, new File(target, file.getName()));
                        copied++;
                    }
                }
            }
            if (copied == 0) return;
            writeMarker(new File(temp, "classes.ok"), out);
            pruneOthers();
            if (!temp.renameTo(entry)) {
                LogUtil.d(TAG, "R classes already kept by another build");
            }
            classesOk = null;
            dexOk = null;
            pruneProjects(entry.getParentFile(), PROJECTS_KEPT);
        } catch (Exception e) {
            LogUtil.e(TAG, "Couldn't keep the R classes", e);
        } finally {
            delete(temp);
        }
    }

    /**
     * Puts the R classes' DEX into the build's DEX folder: the kept one, or D8's output of the kept classes (then kept
     * too). Called after the project's own classes were dexed.
     */
    public void addDex(File dexDirectory, File androidJar) throws Exception {
        if (!hasClasses()) throw new IOException("the R classes weren't kept");
        if (!hasDex()) {
            File temp = new File(entry, ".dex-" + Thread.currentThread().getId() + "-" + System.nanoTime());
            try {
                if (!temp.mkdirs()) throw new IOException("can't create " + temp);
                List<Path> program = new ArrayList<>();
                classFiles(new File(entry, "classes"), program);
                D8Command.Builder builder = D8Command.builder()
                        .setMode(CompilationMode.RELEASE)
                        .setIntermediate(true)
                        .setMinApiLevel(minSdk)
                        .setOutput(temp.toPath(), OutputMode.DexIndexed)
                        .addProgramFiles(program);
                if (androidJar != null && androidJar.isFile()) builder.addLibraryFiles(androidJar.toPath());
                D8.run(builder.build());
                File dex = new File(entry, "dex");
                delete(dex);
                delete(new File(entry, "dex.ok"));
                boolean kept = temp.renameTo(dex);
                try {
                    if (kept) writeMarker(new File(entry, "dex.ok"), dex);
                } catch (IOException e) {
                    kept = false;
                }
                dexOk = null;
                if (!kept) {
                    // the DEX is right, only keeping it failed: this build uses it as it is
                    LogUtil.d(TAG, "R DEX not kept, used for this build only");
                    putDex(dex.isDirectory() ? dex : temp, dexDirectory);
                    delete(dex);
                    return;
                }
            } finally {
                delete(temp);
            }
        }
        putDex(new File(entry, "dex"), dexDirectory);
    }

    private static void putDex(File from, File dexDirectory) throws IOException {
        File[] files = from.listFiles((dir, name) -> name.endsWith(".dex"));
        if (files == null || files.length == 0) throw new IOException("no R DEX kept");
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File file : files) {
            String name = file.getName();   // classes.dex, classes2.dex ...
            copy(file, new File(dexDirectory, "r-" + name));
        }
    }

    private static void classFiles(File dir, List<Path> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) classFiles(file, out);
            else if (file.getName().endsWith(".class")) out.add(file.toPath());
        }
    }

    /**
     * Keeps the entries of the {@code keep} most recently used projects in {@code root}'s parent (one folder per
     * project): {@code project} itself and the newest others. A project that was deleted loses its entry this way.
     */
    static void pruneProjects(File project, int keep) {
        File root = project == null ? null : project.getParentFile();
        File[] projects = root == null ? null : root.listFiles(File::isDirectory);
        if (projects == null || projects.length <= keep) return;
        List<File> others = new ArrayList<>();
        for (File p : projects) if (!p.equals(project)) others.add(p);
        Collections.sort(others, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep - 1; i < others.size(); i++) delete(others.get(i));
    }

    private void pruneOthers() {
        File[] others = entry.getParentFile().listFiles();
        if (others == null) return;
        for (File other : others) {
            if (!other.getName().startsWith(".") && !other.getName().equals(entry.getName())) delete(other);
        }
    }

    public static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("can't create " + parent);
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }

    public static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return s.toString();
    }
}
