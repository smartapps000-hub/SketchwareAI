#!/bin/bash
# v2.0.0: copies what V200Test needs from the IDE, on top of setup_newproject.sh: the drawer header with the GitHub
# account card (main_drawer_header.xml + com.sketchware.ai.github.DrawerAccountCard), the in-app sign-in window's views
# and the sign-in gate card (GitHubViews, ob_github.xml), the no-glow pills. GitHubSession is replaced by a stand-in that
# holds only its Profile class (the real one needs the whole sign-in package). Usage:
# setup_newproject.sh <repo root>; setup_v200.sh <repo root>; then
# ./gradlew :render:testDebugUnitTest --tests 'pz.V200Test'   (writes /home/claude/pz/out/v200_github.png)
# Run in a copy that holds only the tests whose setup files are present (the Kotlin tests compile together).
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
G=$DST/java/com/sketchware/ai/github
mkdir -p $G $DST/res/drawable $DST/res/layout
rm -f $G/GitHubScreen.java
for f in GitHubViews DrawerAccountCard; do
  sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/github/$f.java > $G/$f.java
done
cat > $G/GitHubSession.java <<'JAVA'
package com.sketchware.ai.github;

/** Stand-in for the render: the real GitHubSession's Profile only. */
public final class GitHubSession {
    public static final class Profile {
        public String login;
        public String name;
        public String email;
        public java.io.File avatar;
    }
}
JAVA
# plain-shape (pre-API 24) drawables: layoutlib can not inflate the GlassDrawable class of drawable-v24
for f in ob_cta_pill_soft ob_cta_pill_soft_pressed ob_cta_pill_soft_state ob_ic_github ob_ic_close ob_ic_spark ob_tile; do
  cp $SRC/res/drawable/$f.xml $DST/res/drawable/
done
cp $SRC/res/layout/ob_github.xml $DST/res/layout/
sed 's|@id/layout_main_drawer_header|@+id/layout_main_drawer_header|' $SRC/res/layout/main_drawer_header.xml > $DST/res/layout/main_drawer_header.xml
echo copied
