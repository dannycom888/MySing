#!/usr/bin/env python3
"""Compose the regenerated Chun-Li frames onto the series canvas:
keep the largest alpha component, firm the matte with the pose
pipeline's curve, scale the figure to the slot's target height
(the original frame's, or the tour neighbours' mean where the
original was truncated/stretched), and paste it with the bbox
bottom on the 586 baseline and the bbox centre on the original's
centre-x. Saves the PNG master + the shipped WebP (q93, m6)."""
import numpy as np
from PIL import Image
from scipy import ndimage
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
RES = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
MASTERS = os.path.join(ROOT, 'dancers/poses/frames_png')
CUT = os.path.join(ROOT, 'dancers/fix136/cut')
BASE = 586
TARGETS = {  # file# -> (figure height, bbox centre x)
    13: (491, 228), 14: (482, 225), 23: (481, 228), 30: (497, 227),
    44: (461, 226), 49: (574, 227), 53: (494, 228), 60: (575, 228),
}

for n, (th, bx) in TARGETS.items():
    im = Image.open(os.path.join(CUT, f'{n:02d}_raw.png')).convert('RGBA')
    arr = np.asarray(im).astype(np.float32)
    a = arr[:, :, 3] / 255.0
    a = np.clip((a - 0.10) / 0.75, 0.0, 1.0)
    lab, ncomp = ndimage.label(a > 0.25)
    sizes = ndimage.sum(a > 0.25, lab, range(1, ncomp + 1))
    main = np.argmax(sizes) + 1
    a = np.where(lab == main, a, 0.0)
    arr[:, :, 3] = a * 255.0
    fig = Image.fromarray(arr.astype(np.uint8))
    ys, xs = np.nonzero(a > 32 / 255.0)
    fig_h = ys.max() - ys.min() + 1
    fig = fig.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    scale = th / fig_h
    fig = fig.resize((round(fig.width * scale), th), Image.LANCZOS)
    canvas = Image.new('RGBA', (455, 600), (0, 0, 0, 0))
    x0 = int(round(bx - fig.width / 2))
    y0 = BASE - th + 1  # bbox bottom row == BASE (0-indexed: y0+th-1 = BASE-? ) -> paste so last row = 585
    y0 = BASE - th
    canvas.alpha_composite(fig, (x0, y0))
    canvas.save(os.path.join(MASTERS, f'dancer_modern_woman_{n:02d}.png'), optimize=True)
    canvas.save(os.path.join(RES, f'dancer_modern_woman_{n:02d}.webp'), 'WEBP', quality=93, method=6)
    # verify geometry on the saved webp
    chk = np.asarray(Image.open(os.path.join(RES, f'dancer_modern_woman_{n:02d}.webp')).convert('RGBA'))[:, :, 3]
    ys, xs = np.nonzero(chk > 32)
    print(f'_{n:02d}: h={ys.max()-ys.min()+1} base={ys.max()} bx={(xs.min()+xs.max())/2:.0f} '
          f'w={xs.max()-xs.min()+1} x=[{xs.min()},{xs.max()}] y=[{ys.min()},{ys.max()}]')
