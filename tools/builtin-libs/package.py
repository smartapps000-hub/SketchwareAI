"""Turn the solver's choice into the IDE's built-in library format (libs.zip folders + dexs.zip) and a plan for
BuiltInLibraries.java. Usage: package.py <work dir> <repo>
  <work>/solve.json   solver output
  <repo>              Sketchware AI repo (old libs.zip / dexs.zip / BuiltInLibraries.java)
Writes <work>/out/{libs.zip,dexs.zip,plan.json,report.tsv}.
"""
import io, json, os, re, shutil, subprocess, sys, zipfile

WORK, REPO = sys.argv[1], sys.argv[2]
sys.argv = [sys.argv[0], os.path.join(WORK, "cache"), os.path.join(WORK, "unused.json")]
sys.path.insert(0, os.path.join(WORK, "tools"))
import solve  # noqa: E402  (fetch / inspect / gate)
from coords import C  # noqa: E402

R8 = "/root/.gradle/caches/modules-2/files-2.1/com.android.tools/r8/3.3.75/206b47b2832c5ca317560d46cbec9d9b3db80f3f/r8-3.3.75.jar"
JAVA = "/usr/lib/jvm/java-17-openjdk-amd64/bin/java"
ASSETS = os.path.join(REPO, "app/src/main/assets/libs")
OUT = os.path.join(WORK, "out")

chosen = json.load(open(os.path.join(WORK, "solve.json")))["chosen"]

# --- decisions on top of the solver -------------------------------------------------------------------------
PIN = {  # same version as the library group / parent that ships with it
    "androidx.lifecycle:lifecycle-common": "2.8.7",            # lifecycle artifacts move together
    "androidx.constraintlayout:constraintlayout-core": "1.1.2",  # what constraintlayout 2.2.2 is built with
}
KEEP_BUNDLED = {  # bundled build is newer than the newest usable stable, or the update would drag Kotlin+coroutines
    "androidx.swiperefreshlayout:swiperefreshlayout": "bundled 1.2.0-alpha01 is newer than the usable stable 1.1.0",
    "de.hdodenhof:circleimageview": "same version as bundled (folder circle-imageview-v3.1.0)",
    "com.google.guava:listenablefuture": "same artifact as bundled (1.0 = folder listenablefuture-1.0.0)",
    "androidx.slidingpanelayout:slidingpanelayout": "1.2.0 adds androidx.window + Kotlin coroutines to every project that "
                                                     "uses legacy-support-core-ui; nothing generated uses it",
}
DROP = {  # empty jars since Kotlin 1.8 (merged into kotlin-stdlib); annotation-only jars nothing needs at run time
    "com.google.errorprone:error_prone_annotations",  # annotation-only (@CanIgnoreReturnValue etc.), nothing needs it at run time
    "org.jspecify:jspecify",
    "org.jetbrains.kotlin:kotlin-stdlib-common", "org.jetbrains.kotlin:kotlin-stdlib-jdk7", "org.jetbrains.kotlin:kotlin-stdlib-jdk8",
    "androidx.lifecycle:lifecycle-livedata-core-ktx",  # empty since lifecycle 2.8 (its classes moved into lifecycle-livedata-core)
}
for k, v in PIN.items():
    g, a = k.split(":")
    f = solve.inspect(solve.repo_of(g), g, a, v)
    assert f and not solve.gate(f), (k, v, f and solve.gate(f))
    chosen[k] = f

# constant name for every coordinate (existing constants first)
CONST = {f"{g}:{a}": n for n, (g, a, _) in C.items()}
NEW_CONST = {
    "org.jetbrains.kotlinx:kotlinx-coroutines-core": "KOTLINX_COROUTINES_CORE",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android": "KOTLINX_COROUTINES_ANDROID",
    "com.google.guava:listenablefuture": "ANDROIDX_LISTENABLEFUTURE",
    "androidx.profileinstaller:profileinstaller": "ANDROIDX_PROFILEINSTALLER",
    "androidx.customview:customview-poolingcontainer": "ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER",
    "com.google.errorprone:error_prone_annotations": "GOOGLE_ERROR_PRONE_ANNOTATIONS",
    "org.jspecify:jspecify": "JSPECIFY",
    "androidx.graphics:graphics-shapes": "ANDROIDX_GRAPHICS_SHAPES",    # v1.8.4: Material 1.13 (shape morphing)
    "org.jetbrains.kotlinx:atomicfu": "KOTLINX_ATOMICFU",                # v1.8.4: lifecycle-common 2.8 lists it
}
CONST.update(NEW_CONST)
FOLDER_PREFIX = {"com.squareup.okio:okio": "Okio", "com.airbnb.android:lottie": "Lottie",
                 "androidx.dynamicanimation:dynamicanimation": "dynamic-animation"}  # upstream folder names

# old BuiltInLibraries constants -> folder names
src = open(os.path.join(WORK, "BuiltInLibraries.orig.java")).read()  # names of the release being updated (v1.8.3 for v1.8.4)
OLD = dict(re.findall(r'public static String (\w+) = "([^"]+)";', src))

# KMP: "<artifact>-jvm" / "-android" dependencies mean the root artifact the set bundles
def norm(k):
    for suf in ("-jvm", "-android"):
        if k.endswith(suf) and k[: -len(suf)] in chosen:
            return k[: -len(suf)]
    return k
for f in chosen.values():
    f["deps"] = [[g, a, v] for g, a, v in ((d[0], d[1], d[2]) for d in f["deps"])]
    for d in f["deps"]:
        nk = norm(f"{d[0]}:{d[1]}")
        d[0], d[1] = nk.split(":")

# --- closure from the bundled roots --------------------------------------------------------------------------
final, todo = {}, [f"{g}:{a}" for g, a, _ in C.values()]
while todo:
    k = todo.pop()
    if k in final or k in DROP:
        continue
    if k in KEEP_BUNDLED:
        final[k] = {"keep": True}
        continue
    f = chosen.get(k)
    if f is None:
        raise SystemExit(f"no choice for {k}")
    final[k] = f
    todo += [f"{g}:{a}" for g, a, v in f["deps"]]

def vkey(v):
    return [int(x) for x in re.findall(r"\d+", v)]

missing = sorted({k for k in final if k not in CONST} | {f"{g}:{a}" for f in final.values() if not f.get("keep") for g, a, v in f["deps"] if f"{g}:{a}" not in CONST and f"{g}:{a}" not in DROP})
if missing:
    raise SystemExit("no constant for: " + ", ".join(missing))
plan, report = [], []
os.makedirs(OUT, exist_ok=True)
stage = os.path.join(OUT, "stage")
shutil.rmtree(stage, ignore_errors=True)
os.makedirs(os.path.join(stage, "libs"))
os.makedirs(os.path.join(stage, "dexs"))
old_libs = zipfile.ZipFile(os.path.join(ASSETS, "libs.zip"))
old_dexs = zipfile.ZipFile(os.path.join(ASSETS, "dexs.zip"))

for k in sorted(final):
    f = final[k]
    const = CONST.get(k)
    if const is None:
        raise SystemExit(f"no constant for {k}")
    old = OLD.get(const)
    if f.get("keep"):
        plan.append({"coord": k, "const": const, "name": old, "action": "keep", "why": KEEP_BUNDLED[k]})
        report.append((const, k, old, old, "kept: " + KEEP_BUNDLED[k]))
        continue
    g, a = k.split(":")
    prefix = FOLDER_PREFIX.get(k, a)
    name = f"{prefix}-{f['version']}"
    if old and old.lower() == name.lower():
        name = old  # same version as bundled: keep the bundled folder and dex untouched
    deps = []
    for dg, da, dv in f["deps"]:
        dk = f"{dg}:{da}"
        if dk in DROP or dk == k:
            continue
        if dk not in final:
            raise SystemExit(f"{k} needs {dk}, which is not in the set")
        if dk in KEEP_BUNDLED:
            pass
        elif dv and re.match(r"^\d", dv) and vkey(final[dk]["version"]) < vkey(dv):
            raise SystemExit(f"{k} needs {dk} >= {dv}, set has {final[dk]['version']}")
        deps.append(CONST[dk])
    deps = sorted(set(deps), key=deps.index)
    entry = {"coord": k, "const": const, "name": name, "old": old, "version": f["version"], "deps": deps,
             "package": f.get("package") if f.get("hasRes") else None, "hasAssets": f.get("hasAssets"),
             "kind": f["kind"], "kmp": f.get("kmpRoot"), "facts": {x: f.get(x) for x in ("minCompileSdk", "minSdk", "kotlin", "maxClass")}}
    if old == name:
        entry["action"] = "same"
        report.append((const, k, old, name, "same version"))
        plan.append(entry)
        continue
    entry["action"] = "update" if old else "new"
    # unpack the published artifact into the folder layout libs.zip uses
    repo = solve.repo_of(f["group"])
    data = solve.fetch(solve.base(repo, f["group"], f["artifact"]) + f"{f['version']}/{f['artifact']}-{f['version']}.{f['kind']}")
    d = os.path.join(stage, "libs", name)
    os.makedirs(d)
    if f["kind"] == "aar":
        z = zipfile.ZipFile(io.BytesIO(data))
        inner = [n for n in z.namelist() if n.startswith("libs/") and n.endswith(".jar")]
        for n in z.namelist():
            if n.endswith("/"):
                continue
            top = n.split("/")[0]
            if n == "classes.jar" or top in ("res", "assets") or n in ("AndroidManifest.xml", "R.txt", "public.txt", "proguard.txt", "annotations.zip"):
                p = os.path.join(d, n)
                os.makedirs(os.path.dirname(p), exist_ok=True)
                open(p, "wb").write(z.read(n))
        if not os.path.exists(os.path.join(d, "classes.jar")):
            raise SystemExit(f"{k}: no classes.jar in the AAR")
        if inner:  # e.g. emoji2's libs/repackaged.jar: one classes.jar, as the bundled emoji2 folder has
            merged = io.BytesIO()
            seen = set()
            with zipfile.ZipFile(merged, "w", zipfile.ZIP_DEFLATED) as mz:
                for jar in [open(os.path.join(d, "classes.jar"), "rb").read()] + [z.read(n) for n in inner]:
                    jz = zipfile.ZipFile(io.BytesIO(jar))
                    for n in jz.namelist():
                        if n.endswith("/") or n in seen or (n.startswith("META-INF/") and not n.endswith(".kotlin_module")):
                            continue
                        seen.add(n)
                        mz.writestr(n, jz.read(n))
            open(os.path.join(d, "classes.jar"), "wb").write(merged.getvalue())
            entry_merged = inner
        else:
            entry_merged = []
    else:
        open(os.path.join(d, "classes.jar"), "wb").write(data)
    entry["mergedJars"] = entry_merged if f["kind"] == "aar" else []
    plan.append(entry)
    report.append((const, k, old or "(new)", name, f"minCompileSdk {f['minCompileSdk']}, minSdk {f['minSdk']}, Kotlin {f['kotlin']}, class {f['maxClass']}"))

# --- v1.8.4: remove nameless MethodParameters attributes (javac 21 output) that crash D8/R8 3.3.75 ------------------
# Same class as the IDE runs on user libraries (com.sketchware.ai.project.ClassFileSanitizer), compiled from the repo.
san_cls = os.path.join(OUT, "sanitizer")
shutil.rmtree(san_cls, ignore_errors=True)
env0 = dict(os.environ); env0.pop("JAVA_TOOL_OPTIONS", None)
subprocess.run([os.path.join(os.path.dirname(JAVA), "javac"), "--release", "11", "-d", san_cls,
                os.path.join(REPO, "app/src/main/java/com/sketchware/ai/project/ClassFileSanitizer.java")], check=True, env=env0)
for e in plan:
    if e["action"] in ("update", "new"):
        j = os.path.join(stage, "libs", e["name"], "classes.jar")
        r = subprocess.run([JAVA, "-cp", san_cls, "com.sketchware.ai.project.ClassFileSanitizer", j],
                           capture_output=True, text=True, env=env0, check=True)
        e["sanitized"] = r.stdout.strip().split(": ", 1)[1]

# --- dex the new/updated libraries with the D8 the IDE ships (3.3.75), min API 21, intermediate --------------------
android_jar = os.path.join(OUT, "android.jar")
if not os.path.exists(android_jar):
    zipfile.ZipFile(os.path.join(ASSETS, "android.jar.zip")).extract("android.jar", OUT)
def jar_of(e):
    if e["action"] in ("update", "new"):
        return os.path.join(stage, "libs", e["name"], "classes.jar")
    return None
cp_jars = []
for e in plan:
    j = jar_of(e)
    if j:
        cp_jars.append(j)
    elif e["name"]:
        # bundled folder (kept/same): extract its classes.jar for the classpath
        n = e["name"] + "/classes.jar"
        if n in old_libs.namelist():
            p = os.path.join(OUT, "oldcp", n)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            if not os.path.exists(p):
                open(p, "wb").write(old_libs.read(n))
            cp_jars.append(p)
env = dict(os.environ)
env.pop("JAVA_TOOL_OPTIONS", None)
d8fail = []
for e in plan:
    j = jar_of(e)
    if not j:
        continue
    tmp = os.path.join(OUT, "d8tmp", e["name"])
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)
    args = [JAVA, "-cp", R8, "com.android.tools.r8.D8", "--release", "--intermediate", "--min-api", "21",
            "--lib", android_jar]
    for c in cp_jars:
        if c != j:
            args += ["--classpath", c]
    args += ["--output", tmp, j]
    r = subprocess.run(args, capture_output=True, text=True, env=env)
    out = sorted(os.listdir(tmp))
    if r.returncode != 0 or out != ["classes.dex"]:
        msg = [l for l in (r.stdout + r.stderr).splitlines() if l.strip()][:4]
        d8fail.append((e["name"], r.returncode, out, msg))
        continue
    e["d8warnings"] = [l for l in (r.stdout + r.stderr).splitlines() if l.strip()][:20]
    shutil.move(os.path.join(tmp, "classes.dex"), os.path.join(stage, "dexs", e["name"] + ".dex"))

if d8fail:
    for f in d8fail:
        print("D8 FAILED", f)
    raise SystemExit(f"{len(d8fail)} libraries can't be dexed by D8 3.3.75")

# --- new archives: old content minus replaced libraries, plus the new folders -------------------------------
replaced = {e["old"] for e in plan if e["action"] == "update"}
replaced.add("kotlin-stdlib-jdk7-1.4.30-M1-release-152")  # its classes are in kotlin-stdlib 1.8 (KOTLIN_STDLIB_JDK7 = KOTLIN_STDLIB)
def rebuild(old_zip, new_path, staged, is_dex):
    with zipfile.ZipFile(new_path, "w", zipfile.ZIP_DEFLATED) as z:
        for info in old_zip.infolist():
            top = info.filename[:-4] if is_dex else info.filename.split("/")[0]
            if top in replaced:
                continue
            z.writestr(info, old_zip.read(info.filename))
        for root, _, files in os.walk(staged):
            for fn in sorted(files):
                p = os.path.join(root, fn)
                z.write(p, os.path.relpath(p, staged))
rebuild(old_libs, os.path.join(OUT, "libs.zip"), os.path.join(stage, "libs"), False)
rebuild(old_dexs, os.path.join(OUT, "dexs.zip"), os.path.join(stage, "dexs"), True)
json.dump(plan, open(os.path.join(OUT, "plan.json"), "w"), indent=1)
with open(os.path.join(OUT, "report.tsv"), "w") as w:
    for r in report:
        w.write("\t".join(map(str, r)) + "\n")
for r in report:
    print(f"{r[0]:42s} {str(r[2]):45s} -> {str(r[3]):40s} {r[4]}")
print("replaced", len(replaced), "new", sum(1 for e in plan if e["action"] == "new"))
