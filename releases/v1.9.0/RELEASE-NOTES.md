# Sketchware AI v1.9.0

versionCode 190, versionName `@@VERSIONNAME@@`. Installs over v1.8.13 (same test key). Variables from your Java code,
a readable source sheet you can edit, faster repeat builds, Wear OS / TV targets, Material Symbols, Android 17 SDK.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.0.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.0.apk

@@SIZE@@ bytes. Not a `raw.githubusercontent.com` link: that one gives the Git LFS pointer.

## Java editor: variables instead of add source

- `String userName = "Ravi";`, `double total = 2.5;`, `boolean shown = true;` become **project variables** (String,
  Number, Boolean) with their **set** blocks. Lines after them that use the name get variable blocks
  (`textview1.setText(userName);` → setText with the userName block).
- An assignment to a name you never declared (`rating = 4.5;`, `label = edittext1.getText().toString();`) becomes a
  variable of the type its value shows.
- `int count = 3;`, an Intent, a library class: a **custom variable** (`private int count`) and the assignment stays code.
  An int is not turned into a Number variable on purpose: Number is a `double`, so `String.valueOf(count)` would show
  `3.0`.
- A local stays a local (and the save tells you why) when making it a variable would change what the code does: the
  same name declared twice, a listener opened after it that reads it, a MoreBlock that calls itself.
- Decimal numbers appear as `2.5d` in the generated code: that is how the Number blocks write them.

## Source sheet (`</>` in the logic editor)

- Readable on the dark sheet: Material 3 dark colours, every kind of word at least 5:1 contrast (keywords were dark blue
  and names grey before).
- **Edit** shows the event's code; change it or paste code, **Apply** turns it into blocks with the same rules as the
  Java editor (blocks, variables, add source for the rest) and opens the event again with the new blocks. Code the
  blocks can't give back exactly is refused with its line, nothing changed.

## Faster repeat builds

Build Settings → **Build cache** (on). A Run build keeps the compiled R classes (a Material 3 project has 28 R files of
2.5 MB each, compiled and dexed every build until now) and the compiled resources of the project and its local
libraries, and reuses them while they are unchanged. On the test machine a repeat build of a Material 3 project went
from 324 s to 36 s with identical output. Exports (APK, AAB, Android Studio) always build everything.

## Target device

Project Configuration → **Target device**: Phone, Foldable, Tablet, Wear OS, Android TV.
- Wear OS: the manifest gets `android.hardware.type.watch` and the standalone Wear meta-data.
- Android TV: leanback (not required, the app still installs on phones), the TV launcher entry, the app icon as banner.
- Design editor → **Device** button: preview the screen at foldable 700×840 dp, tablet 1280×800 dp, Wear OS round
  227×227 dp (454×454 px), TV 960×540 dp (1920×1080 px), with the Material 3 size class.

## Material Symbols

Image Manager → icons: a row **2014 icons / Outlined / Rounded / Sharp**. 3,937 Material Symbols per style, added in
black, grey or white like the other icons.

## Toolchain

- Projects compile against **Android 17 (API 37)** (was API 34). Android 17's SDK dropped `FingerprintManager`; it is
  kept in the bundled android.jar so fingerprint code in add-source blocks still compiles.
- D8 / R8 **8.13.25** (was 8.11.18).
- Material Components 1.13.0 and ConstraintLayout 2.2.2 were already built in (newer than 1.12.0 / 2.2.0).

## Checked

@@CHECKED@@

**Not run here:** the source sheet, the design canvas sizes and Device button, Project Configuration's Target device
row, the icon screen (Activities), a generated app on a watch or TV, build times on a phone. Please try on the phone:
1. In an event's `</>` sheet: Edit, add `String greeting = "Hi";` and `textview1.setText(greeting);`, Apply: the event
   shows a set block and setText with the greeting block; the Variables list has greeting.
2. Run the same project twice without changes: the second build should be clearly faster (Compile log, Build map).
3. Project Configuration → Target device → Wear OS, then the design editor's Device button.

## Files

`apk/Sketchware_AI_v1.9.0.apk` (@@SIZE@@ bytes, sha256 `@@SHA@@`) on the repository's `binaries` branch (one file,
Git LFS).
