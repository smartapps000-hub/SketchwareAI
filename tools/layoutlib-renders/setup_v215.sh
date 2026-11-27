#!/bin/bash
# v2.1.5: on top of setup_v214.sh (which copies the current Play Store, Publish, Restore, update card, bar and sign-in
# window sources): FollowWatch as it is (Follow schedules it), BlockGuide / BlockShapeView / PermissionPicker and
# JsonCode as they are (the JSON power pack's guide sheet). Usage: setup_v215.sh <repo>;
# ./gradlew :render:testDebugUnitTest --tests 'pz.V215*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v214.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; P=$J/com/sketchware/ai/project; U=$J/com/sketchware/ai/ui
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' "$1" > "$2"; }
real $SRC/java/com/sketchware/ai/github/FollowWatch.java $G/FollowWatch.java
mkdir -p $P $U
real $SRC/java/com/sketchware/ai/project/JsonCode.java $P/JsonCode.java
for f in BlockGuide BlockShapeView PermissionPicker; do real $SRC/java/com/sketchware/ai/ui/$f.java $U/$f.java; done
# layoutlib has no telephony service (it asserts): the render copy skips the SIM / network read, so the phone's region
# setting answers (on a phone the service is there; without telephony getSystemService gives null)
perl -0pi -e 's/context\s*\.getSystemService\(Context\.TELEPHONY_SERVICE\)/null/' $G/PlayStore.java
# the window-less base: Follow's notification question asks Android for the permission
BA=$J/com/besome/sketch/lib/base/BaseAppCompatActivity.java
grep -q "void requestPermissions" $BA || sed -i 's/    public void startActivityForResult(android.content.Intent i, int code) { }/&\n    public void requestPermissions(String[] p, int code) { }/' $BA
for f in $(grep -rl "com\.sketchware\.remod\.R\." $J 2>/dev/null); do sed -i 's/com\.sketchware\.remod\.R\./pz.render.R./g' $f; done
echo "v2.1.5 render sources ready"
