# Sketchware AI

A fork of **Sketchware Pro v6.4.0-rc05** (package `com.sketchware.ai`) for building Android apps from blocks on a phone.
Current release: **v1.8.13** (versionCode 183). Maintained by AiTechApps. Not affiliated with Sketchware
(besome) or with the Sketchware Pro team.

## Licence status (read this first)

- **This project is not open source.** It is built on Sketchware Pro, which its own [LICENSE](LICENSE) calls
  *source-available, not open-source*: most of its code is decompiled from the original Sketchware app, and the licence
  allows forks for personal use. Every file this fork inherits keeps that status.
- **GPL-3.0 parts** (as stated in the upstream LICENSE): the `kotlinc` module, taken from
  [CodeAssist](https://github.com/tyron12233/CodeAssist), and the `minApi26` build flavour, a derivative of it.
  The APK this fork ships is the `minApi26` flavour, so those parts are GPL-3.0.
- **Third-party components** (AOSP aapt2 and libraries, R8/D8, desugar_jdk_libs, bundletool, apksig, ECJ, kotlinc,
  fonts, and the libraries generated apps can use) keep their own licences: Apache-2.0, BSD-3-Clause, EPL-2.0,
  GPL-2.0 with Classpath Exception, OFL-1.1 and others. Each one is listed in [CREDITS.md](CREDITS.md).
- **The code this fork added** (`com.sketchware.ai.*`, `tools/`, `engineering/` and its changes to upstream files) is
  licensed by its author, AiTechApps, under the **GPL-3.0** ([COPYING](COPYING)). That covers only this fork's own
  work: upstream files keep upstream's terms, so the repository as a whole is still not open source.

This is a summary of what the files say, not legal advice.

## What "AI" means here

The name is the product name. **There is no AI model, LLM or cloud service in the app or in its builds.** The build
auto-fix and Build Doctor are rule-based and run offline on the phone. Network is used only to download Maven libraries
(Google Maven, Maven Central, JitPack).

## Shipped (v1.0 – v1.8.13)

Everything in this section is in the released APK and was exercised as described under *How it is tested*.

| Area | What it does | Since |
|---|---|---|
| On-phone toolchain | aapt2 2.20 built from AOSP `android-16.0.0_r4`, static, for arm64-v8a, armeabi-v7a, x86_64, x86, 16 KB-aligned | v1.0 (64-bit), v1.8.4 (all ABIs), v1.8.5 (android-16) |
| | Projects compile against the bundled Android 14 (API 34) `android.jar`; ECJ 3.26 (Java 7 to 11) | v1.8.4 |
| | D8 / R8 8.11.18 with core library desugaring (`desugar_jdk_libs_nio` 2.1.5): `java.time`, streams and `java.nio.file` on older Android versions. If that step fails, the build continues without it and says so | v1.8.5 |
| | Embedded Kotlin compiler 1.7.10; bundletool 1.18.3 (AAB export, 16 KB page alignment for native libraries) | upstream / v1.8.5 |
| Build repair (offline, rule-based) | Unreachable code, missing imports, missing or broken libraries (restore, exact Maven download), damaged PNGs, missing images. Repairs never move a line. Repairs inside *add source directly* blocks can be stored back into the block | v1.7.0 – v1.8.2 |
| Diagnostics | Source map (a compile error points to its block, with *Open block*), build map (time and status per step), Build Doctor diagnosis, build history | v1.8.0 – v1.8.2 |
| New Project screen | Full screen in cards: names with icons, app icon, version; templates (Empty Activity, Material 3 Scaffold, Bottom Navigation, Navigation Drawer, Tabbed ViewPager) written as ordinary views and blocks you can edit; colour presets and a random harmonious set (Material Color Utilities) with a live light/dark preview; Material 3, dark theme and dynamic colour; minimum / target SDK and Java level. Shows the phone's build engine (D8 / R8 on Android 8+) | v1.8.7 |
| New-project defaults | AppCompat and Design on (Material 3 needs it), target API 37, min API 21, D8, Java 11 (core library desugaring and the 16 KB aapt2 are always on), Material 3 theme with dark theme. Existing projects keep their stored settings | v1.2 – v1.8.7 |
| Material 3 | Material 3 AppTheme whose colour roles (containers, tonal surfaces, outlines, tertiary, error) are computed from the project's primary colour with Material Color Utilities; the project's own colours stay exact. Light and dark (follows the phone's dark mode; on for new projects, a Library Manager switch for older ones). Dynamic colour from the wallpaper on Android 12+ as an opt-in switch. Plain Buttons become filled Material 3 buttons (also in a background colour of their own, keeping the pill shape), Switches `MaterialSwitch`, CheckBoxes and RadioButtons their Material versions, plain EditTexts outlined fields with 8dp corners, at least as wide as their label; default text colours follow the theme. Widgets the project's own code treats as framework widgets stay as they are. The design canvas draws the light look with the project's colours | v1.8.4 – v1.8.6 |
| Java editor | Design editor → *Edit Java code* or the Code tab: an activity's generated Java in a code editor. Saving turns the changes back into the project: changed lines inside an event become *add source directly* blocks (only the blocks whose lines changed are replaced; changes inside if/else/loops stay there), new fields custom variables, new imports custom imports, methods written as `public void _name(final String _x, …)` MoreBlocks. What the designer generates itself is refused with its line. Nothing is kept unless the code generated again from the blocks is the code you wrote | v1.8.8 |
| Library Resolver | Replaces the local library manager. Required / All / Updates / Cache tabs; Maven Central search; + Add by Maven coordinate (POMs read like Gradle: parents, BOMs, properties, exclusions, version ranges) or AAR/JAR/ZIP import; libraries Sketchware AI has built in are not downloaded, and a build adds the built-in libraries a local library needs (Retrofit → OkHttp, Gson) | v1.8.8 |
| Export of local libraries | Export to Android Studio includes the project's local libraries: by Maven coordinate in `build.gradle` when known, else as `.jar` / `.aar` files in `app/libs` | v1.8.8 |
| Java statements to blocks | Saving the Java editor turns statements a block writes (setText, setVisible, setEnabled/Clickable/Checked, requestFocus, Toast, setTitle, set/increase/decrease variables, Intent setScreen/putExtra, StartActivity, Finish) into those blocks instead of add-source blocks, when the block generates exactly the typed line; `X.getText().toString()` in a text slot becomes the getText block (v1.8.13); a setHint on a Material 3 outlined field is saved (v1.8.13) | v1.8.9 |
| Library Resolver 2 | Search as you type; `group:artifact` queries Maven Central by field (no HTTP 400) and Google Maven / JitPack by coordinate; `import pkg.*;` added to generated activities for local-library classes the code uses (skipped when it would make a name ambiguous); (i) per library: POM description, licence, project page, manifest permissions, packages, Copy import (offline) | v1.8.9 |
| Library Resolver 3 | One card per library: readable name (POM name, or the artifact made readable) with a version badge, the coordinate, (i), Share (GitHub / project page), Latest / Update, Delete, switched on; long names scroll. Sort A to Z or updates & newest first. + Add also takes a GitHub or JitPack link (newest version JitPack built, or the tag in the link); a com.github version is tried with and without a leading `v`; repositories that redirect (301/302 to another host) are followed; when nothing is found the dialog lists the repositories that didn't answer and why. Progress dialog with the step (POM, archive, D8) and the bytes of the download. Search shows only .aar / .jar libraries (no WebJars, npm, Kotlin/JS or native, BOMs). Jars an .aar bundles in `libs/` (not part of the build) are named after the install, with how to add them. After an update, the old version is listed under *Older versions* instead of offering the same update again | v1.8.11 |
| Library Resolver 4 | Only Google Maven, Maven Central and JitPack (JitPack first for GitHub groups); `repositories.json` is no longer read. Find shows the step and a bar, Cancel stops it, Try again; one failure dialog with Copy log (every request and its answer); without a POM the .aar/.jar is installed alone. Search hides Boost / C++ Prefab packages and server artifacts; archives without Android code are left out in one line. GitHub/Web button opens the browser; green Up to date | v1.8.12 |
| Library Resolver 5 | No popup when an install worked: a Toast, the switch on and a green ✓ Installed on the card; when something needs a word, one line above the list with Copy log; a dialog only when the library itself couldn't be installed. Readable names instead of folder names. The highest version of a library wins: a lower one is never switched on over a higher one, a needed library uses the higher version that is on | v1.8.13 |
| Material 3 tags | MaterialCardView (elevated) and MaterialToolbar in Material 3 projects; Project Configuration → *Convert to Material 3 & Modern Stack* for older projects (target 37, min 21, AppCompat, Material 3, D8 + Java 11) | v1.8.9 |
| Code tab | Fourth design editor tab: the project's activities (→ Java editor) and its own Java/Kotlin files; + New class | v1.8.8 |
| Fonts | `SketchFonts`: custom TTF/OTF fonts with room for Telugu (and other) stacked glyphs | v1.8.4 |
| Export | APK signed v1+v2+v3; AAB with a SHA-256 JAR signature; "Export to Android Studio" writes an AGP 8.12 / Gradle 8.13 project | v1.8.2, v1.8.5 |
| Built-in libraries | Material 1.13.0, AppCompat 1.7.1, AndroidX core 1.13.1 and the rest of the set listed in `engineering/component-library-inventory.md` | v1.8.4 |
| Native libraries | Warning for a 64-bit `.so` with 4 KB page alignment; local libraries' `.so` files in APK and AAB | v1.8.4 |
| UI | "Obsidian liquid glass" visual layer over upstream's screens. The logic editor, block drawer and view editor are upstream's own (precompiled) classes | v1.6 |

## How it is tested, and what is not

- **Android runtime harness** (`tools/art-harness`): the APK's own classes run on an Android 12 (API 31) ART runtime
  **without the Android framework**: whole Run builds of fixture projects 901–922 (914–918 created by the New Project
  screen's own code; 919 edited through the Java editor's save; 920 with libraries installed by the Library Resolver) and
  AAB exports (aapt2,
  ECJ, Kotlin, D8/L8, DEX merge, packaging, signing). This is not a device test and says nothing about other Android versions.
- **layoutlib** (`tools/layoutlib-renders`): Android 14's view, theme and text code on a desktop JVM, for generated
  layouts and themes (on light and night-mode device settings, large font and bold-text settings), Telugu text and IDE
  dialog layouts. No Android emulator: the build machine has no hardware virtualisation.
- **Host checks**: resource-ID pinning, API-level and linkage scans of the APK, signatures, 16 KB alignment.
- **Not run here**: the IDE's screens (logic editor, design editor, compile log, Java editor, Library Resolver), Maven
  downloads on a phone's network, aapt2 on a real phone, installing or
  starting generated apps. Device testing is done by the maintainer on his phone (Android 15) after each build; its
  results go to `engineering/compatibility-matrix.md`.

Per-release evidence: `engineering/v<version>-verification.md`; device and API status:
`engineering/compatibility-matrix.md`.

## Known limits

- Project code can't call Android 15+ APIs unless a newer `android.jar` is set in Build Settings (the bundled one is API 34).
- Java 17 language level is not possible with ECJ 3.26 and the on-phone R8 (`engineering/java17-path.md`).
- Kotlin code is compiled by kotlinc 1.7.10, which reads Kotlin metadata up to 1.8: Kotlin sources can't use libraries built with newer Kotlin.
- Material 3 Expressive: Material 1.13 (bundled) contains some Expressive components (button groups, split button,
  loading indicator, docked and floating toolbars) but not the Expressive theme, and the palette doesn't offer them yet.
  Material Components 1.14 needs min SDK 23 and API 35: not shipped.
- Dark theme: colours, icons and `setTextColor` blocks the user chose keep their values at night. A dark icon or text
  colour on the theme background can be hard to read in dark mode; the Library Manager's *Dark theme* switch turns it
  off per project. Dynamic colour replaces the project colours on Android 12+ by design.
- The design canvas shows the light look only.
- Java editor: only code inside events, fields, imports and MoreBlock-shaped methods becomes blocks; other methods and
  classes belong in a Java file of their own (Code tab → + New class). Changed code is stored as *add source directly*
  blocks, not as individual blocks.
- Library Resolver: a library that needs a different major version of a built-in library (Retrofit 3 needs OkHttp 4;
  OkHttp 3.14.9 is built in) may not work; the install plan says so. Libraries compiled for Java 8+ can't be dexed on
  Android 7.1 and older (Dx).
- A drawer's panel keeps the AppCompat Manager's light grey (#EEEEEE) in dark mode; its default black text stays readable there.
- Not on the phone: a Gradle build (it needs a JDK and Gradle runtime of about 1.5 GB on the device), Kotlin as the
  language of the blocks (blocks generate Java; Kotlin files can be added in the Java Manager), Jetpack Compose.
- Firebase, Play services (Ads 20.1.0, Maps, Auth), OneSignal and Audience Network are held at their old versions.
- The debug APK is signed with the public test key, so it installs over earlier releases. It is not a release key.

## Advanced Roadmap (v1.9 – v2.0): not built

**None of the following exists in the code yet.** Order as planned; one release does not attempt everything.

1. Build Doctor strategies with IDs, skipping fixes that already failed for the same fingerprint.
2. Error dashboard.
3. Verified repair memory kept on disk.
4. Dependency and library intelligence beyond the Library Resolver.
5. Developer-tools controller, Project Brain, Git/GitHub, extensions, Android Studio import (export exists).
6. Sign-in and telemetry: reviewed only (`engineering/auth-telemetry-review.md`). Blocked until a Firebase project for
   `com.sketchware.ai`, a private release key and consent screens exist. Telemetry would ship switched off.
7. Material 3 Expressive: the Expressive components of Material 1.13 in the palette, then the Material Components 1.14
   migration (Expressive theme; needs min SDK 23 and a bundled API 35 `android.jar`).
8. A dark-mode preview on the design canvas.
9. Any AI assistance (on-device or cloud model): not designed. Today's rules keep models out of the build.

## Building from source

The tree is the upstream source zip `Sketchware-Pro-6_4_0-rc05.zip` (sha256
`ac9c63106dd81879ece6178fc83bb0356dfb0aef6a5493eebf338008d2b1f0b8`) plus this fork's commits. Since v1.8.6 the repository
is complete: the source packages that upstream's unanchored `.gitignore` rule `build/` used to hide (`mod/hey/studios/build`,
`mod/jbk/build`) and the public test key are tracked.

Needs JDK 17 and the Android SDK (platform 34; Gradle 8.13 and AGP 8.12 are fetched by the wrapper). Then:

```
git clone https://github.com/smartapps000-hub/SketchwareAI.git && cd SketchwareAI
echo sdk.dir=/path/to/android-sdk > local.properties
./gradlew assembleMinApi26Debug
```

Output: `app/build/outputs/apk/minApi26/debug/app-minApi26-debug.apk`.

## Repository layout

| Path | Contents |
|---|---|
| `app/` | the IDE (upstream sources + this fork's changes; most of the core is precompiled in `app/libs/*.jar`) |
| `kotlinc/` | the embedded Kotlin compiler module (GPL-3.0, from CodeAssist) |
| `engineering/` | verified state of the fork, per-release verification records, test fixtures, compatibility matrix, known errors |
| `tools/` | Android runtime harness, layoutlib renders, static aapt2 build, built-in library packaging, APK scans, JVM tests |
| `releases/` | release notes and SHA-256 sums per release. The APKs (about 138 MB) are not in this branch: they are on the `binaries` branch (up to v1.8.8 as 7z parts; since v1.8.9 one full APK in `apk/`, stored with Git LFS because GitHub refuses normal files over 100 MB). Download them with the *Download raw file* button or `https://media.githubusercontent.com/media/smartapps000-hub/SketchwareAI/binaries/apk/<file>`: a `raw.githubusercontent.com` link gives the 134-byte LFS pointer text, which Android reports as "package appears to be invalid" |

## Credits

Sketchware (besome), the Sketchware Pro team and contributors, CodeAssist (tyron12233), the Android Open Source Project,
and the authors of every component in [CREDITS.md](CREDITS.md).
