package com.sketchware.ai.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sketchware.ai.ui.M3CodeScheme;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

import a.a.a.jC;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.Magnifier;

/**
 * v1.9.0: the logic editor's "&lt;/&gt;" sheet. Shows the event's code in Material 3 dark colours
 * ({@link M3CodeScheme}); "Edit" makes it editable (type or paste Java), "Apply" turns it into the event's blocks
 * through the Java editor's save ({@link EventCode}) without leaving the logic editor. After an apply the logic editor
 * opens the event again, so its canvas shows the new blocks and the old canvas is never saved over them.
 */
public final class EventCodeSheet {

    /** What the logic editor does for the sheet. */
    public interface Host {
        /** Stores the canvas's blocks in the project data (what the logic editor does when it is left). */
        void storeBlocks();

        /** Opens the event again (the blocks in the project data changed) and shows {@code report}. */
        void reopen(String report);
    }

    /**
     * Above 0 while a sheet reads or changes the project data on its own thread (Edit's open, Apply): the logic editor
     * must not store its canvas then ({@code LogicEditorActivity.E()}, {@code onSaveInstanceState}), or it would race
     * the apply and could put the old blocks back over the new ones. Cleared on the UI thread when the result is
     * handled, so the applied blocks are in place before anything stores a canvas again.
     */
    private static final java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();

    private EventCodeSheet() {
    }

    public static boolean isRunning() {
        return running.get() > 0;
    }

    /** Whether the sheet's own dialog can still be touched (its activity not finishing or destroyed). */
    private static boolean alive(Activity activity, AlertDialog dialog) {
        return !activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing();
    }

    /** Close (and touching outside / Back) only when nothing runs. */
    private static void busy(AlertDialog dialog, Button close, boolean busy) {
        dialog.setCancelable(!busy);
        if (close != null) close.setEnabled(!busy);
    }

    /**
     * @param shownCode the event's code as the sheet always showed it (generated from the canvas's blocks)
     */
    public static void show(final Activity activity, final String scId, final String javaName, final String key,
                            String shownCode, final Host host) {
        final float density = activity.getResources().getDisplayMetrics().density;
        final CodeEditor editor = new CodeEditor(activity);
        editor.setColorScheme(new M3CodeScheme());
        editor.setEditorLanguage(new JavaLanguage());
        editor.setEditable(false);
        editor.setText(shownCode);
        editor.setTextSize(12);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setWordwrap(false);
        editor.getComponent(Magnifier.class).setWithinEditorForcibly(true);

        final TextView status = new TextView(activity);
        status.setTextColor(0xFFCAC4D0);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setSingleLine(false);
        status.setMaxLines(2);
        status.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (6 * density));
        status.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // a fixed share of the screen: the buttons below always stay on screen
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.62f);
        box.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

        final AlertDialog dialog = new ObAlertBuilder(activity)
                .setTitle("Source code")
                .setIcon(R.drawable.code_icon)
                .setPositiveButton(R.string.common_word_close, null)
                .setNeutralButton("Edit", null)
                .create();
        dialog.setView(box, (int) (12 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
        dialog.show();

        final Button action = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        final Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final EventCode[] opened = {null};
        final boolean[] busy = {false};
        if (action == null) return;
        action.setOnClickListener(v -> {
            if (busy[0]) return;
            if (opened[0] == null) {
                // Edit: the canvas's blocks go into the project data, then the activity is opened like the Java editor does
                busy[0] = true;
                action.setEnabled(false);
                say(status, "Preparing the code…");
                try {
                    host.storeBlocks();
                } catch (Throwable t) {
                    busy[0] = false;
                    action.setEnabled(true);
                    say(status, "Couldn't store the blocks: " + t);
                    return;
                }
                running.incrementAndGet();
                busy(dialog, close, true);
                new Thread(() -> {
                    EventCode code;
                    String failure = null;
                    try {
                        code = EventCode.open(activity.getApplicationContext(), scId, javaName, key,
                                jC.b(scId), jC.a(scId), jC.c(scId));
                    } catch (Throwable t) {
                        code = null;
                        failure = String.valueOf(t);
                    }
                    final EventCode result = code;
                    final String error = failure;
                    activity.runOnUiThread(() -> {
                        running.decrementAndGet();
                        if (!alive(activity, dialog)) return;
                        busy[0] = false;
                        busy(dialog, close, false);
                        if (result == null || !result.editable()) {
                            action.setVisibility(View.GONE);
                            say(status, "Read only: " + (result == null ? "the code couldn't be generated (" + error + ")" : result.problem) + ".");
                            return;
                        }
                        opened[0] = result;
                        editor.setText(result.body);
                        editor.setEditable(true);
                        allowKeyboard(dialog, editor);
                        action.setText("Apply");
                        action.setEnabled(true);
                        // v1.9.2: one short line, so the code keeps the room
                        say(status, "Edit or paste Java, then Apply.");
                    });
                }, "EventCodeOpen").start();
                return;
            }
            // Apply
            final String edited = editor.getText().toString();
            if (edited.equals(opened[0].body)) {
                Toast.makeText(activity, "No changes to apply", Toast.LENGTH_SHORT).show();
                return;
            }
            busy[0] = true;
            action.setEnabled(false);
            editor.setEditable(false);
            running.incrementAndGet();
            busy(dialog, close, true);
            say(status, "Turning the code into blocks…");
            final EventCode code = opened[0];
            new Thread(() -> {
                JavaToBlocks.Result result;
                try {
                    result = code.apply(edited);
                    if (result.ok && !result.unchanged) {
                        try {
                            jC.a(scId).k();   // the design editor's working copy, as the logic editor saves it
                        } catch (Throwable t) {
                            result.notes.add("Stored in the open project; writing its backup copy failed (" + t
                                    + "): save the project in the design editor.");
                        }
                    }
                } catch (Throwable t) {
                    result = new JavaToBlocks.Result();
                    result.problems.add(new JavaToBlocks.Problem(0, "Saving failed: " + t + ". Nothing was changed."));
                }
                final JavaToBlocks.Result done = result;
                activity.runOnUiThread(() -> {
                    running.decrementAndGet();
                    boolean shown = alive(activity, dialog);
                    if (done.ok && !done.unchanged) {
                        if (shown) dialog.dismiss();
                        // the host opens the event again; if this activity was recreated meanwhile (dark mode, window
                        // size), it reopens the instance that replaced it
                        host.reopen(report(done));
                        return;
                    }
                    if (!shown) {
                        if (!done.ok) {
                            Toast.makeText(activity.getApplicationContext(), "Source code not applied: nothing was changed",
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    busy[0] = false;
                    busy(dialog, close, false);
                    action.setEnabled(true);
                    editor.setEditable(true);
                    if (done.ok) {
                        say(status, "Nothing changed: the code is what the blocks already write.");
                        return;
                    }
                    say(status, "Not applied: nothing was changed.");
                    StringBuilder text = new StringBuilder();
                    int first = 0;
                    for (JavaToBlocks.Problem problem : done.problems) {
                        int line = code.sheetLine(problem.line);
                        if (first == 0 && line > 0) first = line;
                        text.append(line > 0 ? "Line " + line + ": " : problem.line > 0 ? "Line " + problem.line
                                + " of the activity's code: " : "").append(problem.message).append("\n\n");
                    }
                    final int jump = first;
                    ObAlertBuilder refused = new ObAlertBuilder(activity);
                    refused.setTitle("Not applied").setMessage(text.toString().trim()).setNegativeButton("Keep editing", null);
                    if (jump > 0) {
                        refused.setPositiveButton("Go to line " + jump, (d, w) -> {
                            try {
                                editor.jumpToLine(jump - 1);
                                editor.setSelectionRegion(jump - 1, 0, jump - 1, editor.getText().getColumnCount(jump - 1));
                            } catch (RuntimeException ignored) {
                                // the line is gone
                            }
                        });
                    }
                    refused.show();
                });
            }, "EventCodeApply").start();
        });
    }

    /**
     * The dialog was created while the editor was read only, so AlertDialog marked its window as not needing the
     * keyboard (FLAG_ALT_FOCUSABLE_IM: no text editor in it then); now that the editor takes typing, the keyboard must
     * be able to open.
     */
    private static void allowKeyboard(AlertDialog dialog, CodeEditor editor) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        editor.setFocusable(true);
        editor.setFocusableInTouchMode(true);
        editor.requestFocus();
        // v1.9.2: the keyboard opens with Edit (it took a second tap into the code before)
        editor.post(() -> {
            android.view.inputmethod.InputMethodManager input = (android.view.inputmethod.InputMethodManager)
                    editor.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (input != null) input.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** The changes and notes of an apply, one per line, for the reopened logic editor. */
    static String report(JavaToBlocks.Result result) {
        StringBuilder text = new StringBuilder();
        for (String change : result.changes) text.append("• ").append(change).append('\n');
        if (!result.notes.isEmpty()) {
            text.append('\n');
            for (String note : result.notes) text.append(note).append('\n');
        }
        return text.toString().trim();
    }

    private static void say(TextView status, String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }
}
