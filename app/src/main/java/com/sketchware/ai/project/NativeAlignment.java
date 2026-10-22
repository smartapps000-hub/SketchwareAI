package com.sketchware.ai.project;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 16 KB page size check for the native libraries a build packages (v1.8.4).
 * <p>
 * Devices with 16 KB memory pages (Android 15+) need a 64-bit {@code .so} whose LOAD segments are aligned to 16 KB,
 * and Google Play requires that from apps targeting Android 15+ (since 1 Nov 2025). A library's alignment is
 * fixed when it is linked, so the build can't repair it; it says which file needs a newer build of that library.
 * The check only reads ELF headers and never stops a build.
 */
public final class NativeAlignment {

    private static final int PT_LOAD = 1;
    private static final long REQUIRED = 16384;

    private NativeAlignment() {
    }

    /** Adds a build warning for every 64-bit library under {@code <dir>/<abi>/*.so} that is not 16 KB-aligned. */
    public static void check(File nativeLibrariesDirectory, String origin) {
        if (nativeLibrariesDirectory == null) return;
        for (String abi : new String[]{"arm64-v8a", "x86_64"}) {
            File[] files = new File(nativeLibrariesDirectory, abi).listFiles();
            if (files == null) continue;
            for (File so : files) {
                if (!so.isFile() || !so.getName().endsWith(".so")) continue;
                long alignment;
                try {
                    alignment = minLoadAlignment(so);
                } catch (IOException | RuntimeException e) {
                    continue; // not an ELF file Android would load either: other steps report that
                }
                if (alignment > 0 && alignment < REQUIRED) {
                    BuildWarnings.add(origin + ": " + abi + "/" + so.getName() + " is aligned to " + (alignment / 1024)
                            + " KB pages. Devices with 16 KB pages (Android 15+) may not load it, and Google Play "
                            + "requires 16 KB alignment for apps targeting Android 15+. Use a version of that library "
                            + "built with 16 KB alignment (NDK r28+, or -Wl,-z,max-page-size=16384).");
                }
            }
        }
    }

    /**
     * Smallest p_align of the PT_LOAD segments of a 64-bit little-endian ELF file; 0 when it isn't one
     * (32-bit libraries are not affected by 16 KB pages).
     */
    public static long minLoadAlignment(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] ident = new byte[16];
            in.readFully(ident);
            if (ident[0] != 0x7f || ident[1] != 'E' || ident[2] != 'L' || ident[3] != 'F') return 0;
            if (ident[4] != 2 || ident[5] != 1) return 0; // ELFCLASS64, little-endian
            in.seek(0x20);
            long phoff = readLong(in);
            in.seek(0x36);
            int phentsize = readShort(in);
            int phnum = readShort(in);
            if (phentsize < 56 || phnum <= 0 || phnum > 4096) return 0;
            long min = Long.MAX_VALUE;
            for (int i = 0; i < phnum; i++) {
                long entry = phoff + (long) i * phentsize;
                in.seek(entry);
                int type = readInt(in);
                if (type != PT_LOAD) continue;
                in.seek(entry + 48);
                long align = readLong(in);
                if (align < min) min = align;
            }
            return min == Long.MAX_VALUE ? 0 : min;
        }
    }

    private static int readShort(RandomAccessFile in) throws IOException {
        int b0 = in.read(), b1 = in.read();
        if ((b0 | b1) < 0) throw new IOException("truncated");
        return b0 | (b1 << 8);
    }

    private static int readInt(RandomAccessFile in) throws IOException {
        return readShort(in) | (readShort(in) << 16);
    }

    private static long readLong(RandomAccessFile in) throws IOException {
        return (readInt(in) & 0xffffffffL) | ((long) readInt(in) << 32);
    }
}
