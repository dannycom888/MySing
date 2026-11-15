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
v1.0.11): 10 frames sampled every 4th pose (1, 5, 9, …, 37),
280 ms per frame, height 420 px (aspect + alpha preserved),
lossy quality 80. Result: every file well under 600 KB.

Run from the project root:  python3 dancers/make_anim.py
"""

from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
FRAMES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
OUT_DIR = PROJECT / "app/src/main/assets/dancer_anim"

# Dancer key -> drawable prefix of its 40 pose frames.
DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "girl": "dancer_modern_girl",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
    "robot": "dancer_robot",
}

FRAME_INDICES = [1, 5, 9, 13, 17, 21, 25, 29, 33, 37]  # 10 of 40
FRAME_DURATION_MS = 280
TARGET_HEIGHT = 420
QUALITY = 80


def build(key: str, prefix: str) -> Path:
    resized = []
    for i in FRAME_INDICES:
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
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for key, prefix in DANCERS.items():
        out = build(key, prefix)
        size = out.stat().st_size
        total += size
        print(f"{key:14s} {size:>8,} bytes  -> {out.relative_to(PROJECT)}")
    print(f"{'total':14s} {total:>8,} bytes")


if __name__ == "__main__":
    main()
