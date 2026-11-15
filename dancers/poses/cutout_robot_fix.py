#!/usr/bin/env python3
"""Robot frames 11..40 for MySing v1.0.7 — rescue pass.

The generic pipeline (cutout_poses40.py) failed on robot at the
bad-frame guard: two sheets mis-separate with component-seeded
cores —
  * X3 frame 25 (walking pose): its strong-alpha core was lost,
    so the geodesic vote handed its body to a neighbour
    (frame 23 came out at ~2.5x normal mass, frame 25 at 12k).
  * X5 frame 33 (hands-behind-head pose): rembg's alpha for this
    figure is uniformly faint (p50 0.14, max 0.36 — the
    modern_woman_30 failure), so no strong core existed for it
    either, and its pixels were split between frames 32/34.
Fix: for X3/X5 seed the five cores from column-mass valley
slices (one core per slice, from the slice's largest strong
blob, faint figures falling back to their largest faint blob),
then run the same geodesic assignment. Any frame whose owned
alpha stays faint (p50 < 0.5 — frame 33) additionally gets its
alpha rebuilt from its silhouette (shape right, levels wrong).
X1/X2/X4/X6 use the standard separation.
Scale (0.58, consensus — see cutout_poses40) and canvas
(421x600, baseline 12) match the shipped robot frames.
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

EXTRA = os.path.join(base.D, "extra40")
SCALE = 0.5800
CANVAS_W = 421


def slice_cores(alpha):
    """One seed core per figure, from valley-split column slices."""
    strong = alpha > 0.20
    faint = alpha > 0.05
    pts = base.split_points(alpha)
    bounds = [0] + pts + [alpha.shape[1]]
    cores = []
    for i in range(5):
        lo, hi = bounds[i], bounds[i + 1]
        core = None
        for mask in (strong, faint):
            sl = np.zeros_like(mask)
            sl[:, lo:hi] = mask[:, lo:hi]
            lab, n = ndimage.label(sl)
            if n:
                sizes = ndimage.sum(sl, lab, range(1, n + 1))
                j = int(np.argmax(sizes))
                if sizes[j] >= 800:
                    core = lab == j + 1
                    break
        assert core is not None, f"slice {i} has no core"
        cores.append(core)
    return cores


def frames_of_sheet_sliced(session, path):
    """frames_of_sheet with slice-seeded cores (see module doc)."""
    from rembg import remove
    from skimage.graph import MCP_Geometric

    img = Image.open(path).convert("RGB")
    alpha = np.asarray(remove(img, session=session, only_mask=True)).astype(np.float32) / 255.0
    rgb = np.asarray(img)
    cores = [base.seed_points(c) for c in slice_cores(alpha)]
    domain = ndimage.binary_dilation(alpha > 0.06, iterations=1)
    cost = np.where(domain, 1.0 + (1.0 - alpha) * 4.0, 25.0)
    dists = [MCP_Geometric(cost).find_costs(starts=c)[0] for c in cores]
    label = np.argmin(np.stack(dists), axis=0)
    owned = alpha > 0.03
    frames = []
    for i in range(5):
        a = np.where(owned & (label == i), alpha, 0.0)
        frames.append((rgb, base.clean_frame(rgb, a)))
    return frames


def rebuild_alpha(a):
    """Silhouette alpha for a figure whose rembg levels failed."""
    sil = a > 0.05
    sil = ndimage.binary_fill_holes(sil)
    lab, n = ndimage.label(sil)
    sizes = ndimage.sum(sil, lab, range(1, n + 1))
    sil = lab == (int(np.argmax(sizes)) + 1)
    out = ndimage.gaussian_filter(sil.astype(np.float32), sigma=1.2)
    return np.clip(out / 0.85, 0, 1)


def main():
    from rembg import new_session

    session = new_session("u2net")
    raw = []
    for x in range(1, 7):
        path = os.path.join(EXTRA, f"sheet_robot_X{x}.png")
        if x in (3, 5):
            got = frames_of_sheet_sliced(session, path)
        else:
            got = base.frames_of_sheet(session, path)
        raw += got
        print(f"sheet X{x} separated")
    assert len(raw) == 30

    # No silhouette rebuilds: with slice-seeded cores every
    # frame's own alpha is healthy (the faint readings in the
    # first diagnostic were measured on starved assignments).
    # An earlier run rebuilt frames 17/34 from their silhouettes
    # and the fill swept in background slabs — standard firming
    # is correct for all thirty.
    rebuilt = set()

    masses = sorted(int((a > 0.15).sum()) for _, a in raw)
    median = masses[len(masses) // 2]
    print("robot masses min", masses[0], "median", median)
    assert masses[0] >= 0.35 * median, "still a near-empty frame"

    boxes = [base.content_bbox(a) for _, a in raw]
    # Canvas: shipped width, grown if a new pose is wider (frame 16
    # is 1 px wider than the shipped canvas) — same rule as the
    # main pipeline: widest content + 16, transparent padding only.
    widest = max(round((b[3] - b[2] + 1) * SCALE) for b in boxes)
    canvas_w = max(CANVAS_W, widest + 16)
    print("robot new-frame canvas width:", canvas_w)
    for n, ((rgb, a), (y0, y1, x0, x1)) in enumerate(zip(raw, boxes), start=11):
        if n not in rebuilt:  # rebuilt alphas are already final
            a = np.clip((a - 0.10) / 0.75, 0, 1)
        crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
        crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
        crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
        im = Image.fromarray(crop, "RGBA")
        nw = round(im.width * SCALE)
        nh = round(im.height * SCALE)
        assert nw <= canvas_w, (n, nw)
        im = im.resize((nw, nh), Image.LANCZOS)
        canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - nw) // 2, base.CANVAS_H - base.BOTTOM_MARGIN - nh), im)
        name = f"dancer_robot_{n:02d}"
        canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
        out = os.path.join(base.OUTDIR, name + ".webp")
        canvas.save(out, "WEBP", quality=93, method=6)
        print(name, canvas.size, os.path.getsize(out), "bytes")
    print("done")


if __name__ == "__main__":
    main()
