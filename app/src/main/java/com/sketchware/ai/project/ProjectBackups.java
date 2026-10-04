package com.sketchware.ai.project;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mod.hey.studios.util.Helper;

/**
 * The project's own backups (.swb files in the backup folder) as a place to get a lost file
 * back from: an image, or a local library's folder. A backup counts as the project's own only
 * when its project file names the same package and the same project name; a file taken from
 * it is then the very file the project had when the backup was made, not a look-alike.
 * Nothing here writes to a backup.
 */
public final class ProjectBackups {

    /** How many archives are opened to look for the project's own; newest first. */
    private static final int MAX_ARCHIVES = 60;

    private ProjectBackups() {
    }

    /** One file found in a backup. */
    public static final class Found {
        public File archive;
        public String entry;
    }

    /**
     * @param backupRoot the backup folder (it holds one folder per project name)
     * @param packageName the project's package, e.g. com.my.app
     * @param projectName the project's name (my_ws_name)
     * @return the project's own backups, newest first
     */
    public static List<File> archives(File backupRoot, String packageName, String projectName) {
        List<File> matching = new ArrayList<>();
        if (backupRoot == null || packageName == null || packageName.isEmpty()) return matching;
        // A project that still has the default package and name is not told apart from another such
        // project by them, so its backups can't be proven to be its own.
        if (projectName == null || projectName.isEmpty() || packageName.matches("com\\.my\\.newproject\\d*")) return matching;
        List<File> all = new ArrayList<>();
        collect(backupRoot, all, 0);
        File[] sorted = all.toArray(new File[0]);
        final String own = File.separator + projectName + File.separator;
        // The project's own backup folder first, then newest first.
        Arrays.sort(sorted, (a, b) -> {
            boolean ownA = a.getPath().contains(own), ownB = b.getPath().contains(own);
            if (ownA != ownB) return ownA ? -1 : 1;
            return Long.compare(b.lastModified(), a.lastModified());
        });
        int opened = 0;
        for (File archive : sorted) {
            if (opened++ >= MAX_ARCHIVES) break;
            try (ZipFile zip = new ZipFile(archive)) {
                ZipEntry project = zip.getEntry("project");
                if (project == null) continue;
                HashMap<String, Object> meta = decrypt(read(zip.getInputStream(project)));
                if (meta == null) continue;
                if (!packageName.equals(String.valueOf(meta.get("my_sc_pkg_name")))) continue;
                if (!projectName.equals(String.valueOf(meta.get("my_ws_name")))) continue;
                matching.add(archive);
            } catch (Exception e) {
                // Not a readable backup.
            }
        }
        return matching;
    }

    private static void collect(File directory, List<File> out, int depth) {
        File[] children = directory.listFiles();
        if (children == null || depth > 2) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".swb")) out.add(child);
        }
    }

    /**
     * Looks in the given backups (newest first) for a file directly inside {@code folder} whose
     * name without extension is {@code baseName}, e.g. ("resources/images", "logo").
     */
    public static Found findFile(List<File> archives, String folder, String baseName) {
        String prefix = folder.endsWith("/") ? folder : folder + "/";
        for (File archive : archives) {
            try (ZipFile zip = new ZipFile(archive)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(prefix)) continue;
                    String file = name.substring(prefix.length());
                    if (file.indexOf('/') >= 0) continue;
                    if (ResourceResolver.baseName(file).equals(baseName) && entry.getSize() != 0) {
                        Found found = new Found();
                        found.archive = archive;
                        found.entry = name;
                        return found;
                    }
                }
            } catch (Exception e) {
                // Try the next backup.
            }
        }
        return null;
    }

    /** @return the newest of the given backups that holds the folder with a classes.jar in it, or null */
    public static File findLibrary(List<File> archives, String libraryName) {
        for (File archive : archives) {
            try (ZipFile zip = new ZipFile(archive)) {
                ZipEntry jar = zip.getEntry("local_libs/" + libraryName + "/classes.jar");
                if (jar != null && jar.getSize() != 0) return archive;
            } catch (Exception e) {
                // Try the next backup.
            }
        }
        return null;
    }

    /** Writes one entry of a backup to {@code target} (through a temporary file). */
    public static boolean extract(File archive, String entryName, File target) {
        File temporary = new File(target.getPath() + ".part");
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return false;
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            if (target.exists() && !target.delete()) return false;
            return temporary.renameTo(target);
        } catch (Exception e) {
            return false;
        } finally {
            temporary.delete();
        }
    }

    /**
     * Copies everything under {@code prefix} in the backup into {@code directory}, never over a
     * file that is already there.
     *
     * @return how many files were written
     */
    public static int extractTree(File archive, String prefix, File directory) {
        int written = 0;
        String root;
        try {
            root = directory.getCanonicalPath() + File.separator;
        } catch (java.io.IOException e) {
            return 0;
        }
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) names.add(entry.getName());
            }
        } catch (Exception e) {
            return 0;
        }
        for (String name : names) {
            File target = new File(directory, name.substring(prefix.length()));
            try {
                if (!target.getCanonicalPath().startsWith(root)) continue; // never outside the folder
            } catch (java.io.IOException e) {
                continue;
            }
            if (target.exists()) continue;
            if (extract(archive, name, target)) written++;
        }
        return written;
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = stream.read(buffer)) > 0) bytes.write(buffer, 0, n);
            return bytes.toByteArray();
        }
    }

    /** A project file's content (the same fixed key every Sketchware project file uses). */
    private static HashMap<String, Object> decrypt(byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes();
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            String text = new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8).trim();
            return new Gson().fromJson(text, Helper.TYPE_MAP);
        } catch (Exception e) {
            return null;
        }
    }
}
