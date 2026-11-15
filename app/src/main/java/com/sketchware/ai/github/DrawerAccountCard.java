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
        TextView status = card.findViewById(R.id.drawer_account_status);
        TextView renew = card.findViewById(R.id.drawer_account_renew);
        GitHubViews v = new GitHubViews(card.getContext());
        if (profile == null) {
            v.showPicture(picture, null);
            title.setText("Sign in with GitHub");
            subtitle.setText("Sync & back up projects");
            subtitle.setTextColor(v.color(R.color.ob_text_2));
            action.setVisibility(View.GONE);
            details.setVisibility(View.GONE);
            card.setOnClickListener(x -> actions.signIn());
            card.setClickable(true);
            card.setContentDescription("Sign in with GitHub");
            return;
        }
        v.showPicture(picture, picture(card, profile));
        // the top row: the picture, "GitHub" and its state, Sign out
        title.setText("GitHub");
        boolean trouble = profile.problem != null;
        subtitle.setText(trouble ? "Sign-in not accepted" : "Connected");
        subtitle.setTextColor(v.color(trouble ? R.color.ob_error : R.color.ob_text_2));
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(x -> actions.signOut());
        // the details, each on its own lines, wrapping (nothing cut, nothing drawn over)
        details.setVisibility(View.VISIBLE);
        boolean named = profile.name != null && !profile.name.trim().isEmpty();
        wrap(name, named ? profile.name.trim() : "@" + profile.login);
        wrap(login, named ? "@" + profile.login : "GitHub account");
        if (profile.email != null && !profile.email.isEmpty()) {
            wrap(email, profile.email);
            email.setVisibility(View.VISIBLE);
        } else {
            email.setVisibility(View.GONE);
        }
        if (trouble) {
            wrap(status, profile.problem);
            status.setTextColor(v.color(R.color.ob_error));
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
            status.setTextColor(v.color(R.color.ob_text_2));
            renew.setVisibility(View.GONE);
        }
        card.setOnClickListener(null);
        card.setClickable(false);
        card.setContentDescription("GitHub account @" + profile.login);
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
