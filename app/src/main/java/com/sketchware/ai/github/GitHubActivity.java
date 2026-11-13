package com.sketchware.ai.github;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

/**
 * The sign-in screen {@link AuthGuard} opens when {@code config/app-control.json} requires sign-in ({@link #EXTRA_GATE}):
 * one glass card, "Sign in with GitHub" opens the in-app sign-in window ({@link GitHubInAppAuthDialog}), and the screen
 * closes itself once signed in. While sign-in is required, Back leaves the app. v2.0.0: the account and the backups
 * moved to the drawer's account card, each project's Back up and the Restore card's "Import from GitHub"; the project
 * list that was here (v1.9.9) is gone.
 */
public class GitHubActivity extends BaseAppCompatActivity {

    public static final String EXTRA_GATE = "sketchware_ai_gate";

    private LinearLayout list;
    private boolean gate;
    private GitHubInAppAuthDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github);
        ObsidianWindow.apply(this);
        list = findViewById(R.id.gh_list);
        gate = getIntent().getBooleanExtra(EXTRA_GATE, false);
        findViewById(R.id.gh_back).setOnClickListener(v -> onBackPressed());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_GATE, false)) gate = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (TokenStore.get(this).login() != null && AuthGuard.verifyHardwareBoundSession(this)) {
            // signed in (here or on another screen): nothing to ask for
            finish();
            return;
        }
        render();
    }

    @Override
    public void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (gate && AuthGuard.required(this) && !AuthGuard.verifyHardwareBoundSession(this)) {
            // the gate can't be dismissed: Back leaves the app
            finishAffinity();
            return;
        }
        super.onBackPressed();
    }

    private void render() {
        boolean required = gate && AuthGuard.required(this);
        ((TextView) findViewById(R.id.gh_subtitle)).setText(required ? "Sign in to continue" : "Not signed in");
        findViewById(R.id.gh_back).setVisibility(required ? View.INVISIBLE : View.VISIBLE);
        list.removeAllViews();
        GitHubViews v = new GitHubViews(this);
        LinearLayout card = v.glassCard();
        card.addView(v.header(null, "Sign in with GitHub", "Sync & back up projects"));
        card.addView(v.pill("Sign in with GitHub", true, true, x -> signIn()), v.full(48, 14));
        String error = SignIn.get(this).error();
        if (error != null) card.addView(v.line(error, true), v.full(-1, 10));
        list.addView(card, v.full(-1, 8));
    }

    private void signIn() {
        if (dialog != null && dialog.isShowing()) return;
        dialog = GitHubInAppAuthDialog.show(this, new GitHubInAppAuthDialog.Done() {
            @Override
            public void signedIn(String login) {
                AuthGuard.reset();
                finish();
            }

            @Override
            public void closed() {
                if (!isFinishing()) render();
            }
        });
    }
}
