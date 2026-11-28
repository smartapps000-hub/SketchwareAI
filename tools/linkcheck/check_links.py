# Method-level link check: flags calls from this repo's source classes into APK methods that
# reference classes missing from both the APK and the framework (NoClassDefFoundError at run
# time), following calls up to 3 levels deep inside the APK.
import re, os, sys
defined = set(open('defined.txt').read().split())
framework = set(open('framework.txt').read().split())
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'java')
src = set()
for dp, _, fs in os.walk(root):
    for f in fs:
        if f.endswith('.java'):
            src.add('L' + os.path.relpath(os.path.join(dp, f[:-5]), root) + ';')
# Upstream, already present before Sketchware AI changes and never reached at run time
# (Google Play services is not bundled; R8 record desugaring marker).
ALLOW = {'Lcom/google/android/gms/common/GoogleApiAvailability;', 'Lcom/android/tools/r8/RecordTag;',
         'Lcom/android/tools/r8/annotations/SynthesizedClassV2;'}
def ok(t):
    return t in ALLOW or t in defined or t in framework or t.startswith(('Ljava/', 'Ljavax/', 'Ldalvik/', 'Lorg/json', 'Lorg/xml', 'Lorg/w3c', 'Lsun/', 'Llibcore', 'Lorg/apache/http'))
cls = None; meth = None; mname = None
calls = {}; bad = {}
# v2.1.6: methods are keyed by class, name and descriptor. A non-debuggable build lets D8 outline every call to an API
# above the min SDK into one synthetic class per DEX ($$ExternalSyntheticApiModelOutline0) whose ~100 methods are all
# named "m": keyed by name only, a call to any of them looked like it reached every new-API class the others touch.
mref = re.compile(r"(L[A-Za-z0-9_$/]+;)\.([A-Za-z0-9_$<>]+):(\([^)]*\)[A-Za-z0-9_$/;\[]+)")
tref = re.compile(r"(L[A-Za-z0-9_$/]+;)")
for line in open('dump.txt', errors='replace'):
    if line.startswith("  Class descriptor  :"):
        cls = line.split("'")[1]; meth = None; continue
    if "      name          : '" in line:
        mname = cls + '.' + line.split("'")[1]; meth = None; continue
    if "      type          : '" in line and mname is not None:
        meth = mname + ':' + line.split("'")[1]; mname = None; continue
    if meth is None or '|' not in line or 'const-string' in line: continue
    tail = line.split('|', 1)[1].split(' // ')[0]
    for t in tref.findall(tail):
        if not ok(t): bad.setdefault(meth, set()).add(t)
    for c, m, d in mref.findall(tail):
        calls.setdefault(meth, set()).add(c + '.' + m + ':' + d)
def reaches(m, depth, seen):
    if m in bad: return bad[m]
    if depth == 0 or m in seen: return None
    seen.add(m)
    for n in calls.get(m, ()):
        r = reaches(n, depth - 1, seen)
        if r: return r
    return None
hits = {}
for m, cs in calls.items():
    top = m.split('.')[0]; top = top.split('$')[0] + (';' if '$' in top else '')
    if top not in src: continue
    for c in cs:
        r = reaches(c, 3, set())
        if r: hits.setdefault(c, (m, r))
for c, (m, r) in sorted(hits.items()):
    print(f"{m}\n   -> {c} needs missing {sorted(r)[:3]}")
print('TOTAL risky calls:', len(hits))
sys.exit(1 if hits else 0)
