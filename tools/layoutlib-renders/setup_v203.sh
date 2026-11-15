#!/bin/bash
# v2.0.3: on top of setup_v202.sh and setup_v1971.sh: the drawer account card's new layout (a top row with the picture,
# "GitHub" and its state, Sign out; name, @login, e-mail and the account line wrapped below; the "not accepted" state
# with Sign in again), the project card's Public / Private and Share pills (myprojects_item.xml), and the GitHub blocks'
# guide in #1F6FEB with its two buttons (BlockGuide). Stand-ins for the classes these reach into (CloudBackup's REPO and
# formatTime, GitHubInAppAuthDialog.customTab, ProjectLinks.listChanged, GitHubSample) are written here.
# Usage: the earlier setup scripts, then setup_v203.sh <repo>; ./gradlew :render:testDebugUnitTest --tests 'pz.V203Test'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
G=$DST/java/com/sketchware/ai/github; U=$DST/java/com/sketchware/ai/ui; P=$DST/java/com/sketchware/ai/project
mkdir -p $G $U $P $DST/res/drawable $DST/res/layout
for f in GitHubViews DrawerAccountCard; do
  sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/github/$f.java > $G/$f.java
done
for c in BlockGuide BlockShapeView PermissionPicker; do
  sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/ui/$c.java > $U/$c.java
done
for f in ob_ic_share ob_ic_help ob_c_github ob_ic_lock ob_ic_public ob_divider_fade ob_press_overlay ob_avatar ob_badge ob_cta_pill_soft ob_field; do
  [ -f $SRC/res/drawable/$f.xml ] && cp $SRC/res/drawable/$f.xml $DST/res/drawable/
done
sed 's|@id/layout_main_drawer_header|@+id/layout_main_drawer_header|' $SRC/res/layout/main_drawer_header.xml > $DST/res/layout/main_drawer_header.xml
cp $SRC/res/layout/myprojects_item.xml $DST/res/layout/
cat > $G/GitHubSession.java <<'J'
package com.sketchware.ai.github;

/** Stand-in for the render: the real GitHubSession's Profile only. */
public final class GitHubSession {
    public static final class Profile {
        public String login;
        public String name;
        public String email;
        public java.io.File avatar;
        public int repositories = -1;
        public long checkedAt;
        public String problem;
    }
}
J
cat > $G/CloudBackup.java <<'J'
package com.sketchware.ai.github;

/** Stand-in for the render: what the account card reads. */
public final class CloudBackup {
    public static final String REPO = "SketchwareAI-Projects";

    public static String formatTime(long time) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(time));
    }
}
J
cat > $G/GitHubInAppAuthDialog.java <<'J'
package com.sketchware.ai.github;

/** Stand-in for the render. */
public final class GitHubInAppAuthDialog {
    public static android.content.Intent customTab(android.content.Context context, String url) {
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
    }
}
J
cat > $G/ProjectLinks.java <<'J'
package com.sketchware.ai.github;

/** Stand-in for the render. */
public final class ProjectLinks {
    public static void listChanged() {
    }
}
J
cat > $P/GitHubSample.java <<'J'
package com.sketchware.ai.project;

/** Stand-in for the render. */
public final class GitHubSample {
    public static final String NAME = "GitHub API Demo";

    public static String create(android.content.Context context) {
        return "0";
    }
}
J
echo copied
