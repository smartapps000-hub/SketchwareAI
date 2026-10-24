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

    /** Set when something was changed from the open sheet (a fix stored in a block, references removed). */
    private String changedInSheet;
    /** Binds RESULT and the main button of the sheet that is open again; null when none is. */
    private Runnable rebindSheet;

    @Override
    protected void onResume() {
        super.onResume();
        // Back from Image Manager or Local libraries: what a new build would do may have changed.
        if (rebindSheet != null) rebindSheet.run();
    }

    /**
     * Sketchware AI Diagnostic as a sliding liquid-glass bottom sheet. It is about the step that
     * failed in this log: the current problem, what was found, what the build already changed
     * by itself (permanent or temporary), the build's steps, and the result. "Build again" is
     * only offered when a new build can end differently.
     */
    private void showDiagnostic() {
        String log = logText();
        com.sketchware.ai.doctor.BuildDoctor.Diagnosis d = com.sketchware.ai.doctor.BuildDoctor.diagnose(log);
        changedInSheet = null;

        android.app.Dialog sheet = new android.app.Dialog(this, R.style.Ob_SheetDialog);
        View content = getLayoutInflater().inflate(R.layout.ob_diagnostic_sheet, null);
        sheet.setContentView(content);
        sheetWindow(sheet);

        String subtitle;
        if (log.trim().isEmpty()) {
            subtitle = "Offline · the compile log is empty";
        } else if (d.succeeded) {
            subtitle = "Offline · the build passed";
        } else {
            subtitle = "Offline · " + (d.stage == null ? "build failed" : "failed at: " + d.stage);
        }
        ((TextView) content.findViewById(R.id.ob_diag_subtitle)).setText(subtitle);

        ((TextView) content.findViewById(R.id.ob_diag_first_label)).setText(d.succeeded ? "RESULT" : "CURRENT PROBLEM");
        TextView title = content.findViewById(R.id.ob_diag_title);
        title.setText(d.title == null ? "There is nothing to diagnose yet." : d.title);
        TextView first = content.findViewById(R.id.ob_diag_first_error);
        if (d.firstError == null || d.firstError.isEmpty() || d.succeeded) {
            first.setVisibility(View.GONE);
        } else {
            first.setText(d.firstError);
        }

        StringBuilder found = new StringBuilder();
        if (d.found != null && !d.found.isEmpty() && !d.found.equals(d.firstError)) found.append(d.found);
        for (String advice : d.advice) {
            if (found.length() > 0) found.append("\n\n");
            found.append(advice);
        }
        if (found.length() == 0) {
            content.findViewById(R.id.ob_diag_found_label).setVisibility(View.GONE);
            content.findViewById(R.id.ob_diag_advice).setVisibility(View.GONE);
        } else {
            ((TextView) content.findViewById(R.id.ob_diag_advice)).setText(found);
        }

        showResources(content, sheet, d);
        showFixes(content, sheet, d);
        if (!d.succeeded) showProblems(content, sheet, log);
        showChanges(content, d);
        showSteps(content, d);
        bindResult(content, sheet, d);

        content.findViewById(R.id.ob_diag_copy).setOnClickListener(v -> copyLog());
        content.findViewById(R.id.ob_diag_history).setOnClickListener(v -> showHistory());
        rebindSheet = () -> bindResult(content, sheet, d);
        sheet.setOnDismissListener(dialog -> rebindSheet = null);
        sheet.show();
    }

    private void sheetWindow(android.app.Dialog sheet) {
        android.view.Window window = sheet.getWindow();
        if (window == null) return;
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

    private boolean projectIsOpen() {
        return getIntent().getBooleanExtra(EXTRA_FROM_EDITOR, false) && a.a.a.jC.a != null && scId.equals(a.a.a.jC.a.a);
    }

    /**
     * RESULT and the main button. Building again is offered when it can end differently: the
     * project (or a library or image it needs) changed since the failed build, something was
     * changed in this sheet, the log comes from an older app version, or the failure depended on
     * the phone. Otherwise the button leads to the problem instead.
     */
    private void bindResult(View content, android.app.Dialog sheet, com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        TextView result = content.findViewById(R.id.ob_diag_result);
        TextView primary = content.findViewById(R.id.ob_diag_primary);
        boolean fromEditor = getIntent().getBooleanExtra(EXTRA_FROM_EDITOR, false);
        if (d.empty || logText().trim().isEmpty()) {
            result.setVisibility(View.GONE);
            primary.setText("Done");
            primary.setOnClickListener(v -> sheet.dismiss());
            return;
        }
        if (d.succeeded) {
            int notPermanent = 0;
            for (com.sketchware.ai.doctor.BuildDoctor.Change change : d.changes) {
                if (!Boolean.TRUE.equals(change.permanent)) notPermanent++;
            }
            result.setText(d.changes.isEmpty() ? "RESULT: the app was built."
                    : notPermanent == 0 ? "RESULT: FIXED. The app was built, and every repair is saved in the project: the next "
                    + "build needs none of them again."
                    : "RESULT: the app was built. " + notPermanent + (notPermanent == 1 ? " repair is" : " repairs are")
                    + " not saved in the project and will be redone at the next build (see below for each one).");
            primary.setText("Done");
            primary.setOnClickListener(v -> sheet.dismiss());
            return;
        }
        if (!fromEditor) {
            result.setText("RESULT: NOT FIXED YET. Open the project and run it to build again.");
            primary.setText("Done");
            primary.setOnClickListener(v -> sheet.dismiss());
            return;
        }
        java.util.List<com.sketchware.ai.doctor.BuildHistory.Entry> builds =
                com.sketchware.ai.doctor.BuildHistory.load(new java.io.File(a.a.a.wq.b(scId)));
        if (changedInSheet == null && !builds.isEmpty() && "ok".equals(builds.get(0).result)) {
            // A clean build writes no log, so the last failed one is still what "Show last compile error" shows.
            result.setText("RESULT: this log is from an earlier build. The latest build of this project ("
                    + new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(builds.get(0).time))
                    + ") passed.");
            primary.setText("History");
            primary.setOnClickListener(v -> showHistory());
            return;
        }
        String reason = rebuildReason(d);
        if (reason != null) {
            result.setText("RESULT: NOT FIXED YET. " + reason);
            primary.setText("Build again");
            primary.setOnClickListener(v -> {
                // Real rebuild: back to the design editor, which starts the same build as Run.
                requestRebuild(scId);
                sheet.dismiss();
                finish();
            });
            return;
        }
        java.util.List<com.sketchware.ai.doctor.BuildHistory.Entry> history =
                com.sketchware.ai.doctor.BuildHistory.load(new java.io.File(a.a.a.wq.b(scId)));
        int repeats = com.sketchware.ai.doctor.BuildHistory.sameFailureInARow(history);
        String text = "RESULT: NOT FIXED YET. Nothing in the project has changed since this build failed, so the same "
                + "build would stop at the same place" + (repeats > 1 ? " (it already did " + repeats + " times in a row)" : "") + ".";
        final Runnable action = nextAction(d, sheet);
        if (action == null) {
            result.setText(text + " Change what the problem above names, then run the project.");
            primary.setText("History");
            primary.setOnClickListener(v -> showHistory());
        } else {
            result.setText(text + " " + nextActionHint);
            primary.setText(nextActionLabel);
            primary.setOnClickListener(v -> action.run());
        }
    }

    private String nextActionLabel;
    private String nextActionHint;

    /** Where the main button leads when building again would change nothing; null when there is no such place. */
    private Runnable nextAction(com.sketchware.ai.doctor.BuildDoctor.Diagnosis d, android.app.Dialog sheet) {
        String code = d.code;
        if (com.sketchware.ai.doctor.BuildDoctor.MISSING_RESOURCE.equals(code) && d.imagesOnly) {
            nextActionLabel = "Add images";
            nextActionHint = "Add the images under the names listed, or use Remove references in the card above.";
            return () -> openProjectScreen("com.besome.sketch.editor.manage.image.ManageImageActivity");
        }
        if (com.sketchware.ai.doctor.BuildDoctor.MISSING_FILE.equals(code) || com.sketchware.ai.doctor.BuildDoctor.CORRUPT_JAR.equals(code)
                || com.sketchware.ai.doctor.BuildDoctor.DUPLICATE_CLASS.equals(code)
                || com.sketchware.ai.doctor.BuildDoctor.UNSUPPORTED_BYTECODE.equals(code)
                || com.sketchware.ai.doctor.BuildDoctor.MISSING_CLASS.equals(code)) {
            nextActionLabel = "Local libraries";
            nextActionHint = "Open the project's local libraries to download, replace or switch off the one named above.";
            return () -> openProjectScreen("com.sketchware.ai.library.LibraryResolverActivity");
        }
        if (com.sketchware.ai.doctor.BuildDoctor.JAVA_ERROR.equals(code)) {
            java.util.List<com.sketchware.ai.sourcemap.ErrorOrigins.Item> problems;
            try {
                problems = com.sketchware.ai.sourcemap.ErrorOrigins.find(new java.io.File(a.a.a.wq.d(scId)), logText());
            } catch (RuntimeException e) {
                return null;
            }
            for (com.sketchware.ai.sourcemap.ErrorOrigins.Item problem : problems) {
                if (problem.origin == null) continue;
                nextActionLabel = "Open problem";
                nextActionHint = "Open problem goes to the block of the first error.";
                return () -> {
                    if (openBlock(problem)) sheet.dismiss();
                };
            }
            if (!problems.isEmpty()) {
                final com.sketchware.ai.sourcemap.ErrorOrigins.Item problem = problems.get(0);
                nextActionLabel = "Open problem";
                nextActionHint = "Open problem shows the line of the first error in the generated code.";
                return () -> viewSource(problem);
            }
        }
        return null;
    }

    private void openProjectScreen(String className) {
        try {
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName(this, className);
            intent.putExtra("sc_id", scId);
            startActivity(intent);
        } catch (RuntimeException e) {
            SketchwareUtil.toastError("Couldn't open that screen: " + e.getMessage());
        }
    }

    private void viewSource(com.sketchware.ai.sourcemap.ErrorOrigins.Item problem) {
        android.content.Intent intent = new android.content.Intent(this, com.besome.sketch.common.SrcViewerActivity.class);
        intent.putExtra("sc_id", scId);
        intent.putExtra("current", problem.fileName);
        intent.putExtra("path", problem.file.getPath());
        intent.putExtra("line", problem.line);
        startActivity(intent);
    }

    /** Why a new build can end differently than the one in the log; null when nothing says it can. */
    private String rebuildReason(com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        if (changedInSheet != null) return changedInSheet + " Build again to see the result.";
        String current = com.sketchware.ai.buildmap.BuildMap.appVersion();
        if (d.builtWith == null || !d.builtWith.equals(current)) {
            return "This log was made by " + (d.builtWith == null ? "an older version of Sketchware AI" : "Sketchware AI " + d.builtWith)
                    + ". The version installed now (" + current + ") repairs more by itself; build again with it.";
        }
        if (d.retryCanHelp) {
            return "This failure depended on the phone (internet connection, memory or storage), not on the project. "
                    + "Build again once that is in order.";
        }
        try {
            java.io.File root = com.sketchware.ai.project.ProjectImages.sketchwareRoot();
            if (com.sketchware.ai.doctor.BuildDoctor.MISSING_FILE.equals(d.code) && d.library != null
                    && new java.io.File(root, "libs/local_libs/" + d.library + "/classes.jar").isFile()) {
                return "The files of local library " + d.library + " are on the phone now.";
            }
            java.util.List<com.sketchware.ai.doctor.BuildHistory.Entry> history =
                    com.sketchware.ai.doctor.BuildHistory.load(new java.io.File(a.a.a.wq.b(scId)));
            if (history.isEmpty() || history.get(0).fingerprint == null) {
                return "There is no record of the project's state at this build, so it may have changed since.";
            }
            // The working copy on disk is made current first, as the editor does when it goes to the background.
            if (projectIsOpen()) a.a.a.jC.a.k();
            String now = com.sketchware.ai.doctor.BuildHistory.fingerprint(root, scId);
            if (now == null) return "Whether the project changed since this build couldn't be checked.";
            if (!now.equals(history.get(0).fingerprint)) return "The project was changed after this build.";
        } catch (RuntimeException e) {
            return "Whether the project changed since this build couldn't be checked.";
        }
        return null;
    }

    /** WHAT THIS BUILD CHANGED: one card per repair the build made by itself. */
    private void showChanges(View content, com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        if (d.changes.isEmpty()) return;
        LinearLayout box = content.findViewById(R.id.ob_diag_changes);
        TextView label = content.findViewById(R.id.ob_diag_changes_label);
        label.setVisibility(View.VISIBLE);
        label.setText(d.succeeded ? "WHAT THIS BUILD CHANGED" : "WHAT THIS BUILD CHANGED BEFORE IT STOPPED");
        box.setVisibility(View.VISIBLE);
        for (com.sketchware.ai.doctor.BuildDoctor.Change change : d.changes) {
            View card = getLayoutInflater().inflate(R.layout.ob_diagnostic_change, box, false);
            TextView kind = card.findViewById(R.id.ob_change_kind);
            if (Boolean.TRUE.equals(change.permanent)) {
                kind.setText("PERMANENT · SAVED IN THE PROJECT");
                kind.setTextColor(0xFF7DD3A0);
            } else if (Boolean.FALSE.equals(change.permanent)) {
                kind.setText("TEMPORARY REPAIR · REDONE AT EVERY BUILD");
            } else {
                kind.setText("REPAIR");
            }
            String text = change.text.replace(" Temporary repair: it is redone at every build until you keep it.", "")
                    .replace(" Temporary repair: it is redone at every build.", "")
                    .replace(" Temporary repair: redone at every build; your file was not changed.", "");
            ((TextView) card.findViewById(R.id.ob_change_text)).setText(text.trim());
            box.addView(card);
        }
    }

    /** BUILD STEPS: the build map as a timeline (passed, failed, not reached). */
    private void showSteps(View content, com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        if (d.steps.isEmpty()) return;
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder();
        for (com.sketchware.ai.doctor.BuildDoctor.Step step : d.steps) {
            if (text.length() > 0) text.append('\n');
            int start = text.length();
            int color;
            if ("ok".equals(step.status)) {
                text.append("✓  ").append(step.name);
                if (!step.time.isEmpty()) text.append("  ·  ").append(step.time);
                color = 0xFFD4D4D8;
            } else if ("FAILED".equals(step.status)) {
                text.append("✗  ").append(step.name).append("  ·  failed here");
                color = 0xFFFF8A65;
            } else if ("-".equals(step.status)) {
                text.append("·  ").append(step.name).append("  ·  not reached");
                color = 0xFF8A8A93;
            } else {
                text.append("↻  ").append(step.name).append("  ·  ").append(step.status);
                color = 0xFFFFB877;
            }
            text.setSpan(new android.text.style.ForegroundColorSpan(color), start, text.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        content.findViewById(R.id.ob_diag_steps_label).setVisibility(View.VISIBLE);
        TextView steps = content.findViewById(R.id.ob_diag_steps);
        steps.setVisibility(View.VISIBLE);
        steps.setText(text);
    }

    /**
     * RESOURCE REQUIRED: what screens name that is nowhere on the phone, the widget that names
     * it, and the two ways out: add the image, or clear the widgets' references (with Undo).
     */
    private void showResources(View content, android.app.Dialog sheet, com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        boolean standIns = false;
        for (com.sketchware.ai.doctor.BuildDoctor.Change change : d.changes) {
            if (change.text.contains("An empty stand-in was used")) standIns = true;
        }
        if (!com.sketchware.ai.doctor.BuildDoctor.MISSING_RESOURCE.equals(d.code) && !standIns) return;
        final java.util.List<com.sketchware.ai.project.ResourceResolver.Missing> items = new java.util.ArrayList<>();
        java.util.List<com.sketchware.ai.project.ResourceResolver.Missing> inLog =
                com.sketchware.ai.project.ResourceResolver.parse(d.failureText);
        if (standIns) {
            // The build passed with stand-ins: its record lists the images that are still to be added.
            String log = logText();
            for (com.sketchware.ai.project.ResourceResolver.Missing saved
                    : com.sketchware.ai.project.ResourceResolver.load(new java.io.File(a.a.a.wq.d(scId)))) {
                if (log.contains(saved.name + " (")) items.add(saved);
            }
        }
        // The build's own record names the widget of each one; it is used when it is about this log.
        for (com.sketchware.ai.project.ResourceResolver.Missing saved
                : com.sketchware.ai.project.ResourceResolver.load(new java.io.File(a.a.a.wq.d(scId)))) {
            for (com.sketchware.ai.project.ResourceResolver.Missing logged : inLog) {
                if (logged.file.equals(saved.file) && logged.line == saved.line && logged.name.equals(saved.name)) {
                    items.add(saved);
                    break;
                }
            }
        }
        if (items.isEmpty()) items.addAll(inLog);
        if (items.isEmpty()) return;
        LinearLayout box = content.findViewById(R.id.ob_diag_resources);
        box.setVisibility(View.VISIBLE);
        View card = getLayoutInflater().inflate(R.layout.ob_diagnostic_resource, box, false);
        StringBuilder list = new StringBuilder();
        int removable = 0, shown = 0;
        for (com.sketchware.ai.project.ResourceResolver.Missing item : items) {
            if (item.viewId != null && "drawable".equals(item.type)) removable++;
            if (shown == 14) {
                list.append("\n(").append(items.size() - shown).append(" more in the log)");
                break;
            }
            if (list.length() > 0) list.append('\n');
            list.append(item.file).append(':').append(item.line).append("  ").append(item.type).append('/').append(item.name);
            if (item.viewId != null) {
                list.append("\n    ← ").append(item.widget == null ? "widget" : item.widget).append(' ').append(item.viewId);
            }
            shown++;
        }
        ((TextView) card.findViewById(R.id.ob_resource_list)).setText(list);
        TextView status = card.findViewById(R.id.ob_resource_status);
        TextView choose = card.findViewById(R.id.ob_resource_choose);
        TextView remove = card.findViewById(R.id.ob_resource_remove);
        status.setText("Add each image in Image Manager under exactly that name. Or remove the references: the "
                + "widgets then show no image, and the build no longer needs the files.");
        choose.setOnClickListener(v -> openProjectScreen("com.besome.sketch.editor.manage.image.ManageImageActivity"));
        if (!projectIsOpen() || removable == 0) {
            remove.setVisibility(View.GONE);
            if (projectIsOpen()) {
                status.setText("Add each image in Image Manager under exactly that name. These references are written by "
                        + "hand (Inject or a custom XML file), so they can only be changed there.");
            }
        } else {
            final a.a.a.eC data = a.a.a.jC.a;
            final java.util.List<com.sketchware.ai.project.ProjectImages.Removed> removed = new java.util.ArrayList<>();
            final int byHand = items.size() - removable;
            remove.setOnClickListener(v -> {
                if (removed.isEmpty()) {
                    removed.addAll(com.sketchware.ai.project.ProjectImages.removeBrokenReferences(data,
                            new java.io.File(a.a.a.wq.b(scId)), items));
                    if (removed.isEmpty()) {
                        status.setText("No widget property names these images any more (the project changed since the "
                                + "build). Build again to see the current state.");
                        changedInSheet = "The project changed since this build.";
                    } else {
                        data.k();
                        status.setText("Cleared the image of " + removed.size() + (removed.size() == 1 ? " widget" : " widgets")
                                + " (set to none). Save the project to keep it."
                                + (byHand > 0 ? " " + byHand + " more are written by hand (Inject or custom XML) and stay." : ""));
                        remove.setText("Undo");
                        changedInSheet = "The references to the missing images were removed.";
                    }
                } else {
                    com.sketchware.ai.project.ProjectImages.undoRemove(data, removed);
                    data.k();
                    removed.clear();
                    status.setText("The widgets name the images again.");
                    remove.setText("Remove references");
                    changedInSheet = null;
                }
                bindResult(content, sheet, d);
            });
        }
        box.addView(card);
    }

    /** Build history of this project, newest first, as a second sheet. */
    private void showHistory() {
        java.util.List<com.sketchware.ai.doctor.BuildHistory.Entry> entries =
                com.sketchware.ai.doctor.BuildHistory.load(new java.io.File(a.a.a.wq.b(scId)));
        android.app.Dialog sheet = new android.app.Dialog(this, R.style.Ob_SheetDialog);
        View content = getLayoutInflater().inflate(R.layout.ob_history_sheet, null);
        sheet.setContentView(content);
        sheetWindow(sheet);
        ((TextView) content.findViewById(R.id.ob_history_subtitle)).setText(entries.isEmpty()
                ? "No build of this project has been recorded yet (recording starts with v1.8.2)."
                : entries.size() + (entries.size() == 1 ? " build" : " builds") + ", newest first");
        LinearLayout list = content.findViewById(R.id.ob_history_list);
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault());
        for (com.sketchware.ai.doctor.BuildHistory.Entry entry : entries) {
            View card = getLayoutInflater().inflate(R.layout.ob_diagnostic_change, list, false);
            TextView kind = card.findViewById(R.id.ob_change_kind);
            boolean ok = "ok".equals(entry.result);
            String head = format.format(new java.util.Date(entry.time)) + "  ·  "
                    + (ok ? "BUILT" : "FAILED" + (entry.stage == null ? "" : " AT " + entry.stage.toUpperCase(java.util.Locale.ROOT)));
            kind.setText(head);
            kind.setTextColor(ok ? 0xFF7DD3A0 : 0xFFFF8A65);
            StringBuilder body = new StringBuilder();
            if (!ok && entry.problem != null) body.append(entry.problem);
            if (ok && entry.changes.isEmpty()) body.append("No repair was needed.");
            for (int i = 0; i < entry.changes.size(); i++) {
                if (body.length() > 0) body.append('\n');
                String label = i < entry.kinds.size() ? entry.kinds.get(i) : "";
                body.append(label.equals("permanent") ? "Permanent: " : label.equals("temporary") ? "Temporary: " : "Repair: ");
                String text = entry.changes.get(i);
                body.append(text.length() > 160 ? text.substring(0, 157) + "…" : text);
            }
            if (entry.app != null && !entry.app.isEmpty()) {
                body.append('\n').append(String.format(java.util.Locale.US, "%.0f s", entry.durationMs / 1000.0))
                        .append("  ·  ").append(entry.app);
            }
            ((TextView) card.findViewById(R.id.ob_change_text)).setText(body);
            list.addView(card);
        }
        content.findViewById(R.id.ob_history_close).setOnClickListener(v -> sheet.dismiss());
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
                source.setOnClickListener(v -> viewSource(problem));
            }
            box.addView(card);
            shown++;
        }
    }

    /**
     * One card per repair of the last build that can be stored in an "add source directly" block:
     * where it is, what was wrong, the changed lines before and after, and Apply to block (with
     * Undo). Storing is proved first: the activity is generated again from the changed block and
     * must give the repaired code that compiled.
     */
    private void showFixes(View content, android.app.Dialog sheet, com.sketchware.ai.doctor.BuildDoctor.Diagnosis d) {
        if (!projectIsOpen()) return;
        final a.a.a.eC data = a.a.a.jC.a;
        final java.util.List<com.sketchware.ai.sourcemap.BlockWriteBack.Proposal> proposals =
                com.sketchware.ai.sourcemap.BlockWriteBack.load(new java.io.File(a.a.a.wq.d(scId)));
        if (proposals.isEmpty()) return;
        // Only for the log of that build: it names each repaired line.
        String log = logText();
        LinearLayout box = content.findViewById(R.id.ob_diag_fixes);
        java.util.Set<String> shownGroups = new java.util.HashSet<>();
        for (com.sketchware.ai.sourcemap.BlockWriteBack.Proposal proposal : proposals) {
            if (!log.contains(proposal.file + ":")) continue;
            // A repair that spans several blocks is one card: stored and taken back as a whole.
            if (proposal.group != null && !shownGroups.add(proposal.group)) continue;
            final java.util.List<com.sketchware.ai.sourcemap.BlockWriteBack.Proposal> group =
                    com.sketchware.ai.sourcemap.BlockWriteBack.groupOf(proposals, proposal);
            View card = getLayoutInflater().inflate(R.layout.ob_diagnostic_fix, box, false);
            String where = proposal.where();
            java.util.List<String> removed = new java.util.ArrayList<>();
            java.util.List<String> added = new java.util.ArrayList<>();
            for (com.sketchware.ai.sourcemap.BlockWriteBack.Proposal member : group) {
                removed.addAll(member.removed);
                added.addAll(member.added);
                if (member != proposal) where = where + " and block " + member.position;
            }
            ((TextView) card.findViewById(R.id.ob_fix_where)).setText(where);
            ((TextView) card.findViewById(R.id.ob_fix_problem)).setText(explain(proposal, group.size()));
            ((TextView) card.findViewById(R.id.ob_fix_before)).setText(joinLines(removed));
            ((TextView) card.findViewById(R.id.ob_fix_after)).setText(joinLines(added));
            card.findViewById(R.id.ob_fix_open).setOnClickListener(v -> {
                com.sketchware.ai.sourcemap.ErrorOrigins.Item item = new com.sketchware.ai.sourcemap.ErrorOrigins.Item();
                item.fileName = proposal.file;
                item.origin = new com.sketchware.ai.sourcemap.SourceMap.Origin();
                item.origin.event = new com.sketchware.ai.sourcemap.SourceMap.EventInfo();
                item.origin.event.kind = proposal.kind;
                item.origin.event.targetId = proposal.targetId;
                item.origin.event.eventName = proposal.eventName;
                item.origin.block = new com.sketchware.ai.sourcemap.SourceMap.Entry();
                item.origin.block.blockId = proposal.blockId;
                item.origin.block.opCode = "addSourceDirectly";
                item.origin.block.start = proposal.startLine;
                item.origin.block.end = proposal.endLine;
                item.origin.lineInBlock = proposal.problemLine > 0 ? proposal.problemLine : proposal.firstChangedLine;
                if (openBlock(item)) sheet.dismiss();
            });
            bindFixState(card, group, data, () -> bindResult(content, sheet, d));
            box.addView(card);
        }
        if (box.getChildCount() > 0) box.setVisibility(View.VISIBLE);
    }

    private static String joinLines(java.util.List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size() && i < 8; i++) {
            if (i > 0) text.append('\n');
            text.append(lines.get(i));
        }
        if (lines.size() > 8) text.append("\n(").append(lines.size() - 8).append(" more lines)");
        return text.toString();
    }

    /** What was wrong, in plain words, from the build's own message. */
    private static String explain(com.sketchware.ai.sourcemap.BlockWriteBack.Proposal proposal, int blocks) {
        String message = proposal.message == null ? "" : proposal.message;
        message = message.replace(" It still never runs; your blocks are unchanged.", "")
                .replace("; your blocks are unchanged.", ".");
        if (message.startsWith("unreachable code")) {
            String from = blocks > 1 ? "the code after the statement these " + blocks + " blocks open and close"
                    : proposal.problemLine > 0 ? "the code from line " + proposal.problemLine + " of this block on"
                    : "part of this block's code";
            return "WHAT HAPPENED: " + from + " can never run.\n"
                    + "WHY: everything in front of it always returns, and Java refuses to compile code that can't run.\n"
                    + "WHAT WAS FIXED: the statement in front is wrapped in if (true) { }, so it compiles. The code "
                    + "behind it still never runs: move it above the return if it should.\n"
                    + "NEXT TIME: with Apply to block the fix is in your " + (blocks > 1 ? "blocks" : "block")
                    + ", and the build has nothing to repair here.";
        }
        return "WHAT WAS FIXED: " + message;
    }

    private void bindFixState(View card, java.util.List<com.sketchware.ai.sourcemap.BlockWriteBack.Proposal> group,
                              a.a.a.eC data, Runnable changed) {
        TextView apply = card.findViewById(R.id.ob_fix_apply);
        TextView status = card.findViewById(R.id.ob_fix_status);
        int state = com.sketchware.ai.sourcemap.BlockWriteBack.state(data, group);
        String blocks = group.size() > 1 ? "blocks" : "block";
        apply.setEnabled(true);
        apply.setAlpha(1f);
        if (state == com.sketchware.ai.sourcemap.BlockWriteBack.APPLIED) {
            status.setText("PERMANENT: stored in the " + blocks + ". Save the project to keep it; the next build needs no repair here.");
            apply.setText("Undo");
            apply.setOnClickListener(v -> {
                String problem = com.sketchware.ai.sourcemap.BlockWriteBack.undo(data, group);
                if (problem != null) {
                    SketchwareUtil.toastError(problem);
                } else {
                    data.k();
                    changedInSheet = null;
                }
                bindFixState(card, group, data, changed);
                changed.run();
            });
        } else if (state == com.sketchware.ai.sourcemap.BlockWriteBack.NOT_APPLIED) {
            status.setText("TEMPORARY REPAIR: the build redoes this each time. Apply to " + blocks + " stores the fix in your project.");
            apply.setText(group.size() > 1 ? "Apply to blocks" : "Apply to block");
            apply.setOnClickListener(v -> {
                apply.setEnabled(false);
                apply.setAlpha(0.5f);
                apply.setText("Checking...");
                new Thread(() -> {
                    String problem;
                    try {
                        problem = com.sketchware.ai.sourcemap.BlockWriteBack.apply(data, group, name -> {
                            // The same generation the source viewer uses, from the project as it is now.
                            for (com.besome.sketch.beans.SrcCodeBean bean : new a.a.a.yq(getApplicationContext(), scId)
                                    .a(a.a.a.jC.b(scId), a.a.a.jC.a(scId), a.a.a.jC.c(scId))) {
                                if (name.equals(bean.srcFileName)) return bean.source;
                            }
                            return null;
                        }, new java.io.File(a.a.a.wq.b(scId)));
                        if (problem == null) data.k();
                    } catch (Throwable t) {
                        problem = "Couldn't store the fix: " + t;
                    }
                    final String result = problem;
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        if (result != null) {
                            SketchwareUtil.toastError(result, android.widget.Toast.LENGTH_LONG);
                        } else {
                            changedInSheet = "The repair was stored in the " + blocks + ".";
                        }
                        bindFixState(card, group, data, changed);
                        changed.run();
                    });
                }, "BlockWriteBack").start();
            });
        } else {
            status.setText("These blocks were edited since the build. Build again to see the current state.");
            apply.setVisibility(View.GONE);
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
