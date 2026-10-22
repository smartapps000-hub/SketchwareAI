#!/bin/bash
# v1.8.4 check: every ABI's aapt2 compiles the built-in library res and links it against android-34; the
# resource tables are compared with `aapt2 dump resources` (32-bit and 64-bit builds order the string pool differently).
# usage: run.sh <label> <runner prefix...> <aapt2>   compiles the library res set (/home/claude/bl/a2/lib) and links it against android-34
label=$1; shift
out=/home/claude/a2cmp/out-$label; rm -rf $out; mkdir -p $out/flat $out/gen
A2=/home/claude/bl/a2
for d in $A2/lib/*/; do n=$(basename $d); env -u TZ "$@" compile --dir $d/res -o $out/flat/$n.zip >>$out/log.txt 2>&1 || { echo "$label COMPILE FAIL $n"; tail -3 $out/log.txt; exit 1; }; done
args=""; for z in $out/flat/*.zip; do args="$args -R $z"; done
env -u TZ "$@" link --auto-add-overlay --allow-reserved-package-id --no-version-vectors -I /opt/android-sdk/platforms/android-34/android.jar --manifest $A2/AndroidManifest.xml $args --min-sdk-version 21 --target-sdk-version 37 --extra-packages $(cat $A2/pk.txt) --java $out/gen -o $out/res.apk >>$out/link.txt 2>&1
code=$?; echo "$label: link exit=$code R.java=$(find $out/gen -name R.java | wc -l) arsc=$(unzip -p $out/res.apk resources.arsc 2>/dev/null | sha256sum | cut -c1-16) R=$(find $out/gen -name R.java | sort | xargs cat | sha256sum | cut -c1-16)"
[ $code -eq 0 ] || grep -v tzdata $out/link.txt | tail -3
