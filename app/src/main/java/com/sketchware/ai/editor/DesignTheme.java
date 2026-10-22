package com.sketchware.ai.editor;

import java.util.HashMap;

import mod.hey.studios.project.ProjectSettings;

/**
 * What the design editor needs to know about the open project's AppTheme: whether it is the Material 3 theme
 * (see {@link com.sketchware.ai.project.ModernProjectDefaults#SETTING_MATERIAL3}) and its primary colour.
 * Loaded when the design screen opens a project and again when Project Configuration is saved.
 */
public final class DesignTheme {

    private static volatile String scId;
    private static volatile boolean material3;
    private static volatile int colorPrimary = 0xfff4511e; // R.color.color_primary, the generator's fallback (yq)

    private DesignTheme() {
    }

    public static void load(String sc_id) {
        if (sc_id == null) return;
        boolean m3 = false;
        int primary = fallbackPrimary();
        try {
            m3 = com.sketchware.ai.project.ModernProjectDefaults.material3(new ProjectSettings(sc_id));
        } catch (RuntimeException ignored) {
            // unreadable settings: the MaterialComponents look, as before
        }
        try {
            HashMap<String, Object> metadata = a.a.a.lC.b(sc_id);
            Object value = metadata == null ? null : metadata.get("color_primary");
            if (value instanceof Number) primary = ((Number) value).intValue();
        } catch (RuntimeException ignored) {
            // keep the default colour
        }
        scId = sc_id;
        material3 = m3;
        colorPrimary = primary;
    }

    /** The colour yq writes when the project metadata has no color_primary. */
    private static int fallbackPrimary() {
        try {
            return androidx.core.content.ContextCompat.getColor(com.besome.sketch.SketchApplication.getContext(),
                    com.sketchware.remod.R.color.color_primary);
        } catch (RuntimeException e) {
            return 0xfff4511e;
        }
    }

    /** The project the editor shows uses the Material 3 AppTheme. */
    public static boolean material3() {
        return material3;
    }

    public static int colorPrimary() {
        return colorPrimary;
    }

    public static String scId() {
        return scId;
    }
}
