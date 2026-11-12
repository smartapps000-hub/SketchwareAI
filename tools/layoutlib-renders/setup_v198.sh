#!/bin/bash
# v1.9.8: copies what V198Test (the GitHub screen: ob_github.xml + com.sketchware.ai.github.GitHubScreen in five states)
# needs from the IDE, on top of setup_newproject.sh (Obsidian styles, colours, fonts, pills, CTA, field). Usage:
# setup_newproject.sh <repo root>; setup_v198.sh <repo root>; then
# ./gradlew :render:testDebugUnitTest --tests 'pz.V198Test'   (writes /home/claude/pz/out/v198_github.png)
# Run in a copy that holds only the tests whose setup files are present (the Kotlin tests compile together).
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
mkdir -p $DST/java/com/sketchware/ai/github $DST/res/drawable $DST/res/layout
sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/github/GitHubScreen.java \
  > $DST/java/com/sketchware/ai/github/GitHubScreen.java
# the plain-shape (pre-API 24) version of the card: layoutlib can not inflate the GlassDrawable class of drawable-v24
cp $SRC/res/drawable/ob_gh_card.xml $SRC/res/drawable/ob_cta_pill.xml $SRC/res/drawable/ob_cta_pill_pressed.xml \
  $SRC/res/drawable/ob_cta_pill_state.xml $SRC/res/drawable/ob_ic_github.xml $DST/res/drawable/
cp $SRC/res/layout/ob_github.xml $DST/res/layout/
echo copied
