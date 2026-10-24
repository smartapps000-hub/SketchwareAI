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
        JavaToBlocks.Result result = converter.save(edited);
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
