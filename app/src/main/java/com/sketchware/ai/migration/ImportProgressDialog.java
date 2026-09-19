package com.sketchware.ai.migration;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * Determinate progress for the Sketchware Pro import: "Importing project 5 of 26…", the
 * project's name, a horizontal bar, the percentage and the amount copied.
 * <p>
 * "Run in background" hides it while the copy continues; "Stop" ends the import after the
 * current file, keeping every project that was already finished.
 */
public final class ImportProgressDialog {

    public interface Callbacks {
        void onRunInBackground();

        void onStop();
    }

    private final AlertDialog dialog;
    private final TextView headline;
    private final TextView detail;
    private final ProgressBar bar;
    private final TextView percent;
    private final TextView amount;

    public ImportProgressDialog(Activity activity, Callbacks callbacks) {
        int orange = activity.getResources().getColor(R.color.color_primary);
        int pad = dp(activity, 24);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(activity, 8), pad, dp(activity, 4));

        headline = new TextView(activity);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        headline.setTypeface(Typeface.DEFAULT_BOLD);
        headline.setTextColor(0xDE000000);
        root.addView(headline);

        detail = new TextView(activity);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        detail.setTextColor(0x99000000);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        detail.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
        root.addView(detail);

        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        bar.setProgressTintList(ColorStateList.valueOf(orange));
        bar.setIndeterminateTintList(ColorStateList.valueOf(orange));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33f4511e));
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 10)));

        FrameLayout numbers = new FrameLayout(activity);
        numbers.setPadding(0, dp(activity, 6), 0, 0);
        percent = new TextView(activity);
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        percent.setTypeface(Typeface.DEFAULT_BOLD);
        percent.setTextColor(orange);
        numbers.addView(percent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START));
        amount = new TextView(activity);
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        amount.setTextColor(0x99000000);
        numbers.addView(amount, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END));
        root.addView(numbers);

        TextView note = new TextView(activity);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setTextColor(0x8A000000);
        note.setPadding(0, dp(activity, 12), 0, 0);
        note.setText("Sketchware Pro's files are only read, never changed. "
                + ".swb backups stay in /.sketchware/backups.");
        root.addView(note);

        dialog = new AlertDialog.Builder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Importing from Sketchware Pro")
                .setView(root)
                .setCancelable(false)
                .setPositiveButton("Run in background", null)
                .setNegativeButton("Stop", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                callbacks.onRunInBackground();
            });
            View stop = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            stop.setOnClickListener(v -> new AlertDialog.Builder(activity, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Stop importing?")
                    .setMessage("Projects that are already imported stay. The rest can be imported "
                            + "later: Sketchware AI offers to resume on its next start.")
                    .setPositiveButton("Stop", (d2, w) -> {
                        headline.setText("Stopping after the current file…");
                        stop.setEnabled(false);
                        callbacks.onStop();
                    })
                    .setNegativeButton("Keep importing", null)
                    .show());
        });
        headline.setText("Preparing import…");
    }

    public void show() {
        if (!dialog.isShowing()) dialog.show();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    public void update(LegacyProjectMigrator.Progress p) {
        headline.setText(ImportTexts.headline(p));
        detail.setText(ImportTexts.detail(p));
        if (p.bytesTotal > 0) {
            bar.setIndeterminate(false);
            bar.setProgress(p.percent());
            percent.setText(p.percent() + "%");
            amount.setText(ImportTexts.size(p.bytesDone) + " / " + ImportTexts.size(p.bytesTotal));
        } else {
            bar.setIndeterminate(true);
            percent.setText("");
            amount.setText("");
        }
    }

    /** Summary text for a finished import, for toasts and dialogs. */
    public static String summary(LegacyProjectMigrator.Result result) {
        return ImportTexts.summary(result);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
