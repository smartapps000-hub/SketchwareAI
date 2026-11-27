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

    static final String EXTRA_PEOPLE = "playstore_people";
    static final String EXTRA_PEOPLE_FOLLOWERS = "playstore_people_followers";

    /** v2.1.5: who follows {@code login} ({@code followers}) or whom it follows. */
    static Intent people(Activity activity, String login, boolean followers) {
        return new Intent(activity, PlayStoreActivity.class).putExtra(EXTRA_PEOPLE, login)
                .putExtra(EXTRA_PEOPLE_FOLLOWERS, followers);
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
        } else if (getIntent().getStringExtra(EXTRA_PEOPLE) != null) {
            String who = getIntent().getStringExtra(EXTRA_PEOPLE);
            if (!validLogin(who)) {
                finish();
                return;
            }
            buildPeople(who, getIntent().getBooleanExtra(EXTRA_PEOPLE_FOLLOWERS, true));
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
        // v2.1.4: a publish's Done came back here
        showPublished();
        // review (LOW): a Follow on another page (the creator's) since this page was read: read again
        if (resumedOnce && followPill != null) {
            work.execute(() -> loadFollow(token()));
        }
        resumedOnce = true;
    }

    private boolean resumedOnce;

    @Override
    public void onPause() {
        saveForm();
        super.onPause();
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
        if (liveList != null && liveList.get() == this) liveList = null;
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
    /** v2.1.4: the store list on screen (a publish's Done goes back to it), else null. */
    private static java.lang.ref.WeakReference<PlayStoreActivity> liveList;
    /** v2.1.4: the app lit after a publish's Done ("owner/repo"), else null. */
    private String lit;
    private EditText searchBox;

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
        searchBox = search;
        liveList = new java.lang.ref.WeakReference<>(this);
        list.removeAllViews();
        // v2.1.3 (owner's 22 Nov screenshot: the Publish row marked "Remove", a tick beside the title): Publish is a
        // pill at the right of the header; it opens the publish page
        addPublishButton();
        listStatus = v.line("", false);
        listStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(listStatus, v.full(-1, 10));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(appsBox, v.full(-1, 6));
        lit = PlayStore.takeHighlight();
        apps = PlayStore.withRecent(this, PlayStore.cached(this));
        showApps();
        refreshList(null);
        refreshFollowing();
    }

    /** v2.1.5: whom you follow, from GitHub (also a Follow made on github.com), for the section and the notices. */
    private void refreshFollowing() {
        String me = TokenStore.get(this).login();
        if (me == null) return;
        work.execute(() -> {
            String token = token();
            if (token == null) return;
            try {
                List<String> logins = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.myFollowing(token));
                java.util.Set<String> before = PlayStore.followedSet(getApplicationContext(), me);
                PlayStore.saveFollowing(getApplicationContext(), me, logins);
                FollowWatch.schedule(getApplicationContext());
                if (!before.equals(PlayStore.followedSet(getApplicationContext(), me))) ui(this::showApps);
            } catch (Exception ignored) {
                // the list on the phone stays
            }
        });
    }

    /** v2.1.4: back on the list after a publish's Done: the app first and lit, the store read again from GitHub. */
    private void showPublished() {
        // review (LOW): only the store list takes the highlight (an app or creator page resuming first leaves it)
        if (appsBox == null) return;
        String h = PlayStore.takeHighlight();
        if (h == null) return;
        lit = h;
        if (searchBox != null && searchBox.getText().length() > 0) searchBox.setText("");
        filter = "";
        searched = false;
        apps = PlayStore.withRecent(this, PlayStore.cached(this));
        showApps();
        View scroll = (View) list.getParent();
        if (scroll != null) scroll.scrollTo(0, 0);
        refreshList(null);
    }

    /** v2.1.3: "Publish" at the right of the header (orange, white label, the store icon); once per screen. */
    private void addPublishButton() {
        View titleColumn = (View) findViewById(R.id.gs_title).getParent();
        android.view.ViewGroup header = (android.view.ViewGroup) titleColumn.getParent();
        if (header.findViewWithTag("publish") != null) return;
        TextView publish = v.smallPill("Publish", true, true, x -> {
            if (!AuthGuard.check(this)) return;
            startActivity(new Intent(this, PlayStoreActivity.class).putExtra(EXTRA_PUBLISH, true));
        });
        publish.setTag("publish");
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ob_ic_store);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(0xFFFFFFFF);
            icon.setBounds(0, 0, v.dp(16), v.dp(16));
            publish.setCompoundDrawablesRelative(icon, null, null, null);
            publish.setCompoundDrawablePadding(v.dp(6));
        }
        publish.setPadding(v.dp(12), 0, v.dp(14), 0);
        GitHubViews.oneLine(publish);
        publish.setContentDescription("Publish your app: a project backed up to GitHub, with its APK, .swb and pictures");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, v.dp(36));
        p.leftMargin = v.dp(8);
        header.addView(publish, p);
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
                // v2.1.4: an app published from this phone stays first (and listed) before GitHub's search has it;
                // review (LOW): a search shows its results; only the whole store is kept on the phone (merged and
                // saved in one step with a publish's entry)
                final List<PlayStore.App> listed = searching
                        ? PlayStore.merge(found, PlayStore.cached(getApplicationContext()))
                        : PlayStore.refreshCache(getApplicationContext(), found);
                ui(() -> {
                    apps = listed;
                    searched = searching;
                    View retry = appsBox.findViewWithTag("retry");
                    if (retry != null) appsBox.removeView(retry);
                    if (searching) {
                        listStatus.setText(found.isEmpty() ? "No app on GitHub matches “" + text + "”."
                                : found.size() + (found.size() == 1 ? " app" : " apps") + " on GitHub match “" + text + "”");
                    } else {
                        listStatus.setText(listed.isEmpty() ? "No apps in the store yet: be the first (Publish your app)."
                                : listed.size() + (listed.size() == 1 ? " app" : " apps") + " · newest first");
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
        // v2.1.5 (owner's 23 Nov review: "followed creators' apps shown first, the latest"): a section of its own on the
        // whole store (not in a search); its apps aren't repeated below
        java.util.Set<String> inFollowed = new java.util.HashSet<>();
        if (filter.isEmpty() && !searched) {
            List<PlayStore.App> followed = PlayStore.fromFollowed(apps,
                    PlayStore.followedSet(this, TokenStore.get(this).login()));
            if (!followed.isEmpty()) {
                appsBox.addView(heading("From creators you follow"), v.full(-1, 0));
                for (PlayStore.App a : followed) {
                    if (inFollowed.size() >= 6) break;
                    appsBox.addView(appCard(a), v.full(-1, 8));
                    inFollowed.add(a.owner + "/" + a.repo);
                }
                appsBox.addView(heading("All apps"), v.full(-1, 18));
                shown = 1;
            }
        }
        for (PlayStore.App a : apps) {
            if (!PlayStore.matches(a, filter)) continue;
            if (inFollowed.contains(a.owner + "/" + a.repo)) continue;
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
        // v2.1.4: published from this phone today: marked; just after a publish's Done: lit
        boolean recent = PlayStore.recentlyPublished(this, a);
        if (recent) {
            TextView mark = v.line("● Just published", false);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            mark.setTypeface(v.bold());
            mark.setTextColor(v.color(R.color.ob_amber));
            texts.addView(mark, 1);
        }
        if (PlayStore.is(a, lit)) card.setBackgroundResource(R.drawable.ob_accent_box);
        card.setOnClickListener(x -> startActivity(app(this, a.owner, a.repo)));
        card.setContentDescription(name + " by " + a.owner + (recent ? ", just published" : "")
                + (status == null ? "" : ", " + status));
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
    private volatile boolean statsFailed;
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
        // 1. hero. v2.1.3 (owner's 22 Nov directive: "a very slim card; the title in our orange, one line; @creator as a
        // pill like the version, clickable; a Follow pill beside the version; Downloads / Likes / Shares / Views inside
        // its bottom, smaller; everything above the fold"): the icon with the title and the publish line, a row of
        // pills, a hairline, the four counters
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackgroundResource(R.drawable.ob_pill16);
        hero.setPadding(v.dp(12), v.dp(10), v.dp(12), v.dp(6));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(appTile(a, 52), new LinearLayout.LayoutParams(v.dp(52), v.dp(52)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Title);
        name.setText(a.name());
        name.setTypeface(v.bold());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        name.setTextColor(v.color(R.color.ob_orange));
        GitHubViews.oneLine(name);
        texts.addView(name, new LinearLayout.LayoutParams(-1, -2));
        // third review (MEDIUM, v2.1.2): never cut short: one line here, the whole of it in the description below
        TextView meta = v.line(publishLine(a), false);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        GitHubViews.oneLine(meta);
        texts.addView(meta, v.full(-1, 2));
        heroMeta = meta;
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = v.dp(12);
        head.addView(texts, tp);
        hero.addView(head);
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        // v2.1.1: the creator opens their page
        TextView creator = heroPill("@" + a.owner, R.color.ob_accent, x -> startActivity(developer(this, a.owner)), true);
        creatorPill = creator;
        creator.setMaxWidth(v.dp(150));
        creator.setContentDescription("Developer @" + a.owner + ": open their page");
        // v2.1.4 (render: "👥 Follo…" cut beside a long @name): the @creator pill gives way first, the version and
        // Follow stay whole; the row is only as wide as its pills. v2.1.5 (owner's 23 Nov review: "a long @name or
        // version scrolls; fixed places"): a name or version that doesn't fit scrolls sideways
        GitHubViews.marquee(creator);
        pills.addView(creator, new LinearLayout.LayoutParams(-2, v.dp(28), 1f));
        if (a.versionName != null && !a.versionName.isEmpty()) {
            TextView version = heroPill("v" + a.versionName + (a.versionCode == null || a.versionCode.isEmpty() ? ""
                    : " (" + a.versionCode + ")"), R.color.ob_accent, null, true);
            version.setMaxWidth(v.dp(112));
            GitHubViews.marquee(version);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, v.dp(28));
            vp.leftMargin = v.dp(6);
            pills.addView(version, vp);
        }
        // v2.1.4 (owner's 22 Nov review: "there is no Follow button: how will people follow? like Telegram / Instagram,
        // with how many follow"): always there with the creator's followers; on your own app it shows your followers
        // and opens your page
        String me = TokenStore.get(this).login();
        followTarget = a.owner;
        ownApp = me != null && me.equalsIgnoreCase(a.owner);
        followPill = heroPill(ownApp ? "👥 Followers" : "＋ Follow", R.color.ob_text, x -> {
            if (ownApp) startActivity(people(this, a.owner, true));
            else toggleFollow();
        });
        // v2.1.5 (owner's 23 Nov review: "like YouTube's Subscribe: one fixed size and place, a colour of its own when
        // followed"): always the same width, whatever it says
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(v.dp(108), v.dp(28));
        fp.leftMargin = v.dp(6);
        pills.addView(followPill, fp);
        followPill.setGravity(Gravity.CENTER);
        // review (LOW): "✓ Following" stays whole in the fixed width at large font sizes (shrinks a little instead)
        GitHubViews.fitOneLine(followPill, 9f, 12f);
        showFollow();
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-2, -2);
        pr.topMargin = v.dp(10);
        hero.addView(pills, pr);
        View line = new View(this);
        line.setBackgroundColor(v.color(R.color.ob_divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, v.dp(1) / 2));
        lp.topMargin = v.dp(10);
        hero.addView(line, lp);
        // 2. the counters, inside the card's bottom
        hero.addView(statsRow(), v.full(-1, 2));
        pageBox.addView(hero);
        // 3. the actions, one row of capsules
        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.HORIZONTAL);
        actionsBox.setGravity(Gravity.CENTER_VERTICAL);
        pageBox.addView(actionsBox, v.full(-1, 10));
        actionNote = v.line("", false);
        actionNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pageBox.addView(actionNote, v.full(-1, 6));
        renderActions();
        // 4. description and What's new. v2.1.3 (owner's 22 Nov: "the description looks like one big page: a heading
        // and two lines"): "About this app" and two lines; a tap shows all of it (and two lines again)
        if (a.description != null && !a.description.isEmpty()) {
            pageBox.addView(heading("About this app"), v.full(-1, 14));
            TextView d = v.line(a.description, false);
            d.setTextColor(v.color(R.color.ob_text_2));
            d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            d.setContentDescription(a.description);
            d.setOnClickListener(x -> {
                boolean all = d.getMaxLines() == 2;
                d.setMaxLines(all ? 60 : 2);
            });
            pageBox.addView(d, v.full(-1, 4));
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
            // v2.1.4: the version's notes are Markdown (## headings, **bold**, - points): drawn, not shown as marks
            TextView c = v.line("", false);
            c.setText(plainMarkdown(a.changelog.trim()));
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

    /**
     * v2.1.4: release notes' Markdown as text: "#" headings bold without the marks, "- " / "* " points as "• ",
     * {@code **bold**} bold, {@code `code`} without the backticks, [text](link) as its text, blank-line runs as one.
     */
    static CharSequence plainMarkdown(String md) {
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder();
        boolean blank = false;
        for (String raw : md.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (!blank && out.length() > 0) out.append('\n');
                blank = true;
                continue;
            }
            blank = false;
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            boolean heading = line.startsWith("#");
            if (heading) line = line.replaceFirst("^#+\\s*", "");
            if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.substring(2);
            line = line.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1").replace("`", "");
            int start = out.length();
            // **bold** runs
            int at = 0;
            while (true) {
                int open = line.indexOf("**", at);
                int close = open < 0 ? -1 : line.indexOf("**", open + 2);
                if (open < 0 || close < 0) {
                    out.append(line.substring(at));
                    break;
                }
                out.append(line, at, open);
                int b = out.length();
                out.append(line, open + 2, close);
                out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), b, out.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                at = close + 2;
            }
            if (heading) {
                out.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, out.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
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
            // v2.1.3: inside the hero's bottom, smaller (no tile of their own); Like and Share press like a pill
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(v.dp(2), v.dp(6), v.dp(2), v.dp(6));
            cell.setMinimumHeight(v.dp(48));
            TextView value = new TextView(this, null, 0, R.style.Ob_Text_Label);
            value.setTypeface(v.bold());
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            value.setGravity(Gravity.CENTER);
            GitHubViews.oneLine(value);
            cell.addView(value, new LinearLayout.LayoutParams(-1, -2));
            TextView label = v.line(labels[i], false);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER);
            // third review: "Downloads" whole at a large font size (shrinks to 8sp at most)
            GitHubViews.fitOneLine(label, 8f, 10f);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            statValues[i] = value;
            statLabels[i] = label;
            statCells[i] = cell;
        }
        for (int i = 1; i <= 2; i++) {
            statCells[i].setBackgroundResource(R.drawable.ob_pill16_state_clear);
            statCells[i].setClickable(true);
            statCells[i].setFocusable(true);
            statCells[i].setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.sw_press));
        }
        statCells[1].setOnClickListener(x -> toggleLike());
        statCells[2].setOnClickListener(x -> share());
        showStats();
        return row;
    }

    /** v2.1.3: a 28dp glass pill of the hero (the creator, the version, Follow). */
    private TextView heroPill(String text, int color, View.OnClickListener click) {
        return heroPill(text, color, click, false);
    }

    /** v2.1.5: {@code scrolls}: a long text scrolls sideways (GitHubViews.marquee). */
    private TextView heroPill(String text, int color, View.OnClickListener click, boolean scrolls) {
        TextView pill = scrolls ? new GitHubViews.MarqueeText(this, null, 0, R.style.Ob_Text_Label)
                : new TextView(this, null, 0, R.style.Ob_Text_Label);
        pill.setText(text);
        pill.setTypeface(v.bold());
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pill.setTextColor(v.color(color));
        pill.setGravity(Gravity.CENTER);
        pill.setBackgroundResource(click == null ? R.drawable.ob_pill16 : R.drawable.ob_pill16_state);
        pill.setPadding(v.dp(11), 0, v.dp(11), 0);
        GitHubViews.oneLine(pill);
        if (click != null) {
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setOnClickListener(click);
        }
        return pill;
    }

    // ------------------------------------------------------------------ v2.1.3: Follow

    private TextView followPill;
    /** Whether the signed-in account follows the app's creator (null: not known yet). */
    private Boolean follows;
    private boolean followBusy;
    /** Review (LOW): counts Follow taps; a read that started before a tap doesn't overwrite its result. */
    private volatile int followTaps;
    /** Review (LOW): the reviews couldn't be read (the Review button says so instead of "reading"). */
    private volatile boolean reviewsFailed;

    /** Follow (white) / ✓ Following (accent), as the Private / Public pills show their state. */
    /** v2.1.4: whom the Follow pill follows (the app's creator, or the creator whose page this is). */
    private String followTarget;
    /** v2.1.4: the creator page's Followers number (changes with a Follow tap), else null. */
    private TextView followersValue;
    /** v2.1.4: the app page is the signed-in account's own app (the pill shows its followers). */
    private boolean ownApp;
    /** v2.1.4: the creator's followers on GitHub (-1: not read yet). */
    private int ownerFollowers = -1;
    private TextView creatorPill;

    private TextView heroMeta;
    /** v2.1.5: the follow state last drawn (an animation only when it changes). */
    private Boolean drawnFollows;

    /**
     * v2.1.5 (owner's 23 Nov review: "like YouTube's Subscribe: Follow in colour, Following in another, the same size
     * and place, kept for that user"): Follow = the orange call-to-action pill, white label; Following = the glass pill
     * with the accent label; a change of state bounces it once. The count is shown under the title ("· N followers").
     * On your own app: your followers (a tap lists them). Known state from the phone first (no flicker).
     */
    private void showFollow() {
        if (followPill == null) return;
        String count = ownerFollowers < 0 ? "" : PlayStore.count(ownerFollowers);
        if (followersValue != null && ownerFollowers >= 0) followersValue.setText(count);
        if (heroMeta != null && current != null) {
            heroMeta.setText(publishLine(current) + (ownerFollowers < 0 ? "" : " · " + count
                    + (ownerFollowers == 1 ? " follower" : " followers")));
        }
        if (ownApp) {
            followPill.setText(ownerFollowers < 0 ? "👥 Followers" : "👥 " + count);
            followPill.setTextColor(v.color(R.color.ob_text));
            followPill.setBackgroundResource(R.drawable.ob_pill16_state);
            followPill.setContentDescription((ownerFollowers < 0 ? "Your followers" : count + " people follow you")
                    + ": list them");
            return;
        }
        if (follows == null) follows = PlayStore.knownFollowing(this, TokenStore.get(this).login(), followTarget);
        boolean on = Boolean.TRUE.equals(follows);
        followPill.setText(on ? "✓ Following" : "＋ Follow");
        followPill.setTextColor(on ? v.color(R.color.ob_accent) : 0xFFFFFFFF);
        followPill.setBackgroundResource(on ? R.drawable.ob_pill16_state : R.drawable.ob_cta_pill_soft_state);
        followPill.setAlpha(followBusy ? 0.7f : 1f);
        followPill.setContentDescription((on ? "Following @" + followTarget + ": tap to unfollow"
                : "Follow @" + followTarget + " on GitHub") + (count.isEmpty() ? "" : ", " + count + " followers"));
        if (drawnFollows != null && drawnFollows != on) {
            followPill.animate().cancel();
            followPill.setScaleX(0.9f);
            followPill.setScaleY(0.9f);
            followPill.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        }
        drawnFollows = on;
    }

    /**
     * Reads whether the account follows the creator (on the page's worker). v2.1.4: also the creator's profile: the
     * followers on the pill and the badge on the @creator pill.
     */
    private void loadFollow(String token) {
        if (followPill == null) return;
        String owner = followTarget;
        int tapsAtStart = followTaps;
        try {
            PlayStore.Profile p = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.profile(token, owner));
            int apps = 0;
            for (PlayStore.App x : PlayStore.cached(getApplicationContext())) {
                if (owner.equalsIgnoreCase(x.owner)) apps++;
            }
            int storeApps = Math.max(1, apps);
            long downloads = stats.downloads;
            PlayStore.Badge badge = PlayStore.badge(p.login, p.name, p.bio, p.createdAt, storeApps, p.followers,
                    Math.max(0, downloads), System.currentTimeMillis());
            ui(() -> {
                if (!owner.equals(followTarget)) return;
                // review (LOW): a read that started before a Follow tap doesn't undo the tap's ±1; second review: with
                // no count yet, the read's count with that tap
                // third review (LOW): while the tap's request is on its way its own answer adds the ±1
                if (followBusy) {
                    if (ownerFollowers < 0) ownerFollowers = p.followers;
                } else if (followTaps == tapsAtStart) {
                    ownerFollowers = p.followers;
                } else if (ownerFollowers < 0) {
                    ownerFollowers = Math.max(0, p.followers + (Boolean.TRUE.equals(follows) ? 1 : -1));
                }
                showFollow();
                showCreatorBadge(badge);
            });
        } catch (Exception ignored) {
            // the pill says Follow without a number
        }
        loadFollowing(token);
    }

    /** Whether the signed-in account follows {@link #followTarget} (on a worker). */
    private void loadFollowing(String token) {
        if (token == null || ownApp || followPill == null) return;
        String owner = followTarget;
        int tapsBefore = followTaps;
        try {
            Boolean now = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.following(token, owner));
            // v2.1.5: kept on the phone for that account (the pill is right at once next time)
            if (now != null) PlayStore.noteFollowing(getApplicationContext(), TokenStore.get(this).login(), owner, now);
            ui(() -> {
                if (!owner.equals(followTarget)) return;
                // a tap since this read started decides the pill (its own answer follows)
                if (followBusy || followTaps != tapsBefore) return;
                if (now != null) follows = now;
                showFollow();
            });
        } catch (Exception ignored) {
            // unknown: the pill says Follow; a tap finds out
        }
    }

    private void toggleFollow() {
        if (!signedIn()) {
            GitHubInAppAuthDialog.show(this, login -> recreate());
            return;
        }
        if (followBusy) return;
        boolean on = !Boolean.TRUE.equals(follows);
        String owner = followTarget;
        followTaps++;
        followBusy = true;
        follows = on;
        showFollow();
        android.content.Context app = getApplicationContext();
        POSTS.execute(() -> {
            String problem = null;
            boolean scope = false;
            try {
                String token = token();
                if (token == null) throw new GitHubException("signed_out", "Sign in again.");
                PlayStore.follow(token, owner, on);
            } catch (GitHubException e) {
                problem = e.getMessage();
                scope = "scope".equals(e.code);
            } catch (Exception e) {
                problem = GitHubImportDialog.text(e);
            }
            String why = problem;
            boolean needsScope = scope;
            ui(() -> {
                followBusy = false;
                if (why != null) follows = !on;
                // v2.1.4: the count follows the tap that worked
                else if (ownerFollowers >= 0) ownerFollowers = Math.max(0, ownerFollowers + (on ? 1 : -1));
                showFollow();
                if (why == null) {
                    // v2.1.5: kept for this account; new apps of followed creators come as notifications
                    PlayStore.tapFollow(app, TokenStore.get(app).login(), owner, on);
                    FollowWatch.schedule(app);
                    Toast.makeText(this, on ? "Following @" + owner : "Unfollowed @" + owner, Toast.LENGTH_SHORT).show();
                    if (on) askNotifications(owner);
                } else if (needsScope) {
                    new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                            .setTitle("Sign in again to follow")
                            .setMessage(why)
                            .setPositiveButton("Sign in again", (d, w) -> GitHubInAppAuthDialog.show(this, login -> recreate()))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            });
            if (why != null && !needsScope) toast(app, why);
        });
    }

    /** Request code of the notification permission asked for at a Follow. */
    static final int REQUEST_NOTIFICATIONS = 7420;

    /**
     * v2.1.5 (owner's 23 Nov review: "when following, if notifications are off, a message and turn them on"): Android
     * 13+ asks for the notification permission after saying why; turned off for the app in Settings, or refused for
     * good: a message with Turn on. Review (LOW): asked at most every three days, not at every Follow.
     */
    private void askNotifications(String owner) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean allowed = nm == null || nm.areNotificationsEnabled();
        boolean permission = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                "android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (allowed && permission) return;
        android.content.SharedPreferences p = getSharedPreferences("playstore_follows", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - p.getLong("notices_asked_at", 0) < 3 * PlayStore.DAY_MILLIS) return;
        p.edit().putLong("notices_asked_at", now).apply();
        // refused before and Android won't show its question again: only Settings can turn them on
        boolean refusedForGood = !permission && p.getBoolean("notices_requested", false)
                && !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS");
        if (!permission && !refusedForGood) {
            new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Get @" + owner + "'s new apps?")
                    .setMessage("Sketchware AI tells you with a notification when @" + owner + " publishes a new app or "
                            + "version. Allow notifications on the next screen.")
                    .setPositiveButton("Allow", (d, w) -> {
                        p.edit().putBoolean("notices_requested", true).apply();
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
                    })
                    .setNegativeButton("Not now", null)
                    .show();
            return;
        }
        {
            new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                    .setTitle("Notifications are off")
                    .setMessage("Turn on Sketchware AI's notifications to hear when @" + owner + " publishes a new app.")
                    .setPositiveButton("Turn on", (d, w) -> {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
                        } catch (RuntimeException e) {
                            Toast.makeText(this, "Open Settings → Apps → Sketchware AI → Notifications", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Not now", null)
                    .show();
        }
    }

    /** v2.1.4: "@creator ✔" / "@creator ★" (a tap on the pill opens the creator's page, which says why). */
    private void showCreatorBadge(PlayStore.Badge badge) {
        if (creatorPill == null || current == null || badge == null) return;
        String mark = badge.level == PlayStore.Badge.TRUSTED ? " ★" : badge.level >= PlayStore.Badge.VERIFIED ? " ✔" : "";
        creatorPill.setText("@" + current.owner + mark);
        creatorPill.setContentDescription("Developer @" + current.owner + ", " + badge.label + ": open their page");
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
            Toast.makeText(this, statsFailed ? "The counters couldn't be read from GitHub: open the page again."
                    : statsLoaded ? "Likes for this app start with its owner's next publish."
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
            loadFollow(token);
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
            statsFailed = true;
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
        // v2.1.3 (owner's 22 Nov directive: "Review next to GitHub, four buttons in one row")
        addAction("Review", false, 1f, "Review: write a review of " + a.name(), x -> reviewTapped());
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
            reviewsFailed = false;
            ui(() -> {
                discussionInfo = info;
                showReviews(posts);
            });
        } catch (Exception e) {
            reviewsFailed = true;
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

    /** v2.1.3: the Review button: sign in, then the review form (or why reviews are off / still loading). */
    private void reviewTapped() {
        if (!signedIn()) {
            GitHubInAppAuthDialog.show(this, login -> recreate());
            return;
        }
        if (discussionInfo == null) {
            Toast.makeText(this, reviewsFailed ? "The reviews couldn't be read from GitHub: tap Try again under Reviews."
                    : "Reading the reviews from GitHub…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!"true".equals(discussionInfo[1])) {
            Toast.makeText(this, "Reviews are off for this app: its owner switches GitHub Discussions on (Publish does it).",
                    Toast.LENGTH_LONG).show();
            return;
        }
        writeReview();
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
        // v2.1.4 (owner's 22 Nov review): the creator's level (✔ Verified / ★ Trusted / Not verified; a tap says how it
        // is worked out) and the country. v2.1.5 (owner's 23 Nov screenshot: the level drawn over the country line at a
        // large font): both on their own rows under the picture, each as tall as its text needs
        TextView badge = heroPill("…", R.color.ob_text_2, null);
        badge.setMinHeight(v.dp(28));
        badge.setPadding(v.dp(12), v.dp(4), v.dp(12), v.dp(4));
        badge.setVisibility(View.GONE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = v.dp(12);
        card.addView(badge, bp);
        TextView place = v.line("", false);
        place.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        place.setTextColor(v.color(R.color.ob_text));
        place.setVisibility(View.GONE);
        card.addView(place, v.full(-1, 8));
        // v2.1.5: "Show on my store page" (own page, the country from this phone)
        TextView placeAction = v.smallPill("Show on my store page", true, true, null);
        GitHubViews.oneLine(placeAction);
        placeAction.setVisibility(View.GONE);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(-2, v.dp(36));
        pa.topMargin = v.dp(8);
        card.addView(placeAction, pa);
        creatorPlaceAction = placeAction;
        // v2.1.4: Follow on the creator's page too (like any social app), with the followers number below
        String me = TokenStore.get(this).login();
        followTarget = login;
        ownApp = me != null && me.equalsIgnoreCase(login);
        if (!ownApp) {
            followPill = heroPill("＋ Follow", R.color.ob_text, x -> toggleFollow());
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, v.dp(38));
            fp.topMargin = v.dp(12);
            card.addView(followPill, fp);
            showFollow();
        }
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
            // v2.1.5: Followers opens the list of who follows (as on Instagram)
            if (i == 2) {
                cell.setBackgroundResource(R.drawable.ob_pill16_state);
                cell.setOnClickListener(x -> startActivity(people(this, login, true)));
                cell.setContentDescription("Followers: list them");
            }
        }
        list.addView(numbers, v.full(-1, 10));
        followersValue = values[2];
        // v2.1.5: whom this creator follows
        TextView followingLink = v.smallPill("Following", false, true, x -> startActivity(people(this, login, false)));
        GitHubViews.oneLine(followingLink);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, v.dp(34));
        fl.topMargin = v.dp(8);
        list.addView(followingLink, fl);
        creatorFollowing = followingLink;
        TextView status = v.line("Reading @" + login + " on GitHub…", false);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(status, v.full(-1, 10));
        LinearLayout appsList = new LinearLayout(this);
        appsList.setOrientation(LinearLayout.VERTICAL);
        list.addView(appsList, v.full(-1, 4));
        creatorBadge = badge;
        creatorPlace = place;
        loadDeveloper(login, name, bio, avatar, values, status, appsList);
    }

    private TextView creatorBadge;
    private TextView creatorPlace;
    private TextView creatorPlaceAction;
    private TextView creatorFollowing;

    /** v2.1.4: the creator's badge pill (tap: how the levels are worked out). */
    private void showBadge(PlayStore.Badge b) {
        if (creatorBadge == null || b == null) return;
        creatorBadge.setText(b.label);
        int color = b.level >= PlayStore.Badge.TRUSTED ? R.color.ob_amber : b.level == PlayStore.Badge.VERIFIED
                ? R.color.ob_accent : R.color.ob_text_2;
        creatorBadge.setTextColor(v.color(color));
        creatorBadge.setBackgroundResource(R.drawable.ob_pill16_state);
        creatorBadge.setVisibility(View.VISIBLE);
        creatorBadge.setContentDescription(b.why + " Tap for how levels are worked out.");
        creatorBadge.setOnClickListener(x -> new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle(b.label)
                .setMessage(b.why + "\n\n" + PlayStore.BADGE_RULES)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    /**
     * v2.1.4: "🇮🇳 India · on GitHub since Mar 2019" from the profile's own Location. v2.1.5 (owner's 23 Nov review:
     * "the country should come by itself"): else the country the creator's apps carry on their store pages (published
     * from their phone), else on your own page this phone's country with "Show on my store page".
     */
    private void showPlace(PlayStore.Developer d, String fromApps) {
        if (creatorPlace == null) return;
        Countries.Country c = Countries.fromLocation(d.location);
        String since = d.createdAt > 0 ? "on GitHub since " + new java.text.SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
                .format(new java.util.Date(d.createdAt)) : null;
        String me = TokenStore.get(this).login();
        boolean mine = me != null && me.equalsIgnoreCase(d.login);
        String where;
        String offer = null;
        if (c != null) {
            where = c.toString();
            // the place itself when it says more than the country ("Hyderabad, Telangana"; "Berlin, Germany" → "Berlin")
            String loc = d.location.trim().replaceAll("(?i)[,\\s]*\\b" + java.util.regex.Pattern.quote(c.name) + "\\s*$", "")
                    .replaceAll("[,\\s]+$", "").trim();
            if (!loc.isEmpty() && !loc.equalsIgnoreCase(c.name) && loc.length() <= 40) where = where + " (" + loc + ")";
        } else if (fromApps != null) {
            where = new Countries.Country(fromApps, Countries.nameOf(fromApps)).toString();
        } else if (d.location != null && !d.location.trim().isEmpty()) {
            where = "📍 " + d.location.trim();
        } else if (mine && PlayStore.phoneCountry(this) != null) {
            String phone = PlayStore.phoneCountry(this);
            where = new Countries.Country(phone, Countries.nameOf(phone)) + " (from this phone)";
            if (!d.apps.isEmpty()) offer = phone;
        } else {
            where = "📍 Country not shared";
        }
        creatorPlace.setText(since == null ? where : where + " · " + since);
        creatorPlace.setVisibility(View.VISIBLE);
        if (creatorPlaceAction != null) {
            if (offer == null) {
                creatorPlaceAction.setVisibility(View.GONE);
            } else {
                String code = offer;
                creatorPlaceAction.setVisibility(View.VISIBLE);
                creatorPlaceAction.setText("Show " + new Countries.Country(code, Countries.nameOf(code)) + " on my store page");
                creatorPlaceAction.setOnClickListener(x -> shareCountry(d, code));
            }
        }
    }

    /** v2.1.5: the creator's country as their apps carry it in the phone's copy of the store, else null. */
    private String appsCountry(PlayStore.Developer d) {
        for (PlayStore.App a : PlayStore.cached(this)) {
            if (a.creatorCountry != null && a.owner != null && a.owner.equalsIgnoreCase(d.login)) return a.creatorCountry;
        }
        for (PlayStore.App a : d.apps) if (a.creatorCountry != null) return a.creatorCountry;
        return null;
    }

    /** v2.1.5: the country into the store pages of your apps (your tap), then shown here. */
    private void shareCountry(PlayStore.Developer d, String code) {
        if (!AuthGuard.check(this)) return;
        creatorPlaceAction.setEnabled(false);
        creatorPlaceAction.setText("Saving on GitHub…");
        List<PlayStore.App> apps = new ArrayList<>(d.apps);
        String login = d.login;
        work.execute(() -> {
            String problem = null;
            int written = 0;
            try {
                String token = token();
                if (token == null) throw new GitHubException("signed_out", "Sign in again.");
                written = GitHubHttp.quick(TIMEOUT_MILLIS * 3, () -> PlayStore.shareCountry(token, login, apps, code));
            } catch (Exception e) {
                problem = RepoViewerActivity.whyFailed(e);
            }
            String why = problem;
            int n = written;
            ui(() -> {
                creatorPlaceAction.setEnabled(true);
                if (why != null) {
                    creatorPlaceAction.setText("Try again: show it on my store page");
                    Toast.makeText(this, why, Toast.LENGTH_LONG).show();
                    return;
                }
                creatorPlaceAction.setVisibility(View.GONE);
                Toast.makeText(this, "Your country is on " + n + (n == 1 ? " store page" : " store pages") + " now",
                        Toast.LENGTH_SHORT).show();
                showPlace(d, code);
            });
        });
    }

    private void loadDeveloper(String login, TextView name, TextView bio, ImageView avatar, TextView[] values,
                               TextView status, LinearLayout appsList) {
        status.setText("Reading @" + login + " on GitHub…");
        status.setTextColor(v.color(R.color.ob_text_2));
        int tapsAtStart = followTaps;
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
                if (followBusy) {
                    if (ownerFollowers < 0) ownerFollowers = d.followers;
                } else if (followTaps == tapsAtStart) {
                    ownerFollowers = d.followers;
                } else if (ownerFollowers < 0) {
                    ownerFollowers = Math.max(0, d.followers + (Boolean.TRUE.equals(follows) ? 1 : -1));
                }
                values[2].setText(PlayStore.count(ownerFollowers >= 0 ? ownerFollowers : d.followers));
                showFollow();
                if (creatorFollowing != null) creatorFollowing.setText("Following " + PlayStore.count(d.following) + " ›");
                showPlace(d, appsCountry(d));
                showBadge(PlayStore.badge(d.login, d.name, d.bio, d.createdAt, d.apps.size(), d.followers, 0,
                        System.currentTimeMillis()));
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
            // v2.1.5: no Location on GitHub: the country one of the creator's store pages carries (one read)
            if (Countries.fromLocation(d.location) == null && appsCountry(d) == null && !d.apps.isEmpty()) {
                try {
                    String found = GitHubHttp.quick(TIMEOUT_MILLIS, () -> PlayStore.creatorCountry(token, d.apps.get(0)));
                    if (found != null) ui(() -> showPlace(d, found));
                } catch (Exception ignored) {
                    // stays as shown
                }
            }
            loadFollowing(token);
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
            ui(() -> {
                values[3].setText(any ? PlayStore.count(sum) : "–");
                // downloads can lift the level (Trusted)
                showBadge(PlayStore.badge(d.login, d.name, d.bio, d.createdAt, d.apps.size(), d.followers, sum,
                        System.currentTimeMillis()));
            });
        });
    }

    // ------------------------------------------------------------------ v2.1.5: followers / following

    /**
     * v2.1.5 (owner's 23 Nov review: "the followers of that user, and the following list, as on Instagram"): who
     * follows {@code login} or whom it follows; a row opens that creator's page.
     */
    private void buildPeople(String login, boolean followers) {
        title(followers ? "Followers" : "Following", "@" + login + " · Play Store");
        list.removeAllViews();
        TextView status = v.line(followers ? "Reading who follows @" + login + "…" : "Reading whom @" + login + " follows…",
                false);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(status, v.full(-1, 4));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        list.addView(rows, v.full(-1, 8));
        work.execute(() -> {
            String token = token();
            try {
                List<PlayStore.Person> found = GitHubHttp.quick(TIMEOUT_MILLIS * 2, () -> PlayStore.people(token, login,
                        followers));
                String me = TokenStore.get(this).login();
                if (!followers && me != null && me.equalsIgnoreCase(login)) {
                    // your own following list from GitHub replaces the one on the phone
                    List<String> logins = new ArrayList<>();
                    for (PlayStore.Person p : found) logins.add(p.login);
                    PlayStore.saveFollowing(getApplicationContext(), me, logins);
                }
                ui(() -> {
                    status.setText(found.isEmpty() ? (followers ? "No followers yet." : "Not following anyone yet.")
                            : found.size() + (followers ? (found.size() == 1 ? " follower" : " followers")
                            : " followed") + (found.size() >= 300 ? " (the first 300)" : ""));
                    rows.removeAllViews();
                    for (PlayStore.Person p : found) rows.addView(personRow(p), v.full(-1, rows.getChildCount() == 0 ? 0 : 8));
                });
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    status.setText(why);
                    status.setTextColor(v.color(R.color.ob_error));
                });
            }
        });
    }

    private View personRow(PlayStore.Person p) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.ob_pill16_state);
        row.setPadding(v.dp(12), v.dp(10), v.dp(12), v.dp(10));
        FrameLayout tile = monogramTile(this, v, p.login, 40);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        row.addView(tile, new LinearLayout.LayoutParams(v.dp(40), v.dp(40)));
        TextView name = new TextView(this, null, 0, R.style.Ob_Text_Label);
        name.setText("@" + p.login);
        name.setTypeface(v.bold());
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GitHubViews.oneLine(name);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = v.dp(12);
        row.addView(name, np);
        String me = TokenStore.get(this).login();
        Boolean on = PlayStore.knownFollowing(this, me, p.login);
        if (Boolean.TRUE.equals(on)) {
            TextView mark = v.line("✓ Following", false);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            mark.setTextColor(v.color(R.color.ob_accent));
            row.addView(mark);
        }
        row.setOnClickListener(x -> startActivity(developer(this, p.login)));
        row.setContentDescription("@" + p.login + (Boolean.TRUE.equals(on) ? ", following" : "") + ": open their page");
        if (p.avatar != null) loadPicture(p.avatar, avatar, v.dp(40));
        return row;
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
    /** v2.1.4: the list of projects is open (Change tapped, or none chosen yet). */
    private boolean changingProject;
    private List<ProjectRepo.Info> lastRepos;
    /** v2.1.4: the line under the description (written from the project, or why it couldn't be). */
    private TextView describeNote;
    /** v2.1.5: the country shown on the store page: null = this phone's, "" = not shown, else a picked code. */
    private String formCountry;
    private TextView countryLine;

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
        // v2.1.4 (owner's 22 Nov review: "Your project and its paragraph are too big: one line; once chosen, steps 2
        // and 3 come up; Change goes back"): one heading line, the chosen project as one row with Change
        list.addView(heading("1 · Your project"), v.full(-1, 4));
        reposBox = new LinearLayout(this);
        reposBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(reposBox, v.full(-1, 6));

        LinearLayout storeHead = new LinearLayout(this);
        storeHead.setOrientation(LinearLayout.HORIZONTAL);
        storeHead.setGravity(Gravity.CENTER_VERTICAL);
        storeHead.addView(heading("2 · Store page"), new LinearLayout.LayoutParams(0, -2, 1f));
        // v2.1.4 (owner: "the description should come by itself, from a deep analysis of the project")
        TextView describe = v.smallPill("✨ From project", false, true, x -> describeAsked());
        GitHubViews.oneLine(describe);
        describe.setContentDescription("Write the description from the project: its screens, buttons and features");
        storeHead.addView(describe, new LinearLayout.LayoutParams(-2, v.dp(34)));
        list.addView(storeHead, v.full(-1, 16));
        titleField = v.input("App name", false, 1);
        list.addView(titleField, v.full(46, 6));
        descriptionField = v.input("What the app does", true, 6);
        list.addView(descriptionField, v.full(-1, 4));
        describeNote = v.line("", false);
        describeNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        describeNote.setVisibility(View.GONE);
        list.addView(describeNote, v.full(-1, 4));
        // v2.1.5 (owner's 23 Nov review: "the country should come by itself"): the creator's country, from this phone
        // unless changed; a tap changes or hides it
        countryLine = v.line("", false);
        countryLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countryLine.setBackgroundResource(R.drawable.ob_pill16_state);
        countryLine.setPadding(v.dp(12), v.dp(10), v.dp(12), v.dp(10));
        countryLine.setOnClickListener(x -> chooseCountry());
        list.addView(countryLine, v.full(-1, 6));
        showCountryLine();
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

        list.addView(heading("3 · Pictures"), v.full(-1, 16));
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
            formCountry = staged.country;
            // review (LOW): "Don't show a country" holds for this account's next apps too
            if (formCountry == null && countryOff()) formCountry = "";
            showCountryLine();
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
        form.country = formCountry;
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
        changingProject = false;
        showRepos(repos);
        // v2.1.4: an empty description is written from the project at once
        describe(info, false);
    }

    private void showRepos(List<ProjectRepo.Info> repos) {
        lastRepos = repos;
        reposBox.removeAllViews();
        if (repos.isEmpty()) {
            reposBox.addView(v.line("No project repository yet: back a project up to GitHub first (its card's GitHub "
                    + "pill, or Back up).", false));
            return;
        }
        boolean wasChosen = chosen != null;
        for (ProjectRepo.Info info : repos) {
            if (chosen == null && info.number == chosenNumber) chosen = info;
        }
        if (chosen != null && !wasChosen) describe(chosen, false);
        // v2.1.4: chosen: one row with Change (steps 2 and 3 right under it)
        if (chosen != null && !changingProject) {
            ProjectRepo.Info picked = chosen;
            TextView change = v.smallPill("Change", false, true, x -> {
                changingProject = true;
                showRepos(repos);
            });
            GitHubViews.oneLine(change);
            change.setContentDescription("Choose another project");
            LinearLayout row = v.row("✓ " + PlayStore.displayName(picked.name), picked.name
                    + (picked.isPrivate ? " · private" : " · public"), change);
            reposBox.addView(row);
            return;
        }
        reposBox.addView(v.line("Projects backed up to GitHub from this account (the newest version's APK and .swb are "
                + "what the store offers).", false), v.full(-1, 0));
        for (ProjectRepo.Info info : repos) {
            boolean picked = chosen != null && chosen.number == info.number;
            TextView choose = v.smallPill(picked ? "✓ Chosen" : "Choose", picked, true, x -> choose(info, repos));
            GitHubViews.oneLine(choose);
            LinearLayout row = v.row(PlayStore.displayName(info.name), info.name + (info.isPrivate ? " · private" : " · public"),
                    choose);
            reposBox.addView(row, v.full(-1, 8));
        }
    }

    /** v2.1.5: "🌍 Country on your store page: 🇮🇳 India (from this phone) · Change". */
    private void showCountryLine() {
        if (countryLine == null) return;
        String text;
        if (formCountry != null && formCountry.isEmpty()) {
            text = "🌍 Country on your store page: not shown · Change";
        } else {
            String code = formCountry != null ? formCountry : PlayStore.phoneCountry(this);
            text = code == null ? "🌍 Country on your store page: not known on this phone · Choose"
                    : "🌍 Country on your store page: " + new Countries.Country(code, Countries.nameOf(code))
                    + (formCountry == null ? " (from this phone)" : "") + " · Change";
        }
        countryLine.setText(text);
        countryLine.setContentDescription(text);
    }

    /** v2.1.5: this phone's country, another one, or none. */
    private void chooseCountry() {
        String phone = PlayStore.phoneCountry(this);
        List<String> labels = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (phone != null) {
            labels.add(new Countries.Country(phone, Countries.nameOf(phone)) + " (from this phone)");
            codes.add(null);
        }
        labels.add("Choose another country…");
        codes.add("*");
        labels.add("Don't show a country");
        codes.add("");
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Country on your store page")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String picked = codes.get(which);
                    if ("*".equals(picked)) {
                        chooseAnyCountry();
                        return;
                    }
                    formCountry = picked;
                    rememberCountryOff("".equals(picked));
                    showCountryLine();
                    saveForm();
                })
                .show();
    }

    /** v2.1.5 review (LOW): whether this account chose "Don't show a country" last time. */
    private boolean countryOff() {
        String me = TokenStore.get(this).login();
        return me != null && getSharedPreferences("playstore_publish", MODE_PRIVATE)
                .getBoolean("country_off_" + me.toLowerCase(Locale.ROOT), false);
    }

    private void rememberCountryOff(boolean off) {
        String me = TokenStore.get(this).login();
        if (me == null) return;
        getSharedPreferences("playstore_publish", MODE_PRIVATE).edit()
                .putBoolean("country_off_" + me.toLowerCase(Locale.ROOT), off).apply();
    }

    private void chooseAnyCountry() {
        List<String[]> all = new ArrayList<>();
        for (String code : Locale.getISOCountries()) all.add(new String[]{Countries.nameOf(code), code});
        all.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = new Countries.Country(all.get(i)[1], all.get(i)[0]).toString();
        }
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Choose your country")
                .setItems(labels, (d, which) -> {
                    formCountry = all.get(which)[1];
                    rememberCountryOff(false);
                    showCountryLine();
                    saveForm();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** v2.1.4: "✨ From project": written again (asked first when the field has words). */
    private void describeAsked() {
        if (chosen == null) {
            Toast.makeText(this, "Choose the project first (step 1).", Toast.LENGTH_LONG).show();
            return;
        }
        if (descriptionField.getText().toString().trim().isEmpty()) {
            describe(chosen, true);
            return;
        }
        ProjectRepo.Info info = chosen;
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Write it from the project?")
                .setMessage("The description is replaced by one written from the project's screens, buttons and "
                        + "features. You can change it afterwards.")
                .setPositiveButton("Replace", (d, w) -> describe(info, true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * v2.1.4 (owner's 22 Nov review): the description written from the project on this phone linked to the chosen
     * repository ({@link com.sketchware.ai.project.StoreDescription}); without {@code asked} only into an empty field
     * (never over words the user typed meanwhile).
     */
    private void describe(ProjectRepo.Info info, boolean asked) {
        if (descriptionField == null || info == null) return;
        if (!asked && !descriptionField.getText().toString().trim().isEmpty()) return;
        long number = info.number;
        String account = login;
        note(describeNote, "Reading the project to write what it does…", false);
        work.execute(() -> {
            String scId = linkedProject(account, info);
            String text = scId == null ? null : com.sketchware.ai.project.StoreDescription.of(scId);
            ui(() -> {
                if (chosenNumber != number || descriptionField == null) return;
                if (text == null) {
                    note(describeNote, scId == null ? "This project isn't on this phone: write what the app does (a "
                            + "project backed up from this phone is described by itself)." : "The project couldn't be read: "
                            + "write what the app does.", scId != null);
                    return;
                }
                if (!asked && !descriptionField.getText().toString().trim().isEmpty()) {
                    note(describeNote, "", false);
                    return;
                }
                descriptionField.setText(text);
                note(describeNote, "✨ Written from the project's screens, buttons and features: change anything.", false);
            });
        });
    }

    private void note(TextView t, String text, boolean error) {
        if (t == null) return;
        t.setText(text);
        t.setTextColor(v.color(error ? R.color.ob_error : R.color.ob_text_2));
        t.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
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
        // review (LOW): the country is published exactly as the form shows it (this phone's, fixed now) and named below
        if (formCountry == null) {
            String phone = PlayStore.phoneCountry(this);
            formCountry = phone == null ? "" : phone;
            showCountryLine();
        }
        String countryWords = formCountry.isEmpty() ? "No country is shown on the store page."
                : "Your store page shows the country " + new Countries.Country(formCountry, Countries.nameOf(formCountry))
                + " (Change on this page).";
        saveForm();
        ProjectRepo.Info repo = chosen;
        String title = titleField.getText().toString().trim();
        if (title.isEmpty()) title = PlayStore.displayName(repo.name);
        String appTitle = title;
        // v2.1.1 (owner's 21 Nov screenshot: "Make SketchwareAI-… public?" cut): a short title, the name in the text
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Make it public?")
                .setMessage(repo.name + " becomes public: everyone can see the project's code, its versions (APK and .swb) "
                        + "and this store page; GitHub Discussions are switched on for its reviews. The newest backup's "
                        + "APK is what people install (Run the project before its backup to include it). Make it private "
                        + "again any time with its card's pill (it then leaves the store).\n\n" + countryWords)
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
                else CloudSync.publishToStore(this, store, () -> openStoreAfterPublish(account, repo.name));
            });
        });
    }

    /**
     * v2.1.4 (owner's 22 Nov review: "Done should go to the Play Store and show the app at the top, as in the list"):
     * the store list with the app first and lit, read again from GitHub (the list behind this page, else a new one).
     */
    private void openStoreAfterPublish(String owner, String repo) {
        PlayStore.highlight(owner, repo);
        PlayStoreActivity behind = liveList == null ? null : liveList.get();
        if (behind == null || behind == this || behind.isFinishing() || behind.isDestroyed()) {
            startActivity(tab(this));
        }
        if (!isFinishing()) finish();
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
