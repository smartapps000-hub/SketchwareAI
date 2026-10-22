package com.sketchware.ai.project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Makes library class files readable for the D8/R8 3.3.75 the IDE ships.
 * <p>
 * javac 21 writes a {@code MethodParameters} attribute for the constructors of inner and anonymous classes in which
 * the mandated outer-instance parameter has no name (name_index 0, allowed by JVMS 4.7.24). D8 3.3.75 stops with
 * {@code NullPointerException: Cannot invoke "String.length()"} on such a class, whether it dexes the library or
 * only reads it as a library/classpath input. AndroidX released since 2024 (appcompat 1.7, fragment 1.8, material
 * 1.11+, concurrent-futures 1.2+ ...) is built that way.
 * <p>
 * The fix removes exactly those {@code MethodParameters} attributes that hold a nameless entry. The attribute only
 * feeds {@code java.lang.reflect.Parameter} names; Android apps never see it (D8 drops it unless told to keep it),
 * and nothing else in the class file moves: constant pool, fields, code and every other attribute are copied byte
 * for byte, so a class without such an attribute comes back as the same array.
 */
public final class ClassFileSanitizer {

    private static final int MAGIC = 0xCAFEBABE;

    private ClassFileSanitizer() {
    }

    /** Result of {@link #sanitizeJar}: classes read and classes changed. */
    public static final class Result {
        public int classes;
        public int changed;

        @Override
        public String toString() {
            return changed + " of " + classes + " classes changed";
        }
    }

    /** Jars already checked in this process: path -> size:time after the check. Nothing is written next to them. */
    private static final java.util.Map<String, String> CHECKED = new java.util.HashMap<>();

    /**
     * Rewrites {@code jar} in place (temp file + rename) when one of its classes needs the fix; a jar that needs none
     * is not written. A jar checked before in this process with the same size and time is not read again.
     */
    public static Result sanitizeJar(File jar) throws IOException {
        Result result = new Result();
        String stamp = jar.length() + ":" + jar.lastModified();
        synchronized (CHECKED) {
            if (stamp.equals(CHECKED.get(jar.getAbsolutePath()))) return result;
        }

        File temp = new File(jar.getPath() + ".d8tmp");
        boolean anyChanged = false;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temp))) {
            ZipEntry entry;
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            while ((entry = in.getNextEntry()) != null) {
                if (!seen.add(entry.getName())) continue; // a jar with a duplicate entry keeps the first, like D8 reads it
                byte[] data = readAll(in);
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    result.classes++;
                    byte[] fixed = strip(data);
                    if (fixed != data) {
                        result.changed++;
                        anyChanged = true;
                        data = fixed;
                    }
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                if (entry.getTime() != -1) copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            temp.delete();
            throw e;
        }
        if (anyChanged) {
            if (!temp.renameTo(jar)) {
                // rename over an existing file can fail on some file systems: replace explicitly
                File old = new File(jar.getPath() + ".d8old");
                old.delete();
                if (!jar.renameTo(old) || !temp.renameTo(jar)) {
                    old.renameTo(jar);
                    temp.delete();
                    throw new IOException("Couldn't replace " + jar);
                }
                old.delete();
            }
        } else {
            temp.delete();
        }
        synchronized (CHECKED) {
            CHECKED.put(jar.getAbsolutePath(), jar.length() + ":" + jar.lastModified());
        }
        return result;
    }

    /**
     * Returns {@code classFile} itself when nothing needs fixing, otherwise a copy without the MethodParameters
     * attributes that have a nameless parameter. Input that isn't a well-formed class file is returned unchanged.
     */
    public static byte[] strip(byte[] classFile) {
        try {
            return stripOrThrow(classFile);
        } catch (RuntimeException e) {
            return classFile;
        }
    }

    private static byte[] stripOrThrow(byte[] b) {
        Reader r = new Reader(b);
        if (r.u4() != MAGIC) return b;
        r.skip(4); // minor, major
        int cpCount = r.u2();
        String[] utf8 = new String[cpCount];
        for (int i = 1; i < cpCount; i++) {
            int tag = r.u1();
            switch (tag) {
                case 1: { // Utf8
                    int len = r.u2();
                    if (len == 16) utf8[i] = new String(b, r.pos, len, java.nio.charset.StandardCharsets.ISO_8859_1);
                    r.skip(len);
                    break;
                }
                case 3: case 4: // Integer, Float
                case 9: case 10: case 11: case 12: // Field/Method/InterfaceMethod ref, NameAndType
                case 17: case 18: // Dynamic, InvokeDynamic
                    r.skip(4);
                    break;
                case 5: case 6: // Long, Double take two slots
                    r.skip(8);
                    i++;
                    break;
                case 7: case 8: case 16: case 19: case 20: // Class, String, MethodType, Module, Package
                    r.skip(2);
                    break;
                case 15: // MethodHandle
                    r.skip(3);
                    break;
                default:
                    throw new IllegalArgumentException("constant pool tag " + tag);
            }
        }
        r.skip(6); // access, this, super
        r.skip(2 * r.u2()); // interfaces
        int fields = r.u2();
        for (int f = 0; f < fields; f++) {
            r.skip(6);
            skipAttributes(r);
        }
        int methods = r.u2();
        // [start, end) byte ranges of attributes to drop, with the offset of their method's attribute count
        int[] dropStart = new int[8], dropEnd = new int[8], countPos = new int[8];
        int drops = 0;
        for (int m = 0; m < methods; m++) {
            r.skip(6);
            int countAt = r.pos;
            int attrs = r.u2();
            for (int a = 0; a < attrs; a++) {
                int start = r.pos;
                int nameIndex = r.u2();
                int len = r.u4();
                int body = r.pos;
                boolean drop = false;
                if (nameIndex > 0 && nameIndex < cpCount && "MethodParameters".equals(utf8[nameIndex])) {
                    int params = b[body] & 0xff;
                    for (int p = 0; p < params; p++) {
                        int nameIdx = ((b[body + 1 + 4 * p] & 0xff) << 8) | (b[body + 2 + 4 * p] & 0xff);
                        if (nameIdx == 0) {
                            drop = true;
                            break;
                        }
                    }
                }
                r.skip(len);
                if (drop) {
                    if (drops == dropStart.length) {
                        dropStart = java.util.Arrays.copyOf(dropStart, drops * 2);
                        dropEnd = java.util.Arrays.copyOf(dropEnd, drops * 2);
                        countPos = java.util.Arrays.copyOf(countPos, drops * 2);
                    }
                    dropStart[drops] = start;
                    dropEnd[drops] = r.pos;
                    countPos[drops] = countAt;
                    drops++;
                }
            }
        }
        skipAttributes(r); // class attributes: also checks the file is complete
        if (r.pos != b.length) throw new IllegalArgumentException("trailing bytes");
        if (drops == 0) return b;

        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        int copied = 0;
        for (int d = 0; d < drops; d++) {
            // copy up to the method's attribute count, write it one lower, copy on to the attribute, skip it
            if (copied <= countPos[d]) {
                out.write(b, copied, countPos[d] - copied);
                int count = ((b[countPos[d]] & 0xff) << 8) | (b[countPos[d] + 1] & 0xff);
                int sameMethod = 0;
                for (int e = d; e < drops && countPos[e] == countPos[d]; e++) sameMethod++;
                count -= sameMethod;
                out.write(count >> 8);
                out.write(count);
                copied = countPos[d] + 2;
            }
            out.write(b, copied, dropStart[d] - copied);
            copied = dropEnd[d];
        }
        out.write(b, copied, b.length - copied);
        return out.toByteArray();
    }

    private static void skipAttributes(Reader r) {
        int attrs = r.u2();
        for (int a = 0; a < attrs; a++) {
            r.skip(2);
            r.skip(r.u4());
        }
    }

    private static final class Reader {
        final byte[] b;
        int pos;

        Reader(byte[] b) {
            this.b = b;
        }

        int u1() {
            if (pos >= b.length) throw new IllegalArgumentException("truncated");
            return b[pos++] & 0xff;
        }

        int u2() {
            return (u1() << 8) | u1();
        }

        int u4() {
            return (u2() << 16) | u2();
        }

        void skip(int n) {
            if (n < 0 || pos + n > b.length) throw new IllegalArgumentException("truncated");
            pos += n;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** Tool entry (tools/builtin-libs): {@code ClassFileSanitizer <jar>...} rewrites each jar that needs it. */
    public static void main(String[] args) throws IOException {
        for (String path : args) {
            System.out.println(path + ": " + sanitizeJar(new File(path)));
        }
    }
}
