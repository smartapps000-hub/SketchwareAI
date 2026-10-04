package com.sketchware.ai.doctor;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every Run build of a project, newest first: when, how it ended, at which step, what was wrong
 * and what the build changed by itself. Kept in data/&lt;sc_id&gt;/build_history.json, so it stays
 * with the project (the build's own folder is emptied at every build).
 */
public final class BuildHistory {

    public static final String FILE_NAME = "build_history.json";
    private static final int KEEP = 40;

    public static final class Entry {
        public long time;
        /** "v1.8.2-abc (172)" */
        public String app;
        /** "ok" or "failed" */
        public String result;
        /** The step that failed, or null. */
        public String stage;
        /** {@link BuildDoctor} code of the failure, or null. */
        public String code;
        /** One line: what was wrong; null for a clean build. */
        public String problem;
        public long durationMs;
        public int warnings;
        /** What the build repaired by itself. */
        public List<String> changes = new ArrayList<>();
        /** For each change: "permanent", "temporary" or "". */
        public List<String> kinds = new ArrayList<>();
        /** {@link #fingerprint} of the project when the build ended; null in entries of older versions. */
        public String fingerprint;
    }

    private BuildHistory() {
    }

    /** Notes a finished build from its log (the log shown in the compile log screen). */
    public static synchronized void record(File dataDirectory, String app, String log, long durationMs) {
        record(dataDirectory, null, app, log, durationMs);
    }

    /**
     * A hash of what a build is made from: the content of the project's saved files and of the
     * editor's working copy, and name, size and time of its own files, images, sounds and fonts.
     * Two equal fingerprints mean a new build would get the same input. The content is hashed, not
     * the file times, because the editor rewrites its working copy without changing it.
     *
     * @param sketchwareRoot /storage/emulated/0/.sketchware
     */
    public static String fingerprint(File sketchwareRoot, String scId) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            int[] budget = {4000};
            for (String folder : new String[]{"data/" + scId, "bak/" + scId}) {
                File[] files = new File(sketchwareRoot, folder).listFiles();
                if (files == null) continue;
                java.util.Arrays.sort(files);
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith("_history.json") || name.endsWith(".tmp") || name.equals("repair_backup")) continue;
                    if (file.isDirectory()) {
                        listing(digest, file, folder + "/" + name, 0, budget);
                    } else if (file.length() <= 24L * 1024 * 1024) {
                        digest.update((folder + "/" + name + "\n").getBytes(StandardCharsets.UTF_8));
                        try (InputStream in = new FileInputStream(file)) {
                            byte[] buffer = new byte[32768];
                            int n;
                            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
                        }
                    }
                }
            }
            for (String kind : new String[]{"images", "sounds", "fonts"}) {
                listing(digest, new File(sketchwareRoot, "resources/" + kind + "/" + scId), kind, 0, budget);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void listing(java.security.MessageDigest digest, File directory, String path, int depth, int[] budget) {
        File[] files = directory.listFiles();
        if (files == null || depth > 5) return;
        java.util.Arrays.sort(files);
        for (File file : files) {
            if (budget[0]-- <= 0) return;
            if (file.isDirectory()) {
                listing(digest, file, path + "/" + file.getName(), depth + 1, budget);
            } else {
                digest.update((path + "/" + file.getName() + ":" + file.length() + ":" + file.lastModified() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Notes a finished build; {@code fingerprint} is the project's state the build was made from. */
    public static synchronized void record(File dataDirectory, String fingerprint, String app, String log, long durationMs) {
        try {
            BuildDoctor.Diagnosis d = BuildDoctor.diagnose(log);
            Entry entry = new Entry();
            entry.time = System.currentTimeMillis();
            entry.app = app;
            entry.fingerprint = fingerprint;
            entry.result = d.succeeded || log == null || log.isEmpty() ? "ok" : "failed";
            entry.durationMs = durationMs;
            entry.warnings = d.warnings.size();
            if (!"ok".equals(entry.result)) {
                entry.stage = d.stage;
                entry.code = d.code;
                entry.problem = d.title;
            }
            for (BuildDoctor.Change change : d.changes) {
                entry.changes.add(change.text.length() > 400 ? change.text.substring(0, 397) + "…" : change.text);
                entry.kinds.add(change.permanent == null ? "" : change.permanent ? "permanent" : "temporary");
            }
            List<Entry> all = load(dataDirectory);
            all.add(0, entry);
            while (all.size() > KEEP) all.remove(all.size() - 1);
            File target = new File(dataDirectory, FILE_NAME);
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            File temporary = new File(target.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(temporary)) {
                out.write(new Gson().toJson(all.toArray(new Entry[0])).getBytes(StandardCharsets.UTF_8));
            }
            if (!temporary.renameTo(target)) {
                target.delete();
                temporary.renameTo(target);
            }
        } catch (Throwable t) {
            // The history is a convenience; a build never depends on it.
        }
    }

    public static synchronized List<Entry> load(File dataDirectory) {
        List<Entry> entries = new ArrayList<>();
        try {
            File source = new File(dataDirectory, FILE_NAME);
            if (!source.isFile()) return entries;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(source)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
            }
            Entry[] loaded = new Gson().fromJson(new String(bytes.toByteArray(), StandardCharsets.UTF_8), Entry[].class);
            if (loaded != null) for (Entry entry : loaded) if (entry != null) entries.add(entry);
        } catch (Exception e) {
            // No history.
        }
        return entries;
    }

    /** How many of the newest builds in a row failed with the same step and the same problem as the newest. */
    public static int sameFailureInARow(List<Entry> entries) {
        if (entries.isEmpty() || !"failed".equals(entries.get(0).result)) return 0;
        Entry first = entries.get(0);
        int n = 0;
        for (Entry entry : entries) {
            if (!"failed".equals(entry.result) || !same(entry.stage, first.stage) || !same(entry.problem, first.problem)) break;
            n++;
        }
        return n;
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
