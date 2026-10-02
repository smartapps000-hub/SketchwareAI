package com.sketchware.ai.project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Makes a project's local libraries usable without manual steps:
 * <ul>
 *     <li>{@link #repairEntries}: an enabled library whose entry has no (or a dead) classes.jar /
 *     dex / res path gets the paths of its own folder, exactly what ticking it in Local Library
 *     Manager writes;</li>
 *     <li>{@link #resolve}: when the compiler can't resolve a package or class
 *     ("The import com.google.zxing.common cannot be resolved"), the installed local libraries
 *     are searched for one that contains it, and that library is enabled for the project.</li>
 * </ul>
 * This class downloads nothing and never copies or changes a library file.
 */
public final class LibraryAutoResolve {

    private static final String[][] FILES = {
            {"resPath", "res"}, {"jarPath", "classes.jar"}, {"dexPath", "classes.dex"},
            {"manifestPath", "AndroidManifest.xml"}, {"pgRulesPath", "proguard.txt"}, {"assetsPath", "assets"}};

    private static final Pattern IMPORT = Pattern.compile("The import ([\\w.]+) cannot be resolved");
    private static final Pattern QUALIFIED = Pattern.compile("^([a-z_][\\w]*(?:\\.[a-z_][\\w]*)+)(?:\\.([A-Z]\\w*))? cannot be resolved");

    /** Where to get well-known libraries when none is installed. */
    private static final String[][] KNOWN = {
            {"com.google.zxing", "com.google.zxing:core:3.4.1"},
            {"com.journeyapps.barcodescanner", "com.journeyapps:zxing-android-embedded:3.6.0"},
            {"com.google.gson", "com.google.code.gson:gson:2.10.1"},
            {"com.bumptech.glide", "com.github.bumptech.glide:glide:4.12.0"},
            {"okhttp3", "com.squareup.okhttp3:okhttp:3.12.13"},
            {"com.dantsu.escposprinter", "com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0"},
            {"com.takisoft.datetimepicker", "com.takisoft.datetimepicker:datetimepicker:1.0.3"},
            {"de.hdodenhof.circleimageview", "de.hdodenhof:circleimageview:3.1.0"},
            {"com.airbnb.lottie", "com.airbnb.android:lottie:3.4.0"},
            {"com.squareup.picasso", "com.squareup.picasso:picasso:2.71828"},
    };

    public static final class Outcome {
        /** Libraries added to the project's list. */
        public final List<String> enabled = new ArrayList<>();
        /** Packages no installed library provides, with a download hint where known. */
        public final List<String> unavailable = new ArrayList<>();
        /** Maven coordinates of well-known libraries that would provide them. */
        public final List<String> downloadable = new ArrayList<>();
    }

    private LibraryAutoResolve() {
    }

    /** @return true if any entry changed */
    public static boolean repairEntries(List<HashMap<String, Object>> libraries, File... libraryRoots) {
        if (libraries == null) return false;
        boolean changed = false;
        for (HashMap<String, Object> library : libraries) {
            Object name = library.get("name");
            if (!(name instanceof String) || ((String) name).isEmpty()) continue;
            Object jar = library.get("jarPath");
            if (jar instanceof String && new File((String) jar).isFile()) continue;
            File directory = find((String) name, libraryRoots);
            if (directory == null) continue;
            changed |= fill(library, directory);
        }
        return changed;
    }

    /**
     * Enables installed libraries that provide what the compiler log says is missing.
     *
     * @param libraries the project's local_library list; new entries are appended
     */
    public static Outcome resolve(String compilerLog, List<HashMap<String, Object>> libraries, File... libraryRoots) {
        Outcome outcome = new Outcome();
        Set<String> missing = missingNames(compilerLog);
        if (missing.isEmpty()) return outcome;

        Set<String> enabledNames = new LinkedHashSet<>();
        for (HashMap<String, Object> library : libraries) enabledNames.add(String.valueOf(library.get("name")));

        // Installed, not yet enabled libraries with a usable classes.jar (and dex), by name.
        Map<String, File> candidates = new LinkedHashMap<>();
        for (File root : libraryRoots) {
            File[] directories = root == null ? null : root.listFiles();
            if (directories == null) continue;
            Arrays.sort(directories);
            for (File directory : directories) {
                if (!directory.isDirectory() || enabledNames.contains(directory.getName())
                        || candidates.containsKey(directory.getName())) continue;
                if (new File(directory, "classes.jar").isFile() && new File(directory, "classes.dex").isFile()) {
                    candidates.put(directory.getName(), directory);
                }
            }
        }

        Map<File, Set<String>> index = new HashMap<>();
        Set<String> covered = new LinkedHashSet<>();
        for (String name : missing) {
            if (isCovered(name, covered)) continue;
            String path = name.replace('.', '/');
            File best = null;
            for (File directory : candidates.values()) {
                Set<String> entries = index.computeIfAbsent(directory, LibraryAutoResolve::packagesAndClasses);
                if (!entries.contains(path)) continue;
                // Prefer the newest of several versions: names sort ascending, so keep the last.
                best = directory;
            }
            if (best != null) {
                HashMap<String, Object> entry = new HashMap<>();
                entry.put("name", best.getName());
                fill(entry, best);
                libraries.add(entry);
                outcome.enabled.add(best.getName());
                candidates.remove(best.getName());
                // Everything this library provides is now resolved.
                covered.addAll(index.get(best));
            } else {
                String hint = hint(name);
                String line = name + (hint == null ? "" : " (download " + hint + " in Local Library Manager)");
                boolean duplicate = false;
                for (String existing : outcome.unavailable) {
                    if (hint != null && existing.contains(hint)) duplicate = true;
                }
                if (!duplicate) outcome.unavailable.add(line);
                if (hint != null && !outcome.downloadable.contains(hint)) outcome.downloadable.add(hint);
            }
        }
        return outcome;
    }

    private static boolean isCovered(String name, Set<String> covered) {
        return covered.contains(name.replace('.', '/'));
    }

    /** Package or class names (dotted) the log reports as unresolved. */
    static Set<String> missingNames(String log) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : log.split("\\r?\\n")) {
            String t = line.trim();
            Matcher i = IMPORT.matcher(t);
            if (i.find()) {
                names.add(i.group(1));
                continue;
            }
            Matcher q = QUALIFIED.matcher(t);
            if (q.find()) names.add(q.group(2) == null ? q.group(1) : q.group(1) + "." + q.group(2));
        }
        return names;
    }

    /** Every package path ("com/google/zxing") and class path ("com/google/zxing/Result") in the jar. */
    private static Set<String> packagesAndClasses(File directory) {
        Set<String> result = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(new File(directory, "classes.jar"))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class")) continue;
                result.add(name.substring(0, name.length() - ".class".length()));
                int slash = name.lastIndexOf('/');
                while (slash > 0) {
                    name = name.substring(0, slash);
                    if (!result.add(name)) break;
                    slash = name.lastIndexOf('/');
                }
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable jar: provides nothing.
        }
        return result;
    }

    private static String hint(String name) {
        for (String[] known : KNOWN) {
            if (name.equals(known[0]) || name.startsWith(known[0] + ".")) return known[1];
        }
        return null;
    }

    private static File find(String name, File... roots) {
        File fallback = null;
        for (File root : roots) {
            if (root == null) continue;
            File directory = new File(root, name);
            if (new File(directory, "classes.jar").isFile()) return directory;
            if (fallback == null && directory.isDirectory()) fallback = directory;
        }
        return fallback;
    }

    private static boolean fill(HashMap<String, Object> library, File directory) {
        boolean changed = false;
        for (String[] file : FILES) {
            File candidate = new File(directory, file[1]);
            Object old = library.get(file[0]);
            if (candidate.exists()) {
                if (!candidate.getAbsolutePath().equals(old)) {
                    library.put(file[0], candidate.getAbsolutePath());
                    changed = true;
                }
            } else if (old != null && !new File(old.toString()).exists()) {
                library.remove(file[0]);
                changed = true;
            }
        }
        File config = new File(directory, "config");
        if (!library.containsKey("packageName") && config.isFile()) {
            try {
                String packageName = new String(java.nio.file.Files.readAllBytes(config.toPath()), "UTF-8").trim();
                if (!packageName.isEmpty()) {
                    library.put("packageName", packageName);
                    changed = true;
                }
            } catch (IOException ignored) {
            }
        }
        return changed;
    }
}
