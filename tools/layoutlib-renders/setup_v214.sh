#!/bin/bash
# v2.1.4: on top of setup_v213.sh (which copies the current Play Store, Restore, update card, bar and notification
# sources): Countries and StoreDescription as they are, CloudSync's publishToStore with the Done action and
# GitHubProgress.doneOpens (stand-ins: the sheet isn't drawn here). Usage: setup_v214.sh <repo>;
# ./gradlew :render:testDebugUnitTest --tests 'pz.V214*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v213.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; P=$J/com/sketchware/ai/project
real() { sed -e 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' "$1" > "$2"; }
real $SRC/java/com/sketchware/ai/github/Countries.java $G/Countries.java
mkdir -p $P
real $SRC/java/com/sketchware/ai/project/StoreDescription.java $P/StoreDescription.java
if ! grep -q "Runnable onDone" $G/CloudSync.java; then
  sed -i 's/public static GitHubProgress publishToStore(android.content.Context a, Store s) { return new GitHubProgress(); }/&\n    public static GitHubProgress publishToStore(android.content.Context a, Store s, Runnable onDone) { return new GitHubProgress(); }/' $G/CloudSync.java
fi
grep -q "doneOpens" $G/GitHubProgress.java || sed -i 's/public final class GitHubProgress {/public final class GitHubProgress { public void doneOpens(String l, Runnable r) { }/' $G/GitHubProgress.java
# stand-ins: what the v2.1.4 sources read from them (a button's words; the security log link)
B=$J/com/besome/sketch/beans
[ -f $B/TextBean.java ] || printf 'package com.besome.sketch.beans;\n/** Render stand-in: a view\x27s words. */\npublic class TextBean { public String text; public String hint; }\n' > $B/TextBean.java
grep -q "TextBean text" $B/ViewBean.java || sed -i 's/    public String id, convert = ""; public int type;/&\n    public TextBean text;/' $B/ViewBean.java
grep -q "SECURITY_LOG_URL" $G/GitHubSession.java || sed -i 's/public final class GitHubSession {/&\n    public static final String SECURITY_LOG_URL = "https:\/\/github.com\/settings\/security-log";/' $G/GitHubSession.java
echo "v2.1.4 render sources ready"
