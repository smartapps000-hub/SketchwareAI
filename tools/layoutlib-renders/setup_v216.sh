#!/bin/bash
# v2.1.6: on top of setup_v215.sh (which copies the current Settings, Restore projects, account card and sign-in window
# sources): the Demo tab's order by kind (the GitHub + JSON Live Demo first) and its "Make the …" pills for a missing
# demo, Settings' follow-notification switch. Usage: setup_v216.sh <repo>;
# ./gradlew :render:testDebugUnitTest --tests 'pz.V216*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
bash $T/setup_v215.sh $REPO > /dev/null
J=$DST/java; G=$J/com/sketchware/ai/github; P=$J/com/sketchware/ai/project; U=$J/com/sketchware/ai/ui
w() { mkdir -p $(dirname $1); cat > $1; }
# the Demo tab reads each demo's kind from its mark (data/<id>/demo.json): the render stand-in keeps them in the list
w $P/Demos.java <<'X'
package com.sketchware.ai.project;
/** Render stand-in: a project is a demo when its entry says so ("demo" = its version, "kind" = its kind). */
public final class Demos {
    public static final java.util.Map<String, String> KINDS = new java.util.HashMap<>();
    public static boolean isDemo(java.util.HashMap<String, Object> p) { return p.containsKey("demo"); }
    public static String kind(String scId) { String k = KINDS.get(scId); return k == null ? "github" : k; }
    static int rank(String kind) { return "githubjson".equals(kind) ? 0 : "github".equals(kind) ? 1 : 2; }
    public static void sort(java.util.List<java.util.HashMap<String, Object>> demos) {
        demos.sort((a, b) -> {
            int ra = rank(kind(String.valueOf(a.get("sc_id")))), rb = rank(kind(String.valueOf(b.get("sc_id"))));
            if (ra != rb) return ra - rb;
            return Integer.parseInt(String.valueOf(b.get("demo"))) - Integer.parseInt(String.valueOf(a.get("demo")));
        });
    }
}
X
w $P/JsonSample.java <<'X'
package com.sketchware.ai.project;
public final class JsonSample {
    public static final String NAME = "GitHub + JSON Live Demo";
    public static final String KIND = "githubjson";
    public static String create(android.content.Context c) { return null; }
}
X
for f in $(grep -rl "com\.sketchware\.remod\.R\." $J 2>/dev/null); do sed -i 's/com\.sketchware\.remod\.R\./pz.render.R./g' $f; done
echo "v2.1.6 render sources ready"
