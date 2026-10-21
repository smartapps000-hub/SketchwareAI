"""Rewrite BuiltInLibraries.java from plan.json: names of updated/new libraries, their dependency lists and R packages.
Libraries that were not updated keep their entries. Usage: genjava.py <plan.json> <BuiltInLibraries.java>"""
import json, os, re, sys

plan = json.load(open(sys.argv[1]))
path = sys.argv[2]
STAGE = os.path.join(os.path.dirname(sys.argv[1]), "stage", "libs")

def meaningful_res(name):
    """True when the library's res/ holds a resource (not only an empty <resources/> values file)."""
    d = os.path.join(STAGE, name, "res")
    for root, _, files in os.walk(d):
        for fn in files:
            text = open(os.path.join(root, fn), "rb").read()
            if not (fn.endswith(".xml") and re.sub(rb"\s|<\?xml[^>]*\?>", b"", text) in (b"<resources></resources>", b"<resources/>")):
                return True
    return False

for e in plan:
    if e["action"] in ("update", "new") and e.get("package") and not meaningful_res(e["name"]):
        e["package"] = None
src = open(path).read()

consts = dict(re.findall(r'public static String (\w+) = "([^"]+)";', src))
changed = [e for e in plan if e["action"] in ("update", "new")]

# 1. constant values
for e in changed:
    if e["const"] in consts:
        src = re.sub(r'(public static String %s = )"[^"]+";' % e["const"], r'\1"%s";' % e["name"], src)
    else:
        anchor = re.search(r'\n(\s*)public static String WAVE_SIDE_BAR', src)
        src = src[:anchor.start()] + "\n%spublic static String %s = \"%s\";" % (anchor.group(1), e["const"], e["name"]) + src[anchor.start():]
# kotlin-stdlib-jdk7 is empty since Kotlin 1.8 (merged into kotlin-stdlib): the constant names kotlin-stdlib
src = re.sub(r'public static String KOTLIN_STDLIB_JDK7 = "[^"]+";',
             'public static String KOTLIN_STDLIB_JDK7 = KOTLIN_STDLIB; // empty since Kotlin 1.8: kotlin-stdlib has its classes', src)

# 2. entries
def entry_span(const):
    m = re.search(r'new BuiltInLibrary\(%s(\)|,)' % const, src)
    if not m:
        return None
    i, depth = m.start() + len("new BuiltInLibrary"), 0
    for j in range(i, len(src)):
        if src[j] == "(":
            depth += 1
        elif src[j] == ")":
            depth -= 1
            if depth == 0:
                end = j + 1
                if src[end] == ",":
                    end += 1
                return m.start(), end
    raise SystemExit("unbalanced " + const)

def render(e):
    deps = ", ".join(e["deps"])
    if e["package"]:
        return 'new BuiltInLibrary(%s, List.of(%s), "%s"),' % (e["const"], deps, e["package"])
    if e["deps"]:
        return "new BuiltInLibrary(%s, List.of(%s))," % (e["const"], deps)
    return "new BuiltInLibrary(%s)," % e["const"]

report = []
for e in changed:
    span = entry_span(e["const"])
    if span:
        old = src[span[0]:span[1]]
        # keep every dependency the old entry had: a project's custom code may use a class that only arrived
        # transitively (e.g. SwipeRefreshLayout via recyclerview -> legacy-support-core-ui); the new POM deps are added
        m = re.search(r"List\.of\(([^)]*)\)", old)
        old_deps = [d.strip() for d in m.group(1).split(",") if d.strip()] if m else []
        old_deps = [d for d in old_deps if d != "KOTLIN_STDLIB_JDK7"] + (["KOTLIN_STDLIB"] if "KOTLIN_STDLIB_JDK7" in old_deps else [])
        e["deps"] = old_deps + [d for d in e["deps"] if d not in old_deps]
    new = render(e)
    if span:
        oldpkg = re.search(r'"([^"]+)"\s*\)\s*,?\s*$', old)
        report.append((e["const"], oldpkg.group(1) if oldpkg else None, e["package"]))
        src = src[:span[0]] + new + src[span[1]:]
    else:
        anchor = src.index("new BuiltInLibrary(WAVE_SIDE_BAR")
        src = src[:anchor] + new + "\n            " + src[anchor:]
        report.append((e["const"], "(new)", e["package"]))
span = entry_span("KOTLIN_STDLIB_JDK7")
if span:
    end = span[1]
    while src[end] in " \n":
        end += 1
    src = src[:span[0]] + src[end:]
open(path, "w").write(src)
for r in report:
    if r[1] != r[2]:
        print("R package %-40s %s -> %s" % r)
