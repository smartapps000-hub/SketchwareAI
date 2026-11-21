#!/usr/bin/env python3
"""v2.1.1 (owner's 21 Nov correction "original Android robot on our Obsidian / orange glass card"): the launcher icon a
project's APK gets when the project has no icon of its own.

The robot is the original Android head of Android Studio's default launcher icon (its ic_launcher_foreground path,
unchanged, white; the eyes are holes), centred on an Obsidian glass squircle (superellipse n = 4.2) with a warm
#FF6D24 / #FFB347 radial glow, a faint top sheen and a crisp glass rim (white at the top, amber at the bottom).

Writes drawable-xhdpi/app_icon.png (144 x 144, the template's size) into assets/resource/600/res.zip, the project
template every Run build and export extracts (a project's own icon still replaces it), and a 512 px preview.
Usage: make_default_app_icon.py <repo root> [preview.png]
"""
import io
import math
import sys
import zipfile

import cairosvg
from PIL import Image

# Android Studio's default ic_launcher_foreground (viewport 108 x 108): the Android robot's head, antennae and eyes.
ROBOT = ("M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 "
         "-19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 "
         "31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,"
         "-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,"
         "57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,"
         "0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z")
# its bounding box in the 108 viewport (antenna tips to the flat bottom)
ROBOT_BOX = (31.0, 38.0, 77.0, 63.928)


def squircle(cx, cy, r, n=4.2, steps=360):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        x = cx + r * math.copysign(abs(c) ** (2 / n), c)
        y = cy + r * math.copysign(abs(s) ** (2 / n), s)
        pts.append("%.2f,%.2f" % (x, y))
    return "M" + " L".join(pts) + " Z"


def svg(size=1024):
    inset = 34
    r = size / 2 - inset
    shape = squircle(size / 2, size / 2, r)
    x0, y0, x1, y1 = ROBOT_BOX
    width = 0.60 * size
    k = width / (x1 - x0)
    tx = size / 2 - (x0 + x1) / 2 * k
    ty = size * 0.52 - (y0 + y1) / 2 * k
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
<defs>
  <clipPath id="clip"><path d="{shape}"/></clipPath>
  <linearGradient id="body" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#26262C"/>
    <stop offset="0.55" stop-color="#151518"/>
    <stop offset="1" stop-color="#0B0B0D"/>
  </linearGradient>
  <radialGradient id="glow" cx="0.5" cy="0.6" r="0.5">
    <stop offset="0" stop-color="#FF6D24" stop-opacity="0.85"/>
    <stop offset="0.38" stop-color="#FF8A35" stop-opacity="0.42"/>
    <stop offset="0.7" stop-color="#FFB347" stop-opacity="0.10"/>
    <stop offset="1" stop-color="#FFB347" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="sheen" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.16"/>
    <stop offset="0.22" stop-color="#FFFFFF" stop-opacity="0.05"/>
    <stop offset="0.5" stop-color="#FFFFFF" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="rim" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.55"/>
    <stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.12"/>
    <stop offset="1" stop-color="#FFB347" stop-opacity="0.60"/>
  </linearGradient>
</defs>
<g clip-path="url(#clip)">
  <rect width="{size}" height="{size}" fill="url(#body)"/>
  <rect width="{size}" height="{size}" fill="url(#glow)"/>
  <ellipse cx="{size/2}" cy="{size*0.02}" rx="{size*0.62}" ry="{size*0.40}" fill="url(#sheen)"/>
</g>
<path d="{ROBOT}" fill="#FFFFFF" fill-rule="nonzero" transform="translate({tx:.3f},{ty:.3f}) scale({k:.5f})"/>
<path d="{shape}" fill="none" stroke="url(#rim)" stroke-width="12"/>
</svg>"""


def render(px):
    big = cairosvg.svg2png(bytestring=svg().encode(), output_width=px * 4, output_height=px * 4)
    return Image.open(io.BytesIO(big)).convert("RGBA").resize((px, px), Image.LANCZOS)


def main():
    root = sys.argv[1]
    res_zip = root + "/app/src/main/assets/resource/600/res.zip"
    icon = io.BytesIO()
    render(144).save(icon, "PNG", optimize=True)
    with zipfile.ZipFile(res_zip) as z:
        entries = [(i, z.read(i.filename)) for i in z.infolist()]
    tmp = res_zip + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as out:
        for info, data in entries:
            if info.filename == "drawable-xhdpi/app_icon.png":
                data = icon.getvalue()
            out.writestr(info, data)
    import os
    os.replace(tmp, res_zip)
    if len(sys.argv) > 2:
        render(512).save(sys.argv[2])
    print("app_icon.png", len(icon.getvalue()), "bytes")


if __name__ == "__main__":
    main()
