package com.besome.sketch.tools;

import static mod.SketchwareUtil.dpToPx;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sketchware.remod.R;

import mod.SketchwareUtil;
import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileErrorSaver;

public class CompileLogActivity extends Activity {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private TextView tv_compile_log;
    private HorizontalScrollView err_hScroll;
    private ScrollView err_vScroll;
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;
    private String scId;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compile_log);
        com.sketchware.ai.ui.ObsidianWindow.apply(this);

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        View rootLayout = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        ImageView back = findViewById(R.id.ig_toolbar_back);
        TextView title = findViewById(R.id.tx_toolbar_title);
        ImageView menu = findViewById(R.id.ig_toolbar_load_file);
        tv_compile_log = findViewById(R.id.tv_compile_log);
        err_hScroll = rootLayout.findViewWithTag("err_hScroll");
        err_vScroll = rootLayout.findViewWithTag("err_vScroll");

        back.setOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            title.setText("Last compile log");
        } else {
            title.setText("Compile log");
        }
        title.setSelected(true); // marquee when the title is longer than the header

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }
        scId = sc_id;

        compileErrorSaver = new CompileErrorSaver(sc_id);

        // Glass header pills (Copy, Share, AI); options live behind the "more" button.
        findViewById(R.id.ob_log_copy).setOnClickListener(v -> copyLog());
        findViewById(R.id.ob_log_share).setOnClickListener(v -> shareLog());
        findViewById(R.id.ob_log_ai).setOnClickListener(v -> showDiagnostic());
        menu.setVisibility(View.VISIBLE);

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";

        PopupMenu options = new PopupMenu(new android.view.ContextThemeWrapper(this, R.style.Ob_PopupTheme), menu);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);
        final String clearLabel = "Clear saved logs";
        if (compileErrorSaver.logFileExists()) {
            options.getMenu().add(clearLabel);
        }

        options.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel:
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                    break;

                case monospacedFontLabel:
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                    break;

                case fontSizeLabel:
                    changeFontSizeDialog();
                    break;

                case clearLabel:
                    if (compileErrorSaver.logFileExists()) {
                        compileErrorSaver.deleteSavedLogs();
                        getIntent().removeExtra("error");
                        SketchwareUtil.toast("Compile logs have been cleared.");
                    } else {
                        SketchwareUtil.toast("No compile logs found.");
                    }
                    setErrorText();
                    break;

                default:
                    return false;
            }

            return true;
        });

        menu.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = getIntent().getStringExtra("error");
        if (error == null) {
            error = compileErrorSaver.getLogsFromFile();
        }

        tv_compile_log.setText(CompileLogHelper.colorErrsAndWarnings(error));
        tv_compile_log.setTextIsSelectable(true);
    }

    private String logText() {
        return tv_compile_log.getText() == null ? "" : tv_compile_log.getText().toString();
    }

    private void copyLog() {
        String text = logText();
        if (text.trim().isEmpty()) {
            SketchwareUtil.toast("The compile log is empty.");
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Compile log", text));
        SketchwareUtil.toast("Compile log copied (" + text.length() + " characters)");
    }

    private void shareLog() {
        String text = logText();
        if (text.trim().isEmpty()) {
            SketchwareUtil.toast("The compile log is empty.");
            return;
        }
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_SUBJECT, "Sketchware AI compile log");
        send.putExtra(android.content.Intent.EXTRA_TEXT, text);
        startActivity(android.content.Intent.createChooser(send, "Share compile log"));
    }

    /**
     * Sketchware AI Diagnostic as a sliding liquid-glass bottom sheet: first error, advice for
     * every known pattern in the log, Copy log, and Build again (when opened from the editor).
     */
    private void showDiagnostic() {
        String log = logText();
        com.sketchware.ai.diagnostic.CompileLogDiagnostics.Result result =
                com.sketchware.ai.diagnostic.CompileLogDiagnostics.analyze(log);

        android.app.Dialog sheet = new android.app.Dialog(this, R.style.Ob_SheetDialog);
        View content = getLayoutInflater().inflate(R.layout.ob_diagnostic_sheet, null);
        sheet.setContentView(content);
        android.view.Window window = sheet.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams attributes = window.getAttributes();
                try {
                    // WindowManager.LayoutParams#setBlurBehindRadius (API 31; compileSdk is 30).
                    android.view.WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class)
                            .invoke(attributes, dpToPx(22));
                } catch (ReflectiveOperationException ignored) {
                }
                window.setAttributes(attributes);
            }
        }

        int found = result.advice.size();
        String subtitle;
        if (log.trim().isEmpty()) {
            subtitle = "Offline · the compile log is empty";
        } else {
            subtitle = "Offline · " + (found == 0 ? "no known pattern found"
                    : found + " known pattern" + (found == 1 ? "" : "s") + " found");
        }
        ((TextView) content.findViewById(R.id.ob_diag_subtitle)).setText(subtitle);

        if (result.firstError == null) {
            content.findViewById(R.id.ob_diag_first_box).setVisibility(View.GONE);
        } else {
            ((TextView) content.findViewById(R.id.ob_diag_first_error)).setText(result.firstError);
        }

        showProblems(content, sheet, log);

        StringBuilder advice = new StringBuilder();
        if (found == 0) {
            advice.append(log.trim().isEmpty() ? "There is nothing to diagnose yet."
                    : "No known pattern matched. Use Copy log or Share to send the full log for help.");
        } else {
            for (int i = 0; i < found; i++) {
                if (i > 0) advice.append("\n\n");
                if (found > 1) advice.append(i + 1).append(". ");
                advice.append(result.advice.get(i));
            }
        }
        ((TextView) content.findViewById(R.id.ob_diag_advice)).setText(advice);

        content.findViewById(R.id.ob_diag_copy).setOnClickListener(v -> copyLog());
        TextView primary = content.findViewById(R.id.ob_diag_primary);
        if (getIntent().getBooleanExtra(EXTRA_FROM_EDITOR, false)) {
            // Real rebuild: back to the design editor, which starts the same build as Run.
            primary.setText("Build again");
            primary.setOnClickListener(v -> {
                requestRebuild(scId);
                sheet.dismiss();
                finish();
            });
        } else {
            primary.setText("Done");
            primary.setOnClickListener(v -> sheet.dismiss());
        }
        sheet.show();
    }

    /**
     * One card per Java compiler error: where it is, and which block it was generated from when
     * the build's source map can vouch for that line. "Open block" and "View source" need the
     * project to be open, so they are only offered when this screen was opened from the editor.
     */
    private void showProblems(View content, android.app.Dialog sheet, String log) {
        java.util.List<com.sketchware.ai.sourcemap.ErrorOrigins.Item> problems;
        try {
            problems = com.sketchware.ai.sourcemap.ErrorOrigins.find(new java.io.File(a.a.a.wq.d(scId)), log);
        } catch (RuntimeException e) {
            return;
        }
        if (problems.isEmpty()) return;
        LinearLayout box = content.findViewById(R.id.ob_diag_problems);
        box.setVisibility(View.VISIBLE);
        content.findViewById(R.id.ob_diag_first_box).setVisibility(View.GONE);
        boolean fromEditor = getIntent().getBooleanExtra(EXTRA_FROM_EDITOR, false);
        int shown = 0;
        for (com.sketchware.ai.sourcemap.ErrorOrigins.Item problem : problems) {
            if (shown == 6) {
                TextView more = new TextView(this);
                more.setText((problems.size() - shown) + " more in the log, with their origins at its end.");
                more.setTextColor(getResources().getColor(R.color.ob_text_2));
                more.setTextSize(13);
                more.setPadding(0, 0, 0, dpToPx(10));
                box.addView(more);
                break;
            }
            View card = getLayoutInflater().inflate(R.layout.ob_diagnostic_problem, box, false);
            ((TextView) card.findViewById(R.id.ob_problem_where)).setText(problem.fileName + ":" + problem.line);
            ((TextView) card.findViewById(R.id.ob_problem_message)).setText(
                    problem.excerpt.isEmpty() ? problem.message : problem.message + "\n" + problem.excerpt);
            ((TextView) card.findViewById(R.id.ob_problem_origin)).setText(problem.originText());
            View open = card.findViewById(R.id.ob_problem_open);
            View source = card.findViewById(R.id.ob_problem_source);
            if (!fromEditor) {
                ((View) open.getParent()).setVisibility(View.GONE);
            } else {
                if (problem.origin == null) {
                    open.setVisibility(View.GONE);
                } else {
                    open.setOnClickListener(v -> {
                        if (openBlock(problem)) sheet.dismiss();
                    });
                }
                source.setOnClickListener(v -> {
                    android.content.Intent intent = new android.content.Intent(this, com.besome.sketch.common.SrcViewerActivity.class);
                    intent.putExtra("sc_id", scId);
                    intent.putExtra("current", problem.fileName);
                    intent.putExtra("path", problem.file.getPath());
                    intent.putExtra("line", problem.line);
                    startActivity(intent);
                });
            }
            box.addView(card);
            shown++;
        }
    }

    /** Opens the logic editor at the block the error line was generated from. */
    private boolean openBlock(com.sketchware.ai.sourcemap.ErrorOrigins.Item problem) {
        try {
            com.sketchware.ai.sourcemap.SourceMap.EventInfo event = problem.origin.event;
            com.sketchware.ai.sourcemap.SourceMap.Entry block = problem.origin.block;
            com.besome.sketch.beans.ProjectFileBean activity = null;
            for (com.besome.sketch.beans.ProjectFileBean file : a.a.a.jC.b(scId).b()) {
                if (problem.fileName.equals(file.getJavaName())) activity = file;
            }
            java.util.ArrayList<com.besome.sketch.beans.BlockBean> blocks = activity == null ? null
                    : a.a.a.jC.a(scId).a(activity.getJavaName(), event.targetId + "_" + event.eventName);
            boolean present = false;
            if (blocks != null && block != null) {
                for (com.besome.sketch.beans.BlockBean bean : blocks) {
                    if (block.blockId.equals(bean.id) && (block.opCode == null || block.opCode.equals(bean.opCode))) present = true;
                }
            }
            if (!present) {
                SketchwareUtil.toast("That block isn't in the project any more: the blocks changed since this build. Build again.");
                return false;
            }
            android.content.Intent intent = new android.content.Intent(this, com.besome.sketch.editor.LogicEditorActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("sc_id", scId);
            intent.putExtra("id", event.targetId);
            intent.putExtra("event", event.eventName);
            intent.putExtra("project_file", activity);
            // The same toolbar text the Events list gives the editor.
            intent.putExtra("event_text", "moreBlock".equals(event.eventName)
                    ? mod.hey.studios.moreblock.ReturnMoreblockManager.getMbTypeList(event.targetId)
                    : a.a.a.oq.a(event.eventName, this));
            intent.putExtra("focus_block_id", block.blockId);
            intent.putExtra("focus_block_op", block.opCode);
            intent.putExtra("focus_line", block.end > block.start ? problem.origin.lineInBlock : 0);
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            SketchwareUtil.toast("Couldn't open that block: " + e);
            return false;
        }
    }

    public static final String EXTRA_FROM_EDITOR = "from_design_editor";
    private static String pendingRebuildScId;
    private static long pendingRebuildAt;

    private static void requestRebuild(String scId) {
        pendingRebuildScId = scId;
        pendingRebuildAt = System.currentTimeMillis();
    }

    /** True once if "Build again" was tapped for this project in the last 15 seconds. */
    public static boolean consumeRebuildRequest(String scId) {
        boolean requested = scId != null && scId.equals(pendingRebuildScId)
                && System.currentTimeMillis() - pendingRebuildAt < 15_000;
        pendingRebuildScId = null;
        return requested;
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        tv_compile_log.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, true);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            err_vScroll.removeAllViews();
            if (tv_compile_log.getParent() != null) {
                ((ViewGroup) tv_compile_log.getParent()).removeView(tv_compile_log);
            }
            err_vScroll.addView(tv_compile_log);
        } else {
            err_vScroll.removeAllViews();
            if (tv_compile_log.getParent() != null) {
                ((ViewGroup) tv_compile_log.getParent()).removeView(tv_compile_log);
            }
            err_hScroll.removeAllViews();
            err_hScroll.addView(tv_compile_log);
            err_vScroll.addView(err_hScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            tv_compile_log.setTypeface(com.sketchware.ai.ui.ObFonts.mono(this));
        } else {
            tv_compile_log.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new com.sketchware.ai.ui.ObAlertBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    tv_compile_log.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
