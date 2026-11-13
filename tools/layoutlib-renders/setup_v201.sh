#!/bin/bash
# v2.0.1: on top of setup_v200.sh: the two-row sign-in window (GitHubViews.authChrome: close, "GitHub Sign-In", Reload,
# the thin orange loading line, the code pill and a status of up to three lines, the "Sign in via browser" card), the
# glass Back up sheet (the two classic boxes, "Save on phone", "Back up to GitHub") and Restore sheet
# (GitHubViews.backupSheet / restoreSheet), and the new line icons. Usage:
# setup_newproject.sh <repo root>; setup_v200.sh <repo root>; setup_v201.sh <repo root>; then
# ./gradlew :render:testDebugUnitTest --tests 'pz.V201Test'   (writes <scratchpad>/pz/out/v201_github.png)
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
G=$DST/java/com/sketchware/ai/github
mkdir -p $G $DST/res/drawable
sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/github/GitHubViews.java > $G/GitHubViews.java
for f in ob_ic_refresh ob_ic_open_browser ob_ic_phone ob_ic_copy ob_ic_backup ob_ic_restore \
         ob_pill16 ob_pill16_state ob_field; do
  [ -f $SRC/res/drawable/$f.xml ] && cp $SRC/res/drawable/$f.xml $DST/res/drawable/
done
echo copied
