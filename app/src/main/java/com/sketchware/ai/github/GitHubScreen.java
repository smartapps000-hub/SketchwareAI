package com.sketchware.ai.github;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sketchware.remod.R;

import java.util.List;

/**
 * v1.9.8, redone in v1.9.9 (owner's 12 Nov directive): the cards of "Cloud Sync & Account", built from a {@link State}.
 * The account card has the look of the drawer's "Sketchware AI / Android 17 ready" card: a glass pill ({@code ob_glass}),
 * an orange tile badge ({@code ob_tile_orange}) on the left, a Sora title with one grey line, and the deep-orange pill
 * button. Then the user's projects, each with Back up and Restore, and the backups that are only on GitHub. Short
 * lines only. Kept apart from {@link GitHubActivity} so layoutlib draws exactly this code (tools/layoutlib-renders
 * V199Test).
 */
public final class GitHubScreen {

    /** One project row. */
    public static final class Row {
        public String title;
        public String detail;
        public boolean canBackUp;
        public boolean canRestore;
        /** "Backing up…" while this row's backup runs, else null. */
        public String working;
    }

    /** What the screen shows. */
    public static final class State {
        public boolean supported = true;
        /** Opened because sign-in is required: no way around it but signing in. */
        public boolean gate;
        /** The signed-in user's login, or null. */
        public String login;
        public Drawable avatar;
        /** The Device Flow code being shown, or null. */
        public String userCode;
        /** GitHub approved: reading the account. */
        public boolean finishing;
        /** One short line under the card that is busy (progress or the last error). */
        public String status;
        public boolean statusIsError;
        /** A long press on an error line copies this (the technical cause). */
        public String statusDetail;
        public boolean busy;
        public boolean loadingProjects;
        public String projectsError;
        /** The phone's projects; null when not loaded. */
        public List<Row> projects;
        /** Backups whose project isn't on this phone. */
        public List<Row> cloudOnly;
    }

    public interface Actions {
        void signIn();

        void openVerification();

        void cancelSignIn();

        void signOut();

        void backUp(int index);

        void restore(int index);

        void restoreCloudOnly(int index);

        void refresh();

        void copyDetails();
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
        LinearLayout account = glassCard();
        if (s.login == null) {
            if (!s.supported) {
                account.addView(header(null, "GitHub", "Needs Android 6.0 or newer"));
            } else if (s.finishing) {
                account.addView(header(null, "Almost done…", "Reading your GitHub account"));
            } else if (s.userCode != null) {
                account.addView(header(null, "Enter this code on GitHub", "Code copied — paste it on GitHub"));
                TextView code = new TextView(c, null, 0, R.style.Ob_Text_Screen);
                code.setText(s.userCode);
                code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                code.setLetterSpacing(0.08f);
                code.setTextIsSelectable(true);
                account.addView(code, centred(-2, -2, 14));
                account.addView(pillButton("Open GitHub", true, true, v -> actions.openVerification()), full(dp(52), 14));
                account.addView(textButton("Cancel", v -> actions.cancelSignIn()), centred(-2, dp(40), 6));
            } else {
                account.addView(header(null, "GitHub", s.gate ? "Sign in to continue" : "Back up & sync your projects"));
                account.addView(pillButton(s.busy ? "Signing in…" : "Sign in", true, !s.busy, v -> actions.signIn()),
                        full(dp(52), 16));
            }
            if (s.status != null) account.addView(statusLine(s), full(-2, 12));
            list.addView(account, cardParams(8));
            return;
        }

        // signed in: who, then the projects
        LinearLayout who = header(s.avatar, "@" + s.login, "Signed in");
        // a GitHub login has up to 39 characters: a little smaller here, and cut with "…" only when it must be
        ((TextView) ((LinearLayout) who.getChildAt(1)).getChildAt(0)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        // not while a backup or restore is under way (it would lose its token half way)
        if (!s.busy) who.addView(smallPill("Sign out", false, true, v -> actions.signOut()), smallParams());
        account.addView(who);
        list.addView(account, cardParams(8));

        LinearLayout projects = glassCard();
        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c, null, 0, R.style.Ob_Text_Title);
        title.setText("Your projects");
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!s.busy && !s.loadingProjects) titleRow.addView(textButton("Refresh", v -> actions.refresh()),
                new LinearLayout.LayoutParams(-2, dp(36)));
        projects.addView(titleRow);
        if (s.status != null) projects.addView(statusLine(s), full(-2, 8));
        if (s.projectsError != null) projects.addView(left(line(s.projectsError, true)), full(-2, 8));
        if (s.projects == null) {
            projects.addView(left(line("Loading…", false)), full(-2, 10));
        } else if (s.projects.isEmpty()) {
            projects.addView(left(line("No projects on this phone yet", false)), full(-2, 10));
        } else {
            for (int i = 0; i < s.projects.size(); i++) {
                final int index = i;
                projects.addView(projectRow(s.projects.get(i), s.busy, v -> actions.backUp(index), v -> actions.restore(index)));
            }
        }
        list.addView(projects, cardParams(12));

        if (s.cloudOnly != null && !s.cloudOnly.isEmpty()) {
            LinearLayout only = glassCard();
            TextView onlyTitle = new TextView(c, null, 0, R.style.Ob_Text_Title);
            onlyTitle.setText("Only on GitHub");
            only.addView(onlyTitle);
            for (int i = 0; i < s.cloudOnly.size(); i++) {
                final int index = i;
                only.addView(projectRow(s.cloudOnly.get(i), s.busy, null, v -> actions.restoreCloudOnly(index)));
            }
            list.addView(only, cardParams(12));
        }
    }

    /** The drawer card's row: tile badge (or the avatar), title, one line. */
    private LinearLayout header(Drawable avatar, String title, String subtitle) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(c);
        if (avatar != null) {
            badge.setImageDrawable(avatar);
            badge.setScaleType(ImageView.ScaleType.CENTER_CROP);
            badge.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
                }
            });
            badge.setClipToOutline(true);
        } else {
            badge.setBackgroundResource(R.drawable.ob_tile_orange);
            badge.setImageResource(R.drawable.ob_ic_github);
            badge.setPadding(dp(10), dp(10), dp(10), dp(10));
        }
        badge.setContentDescription("GitHub");
        row.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c, null, 0, R.style.Ob_Text);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(c.getResources().getFont(R.font.sora_bold));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(t);
        TextView sub = new TextView(c, null, 0, R.style.Ob_Text_Caption);
        sub.setText(subtitle);
        sub.setPadding(0, dp(3), 0, 0);
        text.addView(sub);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(12);
        row.addView(text, textParams);
        return row;
    }

    private View projectRow(Row row, boolean busy, View.OnClickListener backUp, View.OnClickListener restore) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.ob_pill16);
        box.setPadding(dp(14), dp(10), dp(10), dp(10));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(c, null, 0, R.style.Ob_Text_Label);
        name.setText(row.title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name);
        TextView detail = left(line(row.working != null ? row.working : row.detail, false));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(2), 0, 0);
        text.addView(detail);
        box.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean enabled = !busy && row.working == null;
        if (backUp != null && row.canBackUp) {
            box.addView(smallPill("Back up", true, enabled, backUp), smallParams());
        }
        if (row.canRestore) {
            box.addView(smallPill("Restore", false, enabled, restore), smallParams());
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        box.setLayoutParams(params);
        return box;
    }

    private LinearLayout.LayoutParams smallParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(36));
        p.leftMargin = dp(6);
        return p;
    }

    /** Back up (orange) / Restore (glass): small pills on a project row. */
    private TextView smallPill(String text, boolean orange, boolean enabled, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, orange ? R.style.Ob_Text_Button_Cta : R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(orange ? R.drawable.ob_cta_pill_state : R.drawable.ob_pill16_state);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setEnabled(enabled);
        t.setAlpha(enabled ? 1f : 0.45f);
        if (enabled) {
            t.setStateListAnimator(AnimatorInflater.loadStateListAnimator(c, R.animator.sw_press));
            t.setOnClickListener(click);
        }
        return t;
    }

    private TextView statusLine(State s) {
        TextView t = line(s.status, s.statusIsError);
        if (s.statusIsError && s.statusDetail != null) {
            // for the owner's reports: the technical cause, without showing it to everyone
            t.setOnLongClickListener(v -> {
                actions.copyDetails();
                return true;
            });
        }
        return t;
    }

    // ------------------------------------------------------------------ views

    /** The drawer header's surface: ob_glass, 14dp padding. */
    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_glass);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        return card;
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

    /** The deep-orange pill (white label), with the GitHub mark when {@code icon}; dimmed while busy. */
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

    /** A quiet text button (Cancel, Sign out, Refresh). */
    private TextView textButton(String text, View.OnClickListener click) {
        TextView t = new TextView(c, null, 0, R.style.Ob_Text_Button);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(c.getResources().getColor(R.color.ob_text_2, c.getTheme()));
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setPadding(dp(14), 0, dp(14), 0);
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
