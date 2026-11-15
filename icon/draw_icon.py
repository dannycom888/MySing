from PIL import Image, ImageDraw, ImageFilter

S = 1024
BG = (5, 30, 60, 255)  # #051E3C, same background as MyLRC icon
WHITE = (255, 255, 255, 255)

def glow_composite(base, sharp, blur=14, passes=2):
    glow = sharp.filter(ImageFilter.GaussianBlur(blur))
    for _ in range(passes):
        base.alpha_composite(glow)
    base.alpha_composite(sharp)
    return base

# ---------- M as musical note: EXACTLY the MyLRC icon's M, only the legs extended ----------
# MyLRC M: straight stems + straight diagonal beams to a center valley, note heads at stem bottoms.
# Here the stems run long (down to y=700) so the M encloses the crossed microphones.
m_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(m_layer)
pts = [(282, 700), (282, 205), (512, 335), (742, 205), (742, 700)]
d.line(pts, fill=WHITE, width=82, joint="curve")
for cx in (252, 712):  # note heads at the long legs' bottoms, centers y=735
    head = Image.new("RGBA", (260, 200), (0, 0, 0, 0))
    hd = ImageDraw.Draw(head)
    hd.ellipse([30, 45, 230, 155], fill=WHITE)
    head = head.rotate(18, resample=Image.BICUBIC, center=(130, 100))
    m_layer.alpha_composite(head, (cx - 130, 735 - 100))

# ---------- microphone (simple outline, long handle) ----------
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
    return mic

# crossed pair inside the M's long legs
mic = make_mic()
CX, CY = 512, 530
mic_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
for ang in (30, -30):
    r = mic.rotate(ang, resample=Image.BICUBIC, expand=True)
    mic_layer.alpha_composite(r, (CX - r.size[0] // 2, CY - r.size[1] // 2))

# ---------- compose ----------
icon = Image.new("RGBA", (S, S), BG)
icon = glow_composite(icon, m_layer, blur=14)
icon = glow_composite(icon, mic_layer, blur=12)
icon.convert("RGB").save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-1024.png")
icon.convert("RGB").resize((192, 192), Image.LANCZOS).save("/home/hatch/workspace/shield-karaoke/icon/mysing-icon-192.png")
print("done")
