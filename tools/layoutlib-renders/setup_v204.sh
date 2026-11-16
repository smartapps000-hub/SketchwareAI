#!/bin/bash
# v2.0.4: on top of the earlier setup scripts: the new Restore projects screen (GitHubBackupsActivity) and Publish to
# GitHub screen (PublishActivity) run as they are, from their own sources, on a stand-in activity base and stand-ins
# for the classes they reach into (network, Keystore, project files: sample data instead); the change analyzer
# (ProjectDiffAnalyzer) runs as it is on stand-in project data; the drawer account card (copy buttons) and the project
# card's two rows from their layouts. Usage: setup_v204.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V204Test'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
J=$DST/java; G=$J/com/sketchware/ai/github; U=$J/com/sketchware/ai/ui; P=$J/com/sketchware/ai/project
mkdir -p $G $U $P $J/com/besome/sketch/lib/base $J/com/besome/sketch/beans $J/a/a/a $J/mod/hey/studios/project/backup \
  $J/mod/agus/jcoderz/lib $DST/res/drawable $DST/res/layout $DST/res/values
# layoutlib has no external storage: the render's own folder stands in for it (the only change to the real sources)
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' \
  -e 's/\(android\.os\.\)\{0,1\}Environment\.getExternalStorageDirectory()/new java.io.File(System.getProperty("java.io.tmpdir"), "render-storage")/g' "$1" > "$2"; }
for f in GitHubViews DrawerAccountCard GitHubBackupsActivity PublishActivity GitHubException PublishDetails; do
  real $SRC/java/com/sketchware/ai/github/$f.java $G/$f.java
done
real $SRC/java/com/sketchware/ai/project/ProjectDiffAnalyzer.java $P/ProjectDiffAnalyzer.java
for f in $SRC/res/drawable/ob_*.xml; do cp $f $DST/res/drawable/; done
cp $SRC/res/values/colors_ob.xml $SRC/res/values/styles_ob.xml $DST/res/values/
sed 's|@id/layout_main_drawer_header|@+id/layout_main_drawer_header|' $SRC/res/layout/main_drawer_header.xml > $DST/res/layout/main_drawer_header.xml
cp $SRC/res/layout/myprojects_item.xml $SRC/res/layout/ob_github_screen.xml $DST/res/layout/
w() { mkdir -p "$(dirname "$1")"; cat > "$1"; }

w $J/com/besome/sketch/lib/base/BaseAppCompatActivity.java <<'X'
package com.besome.sketch.lib.base;

/**
 * Render stand-in: the activity's own code on a context without a window (Activity itself can't run in layoutlib):
 * its content goes into a FrameLayout, posted work is queued for the test.
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
    public void onNewIntent(android.content.Intent i) { }
    public void onDestroy() { }
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
w $U/ObsidianWindow.java <<'X'
package com.sketchware.ai.ui;
public final class ObsidianWindow { public static void apply(android.content.Context a) { } }
X
w $U/ObAlertBuilder.java <<'X'
package com.sketchware.ai.ui;
public class ObAlertBuilder extends android.app.AlertDialog.Builder {
    public ObAlertBuilder(android.content.Context c, int theme) { super(c, theme); }
}
X
w $P/GitHubCode.java <<'X'
package com.sketchware.ai.project;
public final class GitHubCode { public static final int TYPE = 2026; }
X
w $DST/res/values/v204_styles.xml <<'X'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="SketchwareAI.GlassAlertDialog" parent="android:Theme.Material.Dialog.Alert" />
</resources>
X
w $G/TokenStore.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: signed in as the owner's account of the screenshots. */
public final class TokenStore {
    public static String LOGIN = "createnewappswithsketchwareai";
    public static TokenStore get(android.content.Context c) { return new TokenStore(); }
    public String login() { return LOGIN; }
    public String token() { return "render"; }
    public String problem() { return null; }
}
X
w $G/RepoCache.java <<'X'
package com.sketchware.ai.github;
import java.util.List;
/** Render stand-in: the phone's copy of the account's project repositories (sample data set by the test). */
public final class RepoCache {
    public static List<ProjectRepo.Info> REPOS;
    public static java.util.Map<Long, List<ProjectRepo.Version>> VERSIONS = new java.util.HashMap<>();
    public static List<ProjectRepo.Info> repos(android.content.Context c, String login, long[] at) { at[0] = 1794732000000L; return REPOS; }
    public static List<ProjectRepo.Version> versions(android.content.Context c, String login, long number) { return VERSIONS.get(number); }
    public static void saveRepos(android.content.Context c, String login, List<ProjectRepo.Info> r) { }
    public static void saveVersions(android.content.Context c, long number, List<ProjectRepo.Version> v) { }
}
X
w $G/ProjectRepo.java <<'X'
package com.sketchware.ai.github;
import java.util.ArrayList;
import java.util.List;
/** Render stand-in: ProjectRepo's data classes and names as the app has them; the network waits forever. */
public final class ProjectRepo {
    public static final String PREFIX = "SketchwareAI-";
    public static final String MARK = "Sketchware AI project";
    public static final String TOPIC = "sketchware-ai";
    public static final int MAX_VERSIONS = 5;
    public static final class Info { public String name; public long number; public boolean isPrivate; public String description; public long updated; public String owner; }
    public static final class Version {
        public long id; public String tag; public String title; public boolean draft; public boolean standard; public long time;
        public String versionName; public String versionCode; public long size; public String sha256; public String swb; public String apk;
        public String notes; public String htmlUrl;
        public String label() { return "v" + (versionName == null ? "?" : versionName) + " (" + (versionCode == null ? "?" : versionCode) + ")"; }
    }
    static void block() { try { Thread.sleep(600_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    public static String link(String login, String repo) { return "https://github.com/" + login + "/" + repo; }
    public static String repositoryName(String n) { return PREFIX + n.trim().replaceAll("[^A-Za-z0-9_.-]", "_"); }
    public static String tagFor(String vn, String vc) { return "v" + vn + "-" + vc; }
    static long number(com.google.gson.JsonObject a) { return a.has("id") ? a.get("id").getAsLong() : 0; }
    static void sortNewestFirst(List<Version> list) { java.util.Collections.sort(list, (x, y) -> Long.compare(y.time, x.time)); }
    static List<Info> listOwn(String token) throws java.io.IOException, GitHubException { block(); throw new java.io.IOException("render"); }
    static List<Version> versions(String t, String o, String r, boolean d) throws java.io.IOException, GitHubException { block(); throw new java.io.IOException("render"); }
    static ProjectRepo byNumber(String t, String l, long n) throws java.io.IOException, GitHubException { block(); return null; }
    void setStandard(long id, boolean on) { }
    List<Version> versions(boolean drafts) { return new ArrayList<>(); }
}
X
w $G/CloudBackup.java <<'X'
package com.sketchware.ai.github;
import java.util.List;
/** Render stand-in: CloudBackup's names and data classes; the network waits forever. */
public final class CloudBackup {
    public static final String REPO = "SketchwareAI-Projects";
    public static final class Entry { public String path; public String name; public String packageName; public String versionName; public long time; public long size; public String parts; }
    public static final class Version { public String sha; public String message; public String date; }
    public CloudBackup(String token, String login) { }
    public static String formatTime(long time) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(time));
    }
    static String enc(String s) { return s; }
    static GitHubException http(String what, GitHubHttp.Response r) { return new GitHubException("http_" + r.code, what); }
    boolean openIfExists() { ProjectRepo.block(); return false; }
    List<Entry> list() { return new java.util.ArrayList<>(); }
    List<Version> history(String path) { return new java.util.ArrayList<>(); }
}
X
w $G/GitHubImportDialog.java <<'X'
package com.sketchware.ai.github;
import java.util.List;
/** Render stand-in: the older backups' list kept on the phone (sample data set by the test). */
public final class GitHubImportDialog {
    public static List<CloudBackup.Entry> OLDER;
    static final Object LIST_LOCK = new Object();
    static String text(Exception e) { return String.valueOf(e.getMessage()); }
    static List<CloudBackup.Entry> cachedList(android.content.Context c, String login, long[] at) { return OLDER; }
    static void saveCachedList(android.content.Context c, String login, List<CloudBackup.Entry> e, boolean b) { }
}
X
w $G/CloudSync.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class CloudSync {
    public static final class Import {
        public String owner, repo, legacyPath, legacySha, linkPath, title, librariesFrom; public long number, size;
        public boolean repoPrivate, withLibraries; public ProjectRepo.Version version; public GitHubImport.Source link;
    }
    public static void importProject(android.content.Context a, Import i) { }
    public static String megabytes(long bytes) { return String.format(java.util.Locale.ENGLISH, "%.1f MB", bytes / 1048576.0); }
    public static GitHubProgress publish(android.content.Context a, String scId, String name, PublishDetails d) { return new GitHubProgress(); }
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
    public void whenClosed(java.util.function.Consumer<Boolean> c) { }
}
X
w $G/GitHubImport.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class GitHubImport {
    public static final class Source { public String owner, repo, ref, path; public boolean file; }
    public static Source parse(String link) { return null; }
    static java.util.List<String> listBackups(String token, Source s) { return new java.util.ArrayList<>(); }
}
X
w $G/GitHubHttp.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class GitHubHttp {
    static final class Response { int code; String body = "{}"; boolean ok() { return code >= 200 && code < 300; } }
    static Response api(String m, String p, String t, String b) throws java.io.IOException { ProjectRepo.block(); throw new java.io.IOException("render"); }
}
X
w $G/DeviceFlow.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the JSON helpers. */
public final class DeviceFlow {
    static com.google.gson.JsonObject parse(String s) {
        try { return com.google.gson.JsonParser.parseString(s).getAsJsonObject(); } catch (RuntimeException e) { return new com.google.gson.JsonObject(); }
    }
    static String string(com.google.gson.JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }
}
X
w $G/SwbParts.java <<'X'
package com.sketchware.ai.github;
public final class SwbParts { static String displayName(String p) { return p.substring(p.lastIndexOf('/') + 1); } }
X
w $G/AuthGuard.java <<'X'
package com.sketchware.ai.github;
public final class AuthGuard { public static boolean check(android.content.Context a) { return true; } }
X
w $G/GitHubInAppAuthDialog.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class GitHubInAppAuthDialog {
    public interface Done { void signedIn(String login); }
    public static GitHubInAppAuthDialog show(android.content.Context a, Done d) { return null; }
    public static android.content.Intent customTab(android.content.Context context, String url) {
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
    }
}
X
w $G/ProjectLinks.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class ProjectLinks {
    public static String REPO_NAME;
    public static void listChanged() { }
    public static void share(android.content.Context a, String name, String url) { }
    static void standardNow(String scId, String login, String tag) { }
    static long repoNumber(String scId, String login) { return 0; }
    static String repoName(String scId, String login) { return REPO_NAME; }
}
X
w $J/mod/hey/studios/project/backup/BackupFactory.java <<'X'
package mod.hey.studios.project.backup;
/** Render stand-in: the backups folder is the test's sample folder. */
public final class BackupFactory {
    public static String DIR = "/tmp";
    public static String getBackupDir() { return DIR; }
    public static boolean zipContainsFile(String zip, String name) { return false; }
}
X
w $J/mod/hey/studios/project/backup/BackupRestoreManager.java <<'X'
package mod.hey.studios.project.backup;
/** Render stand-in. */
public class BackupRestoreManager {
    public interface RestoreDone { void done(boolean ok, String message); }
    public BackupRestoreManager(android.content.Context a) { }
    public void restore() { }
    public void doRestore(String file, boolean libs, String from, RestoreDone done) { }
}
X
w $J/mod/agus/jcoderz/lib/FileUtil.java <<'X'
package mod.agus.jcoderz.lib;
/** Render stand-in: nothing is read or written. */
public final class FileUtil {
    public static String readFile(String path) { return ""; }
    public static void writeFile(String path, String text) { }
}
X
w $J/a/a/a/lC.java <<'X'
package a.a.a;
/** Render stand-in: the project list entry of the sample project. */
public final class lC {
    public static java.util.HashMap<String, Object> PROJECT = new java.util.HashMap<>();
    public static java.util.HashMap<String, Object> b(String scId) { return PROJECT; }
}
X
w $J/a/a/a/yB.java <<'X'
package a.a.a;
public final class yB {
    public static String c(java.util.Map<String, Object> m, String k) { Object v = m == null ? null : m.get(k); return v == null ? "" : String.valueOf(v); }
}
X
w $J/a/a/a/hC.java <<'X'
package a.a.a;
import com.besome.sketch.beans.ProjectFileBean;
import java.util.ArrayList;
/** Render stand-in: the sample project's screens. */
public class hC {
    public static ArrayList<ProjectFileBean> SCREENS = new ArrayList<>();
    public hC(String scId) { }
    public void i() { }
    public ArrayList<ProjectFileBean> b() { return SCREENS; }
    public ArrayList<ProjectFileBean> c() { return new ArrayList<>(); }
}
X
w $J/a/a/a/eC.java <<'X'
package a.a.a;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ViewBean;
import java.util.ArrayList;
import java.util.HashMap;
/** Render stand-in: the sample project's widgets, logic and components. */
public class eC {
    public static HashMap<String, ArrayList<ViewBean>> C = new HashMap<>();
    public static HashMap<String, HashMap<String, ArrayList<BlockBean>>> D = new HashMap<>();
    public static HashMap<String, ArrayList<ComponentBean>> H = new HashMap<>();
    public HashMap<String, ArrayList<ViewBean>> c = C;
    public HashMap<String, HashMap<String, ArrayList<BlockBean>>> d = D;
    public HashMap<String, ArrayList<ComponentBean>> h = H;
    public eC(String scId) { }
    public void g() { }
    public void e() { }
}
X
w $J/a/a/a/iC.java <<'X'
package a.a.a;
import com.besome.sketch.beans.ProjectLibraryBean;
/** Render stand-in: AppCompat & Material on. */
public class iC {
    public iC(String scId) { }
    public void i() { }
    public ProjectLibraryBean b() { return null; }
    public ProjectLibraryBean c() { ProjectLibraryBean b = new ProjectLibraryBean(); b.libType = ProjectLibraryBean.PROJECT_LIB_TYPE_COMPAT; b.useYn = "Y"; return b; }
    public ProjectLibraryBean d() { return null; }
    public ProjectLibraryBean e() { return null; }
}
X
w $J/com/besome/sketch/beans/BlockBean.java <<'X'
package com.besome.sketch.beans;
public class BlockBean {
    public String opCode, spec;
    public java.util.ArrayList<String> parameters = new java.util.ArrayList<>();
    public BlockBean(String opCode, String spec, String... params) { this.opCode = opCode; this.spec = spec; java.util.Collections.addAll(parameters, params); }
}
X
w $J/com/besome/sketch/beans/ComponentBean.java <<'X'
package com.besome.sketch.beans;
public class ComponentBean { public String componentId; public int type; public ComponentBean(int type, String id) { this.type = type; componentId = id; } }
X
w $J/com/besome/sketch/beans/ProjectFileBean.java <<'X'
package com.besome.sketch.beans;
public class ProjectFileBean {
    private final String java, xml;
    public ProjectFileBean(String java, String xml) { this.java = java; this.xml = xml; }
    public String getJavaName() { return java; }
    public String getXmlName() { return xml; }
}
X
w $J/com/besome/sketch/beans/ProjectLibraryBean.java <<'X'
package com.besome.sketch.beans;
public class ProjectLibraryBean {
    public static final int PROJECT_LIB_TYPE_FIREBASE = 0, PROJECT_LIB_TYPE_COMPAT = 1, PROJECT_LIB_TYPE_ADMOB = 2, PROJECT_LIB_TYPE_GOOGLE_MAP = 3;
    public String useYn = "N"; public int libType;
}
X
w $J/com/besome/sketch/beans/ViewBean.java <<'X'
package com.besome.sketch.beans;
public class ViewBean {
    public String id, convert = ""; public int type;
    public ViewBean(String id, int type) { this.id = id; this.type = type; }
    public static String getViewTypeName(int type) {
        switch (type) { case 3: return "Button"; case 4: return "TextView"; case 5: return "EditText"; case 6: return "ImageView"; case 9: return "ListView"; default: return "LinearLayout"; }
    }
}
X
echo copied
