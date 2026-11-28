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
 * v2.0.4 (no mark) is known by its package and its name "GitHub_API_Demo" and counts as version 1. Newest demo version
 * first, then the newest project.
 * <p>
 * Review (v2.0.5): the mark names its own project id ({@code "sc_id"}): a copy or a restore of a demo (the data folder
 * travels in .swb files and project copies) is the user's own project again and stays in the list.
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
        if (!scId.matches("\\d+")) return false;
        JsonObject m = readMark(scId);
        if (m != null) {
            // second review (MEDIUM): a mark from someone else's backup may hold anything: never throws
            com.google.gson.JsonElement id = m.get("sc_id");
            return id != null && id.isJsonPrimitive() && scId.equals(id.getAsString());
        }
        // made by v2.0.3 / v2.0.4 (no mark): the sample's package and name
        return GitHubSample.PACKAGE.equals(yB.c(project, "my_sc_pkg_name"))
                && GitHubSample.PROJECT_NAME.equals(yB.c(project, "my_ws_name"));
    }

    private static JsonObject readMark(String scId) {
        File f = mark(scId);
        if (!f.isFile() || f.length() > 4096) return null;
        try {
            return com.google.gson.JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** The demo's version (1 for one without a mark). */
    public static int version(String scId) {
        JsonObject o = readMark(scId);
        try {
            com.google.gson.JsonElement v = o == null ? null : o.get("version");
            return v != null && v.isJsonPrimitive() ? v.getAsInt() : 1;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Marks a project as a demo of {@code kind}, version {@code version}. */
    public static void mark(String scId, String kind, int version) {
        JsonObject o = new JsonObject();
        o.addProperty("demo", kind);
        o.addProperty("version", version);
        o.addProperty("sc_id", scId);
        mod.agus.jcoderz.lib.FileUtil.writeFile(mark(scId).getAbsolutePath(), o.toString());
    }

    /** The demo's kind ("github" for one without a mark or kind). */
    public static String kind(String scId) {
        JsonObject o = readMark(scId);
        try {
            com.google.gson.JsonElement k = o == null ? null : o.get("demo");
            return k != null && k.isJsonPrimitive() ? k.getAsString() : "github";
        } catch (RuntimeException e) {
            return "github";
        }
    }

    /** v2.1.6: the order of the kinds in the Demo tab (the GitHub + JSON Live Demo first, then the GitHub API Demo). */
    static int rank(String kind) {
        if (JsonSample.KIND.equals(kind)) return 0;
        if ("github".equals(kind)) return 1;
        return 2;
    }

    /**
     * v2.1.6: by kind ({@link #rank}: the GitHub + JSON Live Demo first), then the newest demo version, then the newest
     * project (the higher id). Each mark is read once.
     */
    public static void sort(List<HashMap<String, Object>> demos) {
        HashMap<String, int[]> keys = new HashMap<>();
        for (HashMap<String, Object> p : demos) {
            String id = yB.c(p, "sc_id");
            if (keys.containsKey(id)) continue;
            boolean numeric = id.matches("\\d+");
            keys.put(id, new int[]{numeric ? rank(kind(id)) : 1, numeric ? version(id) : 1});
        }
        Collections.sort(demos, (a, b) -> {
            String x = yB.c(a, "sc_id");
            String y = yB.c(b, "sc_id");
            int[] kx = keys.get(x);
            int[] ky = keys.get(y);
            if (kx[0] != ky[0]) return Integer.compare(kx[0], ky[0]);
            if (kx[1] != ky[1]) return Integer.compare(ky[1], kx[1]);
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
