from PIL import Image, ImageDraw, ImageFilter
import numpy as np

S = 1024
BG = (5, 30, 60, 255)
WHITE = (255, 255, 255, 255)

# ---- extract the REAL MyLRC M (white mask, car removed) ----
src = Image.open('/home/hatch/workspace/my-lrc-auto/mylrcauto/app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png').convert('RGB')
a = np.array(src).astype(int)
mask = (a[:, :, 0] > 200) & (a[:, :, 1] > 200) & (a[:, :, 2] > 200)
mask[255:, :] = False  # drop the car
# keep only the M's connected component (a thin car-roof line hides in rows 240-254)
try:
    from scipy import ndimage
    lab, n = ndimage.label(mask)
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    keep = 1 + int(np.argmax(sizes))
    mask = lab == keep
    print('components:', n, 'kept size:', int(sizes.max()))
except ImportError:
    print('no scipy - component filter skipped')

# ---- lengthen the legs: cut at y=180 (only the two stems cross there), shift bottom down ----
CUT, DY = 180, 55
top = mask[:CUT, :]
bottom = mask[CUT:, :]
stretched = np.zeros((432 + DY, 432), dtype=bool)
stretched[:CUT, :] = top
stretched[CUT + DY:, :] = bottom
for y in range(CUT, CUT + DY):  # fill the gap with the stem cross-section
    stretched[y, :] = mask[CUT - 1, :]

m_alpha = Image.fromarray((stretched * 255).astype(np.uint8), 'L')
# M bbox in stretched coords: x 118..307, y 123..309 -> scale 4.0, place top at y=110, center x=512
m_big = m_alpha.resize((432 * 4, (432 + DY) * 4), Image.LANCZOS)
m_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
white_full = Image.new("RGBA", m_big.size, WHITE)
m_layer.paste(white_full, (512 - (212 * 4 - 118 * 4) - 118 * 4 + 118 * 4 - 340, 0), m_big)  # placeholder, fixed below
# simpler exact placement: crop bbox then paste
bbox = m_big.getbbox()
m_crop = m_big.crop(bbox)
m_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mx = 512 - m_crop.size[0] // 2
my = 160
m_layer.paste(Image.new("RGBA", m_crop.size, WHITE), (mx, my), m_crop)
print('M placed size', m_crop.size, 'at', mx, my)

# ---- microphones, crossed, inside the long legs ----
def make_mic():
    W, H = 300, 520
    mic = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(mic)
    cx = W // 2
    d.rounded_rectangle([cx - 64, 10, cx + 64, 180], radius=60, outline=WHITE, width=14)
    for y in (62, 108, 154):
        d.line([(cx - 60, y), (cx + 60, y)], fill=WHITE, width=10)
    d.polygon([(cx - 24, 190), (cx + 24, 190), (cx + 10, 490), (cx - 10, 490)], outline=WHITE, width=14)
    d.line([(cx - 21, 222), (cx + 21, 222)], fill=WHITE, width=10)
    return mic.resize((252, 436), Image.LANCZOS)

mic = make_mic()
mic_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
for ang in (24, -24):
    r = mic.rotate(ang, resample=Image.BICUBIC, expand=True)
    mic_layer.alpha_composite(r, (512 - r.size[0] // 2, 615 - r.size[1] // 2))

def glow_composite(base, sharp, blur=14, passes=2):
    glow = sharp.filter(ImageFilter.GaussianBlur(blur))
    for _ in range(passes):
        base.alpha_composite(glow)
    base.alpha_composite(sharp)
    return base

icon = Image.new("RGBA", (S, S), BG)
icon = glow_composite(icon, m_layer, blur=14)
icon = glow_composite(icon, mic_layer, blur=12)
icon.convert("RGB").save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-1024.png")
icon.convert("RGB").resize((192, 192), Image.LANCZOS).save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-192.png")
print("done")
