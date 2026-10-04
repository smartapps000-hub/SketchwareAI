package com.sketchware.ai.project;

import com.google.gson.Gson;
import com.sketchware.ai.library.MavenFetcher;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import mod.agus.jcoderz.lib.FilePathUtil;
import mod.agus.jcoderz.lib.FileUtil;
import mod.jbk.util.LogUtil;

/**
 * Checked before a build compiles anything: every local library the project has enabled must
 * really have its classes.jar. One that doesn't is restored, first from what is still on the
 * phone (an .aar/.jar left in its folder), then by downloading the very same library again:
 * the coordinate recorded when it was installed, or a well-known library's coordinate. A name
 * alone is never looked up in a repository: other people publish look-alike artifacts under
 * the same name. Only the missing files are added; res, manifest and config that are still in
 * the folder stay as they are. When the library can't be identified the build goes on and says so.
 */
public final class LibraryIntegrity {

    private static final String TAG = "LibraryIntegrity";
    /** Written into a library's folder when it is downloaded: where it came from. */
    public static final String SOURCE_FILE = "source.json";

    /** Libraries known by their artifact name alone. */
    private static final String[][] GROUPS = {
            {"AestheticDialogs", "com.github.gabriel-TheCode"},
            {"ESCPOS-ThermalPrinter-Android", "com.github.DantSu"},
            {"MPAndroidChart", "com.github.PhilJay"},
            {"usb-serial-for-android", "com.github.mik3y"},
            {"BlurView", "com.github.Dimezis"},
            {"zxing-android-embedded", "com.journeyapps"},
    };

    public static final class Source {
        public String coordinate;
        public String url;
    }

    private LibraryIntegrity() {
    }

    /**
     * @return what was done or couldn't be done, for the build's warnings; empty when every
     * enabled library is complete
     */
    public static List<String> restore(String scId, LibraryHeal.Progress progress) {
        List<String> notes = new ArrayList<>();
        try {
            String listPath = new FilePathUtil().getPathLocalLibrary(scId);
            if (!FileUtil.isExistFile(listPath)) return notes;
            List<HashMap<String, Object>> libraries = new Gson().fromJson(FileUtil.readFile(listPath), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
            if (libraries == null) return notes;
            File root = LibraryHeal.localLibs();
            boolean changed = false;
            for (HashMap<String, Object> library : libraries) {
                Object name = library.get("name");
                if (!(name instanceof String) || !safeName((String) name)) continue;
                File directory = new File(root, (String) name);
                File jar = new File(directory, "classes.jar");
                File dex = new File(directory, "classes.dex");
                Object listedJar = library.get("jarPath");
                boolean jarListedButGone = listedJar instanceof String && !new File((String) listedJar).isFile();
                if (jar.isFile()) {
                    // Complete but for the dex (an earlier restore that couldn't finish): make it now.
                    if (!dex.isFile()) {
                        if (progress != null) progress.show("Restoring library " + name + "...");
                        if (finish(directory, null)) {
                            LibraryAutoResolve.refresh(library, directory);
                            changed = true;
                            notes.add("auto-fix: local library " + name + " had no classes.dex; it was made from its classes.jar.");
                        } else {
                            notes.add("Local library '" + name + "' has no classes.dex and it couldn't be made, so its classes "
                                    + "won't be in the app. Download it again in Local library manager.");
                        }
                    } else if (jarListedButGone) {
                        // The files are fine; only the project's entry points somewhere else.
                        changed |= LibraryAutoResolve.refresh(library, directory);
                    }
                    continue;
                }
                // No classes.jar. A library that never had one (resources only) is left alone: it is
                // restored only if the project lists a jar for it, or its dex shows it had classes.
                if (!jarListedButGone && !dex.isFile() && !hasArchive(directory)
                        && coordinate((String) name, directory) == null) continue;
                Long failedAt = FAILED.get(name);
                if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) {
                    notes.add(missingNote((String) name, "a download was tried a few minutes ago and failed; it is tried again later."));
                    continue;
                }
                if (progress != null) progress.show("Restoring library " + name + "...");
                String how = restoreOne((String) name, directory, root);
                if (jar.isFile() && dex.isFile()) {
                    LibraryAutoResolve.refresh(library, directory);
                    changed = true;
                    FAILED.remove(name);
                    notes.add("auto-fix: local library " + name + " had no classes.jar; " + how);
                } else {
                    notes.add(missingNote((String) name, how));
                }
            }
            if (changed) LibraryHeal.save(scId, libraries);
        } catch (Throwable t) {
            LogUtil.e(TAG, "Library check failed", t);
        }
        return notes;
    }

    private static boolean hasArchive(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && !lower.equals("classes.jar") && (lower.endsWith(".aar") || lower.endsWith(".jar"))) return true;
        }
        return false;
    }

    private static final long RETRY_AFTER_MS = 5 * 60_000L;
    /** Libraries whose download failed, and when: a build shortly after doesn't wait for the network again. */
    private static final java.util.Map<Object, Long> FAILED = new java.util.concurrent.ConcurrentHashMap<>();

    private static String missingNote(String name, String why) {
        return "Local library '" + name + "' is enabled but its classes.jar is missing and it couldn't be restored: "
                + why + " Download it again in Local library manager (or Import file with its .aar/.jar), or switch "
                + "it off if the project doesn't use it.";
    }

    /** A library's name is its folder name: one path segment, nothing that leads out of local_libs. */
    private static boolean safeName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..") && name.indexOf('/') < 0
                && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /** @return how the library was restored, or why it wasn't */
    private static String restoreOne(String name, File directory, File root) {
        // 1. From what is still on the phone.
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (!file.isFile() || lower.equals("classes.jar")) continue;
                if (!(lower.endsWith(".aar") || lower.endsWith(".jar"))) continue;
                if (addMissingFiles(file, directory, root) && finish(directory, null)) {
                    return "it was restored from " + file.getName() + " in its folder.";
                }
            }
        }
        // 2. The same library again from its repository.
        String coordinate = coordinate(name, directory);
        if (coordinate == null) {
            return "its folder has no .aar/.jar to restore it from, and nothing records where it was downloaded from.";
        }
        String[] gav = MavenFetcher.parse(coordinate);
        MavenFetcher.Result fetched = LibraryHeal.fetch(coordinate, root);
        if (fetched.file == null) {
            FAILED.put(name, System.currentTimeMillis());
            return coordinate + " couldn't be downloaded ("
                    + (fetched.networkFailed ? "no internet connection" : "not found in Google Maven, Maven Central or JitPack") + ").";
        }
        boolean added = addMissingFiles(fetched.file, directory, root);
        fetched.file.delete();
        if (!added) return coordinate + " was downloaded, but it holds no classes.";
        if (!new File(directory, SOURCE_FILE).isFile()) writeSource(directory, coordinate, fetched.url);
        if (!finish(directory, gav[0])) return coordinate + " was downloaded, but couldn't be converted to dex.";
        return coordinate + " was downloaded again; its classes.jar and whatever else was missing were put back.";
    }

    /**
     * Unpacks the archive next to the library and copies into the library's folder only what the
     * folder doesn't have: a res folder, manifest or config that is still there is never replaced.
     *
     * @return true if the folder has a classes.jar afterwards
     */
    private static boolean addMissingFiles(File archive, File directory, File root) {
        File staging = new File(root, ".restore_tmp");
        try {
            deleteTree(staging);
            File copy = new File(root, ".restore_tmp.archive");
            copy(archive, copy);
            String reason = MavenFetcher.install(copy, staging);
            copy.delete();
            if (reason != null) return false;
            directory.mkdirs();
            File[] unpacked = staging.listFiles();
            if (unpacked != null) {
                for (File item : unpacked) {
                    File target = new File(directory, item.getName());
                    if (target.exists()) continue;
                    if (!item.renameTo(target)) copyTree(item, target);
                }
            }
            return new File(directory, "classes.jar").isFile();
        } catch (Exception e) {
            LogUtil.e(TAG, "Couldn't restore " + directory + " from " + archive, e);
            return false;
        } finally {
            deleteTree(staging);
        }
    }

    /** Package name file and dex for a restored folder. */
    static boolean finish(File directory, String group) {
        try {
            LibraryHeal.writeConfig(directory, group);
            if (!new File(directory, "classes.dex").isFile()) LibraryHeal.dex(new File(directory, "classes.jar"));
            return new File(directory, "classes.dex").isFile();
        } catch (Throwable t) {
            LogUtil.e(TAG, "Couldn't finish " + directory, t);
            return false;
        }
    }

    /**
     * Where to download a library again: what was recorded when it was installed, or the
     * coordinate of a well-known library with exactly this folder name ("artifact_V_version").
     *
     * @return "group:artifact:version", or null when that isn't known
     */
    static String coordinate(String name, File directory) {
        Source source = readSource(directory);
        if (source != null && MavenFetcher.parse(source.coordinate) != null) return source.coordinate;
        int split = name.lastIndexOf("_V_");
        if (split <= 0 || split + 3 >= name.length()) return null;
        String artifact = name.substring(0, split);
        String version = name.substring(split + 3);
        for (String known : LibraryAutoResolve.knownCoordinates()) {
            String[] gav = MavenFetcher.parse(known);
            if (gav != null && gav[1].equals(artifact) && gav[2].equals(version)) return known;
        }
        for (String[] group : GROUPS) {
            if (group[0].equals(artifact)) return group[1] + ":" + artifact + ":" + version;
        }
        return null;
    }

    public static Source readSource(File directory) {
        try {
            File file = new File(directory, SOURCE_FILE);
            if (!file.isFile()) return null;
            return new Gson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Source.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Records where a downloaded library came from, so it can be fetched again exactly. */
    public static void writeSource(File directory, String coordinate, String url) {
        try {
            Source source = new Source();
            source.coordinate = coordinate;
            source.url = url;
            FileUtil.writeFile(new File(directory, SOURCE_FILE).getAbsolutePath(), new Gson().toJson(source));
        } catch (RuntimeException e) {
            // The library works without the record.
        }
    }

    private static void copyTree(File from, File to) throws java.io.IOException {
        if (from.isDirectory()) {
            if (!to.isDirectory() && !to.mkdirs()) throw new java.io.IOException("can't create " + to);
            File[] children = from.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(to, child.getName()));
        } else {
            copy(from, to);
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void copy(File from, File to) throws java.io.IOException {
        try (InputStream in = new java.io.FileInputStream(from); java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }
}
