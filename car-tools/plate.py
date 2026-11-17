"""Write a registration on the render's blank number plate, warped to the plate's angle.
usage: plate.py render.png out.png "CF 195 305"
"""
import sys, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
src, out, reg = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert('RGB')
# plate face corners in the 1536x1024 render: top-left, top-right, bottom-right, bottom-left
quad = [(244.5, 572.5), (412.5, 578.5), (411.5, 625.5), (243.5, 617.0)]
TW, TH = 760, 210
font = ImageFont.truetype('/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf', 190)
txt = Image.new('L', (TW, TH), 0)
d = ImageDraw.Draw(txt)
w = d.textlength(reg, font=font)
if w > TW * 0.9:
    font = ImageFont.truetype('/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf', int(190 * TW * 0.9 / w))
    w = d.textlength(reg, font=font)
bb = d.textbbox((0, 0), reg, font=font)
d.text(((TW - w) / 2, (TH - (bb[3] - bb[1])) / 2 - bb[1]), reg, font=font, fill=255)

def coeffs(dst, srcpts):
    # perspective coefficients mapping output (dst) points -> input (src) points
    A, B = [], []
    for (x, y), (u, v) in zip(dst, srcpts):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float)).tolist()

c = coeffs(quad, [(0, 0), (TW, 0), (TW, TH), (0, TH)])
mask = txt.transform(im.size, Image.PERSPECTIVE, c, Image.BICUBIC).filter(ImageFilter.GaussianBlur(0.35))
ink = Image.new('RGB', im.size, (22, 24, 28))
im.paste(ink, (0, 0), mask.point(lambda v: int(v * 0.92)))
im.save(out)
print('ok', reg)
