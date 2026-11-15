#!/usr/bin/env python3
"""Single-figure re-cuts for three v1.0.7 frames the sheet-wide
geodesic separation mangled (QA contact sheets):

  * robot 17        — the vote cut the jumping robot in half
                      vertically (only ~40% of the body mass).
  * modern_woman 24 — carries the previous figure's forearm and
                      leg+boot, fused in through a mid-alpha slab.
  * ancient_woman 14 — carries the previous figure's raised
                      sleeve+hand as a detached floater.

All three figures are fully visible and separated in their
sheets, so a tight crop around the one figure + rembg +
largest-component recovers them cleanly. Placement reuses each
character's recovered scale, canvas width and the shared 600px
canvas / 12px bottom margin, so the re-cut frames sit exactly
like their 39 siblings.
"""
import os
import sys

import numpy as np
from PIL import Image
from rembg import new_session, remove
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

# key, frame number, sheet file, pose index (0-based), scale,
# canvas width, explicit slice (lo, hi) or None for valley split
JOBS = [
    ("robot", 17, "sheet_robot_X2.png", 1, 0.5800, 438, None),
    # The X3 sheet's valley splits fail (1735/1820 land inside
    # one figure — poses 4/5's hair merges). Pose 4's measured
    # extent is x~1330-1800; the fragments in the old frame were
    # pose 3's arm/leg at the slice's left edge.
    ("modern_woman", 24, "sheet_modern_woman_X3.png", 3, 0.573404, 455, (1300, 1830)),
    ("ancient_woman", 14, "sheet_ancient_woman_X1.png", 3, 0.539446, 417, None),
]


def main():
    session = new_session("u2net")
    jobs = [j for j in JOBS if not sys.argv[1:] or j[0] in sys.argv[1:]]
    for key, n, sheet_name, pose_idx, scale, canvas_w, explicit in jobs:
        sheet = Image.open(os.path.join(base.D, "extra40", sheet_name)).convert("RGB")
        # Slice the sheet at the column-mass valleys (same split
        # the main pipeline uses), widened by 40 px each side so
        # nothing of the figure itself is clipped; neighbour
        # slivers inside the slice are dropped by component pick.
        # A job may pin an explicit slice when the valleys fail.
        if explicit is not None:
            lo, hi = explicit
        else:
            full_alpha = np.asarray(remove(sheet, session=session, only_mask=True)).astype(np.float32) / 255.0
            pts = [0] + base.split_points(full_alpha) + [sheet.width]
            lo = max(0, pts[pose_idx] - 40)
            hi = min(sheet.width, pts[pose_idx + 1] + 40)
        crop_img = sheet.crop((lo, 0, hi, sheet.height))
        rgb = np.asarray(crop_img)
        alpha = np.asarray(remove(crop_img, session=session, only_mask=True)).astype(np.float32) / 255.0
        alpha = base.clean_frame(rgb, alpha)
        # Keep the largest solid component (the figure); drop
        # neighbour slivers and shadow chunks.
        lab, cnt = ndimage.label(alpha > 0.5)
        sizes = ndimage.sum(alpha > 0.5, lab, range(1, cnt + 1))
        j = int(np.argmax(sizes))
        keep = lab == j + 1
        # A body part can detach at >0.5 (thin gauze, a raised
        # foot); rescue any component at least 3% of the largest
        # whose bbox overlaps the largest's bbox grown by 60 px.
        ys, xs = np.where(keep)
        gy0, gy1, gx0, gx1 = ys.min() - 60, ys.max() + 60, xs.min() - 60, xs.max() + 60
        for c in range(cnt):
            if c == j or sizes[c] < 0.03 * sizes[j]:
                continue
            ys2, xs2 = np.where(lab == c + 1)
            if ys2.min() <= gy1 and ys2.max() >= gy0 and xs2.min() <= gx1 and xs2.max() >= gx0:
                keep |= lab == c + 1
        alpha = np.where(keep, alpha, 0.0)
        alpha = np.clip((alpha - 0.10) / 0.75, 0, 1)
        y0, y1, x0, x1 = base.content_bbox(alpha)
        fig = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
        fig[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
        fig[:, :, 3] = (alpha[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
        im = Image.fromarray(fig, "RGBA")
        nw = round(im.width * scale)
        nh = round(im.height * scale)
        assert nw <= canvas_w, (key, n, nw, canvas_w)
        im = im.resize((nw, nh), Image.LANCZOS)
        canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - nw) // 2, base.CANVAS_H - base.BOTTOM_MARGIN - nh), im)
        name = f"dancer_{key}_{n:02d}"
        canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
        out = os.path.join(base.OUTDIR, name + ".webp")
        canvas.save(out, "WEBP", quality=93, method=6)
        print(name, canvas.size, os.path.getsize(out), "bytes, slice", lo, hi)
    print("done")


if __name__ == "__main__":
    main()
