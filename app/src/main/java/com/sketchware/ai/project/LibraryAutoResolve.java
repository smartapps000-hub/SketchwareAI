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
    private static final Pattern INDIRECT = Pattern.compile("^The type ([\\w.]+) cannot be resolved\\. It is indirectly referenced");
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
            {"com.hoho.android.usbserial", "com.github.mik3y:usb-serial-for-android:3.8.1"},
            {"eightbitlab.com.blurview", "com.github.Dimezis:BlurView:version-2.0.6"},
            {"com.github.mikephil.charting", "com.github.PhilJay:MPAndroidChart:v3.1.0"},
    };

    /** The coordinates of {@link #KNOWN}, for restoring a library by its folder name. */
    static List<String> knownCoordinates() {
        List<String> coordinates = new ArrayList<>();
        for (String[] known : KNOWN) coordinates.add(known[1]);
        return coordinates;
    }

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
        List<String[]> missing = missingNames(compilerLog);
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
        List<Set<String>> enabledNow = new ArrayList<>();
        for (String[] item : missing) {
            String[] segments = item[1].split("\\.");
            int reported = item[0].split("\\.").length;
            // The compiler names only the first package it can't find ("com.dantsu"); the code names
            // the class. A library qualifies when it has more of that name than the compiler found.
            boolean reportedClass = Character.isUpperCase(segments[reported - 1].charAt(0));
            int need = segments.length > reported && !reportedClass ? reported + 1 : reported;
            if (segments.length == reported && reported < 3 && !reportedClass) {
                continue; // just "com" or "com.github": says nothing about which library
            }
            boolean covered = false;
            for (Set<String> entries : enabledNow) {
                if (depth(entries, segments) >= need) covered = true;
            }
            if (covered) continue;
            File best = null;
            int bestDepth = 0;
            for (File directory : candidates.values()) {
                Set<String> entries = index.get(directory);
                if (entries == null) index.put(directory, entries = packagesAndClasses(directory));
                int depth = depth(entries, segments);
                // Of equally good ones the later name: the newer of several versions.
                if (depth >= need && depth >= bestDepth) {
                    best = directory;
                    bestDepth = depth;
                }
            }
            if (best != null) {
                HashMap<String, Object> entry = new HashMap<>();
                entry.put("name", best.getName());
                fill(entry, best);
                libraries.add(entry);
                outcome.enabled.add(best.getName());
                candidates.remove(best.getName());
                enabledNow.add(index.get(best));
            } else {
                String hint = hint(item[1]);
                String line = item[1] + (hint == null ? "" : " (download " + hint + " in Local Library Manager)");
                boolean duplicate = false;
                for (String existing : outcome.unavailable) {
                    if (existing.equals(line) || hint != null && existing.contains(hint)) duplicate = true;
                }
                if (!duplicate) outcome.unavailable.add(line);
                if (hint != null && !outcome.downloadable.contains(hint)) outcome.downloadable.add(hint);
            }
        }
        return outcome;
    }

    /** Packages the platform, the built-in libraries or optional integrations provide: never linked from local libraries. */
    private static final String[] NOT_LINKED = {
            "java/", "javax/", "android/", "androidx/", "dalvik/", "kotlin/", "kotlinx/", "libcore/", "sun/", "com/sun/",
            "com/android/", "com/google/android/material/", "com/google/android/gms/", "com/google/firebase/",
            "org/xmlpull/", "org/w3c/", "org/xml/", "org/json/", "org/apache/http/", "org/jetbrains/", "org/intellij/",
            "com/google/errorprone/", "org/checkerframework/", "org/codehaus/mojo/", "org/conscrypt/",
            "org/bouncycastle/", "org/openjsse/", "android/support/"};

    /**
     * Sub-dependencies: when an enabled library's own code refers to classes that neither the
     * build classpath nor another enabled library has, and exactly the installed local libraries
     * can supply them, those libraries are enabled too (and their needs in turn). Works offline
     * from the class files; a library whose classes would duplicate an enabled one is never added.
     *
     * @param startNames the libraries to check (e.g. the one just ticked)
     * @param classpath  ':'-separated jars the project compiles against (android.jar, built-in libraries)
     * @return names of the libraries that were added to {@code libraries}
     */
    public static List<String> linkDependencies(List<HashMap<String, Object>> libraries, List<String> startNames,
                                                String classpath, File... libraryRoots) {
        List<String> added = new ArrayList<>();
        Set<String> provided = new java.util.HashSet<>();
        for (String path : (classpath == null ? "" : classpath).split(":")) {
            File jar = new File(path.trim());
            if (jar.isFile()) provided.addAll(classNames(jar));
        }
        Map<String, File> enabledJars = new LinkedHashMap<>();
        for (HashMap<String, Object> library : libraries) {
            Object jar = library.get("jarPath");
            if (jar != null && new File(jar.toString()).isFile()) {
                enabledJars.put(String.valueOf(library.get("name")), new File(jar.toString()));
                provided.addAll(classNames(new File(jar.toString())));
            }
        }
        // Installed, not enabled, usable.
        Map<String, File> candidates = new LinkedHashMap<>();
        for (File root : libraryRoots) {
            File[] directories = root == null ? null : root.listFiles();
            if (directories == null) continue;
            Arrays.sort(directories);
            for (File directory : directories) {
                if (directory.isDirectory() && !enabledJars.containsKey(directory.getName())
                        && !candidates.containsKey(directory.getName())
                        && new File(directory, "classes.jar").isFile() && new File(directory, "classes.dex").isFile()) {
                    candidates.put(directory.getName(), directory);
                }
            }
        }
        if (candidates.isEmpty()) return added;
        Map<String, Set<String>> candidateClasses = new HashMap<>();

        List<String> queue = new ArrayList<>(startNames);
        for (int q = 0; q < queue.size() && added.size() < 12; q++) {
            File jar = enabledJars.get(queue.get(q));
            if (jar == null) continue;
            Set<String> missing = new LinkedHashSet<>();
            for (String reference : classReferences(jar)) {
                if (provided.contains(reference)) continue;
                boolean skip = false;
                for (String prefix : NOT_LINKED) {
                    if (reference.startsWith(prefix)) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) missing.add(reference);
            }
            while (!missing.isEmpty() && added.size() < 12) {
                // The candidate that supplies the most of what is missing; of equals, the later name (newer version).
                String best = null;
                int bestCount = 0;
                for (Map.Entry<String, File> candidate : candidates.entrySet()) {
                    Set<String> classes = candidateClasses.get(candidate.getKey());
                    if (classes == null) {
                        classes = classNames(new File(candidate.getValue(), "classes.jar"));
                        candidateClasses.put(candidate.getKey(), classes);
                    }
                    int count = 0;
                    for (String name : missing) if (classes.contains(name)) count++;
                    if (count > 0 && count >= bestCount) {
                        best = candidate.getKey();
                        bestCount = count;
                    }
                }
                if (best == null) break;
                Set<String> classes = candidateClasses.get(best);
                File directory = candidates.remove(best);
                missing.removeAll(classes);
                boolean duplicates = false;
                for (String name : classes) {
                    if (provided.contains(name)) {
                        duplicates = true;
                        break;
                    }
                }
                if (duplicates) continue; // would put the same class into the app twice
                HashMap<String, Object> entry = new HashMap<>();
                entry.put("name", best);
                fill(entry, directory);
                libraries.add(entry);
                added.add(best);
                provided.addAll(classes);
                enabledJars.put(best, new File(directory, "classes.jar"));
                queue.add(best);
            }
        }
        return added;
    }

    /** Class files in a jar, as "a/b/C" (nested classes included). */
    private static Set<String> classNames(File jar) {
        Set<String> names = new java.util.HashSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/")) names.add(name.substring(0, name.length() - 6));
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable jar: provides nothing.
        }
        return names;
    }

    /** Every class the code in a jar refers to (constant-pool class entries), as "a/b/C". */
    private static Set<String> classReferences(File jar) {
        Set<String> references = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) continue;
                try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(zip.getInputStream(entry)))) {
                    if (in.readInt() != 0xCAFEBABE) continue;
                    in.readInt();
                    int count = in.readUnsignedShort();
                    String[] utf8 = new String[count];
                    int[] classIndex = new int[count];
                    int classes = 0;
                    for (int i = 1; i < count; i++) {
                        int tag = in.readUnsignedByte();
                        switch (tag) {
                            case 1:
                                utf8[i] = in.readUTF();
                                break;
                            case 7:
                                classIndex[classes++] = in.readUnsignedShort();
                                break;
                            case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                                in.skipBytes(4);
                                break;
                            case 5: case 6:
                                in.skipBytes(8);
                                i++;
                                break;
                            case 8: case 16: case 19: case 20:
                                in.skipBytes(2);
                                break;
                            case 15:
                                in.skipBytes(3);
                                break;
                            default:
                                i = count; // unknown layout: stop reading this class
                        }
                    }
                    for (int i = 0; i < classes; i++) {
                        String name = utf8[classIndex[i]];
                        if (name == null) continue;
                        while (name.startsWith("[")) name = name.substring(1);
                        if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
                        if (name.length() > 1) references.add(name);
                    }
                } catch (IOException | RuntimeException e) {
                    // Skip a class that can't be read.
                }
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable jar: refers to nothing.
        }
        return references;
    }

    /** How many leading segments of a dotted name exist in a jar as a package or class. */
    private static int depth(Set<String> entries, String[] segments) {
        StringBuilder path = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) path.append('/');
            path.append(segments[i]);
            if (!entries.contains(path.toString())) break;
            depth = i + 1;
        }
        return depth;
    }

    private static final Pattern IMPORT_LINE = Pattern.compile("import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");
    private static final Pattern DOTTED = Pattern.compile("^[\\w.]+");

    /**
     * What the log reports as unresolved: {the name the compiler printed, the full name the code
     * uses}. The compiler stops at the first package it can't find, so the quoted source line is
     * read for the rest.
     */
    static List<String[]> missingNames(String log) {
        List<String[]> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AutoHeal.Problem problem : AutoHeal.parse(log)) {
            String message = problem.message;
            String reported, full;
            Matcher i = IMPORT.matcher(message);
            Matcher indirect = INDIRECT.matcher(message);
            Matcher q = QUALIFIED.matcher(message);
            if (i.find()) {
                reported = full = i.group(1);
                Matcher line = IMPORT_LINE.matcher(problem.excerpt);
                if (line.find() && line.group(2).startsWith(reported)) {
                    full = line.group(2);
                    // import static a.b.C.member: the class is what a library has
                    if (line.group(1) != null && line.group(3) == null && full.contains(".")) {
                        full = full.substring(0, full.lastIndexOf('.'));
                    }
                    if (full.length() < reported.length()) full = reported;
                }
            } else if (indirect.find()) {
                // A library that needs another library.
                reported = full = indirect.group(1);
            } else if (q.find()) {
                reported = full = q.group(2) == null ? q.group(1) : q.group(1) + "." + q.group(2);
                if (problem.column >= 0 && problem.column < problem.excerpt.length()) {
                    Matcher dotted = DOTTED.matcher(problem.excerpt.substring(problem.column));
                    if (dotted.find() && dotted.group().startsWith(reported)) {
                        full = dotted.group();
                        while (full.endsWith(".")) full = full.substring(0, full.length() - 1);
                    }
                }
            } else {
                continue;
            }
            if (seen.add(reported + "|" + full)) names.add(new String[]{reported, full});
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

    /** Sets an entry's paths (classes.jar, dex, res, manifest, ...) and package from its folder. */
    static boolean refresh(HashMap<String, Object> library, File directory) {
        return fill(library, directory);
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
                String packageName;
                try (java.io.InputStream in = new java.io.FileInputStream(config)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    packageName = new String(out.toByteArray(), "UTF-8").trim();
                }
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
