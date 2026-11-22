#!/bin/bash
# v2.1.3: on top of setup_v212.sh (which copies the current Play Store, Restore, update card and bar sources): the
# AppControl stand-in's update_fixes, the notification card (GlassNotice and its two layouts, applied through
# RemoteViews as Android's shade does), the tab fades, the Play Store's Follow calls (no network: unknown), the grouped
# Restore cards' pins. Usage: setup_v213.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V213*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v212.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' "$1" > "$2"; }
grep -q "updateFixes" $G/AppControl.java || sed -i 's/public final java.util.List<String> updateNotes = new java.util.ArrayList<>();/public final java.util.List<String> updateNotes = new java.util.ArrayList<>(); public final java.util.List<String> updateFixes = new java.util.ArrayList<>();/' $G/AppControl.java
real $SRC/java/com/sketchware/ai/github/GlassNotice.java $G/GlassNotice.java
mkdir -p $DST/res/layout $DST/res/anim
cp $SRC/res/layout/ob_notification_glass.xml $SRC/res/layout/ob_notification_glass_big.xml $DST/res/layout/
cp $SRC/res/anim/ob_tab_in.xml $SRC/res/anim/ob_tab_hold.xml $SRC/res/anim/ob_tab_out.xml $DST/res/anim/
for f in $SRC/res/drawable/ob_*.xml; do cp $f $DST/res/drawable/; done
# fully qualified R names in copied sources (TabScreens' fades)
for f in $(grep -rl "com\.sketchware\.remod\.R\." $J 2>/dev/null); do sed -i 's/com\.sketchware\.remod\.R\./pz.render.R./g' $f; done
# stand-ins: what the v2.1.3 sources call on them
grep -q "static boolean isManifest" $G/SwbParts.java || sed -i 's/public final class SwbParts {/public final class SwbParts { public static boolean isManifest(String p) { return p != null \&\& p.endsWith(".parts.json"); }/' $G/SwbParts.java
grep -q "static String get(String" $G/ProjectLinks.java || sed -i 's/    public static String visibility(String scId, String login) { return VISIBILITY; }/&\n    public static String LINK = "https:\/\/github.com\/bemidi\/SketchwareAI-GitHub_API_Demo";\n    public static String get(String scId, String login) { return LINK; }/' $G/ProjectLinks.java
grep -q "String scopes" $G/GitHubHttp.java || sed -i 's/    static final class Response { int code; String body = "{}";/    static final class Response { int code; String body = "{}"; String scopes;/' $G/GitHubHttp.java
# the Demo cards' Public / Private pill opens Publish (the window-less base stands in for Activity)
sed -i 's/public static void open(android\.app\.Activity activity, String scId, boolean edit)/public static void open(com.besome.sketch.lib.base.BaseAppCompatActivity activity, String scId, boolean edit)/' $G/PublishActivity.java
echo "v2.1.3 render sources ready"
