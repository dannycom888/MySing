#!/usr/bin/env python3
"""Cut the 4 dancers out of the approved design image (v3).

rembg's alpha merges neighbouring characters (their fabrics touch
in the generated art, so connected components and erosion cannot
separate them). Instead: geodesic Voronoi — every foreground pixel
is assigned to the character seed reachable by the shortest path
*inside* the foreground mask. A sleeve tip stays with its owner
because the path through her own arm is shorter than the path
through the touching neighbour. Seeds sit on each torso.

Each character keeps the FULL canvas height so the four keep their
relative sizes and share the stage-floor baseline; all are then
scaled by one common factor (height 600).
"""
import numpy as np
from PIL import Image
from skimage.graph import MCP_Geometric
import os

SRC = os.path.expanduser("~/workspace/shield-karaoke/dancers/media-generation-mysing-dancers-design-3d-0-eb2b820b-901e-4037-b2a4-ec29665c436b.png")
OUTDIR = os.path.expanduser("~/workspace/shield-karaoke/app/src/main/res/drawable-nodpi")
WORK = os.path.expanduser("~/workspace/shield-karaoke/dancers/cutout_work")
os.makedirs(OUTDIR, exist_ok=True)
os.makedirs(WORK, exist_ok=True)
NAMES = ["dancer_modern_woman", "dancer_ancient_woman", "dancer_modern_girl", "dancer_ancient_girl"]
# (y, x) torso seeds
SEEDS = [(620, 330), (680, 850), (880, 1280), (930, 1690)]

img = Image.open(SRC).convert("RGB")
W, H = img.size
alpha = np.asarray(Image.open(os.path.join(WORK, "full_alpha.png")))[:, :, 3].astype(np.float32) / 255.0

domain = alpha > 0.03
cost = np.where(domain, 1.0, 1e7)
dists = []
for s in SEEDS:
    mcp = MCP_Geometric(cost)
    d, _ = mcp.find_costs(starts=[s])
    dists.append(d)
    print("seed", s, "reached", int((d < 1e6).sum()), "px")
label = np.argmin(np.stack(dists), axis=0)
label[~domain] = -1
for k, name in enumerate(NAMES):
    ys, xs = np.where(label == k)
    print(f"{name}: {len(xs)} px, x[{xs.min()},{xs.max()}] y[{ys.min()},{ys.max()}]")

rgb = np.asarray(img)
scale = 600 / H
for k, name in enumerate(NAMES):
    a = np.clip((np.where(label == k, alpha, 0.0) - 0.04) / 0.96, 0, 1)
    xs = np.where((label == k).any(axis=0))[0]
    x0, x1 = max(0, xs.min() - 6), min(W, xs.max() + 7)
    out = np.zeros((H, x1 - x0, 4), dtype=np.uint8)
    out[:, :, :3] = rgb[:, x0:x1]
    out[:, :, 3] = (a[:, x0:x1] * 255).astype(np.uint8)
    im = Image.fromarray(out, "RGBA").resize((round((x1 - x0) * scale), 600), Image.LANCZOS)
    path = os.path.join(OUTDIR, name + ".png")
    im.save(path, optimize=True)
    print("saved", name, im.size, os.path.getsize(path), "bytes")
    canvas = Image.new("RGB", (im.width + 16, im.height + 16), (96, 24, 96))
    canvas.paste(im, (8, 8), im)
    canvas.save(os.path.join(WORK, f"check_{name}.png"))

# applause-row preview at natural relative sizes
prev = Image.new("RGB", (1500, 460), (13, 27, 62))
x = 8
for name in NAMES:
    im = Image.open(os.path.join(OUTDIR, name + ".png"))
    im2 = im.resize((round(im.width * 430 / 600), 430), Image.LANCZOS)
    prev.paste(im2, (x, 460 - 430), im2)
    x += im2.width + 10
prev.save(os.path.join(WORK, "preview_row.png"))
print("previews saved")
