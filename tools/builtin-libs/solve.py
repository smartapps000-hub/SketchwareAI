"""Pick, for each built-in library, the newest stable version the bundled toolchain can use.

Gates per version (all read from the published artifact, nothing assumed):
  minCompileSdk <= 34   (aar-metadata.properties; the bundled android.jar is API 34 since v1.8.4, 33 before;
                         env MAX_COMPILE_SDK overrides)
  minSdk        <= 21   (library AndroidManifest; new projects use min 21)
  Kotlin metadata <= 1.8 (.kotlin_module header; the bundled kotlinc 1.7.10 reads up to 1.8)
  class files   <= 55   (Java 11; ECJ 3.26 + D8 3.3.75)
  every dependency resolvable inside the chosen set (Gradle semantics: chosen >= required)
Usage: solve.py <cache dir> <out json>
"""
import io, json, os, re, subprocess, sys, zipfile, xml.etree.ElementTree as ET
sys.path.insert(0, os.path.dirname(__file__))
from coords import C, GM, MC

CACHE, OUT = sys.argv[1], sys.argv[2]
MAX_COMPILE, MAX_MINSDK, MAX_KOTLIN, MAX_CLASS = int(os.environ.get("MAX_COMPILE_SDK", "34")), 21, (1, 8), 55
STABLE = re.compile(r"^\d+(\.\d+)*$")

def fetch(url):
    path = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9._-]", "_", url.split("//", 1)[1]))
    if not os.path.exists(path):
        r = subprocess.run(["curl", "-sS", "-f", "-L", "--retry", "4", "--retry-delay", "3", "-o", path + ".part", url], capture_output=True)
        if r.returncode != 0:
            return None
        os.rename(path + ".part", path)
    return open(path, "rb").read()

def base(repo, g, a):
    return (GM if repo == "GM" else MC) + g.replace(".", "/") + "/" + a + "/"

def vkey(v):
    return [int(x) for x in v.split(".")]

def versions(repo, g, a):
    data = fetch(base(repo, g, a) + "maven-metadata.xml")
    if data is None:
        return []
    vs = [e.text for e in ET.fromstring(data).iter("version")]
    return sorted({v for v in vs if STABLE.match(v)}, key=vkey, reverse=True)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def pom(repo, g, a, v):
    data = fetch(base(repo, g, a) + f"{v}/{a}-{v}.pom")
    if data is None:
        return None
    root = ET.fromstring(data)
    def t(e, n):
        x = e.find("m:" + n, NS)
        return x.text.strip() if x is not None and x.text else None
    deps = []
    for d in root.findall("m:dependencies/m:dependency", NS):
        scope = t(d, "scope") or "compile"
        if scope in ("test", "provided") or t(d, "optional") == "true":
            continue
        ver = (t(d, "version") or "").strip("[]")
        deps.append((t(d, "groupId"), t(d, "artifactId"), ver.split(",")[0]))
    return {"packaging": t(root, "packaging") or "jar", "deps": deps}

def kotlin_versions(jar):
    out = []
    for n in jar.namelist():
        if n.endswith(".kotlin_module"):
            b = jar.read(n)
            if len(b) >= 4:
                k = int.from_bytes(b[0:4], "big")
                if 0 < k < 6 and len(b) >= 4 + 4 * k:
                    out.append(tuple(int.from_bytes(b[4 + 4 * i:8 + 4 * i], "big") for i in range(k)))
    return out

def max_class(jar):
    m = 0
    for n in jar.namelist():
        if n.endswith(".class") and not n.startswith("META-INF/versions/") and not n.endswith("module-info.class"):
            h = jar.read(n)[:8]
            if h[:4] == b"\xca\xfe\xba\xbe":
                m = max(m, int.from_bytes(h[6:8], "big"))
    return m

def inspect(repo, g, a, v):
    """Facts about one published version, or None if it isn't a plain jar/aar we can bundle."""
    p = pom(repo, g, a, v)
    if p is None:
        return None
    facts = {"group": g, "artifact": a, "version": v, "deps": p["deps"], "kind": None}
    for dg, da, dv in p["deps"]:
        if dg == g and da in (a + "-android", a + "-jvm"):
            inner = inspect(repo, dg, da, dv)
            if inner:
                inner = dict(inner)
                inner["kmpRoot"] = f"{g}:{a}:{v}"
                inner["deps"] = [d for d in inner["deps"] if not (d[0] == g and d[1] == a)] + \
                    [d for d in p["deps"] if not (d[0] == dg and d[1] == da)]
            return inner
    for ext in ("aar", "jar"):
        data = fetch(base(repo, g, a) + f"{v}/{a}-{v}.{ext}")
        if data is None:
            continue
        z = zipfile.ZipFile(io.BytesIO(data))
        facts["kind"] = ext
        if ext == "aar":
            meta = "META-INF/com/android/build/gradle/aar-metadata.properties"
            props = z.read(meta).decode() if meta in z.namelist() else ""
            mc = re.search(r"minCompileSdk=(\d+)", props)
            facts["minCompileSdk"] = int(mc.group(1)) if mc else 1
            man = z.read("AndroidManifest.xml").decode("utf-8", "replace") if "AndroidManifest.xml" in z.namelist() else ""
            ms = re.search(r'minSdkVersion="(\d+)"', man)
            facts["minSdk"] = int(ms.group(1)) if ms else 1
            pk = re.search(r'package="([^"]+)"', man)
            facts["package"] = pk.group(1) if pk else None
            facts["hasRes"] = any(n.startswith("res/") and not n.endswith("/") for n in z.namelist())
            facts["hasAssets"] = any(n.startswith("assets/") and not n.endswith("/") for n in z.namelist())
            jar = zipfile.ZipFile(io.BytesIO(z.read("classes.jar"))) if "classes.jar" in z.namelist() else None
        else:
            facts.update(minCompileSdk=1, minSdk=1, package=None, hasRes=False, hasAssets=False)
            jar = z
        facts["kotlin"] = max(kotlin_versions(jar)) if jar and kotlin_versions(jar) else None
        facts["maxClass"] = max_class(jar) if jar else 0
        facts["native"] = any(n.endswith(".so") for n in z.namelist())
        return facts
    # Kotlin Multiplatform root (packaging pom): the Android/JVM artifact is a dependency.
    for dg, da, dv in p["deps"]:
        if dg == g and da in (a + "-android", a + "-jvm"):
            inner = inspect(repo, dg, da, dv)
            if inner:
                inner["kmpRoot"] = f"{g}:{a}:{v}"
                inner["deps"] = [d for d in inner["deps"]]
            return inner
    return None

CAPS = {"com.squareup.okhttp3:okhttp": 4, "com.squareup.okio:okio": 2, "com.github.bumptech.glide:glide": 5,
        "com.github.bumptech.glide:annotations": 5, "com.github.bumptech.glide:disklrucache": 5,
        "com.github.bumptech.glide:gifdecoder": 5, "com.airbnb.android:lottie": 4}

def gate(f):
    why = []
    if f is None:
        return ["not a plain jar/aar"]
    cap = CAPS.get(f"{f['group']}:{f['artifact']}") or CAPS.get(f.get("kmpRoot", "::").rsplit(":", 1)[0])
    if cap and vkey(f["version"])[0] >= cap: why.append(f"major version {vkey(f['version'])[0]} (generated code is written for < {cap})")
    if f["minCompileSdk"] > MAX_COMPILE: why.append(f"minCompileSdk {f['minCompileSdk']}")
    if f["minSdk"] > MAX_MINSDK: why.append(f"minSdk {f['minSdk']}")
    if f["kotlin"] and f["kotlin"][:2] > MAX_KOTLIN: why.append("Kotlin metadata " + ".".join(map(str, f["kotlin"])))
    if f["maxClass"] > MAX_CLASS: why.append(f"class file {f['maxClass']}")
    if f["native"]: why.append("native code")
    return why

def repo_of(g):
    return "GM" if g.startswith(("androidx.", "com.google.android")) else "MC"

if __name__ == "__main__":
    os.makedirs(CACHE, exist_ok=True)
    # 1. newest version passing the per-artifact gates
    chosen, rejected = {}, {}
    def pick(g, a, below=None):
        repo = repo_of(g)
        tried = []
        for v in versions(repo, g, a):
            if below and vkey(v) >= vkey(below):
                continue
            f = inspect(repo, g, a, v)
            why = gate(f)
            if not why:
                return f, tried
            tried.append((v, why))
        return None, tried
    queue = [(g, a) for g, a, _ in C.values()]
    seen = set()
    while queue:
        g, a = queue.pop(0)
        if (g, a) in seen: continue
        seen.add((g, a))
        f, tried = pick(g, a)
        rejected[f"{g}:{a}"] = tried
        if f is None:
            print("NO VERSION", g, a, tried[:3]); continue
        chosen[(g, a)] = f
        for dg, da, dv in f["deps"]:
            if dg and da and (dg, da) not in seen:
                queue.append((dg, da))
    # 2. consistency: chosen(dep) >= required, else step the requiring library down
    changed = True
    while changed:
        changed = False
        for (g, a), f in list(chosen.items()):
            for dg, da, dv in f["deps"]:
                d = chosen.get((dg, da))
                if d is None or not dv or not STABLE.match(dv):
                    continue
                if vkey(d["version"]) < vkey(dv):
                    nf, tried = pick(g, a, below=f["version"])
                    print(f"step down {g}:{a} {f['version']} (needs {da} {dv} > {d['version']}) -> {nf and nf['version']}")
                    if nf:
                        chosen[(g, a)] = nf
                        for ng, na, nv in nf["deps"]:
                            if (ng, na) not in chosen:
                                q, _ = pick(ng, na)
                                if q: chosen[(ng, na)] = q
                    changed = True
                    break
            if changed: break
    keep, todo = set(), [(g, a) for g, a, _ in C.values()]
    while todo:
        k = todo.pop()
        if k in keep or k not in chosen: continue
        keep.add(k)
        todo += [(dg, da) for dg, da, dv in chosen[k]["deps"]]
    out = {f"{g}:{a}": f for (g, a), f in chosen.items() if (g, a) in keep}
    json.dump({"chosen": out, "rejected": rejected}, open(OUT, "w"), indent=1, default=list)
    for k in sorted(out):
        f = out[k]
        print(f"{k:70s} {f['version']:10s} cSdk {f['minCompileSdk']:>2} min {f['minSdk']:>2} kt {f['kotlin']} cls {f['maxClass']}")
