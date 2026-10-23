#!/usr/bin/env python3
"""v1.8.7: copy a fixture project's generated resources into the render module under its own names, so several
generated apps can be rendered in one module: layouts np<id>_<name>.xml, colours np<id>_<name>, styles AppTheme<id>...
The template code of the project's onCreate (initializeLogic body) goes into PresetCode<id>.java as a static method with
the views as parameters, so the render runs the exact generated Java (menu items, pager adapter).

usage: import_generated.py <generated src/main dir> <id> <render module dir> <view param>...   (param: Type:name)
"""
import os
import re
import sys

src, fid, module = sys.argv[1], sys.argv[2], sys.argv[3]
params = sys.argv[4:]
res = os.path.join(src, 'res')
out_res = os.path.join(module, 'render/src/main/res')
prefix = 'np%s_' % fid


def colour_names():
    names = set()
    for d in ('values', 'values-night'):
        p = os.path.join(res, d)
        if not os.path.isdir(p):
            continue
        for f in os.listdir(p):
            names.update(re.findall(r'<color name="([^"]+)"', open(os.path.join(p, f), encoding='utf-8').read()))
    return names


COLOURS = colour_names()
LAYOUTS = [f[:-4] for f in os.listdir(os.path.join(res, 'layout')) if f.endswith('.xml')]
DRAWABLES = {}
for d in os.listdir(res):
    if d.startswith('drawable'):
        for f in os.listdir(os.path.join(res, d)):
            DRAWABLES[f.rsplit('.', 1)[0]] = (d, f)


def rename(text):
    for c in sorted(COLOURS, key=len, reverse=True):
        text = text.replace('@color/%s"' % c, '@color/%s%s"' % (prefix, c))
        text = text.replace('@color/%s<' % c, '@color/%s%s<' % (prefix, c))
        text = text.replace('<color name="%s">' % c, '<color name="%s%s">' % (prefix, c))
    for l in LAYOUTS:
        text = text.replace('@layout/%s"' % l, '@layout/%s%s"' % (prefix, l))
    for name in DRAWABLES:
        text = text.replace('@drawable/%s"' % name, '@drawable/%s%s"' % (prefix, name))
    text = re.sub(r'\bAppTheme\b', 'AppTheme%s' % fid, text)
    return text


for d in ('values', 'values-night'):
    p = os.path.join(res, d)
    if not os.path.isdir(p):
        continue
    os.makedirs(os.path.join(out_res, d), exist_ok=True)
    for f in os.listdir(p):
        if f not in ('colors.xml', 'styles.xml', 'm3_colors.xml'):
            continue
        open(os.path.join(out_res, d, prefix + f), 'w', encoding='utf-8').write(rename(open(os.path.join(p, f), encoding='utf-8').read()))

os.makedirs(os.path.join(out_res, 'layout'), exist_ok=True)
for l in LAYOUTS:
    open(os.path.join(out_res, 'layout', prefix + l + '.xml'), 'w', encoding='utf-8').write(
        rename(open(os.path.join(res, 'layout', l + '.xml'), encoding='utf-8').read()))

import shutil
for name, (d, f) in DRAWABLES.items():
    os.makedirs(os.path.join(out_res, d), exist_ok=True)
    shutil.copy(os.path.join(res, d, f), os.path.join(out_res, d, prefix + f))

# the onCreate code of the template (add-source block): initializeLogic's body
java = None
for root, _, files in os.walk(os.path.join(src, 'java')):
    if 'MainActivity.java' in files:
        java = open(os.path.join(root, 'MainActivity.java'), encoding='utf-8').read()
body = ''
if java:
    start = java.find('private void initializeLogic() {')
    if start >= 0:
        i = java.index('{', start) + 1
        depth = 1
        j = i
        while depth:
            depth += {'{': 1, '}': -1}.get(java[j], 0)
            j += 1
        body = java[i:j - 1]
args = ', '.join('%s %s' % tuple(p.split(':')) for p in params)
code = 'package pz.render;\n\n/** Generated: initializeLogic() of fixture %s (import_generated.py). */\npublic final class PresetCode%s {\n' \
       '    public static void run(%s) {\n%s\n    }\n}\n' % (fid, fid, args, body)
os.makedirs(os.path.join(module, 'render/src/main/java/pz/render'), exist_ok=True)
open(os.path.join(module, 'render/src/main/java/pz/render/PresetCode%s.java' % fid), 'w', encoding='utf-8').write(code)
print('imported', fid, 'layouts', LAYOUTS, 'colours', len(COLOURS), 'code lines', body.count('\n'))
