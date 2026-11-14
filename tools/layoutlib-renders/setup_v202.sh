#!/bin/bash
# v2.0.2: on top of setup_v200.sh / setup_v201.sh: the drawer account card without cuts (main_drawer_header.xml +
# DrawerAccountCard: wrapped title signed out, marquee name / @login / e-mail and "Sign out" under them signed in), the
# live progress sheet (GitHubViews.progressSheet), the import guide (GitHubViews.importGuide) and the project card with
# its share button (myprojects_item.xml). Usage: setup_newproject.sh, setup_v200.sh, setup_v201.sh, setup_v202.sh <repo>;
# then ./gradlew :render:testDebugUnitTest --tests 'pz.V202Test'   (writes <scratchpad>/pz/out/v202_github.png)
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
G=$DST/java/com/sketchware/ai/github
mkdir -p $G $DST/res/drawable $DST/res/layout
for f in GitHubViews DrawerAccountCard; do
  sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/github/$f.java > $G/$f.java
done
for f in ob_ic_share ob_ic_help ob_c_github ob_divider_fade ob_press_overlay ob_avatar ob_badge ob_cta_pill_soft ob_field; do
  [ -f $SRC/res/drawable/$f.xml ] && cp $SRC/res/drawable/$f.xml $DST/res/drawable/
done
sed 's|@id/layout_main_drawer_header|@+id/layout_main_drawer_header|' $SRC/res/layout/main_drawer_header.xml > $DST/res/layout/main_drawer_header.xml
cp $SRC/res/layout/myprojects_item.xml $DST/res/layout/
echo copied
