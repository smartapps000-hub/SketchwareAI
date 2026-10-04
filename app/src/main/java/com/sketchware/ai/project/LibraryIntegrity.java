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
 * phone (an .aar/.jar left in its folder, or the project's own backup), then by downloading
 * the very same library again: the coordinate recorded when it was installed, the coordinate
 * its folder name spells out, or a well-known library's coordinate. A name alone is never
 * looked up in a repository: other people publish look-alike artifacts under the same name.
 * Only the missing files are added; res, manifest and config that are still in the folder stay
 * as they are. A library that can't be restored is left out of the build ({@link #usable}),
 * so a path to a file that isn't there never reaches the compiler or the dexer.
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
            List<File> backups = null;
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
                        && candidates((String) name, directory).isEmpty()) continue;
                Long failedAt = FAILED.get(name);
                if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) {
                    notes.add(missingNote((String) name, "a download was tried a few minutes ago and failed; it is tried again later."));
                    continue;
                }
                if (progress != null) progress.show("Restoring library " + name + "...");
                if (backups == null) backups = backups(scId);
                String how = restoreOne((String) name, directory, root, backups);
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
            if (file.isFile() && isLibraryArchive(file.getName())) return true;
        }
        return false;
    }

    /**
     * An .aar/.jar left in a library's folder that can be the library itself. Not the parts of an
     * unpacked .aar (lint.jar), sources or javadoc, or a leftover of an interrupted install.
     */
    private static boolean isLibraryArchive(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".aar") || lower.endsWith(".jar"))) return false;
        return !lower.equals("classes.jar") && !lower.equals("lint.jar") && !lower.startsWith("_inner_")
                && !lower.endsWith("-sources.jar") && !lower.endsWith("-javadoc.jar") && !lower.startsWith(".");
    }

    private static final long RETRY_AFTER_MS = 5 * 60_000L;
    /** Libraries whose download failed, and when: a build shortly after doesn't wait for the network again. */
    private static final java.util.Map<Object, Long> FAILED = new java.util.concurrent.ConcurrentHashMap<>();

    private static String missingNote(String name, String why) {
        return "Local library '" + name + "' is enabled but its classes.jar is missing and it couldn't be restored: "
                + why + " It is left out of this build. Download it again in Local library manager (or Import file "
                + "with its .aar/.jar), or switch it off if the project doesn't use it.";
    }

    /** A library's name is its folder name: one path segment, nothing that leads out of local_libs. */
    private static boolean safeName(String name) {
        return !name.isEmpty() && !name.equals(".") && !name.equals("..") && name.indexOf('/') < 0
                && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /** @return how the library was restored, or why it wasn't */
    private static String restoreOne(String name, File directory, File root, List<File> backups) {
        // 1. From what is still on the phone.
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !isLibraryArchive(file.getName())) continue;
                if (addMissingFiles(file, directory, root) && finish(directory, null)) {
                    return "it was restored from " + file.getName() + " in its folder.";
                }
            }
        }
        // 2. From the project's own backup, if it was made with its local libraries.
        try {
            for (File archive : backups) {
                if (ProjectBackups.findLibrary(java.util.Collections.singletonList(archive), name) == null) continue;
                int written = ProjectBackups.extractTree(archive, "local_libs/" + name, directory);
                if (written > 0 && new File(directory, "classes.jar").isFile() && finish(directory, null)) {
                    return "it was restored from this project's backup " + archive.getName() + ".";
                }
            }
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Couldn't restore " + name + " from a backup", e);
        }
        // 3. The same library again from its repository.
        List<Candidate> candidates = candidates(name, directory);
        if (candidates.isEmpty()) {
            return "its folder has no .aar/.jar to restore it from, this project has no backup that holds it, and "
                    + "neither its name nor a download record says which library it is.";
        }
        String failure = null;
        for (Candidate candidate : candidates) {
            String[] gav = MavenFetcher.parse(candidate.coordinate);
            MavenFetcher.Result fetched = LibraryHeal.fetch(candidate.coordinate, root);
            if (fetched.file == null) {
                if (fetched.networkFailed) {
                    FAILED.put(name, System.currentTimeMillis());
                    return candidate.coordinate + " couldn't be downloaded (no internet connection).";
                }
                if (failure == null) {
                    failure = candidate.coordinate + " couldn't be downloaded (not found in Google Maven, Maven Central or JitPack).";
                }
                continue;
            }
            // A library known by its folder name must hold the package it is known for.
            if (candidate.mustContain != null && !holdsPackage(fetched.file, candidate.mustContain)) {
                fetched.file.delete();
                failure = candidate.coordinate + " was downloaded, but it isn't the library this name stands for (no "
                        + candidate.mustContain.replace('/', '.') + " in it), so it wasn't used.";
                continue;
            }
            boolean added = addMissingFiles(fetched.file, directory, root);
            fetched.file.delete();
            if (!added) {
                failure = candidate.coordinate + " was downloaded, but it holds no classes.";
                continue;
            }
            if (!new File(directory, SOURCE_FILE).isFile()) writeSource(directory, candidate.coordinate, fetched.url);
            if (!finish(directory, gav[0])) return candidate.coordinate + " was downloaded, but couldn't be converted to dex.";
            return candidate.coordinate + " was downloaded again; its classes.jar and whatever else was missing were put back.";
        }
        FAILED.put(name, System.currentTimeMillis());
        return failure;
    }

    /** Whether an .aar/.jar (or the classes.jar inside an .aar) has classes under {@code packagePath} ("com/google/zxing"). */
    private static boolean holdsPackage(File archive, String packagePath) {
        String prefix = packagePath + "/";
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive)) {
            java.util.zip.ZipEntry inner = zip.getEntry("classes.jar");
            if (inner == null) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (entry.startsWith(prefix) && entry.endsWith(".class")) return true;
                }
                return false;
            }
            try (java.util.zip.ZipInputStream classes = new java.util.zip.ZipInputStream(zip.getInputStream(inner))) {
                java.util.zip.ZipEntry entry;
                while ((entry = classes.getNextEntry()) != null) {
                    if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".class")) return true;
                }
            }
        } catch (Exception e) {
            // Unreadable: not proven.
        }
        return false;
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

    /** A coordinate to try, and for a library known only by its name the package its jar must hold. */
    static final class Candidate {
        final String coordinate;
        final String mustContain;

        Candidate(String coordinate, String mustContain) {
            this.coordinate = coordinate;
            this.mustContain = mustContain;
        }
    }

    private static final java.util.regex.Pattern SPELLED_OUT = java.util.regex.Pattern.compile(
            "^([A-Za-z][\\w]*(?:\\.[\\w]+)+)-(.+?)-(v?\\d[\\w.+-]*)$");

    /**
     * Where to download a library again, most certain first:
     * <ol>
     *     <li>what was recorded when it was installed (source.json);</li>
     *     <li>a folder name that spells the whole coordinate, "group-artifact-version" with a
     *     dotted group ("com.google.zxing-core-3.4.0");</li>
     *     <li>"artifact_V_version" or "artifact-vversion" ("core-v3.4.1") when a well-known
     *     library has exactly that artifact and version; what is downloaded must then hold the
     *     package the library is known for.</li>
     * </ol>
     *
     * @return the coordinates to try in order; empty when the library can't be identified
     */
    static List<Candidate> candidates(String name, File directory) {
        List<Candidate> out = new ArrayList<>();
        Source source = readSource(directory);
        if (source != null && validCoordinate(source.coordinate)) {
            out.add(new Candidate(source.coordinate, null));
            return out;
        }
        java.util.regex.Matcher spelled = SPELLED_OUT.matcher(name);
        if (spelled.matches()) {
            String coordinate = spelled.group(1) + ":" + spelled.group(2) + ":" + spelled.group(3);
            // "socket.io-client-2.1.0" also has this shape, and its "group" is not one. What is
            // downloaded must hold classes under the group's own package, or it isn't used.
            String[] segments = spelled.group(1).split("\\.");
            if (validCoordinate(coordinate) && segments.length >= 2) {
                out.add(new Candidate(coordinate, segments[0] + "/" + segments[1]));
            }
            return out;
        }
        String artifact;
        List<String> versions = new ArrayList<>();
        int split = name.lastIndexOf("_V_");
        if (split > 0 && split + 3 < name.length()) {
            artifact = name.substring(0, split);
            versions.add(name.substring(split + 3));
        } else {
            // Sketchware Pro's own downloader: "<artifact>-v<version>".
            split = name.lastIndexOf("-v");
            if (split <= 0 || split + 2 >= name.length() || !Character.isDigit(name.charAt(split + 2))) return out;
            artifact = name.substring(0, split);
            versions.add(name.substring(split + 2));
            versions.add(name.substring(split + 1)); // JitPack tags: "v3.1.0"
        }
        for (String version : versions) {
            for (String[] known : LibraryAutoResolve.knownLibraries()) {
                String[] gav = MavenFetcher.parse(known[1]);
                if (gav != null && gav[1].equals(artifact) && gav[2].equals(version)) {
                    out.add(new Candidate(known[1], known[0].replace('.', '/')));
                }
            }
        }
        for (String version : versions) {
            for (String[] group : GROUPS) {
                String coordinate = group[1] + ":" + artifact + ":" + version;
                boolean listed = false;
                for (Candidate candidate : out) if (candidate.coordinate.equals(coordinate)) listed = true;
                if (group[0].equals(artifact) && !listed) out.add(new Candidate(coordinate, null));
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern COORDINATE_PART = java.util.regex.Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.+-]*");

    /** "group:artifact:version" whose parts can't lead out of a repository path or a folder. */
    static boolean validCoordinate(String coordinate) {
        String[] gav = MavenFetcher.parse(coordinate);
        if (gav == null) return false;
        for (String part : gav) {
            if (!COORDINATE_PART.matcher(part).matches() || part.contains("..")) return false;
        }
        return true;
    }

    /** This project's own backups, newest first; empty when its name and package can't be read. */
    private static List<File> backups(String scId) {
        try {
            HashMap<String, Object> meta = a.a.a.lC.b(scId);
            if (meta == null) return new ArrayList<>();
            return ProjectBackups.archives(new File(mod.hey.studios.project.backup.BackupFactory.getBackupDir()),
                    a.a.a.yB.c(meta, "my_sc_pkg_name"), a.a.a.yB.c(meta, "my_ws_name"));
        } catch (Throwable t) {
            LogUtil.e(TAG, "Couldn't list the project's backups", t);
            return new ArrayList<>();
        }
    }

    /**
     * Takes out of a build's library list every entry that names a classes.jar or classes.dex
     * that isn't there. The project's own list is not changed: the library stays enabled, and
     * the next build tries to restore it again.
     *
     * @param libraries the list one build works with (not the saved one)
     * @return the names that were left out
     */
    public static List<String> usable(List<HashMap<String, Object>> libraries) {
        return usable(null, libraries);
    }

    /** Entries a build left out, by project: {position in the project's list, entry}. */
    private static final java.util.Map<String, List<Object[]>> LEFT_OUT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * As {@link #usable(List)}, for the list a build compiles with. The entries taken out are
     * remembered, so that code which saves the build's list ({@link LibraryHeal#run} when it
     * enables another library) writes them back with {@link #withLeftOut}: being left out of a
     * build never switches a library off in the project.
     */
    public static List<String> usable(String scId, List<HashMap<String, Object>> libraries) {
        List<String> leftOut = new ArrayList<>();
        List<Object[]> entries = new ArrayList<>();
        if (libraries != null) {
            int position = 0;
            for (java.util.Iterator<HashMap<String, Object>> it = libraries.iterator(); it.hasNext(); position++) {
                HashMap<String, Object> library = it.next();
                Object jar = library.get("jarPath");
                Object dex = library.get("dexPath");
                boolean jarGone = jar instanceof String && !((String) jar).isEmpty() && !new File((String) jar).isFile();
                boolean dexGone = dex instanceof String && !((String) dex).isEmpty() && !new File((String) dex).isFile();
                if (!jarGone && !dexGone) continue;
                it.remove();
                leftOut.add(String.valueOf(library.get("name")));
                entries.add(new Object[]{position, library});
            }
        }
        if (scId != null) {
            if (entries.isEmpty()) LEFT_OUT.remove(scId);
            else LEFT_OUT.put(scId, entries);
        }
        return leftOut;
    }

    /** The build's list with the entries {@link #usable(String, List)} took out put back in their places. */
    @SuppressWarnings("unchecked")
    public static List<HashMap<String, Object>> withLeftOut(String scId, List<HashMap<String, Object>> libraries) {
        List<Object[]> entries = scId == null ? null : LEFT_OUT.get(scId);
        if (entries == null || entries.isEmpty()) return libraries;
        List<HashMap<String, Object>> all = new ArrayList<>(libraries);
        for (Object[] entry : entries) {
            HashMap<String, Object> library = (HashMap<String, Object>) entry[1];
            boolean present = false;
            for (HashMap<String, Object> other : all) {
                if (String.valueOf(other.get("name")).equals(String.valueOf(library.get("name")))) present = true;
            }
            if (present) continue;
            int position = Math.min((Integer) entry[0], all.size());
            all.add(position, library);
        }
        return all;
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
