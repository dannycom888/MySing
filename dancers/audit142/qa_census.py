#!/usr/bin/env python3
"""v1.0.42 final QA census (read-only): per shipped dancer asset report
component count (alpha>16), semi-transparent pixel share, content bbox.
Flags: >1 component with size>=25px, or bbox touching canvas left/right edge.
Run after repairs land; output compared against ADJUDICATION.md."""
import os
import numpy as np
from PIL import Image
from scipy import ndimage

RES = os.path.expanduser('~/workspace/shield-karaoke/app/src/main/res/drawable-nodpi')
KEYS = ['modern_woman', 'ancient_woman', 'sumo', 'ancient_girl', 'baby', 'robot']

def census(path):
    im = Image.open(path).convert('RGBA')
    a = np.asarray(im)[:, :, 3]
    lab, n = ndimage.label(a > 16)
    sizes = ndimage.sum(a > 16, lab, range(1, n + 1)) if n else []
    big = [int(s) for s in sizes if s >= 25]
    ys, xs = np.nonzero(a > 16)
    semi = int(((a > 8) & (a < 200)).sum())
    return im.size, big, (int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())), semi

for key in KEYS:
    tags = ['still', 'invite'] + [f'{i:02d}' for i in range(1, (61 if key == 'robot' else 101))]
    for tag in tags:
        if tag == 'still':
            p = os.path.join(RES, f'dancer_{key}.png')
        elif tag == 'invite':
            p = os.path.join(RES, f'dancer_{key}_invite.webp')
        else:
            p = os.path.join(RES, f'dancer_{key}_{tag}.webp')
        if not os.path.exists(p):
            print(f'{key} {tag}: MISSING FILE'); continue
        (w, h), big, (x0, x1, y0, y1), semi = census(p)
        notes = []
        if len(big) > 1:
            notes.append(f'COMPONENTS={big}')
        if x0 == 0 or x1 == w - 1:
            notes.append(f'EDGE-TOUCH x[{x0},{x1}] w={w}')
        if notes:
            print(f'{key} {tag}: ' + ' '.join(notes))
print('census done')
