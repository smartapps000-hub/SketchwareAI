# Device / API compatibility matrix

Four different things, tracked separately. A row says "tested" only for what was actually run.

## 1. Sketchware AI itself (the IDE)

| Item | Value |
|---|---|
| minSdk | 26 (flavor minApi26, the one that is shipped); a minApi21 flavor compiles but isn't shipped |
| targetSdk | 34 |
| compileSdk | 30 (newer APIs are called by reflection) |

## 2. Where the IDE's build engine has been run

| Runtime | What ran | Result |
|---|---|---|
| Android 12 (API 31) ART, x86_64, interpreter, **no Android framework** | the APK's own classes: block-to-code generator (Jx/Hx/Fx), source map, ECJ compile step (`Dp.compileJavaCode`), auto-fix, library resolver, D8 on a downloaded library | passes the fixtures in test-fixtures.json |
| JVM 17 (desktop) | same engine classes, larger corpora (false-positive scans) | passes |
| layoutlib (Paparazzi) | layouts of the diagnostic sheet and dialogs; `BlockFocus` | rendered / asserted |
| Any real phone or emulator, by the developer of this fork | nothing | **not tested** |
| User's phone | every release is installed and used by the user | v1.8.0: project #644 built, installed and started; #623 failed in AAPT2 (damaged PNG); #606 failed in Java compile (library without classes.jar). Android version not recorded here |
| Network from the Android runtime here | not possible: the sandbox kernel has no IPv6 and Android opens IPv6 sockets | downloads are run on JVM 17 with the same classes |

Not run anywhere off-device: Activities (logic editor, compile log screen, source viewer), AAPT2, DEX merge, APK packaging/signing, installation.

"Tested on the Android 12 runtime" means only the rows above. It does not mean the IDE supports only Android 12, and it says nothing about Android 8-11 or 13+.

## 3. Projects built with the IDE (generated APKs)

| Item | Where it comes from |
|---|---|
| minSdk / targetSdk | per project: Project Configuration (defaults in `ModernProjectDefaults`) |
| compile platform | bundled android.jar = Android 13 (API 33) unless a custom android.jar is set in Build Settings; APIs newer than 33 can't be called from project code |
| Java level | ECJ 3.26 with -11, falling back to 1.8; D8/R8 3.3 |
| Libraries | bundled AndroidX/Firebase/Play libraries are 2021-2022 releases; local libraries as installed |

Whether a generated APK runs on a given Android version depends on that project's minSdk/targetSdk, the APIs and libraries it uses, and platform behaviour changes. A successful compile on one runtime proves none of that.

## 4. Generated APKs actually run

| Device API | Result |
|---|---|
| (none by the developer of this fork) | no generated APK has been installed or started off-device |
| user's phone (API not recorded) | project #644's APK installed and started (v1.8.0) |
