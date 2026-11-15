from PIL import Image, ImageDraw, ImageFilter
import numpy as np
from scipy import ndimage

S = 1024
BG = (5, 30, 60, 255)
WHITE = (255, 255, 255, 255)

# ---- real MyLRC M with lengthened legs, smaller (safe-zone) ----
src = Image.open('/home/hatch/workspace/my-lrc-auto/mylrcauto/app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png').convert('RGB')
a = np.array(src).astype(int)
mask = (a[:, :, 0] > 200) & (a[:, :, 1] > 200) & (a[:, :, 2] > 200)
mask[255:, :] = False
lab, n = ndimage.label(mask)
sizes = ndimage.sum(mask, lab, range(1, n + 1))
mask = lab == (1 + int(np.argmax(sizes)))
CUT, DY = 180, 55
st = np.zeros((432 + DY, 432), dtype=bool)
st[:CUT] = mask[:CUT]; st[CUT + DY:] = mask[CUT:]
for y in range(CUT, CUT + DY):
    st[y] = mask[CUT - 1]
m_alpha = Image.fromarray((st * 255).astype(np.uint8), 'L')
m_crop = m_alpha.crop(m_alpha.getbbox())
SCALE = 2.85
m_big = m_crop.resize((round(m_crop.size[0] * SCALE), round(m_crop.size[1] * SCALE)), Image.LANCZOS)
m_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mx, my = (S - m_big.size[0]) // 2, (S - m_big.size[1]) // 2
m_layer.paste(Image.new("RGBA", m_big.size, WHITE), (mx, my), m_big)
m_mask = np.array(m_layer)[:, :, 3] > 60
dist = ndimage.distance_transform_edt(~m_mask)

# ---- microphone: BIG head, THICK strokes, SHORT handle ----
def make_mic_img(h):
    W, H = 300, 460
    mic = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(mic)
    cx = W // 2
    d.rounded_rectangle([cx - 74, 8, cx + 74, 210], radius=72, outline=WHITE, width=21)
    for y in (76, 128, 180):
        d.line([(cx - 68, y), (cx + 68, y)], fill=WHITE, width=13)
    d.polygon([(cx - 31, 220), (cx + 31, 220), (cx + 20, 432), (cx - 20, 432)], outline=WHITE, width=21)
    d.line([(cx - 27, 256), (cx + 27, 256)], fill=WHITE, width=12)
    return mic.resize((round(W * h / H), h), Image.LANCZOS)

def pair_mask(h, ang):
    mic = make_mic_img(h)
    pair = Image.new("RGBA", (760, 760), (0, 0, 0, 0))
    for a_ in (ang, -ang):
        r = mic.rotate(a_, resample=Image.BICUBIC, expand=True)
        pair.alpha_composite(r, (380 - r.size[0] // 2, 380 - r.size[1] // 2))
    pm = np.array(pair)[:, :, 3] > 60
    ys, xs = np.where(pm)
    return pm[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

# fit: largest mics, slant >= 42, clearance >= 12
best = None
for h in range(360, 220, -10):
    for ang in (46, 44, 42):
        pm = pair_mask(h, ang)
        ph, pw = pm.shape
        found = None
        for cx in range(520, 621, 10):
            for cy in range(460, 601, 10):
                x0, y0 = cx - pw // 2, cy - ph // 2
                if x0 < 0 or y0 < 0 or x0 + pw > S or y0 + ph > S:
                    continue
                clr = dist[y0:y0 + ph, x0:x0 + pw][pm].min()
                if clr >= 12 and (found is None or clr > found[0]):
                    found = (clr, cx, cy)
            if found: break
        if found:
            best = (h, ang, found[1], found[2], found[0])
            break
    if best: break
h, ang, cx, cy, clr = best
print('fitted: mic height', h, 'angle', ang, 'center', cx, cy, 'clearance', round(float(clr), 1))

mic_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mic_img = make_mic_img(h)
for a_ in (ang, -ang):
    r = mic_img.rotate(a_, resample=Image.BICUBIC, expand=True)
    mic_layer.alpha_composite(r, (cx - r.size[0] // 2, cy - r.size[1] // 2))

yy, xx = np.mgrid[0:S, 0:S]
circle = (xx - 512) ** 2 + (yy - 512) ** 2 <= 345 ** 2
art = m_mask | (np.array(mic_layer)[:, :, 3] > 60)
print('artwork inside safe circle r345:', bool((art & ~circle).sum() == 0))

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
sq = icon.convert("RGB").resize((384, 384), Image.LANCZOS)
cmask = Image.new("L", (384, 384), 0)
ImageDraw.Draw(cmask).ellipse([0, 0, 384, 384], fill=255)
out = Image.new("RGBA", (384, 384), (40, 40, 40, 255)); out.paste(sq, (0, 0), cmask)
out.convert("RGB").save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-circle-preview.png")
print("done")
