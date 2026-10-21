# Built-in library update tools (v1.8.3)

1. `solve.py <cache> <solve.json>`: for each bundled library (coords.py) the newest stable version that the bundled
   toolchain can use: minCompileSdk <= 33 (android.jar API 33), minSdk <= 21, Kotlin metadata <= 1.8 (kotlinc 1.7.10),
   class files <= 55; then steps libraries down until every dependency is satisfied inside the set. Major-version
   caps keep OkHttp 3, Okio 1, Glide 4, Lottie 3 (the generated code is written for them).
2. `package.py <work> <repo>`: pins, kept-bundled and dropped libraries (see the dicts at the top, each with its
   reason), unpacks AARs into the libs.zip folder layout, dexes every new folder with the IDE's D8 3.3.75
   (`--release --intermediate --min-api 21`), fails on anything D8 3.3.75 can't read, writes libs.zip/dexs.zip and
   plan.json. Needs `<work>/BuiltInLibraries.orig.java` (upstream names).
3. `genjava.py plan.json BuiltInLibraries.java`: new names, R packages only for libraries with real resources, and
   every old dependency kept (the new closure is a superset of the old one).
4. `check_graph.py new.java old.java libs.zip dexs.zip old-libs.zip`: folders, dex files, unknown dependencies,
   closure old vs new per component.
