import numpy as np
from PIL import Image
from scipy import ndimage as ndi
from skimage.graph import MCP_Geometric

W, H = 1920, 1280
OUT_H = 600
scale = OUT_H / H
RES = "../app/src/main/res/drawable-nodpi"

def geodesic_labels(alpha_path, seeds):
    rgba = np.asarray(Image.open(alpha_path).convert("RGBA")).astype(np.float32)
    a = rgba[..., 3]
    fg = a > 40
    cost = np.where(fg, 255.0 - a + 1.0, 1e6).astype(np.float32)
    mcp = MCP_Geometric(cost)
    _, tr = mcp.find_costs(starts=[(y, x) for (y, x) in seeds])
    return rgba, fg, np.argmin(np.stack(tr), axis=0)

def keep_main_body(mask, r=5, min_core=300):
    """Drop stray fragments: erode, find solid cores, keep only pixels nearest the main core."""
    er = ndi.binary_erosion(mask, structure=np.ones((2 * r + 1, 2 * r + 1)), border_value=0)
    lab, n = ndi.label(er)
    if n <= 1:
        return mask
    sizes = ndi.sum_labels(er, lab, index=np.arange(1, n + 1))
    cores = [i + 1 for i, s in enumerate(sizes) if s >= min_core]
    if len(cores) <= 1:
        return mask
    main = cores[int(np.argmax([sizes[c - 1] for c in cores]))]
    core_mask = np.isin(lab, cores)
    _, inds = ndi.distance_transform_edt(~core_mask, return_indices=True)
    nearest = lab[tuple(inds)]
    kept = mask & (nearest == main)
    print(f"    cores={len(cores)} dropped_area={int((mask & (nearest != main)).sum())}")
    return kept

def emit(rgba, mask, name):
    out = rgba.copy()
    out[..., 3] = np.where(mask, rgba[..., 3], 0)
    ys, xs = np.where(mask)
    x0, x1 = xs.min(), xs.max() + 1
    crop = out[:, x0:x1]
    im = Image.fromarray(crop.astype(np.uint8), "RGBA")
    im = im.resize((max(1, round(im.width * scale)), OUT_H), Image.LANCZOS)
    im.save(f"{RES}/{name}.png")
    bg = Image.new("RGBA", im.size, (10, 26, 48, 255)); bg.alpha_composite(im)
    bg.convert("RGB").save(f"cutout_work/check_{name}.png")
    print(f"  {name}: {im.size} {__import__('os').path.getsize(f'{RES}/{name}.png')}B")

# --- 1. modern woman: fix leftover sleeve fragment with stronger erosion ---
print("modern woman fix:")
rgba3 = np.asarray(Image.open("cutout_work/full_alpha_v3.png").convert("RGBA")).astype(np.float32)
mask = np.asarray(Image.open(f"{RES}/dancer_modern_woman.png").convert("RGBA"))[..., 3] > 0
# rebuild her full-res mask from the v3 geodesic instead: rerun split quickly using saved labels is complex;
# simpler: work on upscaled current png mask is lossy -> instead re-derive from alpha via geodesic seeds of v3
seeds_v3 = [(620, 330), (680, 850), (880, 1280), (930, 1690)]
_, fg3, lab3 = geodesic_labels("cutout_work/full_alpha_v3.png", seeds_v3)
m = fg3 & (lab3 == 0)
zone = np.zeros_like(m); zone[470:830, 545:665] = True
print("  fragment-zone pixels before:", int((m & zone).sum()))
for r in (9, 13):
    mm = keep_main_body(m, r=r)
    print(f"  r={r} fragment-zone pixels after:", int((mm & zone).sum()))
    if int((mm & zone).sum()) < 50:
        m = mm; break
    m = mm
emit(rgba3, m, "dancer_modern_woman")

# --- 2. baby from v6 ---
print("baby from v6:")
seeds_v6 = [(620, 330), (680, 850), (880, 1180), (930, 1480), (1130, 1210), (950, 1740)]
rgba6, fg6, lab6 = geodesic_labels("cutout_work/full_alpha_v6.png", seeds_v6)
emit(rgba6, keep_main_body(fg6 & (lab6 == 4)), "dancer_baby")

# --- 3. robot from v7 ---
print("robot from v7:")
seeds_v7 = [(620, 330), (680, 850), (880, 1180), (930, 1480), (1080, 1210), (600, 1640)]
rgba7, fg7, lab7 = geodesic_labels("cutout_work/full_alpha_v7.png", seeds_v7)
emit(rgba7, keep_main_body(fg7 & (lab7 == 5), r=3), "dancer_robot")

# --- preview row of all six ---
names = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_baby",
         "dancer_modern_girl", "dancer_ancient_girl", "dancer_robot"]
imgs = [Image.open(f"{RES}/{n}.png") for n in names]
row = Image.new("RGBA", (sum(i.width for i in imgs) + 50, OUT_H), (10, 26, 48, 255))
x = 0
for im in imgs:
    row.alpha_composite(im, (x, OUT_H - im.height)); x += im.width + 10
row.convert("RGB").save("cutout_work/preview_row.png")
print("preview_row:", row.size)
