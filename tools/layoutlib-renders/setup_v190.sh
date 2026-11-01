#!/bin/bash
# v1.9.0: copies what V190Test / V190SymbolsTest need from the IDE (after setup_newproject.sh and setup_v188.sh):
# the TextMate Dracula theme the source sheet used, M3CodeScheme, SymbolIcons and the Material Symbols asset.
# Usage: setup_v190.sh <repo root>; then ./gradlew :render:recordPaparazziDebug --tests 'pz.V190*'
set -e
T=$(cd $(dirname $0) && pwd); REPO=$1; SRC=$REPO/app/src/main; DST=$T/render/src/main
mkdir -p $DST/assets/textmate/themes $DST/assets/icons $DST/java/com/sketchware/ai/ui
cp $SRC/assets/textmate/themes/dracula.json $DST/assets/textmate/themes/
cp $SRC/assets/icons/material_symbols.zip $DST/assets/icons/
cp $SRC/java/com/sketchware/ai/ui/M3CodeScheme.java $SRC/java/com/sketchware/ai/ui/SymbolIcons.java $DST/java/com/sketchware/ai/ui/
echo "copied; then: ./gradlew :render:recordPaparazziDebug --tests 'pz.V190*' (writes /home/claude/pz/out/sheet190_*.png, cfg190_*.png, symbols190.png)"
