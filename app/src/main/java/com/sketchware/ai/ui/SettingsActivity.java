package com.sketchware.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.help.ProgramInfoActivity;
import com.besome.sketch.help.SystemSettingActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.tools.NewKeyStoreActivity;
import com.sketchware.ai.github.AppControl;
import com.sketchware.ai.github.AppUpdater;
import com.sketchware.ai.github.CloudSync;
import com.sketchware.ai.github.DrawerAccountCard;
import com.sketchware.ai.github.GitHubBackupsActivity;
import com.sketchware.ai.github.GitHubInAppAuthDialog;
import com.sketchware.ai.github.GitHubSession;
import com.sketchware.ai.github.GitHubViews;
import com.sketchware.remod.R;

import mod.hilal.saif.activities.tools.Tools;
import mod.ilyasse.activities.about.AboutModActivity;

/**
 * v2.1.0 (owner's 16 Nov directive "full-screen Obsidian Glass Settings"): the Settings tab of the bottom bar, in place
 * of the side drawer. The GitHub account card (picture, @username and e-mail with Copy, repositories, Sign in again /
 * Sign out), Backups & sync (the switch for backing up after saving, Restore projects), the Honey-Drop sound switch, App
 * updates (this version; "Latest" or the update with the in-app updater; a "Coming soon" line from app-control.json:
 * badges, not buttons), and what the drawer held: System Settings, Create Release Keystore, App Theme, Developer Tools,
 * Changelog, About Modders, Program information.
 */
public class SettingsActivity extends BaseAppCompatActivity {

    private GitHubViews v;
    private LinearLayout list;
    private final GitHubSession.Listener accountListener = () -> runOnUiThread(this::build);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        ((TextView) findViewById(R.id.gs_title)).setText("Settings");
        ((TextView) findViewById(R.id.gs_subtitle)).setText("Sketchware AI " + com.sketchware.remod.BuildConfig.VERSION_NAME);
        findViewById(R.id.gs_back).setOnClickListener(x -> onBackPressed());
        build();
    }

    @Override
    public void onStart() {
        super.onStart();
        GitHubSession.addListener(accountListener);
        build();
    }

    @Override
    public void onStop() {
        GitHubSession.removeListener(accountListener);
        super.onStop();
    }

    @Override
    public void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // v2.1.2: brought back as a tab (Home's bar): it stays as it was
        if (com.sketchware.ai.ui.TabScreens.onNewIntent(this, intent)) return;
    }

    @Override
    public void onBackPressed() {
        if (TabScreens.onBack(this)) return;
        super.onBackPressed();
    }

    private void build() {
        if (list == null || isFinishing() || isDestroyed()) return;
        list.removeAllViews();

        list.addView(title("GitHub account"), v.full(-1, 4));
        list.addView(accountCard(), v.full(-1, 6));

        list.addView(title("Backups & sync"), v.full(-1, 22));
        android.widget.CheckBox autoBackup = v.checkBox("Back up to GitHub after saving a project",
                CloudSync.autoBackupOn(this));
        autoBackup.setOnCheckedChangeListener((b, on) -> getSharedPreferences("P12", MODE_PRIVATE).edit()
                .putBoolean(CloudSync.AUTO_BACKUP_KEY, on).apply());
        list.addView(autoBackup, v.full(-1, 4));
        list.addView(v.actionCard(R.drawable.ob_ic_restore, "Restore projects", "GitHub, Demo projects, backups on this phone",
                x -> startActivity(new Intent(this, GitHubBackupsActivity.class))), v.full(-1, 8));

        // v2.1.6 (owner's ask): follow notifications can be switched off, per GitHub account
        list.addView(title("Notifications"), v.full(-1, 22));
        String me = com.sketchware.ai.github.TokenStore.get(this).login();
        android.widget.CheckBox notices = v.checkBox(me == null
                        ? "New apps from creators you follow (sign in first)"
                        : "New apps and versions from creators I follow",
                // review (LOW): ticked only when notices really come (off, or never followed in the app: not ticked)
                me != null && com.sketchware.ai.github.PlayStore.noticesOn(this, me));
        notices.setEnabled(me != null);
        notices.setOnCheckedChangeListener((b, on) -> {
            String login = com.sketchware.ai.github.TokenStore.get(this).login();
            if (login == null) return;
            com.sketchware.ai.github.PlayStore.setNoticesSwitch(this, login, on);
            // the 4-hour check starts or stops with the switch (off: a pending one is cancelled)
            com.sketchware.ai.github.FollowWatch.schedule(this);
            if (on) askForNotifications();
            if (!on) {
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) com.sketchware.ai.github.FollowWatch.clearNotices(nm);
            }
        });
        list.addView(notices, v.full(-1, 4));

        list.addView(title("Sound"), v.full(-1, 22));
        android.widget.CheckBox sound = v.checkBox("Honey-Drop sound on the bottom bar (silent when the phone is)",
                HoneyDropBar.soundOn(this));
        sound.setOnCheckedChangeListener((b, on) -> HoneyDropBar.setSoundOn(this, on));
        list.addView(sound, v.full(-1, 4));

        list.addView(title("App updates"), v.full(-1, 22));
        list.addView(updatesCard(), v.full(-1, 6));

        list.addView(title("More"), v.full(-1, 22));
        list.addView(v.actionCard(R.drawable.ob_ic_settings, "System Settings", "Editor, saving, backups",
                x -> start(SystemSettingActivity.class)), v.full(-1, 4));
        list.addView(v.actionCard(R.drawable.ob_ic_lock, "Create Release Keystore", "Your own key for signed APKs",
                x -> start(NewKeyStoreActivity.class)), v.full(-1, 8));
        list.addView(v.actionCard(R.drawable.ob_ic_dark_theme, "App Theme",
                ThemeMode.LABELS[ThemeMode.get(this)], x -> themeDialog()), v.full(-1, 8));
        list.addView(v.actionCard(R.drawable.ob_ic_tune, "Developer Tools", "Tools for advanced users",
                x -> start(Tools.class)), v.full(-1, 8));
        list.addView(v.actionCard(R.drawable.ob_ic_spark, "Changelog", "What changed in each version", x -> {
            Intent i = new Intent(this, AboutModActivity.class);
            i.putExtra("select", "changelog");
            startActivity(i);
        }), v.full(-1, 8));
        list.addView(v.actionCard(R.drawable.ob_ic_help, "About Modders", "Who made Sketchware Pro and this fork",
                x -> start(AboutModActivity.class)), v.full(-1, 8));
        list.addView(v.actionCard(R.drawable.ob_ic_help, "Program information", "Licences and open-source parts",
                x -> start(ProgramInfoActivity.class)), v.full(-1, 8));
    }

    /**
     * v2.1.6 review (LOW): turned on, the notifications themselves must be allowed: Android 13+ asks for the permission
     * (as a Follow does); refused for good, or switched off for the app in Android's settings: that settings page opens.
     */
    private void askForNotifications() {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean permission = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                "android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (permission && (nm == null || nm.areNotificationsEnabled())) return;
        android.content.SharedPreferences p = getSharedPreferences("playstore_follows", MODE_PRIVATE);
        boolean refusedForGood = !permission && p.getBoolean("notices_requested", false)
                && !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS");
        if (!permission && !refusedForGood) {
            p.edit().putBoolean("notices_requested", true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7421);
            return;
        }
        android.widget.Toast.makeText(this, "Notifications are off for Sketchware AI: turn them on here.",
                android.widget.Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, "Open Android's Settings → Apps → Sketchware AI → Notifications.",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void start(Class<?> screen) {
        Intent i = new Intent(this, screen);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    /** The drawer's account card, on its own (the drawer header's second card). */
    private View accountCard() {
        View header = LayoutInflater.from(this).inflate(R.layout.main_drawer_header, list, false);
        View card = header.findViewById(R.id.drawer_account);
        ((ViewGroup) card.getParent()).removeView(card);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(-1, -2);
        card.setLayoutParams(lp);
        DrawerAccountCard.bind(card, GitHubSession.profile(this), new DrawerAccountCard.Actions() {
            @Override
            public void signIn() {
                GitHubInAppAuthDialog.show(SettingsActivity.this, login -> build());
            }

            @Override
            public void signOut() {
                new ObAlertBuilder(SettingsActivity.this, R.style.SketchwareAI_GlassAlertDialog)
                        .setTitle("Sign out of GitHub?")
                        .setMessage("Your backups on GitHub stay.")
                        .setPositiveButton("Sign out", (d, w) -> {
                            GitHubSession.signOut(SettingsActivity.this);
                            build();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        return card;
    }

    /** This version; "Latest" (a badge) or the update (in-app updater); "Coming soon: …" (a badge) when announced. */
    private View updatesCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16);
        card.setPadding(v.dp(14), v.dp(12), v.dp(12), v.dp(12));
        AppControl.Config config = AppControl.cached(this);
        boolean newer = AppUpdater.newerAvailable(config);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView version = new TextView(this, null, 0, R.style.Ob_Text_Label);
        // the version without the build's hash and flavour ("v2.1.0"): it fits beside the Update pill
        version.setText("Sketchware AI " + com.sketchware.remod.BuildConfig.VERSION_NAME.replaceFirst("-minApi\\d+$", "")
                .replaceFirst("-[0-9a-z]+$", ""));
        version.setTypeface(v.bold());
        GitHubViews.oneLine(version);
        top.addView(version, new LinearLayout.LayoutParams(0, -2, 1f));
        if (newer) {
            String name = config.latestVersionName != null ? config.latestVersionName : "the new version";
            TextView update = v.smallPill("Update to " + name, true, true, x -> AppUpdater.start(this, config));
            GitHubViews.oneLine(update);
            top.addView(update, new LinearLayout.LayoutParams(-2, v.dp(34)));
        } else {
            top.addView(badge(config == null ? "Not checked yet" : "Latest", config != null), new LinearLayout.LayoutParams(-2, v.dp(26)));
        }
        card.addView(top);
        String build = "Build " + com.sketchware.remod.BuildConfig.VERSION_NAME + " (" + com.sketchware.remod.BuildConfig.VERSION_CODE + "). ";
        TextView note = v.line(build + (newer ? "Downloaded in the app and checked (SHA-256) before Android's installer opens."
                : config == null ? "Checked at the next start with a network." : "You have the newest version."), false);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        card.addView(note, v.full(-1, 4));
        if (config != null && !config.comingSoon.isEmpty()) {
            LinearLayout soon = new LinearLayout(this);
            soon.setOrientation(LinearLayout.HORIZONTAL);
            soon.setGravity(Gravity.CENTER_VERTICAL);
            soon.addView(badge("Coming soon", true), new LinearLayout.LayoutParams(-2, v.dp(26)));
            TextView what = v.line(config.comingSoon, false);
            what.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
            wp.leftMargin = v.dp(8);
            soon.addView(what, wp);
            card.addView(soon, v.full(-1, 8));
        }
        return card;
    }

    /** A label on a pill: not a button. */
    private TextView badge(String text, boolean accent) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTypeface(v.bold());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(v.color(accent ? R.color.ob_accent : R.color.ob_text_2));
        t.setBackgroundResource(R.drawable.ob_pill16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(v.dp(10), 0, v.dp(10), 0);
        t.setClickable(false);
        t.setFocusable(false);
        GitHubViews.oneLine(t);
        return t;
    }

    private TextView title(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        GitHubViews.oneLine(t);
        return t;
    }

    private void themeDialog() {
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("App Theme")
                .setSingleChoiceItems(ThemeMode.LABELS, ThemeMode.get(this), (dialog, which) -> {
                    dialog.dismiss();
                    boolean full = ThemeMode.set(this, which);
                    if (!full) {
                        mod.SketchwareUtil.toast("Choosing Light or Dark needs Android 12+. "
                                + "On this phone the theme follows the system setting.", android.widget.Toast.LENGTH_LONG);
                    }
                    if (android.os.Build.VERSION.SDK_INT < 31) recreate();
                    else build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
