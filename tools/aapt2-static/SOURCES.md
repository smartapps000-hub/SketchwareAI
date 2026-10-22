# Sources of the static aapt2 build

All AOSP code comes from **one release tag: `android-14.0.0_r75`**, taken from
`https://android.googlesource.com`. Only the listed subdirectories are downloaded, as
gitiles tarballs:
`https://android.googlesource.com/<project>/+archive/refs/tags/android-14.0.0_r75/<path>.tar.gz`.
`build.sh` does the download. The commit hashes below are what the tag resolves to
(`git ls-remote <repo> refs/tags/android-14.0.0_r75^{}`).

| Project | Path(s) fetched | Commit at tag | Used for |
|---|---|---|---|
| platform/frameworks/base | `tools/aapt2`, `libs/androidfw`, `cmds/idmap2/libidmap2_policies`, `NOTICE` (single file) | e0bc3c7701a52f8c9bdf67adb7353312c48e5aff | aapt2 itself (reports 2.19), libandroidfw (+ libandroidfw_pathutils), the libidmap2_policies header |
| platform/system/libbase | whole project | 2427e370cf5016d9c36d3ff2840d7f1e5bd0e157 | libbase |
| platform/system/core | `libutils` (with `libutils/binder`), `libcutils`, `libsystem` | 7d09db2d47ce283465c8644161f933b7c74d96f4 | libutils (+ libutils_binder), libcutils, the system/ headers |
| platform/system/logging | `liblog` | 153aeb8c89b0d499cda7e39a0f297f34836b4e19 | liblog |
| platform/system/libziparchive | whole project | cca3d3ecc2a314f2827c7ed65a3a9fb5be08a945 | libziparchive |
| platform/system/incremental_delivery | `incfs` (only `incfs/util/map_ptr.cpp` and its headers are compiled) | ee7adcbe339207787029de57288a988b361b5415 | libincfs-utils (used by androidfw) |
| platform/build/soong | `cc/libbuildversion` (header only) | 8bf89894cce3b5387e548a325febf7029828875c | `build/version.h`. A generated one-line `GetBuildNumber()` returns the tag name. |
| platform/external/protobuf | whole project (protobuf v21.12 / 3.21.12) | fece1c3e7124f940e00502a5431726ec2cd3b17a | libprotobuf-cpp-full for the target, and the **host `protoc`** used to generate the aapt2 `.pb.cc` files |
| platform/external/expat | whole project (`testdata/` is deleted after download) | e99bcc1aae9ec06c34808d2e980ae6a626bb2366 | libexpat 2.6.0 |
| platform/external/libpng | whole project | 944c6ff6487120af53c52a626a666a09d20153e2 | libpng 1.6.40 (ARM NEON and Intel SSE2 filters) |
| platform/external/zlib | whole project | bad63664301d80463fde8fb48ca81534b43c219c | libz (Chromium zlib, as in AOSP) |
| platform/external/fmtlib | whole project | 5ddb263616bf56e4f09213fdf9d0dd64aa2e3e63 | fmtlib 10.1.1 (libbase depends on it) |

Protobuf-generated sources: `ApkInfo.proto`, `Configuration.proto`, `Resources.proto`,
`ResourceMetadata.proto` and `ResourcesInternal.proto` (the full list in aapt2's
Android.bp at this tag). They are generated at build time by the host protoc 3.21.12 built
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
