import numpy as np, os
from PIL import Image
from scipy import ndimage as ndi
from skimage.graph import MCP_Geometric

H = 1280
OUT_H = 600
scale = OUT_H / H
RES = "../app/src/main/res/drawable-nodpi"

def labels_for(alpha_path, seeds):
    rgba = np.asarray(Image.open(alpha_path).convert("RGBA")).astype(np.float32)
    a = rgba[..., 3]
    fg = a > 40
    cost = np.where(fg, 255.0 - a + 1.0, 1e6).astype(np.float32)
    costs = []
    for (y, x) in seeds:
        mcp = MCP_Geometric(cost)
        c, _ = mcp.find_costs(starts=[(y, x)])
        costs.append(c)
    lab = np.argmin(np.stack(costs), axis=0)
    areas = [int((fg & (lab == i)).sum()) for i in range(len(seeds))]
    print("  label areas:", areas)
    return rgba, fg, lab

def keep_main_body(mask, r=5, min_core=300):
    er = ndi.binary_erosion(mask, structure=np.ones((2 * r + 1, 2 * r + 1)), border_value=0)
    lab, n = ndi.label(er)
    if n <= 1: return mask
    sizes = ndi.sum_labels(er, lab, index=np.arange(1, n + 1))
    cores = [i + 1 for i, s in enumerate(sizes) if s >= min_core]
    if len(cores) <= 1: return mask
    main = cores[int(np.argmax([sizes[c - 1] for c in cores]))]
    core_mask = np.isin(lab, cores)
    _, inds = ndi.distance_transform_edt(~core_mask, return_indices=True)
    kept = mask & (lab[tuple(inds)] == main)
    print(f"    dropped stray area={int((mask & ~kept).sum())}")
    return kept

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

print("modern woman (v3) + rectangle cut of sleeve fragment:")
rgba, fg, lab = labels_for("cutout_work/full_alpha_v3.png",
                           [(620, 330), (680, 850), (880, 1280), (930, 1690)])
m = fg & (lab == 0)
zone = np.zeros_like(m); zone[460:840, 532:672] = True
print("  zone px before:", int((m & zone).sum()))
m = m & ~zone
m = keep_main_body(m)
print("  zone px after:", int((m & zone).sum()))
emit(rgba, m, "dancer_modern_woman")

print("baby (v8, standing):")
rgba, fg, lab = labels_for("cutout_work/full_alpha_v8.png",
                           [(620, 330), (680, 850), (830, 1180), (930, 1480), (1080, 1210), (600, 1640)])
emit(rgba, keep_main_body(fg & (lab == 4)), "dancer_baby")

print("robot (v7, tall):")
rgba, fg, lab = labels_for("cutout_work/full_alpha_v7.png",
                           [(620, 330), (680, 850), (880, 1180), (930, 1480), (1080, 1210), (600, 1640)])
emit(rgba, keep_main_body(fg & (lab == 5), r=3), "dancer_robot")

names = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_baby",
         "dancer_modern_girl", "dancer_ancient_girl", "dancer_robot"]
imgs = [Image.open(f"{RES}/{n}.png") for n in names]
row = Image.new("RGBA", (sum(i.width for i in imgs) + 50, OUT_H), (10, 26, 48, 255))
x = 0
for im in imgs:
    row.alpha_composite(im, (x, OUT_H - im.height)); x += im.width + 10
row.convert("RGB").save("cutout_work/preview_row.png")
print("preview_row:", row.size)
