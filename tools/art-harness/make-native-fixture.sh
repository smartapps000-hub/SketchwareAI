#!/bin/bash
# Files for fixture 910 'native' (v1.8.4): 64-bit .so files aligned to 4 KB and 16 KB pages (NDK r28 clang), and a tiny
# local library (classes.jar + classes.dex). Usage: make-native-fixture.sh <out dir>
set -e
O=${1:?out dir}; mkdir -p $O/src/com/fixture/nativelib $O/cls
TC=${ANDROID_NDK:-/opt/android-sdk/ndk/28.2.13676358}/toolchains/llvm/prebuilt/linux-x86_64/bin
echo 'int fixture_value(void) { return 42; }' > $O/f.c
for abi in aarch64:arm64-v8a x86_64:x86_64; do
  t=${abi%%:*}; a=${abi##*:}; mkdir -p $O/$a
  $TC/$t-linux-android24-clang -shared -fPIC -o $O/$a/lib4k.so $O/f.c -Wl,-z,max-page-size=4096
  $TC/$t-linux-android24-clang -shared -fPIC -o $O/$a/lib16k.so $O/f.c -Wl,-z,max-page-size=16384
done
cat > $O/src/com/fixture/nativelib/Native.java <<'J'
package com.fixture.nativelib;

public final class Native {
    public static String hello() {
        return "native fixture";
    }
}
J
unset JAVA_TOOL_OPTIONS
/usr/lib/jvm/java-17-openjdk-amd64/bin/javac --release 8 -d $O/cls $O/src/com/fixture/nativelib/Native.java
(cd $O/cls && /usr/lib/jvm/java-17-openjdk-amd64/bin/jar cf ../classes.jar .)
D8=$(ls -d /opt/android-sdk/build-tools/*/d8 | tail -1)
$D8 --min-api 21 --output $O $O/classes.jar
for f in $O/*/lib*.so; do echo "$f $(readelf -lW $f | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')"; done
ls $O
