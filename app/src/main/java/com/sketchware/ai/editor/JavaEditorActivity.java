package com.sketchware.ai.editor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import a.a.a.jC;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import mod.hey.studios.code.SrcCodeEditor;

/**
 * Java editor (v1.8.8): one activity's generated Java code, editable with highlighting and completion. Save turns
 * the changes back into the project's blocks ({@link JavaEditSession}, {@link JavaToBlocks}) and shows the code
 * generated from them; what can't be stored as blocks is listed with its line and nothing is changed. An activity
 * replaced by a Java file of its own (Java Manager) is that file, saved as it is.
 * <p>
 * Started from the design editor with extras sc_id and java (e.g. "MainActivity.java"); RESULT_OK when blocks changed.
 */
public class JavaEditorActivity extends BaseAppCompatActivity {

    public static Intent intent(Context context, String scId, String javaName) {
        Intent intent = new Intent(context, JavaEditorActivity.class);
        intent.putExtra("sc_id", scId);
        intent.putExtra("java", javaName);
        return intent;
    }

    private String scId;
    private String javaName;
    private JavaEditSession session;
    private CodeEditor editor;
    private TextView banner;
    private View progress;
    private View save;
    /** The text the editor was last given (the generated code, or the file). */
    private String baseline = "";
    private boolean busy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_java_editor);
        ObsidianWindow.apply(this);
        if (!j()) {
            finish();
            return;
        }
        scId = getIntent().getStringExtra("sc_id");
        javaName = getIntent().getStringExtra("java");
        ((TextView) findViewById(R.id.je_title)).setText(javaName);
        ((TextView) findViewById(R.id.je_subtitle)).setText("Generating the code…");
        banner = findViewById(R.id.je_banner);
        progress = findViewById(R.id.je_progress);
        save = findViewById(R.id.je_save);

        editor = new CodeEditor(this);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setEditorLanguage(new JavaLanguage());
        SrcCodeEditor.loadCESettings(this, editor, "act");
        editor.setPinLineNumber(true);
        editor.setVisibility(View.INVISIBLE);
        ((FrameLayout) findViewById(R.id.je_editor_box)).addView(editor, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        findViewById(R.id.je_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.je_undo).setOnClickListener(v -> {
            if (editor.canUndo()) editor.undo();
        });
        findViewById(R.id.je_redo).setOnClickListener(v -> {
            if (editor.canRedo()) editor.redo();
        });
        findViewById(R.id.je_reset).setOnClickListener(v -> {
            if (busy || !changed()) return;
            new ObAlertBuilder(this)
                    .setTitle("Reset")
                    .setMessage("Undo every change since the last save?")
                    .setPositiveButton("Reset", (d, w) -> show(baseline))
                    .setNegativeButton("Keep editing", null)
                    .show();
        });
        save.setOnClickListener(v -> save());
        load();
    }

    private void load() {
        busy = true;
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            JavaEditSession opened;
            String failure = null;
            try {
                opened = JavaEditSession.open(getApplicationContext(), scId, javaName, jC.b(scId), jC.a(scId), jC.c(scId));
            } catch (Throwable t) {
                opened = null;
                failure = String.valueOf(t);
            }
            final JavaEditSession result = opened;
            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                busy = false;
                progress.setVisibility(View.GONE);
                if (result == null) {
                    ((TextView) findViewById(R.id.je_subtitle)).setText("Couldn't generate the code");
                    showBanner("Couldn't generate the code: " + error);
                    save.setVisibility(View.GONE);
                    return;
                }
                session = result;
                TextView subtitle = findViewById(R.id.je_subtitle);
                if (session.ownFile != null) {
                    subtitle.setText("Your own Java file (Java Manager)");
                    showBanner("This activity uses your own " + javaName + " (Java Manager) instead of the blocks: "
                            + "Save writes that file.");
                    ((TextView) save).setText("Save file");
                } else if (session.convertible()) {
                    subtitle.setText("Save turns your changes into blocks");
                    banner.setVisibility(View.GONE);
                } else {
                    subtitle.setText("Read only");
                    showBanner("Changes here can't become blocks: " + session.problem + ".");
                    editor.setEditable(false);
                    save.setVisibility(View.GONE);
                    findViewById(R.id.je_reset).setVisibility(View.GONE);
                }
                show(session.code);
                editor.setVisibility(View.VISIBLE);
            });
        }, "JavaEditorLoad").start();
    }

    private void showBanner(String text) {
        banner.setText(text);
        banner.setVisibility(View.VISIBLE);
    }

    /** Puts {@code code} in the editor, keeping the line at the top of the screen where it was. */
    private void show(String code) {
        int line = 0;
        try {
            line = editor.getFirstVisibleLine();
        } catch (RuntimeException ignored) {
            // first time: nothing shown yet
        }
        baseline = code;
        editor.setText(code);
        final int keep = line;
        if (keep > 0) {
            editor.post(() -> {
                try {
                    editor.jumpToLine(Math.min(keep, editor.getLineCount() - 1));
                } catch (RuntimeException ignored) {
                    // stays at the top
                }
            });
        }
    }

    private boolean changed() {
        return editor != null && session != null && !editor.getText().toString().equals(baseline);
    }

    private void save() {
        if (busy || session == null) return;
        if (!changed()) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
            return;
        }
        final String edited = editor.getText().toString();
        busy = true;
        editor.setEditable(false);
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            JavaToBlocks.Result result;
            try {
                result = session.save(edited);
                if (result.ok && !result.unchanged && session.ownFile == null) {
                    // the design editor's working copy, as the logic editor saves it
                    try {
                        jC.a(scId).k();
                    } catch (Throwable t) {
                        result.notes.add("Stored in the open project; writing its backup copy failed (" + t + "): save the project in the design editor.");
                    }
                }
            } catch (Throwable t) {
                result = new JavaToBlocks.Result();
                result.problems.add(new JavaToBlocks.Problem(0, "Saving failed: " + t + ". Nothing was changed."));
            }
            final JavaToBlocks.Result done = result;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                busy = false;
                editor.setEditable(true);
                progress.setVisibility(View.GONE);
                if (done.ok) saved(done);
                else refused(done);
            });
        }, "JavaEditorSave").start();
    }

    private void saved(JavaToBlocks.Result result) {
        setResult(RESULT_OK);
        show(session.code);
        StringBuilder text = new StringBuilder();
        for (String change : result.changes) text.append("• ").append(change).append('\n');
        if (!result.notes.isEmpty()) {
            text.append('\n');
            for (String note : result.notes) text.append(note).append('\n');
        }
        new ObAlertBuilder(this)
                .setTitle(session.ownFile != null ? "Saved" : "Saved — converted to blocks")
                .setMessage(text.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    private void refused(JavaToBlocks.Result result) {
        StringBuilder text = new StringBuilder("Nothing was changed.\n\n");
        int firstLine = 0;
        for (JavaToBlocks.Problem problem : result.problems) {
            if (firstLine == 0 && problem.line > 0) firstLine = problem.line;
            text.append(problem.line > 0 ? "Line " + problem.line + ": " : "").append(problem.message).append("\n\n");
        }
        final int line = firstLine;
        ObAlertBuilder dialog = new ObAlertBuilder(this);
        dialog.setTitle("Not saved")
                .setMessage(text.toString().trim())
                .setNegativeButton("Keep editing", null);
        if (line > 0) {
            dialog.setPositiveButton("Go to line " + line, (d, w) -> {
                try {
                    editor.jumpToLine(line - 1);
                    editor.setSelectionRegion(line - 1, 0, line - 1, editor.getText().getColumnCount(line - 1));
                } catch (RuntimeException ignored) {
                    // the line is gone
                }
            });
        }
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if (changed()) {
            new ObAlertBuilder(this)
                    .setTitle("Unsaved changes")
                    .setMessage("Leave without saving your changes?")
                    .setPositiveButton("Leave", (d, w) -> finish())
                    .setNegativeButton("Keep editing", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }
}
