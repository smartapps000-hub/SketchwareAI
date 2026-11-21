#!/usr/bin/env python3
"""v2.1.1 (owner's 21 Nov corrections: "100% pure circular original Android icon geometry in Obsidian + deep orange
glass"): the launcher icon a project's APK gets when the project has no icon of its own, and the IDE's preview of it.

The geometry is Android Studio's own default launcher icon, element for element, in its round legacy form (the stock
template icon this replaces: a 132 px circle on a 144 px canvas with a soft drop shadow below it, the 108-unit adaptive
viewport at 132 / 74 px per unit, centred):
  * ic_launcher_background: the full grid (lines at 9, 19 ... 99 both ways, and the inner lines 19..89 at 29 ... 79 that
    overlap them, stroke 0.8);
  * ic_launcher_foreground: the 45-degree long shadow path with its linear gradient, and the robot's head, antennae and
    eyes (the eyes are holes);
only the colours change (owner, 21 Nov: "same positions as the original, no border, the shadow in glassy orange on
black, soft glassmorphism, neat and clean"): an Obsidian glass fill (#24242B -> #09090C with a soft frosted light at the
top left and a faint warm glow low right) in place of the flat green, the grid in #FF8A3D at 20 % where the original has
white at 20 %, the 45-degree long shadow in translucent orange (#FF6D24 -> #C84B11 -> clear) where the original has
black, the robot white with its eyes as holes (they show the long shadow, as in the original); no rim (the original has none).

Writes drawable-xhdpi/app_icon.png (144 px) into assets/resource/600/res.zip (the project template every Run build and
export extracts; a project's own icon still replaces it) and the IDE's preview res/drawable-xxhdpi and -xxxhdpi
ob_app_icon_placeholder.png (New Project and project settings show what the APK will get).
Usage: make_default_app_icon.py <repo root> [preview.png]
"""
import io
import os
import sys
import zipfile

import cairosvg
from PIL import Image, ImageFilter

# Android Studio's default launcher icon (viewport 108 x 108)
ROBOT = ("M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 "
         "-19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 "
         "31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,"
         "-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,"
         "57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,"
         "0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z")
LONG_SHADOW = "M31,63.928c0,0 6.4,-11 12.1,-13.1c7.2,-2.6 26,-1.4 26,-1.4l38.1,38.1L107,108.928l-32,-1L31,63.928z"
SHADOW_FROM = (42.9492, 49.59793)
SHADOW_TO = (85.84757, 92.4963)
GRID = ([f"M{u},0L{u},108" for u in range(9, 100, 10)] + [f"M0,{u}L108,{u}" for u in range(9, 100, 10)]
        + [f"M19,{u}L89,{u}" for u in range(29, 80, 10)] + [f"M{u},19L{u},89" for u in range(29, 80, 10)])

SIZE = 144          # the legacy icon's canvas
CENTER = 72.0
RADIUS = 66.0       # 132 px circle
K = 132.0 / 74.0    # px per viewport unit
T = CENTER - 54.0 * K


def svg():
    grid = "".join(f'<path d="{d}" stroke="#FF8A3D" stroke-opacity="0.20" stroke-width="0.8" fill="none"/>' for d in GRID)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">
<defs>
  <clipPath id="circle"><circle cx="{CENTER}" cy="{CENTER}" r="{RADIUS}"/></clipPath>
  <linearGradient id="glass" x1="0.15" y1="0" x2="0.85" y2="1">
    <stop offset="0" stop-color="#24242B"/>
    <stop offset="0.5" stop-color="#121216"/>
    <stop offset="1" stop-color="#09090C"/>
  </linearGradient>
  <radialGradient id="frost" gradientUnits="userSpaceOnUse" cx="{CENTER - RADIUS * 0.35:.3f}" cy="{CENTER - RADIUS * 0.45:.3f}" r="{RADIUS * 1.1:.3f}">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.13"/>
    <stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0.03"/>
    <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
  </radialGradient>
  <radialGradient id="warm" gradientUnits="userSpaceOnUse" cx="{CENTER + RADIUS * 0.3:.3f}" cy="{CENTER + RADIUS * 0.55:.3f}" r="{RADIUS * 0.9:.3f}">
    <stop offset="0" stop-color="#FF6D24" stop-opacity="0.16"/>
    <stop offset="1" stop-color="#FF6D24" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="longShadow" gradientUnits="userSpaceOnUse" x1="{SHADOW_FROM[0]}" y1="{SHADOW_FROM[1]}"
      x2="{SHADOW_TO[0]}" y2="{SHADOW_TO[1]}">
    <stop offset="0" stop-color="#FF6D24" stop-opacity="0.62"/>
    <stop offset="0.5" stop-color="#E2571A" stop-opacity="0.30"/>
    <stop offset="1" stop-color="#C84B11" stop-opacity="0"/>
  </linearGradient>
</defs>
<g clip-path="url(#circle)">
  <rect width="{SIZE}" height="{SIZE}" fill="url(#glass)"/>
  <rect width="{SIZE}" height="{SIZE}" fill="url(#warm)"/>
  <rect width="{SIZE}" height="{SIZE}" fill="url(#frost)"/>
  <g transform="translate({T:.4f},{T:.4f}) scale({K:.6f})">
    {grid}
    <path d="{LONG_SHADOW}" fill="url(#longShadow)"/>
    <path d="{ROBOT}" fill="#FFFFFF" fill-rule="nonzero"/>
  </g>
</g>
</svg>"""


def render(px):
    scale = 8
    big = px * scale
    art = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg().encode(), output_width=big, output_height=big))).convert("RGBA")
    # the legacy icon's soft drop shadow below the circle (as the stock icon has: strongest under its bottom edge)
    mask = Image.new("L", (big, big), 0)
    from PIL import ImageDraw
    d = ImageDraw.Draw(mask)
    f = big / SIZE
    r = RADIUS * f
    cx, cy = CENTER * f, (CENTER + 1.6) * f
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=110)
    mask = mask.filter(ImageFilter.GaussianBlur(1.6 * f))
    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    shadow.putalpha(mask)
    out = Image.alpha_composite(shadow, art)
    return out.resize((px, px), Image.LANCZOS)


def write_zip(res_zip, icon):
    with zipfile.ZipFile(res_zip) as z:
        entries = [(i, z.read(i.filename)) for i in z.infolist()]
    tmp = res_zip + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as out:
        for info, data in entries:
            if info.filename == "drawable-xhdpi/app_icon.png":
                data = icon
            out.writestr(info, data)
    os.replace(tmp, res_zip)


def png(image):
    b = io.BytesIO()
    image.save(b, "PNG", optimize=True)
    return b.getvalue()


def main():
    root = sys.argv[1]
    icon = png(render(144))
    write_zip(root + "/app/src/main/assets/resource/600/res.zip", icon)
    res = root + "/app/src/main/res"
    for folder, px in (("drawable-xxhdpi", 144), ("drawable-xxxhdpi", 192)):
        os.makedirs(f"{res}/{folder}", exist_ok=True)
        with open(f"{res}/{folder}/ob_app_icon_placeholder.png", "wb") as f:
            f.write(icon if px == 144 else png(render(px)))
    if len(sys.argv) > 2:
        render(512).save(sys.argv[2])
    print("app_icon.png", len(icon), "bytes")


if __name__ == "__main__":
    main()
