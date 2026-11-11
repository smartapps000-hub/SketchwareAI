# Sketchware AI v1.9.7.1

versionCode 198, `v1.9.7.1-18ec226-minApi26`, 142333397 bytes, sha256
`34532a4847957b919f72394dbe4781d4eafd2f5ad6a825de72e72b8a00bff88e`. Installs over v1.9.7 (same test key).

**State: built and tested, not published.** The commits are on branch `ccr-c120dc8b-d2wodl`; the APK is not on the
`binaries` branch yet (the session that made it could only push its own branch). The next release, v1.9.8, takes
versionCode 199.

An audit pass (toolchain parity with Android Studio's current tools, Material 3 in generated apps, Play readiness)
with the small fixes it turned up. The full record is `engineering/v1.9.7.1-toolchain-m3-play-audit.md`.

## In the IDE

- **Orange buttons**: the label stays pure white. The button's orange is a little deeper (#D34115 to #C64F14 instead of
  #E8501C to #F97316) so white text on it is readable by the WCAG AA rule (4.6:1; it was 3.75:1 down to 2.80:1).
  Tiles, the + button, the toolbar and every other orange are unchanged.
- **Block guide**: white labels on a slightly deeper teal and brown, for the same reason.

## In the apps you build

- **Storage permissions**: for a project that targets Android 13 or newer, the old READ / WRITE storage permissions of
  File blocks and components are declared for Android 12L and older only. Android 13+ never grants them to such an
  app, so nothing changes in how the app runs. File blocks still reach the app's own folders only on new Android
  versions; photo-picker and "save to Downloads" blocks are the planned fix.
- **http:// addresses**: the app now also carries a network security configuration that permits what
  `usesCleartextTraffic` permits. Android 17 announced that the old attribute goes away; this is the replacement
  Google names. A configuration you set yourself in the manifest editor wins.

## Export to Android Studio

- The exported manifest no longer names a `DebugActivity` class the export does not contain.
- Ten Android Studio lint errors that came from generated helper files are gone (annotations only; the apps run the
  same code). On the test project lint went from 14 errors to 4: two belong to that project's own layout, one is an
  indentation complaint in a generated helper method, one asks for a camera feature tag (a decision about who may
  install the app, left to you).
- An exported project still does not pass lint clean; it compiles and builds an APK with Gradle 9.8.2 / AGP 9.5.0.

## Checked

Release checks on the APK; eleven fixture builds with 0 warnings each, the open / save lane on ten projects, the
bundle of fixture 908 validated by bundletool 1.18.3 and its export built by Gradle; renders of the buttons and the
guide. Nothing was run on a phone.

## If something is wrong

Send the Compile log (Copy) for a build problem. For the button colour, a screenshot is enough: the six colour values
are in `res/drawable*/ob_cta*.xml` and `ob_safe_cta*.xml`.
