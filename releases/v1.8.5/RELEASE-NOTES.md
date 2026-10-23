# Sketchware AI v1.8.5

versionCode 175, versionName `v1.8.5-3bdb3d1-minApi26`. Installs over v1.8.4 (same test key).

## Changes

**Building apps on the phone**
- D8/R8 8.11.18 with core library desugaring (desugar_jdk_libs_nio 2.1.5): `java.time`, streams, `java.nio.file` and
  newer collection APIs work on Android versions that lack them, as with Android Studio's desugaring. Only the classes a project uses are added. If this
  step fails, the build continues without it and says so; the next build extracts the library again.
- aapt2 2.20 built from AOSP `android-16.0.0_r4` for all four ABIs, 16 KB-aligned. Same resource tables as 2.19.
- App bundles: bundletool 1.18.3; native libraries stored uncompressed with 16 KB page alignment; the AAB's JAR signature
  uses SHA-256 (JDK's `jarsigner` now verifies it; v1.8.4's SHA-1 signature counted as unsigned). APK signing unchanged.
- Export to Android Studio writes an AGP 8.12 / Gradle 8.13 project, including the widget libraries (with their R classes)
  and the FCM, Google sign-in, OneSignal and Audience Network dependencies the export never listed.

**Material 3 projects** (new projects since v1.8.4)
- Plain Buttons become filled Material 3 buttons; a background set from code is shown as set.
- Switches become `MaterialSwitch`, CheckBoxes and RadioButtons their Material versions.
- Plain EditTexts become outlined text fields; `setHint` sets the field's label; the field is never narrower than its hint.
- Default text colours follow the theme. The theme stays light.
- Widgets the project's own code treats as framework widgets (a `Switch` variable, thumb/track images, moving a field or
  changing its layout params, a custom checkbox image) stay as they were.
- The design canvas draws the Material 3 look (not run here). It doesn't apply the code rule above: a widget kept plain
  because of the project's code still shows the Material look on the canvas.

**The IDE itself**
- Built with AGP 8.12 / compileSdk 34 (was AGP 7.3.1 / compileSdk 30).
- Licence screen and `CREDITS.md` list the components this fork adds; the aapt2 licence notice matches the android-16 build.

## Checked

On the Android 12 runtime harness (no framework, not a device) with this APK: full Run builds of fixtures 901–912, Export
AAB of 908–912, Export to Android Studio of 902/908/911. Forced failures of the desugaring step. layoutlib renders of the
Material 3 widgets on light and night device settings. Details: `engineering/v1.8.5-verification.md`.

**Not run here:** the IDE's screens, aapt2/D8 on a phone, installing or starting built apps, a Play upload.

## Files

The APK (138,306,080 bytes; also as five 7z parts) and the patches zip are release assets: GitHub refuses files over
100 MB. `SHA256SUMS` lists the APK and its parts; the patches zip's sha256 is given with the release (it cannot list
itself).
