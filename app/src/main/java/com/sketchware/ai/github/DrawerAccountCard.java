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
        TextView email = card.findViewById(R.id.drawer_account_email);
        TextView action = card.findViewById(R.id.drawer_account_action);
        GitHubViews v = new GitHubViews(card.getContext());
        if (profile == null) {
            v.showPicture(picture, null);
            title.setText("Sign in with GitHub");
            subtitle.setText("Sync & back up projects");
            email.setVisibility(View.GONE);
            action.setVisibility(View.GONE);
            card.setOnClickListener(x -> actions.signIn());
            card.setContentDescription("Sign in with GitHub");
            return;
        }
        v.showPicture(picture, picture(card, profile));
        boolean named = profile.name != null && !profile.name.trim().isEmpty();
        title.setText(named ? profile.name.trim() : "@" + profile.login);
        subtitle.setText(named ? "@" + profile.login : "GitHub account");
        if (profile.email != null && !profile.email.isEmpty()) {
            email.setText(profile.email);
            email.setVisibility(View.VISIBLE);
        } else {
            email.setVisibility(View.GONE);
        }
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(x -> actions.signOut());
        card.setOnClickListener(null);
        card.setClickable(false);
        card.setContentDescription("GitHub account @" + profile.login);
    }

    private static Drawable picture(View card, GitHubSession.Profile profile) {
        if (profile.avatar == null) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(profile.avatar.getAbsolutePath());
        return bitmap == null ? null : new BitmapDrawable(card.getResources(), bitmap);
    }
}
