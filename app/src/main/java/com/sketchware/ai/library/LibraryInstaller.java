package com.sketchware.ai.library;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * Library Resolver (v1.8.8): installs a resolved plan as local libraries (/.sketchware/libs/local_libs/&lt;artifact&gt;_V_
 * &lt;version&gt;, as the old downloader named them: classes.jar, classes.dex, res, assets, AndroidManifest.xml, config,
 * source.json, needs.json) and switches them on in a project. Archives come from the resolver's cache, so nothing is
 * downloaded twice; a library folder that is there already is used as it is.
 */
public final class LibraryInstaller {

    public interface Progress {
        void status(String text);
    }

    public static final class Outcome {
        public final List<String> installed = new ArrayList<>();
        public final List<String> reused = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
        /** Library folder names switched on in the project. */
        public final List<String> enabled = new ArrayList<>();
        /** Other versions of the same libraries switched off in the project. */
        public final List<String> replaced = new ArrayList<>();
        /** v1.8.11: things the user should know (jars an .aar bundles that aren't built). */
        public final List<String> warnings = new ArrayList<>();
        /** v1.8.12: archives without Android code (C++ Prefab packages, source zips): left out, listed in one line. */
        public final List<String> skipped = new ArrayList<>();
        /** v1.8.13: a higher version of the same library was on and stays on (folder names). */
        public final List<String> keptNewer = new ArrayList<>();
        /** v1.8.13: why nothing was switched on in the project (its library list can't be read), or null. */
        public String notSwitchedOn;
        /** v1.8.13: the library installed is lower than the version on in the project: nothing of it was switched on. */
        public boolean rootKeptBack;

        public boolean ok() {
            return failed.isEmpty();
        }
    }

    private LibraryInstaller() {
    }

    public static File root() {
        return com.sketchware.ai.project.LibraryHeal.localLibs();
    }

    public static String folderName(MavenResolver.Artifact a) {
        return (a.name + "_V_" + a.version).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * @param scId project to switch the libraries on in, or null (only installed)
     */
    public static Outcome install(MavenResolver resolver, MavenResolver.Plan plan, String scId, Progress progress) {
        Outcome outcome = new Outcome();
        List<MavenResolver.Artifact> order = MavenResolver.installOrder(plan.artifacts);
        List<String> ready = new ArrayList<>();
        java.util.Set<String> leftOut = new java.util.HashSet<>();   // v1.8.12: group:artifact without Android code
        // v1.8.13: group:artifact -> the highest version of it that is on in this project (read once)
        HashMap<String, String[]> on = scId == null ? new HashMap<String, String[]>() : highestOn(enabled(scId));
        int steps = 0, step = 0;
        for (MavenResolver.Artifact a : order) if (!"pom".equals(a.packaging)) steps++;
        for (MavenResolver.Artifact a : order) {
            if (MavenResolver.cancelledHere()) break;   // v1.8.12: Cancel in the progress dialog
            if ("pom".equals(a.packaging)) continue;   // nothing to install, only what it needs
            String name = folderName(a);
            File dir = new File(root(), name);
            String counter = steps > 1 ? " (" + (++step) + "/" + steps + ")" : "";
            try {
                if (a != plan.root && !usable(dir)) {
                    // v1.8.13: a library this one needs is on in this project in a higher version: that one is used
                    // (Gradle's highest-version rule: the project asks for it too), nothing is downloaded
                    String[] newer = on.get(a.group + ":" + a.name);
                    if (newer != null && MavenResolver.compareVersions(newer[1], a.version) >= 0
                            && usable(new File(root(), newer[0]))) {
                        outcome.reused.add(newer[0]);
                        ready.add(newer[0]);
                        continue;
                    }
                }
                if (usable(dir)) {
                    outcome.reused.add(name);
                } else {
                    if (progress != null) progress.status("Fetching the " + (a.packaging == null ? "archive" : "." + a.packaging)
                            + counter + "\n" + a.coordinate());
                    File archive = resolver.archive(a, plan);
                    if (archive == null && MavenResolver.cancelledHere()) break;   // cut off by Cancel, not missing
                    if (archive == null) {
                        outcome.failed.add(a.coordinate() + ": no repository has its .aar or .jar" + hostLinks(a.group, a.name, a.version));
                        continue;
                    }
                    String noCode = MavenFetcher.withoutAndroidCode(archive);
                    if ("not an archive".equals(noCode)) {
                        outcome.failed.add(a.coordinate() + ": the download isn't an .aar or .jar (a web page?)");
                        continue;
                    }
                    if (noCode != null) {
                        outcome.skipped.add(a.name + " " + a.version + " (" + noCode + ")");
                        leftOut.add(a.group + ":" + a.name);
                        continue;
                    }
                    if (progress != null) progress.status("Unpacking" + counter + "\n" + a.coordinate());
                    File staging = new File(root(), "." + name + ".installing");
                    FileUtil.deleteFile(staging.getAbsolutePath());
                    String reason = MavenFetcher.install(archive, staging);
                    if (reason != null) {
                        FileUtil.deleteFile(staging.getAbsolutePath());
                        outcome.failed.add(a.coordinate() + ": " + reason + hostLinks(a.group, a.name, a.version));
                        continue;
                    }
                    // the package name is the library's R package: an .aar's manifest package. A plain .jar has no
                    // resources, so it gets none (its Maven group is no Java package: the generated code would import a
                    // package that only the on-phone build's empty R class makes exist, and Gradle exports fail on it)
                    com.sketchware.ai.project.LibraryHeal.writeConfig(staging, null);
                    if (progress != null) progress.status("D8 dexing" + counter + "\n" + a.coordinate());
                    dex(new File(staging, "classes.jar"));
                    com.sketchware.ai.project.LibraryIntegrity.writeSource(staging, a.coordinate(), a.repository);
                    FileUtil.deleteFile(dir.getAbsolutePath());
                    if (!staging.renameTo(dir)) {
                        FileUtil.deleteFile(staging.getAbsolutePath());
                        outcome.failed.add(a.coordinate() + ": couldn't move it into " + dir);
                        continue;
                    }
                    outcome.installed.add(name);
                    String bundled = MavenFetcher.bundledJarsWarning(a.coordinate(), dir);
                    if (bundled != null) outcome.warnings.add(bundled);
                }
                LibraryNeeds needs = LibraryNeeds.create(a.coordinate());
                for (String need : a.needs) {
                    String[] gav = need.split(":");
                    String key = gav[0] + ":" + gav[1];
                    String builtIn = BuiltInCoordinates.name(key);
                    if (builtIn != null) {
                        if (!needs.builtIn.contains(builtIn)) needs.builtIn.add(builtIn);
                    } else if (!leftOut.contains(key)) {   // a C++ / source-only dependency isn't asked for again
                        needs.requires.add(need);
                    }
                }
                needs.write(dir);
                keepPom(resolver, a, dir);
                ready.add(name);
            } catch (Throwable t) {
                FileUtil.deleteFile(new File(root(), "." + name + ".installing").getAbsolutePath());
                outcome.failed.add(a.coordinate() + ": " + (t.getMessage() != null ? t.getMessage() : t.toString()));
            }
        }
        // v1.8.12: cancelled: what was installed stays installed, nothing is switched on half-way
        if (MavenResolver.cancelledHere()) return outcome;
        if (scId != null && !ready.isEmpty()) enable(scId, folderName(plan.root), ready, outcome);
        return outcome;
    }

    /**
     * v1.8.11: where a library without a usable archive can be looked at: JitPack's build page (its log says why no
     * .aar was made) and the GitHub releases, for com.github coordinates; empty for anything else.
     */
    static String hostLinks(String group, String artifact, String version) {
        if (group == null || artifact == null || !group.startsWith("com.github.")) return "";
        // com.github.User:Repo:v, or a module of a multi-module build: com.github.User.Repo:module:v
        String[] parts = group.substring("com.github.".length()).split("\\.");
        if (parts.length == 0 || parts[0].isEmpty()) return "";
        String user = parts[0], repo = parts.length > 1 ? parts[1] : artifact;
        return "\nJitPack build log: https://jitpack.io/#" + user + "/" + repo + (version == null ? "" : "/" + version)
                + "\nReleases (.aar / .jar): https://github.com/" + user + "/" + repo + "/releases";
    }

    /** v1.8.9: the library's POM copied next to it (pom.xml) for the Library Resolver's offline (i). */
    private static void keepPom(MavenResolver resolver, MavenResolver.Artifact a, File dir) {
        File target = new File(dir, "pom.xml");
        if (target.isFile() || resolver == null || resolver.cacheDir() == null) return;
        File pom = new File(resolver.cacheDir(), MavenResolver.path(a.group, a.name, a.version, "pom"));
        if (!pom.isFile()) return;
        String text = FileUtil.readFile(pom.getAbsolutePath());
        if (text != null && !text.isEmpty()) FileUtil.writeFile(target.getAbsolutePath(), text);
    }

    /**
     * classes.jar → classes.dex next to it: D8 on Android 8 and newer; below that D8 doesn't run and Dx is used, which
     * reads class files up to Java 7.
     */
    public static void dex(File classesJar) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            com.sketchware.ai.project.LibraryHeal.dex(classesJar);
            return;
        }
        if (!mod.hey.studios.lib.JarCheck.checkJar(classesJar.getAbsolutePath(), 44, 51)) {
            throw new IllegalStateException("it is compiled for Java 8 or newer: on Android 7.1 and older only Java 7 "
                    + "libraries can be dexed (Dx)");
        }
        mod.agus.jcoderz.dx.command.dexer.Main.clearInternTables();
        mod.agus.jcoderz.dx.command.dexer.Main.main(new String[]{"--debug", "--verbose", "--multi-dex",
                "--output=" + classesJar.getParentFile().getAbsolutePath(), classesJar.getAbsolutePath()});
    }

    /** A library folder the build can use: classes.jar and classes.dex there. */
    static boolean usable(File dir) {
        return new File(dir, "classes.jar").isFile() && new File(dir, "classes.dex").isFile();
    }

    /** Project's local library list entry for a folder, as the local library manager writes it. */
    public static HashMap<String, Object> entry(String name) {
        File dir = new File(root(), name);
        HashMap<String, Object> library = new HashMap<>();
        library.put("name", name);
        File config = new File(dir, "config");
        if (config.isFile()) library.put("packageName", FileUtil.readFile(config.getAbsolutePath()));
        put(library, "resPath", new File(dir, "res"));
        put(library, "jarPath", new File(dir, "classes.jar"));
        put(library, "dexPath", new File(dir, "classes.dex"));
        put(library, "manifestPath", new File(dir, "AndroidManifest.xml"));
        put(library, "pgRulesPath", new File(dir, "proguard.txt"));
        put(library, "assetsPath", new File(dir, "assets"));
        return library;
    }

    private static void put(HashMap<String, Object> library, String key, File file) {
        if (file.exists()) library.put(key, file.getAbsolutePath());
    }

    public static File listFile(String scId) {
        return new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + scId + "/local_library");
    }

    /** The project's enabled local libraries; empty when it has none; null when the list is there but unreadable. */
    public static ArrayList<HashMap<String, Object>> enabled(String scId) {
        File list = listFile(scId);
        if (!list.isFile()) return new ArrayList<>();
        String text = FileUtil.readFile(list.getAbsolutePath());
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        try {
            ArrayList<HashMap<String, Object>> libraries = new Gson().fromJson(text.replace("/.sketchware_ai/", "/.sketchware/"),
                    mod.hey.studios.util.Helper.TYPE_MAP_LIST);
            return libraries == null ? new ArrayList<HashMap<String, Object>>() : libraries;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void save(String scId, List<HashMap<String, Object>> libraries) {
        com.sketchware.ai.project.LibraryHeal.save(scId, libraries);
    }

    /**
     * Switches the libraries on in the project. v1.8.13: like Gradle, one version per group:artifact and the highest
     * wins: a lower version never replaces a higher one that is on (arrow-continuations asking for animal-sniffer 1.23
     * keeps 1.28); a higher one replaces the lower ones. When the library installed ({@code rootName}) is itself lower
     * than the version on, nothing of it is switched on: the version on brings its own dependencies.
     */
    private static void enable(String scId, String rootName, List<String> names, Outcome outcome) {
        ArrayList<HashMap<String, Object>> libraries = enabled(scId);
        if (libraries == null) {
            outcome.notSwitchedOn = "the project's local library list can't be read, so nothing was switched on";
            return;
        }
        HashMap<String, String> coordinates = new HashMap<>();   // folder -> coordinate, each needs.json read once
        if (names.contains(rootName) && newerOn(rootName, libraries, coordinates) != null) {
            outcome.keptNewer.add(newerOn(rootName, libraries, coordinates));
            outcome.rootKeptBack = true;
            return;
        }
        boolean changed = false;
        for (String name : names) {
            String newer = newerOn(name, libraries, coordinates);
            if (newer != null) {
                outcome.keptNewer.add(newer);
                continue;
            }
            String key = keyOf(coordinate(name, coordinates));
            boolean present = false;
            for (int i = libraries.size() - 1; i >= 0; i--) {
                String other = String.valueOf(libraries.get(i).get("name"));
                if (other.equals(name)) {
                    present = true;
                } else if (key != null && key.equals(keyOf(coordinate(other, coordinates)))) {
                    // another (lower) version of the same group:artifact, whatever its folder is called
                    libraries.remove(i);
                    outcome.replaced.add(other);
                    changed = true;
                }
            }
            if (!present) {
                libraries.add(entry(name));
                outcome.enabled.add(name);
                changed = true;
            }
        }
        if (changed) save(scId, libraries);
    }

    /** The folder of a higher version of {@code name}'s group:artifact that is on, or null. */
    private static String newerOn(String name, List<HashMap<String, Object>> libraries, HashMap<String, String> coordinates) {
        String coordinate = coordinate(name, coordinates);
        String key = keyOf(coordinate);
        if (key == null) return null;
        String version = coordinate.split(":")[2];
        for (HashMap<String, Object> library : libraries) {
            String other = String.valueOf(library.get("name"));
            if (other.equals(name)) continue;
            String otherCoordinate = coordinate(other, coordinates);
            if (key.equals(keyOf(otherCoordinate)) && MavenResolver.compareVersions(otherCoordinate.split(":")[2], version) > 0) {
                return other;
            }
        }
        return null;
    }

    private static String coordinate(String folder, HashMap<String, String> cache) {
        if (cache.containsKey(folder)) return cache.get(folder);
        String coordinate = coordinateOf(folder);
        cache.put(folder, coordinate);
        return coordinate;
    }

    private static String keyOf(String coordinate) {
        if (coordinate == null) return null;
        String[] gav = coordinate.split(":");
        return gav.length >= 3 ? gav[0] + ":" + gav[1] : null;
    }

    /** v1.8.13: group:artifact -> {folder, version} of the highest version switched on in the list (null list: none). */
    static HashMap<String, String[]> highestOn(List<HashMap<String, Object>> libraries) {
        HashMap<String, String[]> on = new HashMap<>();
        if (libraries == null) return on;
        for (HashMap<String, Object> library : libraries) {
            String folder = String.valueOf(library.get("name"));
            String coordinate = coordinateOf(folder);
            String key = keyOf(coordinate);
            if (key == null) continue;
            String version = coordinate.split(":")[2];
            String[] best = on.get(key);
            if (best == null || MavenResolver.compareVersions(version, best[1]) > 0) on.put(key, new String[]{folder, version});
        }
        return on;
    }

    /** "group:artifact" recorded for a library folder (needs.json, else source.json), or null when unknown. */
    public static String artifactKey(String folder) {
        String coordinate = coordinateOf(folder);
        if (coordinate == null) return null;
        String[] gav = coordinate.split(":");
        return gav.length >= 2 ? gav[0] + ":" + gav[1] : null;
    }

    /** The Maven coordinate recorded for a library folder, or null. */
    public static String coordinateOf(String folder) {
        File dir = new File(root(), folder);
        LibraryNeeds needs = LibraryNeeds.read(dir);
        String coordinate = needs != null ? needs.coordinate : null;
        if (coordinate == null) {
            com.sketchware.ai.project.LibraryIntegrity.Source source = com.sketchware.ai.project.LibraryIntegrity.readSource(dir);
            coordinate = source == null ? null : source.coordinate;
        }
        return coordinate == null || coordinate.split(":").length < 3 ? null : coordinate;
    }
}
