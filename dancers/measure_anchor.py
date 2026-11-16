#!/usr/bin/env python3
"""Screen-true anchor stability along the DancerPoseOrder tours.

The stage draws every frame at the same layout height (all
canvases are 600 px tall) centred horizontally in the dancer
zone, so a body point's screen x is zoneCentre + (x - W/2) *
scale with the SAME scale for every frame. The quantity the eye
actually sees jumping is therefore the centre-relative centroid,
in source pixels — not the centroid as % of each frame's own
canvas width (the in-between frames all have different widths,
which inflates the % metric on narrow canvases).

Reports adjacent-step |delta| means along the tour for the
pelvis band (rows 55-75% of bbox) and the torso band (25-60%),
centre-relative, in px and in % of the 600 px canvas height.

Usage: python3 dancers/measure_anchor.py <frames_dir>
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from measure_centroid import PREFIX, frame_stats, parse_orders  # noqa: E402


def main() -> None:
    frames_dir = Path(sys.argv[1])
    orders = parse_orders()
    for key, order in orders.items():
        prefix = PREFIX[[k for k, v in
                         {"MODERN": "modern", "ANCIENT": "ancient",
                          "SUMO": "sumo", "ANCIENT_GIRL": "ancient_girl",
                          "BABY": "baby", "ROBOT": "robot"}.items()
                         if v == key][0]]
        torso_a, pelvis_a = [], []
        for idx in order:
            p = frames_dir / f"{prefix}_{idx + 1:02d}.webp"
            im = Image.open(p)
            w = im.width
            st = frame_stats(im)
            torso_a.append(st["torso_x_pct"] / 100.0 * w - w / 2.0)
            pelvis_a.append(st["pelvis_x_pct"] / 100.0 * w - w / 2.0)
        for name, vals in (("pelvis", pelvis_a), ("torso", torso_a)):
            d = [abs(vals[i + 1] - vals[i]) for i in range(len(vals) - 1)]
            print(f"{key:14s} {name:6s} anchor Δmean {np.mean(d):5.2f}px "
                  f"({np.mean(d) / 6.0:.2f}% of height)  max {np.max(d):.1f}px")


if __name__ == "__main__":
    main()
