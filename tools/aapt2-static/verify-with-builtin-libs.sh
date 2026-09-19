#!/bin/bash
# usage: run.sh <label> <aapt2 command...>
label=$1; shift
out=/home/claude/aapt2/realtest/out-$label; rm -rf $out; mkdir -p $out/res $out/gen
start=$(date +%s)
extra=""
for d in libs/*/; do
  n=$(basename $d)
  [ -d "$d/res" ] || continue
  env -u TZ "$@" compile --dir "$d/res" -o $out/res/$n.zip >>$out/log.txt 2>&1 || { echo "COMPILE FAIL $n"; tail -5 $out/log.txt; exit 1; }
  pkg=$(grep -o 'package="[^"]*"' $d/AndroidManifest.xml | head -1 | cut -d'"' -f2)
  [ -n "$pkg" ] && extra="$extra:$pkg"
done
env -u TZ "$@" compile --dir proj/res -o $out/project.zip >>$out/log.txt 2>&1 || { echo "COMPILE FAIL project"; exit 1; }
args=(link --allow-reserved-package-id --auto-add-overlay --no-version-vectors --no-version-transitions
  --min-sdk-version 24 --target-sdk-version 28 --version-code 1 --version-name 1.0 -I android.jar)
for z in $out/res/*.zip; do args+=(-R $z); done
args+=(-R $out/project.zip --java $out/gen --proguard $out/rules.txt --manifest proj/AndroidManifest.xml
  --extra-packages "${extra#:}" -o $out/res.apk)
env -u TZ "$@" "${args[@]}" >>$out/link.txt 2>&1; code=$?
end=$(date +%s)
echo "$label: link exit=$code, $(ls $out/res/*.zip | wc -l) libs compiled, $(find $out/gen -name R.java | wc -l) R.java files, $((end-start))s"
[ $code -eq 0 ] || tail -8 $out/link.txt
