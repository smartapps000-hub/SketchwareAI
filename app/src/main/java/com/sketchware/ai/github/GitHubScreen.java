package com.sketchware.ai.github;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

import java.util.List;

/**
 * v1.9.8: the GitHub screen's cards, built from a {@link State}: a compact glass card (24dp corners) with the GitHub
 * mark, "GitHub", one line and an orange "Sign in" pill; the one-time code while GitHub waits; the signed-in account
 * and cloud backup. Short lines only (the owner's 12 Nov ask: no paragraphs, no Client ID, no setup text for users).
 * Kept apart from {@link GitHubActivity} so layoutlib can draw exactly this code (tools/layoutlib-renders V198Test).
 */
public final class GitHubScreen {

    /** What the screen shows. */
    public static final class State {
        public boolean supported = true;
        /** The signed-in user's login, or null. */
        public String login;
        /** The Device Flow code being shown, or null. */
        public String userCode;
        /** One short line under the card that is busy (progress or the last error). */
        public String status;
        public boolean statusIsError;
        public boolean busy;
        public boolean loadingBackups;
        public String backupsError;
        /** {title, details} per backed-up project; null when not loaded. */
        public List<String[]> backups;
    }

    public interface Actions {
        void signIn();

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
        if (s.login == null) {
            LinearLayout card = centredCard();
            card.addView(mark(), centred(dp(56), dp(56), 0));
            if (!s.supported) {
                card.addView(heading("GitHub"), centred(-2, -2, 14));
                card.addView(line("Needs Android 6.0 or newer", false), centred(-2, -2, 6));
            } else if (s.userCode != null) {
                card.addView(heading("Enter this code on GitHub"), centred(-2, -2, 14));
                TextView code = new TextView(c, null, 0, R.style.Ob_Text_Screen);
                code.setText(s.userCode);
                code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                code.setLetterSpacing(0.08f);
                code.setTextIsSelectable(true);
                card.addView(code, centred(-2, -2, 12));
                card.addView(line(s.status != null ? s.status : "Code copied — paste it on GitHub", s.statusIsError),
                        centred(-2, -2, 6));
                card.addView(pillButton("Open GitHub", true, true, v -> actions.openVerification()), centred(dp(220), dp(52), 18));
                card.addView(textButton("Cancel", v -> actions.cancelSignIn()), centred(-2, dp(40), 6));
            } else {
                card.addView(heading("GitHub"), centred(-2, -2, 14));
                card.addView(line("Back up & sync your projects", false), centred(-2, -2, 4));
                card.addView(pillButton(s.busy ? "Signing in…" : "Sign in", true, !s.busy, v -> actions.signIn()),
                        centred(dp(220), dp(52), 22));
                if (s.status != null) card.addView(line(s.status, s.statusIsError), centred(-2, -2, 12));
            }
            list.addView(card, cardParams(40));
            return;
        }

        // signed in: the account, then cloud backup
        LinearLayout account = card();
        account.setOrientation(LinearLayout.HORIZONTAL);
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.addView(mark(), new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout who = new LinearLayout(c);
        who.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(c, null, 0, R.style.Ob_Text_Title);
        name.setText("@" + s.login);
        name.setSingleLine(true);
        who.addView(name);
        who.addView(left(line("Signed in", false)));
        LinearLayout.LayoutParams whoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        whoParams.leftMargin = dp(12);
        account.addView(who, whoParams);
        // not while a backup or restore is under way (it would lose its token half way)
        if (!s.busy) account.addView(textButton("Sign out", v -> actions.signOut()), new LinearLayout.LayoutParams(-2, dp(40)));
        list.addView(account, cardParams(12));

        LinearLayout cloud = card();
        cloud.addView(title("Cloud backup"));
        cloud.addView(pillButton(s.busy ? "Working…" : "Back up a project", false, !s.busy, v -> actions.backUp()),
                full(dp(52), 12));
        if (s.status != null) cloud.addView(left(line(s.status, s.statusIsError)), full(-2, 10));
        if (s.loadingBackups) {
            cloud.addView(left(line("Loading backups…", false)), full(-2, 12));
        } else if (s.backupsError != null) {
            cloud.addView(left(line(s.backupsError, true)), full(-2, 12));
            cloud.addView(textButton("Try again", v -> actions.refreshBackups()), full(dp(40), 4));
        } else if (s.backups != null && !s.backups.isEmpty()) {
            for (int i = 0; i < s.backups.size(); i++) cloud.addView(backupRow(s.backups.get(i), i, !s.busy));
            cloud.addView(left(line("Tap a project to restore it", false)), full(-2, 10));
        }
        list.addView(cloud, cardParams(12));
    }

    /** A backed-up project; dimmed and not clickable while a backup or restore is under way. */
    private View backupRow(String[] backup, int index, boolean enabled) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16_state);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.55f);
        if (enabled) {
            row.setClickable(true);
            row.setOnClickListener(v -> actions.openBackup(index));
        }
        TextView name = new TextView(c, null, 0, R.style.Ob_Text_Label);
        name.setText(backup[0]);
        name.setSingleLine(true);
        row.addView(name);
        TextView details = left(line(backup[1], false));
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setSingleLine(true);
        details.setPadding(0, dp(2), 0, 0);
        row.addView(details);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    // ------------------------------------------------------------------ views

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_gh_card);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        return card;
    }

    private LinearLayout centredCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(26));
        return card;
    }

    private ImageView mark() {
        ImageView mark = new ImageView(c);
        mark.setImageResource(R.drawable.ob_ic_github);
        mark.setContentDescription("GitHub");
        return mark;
    }

    private TextView heading(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Screen);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView title(String text) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        return t;
    }

    /** One short line: grey, or the error colour. */
    private TextView line(String text, boolean error) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        if (error) t.setTextColor(c.getResources().getColor(R.color.ob_error, c.getTheme()));
        return t;
    }

    private TextView left(TextView t) {
        t.setGravity(Gravity.START);
        return t;
    }

    /** The orange pill (white label), with the GitHub mark when {@code icon}; dimmed and not clickable while busy. */
    private TextView pillButton(String text, boolean icon, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button_Cta);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_cta_pill_state);
        t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
        t.setPadding(dp(22), 0, dp(22), 0);
        if (icon) {
            Drawable mark = c.getDrawable(R.drawable.ob_ic_github);
            if (mark != null) {
                mark.setBounds(0, 0, dp(20), dp(20));
                t.setCompoundDrawables(mark, null, null, null);
                t.setCompoundDrawablePadding(dp(10));
            }
        }
        t.setEnabled(enabled);
        t.setAlpha(enabled ? 1f : 0.55f);
        if (enabled) t.setOnClickListener(click);
        return t;
    }

    /** A quiet text button (Cancel, Sign out, Try again). */
    private TextView textButton(String text, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(c.getResources().getColor(R.color.ob_text_2, c.getTheme()));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setOnClickListener(click);
        return t;
    }

    private LinearLayout.LayoutParams centred(int width, int height, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams full(int height, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams cardParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
