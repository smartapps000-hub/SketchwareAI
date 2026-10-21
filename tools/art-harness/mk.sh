#!/bin/bash
# Builds out/stubs.zip (framework stand-ins), out/harness.zip and out/android-dex.zip (the IDE's bundled android.jar, dexed).
# Needs JDK 17 and Android build-tools (d8). Usage: mk.sh [repo root, default ../..]
set -e
T=$(cd $(dirname $0) && pwd); REPO=${1:-$T/../..}; O=$T/out; mkdir -p $O
unset JAVA_TOOL_OPTIONS; J=/usr/lib/jvm/java-17-openjdk-amd64/bin; D8=$(ls -d /opt/android-sdk/build-tools/*/d8 | tail -1)
rm -rf $O/stubs-cls $O/h-cls; mkdir -p $O/stubs-cls $O/h-cls
$J/javac -nowarn --release 11 -d $O/stubs-cls $(find $T/stubs -name '*.java')
$D8 --min-api 31 --output $O/stubs.zip $(find $O/stubs-cls -name '*.class')
$J/javac -nowarn --release 11 -cp $O/stubs-cls -d $O/h-cls $(find $T/src -name '*.java')
if [ ! -f $O/android-dex.zip ]; then
  unzip -o -q -d $O $REPO/app/src/main/assets/libs/android.jar.zip android.jar
  $D8 --min-api 31 --output $O/android-dex.zip $O/android.jar
fi
$D8 --min-api 31 --lib $O/android.jar --classpath $O/stubs-cls --output $O/harness.zip $(find $O/h-cls -name '*.class')
ls -la $O/*.zip
