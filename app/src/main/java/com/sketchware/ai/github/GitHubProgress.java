package com.sketchware.ai.github;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.remod.R;

/**
 * v2.0.2 (owner's 14 Nov directive "live 0%-100% progress bar for Back up to GitHub and GitHub import"): an opaque glass
 * sheet with the percentage, an animated orange bar and the step ("65% • Uploading to GitHub…").
 * <p>
 * v2.0.4 (owner's 16 Nov directive "Hide, Cancel, Retry, Open on GitHub"): side-by-side glass pills under the bar.
 * While the job runs: <b>Hide</b> (the sheet closes; the job goes on and its progress is in the notification of
 * {@link CloudSyncService}), <b>Cancel</b>, and for an import or a backup whose repository is known <b>Open on
 * GitHub</b> (the repository in the browser; the sheet hides the same way, the job goes on). At the end: Done, or after
 * a failure <b>Retry</b> and Close. Every method runs on the main thread and does nothing once the screen under it is
 * gone.
 */
public final class GitHubProgress {

    private final Activity activity;
    private final GitHubViews v;
    private final GitHubViews.ProgressSheet sheet;
    private final AlertDialog dialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int shown;
    private boolean finished;
    private boolean hidden;
    private Runnable cancel;
    private Runnable retry;
    private String link;
    private boolean succeeded;
    private java.util.function.Consumer<Boolean> closed;

    public static GitHubProgress show(Activity activity, int icon, String title) {
        return new GitHubProgress(activity, icon, title);
    }

    private GitHubProgress(Activity activity, int icon, String title) {
        this.activity = activity;
        this.v = new GitHubViews(activity);
        this.sheet = v.progressSheet(icon, title);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(sheet.root);
        dialog = new ObAlertBuilder(activity, R.style.SketchwareAI_GlassAlertDialog)
                .setView(scroll)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(d -> {
            if (finished && closed != null) closed.accept(succeeded);
        });
        runningButtons();
        if (alive()) dialog.show();
    }

    /** v2.0.4: what Cancel and Retry do, and the repository Open on GitHub opens (null: none yet). */
    public void actions(Runnable cancel, Runnable retry, String link) {
        this.cancel = cancel;
        this.retry = retry;
        this.link = link;
        if (!finished) runningButtons();
    }

    /** v2.0.4: told (true = it worked) when the sheet closes after the end. */
    public void whenClosed(java.util.function.Consumer<Boolean> callback) {
        closed = callback;
    }

    /** v2.0.4: the repository became known (a backup's or import's). */
    public void link(String url) {
        if (url == null || url.equals(link)) return;
        link = url;
        if (!finished && alive()) runningButtons();
    }

    /** v2.0.4: Retry started the job again: the sheet runs again. */
    public void restart() {
        finished = false;
        succeeded = false;
        shown = 0;
        sheet.status.setTextColor(v.color(R.color.ob_text_2));
        sheet.percent.setTextColor(v.color(R.color.ob_text));
        runningButtons();
        set(1, "Starting again…");
    }

    /**
     * The work went to the background with "Hide", or the screen under the sheet is gone (closed, or re-created after
     * a dark-mode / font / fold change): its end is a toast then (v2.0.2 review).
     */
    public boolean hidden() {
        return hidden || !alive() || !dialog.isShowing();
    }

    /** A step: {@code percent} 0-100, the text after "N% •". */
    public void update(int percent, String text) {
        if (finished || !alive()) return;
        set(Math.max(shown, Math.min(99, percent)), text);
    }

    /**
     * 100%, the closing line. The sheet closes by itself after a moment only while it is in front; when the app was in
     * the background (Home during an upload) it stays at 100% with "Done" for the user to see on coming back.
     */
    public void done(String text) {
        if (!alive()) return;
        finished = true;
        succeeded = true;
        set(100, text);
        buttons(pill("Done", false, x -> dismiss()));
        if (inFront()) main.postDelayed(this::dismiss, 1600);
    }

    private boolean inFront() {
        android.view.Window w = dialog.getWindow();
        return w != null && w.getDecorView().hasWindowFocus();
    }

    /** The error stays on the sheet (in the error colour) with Retry (v2.0.4) and Close. */
    public void failed(String text) {
        failed(text, true);
    }

    public void failed(String text, boolean retryable) {
        if (!alive()) return;
        finished = true;
        sheet.status.setText(text);
        sheet.status.setMaxLines(8);
        sheet.status.setTextColor(v.color(R.color.ob_error));
        sheet.percent.setTextColor(v.color(R.color.ob_error));
        if (retryable && retry != null) {
            buttons(pill("Retry", true, x -> retry.run()), pill("Close", false, x -> dismiss()));
        } else {
            buttons(pill("Close", false, x -> dismiss()));
        }
    }

    private void runningButtons() {
        TextView hide = pill("Hide", false, x -> hide());
        TextView stop = cancel == null ? null : pill("Cancel", false, x -> {
            sheet.status.setText("Cancelling…");
            cancel.run();
        });
        TextView open = link == null ? null : pill("Open on GitHub", true, x -> openOnGitHub());
        buttons(hide, stop, open);
        if (link != null) {
            sheet.note.setText("Open on GitHub: view the repository and its files; this goes on in the notification.");
            sheet.note.setVisibility(View.VISIBLE);
        } else {
            sheet.note.setVisibility(View.GONE);
        }
    }

    private TextView pill(String text, boolean orange, View.OnClickListener click) {
        TextView t = v.smallPill(text, orange, true, click);
        GitHubViews.oneLine(t);
        return t;
    }

    private void buttons(TextView... pills) {
        sheet.buttons.removeAllViews();
        for (TextView pill : pills) {
            if (pill == null) continue;
            // its own width plus a share of the rest: "Open on GitHub" stays whole beside Hide and Cancel
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(40), 1f);
            if (sheet.buttons.getChildCount() > 0) p.leftMargin = v.dp(8);
            sheet.buttons.addView(pill, p);
        }
    }

    /** Hide: the sheet closes, the job goes on; the notification (Android 13+: asked for once) shows it. */
    private void hide() {
        hidden = true;
        askForNotifications();
        dismiss();
    }

    private void openOnGitHub() {
        hide();
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (RuntimeException ignored) {
            // no browser: the job goes on all the same
        }
    }

    private void askForNotifications() {
        if (Build.VERSION.SDK_INT < 33 || !alive()) return;
        if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences(CloudSync.PREFS, Activity.MODE_PRIVATE);
        if (prefs.getBoolean("notifications_asked", false)) return;
        prefs.edit().putBoolean("notifications_asked", true).apply();
        activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7321);
    }

    private void set(int percent, String text) {
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
