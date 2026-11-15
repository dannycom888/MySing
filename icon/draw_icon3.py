from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from scipy import ndimage

S = 1024
BG = (5, 30, 60, 255)
WHITE = (255, 255, 255, 255)

# ---- real MyLRC M, legs lengthened (same extraction as before) ----
src = Image.open('/home/hatch/workspace/my-lrc-auto/mylrcauto/app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png').convert('RGB')
a = np.array(src).astype(int)
mask = (a[:, :, 0] > 200) & (a[:, :, 1] > 200) & (a[:, :, 2] > 200)
mask[255:, :] = False
lab, n = ndimage.label(mask)
sizes = ndimage.sum(mask, lab, range(1, n + 1))
mask = lab == (1 + int(np.argmax(sizes)))
CUT, DY = 180, 55
stretched = np.zeros((432 + DY, 432), dtype=bool)
stretched[:CUT, :] = mask[:CUT, :]
stretched[CUT + DY:, :] = mask[CUT:, :]
for y in range(CUT, CUT + DY):
    stretched[y, :] = mask[CUT - 1, :]
m_alpha = Image.fromarray((stretched * 255).astype(np.uint8), 'L')
bbox = m_alpha.getbbox()
m_crop = m_alpha.crop(bbox)

# ---- smaller M, centered, whole artwork inside the launcher safe zone ----
SCALE = 3.2
m_big = m_crop.resize((int(m_crop.size[0] * SCALE / 4.0 * 4), int(m_crop.size[1] * SCALE)), Image.LANCZOS) if False else m_crop.resize((round(m_crop.size[0] * SCALE), round(m_crop.size[1] * SCALE)), Image.LANCZOS)
m_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mx = (S - m_big.size[0]) // 2
my = (S - m_big.size[1]) // 2 - 10
m_layer.paste(Image.new("RGBA", m_big.size, WHITE), (mx, my), m_big)
m_mask = np.array(m_layer)[:, :, 3] > 60
print('M size', m_big.size, 'placed at', mx, my)

# ---- microphone ----
def make_mic(h):
    W, H = 300, 520
    mic = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(mic)
    cx = W // 2
    d.rounded_rectangle([cx - 64, 10, cx + 64, 180], radius=60, outline=WHITE, width=14)
    for y in (62, 108, 154):
        d.line([(cx - 60, y), (cx + 60, y)], fill=WHITE, width=10)
    d.polygon([(cx - 24, 190), (cx + 24, 190), (cx + 10, 490), (cx - 10, 490)], outline=WHITE, width=14)
    d.line([(cx - 21, 222), (cx + 21, 222)], fill=WHITE, width=10)
    return mic.resize((round(W * h / H), h), Image.LANCZOS)

def mic_layer_for(h, ang, cy):
    mic = make_mic(h)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for a_ in (ang, -ang):
        r = mic.rotate(a_, resample=Image.BICUBIC, expand=True)
        layer.alpha_composite(r, (512 - r.size[0] // 2, cy - r.size[1] // 2))
    return layer

# auto-fit: biggest, most-slanted mics that keep >=12px clearance from the M everywhere
m_dilated = ndimage.binary_dilation(m_mask, iterations=12)
best = None
for ang in (40, 38, 36):
    for h in range(400, 260, -10):
        for cy in range(520, 640, 10):
            layer = mic_layer_for(h, ang, cy)
            mm = np.array(layer)[:, :, 3] > 60
            if not (mm & m_dilated).any():
                best = (h, ang, cy, layer)
                break
        if best: break
    if best: break
h, ang, cy, mic_layer = best
print('mics fitted: height', h, 'angle', ang, 'center y', cy)

# safe-zone check: all artwork within r=350 circle around center
yy, xx = np.mgrid[0:S, 0:S]
circle = (xx - 512) ** 2 + (yy - 512) ** 2 <= 350 ** 2
art = m_mask | (np.array(mic_layer)[:, :, 3] > 60)
print('artwork fully inside safe circle:', bool((art & ~circle).sum() == 0))

def glow_composite(base, sharp, blur=13, passes=2):
    glow = sharp.filter(ImageFilter.GaussianBlur(blur))
    for _ in range(passes):
        base.alpha_composite(glow)
    base.alpha_composite(sharp)
    return base

icon = Image.new("RGBA", (S, S), BG)
icon = glow_composite(icon, m_layer)
icon = glow_composite(icon, mic_layer, blur=11)
icon.convert("RGB").save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-1024.png")
# simulate phone launcher masks: circle crop and squircle-ish rounded crop
sq = icon.convert("RGB").resize((384, 384), Image.LANCZOS)
cmask = Image.new("L", (384, 384), 0)
ImageDraw.Draw(cmask).ellipse([0, 0, 384, 384], fill=255)
out = Image.new("RGBA", (384, 384), (30, 30, 30, 255)); out.paste(sq, (0, 0), cmask)
out.convert("RGB").save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-circle-preview.png")
print("done")
