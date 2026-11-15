#!/usr/bin/env python3
"""Pose-frame + invite cutouts for the sumo dancer （姿三四郎）, MySing v1.0.12.

The sumo replaces the modern girl in the lineup. Eight generated
sheets (poses-sumo-a … poses-sumo-h, five figures each) go through
the exact v1.0.5 separation pipeline (rembg alpha → strong-core
seeding → geodesic ownership → clean_frame) imported from
cutout_poses; normalisation follows the same contract as every
other dancer: ONE common scale for all 40 frames, one canvas
600 px tall, feet on the shared baseline (bottom margin 12).

Scale anchor: the tallest frame's content height maps to 500 px —
the content height of the approved standing single cut from the
v5 master (dancer_sumo.png), so dance frames and the applause
single agree in size.

Sheet A's two shiko leg-lifts came out on the same side (the
generator's known miss); frame _02 is mirrored horizontally so
the pair reads as left/right. The character is front-symmetric
(mawashi panel centred), so the flip is invisible as an edit.

The invite frame is a single-figure generation: rembg + clean,
scaled to the same 500 px content height on the pose canvas.
"""
import glob
import os

import numpy as np
from PIL import Image

import cutout_poses as base

D = base.D
SHEETS = [f"poses-sumo-{c}" for c in "abcdefgh"]
TARGET_H = 500
FLIP_FRAMES = {2}  # sheet A pose 2: mirror the same-side shiko lift


def sheet_file(stem):
    hits = sorted(glob.glob(os.path.join(D, f"media-generation-{stem}-*.webp")))
    assert hits, stem
    return hits[0]


def compose(rgb, a, scale, canvas_w):
    a = np.clip((a - 0.10) / 0.75, 0, 1)  # firm up, same as base.main
    y0, y1, x0, x1 = base.content_bbox(a)
    crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
    crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
    crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
    im = Image.fromarray(crop, "RGBA")
    nw, nh = round(im.width * scale), round(im.height * scale)
    im = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
    canvas.paste(im, ((canvas_w - nw) // 2, base.CANVAS_H - base.BOTTOM_MARGIN - nh), im)
    return canvas


def save(canvas, name):
    canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
    out = os.path.join(base.OUTDIR, name + ".webp")
    canvas.save(out, "WEBP", quality=93, method=6)
    print(name, canvas.size, os.path.getsize(out), "bytes")


def main():
    from rembg import new_session, remove

    session = new_session("u2net")
    raw = []
    for stem in SHEETS:
        raw += base.frames_of_sheet(session, sheet_file(stem))
    assert len(raw) == 40, len(raw)

    masses = sorted(int((a > 0.15).sum()) for _, a in raw)
    median = masses[len(masses) // 2]
    assert masses[0] >= 0.35 * median, (
        f"a frame is nearly empty (mass {masses[0]} vs median {median})"
    )
    print(f"mass min {masses[0]} median {median} max {masses[-1]}")

    boxes = [base.content_bbox(a) for _, a in raw]
    heights = [b[1] - b[0] + 1 for b in boxes]
    scale = TARGET_H / max(heights)
    widths = [round((b[3] - b[2] + 1) * scale) for b in boxes]
    canvas_w = max(widths) + 16
    print(f"scale {scale:.4f} canvas_w {canvas_w}")

    for i, ((rgb, a), _) in enumerate(zip(raw, boxes), start=1):
        if i in FLIP_FRAMES:
            rgb = rgb[:, ::-1].copy()
            a = a[:, ::-1].copy()
            print(f"frame {i:02d}: mirrored (same-side shiko fix)")
        save(compose(rgb, a, scale, canvas_w), f"dancer_sumo_{i:02d}")

    # --- invite: single figure, same content height & canvas ---
    inv_path = sheet_file("dancer-sumo-invite")
    img = Image.open(inv_path).convert("RGB")
    alpha = np.asarray(remove(img, session=session, only_mask=True)).astype(np.float32) / 255.0
    rgb = np.asarray(img)
    a = base.clean_frame(rgb, alpha)
    y0, y1, x0, x1 = base.content_bbox(np.clip((a - 0.10) / 0.75, 0, 1))
    inv_scale = TARGET_H / (y1 - y0 + 1)
    save(compose(rgb, a, inv_scale, canvas_w), "dancer_sumo_invite")
    print("done")


if __name__ == "__main__":
    main()
