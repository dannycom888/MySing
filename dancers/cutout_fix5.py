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

# ---------- baby: raw-alpha cores + nearest-core assignment in window ----------
print("baby (core method):")
rgba = np.asarray(Image.open("cutout_work/full_alpha_v8.png").convert("RGBA")).astype(np.float32)
a = rgba[..., 3]
fg = a > 40
win = np.zeros_like(fg); win[690:1270, 990:1410] = True
m0 = fg & win
r = 6
er = ndi.binary_erosion(m0, structure=np.ones((2 * r + 1, 2 * r + 1)), border_value=0)
lab, n = ndi.label(er)
sizes = ndi.sum_labels(er, lab, index=np.arange(1, n + 1))
cores = [i + 1 for i, s in enumerate(sizes) if s >= 300]
print("   cores:", len(cores), "sizes:", sorted([int(sizes[c - 1]) for c in cores], reverse=True)[:6])
core_mask = np.isin(lab, cores)
_, inds = ndi.distance_transform_edt(~core_mask, return_indices=True)
nearest = lab[tuple(inds)]
seed_core = lab[1080, 1210]
print("   seed core size:", int(sizes[seed_core - 1]))
m = m0 & (nearest == seed_core)
emit(rgba, m, "dancer_baby")

# ---------- robot: warm-dark color cut in hip region ----------
print("robot (color cut):")
rgba7, fg7, lab7 = group_labels("cutout_work/full_alpha_v7.png",
                                [[(620, 330)], [(680, 850)], [(880, 1180)], [(930, 1480)],
                                 [(1080, 1210)], [(600, 1640)]])
m = fg7 & (lab7 == 5)
z = np.zeros_like(m); z[615:855, 1360:1505] = True
m = m & ~z
R, G, B = rgba7[..., 0], rgba7[..., 1], rgba7[..., 2]
region = np.zeros_like(m); region[630:870, 1360:1660] = True
warm_dark = (R > B + 18) & (R < 130) & (G < 110)
print("   warm-dark px cut:", int((m & region & warm_dark).sum()))
m = m & ~(region & warm_dark)
m = keep_seed_component(m, (600, 1640))
emit(rgba7, m, "dancer_robot")

# ---------- modern woman: extra low zone ----------
print("modern woman (extra zone):")
rgba3, fg3, lab3 = group_labels("cutout_work/full_alpha_v3.png",
                                [[(620, 330)], [(680, 850)], [(880, 1280)], [(930, 1690)]])
m = fg3 & (lab3 == 0)
for (y0, y1, x0, x1) in [(455, 920, 498, 680), (555, 690, 462, 680),
                         (840, 960, 490, 680), (950, 1080, 495, 600)]:
    zz = np.zeros_like(m); zz[y0:y1, x0:x1] = True
    m = m & ~zz
m = keep_seed_component(m, (620, 330))
emit(rgba3, m, "dancer_modern_woman")

names = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_baby",
         "dancer_modern_girl", "dancer_ancient_girl", "dancer_robot"]
imgs = [Image.open(f"{RES}/{n}.png") for n in names]
row = Image.new("RGBA", (sum(i.width for i in imgs) + 50, OUT_H), (10, 26, 48, 255))
x = 0
for im in imgs:
    row.alpha_composite(im, (x, OUT_H - im.height)); x += im.width + 10
row.convert("RGB").save("cutout_work/preview_row.png")
print("preview_row:", row.size)
