# API-level check for the IDE APK: every method/field the APK's DEX code references on a framework class
# (java.*, javax.*, android.*, dalvik.*, org.json, org.w3c, org.xml, org.apache.http) must exist on the oldest
# Android the IDE supports. A call to a method that phone doesn't have is a NoSuchMethodError at run time
# (classic case: Java 9-11 library methods such as InputStream.readAllBytes, String.isBlank, List.of on
# Android 8-12). Missing classes are reported too (NoClassDefFoundError).
#
# Usage: python3 api_level.py <dexdump -d output> <android.jar of the oldest supported API> [--strict]
# Prints one line per missing member with the callers' packages, and a summary. Calls made from methods
# that read Build.VERSION.SDK_INT, or from AndroidX-style ApiNNImpl classes, are listed as "guarded".
import re, sys, struct, zipfile, collections

def read_jar(path):
    classes = {}
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if not n.endswith('.class'):
                continue
            classes['L' + n[:-6] + ';'] = parse_class(z.read(n))
    return classes

def parse_class(b):
    pos = 10
    count = struct.unpack('>H', b[8:10])[0]
    cp = [None] * count
    i = 1
    while i < count:
        tag = b[pos]
        if tag == 1:
            ln = struct.unpack('>H', b[pos + 1:pos + 3])[0]
            cp[i] = ('utf8', b[pos + 3:pos + 3 + ln].decode('utf-8', 'replace'))
            pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9; i += 1
        elif tag == 7:
            cp[i] = ('class', struct.unpack('>H', b[pos + 1:pos + 3])[0]); pos += 3
        elif tag in (8, 16, 19, 20):
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError('constant pool tag %d' % tag)
        i += 1
    def cname(idx):
        return 'L' + cp[cp[idx][1]][1] + ';' if idx else None
    pos += 2
    this_c = cname(struct.unpack('>H', b[pos:pos + 2])[0]); pos += 2
    super_c = cname(struct.unpack('>H', b[pos:pos + 2])[0]); pos += 2
    nint = struct.unpack('>H', b[pos:pos + 2])[0]; pos += 2
    ifaces = [cname(struct.unpack('>H', b[pos + 2 * k:pos + 2 * k + 2])[0]) for k in range(nint)]
    pos += 2 * nint
    members = []
    for kind in ('f', 'm'):
        n = struct.unpack('>H', b[pos:pos + 2])[0]; pos += 2
        for _ in range(n):
            name = cp[struct.unpack('>H', b[pos + 2:pos + 4])[0]][1]
            desc = cp[struct.unpack('>H', b[pos + 4:pos + 6])[0]][1]
            na = struct.unpack('>H', b[pos + 6:pos + 8])[0]; pos += 8
            for _ in range(na):
                ln = struct.unpack('>I', b[pos + 2:pos + 6])[0]; pos += 6 + ln
            members.append((kind, name, desc))
    fields = {n for k, n, d in members if k == 'f'}
    methods = {n + d for k, n, d in members if k == 'm'}
    return (super_c, ifaces, methods, fields)

FRAMEWORK = ('Ljava/', 'Ljavax/', 'Landroid/', 'Ldalvik/', 'Lorg/json/', 'Lorg/w3c/', 'Lorg/xml/', 'Lorg/apache/http/')
SKIP_POLY = ('Ljava/lang/invoke/MethodHandle;', 'Ljava/lang/invoke/VarHandle;')

def main():
    dump, jar = sys.argv[1], sys.argv[2]
    fw = read_jar(jar)
    app = {}  # class -> (super, ifaces, methods(name only), fields)
    refs = collections.defaultdict(set)  # (cls, member, kind) -> callers
    guarded = set()
    cls = meth = None
    mref = re.compile(r"(invoke-[a-z/-]+|[is]get[a-z-]*|[is]put[a-z-]*) [^,]*(?:, )?.*?(L[A-Za-z0-9_$/]+;)\.([A-Za-z0-9_$<>]+):(\S+)")
    cur_super = None; cur_ifaces = []; cur_methods = set(); cur_fields = set(); in_ifaces = False
    def flush():
        if cls:
            app[cls] = (cur_super, list(cur_ifaces), set(cur_methods), set(cur_fields))
    sdk_int_methods = set()
    for line in open(dump, errors='replace'):
        if line.startswith('  Class descriptor  :'):
            flush()
            cls = line.split("'")[1]; cur_super = None; cur_ifaces = []; cur_methods = set(); cur_fields = set()
            meth = None; in_ifaces = False
            continue
        if line.startswith('  Superclass        :'):
            cur_super = line.split("'")[1] if "'" in line else None; continue
        if line.startswith('  Interfaces        -'):
            in_ifaces = True; continue
        if in_ifaces and line.startswith('    #') and ': ' in line and "'" in line:
            cur_ifaces.append(line.split("'")[1]); continue
        if line.startswith('  Static fields') or line.startswith('  Instance fields') or line.startswith('  Direct methods') or line.startswith('  Virtual methods'):
            in_ifaces = False
        if "      name          : '" in line:
            meth = line.split("'")[1]; continue
        if "      type          : '" in line and meth is not None:
            t = line.split("'")[1]
            if t.startswith('('):
                cur_methods.add(meth + t)
            else:
                cur_fields.add(meth)
            continue
        if '|' not in line or meth is None:
            continue
        tail = line.split('|', 1)[1].split(' // ')[0]
        if 'Landroid/os/Build$VERSION;.SDK_INT' in tail:
            sdk_int_methods.add(cls + '.' + meth)
        m = mref.search(tail)
        if not m:
            continue
        op, c, name, sig = m.groups()
        kind = 'm' if op.startswith('invoke') else 'f'
        refs[(c, name + sig if kind == 'm' else name, kind)].add(cls + '.' + meth)
    flush()

    def find(c, member, kind, seen):
        if c is None or c in seen:
            return None
        seen.add(c)
        if c in app:
            sup, ifs, ms, fs = app[c]
            if (kind == 'm' and member in ms) or (kind == 'f' and member.split(':')[0] in fs):
                return 'app'
        elif c in fw:
            sup, ifs, ms, fs = fw[c]
            if (kind == 'm' and member in ms) or (kind == 'f' and member in fs):
                return 'fw'
        else:
            return 'nocls' if c.startswith(FRAMEWORK) else 'unknown'
        r = find(sup, member, kind, seen)
        if r in ('app', 'fw'):
            return r
        for i in ifs:
            r2 = find(i, member, kind, seen)
            if r2 in ('app', 'fw'):
                return r2
        return r or 'missing'

    missing = collections.defaultdict(set)
    for (c, member, kind), callers in refs.items():
        if c in SKIP_POLY:
            continue
        if not c.startswith(FRAMEWORK) and c not in app:
            continue  # outside the platform and not in the APK: the class-level linkcheck covers it
        if c in app and not any(c2.startswith(FRAMEWORK) for c2 in chain(c, app)):
            continue  # pure app hierarchy
        r = find(c, member, kind, set())
        if r in ('missing', 'nocls'):
            missing[(c, member, r)] |= callers

    def is_guarded(caller):
        k = caller.split('.')[0]
        return caller in sdk_int_methods or re.search(r'Api\d+Impl|\$Api\d+|ApiImpl|Compat\$Api', k)

    total = 0; unguarded = 0
    pk = collections.Counter()
    for (c, member, r), callers in sorted(missing.items()):
        ug = sorted(x for x in callers if not is_guarded(x))
        total += 1
        if ug:
            unguarded += 1
            pkgs = sorted({'/'.join(x.split('.')[0][1:].split('/')[:3]) for x in ug})
            for p in pkgs:
                pk[p] += 1
            print(('NOCLASS ' if r == 'nocls' else 'MISSING ') + c + '.' + member + '  <- ' + ', '.join(pkgs[:4]) + ('' if len(pkgs) <= 4 else ' +%d' % (len(pkgs) - 4)) + '  e.g. ' + ug[0])
    print('--- unguarded missing members by caller package:')
    for p, n in pk.most_common():
        print('%5d  %s' % (n, p))
    print('TOTAL missing members: %d (unguarded: %d, only behind SDK_INT/ApiNNImpl: %d)' % (total, unguarded, total - unguarded))

def chain(c, app):
    out = []
    seen = set()
    while c and c not in seen:
        seen.add(c); out.append(c)
        if c in app:
            c = app[c][0]
        else:
            break
    return out

if __name__ == '__main__':
    main()
