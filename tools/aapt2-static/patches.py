#!/usr/bin/env python3
"""Source patches applied to the pristine AOSP checkout (idempotent).

aapt2 is a *host* tool in AOSP.  Here it is compiled with the NDK, so
__ANDROID__ is defined and several libraries would pick their on-device code
paths (logd logging, libincfs, binder, libvndksupport ...).  Each patch below
restores the host behaviour when AAPT2_STATIC_TOOL is defined.
"""
import sys, pathlib

S = pathlib.Path(sys.argv[1])
HOSTLIKE = "#if defined(__ANDROID__) && !defined(AAPT2_STATIC_TOOL)"

PATCHES = [
    # incfs verification needs libincfs (device only) -> use plain FileMap path.
    ("system/incremental_delivery/incfs/util/include/util/map_ptr.h", "#ifdef __ANDROID__", HOSTLIKE, None),
    ("system/incremental_delivery/incfs/util/map_ptr.cpp", "#ifdef __ANDROID__", HOSTLIKE, None),
    # androidfw: no logcat noise, no binder.
    ("frameworks/base/libs/androidfw/include/androidfw/Util.h", "#ifdef __ANDROID__", HOSTLIKE, None),
    ("frameworks/base/libs/androidfw/ResourceTypes.cpp", "#ifdef __ANDROID__\n#include <binder/TextOutput.h>",
     HOSTLIKE + "\n#include <binder/TextOutput.h>", 1),
    # libbase / liblog: log to stderr (like the host tool) rather than logd.
    ("system/libbase/logging.cpp", "#ifdef __ANDROID__\n  static auto& logger = *new LogFunction(LogdLogger());",
     HOSTLIKE + "\n  static auto& logger = *new LogFunction(LogdLogger());", 1),
    ("system/logging/liblog/logger_write.cpp",
     "#ifdef __ANDROID__\nstatic __android_logger_function logger_function = __android_log_logd_logger;",
     HOSTLIKE + "\nstatic __android_logger_function logger_function = __android_log_logd_logger;", 1),
    # libziparchive: host build does not emit "Zip: empty archive?" warnings / SafetyNet events.
    ("system/libziparchive/zip_archive.cc", "#if defined(__ANDROID__)\n", HOSTLIKE + "\n", 3),
    # libutils: no libvndksupport / sphal.
    ("system/core/libutils/misc.cpp", "#if defined(__ANDROID__) && !defined(__ANDROID_RECOVERY__)",
     "#if defined(__ANDROID__) && !defined(__ANDROID_RECOVERY__) && !defined(AAPT2_STATIC_TOOL)", None),
]

for rel, old, new, count in PATCHES:
    p = S / rel
    txt = p.read_text()
    if new in txt:
        continue  # already applied
    if old not in txt:
        sys.exit(f"patch failed: {rel}: pattern not found")
    n = txt.count(old)
    if count is not None and n != count:
        sys.exit(f"patch failed: {rel}: expected {count} matches, got {n}")
    p.write_text(txt.replace(old, new))
    print(f"patched {rel} ({n}x)")
