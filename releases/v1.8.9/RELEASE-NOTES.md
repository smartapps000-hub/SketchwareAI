# Sketchware AI v1.8.9

versionCode 179, versionName `v1.8.9-HASH-minApi26`. Installs over v1.8.8 (same test key).

## Java editor: your statements become real blocks

**Save as blocks** in the Java editor now turns each statement you write inside an event or a MoreBlock into the block
that generates it, instead of an *add source directly* block:

| You write | Block |
|---|---|
| `textview1.setText("Hi");`, `textview1.setText(title);` | setText (a String variable becomes its variable block) |
| `edittext1.setHint("Name");` | set hint |
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

(see `engineering/v1.8.9-verification.md`)

## Files

`apk/Sketchware_AI_v1.8.9.apk` on the repository's `binaries` branch: one full APK (no 7z parts), stored with Git LFS
(GitHub's limit for normal files is 100 MB). Download it from GitHub's file page (Download / View raw).

## Not in this release

Shrinking the IDE with R8 (see the verification record: the embedded Kotlin compiler is found by reflection; not safe to
shrink without a way to test every Kotlin path here). Material 1.14 (needs min SDK 23 and API 35 for every project).
