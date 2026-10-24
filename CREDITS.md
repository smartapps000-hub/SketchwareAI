# Credits and licences

Sketchware AI v1.8.7. Who made what this fork is built from, and under which licence each part is distributed.
The column *Checked from* says where the licence was read; "not re-verified" means it is the component's published
licence but this fork found no licence file in the copy it ships. This is a record of what the files say, not legal advice.

## 1. The base

| Part | Licence | Checked from |
|---|---|---|
| Sketchware (besome): the original app, whose classes Sketchware Pro decompiled | All rights reserved ("Copyright Sketchware") | `app/src/main/assets/oss.txt` |
| Sketchware Pro v6.4.0-rc05 (the Sketchware Pro team and contributors) | Source-available, **not open source**; forks for personal use | [LICENSE](LICENSE) |
| `kotlinc` module (and `build-logic`, not part of the 6.4.0-rc05 source zip), from [CodeAssist](https://github.com/tyron12233/CodeAssist) by tyron12233 | GPL-3.0 | [LICENSE](LICENSE) |
| `minApi26` build flavour (the APK this fork ships), a derivative of the GPL-3.0 modules | GPL-3.0 | [LICENSE](LICENSE) |
| This fork's own additions (`com.sketchware.ai.*`, `tools/`, `engineering/`, its changes to upstream files), by AiTechApps | GPL-3.0 (the author's choice, v1.8.6) | [COPYING](COPYING) |

## 2. Toolchain inside the IDE APK (runs on the phone)

| Component | Version | Licence | Checked from |
|---|---|---|---|
| aapt2 with libandroidfw, libbase, libutils, libcutils, liblog, libziparchive, incfs utils (AOSP) | `android-16.0.0_r4` (2.20) | Apache-2.0 | `assets/licenses/aapt2-NOTICE.txt` (all texts) |
| ↳ protobuf | 25.8 | BSD-3-Clause | same NOTICE |
| ↳ abseil-cpp | 20250512.1 | Apache-2.0 | same NOTICE |
| ↳ expat | 2.7.1 | MIT | same NOTICE |
| ↳ libpng | 1.6.47 | libpng licence | same NOTICE |
| ↳ zlib (Chromium) | AOSP copy | zlib licence | same NOTICE |
| ↳ fmtlib | 11.0.2 | MIT | same NOTICE |
| ↳ NDK r28c runtime: bionic libc/libm/libdl; LLVM libc++, libc++abi, libunwind, compiler-rt | r28c | BSD/MIT/ISC and others; Apache-2.0 WITH LLVM-exception | same NOTICE |
| R8 / D8 / L8 | 8.11.18 | BSD-3-Clause (its POM also lists the licences of code it bundles: Apache-2.0, MIT, CDDL-1.1 / GPL-2.0 with Classpath Exception, LGPL-2.1, EDL-1.0) | Maven POM |
| desugar_jdk_libs (code from OpenJDK; in `assets/libs/desugar.zip`, and compiled into a generated app when it uses `java.time`, streams ...) | 2.1.5 | GPL-2.0 with Classpath Exception | Maven POM (`desugar_jdk_libs_nio` 2.1.5) |
| desugar_jdk_libs configuration (nio) | 2.1.5 | BSD-3-Clause | Maven POM |
| bundletool | 1.18.3 | Apache-2.0 | Maven POM |
| apksig (AAB signing, `BundleSigner`) | 8.12.0 | Apache-2.0 | Maven POM |
| apksigner (APK signing) | build-tools 32.0.0 | Apache-2.0 (AOSP) | not re-verified |
| Eclipse Compiler for Java (ECJ) | 3.26.0 (minApi26) / 3.13.0 (minApi21) | EPL-2.0 / EPL-1.0 | Maven POMs |
| Kotlin compiler (embeddable), JetBrains | 1.7.10 | Apache-2.0 | not re-verified (inside the GPL-3.0 `kotlinc` module) |
| ProGuard (`app/libs/proguard-base-7.2.2.jar`, from upstream) | 7.2.2 | GPL-2.0 | not re-verified |
| kellinwood zip-signer (upstream) | — | Apache-2.0 | `assets/oss.txt` |
| dx (DexMerger) and other AOSP code in upstream | — | Apache-2.0 | `assets/oss.txt` |

## 3. Other libraries in the IDE APK

| Component | Version | Licence | Checked from |
|---|---|---|---|
| sora-editor (code editor) | 0.17.2 | LGPL-2.1 | Maven POM |
| Guava | 31.1-jre | Apache-2.0 | Maven parent POM |
| Gson | 2.9.0 | Apache-2.0 | Maven POM |
| OkHttp / Okio | 4.10.0 / 3.1.0 | Apache-2.0 | Maven POMs |
| libsu (topjohnwu) | 5.0.2 | Apache-2.0 | Maven POM |
| sdklib (Android tools) | 25.3.0 | Apache-2.0 | Maven POM |
| CircleImageView (Henning Dodenhof) | 3.1.0 | Apache-2.0 | Maven POM |
| FilePicker (Angad Singh) | 1.1.1 | Apache-2.0 | Maven POM |
| Spongy Castle / Bouncy Castle | 1.47.0.2 | Bouncy Castle licence (MIT-style) | not re-verified |
| Android Jetpack (AndroidX), Material Components | various | Apache-2.0 | `assets/oss.txt` |
| Material Color Utilities (the `com.google.android.material.color.utilities` classes of Material Components 1.13.0, `app/libs/material-color-utilities-1.13.0.jar`; the IDE computes generated apps' colour roles with it, v1.8.6) | 1.13.0 | Apache-2.0 | Maven POM (`com.google.android.material:material:1.13.0`) |
| Glide, Lottie (upstream jars) | — | Glide: BSD / MIT / Apache-2.0 parts; Lottie: Apache-2.0 | not re-verified |
| StringFog (megatronking), zipalign-java (iyxan23) (upstream jars) | — | — | not re-verified |
| Scratch (block ideas, per Sketchware) | — | MIT | `assets/oss.txt` |

## 4. Fonts and platform files

| Part | Licence | Checked from |
|---|---|---|
| Sora, Manrope, JetBrains Mono (`res/font`, the Obsidian UI) | SIL Open Font License 1.1 | `assets/licenses/fonts-OFL.txt` |
| Material icons "palette", "dark_mode", "wallpaper", "android" (`res/drawable/ob_ic_material3.xml`, `ob_ic_dark_theme.xml`, `ob_ic_dynamic_color.xml`, `np_ic_android.xml`) | Apache-2.0 | not re-verified |
| Droid Sans Mono (`assets/fonts`, upstream) | Apache-2.0 | not re-verified |
| `assets/libs/android.jar.zip`: Android SDK platform android-34 rev 3 `android.jar` (compile-only; upstream bundles one the same way) | Android Software Development Kit License Agreement | not re-verified |
| Test fonts used by `tools/` (fixture 909, layoutlib) | OFL-1.1; created by `make-test-fonts.py`, never committed or shipped | — |

## 5. Libraries a generated app can include (built-in libraries)

AndroidX, Material Components 1.13.0, OkHttp 3, Okio 1, Glide 4, Lottie 3, Gson and the rest of the set in
`assets/libs/libs.zip`: Apache-2.0 for the libraries updated in v1.8.3 and v1.8.4 (read from each POM; per-library list in
`engineering/component-library-inventory.md`). Firebase, Google Play services (Ads, Maps, Auth), OneSignal and Audience
Network are held at upstream's versions and come with their vendors' own terms; check them before publishing an app that
uses them.

## 6. Where the in-app licence texts are

The app's licence screen (`LicenseActivity`) shows `assets/oss.txt`, which lists these additions too; the full aapt2 NOTICE and the font licence are in
`assets/licenses/` inside the APK.
