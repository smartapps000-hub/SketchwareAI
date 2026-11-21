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
only the colours change (owner, 21 Nov, last words: "a true dark cast shadow on a frosted slate-obsidian glass base,
ultra-thin low-opacity grid lines, no extra edge lines, a top iOS gloss", then "remove the border; make the white
Android light white glassmorphism, not heavy"): a frosted slate-obsidian glass fill (#23262E -> #16181E) with a faint
#FF6D24 ambient tint, no border; the 45-degree long shadow as a real cast shadow (#040406, 58 % -> clear); the interior
grid lines only (the 9 / 99 and 19 / 89 lines near the circle's edge are left out, owner: "remove the 4 lines at the edges") as hairlines (0.5 unit,
warm white at 9 %; the inner lines still overlap as in the stock icon); the robot as light frosted white glass (the
base, grid and shadow behind it blurred, a white tint of 30 % at its top to 18 % at its foot, only a trace of a brighter edge (owner: "less white at the edges and around the eyes")), its
eyes holes; a soft top gloss over everything.

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
from PIL import Image, ImageChops, ImageFilter

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
GRID = ([f"M{u},0L{u},108" for u in range(29, 80, 10)] + [f"M0,{u}L108,{u}" for u in range(29, 80, 10)]
        + [f"M19,{u}L89,{u}" for u in range(29, 80, 10)] + [f"M{u},19L{u},89" for u in range(29, 80, 10)])

SIZE = 144          # the legacy icon's canvas
CENTER = 72.0
RADIUS = 66.0       # 132 px circle
K = 132.0 / 74.0    # px per viewport unit
T = CENTER - 54.0 * K


def svg(layer):
    """One layer of the icon: "back" (glass base, tint, grid, cast shadow), "robot" (the robot's shape, white), "gloss"."""
    grid = "".join(f'<path d="{d}" stroke="#FFD9B8" stroke-opacity="0.09" stroke-width="0.5" fill="none"/>' for d in GRID)
    head = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">
<defs>
  <clipPath id="circle"><circle cx="{CENTER}" cy="{CENTER}" r="{RADIUS}"/></clipPath>
  <linearGradient id="glass" x1="0.15" y1="0" x2="0.85" y2="1">
    <stop offset="0" stop-color="#23262E"/>
    <stop offset="1" stop-color="#16181E"/>
  </linearGradient>
  <radialGradient id="warm" gradientUnits="userSpaceOnUse" cx="{CENTER + RADIUS * 0.35:.3f}" cy="{CENTER + RADIUS * 0.6:.3f}" r="{RADIUS * 1.0:.3f}">
    <stop offset="0" stop-color="#FF6D24" stop-opacity="0.14"/>
    <stop offset="1" stop-color="#FF6D24" stop-opacity="0"/>
  </radialGradient>
  <radialGradient id="gloss" gradientUnits="userSpaceOnUse" cx="{CENTER - RADIUS * 0.25:.3f}" cy="{CENTER - RADIUS * 0.95:.3f}" r="{RADIUS * 1.15:.3f}">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/>
    <stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.07"/>
    <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="longShadow" gradientUnits="userSpaceOnUse" x1="{SHADOW_FROM[0]}" y1="{SHADOW_FROM[1]}"
      x2="{SHADOW_TO[0]}" y2="{SHADOW_TO[1]}">
    <stop offset="0" stop-color="#040406" stop-opacity="0.58"/>
    <stop offset="1" stop-color="#040406" stop-opacity="0"/>
  </linearGradient>
</defs>
"""
    viewport = f'<g transform="translate({T:.4f},{T:.4f}) scale({K:.6f})">'
    if layer == "back":
        body = f"""<g clip-path="url(#circle)">
  <rect width="{SIZE}" height="{SIZE}" fill="url(#glass)"/>
  <rect width="{SIZE}" height="{SIZE}" fill="url(#warm)"/>
  {viewport}{grid}<path d="{LONG_SHADOW}" fill="url(#longShadow)"/></g>
</g>"""
    elif layer == "robot":
        body = f'{viewport}<path d="{ROBOT}" fill="#FFFFFF" fill-rule="nonzero"/></g>'
    else:
        body = f'<g clip-path="url(#circle)"><rect width="{SIZE}" height="{SIZE}" fill="url(#gloss)"/></g>'
    return head + body + "\n</svg>"


def layer(name, big):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg(name).encode(), output_width=big,
                                                  output_height=big))).convert("RGBA")


def render(px):
    """No border (owner, 21 Nov: "remove the border"); the robot as light white frosted glass ("light white glass
    morphism, not heavy"): what is behind it blurred, a soft white tint brighter at the top, a fine bright top edge."""
    scale = 8
    big = px * scale
    f = big / SIZE
    back = layer("back", big)
    mask = layer("robot", big).split()[3]
    # frosted: the base, grid and shadow behind the robot, blurred
    frosted = back.filter(ImageFilter.GaussianBlur(2.2 * f))
    art = Image.composite(frosted, back, mask)
    # the white glass tint: 30 % at the top of the head, 18 % at its foot
    tint = Image.new("RGBA", (big, big), (255, 255, 255, 0))
    top = int((T + 38.0 * K) * f)
    bottom = int((T + 64.0 * K) * f)
    from PIL import ImageDraw
    d = ImageDraw.Draw(tint)
    for y in range(top, bottom + 1):
        t = (y - top) / max(1, bottom - top)
        d.line([(0, y), (big, y)], fill=(255, 255, 255, int(255 * (0.30 - 0.12 * t))))
    tint.putalpha(Image.composite(tint.split()[3], Image.new("L", (big, big), 0), mask))
    art = Image.alpha_composite(art, tint)
    # the glass edge: a fine bright line along the outline, strongest at the top
    edge = ImageChops.subtract(mask, mask.filter(ImageFilter.MinFilter(int(1.0 * f) | 1)))
    fade = Image.new("L", (big, big), 0)
    fd = ImageDraw.Draw(fade)
    for y in range(top, bottom + 1):
        t = (y - top) / max(1, bottom - top)
        fd.line([(0, y), (big, y)], fill=int(255 * (0.16 - 0.12 * t)))
    edge = ImageChops.multiply(edge, fade)
    art = Image.alpha_composite(art, Image.merge("RGBA", (Image.new("L", (big, big), 255),) * 3 + (edge,)))
    art = Image.alpha_composite(art, layer("gloss", big))
    # the legacy icon's soft drop shadow below the circle (as the stock icon has: strongest under its bottom edge)
    shadow_mask = Image.new("L", (big, big), 0)
    ds = ImageDraw.Draw(shadow_mask)
    r = RADIUS * f
    cx, cy = CENTER * f, (CENTER + 1.6) * f
    ds.ellipse([cx - r, cy - r, cx + r, cy + r], fill=110)
    shadow_mask = shadow_mask.filter(ImageFilter.GaussianBlur(1.6 * f))
    shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    shadow.putalpha(shadow_mask)
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
