#!/usr/bin/env python3
"""Compare out/android-shaping.tsv (Android TextRunShaper in layoutlib) with HarfBuzz for the same fonts and strings."""
import sys, uharfbuzz as hb
F = sys.argv[1] if len(sys.argv) > 1 else 'render/src/main/assets/fonts/'
rows = [l.rstrip('\n').split('\t') for l in open('out/android-shaping.tsv')]
same = 0
for font, s, ids in rows:
    face = hb.Face(hb.Blob.from_file_path(F + font)); f = hb.Font(face); scale = 1000 / face.upem
    buf = hb.Buffer(); buf.add_str(s); buf.guess_segment_properties(); hb.shape(f, buf, {})
    x = 0; out = []
    for i, p in zip(buf.glyph_infos, buf.glyph_positions):
        out.append((i.codepoint, round((x + p.x_offset) * scale), round(-p.y_offset * scale))); x += p.x_advance
    a = [(int(t.split('@')[0]), int(t.split('@')[1].split(':')[0]), int(t.split(':')[1])) for t in ids.split(',')]
    ok = len(a) == len(out) and all(g1 == g2 and abs(x1 - x2) <= 2 and abs(y1 - y2) <= 2 for (g1, x1, y1), (g2, x2, y2) in zip(a, out))
    same += ok
    if not ok: print('DIFF', font, s, a[:6], out[:6])
print(f"{same} of {len(rows)} runs identical (glyph ids, positions within 2/1000 em)")
