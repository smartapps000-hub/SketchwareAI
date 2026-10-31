package com.sketchware.ai.editor;

import android.content.Context;

import com.besome.sketch.beans.ProjectFileBean;

import java.io.File;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.yq;
import mod.agus.jcoderz.lib.FileUtil;

/**
 * One activity opened in the Java editor (v1.8.8): its generated code and the map that ties the code back to events
 * and blocks. {@link #save} stores an edited version as blocks ({@link JavaToBlocks}) in the project data it was
 * opened with (the design editor's live data), then the editor shows the code generated from them again.
 */
public final class JavaEditSession {

    public final String scId;
    public final String javaName;
    public ProjectFileBean file;
    /** The code shown: the normal generation (what a build writes, command blocks aside). */
    public String code;
    /** Why edits can't be turned into blocks (the code is still shown), or null. */
    public String problem;
    /** The activity is replaced by a Java file of the project's own (Java Manager): that file is edited directly. */
    public File ownFile;

    private final Context context;
    private final hC files;
    private final eC data;
    private final iC libraries;
    private EditMap map;
    /** The generator the code was made with (its build config is what Fx needs to write single blocks). */
    private yq generatorUsed;

    private JavaEditSession(Context context, String scId, String javaName, hC files, eC data, iC libraries) {
        this.context = context;
        this.scId = scId;
        this.javaName = javaName;
        this.files = files;
        this.data = data;
        this.libraries = libraries;
    }

    public static JavaEditSession open(Context context, String scId, String javaName, hC files, eC data, iC libraries) {
        JavaEditSession session = new JavaEditSession(context, scId, javaName, files, data, libraries);
        session.load();
        return session;
    }

    private void load() {
        file = null;
        for (ProjectFileBean bean : files.b()) {
            if (javaName.equals(bean.getJavaName())) file = bean;
        }
        if (file == null) {
            problem = javaName + " isn't one of the project's activities.";
            code = "";
            return;
        }
        File own = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/files/java/" + javaName);
        if (own.isFile()) {
            ownFile = own;
            code = FileUtil.readFile(own.getAbsolutePath());
            problem = null;
            return;
        }
        yq generator = new yq(context, scId);
        generatorUsed = generator;
        String written = generator.getFileSrc(javaName, files, data, libraries);
        String formatted = new a.a.a.Jx(generator.N, file, data).formattedCode();
        code = formatted;
        if (!sameLines(written, formatted)) {
            problem = "command blocks rewrite this file when it is built; change those blocks in the logic editor";
            map = null;
            return;
        }
        map = EditMap.generate(generator.N, file, data, formatted);
        problem = map.problem;
    }

    /** v1.8.9: maps single statements to the blocks that write them (verified with the IDE's own generator). */
    /** Java packages of the project's enabled local libraries (v1.8.9 automatic imports, LibraryImports). */
    private java.util.Set<String> libraryPackages() {
        java.util.Set<String> packages = new java.util.HashSet<>();
        try {
            java.util.ArrayList<java.util.HashMap<String, Object>> list = new mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary(scId).list;
            if (list == null) return packages;
            for (java.util.HashMap<String, Object> library : list) {
                Object jar = library.get("jarPath");
                if (jar instanceof String) packages.addAll(com.sketchware.ai.library.LibraryImports.packages(new java.io.File((String) jar)).keySet());
            }
        } catch (RuntimeException ignored) {
            // none known: an automatic import that went away is reported as missing
        }
        return packages;
    }

    StatementBlocks statementBlocks() {
        if (generatorUsed == null || file == null) return null;
        final a.a.a.jq buildConfig = generatorUsed.N;
        final String activityName = file.getActivityName();
        java.util.List<com.besome.sketch.beans.ViewBean> views = new java.util.ArrayList<>();
        try {
            java.util.List<com.besome.sketch.beans.ViewBean> main = data.d(file.getXmlName());
            if (main != null) views.addAll(main);
            String drawer = file.getDrawerXmlName();
            java.util.List<com.besome.sketch.beans.ViewBean> drawerViews = drawer == null || drawer.isEmpty() ? null : data.d(drawer);
            if (drawerViews != null) views.addAll(drawerViews);
        } catch (Throwable ignored) {
            // no views: view blocks aren't offered
        }
        java.util.Set<String> activities = new java.util.HashSet<>();
        for (ProjectFileBean bean : files.b()) {
            String name = bean.getActivityName();
            if (name != null && !name.isEmpty()) activities.add(name);
        }
        java.util.List<com.besome.sketch.beans.ComponentBean> components = null;
        try {
            components = data.e(javaName);
        } catch (Throwable ignored) {
            // none
        }
        // v1.8.13: setHint on an outlined Material 3 field is written as SketchTextField.hint(…), as the build writes it
        final java.util.Set<String> m3Fields = materialTextFields();
        return new StatementBlocks(
                blocks -> com.sketchware.ai.project.M3Widgets.routeHints(new a.a.a.Fx(activityName, buildConfig, "", blocks).a(), m3Fields),
                paletteSpecs(context),
                views, components, data.e.get(javaName), activities);
    }

    /** v1.8.13: the EditTexts the generator writes setHint for as SketchTextField.hint (Material 3 projects). */
    private java.util.Set<String> materialTextFields() {
        try {
            return new a.a.a.Jx(generatorUsed.N, file, data).materialTextFields();
        } catch (Throwable t) {
            return new java.util.HashSet<>();
        }
    }

    /** The palette's spec for an opcode (localized like the palette's), the English one when that can't be read. */
    static StatementBlocks.Specs paletteSpecs(final Context context) {
        return opCode -> {
            String english = StatementBlocks.ENGLISH.get(opCode);
            try {
                java.util.ArrayList<String> params = a.a.a.lq.a(opCode);
                String spec = a.a.a.xB.b().a(context, "block_" + a.a.a.lq.d(opCode), params == null ? new java.util.ArrayList<String>() : params);
                if (spec == null || spec.trim().isEmpty()) return english;
                // the same parameter slots in the same order, or it isn't the block's spec
                if (params != null) {
                    int at = 0;
                    for (String p : params) {
                        int found = spec.indexOf(p, at);
                        if (found < 0) return english;
                        at = found + p.length();
                    }
                }
                return spec;
            } catch (Throwable t) {
                return english;
            }
        };
    }

    /** The two texts differ in nothing but trailing line breaks (what command-block processing does to every file). */
    static boolean sameLines(String a, String b) {
        int x = a.length(), y = b.length();
        while (x > 0 && (a.charAt(x - 1) == '\n' || a.charAt(x - 1) == '\r')) x--;
        while (y > 0 && (b.charAt(y - 1) == '\n' || b.charAt(y - 1) == '\r')) y--;
        return x == y && a.regionMatches(0, b, 0, x);
    }

    /** Whether saving converts to blocks (otherwise only {@link #ownFile} can be written, or nothing). */
    public boolean convertible() {
        return ownFile == null && map != null && map.problem == null && problem == null;
    }

    /**
     * Stores {@code edited}. For an activity of the project's own file the file is written as it is; otherwise the code
     * becomes blocks, and on success {@link #code} is the code generated from them.
     */
    public JavaToBlocks.Result save(String edited) {
        if (ownFile != null) {
            JavaToBlocks.Result result = new JavaToBlocks.Result();
            FileUtil.writeFile(ownFile.getAbsolutePath(), edited);
            code = edited;
            result.ok = true;
            result.changes.add(ownFile.getName() + " saved");
            return result;
        }
        if (!convertible()) {
            JavaToBlocks.Result result = new JavaToBlocks.Result();
            result.problems.add(new JavaToBlocks.Problem(0, "This activity can't be turned back into blocks: " + problem + "."));
            return result;
        }
        JavaToBlocks converter = new JavaToBlocks(data, file, code, map,
                () -> new yq(context, scId).getFileSrc(javaName, files, data, libraries));
        converter.useStatementBlocks(statementBlocks());
        converter.useLibraryPackages(libraryPackages());
        // v1.8.13: edittext1.setHint(…) typed in a Material 3 project is what the generator writes as
        // SketchTextField.hint(edittext1, …) (M3Widgets.routeHints): compared the same way, it is no longer refused.
        // Add-source blocks store it as typed (setHint), so they still build when Material 3 is switched off.
        final java.util.Set<String> m3Fields = materialTextFields();
        converter.useStoredText(text -> com.sketchware.ai.project.M3Widgets.unrouteHints(text, m3Fields));
        JavaToBlocks.Result result = converter.save(com.sketchware.ai.project.M3Widgets.routeHints(edited, m3Fields));
        if (result.ok && !result.unchanged) {
            try {
                load();
            } catch (Throwable t) {
                // stored; only showing the new code failed: keep the edited text as the starting point
                code = edited;
                map = null;
                problem = "the code couldn't be generated again after saving (" + t + "); reopen the editor";
                result.notes.add("Saved, but the code couldn't be generated again: reopen the editor before changing more.");
            }
        }
        return result;
    }
}
