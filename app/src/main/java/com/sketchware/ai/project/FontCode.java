package com.sketchware.ai.project;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom fonts in generated apps (v1.8.4).
 * <p>
 * Upstream writes a font block as {@code view.setTypeface(Typeface.createFromAsset(getAssets(),"fonts/name.ttf"), 0);}.
 * Fonts are copied to {@code assets/fonts/<name>.ttf} whatever they were (TTF or OTF/CFF: Android reads both, it
 * looks at the data, not the name). That line creates a new Typeface every time it runs, crashes the app when the
 * asset is missing, and leaves the TextView's line metrics to the font's own ascent/descent: Telugu vowel signs and
 * stacked consonants (ottulu) of fonts whose metrics are tighter than their ink then overlap the next line or are
 * cut at the top/bottom of the view.
 * <p>
 * {@link #rewrite} turns that line into {@code SketchFonts.apply(view, getAssets(), "fonts/name.ttf", 0);} (same line,
 * so line numbers and the source map stay valid), and {@link #helperClass} is the {@code SketchFonts} class written
 * into the project's package when a generated file uses it. Text is still shaped by Android's own text stack
 * (Minikin + HarfBuzz, with the font's GSUB/GPOS); the helper measures the shaped ink of the font and gives the view
 * the room it needs.
 */
public final class FontCode {

    public static final String CLASS_NAME = "SketchFonts";
    public static final String FILE_NAME = CLASS_NAME + ".java";

    /** {@code <receiver>.setTypeface(Typeface.createFromAsset(<assets>,"fonts/<file>"), <style>);} as Fx writes it. */
    private static final Pattern SET_TYPEFACE = Pattern.compile(
            "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\.setTypeface\\(Typeface\\.createFromAsset\\("
                    + "((?:getContext\\(\\)\\.)?getAssets\\(\\)),\\s*\"(fonts/[^\"]+)\"\\),\\s*([^;()]+)\\);");

    private FontCode() {
    }

    /** Rewrites the generated font blocks of one Java file; returns the same string when there are none. */
    public static String rewrite(String code) {
        if (code == null || !code.contains("Typeface.createFromAsset(")) return code;
        Matcher m = SET_TYPEFACE.matcher(code);
        StringBuffer out = new StringBuffer(code.length() + 64);
        boolean any = false;
        while (m.find()) {
            any = true;
            m.appendReplacement(out, Matcher.quoteReplacement(
                    CLASS_NAME + ".apply(" + m.group(1) + ", " + m.group(2) + ", \"" + m.group(3) + "\", " + m.group(4).trim() + ");"));
        }
        if (!any) return code;
        m.appendTail(out);
        return out.toString();
    }

    /** Whether a generated file calls the helper. */
    public static boolean uses(String code) {
        return code != null && code.contains(CLASS_NAME + ".apply(");
    }

    /** Source of {@code <package>.SketchFonts}. */
    public static String helperClass(String packageName) {
        return "package " + packageName + ";\n"
                + "\n"
                + "import android.content.res.AssetManager;\n"
                + "import android.graphics.Paint;\n"
                + "import android.graphics.Rect;\n"
                + "import android.graphics.Typeface;\n"
                + "import android.os.Build;\n"
                + "import android.text.TextPaint;\n"
                + "import android.widget.TextView;\n"
                + "\n"
                + "import java.io.ByteArrayOutputStream;\n"
                + "import java.io.InputStream;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Locale;\n"
                + "import java.util.Map;\n"
                + "import java.util.WeakHashMap;\n"
                + "\n"
                + "/**\n"
                + " * Written by Sketchware AI for the font blocks of this project.\n"
                + " * Loads each font of assets/fonts once (TTF or OTF), sets it on the view, and gives the view room for the\n"
                + " * ink of complex scripts: Telugu vowel signs and stacked consonants are shaped by Android (HarfBuzz) with\n"
                + " * the font's own OpenType rules, then measured, so lines don't overlap and nothing is cut at the edges.\n"
                + " */\n"
                + "public final class SketchFonts {\n"
                + "\n"
                + "    /** Telugu clusters with the highest and the deepest ink (vowel signs above, three-level ottulu below). */\n"
                + "    static final String TELUGU_PROBE = \"\\u0c36\\u0c4d\\u0c30\\u0c40 \\u0c32\\u0c15\\u0c4d\\u0c37\\u0c4d\\u0c2e\\u0c40 "
                + "\\u0c38\\u0c4d\\u0c24\\u0c4d\\u0c30\\u0c40 \\u0c17\\u0c4d\\u0c30\\u0c40\\u0c37\\u0c4d\\u0c2e "
                + "\\u0c1f\\u0c4d\\u0c1f\\u0c4d\\u0c30\\u0c40 \\u0c37\\u0c4d\\u0c1f\\u0c4d\\u0c30\\u0c4d\\u0c2f\\u0c48 "
                + "\\u0c16\\u0c4d\\u0c2f\\u0c48 \\u0c22\\u0c4d\\u0c22\\u0c4d\\u0c30\\u0c42 \\u0c19\\u0c4d\\u0c15\\u0c4d\\u0c37\\u0c4d\\u0c35\";\n"
                + "    /** Latin letters with accents above and descenders below; only the ones the font has are measured. */\n"
                + "    static final String LATIN_PROBE = \"\\u00c9\\u00c5Hgjpqy\";\n"
                + "\n"
                + "    private static final Map<String, Typeface> TYPEFACES = new HashMap<String, Typeface>();\n"
                + "    /** Per font: the probe text made of characters the font itself maps (never system fallback glyphs). */\n"
                + "    private static final Map<Typeface, String> PROBES = new WeakHashMap<Typeface, String>();\n"
                + "    /** Padding this class added to a view (top, bottom), so applying a font again doesn't add it twice. */\n"
                + "    private static final Map<TextView, int[]> ADDED = new WeakHashMap<TextView, int[]>();\n"
                + "\n"
                + "    private SketchFonts() {\n"
                + "    }\n"
                + "\n"
                + "    /** What the font block does: the font (loaded once), the style, and room for its ink. */\n"
                + "    public static void apply(TextView view, AssetManager assets, String path, int style) {\n"
                + "        Typeface typeface = get(assets, path);\n"
                + "        view.setTypeface(typeface, style);\n"
                + "        fit(view, probe(assets, path, typeface));\n"
                + "    }\n"
                + "\n"
                + "    /** Same line in custom code with a view that isn't a TextView but has setTypeface(Typeface, int). */\n"
                + "    public static void apply(Object view, AssetManager assets, String path, int style) {\n"
                + "        if (view instanceof TextView) {\n"
                + "            apply((TextView) view, assets, path, style);\n"
                + "            return;\n"
                + "        }\n"
                + "        try {\n"
                + "            view.getClass().getMethod(\"setTypeface\", Typeface.class, int.class).invoke(view, get(assets, path), style);\n"
                + "        } catch (Exception e) {\n"
                + "            throw new IllegalArgumentException(\"setTypeface(Typeface, int) failed on \" + view, e);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /** The font at assets/<path>; Typeface.DEFAULT (and a log line) when it can't be read, instead of a crash. */\n"
                + "    public static synchronized Typeface get(AssetManager assets, String path) {\n"
                + "        Typeface typeface = TYPEFACES.get(path);\n"
                + "        if (typeface != null) return typeface;\n"
                + "        typeface = load(assets, path);\n"
                + "        if (typeface == null) typeface = load(assets, path.toLowerCase(Locale.ROOT)); // copied in lower case\n"
                + "        if (typeface == null) {\n"
                + "            android.util.Log.w(\"SketchFonts\", \"Font not found in assets: \" + path);\n"
                + "            typeface = Typeface.DEFAULT;\n"
                + "        }\n"
                + "        TYPEFACES.put(path, typeface);\n"
                + "        return typeface;\n"
                + "    }\n"
                + "\n"
                + "    private static Typeface load(AssetManager assets, String path) {\n"
                + "        try {\n"
                + "            return Typeface.createFromAsset(assets, path);\n"
                + "        } catch (RuntimeException e) {\n"
                + "            return null;\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * Gives the view room for the shaped ink of its font: line spacing so that stacked consonants of one line\n"
                + "     * never reach the vowel signs of the next, and top/bottom padding where the first/last line's ink goes\n"
                + "     * beyond what the view reserves. Depends on the font and text size only, not on the text, so the layout\n"
                + "     * doesn't jump when the text changes. A font whose ink fits its metrics changes nothing.\n"
                + "     */\n"
                + "    public static void fit(TextView view, String probe) {\n"
                + "        if (Build.VERSION.SDK_INT >= 35) {\n"
                + "            // Android 15: lay out by glyph bounds, so marks that hang past the first/last letter aren't cut\n"
                + "            call(view, \"setUseBoundsForWidth\");\n"
                + "            call(view, \"setShiftDrawingOffsetForStartOverhang\");\n"
                + "        }\n"
                + "        if (probe == null || probe.length() == 0 || Build.VERSION.SDK_INT < 16) return;\n"
                + "        TextPaint paint = new TextPaint(view.getPaint());\n"
                + "        Paint.FontMetricsInt fm = paint.getFontMetricsInt();\n"
                + "        Rect ink = new Rect();\n"
                + "        paint.getTextBounds(probe, 0, probe.length(), ink);\n"
                + "        int above = Math.max(-ink.top, -fm.ascent);\n"
                + "        int below = Math.max(ink.bottom, fm.descent);\n"
                + "        int fontHeight = fm.descent - fm.ascent;\n"
                + "        if (fontHeight > 0 && above + below > fontHeight) {\n"
                + "            float multiplier = (above + below) / (float) fontHeight;\n"
                + "            if (multiplier > view.getLineSpacingMultiplier()) {\n"
                + "                view.setLineSpacing(view.getLineSpacingExtra(), multiplier);\n"
                + "            }\n"
                + "        }\n"
                + "        boolean fontPadding = view.getIncludeFontPadding();\n"
                + "        int roomAbove = fontPadding ? Math.max(-fm.top, -fm.ascent) : -fm.ascent;\n"
                + "        int roomBelow = fontPadding ? Math.max(fm.bottom, fm.descent) : fm.descent;\n"
                + "        int addTop = Math.max(0, above - roomAbove);\n"
                + "        int addBottom = Math.max(0, below - roomBelow);\n"
                + "        int[] before = ADDED.get(view);\n"
                + "        int oldTop = before == null ? 0 : before[0];\n"
                + "        int oldBottom = before == null ? 0 : before[1];\n"
                + "        if (addTop != oldTop || addBottom != oldBottom) {\n"
                + "            view.setPadding(view.getPaddingLeft(), view.getPaddingTop() - oldTop + addTop,\n"
                + "                    view.getPaddingRight(), view.getPaddingBottom() - oldBottom + addBottom);\n"
                + "            ADDED.put(view, new int[]{addTop, addBottom});\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    private static void call(TextView view, String setter) {\n"
                + "        try {\n"
                + "            TextView.class.getMethod(setter, boolean.class).invoke(view, true);\n"
                + "        } catch (Exception ignored) {\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /**\n"
                + "     * The text whose ink is measured for this font: the Latin probe letters the font maps, and the Telugu probe\n"
                + "     * when the font has Telugu (KA and the virama in its cmap). Read from the font file itself, so glyphs that\n"
                + "     * Android would take from a system fallback font are never measured. Empty when the file can't be read.\n"
                + "     */\n"
                + "    static synchronized String probe(AssetManager assets, String path, Typeface typeface) {\n"
                + "        String known = PROBES.get(typeface);\n"
                + "        if (known != null) return known;\n"
                + "        StringBuilder probe = new StringBuilder();\n"
                + "        byte[] font = read(assets, path);\n"
                + "        if (font == null) font = read(assets, path.toLowerCase(Locale.ROOT));\n"
                + "        if (font != null) {\n"
                + "            try {\n"
                + "                for (int i = 0; i < LATIN_PROBE.length(); i++) {\n"
                + "                    if (maps(font, LATIN_PROBE.charAt(i))) probe.append(LATIN_PROBE.charAt(i));\n"
                + "                }\n"
                + "                if (maps(font, 0x0C15) && maps(font, 0x0C4D)) probe.append(' ').append(TELUGU_PROBE);\n"
                + "            } catch (RuntimeException e) {\n"
                + "                probe.setLength(0); // not a font file Android reads either\n"
                + "            }\n"
                + "        }\n"
                + "        String result = probe.toString().trim();\n"
                + "        PROBES.put(typeface, result);\n"
                + "        return result;\n"
                + "    }\n"
                + "\n"
                + "    private static byte[] read(AssetManager assets, String path) {\n"
                + "        InputStream in = null;\n"
                + "        try {\n"
                + "            in = assets.open(path);\n"
                + "            if (in.available() > 32 * 1024 * 1024) return null; // a huge (CJK) font is not read into memory\n"
                + "            ByteArrayOutputStream out = new ByteArrayOutputStream();\n"
                + "            byte[] buffer = new byte[16384];\n"
                + "            int n;\n"
                + "            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);\n"
                + "            return out.toByteArray();\n"
                + "        } catch (Exception e) {\n"
                + "            return null;\n"
                + "        } finally {\n"
                + "            if (in != null) {\n"
                + "                try {\n"
                + "                    in.close();\n"
                + "                } catch (Exception ignored) {\n"
                + "                }\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    /** True when the font's Unicode cmap (format 4 or 12) gives code point cp a glyph. TTC: first font. */\n"
                + "    static boolean maps(byte[] f, int cp) {\n"
                + "        int sfnt = 0;\n"
                + "        if (u32(f, 0) == 0x74746366) sfnt = (int) u32(f, 12); // 'ttcf'\n"
                + "        int tables = u16(f, sfnt + 4);\n"
                + "        int cmap = -1;\n"
                + "        for (int i = 0; i < tables; i++) {\n"
                + "            int rec = sfnt + 12 + 16 * i;\n"
                + "            if (u32(f, rec) == 0x636d6170) cmap = (int) u32(f, rec + 8); // 'cmap'\n"
                + "        }\n"
                + "        if (cmap < 0) return false;\n"
                + "        int subtables = u16(f, cmap + 2);\n"
                + "        for (int i = 0; i < subtables; i++) {\n"
                + "            int platform = u16(f, cmap + 4 + 8 * i), encoding = u16(f, cmap + 6 + 8 * i);\n"
                + "            if (!(platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10)))) continue;\n"
                + "            int t = cmap + (int) u32(f, cmap + 8 + 8 * i);\n"
                + "            int format = u16(f, t);\n"
                + "            if (format == 4) {\n"
                + "                int segX2 = u16(f, t + 6);\n"
                + "                int ends = t + 14, starts = ends + segX2 + 2, deltas = starts + segX2, offsets = deltas + segX2;\n"
                + "                for (int s = 0; s < segX2 / 2; s++) {\n"
                + "                    int end = u16(f, ends + 2 * s), start = u16(f, starts + 2 * s);\n"
                + "                    if (cp < start || cp > end) continue;\n"
                + "                    int delta = u16(f, deltas + 2 * s), offset = u16(f, offsets + 2 * s);\n"
                + "                    int glyph;\n"
                + "                    if (offset == 0) {\n"
                + "                        glyph = (cp + delta) & 0xFFFF;\n"
                + "                    } else {\n"
                + "                        glyph = u16(f, offsets + 2 * s + offset + 2 * (cp - start));\n"
                + "                        if (glyph != 0) glyph = (glyph + delta) & 0xFFFF;\n"
                + "                    }\n"
                + "                    if (glyph != 0) return true;\n"
                + "                }\n"
                + "            } else if (format == 12) {\n"
                + "                long groups = u32(f, t + 12);\n"
                + "                for (long g = 0; g < groups; g++) {\n"
                + "                    int rec = t + 16 + (int) (12 * g);\n"
                + "                    if (cp >= u32(f, rec) && cp <= u32(f, rec + 4)) return true;\n"
                + "                }\n"
                + "            }\n"
                + "        }\n"
                + "        return false;\n"
                + "    }\n"
                + "\n"
                + "    private static int u16(byte[] f, int i) {\n"
                + "        return ((f[i] & 0xFF) << 8) | (f[i + 1] & 0xFF);\n"
                + "    }\n"
                + "\n"
                + "    private static long u32(byte[] f, int i) {\n"
                + "        return ((long) u16(f, i) << 16) | u16(f, i + 2);\n"
                + "    }\n"
                + "}\n";
    }
}
