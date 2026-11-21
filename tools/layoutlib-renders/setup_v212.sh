#!/bin/bash
# v2.1.2: on top of setup_v211.sh (which copies the current Play Store, Community, Restore, Settings and bar sources):
# the update card (UpdateCard, real), MonogramDrawable (real), a SessionLog stand-in (LINE = the signed-out card's
# line), the new AppControl.Config fields, BuildConfig of v2.1.1 (the card's version step), a MainActivity class for
# TabScreens. Usage: setup_v212.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V212*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v211.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; U=$J/com/sketchware/ai/ui
w() { mkdir -p "$(dirname "$1")"; cat > "$1"; }
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' \
  -e 's/\bActivity activity\b/com.besome.sketch.lib.base.BaseAppCompatActivity activity/g' "$1" > "$2"; }
real $SRC/java/com/sketchware/ai/github/UpdateCard.java $G/UpdateCard.java
sed -i 's/import android.app.Activity;/import com.besome.sketch.lib.base.BaseAppCompatActivity;/; s/(Activity activity/(BaseAppCompatActivity activity/g' $G/UpdateCard.java
real $SRC/java/com/sketchware/ai/ui/MonogramDrawable.java $U/MonogramDrawable.java
sed -i 's/public String comingSoon = "";/public String comingSoon = ""; public int statusMessageMaxVersionCode; public String updateReason = ""; public final java.util.List<String> updateNotes = new java.util.ArrayList<>();/' $G/AppControl.java
w $G/SessionLog.java <<'X'
package com.sketchware.ai.github;
/** Render stand-in: the signed-out card's line. */
public final class SessionLog {
    public static String LINE;
    public static String signedOutLine() { return LINE; }
    public static void record(android.content.Context c, String k, String t) { }
}
X
w $J/com/sketchware/remod/BuildConfig.java <<'X'
package com.sketchware.remod;
public final class BuildConfig { public static final String VERSION_NAME = "v2.1.2-render-minApi26"; public static final int VERSION_CODE = 209; }
X
mkdir -p $J/com/besome/sketch
[ -f $J/com/besome/sketch/MainActivity.java ] || w $J/com/besome/sketch/MainActivity.java <<'X'
package com.besome.sketch;
/** Render stand-in (TabScreens names it). */
public class MainActivity { }
X
B=$J/com/besome/sketch/lib/base/BaseAppCompatActivity.java
grep -q "overridePendingTransition" $B || sed -i 's/    public void finish() { }/    public void finish() { }\n    public void overridePendingTransition(int a, int b) { }/' $B
