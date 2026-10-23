package com.sketchware.ai.project;

import com.google.android.material.color.utilities.Hct;

import java.util.Random;

/**
 * Colour presets of the New Project screen (v1.8.7): the five project colours Sketchware stores per project
 * (color_accent, color_primary, color_primary_dark, color_control_highlight, color_control_normal, in that order,
 * the order of {@code MyProjectSettingActivity.themeColorKeys}).
 * <p>
 * The colours are fixed values, not the IDE's own theme colours: up to v1.8.6 a new project took its defaults from the
 * IDE's resources, and the IDE's night resources made every new project's status bar #0B0B0D.
 * <p>
 * "Generate random" builds a set from one random hue with Material Color Utilities (HCT): primary at tone 40, the dark
 * primary at tone 30 of the same hue, the accent from the hue 60 degrees on (the tertiary relation Material uses).
 */
public final class ThemePresets {

    /** Keys of the project's colours in the project list data (lC), in {@link Preset#colors} order. */
    public static final String[] KEYS = {"color_accent", "color_primary", "color_primary_dark",
            "color_control_highlight", "color_control_normal"};

    /**
     * Labels of the five colours on the New Project screen: what each one colours in the app (colorAccent = FAB and
     * accents, colorPrimary = toolbar and buttons, colorPrimaryDark = status bar, colorControlHighlight = ripples,
     * colorControlNormal = unchecked controls).
     */
    public static final String[] LABELS = {"Accent", "Primary", "Status bar", "Ripple", "Controls"};

    public static final class Preset {
        public final String name;
        /** accent, primary, primary dark, control highlight, control normal (ARGB). */
        public final int[] colors;

        Preset(String name, int accent, int primary, int primaryDark) {
            this(name, new int[]{accent, primary, primaryDark, highlight(primary), 0xFF757575});
        }

        Preset(String name, int[] colors) {
            this.name = name;
            this.colors = colors;
        }
    }

    public static final Preset[] PRESETS = {
            new Preset("Solar Orange", 0xFFFF7043, 0xFFF4511E, 0xFFBF360C),
            new Preset("Material Purple", 0xFF7D5260, 0xFF6750A4, 0xFF4F378B),
            new Preset("Material Blue", 0xFF00897B, 0xFF1565C0, 0xFF0D47A1),
            new Preset("Material Green", 0xFF00838F, 0xFF2E7D32, 0xFF1B5E20),
            new Preset("Rose Wine", 0xFFB4637A, 0xFF8E4A5E, 0xFF6B2C3F),
    };

    private ThemePresets() {
    }

    /** colorControlHighlight of a primary colour: the colour at 20% alpha (ripples, selection). */
    static int highlight(int primary) {
        return 0x33000000 | (primary & 0xFFFFFF);
    }

    /** A harmonious random set (see the class comment); {@code random} decides the hue. */
    public static int[] random(Random random) {
        double hue = random.nextInt(360);
        int primary = Hct.from(hue, 48, 40).toInt();
        int primaryDark = Hct.from(hue, 48, 30).toInt();
        int accent = Hct.from((hue + 60) % 360, 40, 50).toInt();
        return new int[]{accent | 0xFF000000, primary | 0xFF000000, primaryDark | 0xFF000000, highlight(primary), 0xFF757575};
    }

    /** Index of the preset with exactly these colours, or -1 (custom or random). */
    public static int indexOf(int[] colors) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (java.util.Arrays.equals(PRESETS[i].colors, colors)) return i;
        }
        return -1;
    }
}
