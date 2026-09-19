package com.sketchware.ai.project;

import java.util.HashMap;
import java.util.Map;

import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;

/**
 * Settings written into every project created in Sketchware AI.
 * <p>
 * Projects created before (including ones imported from Sketchware Pro) have no stored value
 * and keep the legacy defaults (targetSdk 28, Dx, Java 1.7), so their builds do not change
 * behind the user's back. New projects start Play-ready:
 * <ul>
 *     <li>targetSdkVersion 36 — the level Google Play requires for new apps and updates in 2026;
 *     avoids Play Protect's "built for an older version of Android" block. 37 can be typed in.</li>
 *     <li>D8 dexer with Java 8 (lambdas, method references, desugaring). Java 11 is still selectable;
 *     D8 in this build desugars up to Java 11, so Java 17 cannot be offered.</li>
 *     <li>Edge-to-edge off: generated activities pad themselves below the status bar and paint it
 *     with the theme's dark primary colour, so layouts look exactly like before on Android 15+.</li>
 * </ul>
 */
public final class ModernProjectDefaults {

    public static final int LEGACY_TARGET_SDK = 28;
    public static final int NEW_PROJECT_TARGET_SDK = 36;
    public static final int NEW_PROJECT_MIN_SDK = 21;

    /** Project setting: "true" to draw under the system bars (edge-to-edge) with auto-padding. */
    public static final String SETTING_EDGE_TO_EDGE = "edge_to_edge";

    private ModernProjectDefaults() {
    }

    /** Writes the modern defaults for a project that was just created. Keeps existing values. */
    public static void applyToNewProject(String scId) {
        Map<String, String> project = new HashMap<>();
        project.put(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(NEW_PROJECT_MIN_SDK));
        project.put(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(NEW_PROJECT_TARGET_SDK));
        project.put(SETTING_EDGE_TO_EDGE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
        putIfAbsent(new ProjectSettings(scId), project);

        // D8 runs on Android 8.0+ only (the minApi21 build can run on older phones).
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Map<String, String> build = new HashMap<>();
            build.put(BuildSettings.SETTING_DEXER, BuildSettings.SETTING_DEXER_D8);
            build.put(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
            putIfAbsent(new BuildSettings(scId), build);
        }
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
