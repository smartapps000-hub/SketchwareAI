# Sources of the static aapt2 build

All AOSP code comes from **one release tag: `android-16.0.0_r4`** (v1.8.5; before: `android-14.0.0_r75`),
taken from `https://android.googlesource.com`. Only the listed subdirectories are downloaded, as
gitiles tarballs:
`https://android.googlesource.com/<project>/+archive/refs/tags/android-16.0.0_r4/<path>.tar.gz`.
`build.sh` does the download. The commit hashes below are what the tag resolves to
(`git ls-remote <repo> refs/tags/android-16.0.0_r4^{}`, checked 2026-10-22).

| Project | Path(s) fetched | Commit at tag | Used for |
|---|---|---|---|
| platform/frameworks/base | `tools/aapt2`, `libs/androidfw`, `cmds/idmap2/libidmap2_policies`, `NOTICE` (single file) | 45034f0663f960d9ee5fb0a101a4732b71f6e2f4 | aapt2 itself (reports 2.20), libandroidfw (+ libandroidfw_pathutils), the libidmap2_policies header |
| platform/system/libbase | whole project | bdf0f4db82fae78492c92a55d473aacc4e963e79 | libbase |
| platform/system/core | `libutils` (with `libutils/binder`), `libcutils`, `libsystem` | e9d1fa3705d7fbd0ac1c942bc4c276161bfbfed1 | libutils (+ libutils_binder), libcutils, the system/ headers |
| platform/system/logging | `liblog` | 58dac317ea08b8f275ebd01ce864acab2de30b93 | liblog |
| platform/system/libziparchive | whole project | 94db64a81930e4cc8bacca4010bf935f664c97e1 | libziparchive |
| platform/system/incremental_delivery | `incfs` (only `incfs/util/map_ptr.cpp` and its headers are compiled) | 6d8b9b339e7cc9abfeba475fc4c89984c2824cd9 | libincfs-utils (used by androidfw) |
| platform/build/soong | `cc/libbuildversion` (header only) | f389fa2a2a768a93bc99957e2288f3fbee032bff | `build/version.h`. A generated one-line `GetBuildNumber()` returns the tag name. |
| platform/external/protobuf | whole project (protobuf v25.8, C++ 4.25.8) | 31fc4250f78631cf8f4f1cc48b200acf11e9c884 | libprotobuf (built with protobuf's own CMake files) for the target, and the **host `protoc`** used to generate the aapt2 `.pb.cc` files |
| platform/external/abseil-cpp | whole project (20250512.1) | 2bf8472a92b84fe4617eeb50053e7a942edcb1b3 | abseil, which protobuf 22+ needs (target and host protoc) |
| platform/external/expat | whole project (`testdata/` is deleted after download) | 79dde19a8fe1dfb580d39de37854221a6de5f09b | libexpat 2.7.1 |
| platform/external/libpng | whole project | 812ea249a49e26a13fa080821dc094f33c0f6608 | libpng 1.6.47 (ARM NEON and Intel SSE2 filters) |
| platform/external/zlib | whole project | a6f47e8bd7c1ba412c4b5a3539aa77fe628d0172 | libz (Chromium zlib, as in AOSP) |
| platform/external/fmtlib | whole project | d50695f04854e474766da7d713bf8242f4ceb4f8 | fmtlib 11.0.2 (libbase depends on it) |

Protobuf-generated sources: `ApkInfo.proto`, `Configuration.proto`, `Resources.proto`,
`ResourceMetadata.proto` and `ResourcesInternal.proto` (the full list in aapt2's
Android.bp at this tag). They are generated at build time by the host protoc 25.8 built
from the same external/protobuf.

The dependency list comes from the `aapt2` / `libaapt2` / `aapt2_defaults` modules in
`tools/aapt2/Android.bp` at the tag: static_libs libandroidfw, libutils, liblog, libcutils,
libexpat, libziparchive, libpng, libbase, libprotobuf-cpp-full, libz, libbuildversion,
libidmap2_policies. The build leaves out the `jni_headers` header lib because aapt2 does not
include jni.h. Tests, fuzzers, benchmarks and Windows/Darwin-only files are not built.

## Toolchain

* Android NDK r28c (28.2.13676358): clang, `libc++_static`, and the bionic static
  `libc.a`/`libm.a`. All four ABIs (arm64-v8a, x86_64 since v1.0; armeabi-v7a, x86 since v1.8.4,
  replacing upstream's older 32-bit binaries, which were 4 KB-aligned and can't compile `<macro>`
  resources such as Material 1.11+). Target `android-24`, `-static`, `-Wl,-z,max-page-size=16384`,
  `-Wl,--gc-sections`, stripped with `llvm-strip --strip-all`.
* The host protoc is built with the system clang (Ubuntu clang 18) and CMake/Ninja.

## Local modifications

* `CMakeLists.txt`: a new build description that replaces Soong. The source lists are
  copied from the Android.bp files at the tag, using the *host* variants.
* `stub-include/gtest/gtest_prod.h`: defines only `FRIEND_TEST`, which the libziparchive
  headers need.
* `stub-include/android_content_res.h` (android-16): androidfw reads one aconfig flag,
  `resource_readwrite_flags`. Soong generates this header from the flag declarations; the read-only
  host library of the release has it fixed off, and the stub returns `false` the same way.
* Link (android-16): abseil's log sink asks for `-llog`; with `-static` the linker resolves it to the
  `liblog.a` built here (`target_link_directories`), so nothing is loaded at run time.
* `patches.py`: small source patches. In AOSP aapt2 is a host tool. Compiling it with the
  NDK defines `__ANDROID__`, which would switch some libraries to their on-device code
  paths. The patches keep the host behaviour when `AAPT2_STATIC_TOOL` is defined:
  * incfs `map_ptr.h/.cpp`: no libincfs verification. It uses a plain FileMap, as on the host.
  * androidfw `Util.h`: `ANDROID_LOG` is a no-op (NullLogger), as on the host.
    `ResourceTypes.cpp`: no binder include.
  * libbase `logging.cpp` and liblog `logger_write.cpp`: the default logger writes to
    stderr, not logd.
  * libziparchive `zip_archive.cc`: no "Zip: empty archive?" logcat warning and no
    SafetyNet event log.
  * libutils `misc.cpp`: no libvndksupport/sphal dlopen.
* Compile definitions: `__ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__` makes the declarations
  of APIs newer than API 24 visible (fdsan, `__system_property_read_callback`, liblog's
  API-30 functions). They are resolved from the NDK's static `libc.a` and our own
  liblog, so nothing is looked up at run time. libziparchive gets
  `INCFS_SUPPORT_DISABLED=1`, the same as AOSP's plain `libziparchive` module.
