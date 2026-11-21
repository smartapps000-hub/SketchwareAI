package com.sketchware.ai.github;

import a.a.a.GC;
import a.a.a.lC;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
 * v2.1.0 (owner's 16 Nov directive "GitHub-powered Play Store"), v2.1.1 (owner's 21 Nov directive "Google Play-grade
 * Play Store"): the Play Store tab of the bottom bar. Four pages, each a screen of its own (Back goes from a page to
 * the one before; on the list, opened as the tab, Back asks to close the tab):
 * <ul>
 * <li>the list: the search box in the header (typing filters the apps on the phone, the keyboard's Search asks GitHub,
 * "@name" opens a creator), "Publish your app" and the app cards (the app's own icon, name, @creator · ★, its version
 * line);</li>
 * <li>an app: icon, name, @creator (a tap opens the creator's page) and the version line; Downloads, Likes (a tap likes
 * or takes the like back), Shares (Share opens Android's share sheet) and Views; What's new; the pictures; Download
 * &amp; Install APK, Import .SWB, Watch demo video, Open on GitHub; the reviews;</li>
 * <li>a creator: picture, name, @login, bio, apps / projects / followers / downloads, the store apps and the other
 * public projects;</li>
 * <li>Publish: one of the account's project repositories, the words, a video link and up to eight pictures, all kept on
 * the phone at once ({@code files/publish_staging/<repository number>}: a sign-in, a turned screen or leaving loses
 * nothing), then after asking a {@link CloudSync} job with the progress sheet and the notification.</li>
 * </ul>
 */
public class PlayStoreActivity extends BaseAppCompatActivity {

    static final String EXTRA_OWNER = "playstore_owner";
    static final String EXTRA_REPO = "playstore_repo";
    static final String EXTRA_PUBLISH = "playstore_publish";
    static final String EXTRA_DEVELOPER = "playstore_developer";
    static final int TIMEOUT_MILLIS = 10_000;
    private static final int PICK_PICTURES = 7421;
    /** A missing picture (an app without an icon on GitHub) is asked again after a day. */
    static final long MISSING_RETRY_MILLIS = 24 * 3600_000L;
    /** v2.1.1 review (LOW): pictures and icons are asked again after a week (a changed icon reaches every phone). */
    static final long REFRESH_MILLIS = 7 * 24 * 3600_000L;

    private GitHubViews v;
    private LinearLayout list;
    private final ExecutorService work = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PlayStore");
        t.setDaemon(true);
        return t;
    });
    /** v2.1.1: icons and pictures, three at a time (a list of 30 apps doesn't start 30 downloads at once). */
    private final ExecutorService images = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "PlayStoreImages");
        t.setDaemon(true);
        return t;
    });
    /** second review (LOW): reviews, posts, replies and reactions on their own thread; they outlive the screen. */
    static final ExecutorService POSTS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PlayStorePosts");
        t.setDaemon(true);
        return t;
    });
    /** v2.1.1: the publish form's words and pictures written to the phone, in order. */
    private static final ExecutorService STAGE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PlayStoreStage");
        t.setDaemon(true);
        return t;
    });

    public static Intent tab(Activity activity) {
        return TabScreens.asTab(new Intent(activity, PlayStoreActivity.class), "Play Store");
    }

    static Intent app(Activity activity, String owner, String repo) {
        return new Intent(activity, PlayStoreActivity.class).putExtra(EXTRA_OWNER, owner).putExtra(EXTRA_REPO, repo);
    }

    static Intent developer(Activity activity, String login) {
        return new Intent(activity, PlayStoreActivity.class).putExtra(EXTRA_DEVELOPER, login);
    }

    static boolean validLogin(String login) {
        return login != null && login.matches("[A-Za-z0-9-]{1,39}");
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
        String creator = getIntent().getStringExtra(EXTRA_DEVELOPER);
        if (owner != null && repo != null) {
            if (!validLogin(owner) || !repo.matches("[A-Za-z0-9_.-]{1,100}")) {
                finish();
                return;
            }
            buildApp(owner, repo);
        } else if (creator != null) {
            if (!validLogin(creator)) {
                finish();
                return;
            }
            buildDeveloper(creator);
        } else if (getIntent().getBooleanExtra(EXTRA_PUBLISH, false)) {
            buildPublish();
        } else {
            buildList();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // pictures picked or removed meanwhile (another screen of the same form)
        if (picturesRow != null) showPictures();
    }

    @Override
    public void onPause() {
        saveForm();
        super.onPause();
    }

    @Override
    public void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // v2.1.2: brought back as a tab (Home's bar): it stays as it was
        com.sketchware.ai.ui.TabScreens.onNewIntent(this, intent);
    }

    @Override
    public void onBackPressed() {
        if (TabScreens.onBack(this)) return;
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        work.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }

    private void title(String title, String subtitle) {
        ((TextView) findViewById(R.id.gs_title)).setText(title);
        TextView sub = findViewById(R.id.gs_subtitle);
        sub.setText(subtitle);
        // v2.1.2 (owner's 21 Nov screenshot: "Apps made with Sketchware AI, shared on G…" crossed out): no cut line
        sub.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String token() {
        TokenStore store = TokenStore.get(this);
        if (store.login() == null || GitHubSession.isRefused(this)) return null;
        return store.token();
    }

    /** Signed in and not refused (no Keystore read: the token is read on a worker thread). */
    private boolean signedIn() {
        return TokenStore.get(this).login() != null && !GitHubSession.isRefused(this);
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

    /**
     * An app's icon: its own icon ({@link RepoIcons#PATH} on GitHub) once read, over its two letters. v2.1.2 (owner's 21
     * Nov directive "the default icon is the APK's only"): an app without an icon of its own shows its two letters like
     * the Home cards (GH, AK, SD), never the round Android icon its APK gets.
     */
    private FrameLayout appTile(PlayStore.App a, int sizeDp) {
        FrameLayout tile = monogramTile(this, v, a.name(), sizeDp);
        ImageView icon = (ImageView) tile.getChildAt(1);
        if (a.owner != null && a.repo != null) loadPicture(a.iconUrl(), icon, v.dp(sizeDp));
        return tile;
    }

    /**
     * v2.1.2: the two letters of {@code name} on the glass tile (Home's look), with an empty picture over them that a
     * real icon fills (child 0: the letters, child 1: the picture).
     */
    static FrameLayout monogramTile(android.content.Context c, GitHubViews v, String name, int sizeDp) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackgroundResource(R.drawable.ob_avatar);
        float radius = v.dp(sizeDp) * 0.24f;
        tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        tile.setClipToOutline(true);
        TextView letters = new TextView(c);
        letters.setText(GC.monogram(name == null ? "" : name));
        letters.setTypeface(v.bold());
        letters.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(14, sizeDp / 3f));
        letters.setTextColor(v.color(R.color.ob_accent_2));
        letters.setGravity(Gravity.CENTER);
        letters.setIncludeFontPadding(false);
        tile.addView(letters, new FrameLayout.LayoutParams(-1, -1));
        ImageView icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setContentDescription(null);
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        return tile;
    }

    // ------------------------------------------------------------------ the list

    private LinearLayout appsBox;
    private TextView listStatus;
    private List<PlayStore.App> apps = new ArrayList<>();
    private String filter = "";
    /** The list on screen is a GitHub search's (not the whole store). */
    private boolean searched;

    private void buildList() {
        title("Play Store", "");
        // v2.1.1 (owner's 21 Nov directive "search bar at the top"): the search box in the header
        LinearLayout header = findViewById(R.id.gs_tabs);
        header.setVisibility(View.VISIBLE);
        EditText search = v.input("Search apps or @creator", false, 1);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setContentDescription("Search the Play Store");
        android.graphics.drawable.Drawable glass = getDrawable(R.drawable.ob_ic_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setBounds(0, 0, v.dp(18), v.dp(18));
            glass.setTint(v.color(R.color.ob_text_2));
            search.setCompoundDrawablesRelative(glass, null, null, null);
            search.setCompoundDrawablePadding(v.dp(10));
        }
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
                // second review (LOW): the search box emptied after a GitHub search: the whole store again
                if (filter.isEmpty() && searched) {
                    searched = false;
                    apps = PlayStore.cached(PlayStoreActivity.this);
                    listStatus.setText("");
                }
                showApps();
            }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            String text = search.getText().toString().trim();
            if (text.startsWith("@") && validLogin(text.substring(1))) {
                startActivity(developer(this, text.substring(1)));
                return true;
            }
            searchGitHub(text);
            return false;
        });
        header.addView(search, new LinearLayout.LayoutParams(-1, v.dp(44)));
        list.removeAllViews();
        // v2.1.2 (owner's 21 Nov screenshot: the tall Publish card circled): one slim row
        list.addView(publishRow(), v.full(52, 4));
        listStatus = v.line("", false);
        listStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(listStatus, v.full(-1, 10));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(appsBox, v.full(-1, 6));
        apps = PlayStore.cached(this);
        showApps();
        refreshList(null);
    }

    /** v2.1.2: "Publish your app" as one slim glass row (icon, title, an arrow). */
    private View publishRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16_state);
        row.setPadding(v.dp(10), 0, v.dp(14), 0);
        ImageView tile = new ImageView(this);
        tile.setBackgroundResource(R.drawable.ob_tile);
        tile.setImageResource(R.drawable.ob_ic_store);
        tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.setPadding(v.dp(7), v.dp(7), v.dp(7), v.dp(7));
        row.addView(tile, new LinearLayout.LayoutParams(v.dp(34), v.dp(34)));
        TextView t = new TextView(this, null, 0, R.style.Ob_Text);
        t.setText("Publish your app");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(v.bold());
        GitHubViews.oneLine(t);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = v.dp(12);
        row.addView(t, tp);
        TextView go = new TextView(this, null, 0, R.style.Ob_Text_Label);
        go.setText("›");
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        go.setTextColor(v.color(R.color.ob_accent));
        row.addView(go, new LinearLayout.LayoutParams(-2, -2));
        row.setContentDescription("Publish your app: a project backed up to GitHub, with its APK, .swb and pictures");
        row.setClickable(true);
        row.setFocusable(true);
        row.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.sw_press));
        row.setOnClickListener(x -> {
            if (!AuthGuard.check(this)) return;
            startActivity(new Intent(this, PlayStoreActivity.class).putExtra(EXTRA_PUBLISH, true));
        });
        return row;
    }

    private void searchGitHub(String text) {
        if (text.isEmpty()) {
            refreshList(null);
        } else {
            refreshList(text);
        }
    }

    /** The store from GitHub (all of it, or the apps matching {@code text}), merged into the phone's copy. */
    private void refreshList(String text) {
        boolean searching = text != null && !text.isEmpty();
        listStatus.setText(searching ? "Searching GitHub for “" + text + "”…"
                : apps.isEmpty() ? "Reading the Play Store on GitHub…" : "Saved on this phone · checking GitHub…");
        listStatus.setTextColor(v.color(R.color.ob_text_2));
        work.execute(() -> {
            String token = token();
            try {
                List<PlayStore.App> found = GitHubHttp.quick(TIMEOUT_MILLIS,
                        () -> PlayStore.search(token, searching ? text : null, null));
                List<PlayStore.App> merged = PlayStore.merge(found, PlayStore.cached(getApplicationContext()));
                // review (LOW): a search shows its results; only the whole store is kept on the phone
                if (!searching) PlayStore.save(getApplicationContext(), merged);
                ui(() -> {
                    apps = merged;
                    searched = searching;
                    View retry = appsBox.findViewWithTag("retry");
                    if (retry != null) appsBox.removeView(retry);
                    if (searching) {
                        listStatus.setText(found.isEmpty() ? "No app on GitHub matches “" + text + "”."
                                : found.size() + (found.size() == 1 ? " app" : " apps") + " on GitHub match “" + text + "”");
                    } else {
                        listStatus.setText(merged.isEmpty() ? "No apps in the store yet: be the first (Publish your app)."
                                : merged.size() + (merged.size() == 1 ? " app" : " apps") + " · newest first");
                    }
                    showApps();
                });
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    listStatus.setText((apps.isEmpty() ? "" : "Showing the copy on this phone. ") + why);
                    listStatus.setTextColor(v.color(R.color.ob_error));
                    if (appsBox.findViewWithTag("retry") == null) {
                        TextView retry = v.smallPill("Try again", false, true, x -> refreshList(text));
                        GitHubViews.oneLine(retry);
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
        View retry = appsBox.findViewWithTag("retry");
        appsBox.removeAllViews();
        if (retry != null) appsBox.addView(retry);
        int shown = 0;
        for (PlayStore.App a : apps) {
            if (!PlayStore.matches(a, filter)) continue;
            appsBox.addView(appCard(a), v.full(-1, shown == 0 ? 0 : 8));
            shown++;
        }
        if (shown == 0 && !apps.isEmpty()) {
            appsBox.addView(v.line("No app on this phone matches “" + filter + "”: Search on the keyboard asks GitHub.",
                    false));
        }
    }

    /** A compact card: icon, name, @creator · ★ · date, the version line, two lines of description. */
    private View appCard(PlayStore.App a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16_state);
        card.setPadding(v.dp(12), v.dp(10), v.dp(12), v.dp(10));
        card.addView(appTile(a, 48), new LinearLayout.LayoutParams(v.dp(48), v.dp(48)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this, null, 0, R.style.Ob_Text_Label);
        String name = a.name();
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
        String status = a.status();
        if (status != null) {
            TextView s = v.line(status, false);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            s.setTextColor(v.color(R.color.ob_accent));
            // the version line wraps to a second line instead of losing "Update #N"
            s.setMaxLines(2);
            texts.addView(s);
        }
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
        card.setContentDescription(name + " by " + a.owner + (status == null ? "" : ", " + status));
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
    private final PlayStore.Stats stats = new PlayStore.Stats();
    /** v2.1.2: the counters' read from GitHub ended (a Like tap before then says it is still reading). */
    private volatile boolean statsLoaded;
    private TextView[] statValues;
    private TextView[] statLabels;
    private LinearLayout[] statCells;

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
        list.addView(heading("Reviews"), v.full(-1, 22));
        reviewsBox = new LinearLayout(this);
        reviewsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(reviewsBox, v.full(-1, 6));
        showPage();
        loadApp();
    }

    /**
     * v2.1.1 (owner's 21 Nov architecture directive, "everything to act on the first screen"): 1. the hero (icon, name,
     * @developer, version pill and publish line), 2. the metrics strip, 3. the actions as one row of capsules right
     * under it (Install APK, .SWB, Demo, GitHub), 4. the description and What's new, 5. the pictures, then the reviews.
     */
    private void showPage() {
        PlayStore.App a = current;
        ((TextView) findViewById(R.id.gs_title)).setText(a.name());
        pageBox.removeAllViews();
        // 1. hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackgroundResource(R.drawable.ob_pill16);
        hero.setPadding(v.dp(14), v.dp(12), v.dp(14), v.dp(12));
        hero.addView(appTile(a, 72), new LinearLayout.LayoutParams(v.dp(72), v.dp(72)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Title);
        name.setText(a.name());
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        // v2.1.1: the creator opens their page. v2.1.2 (owner's 21 Nov screenshot: @username and the version pill drawn
        // into each other): @username on its own row, the pill on the next with clear space between
        TextView creator = new TextView(this, null, 0, R.style.Ob_Text_Label);
        creator.setText("@" + a.owner);
        creator.setTextColor(v.color(R.color.ob_accent));
        creator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        creator.setIncludeFontPadding(true);
        creator.setPadding(0, v.dp(4), v.dp(8), v.dp(4));
        creator.setMinHeight(v.dp(32));
        creator.setGravity(Gravity.CENTER_VERTICAL);
        creator.setContentDescription("Developer @" + a.owner + ": open their page");
        GitHubViews.oneLine(creator);
        creator.setOnClickListener(x -> startActivity(developer(this, a.owner)));
        texts.addView(creator, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout versionRow = new LinearLayout(this);
        versionRow.setOrientation(LinearLayout.HORIZONTAL);
        versionRow.setGravity(Gravity.CENTER_VERTICAL);
        if (a.versionName != null && !a.versionName.isEmpty()) {
            TextView pill = new TextView(this, null, 0, R.style.Ob_Text_Label);
            pill.setText("v" + a.versionName + (a.versionCode == null || a.versionCode.isEmpty() ? "" : " (" + a.versionCode + ")"));
            pill.setTypeface(v.bold());
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pill.setTextColor(v.color(R.color.ob_accent));
            pill.setGravity(Gravity.CENTER);
            pill.setBackgroundResource(R.drawable.ob_pill16);
            pill.setPadding(v.dp(10), v.dp(3), v.dp(10), v.dp(3));
            GitHubViews.oneLine(pill);
            versionRow.addView(pill, new LinearLayout.LayoutParams(-2, -2));
        }
        if (versionRow.getChildCount() > 0) {
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
            vp.topMargin = v.dp(4);
            texts.addView(versionRow, vp);
        }
        // third review (MEDIUM): its own row, two lines at most, never cut short
        TextView meta = v.line(publishLine(a), false);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setMaxLines(2);
        texts.addView(meta, v.full(-1, 8));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = v.dp(14);
        hero.addView(texts, tp);
        pageBox.addView(hero);
        // 2. metrics
        pageBox.addView(statsRow(), v.full(-1, 8));
        // 3. the actions, one row of capsules
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.HORIZONTAL);
        actionsBox.setGravity(Gravity.CENTER_VERTICAL);
        pageBox.addView(actionsBox, v.full(-1, 10));
        actionNote = v.line("", false);
        actionNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pageBox.addView(actionNote, v.full(-1, 6));
        renderActions();
        // 4. description and What's new
        if (a.description != null && !a.description.isEmpty()) {
            TextView d = v.line(a.description, false);
            d.setTextColor(v.color(R.color.ob_text));
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            pageBox.addView(d, v.full(-1, 14));
        }
        String status = a.status();
        if (a.changelog != null && !a.changelog.trim().isEmpty()) {
            pageBox.addView(heading("What's new"), v.full(-1, 16));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.ob_pill16);
            box.setPadding(v.dp(14), v.dp(10), v.dp(14), v.dp(12));
            if (status != null) {
                TextView s = v.line(status + (a.publishedAt > 0 ? " · " + CloudBackup.formatTime(a.publishedAt) : ""), false);
                s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                s.setTextColor(v.color(R.color.ob_accent));
                box.addView(s);
            }
            TextView c = v.line(a.changelog.trim(), false);
            c.setTextColor(v.color(R.color.ob_text));
            c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            c.setMaxLines(14);
            c.setEllipsize(android.text.TextUtils.TruncateAt.END);
            box.addView(c, v.full(-1, status == null ? 0 : 6));
            pageBox.addView(box, v.full(-1, 6));
        }
        // 5. pictures
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
            pageBox.addView(scroll, v.full(-1, 14));
        }
        // review (LOW): the counters are public reactions on GitHub: say so
        TextView note = v.line("Likes, shares and views are ❤️ 🚀 👀 reactions of GitHub accounts on the app's post (yours "
                + "shows there under your name); a view counts once.", false);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pageBox.addView(note, v.full(-1, 12));
    }

    /** "Updated · ★ 42 · 19 Nov 2026", "Re-published • Update #3 · ★ 17 · 18 Nov 2026", "First release · ★ 3". */
    static String publishLine(PlayStore.App a) {
        String what = "republished".equals(a.action) ? "Re-published • Update #" + Math.max(2, a.publishNumber)
                : "updated".equals(a.action) ? "Updated" : "published".equals(a.action) ? "First release" : null;
        long when = a.publishedAt > 0 ? a.publishedAt : a.updated;
        String date = "";
        if (when > 0) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
            date = " · " + f.format(new java.util.Date(when));
        }
        return (what == null ? "" : what + " · ") + "★ " + a.stars + date;
    }

    /**
     * v2.1.1 (owner's 21 Nov directive "Google Play-style stats row"): Downloads (GitHub's download counts of the
     * versions' APK and .swb), Likes (❤️ on the app's stats post: a tap likes or takes it back), Shares (🚀: Share
     * opens Android's share sheet and counts this account once) and Views (👀: counted once per signed-in account when
     * the page opens). "–" = not known yet (signed out: GitHub shows reactions to signed-in accounts only).
     */
    private View statsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Downloads", "Likes", "Shares", "Views"};
        statValues = new TextView[4];
        statLabels = new TextView[4];
        statCells = new LinearLayout[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.ob_pill16);
            cell.setPadding(v.dp(4), v.dp(8), v.dp(4), v.dp(8));
            TextView value = new TextView(this, null, 0, R.style.Ob_Text_Label);
            value.setTypeface(v.bold());
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            value.setGravity(Gravity.CENTER);
            GitHubViews.oneLine(value);
            cell.addView(value, new LinearLayout.LayoutParams(-1, -2));
            TextView label = v.line(labels[i], false);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setGravity(Gravity.CENTER);
            // third review: "Downloads" whole at a large font size (shrinks to 8sp at most)
            GitHubViews.fitOneLine(label, 8f, 11f);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.leftMargin = v.dp(6);
            row.addView(cell, p);
            statValues[i] = value;
            statLabels[i] = label;
            statCells[i] = cell;
        }
        // v2.1.2 (owner's 21 Nov screenshot: the Like tile drawn without its glass): the four tiles keep one glass
        // background; Like and Share show a press with a foreground layer instead of swapping their background
        for (int i = 1; i <= 2; i++) {
            statCells[i].setForeground(getDrawable(R.drawable.ob_press_overlay));
            statCells[i].setClickable(true);
            statCells[i].setFocusable(true);
            statCells[i].setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.sw_press));
        }
        statCells[1].setOnClickListener(x -> toggleLike());
        statCells[2].setOnClickListener(x -> share());
        showStats();
        return row;
    }

    private void showStats() {
        if (statValues == null) return;
        boolean signedIn = signedIn();
        statValues[0].setText(PlayStore.count(stats.downloads));
        statValues[1].setText(PlayStore.count(stats.likes));
        statValues[2].setText(PlayStore.count(stats.shares));
        statValues[3].setText(PlayStore.count(stats.views));
        statLabels[1].setText(stats.liked ? "❤ Liked" : "♡ Like");
        statValues[1].setTextColor(v.color(stats.liked ? R.color.ob_amber : R.color.ob_text));
        statLabels[2].setText("↗ Share");
        // Like signs in first, or says why it can't count yet; Share always opens the share sheet (v2.1.2: the tiles
        // keep their glass; nothing here changes a background)
        statCells[1].setContentDescription(PlayStore.count(stats.likes) + " likes. " + (!signedIn ? "Sign in to like."
                : stats.subjectId == null ? "Likes start with its owner's next publish." : stats.liked ? "Tap to take the like back."
                : "Tap to like."));
        statCells[2].setContentDescription(PlayStore.count(stats.shares) + " shares. Tap to share.");
        statCells[0].setContentDescription(PlayStore.count(stats.downloads) + " downloads");
        statCells[3].setContentDescription(PlayStore.count(stats.views) + " views");
    }

    private void toggleLike() {
        if (!signedIn()) {
            GitHubInAppAuthDialog.show(this, login -> recreate());
            return;
        }
        String subject = stats.subjectId;
        if (subject == null) {
            // v2.1.2: a tap always answers
            Toast.makeText(this, statsLoaded ? "Likes for this app start with its owner's next publish."
                    : "Reading the counters from GitHub…", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean add = !stats.liked;
        stats.liked = add;
        stats.likes = Math.max(0, stats.likes + (add ? 1 : -1));
        showStats();
        android.content.Context app = getApplicationContext();
        POSTS.execute(() -> {
            try {
                String token = token();
                if (token == null) throw new GitHubException("signed_out", "Sign in again.");
                PlayStore.react(token, subject, PlayStore.LIKE, add);
            } catch (Exception e) {
                ui(() -> {
                    stats.liked = !add;
                    stats.likes = Math.max(0, stats.likes + (add ? -1 : 1));
                    showStats();
                });
                toast(app, (add ? "The like" : "Taking the like back") + " didn't work: " + GitHubImportDialog.text(e));
            }
        });
    }

    private void share() {
        PlayStore.App a = current;
        String link = "https://github.com/" + a.owner + "/" + a.repo;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, a.name())
                .putExtra(Intent.EXTRA_TEXT, a.name() + " by @" + a.owner + " (made with Sketchware AI): " + link);
        try {
            startActivity(Intent.createChooser(send, "Share " + a.name()));
        } catch (RuntimeException e) {
            Toast.makeText(this, "No app on this phone shares links.", Toast.LENGTH_LONG).show();
            return;
        }
        String subject = stats.subjectId;
        if (!signedIn() || subject == null || stats.shared) return;
        stats.shared = true;
        stats.shares = Math.max(0, stats.shares) + 1;
        showStats();
        POSTS.execute(() -> {
            try {
                String token = token();
                if (token != null) PlayStore.react(token, subject, PlayStore.SHARE, true);
            } catch (Exception ignored) {
                // counted again at a later share (GitHub keeps one rocket per account)
                ui(() -> stats.shared = false);
            }
        });
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
            boolean read = false;
            try {
                GitHubHttp.quick(TIMEOUT_MILLIS, () -> {
                    PlayStore.readListing(token, fresh);
                    return null;
                });
                PlayStore.remember(getApplicationContext(), fresh);
                read = true;
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
            long downloads = found == null ? -1 : PlayStore.downloadsOf(found);
            ui(() -> {
                if (why != null) {
                    appStatus.setText(why);
                    appStatus.setTextColor(v.color(R.color.ob_error));
                }
                if (downloads >= 0) stats.downloads = downloads;
                showStats();
                showActions(found, why != null);
            });
            loadReactions(token, read ? fresh : a);
            loadReviews(token);
        });
    }

    /** Likes, shares and views of the app's stats post; this account's view counted once. */
    private void loadReactions(String token, PlayStore.App a) {
        if (token == null || a.statsDiscussion <= 0) {
            statsLoaded = true;
            return;
        }
        PlayStore.Stats got = new PlayStore.Stats();
        try {
            GitHubHttp.quick(TIMEOUT_MILLIS, () -> {
                PlayStore.reactions(token, a, got);
                return null;
            });
        } catch (Exception e) {
            statsLoaded = true;
            return;
        }
        boolean countView = got.subjectId != null && !got.viewed;
        if (countView) {
            got.viewed = true;
            got.views = Math.max(0, got.views) + 1;
        }
        ui(() -> {
            long downloads = stats.downloads;
            stats.subjectId = got.subjectId;
            stats.likes = got.likes;
            stats.liked = got.liked;
            stats.shares = got.shares;
            stats.shared = got.shared;
            stats.views = got.views;
            stats.viewed = got.viewed;
            stats.downloads = downloads;
            statsLoaded = true;
            showStats();
        });
        if (countView) {
            POSTS.execute(() -> {
                try {
                    PlayStore.react(token, got.subjectId, PlayStore.VIEW, true);
                } catch (Exception ignored) {
                    // counted at the next visit
                }
            });
        }
    }

    private TextView actionNote;
    /** The versions read for the page (null: not yet / not readable) and whether reading them failed. */
    private List<ProjectRepo.Version> pageVersions;
    private boolean versionsRead;
    private boolean versionsFailed;

    private void showActions(List<ProjectRepo.Version> versions, boolean failed) {
        pageVersions = versions;
        versionsRead = true;
        versionsFailed = failed;
        renderActions();
    }

    /**
     * The action capsules in one row (owner's 21 Nov directive: no stacked buttons, nothing to scroll for): Install APK
     * (the orange one; .SWB takes its place when the front version, the ★ pinned one else the newest, has no APK), .SWB,
     * Demo, GitHub; the line under them names the version they get.
     */
    private void renderActions() {
        if (actionsBox == null) return;
        actionsBox.removeAllViews();
        PlayStore.App a = current;
        ProjectRepo.Version front = pageVersions == null || pageVersions.isEmpty() ? null
                : GitHubBackupsActivity.ordered(pageVersions).get(0);
        ProjectRepo.Asset apk = null;
        if (front != null && front.apk != null) for (ProjectRepo.Asset x : front.assets) if (x.name.equals(front.apk)) apk = x;
        if (apk != null) {
            ProjectRepo.Asset file = apk;
            addAction("Install APK", true, 1.5f, "Install APK: download and install, " + CloudSync.megabytes(apk.size),
                    x -> RepoViewerActivity.downloadApk(this, a.owner, a.repo, front, file));
        }
        if (front != null && front.swb != null) {
            addAction(".SWB", apk == null, apk == null ? 1.4f : 1f, ".SWB: import the project",
                    x -> RepoViewerActivity.restore(this, a.owner, a.repo, front, a.number, false));
        }
        if (a.video != null) {
            addAction("Demo", false, 1f, "Demo: watch the demo video", x -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.video)));
                } catch (RuntimeException e) {
                    Toast.makeText(this, "No app on this phone opens " + a.video, Toast.LENGTH_LONG).show();
                }
            });
        }
        if (versionsFailed && (pageVersions == null || pageVersions.isEmpty())) {
            addAction("Try again", false, 1.2f, "Try again: read its versions again", x -> {
                versionsRead = false;
                versionsFailed = false;
                renderActions();
                loadApp();
            });
        }
        addAction("GitHub", false, 1f, "GitHub: open on GitHub", x -> startActivity(RepoViewerActivity.intent(this, a.owner, a.repo)));
        if (!versionsRead) {
            actionNote.setText("Reading its newest version…");
            actionNote.setTextColor(v.color(R.color.ob_text_2));
        } else if (front == null) {
            actionNote.setText(pageVersions == null ? "Its versions couldn't be read now." : "No version yet: its owner "
                    + "hasn't backed it up with Sketchware AI.");
            actionNote.setTextColor(v.color(pageVersions == null ? R.color.ob_error : R.color.ob_text_2));
        } else {
            String size = (apk != null ? " · APK " + CloudSync.megabytes(apk.size) : " · no APK: import the .swb and Run it")
                    + (front.size > 0 ? " · .swb " + CloudSync.megabytes(front.size) : "");
            actionNote.setText(front.label() + (front.time > 0 ? " · " + CloudBackup.formatTime(front.time) : "") + size);
            actionNote.setTextColor(v.color(R.color.ob_text_2));
        }
    }

    private void addAction(String text, boolean primary, float weight, String description, View.OnClickListener click) {
        TextView pill = v.smallPill(text, primary, true, click);
        GitHubViews.fitOneLine(pill, 9f, 13f);
        pill.setPadding(v.dp(8), 0, v.dp(8), 0);
        pill.setContentDescription(description);
        // third review (LOW): the full 48dp touch target
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, v.dp(48), weight);
        if (actionsBox.getChildCount() > 0) p.leftMargin = v.dp(6);
        actionsBox.addView(pill, p);
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
        int shown = 0;
        for (PlayStore.Post p : posts) {
            // v2.1.1: the stats post holds the counters, it isn't a review
            if (p.title != null && p.title.startsWith("📊 ")) continue;
            String detail = "@" + p.author + (p.time > 0 ? " · " + CloudBackup.formatTime(p.time) : "")
                    + (p.comments > 0 ? " · " + p.comments + (p.comments == 1 ? " reply" : " replies") : "")
                    + (p.body == null || p.body.isEmpty() ? "" : "\n" + p.body);
            LinearLayout row = v.row(p.title == null ? "(no title)" : p.title, detail);
            row.setBackgroundResource(R.drawable.ob_pill16_state);
            row.setOnClickListener(x -> startActivity(CommunityActivity.thread(this, a.owner, a.repo, p.number, p.title)));
            reviewsBox.addView(row, v.full(-1, 8));
            shown++;
        }
        if (shown == 0) reviewsBox.addView(v.line("No reviews yet.", false), v.full(-1, 10));
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

    // ------------------------------------------------------------------ a creator

    private void buildDeveloper(String login) {
        title("@" + login, "Developer · Play Store");
        list.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.ob_pill16);
        card.setPadding(v.dp(14), v.dp(14), v.dp(14), v.dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.ob_avatar);
        tile.setClipToOutline(true);
        TextView letters = new TextView(this, null, 0, R.style.Ob_Text_Label);
        letters.setText(GC.monogram(login));
        letters.setTypeface(v.bold());
        letters.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        letters.setTextColor(v.color(R.color.ob_accent_2));
        letters.setGravity(Gravity.CENTER);
        tile.addView(letters, new FrameLayout.LayoutParams(-1, -1));
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setContentDescription(null);
        tile.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        top.addView(tile, new LinearLayout.LayoutParams(v.dp(72), v.dp(72)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Title);
        name.setText(login);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        TextView handle = v.line("@" + login, false);
        handle.setTextColor(v.color(R.color.ob_accent));
        GitHubViews.oneLine(handle);
        texts.addView(handle);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = v.dp(14);
        top.addView(texts, tp);
        card.addView(top);
        TextView bio = v.line("", false);
        bio.setTextColor(v.color(R.color.ob_text));
        bio.setVisibility(View.GONE);
        card.addView(bio, v.full(-1, 10));
        list.addView(card, v.full(-1, 4));

        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Apps", "Projects", "Followers", "Downloads"};
        TextView[] values = new TextView[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.ob_pill16);
            cell.setPadding(v.dp(4), v.dp(8), v.dp(4), v.dp(8));
            TextView value = new TextView(this, null, 0, R.style.Ob_Text_Label);
            value.setTypeface(v.bold());
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            value.setGravity(Gravity.CENTER);
            value.setText("–");
            GitHubViews.oneLine(value);
            cell.addView(value, new LinearLayout.LayoutParams(-1, -2));
            TextView label = v.line(labels[i], false);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setGravity(Gravity.CENTER);
            // third review: "Downloads" whole at a large font size (shrinks to 8sp at most)
            GitHubViews.fitOneLine(label, 8f, 11f);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.leftMargin = v.dp(6);
            numbers.addView(cell, p);
            values[i] = value;
        }
        list.addView(numbers, v.full(-1, 10));
        TextView status = v.line("Reading @" + login + " on GitHub…", false);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(status, v.full(-1, 10));
        LinearLayout appsList = new LinearLayout(this);
        appsList.setOrientation(LinearLayout.VERTICAL);
        list.addView(appsList, v.full(-1, 4));
        loadDeveloper(login, name, bio, avatar, values, status, appsList);
    }

    private void loadDeveloper(String login, TextView name, TextView bio, ImageView avatar, TextView[] values,
                               TextView status, LinearLayout appsList) {
        status.setText("Reading @" + login + " on GitHub…");
        status.setTextColor(v.color(R.color.ob_text_2));
        work.execute(() -> {
            String token = token();
            PlayStore.Developer d;
            try {
                d = GitHubHttp.quick(TIMEOUT_MILLIS * 2, () -> PlayStore.developer(token, login));
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    status.setText(why);
                    status.setTextColor(v.color(R.color.ob_error));
                    appsList.removeAllViews();
                    TextView retry = v.smallPill("Try again", false, true,
                            x -> loadDeveloper(login, name, bio, avatar, values, status, appsList));
                    GitHubViews.oneLine(retry);
                    appsList.addView(retry, pillWrap(0));
                });
                return;
            }
            ui(() -> {
                ((TextView) findViewById(R.id.gs_title)).setText(d.name != null && !d.name.isEmpty() ? d.name : "@" + d.login);
                name.setText(d.name != null && !d.name.isEmpty() ? d.name : d.login);
                if (d.bio != null && !d.bio.trim().isEmpty()) {
                    bio.setText(d.bio.trim());
                    bio.setVisibility(View.VISIBLE);
                }
                values[0].setText(PlayStore.count(d.apps.size()));
                values[1].setText(PlayStore.count(d.projects.size()));
                values[2].setText(PlayStore.count(d.followers));
                status.setText("");
                appsList.removeAllViews();
                appsList.addView(heading("Apps in the Play Store"), v.full(-1, 8));
                if (d.apps.isEmpty()) {
                    appsList.addView(v.line("No app in the store yet.", false), v.full(-1, 6));
                }
                for (PlayStore.App a : d.apps) appsList.addView(appCard(a), v.full(-1, 8));
                appsList.addView(heading("Projects and demos"), v.full(-1, 22));
                if (d.projects.isEmpty()) {
                    appsList.addView(v.line("No other public Sketchware AI project.", false), v.full(-1, 6));
                }
                for (PlayStore.App p : d.projects) {
                    LinearLayout row = v.row(p.name(), (p.description == null || p.description.isEmpty()
                            ? p.repo : p.description) + (p.updated > 0 ? " · " + CloudBackup.formatTime(p.updated) : ""));
                    row.setBackgroundResource(R.drawable.ob_pill16_state);
                    row.setOnClickListener(x -> startActivity(RepoViewerActivity.intent(this, p.owner, p.repo)));
                    appsList.addView(row, v.full(-1, 8));
                }
            });
            if (d.avatar != null) ui(() -> loadPicture(d.avatar, avatar, v.dp(72)));
            // the downloads of the store apps (ten at most: one request each)
            long total = 0;
            boolean known = false;
            int asked = 0;
            for (PlayStore.App a : d.apps) {
                if (asked++ >= 10) break;
                try {
                    total += GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.downloads(token, a));
                    known = true;
                } catch (Exception ignored) {
                    // left out of the sum
                }
            }
            long sum = total;
            boolean any = known || d.apps.isEmpty();
            ui(() -> values[3].setText(any ? PlayStore.count(sum) : "–"));
        });
    }

    // ------------------------------------------------------------------ pictures

    /** A picture of the store (GitHub's file and avatar hosts only), kept in the app's cache, decoded no larger than needed. */
    private void loadPicture(String url, ImageView into, int heightPx) {
        // review (HIGH): called from a worker too; after onDestroy the pool is shut down: nothing to load then
        if (isFinishing() || isDestroyed()) return;
        try {
            images.execute(() -> {
                // second review (LOW): the saved copy at once, a week-old one refreshed behind it
                File saved = pictureFile(url);
                Bitmap first = saved != null && saved.isFile() ? decode(saved.getPath(), heightPx) : null;
                if (first != null) ui(() -> into.setImageBitmap(first));
                if (first != null && System.currentTimeMillis() - saved.lastModified() < REFRESH_MILLIS) return;
                Bitmap bitmap = picture(url, heightPx);
                if (bitmap != null && bitmap != first) ui(() -> into.setImageBitmap(bitmap));
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // the screen is gone
        }
    }

    /** Where the picture of {@code url} is kept (GitHub's file and avatar hosts only), or null. */
    File pictureFile(String url) {
        if (PlayStore.safeImage(url) == null && PlayStore.safeAvatar(url) == null) return null;
        return new File(new File(getCacheDir(), "playstore"), Integer.toHexString(url.hashCode()) + "_" + url.length() + ".img");
    }

    Bitmap picture(String url, int heightPx) {
        File file = pictureFile(url);
        if (file == null) return null;
        File dir = file.getParentFile();
        String key = file.getName().substring(0, file.getName().length() - 4);
        // v2.1.1: an app without an icon on GitHub (404) isn't asked again for a day
        File missing = new File(dir, key + ".none");
        try {
            // review (LOW): a picture (an icon above all) is asked again after a week; the old copy stays meanwhile and
            // when GitHub doesn't answer
            boolean stale = file.isFile() && System.currentTimeMillis() - file.lastModified() > REFRESH_MILLIS;
            if (!file.isFile() || stale) {
                if (!stale && missing.isFile() && System.currentTimeMillis() - missing.lastModified() < MISSING_RETRY_MILLIS) {
                    return null;
                }
                if (!dir.isDirectory() && !dir.mkdirs()) return null;
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(TIMEOUT_MILLIS);
                c.setReadTimeout(TIMEOUT_MILLIS);
                c.setInstanceFollowRedirects(false);
                try {
                    int code = c.getResponseCode();
                    if (code == 404) {
                        java.nio.file.Files.write(missing.toPath(), new byte[0]);
                        //noinspection ResultOfMethodCallIgnored
                        missing.setLastModified(System.currentTimeMillis());
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return null;
                    }
                    if (code != 200) {
                        // not now: the old copy stays and is asked again after a week (no retry on every list)
                        if (stale) //noinspection ResultOfMethodCallIgnored
                            file.setLastModified(System.currentTimeMillis());
                        return stale ? decode(file.getPath(), heightPx) : null;
                    }
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
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                        throw e;
                    }
                    // second review (LOW): only a picture replaces the saved one (a Wi-Fi login page doesn't)
                    if (decode(temp.getPath(), 16) == null) {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                        if (stale) //noinspection ResultOfMethodCallIgnored
                            file.setLastModified(System.currentTimeMillis());
                        return stale ? decode(file.getPath(), heightPx) : null;
                    }
                    if (file.exists()) //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    if (!temp.renameTo(file)) //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                    //noinspection ResultOfMethodCallIgnored
                    missing.delete();
                } finally {
                    c.disconnect();
                }
            }
            Bitmap b = decode(file.getPath(), heightPx);
            // a damaged copy is read again next time
            if (b == null) //noinspection ResultOfMethodCallIgnored
                file.delete();
            return b;
        } catch (Exception e) {
            if (file.isFile()) //noinspection ResultOfMethodCallIgnored
                file.setLastModified(System.currentTimeMillis());
            return file.isFile() ? decode(file.getPath(), heightPx) : null;
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
            images.execute(() -> {
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

    private static final String PREFS = "playstore_publish";
    private LinearLayout picturesRow;
    private LinearLayout reposBox;
    private ProjectRepo.Info chosen;
    /** The repository whose form is on screen (staging folder), also before the list of projects is read. */
    private long chosenNumber;
    private String login;
    private EditText titleField;
    private EditText descriptionField;
    private EditText videoField;
    /** True while the fields are being filled from the phone (no save meanwhile). */
    private boolean filling;

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private File staging() {
        return chosenNumber > 0 ? PlayStore.stagingDir(this, chosenNumber) : null;
    }

    private void buildPublish() {
        title("Publish your app", "To the Play Store on GitHub");
        list.removeAllViews();
        login = TokenStore.get(this).login();
        if (login == null) {
            list.addView(v.row("Sign in to publish", "The store lists public repositories of GitHub accounts. What you "
                    + "type here stays on this phone.", signInPill()), v.full(-1, 4));
            return;
        }
        // v2.1.1: the project chosen last time, with its form and pictures as they were left
        chosenNumber = prefs().getLong("chosen_" + login, 0);
        list.addView(heading("1 · Your project"), v.full(-1, 4));
        list.addView(v.line("Projects backed up to GitHub from this account. The newest version's APK and .swb are what "
                + "the store offers: Run the project before its backup to include the APK.", false), v.full(-1, 4));
        reposBox = new LinearLayout(this);
        reposBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(reposBox, v.full(-1, 8));

        list.addView(heading("2 · Store page"), v.full(-1, 22));
        titleField = v.input("App name", false, 1);
        list.addView(titleField, v.full(46, 6));
        descriptionField = v.input("What the app does", true, 6);
        list.addView(descriptionField, v.full(-1, 8));
        videoField = v.input("Demo video link (https://…, optional)", false, 1);
        videoField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        list.addView(videoField, v.full(46, 8));
        TextWatcher save = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!filling) saveForm();
            }
        };
        titleField.addTextChangedListener(save);
        descriptionField.addTextChangedListener(save);
        videoField.addTextChangedListener(save);

        list.addView(heading("3 · Pictures"), v.full(-1, 22));
        list.addView(v.line("Up to " + PlayStore.MAX_SHOTS + " screenshots, kept on this phone for this and later publishes "
                + "(long-press one to remove it; publishing without any keeps the store's pictures).", false), v.full(-1, 4));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        picturesRow = new LinearLayout(this);
        picturesRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(picturesRow);
        list.addView(scroll, v.full(-1, 8));

        TextView publish = v.pill("Publish to the Play Store", true, true, x -> confirmPublish());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, v.dp(50));
        pp.topMargin = v.dp(24);
        list.addView(publish, pp);

        if (chosenNumber > 0) fillForm(null);
        showPictures();

        long[] at = new long[1];
        List<ProjectRepo.Info> kept = RepoCache.repos(this, login, at);
        // review (HIGH): nothing saved on this phone yet (signed in before v2.0.4, or offline at sign-in)
        if (kept != null) showRepos(kept);
        else reposBox.addView(v.line("Reading your projects on GitHub…", false));
        boolean nothingSaved = kept == null;
        String account = login;
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
                RepoCache.saveRepos(getApplicationContext(), account, own);
                ui(() -> showRepos(own));
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    if (nothingSaved) reposBox.removeAllViews();
                    reposBox.addView(v.line(why, true), v.full(-1, 6));
                });
            }
        });
    }

    /**
     * The form of the chosen project from the phone: its saved words, else (first time) the repository's name and
     * description.
     */
    private void fillForm(ProjectRepo.Info info) {
        File dir = staging();
        if (dir == null) return;
        PlayStore.Listing staged = PlayStore.Listing.loadForm(dir);
        filling = true;
        try {
            String t = staged.title != null ? staged.title : info != null ? PlayStore.displayName(info.name) : "";
            String d = staged.description != null ? staged.description
                    : info != null && info.description != null && !info.description.startsWith("Sketchware AI")
                    ? info.description : "";
            titleField.setText(t);
            descriptionField.setText(d);
            videoField.setText(staged.video == null ? "" : staged.video);
        } finally {
            filling = false;
        }
    }

    /** The form's words to the phone (its own thread, in order). */
    private void saveForm() {
        File dir = staging();
        if (dir == null || titleField == null) return;
        PlayStore.Listing form = new PlayStore.Listing();
        form.title = titleField.getText().toString();
        form.description = descriptionField.getText().toString();
        form.video = videoField.getText().toString();
        STAGE.execute(() -> {
            try {
                form.saveForm(dir);
            } catch (Exception e) {
                mod.jbk.util.LogUtil.d("PlayStore", "Form not saved: " + e);
            }
        });
    }

    private void choose(ProjectRepo.Info info, List<ProjectRepo.Info> repos) {
        if (chosenNumber != info.number) {
            saveForm();
            chosenNumber = info.number;
            prefs().edit().putLong("chosen_" + login, info.number).apply();
            fillForm(info);
            showPictures();
        }
        chosen = info;
        showRepos(repos);
    }

    private void showRepos(List<ProjectRepo.Info> repos) {
        reposBox.removeAllViews();
        if (repos.isEmpty()) {
            reposBox.addView(v.line("No project repository yet: back a project up to GitHub first (its card's GitHub "
                    + "pill, or Back up).", false));
            return;
        }
        for (ProjectRepo.Info info : repos) {
            if (chosen == null && info.number == chosenNumber) chosen = info;
            boolean picked = chosen != null && chosen.number == info.number;
            TextView choose = v.smallPill(picked ? "✓ Chosen" : "Choose", picked, true, x -> choose(info, repos));
            GitHubViews.oneLine(choose);
            LinearLayout row = v.row(PlayStore.displayName(info.name), info.name + (info.isPrivate ? " · private" : " · public"),
                    choose);
            reposBox.addView(row, v.full(-1, reposBox.getChildCount() == 0 ? 0 : 8));
        }
    }

    private void showPictures() {
        picturesRow.removeAllViews();
        File dir = staging();
        List<File> staged = dir == null ? new ArrayList<>() : PlayStore.stagedPictures(dir);
        for (int i = 0; i < staged.size(); i++) {
            ImageView shot = new ImageView(this);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setBackgroundResource(R.drawable.ob_pill16);
            shot.setClipToOutline(true);
            File picture = staged.get(i);
            // review (LOW): decoded off the main thread
            try {
                images.execute(() -> {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 4;
                    Bitmap b = BitmapFactory.decodeFile(picture.getPath(), o);
                    ui(() -> {
                        if (b != null) shot.setImageBitmap(b);
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // the screen is gone
            }
            int index = i;
            shot.setContentDescription("Picture " + (i + 1) + " (long-press to remove)");
            shot.setOnLongClickListener(x -> {
                STAGE.execute(() -> {
                    PlayStore.unstagePicture(dir, index);
                    ui(this::showPictures);
                });
                return true;
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(v.dp(84), v.dp(156));
            p.rightMargin = v.dp(8);
            picturesRow.addView(shot, p);
        }
        if (staged.size() < PlayStore.MAX_SHOTS) {
            TextView add = v.glassPill("+ Add", true, x -> {
                if (staging() == null) {
                    Toast.makeText(this, "Choose the project first (step 1): its pictures are kept with it.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
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
        File dir = staging();
        if (dir == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        android.content.ContentResolver resolver = getContentResolver();
        android.content.Context app = getApplicationContext();
        // v2.1.1: each picture is copied to the phone at once (the picker's permission ends with this screen)
        STAGE.execute(() -> {
            int failed = 0;
            int full = 0;
            for (Uri uri : uris) {
                byte[] jpeg = jpeg(resolver, uri);
                if (jpeg == null) {
                    failed++;
                    continue;
                }
                try {
                    if (!PlayStore.stagePicture(dir, jpeg)) full++;
                } catch (Exception e) {
                    failed++;
                }
            }
            if (failed > 0) toast(app, failed + (failed == 1 ? " picture" : " pictures") + " couldn't be read.");
            if (full > 0) toast(app, "Up to " + PlayStore.MAX_SHOTS + " pictures: " + full + " left out.");
            ui(this::showPictures);
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
        saveForm();
        ProjectRepo.Info repo = chosen;
        String title = titleField.getText().toString().trim();
        if (title.isEmpty()) title = PlayStore.displayName(repo.name);
        String appTitle = title;
        // v2.1.1 (owner's 21 Nov screenshot: "Make SketchwareAI-… public?" cut): a short title, the name in the text
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Make it public?")
                .setMessage(repo.name + " becomes public: everyone can see the project's code, its versions (APK and .swb) "
                        + "and this store page; GitHub Discussions are switched on for its reviews. Make it private again "
                        + "any time with its card's pill (it then leaves the store).")
                .setPositiveButton("Publish", (d, w) -> publish(repo, appTitle))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * v2.1.1: the publish runs as a {@link CloudSync} job (progress sheet and notification; leaving the screen doesn't
     * stop it); a refused sign-in pauses it and Sign in again runs it from the staged files.
     */
    private void publish(ProjectRepo.Info repo, String title) {
        CloudSync.Store store = new CloudSync.Store();
        store.repo = repo.name;
        store.number = repo.number;
        store.title = title;
        store.staging = PlayStore.stagingDir(this, repo.number);
        store.login = login;
        String account = login;
        android.content.Context app = getApplicationContext();
        // review (LOW): the words are written before the job can read them; the project list is read off the main
        // thread, and the publish starts even when this screen closed meanwhile (then with its notification only)
        STAGE.execute(() -> {
            store.scId = linkedProject(account, repo);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (isFinishing() || isDestroyed()) CloudSync.publishToStoreQuietly(app, store);
                else CloudSync.publishToStore(this, store);
            });
        });
    }

    /** The project on this phone linked to that repository (by number, else by name), or null. */
    private static String linkedProject(String login, ProjectRepo.Info repo) {
        try {
            for (java.util.HashMap<String, Object> p : lC.a()) {
                Object id = p.get("sc_id");
                if (id == null) continue;
                String scId = id.toString();
                if (ProjectLinks.repoNumber(scId, login) == repo.number
                        || repo.name.equalsIgnoreCase(ProjectLinks.repoName(scId, login))) {
                    return scId;
                }
            }
        } catch (RuntimeException ignored) {
            // published without the local project's icon
        }
        return null;
    }

    /** A toast from any thread, with the app's context (the screen may be gone). */
    static void toast(android.content.Context app, String text) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
    }
}
