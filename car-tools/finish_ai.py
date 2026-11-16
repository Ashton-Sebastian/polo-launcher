"""Turn the cut-out AI render into car.png: trim, soft contact shadow, 900 px wide."""
import sys
from PIL import Image, ImageDraw, ImageFilter
src, out = sys.argv[1], sys.argv[2]
car = Image.open(src).convert("RGBA")
a = car.getchannel("A").filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
car.putalpha(a)
car = car.crop(car.getchannel("A").point(lambda v: 255 if v > 10 else 0).getbbox())
W, H = car.size
pad = int(H * 0.10)
canvas = Image.new("RGBA", (W, H + pad), (0, 0, 0, 0))
sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
d = ImageDraw.Draw(sh)
d.ellipse((int(W * .03), int(H * .84), int(W * .99), int(H * 1.04)), fill=(2, 10, 30, 140))
d.ellipse((int(W * .10), int(H * .90), int(W * .62), int(H * 1.01)), fill=(0, 4, 16, 160))
d.ellipse((int(W * .76), int(H * .86), int(W * .97), int(H * .98)), fill=(0, 4, 16, 140))
sh = sh.filter(ImageFilter.GaussianBlur(H * 0.025))
canvas = Image.alpha_composite(canvas, sh)
canvas.alpha_composite(car, (0, 0))
canvas = canvas.resize((900, int(900 * canvas.height / canvas.width)), Image.LANCZOS)
canvas.save(out, optimize=True)
print(canvas.size)
