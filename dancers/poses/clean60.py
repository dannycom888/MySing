#!/usr/bin/env python3
"""v1.0.22 targeted cleanup for new frames 41..60 whose QA showed
(a) flat sheet-navy regions the geodesic vote kept (background
enclosed by raised arms / near-touching neighbours) and/or
(b) small detached floaters from a neighbour.

Rule: zero alpha where the pixel is the sheet's flat navy (sampled
per dancer from its Y1 sheet corner, tight tolerance — character
shading is textured and never this exact flat colour over areas);
then drop solid components that are <6% of the largest AND do not
intersect the largest component's bbox (the v1.0.7 floater rule).
Recompose on the frame's own canvas width, height 600, bottom
margin 12 — pixel scale untouched. Prints every change."""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

DRAWABLE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                            "..", "..", "app", "src", "main", "res", "drawable-nodpi")

TARGETS = {
    "modern_woman": [49, 50],
    "ancient_woman": [41, 42, 47, 48, 53, 54, 55, 56],
    "ancient_girl": [47],
    "baby": [42],
    "robot": [46, 51, 54],
}


def bg_color(key):
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "extra60", f"sheet_{key}_Y1.png")
    im = np.asarray(Image.open(p).convert("RGB")).astype(int)
    return im[4, 4]


for key, nums in TARGETS.items():
    bg = bg_color(key)
    for n in nums:
        name = f"dancer_{key}_{n:02d}"
        path = os.path.join(base.MASTERS, name + ".png")
        im = Image.open(path)
        W = im.width
        arr = np.asarray(im).copy()
        rgb = arr[:, :, :3].astype(int)
        flat = (np.abs(rgb[:, :, 0] - bg[0]) <= 14) & \
               (np.abs(rgb[:, :, 1] - bg[1]) <= 14) & \
               (np.abs(rgb[:, :, 2] - bg[2]) <= 16) & (arr[:, :, 3] > 0)
        n_flat = int(flat.sum())
        arr[:, :, 3] = np.where(flat, 0, arr[:, :, 3])
        a = arr[:, :, 3].astype(np.float32) / 255.0
        lab, cnt = ndimage.label(a > 0.5)
        dropped = []
        if cnt > 1:
            sizes = ndimage.sum(a > 0.5, lab, range(1, cnt + 1))
            j = int(np.argmax(sizes))
            ys, xs = np.where(lab == j + 1)
            bbox = (ys.min(), ys.max(), xs.min(), xs.max())
            for c in range(cnt):
                if c == j or sizes[c] >= 0.06 * sizes[j]:
                    continue
                cys, cxs = np.where(lab == c + 1)
                if cys.min() <= bbox[1] and cys.max() >= bbox[0] and \
                        cxs.min() <= bbox[3] and cxs.max() >= bbox[2]:
                    continue
                arr[:, :, 3] = np.where(lab == c + 1, 0, arr[:, :, 3])
                dropped.append(int(sizes[c]))
        img = Image.fromarray(arr)
        bb = img.getchannel("A").point(lambda v: 255 if v > 10 else 0).getbbox()
        content = img.crop(bb)
        out = Image.new("RGBA", (W, base.CANVAS_H), (0, 0, 0, 0))
        out.alpha_composite(content, ((W - content.width) // 2,
                                      base.CANVAS_H - base.BOTTOM_MARGIN - content.height))
        out.save(path)
        out.save(os.path.join(DRAWABLE_DIR, name + ".webp"),
                 "WEBP", quality=93, method=6)
        print(f"{name}: flat-navy removed={n_flat}, floaters dropped={dropped}")
print("clean60 done")
