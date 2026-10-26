#!/bin/bash
# v1.8.8: copies what V188ScreensTest needs from the IDE into this module: the Library Resolver, Java editor and Code
# tab layouts and the drawables they use (plain-shape fallbacks of drawable/). Run setup_newproject.sh first (fonts,
# styles, colours). Usage: setup_v188.sh <repo root>
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
for f in ob_amber_pill ob_accent_box ob_ic_undo ob_ic_redo ob_pill16_state ob_pill16 ob_pill16_pressed ob_field ob_glass \
    ob_cta_state ob_cta ob_cta_pressed ob_ic_back ob_pill_state ob_pill ob_pill_pressed ob_window_bg; do
  cp $SRC/res/drawable/$f.xml $DST/res/drawable/
done
for f in ob_library_resolver ob_lib_row ob_lib_tab ob_lib_add ob_lib_card ob_java_editor; do cp $SRC/res/layout/$f.xml $DST/res/layout/; done
mkdir -p $DST/res/animator && cp $SRC/res/animator/sw_press.xml $DST/res/animator/   # v1.8.11: ob_lib_card's press animator
echo "copied; then: ./gradlew :render:recordPaparazziDebug --tests 'pz.V188ScreensTest' (v1.8.11 cards: 'pz.V1811CardTest')"
