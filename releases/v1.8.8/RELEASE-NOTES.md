# Sketchware AI v1.8.8

versionCode 178, versionName `v1.8.8-HASH-minApi26`. Installs over v1.8.7 (same test key).

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

## Code tab

A fourth tab in the design editor: the project's activities (→ Java editor) and your own Java/Kotlin classes
(→ code editor); **+ New class** makes a Java or Kotlin class or activity in the project's package (an activity still
needs its manifest entry: Java Manager → ⋮ → Add as Activity).

## Checked

SUITE-PENDING

## Not in this release

GitHub commit from the app, Kotlin as the blocks' language, a Gradle build on the phone, Jetpack Compose.
