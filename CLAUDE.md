# Sketchware AI - Core Engine Skill & Architecture Guidelines
Rules and proven workflow for Sketchware AI (com.sketchware.ai), the Sketchware Pro 6.4.0-rc05 fork: engine and build-repair changes, source map, testing on the Android runtime harness, release checks, and delivering the APK.


# Sketchware AI engine work

Sketchware AI is a fork of Sketchware Pro **v6.4.0-rc05** with package id `com.sketchware.ai`.
The owner works only on a phone, tests every release on a real device within hours, and wants
a working APK back, not a guide.

Repo: `/home/claude/work/Sketchware-Pro-6.4.0-rc05` (git). The base is the repo's first commit,
the upstream zip as is (`git rev-list --max-parents=0 HEAD`; its hash differs per rebuild, same tree).
Read `engineering/architecture-state.md`, `engineering/compatibility-matrix.md`, `engineering/known-errors.json`,
`engineering/test-fixtures.json`, `engineering/reference-index.json` and the latest `engineering/v<version>-verification.md`
in the repo first: they hold the verified state and are updated with every release. `README.md` (licence status, shipped
vs roadmap), `CREDITS.md` (every component's licence and where it was read) and `releases/v<version>/` (release notes,
SHA256SUMS) are part of the repo since v1.8.5.

**Workspaces are wiped between sessions. Since v1.8.6 the repository is on GitHub:
`https://github.com/smartapps000-hub/SketchwareAI` (public, owner's account). A fresh workspace: `add_repo`
smartapps000-hub/sketchwareai with push access, clone it to `/home/claude/work/Sketchware-Pro-6.4.0-rc05` (fetch enough
depth for the history you need), `echo sdk.dir=/opt/android-sdk > local.properties`, build. Its main branch is this fork's
linear history merged (`-s ours`) with the 3 commits the owner made on GitHub first (GPL text, Android .gitignore, an old
README). The old way still works when GitHub isn't reachable:** the upstream zip `Sketchware-Pro-6_4_0-rc05.zip`, sha256
`ac9c63106dd81879ece6178fc83bb0356dfb0aef6a5493eebf338008d2b1f0b8`, plus the latest `SketchwareAI-v<version>-patches.zip`
(on the repository's `binaries` branch since v1.8.6; before that 7z parts in the chat; parts or the extracted zip can
be uploaded, extract with `7z x`, apt package p7zip-full).

`Sketchware-Pro-7_0_0.zip` (sha256 `fd354c4d5d0c91a665d50694c4dd848e8856f1532e7ff189e95d4e7f58f87645`)
and 7.0.0-beta-01 are reading references only, never a base. Rebuilding from the zips: unzip the base, `git init` +
commit it, `git clean -fdX` (v1.8.6+: the base's ignored copies of `mod/**/build/**` and `testkey.keystore` must go, the
patches add them), `git am` the patches, copy `overlay/` (empty since v1.8.6), compare with `git diff --stat`. Plain
github.com URLs from the shell only work for a repo attached with `add_repo` (git through the session proxy); `gh api`
works through the proxy even though `gh auth status` calls the token invalid. Never start from scratch.

Releases so far: v1.0 to v1.6.x (64-bit aapt2, rebrand, project defaults, Obsidian glass UI,
library downloader), v1.7.0 to v1.7.2 (build auto-fix, missing-library resolver, engine 3),
v1.8.0 (source map, error origins with Open block / View source, build map, engineering records),
v1.8.1 (write-back of verified fixes into add-source-directly blocks, PNG sanitizer before AAPT2,
library integrity restore, `ObsidianDialogs`, opaque sheets),
v1.8.2 (missing library files never reach ECJ/D8, restore from archive / own .swb backup / exact
download, missing images restored at AAPT2 link, `BuildDoctor` + `BuildHistory`, export signed
v1+v2+v3, `build_config` path fix, D8 always on Android 8+, readable dialog inputs),
v1.8.3 (built-in libraries updated to the newest the API 33 toolchain could use; min SDK < 21: project DEX merged right
after MultiDex; DexMerger reserves 3x debug info; `tools/art-harness` and `tools/builtin-libs` kept in the repo),
v1.8.4 (projects compile against the bundled **android-34**; aapt2 2.19 for all four ABIs, 16 KB-aligned; built-in
libraries for compileSdk 34: Material 1.13.0, AppCompat 1.7.1, core 1.13.1, fragment 1.8.9, lifecycle 2.8.7 ...;
`ClassFileSanitizer` (D8 3.3.75 NPE on javac 21 class files); Material 3 AppTheme for new projects; `SketchFonts`
for custom TTF/OTF fonts and Telugu ink; AAB export packs local libraries' native libs; 16 KB `.so` warning; min SDK
< 21 DEX merge fixed for the bigger libraries; APK v1.8.4-6b29316, versionCode 174),
v1.8.5 (IDE on AGP 8.12 / Gradle 8.13 / compileSdk 34; D8/R8 8.11.18 with core library desugaring for projects
(`CoreLibDesugaring`, L8, fallbacks); aapt2 2.20 from android-16.0.0_r4 for all four ABIs; bundletool 1.18.3 with 16 KB
page alignment; AAB signing `BundleSigner` (SHA-256, jarsigner verifies it); Material 3 widgets (`M3Widgets`:
SketchButton, MaterialCheckBox/RadioButton, MaterialSwitch, outlined `SketchTextField`, `scanCode`); Export to Android
Studio as AGP 8.12 / Gradle 8.13 project with widget .aar + R.txt and the FCM / sign-in dependencies; rewarded-ad import
generated; README/CREDITS with the licence separation; APK v1.8.5-3bdb3d1, versionCode 175).
v1.8.6 (Material 3 colour roles from the project colour via Material Color Utilities (`M3Palette`, SchemeContent,
`sketch_m3_*`), DayNight for new projects (`m3_dark_theme`), dynamic colour opt-in (`m3_dynamic_color`), tinted pill
buttons, 8dp outlined fields (`AppTheme.TextField`), SketchTextField label measure fix, Build Settings clip fix, yq
re-reads settings per generation, canvas palette (`ItemLinearLayout` as source); repository complete (.gitignore
anchored, test key tracked), GPL-3.0 for the fork's own code (`COPYING`), pushed to GitHub (main + `binaries` branch);
APK v1.8.6-9bc690c, versionCode 176, sha256 `8cf18893...5e67382b`; patches zip sha256 `38319c54...1b8758`, 53 patches).
v1.8.7 (full-screen New Project screen `ui.NewProjectActivity` + `project.NewProjectSetup.create` (one creation path,
also used by fixtures 914-918), templates `project.ProjectPresets` (Empty, M3 Scaffold, Bottom Navigation, Drawer, Tabbed
ViewPager), colour presets `ThemePresets` + live preview `ThemePreviewView`; AppCompat switched on for new projects
(`ModernProjectDefaults.enableAppCompat`: before, Material 3 never applied to phone-made projects); canvas VideoView
measures like the phone (`ItemVideoView`); colors.xml keeps the ripple alpha; APK v1.8.7-b2bdd42, versionCode 177, sha256
`0f616950...c04c35d`; patches zip `089a0ea2...340567`, 55 patches; GitHub `main` 1b3bd75, `binaries` af27d39).
v1.8.8 (Java editor: generated Java back to blocks; Library Resolver replacing the local library manager; Code tab;
Export to Android Studio writes the project's local libraries; APK v1.8.8-f6b8e9f, versionCode 178, sha256
`0f3ca85d...a1ed56d`; patches zip `dae256be...b0fd0`, 59 patches; GitHub `main` 6f2c8de, `binaries` 4d936b0; see section
3c).
v1.8.9 (Java editor statements → real blocks (`StatementBlocks`); Library Resolver: field search without HTTP 400, Google
Maven/JitPack coordinate lookup, live search, automatic `import pkg.*` for local-library classes (`LibraryImports`),
offline (i) (`LibraryInfo`); MaterialCardView/MaterialToolbar in Material 3 projects; Project Configuration → Convert to
Material 3 & Modern Stack; APK v1.8.9-f975642, versionCode 179, sha256 `9023c26e...669c0cc`, 138,476,021 bytes;
patches zip `506aea6f...26c06b58`, 66 patches; GitHub `main` 7a5eaf5, `binaries` 09924a0; see section 3c). Local branch
`main` = GitHub main; `fork-linear` is the pre-merge
linear branch. Built files: branch `binaries` (up to v1.8.8 per release `v<version>/` with the APK and patches zip as two
7z parts each; since v1.8.9 one full APK `apk/Sketchware_AI_v<version>.apk` and `v<version>/SketchwareAI-v<version>-patches.zip`
with Git LFS (section 7); sums in its README and `releases/v<version>/SHA256SUMS`). A zip can't hold its own sha256: give
it in the reply, the `binaries` README and here, never in the source tree. Leftover branch `upload-tmp` (points into
main's history) could not be deleted from here: the owner can delete it on GitHub.

## 0. Prove the fix on the path the owner uses, and say exactly what ran

Several "fixed" items came back from the device still broken because the code was right but
the real path was never exercised. He calls that fake. Before saying something works:

- Reproduce the exact user path, and separate "new project" from "existing or imported
  project" in every answer.
- When a device log is cut off, ask for the full text (Compile log, then Copy) instead of
  guessing. A failed build's log ends with the Auto-fix report, "Where the errors come
  from" and the Build map: ask for those lines.
- Run engine code on the **Android runtime harness** (section 9), with the classes inside
  the built APK, not only on the desktop JVM. It runs the whole Run build, Export AAB and (since v1.8.5) Export to
  Android Studio on fixture projects written by the IDE's own classes. Build an exported project with Gradle
  (`gradle-export-check.sh`, section 9) and dexdump its APK: v1.8.5's first export compiled but lacked library R classes.
- Activities (logic editor, compile log, source viewer, design editor, Project Configuration), the arm64 aapt2 on a
  phone, and installing/starting an app cannot be run here. Layouts, themes and text rendering can be checked with
  layoutlib (`tools/layoutlib-renders`, section 9b): render every generated-widget change in the state users see it
  (empty field with hint, wrap_content width, checked state, background set from code). Say plainly which of these a
  change touches and that it was not run.
- The owner's phone projects #644, #623, #606 are never in the workspace. When he asks to
  "run the harness on #644/#623/#606", run the fixtures that reproduce their failure kinds
  (unreachable code / damaged PNG / missing library) plus the full-build fixtures, say so in
  one line, and list the three projects under "device checks".
- For code that can't be run, have a separate agent review the diff for crashes and
  behaviour changes **before the final build and suite**. If it can't run or stops early (rate limit, session limit),
  retry it; never treat a missing report as a pass, and a self-review is only a stopgap. Findings so far: v1.8.0 a
  stale-log mis-mapping, a source file hidden by `.gitignore`, an export leak; v1.8.4 a theme-switch inflate crash and a
  line-shifting regex (cost a rebuild and a second suite); v1.8.5 the Material inflater tinting every Button background,
  DayNight hiding user colours, the design editor's shared project cache filled from the backup, MaterialSwitch breaking
  `Switch`-typed code; v1.8.6 settings read once per DesignActivity session (Material 3 switched on mid-session made
  aapt2 fail on `AppTheme.TextField`); v1.8.7 "Legacy Dx" offered on Android 8+ where the build forces D8, version names
  without a dot crashing the old version dialog; v1.8.8 a multi-line field stored with a line break (eC's project file is
  line-based: everything after it lost on reload), lines outside members silently dropped, the Code tab reading project
  data on the UI thread during the editor's load, D8-only installs on Android 7.1-, `*:*` exclusions ignored; v1.8.9
  automatic imports made names ambiguous against on-demand imports the check didn't index (built-in libraries, import
  blocks), the Java editor refused saves when an automatic import went away, Convert to Material 3 wrote the unsaved working
  copy's library state into the saved data (and ran on the UI thread), statements in block comments / under braceless
  if-else-loops mapped to blocks, a block hung under the Finish Activity cap block, the generator unpacking android.jar
  while a build reads it.
  A subagent's report arrives through its tool result; if it is lost from context, it is in
  `/root/.claude/projects/<cwd>/<session>/subagents/agent-*.jsonl` (the input of its last tool call).
- "Tested on Android 12" means only: engine classes ran on the API 31 ART runtime without
  the Android framework. Never turn that into a claim about other Android versions, about
  generated APKs, or about a phone. Keep `engineering/compatibility-matrix.md` current.
  The owner's primary device baseline is Android 15 (API 35); fixtures on his phone are
  projects #644, #623, #606.
- Project #639 (Vehicle_Parking, Bluetooth permission MoreBlock with unreachable code) is a
  regression fixture only. Nothing in the engine may special-case a project id, block name
  or class. The real project file was never supplied; the fixture is a reconstruction.

## 1. UI scope

Layouts, navigation, logic canvas and screen structure stay 6.4.0-rc05; 7.0.0-beta-01 is a
source of engine ideas and readable code only. The logic editor (2-column block drawer, block
shapes, canvas) and the view editor are upstream's own precompiled classes; they are used, not
rebuilt (the palette `Icon*` and canvas `Item*` classes in `dev/aldi/sayuti/editor/view/` are source; since v1.8.5 also
`com.besome.sketch.editor.view.item.ItemButton/ItemEditText/ItemSwitch/ItemCheckBox`, removed from the jar with the same
public fields; v1.8.6 also `ItemLinearLayout`, whose screen root shows the M3 window background in DesignActivity panes and
is refreshed by `DesignTheme.load`). On top sits the approved visual layer, "Obsidian liquid glass": `res/values/colors_ob.xml`,
`styles_ob.xml` (`Ob.Text.*`), drawables `ob_*` (with `drawable-v24` glass versions),
`com.sketchware.ai.ui` (`GlassDrawable`, `GlassScreens.SCREENS` lists the legacy screens that get the glass window,
`ObAlertBuilder`, `ObsidianDialogs`, `ObsidianInputs`, `ObsidianPropertyPanel`, `ThemeMode`). Reuse those resources for
anything new: pill `ob_pill16_state`, accent pill `ob_amber_pill`, card `ob_accent_box`, CTA `ob_cta_state`, press
animator `sw_press`, toggle rows `ob_toggle_row`.

Tokens (verified in `colors_ob.xml`; re-checked in the v1.8.4 APK's resource table; v1.8.5's resources.arsc is identical):
`ob_bg #0B0B0D`, `ob_orange #F4511E`, `ob_amber #FF7A29`, `ob_accent #FFB877`, `ob_accent_2 #FF9A4D`,
`ob_text #F5F5F7`, `ob_text_2 #A1A1AA`, `ob_error #FF8A65`, `ob_divider #24FFFFFF`; glass corner
radius 22dp (`GlassDrawable`: `22 * density`); CTA gradient `#F4511E -> #FF7A29`.

- Colours in the user's *project* are semantic and never recoloured.
- The design canvas and block editor are excluded from dark rendering so previews stay true.
- Adding a control: prefer an existing dialog or screen (Project Configuration = `ob_project_config.xml` rows bound in
  `ProjectSettingsDialog`). A new screen needs the owner's ask.
- No button may exist that does nothing or says "coming soon".
- Dialog and button labels must always be fully visible; sheets and dialogs are fully opaque. A dialog root with
  `clipChildren=false` lets its ScrollView content draw over the buttons (v1.8.6 Build Settings bug).
- Canvas items: never reset a background in `onAttachedToWindow` (ViewPane sets image backgrounds before attaching);
  only undo a tint you set yourself. Never call the caching `jC.b/c/d(sc_id)` getters outside the editor's own load
  (they prefer the backup and the editor then reuses that copy): read with own `new iC/hC(sc_id)` + `g()?h():i()`.
  Exception by design: screens started from DesignActivity after its load (`isProjectReady()`; Java editor, Code tab)
  use `jC.a/b/c` to work on the editor's live copy, and write the working copy with `jC.a(sc_id).k()`. Fragments in the
  design editor's pager are created at once (offscreen limit 3): never touch jC from their onResume before the load.

## 2. Source facts that decide how changes must be made

- **Most of the core is precompiled** in `app/libs/*.jar` (`a.a.a.Fx` block-to-code, `eC`
  project data, `hC`, `lC`, `oB`, `kC` resources incl. fonts, `Rs` block view, `BlockPane`, `ViewEditor`, `ViewPane`).
  Read them with `javap -c -p` (run `unset JAVA_TOOL_OPTIONS` first) or the readable 7.0.0
  source. Change behaviour from source classes that call into them (e.g. post-process generated text in
  `Jx.formattedCode`, which feeds both the normal and the source-map generation), or by subclassing.
  `eC.d` = all blocks (javaName -> event -> BlockBean list: `opCode`, `parameters`), `eC.c` = views per xml,
  `eC.e/f/g` = variables, lists, more blocks (`e`: Pair(type, value), 6 = custom declaration, 9 = custom import;
  `g`: Pair(name, spec)); `eC.i` = events. eC's file format is line based (`@<java>_var` section, one `type:value` per
  line; a line starting with `@` starts a section; one bad line drops the rest of the section): a stored value must
  never contain a line break. To replace a jar class with source, write it faithful to the bytecode
  and `zip -d` the class from the jar (keep the old jar in the scratchpad).
- **Keep `namespace "com.sketchware.remod"`**; only `applicationId` is `com.sketchware.ai`.
  New code goes in `com.sketchware.ai.*` (storage, migration, project, editor, diagnostic,
  doctor, ui, sourcemap, buildmap, library).
- **Source packages named `build`** (`mod/hey/studios/build`, `mod/jbk/build` and the flavours' copies: BuildSettings,
  BuildSettingsDialog(+Bridge), DexCompiler, ResourceCompiler, BuiltInLibraries, AppBundleCompiler): until v1.8.5
  upstream's unanchored `build/` rule hid them and they shipped as `overlay/` files; since v1.8.6 `.gitignore` anchors the
  Gradle build folders and they are tracked like everything else. Still never create a new package folder named `build`.
- **Resource IDs are load-bearing.** The jars hard-code about 7,900 IDs, pinned by
  `public-stable-ids.txt` (repo root, keyed `com.sketchware.ai:`, " = " separated). Gradle doesn't track the file:
  run `clean` after touching it. After every build expect 0 mismatches against the APK (98 entries never appear).
  AGP 8 needs `android.nonTransitiveRClass=false` and `android.nonFinalResIds=false` for the precompiled jars.
- **Storage is the original layout, `/.sketchware/`**: project data in
  `/.sketchware/data/<sc_id>/{logic,view,file,library,resource,project_config,build_config,local_library}`
  (logic/view/file are AES/CBC/PKCS5, key = iv = `sketchwaresecure`), native libs in `data/<sc_id>/files/native_libs/<abi>`,
  the open project's working copy in `/.sketchware/bak/<sc_id>/`, generated project in `/.sketchware/mysc/<sc_id>/`
  (deleted at the start of every Run build and every export), local libraries in `/.sketchware/libs/local_libs/<name>/`
  (`jni/<abi>/*.so` packaged too), fonts in `/.sketchware/resources/fonts/<sc_id>/<name>.ttf` (every font is stored
  with `.ttf`, OTF included: Android reads both by content; copied lower-case to `assets/fonts/`).
  `MyProjectSettingActivity` empties the data folder when a project is created: new-project defaults are written after
  that call (`ModernProjectDefaults.applyToNewProject`, the only call site).
- Block ids are the logic editor's view tags: kept across load/save, unique per event. A
  block is identified by activity + `<targetId>_<eventName>` + id. The logic editor opens
  with extras `sc_id`, `id`, `event`, `project_file`, `event_text` (plus `focus_block_id`,
  `focus_block_op`, `focus_line` for Open block).
- Two flavors: `minApi26` (shipped) and `minApi21`. Shared code guards API 26+ calls.
- `FileUtil.writeFile` is atomic. Keep new writers atomic (temp file, rename); per-run temp names when two builds
  (Run and Export) can run at once; clean temp output in `finally`.
- Generated-app helper classes (SketchwareUtil, FileUtil, SketchFonts, SketchTextField, SketchButton ...) are compiled
  by ECJ for projects with min SDK as low as 19 and Java 1.7/8/11: no lambdas, no try-with-resources, no APIs above the
  project's min without a `Build.VERSION` guard, ASCII source.

## 3. Build engine (what exists; details in engineering/architecture-state.md)

- `a.a.a.Dp.compileJavaCode()` order: origin session, pre-compile dead-code pass, library
  integrity (unusable libraries left out of the build's list, kept in the project), ECJ
  (Java 11, fallback 1.8), library rounds, `AutoHealLoop`, report. On failure the generated
  files are restored. Repairs never change a file's line count.
- Repairs are build-copy-only, **except** repairs inside add-source-directly blocks: after a
  build that compiled with them, `sourcemap.BlockWriteBack` stores them in the block(s) once
  regeneration proves the block's lines equal the repaired lines; a repair spanning several
  blocks is stored in all or none. History in `data/<sc_id>/autofix_history.json`. Ordinary
  blocks are never modified.
- `com.sketchware.ai.project`: `AutoHeal` (ECJ log parser, located edits, rollback),
  `JavaTokens` (dead code after JLS 14.22), `AutoHealLoop`, `ClassIndex` (imports from the
  classpath), `LibraryAutoResolve` / `LibraryHeal` (enable an installed local library,
  download known Maven coordinates, link sub-dependencies, D8), `LibraryIntegrity`
  (restore classes.jar: archive in folder -> project's own .swb backup -> exact coordinate from
  `source.json` or curated tables; never search a repository by name), `PngSanitizer`,
  `ResourceResolver` / `ProjectImages` / `SvgVector` (missing images at AAPT2 link),
  `ProjectBackups`, `ReleaseSigner` (APK export v1+v2+v3), `BuildWarnings`, since v1.8.4 `ClassFileSanitizer`
  (removes nameless MethodParameters entries; run on built-in jars when packaged and on a library jar before D8 in
  LibraryHeal.dex / LibraryDownloader), `FontCode` (font blocks -> `SketchFonts.apply`, helper source), `NativeAlignment`
  (16 KB warning for 64-bit `.so`), since v1.8.5 `CoreLibDesugaring` (L8 library DEX, cache, `reset()`),
  `BundleSigner` (AAB JAR signature, apksig, SHA-256), `M3Widgets`, `GradleExport`, and since v1.8.6 `M3Palette`.
- **Desugaring (v1.8.5)**: `DexCompiler` runs D8 8.11.18 with the desugar_jdk_libs_nio 2.1.5 configuration
  (`assets/libs/desugar.zip` -> `cacheDir/libs/desugar`, checked by exact sizes) and a keep-rule consumer; if the code
  uses any `j$` class, L8 builds `bin/dex/desugar_jdk_libs.dex` for the min SDK, cached per min SDK + keep rules +
  android.jar size (run name key + thread + nanoTime, temp output deleted in `finally`). If D8-with-config or L8 fails
  (also OOM), the project is dexed again without it, a warning says so, and the extracted zip copy is deleted so the next
  build re-extracts. Library jars are D8 *library* input (API conversions keep library calls working). R8 8.x has no
  public `CompilationFailedException(String, Throwable)`: use `initCause`.
- DEX: debug builds with min SDK >= 21 add library DEX files unmerged; min SDK < 21 or a non-debug build (export)
  merges them with the bundled dx `DexMerger` (`Dp.getDexFilesReady`). Min SDK < 21 merges the project's own DEX right
  after MultiDex (its Application must be in classes.dex for Android 4.x). The project's own D8 output can be several
  files (the libraries' R classes pass 64K fields); `DexOrder.codeFirst` merges the files holding code right after
  MultiDex. `DexMerger.WriterSizes` reserves 3x the debug info *written per code item* (D8 shares identical
  debug_info_items, the merger writes one per use).
- `com.sketchware.ai.sourcemap`: a second, throw-away generation with marker comment blocks
  around every statement block gives each block's line range (`mysc/<sc_id>/sourcemap.json`). The map is kept only
  when the marked text minus markers equals the normal generation and the file on disk has its hash; a log is resolved
  only with the map it names. Otherwise the answer is "generated source only": **never show a block that isn't
  proven.** Anything rewriting generated code must keep every line where it is.
- `com.sketchware.ai.buildmap.BuildMap`: per-task status and time, in the failed log and in
  `mysc/<sc_id>/buildmap.json`.
- `com.sketchware.ai.doctor`: `BuildDoctor` (diagnosis by failed step, changes labelled
  permanent/temporary, Build again only when the project fingerprint changed), `BuildHistory`.
  Not yet: strategy ids, skipping repeated failed fingerprints, repair memory on disk.
- The owner's phase order is fixed: source map, build map, Build Doctor (strategy ids, skip
  repeated failed fingerprints), error dashboard, verified repair memory, dependency and
  library intelligence, then developer-tools controller, Project Brain, Git/GitHub,
  extensions, Android Studio import. Later phases are not started by bypassing
  earlier ones, and one release does not attempt everything (README "Advanced Roadmap (v1.9 - v2.0)" lists unbuilt
  items; nothing unbuilt goes under "Shipped").
- Standing rules: offline first, no cloud model in the build; network only for Maven artifacts from Google Maven,
  Maven Central and JitPack; never download or run scripts, Gradle hooks or unsigned plugins; never put tokens or
  secrets in the APK or project; never add permissions silently. A tamper/licence "security kit" is out of scope.
- Signing: APK export = `ReleaseSigner` / `ApkSigner` (the owner: keep it untouched; unchanged); AAB export =
  `BundleSigner` since v1.8.5 (the owner's go; JAR signature only, SHA-256, `jarsigner` on JDK 17: "jar verified.").
  Upstream's kellinwood ZipSigner (SHA-1, "treated as unsigned") is no longer used for AABs.

## 3c. Java editor, Library Resolver, Code tab (v1.8.8; details in architecture-state.md)

- `com.sketchware.ai.editor`: `EditMarkers` + `EditMap` (a throw-away generation with `/*@SWED` markers around every
  event and block, through `SourceMapMarkers.wrap` and `Hx`'s empty-event hook; used only when marked minus markers =
  the normal generation; an empty event is matched with or without its blank line), `JavaMembers` (members, imports,
  stray lines, `hasComment` outside literals), `LineDiff` (Myers, O(D²) trace, `slide` for pure runs next to equal
  lines), `JavaToBlocks` (changed runs inside an event → one add-source block per run in the same stack; fields → type
  6 one line; imports → type 9; `public void _name(final T _p)` → MoreBlock; generated parts refused with their line;
  proof by regenerating and comparing, `Snapshot` restored on any Throwable), `JavaEditSession`, `JavaEditorActivity`
  (request 241), `CodeTabFragment`.
- `com.sketchware.ai.library`: `MavenResolver` (Google Maven, Maven Central, JitPack; POM rules like Gradle; built-ins
  from `BuiltInCoordinates` never downloaded; major-version mismatch with a built-in → note in the plan), `MavenSearch`
  (search.maven.org), `LibraryInstaller` (`<artifact>_V_<version>`, staged + renamed, D8 on 8+ / Dx below), `LibraryNeeds`
  (`needs.json`; `Dp` adds the built-in libraries local libraries need, `GradleExport` their coordinates),
  `LibraryResolverActivity` (Required / All / Updates / Cache, + Add).
- Export to Android Studio (`GradleExport.writeLocalLibraries`, v1.8.8): enabled local libraries with a recorded
  coordinate (needs.json, else source.json) become `implementation` lines; others go to `app/libs` as
  `local-<name>.jar` / `.aar` (with R.txt from `textSymbols`); JitPack is added to settings.gradle only when needed.
  Upstream never exported local libraries. A local library's `packageName` (list entry, from its `config` file) is its R
  package: the generated code imports `<pkg>.*` and the on-phone build makes an empty R class there, so a wrong package
  compiles on the phone and fails in Gradle. The installer writes it only from an .aar manifest; the export empties an
  import of a package with no class and no res.
- v1.8.9: `StatementBlocks` maps one statement line (depth 0, not in a block comment, the previous code line ending in
  `;` `{` `}`) to the block whose `Fx` output is exactly that line (setText, setHint, setVisible, setEnable, setClickable,
  setChecked, requestFocus, doToast, setTitle, setVarString/Int/Boolean, increaseInt/decreaseInt, intentSetScreen,
  intentPutExtra, startActivity; finishActivity only as the last line); parameter types from `lq.a(op)`, views by class
  info, intents = components of type 1, variables by eC type; String params unescaped, String variables as `getVar` blocks.
  `setHint` on a Material 3 outlined field can't be saved (routeHints rewrites it; refused, nothing changed).
- v1.8.9 `LibraryImports` (from `Jx.formattedCode`, so normal, marked and source-map generations agree): `import pkg.*;`
  after the last import for local-library packages whose public classes the code uses by simple name (comments, strings,
  text blocks ignored), never when a used name is also reachable on demand (android.jar incl. nested `View.*`, built-in
  library jars under `EXTRACTED_BUILT_IN_LIBRARIES_PATH`, java.lang, androidx.annotation, other library packages), and
  nothing at all when an on-demand package can't be indexed or android.jar isn't unpacked. The generator never unpacks
  android.jar (a build may be reading it): on a fresh install the first build's auto-fix adds single imports (warnings).
  `JavaToBlocks.useLibraryPackages`: such lines are generated, not custom imports. `LibraryInfo`: pom.xml (kept in the
  folder by `LibraryInstaller.keepPom`, else the cache), manifest permissions, every package (a.b.* doesn't cover a.b.c).
  `MavenSearch.solrQuery`/`normalize`: search.maven.org answers HTTP 400 to a raw colon; `g:a[:v]` and Gradle lines become
  `g:"g" AND a:"a"`; no hit or a failure → `maven-metadata.xml` from Google Maven and JitPack (JitPack paths are
  case-sensitive: keep the typed case). `Ox`: MaterialCardView (`Widget.Material3.CardView.Elevated` unless the inject has a
  style; theme surface colour, the canvas preview still white) and MaterialToolbar (no icon tint) only when `material3`.
  `ModernProjectDefaults.convertToModern`: data (`i()`→`l()`) and working copy (`g()`→`h()`→`k()`) separately, off the
  UI thread; the dialog fields set target 37 / min 21 / Material 3.
- Android's regex is ICU: an unescaped `}` is a syntax error on ART and fine on the JVM. Run every new regex on the ART
  harness with the APK's classes, not only in a JVM test.
- v1.8.8 needed three full suite runs (a URL field refused as a comment; then the export gap). Before starting the full
  suite on a release with a new feature, run that feature's own fixture end to end first, including Export AAB and
  Export to Android Studio with a Gradle build of the export: those are the paths a new feature is most likely to miss.

## 4. Apps built with Sketchware AI

Android 17 is **API 37**; Android 16 is API 36. Verify Play deadlines with a web search
before stating them. Defaults live in `ModernProjectDefaults`: new projects target 37, min
21, D8, Java 11, Material 3 AppTheme (`material3_theme`), dark theme on (`m3_dark_theme`), dynamic colour off
(`m3_dynamic_color`); old projects keep their stored values and are never raised silently (no key = MaterialComponents
Bridge theme). The bundled `android.jar` is **API 34**:
APIs newer than that can't be called from project code unless a custom android.jar is set (the helper reaches
API 35 methods by reflection). Java 17 is not possible with ECJ 3.26 (`engineering/java17-path.md`).
Whether a generated APK runs on some Android version depends on that project's min/target, its APIs and libraries;
a compile here proves none of it.

Material 3 theme (v1.8.6): `Theme.Material3.DayNight.NoActionBar` when `m3_dark_theme` is true (written for new
projects; absent = `Theme.Material3.Light.NoActionBar`, so v1.8.4/v1.8.5 projects stay light), project colours exact in
colorPrimary/colorPrimaryVariant/colorSecondary(=accent)/statusBar in both modes; every other M3 role from `M3Palette`
(Material Color Utilities `SchemeContent` of the primary colour, jar `app/libs/material-color-utilities-1.13.0.jar`
from material-1.13.0.aar, Apache-2.0; `values/m3_colors.xml` + `values-night/m3_colors.xml`, resources `sketch_m3_*`
because Material owns `m3_*`); on-primary/on-secondary white when contrast >= 3, else tone 10.
`viewInflaterClass` **AppCompatViewInflater** (never MaterialComponentsViewInflater: it makes every `<Button>` a
MaterialButton, which tints any own background, property colour, image or `setBackground` from code, with the primary
colour). PopupOverlay `ThemeOverlay.Material3` (DayNight) / `.Light`. FAB in the accent colour, icon on-secondary.
`m3_dynamic_color` (off by default): `DynamicColors.applyToActivityIfAvailable(this)` before `super.onCreate` in each
generated activity. User-picked colours, icons and setTextColor results stay as set at night (known limit; the Library
Manager's Dark theme row says so). The owner chose DayNight by default and dynamic colour as an opt-in switch; both
switches are Library Manager rows shown only while Material 3 is on (New Project screen: rows too, dynamic colour off).
24 Oct 2026 he confirmed a "purple toolbar" report was dynamic colour matching the wallpaper and that dynamic colour must
stay off by default so project colours are exact. An empty wrap_content VideoView fills the rest of the screen in black
(a SurfaceView): explain it, don't "fix" the theme.
Material 3 widgets (since v1.8.5, `M3Widgets`, decided once per generation): the generator writes Material classes as
tags: plain filled Button -> `<package>.SketchButton` (MaterialButton that drops its tint once a different background
is set from code), CheckBox/RadioButton -> MaterialCheckBox/MaterialRadioButton, Switch -> MaterialSwitch (+ Java field
types), plain EditText -> outlined TextInputLayout around `<package>.SketchTextField` (setHint routed to the label,
CharSequence and int overloads; visibility forwarded to the box; a wrap_content field measures at least its hint; layout
and transform attributes move to the box). `scanCode` reads the project's blocks/add-source/custom variables first and
keeps widgets plain when code uses the `Switch` type or thumb/track, moves a field or touches its layout params or
parent, or sets a button drawable/tint/icon; an injected `visibility` keeps an EditText plain. v1.8.6: a plain Button
whose only own background is an opaque colour is a SketchButton with `app:backgroundTint` (pill kept; default label
white/#1D1B20 by contrast) unless code calls `<id>.getBackground(`; outlined fields use the generated `AppTheme.TextField`
(8dp corners, the owner's ask; the M3 spec default is 4dp); SketchTextField measures the hint like the collapsed label
(bodySmall 12sp, letter spacing 0.4sp, box typeface + `fontWeightAdjustment`, +2dp). `yq` re-reads ProjectSettings at
every generation (DesignActivity keeps one yq; Library Manager switches write settings mid-session). Default text colours
become theme attributes (`colorOnSurface`, `colorOnSurfaceVariant` for hints, `colorOnPrimary` on a filled button). Not
applied when the user replaced styles.xml or an activity's Java file, nor in replaced layouts. Palette in M3 projects
(`editor.DesignTheme`): TextInputLayout `?attr/textInputOutlinedStyle`, MaterialButton no style. Never inject an explicit
`Widget.Material3.*` style: under a MaterialComponents theme (project switched back off) it throws InflateException
(ThemeSwitchTest in layoutlib). `FontCode` rewrites only whole-name receivers on one line
(`tools/jvm-tests/FontCodeRewriteTest`); any rewrite of generated code must keep every line where it is.
Material 3 Expressive: Material 1.13 has some Expressive components (MaterialButtonGroup, MaterialSplitButton,
LoadingIndicator, Docked/FloatingToolbarLayout) but no Expressive theme; the palette doesn't offer them yet (roadmap).

## 5. Toolchain facts (check live versions before quoting them)

In the app: D8/R8/L8 **8.11.18** + desugar_jdk_libs(_nio) 2.1.5 for projects, ECJ 3.26 (minApi26), embedded kotlinc
1.7.10 (reads Kotlin metadata up to 1.8; the IDE pins kotlin-stdlib 1.7.10 with `kotlin.stdlib.default.dependency=false`),
bundletool **1.18.3**, aapt2 **2.20** (android-16.0.0_r4, static, all four ABIs, 16 KB LOAD alignment; same resource tables
as 2.19). The IDE's own build: **AGP 8.12.0, Gradle 8.13, KGP 1.9.25, compileSdk 34**, target 34, IDE minSdk 26, JDK 17,
coreLibraryDesugaring in the IDE.
The bundled ProGuard 7.2.2 and the `ClassFileSanitizer` path stay (D8/R8 3.3.75 crashed with an NPE "String.length()" on
class files with nameless `MethodParameters` entries: javac 21 output, AndroidX since 2024, concurrent-futures >= 1.2,
error_prone >= 2.48). MDC-Android 1.14.0 (Material 3 Expressive, the last View feature release) needs min SDK 23 and
API 35 (androidx.core 1.16): its own migration. Compose (Material 2 or 3) can't be built on the phone (Kotlin 2.x Compose
plugin, compileSdk 37+, min 23).

Gradle here: Maven Central answers 429 often. The repo uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so put the
Google mirror first only in settings and buildscript repositories, with an init script
(`/root/.gradle/init.d/mirror.gradle`): a closure `front(repos, name)` that creates
`repos.maven { name = n; url = 'https://maven-central.storage-download.googleapis.com/maven2/' }`, removes it and
`repos.add(0, it)`; call it for `settings.pluginManagement.repositories`, `settings.dependencyResolutionManagement.repositories`
(in `settingsEvaluated`) and `buildscript.repositories` (in `allprojects`). No JDK 17 / 7z package until
`apt-get update` has run. SDK: cmdline-tools from dl.google.com, then sdkmanager.

## 5a. Built-in libraries (what a project gets without downloading)

`app/src/main/assets/libs/{libs.zip,dexs.zip,android.jar.zip,desugar.zip}` + `mod/jbk/build/BuiltInLibraries.java`. Extracted to `cacheDir/libs` when an asset's **size** differs from
the extracted copy (`Dp.hasFileChanged`), so a new archive must differ in size. Rules for an
update (`tools/builtin-libs`: coords.py -> solve.py -> package.py (sanitizes, dexes with the IDE's D8) -> genjava.py
-> check_graph.py; `BuiltInLibraries.orig.java` in the work dir = the previous release's file):
- Newest stable that passes, read from the published artifact: `minCompileSdk` <= 34 (bundled android.jar), library
  minSdk <= 21, Kotlin metadata <= 1.8, class files <= 55, dexable by the bundled D8 after the sanitizer. Material declares
  minCompileSdk 1 but its values-v34 resources need android-34: link the res set before shipping (compare-abis.sh).
- Same major for libraries the generated code is written against: OkHttp 3, Okio 1, Glide 4, Lottie 3.
- Dependency lists only grow: keep every dependency an entry had (project code may use a class that arrived
  transitively, e.g. SwipeRefreshLayout via legacy-support-core-ui); the new closure must be a superset of the old one
  for every component (`check_graph.py`, BAD 0).
- List an R package only when the library's `res/` holds a real resource. Licences are read from each POM (parent POM
  when empty) and recorded in the verification file.
- Held (not updated; newer ones need minSdk 23 and/or newer generated manifest code): Firebase, Play services (Ads
  20.1.0, Maps, Auth 19.0.0), FCM 19.0.0, OneSignal 3.14.0, Audience Network 5.9.0, YouTube player, OTPView, CodeView,
  PatternLockView, WaveSideBar.
- Kept on purpose: swiperefreshlayout 1.2.0-alpha01, slidingpanelayout 1.1.0 (1.2 drags androidx.window), circleimageview,
  listenablefuture. Dropped: error_prone_annotations, jspecify (annotation-only), empty -ktx/-jdk7/-jdk8 jars.
- Export to Android Studio (`GradleExport`) mirrors the built-in set: Maven coordinates where they exist (plus FCM
  messaging/iid, play-services-auth, OneSignal, Audience Network), otherwise `app/libs/<name>.aar` from libs.zip with an
  R.txt (`textSymbols`: aapt2 text-symbol format, values 0, enum/flag ids, styleable children android_ first; identical
  to aapt2's for the four libraries); the manifest copy loses its `package` attribute; compileSdk 36.
- Known gaps from upstream: BadgeView generates `com.allenliu.badgeview.BadgeView` (no library has it); Ads 20.1.0 has
  two optional `androidx.work.Worker` classes and WorkManager was never bundled. (The rewarded-ad import is generated
  since v1.8.5.)
- After any library change check fixture 907's classes.dex with dexdump.

## 5b. Sign-in and telemetry (reviewed, not built)

See `engineering/auth-telemetry-review.md`. Blockers: the Firebase project's
`google-services.json` for package `com.sketchware.ai`; a private release keystore (the public
test key must never be registered for OAuth); first ship sign-in as optional, make it mandatory only after the phone
confirms it. Facts checked Oct 2026 (re-verify before building): Android developer verification applies to sideloaded
apps on certified devices (first countries from 30 Sep 2026, global in 2027): package name and signing key must be
registered. Analytics, Crashlytics and Performance collect by default: ship them off, enable after consent. App Check
Play Integrity rejects sideloaded installs unless its advanced settings drop PLAY_RECOGNIZED/LICENSED. Account deletion
(in-app and web) is needed once accounts exist. Devices without Google Play services can't use Credential Manager's
Google sign-in: they need another path.

## 6. Building the APK here

1. JDK **17**, SDK at `/opt/android-sdk` (`platforms;android-34` (+30, 26 for the API scan), `build-tools;36.1.0`;
   `ndk;28.2.13676358` only to rebuild aapt2), `local.properties` with `sdk.dir=/opt/android-sdk`.
2. Bump `versionCode` and `versionName` in `app/build.gradle`, **commit, then build** (the
   version name carries the git hash).
3. `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon -q clean
   assembleMinApi26Debug compileMinApi21DebugJavaWithJavac`. Output:
   `app/build/outputs/apk/minApi26/debug/app-minApi26-debug.apk`, signed with
   `testkey.keystore` (SHA-1 61:ed:37:...:af:81, same every release, so it installs over the last one; v2 signature).

Network: Google Maven, Maven Central (GCS mirror), JitPack, `dl.google.com`, `android.googlesource.com`,
services.gradle.org, registry.npmjs.org, pypi work. github.com from the shell: git only for repos attached with
`add_repo`, `gh api` reads (and git pushes of branches) through the proxy; other GitHub sources: ask for zips. The static
aapt2 (`app/src/main/jniLibs/<abi>/libaapt2.so`) is built with `tools/aapt2-static/build.sh` (all four
ABIs; android-16 needs abseil + protobuf's own CMake, an aconfig header stub, static liblog); `compare-abis.sh` checks them
(qemu-user-static for ARM); the NOTICE step writes `assets/licenses/aapt2-NOTICE.txt` (update it whenever the tag or a
library changes). Don't replace them with v7's 4 KB-aligned binaries.

## 7. Release checks and delivery

All of these, on the final APK, before calling it done:

- `aapt2 dump badging` (package, version, min 26, target 34), `apksigner verify`,
  `zipalign -c -P 16 4`, every ELF in the APK (also inside nested zips) LOAD-aligned 0x4000,
  `tools/linkcheck/run.sh <apk>` ending in `TOTAL risky calls: 0` (then delete its `/tmp/tmp.*` dir by exact path; set
  `PLATFORM_JAR` to `platforms/android-30/android.jar` when android-36 is not installed), `tools/linkcheck/api_level.py`
  on the dexdump against android-26 (compare with the previous release by member signature, not by whole line), pinned
  resource IDs 0 mismatches (`aapt2 dump resources` prints `resource 0x7f.. type/name` under `type <t>`; compare with
  `public-stable-ids.txt`; 98 pinned names never appear). When only DEX files differ from an already checked build
  (compare zip entry CRCs), resources/assets/ELF results carry over; say so.
- The Android-runtime suite of section 9 with the final APK (not with override classes). If a fix changes the APK,
  rebuild and run the whole suite again on the new APK (two lanes in parallel on the 2-CPU sandbox, after one fixture
  has extracted the libraries; start lanes as tracked background jobs, not nohup). A background job is stopped after 2
  hours: run a lane in chunks of about five fixture builds (about 20 min each with two lanes) and start the next chunk
  when one ends. Never `pkill -f`/`pgrep -f` a pattern that appears in your own command line (it kills the calling
  shell): find processes with `ps -eo pid,comm` and kill by PID.
- `git format-patch <base>..HEAD`, `git am` in a worktree at `<base>`, same tree hash.
- **Round trip:** unzip the upstream zip as the worktree, commit it, `git clean -fdX`, `git am`, copy the (now empty)
  overlay; upstream + patches must equal the working `app/src` (`diff -rq --strip-trailing-cr`, 0 lines) and the tree
  hash. `/home/claude/package189.sh` did all of it for v1.8.9 (`R=` the checkout to package; `BASE=216c013`; write such
  scripts with literal paths or `"${VAR:?}/${X:?}"`: `rm -rf $VAR/...` is refused by the shell's safety check; when
  copying a script or README for the next version, replace `v1.8.7` style strings only: a blanket `187→188` once
  rewrote the upstream zip's sha256 inside the README). `git status --short --ignored | grep '^!! app/src'` must stay empty.
- Update `engineering/` (architecture-state, test-fixtures, compatibility-matrix, known-errors, reference-index,
  component-library-inventory, `v<version>-verification.md` with no placeholder left), `README.md` (Shipped vs Advanced
  Roadmap; test claims exactly as run), `CREDITS.md` and `app/src/main/assets/oss.txt` when a component changes,
  `releases/v<version>/` (release notes + SHA256SUMS for the APK; the APK and patches zip go to the `binaries` branch,
  since v1.8.9 as single files with Git LFS: GitHub refuses normal files over 100 MB). Commit the docs before
  packaging so the round trip includes them.
- Patches zip `SketchwareAI-v<version>-patches.zip`: `patches/`, `overlay/`, `engineering/`, `releases/`, `README.txt`,
  `README.md`, `CREDITS.md`, `COPYING`.
- **Delivery = GitHub (owner's rule since v1.8.6: "stop uploading 7z parts and patch text into the chat").** Work on
  local branch `main` (= GitHub main) and push it (plain fast-forward). Through this session's proxy: branch pushes
  work; tag pushes and branch deletions are refused (the connection drops / "Write access to this GitHub API path is
  not permitted"); creating a GitHub release returns HTTP 403 "not permitted for this session type". Don't retry or
  route around those, and don't push work-in-progress branches (they can't be deleted again). Built files go to the
  orphan branch `binaries` instead. **Since v1.8.9 (owner: "Single Full APK", no 7z parts): Git LFS.** The LFS batch API
  and its S3 upload host are reachable through the proxy (`git-lfs` is installed): a worktree of `binaries`,
  `git lfs install --local`, `git lfs track "apk/*.apk" "v<version>/*.zip"` (patterns per release in `.gitattributes`),
  copy `apk/Sketchware_AI_v<version>.apk` and the patches zip, update the README table, commit, `git push github binaries`
  (278 MB uploaded in seconds), then download `https://github.com/smartapps000-hub/SketchwareAI/raw/binaries/apk/...`
  and compare sha256 (GitHub serves the LFS file there). GitHub Free LFS quota: 10 GiB storage and 10 GiB download per
  month (about 70 downloads of a 138 MB APK); tell the owner when releases accumulate. The APK is not shrunk: R8 on the
  IDE was rejected in v1.8.9 (20.5 MB of the 45 MB dex is the embedded Kotlin compiler, found by reflection and
  extension-point XML; other savings ≈ 25 MB, still over 100 MB). Up to v1.8.8: `7z a -bd -v75m -mx0` (parts under
  GitHub's 100 MB file limit), add them with `GIT_INDEX_FILE` + `git read-tree binaries` + `update-index --cacheinfo` +
  `commit-tree -p binaries`, update its README table (sha256 of parts and contents), push `binaries` (one commit and push
  for the APK parts, one for the patches parts + README). Patches for the zip: `git format-patch 216c013..HEAD ^4addcfe`
  (216c013 = the upstream zip commit; the repository has a second root, 76bcbe8, GitHub's first commit, so never use
  `git rev-list --max-parents=0` unquoted; the GitHub repository's 3 first commits are excluded; merges are skipped).
  Work on the next release in a worktree (`/home/claude/work/v189`) branched from main; after amending main's docs commit,
  move it with `git rebase --onto main <old docs commit> <branch>` (a plain rebase replays the old docs commit); when the
  release is done, `git merge --ff-only <branch>` on main and push.
  The first v1.8.6 push uploaded the 260 MB history in chunks of 10 commits to a temporary branch first. Check
  `git ls-remote github` and `gh api repos/smartapps000-hub/SketchwareAI/contents/<path>?ref=<branch>` afterwards; tell
  him he can make a tag/release on github.com himself if he wants one. Chat file sends only if he asks (they cap at
  30 MB: `7z a -bd -v28m -mx0`, extract once and `cmp`; he opens `.7z.001` in ZArchiver).
- Evidence images (renders) are sent as files; he reviews images, not explanations.
- Reply: what changed, how it was checked and where, what was not run, what to send back
  if it fails, and any decision only he can make. Short; detail goes in the delivered files.

## 8. Working with the owner

He writes very long directive specs (often pasted several times, sometimes cut off) and
sends screenshots with hand-drawn marks; the marks point at the real complaint. Read for
intent: past specs had wrong API/codename pairs and impossible asks. Requests like "compileSdk 34 toolchain"
mean the on-device toolchain user projects are built with, unless he names the IDE's own Gradle build; say which in one
line. Say when an attachment is cut off. Don't re-ask for files that are already in the workspace. Treat "you faked it"
as a signal to re-trace the exact path, not to argue. Correct factual slips once, explain a deviation in one line, and
never claim a library version, a device test, or a fix that was not exercised. No manual steps for him: the build must
do the repair itself. When he says to leave something untouched (v1.8.4/v1.8.5: APK signing), leave it and report what
the checks found in it. When a reviewed finding conflicts with something he asked for (v1.8.5: "light/night contrast" vs
DayNight hiding user colours), ship the safe behaviour, say why in one line, and put the choice to him. When he says
"finish the release first, then push", don't push or touch GitHub until he says so. Since v1.8.6 he wants every release
pushed to GitHub (section 7), not 7z parts in the chat. He suggested aider-chat/tree-sitter and the
mytechnotalent/rea repository: not needed (git diff/format-patch are exact; Material's own sources and Material Color
Utilities are public, nothing to reverse-engineer); say so in a line. No Android emulator can run here (no /dev/kvm):
layoutlib renders are the visual check. Agreed scope (23-24 Oct 2026): v1.8.7 New Project screen, v1.8.8 Java editor +
Library Resolver + Code tab; not doing Gradle on the phone, Kotlin blocks, Compose, GitHub commit from the app. 25 Oct 2026 (v1.8.9): "R8-shrink the
IDE / Blocks + libs + M3 tags / Offline from POM/AAR"; R8 turned out unsafe (section 7), single APK via Git LFS instead.

He sometimes sends the same "master architectural directive" (25 rules: 2-column drawer, puzzle FAB, touch thresholds
8/28/48dp, Liquid Glass tokens, AAPT2/ECJ/D8/apksig, target 37 / min 21, orphan-block tolerance, skill sync, licensing
audit) for this fork. Most of it describes AiTechApp; in this fork those parts are upstream's precompiled editors and
the existing toolchain. Don't rebuild or "skin" them: say so in one line and do the concrete steps. Licence facts
(README/CREDITS, agreed with him for v1.8.5): upstream is source-available, not open source (`LICENSE`: personal use);
the `kotlinc` module (from CodeAssist), `build-logic` and the minApi26 flavour (the shipped APK) are GPL-3.0; AOSP code
Apache-2.0; R8 BSD-3-Clause; desugar_jdk_libs GPL-2.0 with Classpath Exception; ECJ EPL-2.0; sora-editor LGPL-2.1;
fonts OFL-1.1; Material Color Utilities and Material icons Apache-2.0; other bundled libraries per their POMs. The fork's
own code is GPL-3.0 (his choice, v1.8.6; `COPYING`); upstream files keep `LICENSE`, so the repository as a whole is not
open source; README says so.
New code copies nothing from AiTechApp and nothing wholesale from 7.0.0; test fonts (OFL) are never committed or
shipped (make-test-fonts.py recreates them).

## 9. Android runtime harness (ART, API 31, no framework)

Everything is in the repo, `tools/art-harness` (README there):

1. `setup-art.sh <work dir>` once per workspace (needs debugfs from e2fsprogs): downloads the Android 12 x86_64 system
   image (`x86_64-31_r03.zip`), whose `system.img` is a GPT disk with a `super` partition: the script reads the liblp
   metadata, copies out the `system` logical partition, takes `/system/{bin,lib64,etc}` and the apexes
   `com.android.{art,runtime,i18n,tzdata,conscrypt,os.statsd}` (some are `.capex`: `original_apex` inside,
   `apex_payload.img` inside that) to `/system` and `/apex/<name>`. Delete the downloaded image files afterwards
   (several GB; v1.8.5 filled the disk).
2. `mk.sh` builds `out/stubs.zip` (framework stand-ins: Context, SharedPreferences, Environment, Build SDK_INT 31, Log,
   Uri, Base64 ...), `out/harness.zip` (`src/h`) and `out/android-dex.zip` (the bundled android.jar dexed; its other
   classes throw `Stub!`, add a stub when one is hit). `run.sh`'s classpath: stubs, harness, **the APK itself** (not only
   its dex: ECJ loads Java resources from it), android-dex; `art.sh` unsets `JAVA_HOME`/`JAVA_TOOL_OPTIONS` and runs
   dalvikvm64 `-Xint`. Don't rebuild these zips while an ART job runs (build into another file name and `mv` it over the
   old one: running jobs keep the old file). `Fixtures.java` writes projects with the IDE's own classes (`lC.a`, `wq.a`, `oB`,
   `ModernProjectDefaults.applyToNewProject`, managers from `jC.b/c/a/d`; views via `eC.a(xmlName, ViewBean)` —
   `eC.a(ProjectFileBean, ViewBean)` REMOVES; palette defaults from `IconDefaults`; save `kC.a(); hC.m(); eC.j(); kC.x();
   iC.l()`). `Main.build` is DesignActivity.BuildAsyncTask minus the Activity (prepare, LibraryIntegrity, generate
   `yq.b/f/e`, `Dp`, aapt2, built-in libraries, resources, Kotlin, Java, dex, merge, package, sign); `Main.exportAab`
   mirrors ExportProjectActivity's Export AAB (with `BundleSigner`); `Main.exportSource` = Export to Android Studio
   (`yq.h` + `GradleExport.writeWidgetLibraries` + since v1.8.8 `writeLocalLibraries`). Every step the real task has must
   be in them (v1.8.3 first missed Kotlin). The reflection helper `call` matches argument types exactly: pass a `File`
   where the method takes one (`projectMyscPath` is a String). `main` ends with `System.exit` (kotlinc leaves non-daemon
   threads).
3. Inputs once per workspace: `make-test-fonts.py out/fonts` (fixture 909; npm @fontsource, fontTools), and
   `make-native-fixture.sh out/native-fixture` (fixture 910; NDK clang).
4. `suite.sh <IDE apk> <tag> libs:901 held:902 plain:903 kotlin:904 autofix:905 typo:906 min19:907 m3:908
   telugu:909 jni:910 desugar:911 m3code:912 m3dark:913` (one log per fixture in `out/logs`; a spec name ends up in a
   package name, so never a Java keyword such as `native`), or `run.sh <apk> <root> create <spec> <id>` /
   `build <id> [resume step]` / `aab <id>` / `export <id>` (Export AAB with the test key; it deletes `mysc/<id>`, so copy
   the debug APK and `mysc/<id>/sourcemap.json` first; output `<root>/signed_aab/<name>.aab`). Projects live in the shared
   `/storage/emulated/0/.sketchware`, so a side test uses its own id (e.g. 921) and its own root; an AAB and an export of
   the same id must not run at the same time (both write `mysc/<id>`).
   v1.8.9 suite scripts: `/home/claude/harness189.sh` (rebuild `out/harness189.zip` from the worktree's harness sources
   after every Fixtures/JavaEdit change: a lane once ran the old zip and reported 51 checks instead of 54),
   `run189.sh`, `lane189.sh <apk> <tag> <out> spec:id ... aab:id export:id je:id jeopen:id,id,...` (lanes of about 6
   fixtures: a background job stops after 2 h; a fresh `<tag>` = a fresh cache dir, i.e. a fresh install), `loadlane188.sh`
   with `LF=<folder of built APKs>`. Fixtures 922 `escpos` (local library ESCPOS from `out/native-fixture/escpos`, code
   without import blocks) and `libinfo <folder> <coordinate>` (LibraryInfo text). bundletool `validate` from the scratchpad
   jar needs its dependencies on the classpath (guava, protobuf-java, dagger, javax.inject, jose4j, failureaccess,
   aapt2-proto, auto-value-annotations, slf4j-api, gson, jsr305 from the Gradle cache) and main class
   `com.android.tools.build.bundletool.BundleToolMain`.
   Check AABs with `/home/claude/bt/check-aab.sh <aab> <package>` (bundletool 1.11.3, 1.17.2, 1.18.3 resolved with a tiny
   Gradle project, classpaths in `cp*.txt`: validate, `build-apks --mode=universal --aapt2=<build-tools aapt2>`,
   `jarsigner -verify`); for 16 KB also `dump config` and `build-apks --device-spec` (arm64, API 35) + `zipalign -c -P 16`
   on the ABI split. AAB file names come from the project name (`Fixture_<spec>.aab`, templates `np<x>.aab`): `ls` the
   folder rather than guessing. Build exported projects with `/home/claude/gradle-export-check.sh <export dir> <work dir>`
   (Gradle from the wrapper properties, JDK 17, `--warning-mode all`) and dexdump the APK: every library R class
   referenced must be defined. `art.sh <cp>:<904 apk> h.CallKt` (`A, B`), `<cp>:<911 apk> h.CallDesugar`
   (`2026-11-01|120|2|X,Y,Z|Y|abab|q|y|3`), `h.L8Race` (L8 from two threads, min SDK 23).
   These helper scripts live outside the repo (`/home/claude`): recreate them from these descriptions when missing.
5. `art.sh <cp> h.LoadAll <built app apk> <package>`: loads and links every class of a built app. Expected failures:
   coroutines `AgentPremain` x2 (JVM-only); fixture 902 also the 2 Ads 20.1.0 offline Worker classes (WorkManager not
   bundled).
6. Facts that cost hours: ART aborts with `-Xmx` above ~1.5 GB; every linker namespace needs `visible = true`;
   `/system/etc/public.libraries.txt` must not be empty; `Dp`'s `SystemLogPrinter.start()` sends System.out/err to
   `/.sketchware/debug.txt`, so the harness prints to `FileDescriptor.out`; `chmod +x` the extracted `libaapt2.so`
   (since v1.8.6 `run.sh` extracts it per run into `out/native-run/<pid>`: two lanes sharing one folder made a run execute
   a half-written file, error=13);
   never `pkill -f <script>` (it matches and kills the calling shell; kill by PID); before re-creating a fixture id
   delete `data/<id>`, `bak/<id>`, `mysc/<id>`, `mysc/list/<id>` (suite.sh does; a left-over `bak/<id>` adds every view
   twice: "Duplicate field"). No IPv6: downloads run on JVM 17. A fixture build takes 5-25 minutes (911's dex step with
   L8 about 8 minutes in the interpreter); an export 15-30; wait with one background `until grep ...` loop, not
   repeated short checks (foreground `sleep` is blocked). Force a failure with a same-size corrupted file (the
   extraction checks sizes only). The disk is about 250 GB with a small free share: delete superseded harness roots
   (`out/root-<tag>`, 160-200 MB each), old fixture APK folders and round-trip trees before packaging.
7. Suite to keep green (`engineering/test-fixtures.json`): 901-913 (906 must fail with the block origin; 905 must
   compile with the unreachable-code repair and an autofix.json proposal; 907's classes.dex must hold MultiDex and the
   project's SketchApplication; 908 Material 3 tags; 909 SketchFonts + OTF data + exact source map; 910 exactly two
   warnings (its two 4 KB libraries) and every .so in APK and AAB (arm64 split: .so Stored, `zipalign -c -P 16` ok);
   911 desugared library (223 `j$` classes in v1.8.5) + CallDesugar; 912 scanCode keeps code-touched widgets plain and
   compiles; 912 also stands for a v1.8.5 Material 3 project (dark setting empty: Light theme, no values-night); 913
   `m3dark`: DayNight + values-night, dynamic colour line, tinted buttons, button4 kept plain by `getBackground()`;
   Export AAB 908-913 `jar verified.`; exports 902/908/911/913 build with Gradle 8.13; v1.8.7: 914-918 `np*` templates
   created by `NewProjectSetup.create` (Fixtures.createLikeNewProjectScreen), each must generate `AppCompatActivity`, AAB
   915/917, export 915 (`drawable/fab_add`); v1.8.8: 919 `javaedit` (`run.sh <apk> <root> create javaedit 919`, then
   `javaedit 919`: CHECK lines, `JAVAEDIT n/n ok` (43 in v1.8.8), then a build), 920 `resolver` (MavenResolver +
   LibraryInstaller with POMs/archives from `out/maven-cache`, filled by `tools/jvm-tests/MavenResolverTest <cache>`;
   delete `libs/local_libs/retrofit_V_2.9.0`, `converter-gson_V_2.9.0` first so it installs fresh), AAB 920, export 920
   built with Gradle (Retrofit by coordinate, 114 `retrofit2` classes), `javaedit-open <ids>` (every activity opens
   convertible and saves unchanged, 20/20); v1.8.9: 919 54 checks (statements to blocks), 922 `escpos` (automatic
   imports; export built with Gradle), 924 = 922 on a fresh `<tag>` (auto-fix adds the imports, 3 warnings), jeopen 21/21). Lanes:
   `/home/claude/lane188.sh <apk> <tag> <outfile> <items>` with items
   `spec:id`, `aab:id`, `export:id`, `je:id`, `jeopen:id,id`, through `/home/claude/run188.sh` (the worktree's harness
   built into `out/harness188.zip`); `/home/claude/je.sh <apk> <id> ...` runs editor classes from the worktree in front of
   an APK (dev loop); `/home/claude/exportvariant.sh <apk>` exports 920 with two coordinate-less library copies.
   `LF=<apk folder> /home/claude/loadlane188.sh <out> id:package ...` (items `kt:x` = CallKt on 904, `ds:x` =
   CallDesugar on 911) runs LoadAll (seconds per app; one job once stalled for hours: each run has `timeout 900`).
   Recreate these from this description if missing. Older fixtures
   650/651/660/png/670 and 639-643 were made by lost scratch programs: recreate them in `Fixtures.java` when touching the
   source map, write-back, library integrity or PNG repair.

## 9b. layoutlib renders

`tools/layoutlib-renders` (Gradle 8.11, AGP 8.7, Paparazzi 1.3.5, Material 1.13): Android 14's own view, theme and text
code (Minikin + HarfBuzz) on the JVM. Tests write full-resolution PNGs with `Bitmap.compress` (Paparazzi's own
snapshots are downscaled to ~1000 px). `TeluguFontsTest` measures ink cut/overlap per line, `ShapingTest` +
`compare_shaping.py` compare Android's shaping with HarfBuzz glyph by glyph, `M3ThemeTest`/`OldThemeTest` render the
generated themes, `Switch*Test` inflate palette widgets under each AppTheme in its own session (a ContextThemeWrapper over
the session theme keeps the session's attributes and hides inflate failures; a failed view shows as a MockView with the
InflateException in the test report), `M385Test` (`M385LightTest`/`M385NightTest`) inflates the generated Material 3
widgets through the theme's inflater with a Factory2 on light/night device settings, checked/unchecked, and SketchButton
vs MaterialButton with a background set from code. v1.8.6: `M386Test` (fixture 913's widgets under the generated
DayNight theme with M3Palette colours written by running M3Palette on the JVM), `LabelTest` (outlined field label width
vs the space CollapsingTextHelper gives it, read by reflection; font scale via `DeviceConfig.copy(fontScale=)`, bold text
via `resources.updateConfiguration` with `fontWeightAdjustment` — `createConfigurationContext` returns null in
layoutlib), `DialogClipTest` (IDE dialog layouts with plain shapes for the glass drawables, before/after). v1.8.7:
`NewProjectScreenTest`, `PresetRenderTest` (+ `import_generated.py`), `VideoViewTest`; v1.8.8: `V188ScreensTest`
(`setup_v188.sh <repo>` after `setup_newproject.sh`: Library Resolver tabs and + Add, Java editor, Code tab); v1.8.9:
`Preset915Light/Night/Nav` after re-importing 915 (MaterialCardView/MaterialToolbar; Nav sets the navigation icon from
code and writes its tint to `out/np915_nav.txt`). The working
copy of the module is `/home/claude/pz` (test output paths are absolute there). layoutlib
runs no animations: set checked states before the first layout and call `jumpDrawablesToCurrentState()`. Use it for
every new or changed layout, theme, text helper or generated widget and send the image.

## 10. Keeping this skill current

This skill holds rules and pointers; `engineering/` in the repo holds the state. At the end of
every release, and whenever a fact here turns out wrong, propose the complete updated SKILL.md
with the skill review card in the same turn (skills can't be written silently; the owner saves the card; read the whole
current SKILL.md first and keep what still holds). Never let the release list, tokens, fixtures or toolchain facts here
fall behind the latest patches zip.

## 11. Separate project: AiTechApp

AiTechApp (`com.aitechapps.ide`, Kotlin/Compose, Apache-2.0) is the owner's clean-room rewrite.
Its AGENTS.md forbids loading this skill or opening this fork in an AiTechApp session, and no
code, names, layouts or assets move between the two. Its Compose block canvas, 2-column drawer,
puzzle button and AAPT2 -> ECJ -> D8 pipeline are its own and are not part of this fork. Work on
it in a separate session from its own repo bundle.


## Project Identity & Target Runtime
- Project: Sketchware AI (Visual Mobile IDE)
- Repository: smartapps000-hub/SketchwareAI
- Target SDK: Android 17 (API 37) | minSdk: 26
- Memory Alignment: Strictly enforce 16 KB page alignment for all compiled ELF/SO native binaries.

---

## 1. Compiler Toolchain & Fast-Build Standards (Target: 3-5s)
- **Incremental D8 Compilation:** Never re-dex untouched libraries or classes from scratch. Cache intermediate DEX outputs and perform incremental merging via `--incremental`.
- **AAPT2 Daemon & Flat Caching:** Cache pre-compiled `.flat` resource files in the project cache directory. Only recompile XML/drawables when their timestamp changes.
- **R8 / ProGuard Optimization:** Use modernized shrinking rules without breaking dynamic reflection used by plugins.

---

## 2. Java AST Visual Block Parser Rules
- **Silent & Zero 'Add Source' Policy:** Raw Java code must convert directly into native visual blocks wherever a matching block definition exists.
- **Nested Method Chains:** Support nested calls such as `textview1.setText(edittext1.getText().toString());` as nested visual blocks (`setText` containing `getText`).
- **Auto-Variable Registration:** When encountering undeclared variables (`String x = ...`, `int y = ...`, `boolean z = ...`), the parser must automatically register the variable name and type into the project's variable registry in the background and output native variable blocks.
- **In-Place Source Code Viewer (`</>`):** Provide accessible syntax highlighting matching M3 dark contrast and allow in-place editing/pasting with instant AST re-parsing.

---

## 3. Silent Library Resolver Architecture
- **Highest Version Wins:** When resolving dependency trees, never downgrade an existing higher version to satisfy a lower transitive dependency (e.g., maintain animal-sniffer 1.28).
- **Silent Toast Notifications:** Do not use blocking alert dialogs for successful downloads/enables. Display a lightweight Toast notification and set the switch status to active with a green `✓ Installed` indicator.
- **Strict Error Boundary:** Show an alert dialog only when an artifact cannot be located in any configured repository (Maven Central, Google Maven, JitPack).
- **Skip Non-Android Artifacts:** Automatically exclude BOMs, POM-only roots, and metadata-only dependencies without throwing build warnings.

---

## 4. Multi-Device Canvas & Responsive Engine
- **Canvas Viewports:** Provide dynamic viewport switching on the top header:
  - Mobile (Compact Portrait)
  - Foldable / Flip (Tabletop Flex Mode, 600dp dual-pane)
  - Tablet (Expanded Landscape, sw720dp)
  - Wear OS (Circular 454x454 with Rotary navigation)
  - Android TV (1920x1080 Landscape with D-Pad focus)
- **Adaptive Window Size Classes:** Automatically apply Material 3 Compact, Medium, and Expanded layout metrics.
- **Target Manifest Injection:**
  - Inject `android.hardware.type.watch` for Wear OS builds.
  - Inject `android.software.leanback` for Android TV builds.

---

## 5. Material 3 Complete Ecosystem
- **HCT Dynamic Theming:** Integrate `material-color-utilities` to support dynamic tonal palettes, elevation, and tonal shadows.
- **Component Stack:** Enforce `androidx.constraintlayout:constraintlayout:2.2.0+` and `com.google.android.material:material:1.12.0+`.
- **Assets Pipeline:** Preserve legacy drawables while integrating Google Material Symbols and variable font loaders (including Anek Telugu).

---

## 6. Verification & Test Suite
Every release candidate must pass all automated test lanes prior to pushing:
- **Fixture 919:** AST block conversion and nested chaining checks.
- **Fixture 920:** Clean 14-step zero-warning build validation.
- **Fixture 922:** ESCPOS hardware & peripheral library integration.
- **Fixture 908:** Material 3 save, hint verification, and clean build.
- **Ten-Project Open/Save Lane:** Verify zero corruption when batch opening and saving existing projects.
