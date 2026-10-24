# Sketchware AI v1.8.8

versionCode 178, versionName `v1.8.8-f6b8e9f-minApi26`. Installs over v1.8.7 (same test key).

## Edit Java code → blocks

Design editor → the build options button next to Run → **Edit Java code**, or the new **Code** tab → an activity → **Edit**. The activity's
generated Java opens in the code editor (Java highlighting, completion, undo/redo). **Save as blocks** stores your
changes in the project:

- Lines you change, add or delete **inside an event** (a click, onCreate, a MoreBlock, a ListView/RecyclerView
  onBindCustomView) become *add source directly* blocks. Only the blocks whose lines you touched are replaced; the
  others stay as blocks. A change inside an `if` / `else` / loop stays inside it.
- A new field (`private int clicks = 0;`) becomes a custom variable; a changed variable line becomes a custom variable
  with your text; a removed variable is removed (refused while a block still uses it).
- A new import becomes a custom import.
- A new method written the way MoreBlocks are written — `public void _name(final String _text, final double _n) { … }`
  (parameters boolean, double, String, Map, List or View, each named `_name`) — becomes a MoreBlock. A removed
  MoreBlock is removed (refused while a block calls it).
- Everything the designer generates (view lookups in `initialize()`, onCreate's frame, listener frames, adapters, the
  class line, generated imports) can't be stored as blocks: Save says which line and what generates it, and nothing
  is changed. Other methods and classes go in a Java file of their own (Code tab → + New class).
- After saving, the code is generated again from the blocks and compared with what you wrote (imports and members as
  sets, indentation and blank lines ignored). If they differ, nothing is kept.

Activities that use a Java file of their own (Java Manager) open that file and save it as it is. Activities changed by
command blocks are shown read-only.

## Library Resolver

Replaces *Local library manager* (design editor drawer, Tools, Compile log → Local libraries). Four tabs:

- **Required**: the project's local libraries: ready to build or not (missing files, not dexed → *Dex*), the built-in
  libraries they use, and libraries they need that are switched off or missing (→ *Add*).
- **All**: every installed library with its Maven coordinate and size; switch it on or off for the project, delete it.
  Search the installed ones, or **Maven Central** (search.maven.org) → *Add*. Google Maven libraries (androidx…) are
  added by coordinate.
- **Updates**: newer stable versions of libraries that came from Maven → *Update* (installs it with what it needs and
  switches the project from the old version to the new one).
- **Cache**: sizes of the Maven download cache, the unpacked built-in libraries, the desugared-library cache,
  unfinished installs and the project's build files, each with *Clear*.

**+ Add**: a coordinate (`group:artifact:version` or a Gradle line). The resolver reads the libraries' POM files like
Gradle: compile and runtime dependencies, parent POMs, properties, dependency management and BOMs, exclusions, version
ranges, the highest version when two ask for different ones. What Sketchware AI has built in (AndroidX, Material,
Kotlin, Gson, OkHttp, Glide, …) is not downloaded; the build now adds those built-in libraries for every local library
that needs them (e.g. Retrofit gets the built-in OkHttp and Gson even when no block uses them). When a library asks for
a different major version of something built in (Retrofit 3 needs OkHttp 4; the built-in OkHttp is 3.14.9), the plan
says so before you install: such a library may not work. Retrofit 2.x works with the built-in OkHttp 3. Or **Import file**
(.aar / .jar / library .zip).

## Export to Android Studio: your local libraries are included

Until now the exported project left out the project's local libraries, so it didn't compile in Android Studio when
the code used one. Now: a library with a known Maven coordinate (installed by the Library Resolver, or downloaded by
the build's library repair) is written to `app/build.gradle` as `implementation 'group:artifact:version'` (JitPack is
added to `settings.gradle` when one came from there); any other library is copied to `app/libs` (a `.jar`, or an `.aar`
when it has resources, assets, native libraries or a manifest). Plain `.jar` libraries the Library Resolver installs no
longer get an "R package" name (their Maven group, e.g. `com.squareup.retrofit2`, is not a Java package; the generated
`import com.squareup.retrofit2.*;` only compiled on the phone); in an export, such an import left by an older install is
removed.

## Code tab

A fourth tab in the design editor: the project's activities (→ Java editor) and your own Java/Kotlin classes
(→ code editor); **+ New class** makes a Java or Kotlin class or activity in the project's package (an activity still
needs its manifest entry: Java Manager → ⋮ → Add as Activity).

## Checked

On the Android 12 (API 31) ART runtime with this APK's own classes, no phone (details:
`engineering/v1.8.8-verification.md`):

- **Java editor**: a test project edited through the editor's save, 43 checks (changes inside events and inside
  if/else, fields, imports, MoreBlocks, removals, refusals that must change nothing, a URL field), each result
  generated again from the blocks and compared; then the project builds. Every activity of the 20 test projects opens
  as convertible and an unchanged save changes nothing.
- **Library Resolver**: Retrofit 2.9.0 + its Gson converter resolved, installed and switched on in a project; the
  build adds the built-in OkHttp and Gson; every Retrofit class loads. POM resolution was also run against the live
  Google Maven, Maven Central and JitPack (Retrofit, Room, RxAndroid, AndroidX Preference, MPAndroidChart).
- **Export to Android Studio** of that project, with its libraries by Maven coordinate and with imported copies in
  `app/libs`: both build with Gradle 8.13 / AGP 8.12.
- **Everything else again**: Run build of all 20 test projects (906 fails as designed and names its block), LoadAll
  of every built app, Export AAB of 9 projects (bundletool 1.11.3 / 1.17.2 / 1.18.3, `jarsigner`), Export to Android
  Studio of 6 projects built with Gradle.
- The Library Resolver, Java editor and Code tab screens were rendered with layoutlib (Android 14's view code on a PC).

**Not run here:** these screens on a phone (Activities, the code editor), Maven Central search and downloads over a
phone's network, Dx on Android 7.1 and older, installing or starting built apps.

## Files

The APK (`SketchwareAI-v1.8.8.apk`, 138,448,009 bytes) and the patches zip are on the repository's `binaries` branch as
7z parts (GitHub refuses files over 100 MB); `SHA256SUMS` lists them. Open `.7z.001` in ZArchiver.

## Not in this release

GitHub commit from the app, Kotlin as the blocks' language, a Gradle build on the phone, Jetpack Compose.
