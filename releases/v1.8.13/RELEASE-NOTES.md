# Sketchware AI v1.8.13

versionCode 183, versionName `v1.8.13-0dab18a-minApi26`. Installs over v1.8.12 (same test key). Library Resolver without popups, no
version flip-flops, and two Java editor fixes.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.8.13.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.8.13.apk

138,506,845 bytes. Not a `raw.githubusercontent.com` link: that one gives the 134-byte Git LFS pointer.

## Library Resolver: no popup when it worked

- **Installed, already installed, switched on: no dialog.** A short Toast ("ESCPOS Thermal Printer Android 3.3.0
  enabled for this project"), the project's switch on, and a green **✓ Installed** on the library's card.
- **Something to check** (a library it needs couldn't be installed, archives without Android code left out, bundled
  jars): the same Toast, plus one line above the list with **Copy log** and **×**. No dialog.
- **A dialog only when the library itself couldn't be installed** (for example a version that is on none of Google
  Maven, Maven Central and JitPack, like `AestheticDialogs:1.3.100.test`). It keeps Copy log.
- Names, not folders: "ESCPOS Thermal Printer Android 3.3.0" or `com.google.zxing:core:3.4.0` instead of
  `core_V_3.4.0`. The folder names are only in Copy log's details.
- Delete, Check for updates and Dex end in a Toast instead of a dialog. Switching a library on by hand switches its
  other versions off and names them ("This project now uses … instead of …").

## The highest version wins

`animal-sniffer-annotations` 1.28 was switched off when a library needing 1.23 was installed ("Switched off (other
version)"), and back again on the next install.
- A library that is on is never replaced by a **lower** version of itself; the lower one is installed but stays off.
- A library the one you install needs uses the **higher version that is on** in the project instead of downloading
  the lower one.
- Installing a library lower than the version on switches nothing on (not its dependencies either); the Toast says
  the newer version stays on. A higher one replaces the lower one.
- The Required tab counts a need as met when a higher version of that library is on.

## Java editor

- `textview1.setText(edittext1.getText().toString());` becomes the **setText** block with the **getText** block
  inside it (no add source block). `X.getText().toString()` works in the text slot of the blocks the editor maps
  (setText, Toast, setTitle, setHint …).
  `textview1.setText(edittext1.getText())` without `.toString()` stays an add source block.
- **"Not saved: the blocks wouldn't give exactly this code" after `edittext1.setHint("…");`** in a Material 3 project
  (your MainActivity screenshot, line 71) is fixed. Material 3 projects write a hint on an outlined field as
  `SketchTextField.hint(edittext1, "…");`; the editor compared your line with that and refused. It now compares them
  the way the generator writes them, and the line becomes the setHint block. A setHint inside an if stays an add
  source block, stored as you typed it.

## Checked

On this APK's own classes on the Android 12 (API 31) ART runtime (no phone; details: `engineering/v1.8.13-verification.md`):
- your MainActivity lines typed unindented into a Material 3 project: saved, setHint and getText become blocks, a
  setHint inside an if is stored as typed, saving again changes nothing, the project builds (8/8 checks, 14 steps);
- the Java editor fixture 919 with the nested getText: 61/61 checks; 10 projects open and save unchanged;
- highest version wins (no downgrade, the higher version on reused, a lower root switched nothing on, a higher
  replaces the lower): 9/9 checks with library folders made for the test;
- Library Resolver install + Run build (920), ESCPOS (922), Material 3 (908): 14 steps, 0 warnings each;
- a build repair is still offered for an add source block that sets an outlined field's hint (923);
- the notice line and the green ✓ Installed card rendered with layoutlib;
- four separate reviews of the code; all their findings fixed or recorded.

**Not run here:** the Library Resolver and Java editor screens themselves (Activities) and your phone's network.
Please try on the phone:
1. + Add `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0` in a project: one Toast, switch on, green ✓ Installed,
   no dialog.
2. Your MainActivity edit (setText, setHint, `String userNotes = edittext1.getText().toString();` …): Save works.
3. `textview1.setText(edittext1.getText().toString());` in an event: Save, then the event shows setText with getText
   inside.

## Files

`apk/Sketchware_AI_v1.8.13.apk` (138506845 bytes, sha256 `7e5e0120…698ad746`) on the repository's `binaries` branch (one file, Git LFS).
