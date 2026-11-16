#!/usr/bin/env python3
"""Measure per-frame horizontal centroid stability along the real
DancerPoseOrder tours (v1.0.41 investigation).

The user (2026-11-16): the dancers "flash" left-right — not a pose
continuity problem. The bbox centres measure steady, but the BODY
MASS centroid jumps every step. This script reproduces that
measurement exactly as the main line took it:

  - frames loaded in DancerPoseOrder.kt tour order (parsed from
    the Kotlin source, the runtime truth);
  - per frame: alpha bbox; torso band = rows in [25%, 60%) of the
    bbox height; pelvis band = rows in [55%, 75%) of bbox height;
  - band centroid x = alpha-weighted mean column, as % of canvas
    width;
  - adjacent-step |delta| along the tour (open chain: N-1 deltas
    for N frames): mean / count over 2% / max.

Usage: python3 dancers/measure_centroid.py [--json out.json]
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
FRAMES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
ORDER_KT = PROJECT / "app/src/main/java/com/dannycom888/shieldkaraoke/ui/DancerPoseOrder.kt"

PREFIX = {
    "MODERN": "dancer_modern_woman",
    "ANCIENT": "dancer_ancient_woman",
    "SUMO": "dancer_sumo",
    "ANCIENT_GIRL": "dancer_ancient_girl",
    "BABY": "dancer_baby",
    "ROBOT": "dancer_robot",
}
KEY_OF = {
    "MODERN": "modern", "ANCIENT": "ancient", "SUMO": "sumo",
    "ANCIENT_GIRL": "ancient_girl", "BABY": "baby", "ROBOT": "robot",
}

ALPHA_MIN = 8  # ignore faint specks when finding the body


def parse_orders() -> dict:
    text = ORDER_KT.read_text()
    orders = {}
    for m in re.finditer(r"DancerKeys\.(\w+) to listOf\(([^)]*)\)", text):
        nums = [int(x) for x in re.findall(r"\d+", m.group(2))]
        orders[KEY_OF[m.group(1)]] = nums
    return orders


def frame_stats(im: Image.Image) -> dict:
    a = np.asarray(im.convert("RGBA"))[:, :, 3].astype(np.float64)
    h, w = a.shape
    mask = a >= ALPHA_MIN
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    top, bottom = rows[0], rows[-1]
    left, right = cols[0], cols[-1]
    bh = bottom - top + 1

    def band_centroid(f0: float, f1: float) -> float:
        r0 = top + int(f0 * bh)
        r1 = top + int(f1 * bh)
        band = a[r0:r1, :]
        band = np.where(band >= ALPHA_MIN, band, 0.0)
        total = band.sum()
        if total <= 0:
            return float("nan")
        xs = np.arange(w, dtype=np.float64)
        return float((band.sum(axis=0) * xs).sum() / total) / w * 100.0

    return {
        "bbox_center_x_pct": (left + right) / 2.0 / w * 100.0,
        "torso_x_pct": band_centroid(0.25, 0.60),
        "pelvis_x_pct": band_centroid(0.55, 0.75),
        "bbox": (int(left), int(top), int(right), int(bottom)),
        "canvas": (int(w), int(h)),
    }


def chain_stats(values: list) -> dict:
    deltas = [abs(values[i + 1] - values[i]) for i in range(len(values) - 1)]
    return {
        "mean": float(np.mean(deltas)),
        "over2": int(sum(1 for d in deltas if d > 2.0)),
        "steps": len(deltas),
        "max": float(np.max(deltas)),
    }


def main() -> None:
    orders = parse_orders()
    result = {}
    for key, order in orders.items():
        prefix = PREFIX[[k for k, v in KEY_OF.items() if v == key][0]]
        stats = []
        for idx in order:
            p = FRAMES_DIR / f"{prefix}_{idx + 1:02d}.webp"
            stats.append(frame_stats(Image.open(p)))
        entry = {
            "frames": len(order),
            "bbox_center": chain_stats([s["bbox_center_x_pct"] for s in stats]),
            "torso": chain_stats([s["torso_x_pct"] for s in stats]),
            "pelvis": chain_stats([s["pelvis_x_pct"] for s in stats]),
        }
        result[key] = entry
        print(f"{key:14s} n={entry['frames']:3d}  "
              f"bbox Δmean {entry['bbox_center']['mean']:.2f}%  "
              f"torso Δmean {entry['torso']['mean']:.2f}% "
              f"(>{2}%: {entry['torso']['over2']}/{entry['torso']['steps']}, max {entry['torso']['max']:.1f}%)  "
              f"pelvis Δmean {entry['pelvis']['mean']:.2f}%")
    if "--json" in sys.argv:
        out = sys.argv[sys.argv.index("--json") + 1]
        Path(out).write_text(json.dumps(result, indent=1))
        print("wrote", out)


if __name__ == "__main__":
    main()
