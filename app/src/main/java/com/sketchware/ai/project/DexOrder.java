package com.sketchware.ai.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mod.agus.jcoderz.dex.ClassDef;
import mod.agus.jcoderz.dex.Dex;

/**
 * Order of a project's own DEX files when they are merged after MultiDex (min SDK below 21, v1.8.4).
 * <p>
 * D8 splits the project's classes into several DEX files once the R classes of all its libraries pass 64K fields
 * (v1.8.4 built-in libraries: four files for an AppCompat project), and the activities and the Application usually
 * land in the last file. Android 4.x loads only classes.dex before MultiDex.install(), so the files that hold code
 * (classes other than R / R$*) are merged first; files with only R classes follow. Same order otherwise (by name).
 */
public final class DexOrder {

    private DexOrder() {
    }

    public static List<File> codeFirst(List<String> paths) {
        List<File> code = new ArrayList<>();
        List<File> resourcesOnly = new ArrayList<>();
        List<String> sorted = new ArrayList<>(paths);
        java.util.Collections.sort(sorted, (a, b) -> {
            int na = number(a), nb = number(b);
            return na != nb ? Integer.compare(na, nb) : a.compareTo(b);
        });
        for (String path : sorted) {
            File file = new File(path);
            (holdsCode(file) ? code : resourcesOnly).add(file);
        }
        code.addAll(resourcesOnly);
        return code;
    }

    /** classes.dex = 1, classesN.dex = N, anything else after them. */
    private static int number(String path) {
        String name = new File(path).getName();
        if (name.equals("classes.dex")) return 1;
        if (name.startsWith("classes") && name.endsWith(".dex")) {
            try {
                return Integer.parseInt(name.substring(7, name.length() - 4));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    /** True when the DEX defines a class that is not an R class; an unreadable file counts as code (kept first). */
    static boolean holdsCode(File file) {
        try {
            Dex dex = new Dex(file);
            List<String> types = dex.typeNames();
            for (ClassDef classDef : dex.classDefs()) {
                String type = types.get(classDef.getTypeIndex());
                int slash = type.lastIndexOf('/');
                String simple = type.substring(slash + 1, type.length() - 1);
                if (!simple.equals("R") && !simple.startsWith("R$")) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
