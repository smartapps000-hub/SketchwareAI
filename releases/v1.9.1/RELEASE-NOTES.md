# Sketchware AI v1.9.1

versionCode 191. Installs over v1.9.0 (same test key). Fixes for the problems in your 1 Nov screenshots and recordings.

## Download

- https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/Sketchware_AI_v1.9.1.apk
- https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/Sketchware_AI_v1.9.1.apk

## Build: "Package APK" failed, false library warnings

- **Package APK**: when two libraries hold the same file outside META-INF (a newer kotlinx-coroutines next to the built-in
  one both have `DebugProbesKt.bin`; a newer kotlin-stdlib and the built-in one both have `kotlin/*.kotlin_builtins`;
  two OkHttp versions both have `publicsuffixes.gz`), the packager stopped the whole build. Now the APK gets the first
  copy and the Compile log names the files as a warning.
- **"Local library 'lifecycle-runtime-ktx-android_V_2.9.4' has no classes.dex … Download it again"**: those -ktx
  libraries (lifecycle-runtime-ktx 2.9.4, lifecycle-viewmodel-ktx 2.9.2, savedstate-ktx 1.3.1) contain no classes at all
  since AndroidX moved their code into the main libraries, so they need no DEX. The three warnings are gone.
- A failed build now repeats the error's first lines under the Build map (`failed with: …`), so a screenshot of the
  log's end shows the cause.

## Java editor and `</>` sheet: more blocks, no empty slots

- `true` / `false` go into a slot as the Operator palette's **true / false blocks**. Before, the slot looked empty (your
  `set Boolean isNative37 to [ ]`). Events saved that way by v1.8.9–v1.9.0 get the block when you open them in the logic
  editor; the generated code is the same.
- Values become the blocks that write them: Operator (`+ - * / %`, `< > ==`, `&&`, `||`, `!`), Math (`Math.pow`,
  `Math.sqrt`, `Math.max` …, getDip, display width/height), text (`String.valueOf`, `.length()`, `.concat`,
  `.contains`, `.equals`, `.substring`, `.replace`, `.trim` …), List (`list.add(…)`, `list.get((int)(…))`,
  `.size()`, `.contains`, `.indexOf`, `.clear()`), Map (`put`, `get`, `containsKey` …), and widget getters
  (`isChecked()`, `getText().toString()` …). Example: `done = (score > 3) && !done;` becomes set Boolean with an `and`
  block holding `>` and `not`.
- `Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();` becomes the **Toast** block (same short
  toast; its code is `SketchwareUtil.showMessage(getApplicationContext(), text);`). A LENGTH_LONG toast stays code.
- Each block is used only when it writes back **exactly** your line, so a few lines still stay add source:
  - Nested operators: the blocks write `score + (1 + 2)`, so `score = score + 1 + 2;` stays code.
  - `"Hi " + name`: Java's `+` on text has no block. The join block writes `"Hi ".concat(name)`, which fails when name is
    null, so it isn't swapped in silently.
  - `int` locals: `compileSpeed = 2;` stays code, because a Number variable is a double and `String.valueOf` would
    show `2.0`.
- **FileUtil** lines become File blocks only if the screen already has a File block. A File block makes the app ask for
  storage permission (manifest and onCreate), so converting your code would add a permission behind your back. The save
  says so when it leaves them as code.

## Export screen

- Sign APK, Android Studio and App Bundle each show a short line under the title saying what they make: release APK
  signatures v2 + v3; Gradle 9.8.2 / AGP 9.5.0; bundletool 1.18.3 with 16 KB alignment.
- The Local Path and Send rows grow with the phone's font size: the path no longer runs under the Send button, and the
  text keeps a margin on the right.
- The App Bundle button no longer moves the Android Studio button (they shared one layout object).
- The Sign APK dialog no longer says "only signing scheme V1". The export signs v2 + v3 (+ v1 below min SDK 24), as it
  has since v1.8.2.

## Checked

See `engineering/v1.9.1-verification.md`.

## Files

`apk/Sketchware_AI_v1.9.1.apk` on the repository's `binaries` branch (Git LFS).
