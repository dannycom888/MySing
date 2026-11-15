#!/usr/bin/env python3
"""Targeted fragment cleanup for four v1.0.7 frames whose QA
contact sheets showed a detached foreign fragment (a neighbour's
arm/sleeve/shoe that the geodesic vote attached and clean_frame
kept): modern_woman 24 + 34, ancient_woman 14, robot 25.

For each named master: label solid (a>0.5) components, keep the
largest, and zero any other component that is small (<6% of the
largest) AND does not intersect the largest component's bounding
box — i.e. a floater, not a body part. Prints what it drops so
the change is auditable. Masters and WebPs are rewritten.
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

TARGETS = [("modern_woman", 24), ("modern_woman", 34),
           ("ancient_woman", 14), ("robot", 25)]


def main():
    for key, n in TARGETS:
        name = f"dancer_{key}_{n:02d}"
        path = os.path.join(base.MASTERS, name + ".png")
        im = Image.open(path)
        arr = np.asarray(im).copy()
        a = arr[:, :, 3].astype(np.float32) / 255.0
        lab, cnt = ndimage.label(a > 0.5)
        sizes = ndimage.sum(a > 0.5, lab, range(1, cnt + 1))
        j = int(np.argmax(sizes))
        ys, xs = np.where(lab == j + 1)
        bbox = (ys.min(), ys.max(), xs.min(), xs.max())
        dropped = []
        for c in range(cnt):
            if c == j or sizes[c] >= 0.06 * sizes[j]:
                continue
            ys2, xs2 = np.where(lab == c + 1)
            inter = not (ys2.max() < bbox[0] or ys2.min() > bbox[1]
                         or xs2.max() < bbox[2] or xs2.min() > bbox[3])
            if not inter:
                arr[lab == c + 1, 3] = 0
                dropped.append((int(sizes[c]),
                                (int(ys2.mean()), int(xs2.mean()))))
        out = Image.fromarray(arr, "RGBA")
        out.save(path, optimize=True)
        out.save(os.path.join(base.OUTDIR, name + ".webp"),
                 "WEBP", quality=93, method=6)
        print(name, "dropped:", dropped if dropped else "nothing")
    print("done")


if __name__ == "__main__":
    main()
