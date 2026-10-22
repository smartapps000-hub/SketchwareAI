#!/usr/bin/env python3
"""Test fonts for the v1.8.4 Telugu fixture (909) and the layoutlib renders, made from OFL fonts on npm (@fontsource).
Nothing derived is committed: run this to (re)create them. Needs npm, fontTools + brotli (pip).
Usage: make-test-fonts.py <out dir>
  ramabhadra.ttf          Ramabhadra (Telugu subset), unchanged metrics
  ramabhadra_tight.ttf    same glyphs, ascent/descent cut to 0.80/0.22 em (line metrics tighter than the Telugu ink,
                          as in many hand-made Telugu fonts); head bbox still true
  ramabhadra_tightbox.ttf same, and the head bbox cut too (includeFontPadding can't help)
  tenali.otf              Tenali Ramakrishna converted to OpenType/CFF (OTTO), GSUB/GPOS/GDEF kept
  noto_telugu.otf         Noto Sans Telugu converted to OpenType/CFF
  mandali.ttf             Mandali, unchanged
The fonts are OFL-1.1; the modified ones are for testing only and are not distributed."""
import os, subprocess, sys, tarfile, tempfile, shutil
os.environ.setdefault("SOURCE_DATE_EPOCH", "1704067200")  # fixed head.modified: same bytes every run
from fontTools.ttLib import TTFont
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.fontBuilder import FontBuilder

out = sys.argv[1]; os.makedirs(out, exist_ok=True)
work = tempfile.mkdtemp()

def fetch(pkg):
    subprocess.run(["npm", "pack", "--silent", "@fontsource/" + pkg], cwd=work, check=True, capture_output=True)
    tgz = [f for f in os.listdir(work) if f.startswith("fontsource-" + pkg + "-") and f.endswith(".tgz")][0]
    d = os.path.join(work, pkg); tarfile.open(os.path.join(work, tgz)).extractall(d, filter="data")
    woff2 = os.path.join(d, "package/files", pkg + "-telugu-400-normal.woff2")
    t = TTFont(woff2); t.flavor = None
    p = os.path.join(work, pkg + ".ttf"); t.save(p)
    return p

def tight(src, dst, asc_em, desc_em, bbox):
    f = TTFont(src, recalcBBoxes=not bbox)
    u = f['head'].unitsPerEm; a = int(asc_em * u); d = int(desc_em * u)
    f['hhea'].ascent = a; f['hhea'].descent = -d; f['hhea'].lineGap = 0
    o = f['OS/2']; o.sTypoAscender = a; o.sTypoDescender = -d; o.sTypoLineGap = 0; o.usWinAscent = a; o.usWinDescent = d
    if bbox:
        f['head'].yMax = a; f['head'].yMin = -d
    f.save(dst)

def to_otf(src, dst):
    s = TTFont(src); order = s.getGlyphOrder(); gs = s.getGlyphSet(); cs = {}
    for g in order:
        pen = T2CharStringPen(gs[g].width, gs); gs[g].draw(pen); cs[g] = pen.getCharString()
    fb = FontBuilder(s['head'].unitsPerEm, isTTF=False)
    fb.setupGlyphOrder(order); fb.setupCharacterMap(s.getBestCmap())
    fb.setupCFF((s['name'].getDebugName(6) or 'Test').replace(' ', ''), {"FullName": s['name'].getDebugName(4) or 'Test'}, cs, {})
    fb.setupHorizontalMetrics({g: s['hmtx'][g] for g in order})
    fb.setupHorizontalHeader(ascent=s['hhea'].ascent, descent=s['hhea'].descent)
    fb.setupNameTable({"familyName": (s['name'].getDebugName(1) or 'Test') + " OTF test", "styleName": "Regular"})
    o = s['OS/2']
    fb.setupOS2(sTypoAscender=o.sTypoAscender, sTypoDescender=o.sTypoDescender, sTypoLineGap=o.sTypoLineGap,
                usWinAscent=o.usWinAscent, usWinDescent=o.usWinDescent)
    fb.setupPost()
    for t in ('GSUB', 'GPOS', 'GDEF'):
        if t in s: fb.font[t] = s[t]
    fb.font.save(dst)

rama = fetch("ramabhadra")
shutil.copy(rama, os.path.join(out, "ramabhadra.ttf"))
tight(rama, os.path.join(out, "ramabhadra_tight.ttf"), 0.80, 0.22, False)
tight(rama, os.path.join(out, "ramabhadra_tightbox.ttf"), 0.80, 0.22, True)
to_otf(fetch("tenali-ramakrishna"), os.path.join(out, "tenali.otf"))
to_otf(fetch("noto-sans-telugu"), os.path.join(out, "noto_telugu.otf"))
shutil.copy(fetch("mandali"), os.path.join(out, "mandali.ttf"))
shutil.rmtree(work)
print("\n".join(sorted(os.listdir(out))))
