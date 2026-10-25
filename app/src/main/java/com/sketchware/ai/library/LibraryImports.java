package com.sketchware.ai.library;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * v1.8.9: imports for the Java packages of the project's local libraries, so code written in add-source blocks or the
 * Java editor can use a library's classes (EscPosPrinter, BluetoothPrintersConnections …) without an import block.
 * <p>
 * A package gets {@code import pkg.*;} only when the activity's code uses one of its public classes by simple name, and
 * only when none of the simple names the code uses is also in a package the activity already imports on demand
 * (android.widget.*, java.util.* …, java.lang) or in another library package added here: then the import would make
 * that name ambiguous, and nothing is added for the package. Offline; the jars are only read.
 */
public final class LibraryImports {

    private LibraryImports() {
    }

    private static final Pattern SEGMENT_SKIP = Pattern.compile("(^|\\.)(internal|impl|shaded|shadow|repackaged)(\\.|$)");

    /** jar path + size + time → package → public top-level simple names */
    private static final Map<String, Map<String, Set<String>>> JAR_CACHE = new HashMap<>();
    /** android.jar path + size + time → package → simple names */
    private static final Map<String, Map<String, Set<String>>> PLATFORM_CACHE = new HashMap<>();

    /** Packages of a library jar with their public top-level classes (R, BuildConfig and internal packages left out). */
    public static synchronized Map<String, Set<String>> packages(File jar) {
        String key = jar.getAbsolutePath() + "|" + jar.length() + "|" + jar.lastModified();
        Map<String, Set<String>> cached = JAR_CACHE.get(key);
        if (cached != null) return cached;
        Map<String, Set<String>> out = new TreeMap<>();
        if (jar.isFile()) {
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.indexOf('$') >= 0 || name.startsWith("META-INF/")) continue;
                    int slash = name.lastIndexOf('/');
                    if (slash < 0) continue;
                    String pkg = name.substring(0, slash).replace('/', '.');
                    String simple = name.substring(slash + 1, name.length() - 6);
                    if (simple.equals("R") || simple.equals("BuildConfig") || simple.equals("package-info")
                            || SEGMENT_SKIP.matcher(pkg).find()) continue;
                    if (!isPublic(zip, entry)) continue;
                    Set<String> set = out.get(pkg);
                    if (set == null) out.put(pkg, set = new HashSet<>());
                    set.add(simple);
                }
            } catch (IOException | RuntimeException e) {
                // unreadable jar: no packages
            }
        }
        JAR_CACHE.put(key, out);
        return out;
    }

    /** The topmost packages (no package above them has classes), for showing the import line. */
    public static List<String> topPackages(Map<String, Set<String>> packages) {
        List<String> top = new ArrayList<>();
        for (String pkg : packages.keySet()) {
            boolean under = false;
            for (String other : packages.keySet()) {
                if (!other.equals(pkg) && pkg.startsWith(other + ".")) {
                    under = true;
                    break;
                }
            }
            if (!under) top.add(pkg);
        }
        return top;
    }

    private static synchronized Map<String, Set<String>> platform(File androidJar) {
        String key = androidJar.getAbsolutePath() + "|" + androidJar.length() + "|" + androidJar.lastModified();
        Map<String, Set<String>> cached = PLATFORM_CACHE.get(key);
        if (cached != null) return cached;
        Map<String, Set<String>> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(androidJar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
                int slash = name.lastIndexOf('/');
                if (slash < 0) continue;
                String pkg = name.substring(0, slash).replace('/', '.');
                String simple = name.substring(slash + 1, name.length() - 6);
                // nested classes count for imports like android.view.View.* (View$OnClickListener → View.OnClickListener)
                int dollar = simple.indexOf('$');
                if (dollar >= 0) {
                    String outer = pkg + "." + simple.substring(0, dollar);
                    String inner = simple.substring(dollar + 1);
                    if (inner.indexOf('$') >= 0 || inner.isEmpty() || Character.isDigit(inner.charAt(0))) continue;
                    pkg = outer;
                    simple = inner;
                }
                Set<String> set = out.get(pkg);
                if (set == null) out.put(pkg, set = new HashSet<>());
                set.add(simple);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        PLATFORM_CACHE.put(key, out);
        return out;
    }

    /** androidx.annotation is imported on demand by AppCompat activities; it is no part of android.jar. */
    private static final Set<String> ANDROIDX_ANNOTATIONS = new HashSet<>(java.util.Arrays.asList("NonNull", "Nullable",
            "Keep", "RequiresApi", "RequiresPermission", "CallSuper", "MainThread", "UiThread", "WorkerThread",
            "AnyThread", "ColorInt", "ColorRes", "DrawableRes", "StringRes", "IdRes", "LayoutRes", "DimenRes", "Px",
            "Dimension", "IntDef", "StringDef", "LongDef", "IntRange", "FloatRange", "Size", "VisibleForTesting",
            "CheckResult", "AnimRes", "AnyRes", "ArrayRes", "AttrRes", "BoolRes", "FontRes", "IntegerRes", "MenuRes",
            "RawRes", "StyleRes", "XmlRes", "Discouraged", "OptIn", "RestrictTo", "GuardedBy", "ChecksSdkIntAtLeast"));

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");
    private static final Pattern NAME = Pattern.compile("(?<![\\w.$])([A-Z][A-Za-z0-9_]*)\\b");

    /**
     * @param code       the generated activity
     * @param libraryJars classes.jar of every enabled local library
     * @param androidJar the android.jar the project compiles against; without it nothing is added
     * @return the packages to import on demand, in order (empty when none)
     */
    public static List<String> forCode(String code, Collection<File> libraryJars, Collection<String> rPackages,
                                       File androidJar, File builtInLibraries) {
        List<String> result = new ArrayList<>();
        if (code == null || libraryJars == null || libraryJars.isEmpty() || androidJar == null || !androidJar.isFile()) {
            return result;
        }

        // what the activity already imports
        Set<String> onDemand = new HashSet<>();
        onDemand.add("java.lang");
        Set<String> singleNames = new HashSet<>();
        Matcher im = IMPORT.matcher(code);
        while (im.find()) {
            if (im.group(1) != null) continue;
            if (im.group(3) != null) onDemand.add(im.group(2));
            else singleNames.add(im.group(2).substring(im.group(2).lastIndexOf('.') + 1));
        }
        Set<String> used = usedNames(code);
        used.removeAll(singleNames);   // a single-type import decides those names already
        if (used.isEmpty()) return result;

        Map<String, Set<String>> libraryPackages = new LinkedHashMap<>();
        for (File jar : libraryJars) {
            for (Map.Entry<String, Set<String>> e : packages(jar).entrySet()) {
                Set<String> set = libraryPackages.get(e.getKey());
                if (set == null) libraryPackages.put(e.getKey(), set = new HashSet<>());
                set.addAll(e.getValue());
            }
        }
        // candidates: library packages not imported yet whose classes the code uses
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : libraryPackages.entrySet()) {
            if (onDemand.contains(e.getKey())) continue;
            for (String name : e.getValue()) {
                if (used.contains(name)) {
                    candidates.add(e.getKey());
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return result;

        // names every on-demand import already brings: android.jar, the built-in libraries, the local libraries.
        // A package found in none of them (an import block of some other package) can't be checked: then nothing
        // is added, so no import can make a name ambiguous.
        Map<String, Set<String>> platform = platform(androidJar);
        if (platform == null) return result;
        Set<String> unknown = new HashSet<>();
        for (String pkg : onDemand) {
            if (!platform.containsKey(pkg) && !libraryPackages.containsKey(pkg) && !pkg.equals("androidx.annotation")
                    && (rPackages == null || !rPackages.contains(pkg))) unknown.add(pkg);
        }
        Map<String, Set<String>> builtIn = unknown.isEmpty() ? new HashMap<String, Set<String>>() : builtInNames(builtInLibraries, unknown);
        for (String pkg : unknown) if (!builtIn.containsKey(pkg)) return result;

        Map<String, Set<String>> reachable = new HashMap<>();   // name -> packages that bring it on demand
        for (String pkg : onDemand) {
            if (pkg.equals("androidx.annotation")) addNames(reachable, pkg, ANDROIDX_ANNOTATIONS);
            addNames(reachable, pkg, platform.get(pkg));
            addNames(reachable, pkg, libraryPackages.get(pkg));
            addNames(reachable, pkg, builtIn.get(pkg));
        }
        // a used name in two candidates is ambiguous: neither is added
        Map<String, Integer> usedCount = new HashMap<>();
        for (String pkg : candidates) {
            for (String name : libraryPackages.get(pkg)) {
                if (used.contains(name)) usedCount.put(name, usedCount.containsKey(name) ? usedCount.get(name) + 1 : 1);
            }
        }
        for (String pkg : candidates) {
            boolean clash = false;
            for (String name : libraryPackages.get(pkg)) {
                if (!used.contains(name)) continue;
                if (reachable.containsKey(name) || usedCount.get(name) > 1) {
                    clash = true;
                    break;
                }
            }
            if (!clash) result.add(pkg);
        }
        return result;
    }

    /** Public names in the given packages (or members of the given classes) of the built-in libraries' jars. */
    private static Map<String, Set<String>> builtInNames(File dir, Set<String> wanted) {
        Map<String, Set<String>> out = new HashMap<>();
        List<File> jars = new ArrayList<>();
        collectJars(dir, jars, 0);
        for (File jar : jars) {
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.startsWith("META-INF/")) continue;
                    int slash = name.lastIndexOf('/');
                    if (slash < 0) continue;
                    String pkg = name.substring(0, slash).replace('/', '.');
                    String simple = name.substring(slash + 1, name.length() - 6);
                    int dollar = simple.indexOf('$');
                    if (dollar >= 0) {
                        String outer = pkg + "." + simple.substring(0, dollar);
                        String inner = simple.substring(dollar + 1);
                        if (!wanted.contains(outer) || inner.isEmpty() || inner.indexOf('$') >= 0 || Character.isDigit(inner.charAt(0))) continue;
                        pkg = outer;
                        simple = inner;
                    } else if (!wanted.contains(pkg) || !isPublic(zip, entry)) {
                        continue;
                    }
                    Set<String> set = out.get(pkg);
                    if (set == null) out.put(pkg, set = new HashSet<>());
                    set.add(simple);
                }
            } catch (IOException | RuntimeException ignored) {
                // unreadable jar: its packages stay unknown
            }
        }
        return out;
    }

    private static void collectJars(File dir, List<File> out, int level) {
        if (dir == null || level > 3) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectJars(f, out, level + 1);
            else if (f.getName().endsWith(".jar")) out.add(f);
        }
    }

    /**
     * Adds the on-demand imports for the libraries' packages after the last import line. Never changes the code when
     * nothing is needed.
     */
    public static String apply(String code, Collection<File> libraryJars, Collection<String> rPackages, File androidJar,
                               File builtInLibraries) {
        List<String> packages;
        try {
            packages = forCode(code, libraryJars, rPackages, androidJar, builtInLibraries);
        } catch (RuntimeException e) {
            return code;
        }
        if (packages.isEmpty()) return code;
        Matcher m = IMPORT.matcher(code);
        int end = -1;
        while (m.find()) end = m.end();
        if (end < 0) return code;
        String eol = code.contains("\r\n") ? "\r\n" : "\n";   // the generator writes \r\n
        StringBuilder lines = new StringBuilder();
        for (String pkg : packages) lines.append(eol).append("import ").append(pkg).append(".*;");
        return code.substring(0, end) + lines + code.substring(end);
    }

    private static void addNames(Map<String, Set<String>> reachable, String pkg, Set<String> names) {
        if (names == null) return;
        for (String name : names) {
            Set<String> set = reachable.get(name);
            if (set == null) reachable.put(name, set = new HashSet<>());
            set.add(pkg);
        }
    }

    /** Capitalised simple names the code uses outside imports, comments and string or char literals. */
    static Set<String> usedNames(String code) {
        String body = IMPORT.matcher(stripLiterals(code)).replaceAll("");
        body = body.replaceAll("(?m)^\\s*package\\s+[\\w.]+\\s*;", "");
        Set<String> names = new HashSet<>();
        Matcher m = NAME.matcher(body);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** Comments and the insides of string and char literals blanked (line breaks kept). */
    static String stripLiterals(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int n = code.length();
        for (int i = 0; i < n; i++) {
            char c = code.charAt(i);
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                while (i < n && code.charAt(i) != '\n') i++;
                if (i < n) out.append('\n');
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                i += 2;
                while (i < n && !(code.charAt(i) == '*' && i + 1 < n && code.charAt(i + 1) == '/')) {
                    if (code.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i++;
                out.append(' ');
            } else if (c == '"' && i + 2 < n && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                // a text block (Java 15+): blanked up to its closing """
                int close = code.indexOf("\"\"\"", i + 3);
                int stopAt = close < 0 ? n : close + 3;
                out.append("\"\"");
                for (int k = i; k < stopAt; k++) if (code.charAt(k) == '\n') out.append('\n');
                i = stopAt - 1;
            } else if (c == '"' || c == '\'') {
                char q = c;
                out.append(q);
                i++;
                while (i < n && code.charAt(i) != q && code.charAt(i) != '\n') {
                    if (code.charAt(i) == '\\') i++;
                    i++;
                }
                out.append(q);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isPublic(ZipFile zip, ZipEntry entry) {
        try (InputStream raw = zip.getInputStream(entry); DataInputStream in = new DataInputStream(raw)) {
            if (in.readInt() != 0xCAFEBABE) return false;
            in.readInt();
            int count = in.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1:
                        in.skipBytes(in.readUnsignedShort());
                        break;
                    case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                        in.skipBytes(4);
                        break;
                    case 5: case 6:
                        in.skipBytes(8);
                        i++;
                        break;
                    case 7: case 8: case 16: case 19: case 20:
                        in.skipBytes(2);
                        break;
                    case 15:
                        in.skipBytes(3);
                        break;
                    default:
                        return false;
                }
            }
            return (in.readUnsignedShort() & 0x0001) != 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
