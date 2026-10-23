# Sketchware AI

A fork of **Sketchware Pro v6.4.0-rc05** (package `com.sketchware.ai`) for building Android apps from blocks on a phone.
Current release: **v1.8.5** (versionCode 175). Maintained by AiTechApps. Not affiliated with Sketchware
(besome) or with the Sketchware Pro team.

## Licence status (read this first)

- **This project is not open source.** It is built on Sketchware Pro, which its own [LICENSE](LICENSE) calls
  *source-available, not open-source*: most of its code is decompiled from the original Sketchware app, and the licence
  allows forks for personal use. Every file this fork inherits keeps that status.
- **GPL-3.0 parts** (as stated in the upstream LICENSE): the `kotlinc` module, taken from
  [CodeAssist](https://github.com/tyron12233/CodeAssist), and the `minApi26` build flavour, a derivative of it.
  The APK this fork ships is the `minApi26` flavour, so those parts are GPL-3.0.
- **Third-party components** (AOSP aapt2 and libraries, R8/D8, desugar_jdk_libs, bundletool, apksig, ECJ, kotlinc,
  fonts, and the libraries generated apps can use) keep their own licences: Apache-2.0, BSD-3-Clause, EPL-2.0,
  GPL-2.0 with Classpath Exception, OFL-1.1 and others. Each one is listed in [CREDITS.md](CREDITS.md).
- The code this fork added (`com.sketchware.ai.*`, `tools/`, `engineering/`) has **no separate licence grant yet**.

This is a summary of what the files say, not legal advice.

## What "AI" means here

The name is the product name. **There is no AI model, LLM or cloud service in the app or in its builds.** The build
auto-fix and Build Doctor are rule-based and run offline on the phone. Network is used only to download Maven libraries
(Google Maven, Maven Central, JitPack).

## Shipped (v1.0 – v1.8.5)

Everything in this section is in the released APK and was exercised as described under *How it is tested*.

| Area | What it does | Since |
|---|---|---|
| On-phone toolchain | aapt2 2.20 built from AOSP `android-16.0.0_r4`, static, for arm64-v8a, armeabi-v7a, x86_64, x86, 16 KB-aligned | v1.0 (64-bit), v1.8.4 (all ABIs), v1.8.5 (android-16) |
| | Projects compile against the bundled Android 14 (API 34) `android.jar`; ECJ 3.26 (Java 7 to 11) | v1.8.4 |
| | D8 / R8 8.11.18 with core library desugaring (`desugar_jdk_libs_nio` 2.1.5): `java.time`, streams and `java.nio.file` on older Android versions. If that step fails, the build continues without it and says so | v1.8.5 |
| | Embedded Kotlin compiler 1.7.10; bundletool 1.18.3 (AAB export, 16 KB page alignment for native libraries) | upstream / v1.8.5 |
| Build repair (offline, rule-based) | Unreachable code, missing imports, missing or broken libraries (restore, exact Maven download), damaged PNGs, missing images. Repairs never move a line. Repairs inside *add source directly* blocks can be stored back into the block | v1.7.0 – v1.8.2 |
| Diagnostics | Source map (a compile error points to its block, with *Open block*), build map (time and status per step), Build Doctor diagnosis, build history | v1.8.0 – v1.8.2 |
| New-project defaults | Target API 37, min API 21, D8, Java 11, Material 3 theme. Existing projects keep their stored settings | v1.2 – v1.8.4 |
| Material 3 | Material 3 AppTheme (light). Plain Buttons become filled Material 3 buttons, Switches `MaterialSwitch`, CheckBoxes and RadioButtons their Material versions, plain EditTexts outlined `TextInputLayout` fields; default text colours follow the theme. Widgets the project's own code treats as framework widgets stay as they are. The design canvas draws them the same way | v1.8.4, v1.8.5 |
| Fonts | `SketchFonts`: custom TTF/OTF fonts with room for Telugu (and other) stacked glyphs | v1.8.4 |
| Export | APK signed v1+v2+v3; AAB with a SHA-256 JAR signature; "Export to Android Studio" writes an AGP 8.12 / Gradle 8.13 project | v1.8.2, v1.8.5 |
| Built-in libraries | Material 1.13.0, AppCompat 1.7.1, AndroidX core 1.13.1 and the rest of the set listed in `engineering/component-library-inventory.md` | v1.8.4 |
| Native libraries | Warning for a 64-bit `.so` with 4 KB page alignment; local libraries' `.so` files in APK and AAB | v1.8.4 |
| UI | "Obsidian liquid glass" visual layer over upstream's screens. The logic editor, block drawer and view editor are upstream's own (precompiled) classes | v1.6 |

## How it is tested, and what is not

- **Android runtime harness** (`tools/art-harness`): the APK's own classes run on an Android 12 (API 31) ART runtime
  **without the Android framework**: whole Run builds of fixture projects 901–912 and AAB exports of 908–912 (aapt2,
  ECJ, Kotlin, D8/L8, DEX merge, packaging, signing). This is not a device test and says nothing about other Android versions.
- **layoutlib** (`tools/layoutlib-renders`): Android 14's view, theme and text code on a desktop JVM, for generated
  layouts and themes (on light and night-mode device settings) and Telugu text.
- **Host checks**: resource-ID pinning, API-level and linkage scans of the APK, signatures, 16 KB alignment.
- **Not run here**: the IDE's screens (logic editor, design editor, compile log), aapt2 on a real phone, installing or
  starting generated apps. Device testing is done by the maintainer on his phone (Android 15) after each build; its
  results go to `engineering/compatibility-matrix.md`.

Per-release evidence: `engineering/v<version>-verification.md`; device and API status:
`engineering/compatibility-matrix.md`.

## Known limits

- Project code can't call Android 15+ APIs unless a newer `android.jar` is set in Build Settings (the bundled one is API 34).
- Java 17 language level is not possible with ECJ 3.26 and the on-phone R8 (`engineering/java17-path.md`).
- Kotlin code is compiled by kotlinc 1.7.10, which reads Kotlin metadata up to 1.8: Kotlin sources can't use libraries built with newer Kotlin.
- Material Components 1.14 (Material 3 Expressive) needs min SDK 23 and API 35: not shipped.
- Firebase, Play services (Ads 20.1.0, Maps, Auth), OneSignal and Audience Network are held at their old versions.
- The debug APK is signed with the public test key, so it installs over earlier releases. It is not a release key.

## Advanced Roadmap (v1.9 – v2.0): not built

**None of the following exists in the code yet.** Order as planned; one release does not attempt everything.

1. Build Doctor strategies with IDs, skipping fixes that already failed for the same fingerprint.
2. Error dashboard.
3. Verified repair memory kept on disk.
4. Dependency and library intelligence.
5. Developer-tools controller, Project Brain, Git/GitHub, extensions, Android Studio import (export exists).
6. Sign-in and telemetry: reviewed only (`engineering/auth-telemetry-review.md`). Blocked until a Firebase project for
   `com.sketchware.ai`, a private release key and consent screens exist. Telemetry would ship switched off.
7. Material Components 1.14 migration (needs min SDK 23 and a bundled API 35 `android.jar`).
8. Dark mode (DayNight) for generated apps as an opt-in project setting: off for now, because colours, icons and
   `setTextColor` blocks in existing projects assume a light background.
9. Any AI assistance (on-device or cloud model): not designed. Today's rules keep models out of the build.

## Building from source

The tree is the upstream source zip `Sketchware-Pro-6_4_0-rc05.zip` (sha256
`ac9c63106dd81879ece6178fc83bb0356dfb0aef6a5493eebf338008d2b1f0b8`) plus this fork's commits. Files in folders hidden by
the `.gitignore` rule `build/` ship as whole files in the release's patches zip (`overlay/`).

Needs JDK 17 and the Android SDK (platform 34; Gradle 8.13 and AGP 8.12 are fetched by the wrapper). Then:

```
echo sdk.dir=/path/to/android-sdk > local.properties
./gradlew assembleMinApi26Debug
```

Output: `app/build/outputs/apk/minApi26/debug/app-minApi26-debug.apk`.

## Repository layout

| Path | Contents |
|---|---|
| `app/` | the IDE (upstream sources + this fork's changes; most of the core is precompiled in `app/libs/*.jar`) |
| `kotlinc/` | the embedded Kotlin compiler module (GPL-3.0, from CodeAssist) |
| `engineering/` | verified state of the fork, per-release verification records, test fixtures, compatibility matrix, known errors |
| `tools/` | Android runtime harness, layoutlib renders, static aapt2 build, built-in library packaging, APK scans, JVM tests |
| `releases/` | release notes and SHA-256 sums per release. The APK (about 138 MB) and the patches zip are release assets, not git files: GitHub refuses files over 100 MB |

## Credits

Sketchware (besome), the Sketchware Pro team and contributors, CodeAssist (tyron12233), the Android Open Source Project,
and the authors of every component in [CREDITS.md](CREDITS.md).
