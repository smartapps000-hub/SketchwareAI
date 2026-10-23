# Sketchware AI v1.8.6

versionCode 176, versionName `v1.8.6-9bc690c-minApi26`. Installs over v1.8.5 (same test key).

## Fixed (from the v1.8.5 device test)

- **Build Settings**: the Cancel / Save row covered the last option ("Enable debug logcat logs ...") when the options had
  to scroll. The dialog's root no longer lets the scrolling list draw outside its area.
- **Outlined text field label cut** ("Telugu language title creat…"): a wrap_content field is now measured with the
  label's own letter spacing and weight (including the phone's bold-text setting), plus 2dp.
- **Library Manager** said "Material You theme (light and dark)" while v1.8.5's theme was light only. The text now says
  what the switch does.

## Material 3 in generated apps

- **Colour system**: the theme's Material 3 colour roles (primary/secondary/tertiary containers, error, background,
  surface and its five container levels, surface variant, inverse colours, outline) are computed from the project's
  primary colour with Material Color Utilities, the library Material Components and the Material Theme Builder use. Your
  own colours stay exact: toolbar (primary), accent, status bar.
- **Dark theme**: new projects follow the phone's dark mode (Theme.Material3.DayNight, dark colour roles in
  values-night). Library Manager → *Dark theme* turns it on or off per project. Material 3 projects made with v1.8.4 /
  v1.8.5 stay light until you switch it on.
- **Dynamic colour** (Library Manager → *Dynamic colour*, off by default): on Android 12+ the app takes its colours from
  the phone's wallpaper instead of the project colours.
- **Buttons with a background colour** keep the Material 3 pill shape (filled with that colour); the label turns white
  or near-black for contrast when it has the default colour. A button whose background your code reads stays as before.
- **Outlined text fields** have 8dp corners.
- **Design canvas**: outlines, label colours and the screen background use the project's colour roles; 8dp field
  corners; buttons with a background colour drawn as pills. The canvas shows the light look.

## Repository

- `.gitignore` no longer hides the source packages named `build` (`mod/hey/studios/build`, `mod/jbk/build`); they and
  the public test key are tracked, so a clone builds as it is.
- Licence: the code this fork adds is GPL-3.0 (`COPYING`), the author's choice. Upstream files keep upstream's terms
  (`LICENSE`). See README and CREDITS.

## New-project defaults

Target API 37, min API 21, D8, Java 11, Material 3 with dark theme. Core library desugaring and the 16 KB aapt2 apply to
every project. Dx and Java 1.7 / 1.8 stay selectable in Build Settings for old and imported projects.

## Checked

On the Android 12 runtime harness (no framework, not a device) with this APK: Run builds of fixtures 901–913 (913 is
new: dark theme, dynamic colour, buttons with their own colours, the long-hint field), Export AAB of 908–913 (validated
with bundletool 1.11.3 / 1.17.2 / 1.18.3, `jar verified.`), Export to Android Studio of 902/908/911/913 built with
Gradle 8.13. layoutlib renders: 913's widgets in light and dark, the field label at font scale 1.0/1.3 and bold text,
the Build Settings sheet before and after. A separate review of the code found 1 serious problem (Library Manager
switches had no effect until the project was reopened) and 3 small ones; all fixed or noted before this build.
Details: `engineering/v1.8.6-verification.md`.

**Not run here:** the IDE's screens (Library Manager, Build Settings, design canvas) on a phone, aapt2 on a phone,
installing or starting built apps, dynamic colour on an Android 12+ device, a Play upload.

## Files

The APK (`SketchwareAI-v1.8.6.apk`, 138,320,721 bytes) and the patches zip are on the repository's `binaries` branch as
7z parts (GitHub refuses files over 100 MB); `SHA256SUMS` lists them. Open `.7z.001` in ZArchiver.
