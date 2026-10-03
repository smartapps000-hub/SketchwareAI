package com.sketchware.ai.project;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the class a simple name ("PackageManager") refers to among the jars the project is
 * compiled against (android.jar, the enabled built-in and local libraries), for adding a missing
 * import. Offline; the jars are only read.
 */
public final class ClassIndex {

    /** Where to look first when several packages have a class of that name. */
    private static final String[] PREFERRED = {
            "android.widget.", "android.view.", "android.app.", "android.content.", "android.os.",
            "android.graphics.", "android.text.", "android.net.", "android.util.", "android.media.",
            "android.", "androidx.", "com.google.android.material.", "java.util.", "java.io.", "java.text.",
            "java.net.", "java.", "org.json."};

    private final String classpath;
    /** simple name -> {fully qualified name, jar path} */
    private Map<String, List<String[]>> bySimpleName;

    public ClassIndex(String classpath) {
        this.classpath = classpath == null ? "" : classpath;
    }

    /** @return the fully qualified name to import, or null if there is none or no clear choice */
    public synchronized String find(String simpleName) {
        if (simpleName.equals("R") || simpleName.equals("BuildConfig")) return null;
        if (bySimpleName == null) build();
        List<String[]> candidates = bySimpleName.get(simpleName);
        if (candidates == null) return null;
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        boolean tie = false;
        List<String> seen = new ArrayList<>();
        for (String[] candidate : candidates) {
            String fqn = candidate[0];
            if (seen.contains(fqn) || fqn.startsWith("java.lang.") && fqn.indexOf('.', 10) < 0) continue;
            if (!isPublic(candidate[1], fqn)) continue;
            seen.add(fqn);
            int rank = PREFERRED.length;
            for (int i = 0; i < PREFERRED.length; i++) {
                if (fqn.startsWith(PREFERRED[i])) {
                    rank = i;
                    break;
                }
            }
            if (rank < bestRank) {
                best = fqn;
                bestRank = rank;
                tie = false;
            } else if (rank == bestRank) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private void build() {
        bySimpleName = new HashMap<>();
        for (String path : classpath.split(":")) {
            File jar = new File(path.trim());
            if (!jar.isFile()) continue;
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class") || name.indexOf('$') >= 0 || name.startsWith("META-INF/")) continue;
                    int slash = name.lastIndexOf('/');
                    if (slash < 0) continue;
                    String simple = name.substring(slash + 1, name.length() - 6);
                    String fqn = name.substring(0, name.length() - 6).replace('/', '.');
                    List<String[]> list = bySimpleName.get(simple);
                    if (list == null) bySimpleName.put(simple, list = new ArrayList<>(1));
                    list.add(new String[]{fqn, jar.getPath()});
                }
            } catch (IOException | RuntimeException e) {
                // Unreadable jar: contributes nothing.
            }
        }
    }

    private static boolean isPublic(String jar, String fqn) {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(fqn.replace('.', '/') + ".class");
            if (entry == null) return false;
            try (InputStream raw = zip.getInputStream(entry); DataInputStream in = new DataInputStream(raw)) {
                if (in.readInt() != 0xCAFEBABE) return false;
                in.readInt(); // minor, major
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
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
