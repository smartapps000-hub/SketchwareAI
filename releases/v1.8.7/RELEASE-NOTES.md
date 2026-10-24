# Sketchware AI v1.8.7

versionCode 177, versionName `v1.8.7-b2bdd42-minApi26`. Installs over v1.8.6 (same test key).

## New Project screen

The + button on the projects list opens a full screen instead of the old dialog:

- **Names**: app name, package, project name, version code / name (each field with its icon), app icon.
- **Template**: Empty Activity, Material 3 Scaffold (scrolling cards + FAB), Bottom Navigation (three items),
  Navigation Drawer (header in the primary colour + three rows), Tabbed ViewPager (three pages). Each template is
  written as ordinary widgets and blocks, so you edit it like anything you made yourself; the navigation and tab code
  is one *add source directly* block in `initializeLogic`.
- **Colours**: Solar Orange, Material Purple, Material Blue, Material Green, Rose Wine, Custom (colour picker per
  swatch), and *Generate random*: a matching set from one random hue with Material Color Utilities. A preview shows the
  status bar, toolbar, text field, button and FAB in light and (with the dark theme on) dark.
- **Material 3**, **Dark theme**, **Dynamic colour** switches (the last two only with Material 3).
- **Build**: minimum SDK (21–34), target SDK (34–37), Java level (what this build of the app can compile: 1.7, 1.8, 11).
  The build engine row shows what the phone uses: D8 / R8 on Android 8 and newer.

## Fixed (from the v1.8.6 device screenshots)

- **New projects had Material 3 off** in Library Manager ("Needs AppCompat and Design"): a new project's AppCompat
  library was never switched on, and Material 3 is only applied to AppCompat projects. New projects now start with
  AppCompat and Design on. **Projects you already made (NewProject8, NewProject9, …) keep their settings**: open
  Library Manager, switch on *AppCompat and Design*, then *Material 3*.
- **Black area below the widgets** (Telugu 1): that is the VideoView. An empty VideoView with wrap_content takes all
  the height left on the screen and stays black until a video plays (Android's own behaviour), which pushed the
  WebView and the Button to 0 height. Give it a fixed height (e.g. 200dp) or remove it. The design canvas now draws
  it at the same size as the phone does, so you see it there too.
- **Purple toolbar** (Telugu 1): dynamic colour was on in that project, so Android 12+ takes the colours from the
  wallpaper (you confirmed it). Dynamic colour stays off for new projects; switch it off in Library Manager to get the
  project's own colours back.
- **New projects' status bar colour was #0B0B0D** when the IDE was in dark mode (the old dialog took the IDE's own
  colours). The New Project screen uses the preset's colours.
- **Pressed rows flashed a solid colour**: the ripple colour (`colorControlHighlight`) lost its transparency in
  colors.xml. It keeps it now; this changes existing projects' ripples too.
- The project settings dialog no longer fails on a version name without a dot ("1" is read as "1.0").

## Not in this release

The Library Resolver (Maven search, AAR/JAR import, cache), the Direct Java editor (code back to blocks) and the Code
tab are the next release, v1.8.8. Not planned, because they would not work on the phone: a Gradle build engine (it
needs a ~1.5 GB JDK + Gradle on the phone), Kotlin as the blocks' language (blocks generate Java; Kotlin files work
through the Java Manager), Jetpack Compose.

## Checked

On the Android 12 (API 31) ART runtime with this APK's own classes, no phone (details:
`engineering/v1.8.7-verification.md`):

- Run build of all 18 fixtures: 901–905 and 907–918 build; 906 fails as designed and names its block. The five New
  Project templates (914–918) are created by the New Project screen's own code and all generate `AppCompatActivity`
  with the chosen theme.
- Every class of every built app loads and links (only the known JVM-only coroutines classes and, in 902, the two Ads
  WorkManager classes fail, as in v1.8.6).
- Export AAB of 908–913, 915, 917: bundletool 1.11.3 / 1.17.2 / 1.18.3 validate them, universal APKs build,
  `jarsigner` verifies them.
- Export to Android Studio of 902, 908, 911, 913, 915: each builds with Gradle 8.13 / AGP 8.12.
- The New Project screen, the template apps and the VideoView canvas were rendered with layoutlib (Android 14's view
  code on a PC).

**Not run here:** the IDE's screens (New Project screen, Library Manager, design canvas) on a phone, aapt2 on a phone,
installing or starting built apps, dynamic colour on an Android 12+ device, a Play upload.

## Files

The APK (`SketchwareAI-v1.8.7.apk`, 138,357,541 bytes) and the patches zip are on the repository's `binaries` branch as
7z parts (GitHub refuses files over 100 MB); `SHA256SUMS` lists them. Open `.7z.001` in ZArchiver.
