"""Check BuiltInLibraries.java against libs.zip/dexs.zip: names, folders, dex files, dependencies, closure vs old.
Usage: check_graph.py <new java> <old java> <libs.zip> <dexs.zip> <old libs.zip>"""
import re, sys, zipfile
def parse(path):
    s = open(path).read()
    consts = dict(re.findall(r'public static String (\w+) = "([^"]+)";', s))
    for a, b in re.findall(r'public static String (\w+) = (\w+);', s):
        consts[a] = consts[b]
    entries = {}
    for m in re.finditer(r'new BuiltInLibrary\((\w+)(?:,\s*List\.of\(([^)]*)\))?(?:,\s*"([^"]+)")?\)', s):
        name = consts[m.group(1)]
        if name in entries:
            raise SystemExit("duplicate entry " + name)
        deps = [consts[d.strip()] for d in (m.group(2) or "").split(",") if d.strip()]
        entries[name] = (deps, m.group(3))
    return consts, entries
consts, entries = parse(sys.argv[1])
oconsts, oentries = parse(sys.argv[2])
libs = {n.split("/")[0] for n in zipfile.ZipFile(sys.argv[3]).namelist()}
dexs = {n[:-4] for n in zipfile.ZipFile(sys.argv[4]).namelist() if n.endswith(".dex")}
libz = zipfile.ZipFile(sys.argv[3])
bad = 0
for name, (deps, pkg) in entries.items():
    if name not in libs: print("NO FOLDER", name); bad += 1
    if name not in dexs and name != "legacy-support-v4-1.0.0": print("NO DEX", name); bad += 1
    if pkg and not any(n.startswith(name + "/res/") and not n.endswith("/") for n in libz.namelist()): print("PKG WITHOUT RES", name, pkg); bad += 1
    for d in deps:
        if d not in entries: print("UNKNOWN DEP", name, "->", d); bad += 1
def closure(ent, root):
    seen, todo = [], [root]
    while todo:
        n = todo.pop()
        if n in seen: continue
        seen.append(n); todo += ent[n][0]
    return seen
base = lambda n: re.sub(r"-v?\d[\w.\-]*$", "", n).lower()
for c in ["ANDROIDX_APPCOMPAT", "MATERIAL", "GLIDE", "OKHTTP", "GSON", "LOTTIE", "YOUTUBE_PLAYER", "OTPVIEW", "FIREBASE_DATABASE", "FIREBASE_AUTH",
          "PLAY_SERVICES_ADS", "PLAY_SERVICES_MAPS", "ONESIGNAL", "PLAY_SERVICES_AUTH", "FIREBASE_MESSAGING", "KOTLIN_STDLIB"]:
    new = {base(n) for n in closure(entries, consts[c])}
    old = {base(n) for n in closure(oentries, oconsts[c])}
    lost = old - new
    print(f"{c:22s} old {len(old):3d} new {len(new):3d} lost {sorted(lost)} added {sorted(new - old)}")
    bad += len(lost)
unused = sorted(libs - set(entries) - {""})
print("folders without entry:", unused)
print("BAD", bad)
