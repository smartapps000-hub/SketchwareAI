# Sketchware AI v1.8.9

versionCode 179, versionName `v1.8.9-f975642-minApi26`. Installs over v1.8.8 (same test key).

## Java editor: your statements become real blocks

**Save as blocks** in the Java editor now turns each statement you write inside an event or a MoreBlock into the block
that generates it, instead of an *add source directly* block:

| You write | Block |
|---|---|
| `textview1.setText("Hi");`, `textview1.setText(title);` | setText (a String variable becomes its variable block) |
| `edittext1.setHint("Name");` | set hint (not on Material 3 outlined fields: there the generator writes the hint as the field's label, so the Java editor refuses that line and changes nothing) |
| `button1.setVisibility(View.GONE);` | setVisible (VISIBLE / INVISIBLE / GONE) |
| `button1.setEnabled(false);`, `setClickable`, `checkbox1.setChecked(true);`, `requestFocus()` | setEnable, setClickable, setChecked, request focus |
| `SketchwareUtil.showMessage(getApplicationContext(), "Saved");` | Toast |
| `intent1.setClass(getApplicationContext(), SecondActivity.class);` | Intent setScreen |
| `intent1.putExtra("key", value);`, `startActivity(intent1);`, `finish();` | putExtra, StartActivity, Finish Activity |
| `title = "x";`, `score = 5;`, `done = true;`, `score++;`, `score--;` | set variable, increase / decrease |
| `setTitle("Home");` | Activity set title |

A statement becomes a block only when that block generates exactly the line you wrote (same widget, intent, activity
and variable names, same text); anything else, and every `if`/loop you write, stays *add source directly* as before. New
`public void _name(...)` methods still become MoreBlocks, and their statements are mapped the same way.

## Library Resolver

- **Search as you type** (All tab, 3+ characters, after a short pause). `group:artifact` (or a Gradle line) no longer
  sends a colon to Maven Central's search (it answered HTTP 400): it asks for that group and artifact. When Maven
  Central doesn't have it, Google Maven and JitPack are asked for that coordinate's `maven-metadata.xml`, so
  `com.github.DantSu:ESCPOS-ThermalPrinter-Android` is found (JitPack) — type the case as the project writes it.
- **Imports added for you**: when your code (add source blocks, the Java editor) uses a class of a local library by its
  simple name — `EscPosPrinter`, `BluetoothPrintersConnections` — the generated activity gets `import <its package>.*;`.
  Only packages your code uses are imported, and none whose import would make a name your code uses ambiguous
  (a library class named like an `android.widget` or `java.util` class): such a class still needs its full name.
- **(i)** on every installed library (Required and All tabs): description, licence and project page from its POM, the
  permissions its manifest adds to your app, its packages and the import line (**Copy import**). Read from the files on
  the phone only; libraries installed from now on keep their POM next to them (older installs: if the download cache
  still has it).
- Updates tab and MultiDex: as in v1.8.8 (Updates checks maven-metadata.xml of Google Maven, Maven Central and JitPack;
  MultiDex is switched on by the build when needed).

## Material 3

- In Material 3 projects, CardView is written as **MaterialCardView** (elevated style: your elevation and corner radius
  stay, no outline; its colour is the theme's surface colour unless set) and the activity toolbar as **MaterialToolbar**.
  Your code keeps using them as CardView and Toolbar. Older (non-Material 3) projects are unchanged.
- **Convert to Material 3 & Modern Stack** (project list → ⋮ → Project Configuration, on older and imported projects):
  shows what changes, then on Save: target SDK 37, min SDK 21, AppCompat and Design on, Material 3 theme on, D8 + Java 11.
  Blocks, layouts and colours are kept; the theme stays light (Dark theme: Library Manager). Target 30+ changes Android's
  storage and permission rules for the app: test file access after converting.

## Checked

On the Android 12 (API 31) ART runtime with this APK's own classes, no phone (details: `engineering/v1.8.9-verification.md`):

- **Statements to blocks**: 21 statements typed into a click event of a test project become the expected blocks with the
  right parameters; the code generated again from them is what was typed; the project builds (54 checks). Every activity
  of the 21 test projects opens and saves unchanged.
- **Imports**: a project using the ESCPOS printer library with no import blocks builds; its exported Android Studio project
  builds with Gradle. On a fresh install (before the first build has unpacked android.jar) the build's auto-fix adds the
  imports instead, with a warning. Ambiguous cases (e.g. `okhttp3.*` and a library `Callback`) add nothing.
- **Search and (i)**: the field query and the JitPack lookup against the live services; the (i) text of the installed
  ESCPOS library (description, MIT licence, project page, 8 packages).
- **Material 3**: MaterialCardView / MaterialToolbar layouts build; rendered with layoutlib in light and dark (toolbar
  icon and title white on the toolbar colour).
- **Everything else again**: Run build of all test projects (906 fails as designed), LoadAll of every built app, Export AAB
  (bundletool validate, jarsigner), Export to Android Studio built with Gradle.

**Not run here:** these screens on a phone (Library Resolver live search, (i), Copy import, Project Configuration and
Convert, the Java editor), installing or starting built apps. **First build after installing:** library imports come from
the auto-fix (a warning) until android.jar has been unpacked once.

## Files

`apk/Sketchware_AI_v1.8.9.apk` on the repository's `binaries` branch: one full APK (no 7z parts), stored with Git LFS
(GitHub's limit for normal files is 100 MB). Download it from GitHub's file page (Download / View raw).

## Not in this release

Shrinking the IDE with R8 (see the verification record: the embedded Kotlin compiler is found by reflection; not safe to
shrink without a way to test every Kotlin path here). Material 1.14 (needs min SDK 23 and API 35 for every project).
