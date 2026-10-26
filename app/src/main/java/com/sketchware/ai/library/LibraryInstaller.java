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
        int steps = 0, step = 0;
        for (MavenResolver.Artifact a : order) if (!"pom".equals(a.packaging)) steps++;
        for (MavenResolver.Artifact a : order) {
            if (MavenResolver.cancelledHere()) break;   // v1.8.12: Cancel in the progress dialog
            if ("pom".equals(a.packaging)) continue;   // nothing to install, only what it needs
            String name = folderName(a);
            File dir = new File(root(), name);
            String counter = steps > 1 ? " (" + (++step) + "/" + steps + ")" : "";
            try {
                if (usable(dir)) {
                    outcome.reused.add(name);
                } else {
                    if (progress != null) progress.status("Fetching the " + (a.packaging == null ? "archive" : "." + a.packaging)
                            + counter + "\n" + a.coordinate());
                    File archive = resolver.archive(a, plan);
                    if (archive == null) {
                        outcome.failed.add(a.coordinate() + ": no repository has its .aar or .jar" + hostLinks(a.group, a.name, a.version));
                        continue;
                    }
                    String noCode = MavenFetcher.withoutAndroidCode(archive);
                    if (noCode != null) {
                        outcome.skipped.add(a.name + " " + a.version + " (" + noCode + ")");
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
                    } else {
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
        if (scId != null && !ready.isEmpty()) enable(scId, ready, outcome);
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

    private static void enable(String scId, List<String> names, Outcome outcome) {
        ArrayList<HashMap<String, Object>> libraries = enabled(scId);
        if (libraries == null) {
            outcome.failed.add("The project's local library list can't be read, so nothing was switched on (installed: "
                    + String.valueOf(names).replace("[", "").replace("]", "") + ")");
            return;
        }
        for (String name : names) {
            String key = artifactKey(name);
            boolean present = false;
            for (int i = libraries.size() - 1; i >= 0; i--) {
                String other = String.valueOf(libraries.get(i).get("name"));
                if (other.equals(name)) {
                    present = true;
                } else if (key != null && key.equals(artifactKey(other))) {
                    // v1.8.11: another version of the same group:artifact, whatever its folder is called (the old
                    // downloader named folders "core-v3.4.1"): the project switches to the new one
                    libraries.remove(i);
                    outcome.replaced.add(other);
                }
            }
            if (!present) {
                libraries.add(entry(name));
                outcome.enabled.add(name);
            }
        }
        save(scId, libraries);
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
