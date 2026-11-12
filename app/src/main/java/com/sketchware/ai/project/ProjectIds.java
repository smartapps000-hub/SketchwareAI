package com.sketchware.ai.project;

import android.os.Environment;

import java.io.File;

/**
 * v1.9.8: project ids for restored projects. {@code lC.b()} returns the highest id of the readable projects + 1; a
 * project whose list file is missing or unreadable (a restore cut off half way, a project deleted by hand) can still
 * have folders under that id, and a restore into it used to merge the backup into those leftovers. The id handed out
 * here has no folder anywhere under {@code /.sketchware}.
 */
public final class ProjectIds {

    private static final String[] FOLDERS = {"mysc/list", "data", "bak", "mysc", "resources/images", "resources/icons",
            "resources/fonts", "resources/sounds"};

    private ProjectIds() {
    }

    /** {@code candidate} (normally {@code lC.b()}) or the next number with no folder of its own. */
    public static String free(String candidate) {
        return free(new File(Environment.getExternalStorageDirectory(), ".sketchware"), candidate);
    }

    static String free(File sketchwareRoot, String candidate) {
        int id;
        try {
            id = Integer.parseInt(candidate.trim());
        } catch (RuntimeException e) {
            id = 601;
        }
        if (id < 601) id = 601;
        while (inUse(sketchwareRoot, id)) id++;
        return String.valueOf(id);
    }

    static boolean inUse(File sketchwareRoot, int id) {
        String name = String.valueOf(id);
        for (String folder : FOLDERS) {
            if (new File(new File(sketchwareRoot, folder), name).exists()) return true;
        }
        return false;
    }

    /** The highest numeric project folder in {@code mysc/list} + 1 (601 when there is none); names that aren't numbers are skipped. */
    public static String nextAfterList() {
        File list = new File(Environment.getExternalStorageDirectory(), ".sketchware/mysc/list");
        int max = 600;
        String[] names = list.list();
        if (names != null) {
            for (String name : names) {
                try {
                    max = Math.max(max, Integer.parseInt(name));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return free(String.valueOf(max + 1));
    }
}
