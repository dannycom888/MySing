import numpy as np, os
from PIL import Image
from scipy import ndimage as ndi
from skimage.graph import MCP_Geometric

H = 1280; OUT_H = 600; scale = OUT_H / H
RES = "../app/src/main/res/drawable-nodpi"

def group_costs(alpha_path, seed_groups):
    rgba = np.asarray(Image.open(alpha_path).convert("RGBA")).astype(np.float32)
    a = rgba[..., 3]
    fg = a > 40
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

def comps(mask, min_area=150):
    lab, n = ndi.label(mask, structure=np.ones((3, 3)))
    out = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(ys) >= min_area:
            out.append((len(ys), xs.min(), xs.max(), ys.min(), ys.max(), i))
    return lab, sorted(out, reverse=True)

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

# ---------- modern woman: diagnose scraps ----------
print("modern woman:")
rgba, fg, lab = group_costs("cutout_work/full_alpha_v3.png",
                            [[(620, 330)], [(680, 850)], [(880, 1280)], [(930, 1690)]])
m = fg & (lab == 0)
z = np.zeros_like(m); z[455:920, 498:680] = True
m = m & ~z
_, cs = comps(m)
for c in cs: print("   comp area=%d x[%d,%d] y[%d,%d]" % c[:5])
# cut bridges: hand bridge left of old zone + low pink tip
z2 = np.zeros_like(m); z2[555:690, 462:680] = True; z2[840:960, 490:680] = True
m = m & ~z2
m = keep_seed_component(m, (620, 330))
_, cs = comps(m)
for c in cs: print("   after: area=%d x[%d,%d] y[%d,%d]" % c[:5])
emit(rgba, m, "dancer_modern_woman")

# ---------- baby: windowed competition ----------
print("baby:")
rgba, fg, lab = group_costs("cutout_work/full_alpha_v8.png",
                            [[(620, 330)], [(680, 850)], [(830, 1180), (640, 1180)],
                             [(930, 1480)], [(1080, 1210), (810, 1215)], [(600, 1640)]])
m = fg & (lab == 4)
win = np.zeros_like(m); win[700:1270, 1000:1405] = True
m = m & win
m = keep_seed_component(m, (1080, 1210))
_, cs = comps(m)
for c in cs: print("   comp area=%d x[%d,%d] y[%d,%d]" % c[:5])
emit(rgba, m, "dancer_baby")

# ---------- robot: bun exclusion ----------
print("robot:")
rgba, fg, lab = group_costs("cutout_work/full_alpha_v7.png",
                            [[(620, 330)], [(680, 850)], [(880, 1180)], [(930, 1480)],
                             [(1080, 1210)], [(600, 1640)]])
m = fg & (lab == 5)
_, cs = comps(m)
for c in cs[:4]: print("   comp area=%d x[%d,%d] y[%d,%d]" % c[:5])
z3 = np.zeros_like(m); z3[615:795, 1380:1542] = True
m = m & ~z3
m = keep_seed_component(m, (600, 1640))
_, cs = comps(m)
for c in cs: print("   after: area=%d x[%d,%d] y[%d,%d]" % c[:5])
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
