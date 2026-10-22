# Architecture state

Verified facts about this fork, kept so they don't have to be rediscovered. Last updated for v1.8.4.

## Foundations (do not bypass)

- Fork of Sketchware Pro 6.4.0-rc05 (git base `966d905`); package `com.sketchware.ai`.
- Storage is the original layout: `/.sketchware/data/<sc_id>/{logic,view,file,library,resource}` (logic/view/file are AES/CBC, key = iv = `sketchwaresecure`), generated project in `/.sketchware/mysc/<sc_id>/`, local libraries in `/.sketchware/libs/local_libs/<name>/`.
- The block graph is the source of truth. Generated Java is derived and rewritten at every build (`mysc/<sc_id>` is deleted first). Build-time repairs therefore never persist in files; they are deterministic and re-applied at each build.
- Most of the core (`a.a.a.Fx` block-to-code, `eC` project data, `BlockPane`, `Rs` block view) exists only as compiled classes in `app/libs/*.jar`. They are used, not modified.
- Block ids: a block's id is the view tag it gets in the logic editor; it is kept across load/save of an event and is unique per event (not per project). A block is identified by activity + event key (`<targetId>_<eventName>`) + id.

## Phase 1 - Source Map Engine (done in v1.8.0)

`com.sketchware.ai.sourcemap`

- `SourceMapMarkers`: during a second, throw-away generation every statement block of every event/MoreBlock gets a marker block in front and behind (plain "add source directly" blocks whose code is a comment). The generator itself is not changed.
- `SourceMap.extract`: removes the markers and notes the lines. Hooked in `Jx.generateCode()` (activities) with `SourceMapMarkers.wrap` at the four block-to-code call sites (onCreate, events in `Hx`, adapters, MoreBlocks).
- Proof instead of trust: the map is kept only if the marked text minus markers equals the normal generation character for character, and (at `SourceMaps.finish`) only if the file on disk has the hash of that text. A lookup re-checks the hash (`SourceMaps.resolve`) or uses maps checked at the start of the compile step (`ErrorOrigins.session`). Anything else answers "generated source only"; a wrong block is never shown.
- Written per build by `yq.b()` to `mysc/<sc_id>/sourcemap.json`: per file `{file, sourceHash, generator, exact, events[{kind,targetId,eventName,start,end}], entries[{event, blockId, opCode, position, depth, start, end}]}`. Regenerated from the block graph each build, so it survives edits by construction; only activities whose code is generated are mapped.
- A saved log is tied to its map: the build's log ends with `(source map <file>@<hash prefix>)`; the compile log screen resolves a file only if the log names the map on disk, the file still has the map's hash, and the line on disk is the line the compiler quoted. An older log shown after a newer build gets no origins.
- Exports (`exportingProject`) make no source map and leave none in the exported folder.
- Consumers: failed build log gets "Where the errors come from"; auto-fix messages get the origin; the diagnostic sheet shows one card per Java error with Origin, **Open block** (logic editor opened on the event, block scrolled into view and ringed: extras `focus_block_id`, `focus_block_op`, `focus_line`) and **View source** (the file the compiler read, from disk, at the line: extras `path`, `line`).
- Not mapped: lines outside block code (fields, method headers, listener frames, imports), expression blocks on their own (they belong to their statement block), XML/layout lines, files replaced by custom Java, files rewritten by command blocks.

## Phase 2 - Build Map (first part in v1.8.0)

- `com.sketchware.ai.buildmap.BuildMap`: 13 tasks (prepare, generate, built-in libraries, AAPT2, Kotlin, Java, StringFog, ProGuard/R8, DEX, DEX merge, package, sign, install) with status and duration; errors and repair attempts for the failed task. `mysc/<sc_id>/buildmap.json`; printed at the end of a failed build's log.
- Missing: per-task inputs/outputs, warnings per task, cache (the build has none), AAB/zipalign/launch tasks, a dashboard screen.

## v1.8.1 additions

- **Write-back to blocks** (`sourcemap.BlockWriteBack`): after a build that compiled with repairs, each repair whose changed lines lie inside one "add source directly" block becomes a proposal in `mysc/<sc_id>/autofix.json` (block code before/after, changed lines). The compile log's sheet shows it with **Apply to block** / **Undo**. Apply replaces the block's code in the open project's data, generates the activity again and requires the block's lines to equal the repaired lines that compiled; otherwise the old code is put back. History in `data/<sc_id>/autofix_history.json`. Not written back: repairs in ordinary blocks, added imports, repairs that span two blocks. The build still repairs those each time.
- **PNG sanitizer** (`project.PngSanitizer`), hooked in `ResourceCompiler.compileDirectory` for project, imported and local-library resources.
- **Library integrity** (`project.LibraryIntegrity`), run by `DesignActivity` before the sources are generated. Restores a missing classes.jar from an archive in the folder, or by downloading a coordinate that is certain (`source.json`, written at every download from now on, or the curated tables); never by searching a repository for the name. Adds only missing files. `LibraryHeal.writeConfig` takes the package from the AAR's manifest, never writes a Maven group that isn't a valid Java package, and never replaces an existing config.
- **Dialogs that reuse `R.layout.dialog`** (`AsdAll`, `AsdOrigin`, `SketchDialog`) now go through `ui.ObsidianDialogs.window/leftButton/finish`. Before, they added a bare "Code Editor" text in front of the pill row and never hid the unnamed left pill, so Cancel/Select were squeezed and unreadable, and their option lists kept dark text on the dark sheet.
- Sheets and dialogs are fully opaque (2-3% see-through showed the log's text behind them on OLED).
- The owner's "four tiers" are the existing order of the compile step, not new machinery: pre-compile pass (syntax/dead code) -> library integrity and library rounds (dependencies) -> AutoHealLoop (several attempts, each verified by a real compile) -> rollback and origin cards. No strategy ids or repair memory yet (Phase 3/5).
- Sign-in and telemetry: reviewed, not built. See `auth-telemetry-review.md`.

## v1.8.3 additions

- **Built-in libraries updated** (`assets/libs/libs.zip`, `dexs.zip`, `mod/jbk/build/BuiltInLibraries.java` in `overlay/`):
  AndroidX to the newest versions that build against API 33 (appcompat 1.6.1, material 1.9.0, core 1.10.1, activity 1.7.2,
  fragment 1.6.2, lifecycle 2.6.2, recyclerview 1.3.2, ...), Kotlin stdlib 1.8.22 (+ coroutines 1.7.3), Glide 4.16.0,
  Gson 2.14.0, OkHttp 3.14.9, Okio 1.17.6, Lottie 3.7.2. Firebase / Play services / OneSignal / FAN / YouTube are unchanged.
  Rules in `v1.8.3-verification.md`; tools in `tools/builtin-libs`.
- `BuiltInLibraries` dependency lists only grow: every dependency an entry had in 1.8.2 is kept, the new POM ones are added
  (code in a project may use a class that arrived transitively). An R package is listed only when the library's `res/` has a
  real resource (`androidx.activity`, `androidx.customview.poolingcontainer` are new).
- The extracted copy in `cacheDir/libs` is refreshed when the asset's **size** changes (`Dp.hasFileChanged`): a new libs.zip
  must differ in size from the old one (it does: 24.9 MB vs the 1.8.2 size).
- `Lx` (Android Studio export) build.gradle names the same versions.
- DEX merge (min SDK < 21, or a non-debug build): with min SDK < 21 the project's own DEX is merged right after
  MultiDex so its Application is in classes.dex; `DexMerger` reserves 3x the debug info (index growth 1 -> 3 bytes).
- **Android runtime harness kept in the repository** (`tools/art-harness`): installs an Android 12 ART into the workspace,
  writes fixture projects with the IDE's own project classes and runs the DesignActivity Run-build steps (generate,
  built-in libraries, AAPT2, Kotlin, ECJ + auto-fix, D8, merge, package, sign) with the classes and aapt2 of a built APK.

## v1.8.4 additions

- **Compile platform android-34**: `assets/libs/android.jar.zip` holds the Android 14 (API 34) platform android.jar (class files
  52, like 33). User code, AAPT2 links and D8 desugaring use it unless Build Settings names a custom android.jar. Its size differs
  from the API 33 archive, so `Dp.maybeExtractAndroidJar` extracts it again once. `BuildWarnings.BUNDLED_ANDROID_JAR_API = 34`.
- **aapt2 2.19 (android-14.0.0_r75) for all four ABIs, 16 KB LOAD alignment**: armeabi-v7a and x86 are now built by
  `tools/aapt2-static` like the 64-bit ones (upstream's 32-bit binaries: 4 KB, and can't compile `<macro>` resources, i.e.
  Material 1.11+). The same tag links android-35 resource tables too (checked with a values-v35 attribute).
  `tools/aapt2-static/compare-abis.sh`: all four ABIs give the same resource table (`aapt2 dump resources`) and R.java.
- **ClassFileSanitizer** (`com.sketchware.ai.project`): javac 21 writes `MethodParameters` entries with name_index 0 for
  mandated parameters; D8/R8 3.3.75 throws `NullPointerException ... String.length()` on them (root cause of the old
  concurrent-futures >= 1.2 / error_prone >= 2.48 crash). The sanitizer removes only those attributes (other bytes copied as they
  are). Run on the built-in jars when they are packaged (`tools/builtin-libs/package.py`), and on a library's classes.jar before
  D8 in `LibraryHeal.dex` and `LibraryDownloader._jar2dex`. Not run on local libraries imported before v1.8.4 (D8 reads them
  as library input in `DexCompiler`).
- **Built-in libraries for compileSdk 34**: Material 1.13.0, AppCompat 1.7.1, core 1.13.1, activity 1.9.3, fragment 1.8.9,
  lifecycle 2.8.7, constraintlayout 2.2.2 ... (component-library-inventory.md section 0a). Kotlin stays 1.8.22 (kotlinc 1.7.10
  reads metadata up to 1.8; lifecycle/core/activity at these versions use 1.8 metadata).
- **Material 3 AppTheme** (`ModernProjectDefaults.SETTING_MATERIAL3` = `material3_theme`): new projects "true"; a project
  without the key keeps the MaterialComponents (Bridge) theme. `yq` writes `Theme.Material3.Light.NoActionBar` with the
  project colours in colorPrimary/colorPrimaryVariant/colorSecondary(=accent)/status bar, `viewInflaterClass` =
  `androidx.appcompat.app.AppCompatViewInflater` (plain `<Button>`/`<CheckBox>`... stay AppCompat widgets, as designed),
  FAB style in the accent colour. Toggle in Project Configuration (row `ob_cfg_row_m3`). Design editor: `editor.DesignTheme`
  (loaded in `DesignActivity.onCreate` and after Project Configuration is saved). In M3 projects the palette gives a new
  TextInputLayout `style="?attr/textInputOutlinedStyle"` and a new MaterialButton no style (the theme's button style), never
  an explicit `Widget.Material3.*` style: those need M3 theme attributes and fail to inflate (InflateException) if the project
  is switched back to MaterialComponents. `ItemMaterialButton` draws the fill and corner radius: injected cornerRadius, else a
  pill for Widget.Material3 or for no explicit style in an M3 project, else 4dp.
  Container colours (primaryContainer, secondaryContainer ...) stay Material 3 baseline.
- **Fonts (`project.FontCode`)**: `Jx.formattedCode` rewrites `X.setTypeface(Typeface.createFromAsset(<assets>,"fonts/N.ttf"), S);`
  to `SketchFonts.apply(X, <assets>, "fonts/N.ttf", S);` (same line; X must be a whole name chain, so `(cast) x).view` and
  `a[i].view` lines stay as written, and a match never crosses a line end). `yq` adds `<package>.SketchFonts` when a generated
  activity or a custom Java file of the project calls it (a custom SketchFonts.java in the Java manager wins). The helper caches Typefaces, falls back to
  Typeface.DEFAULT with a log line instead of crashing on a missing asset (also tries the lower-case path the font copy uses),
  reads the font's cmap to build a probe of characters the font itself maps (Latin letters + Telugu worst-case clusters when it
  maps KA + virama), measures the probe's shaped ink with `Paint.getTextBounds` (Minikin/HarfBuzz) and raises the line-spacing
  multiplier and top/bottom padding only where the ink exceeds the font metrics (remembered per view, so applying twice
  doesn't add twice); API 35+: `setUseBoundsForWidth` + `setShiftDrawingOffsetForStartOverhang` by reflection.
  The font manager still stores every font as `<name>.ttf`; Android reads TTF and OTF/CFF by content.
- **AAB export** (minApi26 `AppBundleCompiler`, overlay): local libraries' `jni/` native libraries are added (the APK build
  always did; AABs missed them), duplicate entries and library jar signature files are skipped. Signing of the .aab is
  unchanged (kellinwood ZipSigner, as upstream).
- **NativeAlignment**: build warning for a 64-bit `.so` (project or local library) whose LOAD alignment is below 16 KB.
- **DEX merge with the bigger libraries**: for min SDK < 21 the project's own D8 output is now several files (the R classes of
  the libraries pass 64K fields; the activities and the Application land in the last one). `project.DexOrder.codeFirst` merges
  the files that hold code right after MultiDex. `DexMerger.WriterSizes` reserves 3x the debug info written per code item
  (D8 shares identical debug_info_items; the merger writes one per use) instead of 3x the section, which overflowed (fixture 907).

## Existing repair engine (v1.7.x)

- `AutoHeal` (problem parser, located edits, never changes line count, rollback), `JavaTokens` (dead-code detection after JLS 14.22), `AutoHealLoop` (attempts, per-site escalation keep -> remove, report), `ClassIndex` (imports from the classpath), `LibraryAutoResolve`/`LibraryHeal` (enable installed library, known Maven coordinates, sub-dependency linking, D8), `BuildWarnings`.
- Order in `Dp.compileJavaCode()`: origin session -> pre-compile dead-code pass -> ECJ -> library rounds -> AutoHealLoop -> report.

## Not built

Phase 3 Build Doctor as a strategy-ID pipeline with result fingerprints, Phase 4 dashboard screen, Phase 5 repair history/memory on disk, Phase 6 Local Library Manager status display, Phase 7 Developer Tools controller, Project Brain, Git/GitHub, extensions, Android Studio import/export. A tamper/licence "security kit" is out of scope and will not be added.

## Rules carried from the directives

- Offline first; no cloud model in the build. Network only for Maven artifacts from Google Maven, Maven Central, JitPack. No scripts, Gradle hooks or unsigned plugins are ever downloaded or executed.
- No GitHub tokens/secrets in the APK, resources or project files.
- Permissions are never added silently.
- Project #639 is a regression fixture; no project id, block name or Bluetooth class is special-cased.
- A release note says "tested" only for what ran, and where (see compatibility-matrix.md).

## Repository notes

- `.gitignore` has an unanchored `build/` rule, so source folders named `build` are not tracked: `mod/hey/studios/build/`, `mod/jbk/build/` (and the flavors' `mod/jbk/build`). Three upstream files there are modified by this fork (`BuildSettings.java`, `BuildSettingsDialog.java`, `ResourceCompiler.java`); they are not in the patch series and ship as whole files in the patches zip under `overlay/`. Never put new code in a package folder named `build`.
