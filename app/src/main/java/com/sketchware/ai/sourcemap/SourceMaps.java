package com.sketchware.ai.sourcemap;

import com.besome.sketch.beans.ProjectFileBean;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.Jx;
import a.a.a.eC;
import a.a.a.jq;

/**
 * Builds, keeps and looks up the source maps of a project's generated activities.
 * <p>
 * A map is only kept when it is proven: the marked generation, with the markers taken out, must
 * be character for character the code the normal generation produced. Otherwise there is no map
 * for that file and a lookup says so; a wrong block is never shown.
 */
public final class SourceMaps {

    /** One file, written when the project is compiled: mysc/&lt;sc_id&gt;/sourcemap.json. */
    public static final String FILE_NAME = "sourcemap.json";
    /** Which generator the lines belong to; a different one makes its own map at its first build. */
    public static final String GENERATOR = "sketchware-ai/markers-1";

    /** Maps made during the generation that is running now, until {@link #finish} writes them. */
    private static final Map<String, Map<String, SourceMap>> STAGED = new HashMap<>();
    /** Why a file has no map, for the build log. */
    private static final Map<String, String> PROBLEMS = new HashMap<>();
    /** The project whose sources this thread is generating for a build, if any. */
    private static final ThreadLocal<String> COLLECTING = new ThreadLocal<>();
    /** Last sourcemap.json read: path, its modification time, its maps. */
    private static String loadedPath;
    private static long loadedStamp;
    private static SourceMap[] loaded;

    private SourceMaps() {
    }

    /**
     * Called when a build starts generating the project's sources on this thread. Maps are only
     * made between this and {@link #finish}; viewing source code doesn't generate twice.
     */
    public static void start(String scId) {
        synchronized (STAGED) {
            STAGED.remove(scId);
            for (java.util.Iterator<String> it = PROBLEMS.keySet().iterator(); it.hasNext(); ) {
                if (it.next().startsWith(scId + "/")) it.remove();
            }
        }
        COLLECTING.set(scId);
    }

    /**
     * Called by the activity generator after it produced {@code classic} (the formatted code) and
     * {@code written} (after command blocks, what goes to disk). Never throws and never changes
     * what is generated.
     */
    public static void build(jq buildConfig, ProjectFileBean file, eC projectData, String classic, String written) {
        if (SourceMapMarkers.active() || buildConfig == null || file == null) return;
        String scId = buildConfig.sc_id;
        if (scId == null || !scId.equals(COLLECTING.get())) return;
        String name = file.getJavaName();
        try {
            String marked;
            List<SourceMapMarkers.Event> session;
            SourceMapMarkers.begin();
            try {
                marked = new Jx(buildConfig, file, projectData).formattedCode();
            } finally {
                session = SourceMapMarkers.end();
            }
            SourceMap.Extracted extracted = SourceMap.extract(marked, session);
            synchronized (STAGED) {
                Map<String, SourceMap> maps = STAGED.get(scId);
                if (maps == null) STAGED.put(scId, maps = new LinkedHashMap<>());
                if (!extracted.code.equals(classic)) {
                    maps.remove(name);
                    PROBLEMS.put(scId + "/" + name, "generating it twice gave different code, first at line "
                            + firstDifferentLine(extracted.code, classic));
                    return;
                }
                extracted.map.file = name;
                // The compiler also ends a line at a lone \r; the map counts \n. Don't vouch for such a file.
                extracted.map.exact = sameLines(written, classic) && !hasLoneCarriageReturn(written);
                extracted.map.sourceHash = sha1(written.getBytes(StandardCharsets.UTF_8));
                extracted.map.generator = GENERATOR;
                maps.put(name, extracted.map);
                if (!extracted.map.exact) {
                    PROBLEMS.put(scId + "/" + name, "command blocks changed the file after its lines were noted, or it has unusual line ends");
                }
            }
        } catch (Throwable t) {
            synchronized (STAGED) {
                Map<String, SourceMap> maps = STAGED.get(scId);
                if (maps != null) maps.remove(name);
                PROBLEMS.put(scId + "/" + name, String.valueOf(t));
            }
        }
    }

    private static int firstDifferentLine(String a, String b) {
        int line = 1;
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) return line;
            if (a.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** A \r that isn't part of \r\n, other than at the very end of the file (nothing follows it there). */
    private static boolean hasLoneCarriageReturn(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) end--;
        for (int i = text.indexOf('\r'); i >= 0 && i < end; i = text.indexOf('\r', i + 1)) {
            if (text.charAt(i + 1) != '\n') return true;
        }
        return false;
    }

    /**
     * Command-block processing splits and re-joins the file even when the project has no command
     * blocks, which only changes how the file ends. Line numbers are the same as long as the two
     * texts differ in nothing but trailing line breaks.
     */
    static boolean sameLines(String written, String classic) {
        int a = written.length();
        int b = classic.length();
        while (a > 0 && (written.charAt(a - 1) == '\n' || written.charAt(a - 1) == '\r')) a--;
        while (b > 0 && (classic.charAt(b - 1) == '\n' || classic.charAt(b - 1) == '\r')) b--;
        return a == b && written.regionMatches(0, classic, 0, a);
    }

    /**
     * Called when the build has written the generated sources: stores the maps next to them, in
     * mysc/&lt;sc_id&gt;/sourcemap.json. A map whose file on disk isn't the text it was made for
     * is not written.
     *
     * @param javaPackageDirectory where the activities' .java files were written
     */
    public static void finish(String scId, File projectMyscDirectory, File javaPackageDirectory) {
        COLLECTING.remove();
        Map<String, SourceMap> maps;
        synchronized (STAGED) {
            maps = STAGED.remove(scId);
        }
        try {
            List<SourceMap> keep = new java.util.ArrayList<>();
            if (maps != null) {
                for (SourceMap map : maps.values()) {
                    String onDisk = sha1(new File(javaPackageDirectory, map.file));
                    if (onDisk != null && onDisk.equals(map.sourceHash)) {
                        keep.add(map);
                    } else {
                        synchronized (STAGED) {
                            PROBLEMS.put(scId + "/" + map.file, "the file on disk isn't the generated text");
                        }
                    }
                }
            }
            File target = new File(projectMyscDirectory, FILE_NAME);
            File temporary = new File(projectMyscDirectory, FILE_NAME + ".tmp");
            try (OutputStream out = new FileOutputStream(temporary)) {
                out.write(new Gson().toJson(keep.toArray(new SourceMap[0])).getBytes(StandardCharsets.UTF_8));
            }
            if (!temporary.renameTo(target)) {
                target.delete();
                temporary.renameTo(target);
            }
        } catch (Exception e) {
            // The map is a convenience: building goes on without it. A stale one must not stay.
            new File(projectMyscDirectory, FILE_NAME).delete();
        }
    }

    /** The map the last build wrote for a generated file, or null. */
    public static SourceMap get(File projectMyscDirectory, String javaFileName) {
        try {
            File source = new File(projectMyscDirectory, FILE_NAME);
            if (!source.isFile()) return null;
            SourceMap[] maps;
            synchronized (STAGED) {
                if (!source.getPath().equals(loadedPath) || source.lastModified() != loadedStamp || loaded == null) {
                    loaded = new Gson().fromJson(new String(read(source), StandardCharsets.UTF_8), SourceMap[].class);
                    loadedPath = source.getPath();
                    loadedStamp = source.lastModified();
                }
                maps = loaded;
            }
            if (maps == null) return null;
            for (SourceMap map : maps) {
                if (map != null && javaFileName.equals(map.file) && map.version == SourceMap.VERSION) return map;
            }
        } catch (Exception e) {
            // Unreadable: no map.
        }
        return null;
    }

    /**
     * What a line of a generated file comes from.
     *
     * @param javaFile the generated file the compiler named
     * @return null when that isn't known for certain: no map, a file the map wasn't made for
     * (its hash is checked), or a line that isn't block code
     */
    public static SourceMap.Origin resolve(File projectMyscDirectory, File javaFile, int line) {
        SourceMap map = get(projectMyscDirectory, javaFile.getName());
        if (map == null || !map.exact || map.sourceHash == null) return null;
        if (!map.sourceHash.equals(sha1(javaFile))) return null;
        return map.resolve(line);
    }

    /** Why {@code javaFileName} got no map in the generation that just ran, or null. */
    public static String problem(String scId, String javaFileName) {
        synchronized (STAGED) {
            return PROBLEMS.get(scId + "/" + javaFileName);
        }
    }

    /** "MainActivity → MoreBlock Bt_Perm → block 3 (add source directly), line 46 of its code". */
    public static String describe(SourceMap.Origin origin) {
        StringBuilder text = new StringBuilder();
        String activity = origin.file.endsWith(".java") ? origin.file.substring(0, origin.file.length() - 5) : origin.file;
        text.append(activity).append(" → ").append(describe(origin.event));
        if (origin.block != null) {
            text.append(" → block ").append(origin.block.position).append(" (").append(blockName(origin.block.opCode)).append(")");
            if (origin.block.end > origin.block.start) {
                text.append(", line ").append(origin.lineInBlock).append(" of its code");
            }
        }
        return text.toString();
    }

    public static String describe(SourceMap.EventInfo event) {
        if ("moreBlock".equals(event.kind)) return "MoreBlock " + event.targetId;
        if ("onCreate".equals(event.kind)) return "onCreate";
        if (event.targetId != null && event.targetId.equals(event.eventName)) return event.eventName;
        if ("adapter".equals(event.kind)) return event.targetId + " onBindCustomView";
        return event.targetId + " " + event.eventName;
    }

    private static String blockName(String opCode) {
        if (opCode == null) return "block";
        if (opCode.equals("addSourceDirectly")) return "add source directly";
        if (opCode.equals("definedFunc")) return "MoreBlock call";
        return opCode;
    }

    static byte[] read(File file) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
    }

    /** @return the file's SHA-1, or null if it can't be read */
    static String sha1(File file) {
        try {
            return sha1(read(file));
        } catch (Exception e) {
            return null;
        }
    }

    static String sha1(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
