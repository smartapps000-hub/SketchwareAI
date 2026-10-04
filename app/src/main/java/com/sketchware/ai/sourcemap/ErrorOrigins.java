package com.sketchware.ai.sourcemap;

import com.sketchware.ai.project.AutoHeal;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Says where each Java compiler error of a build log comes from in the project: the activity,
 * the event or MoreBlock, and the block. Uses the source map the build wrote; when the map can't
 * vouch for a line, the answer is "generated code", never a guess.
 */
public final class ErrorOrigins {

    /** One compiler error and, when known, the block its line was generated from. */
    public static final class Item {
        /** "MainActivity.java" */
        public String fileName;
        public File file;
        public int line;
        public String message;
        /** The source line the compiler quoted. */
        public String excerpt;
        /** Null when the line isn't block code or can't be verified. */
        public SourceMap.Origin origin;

        /** "MainActivity → MoreBlock Bt_Perm → block 2 (add source directly), line 66 of its code" */
        public String originText() {
            return origin == null ? "generated source only (no block for this line)" : SourceMaps.describe(origin);
        }
    }

    private ErrorOrigins() {
    }

    /**
     * The maps of one build, checked against the generated files before the build's repairs touch
     * them. Repairs never add or remove lines, so the lines stay valid for the whole build.
     */
    public static final class Session {
        private final Map<String, SourceMap> verified = new HashMap<>();
        private final java.util.Set<String> checked = new java.util.HashSet<>();
        /** The generated text of each mapped file as it was before any repair. */
        private final Map<String, String> originals = new HashMap<>();

        /** Paths of the generated files this build has a proven map for. */
        public java.util.Set<String> mappedFiles() {
            return verified.keySet();
        }

        public SourceMap map(String path) {
            return verified.get(path);
        }

        /** @return the file's text as generated, before the build repaired anything; null if not kept */
        public String original(String path) {
            return originals.get(path);
        }

        /** What a line of a generated file comes from, or null. */
        public SourceMap.Origin resolve(File javaFile, int line) {
            SourceMap map = verified.get(javaFile.getPath());
            return map == null ? null : map.resolve(line);
        }

        /** Adds where the line is from to a repair message that starts with "File.java:123 ". */
        public String annotate(String message) {
            try {
                Matcher m = MESSAGE.matcher(message);
                if (!m.find()) return message;
                for (Map.Entry<String, SourceMap> entry : verified.entrySet()) {
                    if (!new File(entry.getKey()).getName().equals(m.group(1))) continue;
                    SourceMap.Origin origin = entry.getValue().resolve(Integer.parseInt(m.group(2)));
                    if (origin != null) return message + " [" + SourceMaps.describe(origin) + "]";
                }
            } catch (RuntimeException e) {
                // The message goes out as it is.
            }
            return message;
        }

        /**
         * The "Where the errors come from" section for a failed build's log, or "". Its last
         * lines name the maps it was made with; a log is only resolved again with those.
         */
        public String report(String compilerLog) {
            // The build's repairs have been taken back by now: the files are as generated.
            List<Item> items = find(compilerLog, this, true);
            String text = ErrorOrigins.report(items);
            if (text.isEmpty()) return text;
            StringBuilder tags = new StringBuilder();
            for (Item item : items) {
                SourceMap map = verified.get(item.file.getPath());
                if (map == null) continue;
                String tag = tag(map);
                if (tags.indexOf(tag) < 0) tags.append("\n    (source map ").append(tag).append(")");
            }
            return text + tags;
        }
    }

    private static final Pattern MESSAGE = Pattern.compile("^([\\w$]+\\.java):(\\d+) ");

    /**
     * @param projectMyscDirectory mysc/&lt;sc_id&gt;, where the build keeps its source map
     * @param javaRoot             the build's generated Java sources
     */
    public static Session session(File projectMyscDirectory, File javaRoot) {
        Session session = new Session();
        try {
            List<File> files = new ArrayList<>();
            collect(javaRoot, files, 0);
            for (File file : files) {
                SourceMap map = SourceMaps.get(projectMyscDirectory, file.getName());
                if (map == null || !map.exact || map.sourceHash == null) continue;
                byte[] bytes = SourceMaps.read(file);
                if (map.sourceHash.equals(SourceMaps.sha1(bytes))) {
                    session.verified.put(file.getPath(), map);
                    session.originals.put(file.getPath(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (RuntimeException | java.io.IOException e) {
            // No origins for this build.
        }
        return session;
    }

    private static void collect(File directory, List<File> out, int depth) {
        File[] children = directory.listFiles();
        if (children == null || depth > 12) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out, depth + 1);
            else if (child.getName().endsWith(".java")) out.add(child);
        }
    }

    /**
     * For looking at a finished build's log: every line is checked against the file as it is on
     * disk now.
     *
     * @param projectMyscDirectory mysc/&lt;sc_id&gt;, where the build keeps its source map
     * @return the log's Java errors in log order (the same line is listed once)
     */
    public static List<Item> find(File projectMyscDirectory, String compilerLog) {
        /*
         * A saved log can be older than the map (the project was built again since). So a file's
         * map is used only if the log names that very map, the file on disk still has the map's
         * hash, and (per error) the line on disk is the line the compiler quoted.
         */
        Session session = new Session();
        try {
            for (AutoHeal.Problem problem : AutoHeal.parse(compilerLog == null ? "" : compilerLog)) {
                if (problem.file == null || session.checked.contains(problem.file.getPath())) continue;
                session.checked.add(problem.file.getPath());
                SourceMap map = SourceMaps.get(projectMyscDirectory, problem.file.getName());
                if (map == null || !map.exact || map.sourceHash == null) continue;
                if (!compilerLog.contains("(source map " + tag(map) + ")")) continue;
                if (map.sourceHash.equals(SourceMaps.sha1(problem.file))) session.verified.put(problem.file.getPath(), map);
            }
        } catch (RuntimeException e) {
            // No origins.
        }
        return find(compilerLog, session, true);
    }

    /** "MainActivity.java@3f2a9c1e0b": a file's map, as a failed build's log names it. */
    private static String tag(SourceMap map) {
        String hash = map.sourceHash == null ? "" : map.sourceHash;
        return map.file + "@" + (hash.length() > 10 ? hash.substring(0, 10) : hash);
    }

    /** Letters, digits and punctuation of a line, without spaces and non-ASCII (the log escapes those). */
    private static String essence(String line) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c > ' ' && c < 127) out.append(c);
        }
        return out.toString();
    }

    private static boolean quotedLineIsOnDisk(AutoHeal.Problem problem, Map<String, String[]> files) {
        try {
            String[] lines = files.get(problem.file.getPath());
            if (lines == null) {
                lines = new String(SourceMaps.read(problem.file), java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
                files.put(problem.file.getPath(), lines);
            }
            if (problem.line < 1 || problem.line > lines.length || problem.excerpt == null) return false;
            String quoted = essence(problem.excerpt);
            return !quoted.isEmpty() && essence(lines[problem.line - 1]).equals(quoted);
        } catch (Exception e) {
            return false;
        }
    }

    private static List<Item> find(String compilerLog, Session session, boolean checkQuotedLine) {
        Map<String, String[]> files = new HashMap<>();
        List<Item> items = new ArrayList<>();
        if (compilerLog == null || compilerLog.isEmpty()) return items;
        List<AutoHeal.Problem> problems;
        try {
            problems = AutoHeal.parse(compilerLog);
        } catch (RuntimeException e) {
            return items;
        }
        for (AutoHeal.Problem problem : problems) {
            if (problem.file == null) continue;
            boolean seen = false;
            for (Item item : items) {
                if (item.line == problem.line && item.file.equals(problem.file) && item.message.equals(problem.message)) seen = true;
            }
            if (seen) continue;
            Item item = new Item();
            item.file = problem.file;
            item.fileName = problem.file.getName();
            item.line = problem.line;
            item.message = problem.message;
            item.excerpt = problem.excerpt == null ? "" : problem.excerpt.trim();
            try {
                item.origin = session.resolve(problem.file, problem.line);
                if (item.origin != null && checkQuotedLine && !quotedLineIsOnDisk(problem, files)) item.origin = null;
            } catch (RuntimeException e) {
                item.origin = null;
            }
            items.add(item);
        }
        return items;
    }

    private static String report(List<Item> items) {
        if (items.isEmpty()) return "";
        StringBuilder text = new StringBuilder("Where the errors come from:\n");
        int shown = 0;
        for (Item item : items) {
            if (shown == 20) {
                text.append("    (").append(items.size() - shown).append(" more)\n");
                break;
            }
            text.append("    ").append(item.fileName).append(':').append(item.line).append("  ").append(item.originText()).append('\n');
            shown++;
        }
        return text.toString().trim();
    }
}
