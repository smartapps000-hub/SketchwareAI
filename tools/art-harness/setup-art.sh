#!/bin/bash
# Installs an Android 12 (API 31) ART runtime into this Linux workspace: /system, /apex/<name>, /linkerconfig.
# Needs root, curl, unzip, debugfs (e2fsprogs), python3. About 2 GB of downloads/disk. Usage: setup-art.sh <work dir>
set -euo pipefail
W=${1:?work dir}
mkdir -p "$W" && cd "$W"
[ -f sysimg.zip ] || curl -sS -f -o sysimg.zip https://dl.google.com/android/repository/sys-img/android/x86_64-31_r03.zip
unzip -q -o sysimg.zip x86_64/system.img

# system.img is a GPT disk whose "super" partition holds logical partitions (liblp); copy out "system"
python3 - <<'EOF'
import struct, subprocess
IMG = 'x86_64/system.img'
f = open(IMG, 'rb')
f.seek(512); h = f.read(92); assert h[:8] == b'EFI PART'
lba, n, sz = struct.unpack('<Q', h[72:80])[0], struct.unpack('<I', h[80:84])[0], struct.unpack('<I', h[84:88])[0]
f.seek(lba * 512); sup = None
for i in range(n):
    e = f.read(sz); first = struct.unpack('<Q', e[32:40])[0]
    if e[56:128].decode('utf-16le').rstrip('\0') == 'super': sup = first * 512
assert sup is not None
f.seek(sup + 4096 + 2 * 4096); h = f.read(256)
assert struct.unpack('<I', h[:4])[0] == 0x414C5030
hsz, tsz = struct.unpack('<I', h[8:12])[0], struct.unpack('<I', h[44:48])[0]
td = lambda o: struct.unpack('<III', h[o:o + 12])
parts, exts = td(80), td(92)
f.seek(sup + 4096 + 2 * 4096 + hsz); t = f.read(tsz)
E = [struct.unpack('<QIQI', t[exts[0] + i * exts[2]:exts[0] + i * exts[2] + 24]) for i in range(exts[1])]
for i in range(parts[1]):
    p = t[parts[0] + i * parts[2]:parts[0] + (i + 1) * parts[2]]
    if p[:36].rstrip(b'\0') == b'system':
        fe, ne = struct.unpack('<II', p[40:48]); (nsec, typ, start, src) = E[fe]; assert ne == 1
        subprocess.check_call(['dd', 'if=' + IMG, 'of=system_part.img', 'bs=512', 'skip=%d' % (sup // 512 + start), 'count=%d' % nsec, 'status=none'])
EOF

mkdir -p root/system
for d in bin lib64 etc apex; do debugfs -R "rdump /system/$d root/system" system_part.img 2>/dev/null; done
mkdir -p /system && cp -a root/system/bin root/system/lib64 root/system/etc /system/

# apexes (some are compressed .capex: original_apex inside, apex_payload.img inside that)
for n in art runtime i18n tzdata conscrypt os.statsd; do
  src=root/system/apex/com.android.$n.apex; [ -f "$src" ] || src=root/system/apex/com.android.$n.capex
  w=apexwork/$n; mkdir -p "$w"
  if [[ $src == *.capex ]]; then unzip -q -o "$src" original_apex -d "$w"; unzip -q -o "$w/original_apex" apex_payload.img -d "$w"
  else unzip -q -o "$src" apex_payload.img -d "$w"; fi
  mkdir -p /apex/com.android.$n && debugfs -R "rdump / /apex/com.android.$n" "$w/apex_payload.img" 2>/dev/null
done

# linker config: the ART binary reads /linkerconfig/com.android.art/ld.config.txt. libnativeloader needs the
# "system" namespace (and the apex ones) to be visible, or ART aborts with "Failed to get system namespace".
mkdir -p /linkerconfig/com.android.art
python3 - <<'EOF'
ns = ['com_android_art', 'com_android_i18n', 'com_android_conscrypt', 'com_android_os_statsd', 'com_android_runtime', 'system']
paths = {'com_android_art': ['/apex/com.android.art/lib64'], 'com_android_i18n': ['/apex/com.android.i18n/lib64'],
         'com_android_conscrypt': ['/apex/com.android.conscrypt/lib64'], 'com_android_os_statsd': ['/apex/com.android.os.statsd/lib64'],
         'com_android_runtime': ['/apex/com.android.runtime/lib64', '/apex/com.android.runtime/lib64/bionic'], 'system': ['/system/lib64']}
L = ['dir.legacy = ' + d for d in ['/system', '/apex', '/data', '/tmp', '/home', '/storage']] + ['[legacy]', 'additional.namespaces = ' + ','.join(ns),
     'namespace.default.isolated = false']
allp = ['/system/lib64'] + [p for k in ns[:-1] for p in paths[k]]
L += ['namespace.default.search.paths %s %s' % ('=' if i == 0 else '+=', p) for i, p in enumerate(allp)]
L += ['namespace.default.links = ' + ','.join(ns)] + ['namespace.default.link.%s.allow_all_shared_libs = true' % k for k in ns]
for k in ns:
    L += ['namespace.%s.isolated = false' % k] + ['namespace.%s.search.paths %s %s' % (k, '=' if i == 0 else '+=', p) for i, p in enumerate(paths[k])]
    L += ['namespace.%s.links = default' % k, 'namespace.%s.link.default.allow_all_shared_libs = true' % k, 'namespace.%s.visible = true' % k]
text = '\n'.join(L) + '\n'
for p in ['/linkerconfig/ld.config.txt', '/linkerconfig/com.android.art/ld.config.txt', '/apex/com.android.art/etc/ld.config.txt']:
    open(p, 'w').write(text)
EOF
# a class loader that loads a native library links to "system" with these; an empty list aborts ART (apksigner path)
printf "libandroid.so\nlibc.so\nlibdl.so\nlibEGL.so\nlibGLESv2.so\nlibjnigraphics.so\nliblog.so\nlibm.so\nlibmediandk.so\nlibnativewindow.so\nlibstdc++.so\nlibsync.so\nlibvulkan.so\nlibz.so\n" > /system/etc/public.libraries.txt
mkdir -p /storage/emulated/0
echo "ART API 31 installed"
