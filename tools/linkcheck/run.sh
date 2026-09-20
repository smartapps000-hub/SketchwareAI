#!/bin/bash
# Release check: finds calls from this repo's code into APK methods that need classes missing
# from both the APK and the Android framework (NoClassDefFoundError at run time).
# v1.6.0 crashed this way (ResourcesCompat.getFont -> TypefaceCompat, absent from the stripped
# AndroidX jar). Usage: tools/linkcheck/run.sh path/to/app.apk
set -e
APK=$(realpath "$1"); HERE=$(cd "$(dirname "$0")" && pwd)
BT=${BUILD_TOOLS:-/opt/android-sdk/build-tools/36.1.0}; PLATFORM=${PLATFORM_JAR:-/opt/android-sdk/platforms/android-36/android.jar}
W=$(mktemp -d); cd "$W"
unzip -q -o "$APK" 'classes*.dex'
for d in classes*.dex; do "$BT/dexdump" -d "$d" 2>/dev/null; done > dump.txt
grep -o "Class descriptor  : 'L[^']*;'" dump.txt | sed "s/.*'\(L[^']*;\)'/\1/" | sort -u > defined.txt
unzip -Z1 "$PLATFORM" | grep '\.class$' | sed 's/\.class$//; s/^/L/; s/$/;/' | sort -u > framework.txt
python3 "$HERE/check_links.py"
