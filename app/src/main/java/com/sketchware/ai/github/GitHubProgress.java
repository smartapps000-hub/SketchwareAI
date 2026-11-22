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
    /** v2.0.5: a second row (Open on GitHub, full width) and the notifications line under the pills. */
    private final LinearLayout buttons2;
    private final LinearLayout notice;

    public static GitHubProgress show(Activity activity, int icon, String title) {
        return new GitHubProgress(activity, icon, title);
    }

    private GitHubProgress(Activity activity, int icon, String title) {
        this.activity = activity;
        this.v = new GitHubViews(activity);
        this.sheet = v.progressSheet(icon, title);
        buttons2 = new LinearLayout(activity);
        buttons2.setOrientation(LinearLayout.HORIZONTAL);
        notice = new LinearLayout(activity);
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(android.view.Gravity.CENTER_VERTICAL);
        notice.setVisibility(View.GONE);
        int at = sheet.root.indexOfChild(sheet.buttons);
        sheet.root.addView(buttons2, at + 1, v.full(-1, 8));
        sheet.root.addView(notice, at + 2, v.full(-1, 10));
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
        checkNotifications();
    }

    /**
     * v2.0.5 (owner's 16 Nov phone report: "No notifications" while a backup ran): Android 13+ shows the progress
     * notification only with POST_NOTIFICATIONS, asked here once when the transfer starts; when notifications stay off
     * (denied, or blocked in Android's settings) a line with "Turn on" opens the app's notification settings. The job
     * runs either way.
     */
    private void checkNotifications() {
        if (!alive()) return;
        android.app.NotificationManager nm = activity.getSystemService(android.app.NotificationManager.class);
        boolean enabled = nm != null && nm.areNotificationsEnabled();
        if (enabled) {
            notice.setVisibility(View.GONE);
            return;
        }
        android.content.SharedPreferences prefs = activity.getSharedPreferences(CloudSync.PREFS, Activity.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("notifications_asked", false)) {
            prefs.edit().putBoolean("notifications_asked", true).apply();
            activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7321);
            // asked: whether it stays off is seen when the sheet is shown next
            return;
        }
        notice.removeAllViews();
        TextView line = v.line("Notifications are off: the progress shows only here.", false);
        line.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        notice.addView(line, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView on = pill("Turn on", true, x -> openNotificationSettings());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(36));
        p.leftMargin = v.dp(8);
        notice.addView(on, p);
        notice.setVisibility(View.VISIBLE);
    }

    private void openNotificationSettings() {
        try {
            activity.startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
        } catch (RuntimeException e) {
            try {
                activity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException ignored) {
                // no settings screen: nothing to open
            }
        }
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
        if (doneAction != null) {
            // v2.1.4: stays until tapped; Done opens what was made (the store list with the app first)
            Runnable open = doneAction;
            TextView done = pill(doneLabel == null ? "Done" : doneLabel, true, x -> {
                dismiss();
                open.run();
            });
            GitHubViews.oneLine(done);
            buttons(done);
            buttons2.removeAllViews();
            return;
        }
        buttons(pill("Done", false, x -> dismiss()));
        buttons2.removeAllViews();
        if (inFront()) main.postDelayed(this::dismiss, 1600);
    }

    private Runnable doneAction;
    private String doneLabel;

    /** v2.1.4: when the job ends well, the sheet stays with an orange {@code label} pill that runs {@code open}. */
    public void doneOpens(String label, Runnable open) {
        doneLabel = label;
        doneAction = open;
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
        buttons2.removeAllViews();
    }

    /**
     * v2.1.2 (owner's 21 Nov directive: the update download has no Cancel; "on an error, Retry in its place"): the
     * error with Hide and Retry.
     */
    public void failedHideRetry(String text) {
        if (!alive()) return;
        finished = true;
        sheet.status.setText(text);
        sheet.status.setMaxLines(8);
        sheet.status.setTextColor(v.color(R.color.ob_error));
        sheet.percent.setTextColor(v.color(R.color.ob_error));
        buttons(pill("Hide", false, x -> hide()), retry == null ? null : pill("Retry", true, x -> retry.run()));
        buttons2.removeAllViews();
    }

    /**
     * v2.0.5: GitHub refused the saved sign-in (HTTP 401): "Sign in again" renews it in one tap and the job runs again
     * (Retry), else Close.
     */
    public void failedSignIn(String text) {
        failed(text, false);
        if (!alive()) return;
        buttons(pill("Sign in again", true, x -> GitHubInAppAuthDialog.show(activity, login -> {
            if (retry != null && alive()) retry.run();
        })), pill("Close", false, x -> dismiss()));
    }

    private void runningButtons() {
        // v2.0.5 (owner's 16 Nov phone report: "H…", "Ca…", "Open on Git…" at a large font size): Hide and Cancel side
        // by side, Open on GitHub on its own row at full width
        TextView hide = pill("Hide", false, x -> hide());
        TextView stop = cancel == null ? null : pill("Cancel", false, x -> {
            sheet.status.setText("Cancelling…");
            cancel.run();
        });
        buttons(hide, stop);
        buttons2.removeAllViews();
        if (link != null) {
            buttons2.addView(pill("Open on GitHub", true, x -> openOnGitHub()), new LinearLayout.LayoutParams(-1, v.dp(40)));
        }
        sheet.note.setVisibility(View.GONE);
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
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, v.dp(40), 1f);
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
        // v2.0.5: the repository opens in the app (RepoViewerActivity: details, README, files, versions)
        RepoViewerActivity.open(activity, link);
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
