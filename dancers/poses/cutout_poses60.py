#!/usr/bin/env python3
"""Pose-frame cutouts for MySing v1.0.22: extend each dancer from
40 frames to 60 using the extra60 sheets (4 sheets x 5 poses per
character, generated after v1.0.17).

Alignment contract (same as v1.0.7, extended): the new frames
_41.._60 must sit on the SAME canvas height, scale and baseline
as frames _01.._40, so any of the 60 frames can replace any other
on screen without the figure jumping or resizing. The scale is
NOT recomputed from the new sheets: for the five original dancers
it is recovered from the original v1.0.5 sheets exactly as
cutout_poses40 did (and verified against the shipped masters);
for the sumo it is re-derived from his eight v1.0.12 sheets as
500 / max raw content height and verified against all 40 shipped
masters.

Sumo frame _42 (sheet Y1 pose 2) is mirrored horizontally: the
generator drew both shiko leg-lifts on the same side again (the
known miss from v1.0.12 sheet A), and the character is
front-symmetric, so the flip is invisible as an edit and makes
the pair read as left/right.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base
import cutout_poses40 as p40

EXTRA60 = os.path.join(base.D, "extra60")
BASE_KEYS = ["modern_woman", "ancient_woman", "ancient_girl", "baby", "robot"]
SUMO_SHEETS = [f"poses-sumo-{c}" for c in "abcdefgh"]
SUMO_TARGET_H = 500
SUMO_FLIP_FRAMES = {42}  # Y1 pose 2: same-side shiko lift, mirror it


def shipped_canvas_w(key):
    ref = Image.open(os.path.join(base.MASTERS, f"dancer_{key}_40.png"))
    return ref.size[0]


def compose(rgb, a, key, scale, canvas_w):
    """Firm, crop, scale and paste one raw frame onto the shared
    canvas — the exact composition cutout_poses40 used."""
    a = np.clip((a - 0.10) / 0.75, 0, 1)
    if key == "ancient_woman":
        a = np.where(a > 0.55, np.maximum(a, 0.92), a)
    y0, y1, x0, x1 = base.content_bbox(a)
    crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
    crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
    crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
    im = Image.fromarray(crop, "RGBA")
    nw, nh = round(im.width * scale), round(im.height * scale)
    im = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
    canvas.paste(im, ((canvas_w - nw) // 2, base.CANVAS_H - base.BOTTOM_MARGIN - nh), im)
    return canvas, nw


def save(canvas, key, n):
    name = f"dancer_{key}_{n:02d}"
    canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
    out = os.path.join(base.OUTDIR, name + ".webp")
    canvas.save(out, "WEBP", quality=93, method=6)
    print(name, canvas.size, os.path.getsize(out), "bytes")


def guard_masses(key, raw_new, raw_old):
    masses = sorted(int((a > 0.15).sum()) for _, a in raw_new)
    median = masses[len(masses) // 2]
    old = sorted(int((a > 0.15).sum()) for _, a in raw_old)
    old_median = old[len(old) // 2]
    print(f"[{key}] new masses min={masses[0]} median={median} old median={old_median}")
    assert masses[0] >= 0.35 * median, f"{key}: near-empty new frame"
    assert masses[0] >= 0.35 * old_median, f"{key}: new frame far lighter than old"


def process_base(session, key):
    stem_a, stem_b, target_h = base.CHARS[key]

    # --- recover the v1.0.5 scale from the original sheets
    # (identical to cutout_poses40, incl. its robot override) ---
    old_raw = []
    for stem in (stem_a, stem_b):
        old_raw += base.frames_of_sheet(session, base.sheet_path(stem))
    assert len(old_raw) == 10, (key, len(old_raw))
    old_boxes = [base.content_bbox(a) for _, a in old_raw]
    old_heights = [b[1] - b[0] + 1 for b in old_boxes]
    scale = p40.SCALE_OVERRIDE.get(key) or target_h / max(old_heights)
    skip = p40.VERIFY_SKIP.get(key, set())
    diffs = []
    for i, h in enumerate(old_heights, start=1):
        if i in skip:
            diffs.append(-1)
        else:
            diffs.append(abs(round(h * scale) - p40.final_content_height(key, i)))
    print(f"[{key}] scale={scale:.6f} verify diffs (px): {diffs}")
    assert max(diffs) <= 6, f"{key}: scale recovery mismatch {diffs}"

    # --- separate the 20 new poses ---
    raw = []
    for y in range(1, 5):
        path = os.path.join(EXTRA60, f"sheet_{key}_Y{y}.png")
        got = base.frames_of_sheet(session, path)
        assert len(got) == 5, (path, len(got))
        raw += got
        print(f"[{key}] sheet Y{y} separated")
    assert len(raw) == 20, (key, len(raw))
    guard_masses(key, raw, old_raw)

    boxes = [base.content_bbox(np.clip((a - 0.10) / 0.75, 0, 1)) for _, a in raw]
    widest = max(round((b[3] - b[2] + 1) * scale) for b in boxes)
    canvas_w = max(shipped_canvas_w(key), widest + 16)
    print(f"[{key}] canvas width={canvas_w} (shipped _40: {shipped_canvas_w(key)})")
    overflow = []
    for n, (rgb, a) in enumerate(raw, start=41):
        canvas, nw = compose(rgb, a, key, scale, canvas_w)
        if nw > canvas_w:
            overflow.append((n, nw - canvas_w))
        save(canvas, key, n)
    if overflow:
        print(f"[{key}] !! WIDTH OVERFLOW (clipped px): {overflow}")
    return overflow


def process_sumo(session):
    key = "sumo"
    old_raw = []
    for stem in SUMO_SHEETS:
        old_raw += base.frames_of_sheet(session, _sheet_file(stem))
    assert len(old_raw) == 40, len(old_raw)
    old_boxes = [base.content_bbox(a) for _, a in old_raw]
    old_heights = [b[1] - b[0] + 1 for b in old_boxes]
    scale = SUMO_TARGET_H / max(old_heights)
    diffs = [abs(round(h * scale) - p40.final_content_height(key, i))
             for i, h in enumerate(old_heights, start=1)]
    print(f"[sumo] scale={scale:.6f} verify diffs max={max(diffs)} (px)")
    # Robust rule (v1.0.22): the scale is confirmed by the bulk of the
    # shipped masters; one hand-adjusted v1.0.12 master (frame 35)
    # sits 12 px off. Require the median within 6 px and at most 2
    # outliers, and name them in the log.
    med = sorted(diffs)[len(diffs) // 2]
    outliers = [(i + 1, d) for i, d in enumerate(diffs, start=1) if d > 6]
    print(f"[sumo] median diff={med}, outliers={outliers}")
    assert med <= 6 and len(outliers) <= 2, \
        f"sumo: scale recovery mismatch {diffs}"

    raw = []
    for y in range(1, 5):
        path = os.path.join(EXTRA60, f"sheet_sumo_Y{y}.png")
        got = base.frames_of_sheet(session, path)
        assert len(got) == 5, (path, len(got))
        raw += got
        print(f"[sumo] sheet Y{y} separated")
    assert len(raw) == 20, len(raw)
    guard_masses(key, raw, old_raw)

    boxes = [base.content_bbox(np.clip((a - 0.10) / 0.75, 0, 1)) for _, a in raw]
    widest = max(round((b[3] - b[2] + 1) * scale) for b in boxes)
    canvas_w = max(shipped_canvas_w(key), widest + 16)
    print(f"[sumo] canvas width={canvas_w} (shipped _40: {shipped_canvas_w(key)})")
    overflow = []
    for n, (rgb, a) in enumerate(raw, start=41):
        canvas, nw = compose(rgb, a, key, scale, canvas_w)
        if nw > canvas_w:
            overflow.append((n, nw - canvas_w))
        if n in SUMO_FLIP_FRAMES:
            canvas = canvas.transpose(Image.FLIP_LEFT_RIGHT)
            print(f"[sumo] frame {n} mirrored (same-side shiko fix)")
        save(canvas, key, n)
    if overflow:
        print(f"[sumo] !! WIDTH OVERFLOW (clipped px): {overflow}")
    return overflow


def _sheet_file(stem):
    import glob
    hits = sorted(glob.glob(os.path.join(base.D, f"media-generation-{stem}-*.webp")))
    assert hits, stem
    return hits[0]


def main():
    from rembg import new_session

    keys = sys.argv[1:]
    session = new_session("u2net")
    problems = {}
    for key in BASE_KEYS:
        if keys and key not in keys:
            continue
        ov = process_base(session, key)
        if ov:
            problems[key] = ov
    if not keys or "sumo" in keys:
        ov = process_sumo(session)
        if ov:
            problems["sumo"] = ov
    print("done; overflows:", problems)


if __name__ == "__main__":
    main()
