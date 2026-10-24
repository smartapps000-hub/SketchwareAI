package com.sketchware.ai.library;

import com.google.gson.Gson;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import mod.agus.jcoderz.lib.FileUtil;

/**
 * What a local library installed by the Library Resolver needs (v1.8.8), in needs.json next to its classes.jar:
 * its Maven coordinate, the other libraries it needs (installed as local libraries of their own) and the built-in
 * libraries it needs (BuiltInLibraries constant names, e.g. "GSON"). A build adds those built-in libraries for every
 * enabled local library ({@link #addBuiltIns}), so Retrofit gets the built-in OkHttp and Gson even when no block of
 * the project uses them.
 */
public final class LibraryNeeds {

    public static final String FILE_NAME = "needs.json";

    public String coordinate;
    public List<String> requires = new ArrayList<>();
    public List<String> builtIn = new ArrayList<>();

    private LibraryNeeds() {
    }

    public static LibraryNeeds create(String coordinate) {
        LibraryNeeds needs = new LibraryNeeds();
        needs.coordinate = coordinate;
        return needs;
    }

    public static LibraryNeeds read(File libraryDirectory) {
        File file = new File(libraryDirectory, FILE_NAME);
        if (!file.isFile()) return null;
        try {
            LibraryNeeds needs = new Gson().fromJson(new String(MavenResolver.read(file), StandardCharsets.UTF_8), LibraryNeeds.class);
            if (needs != null) {
                if (needs.requires == null) needs.requires = new ArrayList<>();
                if (needs.builtIn == null) needs.builtIn = new ArrayList<>();
            }
            return needs;
        } catch (Exception e) {
            return null;
        }
    }

    public void write(File libraryDirectory) {
        MavenResolver.write(new File(libraryDirectory, FILE_NAME), new Gson().toJson(this).getBytes(StandardCharsets.UTF_8));
    }

    /** Built-in library names (BuiltInLibraries field values) that the project's enabled local libraries need. */
    public static Set<String> builtInsFor(String scId) {
        Set<String> names = new LinkedHashSet<>();
        File list = new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + scId + "/local_library");
        if (!list.isFile()) return names;
        List<HashMap<String, Object>> libraries;
        try {
            libraries = new Gson().fromJson(FileUtil.readFile(list.getAbsolutePath()), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
        } catch (RuntimeException e) {
            return names;
        }
        if (libraries == null) return names;
        File root = com.sketchware.ai.project.LibraryHeal.localLibs();
        for (HashMap<String, Object> library : libraries) {
            Object name = library.get("name");
            if (name == null) continue;
            LibraryNeeds needs = read(new File(root, String.valueOf(name)));
            if (needs == null) continue;
            for (String constant : needs.builtIn) {
                String value = builtInValue(constant);
                if (value != null) names.add(value);
            }
        }
        return names;
    }

    /** "GSON" → mod.jbk.build.BuiltInLibraries.GSON ("gson-2.14.0"), or null when this build has no such library. */
    static String builtInValue(String constant) {
        try {
            Field field = mod.jbk.build.BuiltInLibraries.class.getField(constant);
            Object value = field.get(null);
            return value instanceof String ? (String) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Export to Android Studio: Maven coordinates (the built-in versions) of the built-in libraries the enabled local
     * libraries need, for app/build.gradle.
     */
    public static Set<String> exportCoordinates(String scId) {
        Set<String> coordinates = new LinkedHashSet<>();
        File list = new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + scId + "/local_library");
        if (!list.isFile()) return coordinates;
        try {
            List<HashMap<String, Object>> libraries = new Gson().fromJson(FileUtil.readFile(list.getAbsolutePath()), mod.hey.studios.util.Helper.TYPE_MAP_LIST);
            if (libraries == null) return coordinates;
            File root = com.sketchware.ai.project.LibraryHeal.localLibs();
            for (HashMap<String, Object> library : libraries) {
                LibraryNeeds needs = library.get("name") == null ? null : read(new File(root, String.valueOf(library.get("name"))));
                if (needs == null) continue;
                for (String constant : needs.builtIn) {
                    String coordinate = BuiltInCoordinates.coordinateOf(constant);
                    if (coordinate != null) coordinates.add(coordinate);
                }
            }
        } catch (RuntimeException ignored) {
            // nothing extra
        }
        return coordinates;
    }

    /** Called by the build (Dp) after it picked the built-in libraries the project uses. */
    public static void addBuiltIns(String scId, a.a.a.Kp manager) {
        try {
            for (String name : builtInsFor(scId)) manager.a(name);
        } catch (RuntimeException ignored) {
            // the build goes on with what it has
        }
    }
}
