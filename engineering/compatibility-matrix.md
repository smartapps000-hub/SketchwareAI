# Device / API compatibility matrix

Four different things, tracked separately. A row says "tested" only for what was actually run.

## 1. Sketchware AI itself (the IDE)

| Item | Value |
|---|---|
| minSdk | 26 (flavor minApi26, the one that is shipped); a minApi21 flavor compiles but isn't shipped |
| targetSdk | 34 |
| compileSdk | 34 since v1.8.5 (AGP 8.12.0, Gradle 8.13, JDK 17; before: 30 with AGP 7.3.1). APIs above 34 are called by reflection |

## 2. Where the IDE's build engine has been run

| Runtime | What ran | Result |
|---|---|---|
| Android 12 (API 31) ART, x86_64, interpreter, **no Android framework** | the APK's own classes: block-to-code generator (Jx/Hx/Fx), source map, ECJ compile step (`Dp.compileJavaCode`), auto-fix, library resolver, D8 on a downloaded library | passes the fixtures in test-fixtures.json |
| JVM 17 (desktop) | same engine classes, larger corpora (false-positive scans) | passes |
| layoutlib (Paparazzi) | layouts of the diagnostic sheet and dialogs; `BlockFocus` | rendered / asserted |
| Any real phone or emulator, by the developer of this fork | nothing | **not tested** |
| User's phone | every release is installed and used by the user | v1.8.0: project #644 built, installed and started; #623 failed in AAPT2 (damaged PNG); #606 failed in Java compile (library without classes.jar). Android version not recorded here |
| Network from the Android runtime here | not possible: the sandbox kernel has no IPv6 and Android opens IPv6 sockets | downloads are run on JVM 17 with the same classes |

Not run anywhere off-device: Activities (logic editor, compile log screen, source viewer), installation. From v1.8.3 the Android 12 ART harness (`tools/art-harness`) also runs AAPT2 (the APK's x86_64 aapt2), Kotlin, DEX merge, APK packaging and signing of the Run build.

"Tested on the Android 12 runtime" means only the rows above. It does not mean the IDE supports only Android 12, and it says nothing about Android 8-11 or 13+.

## 3. Projects built with the IDE (generated APKs)

| Item | Where it comes from |
|---|---|
| minSdk / targetSdk | per project: Project Configuration (defaults in `ModernProjectDefaults`) |
| compile platform | bundled android.jar = Android 14 (API 34) since v1.8.4 (API 33 before) unless a custom android.jar is set in Build Settings; APIs newer than 34 can't be called from project code |
| Java level | ECJ 3.26 with -11, falling back to 1.8; D8/R8 8.11.18 (3.3.75 before v1.8.5) with core library desugaring (desugar_jdk_libs_nio 2.1.5) |
| Libraries | v1.8.4: Material 1.13.0, AppCompat 1.7.1, AndroidX releases that build against API 34 (core 1.13.1, fragment 1.8.9, lifecycle 2.8.7 ...), Kotlin 1.8.22, Glide 4.16, Gson 2.14, OkHttp 3.14.9; Firebase/Play libraries still the 2021-2022 releases; local libraries as installed |
| Theme | v1.8.4: new projects Material 3 AppTheme (Theme.Material3.Light.NoActionBar); older projects keep MaterialComponents (Bridge). v1.8.5: same light theme; Material 3 widgets written as tags (SketchButton, MaterialCheckBox/RadioButton, MaterialSwitch, outlined text fields); no DayNight |
| App bundles | v1.8.5: bundletool 1.18.3, native libraries uncompressed with 16 KB page alignment; JAR signature with SHA-256 digests (BundleSigner) |
| Export to Android Studio | v1.8.5: AGP 8.12.0 / Gradle 8.13 project, compileSdk 36, targetSdk as in the project |

Whether a generated APK runs on a given Android version depends on that project's minSdk/targetSdk, the APIs and libraries it uses, and platform behaviour changes. A successful compile on one runtime proves none of that.

## 4. Generated APKs actually run

| Device API | Result |
|---|---|
| (none by the developer of this fork) | no generated APK has been installed or started off-device |
| user's phone (API not recorded) | project #644's APK installed and started (v1.8.0) |

## v1.8.2 (4 Oct 2026)
- Primary device baseline named by the owner: Android 15 (API 35). Results on it are reported by the owner; none are
  recorded here yet for 1.8.2. Android 16 / 17: NOT TESTED. Engine checks in the sandbox ran on an API 31 ART runtime.
- See `v1.8.2-verification.md`, `android15-audit.md`, `android15-device-test-plan.md`.

## v1.8.3 (21 Oct 2026)
- Full Run build of fixture projects 901-907 on the Android 12 (API 31) ART harness with the v1.8.3 APK; results in
  `v1.8.3-verification.md`. No device run by the developer of this fork; Android 15 results come from the owner.

## v1.8.4 (22 Oct 2026)
- Projects compile against android-34; aapt2 2.19 for all four ABIs, 16 KB-aligned. Full Run build of fixtures 901-910 and
  Export AAB of 908/909/910 on the Android 12 (API 31) ART harness with the final APK; layoutlib (Android 14 text and view
  code on the JVM) renders of the Material 3 theme and of Telugu text with six fonts. Results in `v1.8.4-verification.md`.
- No device run by the developer of this fork. Android 15 / 16 KB-page devices: NOT TESTED. Play upload of an AAB: NOT TESTED
  (the AAB's JAR signature uses SHA-1 digests, see the verification file).

## v1.8.5 (22-23 Oct 2026)
- The IDE builds with AGP 8.12 / compileSdk 34. Projects: D8/R8 8.11.18 with core library desugaring, bundletool 1.18.3,
  aapt2 2.20 (android-16.0.0_r4) for all four ABIs, Material 3 widgets. Full Run build of fixtures 901-912, Export AAB of
  908/909/910/911/912 and Export to Android Studio of 902/908/911 on the Android 12 (API 31) ART harness with the final APK;
  L8 and D8 desugaring failures forced on the harness; layoutlib renders of the generated Material 3 widgets on light and
  night device settings. Results in `v1.8.5-verification.md`.
- No device run by the developer of this fork. Android 15 / 16 KB-page devices and a Play upload of an AAB: NOT TESTED.
  The AAB's JAR signature now verifies with JDK 17's jarsigner (SHA-256).

## v1.8.6 (23 Oct 2026)
- Generated Material 3 apps: colour roles from the project colour, DayNight for new projects, dynamic colour opt-in,
  tinted pill buttons, 8dp outlined fields, label measure fix. Run build of fixtures 901-913, Export AAB of 908-913 and
  Export to Android Studio of 902/908/911/913 on the Android 12 (API 31) ART harness with the final APK; layoutlib renders
  of fixture 913's widgets (light and night), of the outlined field label (font scale 1.0/1.3, bold text) and of the
  Build Settings sheet. Results in `v1.8.6-verification.md`.
- No device run by the developer of this fork; no Android emulator (no hardware virtualisation on the build machine).
  Dynamic colour needs Android 12+ with a wallpaper: NOT TESTED. Android 15 / 16 KB-page devices, Play upload: NOT TESTED.

## v1.8.7 (23 Oct 2026)
- New Project screen; new projects get AppCompat on, so Material 3 applies to projects made on the phone. Projects made by
  the screen's own code (`NewProjectSetup.create`: fixtures 914-918, one per template) and the regression fixtures
  901-913 built on the Android 12 (API 31) ART harness with the final APK; layoutlib renders of the New Project screen,
  of the five generated template apps (light and night) and of an empty VideoView in the owner's layout. Results in
  `v1.8.7-verification.md`.
- The New Project screen itself (an Activity), the design canvas VideoView and the template apps on a phone: NOT TESTED
  here (no device, no emulator).

## v1.8.8 (24 Oct 2026)
- Java editor (generated Java back to blocks): `JavaEditSession` / `JavaToBlocks` ran on the Android 12 (API 31) ART
  harness with the final APK's classes: fixture 919's edit series (stored, refused, regenerated and compared, then built)
  and an unchanged open + save of every suite project's activities. Library Resolver: POM resolution and install of
  Retrofit 2.9.0 + converter-gson (fixture 920, POMs and archives from a cache downloaded from Maven Central; the live
  repositories were read by `tools/jvm-tests/MavenResolverTest` on JDK 17), built with the built-in OkHttp and Gson.
  Regression fixtures 901-918 built again. Results in `v1.8.8-verification.md`.
- The Java editor, Library Resolver and Code tab screens (Activities, sora-editor), Maven Central search and downloads on a
  phone's network, Dx on Android 7.1 and older (minApi21 build): NOT TESTED here (no device, no emulator). Layouts
  rendered with layoutlib.
