import numpy as np, os
from PIL import Image
from scipy import ndimage as ndi
from skimage.graph import MCP_Geometric

H = 1280; OUT_H = 600; scale = OUT_H / H
RES = "../app/src/main/res/drawable-nodpi"

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

def emit(rgba, mask, name, alpha_override=None):
    out = rgba.copy()
    base_a = rgba[..., 3] if alpha_override is None else alpha_override
    out[..., 3] = np.where(mask, base_a, 0)
    ys, xs = np.where(mask)
    crop = out[:, xs.min():xs.max() + 1]
    im = Image.fromarray(crop.astype(np.uint8), "RGBA")
    im = im.resize((max(1, round(im.width * scale)), OUT_H), Image.LANCZOS)
    im.save(f"{RES}/{name}.png")
    bg = Image.new("RGBA", im.size, (10, 26, 48, 255)); bg.alpha_composite(im)
    bg.convert("RGB").save(f"cutout_work/check_{name}.png")
    print(f"  {name}: {im.size} {os.path.getsize(f'{RES}/{name}.png')}B")

# ---------- ancient woman v9 (sheer gauze) ----------
print("ancient woman v9:")
rgba, fg, lab = group_labels("cutout_work/full_alpha_v9.png",
                             [[(620, 330)], [(680, 850)], [(830, 1180), (640, 1180)],
                              [(930, 1480)], [(1080, 1210), (810, 1215)], [(600, 1640)]],
                             fg_thresh=12)
a = rgba[..., 3]
m = fg & (lab == 1)
m = keep_seed_component(m, (680, 850))
# body core (solid alpha) forced opaque; gauze keeps rembg semi-transparency
core = ndi.binary_erosion((a > 120) & m, structure=np.ones((7, 7)), border_value=0)
alpha_out = np.where(core, np.maximum(a, 245), a)
hist = np.histogram(a[m], bins=[0, 20, 40, 80, 120, 180, 255])[0]
print("   alpha hist in mask:", hist, "core px:", int(core.sum()), "mask px:", int(m.sum()))
emit(rgba, m, "dancer_ancient_woman", alpha_override=alpha_out)

# ---------- baby: exclusions + head restore ----------
print("baby fix:")
rgba, fg, lab = group_labels("cutout_work/full_alpha_v8.png",
                             [[(620, 330)], [(680, 850)], [(830, 1180), (640, 1180)],
                              [(930, 1480)], [(1080, 1210), (810, 1215)], [(600, 1640)]])
m = fg & (lab == 4)
win = np.zeros_like(m); win[700:1270, 1000:1405] = True
m = m & win
ex = np.zeros_like(m)
ex[940:1090, 995:1105] = True    # modern girl's skirt at his left
ex[690:790, 1000:1125] = True    # her pigtail above his head-left
m = m & ~ex
# restore his forehead inside head circle
yy, xx = np.mgrid[0:1280, 0:1920]
head = ((xx - 1215) ** 2 + (yy - 812) ** 2) <= 100 ** 2
m = m | (fg & head & ~ex)
m = keep_seed_component(m, (1080, 1210))
emit(rgba, m, "dancer_baby")

# ---------- robot: extend bun exclusion ----------
print("robot fix:")
rgba, fg, lab = group_labels("cutout_work/full_alpha_v7.png",
                             [[(620, 330)], [(680, 850)], [(880, 1180)], [(930, 1480)],
                              [(1080, 1210)], [(600, 1640)]])
m = fg & (lab == 5)
z = np.zeros_like(m); z[615:855, 1360:1505] = True
m = m & ~z
m = keep_seed_component(m, (600, 1640))
emit(rgba, m, "dancer_robot")

names = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_baby",
         "dancer_modern_girl", "dancer_ancient_girl", "dancer_robot"]
imgs = [Image.open(f"{RES}/{n}.png") for n in names]
row = Image.new("RGBA", (sum(i.width for i in imgs) + 50, OUT_H), (10, 26, 48, 255))
x = 0
for im in imgs:
    row.alpha_composite(im, (x, OUT_H - im.height)); x += im.width + 10
row.convert("RGB").save("cutout_work/preview_row.png")
print("preview_row:", row.size)
