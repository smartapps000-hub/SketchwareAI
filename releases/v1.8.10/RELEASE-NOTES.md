# Sketchware AI v1.8.10

versionCode 180, versionName `v1.8.10-23820dd-minApi26`. Installs over v1.8.9 (same test key). A fix release.

## Fixed: design editor crash in projects made from a New Project template

"Sketchware AI stopped: ClassCastException: android.widget.FrameLayout$LayoutParams cannot be cast to
android.widget.LinearLayout$LayoutParams" when the design editor showed a project made from the **Material 3 Scaffold**
template (Android 15, owner's report, 26 Oct 2026).

Cause: the templates (since v1.8.7) stored the layout inside each card with the card as its parent type. The design
editor draws a card as a LinearLayout but gives a view whose stored parent type isn't 0 FrameLayout sizes, so measuring
the card failed. Views dropped by hand are stored with 0, which is why only template cards crashed.

- The design editor converts such sizes itself (size, margins and gravity kept): projects already made from the template
  open again, nothing to repair by hand.
- The templates now store what the editor stores.

## Fixed: an EditText dropped into a TextInputLayout (Material 3 projects)

The editor stores such a field with parent type 0 (the TextInputLayout is drawn as a LinearLayout), so the Material 3
rewrite didn't see that it was already inside one and would have put a second outlined box around it. It now checks
the parent itself: the field stays as you placed it.

## Checked

On the Android 12 (API 31) ART runtime with this APK's own classes and with layoutlib (Android 14's view code on a PC),
no phone (details: `engineering/v1.8.10-verification.md`):
- the crash reproduced with Android's own LinearLayout (same `LinearLayout.java:1194`) and gone with the fix;
- Run build of the templates 914-918 and the Material 3 projects 908, 912, 913 (913 now has a field inside a
  TextInputLayout: one box); LoadAll of every built app; Export AAB and Export to Android Studio of 915; every one of
  these projects opens and saves unchanged in the Java editor.

**Not run here:** the design editor itself (an Activity). Please open the project that crashed (or a new Material 3
Scaffold project) in the design editor.

## Files

`apk/Sketchware_AI_v1.8.10.apk` (138476161 bytes, sha256 `46c46680…ffc21a5`) on the repository's `binaries` branch (one file, Git LFS).
