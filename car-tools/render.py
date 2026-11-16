import sys, numpy as np
from PIL import Image, ImageFilter, ImageDraw
src, out = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGBA")
# work at 2x for smoother result
im = im.resize((im.width * 2, im.height * 2), Image.LANCZOS)
W, H = im.size
rgb_img = im.convert("RGB")
# 1) render-like smoothing: median removes photo grain, then gentle blend back for detail
smooth = rgb_img.filter(ImageFilter.MedianFilter(5)).filter(ImageFilter.SMOOTH)
rgb = np.asarray(rgb_img).astype(np.float32) / 255
sm = np.asarray(smooth).astype(np.float32) / 255
rgb = sm * 0.7 + rgb * 0.3
al = np.asarray(im.getchannel("A")).astype(np.float32) / 255
L = rgb.mean(-1)
yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
# 2) shadows pick up the navy of the UI (deep blacks -> blue-black)
navy = np.array([0.03, 0.09, 0.22])
dark_w = np.clip((0.35 - L) / 0.35, 0, 1)[..., None] * 0.55
rgb = rgb * (1 - dark_w) + (rgb * 0.5 + navy) * dark_w
# 3) paint: cool blue environment reflection increasing toward the bottom of the body
bright = np.clip((L - 0.55) / 0.25, 0, 1)
refl = np.array([0.62, 0.80, 1.00])
w = (bright * np.clip((yy - 0.3) / 0.7, 0, 1) * 0.6)[..., None]
rgb = rgb * (1 - w) + rgb * refl * w + refl * 0.08 * w
# top surfaces (roof/bonnet) catch a soft white-cyan sky
w2 = (bright * np.clip((0.45 - yy) / 0.45, 0, 1) * 0.25)[..., None]
rgb = rgb * (1 - w2) + np.array([0.93, 0.98, 1.0]) * w2
# 4) glass: dark windows get a blue sky reflection, strongest at the top
glassy = (np.clip((0.32 - L) / 0.2, 0, 1) * np.clip((0.55 - yy) / 0.55, 0, 1))[..., None]
rgb = rgb * (1 - glassy * 0.5) + np.array([0.12, 0.30, 0.55]) * glassy * 0.5
rgb = np.clip(rgb, 0, 1)
# 5) clean edges: shrink alpha 1px and feather (removes photo fringe)
a_img = Image.fromarray((al * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
car = Image.fromarray((rgb * 255).astype(np.uint8), "RGB").convert("RGBA")
car.putalpha(a_img)
# 6) canvas with soft contact shadow under the car
pad_b = int(H * 0.12)
canvas = Image.new("RGBA", (W, H + pad_b), (0, 0, 0, 0))
sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
d = ImageDraw.Draw(sh)
d.ellipse((int(W * 0.02), int(H * 0.86), int(W * 0.99), int(H * 1.04)), fill=(2, 10, 30, 150))
d.ellipse((int(W * 0.06), int(H * 0.90), int(W * 0.60), int(H * 1.00)), fill=(0, 4, 16, 170))  # under front wheels
sh = sh.filter(ImageFilter.GaussianBlur(H * 0.03))
canvas = Image.alpha_composite(canvas, sh)
canvas = Image.alpha_composite(canvas, Image.new("RGBA", canvas.size, (0, 0, 0, 0)))
canvas.paste(car, (0, 0), car)
canvas = canvas.resize((W // 2 * 1 + 0, (H + pad_b) // 2), Image.LANCZOS) if False else canvas.resize((900, int(900 * (H + pad_b) / W)), Image.LANCZOS)
canvas.save(out, optimize=True)
print(canvas.size)
