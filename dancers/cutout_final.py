#!/usr/bin/env python3
"""Final dancer cutouts — 5 characters.

Sources (user revisions 2026-11-15):
- characters 0..3 (modern woman [golden hair, white outfit],
  ancient woman, modern girl, ancient girl): design v3 image.
- baby: design v4 image (in v4 the baby occludes the modern
  girl's legs, which is why the girl is NOT taken from v4).

Separation: geodesic Voronoi inside the rembg foreground.
Stray-fragment removal: geodesic regions can contain a neighbour's
chunk (sleeve tip / fist) joined to the body by a wispy bridge, so
plain component analysis sees one blob. Erode the character mask
(r=5): bridges snap, body parts stay as thick cores. Assign every
mask pixel to its nearest eroded core and keep only the main
body's pixels (thin legit parts like hairpin tassels erode away
but their pixels fall to the main core and are kept).

All five keep the FULL canvas height (relative sizes + shared
floor baseline), one common scale (height 600).
"""
import numpy as np
from PIL import Image
from scipy import ndimage
from skimage.graph import MCP_Geometric
import os

D = os.path.expanduser("~/workspace/shield-karaoke/dancers")
OUTDIR = os.path.expanduser("~/workspace/shield-karaoke/app/src/main/res/drawable-nodpi")
WORK = os.path.join(D, "cutout_work")
os.makedirs(OUTDIR, exist_ok=True)
NAMES = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_modern_girl", "dancer_ancient_girl", "dancer_baby"]
SEEDS4 = [(620, 330), (680, 850), (880, 1280), (930, 1690)]       # (y, x) torsos, v3 layout
SEEDS5 = SEEDS4 + [(1080, 1330)]                                   # + baby torso, v4 layout

def geodesic_labels(alpha, seeds):
    domain = alpha > 0.03
    cost = np.where(domain, 1.0, 1e7)
    dists = [MCP_Geometric(cost).find_costs(starts=[s])[0] for s in seeds]
    label = np.argmin(np.stack(dists), axis=0)
    label[~domain] = -1
    return label

def keep_main_body(alpha_char, name):
    mask = alpha_char > 0.12
    struct = ndimage.generate_binary_structure(2, 1)
    eroded = ndimage.binary_erosion(mask, structure=struct, iterations=5)
    lab, n = ndimage.label(eroded)
    if n == 0:
        return alpha_char
    objs = ndimage.find_objects(lab)
    cores = [(int((lab[sl] == i + 1).sum()), i + 1) for i, sl in enumerate(objs)]
    cores = [c for c in cores if c[0] >= 300]
    cores.sort(reverse=True)
    main_id = cores[0][1]
    for area, cid in cores[1:]:
        print(f"  {name}: dropping stray core area={area}")
    # nearest-core assignment for every mask pixel
    _, indices = ndimage.distance_transform_edt(lab == 0, return_indices=True)
    nearest = lab[indices[0], indices[1]]
    keep = mask & (nearest == main_id)
    return np.where(keep, alpha_char, 0.0)

def emit(rgb, alpha_char, name):
    H, W = alpha_char.shape
    a = np.clip((alpha_char - 0.04) / 0.96, 0, 1)
    xs = np.where((a > 0.01).any(axis=0))[0]
    x0, x1 = max(0, xs.min() - 6), min(W, xs.max() + 7)
    out = np.zeros((H, x1 - x0, 4), dtype=np.uint8)
    out[:, :, :3] = rgb[:, x0:x1]
    out[:, :, 3] = (a[:, x0:x1] * 255).astype(np.uint8)
    im = Image.fromarray(out, "RGBA").resize((round((x1 - x0) * 600 / H), 600), Image.LANCZOS)
    path = os.path.join(OUTDIR, name + ".png")
    im.save(path, optimize=True)
    print("saved", name, im.size, os.path.getsize(path), "bytes")
    canvas = Image.new("RGB", (im.width + 16, im.height + 16), (96, 24, 96))
    canvas.paste(im, (8, 8), im)
    canvas.save(os.path.join(WORK, f"check_{name}.png"))

# --- v3: characters 0..3 ---
v3_rgb = np.asarray(Image.open(os.path.join(D, "media-generation-mysing-dancers-design-3d-v3-0-69908290-f045-4d9a-96ce-88c9ad170c76.png")).convert("RGB"))
v3_alpha = np.asarray(Image.open(os.path.join(WORK, "full_alpha_v3.png")))[:, :, 3].astype(np.float32) / 255.0
lab3 = geodesic_labels(v3_alpha, SEEDS4)
for k in range(4):
    a = keep_main_body(np.where(lab3 == k, v3_alpha, 0.0), NAMES[k])
    emit(v3_rgb, a, NAMES[k])

# --- v4: baby only ---
v4_rgb = np.asarray(Image.open(os.path.join(D, "media-generation-mysing-dancers-design-3d-v4-0-eda7bc06-1bc6-4b23-83f6-a52de5856aa1.png")).convert("RGB"))
v4_alpha = np.asarray(Image.open(os.path.join(WORK, "full_alpha_v4.png")))[:, :, 3].astype(np.float32) / 255.0
lab4 = geodesic_labels(v4_alpha, SEEDS5)
a = keep_main_body(np.where(lab4 == 4, v4_alpha, 0.0), NAMES[4])
emit(v4_rgb, a, NAMES[4])

# --- applause-row preview: modern, ancient, baby, girl, ancient girl ---
prev = Image.new("RGB", (1560, 460), (13, 27, 62))
x = 8
for name in [NAMES[0], NAMES[1], NAMES[4], NAMES[2], NAMES[3]]:
    im = Image.open(os.path.join(OUTDIR, name + ".png"))
    im2 = im.resize((round(im.width * 430 / 600), 430), Image.LANCZOS)
    prev.paste(im2, (x, 30), im2)
    x += im2.width + 8
prev.save(os.path.join(WORK, "preview_row.png"))
print("done")
