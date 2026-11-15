#!/usr/bin/env python3
"""Pose-frame cutouts for MySing v1.0.5.

Each pose sheet shows ONE character five times in a row on a dark
navy background. Separation: rembg (u2net) alpha, then split the
sheet at the centres of the widest fully-empty column runs (the
gaps between figures), keep the real components inside each slice.

Normalisation per character: ONE common scale for all 10 frames so
poses keep their natural relative heights (a squat stays short);
the tallest frame's content height is mapped to the character's
content height in the approved v1.0.3 single-frame cutout, and all
frames share one canvas (600 px tall) with feet on a common
baseline — switching frames never moves the figure.
"""
import glob
import os

import numpy as np
from PIL import Image
from scipy import ndimage

D = os.path.expanduser("~/workspace/shield-karaoke/dancers/poses")
OUTDIR = os.path.expanduser("~/workspace/shield-karaoke/app/src/main/res/drawable-nodpi")
MASTERS = os.path.join(D, "frames_png")
os.makedirs(OUTDIR, exist_ok=True)
os.makedirs(MASTERS, exist_ok=True)

CANVAS_H = 600
BOTTOM_MARGIN = 12

# character -> (sheetA glob, sheetB glob, target max content height)
CHARS = {
    # sheet A is the PATCHED copy: figure 4's raised forearm reaches
    # into figure 3's hair, rembg bridges them with opaque alpha, and
    # every geodesic variant assigned the forearm to figure 3 (frame
    # 3 grew a disembodied arm, frame 4 lost it). The forearm+hand
    # are painted out with background navy in the patched sheet;
    # figure 4 keeps her upper arm, which reads as raised behind
    # her hair. Patch script: paint polygon [(1415,205),(1492,198),
    # (1572,298),(1562,348),(1465,288),(1410,278)] + ellipse
    # [1448,270,1480,300] in the sheet's median background colour.
    "modern_woman": ("poses-modern-woman-apatched", "poses-modern-woman-b", 539),
    "ancient_woman": ("poses-ancient-woman-a", "poses-ancient-woman-b2", 506),
    "modern_girl": ("poses-modern-girl-a", "poses-modern-girl-b", 336),
    "ancient_girl": ("poses-ancient-girl-a", "poses-ancient-girl-b", 305),
    "baby": ("poses-baby-a", "poses-baby-b", 259),
    "robot": ("poses-robot-a", "poses-robot-b", 523),
}


def sheet_path(stem):
    hits = sorted(glob.glob(os.path.join(D, f"media-generation-{stem}-*.webp")))
    assert hits, stem
    return hits[0]


def split_points(alpha):
    """Valley splitting: the 4 split columns are the minima of the
    smoothed vertical alpha-mass profile near the quintile
    boundaries. Flowing hair can bridge the empty-column gaps, but
    it carries almost no mass, so the valleys survive."""
    profile = np.clip(alpha - 0.15, 0, None).sum(axis=0)
    kernel = np.ones(21) / 21.0
    smooth = np.convolve(profile, kernel, mode="same")
    W = alpha.shape[1]
    picks = []
    for k in range(1, 5):
        want = int(W * k / 5.0)
        lo, hi = want - W // 10, want + W // 10
        picks.append(lo + int(np.argmin(smooth[lo:hi])))
    return picks


def split_by_erosion(comp, alpha):
    """Split a component that hides two figures into two seed
    pieces. Erosion usually snaps the thin bridge between them —
    but the split only counts when the two pieces are comparable
    in size (two torsos): the first weak point erosion finds may
    be a wrist inside one figure, and accepting that "split"
    leaves the merged pair whole and invents a core out of a hand.
    When no balanced erosion split exists (figures overlap too
    broadly — gauze sleeves interleaving), fall back to cutting
    the component at its own mass valley near the middle; the
    pieces are only geodesic seeds, and the alpha-weighted cost
    carves the real boundary afterwards."""
    best = None
    for k in (4, 7, 10, 14, 18, 24):
        er = ndimage.binary_erosion(comp, iterations=k)
        lab, n = ndimage.label(er)
        if n >= 2:
            sizes = ndimage.sum(er, lab, range(1, n + 1))
            big = sorted(
                ((sizes[i], lab == i + 1) for i in range(n) if sizes[i] >= 400),
                key=lambda t: -t[0],
            )
            if len(big) >= 2 and big[1][0] >= 0.20 * big[0][0]:
                if best is None or big[1][0] > best[1][0]:
                    best = big
    if best is not None:
        return [best[0][1], best[1][1]]
    ys, xs = np.where(comp)
    x0, x1 = int(xs.min()), int(xs.max())
    prof = np.clip(np.where(comp, alpha, 0.0) - 0.15, 0, None).sum(axis=0)
    sm = np.convolve(prof, np.ones(21) / 21.0, mode="same")
    mid = (x0 + x1) // 2
    span = (x1 - x0) // 4
    cut = mid - span + int(np.argmin(sm[mid - span:mid + span]))
    cols = np.indices(comp.shape)[1]
    left = ndimage.binary_erosion(comp & (cols < cut), iterations=2)
    right = ndimage.binary_erosion(comp & (cols >= cut), iterations=2)
    if left.sum() >= 0.10 * comp.sum() and right.sum() >= 0.10 * comp.sum():
        return [left, right]
    return None


def find_cores(strong, alpha):
    """Five seed-core masks, ordered left to right by centroid."""
    lab, n = ndimage.label(strong)
    sizes = ndimage.sum(strong, lab, range(1, n + 1))
    cores = [lab == i + 1 for i in range(n) if sizes[i] >= 5000]
    while len(cores) < 5:
        cores.sort(key=lambda m: -m.sum())
        pieces = split_by_erosion(cores[0], alpha)
        if pieces is None:
            break
        cores = pieces + cores[1:]
    cores.sort(key=lambda m: -m.sum())
    cores = cores[:5]
    cores.sort(key=lambda m: np.where(m)[1].mean())
    return [ndimage.binary_erosion(c, iterations=1) if c.sum() > 2000 else c for c in cores]


def seed_points(core, cap=4000):
    ys, xs = np.where(core)
    step = max(1, len(ys) // cap)
    return [(int(ys[j]), int(xs[j])) for j in range(0, len(ys), step)]


def frames_of_sheet(session, path):
    """Geodesic Voronoi separation (same idea as the v1.0.3 group
    cutouts): valley slices only locate each figure's core; every
    foreground pixel then belongs to the core reachable by the
    shortest path *inside* the foreground, so a hand or sleeve that
    crosses a slice boundary stays with its owner. A final pass
    drops anything not touching the main body (background shadow
    slabs rembg sometimes hallucinates between figures)."""
    from rembg import remove
    from skimage.graph import MCP_Geometric

    img = Image.open(path).convert("RGB")
    alpha = np.asarray(remove(img, session=session, only_mask=True)).astype(np.float32) / 255.0
    rgb = np.asarray(img)
    strong = alpha > 0.20

    # One seed core per figure, from the strong mask's own
    # components — NOT from column slices: a valley split can land
    # inside a body (two figures' hair overlaps in projection, so
    # the mass minimum sat inside figure 4 and her whole left side
    # started life closer to figure 3's slice-seeded core). When
    # two figures merge into one component (hair streams touch),
    # split the largest component by progressive erosion: the thin
    # bridge erodes away long before the thick torsos do.
    cores = find_cores(strong, alpha)
    assert len(cores) == 5, f"{path}: found {len(cores)} cores"
    cores = [seed_points(core) for core in cores]

    # Geodesic domain: faint alpha dilated a little, so a wrist or
    # gauze strand rembg barely caught still connects a limb to its
    # own body. Travelling through faint pixels costs extra, so the
    # boundary between two touching figures prefers the thin, wispy
    # bridge over a thick limb. Crucially the background cost is
    # FINITE (25/px): with a near-infinite background cost, a hair
    # wisp at alpha 0.04 bridging to the neighbour beat a 10 px
    # alpha gap at the limb's own wrist every time, and limbs kept
    # being awarded to the wrong figure. Ownership is read on the
    # raw alpha, and a frame keeps its pixels ANYWHERE on the
    # sheet — a wide arm reaches into the neighbour's slice but
    # stays in its own frame.
    domain = ndimage.binary_dilation(alpha > 0.06, iterations=1)
    cost = np.where(domain, 1.0 + (1.0 - alpha) * 4.0, 25.0)
    dists = [MCP_Geometric(cost).find_costs(starts=c)[0] for c in cores]
    label = np.argmin(np.stack(dists), axis=0)
    owned = alpha > 0.03

    frames = []
    for i in range(5):
        a = np.where(owned & (label == i), alpha, 0.0)
        a = clean_frame(rgb, a)
        frames.append((rgb, a))
    return frames


def clean_frame(rgb, a):
    """Drop everything that is not the main body: erode the frame
    mask (thin bridges snap), keep the main eroded component plus
    any sizeable fragment eroded-core within 40 px of it (tassels,
    a hand on a thin wrist), then give every pixel to its nearest
    kept core. A kept fragment that is dark and faint is a floor
    shadow rembg invented, not a body part — drop it too."""
    m = a > 0.15
    er = ndimage.binary_erosion(m, iterations=3)
    lab, n = ndimage.label(er)
    if n <= 1:
        return a
    sizes = ndimage.sum(er, lab, range(1, n + 1))
    main_id = int(np.argmax(sizes)) + 1
    main = lab == main_id
    dist_to_main = ndimage.distance_transform_edt(~main)
    lum = rgb.astype(np.float32).mean(axis=2)
    keep_ids = [main_id]
    for j in range(n):
        if j + 1 == main_id:
            continue
        comp = lab == j + 1
        if sizes[j] < 150:  # speck
            continue
        if dist_to_main[comp].min() > 40:  # not attached to the body
            continue
        keep_ids.append(j + 1)
    # shadow test needs the fragment's full (uneroded) extent: use
    # nearest-core assignment first, then test each kept fragment
    core_mask = np.isin(lab, keep_ids)
    _, idx = ndimage.distance_transform_edt(lab == 0, return_indices=True)
    nearest = lab[idx[0], idx[1]]
    keep = np.isin(nearest, keep_ids)
    for j in keep_ids:
        if j == main_id:
            continue
        frag = keep & (nearest == j)
        if frag.sum() == 0:
            continue
        if a[frag].mean() < 0.55 and lum[frag].mean() < 60:
            keep &= ~frag  # dark + faint: floor shadow, not a limb
    return np.where(keep, a, 0.0)


def content_bbox(a):
    m = a > 0.05
    ys = np.where(m.any(axis=1))[0]
    xs = np.where(m.any(axis=0))[0]
    return ys.min(), ys.max(), xs.min(), xs.max()


def main():
    from rembg import new_session

    session = new_session("u2net")
    for key, (stem_a, stem_b, target_h) in CHARS.items():
        raw = []
        for stem in (stem_a, stem_b):
            raw += frames_of_sheet(session, sheet_path(stem))
        assert len(raw) == 10, (key, len(raw))
        masses = sorted(int((a > 0.15).sum()) for _, a in raw)
        median = masses[len(masses) // 2]
        assert masses[0] >= 0.35 * median, (
            f"{key}: a frame is nearly empty (mass {masses[0]} vs median {median}) — "
            "core seeding went wrong on one of its sheets"
        )
        boxes = [content_bbox(a) for _, a in raw]
        heights = [b[1] - b[0] + 1 for b in boxes]
        scale = target_h / max(heights)
        # canvas width: widest scaled frame + padding
        widths = [round((b[3] - b[2] + 1) * scale) for b in boxes]
        canvas_w = max(widths) + 16
        for i, ((rgb, a), (y0, y1, x0, x1)) in enumerate(zip(raw, boxes), start=1):
            # firm the alpha up: rembg is timid on some poses (the
            # crouching robot came back at ~0.78 mean) and a
            # semi-transparent dancer looks washed out on stage
            a = np.clip((a - 0.10) / 0.75, 0, 1)
            if key == "ancient_woman":
                # gauze stays translucent, the body core goes opaque
                a = np.where(a > 0.55, np.maximum(a, 0.92), a)
            crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
            crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
            crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
            im = Image.fromarray(crop, "RGBA")
            nw = round(im.width * scale)
            nh = round(im.height * scale)
            im = im.resize((nw, nh), Image.LANCZOS)
            canvas = Image.new("RGBA", (canvas_w, CANVAS_H), (0, 0, 0, 0))
            canvas.paste(im, ((canvas_w - nw) // 2, CANVAS_H - BOTTOM_MARGIN - nh), im)
            name = f"dancer_{key}_{i:02d}"
            canvas.save(os.path.join(MASTERS, name + ".png"), optimize=True)
            out = os.path.join(OUTDIR, name + ".webp")
            canvas.save(out, "WEBP", quality=93, method=6)
            print(name, canvas.size, os.path.getsize(out), "bytes")
    print("done")


if __name__ == "__main__":
    main()
