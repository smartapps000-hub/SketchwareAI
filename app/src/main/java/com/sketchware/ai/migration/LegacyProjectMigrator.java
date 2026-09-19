package com.sketchware.ai.migration;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Copies projects from a Sketchware Pro install (/.sketchware) into Sketchware AI's own
 * storage (/.sketchware_ai).
 * <p>
 * Rules:
 * <ul>
 *     <li>The legacy folder is only ever read. Nothing in it is modified or deleted, so the user
 *     can keep using Sketchware Pro side by side.</li>
 *     <li>Files already present in Sketchware AI's folder are never overwritten.</li>
 *     <li>Each project becomes visible only after all of its files are copied: its entry in
 *     mysc/list is written to a hidden temporary folder first and renamed into place last.</li>
 *     <li>Absolute paths to the legacy folder inside plain-text project settings are rewritten
 *     so the imported project does not keep depending on Sketchware Pro's files.</li>
 * </ul>
 * Call {@link #run} on a background thread.
 */
public final class LegacyProjectMigrator {

    public static final String LEGACY_ROOT_NAME = ".sketchware";
    public static final String ROOT_NAME = ".sketchware_ai";

    private static final String TAG = "LegacyProjectMigrator";
    private static final String DECLINED_MARKER = ".legacy_import_declined";
    private static final String DONE_MARKER = ".legacy_import_done";
    /** Present while an import is running or was interrupted; lets the next start resume it. */
    private static final String IN_PROGRESS_MARKER = ".legacy_import_in_progress";

    /** Top-level folders of the legacy root that hold nothing worth importing. */
    private static final Set<String> SKIPPED_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "temp", "download", "tutorial", "upload"));

    /** Plain-text per-project settings files that may contain absolute legacy paths. */
    private static final String[] PATH_BEARING_FILES = {
            "local_library", "build_config", "project_config"};

    public interface ProgressListener {
        void onProgress(Progress progress);
    }

    /** A snapshot of the import's progress. Immutable; safe to hand to the UI thread. */
    public static final class Progress {
        /** 1-based index of the project being imported; 0 while shared files are copied. */
        public final int projectIndex;
        public final int projectTotal;
        public final long bytesDone;
        public final long bytesTotal;
        /** Short description of the current step, e.g. "Copying custom blocks". */
        public final String step;

        Progress(int projectIndex, int projectTotal, long bytesDone, long bytesTotal, String step) {
            this.projectIndex = projectIndex;
            this.projectTotal = projectTotal;
            this.bytesDone = bytesDone;
            this.bytesTotal = bytesTotal;
            this.step = step;
        }

        /** 0..100, from the bytes copied so far. */
        public int percent() {
            if (bytesTotal <= 0) return 0;
            return (int) Math.min(100, bytesDone * 100 / bytesTotal);
        }
    }

    /** Lets the caller stop the import between two files. */
    public interface CancelSignal {
        boolean isCancelled();
    }

    public static final class Result {
        public final int importedProjects;
        public final int skippedProjects;
        public final boolean cancelled;
        public final String error;

        public Result(int importedProjects, int skippedProjects, boolean cancelled, String error) {
            this.importedProjects = importedProjects;
            this.skippedProjects = skippedProjects;
            this.cancelled = cancelled;
            this.error = error;
        }

        Result(int importedProjects, int skippedProjects, String error) {
            this(importedProjects, skippedProjects, false, error);
        }

        public boolean isSuccess() {
            return error == null && !cancelled;
        }
    }

    private static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    /** Tracks bytes copied and throttles progress callbacks to a few per second. */
    private static final class Meter {
        private static final long MIN_INTERVAL_MS = 120;
        final ProgressListener listener;
        final CancelSignal cancel;
        final long bytesTotal;
        final int projectTotal;
        long bytesDone;
        int projectIndex;
        String step = "";
        private long lastReport;

        Meter(ProgressListener listener, CancelSignal cancel, long bytesTotal, int projectTotal) {
            this.listener = listener;
            this.cancel = cancel;
            this.bytesTotal = bytesTotal;
            this.projectTotal = projectTotal;
        }

        void step(int projectIndex, String step) throws CancelledException {
            this.projectIndex = projectIndex;
            this.step = step;
            report(true);
        }

        void add(long bytes) throws CancelledException {
            bytesDone += bytes;
            report(false);
        }

        void report(boolean force) throws CancelledException {
            if (cancel != null && cancel.isCancelled()) throw new CancelledException();
            if (listener == null) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (!force && now - lastReport < MIN_INTERVAL_MS) return;
            lastReport = now;
            listener.onProgress(new Progress(projectIndex, projectTotal,
                    Math.min(bytesDone, bytesTotal), bytesTotal, step));
        }
    }

    private LegacyProjectMigrator() {
    }

    public static File legacyRoot() {
        return new File(Environment.getExternalStorageDirectory(), LEGACY_ROOT_NAME);
    }

    public static File root() {
        return new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
    }

    /** Number of project folders in Sketchware Pro's project list. */
    public static int legacyProjectCount() {
        return countDirectories(new File(legacyRoot(), "mysc" + File.separator + "list"));
    }

    /** Number of projects already present in Sketchware AI's project list. */
    public static int projectCount() {
        return countDirectories(new File(root(), "mysc" + File.separator + "list"));
    }

    /**
     * Whether to offer the import: Sketchware Pro has projects, Sketchware AI has none yet,
     * and the user has neither imported nor declined before.
     */
    public static boolean shouldOffer(Context context) {
        File filesDir = context.getFilesDir();
        if (new File(filesDir, DECLINED_MARKER).exists() || new File(filesDir, DONE_MARKER).exists()) {
            return false;
        }
        if (legacyProjectCount() == 0) return false;
        // An interrupted import resumes: finished projects and existing files are skipped.
        return projectCount() == 0 || new File(filesDir, IN_PROGRESS_MARKER).exists();
    }

    /** Whether an earlier import was interrupted (app killed, cancelled, error). */
    public static boolean wasInterrupted(Context context) {
        return new File(context.getFilesDir(), IN_PROGRESS_MARKER).exists();
    }

    public static void markDeclined(Context context) {
        touch(new File(context.getFilesDir(), DECLINED_MARKER));
    }

    /** Clears the "declined" and "done" flags so the import can be offered or run again. */
    public static void resetOffer(Context context) {
        new File(context.getFilesDir(), DECLINED_MARKER).delete();
        new File(context.getFilesDir(), DONE_MARKER).delete();
    }

    /** Per-project folders, relative to the root, besides mysc/list/&lt;id&gt;. */
    private static java.util.List<String> projectPaths(File legacy, String id) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add("data" + File.separator + id);
        File[] resourceTypes = new File(legacy, "resources").listFiles();
        if (resourceTypes != null) {
            java.util.Arrays.sort(resourceTypes);
            for (File type : resourceTypes) {
                if (type.isDirectory()) {
                    paths.add("resources" + File.separator + type.getName() + File.separator + id);
                }
            }
        }
        paths.add("bak" + File.separator + id);
        return paths;
    }

    /** Project ids from mysc/list, in numeric order (601, 602, ... 1000). */
    private static java.util.List<String> projectIds(File legacyList) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        File[] projects = legacyList.listFiles();
        if (projects == null) return ids;
        for (File project : projects) {
            if (project.isDirectory() && !project.getName().startsWith(".")) ids.add(project.getName());
        }
        java.util.Collections.sort(ids, (a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        return ids;
    }

    /**
     * Runs the import. Blocking; call from a worker thread.
     * <p>
     * Order: shared folders first (custom blocks, components, libraries, ...), then one project
     * at a time (its data, resources and history, then its list entry last), so an interrupted
     * import leaves only whole projects behind.
     *
     * @param listener receives throttled progress snapshots; may be null
     * @param cancel   polled between files; may be null
     */
    public static Result run(Context context, ProgressListener listener, CancelSignal cancel) {
        File legacy = legacyRoot();
        File target = root();
        File legacyList = new File(legacy, "mysc" + File.separator + "list");
        File targetList = new File(target, "mysc" + File.separator + "list");

        if (!legacyList.isDirectory()) {
            return new Result(0, 0, "No Sketchware Pro projects were found in " + legacyList);
        }
        File[] topLevel = legacy.listFiles();
        if (topLevel == null) {
            return new Result(0, 0, "Cannot read " + legacy + ". Grant storage access and retry.");
        }
        java.util.Arrays.sort(topLevel);

        java.util.List<String> ids = projectIds(legacyList);
        java.util.List<String> toImport = new java.util.ArrayList<>();
        int skipped = 0;
        for (String id : ids) {
            if (new File(targetList, id).exists()) skipped++;
            else toImport.add(id);
        }

        // Every per-project path is copied in the project phase, never in the shared phase.
        Set<String> projectOwned = new HashSet<>();
        for (String id : ids) {
            for (String path : projectPaths(legacy, id)) {
                projectOwned.add(new File(legacy, path).getAbsolutePath());
            }
        }

        if (listener != null) listener.onProgress(new Progress(0, ids.size(), 0, 0,
                "Measuring Sketchware Pro's files"));
        long sharedBytes = 0;
        for (File entry : topLevel) {
            if (isSharedTopLevel(entry)) sharedBytes += sizeOf(entry, projectOwned);
        }
        long projectBytes = 0;
        for (String id : toImport) {
            projectBytes += sizeOf(new File(legacyList, id), null);
            for (String path : projectPaths(legacy, id)) {
                projectBytes += sizeOf(new File(legacy, path), null);
            }
        }
        long needed = sharedBytes + projectBytes;
        long available = availableBytes(Environment.getExternalStorageDirectory());
        if (available >= 0 && needed > available) {
            return new Result(0, 0, "Not enough free storage: the import needs "
                    + megabytes(needed) + " MB but only " + megabytes(available) + " MB is free.");
        }

        touch(new File(context.getFilesDir(), IN_PROGRESS_MARKER));
        Meter meter = new Meter(listener, cancel, needed, ids.size());
        int imported = 0;
        File stagingRoot = new File(target, "mysc" + File.separator + ".import-staging");
        try {
            for (File entry : topLevel) {
                if (!isSharedTopLevel(entry)) continue;
                meter.step(0, "Copying " + describe(entry.getName()));
                copyTreeWithoutOverwriting(entry, new File(target, entry.getName()), projectOwned, meter);
            }

            if (!targetList.isDirectory() && !targetList.mkdirs()) {
                return new Result(0, 0, "Cannot create " + targetList);
            }

            for (int i = 0; i < toImport.size(); i++) {
                String id = toImport.get(i);
                String name = projectName(new File(legacyList, id), id);
                // Numbered among all of Sketchware Pro's projects, so a resumed import reads
                // "Importing project 10 of 26" rather than restarting at 1.
                meter.step(skipped + i + 1, name);
                for (String path : projectPaths(legacy, id)) {
                    copyTreeWithoutOverwriting(new File(legacy, path), new File(target, path), null, meter);
                }
                // Staged next to mysc/list (same file system, so the rename below is atomic)
                // so the project list never sees a half-copied project.
                File staging = new File(stagingRoot, id);
                deleteTree(staging);
                copyTreeWithoutOverwriting(new File(legacyList, id), staging, null, meter);
                rewriteLegacyPaths(new File(target, "data" + File.separator + id));
                writeImportMarker(new File(target, "data" + File.separator + id));
                if (!staging.renameTo(new File(targetList, id))) {
                    deleteTree(staging);
                    throw new IOException("Could not move project " + id + " into place");
                }
                imported++;
            }

            stagingRoot.delete();
            touch(new File(context.getFilesDir(), DONE_MARKER));
            new File(context.getFilesDir(), IN_PROGRESS_MARKER).delete();
            meter.step(ids.size(), "Done");
            return new Result(imported, skipped, null);
        } catch (CancelledException e) {
            deleteTree(stagingRoot);
            return new Result(imported, skipped, true, null);
        } catch (IOException e) {
            Log.e(TAG, "Import failed", e);
            deleteTree(stagingRoot);
            return new Result(imported, skipped, "Import failed: " + e.getMessage());
        }
    }

    /**
     * Shared top-level folders: everything except the project list/build folder, temporary
     * folders and the backups folder (.swb backups can be large and stay usable from
     * Sketchware Pro's folder via Restore projects).
     */
    private static boolean isSharedTopLevel(File entry) {
        String name = entry.getName();
        return entry.isDirectory() && !SKIPPED_TOP_LEVEL.contains(name) && !name.equals("mysc")
                && !name.equals("backups") && !name.startsWith(".");
    }

    private static String describe(String topLevelName) {
        switch (topLevelName) {
            case "resources":
                return "shared resources";
            case "libs":
                return "libraries";
            case "bak":
                return "project history";
            case "data":
                return "custom blocks, components and settings";
            default:
                return topLevelName;
        }
    }

    /** The project's app name from mysc/list/&lt;id&gt;/project, falling back to its id. */
    private static String projectName(File listEntry, String id) {
        try {
            File project = new File(listEntry, "project");
            if (project.isFile() && project.length() < 256 * 1024) {
                String text = decryptProjectFile(project);
                if (text != null) {
                    org.json.JSONObject json = new org.json.JSONObject(text);
                    String appName = json.optString("my_ws_name", "");
                    if (appName.isEmpty()) appName = json.optString("my_app_name", "");
                    if (!appName.isEmpty()) return appName;
                }
            }
        } catch (Throwable ignored) {
            // Name is cosmetic; the id is always good enough.
        }
        return "Project " + id;
    }

    /** mysc/list/&lt;id&gt;/project is AES-encrypted JSON (Sketchware's fixed key); plain JSON also works. */
    private static String decryptProjectFile(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        if (data.length > 0 && data[0] == '{') return new String(data, StandardCharsets.UTF_8);
        byte[] key = "sketchwaresecure".getBytes(StandardCharsets.UTF_8);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
                new javax.crypto.spec.IvParameterSpec(key));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static long sizeOf(File file, Set<String> excluded) {
        if (excluded != null && excluded.contains(file.getAbsolutePath())) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child, excluded);
        }
        return total;
    }

    private static long availableBytes(File path) {
        try {
            return new StatFs(path.getAbsolutePath()).getAvailableBytes();
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private static long megabytes(long bytes) {
        return (bytes + (1 << 20) - 1) >> 20;
    }

    /**
     * Copies a directory tree, leaving files that already exist at the destination untouched.
     * Paths in {@code excluded} (absolute) are skipped. A missing source is not an error.
     */
    static void copyTreeWithoutOverwriting(File source, File destination, Set<String> excluded,
                                           Meter meter) throws IOException {
        if (excluded != null && excluded.contains(source.getAbsolutePath())) return;
        if (source.isDirectory()) {
            if (!destination.isDirectory() && !destination.mkdirs()) {
                throw new IOException("Cannot create " + destination);
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read " + source);
            }
            for (File child : children) {
                copyTreeWithoutOverwriting(child, new File(destination, child.getName()), excluded, meter);
            }
        } else if (source.isFile()) {
            if (destination.exists()) {
                if (meter != null) meter.add(source.length());
            } else {
                copyFileAtomically(source, destination, meter);
            }
        }
    }

    private static void copyFileAtomically(File source, File destination, Meter meter) throws IOException {
        File temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (meter != null) meter.add(read);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        if (!temp.renameTo(destination)) {
            temp.delete();
            throw new IOException("Cannot write " + destination);
        }
        destination.setLastModified(source.lastModified());
    }

    /**
     * Rewrites absolute paths such as /storage/emulated/0/.sketchware/libs/... inside the
     * project's plain-text settings so they point at Sketchware AI's folder.
     */
    private static void rewriteLegacyPaths(File projectDataDir) throws IOException {
        String from = File.separator + LEGACY_ROOT_NAME + File.separator;
        String to = File.separator + ROOT_NAME + File.separator;
        for (String name : PATH_BEARING_FILES) {
            File file = new File(projectDataDir, name);
            if (!file.isFile() || file.length() > 4L * 1024 * 1024) continue;
            String text = readUtf8(file);
            if (text == null || !text.contains(from)) continue;
            writeUtf8Atomically(file, text.replace(from, to));
        }
    }

    /** Records where a project came from, for later schema migrations. */
    private static void writeImportMarker(File projectDataDir) throws IOException {
        File dir = new File(projectDataDir, "sketchware_ai");
        File meta = new File(dir, "meta.json");
        if (meta.exists()) return;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        writeUtf8Atomically(meta, "{\"schemaVersion\":1,\"importedFrom\":\"sketchware_pro\","
                + "\"importedAt\":" + System.currentTimeMillis() + ",\"edgeToEdgeInsets\":false}");
    }

    private static String readUtf8(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
            String text = new String(data, 0, offset, StandardCharsets.UTF_8);
            // Encrypted or binary files would not survive a round trip through a String.
            return text.indexOf('�') >= 0 ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeUtf8Atomically(File file, String text) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        backup.delete();
        if (file.exists() && !file.renameTo(backup)) {
            temp.delete();
            throw new IOException("Cannot replace " + file);
        }
        if (!temp.renameTo(file)) {
            backup.renameTo(file);
            throw new IOException("Cannot replace " + file);
        }
        backup.delete();
    }

    private static int countDirectories(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) count++;
        }
        return count;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static void touch(File file) {
        try (OutputStream ignored = new FileOutputStream(file, true)) {
            // Creating the file is all that is needed.
        } catch (IOException e) {
            Log.w(TAG, "Could not create " + file, e);
        }
    }

}
