#!/bin/bash
# v2.1.1: on top of setup_v210.sh (which copies the current Play Store, Community, Restore and bar sources): stand-ins
# for what v2.1.1 adds around them: RepoIcons (icons from files the test names, no network), a.a.a.GC's monogram,
# CloudSync's Play Store job (Store, publishToStore), ProjectRepo.Asset.downloads.
# Usage: setup_v211.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V211*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v210.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github
w() { mkdir -p "$(dirname "$1")"; cat > "$1"; }
sed -i 's/public static final class Asset { public long id; public String name; public long size;/public static final class Asset { public long id; public String name; public long size; public long downloads;/' $G/ProjectRepo.java
sed -i 's/^}$//' $G/CloudSync.java
cat >> $G/CloudSync.java <<'X'
    static final String STORE = "store";
    public static final class Store { public String repo; public long number; public String title; public java.io.File staging; public String scId; }
    public static GitHubProgress publishToStore(android.content.Context a, Store s) { return new GitHubProgress(); }
}
X
w $G/RepoIcons.java <<'X'
package com.sketchware.ai.github;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
/** Render stand-in: ICONS (repository number -> PNG file) and LOCAL (sc_id -> PNG file) instead of GitHub and lC. */
public final class RepoIcons {
    static final String PATH = "playstore/icon.png";
    public static final java.util.Map<Long, String> ICONS = new java.util.HashMap<>();
    public static final java.util.Map<String, String> LOCAL = new java.util.HashMap<>();
    public static File localIcon(String scId) { String p = LOCAL.get(scId); return p == null ? null : new File(p); }
    public static byte[] fromSwb(File swb) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(swb)) {
            java.util.zip.ZipEntry e = zip.getEntry("resources/icons/icon.png");
            if (e == null) return null;
            try (java.io.InputStream in = zip.getInputStream(e)) { return in.readAllBytes(); }
        } catch (Exception e) { return null; }
    }
    public static Bitmap decode(byte[] b, int px) { return b == null ? null : BitmapFactory.decodeByteArray(b, 0, b.length); }
    public static Bitmap decode(File f, int px) { return f == null ? null : BitmapFactory.decodeFile(f.getPath()); }
    public static Bitmap forRepo(android.content.Context c, String token, String owner, String repo, long number, String scId, int px) {
        String p = ICONS.get(number); return p == null ? null : BitmapFactory.decodeFile(p);
    }
    public static java.util.Map<Long, String> linkedProjects(String login) { return new java.util.HashMap<>(); }
    static boolean upload(String t, String l, String r, byte[] icon) { return false; }
    static void uploadQuietly(String t, String l, String r, String scId) { }
}
X
mkdir -p $J/a/a/a
{ echo "package a.a.a;"; echo "/** Render stand-in: the projects screen's monogram. */"; echo "public final class GC {";
  sed -n '/static String monogram(String name) {/,/^    }/p' $SRC/java/a/a/a/GC.java; echo "}"; } > $J/a/a/a/GC.java
# the publish form saves itself in onPause and re-reads its pictures in onResume
B=$J/com/besome/sketch/lib/base/BaseAppCompatActivity.java
grep -q "public void onResume()" $B || sed -i 's/    public void onStart() { }/    public void onStart() { }\n    public void onResume() { }\n    public void onPause() { }/' $B
