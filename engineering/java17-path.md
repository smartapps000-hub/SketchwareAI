# Java 17 toolchain path

Audited: commit `c82aba4` (v1.8.1), APK `app-minApi26-debug.apk` (sha256 `2db64781…bc79b`). Date: 2026-10-04.
**All results here are JVM-on-Linux or ART-API-31-on-Linux evidence. None is on-device evidence.**

## Summary

- Bundled today: **ECJ 3.26.0** (v20210609-0549), **D8/R8 3.3.75**, **android.jar API 33**, and no core-library desugaring for user apps.
- **Truly supported today:** Java 7, 8, 9, 10 and 11 language features. Every one of them compiled with the bundled ECJ at the level the dropdown passes, dexed with the bundled D8 at min API 21 and 26, and ran on the API 31 ART runtime. The dropdown values `1.7, 1.8, 1.9, 10, 11` are real.
- **Java 17 is not supported, and adding "17" to the dropdown would be fake:** the bundled ECJ answers `Unrecognized option : -17`.
- ECJ 3.26 also accepts `-12` to `-16`. Switch expressions (14), text blocks (15) and pattern matching for instanceof (16) compiled, dexed and ran. **Records do not compile** (`java.lang.Record cannot be resolved to a type`: the bundled android.jar is API 33). **Sealed classes** are a preview feature in this ECJ and the bundled D8 rejects them.
- A real Java 17 profile needs four things: a newer ECJ **that runs on Android** (3.28.0 and 3.33.0 crash on API 31 ART), a newer R8 (8.5.35 worked in a prototype), an android.jar of API 34 or newer, and a change in how D8 is called.
- Side finding: Build Settings (Java version, dexer, android.jar path) are stored under `/.sketchware_ai/data/<sc_id>/build_config`, not under `/.sketchware/` like everything else.

## How this was checked

Labels as in android15-audit.md: VERIFIED (source), VERIFIED (experiment here), DOCUMENTED (URL), NOT TESTED, UNKNOWN.

Scratch directory (does not survive a new workspace): `/tmp/claude-0/-home-claude/45d69778-7163-56e9-aa30-4ad415926e33/scratchpad/audit182/` — sources in `java/src/`, scripts `java/run_matrix.sh`, `java/run_d8.sh`, tables `logs/compile.tsv`, `logs/d8.tsv`, logs `java/logs/*.txt`.

Tools used, all taken from the project:

| Tool | Where it comes from |
|---|---|
| ECJ | `ecj-3.26.0.jar` from the Gradle cache (the artifact of `app/build.gradle:137`); and the copy inside the APK's dex |
| D8 | `r8-3.3.75.jar` from the Gradle cache (`app/build.gradle:119`); and the copy inside the APK's dex |
| android.jar | `app/src/main/assets/libs/android.jar.zip` → `android.jar` |
| lambda stubs | `app/src/main/assets/libs/core-lambda-stubs.jar` |

Two environments:

1. **JVM 17** (`/usr/lib/jvm/java-17-openjdk-amd64`): a 12-line wrapper `W` that calls `new org.eclipse.jdt.internal.compiler.batch.Main(out, err, false, null, null).compile(args)`, the same call as `a/a/a/Dp.java:620-626`.
2. **ART, Android 12 (API 31), x86_64, no Android framework** (the harness of compatibility-matrix.md): the same wrapper, with **the APK itself on the classpath**, so the ECJ that ran is the one that ships.

Arguments, same shape as `Dp.java:562-576`: `-<level> -nowarn -d <out> -cp <android.jar>:<core-lambda-stubs.jar> -proc:none <source dir>` (no lambda stubs at `1.7`, as in `Dp.java:290-294`).

D8: a driver with the same builder calls as `mod/jbk/build/compiler/dex/DexCompiler.java:52-59`: `setMode(RELEASE)`, `setIntermediate(true)`, `setMinApiLevel(n)`, classpath as library files, `OutputMode.DexIndexed`. Run on JVM 17 with `r8-3.3.75.jar` for every case. For a sample of four cases it was also run on ART with the APK's own D8; the three that succeeded gave byte-identical dex files (`logs/extra_d8_on_art.txt`).

Each dex was then started on the API 31 ART runtime (`dalvikvm64 -cp classes.dex t.Fxx`) and had to print its "ok" line.

**Limit of the JVM run:** at level 9 and above, ECJ on a JVM always takes `java.*` from the JDK's own module image and then rejects the same packages in android.jar. Decisive line (`java/logs/ecj_jvm_11_f07_j9_list_of_library.txt`):

```
The package java.util is accessible from more than one module: <unnamed>, java.base
```

`-bootclasspath` is refused at these levels (`option -bootclasspath not supported at compliance level 9 and above`). ART has no JDK module image, so there the classes come from android.jar, as on a phone. For levels ≥ 9 the ART run is therefore the meaningful one. Where both environments compiled a case, the class files were byte-identical (88 of 88 cases, `run_d8.sh` output).

## 1. What is bundled

| Part | Version | Evidence |
|---|---|---|
| ECJ, flavor minApi26 (shipped) | **3.26.0**, `Bundle-Version: 3.26.0.v20210609-0549`, built for Java 11 | `app/build.gradle:137`. `META-INF/MANIFEST.MF` of the jar (sha1 `4837be60…`). Inside the APK: `org/eclipse/jdt/internal/compiler/batch/messages.properties` → `compiler.version = v20210609-0549, 3.26.0`. VERIFIED (experiment here). |
| ECJ, flavor minApi21 | 3.13.0 | `app/build.gradle:136`. Not shipped, not tested here. |
| D8 / R8 | **3.3.75** | `app/build.gradle:119`. `java -cp r8-3.3.75.jar com.android.tools.r8.D8 --version` → `D8 3.3.75 (build d6dfa2fced8f…)`. The string `3.3.75` is in `classes34.dex` of the APK. |
| Dx (legacy dexer) | source in `mod/agus/jcoderz/dx/**` | Used when the dexer setting is "Dx" (`Dp.java:235-260`). |
| android.jar | **API 33** | `BuildWarnings.java:24`. In the jar: highest `android.os.Build$VERSION_CODES` constant is `TIRAMISU = 33`; `java/lang/Record.class` is absent. 27,138,945 bytes, dated 2022-07-07. |
| core-lambda-stubs.jar | `java.lang.invoke` stubs (LambdaMetafactory and 7 more classes) | `unzip -l`. Added to the classpath for every level except 1.7. |
| Core library desugaring for user apps | **not available** | `desugar_jdk_libs` appears once in the repo: `app/build.gradle:143`, for the IDE's own build. `DexCompiler.java:52-59` passes no desugared-library configuration to D8. |

## 2. What the Build Settings choices pass

| Dropdown value (minApi26) | Argument given to ECJ | Class file version produced |
|---|---|---|
| `1.7` | `-1.7` | 51 |
| `1.8` | `-1.8` | 52 |
| `1.9` | `-1.9` | 53 |
| `10` | `-10` | 54 |
| `11` | `-11` | 55 |

- The list comes from `app/src/minApi26/java/mod/hey/studios/build/BuildSettingsDialogBridge.java:12-20` (minApi21: `1.7`, `1.8` only, `app/src/minApi21/…/BuildSettingsDialogBridge.java:12-17`).
- The argument is built as `"-" + javaVersion` (`Dp.java:563-565`). No `-source`, `-target`, `--release` or `-bootclasspath` is passed. android.jar goes on `-cp` (`Dp.java:263-333`).
- When a project has no stored value: `11` on an API 26+ device, `1.7` below (`ModernProjectDefaults.java:95-97`). New projects get `11` and D8 written at creation (`ModernProjectDefaults.java:57-63`). So an old or imported project with no stored value is also built with D8 and Java 11 on the owner's phone; the class comment at `ModernProjectDefaults.java:13-15` ("Dx, Java 1.7") does not match the code.
- Fallback: if a build at `1.9`/`10`/`11` fails with a "system library" kind of error or the compiler crashes, the build is repeated at `1.8` and a warning is written (`Dp.java:638-658`, `ModernProjectDefaults.java:100-118`). On the API 31 ART runtime `-11` compiled directly and the fallback was not needed. On the owner's Android 15 phone: NOT TESTED (the device test plan checks for the warning text "Java 11 couldn't be used").
- **Where the value is stored:** `BuildSettings.getPath()` returns `/.sketchware_ai/data/<sc_id>/build_config` (`mod/hey/studios/build/BuildSettings.java:32`; the string `/.sketchware_ai/data/` is in `classes26.dex` of the APK). `ProjectSettings` uses `/.sketchware/data/<sc_id>/project_config` (`ProjectSettings.java:97-99`). So a `build_config` that sits in `/.sketchware/data/<sc_id>/` (a project restored from Sketchware Pro) is not read. VERIFIED (source). Effect on the phone: NOT TESTED. This does not match architecture-state.md ("Storage is the original layout").

## 3. Results: bundled ECJ → bundled D8 → run on ART API 31

"Lowest level" is the first level at which the bundled ECJ accepts the source. "Dropdown" says whether that level can be chosen in Build Settings today. Every "yes" in the D8 columns holds for all levels from the lowest one up to 16 (224 dex runs, all "ok", `logs/d8.tsv`).

| # | Feature | Kind | Lowest level | Dropdown | Compiles | Error just below that level | D8 min 21 | D8 min 26 | Ran on API 31 ART | Needs from the Android runtime |
|---|---|---|---|---|---|---|---|---|---|---|
| F01 | diamond, switch on String, try-with-resources | Java 7 language | 1.7 | yes | yes | — | yes | yes | yes | nothing |
| F02 | lambda | Java 8 language | 1.8 | yes | yes | `Lambda expressions are allowed only at source level 1.8 or above` | yes | yes | yes | nothing (D8 turns it into a class `F02$$ExternalSyntheticLambda0`) |
| F03 | method reference | Java 8 language | 1.8 | yes | yes | `Method references are allowed only at source level 1.8 or above` | yes | yes | yes | nothing |
| F04 | default and static interface methods | Java 8 language | 1.8 | yes | yes | `Default methods are allowed only at source level 1.8 or above` | yes | yes | yes | nothing (at min 21 D8 adds a companion class `F04$Greeter$-CC`; at min 26 it does not) |
| F05 | `java.util.stream`, `Optional` | Java 8 **library** | 1.8 | yes | yes | — | yes | yes | yes | **API 24+**. D8 does not rewrite it: the dex calls `java.util.List.stream()` directly. On API 21-23 it would fail at runtime: NOT TESTED (no such runtime here). |
| F06 | private interface method | Java 9 language | 1.9 | yes | yes | `Abstract methods do not specify a body`; `Illegal modifier for the interface method p; only public, abstract, default, static and strictfp are permitted` | yes | yes | yes | nothing |
| F17 | string concatenation with `+` | — | 1.7 | yes | yes | — | yes | yes | yes | nothing. ECJ 3.26 emits `StringBuilder` calls even at `-11` (checked with `javap`), not `invokedynamic`. |
| F07 | `List.of`, `Map.of` | Java 9 **library** | 1.8 | yes | yes | — | yes | yes | yes | Platform has them from API 30. Not needed: D8 replaces the calls with its own backport classes (`F07$$ExternalSyntheticBackport0…3`, calling `Collections.unmodifiableList`), at min 21 and at min 26. Below API 30: NOT TESTED at runtime. |
| F08 | `var` for local variables | Java 10 language | 10 | yes | yes | `var cannot be resolved to a type` | yes | yes | yes | nothing |
| F09 | `var` in lambda parameters | Java 11 language | 11 | yes | yes | `'var' is not allowed here` | yes | yes | yes | nothing |
| F10 | `String.repeat`, `isBlank`, `strip` | Java 11 **library** | 1.7 | yes | yes | — | yes | yes | yes | Platform has them from API 33. Not needed: D8 adds backport classes (`F10$$ExternalSyntheticBackport0…2`); the dex has no direct call to them. That is why it ran on API 31. |
| F11 | inner class reads a private field (nestmates) | Java 11 class-file feature | 1.7 | yes | yes | — | yes | yes | yes | nothing |
| F12 | switch expression, `yield` | Java 14 language | 14 | **no** | yes | `Switch Expressions are supported from Java 14 onwards only` | yes | yes | yes | nothing |
| F13 | text block | Java 15 language | 15 | **no** | yes | `The Java feature 'Text Blocks' is only available with source level 15 and above` | yes | yes | yes | nothing |
| F15 | pattern matching for `instanceof` | Java 16 language | 16 | **no** | yes | `The Java feature 'Pattern Matching in instanceof Expressions' is only available with source level 16 and above` | yes | yes | yes | nothing |
| F14 | record | Java 16 language | — | **no** | **no**, at any level | at `-16`: `java.lang.Record cannot be resolved to a type` | — | — | — | `java.lang.Record` exists from **API 34**; see section 5 |
| F16 | sealed interface | Java 17 language | — | **no** | **no** | at `-16`: `Sealed Types is a preview feature and disabled by default. Use --enable-preview to enable`. At `-17`: `Unrecognized option : -17` | — | — | — | see section 5 |

API levels in the last column: `platforms/android-36/data/api-versions.xml` of the Android SDK on this machine (`java/util/stream/Stream` since 24, `List.of` since 30, `String.repeat/isBlank/strip` since 33, `java/lang/Record` since 34, `InputStream.readAllBytes` since 33). VERIFIED (experiment here: file read).

Language features and library features are different things. A language feature needs only the compiler and D8. A library feature also needs the class to exist on the phone, unless D8 backports that one method (F07, F10) or a desugared library is shipped with the app (not available here; documented at https://developer.android.com/studio/write/java8-support as the way to get `java.util.stream`, `java.time` and others below their API level).

## 4. The legacy Dx dexer

Run from the APK's dex on ART API 31, same arguments as `Dp.java:237-243` without `--verbose` (`java/logs/extra_dx.txt`):

| Input | Result |
|---|---|
| F01 compiled at `-1.7` and at `-1.8` | Dx ok, runs |
| F02 (lambda) at `-1.8` | `invalid opcode ba - invokedynamic requires --min-sdk-version >= 26 (currently 13)` |
| F04 (static interface method) at `-1.8` | `invoking a static interface method … strictly requires --min-sdk-version >= 24 (blocked at current API level 13)` |
| F08 (`var`) at `-11` | `unsupported class file version 55.0` |

So with Dx only Java 7 code works. `BuildWarnings.java:63-65` already warns about this combination.

## 5. Records and sealed classes: what exactly stops them

All in `java/logs/extra_experiments.txt`, `extra_record_nonintermediate.txt`.

| Experiment | Result |
|---|---|
| Record, bundled ECJ `-16`, bundled android.jar (API 33) | does not compile: `java.lang.Record cannot be resolved to a type` |
| Record, bundled ECJ `-16`, android.jar of **API 34** (from the SDK on this machine, not bundled) | compiles |
| … then bundled D8 called as the app calls it (intermediate mode), min 21 and 26, on JVM and on ART | **fails**: `Invalid build configuration. Attempt to create a global synthetic for 'Record desugaring' without a global-synthetics consumer.` |
| … same class files, bundled D8 **without** intermediate mode (not what the app does) | works: output has `com/android/tools/r8/RecordTag` and synthetic record helpers; ran on API 31 ART and printed `F14 ok record 1 Point[x=1, y=2] true true` |
| Sealed interface, bundled ECJ `-16 --enable-preview` | compiles (class file version 60, minor 65535) |
| … then bundled D8 | **fails**: `Sealed classes are not supported as program classes` |
| Class files from `javac --release 17` (not bundled), bundled D8 | switch expression, pattern instanceof, string concat: ok and run. Record: same intermediate-mode error. Sealed: same "not supported" error. |

So D8 3.3.75 can read version-61 class files, but it cannot take sealed classes at all, and records only outside intermediate mode.

## 6. What a real Java 17 profile would require

Prototype runs, `java/logs/proto_*.txt`. Jars downloaded from Maven Central and Google Maven for the experiment only; nothing was added to the project.

| Need | What was tried | Result |
|---|---|---|
| A compiler that accepts `-17` | **ECJ 3.28.0** (v20211117-1416; accepts `-17` on the JVM) and **ECJ 3.33.0** (manifest: `Bundle-RequiredExecutionEnvironment: JavaSE-11`), each dexed with the bundled D8 (min 26) and run on ART API 31 with the IDE APK behind it on the classpath | Both **crash before compiling anything**: `NoSuchMethodError: No virtual method readAllBytes()[B in class Ljava/io/InputStream;` at `org.eclipse.jdt.internal.compiler.util.Util.getInputStreamAsByteArray`. `InputStream.readAllBytes` exists from API 33. ECJ 3.33.0 without the APK on the classpath stops earlier at `javax.lang.model.SourceVersion` (the APK defines that class in `classes36.dex`). |
| | ECJ 3.46.100 (newest on Maven Central today) | Not tried on ART. Its manifest says `Bundle-RequiredExecutionEnvironment: JavaSE-17`, class files are version 61. |
| | ECJ 3.28.0 and 3.33.0 on JVM 17 at `-17` | Record and sealed interface compile with both; pattern matching too with 3.33.0 (class files version 61). |
| A dexer that takes sealed classes and records | **R8 8.5.35**, D8 command line on JVM 17, `--lib` android.jar API 34, min 21 and 26 | Sealed: ok in both modes, ran on API 31 ART (`F16 ok sealed Circle`). Record: ok without `--intermediate`, ran on API 31 ART; **with `--intermediate` the same "global-synthetics consumer" error** as 3.3.75. |
| The newer dexer must run on Android | `r8-8.5.35.jar` (class files version 55) dexed with the bundled D8 3.3.75 at min 26 (35 s, one 7.6 MB dex), then its `D8` main run on ART API 31 | Worked for the two inputs tried (sealed, record). On API 26-30: NOT TESTED. Building the IDE with this dependency under AGP 7.3.1: NOT TESTED (no Gradle build was run for this audit). |
| `java.lang.Record` at compile time | android.jar API 34 or newer | See section 5. |
| Records at run time | D8 record desugaring | Ran on API 31 ART without `java.lang.Record` on the device. Below API 31: NOT TESTED. |
| Change in the D8 call | `DexCompiler.java:54` sets `setIntermediate(true)` | Records need either non-intermediate mode or a global-synthetics consumer. Effect on the later dex-merge step (`Dp.java:869-940`): NOT TESTED. |
| Java 17 library APIs on old phones | core library desugaring (`desugar_jdk_libs`) | Not in this toolchain. Not prototyped. |

Sizes (bytes), measured on this machine:

| File | Size |
|---|---|
| ecj-3.26.0.jar (bundled) | 3,133,846 |
| ecj-3.28.0.jar / ecj-3.33.0.jar / ecj-3.46.100.jar | 3,180,419 / 3,160,927 / 3,384,534 |
| r8-3.3.75.jar (bundled) | 8,321,735 |
| r8-8.2.47.jar / r8-8.5.35.jar | 15,298,967 / 16,745,967 |
| android.jar API 33 (bundled; zipped asset 24,960,965) | 27,138,945 |
| android.jar API 34 / API 36 (SDK) | 26,361,808 / 27,768,026 |
| desugar_jdk_libs 1.1.5 (IDE's own build) / 2.1.5 + its configuration jar | 1,055,273 / 6,113,073 + 55,731 |

Risks:

- **ECJ on Android.** The newer ECJ calls Java 9+ library methods that Android has only from API 33. The IDE's minSdk is 26. Either the calls are patched out (own build of ECJ), or the Java 17 choice is offered only on API 33+ phones. Other missing methods behind the first crash: UNKNOWN.
- **Auto-fix.** `AutoHeal` reads ECJ's messages (known-errors.json). A different ECJ may word them differently. All fixtures of test-fixtures.json must be run again with the new compiler.
- **R8 and AGP.** The prototype only shows that the R8 8.5.35 jar can be dexed and run. It does not show that the IDE builds with it. Earlier notes say a newer R8 needs an AGP 8 migration; that was not checked here.
- **android.jar.** Changing it changes what every project compiles and links against (AAPT2 `-I`, `ResourceCompiler.java:173-176`). All fixtures and a device build are needed.
- **APK size.** About +8 MB of jar for R8 before dexing; ECJ about the same size as now.

## 7. Recommended staged plan

Each stage ends with the fixtures of test-fixtures.json on the ART runtime and then a build on the owner's phone. A label goes into the dropdown only after that.

| Stage | Change | New binaries | What it would give | Status of the evidence |
|---|---|---|---|---|
| 0 | No toolchain change. Correct the comment at `ModernProjectDefaults.java:13-15`. Decide on the `build_config` path (section 2). Keep the dropdown at 1.7-11. | none | An honest statement of what works: sections 3 and 4 | VERIFIED (experiment here) |
| 1 | Optional: offer `14`, `15`, `16` with a note "no records, no sealed classes". Extend `ModernProjectDefaults.javaFallback` to these levels. | none | Switch expressions, text blocks, pattern matching for instanceof | Compile, dex and run: VERIFIED (experiment here) on ART API 31. In the app's build pipeline and on a phone: NOT TESTED. |
| 2 | android.jar API 34 or newer, and D8 without intermediate mode (or with a global-synthetics consumer) | android.jar (about 26-28 MB, replaces the current one) | Records at level 16 with the **current** ECJ and D8 | Pieces VERIFIED (experiment here) separately (section 5). Together inside `Dp`: NOT TESTED. |
| 3 | Newer ECJ that runs on API 26+, newer R8, level `17` | ECJ ≥ 3.28 (patched or gated to API 33+), R8 8.x | Sealed classes, `-17` | Prototype only (section 6). ECJ on Android: currently fails. |
| 4 | Core library desugaring for user apps | desugar_jdk_libs + configuration, an extra L8 step | `java.util.stream`, `java.time` below their API level | Not prototyped. |

Until stage 3 is proven on a device, the correct label for the top choice is "11" (or "16, no records/sealed" after stage 1), not "17".

## Could not be determined here

- Anything on a phone: whether `-11` compiles without the fallback on the owner's Android 15 device, speed, memory.
- Runtime behaviour below API 31 (only an API 31 runtime exists here): streams on API 21-23, D8's backports and record desugaring on old versions.
- Whether the IDE can be built with R8 8.x under AGP 7.3.1.
- Whether ECJ 3.28+ runs on API 33+ once `readAllBytes` exists (no API 33+ runtime here).
- The minApi21 flavor (ECJ 3.13.0) was not exercised.
