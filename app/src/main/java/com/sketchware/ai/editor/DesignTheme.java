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
    /** The generator rewrites widgets for Material 3 (M3Widgets.decide): the canvas draws them that way. */
    private static volatile boolean widgets;
    private static volatile int colorPrimary = 0xfff4511e; // R.color.color_primary, the generator's fallback (yq)
    /** v1.8.6: the project's Material 3 colour roles (light), as the generator writes them (M3Palette); null if not M3. */
    private static volatile com.sketchware.ai.project.M3Palette palette;

    private DesignTheme() {
    }

    public static void load(String sc_id) {
        if (sc_id == null) return;
        boolean m3 = false;
        boolean m3Widgets = false;
        int primary = fallbackPrimary();
        try {
            m3 = com.sketchware.ai.project.ModernProjectDefaults.material3(new ProjectSettings(sc_id));
            if (m3) {
                // like the generator (M3Widgets): the Material 3 theme needs the AppCompat and Design libraries.
                // Read with own instances, never jC's: jC caches the project for the editor (preferring the backup),
                // and DesignActivity.loadProject would then get that copy instead of the one the user chose.
                a.a.a.iC libraries = new a.a.a.iC(sc_id);
                if (libraries.g()) {
                    libraries.h();
                } else {
                    libraries.i();
                }
                com.besome.sketch.beans.ProjectLibraryBean compat = libraries.c();
                m3 = compat != null && "Y".equals(compat.useYn);
            }
            if (m3) {
                a.a.a.hC files = new a.a.a.hC(sc_id);
                if (files.g()) {
                    files.h();
                } else {
                    files.i();
                }
                m3Widgets = com.sketchware.ai.project.M3Widgets.decide(sc_id, true, files);
            }
        } catch (RuntimeException ignored) {
            // unreadable settings: the MaterialComponents look, as before
        }
        int accent = fallbackAccent();
        try {
            HashMap<String, Object> metadata = a.a.a.lC.b(sc_id);
            Object value = metadata == null ? null : metadata.get("color_primary");
            if (value instanceof Number) primary = ((Number) value).intValue();
            Object accentValue = metadata == null ? null : metadata.get("color_accent");
            if (accentValue instanceof Number) accent = ((Number) accentValue).intValue();
        } catch (RuntimeException ignored) {
            // keep the default colour
        }
        com.sketchware.ai.project.M3Palette roles = null;
        if (m3 && !com.sketchware.ai.project.M3Widgets.stylesReplaced(sc_id)) {
            // the generated AppTheme with these roles (a styles.xml of the user's own replaces it)
            try {
                roles = com.sketchware.ai.project.M3Palette.of(primary, accent);
            } catch (RuntimeException ignored) {
                // the canvas falls back to the M3 baseline colours
            }
        }
        scId = sc_id;
        material3 = m3;
        widgets = m3Widgets;
        colorPrimary = primary;
        palette = roles;
        com.besome.sketch.editor.view.item.ItemLinearLayout.refreshScreenRoots();
    }

    /** The colour yq writes when the project metadata has no color_accent. */
    private static int fallbackAccent() {
        try {
            return androidx.core.content.ContextCompat.getColor(com.besome.sketch.SketchApplication.getContext(),
                    com.sketchware.remod.R.color.color_accent);
        } catch (RuntimeException e) {
            return 0xffff7a29;
        }
    }

    /**
     * A light Material 3 colour role of the open project (resource name of M3Palette.ROLES, e.g. "sketch_m3_outline"), or the
     * fallback when the project isn't Material 3.
     */
    public static int role(String name, int fallback) {
        com.sketchware.ai.project.M3Palette roles = palette;
        if (roles == null) return fallback;
        int value = roles.get(name, false);
        return value == 0 ? fallback : value;
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

    /** Widgets are drawn as the generated Material 3 widgets (MaterialSwitch, outlined field, filled button). */
    public static boolean widgets() {
        return widgets;
    }

    public static int colorPrimary() {
        return colorPrimary;
    }

    public static String scId() {
        return scId;
    }
}
