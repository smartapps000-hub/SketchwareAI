package com.sketchware.ai.github;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.ai.ui.TabScreens;
import com.sketchware.remod.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v2.1.0 (owner's 16 Nov directive "Community hub via GitHub Discussions"): the Community tab of the bottom bar shows
 * the discussions of Sketchware AI's own repository ({@value #OWNER}/{@value #REPO}): newest first, a new post in one of
 * its categories, and each discussion with its replies and a reply box (the same page shows a Play Store app's review).
 * GitHub shows Discussions to signed-in accounts only (GraphQL), so a signed-out phone gets the sign-in card; a
 * repository whose Discussions are off says so with Open on GitHub (its owner switches them on in the repository's
 * Settings, Features, Discussions).
 */
public class CommunityActivity extends BaseAppCompatActivity {

    static final String OWNER = "smartapps000-hub";
    static final String REPO = "SketchwareAI";
    static final String EXTRA_OWNER = "community_owner";
    static final String EXTRA_REPO = "community_repo";
    static final String EXTRA_NUMBER = "community_number";
    static final String EXTRA_TITLE = "community_title";

    private GitHubViews v;
    private LinearLayout list;
    private LinearLayout box;
    private String owner;
    private String repo;
    private int number;
    private String[] info;
    private final ExecutorService work = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Community");
        t.setDaemon(true);
        return t;
    });

    public static Intent tab(Activity activity) {
        return TabScreens.asTab(new Intent(activity, CommunityActivity.class), "Community");
    }

    static Intent thread(Activity activity, String owner, String repo, int number, String title) {
        return new Intent(activity, CommunityActivity.class).putExtra(EXTRA_OWNER, owner).putExtra(EXTRA_REPO, repo)
                .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_TITLE, title);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        findViewById(R.id.gs_back).setOnClickListener(x -> onBackPressed());
        owner = getIntent().getStringExtra(EXTRA_OWNER);
        repo = getIntent().getStringExtra(EXTRA_REPO);
        number = getIntent().getIntExtra(EXTRA_NUMBER, 0);
        if (owner == null || repo == null) {
            owner = OWNER;
            repo = REPO;
        }
        if (!owner.matches("[A-Za-z0-9-]{1,39}") || !repo.matches("[A-Za-z0-9_.-]{1,100}")) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        ((TextView) findViewById(R.id.gs_title)).setText(number > 0 ? (title == null ? "Discussion #" + number : title)
                : "Community");
        ((TextView) findViewById(R.id.gs_subtitle)).setText(number > 0 ? owner + "/" + repo + " · #" + number
                : "Sketchware AI's GitHub Discussions");
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        list.addView(box, v.full(-1, 0));
        load();
    }

    @Override
    public void onBackPressed() {
        if (TabScreens.onBack(this)) return;
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        work.shutdownNow();
        super.onDestroy();
    }

    private String token() {
        TokenStore store = TokenStore.get(this);
        if (store.login() == null || GitHubSession.isRefused(this)) return null;
        return store.token();
    }

    private void ui(Runnable r) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    private String webLink() {
        return "https://github.com/" + owner + "/" + repo + "/discussions" + (number > 0 ? "/" + number : "");
    }

    private void load() {
        box.removeAllViews();
        if (TokenStore.get(this).login() == null) {
            TextView in = v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, login -> load()));
            GitHubViews.oneLine(in);
            box.addView(v.row("Sign in to join the community", "GitHub shows Discussions to signed-in accounts only.", in));
            box.addView(openOnGitHub(), pill(10));
            return;
        }
        box.addView(v.line(number > 0 ? "Reading the discussion…" : "Reading the discussions…", false));
        work.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "GitHub didn't accept the saved sign-in: "
                        + "sign in again (Settings).");
                if (number > 0) {
                    List<PlayStore.Post> posts = GitHubHttp.quick(PlayStoreActivity.TIMEOUT_MILLIS,
                            () -> PlayStore.thread(token, owner, repo, number));
                    ui(() -> showThread(posts));
                } else {
                    String[] got = new String[3];
                    List<PlayStore.Post> posts = GitHubHttp.quick(PlayStoreActivity.TIMEOUT_MILLIS,
                            () -> PlayStore.posts(token, owner, repo, got));
                    ui(() -> {
                        info = got;
                        showList(posts);
                    });
                }
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    box.removeAllViews();
                    box.addView(v.line(why, true));
                    TextView retry = v.smallPill("Try again", false, true, x -> load());
                    GitHubViews.oneLine(retry);
                    box.addView(retry, wrapPill(8));
                    box.addView(openOnGitHub(), pill(10));
                });
            }
        });
    }

    private TextView openOnGitHub() {
        return v.glassPill("Open on GitHub", true, x -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webLink())));
            } catch (RuntimeException e) {
                Toast.makeText(this, "No browser on this phone", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private LinearLayout.LayoutParams pill(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, v.dp(46));
        p.topMargin = v.dp(topDp);
        return p;
    }

    private LinearLayout.LayoutParams wrapPill(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(34));
        p.topMargin = v.dp(topDp);
        return p;
    }

    private void showList(List<PlayStore.Post> posts) {
        box.removeAllViews();
        if (!"true".equals(info[1])) {
            box.addView(v.line("Community discussions are off for " + owner + "/" + repo + " right now: the repository's "
                    + "owner switches them on in Settings › Features › Discussions on GitHub.", false));
            box.addView(openOnGitHub(), pill(10));
            return;
        }
        TextView write = v.smallPill("New post", true, true, x -> newPost());
        GitHubViews.oneLine(write);
        box.addView(write, wrapPill(0));
        if (posts.isEmpty()) {
            box.addView(v.line("No discussions yet: start the first one.", false), v.full(-1, 10));
            return;
        }
        for (PlayStore.Post p : posts) {
            String detail = (p.category == null ? "" : p.category + " · ") + "@" + p.author
                    + (p.time > 0 ? " · " + CloudBackup.formatTime(p.time) : "")
                    + (p.comments > 0 ? " · " + p.comments + (p.comments == 1 ? " reply" : " replies") : "");
            LinearLayout row = v.row(p.title == null ? "(no title)" : p.title, detail);
            row.setBackgroundResource(R.drawable.ob_pill16_state);
            row.setOnClickListener(x -> startActivity(thread(this, owner, repo, p.number, p.title)));
            box.addView(row, v.full(-1, 8));
        }
    }

    private void showThread(List<PlayStore.Post> posts) {
        box.removeAllViews();
        for (int i = 0; i < posts.size(); i++) {
            PlayStore.Post p = posts.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.ob_pill16);
            card.setPadding(v.dp(14), v.dp(10), v.dp(14), v.dp(12));
            if (i == 0 && p.title != null) {
                TextView title = new TextView(this, null, 0, R.style.Ob_Text_Title);
                title.setText(p.title);
                card.addView(title);
            }
            TextView who = v.line("@" + p.author + (p.time > 0 ? " · " + CloudBackup.formatTime(p.time) : ""), false);
            who.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            card.addView(who);
            TextView body = v.line(p.body == null ? "" : p.body, false);
            body.setTextColor(v.color(R.color.ob_text));
            body.setTextIsSelectable(true);
            card.addView(body, v.full(-1, 6));
            box.addView(card, v.full(-1, i == 0 ? 0 : 8));
            if (i == 0 && posts.size() > 1) {
                TextView replies = new TextView(this, null, 0, R.style.Ob_Text_Title);
                replies.setText((posts.size() - 1) + (posts.size() == 2 ? " reply" : " replies"));
                box.addView(replies, v.full(-1, 18));
            }
        }
        String discussionId = posts.isEmpty() ? null : posts.get(0).id;
        if (discussionId == null) return;
        EditText reply = v.input("Write a reply", true, 5);
        box.addView(reply, v.full(-1, 18));
        TextView send = v.pill("Reply", true, true, x -> {
            String text = reply.getText().toString().trim();
            if (text.isEmpty()) return;
            sendReply(discussionId, text);
        });
        box.addView(send, pill(8));
        box.addView(openOnGitHub(), pill(8));
    }

    private void sendReply(String discussionId, String text) {
        if (!AuthGuard.check(this)) return;
        Toast.makeText(this, "Sending…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        PlayStoreActivity.WRITES.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "Sign in to reply.");
                PlayStore.reply(token, discussionId, text);
                PlayStoreActivity.toast(app, "Reply posted");
                ui(this::load);
            } catch (Exception e) {
                PlayStoreActivity.toast(app, GitHubImportDialog.text(e));
            }
        });
    }

    private void newPost() {
        if (info == null || !AuthGuard.check(this)) return;
        String[] lines = info[2].isEmpty() ? new String[0] : info[2].split("\n");
        if (lines.length == 0) {
            Toast.makeText(this, "These Discussions have no category to post in.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] ids = new String[lines.length];
        String[] names = new String[lines.length];
        int general = 0;
        for (int i = 0; i < lines.length; i++) {
            int tab = lines[i].indexOf('\t');
            ids[i] = tab > 0 ? lines[i].substring(0, tab) : lines[i];
            names[i] = tab > 0 ? lines[i].substring(tab + 1) : lines[i];
            if ("general".equalsIgnoreCase(names[i])) general = i;
        }
        int[] picked = {general};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(v.dp(20), v.dp(8), v.dp(20), 0);
        TextView category = v.glassPill("Category: " + names[general], true, null);
        category.setOnClickListener(x -> new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Category")
                .setSingleChoiceItems(names, picked[0], (d, which) -> {
                    picked[0] = which;
                    category.setText("Category: " + names[which]);
                    d.dismiss();
                })
                .show());
        form.addView(category, new LinearLayout.LayoutParams(-1, v.dp(42)));
        EditText title = v.input("Title", false, 1);
        form.addView(title, v.full(46, 10));
        EditText body = v.input("Your question, idea or project", true, 8);
        form.addView(body, v.full(-1, 10));
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("New post")
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    String t = title.getText().toString().trim();
                    String b = body.getText().toString().trim();
                    if (t.isEmpty() || b.isEmpty()) {
                        Toast.makeText(this, "A post needs a title and a few words.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    post(ids[picked[0]], t, b);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void post(String categoryId, String title, String body) {
        String repositoryId = info[0];
        Toast.makeText(this, "Posting…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        PlayStoreActivity.WRITES.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "Sign in to post.");
                int made = PlayStore.newPost(token, repositoryId, categoryId, title, body);
                PlayStoreActivity.toast(app, "Posted");
                ui(() -> {
                    if (made > 0) startActivity(thread(this, owner, repo, made, title));
                    load();
                });
            } catch (Exception e) {
                PlayStoreActivity.toast(app, GitHubImportDialog.text(e));
            }
        });
    }
}
