#!/usr/bin/env python3
"""Invite frames for MySing v1.0.7: dancer_<key>_invite.webp.

Source: dancers/media-generation-...-invite-sheet-....webp —
the six dancers in ONE row (modern woman, ancient woman,
modern girl, ancient girl, baby, robot), each with one arm
extended in a "please sing" invite. The figures stand close
and their alpha regions touch, so the whole sheet is separated
by geodesic assignment from six hand-placed seed discs (one
inside each figure's torso), the same machinery as the pose
sheets.

The invite sheet was generated at a different figure scale
than the pose sheets, so the shared pose scale does NOT apply;
each figure's content height is matched to its neutral
standing single (dancer_<key>.png, the applause figure) — the
on-screen size then matches the dancer the audience already
knows. Canvas height 600 and the 12 px bottom margin are
shared with all other frames, so the feet baseline is
identical.
"""
import os
import sys

import numpy as np
from PIL import Image
from rembg import new_session, remove
from scipy import ndimage
from skimage.graph import MCP_Geometric

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cutout_poses as base

SHEET = os.path.join(
    os.path.dirname(base.D), "media-generation-mysing-dancers-"
    "invite-sheet-0-925ec319-9570-40b3-bab2-76ebef62681c.webp")
KEYS = ["modern_woman", "ancient_woman", "modern_girl",
        "ancient_girl", "baby", "robot"]
# Seed points (x, y) inside each figure's torso, read off the
# sheet: one row, adults tall, girls and baby shorter.
SEEDS = [(290, 490), (655, 510), (905, 720),
         (1133, 800), (1344, 830), (1613, 500)]
RES = os.path.expanduser(
    "~/workspace/shield-karaoke/app/src/main/res/drawable-nodpi")


def content_h(im):
    bb = im.getchannel("A").getbbox()
    return bb[3] - bb[1]


def main():
    sheet = Image.open(SHEET).convert("RGB")
    assert sheet.size == (1920, 1280), sheet.size
    rgb = np.asarray(sheet)
    session = new_session("u2net")
    alpha = np.asarray(remove(sheet, session=session, only_mask=True)).astype(np.float32) / 255.0

    h, w = alpha.shape
    yy, xx = np.mgrid[0:h, 0:w]
    cores = []
    for sx, sy in SEEDS:
        disc = (xx - sx) ** 2 + (yy - sy) ** 2 <= 70 ** 2
        core = disc & (alpha > 0.20)
        assert core.sum() > 500, (sx, sy, int(core.sum()))
        cores.append(base.seed_points(core))
    # The figures stand close and share a floor shadow, so the
    # faint-alpha penalty is much stronger than the pose sheets'
    # (x12, domain alpha>0.08, no dilation): paths must stay
    # inside solid figure, not shortcut across the shadow.
    domain = alpha > 0.08
    cost = np.where(domain, 1.0 + (1.0 - alpha) * 12.0, 60.0)
    dists = [MCP_Geometric(cost).find_costs(starts=c)[0] for c in cores]
    label = np.argmin(np.stack(dists), axis=0)
    # In the lower half the left four figures are cleanly
    # separated (measured alpha runs at y 960-1119 and y
    # 1120-1279 show real gaps), while the geodesic vote leaks
    # across the shared floor shadow there (the ancient woman's
    # dress hem went to the modern girl). Override the lower
    # zones with column assignment at the measured gap
    # midpoints — but only left of x=1286: the baby's wide
    # stance reaches x~1560, interleaved with the robot's feet,
    # and the geodesic vote separates those two correctly.
    # Borders read off the sheet itself: the ancient woman's
    # sleeve ends at x~730 and the modern girl's outstretched
    # hand starts at x~728 (y 800-850), with a clean 730-800
    # gap below; the baby's hand/leg and the robot's shin meet
    # at x~1552. Above y=780 the geodesic vote is reliable
    # (real background gaps), so zones only cover y>=780.
    for y in range(780, 880):
        label[y, 0:362] = 0
        label[y, 362:742] = 1
        label[y, 742:1016] = 2
        label[y, 1016:1286] = 3
    for y in range(880, 1100):
        label[y, 0:362] = 0
        label[y, 362:765] = 1
        label[y, 765:1016] = 2
        label[y, 1016:1286] = 3
    for j, (lo, hi) in enumerate(((0, 414), (414, 754), (754, 1017), (1017, 1286))):
        label[1100:, lo:hi] = j
    label[800:, 1286:1552] = 4
    label[800:, 1552:] = 5
    owned = alpha > 0.03

    for i, key in enumerate(KEYS):
        a = np.where(owned & (label == i), alpha, 0.0)
        a = base.clean_frame(rgb, a)
        a = np.clip((a - 0.10) / 0.75, 0, 1)
        # Drop stray solid fragments a neighbour shed across a
        # boundary (robot knee sliver on the baby, a pigtail
        # tip): at solid alpha keep the largest component plus
        # anything at least 5% of it; faint shadow bridges do
        # not survive the 0.15 threshold.
        lab, n = ndimage.label(a > 0.15)
        if n > 1:
            sizes = ndimage.sum(a > 0.15, lab, range(1, n + 1))
            keep = (sizes >= 0.05 * sizes.max())
            if key == "ancient_woman":
                # Small dark fragments of the modern girl's hair
                # sit at x>900 beside her sleeve; her own solid
                # body never reaches that far right detached.
                cents = ndimage.center_of_mass(a > 0.15, lab, range(1, n + 1))
                for j2 in range(n):
                    if cents[j2][1] > 900 and sizes[j2] < 0.08 * sizes.max():
                        keep[j2] = False
            a = np.where(keep[lab - 1], a, 0.0)
        y0, y1, x0, x1 = base.content_bbox(a)
        crop = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
        crop[:, :, :3] = rgb[y0:y1 + 1, x0:x1 + 1]
        crop[:, :, 3] = (a[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
        fig = Image.fromarray(crop, "RGBA")
        single = Image.open(os.path.join(RES, f"dancer_{key}.png"))
        scale = content_h(single) / fig.height
        nw = round(fig.width * scale)
        nh = round(fig.height * scale)
        fig = fig.resize((nw, nh), Image.LANCZOS)
        canvas_w = nw + 16
        canvas = Image.new("RGBA", (canvas_w, base.CANVAS_H), (0, 0, 0, 0))
        canvas.paste(fig, ((canvas_w - nw) // 2,
                           base.CANVAS_H - base.BOTTOM_MARGIN - nh), fig)
        name = f"dancer_{key}_invite"
        canvas.save(os.path.join(base.MASTERS, name + ".png"), optimize=True)
        out = os.path.join(base.OUTDIR, name + ".webp")
        canvas.save(out, "WEBP", quality=93, method=6)
        print(name, canvas.size, f"scale {scale:.3f}", os.path.getsize(out), "bytes")
    print("done")


if __name__ == "__main__":
    main()
