#!/usr/bin/env python3
"""_34 surgical erase: the severed hand (core x134-172 y285-310 at
alpha>110) is bridged to the body by a faint string/smudge, so the
generic fragment rule kept it. Erase every non-body pixel inside
the box x[90,235] y[270,470]; the body is protected by its
alpha>110 core dilated 3px. Then the generic cloud pass."""
import numpy as np
from PIL import Image
from scipy import ndimage
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from erase_pass import erase, MASTERS, RES

p = os.path.join(MASTERS, 'dancer_modern_woman_34.png')
im = Image.open(p).convert('RGBA')
arr = np.asarray(im).copy()
a = arr[:, :, 3].astype(int)
lab, n = ndimage.label(a > 110)
sizes = ndimage.sum(a > 110, lab, range(1, n + 1))
main = np.argmax(sizes) + 1
keep = ndimage.binary_dilation(lab == main, iterations=3)
box = np.zeros(a.shape, bool)
box[270:471, 90:236] = True
kill = box & ~keep
arr[kill] = (0, 0, 0, 0)
print('surgical removed:', int(kill.sum()))
out, rep = erase(Image.fromarray(arr))
print('generic pass:', rep)
out.save(p, optimize=True)
out.save(os.path.join(RES, 'dancer_modern_woman_34.webp'), 'WEBP', quality=93, method=6)
