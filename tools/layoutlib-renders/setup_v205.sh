#!/bin/bash
# v2.0.5: on top of setup_v204.sh: the Public details screen with editable fields (PublishActivity), the Restore screen's
# Paste link box and version carousel (GitHubBackupsActivity), the in-app repository viewer (RepoViewerActivity) on canned
# GitHub answers, the Demo row's cards (ui.DemoCards), the drawer account card at a large font; the change analyzer's
# format 2 on stand-in project data (MoreBlocks, variables, screen parts). Usage: setup_v205.sh <repo>;
# ./gradlew :render:testDebugUnitTest --tests 'pz.V205Test'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v204.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; U=$J/com/sketchware/ai/ui; P=$J/com/sketchware/ai/project
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' \
  -e 's/\(android\.os\.\)\{0,1\}Environment\.getExternalStorageDirectory()/new java.io.File(System.getProperty("java.io.tmpdir"), "render-storage")/g' "$1" > "$2"; }
# the app's androidx is the shrunk copy in app/libs (FileProvider.getUriForFile is "a" there); the render has the real one
real $SRC/java/com/sketchware/ai/github/RepoViewerActivity.java $G/RepoViewerActivity.java
sed -i 's/androidx\.core\.content\.FileProvider\.a(/androidx.core.content.FileProvider.getUriForFile(/' $G/RepoViewerActivity.java
real $SRC/java/com/sketchware/ai/ui/DemoCards.java $U/DemoCards.java
w() { mkdir -p "$(dirname "$1")"; cat > "$1"; }

w $G/GitHubSession.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the real GitHubSession's Profile; the sign-in is accepted. */
public final class GitHubSession {
    public static final class Profile {
        public String login; public String name; public String email; public java.io.File avatar;
        public int repositories = -1; public long checkedAt; public String problem;
    }
    public static boolean isRefused(android.content.Context c) { return false; }
}
X
w $G/GitHubHttp.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: canned answers for the repository viewer (ANSWERS: path → JSON); anything else waits forever. */
public final class GitHubHttp {
    public static final java.util.Map<String, String> ANSWERS = new java.util.HashMap<>();
    static final class Response { int code; String body = "{}"; boolean ok() { return code >= 200 && code < 300; } }
    static Response api(String m, String p, String t, String b) throws java.io.IOException {
        String body = ANSWERS.get(p);
        if (body == null) { ProjectRepo.block(); throw new java.io.IOException("render"); }
        Response r = new Response(); r.code = body.isEmpty() ? 404 : 200; r.body = body; return r;
    }
    interface Progress { void bytes(long done, long total); }
    static Response downloadAsset(String p, String t, java.io.File to, long max, Progress progress) throws java.io.IOException { ProjectRepo.block(); throw new java.io.IOException("render"); }
}
X
w $G/ProjectRepo.java <<'X'
package com.sketchware.ai.github;
import java.util.ArrayList;
import java.util.List;
/** Render stand-in: ProjectRepo's data classes and names as the app has them; VIEWER = the viewer's versions. */
public final class ProjectRepo {
    public static final String PREFIX = "SketchwareAI-";
    public static final String MARK = "Sketchware AI project";
    public static final String TOPIC = "sketchware-ai";
    public static final int MAX_VERSIONS = 5;
    public static List<Version> VIEWER;
    public static final class Info { public String name; public long number; public boolean isPrivate; public String description; public long updated; public String owner; }
    public static final class Asset { public long id; public String name; public long size; public String state = "uploaded"; public String url; }
    public static final class Version {
        public long id; public String tag; public String title; public boolean draft; public boolean standard; public long time;
        public String versionName; public String versionCode; public long size; public String sha256; public String swb; public String apk;
        public String notes; public String htmlUrl; public final List<Asset> assets = new ArrayList<>();
        public String label() { return "v" + (versionName == null ? "?" : versionName) + " (" + (versionCode == null ? "?" : versionCode) + ")"; }
    }
    static void block() { try { Thread.sleep(600_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    public static String link(String login, String repo) { return "https://github.com/" + login + "/" + repo; }
    public static String repositoryName(String n) { return PREFIX + n.trim().replaceAll("[^A-Za-z0-9_.-]", "_"); }
    public static String tagFor(String vn, String vc) { return "v" + vn + "-" + vc; }
    static String safeFileName(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }
    static long isoTime(String... values) {
        for (String v : values) {
            if (v == null) continue;
            try { return java.time.Instant.parse(v).toEpochMilli(); } catch (RuntimeException ignored) { }
        }
        return 0;
    }
    static long number(com.google.gson.JsonObject a) { return a.has("id") ? a.get("id").getAsLong() : 0; }
    static void sortNewestFirst(List<Version> list) { java.util.Collections.sort(list, (x, y) -> Long.compare(y.time, x.time)); }
    static List<Info> listOwn(String token) throws java.io.IOException, GitHubException { block(); throw new java.io.IOException("render"); }
    static List<Version> versions(String t, String o, String r, boolean d) throws java.io.IOException, GitHubException {
        if (VIEWER != null) return new ArrayList<>(VIEWER);
        block(); throw new java.io.IOException("render");
    }
    static ProjectRepo byNumber(String t, String l, long n) throws java.io.IOException, GitHubException { block(); return null; }
    void setStandard(long id, boolean on) { }
    List<Version> versions(boolean drafts) { return new ArrayList<>(); }
}
X
w $G/CloudSync.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class CloudSync {
    public static final String PREFS = "cloud_sync";
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
w $G/ProjectLinks.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in. */
public final class ProjectLinks {
    public static String REPO_NAME;
    public static String VISIBILITY = "public";
    public static void listChanged() { }
    public static void share(android.content.Context a, String name, String url) { }
    static void standardNow(String scId, String login, String tag) { }
    static long repoNumber(String scId, String login) { return 0; }
    static String repoName(String scId, String login) { return REPO_NAME; }
    public static String visibility(String scId, String login) { return VISIBILITY; }
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
w $J/a/a/a/eC.java <<'X'
package a.a.a;
import android.util.Pair;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ViewBean;
import java.util.ArrayList;
import java.util.HashMap;
/** Render stand-in: the sample project's widgets, logic, components, variables, lists and MoreBlocks. */
public class eC {
    public static HashMap<String, ArrayList<ViewBean>> C = new HashMap<>();
    public static HashMap<String, HashMap<String, ArrayList<BlockBean>>> D = new HashMap<>();
    public static HashMap<String, ArrayList<Pair<Integer, String>>> E = new HashMap<>();
    public static HashMap<String, ArrayList<Pair<Integer, String>>> F = new HashMap<>();
    public static HashMap<String, ArrayList<Pair<String, String>>> G = new HashMap<>();
    public static HashMap<String, ArrayList<ComponentBean>> H = new HashMap<>();
    public HashMap<String, ArrayList<ViewBean>> c = C;
    public HashMap<String, HashMap<String, ArrayList<BlockBean>>> d = D;
    public HashMap<String, ArrayList<Pair<Integer, String>>> e = E;
    public HashMap<String, ArrayList<Pair<Integer, String>>> f = F;
    public HashMap<String, ArrayList<Pair<String, String>>> g = G;
    public HashMap<String, ArrayList<ComponentBean>> h = H;
    public eC(String scId) { }
    public void g() { }
    public void e() { }
}
X
w $J/com/besome/sketch/beans/ProjectFileBean.java <<'X'
package com.besome.sketch.beans;
public class ProjectFileBean {
    public static final int OPTION_ACTIVITY_TOOLBAR = 1, OPTION_ACTIVITY_FULLSCREEN = 2, OPTION_ACTIVITY_DRAWER = 4, OPTION_ACTIVITY_FAB = 8;
    public static final int ORIENTATION_PORTRAIT = 0, ORIENTATION_LANDSCAPE = 1, ORIENTATION_BOTH = 2;
    private final String java, xml;
    public int options = 1; public int orientation;
    public ProjectFileBean(String java, String xml) { this.java = java; this.xml = xml; }
    public String getJavaName() { return java; }
    public String getXmlName() { return xml; }
    public boolean hasActivityOption(int option) { return (options & option) == option; }
}
X
echo copied v205
