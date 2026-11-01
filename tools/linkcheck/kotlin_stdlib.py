#!/usr/bin/env python3
"""Release check (v1.9.0): every kotlin.* method and field the APK's code refers to is defined in the APK.

The IDE runs on kotlin-stdlib 1.7.x (the embedded kotlinc 1.7.10 needs it), but since the IDE build moved to AGP 9 /
Kotlin Gradle plugin 2.4 its own Kotlin sources are compiled with language and API version 2.0 (the oldest Kotlin 2.4
accepts). A call the 2.x compiler emits into a stdlib member newer than the one in the APK would only fail at run time
(NoSuchMethodError / NoClassDefFoundError). This lists every `Lkotlin/...;.member:type` reference in the DEX code and
resolves it through the class hierarchy defined in the APK (superclasses and interfaces); a hierarchy that leaves
kotlin.* (java.*, android.*) counts as resolved.

usage: kotlin_stdlib.py <apk>   (dexdump from BUILD_TOOLS, default /opt/android-sdk/build-tools/36.1.0)
Prints the unresolved references and `TOTAL unresolved kotlin references: N`; exit status 1 when N > 0.
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

DEXDUMP = os.path.join(os.environ.get("BUILD_TOOLS", "/opt/android-sdk/build-tools/36.1.0"), "dexdump")
REF = re.compile(r"(Lkotlin/[^;\s]*;)\.([^:\s]+):(\S+)")
CLASS = re.compile(r"^  Class descriptor  : '(L[^']+;)'")
SUPER = re.compile(r"^  Superclass        : '(L[^']+;)'")
IFACE = re.compile(r"^    #\d+\s+: '(L[^']+;)'")
NAME = re.compile(r"^      name          : '([^']+)'")
TYPE = re.compile(r"^      type          : '([^']+)'")


def main():
    apk = sys.argv[1]
    defined = {}      # class -> set of "name:type" (methods: name:(..)R, fields: name:T)
    supers = {}       # class -> [superclass, interfaces...]
    refs = {}         # (class, member) -> example user class
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(apk) as z:
            dexes = [n for n in z.namelist() if re.match(r"classes\d*\.dex$", n)]
            for name in dexes:
                path = os.path.join(tmp, name)
                with open(path, "wb") as f:
                    f.write(z.read(name))
                out = subprocess.run([DEXDUMP, "-d", path], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                     universal_newlines=True, errors="replace").stdout
                current, section, member = None, None, None
                for line in out.splitlines():
                    m = CLASS.match(line)
                    if m:
                        current = m.group(1)
                        defined.setdefault(current, set())
                        supers.setdefault(current, [])
                        section = "head"
                        continue
                    if current is None:
                        continue
                    if line.startswith("  Interfaces"):
                        section = "ifaces"
                        continue
                    if line.startswith("  Static fields") or line.startswith("  Instance fields") \
                            or line.startswith("  Direct methods") or line.startswith("  Virtual methods"):
                        section = "members"
                        continue
                    m = SUPER.match(line)
                    if m:
                        supers[current].append(m.group(1))
                        continue
                    if section == "ifaces":
                        m = IFACE.match(line)
                        if m:
                            supers[current].append(m.group(1))
                            continue
                    if section == "members":
                        m = NAME.match(line)
                        if m:
                            member = m.group(1)
                            continue
                        m = TYPE.match(line)
                        if m and member is not None:
                            defined[current].add(member + ":" + m.group(1))
                            member = None
                            continue
                    if "Lkotlin/" in line and ("invoke-" in line or "get" in line or "put" in line):
                        for r in REF.finditer(line):
                            if not current.startswith("Lkotlin/"):
                                refs.setdefault((r.group(1), r.group(2) + ":" + r.group(3)), current)
                os.remove(path)

    def resolves(cls, member, seen):
        if cls in seen:
            return False
        seen.add(cls)
        if cls not in defined:
            # outside the APK: a framework / java class ends the walk (the member may be inherited from it)
            return not cls.startswith("Lkotlin/")
        if member in defined[cls]:
            return True
        return any(resolves(s, member, seen) for s in supers.get(cls, []))

    bad = sorted((c, m, user) for (c, m), user in refs.items() if not resolves(c, m, set()))
    for c, m, user in bad:
        print("UNRESOLVED %s.%s  (from %s)" % (c, m, user))
    print("kotlin references checked: %d (from %d classes outside kotlin.*)" % (
        len(refs), len(set(refs.values()))))
    print("TOTAL unresolved kotlin references: %d" % len(bad))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
