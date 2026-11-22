package com.sketchware.ai.github;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.sketchware.remod.R;

/**
 * v2.0.0 (owner's 13 Nov directive): the GitHub account card under the drawer's "Sketchware AI" card
 * ({@code main_drawer_header.xml}, {@code drawer_account*}). Signed out: the GitHub mark, "Sign in with GitHub", "Sync &
 * back up projects"; a tap opens the in-app sign-in window. Signed in: the account picture (else the mark), the profile
 * name (else @login), @login, the primary e-mail when GitHub gave it, and a "Sign out" glass pill. Plain view binding,
 * so layoutlib draws this code (V200Test).
 * <p>
 * v2.0.2 (owner's 14 Nov phone test: "Sign in with Gi…", "@cr…", "createn…"): signed out, the title wraps to a second
 * line instead of being cut.
 * <p>
 * v2.0.3 (owner's 15 Nov phone test: the marquee lines drew over each other, "wararao @l", "nail.com bammid"): the top row
 * holds the picture, "GitHub" with the sign-in's state and the Sign out pill; below it the name, @login, e-mail and
 * the account line (repositories, last check) each wrap onto as many lines as they need. When GitHub didn't accept the
 * saved sign-in (or this phone can't read it), the line says so in the error colour with "Sign in again": the session is
 * never removed by itself.
 */
public final class DrawerAccountCard {

    public interface Actions {
        void signIn();

        void signOut();
    }

    private DrawerAccountCard() {
    }

    public static void bind(View card, GitHubSession.Profile profile, Actions actions) {
        if (card == null) return;
        ImageView picture = card.findViewById(R.id.drawer_account_picture);
        TextView title = card.findViewById(R.id.drawer_account_title);
        TextView subtitle = card.findViewById(R.id.drawer_account_subtitle);
        TextView action = card.findViewById(R.id.drawer_account_action);
        View details = card.findViewById(R.id.drawer_account_details);
        TextView name = card.findViewById(R.id.drawer_account_name);
        TextView login = card.findViewById(R.id.drawer_account_login);
        TextView email = card.findViewById(R.id.drawer_account_email);
        View emailRow = card.findViewById(R.id.drawer_account_email_row);
        View copyLogin = card.findViewById(R.id.drawer_account_copy_login);
        View copyEmail = card.findViewById(R.id.drawer_account_copy_email);
        TextView status = card.findViewById(R.id.drawer_account_status);
        TextView renew = card.findViewById(R.id.drawer_account_renew);
        TextView signOut = card.findViewById(R.id.drawer_account_signout);
        GitHubViews v = new GitHubViews(card.getContext());
        if (profile == null) {
            v.showPicture(picture, null);
            // signed out: the title may take two lines (never cut)
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            title.setSingleLine(false);
            title.setMaxLines(2);
            subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            subtitle.setSingleLine(false);
            subtitle.setMaxLines(2);
            title.setText("Sign in with GitHub");
            // v2.1.2 (owner's 21 Nov report: signed out after an update): why this phone is signed out, when it is known
            String why = SessionLog.signedOutLine();
            subtitle.setMaxLines(why == null ? 2 : 4);
            subtitle.setText(why == null ? "Sync & back up projects" : "Sync & back up projects. " + why);
            subtitle.setTextColor(v.color(R.color.ob_text_2));
            action.setVisibility(View.GONE);
            details.setVisibility(View.GONE);
            if (signOut != null) signOut.setVisibility(View.GONE);
            card.setOnClickListener(x -> actions.signIn());
            card.setClickable(true);
            card.setContentDescription("Sign in with GitHub");
            return;
        }
        v.showPicture(picture, picture(card, profile));
        // the top row: the picture, "GitHub" and its state, Sign out
        // v2.0.4 (owner's 16 Nov phone test: "GitHu-b", "Connect-ed" beside Sign out): smaller, one line each
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        title.setSingleLine(false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        // v2.1.0 (owner's 16 Nov phone report: "Sign-in not accepted" drawn over the next line): no single-line
        // scrolling text; the refused state may take two lines and pushes the rest down
        boolean trouble = profile.problem != null;
        subtitle.setSingleLine(false);
        subtitle.setMaxLines(trouble ? 2 : 1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setIncludeFontPadding(true);
        title.setText("GitHub");
        subtitle.setText(trouble ? "Sign-in not accepted" : "Connected");
        subtitle.setTextColor(v.color(trouble ? R.color.ob_error : R.color.ob_text_2));
        // v2.0.5: Sign out sits under the details (the top row keeps "GitHub" and its state whole)
        action.setVisibility(View.GONE);
        signOut.setVisibility(View.VISIBLE);
        signOut.setOnClickListener(x -> actions.signOut());
        // the details, each on its own lines, wrapping (nothing cut, nothing drawn over)
        details.setVisibility(View.VISIBLE);
        // v2.0.5: the name line only when GitHub has a name (else @username showed twice); @username and the e-mail
        // on one line each (middle ellipsis: both ends stay visible), the copy buttons copy them whole
        boolean named = profile.name != null && !profile.name.trim().isEmpty();
        if (named) {
            wrap(name, profile.name.trim());
            name.setVisibility(View.VISIBLE);
        } else {
            name.setVisibility(View.GONE);
        }
        oneLine(login, "@" + profile.login);
        copyLogin.setOnClickListener(x -> copy(card, "GitHub user name", profile.login));
        if (profile.email != null && !profile.email.isEmpty()) {
            oneLine(email, profile.email);
            emailRow.setVisibility(View.VISIBLE);
            copyEmail.setOnClickListener(x -> copy(card, "E-mail", profile.email));
        } else {
            emailRow.setVisibility(View.GONE);
        }
        if (trouble) {
            wrap(status, profile.problem);
            status.setTextColor(v.color(R.color.ob_error));
            // v2.1.4 (owner's 22 Nov report): GitHub's own record of why it ended the sign-in, one tap away; review
            // (LOW): only when GitHub refused it (not when this phone can't read the saved sign-in)
            if (GitHubSession.isRefused(card.getContext())) {
                whyLink(status, profile.problem);
            } else {
                status.setMovementMethod(null);
            }
            renew.setVisibility(View.VISIBLE);
            renew.setOnClickListener(x -> actions.signIn());
        } else {
            StringBuilder line = new StringBuilder();
            if (profile.repositories >= 0) {
                line.append(profile.repositories).append(profile.repositories == 1 ? " repository" : " repositories");
            }
            if (profile.checkedAt > 0) {
                if (line.length() > 0) line.append(" · ");
                line.append("checked ").append(CloudBackup.formatTime(profile.checkedAt));
            }
            if (line.length() == 0) line.append("Backups go to your private repository ").append(CloudBackup.REPO);
            wrap(status, line.toString());
            status.setMovementMethod(null);
            status.setTextColor(v.color(R.color.ob_text_2));
            renew.setVisibility(View.GONE);
        }
        // review (LOW): with Sign in again beside it both pills share the row (two lines rather than cut at a large
        // font); alone, Sign out keeps its own width
        android.widget.LinearLayout.LayoutParams renewParams = (android.widget.LinearLayout.LayoutParams) renew.getLayoutParams();
        android.widget.LinearLayout.LayoutParams outParams = (android.widget.LinearLayout.LayoutParams) signOut.getLayoutParams();
        boolean both = renew.getVisibility() == View.VISIBLE;
        renewParams.width = both ? 0 : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        renewParams.weight = both ? 1 : 0;
        outParams.width = both ? 0 : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        outParams.weight = both ? 1 : 0;
        renew.setLayoutParams(renewParams);
        signOut.setLayoutParams(outParams);
        card.setOnClickListener(null);
        card.setClickable(false);
        card.setContentDescription("GitHub account @" + profile.login);
    }

    /**
     * v2.1.4: "Why? GitHub's security log ›" after the refusal's words: GitHub's own record (sign-ins ended, by whom and
     * why) opens in the browser.
     */
    private static void whyLink(TextView status, String problem) {
        String why = "Why? GitHub's security log ›";
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(problem).append("  ");
        int start = text.length();
        text.append(why);
        text.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                try {
                    widget.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(GitHubSession.SECURITY_LOG_URL))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    android.widget.Toast.makeText(widget.getContext(), "No browser to open GitHub's security log",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                ds.setColor(0xFFFFB877);
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
            }
        }, start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setText(text);
        status.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    /** v2.0.5: one line, never wrapped; a long value keeps its start and end ("bammidieswar…@gmail.com"). */
    private static void oneLine(TextView t, String text) {
        t.setSelected(false);
        t.setSingleLine(true);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        t.setText(text);
    }

    /** v2.0.4: one tap copies (a toast says what). */
    private static void copy(View card, String what, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) card.getContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText(what, text));
        android.widget.Toast.makeText(card.getContext(), what + " copied", android.widget.Toast.LENGTH_SHORT).show();
    }

    /** All of the text on as many lines as it needs: long words (an e-mail) break inside instead of being cut. */
    private static void wrap(TextView t, String text) {
        t.setSelected(false);
        t.setSingleLine(false);
        t.setMaxLines(Integer.MAX_VALUE);
        t.setEllipsize(null);
        t.setHorizontallyScrolling(false);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }
        t.setText(text);
    }

    private static Drawable picture(View card, GitHubSession.Profile profile) {
        if (profile.avatar == null) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(profile.avatar.getAbsolutePath());
        return bitmap == null ? null : new BitmapDrawable(card.getResources(), bitmap);
    }
}
