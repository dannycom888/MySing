#!/usr/bin/env python3
"""v1.0.43 measurement: per-frame figure-height ratio distribution.

The user, after v1.0.42 killed the left-right flash: the dancer
still "有时会变大变小" — sometimes grows/shrinks. Cause: the stage
renders every frame at a fixed layout height, so the on-screen
figure size is the frame's own figure-height ratio (alpha bbox
height / canvas height), and the generated frames carry real
scale variance around their dancer's norm.

This script only MEASURES (the normaliser is
normalize_size_v1043.py). For every dancer it walks the real
DancerPoseOrder tour and reports, per frame and in aggregate:
  ratio  = alpha-bbox height / canvas height (ALPHA_MIN = 8,
           the same threshold the v1.0.41 alignment used)
  median / IQR / min / max of the ratio along the tour
  adjacent-step |delta ratio| mean and max along the tour,
  including the wrap edge last -> first (the tour loops)

It also measures the still and invite images against the same
yardstick, since v1.0.43 normalises those to the dance median too
(the invite -> dancing switch must not jump in size).

Run from the project root:  python3 dancers/measure_size_v1043.py
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
RES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
ORDER_KT = (PROJECT / "app/src/main/java/com/dannycom888/"
            "shieldkaraoke/ui/DancerPoseOrder.kt")

DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "sumo": "dancer_sumo",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
    "robot": "dancer_robot",
}
KEY_CONST = {
    "modern": "MODERN", "ancient": "ANCIENT", "sumo": "SUMO",
    "ancient_girl": "ANCIENT_GIRL", "baby": "BABY", "robot": "ROBOT",
}
ALPHA_MIN = 8
PELVIS_F0, PELVIS_F1 = 0.55, 0.75


def parse_orders() -> dict:
    text = ORDER_KT.read_text()
    orders = {}
    for key, const in KEY_CONST.items():
        m = re.search(r"DancerKeys\.%s to listOf\(([^)]*)\)" % const, text)
        assert m, const
        orders[key] = [int(x) for x in re.findall(r"\d+", m.group(1))]
    return orders


def frame_stats(im: Image.Image) -> dict:
    """bbox, height ratio, pelvis anchor x, foot baseline y."""
    a = np.asarray(im.convert("RGBA"))[:, :, 3].astype(np.float64)
    h, w = a.shape
    mask = a >= ALPHA_MIN
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    top, bottom = int(rows[0]), int(rows[-1])
    left, right = int(cols[0]), int(cols[-1])
    bh = bottom - top + 1
    r0 = top + int(PELVIS_F0 * bh)
    r1 = top + int(PELVIS_F1 * bh)
    band = np.where(a[r0:r1, :] >= ALPHA_MIN, a[r0:r1, :], 0.0)
    xs = np.arange(w, dtype=np.float64)
    pelvis_x = float((band.sum(axis=0) * xs).sum() / band.sum())
    return {
        "canvas": (w, h), "bbox": (left, top, right, bottom),
        "ratio": bh / h, "pelvis_x": pelvis_x, "foot_y": bottom,
    }


def distribution(ratios: list) -> dict:
    r = np.array(ratios)
    tour = list(ratios) + [ratios[0]]  # wrap edge: the tour loops
    deltas = [abs(tour[i + 1] - tour[i]) for i in range(len(ratios))]
    q1, med, q3 = np.percentile(r, [25, 50, 75])
    return {
        "n": len(ratios), "median": float(med), "q1": float(q1),
        "q3": float(q3), "iqr": float(q3 - q1),
        "min": float(r.min()), "max": float(r.max()),
        "adj_delta_mean": float(np.mean(deltas)),
        "adj_delta_max": float(np.max(deltas)),
    }


def main() -> None:
    orders = parse_orders()
    out = {}
    for key, prefix in DANCERS.items():
        order = orders[key]
        by_index = {}
        for idx in order:
            f = RES_DIR / f"{prefix}_{idx + 1:02d}.webp"
            by_index[idx] = frame_stats(Image.open(f))
        tour_ratios = [by_index[idx]["ratio"] for idx in order]
        d = distribution(tour_ratios)
        still = frame_stats(Image.open(RES_DIR / f"{prefix}.png"))
        invite = frame_stats(Image.open(RES_DIR / f"{prefix}_invite.webp"))
        out[key] = {"tour": d, "still_ratio": still["ratio"],
                    "invite_ratio": invite["ratio"]}
        print(f"{key:14s} n={d['n']:3d} median={d['median']:.4f} "
              f"IQR={d['iqr']:.4f} [{d['q1']:.4f},{d['q3']:.4f}] "
              f"range=[{d['min']:.4f},{d['max']:.4f}] "
              f"adjΔ mean={d['adj_delta_mean']:.4f} max={d['adj_delta_max']:.4f} "
              f"| still={still['ratio']:.4f} invite={invite['ratio']:.4f}")
    if "--json" in sys.argv:
        print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()
