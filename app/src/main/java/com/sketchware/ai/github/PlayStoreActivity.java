package com.sketchware.ai.github;

import a.a.a.lC;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.ai.ui.TabScreens;
import com.sketchware.remod.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v2.1.0 (owner's 16 Nov directive "GitHub-powered Play Store"): the Play Store tab of the bottom bar. Three pages, each
 * a screen of its own (Back goes from a page to the one before; on the list, opened as the tab, Back asks to close the
 * tab):
 * <ul>
 * <li>the list ({@link PlayStore#search}: public repositories with the store topic; the copy on the phone first, then
 * GitHub, 10 seconds at most), a filter box and "Publish your app";</li>
 * <li>an app: its store page, the pictures (a row; a tap opens them full screen), Download &amp; Install APK and Import
 * .SWB from its newest version, Watch demo video, Open on GitHub, and the reviews (its GitHub Discussions; reading and
 * writing them needs a sign-in, GitHub's rule);</li>
 * <li>Publish: one of the account's project repositories, the words, a video link and up to eight pictures, then (after
 * asking) public with the store topic and Discussions on.</li>
 * </ul>
 */
public class PlayStoreActivity extends BaseAppCompatActivity {

    static final String EXTRA_OWNER = "playstore_owner";
    static final String EXTRA_REPO = "playstore_repo";
    static final String EXTRA_PUBLISH = "playstore_publish";
    static final int TIMEOUT_MILLIS = 10_000;
    private static final int PICK_PICTURES = 7421;

    private GitHubViews v;
    private LinearLayout list;
    private final ExecutorService work = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PlayStore");
        t.setDaemon(true);
        return t;
    });
    /**
     * review (MEDIUM): publishing and posting outlive the screen (leaving it never cuts a publish off half-way); their
     * end is a toast when the sheet was hidden or the screen is gone.
     */
    static final ExecutorService WRITES = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PlayStoreWrites");
        t.setDaemon(true);
        return t;
    });
    /** second review (LOW): reviews, posts and replies don't wait behind a publish's picture uploads. */
    static final ExecutorService POSTS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PlayStorePosts");
        t.setDaemon(true);
        return t;
    });

    public static Intent tab(Activity activity) {
        return TabScreens.asTab(new Intent(activity, PlayStoreActivity.class), "Play Store");
    }

    static Intent app(Activity activity, String owner, String repo) {
        return new Intent(activity, PlayStoreActivity.class).putExtra(EXTRA_OWNER, owner).putExtra(EXTRA_REPO, repo);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        findViewById(R.id.gs_back).setOnClickListener(x -> onBackPressed());
        String owner = getIntent().getStringExtra(EXTRA_OWNER);
        String repo = getIntent().getStringExtra(EXTRA_REPO);
        if (owner != null && repo != null) {
            if (!owner.matches("[A-Za-z0-9-]{1,39}") || !repo.matches("[A-Za-z0-9_.-]{1,100}")) {
                finish();
                return;
            }
            buildApp(owner, repo);
        } else if (getIntent().getBooleanExtra(EXTRA_PUBLISH, false)) {
            buildPublish();
        } else {
            buildList();
        }
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

    private void title(String title, String subtitle) {
        ((TextView) findViewById(R.id.gs_title)).setText(title);
        ((TextView) findViewById(R.id.gs_subtitle)).setText(subtitle);
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

    private TextView heading(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        GitHubViews.oneLine(t);
        return t;
    }

    // ------------------------------------------------------------------ the list

    private LinearLayout appsBox;
    private TextView listStatus;
    private List<PlayStore.App> apps = new ArrayList<>();
    private String filter = "";

    private void buildList() {
        title("Play Store", "Apps made with Sketchware AI, shared on GitHub");
        list.removeAllViews();
        list.addView(v.actionCard(R.drawable.ob_ic_store, "Publish your app",
                "A project backed up to GitHub: its APK, .swb, pictures and reviews", x -> {
                    if (!AuthGuard.check(this)) return;
                    startActivity(new Intent(this, PlayStoreActivity.class).putExtra(EXTRA_PUBLISH, true));
                }), v.full(-1, 4));
        EditText search = v.input("Filter by name or account", false, 1);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                showApps();
            }
        });
        list.addView(search, v.full(46, 12));
        listStatus = v.line("", false);
        listStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(listStatus, v.full(-1, 10));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(appsBox, v.full(-1, 6));
        apps = PlayStore.cached(this);
        showApps();
        refreshList();
    }

    private void refreshList() {
        listStatus.setText(apps.isEmpty() ? "Reading the Play Store on GitHub…" : "Saved on this phone · checking GitHub…");
        listStatus.setTextColor(v.color(R.color.ob_text_2));
        work.execute(() -> {
            String token = token();
            try {
                List<PlayStore.App> found = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.search(token));
                List<PlayStore.App> merged = PlayStore.merge(found, PlayStore.cached(getApplicationContext()));
                PlayStore.save(getApplicationContext(), merged);
                ui(() -> {
                    apps = merged;
                    listStatus.setText(merged.isEmpty() ? "No apps in the store yet: be the first (Publish your app)."
                            : merged.size() + (merged.size() == 1 ? " app" : " apps") + " · newest first");
                    showApps();
                });
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    listStatus.setText((apps.isEmpty() ? "" : "Showing the copy on this phone. ") + why);
                    listStatus.setTextColor(v.color(R.color.ob_error));
                    TextView retry = v.smallPill("Try again", false, true, x -> refreshList());
                    GitHubViews.oneLine(retry);
                    if (appsBox.findViewWithTag("retry") == null) {
                        retry.setTag("retry");
                        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(34));
                        p.bottomMargin = v.dp(8);
                        appsBox.addView(retry, 0, p);
                    }
                });
            }
        });
    }

    private void showApps() {
        if (appsBox == null) return;
        appsBox.removeAllViews();
        int shown = 0;
        for (PlayStore.App a : apps) {
            if (!filter.isEmpty() && !a.name().toLowerCase(Locale.ROOT).contains(filter)
                    && !a.owner.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            appsBox.addView(appCard(a), v.full(-1, shown == 0 ? 0 : 8));
            shown++;
        }
        if (shown == 0 && !apps.isEmpty()) appsBox.addView(v.line("No app matches “" + filter + "”.", false));
    }

    /** A compact card: letter tile, name, @owner · ★ · date, two lines of description. */
    private View appCard(PlayStore.App a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16_state);
        card.setPadding(v.dp(12), v.dp(10), v.dp(12), v.dp(10));
        TextView tile = new TextView(this, null, 0, R.style.Ob_Text_Button_Cta);
        String name = a.name();
        tile.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
        tile.setGravity(Gravity.CENTER);
        tile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tile.setBackgroundResource(R.drawable.ob_tile_orange);
        card.addView(tile, new LinearLayout.LayoutParams(v.dp(44), v.dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this, null, 0, R.style.Ob_Text_Label);
        title.setText(name);
        title.setTypeface(v.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GitHubViews.oneLine(title);
        texts.addView(title);
        TextView meta = v.line("@" + a.owner + " · ★ " + a.stars + (a.updated > 0 ? " · " + CloudBackup.formatTime(a.updated) : ""),
                false);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        GitHubViews.oneLine(meta);
        texts.addView(meta);
        if (a.description != null && !a.description.isEmpty()) {
            TextView d = v.line(a.description, false);
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(d);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = v.dp(12);
        card.addView(texts, tp);
        card.setOnClickListener(x -> startActivity(app(this, a.owner, a.repo)));
        card.setContentDescription(name + " by " + a.owner);
        return card;
    }

    // ------------------------------------------------------------------ an app

    private PlayStore.App current;
    private LinearLayout pageBox;
    private LinearLayout shotsRow;
    private LinearLayout actionsBox;
    private LinearLayout reviewsBox;
    private TextView appStatus;
    private String[] discussionInfo;

    private void buildApp(String owner, String repo) {
        current = null;
        for (PlayStore.App a : PlayStore.cached(this)) {
            if (a.owner.equalsIgnoreCase(owner) && a.repo.equalsIgnoreCase(repo)) current = a;
        }
        if (current == null) {
            current = new PlayStore.App();
            current.owner = owner;
            current.repo = repo;
        }
        title(current.name(), "@" + owner + " · Play Store");
        list.removeAllViews();
        pageBox = new LinearLayout(this);
        pageBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(pageBox, v.full(-1, 0));
        appStatus = v.line("", false);
        appStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(appStatus, v.full(-1, 8));
        list.addView(heading("Get it"), v.full(-1, 18));
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        actionsBox.addView(v.line("Reading its newest version…", false));
        list.addView(actionsBox, v.full(-1, 6));
        list.addView(heading("Reviews"), v.full(-1, 22));
        reviewsBox = new LinearLayout(this);
        reviewsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(reviewsBox, v.full(-1, 6));
        showPage();
        loadApp();
    }

    private void showPage() {
        PlayStore.App a = current;
        ((TextView) findViewById(R.id.gs_title)).setText(a.name());
        pageBox.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16);
        card.setPadding(v.dp(14), v.dp(12), v.dp(14), v.dp(12));
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Title);
        name.setText(a.name());
        name.setMaxLines(2);
        card.addView(name);
        TextView meta = v.line("@" + a.owner + " · ★ " + a.stars + (a.updated > 0 ? " · updated "
                + CloudBackup.formatTime(a.updated) : ""), false);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        card.addView(meta, v.full(-1, 2));
        if (a.description != null && !a.description.isEmpty()) {
            TextView d = v.line(a.description, false);
            d.setTextColor(v.color(R.color.ob_text));
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            card.addView(d, v.full(-1, 8));
        }
        pageBox.addView(card);
        if (!a.screenshots.isEmpty()) {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            shotsRow = new LinearLayout(this);
            shotsRow.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(shotsRow);
            List<String> urls = new ArrayList<>(a.screenshots);
            for (int i = 0; i < urls.size(); i++) {
                ImageView shot = new ImageView(this);
                shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
                shot.setBackgroundResource(R.drawable.ob_pill16);
                shot.setClipToOutline(true);
                shot.setContentDescription("Picture " + (i + 1) + " of " + urls.size());
                int index = i;
                shot.setOnClickListener(x -> showFull(urls, index));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(v.dp(124), v.dp(232));
                if (i > 0) p.leftMargin = v.dp(8);
                shotsRow.addView(shot, p);
                loadPicture(urls.get(i), shot, v.dp(248));
            }
            pageBox.addView(scroll, v.full(-1, 10));
        }
    }

    private void loadApp() {
        PlayStore.App a = current;
        appStatus.setText(a.listingRead ? "Saved on this phone · checking GitHub…" : "Reading its store page…");
        appStatus.setTextColor(v.color(R.color.ob_text_2));
        // review (MEDIUM): the page is read into a copy and shown on the main thread; the pictures on screen keep
        // their own list meanwhile
        PlayStore.App fresh = a.copy();
        work.execute(() -> {
            String token = token();
            String failure = null;
            try {
                GitHubHttp.quick(TIMEOUT_MILLIS, () -> {
                    PlayStore.readListing(token, fresh);
                    return null;
                });
                PlayStore.remember(getApplicationContext(), fresh);
                ui(() -> {
                    current = fresh;
                    appStatus.setText("");
                    showPage();
                });
            } catch (Exception e) {
                failure = RepoViewerActivity.whyFailed(e);
            }
            List<ProjectRepo.Version> versions = null;
            try {
                versions = GitHubHttp.quick(TIMEOUT_MILLIS, () -> ProjectRepo.versions(token, a.owner, a.repo, false));
            } catch (Exception e) {
                if (failure == null) failure = RepoViewerActivity.whyFailed(e);
            }
            String why = failure;
            List<ProjectRepo.Version> found = versions;
            ui(() -> {
                if (why != null) {
                    appStatus.setText(why);
                    appStatus.setTextColor(v.color(R.color.ob_error));
                }
                showActions(found, why != null);
            });
            loadReviews(token);
        });
    }

    private void showActions(List<ProjectRepo.Version> versions, boolean failed) {
        actionsBox.removeAllViews();
        PlayStore.App a = current;
        if (versions == null || versions.isEmpty()) {
            actionsBox.addView(v.line(versions == null ? "Its versions couldn't be read now." : "No version yet: its owner "
                    + "hasn't backed it up with Sketchware AI.", versions == null));
            if (failed) {
                TextView retry = v.smallPill("Try again", false, true, x -> {
                    actionsBox.removeAllViews();
                    actionsBox.addView(v.line("Reading its newest version…", false));
                    loadApp();
                });
                GitHubViews.oneLine(retry);
                actionsBox.addView(retry, pill(8));
            }
        } else {
            ProjectRepo.Version front = GitHubBackupsActivity.ordered(versions).get(0);
            String size = front.size > 0 ? " · .swb " + CloudSync.megabytes(front.size) : "";
            actionsBox.addView(v.line(front.label() + (front.time > 0 ? " · " + CloudBackup.formatTime(front.time) : "")
                    + size, false));
            ProjectRepo.Asset apk = null;
            if (front.apk != null) for (ProjectRepo.Asset x : front.assets) if (x.name.equals(front.apk)) apk = x;
            if (apk != null) {
                ProjectRepo.Asset file = apk;
                TextView install = v.pill("Download & Install APK  ·  " + CloudSync.megabytes(apk.size), true, true,
                        x -> RepoViewerActivity.downloadApk(this, a.owner, a.repo, front, file));
                actionsBox.addView(install, pill(10));
            } else {
                actionsBox.addView(v.line("This version has no APK: import the .swb and Run it.", false), v.full(-1, 8));
            }
            if (front.swb != null) {
                TextView swb = v.glassPill("Import .SWB (the project)", true,
                        x -> RepoViewerActivity.restore(this, a.owner, a.repo, front, a.number, false));
                actionsBox.addView(swb, pill(8));
            }
        }
        if (a.video != null) {
            TextView video = v.glassPill("Watch demo video", true, x -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.video)));
                } catch (RuntimeException e) {
                    Toast.makeText(this, "No app on this phone opens " + a.video, Toast.LENGTH_LONG).show();
                }
            });
            actionsBox.addView(video, pill(8));
        }
        TextView open = v.glassPill("Open on GitHub", true, x -> startActivity(RepoViewerActivity.intent(this, a.owner, a.repo)));
        actionsBox.addView(open, pill(8));
    }

    private LinearLayout.LayoutParams pill(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, v.dp(46));
        p.topMargin = v.dp(topDp);
        return p;
    }

    private void loadReviews(String token) {
        if (token == null) {
            ui(() -> {
                reviewsBox.removeAllViews();
                reviewsBox.addView(v.row("Sign in to read and write reviews", "GitHub shows Discussions to signed-in "
                        + "accounts only.", signInPill()));
            });
            return;
        }
        PlayStore.App a = current;
        ui(() -> {
            reviewsBox.removeAllViews();
            reviewsBox.addView(v.line("Reading the reviews…", false));
        });
        try {
            String[] info = new String[3];
            List<PlayStore.Post> posts = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.posts(token, a.owner, a.repo, info));
            ui(() -> {
                discussionInfo = info;
                showReviews(posts);
            });
        } catch (Exception e) {
            String why = RepoViewerActivity.whyFailed(e);
            ui(() -> {
                reviewsBox.removeAllViews();
                reviewsBox.addView(v.line(why, true));
                TextView retry = v.smallPill("Try again", false, true, x -> work.execute(() -> loadReviews(token())));
                GitHubViews.oneLine(retry);
                reviewsBox.addView(retry, pillWrap(8));
            });
        }
    }

    private View signInPill() {
        TextView in = v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, login -> recreate()));
        GitHubViews.oneLine(in);
        return in;
    }

    private LinearLayout.LayoutParams pillWrap(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(34));
        p.topMargin = v.dp(topDp);
        return p;
    }

    private void showReviews(List<PlayStore.Post> posts) {
        reviewsBox.removeAllViews();
        PlayStore.App a = current;
        if (!"true".equals(discussionInfo[1])) {
            reviewsBox.addView(v.line("Reviews are off for this app: its owner switches GitHub Discussions on (Publish does "
                    + "it).", false));
            return;
        }
        TextView write = v.smallPill("Write a review", true, true, x -> writeReview());
        GitHubViews.oneLine(write);
        reviewsBox.addView(write, pillWrap(0));
        if (posts.isEmpty()) {
            reviewsBox.addView(v.line("No reviews yet.", false), v.full(-1, 10));
            return;
        }
        for (PlayStore.Post p : posts) {
            String detail = "@" + p.author + (p.time > 0 ? " · " + CloudBackup.formatTime(p.time) : "")
                    + (p.comments > 0 ? " · " + p.comments + (p.comments == 1 ? " reply" : " replies") : "")
                    + (p.body == null || p.body.isEmpty() ? "" : "\n" + p.body);
            LinearLayout row = v.row(p.title == null ? "(no title)" : p.title, detail);
            row.setBackgroundResource(R.drawable.ob_pill16_state);
            row.setOnClickListener(x -> startActivity(CommunityActivity.thread(this, a.owner, a.repo, p.number, p.title)));
            reviewsBox.addView(row, v.full(-1, 8));
        }
    }

    private void writeReview() {
        if (discussionInfo == null || !AuthGuard.check(this)) return;
        String category = PlayStore.reviewCategory(discussionInfo[2]);
        if (category == null) {
            Toast.makeText(this, "This app's Discussions have no category to post in.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(v.dp(20), v.dp(8), v.dp(20), 0);
        int[] stars = {5};
        LinearLayout starRow = new LinearLayout(this);
        starRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] starViews = new TextView[5];
        for (int i = 0; i < 5; i++) {
            TextView s = new TextView(this, null, 0, R.style.Ob_Text_Title);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            s.setPadding(0, 0, v.dp(6), 0);
            int n = i + 1;
            s.setContentDescription(n + (n == 1 ? " star" : " stars"));
            s.setOnClickListener(x -> {
                stars[0] = n;
                paintStars(starViews, n);
            });
            starViews[i] = s;
            starRow.addView(s);
        }
        paintStars(starViews, 5);
        form.addView(starRow);
        EditText title = v.input("Title", false, 1);
        form.addView(title, v.full(46, 10));
        EditText body = v.input("What you think of the app", true, 6);
        form.addView(body, v.full(-1, 10));
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Review " + current.name())
                .setView(form)
                .setPositiveButton("Post", (d, w) -> {
                    String t = title.getText().toString().trim();
                    String b = body.getText().toString().trim();
                    if (t.isEmpty() || b.isEmpty()) {
                        Toast.makeText(this, "A review needs a title and a few words.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String full = starText(stars[0]) + " " + t;
                    post(category, full, b);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static String starText(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 5; i++) s.append(i < n ? '★' : '☆');
        return s.toString();
    }

    private void paintStars(TextView[] views, int n) {
        for (int i = 0; i < views.length; i++) {
            views[i].setText(i < n ? "★" : "☆");
            views[i].setTextColor(v.color(i < n ? R.color.ob_amber : R.color.ob_text_2));
        }
    }

    private void post(String category, String title, String body) {
        String repositoryId = discussionInfo[0];
        Toast.makeText(this, "Posting the review…", Toast.LENGTH_SHORT).show();
        android.content.Context app = getApplicationContext();
        POSTS.execute(() -> {
            String token = token();
            try {
                if (token == null) throw new GitHubException("signed_out", "Sign in to post a review.");
                PlayStore.newPost(token, repositoryId, category, title, body + "\n\n— posted from Sketchware AI");
                toast(app, "Review posted");
                ui(() -> work.execute(() -> loadReviews(token())));
            } catch (Exception e) {
                toast(app, GitHubImportDialog.text(e));
            }
        });
    }

    // ------------------------------------------------------------------ pictures

    /** A picture of the store (GitHub's file host only), kept in the app's cache, decoded no larger than needed. */
    private void loadPicture(String url, ImageView into, int heightPx) {
        work.execute(() -> {
            Bitmap bitmap = picture(url, heightPx);
            ui(() -> {
                if (bitmap != null) into.setImageBitmap(bitmap);
            });
        });
    }

    Bitmap picture(String url, int heightPx) {
        if (PlayStore.safeImage(url) == null) return null;
        File dir = new File(getCacheDir(), "playstore");
        File file = new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".img");
        try {
            if (!file.isFile()) {
                if (!dir.isDirectory() && !dir.mkdirs()) return null;
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(TIMEOUT_MILLIS);
                c.setReadTimeout(TIMEOUT_MILLIS);
                c.setInstanceFollowRedirects(false);
                try {
                    if (c.getResponseCode() != 200) return null;
                    File temp = new File(file.getPath() + ".tmp" + Thread.currentThread().getId());
                    long total = 0;
                    try (InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > 8L * 1024 * 1024) throw new java.io.IOException("picture over 8 MB");
                            out.write(buf, 0, n);
                        }
                    } catch (java.io.IOException e) {
                        temp.delete();
                        throw e;
                    }
                    if (!temp.renameTo(file)) temp.delete();
                } finally {
                    c.disconnect();
                }
            }
            return decode(file.getPath(), heightPx);
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap decode(String path, int heightPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outHeight <= 0 || bounds.outWidth <= 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), heightPx);
        return BitmapFactory.decodeFile(path, o);
    }

    /** The largest power of two that keeps the longer side at least {@code wanted} pixels. */
    static int sampleSize(int longer, int wanted) {
        int s = 1;
        while (wanted > 0 && longer / (s * 2) >= wanted) s *= 2;
        return s;
    }

    /** The pictures full screen: ‹ › and the count, Close (Back closes too). */
    private void showFull(List<String> urls, int start) {
        if (urls.isEmpty()) return;
        start = Math.max(0, Math.min(start, urls.size() - 1));
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView count = new TextView(this, null, 0, R.style.Ob_Text_Label);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cp.topMargin = v.dp(18);
        root.addView(count, cp);
        TextView close = v.smallPill("Close", false, true, x -> d.dismiss());
        GitHubViews.oneLine(close);
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(-2, v.dp(36), Gravity.TOP | Gravity.END);
        xp.topMargin = v.dp(12);
        xp.rightMargin = v.dp(12);
        root.addView(close, xp);
        int[] at = {start};
        int height = getResources().getDisplayMetrics().heightPixels;
        Runnable show = () -> {
            count.setText((at[0] + 1) + " / " + urls.size());
            image.setImageDrawable(null);
            String url = urls.get(at[0]);
            int shownAt = at[0];
            work.execute(() -> {
                Bitmap b = picture(url, Math.min(height, 2048));
                ui(() -> {
                    if (b != null && at[0] == shownAt && d.isShowing()) image.setImageBitmap(b);
                });
            });
        };
        if (urls.size() > 1) {
            TextView prev = v.smallPill("‹", false, true, x -> {
                at[0] = (at[0] + urls.size() - 1) % urls.size();
                show.run();
            });
            prev.setContentDescription("Previous picture");
            TextView next = v.smallPill("›", false, true, x -> {
                at[0] = (at[0] + 1) % urls.size();
                show.run();
            });
            next.setContentDescription("Next picture");
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(v.dp(48), v.dp(48), Gravity.CENTER_VERTICAL | Gravity.START);
            pp.leftMargin = v.dp(10);
            root.addView(prev, pp);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(v.dp(48), v.dp(48), Gravity.CENTER_VERTICAL | Gravity.END);
            np.rightMargin = v.dp(10);
            root.addView(next, np);
        }
        d.setContentView(root);
        show.run();
        d.show();
    }

    // ------------------------------------------------------------------ publishing

    private final List<byte[]> pictures = new ArrayList<>();
    private LinearLayout picturesRow;
    private LinearLayout reposBox;
    private ProjectRepo.Info chosen;
    private EditText titleField;
    private EditText descriptionField;
    private EditText videoField;

    private void buildPublish() {
        title("Publish your app", "To the Play Store on GitHub");
        list.removeAllViews();
        String login = TokenStore.get(this).login();
        if (login == null) {
            list.addView(v.row("Sign in to publish", "The store lists public repositories of GitHub accounts.",
                    signInPill()), v.full(-1, 4));
            return;
        }
        list.addView(heading("1 · Your project"), v.full(-1, 4));
        list.addView(v.line("Projects backed up to GitHub from this account. The newest version's APK and .swb are what "
                + "the store offers: Run the project before its backup to include the APK.", false), v.full(-1, 4));
        reposBox = new LinearLayout(this);
        reposBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(reposBox, v.full(-1, 8));
        long[] at = new long[1];
        List<ProjectRepo.Info> kept = RepoCache.repos(this, login, at);
        // review (HIGH): nothing saved on this phone yet (signed in before v2.0.4, or offline at sign-in)
        if (kept != null) showRepos(kept);
        else reposBox.addView(v.line("Reading your projects on GitHub…", false));
        boolean nothingSaved = kept == null;
        work.execute(() -> {
            String token = token();
            if (token == null) {
                // second review (LOW): a refused sign-in: say so instead of "Reading…" for ever
                if (nothingSaved) ui(() -> {
                    reposBox.removeAllViews();
                    reposBox.addView(v.line("GitHub didn't accept the saved sign-in: sign in again (Settings) to list "
                            + "your projects.", true));
                });
                return;
            }
            try {
                List<ProjectRepo.Info> own = GitHubHttp.quick(TIMEOUT_MILLIS, () -> ProjectRepo.listOwn(token));
                RepoCache.saveRepos(getApplicationContext(), login, own);
                ui(() -> showRepos(own));
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    if (nothingSaved) reposBox.removeAllViews();
                    reposBox.addView(v.line(why, true), v.full(-1, 6));
                });
            }
        });

        list.addView(heading("2 · Store page"), v.full(-1, 22));
        titleField = v.input("App name", false, 1);
        list.addView(titleField, v.full(46, 6));
        descriptionField = v.input("What the app does", true, 6);
        list.addView(descriptionField, v.full(-1, 8));
        videoField = v.input("Demo video link (https://…, optional)", false, 1);
        videoField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        list.addView(videoField, v.full(46, 8));

        list.addView(heading("3 · Pictures"), v.full(-1, 22));
        list.addView(v.line("Up to " + PlayStore.MAX_SHOTS + " screenshots (long-press one to remove it).", false),
                v.full(-1, 4));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        picturesRow = new LinearLayout(this);
        picturesRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(picturesRow);
        list.addView(scroll, v.full(-1, 8));
        showPictures();

        TextView publish = v.pill("Publish to the Play Store", true, true, x -> confirmPublish());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, v.dp(50));
        pp.topMargin = v.dp(24);
        list.addView(publish, pp);
    }

    private void showRepos(List<ProjectRepo.Info> repos) {
        reposBox.removeAllViews();
        if (repos.isEmpty()) {
            reposBox.addView(v.line("No project repository yet: back a project up to GitHub first (its card's GitHub "
                    + "pill, or Back up).", false));
            return;
        }
        for (ProjectRepo.Info info : repos) {
            boolean picked = chosen != null && chosen.number == info.number;
            TextView choose = v.smallPill(picked ? "✓ Chosen" : "Choose", picked, true, x -> {
                chosen = info;
                if (titleField != null && titleField.getText().length() == 0) {
                    titleField.setText(PlayStore.displayName(info.name));
                }
                if (descriptionField != null && descriptionField.getText().length() == 0 && info.description != null
                        && !info.description.startsWith("Sketchware AI")) {
                    descriptionField.setText(info.description);
                }
                showRepos(repos);
            });
            GitHubViews.oneLine(choose);
            LinearLayout row = v.row(PlayStore.displayName(info.name), info.name + (info.isPrivate ? " · private" : " · public"),
                    choose);
            reposBox.addView(row, v.full(-1, reposBox.getChildCount() == 0 ? 0 : 8));
        }
    }

    private void showPictures() {
        picturesRow.removeAllViews();
        for (int i = 0; i < pictures.size(); i++) {
            byte[] jpeg = pictures.get(i);
            ImageView shot = new ImageView(this);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setBackgroundResource(R.drawable.ob_pill16);
            shot.setClipToOutline(true);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            shot.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o));
            int index = i;
            shot.setContentDescription("Picture " + (i + 1) + " (long-press to remove)");
            shot.setOnLongClickListener(x -> {
                pictures.remove(index);
                showPictures();
                return true;
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(v.dp(84), v.dp(156));
            p.rightMargin = v.dp(8);
            picturesRow.addView(shot, p);
        }
        if (pictures.size() < PlayStore.MAX_SHOTS) {
            TextView add = v.glassPill("+ Add", true, x -> {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(pick, "Screenshots"), PICK_PICTURES);
                } catch (RuntimeException e) {
                    Toast.makeText(this, "No app on this phone picks pictures.", Toast.LENGTH_LONG).show();
                }
            });
            add.setContentDescription("Add screenshots");
            picturesRow.addView(add, new LinearLayout.LayoutParams(v.dp(84), v.dp(156)));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PICTURES || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        android.content.ContentResolver resolver = getContentResolver();
        work.execute(() -> {
            List<byte[]> made = new ArrayList<>();
            for (Uri uri : uris) {
                byte[] jpeg = jpeg(resolver, uri);
                if (jpeg != null) made.add(jpeg);
            }
            ui(() -> {
                for (byte[] j : made) if (pictures.size() < PlayStore.MAX_SHOTS) pictures.add(j);
                if (made.size() < uris.size()) {
                    Toast.makeText(this, (uris.size() - made.size()) + " picture(s) couldn't be read.", Toast.LENGTH_LONG).show();
                }
                showPictures();
            });
        });
    }

    /** A picked picture as a JPEG whose longer side is 1600 px at most (GitHub's contents API takes up to 100 MB). */
    static byte[] jpeg(android.content.ContentResolver resolver, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), 1600);
            Bitmap b;
            try (InputStream in = resolver.openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return null;
            int longer = Math.max(b.getWidth(), b.getHeight());
            if (longer > 1600) {
                float scale = 1600f / longer;
                Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true);
                if (s != b) b.recycle();
                b = s;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            b.recycle();
            return out.toByteArray();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void confirmPublish() {
        if (!AuthGuard.check(this)) return;
        if (chosen == null) {
            Toast.makeText(this, "Choose the project first (step 1).", Toast.LENGTH_LONG).show();
            return;
        }
        String video = videoField.getText().toString().trim();
        if (!video.isEmpty() && PlayStore.safeVideo(video) == null) {
            Toast.makeText(this, "The video link must start with https://", Toast.LENGTH_LONG).show();
            return;
        }
        PlayStore.Listing listing = new PlayStore.Listing();
        listing.title = titleField.getText().toString().trim();
        if (listing.title.isEmpty()) listing.title = PlayStore.displayName(chosen.name);
        listing.description = descriptionField.getText().toString();
        listing.video = video;
        listing.screenshots.addAll(pictures);
        ProjectRepo.Info repo = chosen;
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Make " + repo.name + " public?")
                .setMessage("Everyone can then see the project's code, its versions (APK and .swb) and this store page; "
                        + "GitHub Discussions are switched on for its reviews. Make it private again any time with its "
                        + "card's pill (it then leaves the store).")
                .setPositiveButton("Publish", (d, w) -> publish(repo, listing))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void publish(ProjectRepo.Info repo, PlayStore.Listing listing) {
        GitHubProgress progress = GitHubProgress.show(this, R.drawable.ob_ic_store, "Publishing " + listing.title);
        progress.update(2, "Starting…");
        Runnable[] again = new Runnable[1];
        again[0] = () -> {
            progress.restart();
            runPublish(progress, repo, listing);
        };
        progress.actions(null, again[0], null);
        runPublish(progress, repo, listing);
    }

    /** Every step repeats safely (PATCH, topics, files replaced by their sha), so Retry runs it all again. */
    private void runPublish(GitHubProgress progress, ProjectRepo.Info repo, PlayStore.Listing listing) {
        android.content.Context app = getApplicationContext();
        WRITES.execute(() -> {
            TokenStore store = TokenStore.get(app);
            String login = store.login();
            String token = store.token();
            try {
                if (login == null || token == null) throw new GitHubException("signed_out", "Sign in to publish.");
                String note = PlayStore.publish(token, login, repo.name, listing,
                        (percent, text) -> ui(() -> progress.update(percent, text)));
                repo.isPrivate = false;
                markLocalProjectPublic(app, login, repo);
                String done = note == null ? "Published: it is in the Play Store now." : "Published. " + note;
                if (gone() || progress.hidden()) toast(app, listing.title + ": " + done);
                ui(() -> {
                    progress.done(done);
                    progress.link(ProjectRepo.link(login, repo.name));
                });
            } catch (Exception e) {
                String why = GitHubImportDialog.text(e);
                if (gone() || progress.hidden()) toast(app, "Publishing " + listing.title + " didn't work: " + why);
                ui(() -> progress.failed(why, true));
            }
        });
    }

    private boolean gone() {
        return isFinishing() || isDestroyed();
    }

    /** A toast from any thread, with the app's context (the screen may be gone). */
    static void toast(android.content.Context app, String text) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
    }

    /** The project on this phone linked to that repository shows Public now. */
    private static void markLocalProjectPublic(android.content.Context app, String login, ProjectRepo.Info repo) {
        try {
            for (java.util.HashMap<String, Object> p : lC.a()) {
                Object id = p.get("sc_id");
                if (id == null) continue;
                String scId = id.toString();
                if (ProjectLinks.repoNumber(scId, login) == repo.number
                        || repo.name.equalsIgnoreCase(ProjectLinks.repoName(scId, login))) {
                    // review (LOW): a backup running meanwhile doesn't write the card back to Private (told first)
                    CloudSync.get(app).visibilityChanged(scId);
                    ProjectLinks.visibilityNow(scId, login, false);
                }
            }
        } catch (RuntimeException ignored) {
            // the card catches up at the next backup
        }
    }
}
