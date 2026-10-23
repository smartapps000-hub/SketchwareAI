package com.sketchware.ai.project;

import java.util.HashMap;
import java.util.Map;

import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;

/**
 * Settings written into every project created in Sketchware AI.
 * <p>
 * Projects created before (including ones imported from Sketchware Pro) keep what they stored.
 * Where they stored nothing, the target SDK stays the legacy 28, and the dexer and Java level
 * are D8 and Java 11 on Android 8.0+ (Dx and Java 1.7 below), see {@link #defaultDexer}.
 * New projects start Play-ready:
 * <ul>
 *     <li>targetSdkVersion 37 (Android 17); avoids Play Protect's "built for an older version of
 *     Android" block.</li>
 *     <li>D8 dexer with Java 11 (D8 in this build desugars up to Java 11; Java 17 needs a newer
 *     compiler + D8). If the on-device compiler can't use Java 11 system libraries, the build
 *     falls back to Java 8 automatically (see {@link #javaFallback}).</li>
 *     <li>Edge-to-edge off: generated activities pad themselves below the status bar and paint it
 *     with the theme's dark primary colour, so layouts look exactly like before on Android 15+.</li>
 * </ul>
 */
public final class ModernProjectDefaults {

    public static final int LEGACY_TARGET_SDK = 28;
    public static final int NEW_PROJECT_TARGET_SDK = 37;
    public static final int NEW_PROJECT_MIN_SDK = 21;

    /** Project setting: "true" to draw under the system bars (edge-to-edge) with auto-padding. */
    public static final String SETTING_EDGE_TO_EDGE = "edge_to_edge";
    /** Auto-heal builds (see {@link AutoHeal}); on unless the project turns it off. */
    public static final String SETTING_AUTO_HEAL = "auto_heal";
    /**
     * Project setting: "true" makes AppTheme a Material 3 theme (Theme.Material3.Light.NoActionBar, or DayNight with
     * {@link #SETTING_M3_DARK_THEME}).
     * New projects start with it on; projects that never stored it (older and imported ones) keep the
     * MaterialComponents theme they were built with.
     */
    public static final String SETTING_MATERIAL3 = "material3_theme";

    /**
     * v1.8.6: "true" makes the Material 3 AppTheme a DayNight theme (light and dark colour roles, values-night): the app
     * follows the phone's dark mode. New projects start with it on; Material 3 projects of v1.8.4/v1.8.5 never stored it
     * and stay light.
     */
    public static final String SETTING_M3_DARK_THEME = "m3_dark_theme";
    /**
     * v1.8.6: "true" applies dynamic colour (DynamicColors) in every generated activity: on Android 12+ the theme's
     * colour roles come from the phone's wallpaper instead of the project colours. Off unless the user turns it on.
     */
    public static final String SETTING_M3_DYNAMIC_COLOR = "m3_dynamic_color";

    public static boolean m3DarkTheme(ProjectSettings settings) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                settings.getValue(SETTING_M3_DARK_THEME, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }

    public static boolean m3DynamicColor(ProjectSettings settings) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                settings.getValue(SETTING_M3_DYNAMIC_COLOR, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }

    public static boolean material3(ProjectSettings settings) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                settings.getValue(SETTING_MATERIAL3, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }

    public static boolean autoHealEnabled(String sc_id) {
        try {
            return !ProjectSettings.SETTING_GENERIC_VALUE_FALSE.equals(
                    new ProjectSettings(sc_id).getValue(SETTING_AUTO_HEAL, ProjectSettings.SETTING_GENERIC_VALUE_TRUE));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private ModernProjectDefaults() {
    }

    /** Writes the modern defaults for a project that was just created. Keeps existing values. */
    public static void applyToNewProject(String scId) {
        BuildSettings.forgetMisplaced(scId);
        Map<String, String> project = new HashMap<>();
        project.put(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(NEW_PROJECT_MIN_SDK));
        project.put(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(NEW_PROJECT_TARGET_SDK));
        project.put(SETTING_EDGE_TO_EDGE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        project.put(SETTING_MATERIAL3, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        project.put(SETTING_M3_DARK_THEME, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        project.put(SETTING_M3_DYNAMIC_COLOR, ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        putIfAbsent(new ProjectSettings(scId), project);

        // D8 runs on Android 8.0+ only (the minApi21 build can run on older phones).
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Map<String, String> build = new HashMap<>();
            build.put(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
            build.put(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_11);
            putIfAbsent(new BuildSettings(scId), build);
        }
    }

    /** Sets D8 + Java 11 (Android 8.0+) for a project, overwriting its stored choice. */
    public static void applyModernBuildSettings(String scId) {
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        BuildSettings build = new BuildSettings(scId);
        build.putValue(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
        build.putValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_11);
        build.saveNow();
    }

    private static void putIfAbsent(ProjectSettings settings, Map<String, String> values) {
        boolean changed = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (settings.getValue(entry.getKey(), "").isEmpty()) {
                settings.putValue(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) settings.saveNow();
    }

    /**
     * Dexer used when a project never saved one: D8 wherever it can run (Android 8.0+), since Dx
     * cannot read Java 8+ bytecode or most modern libraries.
     */
    public static String defaultDexer() {
        return android.os.Build.VERSION.SDK_INT >= 26 ? BuildSettings.SETTING_DEXER_D8 : BuildSettings.SETTING_DEXER_DX;
    }

    /** Java language level used when a project never saved one. */
    public static String defaultJavaVersion() {
        return android.os.Build.VERSION.SDK_INT >= 26 ? BuildSettings.SETTING_JAVA_VERSION_11 : BuildSettings.SETTING_JAVA_VERSION_1_7;
    }

    /** Java level to retry with when {@code version} fails for reasons outside the user's code. */
    public static String javaFallback(String version) {
        if (BuildSettings.SETTING_JAVA_VERSION_1_9.equals(version)
                || BuildSettings.SETTING_JAVA_VERSION_10.equals(version)
                || BuildSettings.SETTING_JAVA_VERSION_11.equals(version)) {
            return BuildSettings.SETTING_JAVA_VERSION_1_8;
        }
        return null;
    }

    /** Whether ECJ's errors mean it couldn't see the Java system library at this level. */
    public static boolean isSystemLibraryFailure(String errors) {
        if (errors == null) return false;
        return errors.contains("java.lang.Object cannot be resolved")
                || errors.contains("accessible from more than one module")
                || errors.contains("The type java.lang.String cannot be resolved")
                || errors.contains("String cannot be resolved to a type")
                || errors.contains("system libraries")
                || errors.contains("--release");
    }

    public static int targetSdk(ProjectSettings settings) {
        try {
            return Integer.parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION,
                    String.valueOf(LEGACY_TARGET_SDK)).trim());
        } catch (NumberFormatException e) {
            return LEGACY_TARGET_SDK;
        }
    }

    public static boolean edgeToEdge(ProjectSettings settings) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                settings.getValue(SETTING_EDGE_TO_EDGE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }

    /** Whether generated activities need the system-bar insets helper. */
    public static boolean needsSystemBarsCode(ProjectSettings settings) {
        return edgeToEdge(settings) || targetSdk(settings) >= 35;
    }
}
