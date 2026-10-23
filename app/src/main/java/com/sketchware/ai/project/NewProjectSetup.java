package com.sketchware.ai.project;

import android.content.Context;

import java.util.HashMap;

import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;

/**
 * Creates a project from the choices of the New Project screen (v1.8.7). The screen and the Android-runtime harness
 * both call {@link #create}, so the harness builds exactly what the screen writes.
 * <p>
 * Order, as the project setting dialog did it before (MyProjectSettingActivity.SaveProjectAsyncTask): the project list
 * entry (lC), the project's preference files (wq.a), the emptied data folder, then the modern defaults
 * ({@link ModernProjectDefaults#applyToNewProject}: target 37, Material 3, AppCompat on ...), and after them the
 * values chosen on the screen, which replace those defaults, and the template's views and blocks.
 */
public final class NewProjectSetup {

    public static final class Choices {
        public String appName = "";
        public String packageName = "";
        public String projectName = "";
        public String versionCode = "1";
        public String versionName = "1.0";
        public boolean customIcon;
        /** accent, primary, primary dark, control highlight, control normal (see {@link ThemePresets}). */
        public int[] colors = ThemePresets.PRESETS[0].colors.clone();
        public String preset = ProjectPresets.EMPTY;
        public int minSdk = ModernProjectDefaults.NEW_PROJECT_MIN_SDK;
        public int targetSdk = ModernProjectDefaults.NEW_PROJECT_TARGET_SDK;
        public String dexer = BuildSettings.SETTING_DEXER_D8;
        public String javaVersion = BuildSettings.SETTING_JAVA_VERSION_11;
        public boolean material3 = true;
        public boolean darkTheme = true;
        public boolean dynamicColor;
        /** The IDE's version number stored with the project ({@code GB.d(context)} on the phone). */
        public int sketchwareVersion = 150;
    }

    private NewProjectSetup() {
    }

    public static void create(Context context, String scId, Choices choices) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("sc_id", scId);
        data.put("my_sc_pkg_name", choices.packageName);
        data.put("my_ws_name", choices.projectName);
        data.put("my_app_name", choices.appName);
        data.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
        data.put("custom_icon", choices.customIcon);
        data.put("sc_ver_code", choices.versionCode);
        data.put("sc_ver_name", choices.versionName);
        data.put("sketchware_ver", choices.sketchwareVersion);
        for (int i = 0; i < ThemePresets.KEYS.length; i++) {
            data.put(ThemePresets.KEYS[i], choices.colors[i]);
        }
        lC.a(scId, data);
        wq.a(context, scId);
        new oB().b(wq.b(scId));
        ModernProjectDefaults.applyToNewProject(scId);
        apply(scId, choices);
    }

    /** The screen's settings and template, written over the defaults of a project that was just created. */
    static void apply(String scId, Choices choices) {
        String yes = ProjectSettings.SETTING_GENERIC_VALUE_TRUE;
        String no = ProjectSettings.SETTING_GENERIC_VALUE_FALSE;
        ProjectSettings project = new ProjectSettings(scId);
        project.putValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(choices.minSdk));
        project.putValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(choices.targetSdk));
        project.putValue(ModernProjectDefaults.SETTING_MATERIAL3, choices.material3 ? yes : no);
        project.putValue(ModernProjectDefaults.SETTING_M3_DARK_THEME, choices.material3 && choices.darkTheme ? yes : no);
        project.putValue(ModernProjectDefaults.SETTING_M3_DYNAMIC_COLOR, choices.material3 && choices.dynamicColor ? yes : no);
        project.saveNow();

        BuildSettings build = new BuildSettings(scId);
        build.putValue(BuildSettings.SETTING_DEXER, choices.dexer);
        // Dx reads Java 7 class files only: the legacy engine always compiles at 1.7
        build.putValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_DEXER_DX.equals(choices.dexer)
                ? BuildSettings.SETTING_JAVA_VERSION_1_7 : choices.javaVersion);
        build.saveNow();

        ProjectPresets.write(scId, choices.preset, choices.appName, choices.colors[1]);
    }
}
