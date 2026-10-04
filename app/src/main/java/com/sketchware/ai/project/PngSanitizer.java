package com.sketchware.ai.project;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Keeps a damaged PNG from stopping (or crashing) AAPT2. A cut-off or corrupt image is found
 * before the resources are compiled and replaced, in the build's copy only, by a valid PNG that
 * keeps every row that can still be read; the rest of the picture is left empty. The image the
 * user put in the project is never changed.
 */
public final class PngSanitizer {

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final int IHDR = 0x49484452;
    private static final int PLTE = 0x504C5445;
    private static final int TRNS = 0x74524E53;
    private static final int IDAT = 0x49444154;
    private static final int IEND = 0x49454E44;
    /** Decoded size above which a damaged image is not salvaged (memory on the phone). */
    private static final long MAX_RAW_BYTES = 96L * 1024 * 1024;

    private PngSanitizer() {
    }

    /**
     * A quick look at the file's structure: signature, chunk lengths, IHDR first, IEND present.
     * Reads only the chunk headers.
     *
     * @return null when the structure is sound, otherwise what is wrong, in plain words
     */
    public static String problem(File png) {
        try (RandomAccessFile file = new RandomAccessFile(png, "r")) {
            long size = file.length();
            byte[] head = new byte[8];
            if (size < 8 || file.read(head) != 8 || !java.util.Arrays.equals(head, SIGNATURE)) {
                return "it is not a PNG image (wrong file signature)";
            }
            long position = 8;
            boolean first = true;
            boolean data = false;
            while (true) {
                if (position == size) return "it is cut off: the end of the image (IEND) is missing";
                if (position + 8 > size) return "it is cut off in the middle of a chunk header at byte " + position;
                file.seek(position);
                long length = file.readInt() & 0xFFFFFFFFL;
                int type = file.readInt();
                if (first && (type != IHDR || length != 13)) return "its header (IHDR) is missing or damaged";
                first = false;
                if (length > Integer.MAX_VALUE || position + 12 + length > size) {
                    return "it is cut off: the " + name(type) + " chunk at byte " + position + " says " + length
                            + " bytes, but the file ends at byte " + size;
                }
                if (type == IDAT) data = true;
                if (type == IEND) return data ? null : "it has no image data";
                position += 12 + length;
            }
        } catch (IOException e) {
            return "it can't be read (" + e.getMessage() + ")";
        }
    }

    /**
     * The thorough check, used once AAPT2 has failed: every chunk's checksum, and that the
     * pixel data unpacks to the size the header promises.
     *
     * @return null when the image is sound, otherwise what is wrong
     */
    public static String deepProblem(File png) {
        String structure = problem(png);
        if (structure != null) return structure;
        try {
            byte[] file = readAll(png);
            int position = 8;
            int width = 0, height = 0, depth = 0, colorType = 0, interlace = 0;
            Inflater inflater = new Inflater();
            long unpacked = 0;
            byte[] sink = new byte[65536];
            try {
                while (position + 12 <= file.length) {
                    int length = readInt(file, position);
                    int type = readInt(file, position + 4);
                    if (!crcOk(file, position, length)) {
                        return "the " + name(type) + " chunk at byte " + position + " has a wrong checksum (damaged data)";
                    }
                    if (type == IHDR) {
                        width = readInt(file, position + 8);
                        height = readInt(file, position + 12);
                        depth = file[position + 16];
                        colorType = file[position + 17];
                        interlace = file[position + 20];
                    } else if (type == IDAT) {
                        inflater.setInput(file, position + 8, length);
                        while (!inflater.needsInput() && !inflater.finished()) {
                            int n = inflater.inflate(sink);
                            if (n == 0 && inflater.needsDictionary()) return "its pixel data is damaged";
                            unpacked += n;
                        }
                    } else if (type == IEND) {
                        break;
                    }
                    position += 12 + length;
                }
            } catch (DataFormatException e) {
                return "its pixel data is damaged (" + e.getMessage() + ")";
            } finally {
                inflater.end();
            }
            int channels = colorType == 2 ? 3 : colorType == 4 ? 2 : colorType == 6 ? 4 : 1;
            long expected = ((long) width * channels * depth + 7) / 8 + 1;
            expected *= height;
            if (width <= 0 || height <= 0) return "its header gives no size";
            if (interlace == 0 && unpacked != expected) {
                return "its pixel data unpacks to " + unpacked + " bytes instead of " + expected;
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return "it can't be read (" + e.getMessage() + ")";
        }
    }

    private static String name(int type) {
        StringBuilder name = new StringBuilder(4);
        for (int shift = 24; shift >= 0; shift -= 8) {
            int c = (type >> shift) & 0xFF;
            name.append(c >= 'A' && c <= 'z' ? (char) c : '?');
        }
        return name.toString();
    }

    /**
     * Checks every PNG below a res folder that belongs to the build (it is rewritten at each
     * build) and repairs the damaged ones in place.
     *
     * @return one note per repaired image, for the build's warnings
     */
    public static List<String> fixTree(File resDirectory) {
        List<String> notes = new ArrayList<>();
        for (File png : pngs(resDirectory)) {
            String problem = problem(png);
            if (problem != null) notes.add(repair(png, png, problem));
        }
        return notes;
    }

    /**
     * For a res folder that belongs to the user or to a library: if it holds a damaged PNG, a
     * copy of the folder with the image repaired is made in {@code mirror} and returned.
     *
     * @return {@code resDirectory} itself when all its PNGs are sound, otherwise the copy
     */
    public static File usable(File resDirectory, File mirror, List<String> notes) {
        List<File> damaged = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (File png : pngs(resDirectory)) {
            String problem = problem(png);
            if (problem != null) {
                damaged.add(png);
                problems.add(problem);
            }
        }
        if (damaged.isEmpty()) return resDirectory;
        return mirrorWith(resDirectory, mirror, damaged, problems, notes) ? mirror : resDirectory;
    }

    private static final Pattern AAPT_PNG_ERROR = Pattern.compile("(?m)^(/[^\\n:]+\\.png):(?:\\d+:)? error:");

    /**
     * After AAPT2 rejected PNGs that looked sound (bad checksum, bad compressed data): repairs
     * the ones its log names that lie in {@code compiled}.
     *
     * @param ownedByBuild true when the folder is the build's copy and may be changed in place
     * @return the folder to compile again, or null when the log names no PNG of this folder
     */
    public static File afterFailure(String aaptLog, File compiled, boolean ownedByBuild, File mirror, List<String> notes) {
        if (aaptLog == null) return null;
        List<File> named = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Matcher m = AAPT_PNG_ERROR.matcher(aaptLog);
        String root;
        try {
            root = compiled.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            return null;
        }
        while (m.find()) {
            File png = new File(m.group(1));
            try {
                if (!png.getCanonicalPath().startsWith(root) || !png.isFile() || named.contains(png)) continue;
            } catch (IOException e) {
                continue;
            }
            named.add(png);
            problems.add("AAPT2 can't read it");
        }
        // AAPT2 can crash on a damaged image without naming it: look at every PNG thoroughly.
        for (File png : pngs(compiled)) {
            if (named.contains(png)) continue;
            String problem = deepProblem(png);
            if (problem != null) {
                named.add(png);
                problems.add(problem);
            }
        }
        if (named.isEmpty()) return null;
        if (ownedByBuild) {
            for (int i = 0; i < named.size(); i++) notes.add(repair(named.get(i), named.get(i), problems.get(i)));
            return compiled;
        }
        return mirrorWith(compiled, mirror, named, problems, notes) ? mirror : null;
    }

    private static boolean mirrorWith(File source, File mirror, List<File> damaged, List<String> problems, List<String> notes) {
        try {
            deleteTree(mirror);
            copyTree(source, mirror);
            String root = source.getCanonicalPath();
            for (int i = 0; i < damaged.size(); i++) {
                String relative = damaged.get(i).getCanonicalPath().substring(root.length());
                notes.add(repair(damaged.get(i), new File(mirror, relative), problems.get(i)));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            notes.add("Damaged images in " + source + " couldn't be repaired for the build: " + e.getMessage());
            return false;
        }
    }

    /** Takes a repaired image over into the project itself, so the next build has nothing to repair. */
    public interface Keeper {
        /**
         * @param buildFile the damaged image in the build's res folder
         * @param repaired  the repaired PNG
         * @return where the damaged original was put aside, or null when the project's image wasn't replaced
         */
        String keep(File buildFile, byte[] repaired);
    }

    private static final ThreadLocal<Keeper> KEEPER = new ThreadLocal<>();

    /** Set while the project's own res folder is compiled; null otherwise. */
    public static void keeper(Keeper keeper) {
        if (keeper == null) KEEPER.remove();
        else KEEPER.set(keeper);
    }

    /** Writes a usable version of {@code source} to {@code target} and says what was done. */
    private static String repair(File source, File target, String problem) {
        String damaged = "Image " + source.getName() + " is damaged: " + problem + ". ";
        // A repair is listed with the build's other repairs; a failure to repair stays a warning.
        String what = "auto-fix: " + damaged;
        try {
            byte[] bytes = readAll(source);
            Salvaged salvaged = salvage(bytes);
            if (salvaged == null) salvaged = decodeWithAndroid(source);
            if (salvaged == null) {
                write(target, encode(1, 1, 8, 6, null, null, new byte[]{0, 0, 0, 0, 0}));
                return what + "Nothing of it could be read, so the build used an empty (transparent) image in its place. "
                        + "Temporary repair: redone at every build. Replace the image in Image Manager; your file was not changed.";
            }
            write(target, salvaged.png);
            String kept = null;
            Keeper keeper = KEEPER.get();
            if (keeper != null && source.equals(target)) {
                try {
                    kept = keeper.keep(source, salvaged.png);
                } catch (RuntimeException e) {
                    kept = null;
                }
            }
            String permanence = kept != null
                    ? "Permanent: the project's image is now this repaired copy, so the next build has nothing to repair; "
                    + "the damaged file is kept as " + kept + "."
                    : "Temporary repair: redone at every build; your file was not changed.";
            if (salvaged.guessed) {
                return what + "The picture is intact down to the damaged spot and can look wrong below it; replace it in "
                        + "Image Manager with a good copy. " + permanence;
            }
            if (salvaged.rows >= salvaged.height) {
                return what + "The whole picture could still be read and was written again as a sound file. " + permanence;
            }
            return what + "The first " + salvaged.rows + " of " + salvaged.height + " pixel rows could be read; the rest is "
                    + "empty. Replace it in Image Manager with a good copy. " + permanence;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return damaged + "It couldn't be repaired (" + e + ").";
        }
    }

    private static final class Salvaged {
        byte[] png;
        int rows;
        int height;
        /** Data with a wrong checksum was used: the rows below the damaged spot can be wrong. */
        boolean guessed;
    }

    /** @return the image rewritten as a valid PNG, or null when this decoder can't handle it */
    static Salvaged salvage(byte[] file) {
        if (file.length < 8) return null;
        for (int i = 0; i < 8; i++) if (file[i] != SIGNATURE[i]) return null;
        int position = 8;
        int width = 0, height = 0, depth = 0, colorType = 0;
        boolean header = false;
        boolean guessed = false;
        byte[] palette = null, transparency = null;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        while (position + 8 <= file.length) {
            long length = readInt(file, position) & 0xFFFFFFFFL;
            int type = readInt(file, position + 4);
            int available = (int) Math.min(length, file.length - position - 8);
            if (available < 0) break;
            boolean whole = position + 12 + length <= file.length;
            if (!header) {
                if (type != IHDR || !whole || length != 13) return null;
                width = readInt(file, position + 8);
                height = readInt(file, position + 12);
                depth = file[position + 16];
                colorType = file[position + 17];
                if (file[position + 18] != 0 || file[position + 19] != 0) return null;
                if (file[position + 20] != 0) return null; // interlaced: left to the platform decoder
                header = true;
            } else if (type == PLTE && whole && crcOk(file, position, (int) length)) {
                palette = java.util.Arrays.copyOfRange(file, position + 8, position + 8 + (int) length);
            } else if (type == TRNS && whole && crcOk(file, position, (int) length)) {
                transparency = java.util.Arrays.copyOfRange(file, position + 8, position + 8 + (int) length);
            } else if (type == IDAT) {
                // A chunk whose checksum is wrong holds damaged data: what came before it is kept.
                // If it is the first one, its data is still tried; the rows in front of the damage unpack.
                if (whole && !crcOk(file, position, (int) length)) {
                    if (compressed.size() == 0) {
                        compressed.write(file, position + 8, available);
                        guessed = true;
                    }
                    break;
                }
                compressed.write(file, position + 8, available);
            } else if (type == IEND) {
                break;
            }
            if (!whole) break;
            position += 12 + (int) length;
        }
        if (!header || width <= 0 || height <= 0 || width > 32767 || height > 32767) return null;
        int channels;
        switch (colorType) {
            case 0: channels = 1; break;
            case 2: channels = 3; break;
            case 3: channels = 1; break;
            case 4: channels = 2; break;
            case 6: channels = 4; break;
            default: return null;
        }
        if (depth != 1 && depth != 2 && depth != 4 && depth != 8 && depth != 16) return null;
        if (colorType == 3 && (palette == null || depth == 16)) return null;
        if ((colorType == 2 || colorType == 4 || colorType == 6) && depth < 8) return null;
        long bitsPerPixel = (long) channels * depth;
        long rowBytes = (width * bitsPerPixel + 7) / 8;
        long raw = (rowBytes + 1) * height;
        if (raw > MAX_RAW_BYTES) return null;
        int stride = (int) rowBytes + 1;
        int pixelBytes = (int) Math.max(1, bitsPerPixel / 8);

        byte[] data = new byte[(int) raw];
        int filled = 0;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed.toByteArray());
            // In small steps: when the data turns out damaged, what was unpacked before that step is kept.
            while (filled < data.length && !inflater.finished() && !inflater.needsInput()) {
                int n = inflater.inflate(data, filled, Math.min(4096, data.length - filled));
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                filled += n;
            }
        } catch (DataFormatException e) {
            // What was read before the damage is kept.
        } finally {
            inflater.end();
        }
        int rows = filled / stride;

        // Undo each row's filter so the rows can be stored unfiltered.
        byte[] out = new byte[data.length];
        int good = 0;
        for (int y = 0; y < rows; y++) {
            int row = y * stride;
            int filter = data[row] & 0xFF;
            if (filter > 4) break;
            for (int x = 1; x < stride; x++) {
                int a = x > pixelBytes ? out[row + x - pixelBytes] & 0xFF : 0;
                int b = y > 0 ? out[row - stride + x] & 0xFF : 0;
                int c = y > 0 && x > pixelBytes ? out[row - stride + x - pixelBytes] & 0xFF : 0;
                int value = data[row + x] & 0xFF;
                switch (filter) {
                    case 1: value += a; break;
                    case 2: value += b; break;
                    case 3: value += (a + b) / 2; break;
                    case 4: {
                        int p = a + b - c;
                        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                        value += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
                        break;
                    }
                    default:
                }
                out[row + x] = (byte) value;
            }
            out[row] = 0;
            good++;
        }
        if (good == 0) return null;
        // Rows that couldn't be read stay zero: transparent where the image has an alpha channel.
        for (int i = good * stride; i < out.length; i++) out[i] = 0;

        Salvaged salvaged = new Salvaged();
        salvaged.png = encode(width, height, depth, colorType, palette, transparency, out);
        salvaged.rows = good;
        salvaged.height = height;
        salvaged.guessed = guessed;
        return salvaged;
    }

    /** The platform's decoder, for what the reader above leaves out (interlaced images). */
    private static Salvaged decodeWithAndroid(File source) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(source.getAbsolutePath());
            if (bitmap == null) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)) return null;
            Salvaged salvaged = new Salvaged();
            salvaged.png = bytes.toByteArray();
            salvaged.height = bitmap.getHeight();
            salvaged.rows = salvaged.height;
            bitmap.recycle();
            return salvaged;
        } catch (Throwable t) {
            return null;
        }
    }

    /** @param rows height * (1 filter byte + row bytes), filter bytes 0 */
    static byte[] encode(int width, int height, int depth, int colorType, byte[] palette, byte[] transparency, byte[] rows) {
        ByteArrayOutputStream png = new ByteArrayOutputStream(rows.length / 4 + 128);
        png.write(SIGNATURE, 0, 8);
        byte[] header = new byte[13];
        writeInt(header, 0, width);
        writeInt(header, 4, height);
        header[8] = (byte) depth;
        header[9] = (byte) colorType;
        chunk(png, IHDR, header, header.length);
        if (palette != null) chunk(png, PLTE, palette, palette.length);
        if (transparency != null) chunk(png, TRNS, transparency, transparency.length);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(rows);
        deflater.finish();
        byte[] buffer = new byte[32768];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            if (n > 0) chunk(png, IDAT, buffer, n);
        }
        deflater.end();
        chunk(png, IEND, new byte[0], 0);
        return png.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, int type, byte[] data, int length) {
        byte[] head = new byte[8];
        writeInt(head, 0, length);
        writeInt(head, 4, type);
        out.write(head, 0, 8);
        out.write(data, 0, length);
        CRC32 crc = new CRC32();
        crc.update(head, 4, 4);
        crc.update(data, 0, length);
        byte[] sum = new byte[4];
        writeInt(sum, 0, (int) crc.getValue());
        out.write(sum, 0, 4);
    }

    private static boolean crcOk(byte[] file, int chunkStart, int length) {
        CRC32 crc = new CRC32();
        crc.update(file, chunkStart + 4, 4 + length);
        return (int) crc.getValue() == readInt(file, chunkStart + 8 + length);
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static void writeInt(byte[] b, int at, int value) {
        b[at] = (byte) (value >>> 24);
        b[at + 1] = (byte) (value >>> 16);
        b[at + 2] = (byte) (value >>> 8);
        b[at + 3] = (byte) value;
    }

    private static List<File> pngs(File directory) {
        List<File> found = new ArrayList<>();
        collect(directory, found, 0);
        return found;
    }

    private static void collect(File directory, List<File> found, int depth) {
        File[] children = directory.listFiles();
        if (children == null || depth > 4) return;
        for (File child : children) {
            String name = child.getName().toLowerCase(java.util.Locale.ROOT);
            if (child.isDirectory()) {
                // AAPT2 only reads the pixels of images in drawable and mipmap folders; res/raw and
                // the others are copied as they are and may hold anything under a .png name.
                if (depth > 0 || name.startsWith("drawable") || name.startsWith("mipmap")) {
                    collect(child, found, depth + 1);
                }
            } else if (depth > 0 && name.endsWith(".png")) {
                found.add(child);
            }
        }
    }

    private static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE - 16, Math.max(32, file.length())));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) > 0) bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
    }

    private static void write(File target, byte[] bytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temporary = new File(target.getPath() + ".fix");
        try (OutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
        }
        if (!temporary.renameTo(target)) {
            target.delete();
            if (!temporary.renameTo(target)) throw new IOException("can't replace " + target);
        }
    }

    private static void copyTree(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("can't create " + target);
            File[] children = source.listFiles();
            if (children == null) return;
            for (File child : children) copyTree(child, new File(target, child.getName()));
        } else {
            try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
