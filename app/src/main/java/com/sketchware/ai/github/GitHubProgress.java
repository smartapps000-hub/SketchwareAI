package com.sketchware.ai.github;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

/**
 * v2.0.2 (owner's 14 Nov directive "live 0%-100% progress bar for Back up to GitHub and GitHub import"): an opaque glass
 * sheet with the percentage, an animated orange bar and the step ("65% • Uploading to GitHub…"). "Hide" leaves the work
 * running (a toast says when it ends); at the end the button reads "Done" (success closes by itself after a moment).
 * Every method runs on the main thread and does nothing once the screen under it is gone.
 */
public final class GitHubProgress {

    private final Activity activity;
    private final GitHubViews.ProgressSheet sheet;
    private final AlertDialog dialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int shown;
    private boolean finished;
    private boolean hidden;

    public static GitHubProgress show(Activity activity, int icon, String title) {
        return new GitHubProgress(activity, icon, title);
    }

    private GitHubProgress(Activity activity, int icon, String title) {
        this.activity = activity;
        this.sheet = new GitHubViews(activity).progressSheet(icon, title);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(sheet.root);
        dialog = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(scroll)
                .setPositiveButton("Hide", null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) button.setOnClickListener(v -> {
                hidden = true;
                dismiss();
            });
        });
        if (alive()) dialog.show();
    }

    /** The work went to the background with "Hide" (its end is a toast then). */
    public boolean hidden() {
        return hidden || !dialog.isShowing();
    }

    /** A step: {@code percent} 0-100, the text after "N% •". */
    public void update(int percent, String text) {
        if (finished || !alive()) return;
        set(Math.max(shown, Math.min(99, percent)), text, false);
    }

    /** 100%, the closing line; the sheet closes by itself after a moment. */
    public void done(String text) {
        if (!alive()) return;
        finished = true;
        set(100, text, false);
        button("Done");
        main.postDelayed(this::dismiss, 1600);
    }

    /** The error stays on the sheet (in the error colour) until "Close". */
    public void failed(String text) {
        if (!alive()) return;
        finished = true;
        sheet.status.setText(text);
        sheet.status.setTextColor(new GitHubViews(activity).color(R.color.ob_error));
        sheet.percent.setTextColor(new GitHubViews(activity).color(R.color.ob_error));
        button("Close");
    }

    private void set(int percent, String text, boolean error) {
        shown = percent;
        sheet.percent.setText(percent + "%");
        int target = percent * 10;
        if (Build.VERSION.SDK_INT >= 24) {
            sheet.bar.setProgress(target, true);
        } else {
            sheet.bar.setProgress(target);
        }
        sheet.status.setText(percent + "% • " + text);
    }

    private void button(String label) {
        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setText(label);
            button.setOnClickListener(v -> dismiss());
            button.setVisibility(View.VISIBLE);
        }
    }

    private void dismiss() {
        main.removeCallbacksAndMessages(null);
        try {
            if (dialog.isShowing() && alive()) dialog.dismiss();
        } catch (RuntimeException ignored) {
            // the window went with its screen
        }
    }

    private boolean alive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }
}
