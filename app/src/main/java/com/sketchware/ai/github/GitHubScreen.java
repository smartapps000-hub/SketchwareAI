package com.sketchware.ai.github;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

import java.util.List;

/**
 * v1.9.8: the cards of the GitHub screen (account, cloud backup, backups in the repository), built from a {@link State}.
 * Kept apart from {@link GitHubActivity} so layoutlib can draw exactly this code (tools/layoutlib-renders V198Test).
 * Only the Obsidian resources: ob_accent_box cards, ob_cta_state call-to-action, ob_pill16_state pills, Ob.Text.*.
 */
public final class GitHubScreen {

    /** What the screen shows. */
    public static final class State {
        public boolean supported = true;
        /** The signed-in user's login, or null. */
        public String login;
        /** The Device Flow code being shown, or null. */
        public String userCode;
        /** One line under the card that is busy (progress or the last error). */
        public String status;
        public boolean statusIsError;
        public boolean busy;
        public String clientId = "";
        public String repository = "SketchwareAI-Projects";
        public boolean loadingBackups;
        public String backupsError;
        /** {title, details} per backed-up project; null when not loaded. */
        public List<String[]> backups;
    }

    public interface Actions {
        void signIn(String clientId);

        void openVerification();

        void cancelSignIn();

        void signOut();

        void backUp();

        void refreshBackups();

        void openBackup(int index);
    }

    private final Context c;
    private final Actions actions;

    private GitHubScreen(Context context, Actions actions) {
        this.c = context;
        this.actions = actions;
    }

    /** Replaces the children of {@code list} with the cards for {@code s}. */
    public static void render(Context context, LinearLayout list, State s, Actions actions) {
        new GitHubScreen(context, actions).build(list, s);
    }

    private void build(LinearLayout list, State s) {
        list.removeAllViews();
        LinearLayout account = card();
        account.addView(title("Account"));
        if (!s.supported) {
            account.addView(body("GitHub sign-in needs Android 6.0 or newer (its token is kept in the Android Keystore)."));
        } else if (s.login != null) {
            account.addView(body("Signed in as @" + s.login + ". The access token is encrypted with a key in this phone's "
                    + "Android Keystore; it is sent only to api.github.com."));
            // not while a backup or restore is under way (it would lose its token half way)
            if (!s.busy) account.addView(pill("Sign out", v -> actions.signOut()));
        } else if (s.userCode != null) {
            account.addView(body("On github.com/login/device, enter this code:"));
            TextView code = new TextView(c, null, 0, R.style.Ob_Text_Screen);
            code.setText(s.userCode);
            code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            code.setLetterSpacing(0.08f);
            code.setGravity(Gravity.CENTER);
            code.setTextIsSelectable(true);
            code.setPadding(0, dp(10), 0, dp(10));
            account.addView(code, matchWrap());
            account.addView(cta("Copy code and open GitHub", true, v -> actions.openVerification()));
            account.addView(s.statusIsError && s.status != null ? error(s.status)
                    : caption(s.status == null ? "Waiting for you to approve on GitHub…" : s.status));
            account.addView(pill("Cancel", v -> actions.cancelSignIn()));
        } else {
            account.addView(body("Sign in with your GitHub account to back up projects to a private repository of yours ("
                    + s.repository + "). You enter a one-time code on github.com; no password is typed here. "
                    + "GitHub asks you to allow repository access (the \"repo\" permission: GitHub can't limit an app to "
                    + "one repository)."));
            EditText clientId = new EditText(c, null, 0, R.style.Ob_Field);
            clientId.setHint("OAuth app Client ID");
            clientId.setSingleLine(true);
            // built in code, the field doesn't get the theme's EditText style (which centres the text)
            clientId.setGravity(Gravity.CENTER_VERTICAL);
            clientId.setTextColor(c.getResources().getColor(R.color.ob_text, c.getTheme()));
            clientId.setHintTextColor(c.getResources().getColor(R.color.ob_text_2, c.getTheme()));
            clientId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            clientId.setText(s.clientId);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            fieldParams.topMargin = dp(10);
            account.addView(clientId, fieldParams);
            account.addView(caption("The Client ID of the GitHub OAuth app (github.com > Settings > Developer settings > "
                    + "OAuth Apps), with \"Enable Device Flow\" ticked. It is public; no client secret is used."));
            if (s.status != null) account.addView(s.statusIsError ? error(s.status) : caption(s.status));
            account.addView(cta(s.busy ? "Starting…" : "Sign in with GitHub", !s.busy,
                    v -> actions.signIn(clientId.getText().toString())));
        }
        list.addView(account, cardParams());

        if (s.login == null) return;
        LinearLayout cloud = card();
        cloud.addView(title("Cloud backup"));
        cloud.addView(body("Projects are saved as .swb files in the private repository " + s.login + "/" + s.repository
                + ". Every backup is a commit, so older backups stay and can be restored. A restore always makes a "
                + "new project; existing projects are never overwritten."));
        cloud.addView(cta(s.busy ? "Working…" : "Back up a project", !s.busy, v -> actions.backUp()));
        if (s.status != null) cloud.addView(s.statusIsError ? error(s.status) : caption(s.status));
        list.addView(cloud, cardParams());

        LinearLayout saved = card();
        saved.addView(title("Backups in your repository"));
        if (s.loadingBackups) {
            saved.addView(caption("Loading…"));
        } else if (s.backupsError != null) {
            saved.addView(error(s.backupsError));
            saved.addView(pill("Try again", v -> actions.refreshBackups()));
        } else if (s.backups == null || s.backups.isEmpty()) {
            saved.addView(caption("No backups yet."));
        } else {
            for (int i = 0; i < s.backups.size(); i++) saved.addView(backupRow(s.backups.get(i), i));
            saved.addView(caption("Tap a backup to restore it (or an older one) as a new project."));
            saved.addView(pill("Refresh", v -> actions.refreshBackups()));
        }
        list.addView(saved, cardParams());
    }

    private View backupRow(String[] backup, int index) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16_state);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setClickable(true);
        row.setOnClickListener(v -> actions.openBackup(index));
        TextView name = new TextView(c, null, 0, R.style.Ob_Text_Label);
        name.setText(backup[0]);
        row.addView(name);
        TextView details = caption(backup[1]);
        details.setPadding(0, dp(2), 0, 0);
        row.addView(details);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_accent_box);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        return params;
    }

    private TextView title(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Body);
        t.setText(text);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private TextView caption(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private TextView error(String text) {
        TextView t = caption(text);
        t.setTextColor(c.getResources().getColor(R.color.ob_error, c.getTheme()));
        return t;
    }

    /** The orange call-to-action (white label); dimmed and not clickable while the screen is busy. */
    private TextView cta(String text, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_cta_state);
        t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setEnabled(enabled);
        t.setAlpha(enabled ? 1f : 0.55f);
        if (enabled) t.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.topMargin = dp(12);
        t.setLayoutParams(params);
        return t;
    }

    private TextView pill(String text, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.topMargin = dp(10);
        t.setLayoutParams(params);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
