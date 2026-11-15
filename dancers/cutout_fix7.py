import numpy as np, os
from PIL import Image
from scipy import ndimage as ndi
from skimage.graph import MCP_Geometric

H = 1280; OUT_H = 600; scale = OUT_H / H
RES = "../app/src/main/res/drawable-nodpi"
yy, xx = np.mgrid[0:1280, 0:1920]

def group_labels(alpha_path, seed_groups, fg_thresh=40):
    rgba = np.asarray(Image.open(alpha_path).convert("RGBA")).astype(np.float32)
    a = rgba[..., 3]
    fg = a > fg_thresh
    cost = np.where(fg, 255.0 - a + 1.0, 1e6).astype(np.float32)
    gcs = []
    for group in seed_groups:
        best = None
        for (y, x) in group:
            mcp = MCP_Geometric(cost)
            c, _ = mcp.find_costs(starts=[(y, x)])
            best = c if best is None else np.minimum(best, c)
        gcs.append(best)
    return rgba, fg, np.argmin(np.stack(gcs), axis=0)

def keep_seed_component(mask, seed):
    lab, _ = ndi.label(mask, structure=np.ones((3, 3)))
    return lab == lab[seed]

def emit(rgba, mask, name):
    out = rgba.copy()
    out[..., 3] = np.where(mask, rgba[..., 3], 0)
    ys, xs = np.where(mask)
    crop = out[:, xs.min():xs.max() + 1]
    im = Image.fromarray(crop.astype(np.uint8), "RGBA")
    im = im.resize((max(1, round(im.width * scale)), OUT_H), Image.LANCZOS)
    im.save(f"{RES}/{name}.png")
    bg = Image.new("RGBA", im.size, (10, 26, 48, 255)); bg.alpha_composite(im)
    bg.convert("RGB").save(f"cutout_work/check_{name}.png")
    print(f"  {name}: {im.size} {os.path.getsize(f'{RES}/{name}.png')}B")

def capsule(p0, p1, w):
    (y0, x0), (y1, x1) = p0, p1
    vy, vx = y1 - y0, x1 - x0
    t = np.clip(((yy - y0) * vy + (xx - x0) * vx) / (vy * vy + vx * vx), 0, 1)
    dy = yy - (y0 + t * vy); dx = xx - (x0 + t * vx)
    return (dy * dy + dx * dx) <= (w / 2) ** 2

# ---------- baby: analytic + color trims ----------
print("baby:")
rgba = np.asarray(Image.open("cutout_work/full_alpha_v8.png").convert("RGBA")).astype(np.float32)
R, G, B = rgba[..., 0], rgba[..., 1], rgba[..., 2]
fg = rgba[..., 3] > 40
head = (xx - 1215) ** 2 + (yy - 812) ** 2 <= 108 ** 2
torso = ((xx - 1220) / 118) ** 2 + ((yy - 1010) / 125) ** 2 <= 1
legs = (yy >= 1020) & (yy <= 1262) & (xx >= 1092) & (xx <= 1348)
shape = head | torso | legs | capsule((950, 1150), (845, 1065), 58) | capsule((950, 1295), (840, 1340), 58)
ex = np.zeros_like(fg); ex[0:735, 1000:1170] = True
m = fg & shape & ~ex
band = np.zeros_like(fg); band[695:775, 1080:1355] = True
pinkish = (B > G + 8) & (R > G + 20)
print("   pink cut:", int((m & band & pinkish).sum())); m = m & ~(band & pinkish)
reg = np.zeros_like(fg); reg[940:1125, 1000:1115] = True
purple = (B > R + 15) & (B > G + 35)
print("   purple cut:", int((m & reg & purple).sum())); m = m & ~(reg & purple)
shoe = np.zeros_like(fg); shoe[1035:1140, 1328:1380] = True
m = m & ~shoe
m = keep_seed_component(m, (1080, 1210))
emit(rgba, m, "dancer_baby")

# ---------- robot: gold trim ----------
print("robot:")
rgba7, fg7, lab7 = group_labels("cutout_work/full_alpha_v7.png",
                                [[(620, 330)], [(680, 850)], [(880, 1180)], [(930, 1480)],
                                 [(1080, 1210)], [(600, 1640)]])
m = fg7 & (lab7 == 5)
z = np.zeros_like(m); z[615:855, 1360:1505] = True
m = m & ~z
bun = ((xx - 1535) / 48) ** 2 + ((yy - 762) / 78) ** 2 <= 1
m = m & ~bun
R, G, B = rgba7[..., 0], rgba7[..., 1], rgba7[..., 2]
reg = np.zeros_like(m); reg[620:860, 1480:1650] = True
gold = (R > 170) & (G > 120) & (B < 110)
print("   gold cut:", int((m & reg & gold).sum())); m = m & ~(reg & gold)
m = keep_seed_component(m, (600, 1640))
emit(rgba7, m, "dancer_robot")

names = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_baby",
         "dancer_modern_girl", "dancer_ancient_girl", "dancer_robot"]
imgs = [Image.open(f"{RES}/{n}.png") for n in names]
row = Image.new("RGBA", (sum(i.width for i in imgs) + 50, OUT_H), (10, 26, 48, 255))
x = 0
for im in imgs:
    row.alpha_composite(im, (x, OUT_H - im.height)); x += im.width + 10
row.convert("RGB").save("cutout_work/preview_row.png")
print("preview_row:", row.size)
