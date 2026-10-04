package com.sketchware.ai.project;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.origin.Origin;
import com.google.gson.Gson;
import com.sketchware.ai.library.MavenFetcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import mod.agus.jcoderz.lib.FilePathUtil;
import mod.agus.jcoderz.lib.FileUtil;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.util.LogUtil;

/**
 * Build-time repair for "X cannot be resolved" caused by a missing local library: enables an
 * installed library that provides the package, and if none is installed, downloads the
 * well-known one (same folder layout as Local Library Manager's downloader), then enables it.
 */
public final class LibraryHeal {

    private static final String TAG = "LibraryHeal";
    private static final String[][] REPOSITORIES = {
            {"Google Maven", "https://dl.google.com/dl/android/maven2"},
            {"Maven Central", "https://repo1.maven.org/maven2"},
            {"JitPack", "https://jitpack.io"},
    };

    /** One automatic full rebuild per tap on Run. */
    private static boolean rebuildUsed;

    public static final class Result {
        /** classes.jar files to add to the compiler classpath. */
        public final List<String> jars = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        /** An enabled library has resources/manifest, so the whole build has to start over. */
        public boolean needsRebuild;
    }

    public interface Progress {
        void show(String text);
    }

    private LibraryHeal() {
    }

    public static synchronized void userStartedBuild() {
        rebuildUsed = false;
    }

    /** @return true the first time after a user-started build */
    public static synchronized boolean claimRebuild() {
        if (rebuildUsed) return false;
        rebuildUsed = true;
        return true;
    }

    public static File localLibs() {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs");
    }

    public static void save(String sc_id, List<HashMap<String, Object>> libraries) {
        FileUtil.writeFile(new FilePathUtil().getPathLocalLibrary(sc_id), new Gson().toJson(libraries));
    }

    public static Result run(String sc_id, String compilerLog, List<HashMap<String, Object>> libraries, String classpath, Progress progress) {
        Result result = new Result();
        File root = localLibs();
        int before = libraries.size();
        LibraryAutoResolve.Outcome outcome = LibraryAutoResolve.resolve(compilerLog, libraries, root);

        if (!outcome.downloadable.isEmpty()) {
            boolean installed = false;
            for (String coordinate : outcome.downloadable) {
                if (progress != null) progress.show("Downloading missing library " + coordinate + "...");
                String problem = download(coordinate, root);
                if (problem == null) {
                    installed = true;
                    result.notes.add("auto-fix: downloaded missing library " + coordinate);
                } else {
                    result.notes.add("Couldn't download " + coordinate + " automatically: " + problem);
                }
            }
            if (installed) {
                LibraryAutoResolve.Outcome second = LibraryAutoResolve.resolve(compilerLog, libraries, root);
                outcome.enabled.addAll(second.enabled);
            }
        }

        for (String line : outcome.unavailable) {
            if (!line.contains("(download ")) {
                result.notes.add("No installed local library has " + line + ": add the library that contains it in Local library manager.");
            }
        }

        // What the newly enabled libraries need themselves (sub-dependencies), from the installed libraries.
        if (libraries.size() > before) {
            List<String> start = new ArrayList<>();
            for (int i = before; i < libraries.size(); i++) start.add(String.valueOf(libraries.get(i).get("name")));
            try {
                LibraryAutoResolve.linkDependencies(libraries, start, classpath, root);
            } catch (RuntimeException e) {
                LogUtil.e(TAG, "Couldn't link sub-dependencies", e);
            }
        }

        // A library that was enabled all along but had no files until the download above.
        java.util.Set<Object> usable = new java.util.HashSet<>();
        for (int i = 0; i < before; i++) {
            Object jar = libraries.get(i).get("jarPath");
            if (jar != null && new File(jar.toString()).isFile()) usable.add(jar);
        }
        boolean repaired = LibraryAutoResolve.repairEntries(libraries.subList(0, before), root);
        for (int i = 0; repaired && i < before; i++) {
            HashMap<String, Object> entry = libraries.get(i);
            Object jar = entry.get("jarPath");
            if (jar == null || usable.contains(jar) || !new File(jar.toString()).isFile()) continue;
            result.notes.add("auto-fix: restored the files of local library " + entry.get("name"));
            result.jars.add(jar.toString());
            if (entry.containsKey("resPath") || entry.containsKey("manifestPath") || entry.containsKey("assetsPath")) {
                result.needsRebuild = true;
            }
        }

        if (libraries.size() == before && !repaired) return result;
        save(sc_id, libraries);
        for (int i = before; i < libraries.size(); i++) {
            HashMap<String, Object> entry = libraries.get(i);
            result.notes.add("auto-fix: enabled local library " + entry.get("name") + " for this project");
            if (entry.get("jarPath") != null) result.jars.add(entry.get("jarPath").toString());
            if (entry.containsKey("resPath") || entry.containsKey("manifestPath") || entry.containsKey("assetsPath")) {
                result.needsRebuild = true;
            }
        }
        return result;
    }

    /** @return null on success, otherwise why it failed */
    private static String download(String coordinate, File root) {
        return download(coordinate, root, null);
    }

    /**
     * @param folderName the library's folder under {@code root}; null for "artifact_V_version"
     * @return null on success, otherwise why it failed
     */
    static String download(String coordinate, File root, String folderName) {
        String[] gav = MavenFetcher.parse(coordinate);
        if (gav == null) return "not a Maven coordinate";
        File directory = new File(root, folderName != null ? folderName : gav[1] + "_V_" + gav[2]);
        try {
            root.mkdirs();
            if (!new File(directory, "classes.jar").isFile()) {
                MavenFetcher.Result fetched = fetch(coordinate, root);
                if (fetched.file == null) {
                    return fetched.networkFailed ? "no internet connection" : "not found in Google Maven, Maven Central or JitPack";
                }
                String reason = MavenFetcher.install(fetched.file, directory);
                fetched.file.delete();
                if (reason != null) {
                    // A folder that was there before (res, config of a library being restored) is kept.
                    if (folderName == null) FileUtil.deleteFile(directory.getAbsolutePath());
                    return reason;
                }
                LibraryIntegrity.writeSource(directory, gav[0] + ":" + gav[1] + ":" + gav[2], fetched.url);
            }
            writeConfig(directory, gav[0]);
            if (!new File(directory, "classes.dex").isFile()) dex(new File(directory, "classes.jar"));
            return new File(directory, "classes.dex").isFile() ? null : "couldn't convert it to dex";
        } catch (Throwable e) {
            LogUtil.e(TAG, "Couldn't install " + coordinate, e);
            return String.valueOf(e.getMessage());
        }
    }

    /** Downloads the artifact's .aar/.jar into {@code root}. A request that broke off is tried once more. */
    static MavenFetcher.Result fetch(String coordinate, File root) {
        MavenFetcher.Result fetched = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            fetched = new MavenFetcher().fetch(coordinate, Arrays.asList(REPOSITORIES), root, null);
            if (fetched.file != null || fetched.networkFailed || fetched.log.indexOf("Exception") < 0) break;
        }
        return fetched;
    }

    private static final java.util.regex.Pattern MANIFEST_PACKAGE =
            java.util.regex.Pattern.compile("<manifest[^>]*\\spackage=\"([^\"]+)\"", java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern JAVA_PACKAGE =
            java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    /**
     * The library's "config" file holds the package its R class is generated for and that
     * activities import. That is the package of its AndroidManifest.xml; for a plain .jar the
     * Maven group is used as before, but only when it is a valid Java package name
     * ("com.github.some-user" is not, and would break the import and the resource link).
     */
    static void writeConfig(File directory, String group) {
        File config = new File(directory, "config");
        if (config.exists()) return; // what is there (possibly set by the user) stays
        String packageName = null;
        File manifest = new File(directory, "AndroidManifest.xml");
        if (manifest.isFile()) {
            java.util.regex.Matcher m = MANIFEST_PACKAGE.matcher(FileUtil.readFile(manifest.getAbsolutePath()));
            if (m.find()) packageName = m.group(1).trim();
        }
        if (packageName == null) packageName = group;
        if (packageName == null || !JAVA_PACKAGE.matcher(packageName).matches()) return;
        FileUtil.writeFile(config.getAbsolutePath(), packageName);
    }

    static void dex(File classesJar) throws Exception {
        File libs = BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH;
        // D8 needs the bundled android.jar, which the first build after an install hasn't unpacked yet.
        if (!new File(libs, "android.jar").isFile()) a.a.a.Dp.maybeExtractAndroidJar(null);
        List<String> arguments = new ArrayList<>(Arrays.asList(
                "--release", "--intermediate",
                "--lib", new File(libs, "android.jar").getAbsolutePath()));
        File lambdaStubs = new File(libs, "core-lambda-stubs.jar");
        if (lambdaStubs.isFile()) {
            arguments.add("--classpath");
            arguments.add(lambdaStubs.getAbsolutePath());
        }
        arguments.add("--output");
        arguments.add(classesJar.getParentFile().getAbsolutePath());
        arguments.add(classesJar.getAbsolutePath());
        // Not D8.main(): that calls System.exit() on failure.
        D8.run(D8Command.parse(arguments.toArray(new String[0]), Origin.root()).build());
    }
}
