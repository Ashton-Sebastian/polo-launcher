import sys, numpy as np
from PIL import Image, ImageDraw, ImageFilter
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")
W, H = im.size
a = np.asarray(im).astype(np.float32) / 255
rgb, al = a[..., :3], a[..., 3]
mx, mn = rgb.max(-1), rgb.min(-1)
L = (mx + mn) / 2
sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
orange = (r > g) & (g > b) & (sat > 0.12)

def poly_mask(polys, blur=2):
    m = Image.new("L", (W, H), 0); d = ImageDraw.Draw(m)
    for p in polys: d.polygon(p, fill=255)
    return np.asarray(m.filter(ImageFilter.GaussianBlur(blur))).astype(np.float32) / 255

glass = poly_mask([
    [(284,204),(400,196),(520,197),(570,235),(622,284),(470,288),(320,291),(305,255)],   # windscreen
    [(200,200),(275,197),(300,225),(318,262),(306,287),(285,290),(205,288),(195,250)],   # front door glass
    [(128,210),(198,200),(190,250),(186,286),(120,281),(108,255)],                        # rear door glass
])
mirror = poly_mask([[(212,276),(240,270),(275,272),(282,292),(262,302),(215,300)]], 1)
plate = poly_mask([[(556,407),(671,405),(671,433),(556,435)]], 0.6)
taillight = poly_mask([[(52,285),(68,280),(70,340),(55,345)]], 1)

out_rgb = rgb.copy()
# 1) silver paint -> white: lift neutral mid/high tones, keep shading
bluish = b >= r
sat_lim = np.where(bluish, 0.55, 0.24)
neutral = np.clip((sat_lim - sat) / 0.12, 0, 1) * np.clip((L - 0.30) / 0.08, 0, 1)
wheels = Image.new("L", (W, H), 0); wd = ImageDraw.Draw(wheels)
for cx, cy, rr in [(328, 468, 58), (74, 455, 44), (690, 505, 40)]:
    wd.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=255)
wheels = np.asarray(wheels.filter(ImageFilter.GaussianBlur(2))).astype(np.float32) / 255
t = np.clip((L - 0.33) / 0.57, 0, 1)
newL = 0.70 + 0.30 * t ** 0.8
scale = np.where(L > 0, newL / np.maximum(L, 1e-3), 1)
white = np.clip(rgb * scale[..., None], 0, 1)
# pull toward neutral white (tiny cool tint like real white paint)
grey = white.mean(-1, keepdims=True)
white = grey * 0.85 + white * 0.15 + np.array([-0.005, 0, 0.01])
w_body = neutral * (1 - glass) * (1 - taillight) * (1 - wheels)
out_rgb = out_rgb * (1 - w_body[..., None]) + white * w_body[..., None]
# 2) kill orange garage-door reflections on paint
spill = orange & (L > 0.3)
sp = spill.astype(np.float32) * (1 - glass) * (1 - taillight)
gw = np.clip(out_rgb.mean(-1, keepdims=True) * 1.15, 0, 1)
out_rgb = out_rgb * (1 - sp[..., None]) + gw * sp[..., None]
# 3) tinted glass: darken + desaturate everything behind the windows (mirror excluded)
gm = glass * (1 - mirror)
gl = rgb.mean(-1, keepdims=True)
tint = np.clip(gl * 0.38 + np.array([0.02, 0.035, 0.06]), 0, 1)
out_rgb = out_rgb * (1 - gm[..., None]) + tint * gm[..., None]
# 4) blank number plate (white with faint edge)
pl = np.ones_like(rgb) * np.array([0.95, 0.955, 0.96])
out_rgb = out_rgb * (1 - plate[..., None]) + pl * plate[..., None]

res = np.dstack([np.clip(out_rgb, 0, 1), al])
img = Image.fromarray((res * 255).astype(np.uint8), "RGBA").transpose(Image.FLIP_LEFT_RIGHT)  # face left like stock
img = img.crop(img.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox())
img.save(out, optimize=True)
print(img.size)
