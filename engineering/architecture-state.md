# Architecture state

Verified facts about this fork, kept so they don't have to be rediscovered. Last updated for v1.8.3.

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
- **Android runtime harness kept in the repository** (`tools/art-harness`): installs an Android 12 ART into the workspace,
  writes fixture projects with the IDE's own project classes and runs the DesignActivity Run-build steps (generate,
  built-in libraries, AAPT2, Kotlin, ECJ + auto-fix, D8, merge, package, sign) with the classes and aapt2 of a built APK.

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
