#!/bin/bash
# v1.9.1: copies ExportCards (the Export screen's card notice and result rows) from the IDE, with the render module's R,
# plus the folder icon and the path background. Usage: setup_v191.sh <repo root>; then
# ./gradlew :render:recordPaparazziDebug --tests 'pz.V191*'   (writes /home/claude/pz/out/export191_*.png)
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
mkdir -p $DST/java/com/sketchware/ai/ui $DST/res/drawable
sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/ui/ExportCards.java > $DST/java/com/sketchware/ai/ui/ExportCards.java
cp $SRC/res/drawable/bg_round_light_grey.xml $DST/res/drawable/ 2>/dev/null || true
echo copied
