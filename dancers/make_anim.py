#!/usr/bin/env python3
"""Build the phone picker's animated dancer cards (v1.0.11).

The phone ordering page's "选伴舞" cards used to show the six
dancers' static standing portraits; the user asked for the cards
to move — "选人时就能看到每个小人跳舞的样子". For each dancer we
sample frames from the same forty dance-pose WebPs the TV player
steps through (app/src/main/res/drawable-nodpi), downscale them
and stitch them into an infinitely-looping animated WebP.

Output: app/src/main/assets/dancer_anim/<key>.webp, one per
dancer key (same keys as state/DancerKeys and the /dancer-img
route). The HTTP server serves them at /dancer-anim/<key>; the
page falls back to the static /dancer-img/<key> on error.

Parameters (chosen against the size budget, see BUILD_REPORT
v1.0.11): 10 frames per card, 280 ms per frame, height 420 px
(aspect + alpha preserved), lossy quality 80.

v1.0.22: the library grew to 60 poses per dancer and the dance
order was re-sequenced (see DancerPoseOrder / resequence.py);
the cards now sample the NEW dance order evenly — tour positions
0, 6, 12, …, 54 mapped through dancers/resequence_orders.json —
so the picker previews the same sequence the TV dances.

v1.0.33: the five human dancers' tours grew to 100 steps with
the generated in-between frames spliced in (see
dancers/inbetween/MANIFEST.md). Their cards now sample the
100-step tour evenly — positions 0, 10, 20, …, 90 mapped through
dancers/inbetween/inbetween_orders.json — so the picker previews
the smoothed sequence, in-betweens included. The robot keeps
his 60-step tour and the v1.0.22 sampling.

Run from the project root:  python3 dancers/make_anim.py
"""

from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
FRAMES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
OUT_DIR = PROJECT / "app/src/main/assets/dancer_anim"

# Dancer key -> drawable prefix of its 60 pose frames.
DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "sumo": "dancer_sumo",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
    "robot": "dancer_robot",
}

# Tour positions sampled from the dance order; build() maps
# them through the order tables to file numbers. The 60-step
# tours (robot) sample every 6th step; the 100-step tours
# (v1.0.33, the five humans) every 10th.
TOUR_POSITIONS = [0, 6, 12, 18, 24, 30, 36, 42, 48, 54]  # 10 of 60
TOUR_POSITIONS_100 = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90]  # 10 of 100
ORDERS_JSON = PROJECT / "dancers/resequence_orders.json"
INBETWEEN_ORDERS_JSON = PROJECT / "dancers/inbetween/inbetween_orders.json"
FRAME_DURATION_MS = 280
TARGET_HEIGHT = 420
QUALITY = 80


def build(key: str, prefix: str, order: list, positions: list) -> Path:
    resized = []
    for pos in positions:
        i = order[pos] + 1  # tour value is 0-based; files are 1-based
        src = FRAMES_DIR / f"{prefix}_{i:02d}.webp"
        im = Image.open(src).convert("RGBA")
        w = max(1, round(im.width * TARGET_HEIGHT / im.height))
        resized.append(im.resize((w, TARGET_HEIGHT), Image.LANCZOS))
    # Animated WebP needs one shared canvas; the pose frames share
    # a floor baseline, so paste each on the widest canvas,
    # centred horizontally and flush with the bottom.
    canvas_w = max(im.width for im in resized)
    frames = []
    for im in resized:
        canvas = Image.new("RGBA", (canvas_w, TARGET_HEIGHT), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - im.width) // 2, TARGET_HEIGHT - im.height), im)
        frames.append(canvas)
    out = OUT_DIR / f"{key}.webp"
    frames[0].save(
        out,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_DURATION_MS,
        loop=0,  # loop forever
        quality=QUALITY,
        method=4,
    )
    return out


def main() -> None:
    import json

    orders = {k: v["order"] for k, v in
              json.loads(ORDERS_JSON.read_text()).items()}
    if INBETWEEN_ORDERS_JSON.exists():
        for k, v in json.loads(INBETWEEN_ORDERS_JSON.read_text()).items():
            orders[k] = v["order"]  # v1.0.33: 100-step spliced tours
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for key, prefix in DANCERS.items():
        order = orders[key]
        positions = TOUR_POSITIONS_100 if len(order) == 100 else TOUR_POSITIONS
        out = build(key, prefix, order, positions)
        size = out.stat().st_size
        total += size
        print(f"{key:14s} {size:>8,} bytes  -> {out.relative_to(PROJECT)}")
    print(f"{'total':14s} {total:>8,} bytes")


if __name__ == "__main__":
    main()
