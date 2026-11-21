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
 * v2.1.0 (owner's 16 Nov directive "Community hub via GitHub Discussions"), v2.1.1 (owner's 21 Nov report: "Open on
 * GitHub" led to a 404 because Sketchware AI's own Discussions are off): the Community tab of the bottom bar is a feed
 * built in the app ({@link PlayStore#feed}): the Discussions of Sketchware AI's repository when they are on, of the Play
 * Store's apps, of repositories with the {@value PlayStore#COMMUNITY_TOPIC} topic and of the account's own repositories
 * with Discussions on, newest first, kept on the phone for offline reading. A post opens as a thread with its replies
 * and a reply box (an app's review opens the same way); New post picks one of the repositories whose Discussions are
 * on, then its category. GitHub shows Discussions to signed-in accounts only (GraphQL), so a signed-out phone gets the
 * sign-in card and the copy kept on the phone. Nothing links to a Discussions page that is off.
 */
public class CommunityActivity extends BaseAppCompatActivity {

    static final String OWNER = PlayStore.COMMUNITY_OWNER;
    static final String REPO = PlayStore.COMMUNITY_REPO;
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
    private PlayStore.Feed feed;
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
        if (number > 0 && (owner == null || repo == null || !PlayStoreActivity.validLogin(owner)
                || !repo.matches("[A-Za-z0-9_.-]{1,100}"))) {
            finish();
            return;
        }
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        ((TextView) findViewById(R.id.gs_title)).setText(number > 0 ? (title == null ? "Discussion #" + number : title)
                : "Community");
        ((TextView) findViewById(R.id.gs_subtitle)).setText(number > 0 ? where(owner, repo) + " · #" + number
                : "Discussions on GitHub: Sketchware AI and its apps");
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        list.addView(box, v.full(-1, 0));
        load();
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

    /** "Sketchware AI" for the official repository, else the app's name and its creator. */
    static String where(String owner, String repo) {
        if (OWNER.equalsIgnoreCase(owner) && REPO.equalsIgnoreCase(repo)) return "Sketchware AI";
        return PlayStore.displayName(repo) + " · @" + owner;
    }

    private void load() {
        if (number > 0) loadThread();
        else loadFeed();
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

    // ------------------------------------------------------------------ the feed

    private TextView feedStatus;
    private LinearLayout feedActions;
    private LinearLayout feedBox;

    private void loadFeed() {
        box.removeAllViews();
        String login = TokenStore.get(this).login();
        if (login == null) {
            TextView in = v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, l -> load()));
            GitHubViews.oneLine(in);
            box.addView(v.row("Sign in to join the community", "GitHub shows Discussions to signed-in accounts only.", in));
        }
        feedStatus = v.line("", false);
        feedStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        box.addView(feedStatus, v.full(-1, login == null ? 12 : 0));
        feedActions = new LinearLayout(this);
        feedActions.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(feedActions, v.full(-1, 6));
        feedBox = new LinearLayout(this);
        feedBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(feedBox, v.full(-1, 4));
        PlayStore.Feed kept = PlayStore.cachedFeed(this);
        if (kept != null) showFeed(kept, login != null);
        if (login == null) {
            feedStatus.setText(kept == null || kept.posts.isEmpty() ? "" : "Saved on this phone (sign in for the newest).");
            return;
        }
        feedStatus.setText(kept == null ? "Reading the community on GitHub…" : "Saved on this phone · checking GitHub…");
        feedStatus.setTextColor(v.color(R.color.ob_text_2));
        work.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "GitHub didn't accept the saved sign-in: "
                        + "sign in again (Settings).");
                PlayStore.Feed got = GitHubHttp.quick(PlayStoreActivity.TIMEOUT_MILLIS * 2, () -> PlayStore.feed(token, login));
                PlayStore.saveFeed(getApplicationContext(), got);
                ui(() -> {
                    feedStatus.setText(got.posts.isEmpty() ? "" : got.posts.size() + (got.posts.size() == 1 ? " post" : " posts")
                            + " · newest first");
                    showFeed(got, true);
                });
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    feedStatus.setText((feed == null ? "" : "Showing the copy on this phone. ") + why);
                    feedStatus.setTextColor(v.color(R.color.ob_error));
                    if (feedActions.findViewWithTag("retry") == null) {
                        TextView retry = v.smallPill("Try again", false, true, x -> load());
                        GitHubViews.oneLine(retry);
                        retry.setTag("retry");
                        feedActions.addView(retry, wrapPill(0));
                    }
                });
            }
        });
    }

    private void showFeed(PlayStore.Feed f, boolean signedIn) {
        feed = f;
        feedActions.removeAllViews();
        feedBox.removeAllViews();
        if (signedIn && !f.open.isEmpty()) {
            TextView write = v.smallPill("New post", true, true, x -> chooseWhere());
            GitHubViews.oneLine(write);
            feedActions.addView(write, wrapPill(0));
        }
        if (f.posts.isEmpty()) {
            feedBox.addView(v.line(signedIn ? "No discussions to show yet. Sketchware AI's own Discussions on GitHub are "
                    + "switched off for now; apps published to the Play Store bring theirs (Publish switches them on), and "
                    + "so does any of your repositories with Discussions on." : "Nothing saved on this phone yet.", false),
                    v.full(-1, 8));
            return;
        }
        for (PlayStore.Post p : f.posts) {
            String detail = where(p.owner, p.repo) + (p.category == null ? "" : " · " + p.category)
                    + "\n@" + p.author + (p.updated > 0 ? " · " + CloudBackup.formatTime(p.updated) : "")
                    + (p.comments > 0 ? " · " + p.comments + (p.comments == 1 ? " reply" : " replies") : "");
            LinearLayout row = v.row(p.title == null ? "(no title)" : p.title, detail);
            row.setBackgroundResource(R.drawable.ob_pill16_state);
            row.setOnClickListener(x -> startActivity(thread(this, p.owner, p.repo, p.number, p.title)));
            feedBox.addView(row, v.full(-1, feedBox.getChildCount() == 0 ? 0 : 8));
        }
    }

    /** New post: where (one of the repositories whose Discussions are on), then its category and the words. */
    private void chooseWhere() {
        if (feed == null || feed.open.isEmpty() || !AuthGuard.check(this)) return;
        String[] full = feed.open.toArray(new String[0]);
        if (full.length == 1) {
            startPost(full[0]);
            return;
        }
        String[] names = new String[full.length];
        for (int i = 0; i < full.length; i++) {
            int slash = full[i].indexOf('/');
            names[i] = slash > 0 ? where(full[i].substring(0, slash), full[i].substring(slash + 1)) : full[i];
        }
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Post where?")
                .setItems(names, (d, which) -> startPost(full[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startPost(String full) {
        int slash = full.indexOf('/');
        if (slash <= 0) return;
        String o = full.substring(0, slash);
        String r = full.substring(slash + 1);
        Toast.makeText(this, "Reading its categories…", Toast.LENGTH_SHORT).show();
        work.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "Sign in to post.");
                String[] got = new String[3];
                GitHubHttp.quick(PlayStoreActivity.TIMEOUT_MILLIS, () -> PlayStore.posts(token, o, r, got));
                ui(() -> {
                    if (!"true".equals(got[1])) {
                        Toast.makeText(this, where(o, r) + ": its Discussions were switched off meanwhile.",
                                Toast.LENGTH_LONG).show();
                        load();
                        return;
                    }
                    newPost(o, r, got);
                });
            } catch (Exception e) {
                PlayStoreActivity.toast(getApplicationContext(), GitHubImportDialog.text(e));
            }
        });
    }

    private void newPost(String o, String r, String[] info) {
        if (!AuthGuard.check(this)) return;
        String[] lines = info[2] == null || info[2].isEmpty() ? new String[0] : info[2].split("\n");
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
        TextView where = v.line("In " + where(o, r), false);
        where.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        form.addView(where);
        TextView category = v.glassPill("Category: " + names[general], true, null);
        category.setOnClickListener(x -> new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Category")
                .setSingleChoiceItems(names, picked[0], (d, which) -> {
                    picked[0] = which;
                    category.setText("Category: " + names[which]);
                    d.dismiss();
                })
                .show());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, v.dp(42));
        cp.topMargin = v.dp(6);
        form.addView(category, cp);
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
                    post(o, r, info[0], ids[picked[0]], t, b);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void post(String o, String r, String repositoryId, String categoryId, String title, String body) {
        Toast.makeText(this, "Posting…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        PlayStoreActivity.POSTS.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "Sign in to post.");
                int made = PlayStore.newPost(token, repositoryId, categoryId, title, body);
                PlayStoreActivity.toast(app, "Posted");
                ui(() -> {
                    if (made > 0) startActivity(thread(this, o, r, made, title));
                    load();
                });
            } catch (Exception e) {
                PlayStoreActivity.toast(app, GitHubImportDialog.text(e));
            }
        });
    }

    // ------------------------------------------------------------------ a thread

    private void loadThread() {
        box.removeAllViews();
        if (TokenStore.get(this).login() == null) {
            TextView in = v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, l -> load()));
            GitHubViews.oneLine(in);
            box.addView(v.row("Sign in to read this discussion", "GitHub shows Discussions to signed-in accounts only.", in));
            return;
        }
        box.addView(v.line("Reading the discussion…", false));
        work.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "GitHub didn't accept the saved sign-in: "
                        + "sign in again (Settings).");
                List<PlayStore.Post> posts = GitHubHttp.quick(PlayStoreActivity.TIMEOUT_MILLIS,
                        () -> PlayStore.thread(token, owner, repo, number));
                ui(() -> showThread(posts));
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    box.removeAllViews();
                    box.addView(v.line(why, true));
                    TextView retry = v.smallPill("Try again", false, true, x -> load());
                    GitHubViews.oneLine(retry);
                    box.addView(retry, wrapPill(8));
                });
            }
        });
    }

    private void showThread(List<PlayStore.Post> posts) {
        box.removeAllViews();
        if (posts.isEmpty()) {
            box.addView(v.line("This discussion isn't there any more.", false));
            return;
        }
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
            // v2.1.1: the author opens their creator page
            TextView who = v.line("@" + p.author + (p.time > 0 ? " · " + CloudBackup.formatTime(p.time) : ""), false);
            who.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            if (PlayStoreActivity.validLogin(p.author)) {
                who.setTextColor(v.color(R.color.ob_accent));
                who.setBackgroundResource(R.drawable.ob_press_overlay);
                who.setPadding(0, v.dp(4), v.dp(8), v.dp(4));
                who.setContentDescription("@" + p.author + ": open their page");
                who.setOnClickListener(x -> startActivity(PlayStoreActivity.developer(this, p.author)));
                card.addView(who, new LinearLayout.LayoutParams(-2, -2));
            } else {
                card.addView(who);
            }
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
        String discussionId = posts.get(0).id;
        if (discussionId == null) return;
        EditText reply = v.input("Write a reply", true, 5);
        box.addView(reply, v.full(-1, 18));
        TextView send = v.pill("Reply", true, true, x -> {
            String text = reply.getText().toString().trim();
            if (text.isEmpty()) return;
            sendReply(discussionId, text);
        });
        box.addView(send, pill(8));
        // the discussion was just read from GitHub, so its page is there: the browser for reactions and edits
        String url = posts.get(0).url;
        if (url != null && url.startsWith("https://github.com/")) {
            TextView open = v.glassPill("Open on GitHub", true, x -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (RuntimeException e) {
                    Toast.makeText(this, "No browser on this phone", Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(open, pill(8));
        }
    }

    private void sendReply(String discussionId, String text) {
        if (!AuthGuard.check(this)) return;
        Toast.makeText(this, "Sending…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        PlayStoreActivity.POSTS.execute(() -> {
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
}
