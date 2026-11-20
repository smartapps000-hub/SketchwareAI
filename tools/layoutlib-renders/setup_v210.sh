#!/bin/bash
# v2.1.0: on top of setup_v205.sh: the Home screen without drawer and floating button (main.xml as it is, project cards
# in place of the pager) with the Honey-Drop bar (ui.HoneyDropBar, drawn at chosen moments of a tap with
# frameForTests), Restore projects with its GitHub | Demo | On this phone tabs and compact cards, the Settings screen
# (ui.SettingsActivity), the Play Store (list, an app's page with pictures / Get it / reviews, Publish) and Community
# (github.PlayStoreActivity, CommunityActivity, PlayStore, GitHubGraphQL as they are, on canned GitHub answers), and the
# repository viewer showing its saved page with the 10-second timeout line. Screens that take an android.app.Activity
# parameter get the window-less stand-in base instead (the only change besides the ones setup_v204/v205 make).
# Usage: setup_v210.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V210*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v205.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; U=$J/com/sketchware/ai/ui; P=$J/com/sketchware/ai/project
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' \
  -e 's/\(android\.os\.\)\{0,1\}Environment\.getExternalStorageDirectory()/new java.io.File(System.getProperty("java.io.tmpdir"), "render-storage")/g' \
  -e 's/\bActivity activity\b/com.besome.sketch.lib.base.BaseAppCompatActivity activity/g' "$1" > "$2"; }
for f in PlayStore PlayStoreActivity CommunityActivity GitHubGraphQL RepoViewerActivity GitHubBackupsActivity DrawerAccountCard; do
  real $SRC/java/com/sketchware/ai/github/$f.java $G/$f.java
done
sed -i 's/androidx\.core\.content\.FileProvider\.a(/androidx.core.content.FileProvider.getUriForFile(/' $G/RepoViewerActivity.java
sed -i 's/^import a\.a\.a\.lC;$/import a.a.a.lC;/' $G/PlayStoreActivity.java
for f in HoneyDropBar TabScreens SettingsActivity; do real $SRC/java/com/sketchware/ai/ui/$f.java $U/$f.java; done
for f in $SRC/res/drawable/ob_*.xml; do cp $f $DST/res/drawable/; done
mkdir -p $DST/res/raw && cp $SRC/res/raw/honey_drop.wav $DST/res/raw/
cp $SRC/res/layout/main.xml $SRC/res/layout/myprojects_item.xml $DST/res/layout/
w() { mkdir -p "$(dirname "$1")"; cat > "$1"; }

w $DST/res/values/v210_ids.xml <<'X'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <item name="layout_coordinator" type="id" />
    <item name="viewpager" type="id" />
    <item name="fab" type="id" />
    <style name="ThemeOverlay.ToolbarMenu" parent="@style/ThemeOverlay.AppCompat.Light" />
</resources>
X
w $J/com/besome/sketch/lib/base/BaseAppCompatActivity.java <<'X'
package com.besome.sketch.lib.base;

/**
 * Render stand-in: the activity's own code on a context without a window (Activity itself can't run in layoutlib):
 * its content goes into a FrameLayout, posted work is queued for the test. v2.1.0: the lifecycle methods the new
 * screens override.
 */
public class BaseAppCompatActivity extends android.view.ContextThemeWrapper {
    public static final java.util.List<Runnable> QUEUE = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private android.widget.FrameLayout root;
    private android.content.Intent intent = new android.content.Intent();

    public void attach(android.content.Context base, android.content.Intent i) {
        attachBaseContext(base);
        setTheme(android.R.style.Theme_Material_NoActionBar);
        if (i != null) intent = i;
        root = new android.widget.FrameLayout(this);
    }

    public android.view.View root() { return root; }
    public void onCreate(android.os.Bundle b) { }
    public void onStart() { }
    public void onStop() { }
    public void onNewIntent(android.content.Intent i) { }
    public void onDestroy() { }
    public void onBackPressed() { }
    public void onActivityResult(int request, int result, android.content.Intent data) { }
    public void recreate() { }
    public void setContentView(int layout) { android.view.LayoutInflater.from(this).inflate(layout, root, true); }
    public <V extends android.view.View> V findViewById(int id) { return root.findViewById(id); }
    public android.content.Intent getIntent() { return intent; }
    public void setIntent(android.content.Intent i) { intent = i; }
    public void finish() { }
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public void runOnUiThread(Runnable r) { QUEUE.add(r); }
    @Override public void startActivity(android.content.Intent i) { }
    public void startActivityForResult(android.content.Intent i, int code) { }
}
X
w $G/GitHubHttp.java <<'X'
package com.sketchware.ai.github;
/**
 * Render stand-in: canned answers (ANSWERS: path → JSON, "" = 404; ERRORS: path → the exception thrown, e.g. a timeout);
 * anything else waits forever. quick() runs its call as the app's does.
 */
public final class GitHubHttp {
    public static final java.util.Map<String, String> ANSWERS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<String, java.io.IOException> ERRORS = new java.util.concurrent.ConcurrentHashMap<>();
    static final class Response { int code; String body = "{}"; boolean ok() { return code >= 200 && code < 300; } }
    interface Call<T> { T run() throws java.io.IOException, GitHubException; }
    static <T> T quick(int millis, Call<T> call) throws java.io.IOException, GitHubException { return call.run(); }
    static Response api(String m, String p, String t, String b) throws java.io.IOException {
        java.io.IOException e = ERRORS.get(p);
        if (e != null) throw e;
        String body = ANSWERS.get(p);
        if (body == null) { ProjectRepo.block(); throw new java.io.IOException("render"); }
        Response r = new Response(); r.code = body.isEmpty() ? 404 : 200; r.body = body; return r;
    }
    interface Progress { void bytes(long done, long total); }
    static Response downloadAsset(String p, String t, java.io.File to, long max, Progress progress) throws java.io.IOException { ProjectRepo.block(); throw new java.io.IOException("render"); }
}
X
w $G/GitHubSession.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the real GitHubSession's Profile and listener; PROFILE is the account shown. */
public final class GitHubSession {
    public static final class Profile {
        public String login; public String name; public String email; public java.io.File avatar;
        public int repositories = -1; public long checkedAt; public String problem;
    }
    public interface Listener { void changed(); }
    public static Profile PROFILE;
    public static boolean isRefused(android.content.Context c) { return PROFILE != null && PROFILE.problem != null; }
    public static Profile profile(android.content.Context c) { return PROFILE; }
    public static void addListener(Listener l) { }
    public static void removeListener(Listener l) { }
    public static void signOut(android.content.Context c) { }
}
X
w $G/AppControl.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the Config fields Settings reads; CONFIG is the file as last read. */
public final class AppControl {
    public static final class Config {
        public boolean appEnabled = true; public int minVersionCode; public boolean requireLogin; public String statusMessage = "";
        public String updateUrl; public String messageUrl; public boolean forceUpdate; public String updateSha256;
        public int latestVersionCode; public String latestVersionName; public String comingSoon = "";
    }
    public static Config CONFIG;
    public static Config cached(android.content.Context c) { return CONFIG; }
    public static boolean updateRequired() { return false; }
}
X
w $G/AppUpdater.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the real rule (a newer release or a minimum above 207, with a link). */
public final class AppUpdater {
    public static boolean newerAvailable(AppControl.Config c) {
        return c != null && c.updateUrl != null && (207 < c.minVersionCode || 207 < c.latestVersionCode);
    }
    public static void start(android.content.Context a, AppControl.Config c) { }
}
X
w $G/CloudSync.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class CloudSync {
    public static final String PREFS = "cloud_sync";
    public static final String AUTO_BACKUP_KEY = "P12I_github_backup";
    public static boolean autoBackupOn(android.content.Context c) { return true; }
    public static final class Import {
        public String owner, repo, legacyPath, legacySha, linkPath, title, librariesFrom; public long number, size;
        public boolean repoPrivate, withLibraries; public ProjectRepo.Version version; public GitHubImport.Source link;
    }
    public static void importProject(android.content.Context a, Import i) { }
    public static String megabytes(long bytes) { return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / 1048576.0); }
    public static GitHubProgress publish(android.content.Context a, String scId, String name, PublishDetails d) { return new GitHubProgress(); }
    public static GitHubProgress saveDetails(android.content.Context a, String scId, String name, PublishDetails d) { return new GitHubProgress(); }
}
X
w $G/GitHubProgress.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in (the sheet itself is rendered from GitHubViews.progressSheet). */
public final class GitHubProgress {
    public static GitHubProgress show(android.content.Context a, int icon, String title) { return new GitHubProgress(); }
    public void update(int p, String t) { }
    public void done(String t) { }
    public void failed(String t, boolean retry) { }
    public void failed(String t) { }
    public void whenClosed(java.util.function.Consumer<Boolean> c) { }
    public void actions(Runnable cancel, Runnable retry, String link) { }
    public void link(String url) { }
    public void restart() { }
    public boolean hidden() { return false; }
}
X
w $G/ProjectLinks.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class ProjectLinks {
    public static String REPO_NAME;
    public static String VISIBILITY = "public";
    public static void listChanged() { }
    public static void share(android.content.Context a, String name, String url) { }
    static void standardNow(String scId, String login, String tag) { }
    static void visibilityNow(String scId, String login, boolean isPrivate) { }
    static long repoNumber(String scId, String login) { return 0; }
    static String repoName(String scId, String login) { return REPO_NAME; }
    public static String visibility(String scId, String login) { return VISIBILITY; }
}
X
# ProjectRepo stand-in of v205 + ordered (the real rule, v2.1.0 moved it here)
python3 - "$G/ProjectRepo.java" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
add = '''    static List<Version> ordered(List<Version> versions) {
        List<Version> byDate = new ArrayList<>(versions);
        sortNewestFirst(byDate);
        List<Version> out = new ArrayList<>();
        for (Version x : byDate) if (x.standard) { out.add(x); break; }
        for (Version x : byDate) if (!out.contains(x)) out.add(x);
        return out.size() > MAX_VERSIONS ? new ArrayList<>(out.subList(0, MAX_VERSIONS)) : out;
    }
'''
i = s.rindex('}')
s = s[:i] + add + s[i:]
open(p, 'w').write(s)
PY
w $J/a/a/a/lC.java <<'X'
package a.a.a;
/** Render stand-in: the project list (LIST) and the sample project's entry (PROJECT). */
public final class lC {
    public static java.util.HashMap<String, Object> PROJECT = new java.util.HashMap<>();
    public static java.util.ArrayList<java.util.HashMap<String, Object>> LIST = new java.util.ArrayList<>();
    public static java.util.HashMap<String, Object> b(String scId) { return PROJECT; }
    public static java.util.ArrayList<java.util.HashMap<String, Object>> a() { return new java.util.ArrayList<>(LIST); }
    public static void a(android.content.Context c, String scId) { }
}
X
w $P/Demos.java <<'X'
package com.sketchware.ai.project;
/** Render stand-in: a project is a demo when its entry says so ("demo" = its version). */
public final class Demos {
    public static boolean isDemo(java.util.HashMap<String, Object> p) { return p.containsKey("demo"); }
    public static void sort(java.util.List<java.util.HashMap<String, Object>> demos) {
        demos.sort((a, b) -> Integer.parseInt(String.valueOf(b.get("demo"))) - Integer.parseInt(String.valueOf(a.get("demo"))));
    }
}
X
w $P/GitHubSample.java <<'X'
package com.sketchware.ai.project;
public final class GitHubSample { public static String create(android.content.Context c) { return null; } }
X
w $J/com/besome/sketch/design/DesignActivity.java <<'X'
package com.besome.sketch.design;
public class DesignActivity extends android.app.Activity { }
X
w $J/mod/hey/studios/project/ProjectTracker.java <<'X'
package mod.hey.studios.project;
public final class ProjectTracker { public static void setScId(String s) { } }
X
for c in com/besome/sketch/help/ProgramInfoActivity com/besome/sketch/help/SystemSettingActivity \
         com/besome/sketch/tools/NewKeyStoreActivity mod/hilal/saif/activities/tools/Tools \
         mod/ilyasse/activities/about/AboutModActivity; do
  pkg=$(dirname $c | tr / .); cls=$(basename $c)
  w $J/$c.java <<X
package $pkg;
public class $cls extends android.app.Activity { }
X
done
w $U/ThemeMode.java <<'X'
package com.sketchware.ai.ui;
public final class ThemeMode {
    public static final String[] LABELS = {"System default", "Light Glass", "Pure Dark Glass"};
    public static int get(android.content.Context c) { return 2; }
    public static boolean set(android.content.Context c, int mode) { return true; }
}
X
w $J/mod/SketchwareUtil.java <<'X'
package mod;
public final class SketchwareUtil { public static void toast(String s, int length) { } }
X
w $J/com/sketchware/remod/BuildConfig.java <<'X'
package com.sketchware.remod;
public final class BuildConfig { public static final String VERSION_NAME = "v2.1.0-render-minApi26"; public static final int VERSION_CODE = 207; }
X
echo copied v210
