#!/bin/bash
# usage: run.sh <IDE apk> <root dir for the app's own folders> create <spec> <sc_id> | build <sc_id> [resume step] | aab <sc_id>
# The telugu fixture reads its fonts from out/fonts (make-test-fonts.py out/fonts).
# Projects go to /storage/emulated/0/.sketchware as on a phone. aapt2 is the APK's own lib/x86_64/libaapt2.so.
T=$(cd $(dirname $0) && pwd); O=$T/out
APK=$1; ROOT=$2; shift 2; mkdir -p $ROOT $O/native
unzip -o -q -j $APK lib/x86_64/libaapt2.so -d $O/native && chmod +x $O/native/libaapt2.so
ART_OPTS="-Xmx1536m -Xms64m -Dh.apk=$APK -Dh.root=$ROOT -Dh.native=$O/native -Dh.fonts=${H_FONTS:-$O/fonts}" exec $T/art.sh $O/stubs.zip:$O/harness.zip:$APK:$O/android-dex.zip h.Main "$@"
