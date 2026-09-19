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

    /** Top-level folders of the legacy root that hold nothing worth importing. */
    private static final Set<String> SKIPPED_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "temp", "download", "tutorial", "upload"));

    /** Plain-text per-project settings files that may contain absolute legacy paths. */
    private static final String[] PATH_BEARING_FILES = {
            "local_library", "build_config", "project_config"};

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static final class Result {
        public final int importedProjects;
        public final int skippedProjects;
        public final String error;

        Result(int importedProjects, int skippedProjects, String error) {
            this.importedProjects = importedProjects;
            this.skippedProjects = skippedProjects;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
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
        return legacyProjectCount() > 0 && projectCount() == 0;
    }

    public static void markDeclined(Context context) {
        touch(new File(context.getFilesDir(), DECLINED_MARKER));
    }

    /** Clears the "declined" and "done" flags so the import can be offered or run again. */
    public static void resetOffer(Context context) {
        new File(context.getFilesDir(), DECLINED_MARKER).delete();
        new File(context.getFilesDir(), DONE_MARKER).delete();
    }

    /**
     * Runs the import. Blocking; call from a worker thread.
     *
     * @param listener receives short progress messages; may be null
     */
    public static Result run(Context context, ProgressListener listener) {
        File legacy = legacyRoot();
        File target = root();
        File legacyList = new File(legacy, "mysc" + File.separator + "list");
        File targetList = new File(target, "mysc" + File.separator + "list");

        if (!legacyList.isDirectory()) {
            return new Result(0, 0, "No Sketchware Pro projects were found in " + legacyList);
        }

        report(listener, "Measuring Sketchware Pro's files…");
        long needed = sizeOfImport(legacy);
        long available = availableBytes(Environment.getExternalStorageDirectory());
        if (available >= 0 && needed > available) {
            return new Result(0, 0, "Not enough free storage: the import needs "
                    + megabytes(needed) + " MB but only " + megabytes(available) + " MB is free.");
        }

        try {
            File[] topLevel = legacy.listFiles();
            if (topLevel == null) {
                return new Result(0, 0, "Cannot read " + legacy + ". Grant storage access and retry.");
            }
            for (File entry : topLevel) {
                String name = entry.getName();
                if (!entry.isDirectory() || SKIPPED_TOP_LEVEL.contains(name) || name.equals("mysc")) {
                    continue;
                }
                report(listener, "Copying " + name + "…");
                copyTreeWithoutOverwriting(entry, new File(target, name));
            }

            if (!targetList.isDirectory() && !targetList.mkdirs()) {
                return new Result(0, 0, "Cannot create " + targetList);
            }

            int imported = 0;
            int skipped = 0;
            File[] projects = legacyList.listFiles();
            if (projects != null) {
                for (File project : projects) {
                    if (!project.isDirectory()) continue;
                    String id = project.getName();
                    File destination = new File(targetList, id);
                    if (destination.exists()) {
                        skipped++;
                        continue;
                    }
                    report(listener, "Importing project " + id + "…");
                    // Staged next to mysc/list (same file system, so the rename below is atomic)
                    // so the project list never sees a half-copied project.
                    File staging = new File(target, "mysc" + File.separator + ".import-staging"
                            + File.separator + id);
                    deleteTree(staging);
                    copyTreeWithoutOverwriting(project, staging);
                    rewriteLegacyPaths(new File(target, "data" + File.separator + id));
                    writeImportMarker(new File(target, "data" + File.separator + id));
                    if (!staging.renameTo(destination)) {
                        deleteTree(staging);
                        throw new IOException("Could not move project " + id + " into place");
                    }
                    imported++;
                }
            }

            new File(target, "mysc" + File.separator + ".import-staging").delete();
            touch(new File(context.getFilesDir(), DONE_MARKER));
            return new Result(imported, skipped, null);
        } catch (IOException e) {
            Log.e(TAG, "Import failed", e);
            return new Result(0, 0, "Import failed: " + e.getMessage());
        }
    }

    /** Estimated bytes the import will write: everything that is copied and not yet present. */
    private static long sizeOfImport(File legacy) {
        long total = 0;
        File[] topLevel = legacy.listFiles();
        if (topLevel == null) return 0;
        for (File entry : topLevel) {
            String name = entry.getName();
            if (!entry.isDirectory() || SKIPPED_TOP_LEVEL.contains(name)) continue;
            if (name.equals("mysc")) {
                total += sizeOf(new File(entry, "list"));
            } else {
                total += sizeOf(entry);
            }
        }
        return total;
    }

    private static long sizeOf(File file) {
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
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

    /** Copies a directory tree, leaving files that already exist at the destination untouched. */
    static void copyTreeWithoutOverwriting(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.isDirectory() && !destination.mkdirs()) {
                throw new IOException("Cannot create " + destination);
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read " + source);
            }
            for (File child : children) {
                copyTreeWithoutOverwriting(child, new File(destination, child.getName()));
            }
        } else if (source.isFile() && !destination.exists()) {
            copyFileAtomically(source, destination);
        }
    }

    private static void copyFileAtomically(File source, File destination) throws IOException {
        File temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(temp, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
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

    private static void report(ProgressListener listener, String message) {
        if (listener != null) listener.onProgress(message);
    }
}
