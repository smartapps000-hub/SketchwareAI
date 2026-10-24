#!/bin/bash
# v1.8.7: copies what NewProjectScreenTest needs from the IDE into this module (the IDE's New Project layouts, their
# drawables as the plain-shape fallbacks of drawable/, fonts, Obsidian styles and colours, and the three classes it
# draws with: M3Palette, ThemePresets, ThemePreviewView). Usage: setup_newproject.sh [repo root, default ../..]
set -e
T=$(cd $(dirname $0) && pwd); REPO=${1:-$T/../..}; SRC=$REPO/app/src/main; DST=$T/render/src/main
mkdir -p $DST/res/drawable $DST/res/layout $DST/res/font $DST/res/animator $DST/res/color $DST/res/values \
  $DST/java/com/sketchware/ai/project $DST/java/com/sketchware/ai/ui
for f in ob_glass ob_field ob_pill16_state ob_pill16 ob_pill16_pressed ob_seg_pill ob_tile_orange ob_pill ob_pill_pressed \
    ob_cta_state ob_cta ob_cta_pressed ob_avatar ob_badge ob_press_overlay ob_window_bg ob_ic_back ob_ic_material3 \
    ob_ic_chevron_down ob_app_icon_placeholder; do cp $SRC/res/drawable/$f.xml $DST/res/drawable/; done
cp $SRC/res/drawable/np_*.xml $DST/res/drawable/
for f in ob_new_project ob_np_template ob_np_palette ob_toggle_row myproject_color; do cp $SRC/res/layout/$f.xml $DST/res/layout/; done
sed -i 's/@id\/layout_theme_color/@+id\/layout_theme_color/; s/@id\/color"/@+id\/color"/; s/@id\/name"/@+id\/name"/' $DST/res/layout/myproject_color.xml
cp $SRC/res/font/manrope_*.ttf $SRC/res/font/sora_bold.ttf $DST/res/font/
cp $SRC/res/animator/sw_press.xml $DST/res/animator/; cp $SRC/res/color/ob_check_tint.xml $DST/res/color/
cp $SRC/res/values/colors_ob.xml $DST/res/values/
python3 - "$SRC/res/values/styles_ob.xml" "$DST/res/values/styles_ob.xml" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
keep = ['Ob', 'Ob.Text', 'Ob.Text.Brand', 'Ob.Text.Screen', 'Ob.Text.Title', 'Ob.Text.Body', 'Ob.Text.Caption',
        'Ob.Text.Label', 'Ob.Text.Button', 'Ob.Text.Button.Cta', 'Ob.IconPill', 'Ob.FieldLabel', 'Ob.Field']
out = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
for m in re.finditer(r'(<style name="([^"]+)"[^>]*?(/>|>.*?</style>))', src, re.S):
    if m.group(2) in keep: out.append('    ' + m.group(1))
out.append('</resources>')
open(sys.argv[2], 'w').write('\n'.join(out) + '\n')
PY
cp $SRC/java/com/sketchware/ai/project/M3Palette.java $SRC/java/com/sketchware/ai/project/ThemePresets.java $DST/java/com/sketchware/ai/project/
cp $SRC/java/com/sketchware/ai/ui/ThemePreviewView.java $DST/java/com/sketchware/ai/ui/
echo "copied; then: ./gradlew :render:recordPaparazziDebug --tests 'pz.NewProjectScreenTest'"
