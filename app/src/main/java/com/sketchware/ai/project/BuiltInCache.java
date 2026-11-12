package com.sketchware.ai.project;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import mod.jbk.util.LogUtil;

/**
 * v1.9.8: the built-in libraries the app unpacks into {@code cacheDir/libs} repair themselves.
 * <p>
 * Before: {@code libs.zip} / {@code dexs.zip} were copied to the cache and unpacked in place, and unpacked again only
 * when the copy's size differed from the asset. An extraction cut off (app killed, phone restarted) or files the system
 * removed from the cache dir left a library folder without its {@code classes.jar} for good: every build then stopped
 * (D8 / packaging: {@code NoSuchFileException .../cache/libs/libs/graphics-shapes-1.0.1/classes.jar}) until the app was
 * reinstalled.
 * <p>
 * Now: (1) a new app version moves the unpacked libraries aside at app start ({@link #onAppStart}, renames only) and
 * deletes them on a background thread; (2) archives are unpacked into a temp folder, checked, then renamed into place
 * ({@link #extractStaged}); (3) every build checks the files of each built-in library it uses against the archive,
 * entry by entry (sizes), and unpacks a damaged library again through a temp folder ({@link #verify}); (4) a step that
 * still fails on a missing or damaged file under {@code cacheDir/libs} has that library unpacked again and the build
 * starts over once ({@link #recover}).
 */
public final class BuiltInCache {

    private static final String TAG = "BuiltInCache";
    private static final String PREFS = "sketchware_ai_builtin_cache";
    private static final String KEY_VERSION = "app_version";
    /** What a version change moves aside (names under the cache dir). android.jar, testkey and the lambda stubs stay:
     * each is checked by its own size at every build, and the generator's automatic imports read android.jar. */
    static final String[] RETIRED_ON_UPDATE = {"libs/libs", "libs/dexs", "libs/libs.zip", "libs/dexs.zip", "libs/desugar",
            "libs/desugar.zip", "compiledLibs"};
    private static final String STALE_PREFIX = "builtin-stale-";

    private static boolean retryUsed;

    public interface Progress {
        void show(String text);
    }

    private BuiltInCache() {
    }

    // ------------------------------------------------------------------ app start

    /**
     * Called from {@code SketchApplication.onCreate}. When {@code appVersion} differs from the version that filled the
     * cache, the unpacked built-in libraries are moved into {@code cacheDir/builtin-stale-<n>} (renames: instant, no
     * work on the main thread) and the next build unpacks this version's archives. Stale folders are deleted on a
     * background thread. Never throws.
     *
     * @return true when the cache was retired now
     */
    public static boolean onAppStart(Context context, String appVersion) {
        boolean retired = false;
        try {
            File cache = context.getCacheDir();
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String last = prefs.getString(KEY_VERSION, null);
            if (!appVersion.equals(last)) {
                if (retire(cache)) {
                    prefs.edit().putString(KEY_VERSION, appVersion).apply();
                    retired = true;
                    LogUtil.d(TAG, "App version changed (" + last + " -> " + appVersion + "): built-in library cache retired");
                }
            }
            final File cacheDir = cache;
            Thread cleanup = new Thread(() -> deleteStale(cacheDir), "BuiltInCacheCleanup");
            cleanup.setDaemon(true);
            cleanup.setPriority(Thread.MIN_PRIORITY);
            cleanup.start();
        } catch (Throwable t) {
            LogUtil.e(TAG, "Couldn't check the built-in library cache at app start", t);
        }
        return retired;
    }

    /** Moves the version-bound parts of the cache aside. False when one of them couldn't be moved. */
    static boolean retire(File cache) {
        File stale = new File(cache, STALE_PREFIX + System.currentTimeMillis() + "-" + System.nanoTime());
        boolean ok = true;
        boolean made = false;
        for (String name : RETIRED_ON_UPDATE) {
            File file = new File(cache, name);
            if (!file.exists()) continue;
            if (!made) {
                made = stale.mkdirs() || stale.isDirectory();
                if (!made) return false;
            }
            File target = new File(stale, name.replace('/', '_'));
            if (!file.renameTo(target)) {
                ok = false;
                LogUtil.e(TAG, "Couldn't move " + file + " aside");
            }
        }
        return ok;
    }

    /** Deletes every {@code builtin-stale-*} folder of the cache dir (and leftovers of staged extractions). */
    static void deleteStale(File cache) {
        File[] children = cache.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().startsWith(STALE_PREFIX)) deleteRecursively(child);
            }
        }
        File libs = new File(cache, "libs");
        File[] staged = libs.listFiles();
        if (staged != null) {
            for (File child : staged) {
                // a staged extraction that a killed process left behind (never the live folders)
                if (child.getName().matches("(libs|dexs)\\.(tmp|old)-\\d+.*")) deleteRecursively(child);
            }
        }
    }

    // ------------------------------------------------------------------ whole archives

    /**
     * Unpacks {@code archive} into {@code target} through {@code target.tmp-<n>}: every file is checked against the
     * archive's sizes before the folder is renamed into place. The previous folder is moved aside and deleted after.
     *
     * @throws IOException when the archive can't be read or the result can't be put in place
     */
    public static void extractStaged(File archive, File target) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Can't create " + parent);
        long stamp = System.nanoTime();
        File temp = new File(parent, target.getName() + ".tmp-" + stamp);
        File old = new File(parent, target.getName() + ".old-" + stamp);
        try {
            try (ZipFile zip = new ZipFile(archive)) {
                List<ZipEntry> entries = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) entries.add(e.nextElement());
                unpack(zip, entries, "", temp);
                String bad = firstMismatch(entries, "", temp);
                if (bad != null) throw new IOException("Unpacked " + archive.getName() + " has a damaged file: " + bad);
            }
            if (target.exists() && !target.renameTo(old)) {
                deleteRecursively(target);
                if (target.exists()) throw new IOException("Can't replace " + target);
            }
            if (!temp.renameTo(target)) throw new IOException("Can't move " + temp + " to " + target);
        } finally {
            deleteRecursively(temp);
            deleteRecursively(old);
        }
    }

    /** Unpacks one entry of {@code archive} to {@code target} through a temp file that is renamed over it. */
    public static void extractFile(File archive, String entryName, File target) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) throw new IOException(archive.getName() + " has no " + entryName);
            repairFile(zip, entry, target);
        }
    }

    // ------------------------------------------------------------------ per library, every build

    /** What {@link #verify} did. */
    public static final class Report {
        /** Libraries (folder names) whose files were unpacked again. */
        public final List<String> repaired = new ArrayList<>();
        /** Libraries whose DEX file was unpacked again. */
        public final List<String> repairedDex = new ArrayList<>();
        /** Problems that couldn't be repaired (the build goes on; the failing step then says what is missing). */
        public final List<String> failed = new ArrayList<>();

        public boolean changed() {
            return !repaired.isEmpty() || !repairedDex.isEmpty();
        }
    }

    /**
     * Checks the unpacked files of each named built-in library (folder in {@code libs.zip}, {@code <name>.dex} in
     * {@code dexs.zip}) against the archives in {@code compileAssets} and unpacks a damaged one again. A file is
     * damaged when it is missing or its size differs from the archive entry. Never throws.
     *
     * @param compileAssets {@code cacheDir/libs}
     */
    public static Report verify(File compileAssets, Collection<String> libraryNames, Progress progress) {
        Report report = new Report();
        Set<String> names = new LinkedHashSet<>(libraryNames);
        File libsArchive = new File(compileAssets, "libs.zip");
        File libsDir = new File(compileAssets, "libs");
        File dexsArchive = new File(compileAssets, "dexs.zip");
        File dexsDir = new File(compileAssets, "dexs");
        File compiledLibs = new File(compileAssets.getParentFile(), "compiledLibs");
        try (ZipFile zip = new ZipFile(libsArchive)) {
            Map<String, List<ZipEntry>> byLibrary = group(zip);
            for (String name : names) {
                List<ZipEntry> entries = byLibrary.get(name);
                if (entries == null) continue;
                String bad = firstMismatch(entries, name + "/", new File(libsDir, name));
                if (bad == null) continue;
                if (progress != null) progress.show("Repairing built-in library " + name + "...");
                try {
                    repairFolder(zip, entries, name, libsDir);
                    // compiled from the damaged res/ folder: compiled again by this build
                    new File(compiledLibs, name + ".zip").delete();
                    report.repaired.add(name);
                    LogUtil.d(TAG, "Unpacked built-in library " + name + " again (" + bad + ")");
                } catch (IOException e) {
                    report.failed.add(name + ": " + e.getMessage());
                    LogUtil.e(TAG, "Couldn't repair built-in library " + name, e);
                }
            }
        } catch (IOException e) {
            report.failed.add(libsArchive.getName() + ": " + e.getMessage());
            LogUtil.e(TAG, "Couldn't read " + libsArchive, e);
        }
        try (ZipFile zip = new ZipFile(dexsArchive)) {
            for (String name : names) {
                ZipEntry entry = zip.getEntry(name + ".dex");
                if (entry == null || entry.isDirectory()) continue;
                File dex = new File(dexsDir, name + ".dex");
                if (dex.isFile() && dex.length() == entry.getSize()) continue;
                if (progress != null) progress.show("Repairing built-in library " + name + "...");
                try {
                    repairFile(zip, entry, dex);
                    report.repairedDex.add(name);
                    LogUtil.d(TAG, "Unpacked " + name + ".dex again");
                } catch (IOException e) {
                    report.failed.add(name + ".dex: " + e.getMessage());
                    LogUtil.e(TAG, "Couldn't repair " + dex, e);
                }
            }
        } catch (IOException e) {
            report.failed.add(dexsArchive.getName() + ": " + e.getMessage());
            LogUtil.e(TAG, "Couldn't read " + dexsArchive, e);
        }
        return report;
    }

    /**
     * Checks a single-entry archive's unpacked file ({@code android.jar.zip} -> {@code android.jar}): true when it is
     * there with the archive's size. Never throws.
     */
    public static boolean unpackedFileIntact(File archive, File directory) {
        try (ZipFile zip = new ZipFile(archive)) {
            String bad = firstMismatch(list(zip), "", directory);
            return bad == null;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ a step failed anyway

    /**
     * The built-in library a failed step's log blames for a missing or damaged file, or null (see
     * {@link com.sketchware.ai.doctor.BuildDoctor#builtInLibraryOf}).
     */
    public static String blamedLibrary(String failure) {
        return com.sketchware.ai.doctor.BuildDoctor.builtInLibraryOf(failure);
    }

    /**
     * After a failed build: when the failure names a file of a built-in library, that library's folder and DEX file
     * are deleted and unpacked again from the archives. Returns the library's name when it was repaired, else null.
     */
    public static String recover(File compileAssets, String failure) {
        String name = blamedLibrary(failure);
        if (name == null) return null;
        File folder = new File(new File(compileAssets, "libs"), name);
        File dex = new File(new File(compileAssets, "dexs"), name + ".dex");
        // Damaged in a way sizes don't show (a same-size corrupt file): drop both copies, then unpack them again.
        moveAsideAndDelete(folder);
        dex.delete();
        List<String> one = new ArrayList<>();
        one.add(name);
        Report report = verify(compileAssets, one, null);
        boolean back = new File(folder, "classes.jar").isFile() || dex.isFile() || report.changed();
        return back && report.failed.isEmpty() ? name : null;
    }

    /** The build a user starts may restart once after {@link #recover}; later failures are shown. */
    public static synchronized void userStartedBuild() {
        retryUsed = false;
    }

    /** True the first time after {@link #userStartedBuild}. */
    public static synchronized boolean claimRetry() {
        if (retryUsed) return false;
        retryUsed = true;
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, List<ZipEntry>> group(ZipFile zip) {
        Map<String, List<ZipEntry>> byLibrary = new LinkedHashMap<>();
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();
            String name = entry.getName();
            int slash = name.indexOf('/');
            if (slash <= 0) continue;
            String library = name.substring(0, slash);
            List<ZipEntry> list = byLibrary.get(library);
            if (list == null) byLibrary.put(library, list = new ArrayList<>());
            list.add(entry);
        }
        return byLibrary;
    }

    private static List<ZipEntry> list(ZipFile zip) {
        List<ZipEntry> entries = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) entries.add(e.nextElement());
        return entries;
    }

    /** The first entry (name relative to {@code root}) whose unpacked copy is missing or has another size, or null. */
    static String firstMismatch(List<ZipEntry> entries, String prefix, File root) {
        for (ZipEntry entry : entries) {
            String relative = entry.getName().substring(prefix.length());
            if (relative.isEmpty()) continue;
            File file = new File(root, relative);
            if (entry.isDirectory()) {
                if (!file.isDirectory()) return relative + " (missing folder)";
            } else if (!file.isFile()) {
                return relative + " (missing)";
            } else if (entry.getSize() >= 0 && file.length() != entry.getSize()) {
                return relative + " (" + file.length() + " bytes, " + entry.getSize() + " expected)";
            }
        }
        return null;
    }

    private static void repairFolder(ZipFile zip, List<ZipEntry> entries, String name, File libsDir) throws IOException {
        long stamp = System.nanoTime();
        File temp = new File(libsDir, name + ".tmp-" + stamp);
        File old = new File(libsDir, name + ".old-" + stamp);
        File target = new File(libsDir, name);
        try {
            unpack(zip, entries, name + "/", temp);
            String bad = firstMismatch(entries, name + "/", temp);
            if (bad != null) throw new IOException("unpacked copy is damaged: " + bad);
            if (target.exists() && !target.renameTo(old)) {
                deleteRecursively(target);
            }
            if (!temp.renameTo(target)) {
                // another build put its copy there first: fine when that one is complete
                if (firstMismatch(entries, name + "/", target) != null) throw new IOException("can't move " + temp + " into place");
            }
        } finally {
            deleteRecursively(temp);
            deleteRecursively(old);
        }
    }

    private static void repairFile(ZipFile zip, ZipEntry entry, File target) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("can't create " + parent);
        File temp = new File(parent, target.getName() + ".tmp-" + System.nanoTime());
        try {
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(temp)) {
                copy(in, out);
            }
            if (temp.length() != entry.getSize()) throw new IOException("unpacked " + entry.getName() + " is cut off");
            if (!temp.renameTo(target)) {
                target.delete();
                if (!temp.renameTo(target)) throw new IOException("can't move " + temp + " into place");
            }
        } finally {
            temp.delete();
        }
    }

    private static void unpack(ZipFile zip, List<ZipEntry> entries, String prefix, File root) throws IOException {
        String canonicalRoot = root.getCanonicalPath() + File.separator;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("can't create " + root);
        byte[] buffer = new byte[64 * 1024];
        for (ZipEntry entry : entries) {
            String relative = entry.getName().substring(prefix.length());
            if (relative.isEmpty()) continue;
            File file = new File(root, relative);
            if (!file.getCanonicalPath().startsWith(canonicalRoot)) throw new IOException("bad entry name " + entry.getName());
            if (entry.isDirectory()) {
                if (!file.isDirectory() && !file.mkdirs()) throw new IOException("can't create " + file);
                continue;
            }
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("can't create " + parent);
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(file)) {
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    }

    private static void moveAsideAndDelete(File folder) {
        if (!folder.exists()) return;
        File aside = new File(folder.getParentFile(), folder.getName() + ".old-" + System.nanoTime());
        if (folder.renameTo(aside)) deleteRecursively(aside);
        else deleteRecursively(folder);
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) {
                // never follow a link out of the folder
                if (isLink(child)) child.delete();
                else deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static boolean isLink(File file) {
        try {
            File parent = file.getParentFile().getCanonicalFile();
            return !new File(parent, file.getName()).getCanonicalFile().equals(new File(parent, file.getName()).getAbsoluteFile());
        } catch (IOException e) {
            return true;
        }
    }
}
