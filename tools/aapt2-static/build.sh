#!/usr/bin/env bash
# Build statically linked aapt2 executables for Android (arm64-v8a, x86_64, armeabi-v7a, x86)
# from AOSP sources.  Output: out/<abi>/libaapt2.so (an executable named lib*.so
# so the Android package installer extracts it into nativeLibraryDir).
#
# Usage: ./build.sh [--clean]
# Env:   AOSP_TAG (default android-14.0.0_r75), ANDROID_NDK, ABIS, JOBS
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TAG="${AOSP_TAG:-android-14.0.0_r75}"
NDK="${ANDROID_NDK:-/opt/android-sdk/ndk/28.2.13676358}"
ABIS="${ABIS:-arm64-v8a x86_64 armeabi-v7a x86}"
API=24
JOBS="${JOBS:-$(nproc)}"
SRC="$ROOT/src"
BUILD="$ROOT/build"
OUT="$ROOT/out"
GITILES=https://android.googlesource.com
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

log() { printf '\n==> %s\n' "$*"; }

if [[ "${1:-}" == "--clean" ]]; then
  rm -rf "$SRC" "$BUILD" "$OUT"
fi

[[ -f "$NDK/build/cmake/android.toolchain.cmake" ]] || { echo "NDK not found at $NDK" >&2; exit 1; }
for t in cmake ninja clang clang++ python3 curl tar; do command -v $t >/dev/null || { echo "missing $t" >&2; exit 1; }; done

# ---------------------------------------------------------------------------
# 1. Fetch sources: only the needed subdirectories, as gitiles tarballs.
# ---------------------------------------------------------------------------
# fetch <project> <path-in-project or ""> <dest relative to src/>
fetch() {
  local project=$1 path=$2 dest="$SRC/$3"
  [[ -f "$dest/.fetched-$TAG" ]] && return 0
  rm -rf "$dest"; mkdir -p "$dest"
  local url="$GITILES/$project/+archive/refs/tags/$TAG${path:+/$path}.tar.gz"
  echo "fetch $url"
  curl -sSfL --retry 3 "$url" | tar xz -C "$dest"
  touch "$dest/.fetched-$TAG"
}
# fetch_file <project> <file> <dest file relative to src/>
fetch_file() {
  local project=$1 file=$2 dest="$SRC/$3"
  [[ -s "$dest" ]] && return 0
  mkdir -p "$(dirname "$dest")"
  curl -sSfL --retry 3 "$GITILES/$project/+/refs/tags/$TAG/$file?format=TEXT" | base64 -d > "$dest"
}

log "Fetching AOSP sources at $TAG"
fetch platform/frameworks/base      tools/aapt2                  frameworks/base/tools/aapt2
fetch platform/frameworks/base      libs/androidfw               frameworks/base/libs/androidfw
fetch platform/frameworks/base      cmds/idmap2/libidmap2_policies frameworks/base/cmds/idmap2/libidmap2_policies
fetch_file platform/frameworks/base NOTICE                       frameworks/base/NOTICE
fetch platform/system/libbase       ""                           system/libbase
fetch platform/system/core          libutils                     system/core/libutils
fetch platform/system/core          libcutils                    system/core/libcutils
fetch platform/system/core          libsystem                    system/core/libsystem
fetch platform/system/logging       liblog                       system/logging/liblog
fetch platform/system/libziparchive ""                           system/libziparchive
fetch platform/system/incremental_delivery incfs                 system/incremental_delivery/incfs
fetch platform/build/soong          cc/libbuildversion           build/soong/cc/libbuildversion
fetch platform/external/protobuf    ""                           external/protobuf
fetch platform/external/expat       ""                           external/expat
fetch platform/external/libpng      ""                           external/libpng
fetch platform/external/zlib        ""                           external/zlib
fetch platform/external/fmtlib      ""                           external/fmtlib
rm -rf "$SRC/external/expat/testdata"   # 128 MB of test data, unused

# ---------------------------------------------------------------------------
# 2. Patch (host-tool behaviour on bionic; see patches.py).
# ---------------------------------------------------------------------------
log "Applying patches"
python3 "$ROOT/patches.py" "$SRC"

# ---------------------------------------------------------------------------
# 3. Host protoc from the same external/protobuf (v21.12).
# ---------------------------------------------------------------------------
PROTOC="$BUILD/host-protoc/protoc"
if [[ ! -x "$PROTOC" ]]; then
  log "Building host protoc"
  cmake -G Ninja -S "$SRC/external/protobuf" -B "$BUILD/host-protoc" \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_CXX_FLAGS="-I$SRC/external/protobuf/android" \
    -Dprotobuf_BUILD_TESTS=OFF -Dprotobuf_WITH_ZLIB=OFF -Dprotobuf_BUILD_SHARED_LIBS=OFF
  ninja -C "$BUILD/host-protoc" -j "$JOBS" protoc
fi
"$PROTOC" --version

# ---------------------------------------------------------------------------
# 4. Cross-compile aapt2 for each ABI with the NDK (static, 16 KB pages).
# ---------------------------------------------------------------------------
for ABI in $ABIS; do
  log "Building aapt2 for $ABI"
  cmake -G Ninja -S "$ROOT" -B "$BUILD/$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DAOSP_SRC="$SRC" -DHOST_PROTOC="$PROTOC" \
    -DAAPT2_BUILD_NUMBER="$TAG"
  ninja -C "$BUILD/$ABI" -j "$JOBS" aapt2
  mkdir -p "$OUT/$ABI"
  "$TC/llvm-strip" --strip-all -o "$OUT/$ABI/libaapt2.so" "$BUILD/$ABI/aapt2"
  chmod 755 "$OUT/$ABI/libaapt2.so"
done

# ---------------------------------------------------------------------------
# 5. NOTICE: licenses of everything linked into the binaries.
# ---------------------------------------------------------------------------
log "Writing $OUT/NOTICE"
{
  section() { printf '\n%s\n%s\n%s\n\n' "================================================================================" "$1" "================================================================================"; cat "$2"; }
  echo "aapt2 ($TAG), statically linked. This file collects the license/NOTICE texts of all components linked into it."
  section "frameworks/base (tools/aapt2, libs/androidfw, cmds/idmap2 headers) - Apache-2.0" "$SRC/frameworks/base/NOTICE"
  section "system/libbase - Apache-2.0" "$SRC/system/libbase/NOTICE"
  section "system/core/libutils - Apache-2.0" "$SRC/system/core/libutils/NOTICE"
  section "system/core/libcutils - Apache-2.0" "$SRC/system/core/libcutils/NOTICE"
  section "system/logging/liblog - Apache-2.0" "$SRC/system/logging/liblog/NOTICE"
  section "system/incremental_delivery/incfs (util/map_ptr) - Apache-2.0" "$SRC/system/incremental_delivery/incfs/NOTICE"
  printf '\n%s\n%s\n%s\n\n%s\n' "================================================================================" \
    "system/libziparchive, build/soong/cc/libbuildversion (header) - Apache-2.0" \
    "================================================================================" \
    "Copyright (C) The Android Open Source Project. Licensed under the Apache License, Version 2.0 (full text in the frameworks/base section above)."
  section "external/protobuf (v21.12) - BSD-3-Clause" "$SRC/external/protobuf/LICENSE"
  section "external/expat - MIT" "$SRC/external/expat/expat/COPYING"
  section "external/libpng - libpng license" "$SRC/external/libpng/LICENSE"
  section "external/zlib (Chromium zlib) - zlib license" "$SRC/external/zlib/LICENSE"
  section "external/fmtlib - MIT" "$SRC/external/fmtlib/LICENSE"
  section "Android NDK r28c runtime: bionic libc.a/libm.a/libdl.a (static) - BSD/MIT/ISC and others" "$NDK/NOTICE"
  LLVM_NOTICE="$NDK/toolchains/llvm/prebuilt/linux-x86_64/NOTICE"
  printf '\n%s\n%s\n%s\n\n' "================================================================================" \
    "Android NDK r28c runtime: LLVM libc++ (c++_static), libc++abi, libunwind, compiler-rt builtins - Apache-2.0 WITH LLVM-exception" \
    "================================================================================"
  awk '/^The LLVM Project is under the Apache License/ && ++n==2 {exit} {print}' "$LLVM_NOTICE" | head -n -1
} > "$OUT/NOTICE"

log "Done"
for ABI in $ABIS; do file "$OUT/$ABI/libaapt2.so"; done
