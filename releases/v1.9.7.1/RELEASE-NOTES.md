# Sketchware AI v1.9.7.1

versionCode 198, `v1.9.7.1-6e5d022-minApi26`, 142334438 bytes, sha256
`1ed71d07b1d32c52fff081469cd169613c1bab06ee89dc9548c091c81a7e1292`. Installs over v1.9.7 (same test key).

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.7.1.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.7.1.apk

The next release, v1.9.8, takes versionCode 199.

An audit pass (toolchain parity with Android Studio's current tools, Material 3 in generated apps, Play readiness)
with the small fixes it turned up. The full record is `engineering/v1.9.7.1-toolchain-m3-play-audit.md`.

## In the IDE

- **Orange buttons**: the label stays pure white. The button's orange is a little deeper (#D34115 to #C64F14 instead of
  #E8501C to #F97316) so white text on it is readable by the WCAG AA rule (4.6:1; it was 3.75:1 down to 2.80:1).
  Tiles, the + button, the toolbar and every other orange are unchanged.
- **Selected pills** (Basic / Recent / Event in the property panel, the engine row of New Project): white label on
  the same deeper orange. The orange tiles of the main screen and the dialogs are unchanged.
- **Block guide**: white labels on a slightly deeper teal and brown, for the same reason.

## In the apps you build

- **Storage permissions**: for a project that targets Android 13 or newer, the old READ / WRITE storage permissions of
  File blocks and components are declared for Android 12L and older only. Android 13+ never grants them to such an
  app, so nothing changes in how the app runs. File blocks still reach the app's own folders only on new Android
  versions; photo-picker and "save to Downloads" blocks are the planned fix.
- **http:// addresses**: the app now also carries a network security configuration that permits what
  `usesCleartextTraffic` permits. Android 17 announced that the old attribute goes away; this is the replacement
  Google names. A configuration you set yourself in the manifest editor wins.

- **Camera**: an app that uses the camera now says the camera is not required
  (`<uses-feature android:name="android.hardware.camera" android:required="false"/>`), so Google Play also offers it
  to devices without a camera. If your manifest already names the camera feature, yours is kept. One thing to know: on
  such a device the "take picture" block has no camera app to open, so check before you use it.

## Export to Android Studio

- The exported manifest no longer names a `DebugActivity` class the export does not contain.
- Eleven Android Studio lint errors that came from generated files are gone (ten by annotations only, the apps run
  the same code; one by the camera line above). On the test project lint went from 14 errors to 3: two belong to that
  project's own layout, one is an indentation complaint in a generated helper method.
- An exported project still does not pass lint clean; it compiles and builds an APK with Gradle 9.8.2 / AGP 9.5.0.

## Checked

Release checks on the APK; eleven fixture builds (ten projects and one bundle) with 0 warnings each, the open / save lane on ten projects, the
bundle of fixture 908 validated by bundletool 1.18.3 and its export built by Gradle; renders of the buttons, the pills
and the guide; two separate code reviews. Nothing was run on a phone.

## If something is wrong

Send the Compile log (Copy) for a build problem. For the button colour, a screenshot is enough: the six colour values
are in `res/drawable*/ob_cta*.xml` and `ob_safe_cta*.xml`.
