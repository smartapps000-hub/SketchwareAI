package com.sketchware.ai.project;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import a.a.a.yB;

/**
 * v2.0.5 (owner's 16 Nov directive "dedicated Demo category, latest full-fledged demo version first, older versions in
 * one horizontal row, made to grow as more component demos are added"): which projects are demos and in what order.
 * A demo carries {@code data/<sc_id>/demo.json} ({"demo": "github", "version": n}); the GitHub API Demo made by v2.0.3 /
 * v2.0.4 (no mark) is known by its package and counts as version 1. Newest demo version first, then the newest project.
 */
public final class Demos {

    public static final String MARK = "demo.json";

    private Demos() {
    }

    private static File mark(String scId) {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data/" + scId + "/" + MARK);
    }

    public static boolean isDemo(HashMap<String, Object> project) {
        String scId = yB.c(project, "sc_id");
        return GitHubSample.PACKAGE.equals(yB.c(project, "my_sc_pkg_name")) || (scId.matches("\\d+") && mark(scId).isFile());
    }

    /** The demo's version (1 for one without a mark). */
    public static int version(String scId) {
        File f = mark(scId);
        if (!f.isFile() || f.length() > 4096) return 1;
        try {
            JsonObject o = com.google.gson.JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("version") ? o.get("version").getAsInt() : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    /** Marks a project as a demo of {@code kind}, version {@code version}. */
    public static void mark(String scId, String kind, int version) {
        JsonObject o = new JsonObject();
        o.addProperty("demo", kind);
        o.addProperty("version", version);
        mod.agus.jcoderz.lib.FileUtil.writeFile(mark(scId).getAbsolutePath(), o.toString());
    }

    /** Newest demo version first, then the newest project (the higher id). */
    public static void sort(List<HashMap<String, Object>> demos) {
        Collections.sort(demos, (a, b) -> {
            String x = yB.c(a, "sc_id");
            String y = yB.c(b, "sc_id");
            int vx = x.matches("\\d+") ? version(x) : 1;
            int vy = y.matches("\\d+") ? version(y) : 1;
            if (vx != vy) return Integer.compare(vy, vx);
            return Long.compare(number(y), number(x));
        });
    }

    private static long number(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
