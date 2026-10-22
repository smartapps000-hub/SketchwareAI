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
    private static volatile int colorPrimary = 0xff008dcd; // Sketchware's default primary colour

    private DesignTheme() {
    }

    public static void load(String sc_id) {
        if (sc_id == null) return;
        boolean m3 = false;
        int primary = 0xff008dcd;
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
