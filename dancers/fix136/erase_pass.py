#!/usr/bin/env python3
"""v1.0.36 erase pass for Chun-Li frames: remove detached fragments
and faint baked shadow clouds without touching the figure.

Rules (conservative, distance-gated):
  - a connected component (alpha>32) that is not the main figure
    and sits >4px away from it is a detached fragment -> erase
    (the _34 severed hand, the _03 boot sliver, the _50 dashes);
  - pixels with 8<alpha<=90 lying >6px from the opaque core
    (alpha>128) are baked shadow cloud -> erase
    (_39's 16k px cloud, the small clouds on _27/_28/_29/_54/_86).
Everything else is byte-identical to the source.
Sources: PNG masters for frames <=60, the shipped WebP for _86.
Outputs: updated PNG master (when one exists) + WebP q93/m6.
"""
import numpy as np
from PIL import Image
from scipy import ndimage
import os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
RES = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
MASTERS = os.path.join(ROOT, 'dancers/poses/frames_png')

def load(n):
    mp = os.path.join(MASTERS, f'dancer_modern_woman_{n:02d}.png')
    if os.path.exists(mp):
        return Image.open(mp).convert('RGBA'), mp
    return Image.open(os.path.join(RES, f'dancer_modern_woman_{n:02d}.webp')).convert('RGBA'), None

def erase(im):
    arr = np.asarray(im).copy()
    a = arr[:, :, 3].astype(int)
    core = a > 128
    dist_core = ndimage.distance_transform_edt(~core)
    lab, ncomp = ndimage.label(a > 32)
    removed = np.zeros(a.shape, bool)
    report = {}
    if ncomp:
        sizes = ndimage.sum(a > 32, lab, range(1, ncomp + 1))
        main = np.argmax(sizes) + 1
        dist_main = ndimage.distance_transform_edt(lab != main)
        frag = 0
        for i in range(1, ncomp + 1):
            if i == main:
                continue
            m = (lab == i) & (dist_main > 4)
            frag += int(m.sum())
            removed |= m
        report['fragment_px'] = frag
    cloud = (a > 8) & (a <= 90) & (dist_core > 6)
    report['cloud_px'] = int((cloud & ~removed).sum())
    removed |= cloud
    arr[removed] = (0, 0, 0, 0)
    report['total_removed'] = int(removed.sum())
    return Image.fromarray(arr), report

for n in [3, 27, 28, 29, 34, 39, 50, 54, 86]:
    im, mp = load(n)
    out, rep = erase(im)
    if mp:
        out.save(mp, optimize=True)
    out.save(os.path.join(RES, f'dancer_modern_woman_{n:02d}.webp'), 'WEBP', quality=93, method=6)
    print(f'_{n:02d}: {rep}')
