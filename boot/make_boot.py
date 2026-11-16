#!/usr/bin/env python3
"""Make VW start-up logo files for an Android head unit.

Outputs (in ./out):
  logo_<W>x<H>.png / .bmp / .jpg  - static start-up logo (for the unit's "Car logo / Boot logo" setting or USB upload)
  bootanimation.zip               - Android boot animation (VW logo fades in, then a soft glow pulse)

Usage:  python3 make_boot.py               (1280x720, most 9" units)
        python3 make_boot.py 1024 600      (older 1024x600 units)
Needs Pillow:  python3 -m pip install pillow
"""
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFilter

W, H = (int(sys.argv[1]), int(sys.argv[2])) if len(sys.argv) > 2 else (1280, 720)
FPS = 30
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", f"{W}x{H}")
NAVY = (0, 30, 80)
SS = 4  # supersampling


def stroke_polygon(pts, w):
    """Outline polygon for a polyline of width w with mitred joints."""
    def unit(x, y):
        l = math.hypot(x, y)
        return x / l, y / l
    left, right = [], []
    n = len(pts)
    for i, (px, py) in enumerate(pts):
        if i == 0 or i == n - 1:
            a, b = (pts[0], pts[1]) if i == 0 else (pts[-2], pts[-1])
            dx, dy = unit(b[0] - a[0], b[1] - a[1])
            nx, ny, m = -dy, dx, w / 2
        else:
            d1 = unit(px - pts[i - 1][0], py - pts[i - 1][1])
            d2 = unit(pts[i + 1][0] - px, pts[i + 1][1] - py)
            n1, n2 = (-d1[1], d1[0]), (-d2[1], d2[0])
            nx, ny = unit(n1[0] + n2[0], n1[1] + n2[1])
            m = (w / 2) / (nx * n1[0] + ny * n1[1])
        left.append((px + nx * m, py + ny * m))
        right.append((px - nx * m, py - ny * m))
    return left + right[::-1]


def vw_logo(d):
    """VW roundel (flat 2019 style), d pixels across, RGBA. Geometry in units of a 100-wide circle."""
    S = d * SS
    k = S / 100.0
    tx = lambda pts: [((x + 50) * k, (y + 50) * k) for x, y in pts]
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    # navy disc with a slight top-left highlight
    disc = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.radial_gradient("L").resize((S * 2, S * 2))
    hl = Image.new("RGBA", (S, S), (10, 58, 134, 255))
    base = Image.new("RGBA", (S, S), NAVY + (255,))
    mask = grad.crop((int(S * 0.6), int(S * 0.7), int(S * 0.6) + S, int(S * 0.7) + S))
    disc = Image.composite(base, hl, mask)
    circle = Image.new("L", (S, S), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, S - 1, S - 1), fill=255)
    img.paste(disc, (0, 0), circle)
    # white ring
    r_out, r_in = 47.3 * k, 43.9 * k
    c = S / 2
    dr.ellipse((c - r_out, c - r_out, c + r_out, c + r_out), fill=(255, 255, 255, 255))
    dr.ellipse((c - r_in, c - r_in, c + r_in, c + r_in), fill=NAVY + (255,))
    # letters, clipped to the inside of the ring (they merge into it)
    letters = Image.new("L", (S, S), 0)
    ld = ImageDraw.Draw(letters)
    sw = 4.6
    ld.polygon(tx(stroke_polygon([(-16.2, -48), (0, -6), (16.2, -48)], sw)), fill=255)
    ld.polygon(tx(stroke_polygon([(-39.2, -34), (-9.2, 44), (0, 20), (9.2, 44), (39.2, -34)], sw)), fill=255)
    clip = Image.new("L", (S, S), 0)
    rc = 44.6 * k
    ImageDraw.Draw(clip).ellipse((c - rc, c - rc, c + rc, c + rc), fill=255)
    letters = Image.composite(letters, Image.new("L", (S, S), 0), clip)
    img.paste((255, 255, 255, 255), (0, 0), letters)
    return img.resize((d, d), Image.LANCZOS)


def background(glow=0.0):
    """Very dark blue radial background, optional blue glow behind the logo (0..1)."""
    bg = Image.new("RGB", (W, H), (0, 4, 12))
    g = Image.radial_gradient("L").resize((int(W * 1.5), int(W * 1.5)))
    g = g.point(lambda v: 255 - v)  # bright centre
    layer = Image.new("RGB", g.size, (6, 40, 96))
    strength = 0.55 + 0.45 * glow
    g = g.point(lambda v: int(v * strength))
    bg.paste(layer, ((W - g.size[0]) // 2, (H - g.size[1]) // 2), g)
    return bg


def frame(alpha, scale, glow):
    bg = background(glow)
    d = int(H * 0.46 * scale)
    logo = vw_logo(d)
    if glow > 0:
        halo = Image.new("RGBA", (d + 160, d + 160), (0, 0, 0, 0))
        ImageDraw.Draw(halo).ellipse((80, 80, 80 + d, 80 + d), fill=(70, 160, 255, int(120 * glow * alpha)))
        halo = halo.filter(ImageFilter.GaussianBlur(36))
        bg.paste(halo, ((W - halo.size[0]) // 2, (H - halo.size[1]) // 2), halo)
    if alpha < 1:
        a = logo.getchannel("A").point(lambda v: int(v * alpha))
        logo.putalpha(a)
    bg.paste(logo, ((W - d) // 2, (H - d) // 2), logo)
    return bg


def png_bytes(im):
    b = io.BytesIO()
    im.save(b, "PNG", optimize=True)
    return b.getvalue()


def main():
    os.makedirs(OUT, exist_ok=True)
    still = frame(1, 1, 0.6)
    name = f"logo_{W}x{H}"
    still.save(os.path.join(OUT, name + ".png"))
    still.save(os.path.join(OUT, name + ".bmp"))           # 24-bit BMP, what most units' logo pickers want
    still.save(os.path.join(OUT, name + ".jpg"), quality=95)

    # boot animation: part0 = 1.2 s fade/scale in (plays once), part1 = 2 s glow pulse (loops until Android is up)
    zpath = os.path.join(OUT, "bootanimation.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_STORED) as z:  # must be STORED (no compression)
        z.writestr("desc.txt", f"{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n")
        n0 = int(FPS * 1.2)
        for i in range(n0):
            t = i / (n0 - 1)
            e = 1 - (1 - t) ** 3  # ease-out
            z.writestr(f"part0/{i:03d}.png", png_bytes(frame(e, 0.9 + 0.1 * e, 0.6 * e)))
        n1 = FPS * 2
        for i in range(n1):
            g = 0.6 + 0.4 * math.sin(i / n1 * 2 * math.pi)
            z.writestr(f"part1/{i:03d}.png", png_bytes(frame(1, 1, g)))
    print("wrote", OUT)
    for f in sorted(os.listdir(OUT)):
        print(f"  {f}  {os.path.getsize(os.path.join(OUT, f)) // 1024} KB")


if __name__ == "__main__":
    main()
