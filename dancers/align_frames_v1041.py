#!/usr/bin/env python3
"""v1.0.41 frame anchor alignment: kill the left-right "flash".

The user (2026-11-16): while dancing, the partner keeps flashing
sideways — "不是动作连贯问题，看得眼睛痛". Offline measurement
along the real DancerPoseOrder tours (dancers/measure_centroid.py)
showed the frame bbox centres are steady (adjacent-step delta
means 0.1-0.9% of canvas width) but the BODY MASS centroid jumps
every step: torso-band (bbox rows 25-60%) centroid delta means —
modern 4.92%, ancient 3.10%, sumo 2.44%, ancient_girl 2.16%,
baby 1.96%, robot 3.17%. One frame per beat, so the eye sees the
whole figure teleport left/right on every beat.

RENDER GEOMETRY (why the anchor is centre-relative): the stage
draws each frame at a fixed layout height with the width following
the frame's own aspect, inside a BottomCenter-aligned zone box
(MainActivity's dancer zone). A source point x in a W-wide canvas
therefore lands on screen at zoneCentre + (x - W/2) * scale, and
every canvas is 600 px tall so the scale is the same for all
frames. The screen-true anchor of a frame is thus its pelvis
centroid measured FROM THE CANVAS CENTRE, in pixels. (The sixty
originals share one canvas per dancer, where centre-relative and
absolute alignment coincide; the generated in-betweens each kept
their own canvas width, where only the centre-relative form is
honest.)

Fix: translate every dance frame horizontally so its PELVIS-band
centroid (bbox rows 55-75% — the body's real pivot) sits closer
to the dancer's own median centre-relative pelvis across the
tour:

  shift = round((median_anchor - frame_anchor) * 0.65)

Only 65% of each deviation is compensated — the remaining 35%
keeps the natural lateral life of the dance instead of nailing
the figure to a rail. Shifts are clamped to each frame's
transparent margins: the body is never cropped and the canvas
size never changes; every clamped frame is recorded in
dancers/alignment_v1041_log.json and the BUILD_REPORT chapter.

The baby's falls are a RUNTIME transform (DanceMotion.BabyFall
rotates/sinks the view); every baby frame is an upright pose
(verified: no wide/short bbox in his set), so his frames align
like everyone else's — no exemptions. The robot gets position
alignment only; his stepped hard-cut motion is untouched (that
lives in DanceMotion, not here).

Applied to the shipped drawable WebPs, and the identical integer
shift is mirrored into the source masters — PNGs in
dancers/poses/frames_png (frames 1-60) and the in-between
sources in dancers/inbetween/<key>/ (frames 61-100, mapping
rebuilt with integrate_v1033.py's own assignment logic and
cross-checked against inbetween_orders.json) — so a future
re-integration cannot silently resurrect the flash. A source is
only shifted when its canvas matches the shipped frame's;
mismatches are skipped and recorded.

Re-encode: WebP quality 95, method 4 (the shipped frames are
lossy ~q90-95; frames whose applied shift is 0 are left
byte-identical). Stills and invite frames are NOT dance frames
and are not touched.

Run from the project root:  python3 dancers/align_frames_v1041.py
"""
import json
import re
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
RES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
PNG_DIR = PROJECT / "dancers/poses/frames_png"
INBETWEEN_DIR = PROJECT / "dancers/inbetween"
LOG_PATH = PROJECT / "dancers/alignment_v1041_log.json"

DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "sumo": "dancer_sumo",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
    "robot": "dancer_robot",
}
COMPENSATION = 0.65
ALPHA_MIN = 8
PELVIS_F0, PELVIS_F1 = 0.55, 0.75


def frame_number(path: Path) -> int:
    return int(re.search(r"_(\d+)\.webp$", path.name).group(1))


def pelvis_and_bbox(im: Image.Image):
    a = np.asarray(im.convert("RGBA"))[:, :, 3].astype(np.float64)
    h, w = a.shape
    mask = a >= ALPHA_MIN
    rows = np.where(mask.any(axis=1))[0]
    cols = np.where(mask.any(axis=0))[0]
    top, bottom = int(rows[0]), int(rows[-1])
    left, right = int(cols[0]), int(cols[-1])
    bh = bottom - top + 1
    r0 = top + int(PELVIS_F0 * bh)
    r1 = top + int(PELVIS_F1 * bh)
    band = np.where(a[r0:r1, :] >= ALPHA_MIN, a[r0:r1, :], 0.0)
    xs = np.arange(w, dtype=np.float64)
    cx = float((band.sum(axis=0) * xs).sum() / band.sum())
    return cx, (left, top, right, bottom), (w, h)


def shift_image(im: Image.Image, shift: int) -> Image.Image:
    arr = np.asarray(im.convert("RGBA")).copy()
    out = np.zeros_like(arr)
    if shift > 0:
        out[:, shift:, :] = arr[:, :-shift, :]
    elif shift < 0:
        out[:, :shift, :] = arr[:, -shift:, :]
    else:
        out = arr
    return Image.fromarray(out, "RGBA")


def clamp_shift(wanted: int, left: int, right: int, width: int) -> int:
    if wanted > 0:
        return min(wanted, width - 1 - right)
    if wanted < 0:
        return max(wanted, -left)
    return 0


def inbetween_sources(key: str) -> dict:
    """frame index (60..99) -> source Path, rebuilt with
    integrate_v1033.py's assignment logic."""
    disk = {}
    for f in sorted((INBETWEEN_DIR / key).glob("after_*.webp")):
        m = re.fullmatch(r"after_(\d+)(?:_(t1|t2))?\.webp", f.name)
        idx, kind = int(m.group(1)), m.group(2) or "mid"
        disk.setdefault(idx, {})[kind] = f
    assign = {}
    nxt = 60
    for gap in sorted(disk):
        seq = ["t1", "mid", "t2"] if "t1" in disk[gap] else ["mid"]
        for kind in seq:
            assign[nxt] = disk[gap][kind]
            nxt += 1
    assert nxt == 100, (key, nxt)
    # Cross-check against the durable integration record.
    record = json.loads((INBETWEEN_DIR / "inbetween_orders.json").read_text())
    assert sorted(record[key]["order"]) == list(range(100))
    return assign


def main() -> None:
    log = {"compensation": COMPENSATION,
           "anchor": "pelvis-band centroid x relative to canvas centre, px",
           "dancers": {}}
    for key, prefix in DANCERS.items():
        files = [f for f in RES_DIR.glob(f"{prefix}_*.webp")
                 if re.search(r"_\d+\.webp$", f.name)]
        files.sort(key=frame_number)
        assert files and frame_number(files[0]) == 1, key
        stats = []
        for f in files:
            im = Image.open(f)
            cx, bbox, canvas = pelvis_and_bbox(im)
            stats.append({"file": f, "num": frame_number(f),
                          "anchor": cx - canvas[0] / 2.0,
                          "bbox": bbox, "canvas": canvas})
        median = float(np.median([s["anchor"] for s in stats]))
        src_map = inbetween_sources(key) if key != "robot" else {}
        dlog = {"median_anchor_px": median, "frames": len(stats),
                "shifted": 0, "clamped": [], "source_skips": [],
                "sources_shifted": 0}
        for s in stats:
            idx = s["num"] - 1
            left, top, right, bottom = s["bbox"]
            w, h = s["canvas"]
            wanted = int(round((median - s["anchor"]) * COMPENSATION))
            applied = clamp_shift(wanted, left, right, w)
            if applied != wanted:
                dlog["clamped"].append(
                    {"frame": s["num"], "wanted": wanted, "applied": applied})
            if applied == 0:
                continue
            dlog["shifted"] += 1
            im = Image.open(s["file"])
            shift_image(im, applied).save(s["file"], format="WEBP",
                                          quality=95, method=4)
            # Mirror the identical shift into the source masters.
            sources = []
            if idx < 60:
                sources.append(PNG_DIR / f"{prefix}_{idx + 1:02d}.png")
            elif idx in src_map:
                sources.append(src_map[idx])
            for src in sources:
                if not src.exists():
                    dlog["source_skips"].append({"frame": s["num"], "src": str(src), "why": "missing"})
                    continue
                sim = Image.open(src)
                if sim.size != (w, h):
                    dlog["source_skips"].append({"frame": s["num"], "src": str(src), "why": f"canvas {sim.size} != {(w, h)}"})
                    continue
                _, (sl, st, sr, sb), _ = pelvis_and_bbox(sim)
                if clamp_shift(applied, sl, sr, w) != applied:
                    dlog["source_skips"].append({"frame": s["num"], "src": str(src), "why": "source margins clamp differently"})
                    continue
                out = shift_image(sim, applied)
                if src.suffix == ".png":
                    out.save(src, format="PNG")
                else:
                    out.save(src, format="WEBP", quality=95, method=4)
                dlog["sources_shifted"] += 1
        log["dancers"][key] = dlog
        print(f"{key:14s} frames={len(stats)} median_anchor={median:+.1f}px "
              f"shifted={dlog['shifted']} clamped={len(dlog['clamped'])} "
              f"sources_shifted={dlog['sources_shifted']} source_skips={len(dlog['source_skips'])}")
        for c in dlog["clamped"]:
            print(f"    clamped frame {c['frame']}: wanted {c['wanted']:+d}px, applied {c['applied']:+d}px")
        for sk in dlog["source_skips"]:
            print(f"    source skip frame {sk['frame']}: {sk['why']}")
    LOG_PATH.write_text(json.dumps(log, indent=1))
    print("wrote", LOG_PATH.relative_to(PROJECT))


if __name__ == "__main__":
    main()
