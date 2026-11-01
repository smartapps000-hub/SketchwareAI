package com.sketchware.ai.project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * v1.9.0 build cache for AAPT2's compile step of a Run build (aapt2 compile output is deterministic: the same input
 * file gives the same .flat bytes, archives carry fixed 1980 timestamps).
 * <ul>
 * <li><b>Local libraries</b>: a library's res folder was compiled at every build. Its archive is kept, keyed by the
 * folder's path and every file's relative path, size and modification time; the PNG notes of that compile are kept
 * with it and reported again.</li>
 * <li><b>Project resources</b> (generated into mysc at every build, so times change): one .flat per file, keyed by
 * the file's relative path and content. Files whose .flat is kept aren't compiled again; changed files are compiled
 * one by one (many: the folder as before); the project archive is written from the .flat files.</li>
 * </ul>
 * Every key includes the aapt2 binary (path, size, time). The project's cache holds only the last build's files.
 */
public final class ResourceCache {

    private static final String VERSION = "res-1";

    private final File root;
    private final String aapt2;

    /** @param cacheRoot the app's cache folder */
    public ResourceCache(File cacheRoot, String scId, File aapt2Binary) {
        this.root = new File(new File(new File(cacheRoot, "build-cache"), "res"), scId);
        this.aapt2 = aapt2Binary.getAbsolutePath() + "|" + aapt2Binary.length() + "|" + aapt2Binary.lastModified();
    }

    // -------------------------------------------------------------------------------------------- local libraries

    /** The kept archive for a local library's res folder (it may not exist yet). */
    public File libraryArchive(File resDir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        update(digest, VERSION + "|" + aapt2 + "|" + resDir.getAbsolutePath());
        List<File> files = new ArrayList<>();
        listAll(resDir, files);
        String base = resDir.getAbsolutePath();
        for (File file : files) {
            update(digest, "|" + file.getAbsolutePath().substring(base.length()) + "|" + file.length() + "|" + file.lastModified());
        }
        String pathKey = RClassCache.hex(sha1(resDir.getAbsolutePath()));
        return new File(new File(root, "lib"), pathKey + "-" + RClassCache.hex(digest.digest()) + ".zip");
    }

    /** The PNG notes the kept compile of this archive gave. */
    public List<String> libraryNotes(File archive) {
        List<String> notes = new ArrayList<>();
        File file = new File(archive.getPath() + ".notes");
        if (!file.isFile()) return notes;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) if (!line.isEmpty()) notes.add(line);
        } catch (IOException ignored) {
            // no notes
        }
        return notes;
    }

    /** Keeps a library's freshly compiled archive (and its notes); older archives of that folder are deleted. */
    public void keepLibrary(File archive, File compiled, List<String> notes) {
        File dir = archive.getParentFile();
        File temp = new File(dir, "." + archive.getName() + "-" + System.nanoTime());
        File tempNotes = new File(dir, "." + archive.getName() + ".notes-" + System.nanoTime());
        try {
            RClassCache.copy(compiled, temp);
            try (Writer out = new OutputStreamWriter(new FileOutputStream(tempNotes), StandardCharsets.UTF_8)) {
                for (String note : notes) out.write(note.replace('\n', ' ') + "\n");
            }
            String prefix = archive.getName().substring(0, archive.getName().indexOf('-') + 1);
            File[] older = dir.listFiles((d, name) -> name.startsWith(prefix));
            if (older != null) for (File file : older) RClassCache.delete(file);
            if (!tempNotes.renameTo(new File(archive.getPath() + ".notes"))) return;
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(archive);
        } catch (IOException ignored) {
            // not kept: the next build compiles the library again
        } finally {
            RClassCache.delete(temp);
            RClassCache.delete(tempNotes);
        }
    }

    // -------------------------------------------------------------------------------------------- project resources

    /** A project resource file and what aapt2 makes of it. */
    public static final class Item {
        public final File file;
        /** The entry aapt2 writes for it: values/strings.xml → values_strings.arsc.flat, layout/main.xml → layout_main.xml.flat */
        public final String flatName;
        final String key;

        Item(File file, String flatName, String key) {
            this.file = file;
            this.flatName = flatName;
            this.key = key;
        }
    }

    /**
     * The files of a res folder (res/&lt;type&gt;/&lt;file&gt;), each with its .flat name and key; null when the folder
     * has anything this cache doesn't name the way aapt2 does (then it is compiled as before).
     */
    public List<Item> projectItems(File resDir) throws Exception {
        File[] types = resDir.listFiles();
        if (types == null) return null;
        List<Item> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        String base = resDir.getAbsolutePath();
        byte[] buffer = new byte[64 * 1024];
        for (File type : sorted(types)) {
            if (!type.isDirectory() || type.getName().startsWith(".")) return null;
            File[] files = type.listFiles();
            if (files == null) continue;
            for (File file : sorted(files)) {
                String name = file.getName();
                if (!file.isFile() || name.startsWith(".")) return null;
                String flat;
                if (type.getName().equals("values") || type.getName().startsWith("values-")) {
                    if (!name.endsWith(".xml")) return null;
                    flat = type.getName() + "_" + name.substring(0, name.length() - 4) + ".arsc.flat";
                } else {
                    flat = type.getName() + "_" + name + ".flat";
                }
                if (!names.add(flat)) return null;
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                update(digest, VERSION + "|" + aapt2 + "|" + file.getAbsolutePath().substring(base.length()) + "|" + file.length() + "|");
                try (InputStream in = new FileInputStream(file)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
                }
                items.add(new Item(file, flat, RClassCache.hex(digest.digest())));
            }
        }
        return items;
    }

    /** The kept .flat of an item (it may not exist). */
    public File flat(Item item) {
        return new File(new File(root, "flat"), item.key + ".flat");
    }

    /** Keeps {@code compiled} as the item's .flat. */
    public void keepFlat(Item item, File compiled) {
        File target = flat(item);
        File temp = new File(target.getParentFile(), "." + target.getName() + "-" + System.nanoTime());
        try {
            RClassCache.copy(compiled, temp);
            if (!temp.renameTo(target)) RClassCache.delete(temp);
        } catch (IOException e) {
            RClassCache.delete(temp);
        }
    }

    /** Keeps every item's .flat from an archive aapt2 compiled from the whole folder; false when one is missing. */
    public boolean keepFromArchive(List<Item> items, File archive) {
        File dir = new File(root, "flat");
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() != items.size()) return false;
            for (Item item : items) {
                ZipEntry entry = zip.getEntry(item.flatName);
                if (entry == null) return false;
                File target = flat(item);
                if (target.isFile()) continue;
                File temp = new File(dir, "." + target.getName() + "-" + System.nanoTime());
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                if (!temp.renameTo(target)) RClassCache.delete(temp);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Writes the project archive from the kept .flat files (stored entries, sorted by name like aapt2's). */
    public void writeArchive(List<Item> items, File archive) throws IOException {
        Map<String, File> entries = new TreeMap<>();
        for (Item item : items) {
            File flat = flat(item);
            if (!flat.isFile()) throw new IOException("not kept: " + item.flatName);
            entries.put(item.flatName, flat);
        }
        File temp = new File(archive.getPath() + ".tmp-" + System.nanoTime());
        try {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[64 * 1024];
                for (Map.Entry<String, File> e : entries.entrySet()) {
                    byte[] bytes = readAll(e.getValue(), buffer);
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    ZipEntry entry = new ZipEntry(e.getKey());
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(bytes.length);
                    entry.setCompressedSize(bytes.length);
                    entry.setCrc(crc.getValue());
                    entry.setTime(315532800000L);   // 1980-01-01, as aapt2 writes
                    out.putNextEntry(entry);
                    out.write(bytes);
                    out.closeEntry();
                }
            }
            if (archive.exists() && !archive.delete()) throw new IOException("can't replace " + archive);
            if (!temp.renameTo(archive)) throw new IOException("can't write " + archive);
        } finally {
            RClassCache.delete(temp);
        }
    }

    /** Deletes kept .flat files this build didn't use (the cache holds one build's files per project). */
    public void pruneFlats(List<Item> items) {
        Set<String> keep = new HashSet<>();
        for (Item item : items) keep.add(item.key + ".flat");
        File[] files = new File(root, "flat").listFiles();
        if (files == null) return;
        for (File file : files) if (!keep.contains(file.getName())) RClassCache.delete(file);
    }

    // -------------------------------------------------------------------------------------------- helpers

    private static byte[] readAll(File file, byte[] buffer) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) Math.max(0, file.length()));
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static List<File> sorted(File[] files) {
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    private static void listAll(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : sorted(files)) {
            if (file.isDirectory()) listAll(file, out);
            else out.add(file);
        }
    }

    private static byte[] sha1(String text) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }
}
