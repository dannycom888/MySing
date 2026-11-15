#!/usr/bin/env python3
"""Re-sequence the dancers' pose frames into a smooth dance loop
(v1.0.17; frame count raised 40 -> 60 in v1.0.22).

Why: the forty pose frames per dancer were cut from pose sheets in
sheet order (1, 2, 3, ... 40), which is NOT a dance order — frame
N and frame N+1 can be unrelated poses, so stepping one frame per
beat reads as random jumping ("姿势乱跳，前后接不上", user,
2026-11-15). The v1.0.10 cross-fade could not fix that; the order
itself has to change. The frame artwork is untouched — only the
playback order changes.

Method: for each dancer, paste the 40 frames onto one shared canvas
(bottom-centred, exactly how the TV renderer draws them at a fixed
height), downscale to a small thumbnail, and take the pairwise
distance between frames as the RMS difference of the premultiplied
RGBA thumbnails (transparent pixels contribute nothing; a shifted
or reshaped silhouette costs a lot, a near-identical pose costs
almost nothing). Then find a closed tour through all 40 frames
minimising the total adjacent cost (the wrap edge last->first
counts too): multi-start greedy nearest-neighbour (every possible
second frame, plus randomised restarts) followed by 2-opt until
no improving move exists. Frame 0 — the neutral standing frame the
app also uses for the outro "still" phase — is pinned to position
0 of the tour so a song's dancing starts from the standing pose.

Outputs:
  dancers/resequence_orders.json   machine-readable orders + costs
  stdout                           per-dancer before/after mean
                                   adjacent cost + Kotlin literals
                                   for ui/DancerPoseOrder.kt

Run from the project root:  python3 dancers/resequence.py
"""

import json
import random
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
FRAMES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
OUT_JSON = Path(__file__).resolve().parent / "resequence_orders.json"

# Dancer key -> drawable prefix of its pose frames.
DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "sumo": "dancer_sumo",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
    "robot": "dancer_robot",
}

FRAME_COUNT = 60
THUMB_W, THUMB_H = 46, 60  # thumbnail the distance is measured on


def load_features(prefix: str) -> np.ndarray:
    """One feature vector per frame: the premultiplied-RGBA thumbnail
    (alpha in 0..1 multiplied into RGB, alpha itself appended), so
    Euclidean distance between vectors is the alpha-weighted pixel
    distance between the two on-screen figures."""
    imgs = []
    for i in range(1, FRAME_COUNT + 1):
        imgs.append(Image.open(FRAMES_DIR / f"{prefix}_{i:02d}.webp").convert("RGBA"))
    canvas_w = max(im.width for im in imgs)
    canvas_h = max(im.height for im in imgs)
    feats = []
    for im in imgs:
        canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - im.width) // 2, canvas_h - im.height), im)
        thumb = canvas.resize((THUMB_W, THUMB_H), Image.LANCZOS)
        arr = np.asarray(thumb, dtype=np.float64) / 255.0
        a = arr[..., 3:4]
        premul = np.concatenate([arr[..., :3] * a, a], axis=-1)
        feats.append(premul.ravel())
    return np.stack(feats)


def distance_matrix(feats: np.ndarray) -> np.ndarray:
    sq = (feats**2).sum(axis=1)
    d2 = sq[:, None] + sq[None, :] - 2.0 * feats @ feats.T
    np.maximum(d2, 0.0, out=d2)
    # RMS over the thumbnail's values, so the number reads as a
    # per-pixel distance in 0..~1 regardless of thumbnail size.
    return np.sqrt(d2 / feats.shape[1])


def tour_cost(tour: list[int], dist: np.ndarray) -> float:
    n = len(tour)
    return float(sum(dist[tour[i], tour[(i + 1) % n]] for i in range(n)))


def greedy_tour(dist: np.ndarray, second: int, rng: random.Random | None) -> list[int]:
    """Nearest-neighbour tour starting at frame 0 via [second].
    With an rng, pick randomly among the 3 nearest unvisited frames
    (weighted toward the nearest) for restart diversity."""
    n = dist.shape[0]
    tour = [0, second]
    unvisited = set(range(n)) - {0, second}
    while unvisited:
        last = tour[-1]
        cands = sorted(unvisited, key=lambda j: dist[last, j])
        if rng is not None and len(cands) > 1:
            pick = rng.choices(cands[:3], weights=[3, 2, 1][: len(cands[:3])])[0]
        else:
            pick = cands[0]
        tour.append(pick)
        unvisited.discard(pick)
    return tour


def two_opt(tour: list[int], dist: np.ndarray) -> list[int]:
    """Improve the closed tour by segment reversals. tour[0] stays
    pinned: reversal ranges start at index 1. The wrap edge
    (last -> first) is part of the cost, so the loop closes
    smoothly too."""
    n = len(tour)
    improved = True
    while improved:
        improved = False
        for i in range(1, n - 1):
            a, b = tour[i - 1], tour[i]
            for j in range(i + 1, n):
                c = tour[j]
                d = tour[(j + 1) % n]
                delta = dist[a, c] + dist[b, d] - dist[a, b] - dist[c, d]
                if delta < -1e-12:
                    tour[i : j + 1] = reversed(tour[i : j + 1])
                    b = tour[i]
                    improved = True
            # loop continues with updated tour
    return tour


def best_tour(dist: np.ndarray) -> list[int]:
    rng = random.Random(20261115)
    candidates = []
    for second in range(1, dist.shape[0]):
        candidates.append(greedy_tour(dist, second, None))
    for _ in range(300):
        second = rng.randrange(1, dist.shape[0])
        candidates.append(greedy_tour(dist, second, rng))
    best, best_cost = None, float("inf")
    for cand in candidates:
        opt = two_opt(list(cand), dist)
        c = tour_cost(opt, dist)
        if c < best_cost:
            best, best_cost = opt, c
    assert best is not None and best[0] == 0
    return best


def kotlin_literal(order: list[int]) -> str:
    rows = []
    for k in range(0, len(order), 10):
        rows.append("            " + ", ".join(str(v) for v in order[k : k + 10]) + ",")
    return "\n".join(rows)


def main() -> None:
    results = {}
    for key, prefix in DANCERS.items():
        feats = load_features(prefix)
        dist = distance_matrix(feats)
        identity = list(range(FRAME_COUNT))
        before = tour_cost(identity, dist) / FRAME_COUNT
        order = best_tour(dist)
        after = tour_cost(order, dist) / FRAME_COUNT
        assert sorted(order) == identity, f"{key}: not a permutation"
        results[key] = {
            "order": order,
            "mean_adjacent_before": round(before, 4),
            "mean_adjacent_after": round(after, 4),
        }
        print(f"== {key} ({prefix})")
        print(f"   mean adjacent cost: before {before:.4f} -> after {after:.4f} "
              f"({(1 - after / before) * 100:.1f}% lower)")
        print(f"   order (0-based frame indices, file = index+1):")
        print("   " + ", ".join(str(v) for v in order))
        print(f"   kotlin:\n{kotlin_literal(order)}")
    OUT_JSON.write_text(json.dumps(results, indent=2, ensure_ascii=False) + "\n")
    print(f"\nwrote {OUT_JSON.relative_to(PROJECT)}")


if __name__ == "__main__":
    main()
