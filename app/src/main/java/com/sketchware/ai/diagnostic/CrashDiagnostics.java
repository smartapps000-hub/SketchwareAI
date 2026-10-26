package com.sketchware.ai.diagnostic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline explanation of an app crash (uncaught exception) for the crash report screen:
 * the root cause in one line and what it means in plain words. Pure Java, no Android types,
 * so the crash screen can't fail because of it.
 */
public final class CrashDiagnostics {

    private CrashDiagnostics() {
    }

    public static final class Result {
        /** Root cause, e.g. "NoClassDefFoundError: androidx.core.graphics.TypefaceCompat". */
        public final String cause;
        /** Where it happened in the app's own code, e.g. "ProjectFileSelector.java:174", or null. */
        public final String where;
        public final String advice;

        Result(String cause, String where, String advice) {
            this.cause = cause;
            this.where = where;
            this.advice = advice;
        }
    }

    private static final Pattern THROWABLE = Pattern.compile("^(?:Caused by: )?([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?$");
    private static final Pattern FRAME = Pattern.compile("^at ((?:com\\.besome|com\\.sketchware|mod\\.|a\\.a\\.a|dev\\.aldi)[\\w.$]*)\\.[\\w$<>]+\\(([\\w$]+\\.java:\\d+)\\)");

    public static Result explain(String trace) {
        if (trace == null || trace.trim().isEmpty()) {
            return new Result("No stack trace was saved.", null,
                    "Sketchware AI closed unexpectedly. If it keeps happening, note what you tapped just before.");
        }
        String rootType = null, rootMessage = null, where = null, blockWhere = null;
        boolean afterRoot = false;
        for (String raw : trace.split("\n")) {
            String line = raw.trim();
            Matcher t = THROWABLE.matcher(line);
            if (t.find()) {
                rootType = t.group(1);
                rootMessage = t.group(2) == null ? "" : t.group(2).trim();
                afterRoot = true;
                blockWhere = null;
                continue;
            }
            if (afterRoot && blockWhere == null) {
                Matcher f = FRAME.matcher(line);
                if (f.find()) {
                    // The deepest "Caused by" block that runs through the app's own code.
                    blockWhere = f.group(2);
                    where = blockWhere;
                }
            }
        }
        if (rootType == null) {
            String first = trace.trim().split("\n")[0];
            return new Result(shorten(first), null, generic());
        }
        String simple = rootType.substring(rootType.lastIndexOf('.') + 1);
        int onPath = rootMessage.indexOf(" on path:");
        if (onPath > 0) rootMessage = rootMessage.substring(0, onPath);
        String cause = simple + (rootMessage.isEmpty() ? "" : ": " + shorten(rootMessage));
        return new Result(cause, where, advice(simple, rootMessage, trace));
    }

    /**
     * v1.8.11: crashes whose cause is known and fixed (engineering/known-errors.json): matched on the whole report, so
     * the screen says what it was and what to do instead of the generic text. Rules, not guesses: no match, no claim.
     */
    private static final String[][] KNOWN = {
            {"(?s)ClassCastException: android\\.widget\\.FrameLayout\\$LayoutParams cannot be cast to android\\.widget\\.LinearLayout\\$LayoutParams.*at android\\.widget\\.LinearLayout\\.",
                    "Known cause (fixed in v1.8.10): a card or layout of a template project was stored with the wrong parent "
                            + "type, and the design canvas measured it as a LinearLayout child. The project isn't damaged: "
                            + "with v1.8.10 or newer the canvas converts the layout values and opens it."},
            {"(?s)WindowManager\\$BadTokenException|Unable to add window.*is your activity running",
                    "Known cause (guarded since v1.8.11): a dialog was opened for a screen that was already closing, e.g. the "
                            + "ID dialog of the property bar right after leaving the editor. Open the screen again and select "
                            + "the widget before changing its ID. If it still happens, copy this report."},
            {"(?s)NullPointerException.*at com\\.besome\\.sketch\\.editor\\.property\\.PropertyInputItem\\.",
                    "Known cause (guarded since v1.8.11): a property dialog (ID, text, …) opened before the widget's project data "
                            + "was loaded. Select the widget on the canvas again, then change the property. If it still "
                            + "happens, copy this report."},
            {"(?s)Found invalid data of Custom Variable",
                    "Known cause (fixed in v1.8.11): a statement was saved as a custom variable by Java to blocks. Open Edit "
                            + "Java code, delete that line from the class body and tap Save as blocks to remove it."},
    };

    private static String known(String trace) {
        for (String[] rule : KNOWN) {
            if (Pattern.compile(rule[0]).matcher(trace).find()) return rule[1];
        }
        return null;
    }

    private static String advice(String type, String message, String trace) {
        String knownCause = known(trace);
        if (knownCause != null) return knownCause;
        String missing = null;
        Matcher m = Pattern.compile("(?:Failed resolution of: L|Didn't find class \")([\\w/$.]+)").matcher(trace);
        if (m.find()) missing = m.group(1).replace('/', '.').replace(";", "");
        if (type.equals("NoClassDefFoundError") || type.equals("ClassNotFoundException")) {
            return "Part of Sketchware AI" + (missing == null ? "" : " (" + missing + ")")
                    + " is missing from this build. This is a bug in the app, not in your project. "
                    + "Copy or share this report so it can be fixed.";
        }
        if (type.equals("OutOfMemoryError")) {
            return "The phone ran out of memory for Sketchware AI. Close other apps and try again; very large images in a project can also cause this.";
        }
        if (type.equals("SecurityException")) {
            return "Android refused a permission Sketchware AI needed. Check All files access (Android 11+) in the app's settings, then try again.";
        }
        if (type.equals("ActivityNotFoundException")) {
            return "No app on this phone can open what was requested. Install an app that handles it and try again.";
        }
        if (type.equals("InflateException") || trace.contains("InflateException")) {
            return "A screen couldn't be built. This is a bug in Sketchware AI's layouts. Copy or share this report so it can be fixed.";
        }
        if (type.equals("NullPointerException")) {
            return "Sketchware AI used something that wasn't loaded yet. If it happens every time on the same screen, copy or share this report.";
        }
        if (type.equals("FileNotFoundException") || type.equals("IOException")) {
            return "A file couldn't be read or written. Check free space and storage access, then try again.";
        }
        return generic();
    }

    private static String generic() {
        return "Sketchware AI hit an unexpected error. Copy or share this report so it can be fixed.";
    }

    private static String shorten(String text) {
        text = text.trim();
        return text.length() > 220 ? text.substring(0, 217) + "…" : text;
    }
}
