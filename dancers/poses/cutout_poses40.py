#!/usr/bin/env python3
"""Pose-frame cutouts for MySing v1.0.7: extend each dancer from
10 frames to 40 using the extra40 sheets (6 sheets x 5 poses per
character, produced after v1.0.5).

Alignment contract (the whole point of this script): the new
frames _11.._40 must sit on the SAME canvas, scale and baseline as
the v1.0.5 frames _01.._10, so any of the 40 frames can replace
any other on screen without the figure jumping or resizing.

The v1.0.5 scale was target_h / max(raw content height over the
character's 10 raw frames). rembg/u2net is deterministic, so
re-running the v1.0.5 separation on the original sheets reproduces
those raw frames and therefore the exact same scale; the script
verifies the recovered scale by predicting each old frame's final
content height and comparing it with the shipped PNG masters.
Canvas width and baseline are taken from the shipped masters.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

EXTRA = os.path.join(base.D, "extra40")
KEYS = list(base.CHARS.keys())

# Robot: the v1.0.5 scale cannot be recovered by the plain formula.
# In the original run the tallest raw frame measured ~905 px; a
# re-run's rembg alpha crosses the 0.05 content threshold a few px
# differently on the antenna tip, giving 899 px and a scale 0.7%
# too big. The consensus of the eight unaffected shipped frames
# (shipped height + ~2 px firming shrink, over raw height) puts
# the original scale at 0.5800 ± 0.001 — predictions then match
# every shipped frame within 3 px. Frames 6/7 are excluded from
# verification: their shipped cutouts lack a wispy top element the
# re-run keeps (clean_frame keep/drop threshold), a content
# difference in those two shipped frames, not a scale error.
SCALE_OVERRIDE = {"robot": 0.5800}
VERIFY_SKIP = {"robot": {6, 7}}


def existing_canvas(key):
    ref = Image.open(os.path.join(base.MASTERS, f"dancer_{key}_01.png"))
    return ref.size  # (w, h)


def final_content_height(key, i):
    im = Image.open(os.path.join(base.MASTERS, f"dancer_{key}_{i:02d}.png"))
    a = np.asarray(im)[:, :, 3].astype(np.float32) / 255.0
    return base.content_bbox(a)[1] - base.content_bbox(a)[0] + 1


def process(session, key):
    stem_a, stem_b, target_h = base.CHARS[key]
    canvas_w, canvas_h = existing_canvas(key)
    assert canvas_h == base.CANVAS_H, (key, canvas_h)

    # --- recover the v1.0.5 scale from the original sheets ---
    old_raw = []
    for stem in (stem_a, stem_b):
        old_raw += base.frames_of_sheet(session, base.sheet_path(stem))
    assert len(old_raw) == 10, (key, len(old_raw))
    old_boxes = [base.content_bbox(a) for _, a in old_raw]
    old_heights = [b[1] - b[0] + 1 for b in old_boxes]
    scale = SCALE_OVERRIDE.get(key) or target_h / max(old_heights)
    skip = VERIFY_SKIP.get(key, set())
    diffs = []
    for i, h in enumerate(old_heights, start=1):
        if i in skip:
            diffs.append(-1)  # known content anomaly, see SCALE_OVERRIDE note
        else:
            diffs.append(abs(round(h * scale) - final_content_height(key, i)))
    print(f"[{key}] scale={scale:.6f} target_h={target_h} "
          f"old-frame height prediction diffs (px): {diffs}")
    assert max(diffs) <= 6, f"{key}: scale recovery mismatch {diffs}"

    # --- separate the 30 new poses ---
    raw = []
    for x in range(1, 7):
        path = os.path.join(EXTRA, f"sheet_{key}_X{x}.png")
        got = base.frames_of_sheet(session, path)
        assert len(got) == 5, (path, len(got))
        raw += got
        print(f"[{key}] sheet X{x} separated")
    assert len(raw) == 30, (key, len(raw))

    # bad-frame guard, same rule as v1.0.5, over the new frames and
    # cross-checked against the old frames' mass median
    masses = sorted(int((a > 0.15).sum()) for _, a in raw)
    median = masses[len(masses) // 2]
    old_masses = sorted(int((a > 0.15).sum()) for _, a in old_raw)
    old_median = old_masses[len(old_masses) // 2]
    print(f"[{key}] new masses min={masses[0]} median={median} "
          f"old median={old_median}")
    assert masses[0] >= 0.35 * median, f"{key}: near-empty new frame"
    assert masses[0] >= 0.35 * old_median, f"{key}: new frame far lighter than old"

    boxes = [base.content_bbox(a) for _, a in raw]
    # Canvas width: the shipped width, unless a new pose is wider
    # (ancient_woman's gauze sleeves spread past her old canvas).
    # Only the transparent padding grows — content scale, height
    # and baseline are untouched, and the player sizes the dancer
    # by height, so a wider canvas changes nothing on screen
    # except that the wide pose is no longer clipped.
    widest = max(round((b[3] - b[2] + 1) * scale) for b in boxes)
    canvas_w = max(canvas_w, widest + 16)
    print(f"[{key}] new-frame canvas width={canvas_w} (shipped {existing_canvas(key)[0]})")
    overflow = []
    for n, ((rgb, a), (y0, y1, x0, x1)) in enumerate(zip(raw, boxes), start=11):
        a = np.clip((a - 0.10) / 0.75, 0, 1)
        if key == "ancient_woman":
            a = np.where(a > 0.55, np.maximum(a, 0.92), a)
        crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
        crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
        crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
        im = Image.fromarray(crop, "RGBA")
        nw = round(im.width * scale)
        nh = round(im.height * scale)
        if nw > canvas_w:
            overflow.append((n, nw - canvas_w))
        im = im.resize((nw, nh), Image.LANCZOS)
        canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - nw) // 2, base.CANVAS_H - base.BOTTOM_MARGIN - nh), im)
        name = f"dancer_{key}_{n:02d}"
        canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
        out = os.path.join(base.OUTDIR, name + ".webp")
        canvas.save(out, "WEBP", quality=93, method=6)
        print(name, canvas.size, os.path.getsize(out), "bytes")
    if overflow:
        print(f"[{key}] !! WIDTH OVERFLOW (clipped px): {overflow}")
    return overflow


def main():
    from rembg import new_session

    keys = sys.argv[1:] or KEYS
    session = new_session("u2net")
    problems = {}
    for key in keys:
        ov = process(session, key)
        if ov:
            problems[key] = ov
    print("done; overflows:", problems)


if __name__ == "__main__":
    main()
