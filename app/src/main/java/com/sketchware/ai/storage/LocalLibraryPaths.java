package com.sketchware.ai.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

/**
 * Keeps a project's local_library list pointing at files that exist.
 * <p>
 * Each entry stores absolute paths (jarPath, dexPath, resPath...). Projects restored from a
 * Sketchware Pro backup, or copied from Pro, still say /.sketchware/libs/local_libs/..., while
 * Sketchware AI keeps its libraries in /.sketchware_ai/libs/local_libs/. The build then can't
 * find classes.jar and every import from that library fails.
 * <p>
 * For every entry this finds the library folder by name (Sketchware AI's folder first, then
 * Pro's; a library only found in Pro's folder is copied into Sketchware AI's) and rebuilds the
 * entry exactly like Local Library Manager does when you tick a library.
 */
public final class LocalLibraryPaths {

    private static final String[][] FILES = {
            {"resPath", "res"}, {"jarPath", "classes.jar"}, {"dexPath", "classes.dex"},
            {"manifestPath", "AndroidManifest.xml"}, {"pgRulesPath", "proguard.txt"}, {"assetsPath", "assets"}};

    private LocalLibraryPaths() {
    }

    /** @param storageRoot external storage root (/storage/emulated/0); @return true if anything changed */
    public static boolean repair(List<HashMap<String, Object>> libraries, File storageRoot) {
        if (libraries == null) return false;
        File aiLibs = new File(storageRoot, ".sketchware_ai/libs/local_libs");
        File proLibs = new File(storageRoot, ".sketchware/libs/local_libs");
        boolean changed = false;
        for (HashMap<String, Object> library : libraries) {
            Object nameObject = library.get("name");
            if (!(nameObject instanceof String) || ((String) nameObject).isEmpty()) continue;
            String name = (String) nameObject;
            File directory = locate(name, library, aiLibs, proLibs);
            if (directory == null) continue;
            for (String[] file : FILES) {
                File candidate = new File(directory, file[1]);
                String value = candidate.exists() ? candidate.getAbsolutePath() : null;
                Object old = library.get(file[0]);
                if (value == null) {
                    if (old != null && !new File(old.toString()).exists()) {
                        library.remove(file[0]);
                        changed = true;
                    }
                } else if (!value.equals(old)) {
                    library.put(file[0], value);
                    changed = true;
                }
            }
            File config = new File(directory, "config");
            if (!library.containsKey("packageName") && config.isFile()) {
                String packageName = read(config).trim();
                if (!packageName.isEmpty()) {
                    library.put("packageName", packageName);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean allRecordedPathsExist(HashMap<String, Object> library) {
        for (String[] file : FILES) {
            Object path = library.get(file[0]);
            if (path != null && !new File(path.toString()).exists()) return false;
        }
        return true;
    }

    /** The library's folder, preferring one that has classes.jar; copies a Pro-only one over. */
    private static File locate(String name, HashMap<String, Object> library, File aiLibs, File proLibs) {
        File ai = new File(aiLibs, name);
        File pro = new File(proLibs, name);
        File recorded = null;
        Object jarPath = library.get("jarPath");
        if (jarPath != null) recorded = new File(jarPath.toString()).getParentFile();

        if (new File(ai, "classes.jar").isFile()) return ai;
        if (recorded != null && new File(recorded, "classes.jar").isFile()) {
            if (recorded.equals(pro) && copyTree(pro, ai)) return ai;
            return recorded;
        }
        if (new File(pro, "classes.jar").isFile()) {
            return copyTree(pro, ai) ? ai : pro;
        }
        if (ai.isDirectory()) return ai;
        if (pro.isDirectory()) return pro;
        return null;
    }

    private static boolean copyTree(File from, File to) {
        try {
            copyRecursive(from, to);
            return new File(to, "classes.jar").isFile();
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyRecursive(File from, File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);
            String[] children = from.list();
            if (children == null) return;
            for (String child : children) copyRecursive(new File(from, child), new File(to, child));
        } else if (!to.exists() || to.length() != from.length()) {
            File temp = new File(to.getPath() + ".tmp");
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            if (!temp.renameTo(to)) {
                temp.delete();
                throw new IOException("Cannot write " + to);
            }
        }
    }

    private static String read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 4096)];
            int n = in.read(data);
            return n > 0 ? new String(data, 0, n, "UTF-8") : "";
        } catch (IOException e) {
            return "";
        }
    }
}
