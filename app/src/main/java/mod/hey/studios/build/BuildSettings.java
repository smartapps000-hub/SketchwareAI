package mod.hey.studios.build;

import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.project.ProjectSettings;

public class BuildSettings extends ProjectSettings {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_INCREMENTAL_BUILD_ACTIVE = "incremental_build";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_1_9 = "1.9";
    public static final String SETTING_JAVA_VERSION_10 = "10";
    public static final String SETTING_JAVA_VERSION_11 = "11";
    // d8 only supports desugaring till java 11

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    /**
     * data/&lt;sc_id&gt;/build_config under /.sketchware, where the rest of the project is (and where a
     * restored backup puts it). Sketchware AI up to 1.8.1 kept this one file under /.sketchware_ai
     * by mistake; a file found there holds what the user last chose in this app, so it is moved
     * over once.
     */
    @Override
    public String getPath() {
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
        try {
            java.io.File misplaced = misplaced(sc_id);
            java.io.File target = new java.io.File(path);
            if (misplaced.isFile()) {
                // The newer of the two is what the user last chose.
                if (misplaced.length() > 0 && (!target.isFile() || misplaced.lastModified() > target.lastModified())) {
                    java.io.File temporary = new java.io.File(path + ".tmp");
                    FileUtil.copyFile(misplaced.getAbsolutePath(), temporary.getAbsolutePath());
                    if (temporary.length() == misplaced.length() && (!target.exists() || target.delete())
                            && temporary.renameTo(target)) {
                        misplaced.delete();
                    } else {
                        temporary.delete();
                    }
                } else {
                    misplaced.delete();
                }
            }
        } catch (RuntimeException e) {
            // The settings are read from where they are.
        }
        return path;
    }

    private static java.io.File misplaced(String sc_id) {
        return new java.io.File(FileUtil.getExternalStorageDir() + "/.sketchware_ai/data/" + sc_id + "/build_config");
    }

    /** For a project id that is being given to a new or restored project: an old id's leftover must not become its settings. */
    public static void forgetMisplaced(String sc_id) {
        try {
            misplaced(sc_id).delete();
        } catch (RuntimeException ignored) {
        }
    }
}
