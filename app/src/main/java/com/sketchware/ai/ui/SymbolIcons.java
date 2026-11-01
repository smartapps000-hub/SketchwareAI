package com.sketchware.ai.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * v1.9.0: Google's Material Symbols (Apache-2.0; weight 400, unfilled) as image presets of the Image Manager's icon
 * screen, next to the 2014 Material icons it always had. {@code assets/icons/material_symbols.zip} holds one line per
 * icon and style (Outlined, Rounded, Sharp): name, tab, the SVG path (viewBox 0 -960 960 960). An icon is drawn into
 * a PNG of the legacy icons' size (48×48 px, 24 dp at xhdpi) in the colour chosen on the screen, so the Image Manager
 * adds it like any other icon.
 */
public final class SymbolIcons {

    public static final String ASSET = "icons/material_symbols.zip";
    public static final String[] STYLES = {"outlined", "rounded", "sharp"};

    /** One icon: its name (home, arrow_back ...) and SVG path data. */
    public static final class Icon {
        public final String name;
        public final String path;

        Icon(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    /** Receives the drawing commands of an SVG path (absolute coordinates). */
    public interface Sink {
        void moveTo(float x, float y);

        void lineTo(float x, float y);

        void quadTo(float x1, float y1, float x, float y);

        void cubicTo(float x1, float y1, float x2, float y2, float x, float y);

        void close();
    }

    private static final Map<String, List<Icon>> LOADED = new HashMap<>();

    private SymbolIcons() {
    }

    /** The icons of a style, read once from the asset. */
    public static synchronized List<Icon> load(Context context, String style) throws IOException {
        List<Icon> icons = LOADED.get(style);
        if (icons != null) return icons;
        try (InputStream in = context.getAssets().open(ASSET)) {
            Map<String, List<Icon>> all = read(in);
            LOADED.putAll(all);
        }
        icons = LOADED.get(style);
        if (icons == null) throw new IOException("no Material Symbols style " + style);
        return icons;
    }

    /** Every style in the asset (style.txt entries). */
    public static Map<String, List<Icon>> read(InputStream asset) throws IOException {
        Map<String, List<Icon>> out = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(asset);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            if (!name.endsWith(".txt")) continue;
            List<Icon> icons = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) icons.add(new Icon(line.substring(0, tab), line.substring(tab + 1)));
            }
            out.put(name.substring(0, name.length() - 4), icons);
        }
        return out;
    }

    /** Resource-safe image name: ms_&lt;icon&gt;_&lt;style&gt;_&lt;colour&gt; (lower case, digits, underscores). */
    public static String imageName(String icon, String style, String colour) {
        return ("ms_" + icon + "_" + style + "_" + colour).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    /** The icon drawn {@code size} × {@code size} pixels in {@code color} (transparent background). */
    public static Bitmap render(String pathData, int size, int color) {
        final Path path = new Path();
        final float scale = size / 960f;
        boolean ok = parse(pathData, new Sink() {
            @Override
            public void moveTo(float x, float y) {
                path.moveTo(x * scale, (y + 960f) * scale);
            }

            @Override
            public void lineTo(float x, float y) {
                path.lineTo(x * scale, (y + 960f) * scale);
            }

            @Override
            public void quadTo(float x1, float y1, float x, float y) {
                path.quadTo(x1 * scale, (y1 + 960f) * scale, x * scale, (y + 960f) * scale);
            }

            @Override
            public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
                path.cubicTo(x1 * scale, (y1 + 960f) * scale, x2 * scale, (y2 + 960f) * scale, x * scale, (y + 960f) * scale);
            }

            @Override
            public void close() {
                path.close();
            }
        });
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        if (!ok) return bitmap;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        new Canvas(bitmap).drawPath(path, paint);
        return bitmap;
    }

    /** Writes the icon as a PNG file. */
    public static File savePng(String pathData, int size, int color, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("can't create " + parent);
        Bitmap bitmap = render(pathData, size, color);
        try (OutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG not written");
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    /**
     * Parses SVG path data (M L H V Q T C S Z, absolute and relative; what the Material Symbols use) into absolute
     * commands. Returns false for anything else (arcs), with nothing more sent.
     */
    public static boolean parse(String d, Sink sink) {
        float x = 0, y = 0, startX = 0, startY = 0;
        float lastControlX = 0, lastControlY = 0;
        char last = 0;
        int i = 0, n = d.length();
        char command = 0;
        float[] value = new float[1];
        int[] at = new int[1];
        while (true) {
            at[0] = i;
            skipSeparators(d, at);
            i = at[0];
            if (i >= n) return true;
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                command = c;
                i++;
            } else if (command == 0) {
                return false;
            } else if (command == 'M') {
                command = 'L';   // coordinates after a moveto are linetos
            } else if (command == 'm') {
                command = 'l';
            }
            boolean relative = Character.isLowerCase(command);
            float bx = relative ? x : 0, by = relative ? y : 0;
            at[0] = i;
            switch (Character.toUpperCase(command)) {
                case 'Z':
                    sink.close();
                    x = startX;
                    y = startY;
                    last = 'Z';
                    continue;
                case 'M': {
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.moveTo(px, py);
                    x = startX = px;
                    y = startY = py;
                    last = 'M';
                    break;
                }
                case 'L': {
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.lineTo(px, py);
                    x = px;
                    y = py;
                    last = 'L';
                    break;
                }
                case 'H': {
                    float px = number(d, at, value) + (relative ? x : 0);
                    if (at[0] < 0) return false;
                    sink.lineTo(px, y);
                    x = px;
                    last = 'L';
                    break;
                }
                case 'V': {
                    float py = number(d, at, value) + (relative ? y : 0);
                    if (at[0] < 0) return false;
                    sink.lineTo(x, py);
                    y = py;
                    last = 'L';
                    break;
                }
                case 'Q': {
                    float x1 = number(d, at, value) + bx, y1 = number(d, at, value) + by;
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.quadTo(x1, y1, px, py);
                    lastControlX = x1;
                    lastControlY = y1;
                    x = px;
                    y = py;
                    last = 'Q';
                    break;
                }
                case 'T': {
                    float x1 = last == 'Q' ? 2 * x - lastControlX : x;
                    float y1 = last == 'Q' ? 2 * y - lastControlY : y;
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.quadTo(x1, y1, px, py);
                    lastControlX = x1;
                    lastControlY = y1;
                    x = px;
                    y = py;
                    last = 'Q';
                    break;
                }
                case 'C': {
                    float x1 = number(d, at, value) + bx, y1 = number(d, at, value) + by;
                    float x2 = number(d, at, value) + bx, y2 = number(d, at, value) + by;
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.cubicTo(x1, y1, x2, y2, px, py);
                    lastControlX = x2;
                    lastControlY = y2;
                    x = px;
                    y = py;
                    last = 'C';
                    break;
                }
                case 'S': {
                    float x1 = last == 'C' ? 2 * x - lastControlX : x;
                    float y1 = last == 'C' ? 2 * y - lastControlY : y;
                    float x2 = number(d, at, value) + bx, y2 = number(d, at, value) + by;
                    float px = number(d, at, value) + bx, py = number(d, at, value) + by;
                    if (at[0] < 0) return false;
                    sink.cubicTo(x1, y1, x2, y2, px, py);
                    lastControlX = x2;
                    lastControlY = y2;
                    x = px;
                    y = py;
                    last = 'C';
                    break;
                }
                default:
                    return false;
            }
            i = at[0];
        }
    }

    private static void skipSeparators(String d, int[] at) {
        int i = at[0];
        while (i < d.length() && (d.charAt(i) == ' ' || d.charAt(i) == ',' || d.charAt(i) == '\n' || d.charAt(i) == '\t')) i++;
        at[0] = i;
    }

    /** The next number (SVG syntax: "-1.5", ".5", "1e3", "10.83 7.12", "7.12-7.09" ...); at[0] = -1 on a bad number. */
    private static float number(String d, int[] at, float[] value) {
        if (at[0] < 0) return 0;
        skipSeparators(d, at);
        int i = at[0], start = i, n = d.length();
        if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
        boolean digits = false, dot = false;
        while (i < n) {
            char c = d.charAt(i);
            if (c >= '0' && c <= '9') {
                digits = true;
                i++;
            } else if (c == '.' && !dot) {
                dot = true;
                i++;
            } else {
                break;
            }
        }
        if (i < n && (d.charAt(i) == 'e' || d.charAt(i) == 'E') && digits) {
            int e = i + 1;
            if (e < n && (d.charAt(e) == '-' || d.charAt(e) == '+')) e++;
            if (e < n && Character.isDigit(d.charAt(e))) {
                i = e;
                while (i < n && Character.isDigit(d.charAt(i))) i++;
            }
        }
        if (!digits) {
            at[0] = -1;
            return 0;
        }
        at[0] = i;
        return value[0] = Float.parseFloat(d.substring(start, i));
    }
}
