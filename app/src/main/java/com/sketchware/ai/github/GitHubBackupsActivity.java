package com.sketchware.ai.github;

import a.a.a.GC;
import a.a.a.lC;
import a.a.a.yB;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.sketchware.ai.ui.ObAlertBuilder;
import com.sketchware.ai.ui.ObsidianWindow;
import com.sketchware.remod.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;

/**
 * v2.0.4 (owner's 16 Nov directive "full-screen edge-to-edge Import & Restore page, versions with Latest at #1 and the
 * Standard pin at #2, on-device backups list"): Restore projects, full screen in the app's glass, with two tabs.
 * <ul>
 * <li><b>GitHub</b>: a link field (a project repository, a folder or a .swb file on github.com; also what a shared link
 * opens, {@link GitHubLinkActivity}); the account's project repositories ({@link ProjectRepo}), shown at once from
 * the phone's copy ({@link RepoCache}) and refreshed in the background, each with its Public / Private badge, Share,
 * and its versions in one horizontal row (at most five; v2.0.5: the pinned one in front, "★ Pinned", else the newest,
 * "New"; then by date; "+N older versions →"), Restore and ★ Pin / Unpin; and the older backups of
 * {@link CloudBackup#REPO} (read only).</li>
 * <li><b>On this phone</b>: every .swb in the backups folder with its time and size, newest first, Restore; and a .swb
 * file from anywhere. Nothing here is ever deleted by the app.</li>
 * </ul>
 * A restore from GitHub runs in the background ({@link CloudSync#importProject}: staged download, checked, a new project;
 * Hide and Open on GitHub hand it to the notification).
 */
public class GitHubBackupsActivity extends BaseAppCompatActivity {

    public static final String EXTRA_LINK = "github_link";
    public static final String EXTRA_PHONE = "on_this_phone";

    private GitHubViews v;
    private LinearLayout list;
    private LinearLayout tabs;
    private boolean phoneTab;
    /** v2.1.0: which tab: {@link #TAB_GITHUB}, {@link #TAB_DEMO}, {@link #TAB_PHONE}. */
    private int tab;
    static final int TAB_GITHUB = 0;
    static final int TAB_DEMO = 1;
    static final int TAB_PHONE = 2;
    public static final String EXTRA_TAB = "tab";
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RestoreProjects");
        t.setDaemon(true);
        return t;
    });
    /** v2.0.5: link lookups on their own worker (never behind the projects list being read). */
    private final ExecutorService lookups = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RestoreLookups");
        t.setDaemon(true);
        return t;
    });

    private EditText link;
    private TextView linkStatus;
    private LinearLayout found;
    private LinearLayout projects;
    private LinearLayout older;
    private boolean busy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ob_github_screen);
        ObsidianWindow.apply(this);
        v = new GitHubViews(this);
        list = findViewById(R.id.gs_list);
        tabs = findViewById(R.id.gs_tabs);
        ((TextView) findViewById(R.id.gs_title)).setText("Restore projects");
        findViewById(R.id.gs_back).setOnClickListener(x -> onBackPressed());
        tabs.setVisibility(View.VISIBLE);
        int first = getIntent().getIntExtra(EXTRA_TAB, getIntent().getBooleanExtra(EXTRA_PHONE, false) ? TAB_PHONE : TAB_GITHUB);
        show(first);
        linkFrom(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        linkFrom(intent);
    }

    private void linkFrom(Intent intent) {
        String l = intent == null ? null : intent.getStringExtra(EXTRA_LINK);
        if (l == null) return;
        if (tab != TAB_GITHUB) show(TAB_GITHUB);
        if (link == null) {
            // review H2: the GitHub tab isn't there (no storage access yet): say what to do, never crash
            list.addView(v.line("Then open the shared link again: " + l, false), v.full(-1, 8));
            return;
        }
        link.setText(l);
        // a shared link only looks the project up (nothing is restored without a tap)
        importLink(false);
    }

    @Override
    public void onBackPressed() {
        // v2.1.0: opened as the GitHub tab of the bottom bar: "Do you want to close GitHub?"
        if (com.sketchware.ai.ui.TabScreens.onBack(this)) return;
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        work.shutdownNow();
        lookups.shutdownNow();
        super.onDestroy();
    }

    private void subtitle() {
        String login = TokenStore.get(this).login();
        ((TextView) findViewById(R.id.gs_subtitle)).setText(tab == TAB_PHONE ? "Backups on this phone"
                : tab == TAB_DEMO ? "Demo projects on this phone"
                : login == null ? "GitHub · not signed in" : "GitHub · @" + login);
    }

    /** v2.0.4 callers: the phone tab or the GitHub tab. */
    private void show(boolean phone) {
        show(phone ? TAB_PHONE : TAB_GITHUB);
    }

    /**
     * v2.1.0 (owner's 16 Nov directive): three tabs on one line, [ GitHub ] [ Demo ] [ On this phone ], 12sp, equal
     * widths; GitHub first.
     */
    private void show(int which) {
        tab = which;
        boolean phone = which == TAB_PHONE;
        phoneTab = phone;
        tabs.removeAllViews();
        tabs.addView(tab("GitHub", which == TAB_GITHUB, x -> show(TAB_GITHUB)), tabParams(0));
        tabs.addView(tab("Demo", which == TAB_DEMO, x -> show(TAB_DEMO)), tabParams(6));
        tabs.addView(tab("On this phone", phone, x -> show(TAB_PHONE)), tabParams(6));
        list.removeAllViews();
        subtitle();
        link = null;
        if (!storageReady()) {
            list.addView(v.line("Sketchware AI needs access to its files first: open the projects list once and allow "
                    + "it, then come back.", true), v.full(-1, 8));
            list.addView(v.pill("Open the projects list", false, true, x -> {
                Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (i != null) startActivity(i);
                finish();
            }), v.full(46, 12));
            return;
        }
        if (phone) buildPhone();
        else if (which == TAB_DEMO) buildDemo();
        else buildGitHub();
    }

    private TextView tab(String text, boolean on, View.OnClickListener click) {
        TextView t = v.glassPill(text, true, click);
        t.setBackgroundResource(on ? R.drawable.ob_seg_pill_on : R.drawable.ob_seg_pill);
        t.setTextColor(on ? 0xFFFFFFFF : v.color(R.color.ob_text_2));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setPadding(v.dp(6), 0, v.dp(6), 0);
        // 12sp, smaller only when the phone's font size needs it ("On this phone" whole at font scale 1.3)
        GitHubViews.fitOneLine(t, 9, 12);
        return t;
    }

    private LinearLayout.LayoutParams tabParams(int leftDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, v.dp(38), 1f);
        p.leftMargin = v.dp(leftDp);
        return p;
    }

    private static boolean storageReady() {
        File root = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware");
        return root.isDirectory() && root.canWrite();
    }

    // ------------------------------------------------------------------ GitHub

    private void buildGitHub() {
        list.addView(title("Import from a link"), v.full(-1, 4));
        // v2.0.5: a box that takes typing, with Paste inside its right edge (pastes and looks the link up at once)
        EditText[] box = new EditText[1];
        // v2.1.0: Paste (or the keyboard's Go) imports at once: the front version (★ pinned, else the newest), or the one
        // .swb a link points to; the other versions are listed under it
        list.addView(v.linkBox(box, () -> importLink(true)), v.full(50, 8));
        link = box[0];
        TextView hint = v.line("Paste a project repository, folder or .swb link: the import starts at once", false);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(hint, v.full(-1, 6));
        linkStatus = v.line("", false);
        linkStatus.setVisibility(View.GONE);
        list.addView(linkStatus, v.full(-1, 8));
        found = new LinearLayout(this);
        found.setOrientation(LinearLayout.VERTICAL);
        list.addView(found, v.full(-1, 4));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title("Your projects on GitHub"), new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(v.smallPill("Refresh", false, true, x -> loadProjects(true)), new LinearLayout.LayoutParams(-2, v.dp(36)));
        list.addView(head, v.full(-1, 22));
        projects = new LinearLayout(this);
        projects.setOrientation(LinearLayout.VERTICAL);
        list.addView(projects, v.full(-1, 4));

        list.addView(title("Older backups (" + CloudBackup.REPO + ")"), v.full(-1, 22));
        older = new LinearLayout(this);
        older.setOrientation(LinearLayout.VERTICAL);
        list.addView(older, v.full(-1, 4));

        if (Build.VERSION.SDK_INT >= 31) {
            list.addView(v.actionCard(R.drawable.ob_ic_github, "Open shared GitHub links here",
                    "Android asks once: Open by default > Add links > github.com", x -> {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException e) {
                            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        }
                    }), v.full(-1, 22));
        }
        loadProjects(false);
        loadOlder();
    }

    private TextView title(String text) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Title);
        t.setText(text);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private String token() throws GitHubException {
        TokenStore store = TokenStore.get(this);
        String token = store.token();
        if (token == null) {
            String problem = store.problem();
            throw new GitHubException("keystore", problem == null
                    ? "The sign-in couldn't be read from the Android Keystore just now. Try again."
                    : "This phone can't read the saved sign-in (" + problem + "): tap Sign in again on the account card.");
        }
        return token;
    }

    /** The account's project repositories: the phone's copy at once, GitHub's in the background. */
    private void loadProjects(boolean refreshing) {
        projects.removeAllViews();
        String login = TokenStore.get(this).login();
        if (login == null) {
            projects.addView(v.row("Sign in to see your projects", "Every project you backed up, from any phone",
                    v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, l -> {
                        if (!isFinishing()) show(false);
                    }))), v.full(-1, 8));
            return;
        }
        long[] at = new long[1];
        List<ProjectRepo.Info> kept = RepoCache.repos(this, login, at);
        if (kept != null) {
            renderProjects(kept, login, refreshing ? "Checking GitHub…" : (at[0] > 0 ? "Checked "
                    + CloudBackup.formatTime(at[0]) + " · checking GitHub…" : "Checking GitHub…"), false);
        } else {
            projects.addView(v.line("Looking for your projects on GitHub…", false), v.full(-1, 8));
        }
        work.execute(() -> {
            try {
                List<ProjectRepo.Info> fresh = ProjectRepo.listOwn(token());
                RepoCache.saveRepos(this, login, fresh);
                ui(() -> renderProjects(fresh, login, null, false));
                for (ProjectRepo.Info info : fresh) {
                    try {
                        List<ProjectRepo.Version> versions = ProjectRepo.versions(token(), login, info.name, false);
                        RepoCache.saveVersions(this, info.number, versions);
                        ui(() -> renderVersions(info, login, versions));
                    } catch (Exception e) {
                        ui(() -> versionsFailed(info, GitHubImportDialog.text(e)));
                    }
                }
            } catch (Exception e) {
                String why = GitHubImportDialog.text(e);
                ui(() -> {
                    if (kept != null) {
                        renderProjects(kept, login, "Kept on this phone · " + why, true);
                    } else {
                        projects.removeAllViews();
                        projects.addView(v.line(why, true), v.full(-1, 8));
                        projects.addView(v.smallPill("Try again", true, true, x -> loadProjects(true)), pillAt(6));
                    }
                });
            }
        });
    }

    private final java.util.Map<Long, LinearLayout> cards = new java.util.HashMap<>();

    private void renderProjects(List<ProjectRepo.Info> repos, String login, String note, boolean failed) {
        projects.removeAllViews();
        cards.clear();
        if (note != null) projects.addView(v.line(note, failed), v.full(-1, 4));
        if (repos.isEmpty()) {
            projects.addView(v.line("No project repositories yet. Back up a project to GitHub (its card > Back up > "
                    + "Back up to GitHub): each project gets its own repository.", false), v.full(-1, 8));
            return;
        }
        java.util.Map<Long, ImageView> icons = new java.util.HashMap<>();
        for (ProjectRepo.Info info : repos) {
            // v2.1.1 (owner's 21 Nov directive "slim cards like Demo and Home"): two rows: the app icon, the name and
            // one line, Public / Private and Share; then the version selector, Restore and Pin
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.ob_pill16);
            card.setPadding(v.dp(10), v.dp(8), v.dp(8), v.dp(8));
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.FrameLayout tile = iconTile(displayName(info.name));
            top.addView(tile, new LinearLayout.LayoutParams(v.dp(40), v.dp(40)));
            icons.put(info.number, (ImageView) tile.getChildAt(1));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this, null, 0, R.style.Ob_Text_Label);
            name.setText(displayName(info.name));
            name.setTypeface(v.bold());
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            GitHubViews.oneLine(name);
            texts.addView(name);
            TextView when = v.line(info.updated > 0 ? "Updated " + CloudBackup.formatTime(info.updated) : info.name, false);
            when.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            GitHubViews.oneLine(when);
            texts.addView(when);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.leftMargin = v.dp(10);
            top.addView(texts, tp);
            TextView badge = badge(info.isPrivate ? "Private" : "Public", !info.isPrivate);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, v.dp(26));
            bp.leftMargin = v.dp(6);
            top.addView(badge, bp);
            ImageView share = v.iconButton(R.drawable.ob_ic_share, "Share " + info.name);
            share.setOnClickListener(x -> ProjectLinks.share(this, displayName(info.name), ProjectRepo.link(login, info.name)));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(v.dp(30), v.dp(30));
            sp.leftMargin = v.dp(6);
            top.addView(share, sp);
            card.addView(top);
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(body, v.full(-1, 6));
            List<ProjectRepo.Version> kept = RepoCache.versions(this, login, info.number);
            cards.put(info.number, body);
            if (kept != null) {
                renderVersions(info, login, kept);
            } else {
                body.addView(v.line("Loading versions…", false));
            }
            projects.addView(card, v.full(-1, 8));
        }
        loadIcons(repos, login, icons);
    }

    /** v2.1.1: a row of GitHubViews.row with the project's tile in front of its texts; the tile's icon view. */
    private ImageView withIcon(LinearLayout row, String name) {
        android.widget.FrameLayout tile = iconTile(name);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(v.dp(40), v.dp(40));
        p.rightMargin = v.dp(10);
        row.addView(tile, 0, p);
        row.setPadding(v.dp(10), v.dp(8), v.dp(10), v.dp(8));
        return (ImageView) tile.getChildAt(1);
    }

    /**
     * v2.1.1: the Home card's tile at 40dp (the Liquid Glass design file: two letters in Sora bold {@code ob_accent_2}
     * on {@code ob_avatar}); the project's own icon covers it once found (child 1). A project without an own icon keeps
     * its letters, as on Home: its app gets the template's icon.
     */
    private android.widget.FrameLayout iconTile(String name) {
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
        tile.setBackgroundResource(R.drawable.ob_avatar);
        tile.setClipToOutline(true);
        TextView letters = new TextView(this);
        letters.setText(GC.monogram(name == null ? "" : name));
        letters.setTypeface(v.bold());
        letters.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        letters.setTextColor(v.color(R.color.ob_accent_2));
        letters.setGravity(Gravity.CENTER);
        letters.setIncludeFontPadding(false);
        tile.addView(letters, new android.widget.FrameLayout.LayoutParams(-1, -1));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setContentDescription(null);
        tile.addView(icon, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return tile;
    }

    /** v2.1.1: the cards' real icons in the background (the linked project's own icon, else GitHub's copy). */
    private void loadIcons(List<ProjectRepo.Info> repos, String login, java.util.Map<Long, ImageView> icons) {
        List<ProjectRepo.Info> list = new ArrayList<>(repos);
        int px = v.dp(40);
        lookups.execute(() -> {
            java.util.Map<Long, String> linked = RepoIcons.linkedProjects(login);
            String token = TokenStore.get(getApplicationContext()).login() == null
                    || GitHubSession.isRefused(getApplicationContext()) ? null : TokenStore.get(getApplicationContext()).token();
            for (ProjectRepo.Info info : list) {
                android.graphics.Bitmap b = RepoIcons.forRepo(getApplicationContext(), token, login, info.name, info.number,
                        linked.get(info.number), px);
                if (b == null) continue;
                ui(() -> {
                    ImageView view = icons.get(info.number);
                    if (view != null && cards.containsKey(info.number)) view.setImageBitmap(b);
                });
            }
        });
    }

    private static String displayName(String repo) {
        String n = repo.regionMatches(true, 0, ProjectRepo.PREFIX, 0, ProjectRepo.PREFIX.length())
                ? repo.substring(ProjectRepo.PREFIX.length()) : repo;
        if (n.regionMatches(true, 0, "Public-", 0, 7)) n = n.substring(7);
        return n.replace('_', ' ');
    }

    private TextView badge(String text, boolean accent) {
        TextView t = new TextView(this, null, 0, R.style.Ob_Text_Caption);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(v.bold());
        t.setGravity(Gravity.CENTER);
        GitHubViews.oneLine(t);
        t.setTextColor(accent ? v.color(R.color.ob_accent) : v.color(R.color.ob_text_2));
        t.setBackgroundResource(R.drawable.ob_pill16_state);
        t.setPadding(v.dp(10), 0, v.dp(10), 0);
        return t;
    }

    private void versionsFailed(ProjectRepo.Info info, String why) {
        LinearLayout body = cards.get(info.number);
        if (body == null || body.getChildCount() > 1) return;
        body.removeAllViews();
        body.addView(v.line(why, true));
    }

    /**
     * v2.0.5 (owner's 16 Nov directive): the pinned (★ Standard) version in the front slot when there is one, else the
     * newest ("New"); then the rest newest first; at most five.
     */
    static List<ProjectRepo.Version> ordered(List<ProjectRepo.Version> versions) {
        return ProjectRepo.ordered(versions);
    }

    static long newestTime(List<ProjectRepo.Version> versions) {
        long t = Long.MIN_VALUE;
        for (ProjectRepo.Version x : versions) t = Math.max(t, x.time);
        return t;
    }

    /** "★ Pinned", "New", both, or nothing. */
    static String badgeOf(ProjectRepo.Version ver, boolean newest) {
        if (ver.standard && newest) return "★ Pinned · New";
        if (ver.standard) return "★ Pinned";
        return newest ? "New" : "";
    }

    private void renderVersions(ProjectRepo.Info info, String login, List<ProjectRepo.Version> versions) {
        LinearLayout body = cards.get(info.number);
        if (body == null) return;
        body.removeAllViews();
        List<ProjectRepo.Version> shown = ordered(versions);
        if (shown.isEmpty()) {
            body.addView(v.line("No versions yet (back it up from its card).", false));
            return;
        }
        long newest = newestTime(versions);
        // v2.1.1: one compact row: the version selector (★ Pinned / New, how many more; a tap lists them), Restore, Pin
        ProjectRepo.Version[] selected = {shown.get(0)};
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            body.removeAllViews();
            ProjectRepo.Version s = selected[0];
            String mark = badgeOf(s, s.time == newest);
            int more = shown.size() - 1;
            TextView selector = v.smallPill(s.label() + (mark.isEmpty() ? "" : " · " + mark)
                    + (more > 0 ? "  +" + more : "") + "  ▾", false, true, x -> chooseVersion(shown, newest, selected,
                    refresh[0]));
            selector.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            selector.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            selector.setPadding(v.dp(12), 0, v.dp(10), 0);
            GitHubViews.oneLine(selector);
            selector.setContentDescription("Version " + s.label() + " " + mark + (more > 0 ? ", " + more + " more" : ""));
            body.addView(selector, new LinearLayout.LayoutParams(0, v.dp(32), 1f));
            TextView restore = v.smallPill("Restore", true, true, x -> restoreVersion(info, login, s));
            restore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            restore.setContentDescription("Restore " + s.label());
            GitHubViews.oneLine(restore);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, v.dp(32));
            rp.leftMargin = v.dp(6);
            body.addView(restore, rp);
            // v2.0.5: Pin moves the version to the front slot (★ Pinned); Unpin gives it back to New
            TextView pin = v.smallPill(s.standard ? "Unpin" : "★ Pin", false, true,
                    x -> setStandard(info, login, s, !s.standard));
            pin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            GitHubViews.oneLine(pin);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, v.dp(32));
            pp.leftMargin = v.dp(6);
            body.addView(pin, pp);
        };
        refresh[0].run();
    }

    /** The versions of a card to pick from (newest first, ★ Pinned and New marked, with their dates). */
    private void chooseVersion(List<ProjectRepo.Version> shown, long newest, ProjectRepo.Version[] selected, Runnable refresh) {
        String[] labels = new String[shown.size()];
        int at = 0;
        for (int i = 0; i < shown.size(); i++) {
            ProjectRepo.Version ver = shown.get(i);
            String mark = badgeOf(ver, ver.time == newest);
            labels[i] = ver.label() + (mark.isEmpty() ? "" : " · " + mark)
                    + (ver.time > 0 ? "\n" + CloudBackup.formatTime(ver.time) : "");
            if (ver == selected[0]) at = i;
        }
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Version")
                .setSingleChoiceItems(labels, at, (d, which) -> {
                    selected[0] = shown.get(which);
                    d.dismiss();
                    refresh.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setStandard(ProjectRepo.Info info, String login, ProjectRepo.Version ver, boolean on) {
        android.widget.Toast.makeText(this, on ? "Pinning " + ver.label() + "…" : "Unpinning…",
                android.widget.Toast.LENGTH_SHORT).show();
        work.execute(() -> {
            try {
                ProjectRepo repo = ProjectRepo.byNumber(token(), login, info.number);
                if (repo == null) throw new GitHubException("not_found", info.name + " isn't on GitHub any more.");
                repo.setStandard(ver.id, on);
                List<ProjectRepo.Version> versions = repo.versions(false);
                RepoCache.saveVersions(this, info.number, versions);
                linkedProjectsStandard(login, info.number, on ? ver.tag : null);
                ui(() -> {
                    renderVersions(info, login, versions);
                    android.widget.Toast.makeText(this, on ? ver.label() + " is pinned: it stays in front and is "
                            + "never removed" : "Unpinned", android.widget.Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String why = GitHubImportDialog.text(e);
                ui(() -> android.widget.Toast.makeText(this, why, android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    /** The projects on this phone linked to that repository learn the new Standard tag. */
    private static void linkedProjectsStandard(String login, long number, String tag) {
        File[] data = new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/data").listFiles();
        if (data == null) return;
        for (File p : data) {
            if (p.getName().matches("\\d+") && ProjectLinks.repoNumber(p.getName(), login) == number) {
                ProjectLinks.standardNow(p.getName(), login, tag);
            }
        }
    }

    private void restoreVersion(ProjectRepo.Info info, String login, ProjectRepo.Version ver) {
        if (!AuthGuard.check(this)) return;
        CloudSync.Import src = new CloudSync.Import();
        src.owner = login;
        src.repo = info.name;
        src.number = info.number;
        src.repoPrivate = info.isPrivate;
        src.version = ver;
        src.size = ver.size;
        // own backups: the libraries they hold are copied when missing here (the ones on the phone are kept)
        src.withLibraries = true;
        src.title = displayName(info.name) + " " + ver.label();
        CloudSync.importProject(this, src);
    }

    // ------------------------------------------------------------------ older backups (read only)

    private void loadOlder() {
        older.removeAllViews();
        String login = TokenStore.get(this).login();
        if (login == null) return;
        List<CloudBackup.Entry> kept = GitHubImportDialog.cachedList(this, login, new long[1]);
        if (kept != null) renderOlder(kept, login);
        else older.addView(v.line("Looking…", false), v.full(-1, 4));
        work.execute(() -> {
            try {
                CloudBackup cloud = new CloudBackup(token(), login);
                List<CloudBackup.Entry> entries = cloud.openIfExists() ? cloud.list() : new ArrayList<>();
                synchronized (GitHubImportDialog.LIST_LOCK) {
                    GitHubImportDialog.saveCachedList(this, login, entries, true);
                }
                ui(() -> renderOlder(entries, login));
            } catch (Exception e) {
                String why = GitHubImportDialog.text(e);
                ui(() -> {
                    if (kept == null) {
                        older.removeAllViews();
                        older.addView(v.line(why, true), v.full(-1, 4));
                    }
                });
            }
        });
    }

    private void renderOlder(List<CloudBackup.Entry> entries, String login) {
        older.removeAllViews();
        if (entries.isEmpty()) {
            older.addView(v.line("None.", false), v.full(-1, 4));
            return;
        }
        List<CloudBackup.Entry> sorted = new ArrayList<>(entries);
        java.util.Collections.sort(sorted, (x, y) -> Long.compare(y.time, x.time));
        for (CloudBackup.Entry entry : sorted) {
            String name = entry.name == null ? entry.path : entry.name;
            String detail = (entry.packageName == null ? "" : entry.packageName + " · ") + CloudBackup.formatTime(entry.time)
                    + (entry.size > 0 ? " · " + CloudSync.megabytes(entry.size) : "");
            older.addView(v.row(name, detail, v.smallPill("Restore", true, true, x -> olderVersions(login, entry, name))),
                    v.full(-1, 8));
        }
    }

    private void olderVersions(String login, CloudBackup.Entry entry, String name) {
        if (busy) return;
        busy = true;
        work.execute(() -> {
            try {
                CloudBackup cloud = new CloudBackup(token(), login);
                if (!cloud.openIfExists()) throw new GitHubException("not_found", CloudBackup.REPO + " isn't on GitHub any more.");
                List<CloudBackup.Version> history = cloud.history(entry.path);
                ui(() -> {
                    busy = false;
                    LinearLayout rows = new LinearLayout(this);
                    rows.setOrientation(LinearLayout.VERTICAL);
                    rows.setPadding(v.dp(20), v.dp(8), v.dp(20), v.dp(4));
                    rows.addView(v.header(R.drawable.ob_ic_restore, "Restore " + name, "Always as a new project"));
                    AlertDialog[] sheet = new AlertDialog[1];
                    for (int i = 0; i < history.size(); i++) {
                        CloudBackup.Version version = history.get(i);
                        String when = version.date.replace('T', ' ').replace("Z", " UTC");
                        rows.addView(v.row(i == 0 ? "Newest backup" : "Backup " + (history.size() - i), when,
                                v.smallPill("Restore", true, true, x -> {
                                    sheet[0].dismiss();
                                    CloudSync.Import src = new CloudSync.Import();
                                    src.owner = login;
                                    src.repo = CloudBackup.REPO;
                                    src.legacyPath = entry.path;
                                    src.legacySha = version.sha;
                                    src.size = entry.size;
                                    src.withLibraries = true;
                                    src.title = name;
                                    if (AuthGuard.check(this)) CloudSync.importProject(this, src);
                                })), v.full(-1, i == 0 ? 14 : 8));
                    }
                    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
                    scroll.addView(rows);
                    sheet[0] = new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                            .setView(scroll)
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                String why = GitHubImportDialog.text(e);
                ui(() -> {
                    busy = false;
                    android.widget.Toast.makeText(this, why, android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ------------------------------------------------------------------ a link

    private void status(String text, boolean error) {
        linkStatus.setText(text == null ? "" : text);
        linkStatus.setTextColor(v.color(error ? R.color.ob_error : R.color.ob_text_2));
        linkStatus.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void importLink(boolean auto) {
        if (busy) return;
        GitHubImport.Source source = GitHubImport.parse(link.getText().toString());
        found.removeAllViews();
        if (source == null) {
            status("That isn't a GitHub repository, folder or .swb link.", true);
            return;
        }
        busy = true;
        status("Looking at " + source.owner + "/" + source.repo + "…", false);
        String login = TokenStore.get(this).login();
        lookups.execute(() -> {
            try {
                String token = login == null ? null : token();
                // v2.1.0: a lookup waits 10 seconds at most
                GitHubHttp.Response r = GitHubHttp.quick(RepoViewerActivity.TIMEOUT_MILLIS, () -> GitHubHttp.api("GET",
                        "/repos/" + CloudBackup.enc(source.owner) + "/" + CloudBackup.enc(source.repo), token, null));
                if (r.code == 404) {
                    ui(() -> {
                        busy = false;
                        status(login == null
                                ? "This project is private, or the link is wrong: sign in with the owner's GitHub account "
                                + "to restore a private project."
                                : "This project is private, or the link is wrong: only the owner's GitHub account (@"
                                + source.owner + ") can restore it.", true);
                        if (login == null) {
                            found.addView(v.smallPill("Sign in", true, true, x -> GitHubInAppAuthDialog.show(this, l -> {
                                if (!isFinishing()) importLink(true);
                            })), pillAt(6));
                        }
                    });
                    return;
                }
                if (!r.ok()) throw CloudBackup.http("Opening " + source.owner + "/" + source.repo, r);
                com.google.gson.JsonObject repoInfo = DeviceFlow.parse(r.body);
                boolean isPrivate = !repoInfo.has("private") || repoInfo.get("private").getAsBoolean();
                long number = ProjectRepo.number(repoInfo);
                boolean own = login != null && login.equalsIgnoreCase(source.owner);
                List<ProjectRepo.Version> versions = source.file ? new ArrayList<>()
                        : GitHubHttp.quick(RepoViewerActivity.TIMEOUT_MILLIS,
                        () -> ProjectRepo.versions(token, source.owner, source.repo, false));
                if (!versions.isEmpty()) {
                    List<ProjectRepo.Version> shown = ordered(versions);
                    ui(() -> {
                        busy = false;
                        status((isPrivate ? "Private" : "Public") + " project " + source.owner + "/" + source.repo + ": "
                                + shown.size() + (shown.size() == 1 ? " version" : " versions") + ".", false);
                        for (int i = 0; i < shown.size(); i++) {
                            ProjectRepo.Version ver = shown.get(i);
                            String tagLine = (i == 0 ? "Latest · " : ver.standard ? "★ Standard · " : "")
                                    + (ver.time > 0 ? CloudBackup.formatTime(ver.time) : ver.tag)
                                    + (ver.size > 0 ? " · " + CloudSync.megabytes(ver.size) : "");
                            found.addView(v.row(displayName(source.repo) + " " + ver.label(), tagLine,
                                    v.smallPill("Import", true, true, x -> importRelease(source, number, isPrivate, own, ver))),
                                    v.full(-1, 8));
                        }
                        if (auto) {
                            ProjectRepo.Version front = shown.get(0);
                            status("Importing " + displayName(source.repo) + " " + front.label() + " (the "
                                    + (front.standard ? "pinned" : "newest") + " version)…"
                                    + (shown.size() > 1 ? " The other versions are below." : ""), false);
                            importRelease(source, number, isPrivate, own, front);
                        }
                        if (!isPrivate) {
                            TextView note = v.line("Public project: you can also look at it on GitHub.", false);
                            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            found.addView(note, v.full(-1, 6));
                            found.addView(v.smallPill("Open on GitHub", false, true, x -> openBrowser(
                                    ProjectRepo.link(source.owner, source.repo))), pillAt(6));
                        }
                    });
                    return;
                }
                List<String> files = GitHubImport.listBackups(token, source);
                ui(() -> {
                    busy = false;
                    if (files.isEmpty()) {
                        status("No backups in " + source.owner + "/" + source.repo + ".", true);
                        return;
                    }
                    status(files.size() == 1 ? "1 backup:" : files.size() + " backups:", false);
                    if (auto && files.size() == 1) {
                        status("Importing " + SwbParts.displayName(files.get(0)) + "…", false);
                        importFile(source, number, isPrivate, own, files.get(0));
                    }
                    for (String path : files) {
                        String file = SwbParts.displayName(path);
                        String folder = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "(top folder)";
                        found.addView(v.row(file, folder, v.smallPill("Import", true, true,
                                x -> importFile(source, number, isPrivate, own, path))), v.full(-1, 8));
                    }
                });
            } catch (Exception e) {
                String why = RepoViewerActivity.whyFailed(e);
                ui(() -> {
                    busy = false;
                    status(why, true);
                });
            }
        });
    }

    // ------------------------------------------------------------------ v2.1.0: Demo

    /**
     * v2.1.0 (owner's 16 Nov directive: the Demo row off the projects list, a centred Demo tab): the demo projects on
     * this phone numbered #1, #2 … (newest demo version first, {@link com.sketchware.ai.project.Demos#sort}); a tap
     * opens one, a long press offers Delete. None yet: a button makes the GitHub API Demo.
     */
    private void buildDemo() {
        list.addView(title("Demo projects"), v.full(-1, 4));
        TextView note = v.line("Ready-made projects that show what Sketchware AI can do: open one to look at its screens and "
                + "blocks, or run it.", false);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(note, v.full(-1, 4));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.addView(v.line("Reading the projects…", false));
        list.addView(rows, v.full(-1, 8));
        work.execute(() -> {
            List<java.util.HashMap<String, Object>> demos = demoProjects();
            ui(() -> renderDemos(rows, demos));
        });
    }

    static List<java.util.HashMap<String, Object>> demoProjects() {
        List<java.util.HashMap<String, Object>> demos = new ArrayList<>();
        try {
            List<java.util.HashMap<String, Object>> all = lC.a();
            if (all != null) {
                for (java.util.HashMap<String, Object> p : all) {
                    if (com.sketchware.ai.project.Demos.isDemo(p)) demos.add(p);
                }
            }
        } catch (RuntimeException ignored) {
            // no project list: none
        }
        com.sketchware.ai.project.Demos.sort(demos);
        return demos;
    }

    private void renderDemos(LinearLayout rows, List<java.util.HashMap<String, Object>> demos) {
        rows.removeAllViews();
        if (demos.isEmpty()) {
            rows.addView(v.line("No demo project on this phone yet.", false));
            rows.addView(v.smallPill("Make the GitHub API Demo", true, true, x -> {
                android.widget.Toast.makeText(this, "Making the GitHub API Demo…", android.widget.Toast.LENGTH_SHORT).show();
                work.execute(() -> {
                    String made = null;
                    try {
                        made = com.sketchware.ai.project.GitHubSample.create(getApplicationContext());
                    } catch (Throwable e) {
                        // review (LOW): an Error too (shown below; never a crash on the executor thread)
                        mod.jbk.util.LogUtil.e("GitHubBackups", "Demo not made", e);
                    }
                    String id = made;
                    ui(() -> {
                        if (id == null) {
                            android.widget.Toast.makeText(this, "The demo couldn't be made.", android.widget.Toast.LENGTH_LONG).show();
                        }
                        if (tab == TAB_DEMO) show(TAB_DEMO);
                    });
                });
            }), pillAt(8));
            return;
        }
        for (int i = 0; i < demos.size(); i++) {
            java.util.HashMap<String, Object> p = demos.get(i);
            String scId = yB.c(p, "sc_id");
            String app = yB.c(p, "my_app_name");
            String name = app.isEmpty() ? yB.c(p, "my_ws_name").replace('_', ' ') : app;
            String detail = "Project #" + scId + " · v" + yB.c(p, "sc_ver_name") + " ("
                    + yB.c(p, "sc_ver_code") + ")" + (i == 0 ? " · Latest demo" : "");
            TextView open = v.smallPill("Open", true, true, x -> openProject(scId));
            open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            GitHubViews.oneLine(open);
            LinearLayout row = v.row("#" + (i + 1) + "  " + name, detail, open);
            // v2.1.1: the demo's own icon
            ImageView demoIcon = withIcon(row, name);
            work.execute(() -> {
                android.graphics.Bitmap b = RepoIcons.decode(RepoIcons.localIcon(scId), v.dp(40));
                if (b != null) ui(() -> demoIcon.setImageBitmap(b));
            });
            row.setOnLongClickListener(x -> {
                new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                        .setTitle("Delete " + name + " (#" + scId + ")?")
                        .setMessage("The demo project is removed from this phone (its GitHub backups stay).")
                        .setPositiveButton("Delete", (d, w) -> work.execute(() -> {
                            lC.a(getApplicationContext(), scId);
                            ui(() -> {
                                if (tab == TAB_DEMO) show(TAB_DEMO);
                            });
                        }))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
            rows.addView(row, v.full(-1, i == 0 ? 0 : 6));
        }
    }

    /** Opens a project in the editor (as the projects list does). */
    private void openProject(String scId) {
        Intent intent = new Intent(this, com.besome.sketch.design.DesignActivity.class);
        mod.hey.studios.project.ProjectTracker.setScId(scId);
        intent.putExtra("sc_id", scId);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void importRelease(GitHubImport.Source source, long number, boolean isPrivate, boolean own,
                               ProjectRepo.Version ver) {
        CloudSync.Import src = new CloudSync.Import();
        src.owner = source.owner;
        src.repo = source.repo;
        src.number = own ? number : 0;
        src.repoPrivate = isPrivate;
        src.version = ver;
        src.size = ver.size;
        src.title = displayName(source.repo) + " " + ver.label();
        start(src, own);
    }

    private void importFile(GitHubImport.Source source, long number, boolean isPrivate, boolean own, String path) {
        CloudSync.Import src = new CloudSync.Import();
        src.owner = source.owner;
        src.repo = source.repo;
        src.repoPrivate = isPrivate;
        src.link = source;
        src.linkPath = path;
        src.title = SwbParts.displayName(path).replace(".swb", "");
        start(src, own);
    }

    /** An own backup's libraries are copied when missing here; someone else's only when asked for. */
    private void start(CloudSync.Import src, boolean own) {
        if (!AuthGuard.check(this)) return;
        if (own) {
            src.withLibraries = true;
            CloudSync.importProject(this, src);
            return;
        }
        String from = src.owner + "/" + src.repo;
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Local libraries from " + from)
                .setMessage("If this backup brings local libraries, their code goes into the apps you build with them: "
                        + "copy them only if you trust " + from + ". Libraries already on this phone are never replaced. "
                        + "Without them, the Library Resolver can install the libraries from Maven.")
                .setPositiveButton("Without libraries", (d, w) -> {
                    src.withLibraries = false;
                    CloudSync.importProject(this, src);
                })
                .setNegativeButton("Copy libraries", (d, w) -> {
                    src.withLibraries = true;
                    src.librariesFrom = from;
                    CloudSync.importProject(this, src);
                })
                .show();
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException ignored) {
            // no browser
        }
    }

    // ------------------------------------------------------------------ on this phone

    private void buildPhone() {
        list.addView(v.actionCard(R.drawable.ob_ic_restore, "Pick a .swb file", "From any folder of the phone",
                x -> new BackupRestoreManager(this).restore()), v.full(-1, 4));
        list.addView(title("Backups on this phone"), v.full(-1, 22));
        TextView where = v.line(BackupFactory.getBackupDir(), false);
        where.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        list.addView(where, v.full(-1, 2));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        list.addView(rows, v.full(-1, 8));
        rows.addView(v.line("Reading…", false));
        work.execute(() -> {
            List<File> files = new ArrayList<>();
            collectBackups(new File(BackupFactory.getBackupDir()), files, 0);
            java.util.Collections.sort(files, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
            ui(() -> {
                rows.removeAllViews();
                if (files.isEmpty()) {
                    rows.addView(v.line("No .swb backups in this folder yet (a project's Back up > Save on phone).", false));
                    return;
                }
                java.util.Map<File, ImageView> icons = new java.util.HashMap<>();
                for (File f : files) {
                    String folder = f.getParentFile() == null ? "" : f.getParentFile().getName();
                    String detail = CloudBackup.formatTime(f.lastModified()) + " · " + CloudSync.megabytes(f.length())
                            + (folder.equals(new File(BackupFactory.getBackupDir()).getName()) ? "" : " · " + folder);
                    TextView restore = v.smallPill("Restore", true, true, x -> restoreLocal(f));
                    restore.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    GitHubViews.oneLine(restore);
                    LinearLayout row = v.row(f.getName().replaceAll("(?i)\\.swb$", ""), detail, restore);
                    icons.put(f, withIcon(row, f.getName().replaceAll("(?i)\\.swb$", "")));
                    rows.addView(row, v.full(-1, 6));
                }
                // v2.1.1: each backup's own app icon (its resources/icons/icon.png)
                int px = v.dp(40);
                work.execute(() -> {
                    for (File f : files) {
                        android.graphics.Bitmap b = RepoIcons.decode(RepoIcons.fromSwb(f), px);
                        if (b != null) ui(() -> icons.get(f).setImageBitmap(b));
                    }
                });
            });
        });
    }

    private static void collectBackups(File dir, List<File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 2) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().equals("staging") && !f.getName().endsWith("_temp")) collectBackups(f, out, depth + 1);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".swb") && f.length() > 0) {
                out.add(f);
            }
        }
    }

    private void restoreLocal(File f) {
        if (!AuthGuard.check(this)) return;
        boolean libraries = BackupFactory.zipContainsFile(f.getAbsolutePath(), "local_libs");
        Runnable go = () -> {
            GitHubProgress progress = GitHubProgress.show(this, R.drawable.ob_ic_restore, "Restore " + f.getName());
            progress.update(30, "Restoring as a new project…");
            new BackupRestoreManager(this).doRestore(f.getAbsolutePath(), libraries, null, (ok, message) -> {
                ProjectLinks.listChanged();
                if (ok) progress.done(message);
                else progress.failed(message, false);
            });
        };
        if (!libraries) {
            go.run();
            return;
        }
        new ObAlertBuilder(this, R.style.SketchwareAI_GlassAlertDialog)
                .setTitle("Local libraries")
                .setMessage("This backup holds local libraries. They are copied when this phone doesn't have them yet; "
                        + "libraries already here are kept as they are.")
                .setPositiveButton("Restore", (d, w) -> go.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private void ui(Runnable r) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    private LinearLayout.LayoutParams pillAt(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, v.dp(36));
        params.topMargin = v.dp(topDp);
        return params;
    }
}
