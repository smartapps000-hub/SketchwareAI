package com.sketchware.ai.project;

import java.util.ArrayList;
import java.util.List;

import a.a.a.jq;
import a.a.a.yq;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.util.LogUtil;

/**
 * Warnings collected during one Run build, so problems that don't stop the build are never
 * silent: they go to logcat as they happen, and when the build finishes the editor shows
 * "Built with N warnings" and saves them to the compile log (Show last compile error).
 * <p>
 * The checks at the start compare the project against what this toolchain really provides:
 * code is compiled against the bundled Android 13 (API 33) android.jar with ECJ 3.26 and
 * D8/R8 3.3, and the bundled Jetpack/Firebase/Play libraries are the 2021-2022 releases.
 */
public final class BuildWarnings {

    /** API level of assets/libs/android.jar.zip, the platform user code is compiled against. */
    public static final int BUNDLED_ANDROID_JAR_API = 33;

    private static final String TAG = "BuildWarnings";
    private static final List<String> warnings = new ArrayList<>();
    /** Advisory facts about the target/toolchain: logged and listed, but never a "warning" alone. */
    private static final List<String> notes = new ArrayList<>();

    /** Entries from a build that restarted itself, shown with the build that follows. */
    private static final List<String> carried = new ArrayList<>();

    private BuildWarnings() {
    }

    public static synchronized void carry(List<String> entries) {
        carried.addAll(entries);
    }

    /** Starts a build: clears old warnings and checks the project's settings and features. */
    public static synchronized void begin(yq project) {
        warnings.clear();
        notes.clear();
        warnings.addAll(carried);
        carried.clear();
        if (project == null) return;
        try {
            ProjectSettings settings = new ProjectSettings(project.sc_id);
            BuildSettings build = new BuildSettings(project.sc_id);
            int target = ModernProjectDefaults.targetSdk(settings);
            String dexer = build.getValue(BuildSettings.SETTING_DEXER, ModernProjectDefaults.defaultDexer());
            String java = build.getValue(BuildSettings.SETTING_JAVA_VERSION, ModernProjectDefaults.defaultJavaVersion());
            String customJar = build.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, "");
            jq features = project.N;

            if (target > BUNDLED_ANDROID_JAR_API && customJar.trim().isEmpty()) {
                note("Target API " + target + " (Android " + (target - 20) + "), but code is compiled against the bundled "
                        + "Android 13 (API " + BUNDLED_ANDROID_JAR_API + ") android.jar: APIs added in Android 14+ can't be "
                        + "called from your code. The app still gets Android " + (target - 20) + "'s runtime rules. "
                        + "Set a newer android.jar in Build Settings to use newer APIs.");
            }
            if (BuildSettings.SETTING_DEXER_DX.equals(dexer) && !BuildSettings.SETTING_JAVA_VERSION_1_7.equals(java)) {
                add("Dx can't read Java " + java + " bytecode reliably; switch the dexer to D8 in Build Settings.");
            }
            if (target >= 37 && features != null) {
                if (features.hasPermission(jq.PERMISSION_BLUETOOTH)) {
                    note("Android 17: a closed Bluetooth (RFCOMM) socket now returns -1 from read() instead of throwing. "
                            + "Sketchware AI's BluetoothController handles it; check your own custom Bluetooth code.");
                }
                if (features.hasPermission(jq.PERMISSION_INTERNET)) {
                    note("Android 17: connecting to devices on the local network (LAN IPs, printers, ESP32) needs the "
                            + "ACCESS_LOCAL_NETWORK runtime permission. Internet (public) servers are not affected.");
                }
                if (features.hasPermission(jq.PERMISSION_RECORD_AUDIO)) {
                    note("Android 17: audio started from the background needs a foreground service; playback from a "
                            + "visible screen is not affected.");
                }
            }
            if (target >= 36) {
                note("Android 16+: on tablets and foldables (600dp+ wide) fixed screen orientation and resizability "
                        + "settings are ignored; test layouts in landscape.");
            }
        } catch (Throwable t) {
            // The checks are advisory; never let them break a build.
            LogUtil.e(TAG, "Build checks failed", t);
        }
    }

    public static synchronized void add(String warning) {
        if (warning == null || warnings.contains(warning)) return;
        warnings.add(warning);
        LogUtil.w(TAG, warning);
    }

    public static synchronized void note(String note) {
        if (note == null || notes.contains(note)) return;
        notes.add(note);
        LogUtil.d(TAG, "note: " + note);
    }

    /** @return true if a warning of this build (or one carried into it) contains {@code text} */
    public static synchronized boolean mentions(String text) {
        for (String warning : warnings) if (warning.contains(text)) return true;
        for (String warning : carried) if (warning.contains(text)) return true;
        return false;
    }

    /** Real problems only (a step failed, was skipped or downgraded); notes don't count. */
    public static synchronized int count() {
        return warnings.size();
    }

    /** Text for the compile log: "Build succeeded with N warning(s):" and a numbered list. */
    public static synchronized String report() {
        StringBuilder out = new StringBuilder("Build succeeded with " + warnings.size()
                + (warnings.size() == 1 ? " warning" : " warnings") + ":\n");
        for (int i = 0; i < warnings.size(); i++) {
            out.append('\n').append(i + 1).append(warnings.get(i).startsWith("auto-fix: ") ? ". " : ". warning: ")
                    .append(warnings.get(i)).append('\n');
        }
        if (!notes.isEmpty()) {
            out.append("\nNotes for this target / toolchain:\n");
            for (String note : notes) out.append("\n- note: ").append(note).append('\n');
        }
        return out.toString();
    }

    /**
     * Text appended to a failed build's log: the warnings and notes collected before the failure
     * (e.g. "compiled against API 33" explains a "cannot find symbol" for an Android 14+ API).
     * Empty when there is nothing to add.
     */
    public static synchronized String failureAppendix() {
        if (warnings.isEmpty() && notes.isEmpty()) return "";
        return "\n\n" + report().replaceFirst("^Build succeeded with [0-9]+ warnings?:",
                "Before the build failed (" + warnings.size() + (warnings.size() == 1 ? " warning" : " warnings") + "):");
    }
}
