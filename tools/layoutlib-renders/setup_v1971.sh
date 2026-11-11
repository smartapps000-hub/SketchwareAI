#!/bin/bash
# v1.9.7.1: copies what V197Test (guide sheet: label colour by contrast) and V1971Test (call-to-action label before /
# after) need from the IDE. Usage: setup_v1971.sh <repo root>; then
# ./gradlew :render:testDebugUnitTest --tests 'pz.V197Test' --tests 'pz.V1971Test'   (writes /home/claude/pz/out/*.png)
# The other tests need their own setup scripts' files: run in a copy that holds only these two tests, or after them.
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
mkdir -p $DST/java/com/sketchware/ai/ui $DST/res/drawable $DST/res/font
for c in BlockGuide BlockShapeView PermissionPicker; do
  sed 's/^import com\.sketchware\.remod\.R;/import pz.render.R;/' $SRC/java/com/sketchware/ai/ui/$c.java > $DST/java/com/sketchware/ai/ui/$c.java
done
cp $SRC/res/drawable/ob_safe_cta.xml $SRC/res/drawable/ob_safe_cta_pressed.xml $SRC/res/drawable/ob_ic_play_small.xml $DST/res/drawable/
cp $SRC/res/font/manrope_extrabold.ttf $SRC/res/font/manrope_bold.ttf $DST/res/font/
echo copied
