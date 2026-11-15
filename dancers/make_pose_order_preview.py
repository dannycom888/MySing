#!/usr/bin/env python3
"""Preview of the v1.0.17 pose re-sequencing: Chun-Li's 40 frames in
the NEW order from dancers/resequence.py, stitched into one looping
animated WebP (all 40 frames, 280 ms each — the same cadence family
as dancers/make_anim.py) so the user can eyeball whether the dance
now flows instead of jumping between unrelated poses.

Output: pose-order-preview.webp in the project root.

Run dancers/resequence.py first (it writes resequence_orders.json).
Run from the project root:  python3 dancers/make_pose_order_preview.py
"""

import json
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
FRAMES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
ORDERS_JSON = Path(__file__).resolve().parent / "resequence_orders.json"
OUT = PROJECT / "pose-order-preview.webp"

PREFIX = "dancer_modern_woman"  # 春丽, the default dancer
FRAME_DURATION_MS = 280
TARGET_HEIGHT = 420
QUALITY = 80


def main() -> None:
    order = json.loads(ORDERS_JSON.read_text())["modern"]["order"]
    resized = []
    for idx in order:  # 0-based frame index -> file is index+1
        src = FRAMES_DIR / f"{PREFIX}_{idx + 1:02d}.webp"
        im = Image.open(src).convert("RGBA")
        w = max(1, round(im.width * TARGET_HEIGHT / im.height))
        resized.append(im.resize((w, TARGET_HEIGHT), Image.LANCZOS))
    canvas_w = max(im.width for im in resized)
    frames = []
    for im in resized:
        canvas = Image.new("RGBA", (canvas_w, TARGET_HEIGHT), (0, 0, 0, 0))
        canvas.paste(im, ((canvas_w - im.width) // 2, TARGET_HEIGHT - im.height), im)
        frames.append(canvas)
    frames[0].save(
        OUT,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_DURATION_MS,
        loop=0,
        quality=QUALITY,
        method=4,
    )
    print(f"{OUT.name}: {OUT.stat().st_size:,} bytes, {len(frames)} frames in new order")


if __name__ == "__main__":
    main()
