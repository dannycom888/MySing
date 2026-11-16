#!/usr/bin/env python3
"""v1.0.43 figure-size normalisation: kill the "有时会变大变小".

After v1.0.42 the user: the dancer still sometimes grows/shrinks
between steps, "但可以接受" — then, told the cause, "那就做吧".
The stage renders every frame at a fixed layout height, so the
on-screen figure size IS each frame's figure-height ratio (alpha
bbox height / canvas height; measured by measure_size_v1043.py —
medians 0.41-0.82 across the six dancers, but IQRs up to 0.088
and adjacent-step deltas up to 0.21 for the robot).

Fix: scale each frame's CONTENT toward its dancer's median ratio
with the v1.0.41 philosophy — compensate 65%, never pin it flat:

    s = (median / ratio) ** 0.65        (log-space deviation -> 35%)

Guard rails:
  * Only frames whose ratio sits inside median +/-25% are
    touched. Outside the band is a TRUE pose height (deep squat,
    bow, crouch) — left alone and recorded, per the brief.
  * The scale pivots on (pelvis anchor x after the v1.0.41
    alignment, foot baseline y = bbox bottom edge), so the .41
    pelvis alignment and the feet's floor position are preserved
    by construction.
  * Growth is clamped to the frame's transparent margins (bbox
    expanded 1 px for the anti-aliased fringe): the body is never
    cropped and the canvas size never changes; every clamped
    frame is recorded in dancers/normalize_v1043_log.json.
  * The beat-accent scale pulse and the intro step-forward growth
    are runtime design (DanceMotion / DancerActivity) — untouched;
    this script only normalises frame content.

Stills and invite images are normalised to the same dance-chain
median under the same band rule, so the invite -> dancing switch
cannot jump in size (their ratios sit 4-14% above the medians).

Mirroring (so a future re-integration cannot resurrect the size
jitter): the identical scale is applied to the source masters —
PNGs in dancers/poses/frames_png (dance frames 1-60 and the
invite masters) directly, and the in-between sources in
dancers/inbetween/<key>/ via CONTENT MATCHING against the shipped
frames (the v1.0.41 script's rebuilt index mapping is known to be
wrong for at least ancient 61/74 — see BUILD_REPORT v1.0.42 — so
each shipped in-between is matched to its true source by
premultiplied-thumbnail RMS, with the distances recorded; unclear
matches are skipped and recorded, never guessed). The stills have
no separate master: the res PNG is lossless and is edited in
place.

Re-encode: WebP quality 95, method 4 (the pipeline standard
since v1.0.41); frames whose applied scale would not change the
bbox height by a pixel are left byte-identical.

Run from the project root:
    python3 dancers/normalize_size_v1043.py           # dry run
    python3 dancers/normalize_size_v1043.py --apply   # write
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from measure_size_v1043 import (DANCERS, RES_DIR, frame_stats,
                                parse_orders)

PROJECT = Path(__file__).resolve().parent.parent
PNG_DIR = PROJECT / "dancers/poses/frames_png"
INBETWEEN_DIR = PROJECT / "dancers/inbetween"
LOG_PATH = PROJECT / "dancers/normalize_v1043_log.json"

COMPENSATION = 0.65
BAND_FRAC = 0.25
APPLY = "--apply" in sys.argv


# ---------------------------------------------------------------- scale

def scale_about(im: Image.Image, s: float, ax: float, ay: float) -> Image.Image:
    """Scale the whole canvas by s around the point (ax, ay),
    premultiplied-alpha correct, canvas size unchanged."""
    arr = np.asarray(im.convert("RGBA")).astype(np.float32)
    h, w = arr.shape[:2]
    a = arr[:, :, 3:4] / 255.0
    premult = np.concatenate([arr[:, :, :3] * a, arr[:, :, 3:4]], axis=2)
    nw, nh = max(1, round(w * s)), max(1, round(h * s))
    chans = []
    for c in range(4):
        ch = Image.fromarray(premult[:, :, c], mode="F")
        chans.append(np.asarray(ch.resize((nw, nh), Image.LANCZOS)))
    scaled = np.stack(chans, axis=2)
    out = np.zeros((h, w, 4), dtype=np.float32)
    ox, oy = round(ax - ax * s), round(ay - ay * s)
    x0, y0 = max(0, ox), max(0, oy)
    x1, y1 = min(w, ox + nw), min(h, oy + nh)
    if x1 > x0 and y1 > y0:
        out[y0:y1, x0:x1] = scaled[y0 - oy:y1 - oy, x0 - ox:x1 - ox]
    alpha = out[:, :, 3:4]
    rgb = np.where(alpha > 0.5,
                   out[:, :, :3] * 255.0 / np.maximum(alpha, 1e-6), 0.0)
    rgba = np.concatenate([rgb, alpha], axis=2)
    return Image.fromarray(np.clip(np.round(rgba), 0, 255).astype(np.uint8),
                           "RGBA")


def clamp_scale(s: float, st: dict) -> float:
    """Largest scale <= s whose scaled bbox (expanded 1 px for the
    anti-aliased fringe) stays inside the canvas, pivoting on
    (pelvis_x, bbox bottom edge). Only binds for growth (s > 1)."""
    if s <= 1.0:
        return s
    w, _ = st["canvas"]
    left, top, right, bottom = st["bbox"]
    left, top, right, bottom = left - 1, top - 1, right + 1, bottom + 1
    ax, ay = st["pelvis_x"], float(bottom)  # bottom edge, continuous
    smax = s
    if ax > left:
        smax = min(smax, ax / (ax - left))
    if right > ax:
        smax = min(smax, (w - ax) / (right - ax))
    if ay > top:
        smax = min(smax, ay / (ay - top))
    return smax


def plan(st: dict, median: float) -> dict:
    """Decide one image's fate against the dancer median."""
    r = st["ratio"]
    if r < median * (1 - BAND_FRAC) or r > median * (1 + BAND_FRAC):
        return {"action": "out_of_band", "ratio": r}
    s_wanted = (median / r) ** COMPENSATION
    s_applied = clamp_scale(s_wanted, st)
    bh = st["bbox"][3] - st["bbox"][1] + 1
    if round(bh * s_applied) == bh:
        return {"action": "noop", "ratio": r, "s_wanted": s_wanted}
    return {"action": "scale", "ratio": r, "s_wanted": s_wanted,
            "s_applied": s_applied, "clamped": s_applied < s_wanted - 1e-9}


def apply_to_file(path: Path, s: float, st: dict) -> None:
    im = Image.open(path)
    out = scale_about(im, s, st["pelvis_x"], st["bbox"][3] + 1.0)
    if path.suffix == ".png":
        out.save(path, format="PNG")
    else:
        out.save(path, format="WEBP", quality=95, method=4)


# ------------------------------------------------------- source matching

def _rep(im: Image.Image, size: int = 96) -> np.ndarray:
    arr = np.asarray(im.convert("RGBA")).astype(np.float32)
    a = arr[:, :, 3:4] / 255.0
    premult = np.concatenate([arr[:, :, :3] * a, arr[:, :, 3:4]], axis=2)
    chans = []
    for c in range(4):
        ch = Image.fromarray(premult[:, :, c], mode="F")
        chans.append(np.asarray(ch.resize((size, size), Image.LANCZOS)))
    return np.stack(chans, axis=2).ravel() / 255.0


def match_inbetween(key: str, shipped: dict) -> dict:
    """shipped: {index: Path} for indices 60..99. Returns
    {index: (source Path, best_dist, second_dist)} for accepted
    matches only."""
    srcs = {}
    for f in sorted((INBETWEEN_DIR / key).glob("after_*.webp")):
        srcs[f] = _rep(Image.open(f))
    out = {}
    for idx, f in shipped.items():
        rep = _rep(Image.open(f))
        dists = sorted(
            ((float(np.sqrt(np.mean((rep - srep) ** 2))), spath)
             for spath, srep in srcs.items()),
            key=lambda t: t[0])
        best, second = dists[0], dists[1]
        if best[0] < 0.03 and best[0] < 0.6 * second[0]:
            out[idx] = (best[1], best[0], second[0])
    return out


# ---------------------------------------------------------------- main

def main() -> None:
    orders = parse_orders()
    log = {"compensation": COMPENSATION, "band_frac": BAND_FRAC,
           "anchor": "pelvis x (post-v1.0.41) + foot baseline (bbox "
                     "bottom edge); scale s=(median/ratio)^0.65",
           "dancers": {}}
    for key, prefix in DANCERS.items():
        order = orders[key]
        files = {idx: RES_DIR / f"{prefix}_{idx + 1:02d}.webp"
                 for idx in order}
        stats = {idx: frame_stats(Image.open(f))
                 for idx, f in files.items()}
        median = float(np.median([s["ratio"] for s in stats.values()]))
        dlog = {"median_ratio": median, "frames": len(stats),
                "scaled": 0, "noop": [], "out_of_band": [],
                "clamped": [], "still": None, "invite": None,
                "sources_scaled": 0, "source_skips": [],
                "match_rejects": []}
        # Match in-between sources BEFORE touching anything.
        inb_shipped = {idx: files[idx] for idx in order if idx >= 60}
        matches = match_inbetween(key, inb_shipped) if inb_shipped else {}
        for idx in inb_shipped:
            if idx not in matches:
                dlog["match_rejects"].append(idx + 1)
        plans = {}
        for idx in order:
            p = plan(stats[idx], median)
            plans[idx] = p
            if p["action"] == "out_of_band":
                dlog["out_of_band"].append(
                    {"frame": idx + 1, "ratio": round(p["ratio"], 4)})
            elif p["action"] == "noop":
                dlog["noop"].append(idx + 1)
            else:
                dlog["scaled"] += 1
                if p["clamped"]:
                    dlog["clamped"].append(
                        {"frame": idx + 1,
                         "s_wanted": round(p["s_wanted"], 4),
                         "s_applied": round(p["s_applied"], 4)})
        # Still + invite against the same median.
        still_path = RES_DIR / f"{prefix}.png"
        invite_path = RES_DIR / f"{prefix}_invite.webp"
        still_st = frame_stats(Image.open(still_path))
        invite_st = frame_stats(Image.open(invite_path))
        dlog["still"] = {"ratio": round(still_st["ratio"], 4),
                         **{k: v for k, v in plan(still_st, median).items()
                            if k != "ratio"}}
        dlog["invite"] = {"ratio": round(invite_st["ratio"], 4),
                          **{k: v for k, v in plan(invite_st, median).items()
                             if k != "ratio"}}
        print(f"{key:14s} median={median:.4f} scaled={dlog['scaled']} "
              f"noop={len(dlog['noop'])} out_of_band={len(dlog['out_of_band'])} "
              f"clamped={len(dlog['clamped'])} matched_src={len(matches)}/"
              f"{len(inb_shipped)} still={dlog['still']} invite={dlog['invite']}")
        if not APPLY:
            continue
        # ---- shipped dance frames
        for idx in order:
            p = plans[idx]
            if p["action"] == "scale":
                apply_to_file(files[idx], p["s_applied"], stats[idx])
        # ---- still + invite (res files; the still PNG is its own master)
        if dlog["still"]["action"] == "scale":
            apply_to_file(still_path, dlog["still"]["s_applied"], still_st)
        if dlog["invite"]["action"] == "scale":
            apply_to_file(invite_path, dlog["invite"]["s_applied"], invite_st)
        # ---- mirror: PNG masters for frames 1..60
        for idx in order:
            p = plans[idx]
            if p["action"] != "scale" or idx >= 60:
                continue
            src = PNG_DIR / f"{prefix}_{idx + 1:02d}.png"
            _mirror(src, p, stats[idx], dlog, frame=idx + 1)
        # ---- mirror: matched in-between sources
        for idx, (src, bd, sd) in matches.items():
            p = plans[idx]
            if p["action"] != "scale":
                continue
            _mirror(src, p, stats[idx], dlog, frame=idx + 1,
                    match=(round(bd, 5), round(sd, 5)))
        # ---- mirror: invite master PNG
        if dlog["invite"]["action"] == "scale":
            _mirror(PNG_DIR / f"{prefix}_invite.png",
                    dlog["invite"], invite_st, dlog, frame="invite")
        log["dancers"][key] = dlog
    if APPLY:
        LOG_PATH.write_text(json.dumps(log, indent=1))
        print("wrote", LOG_PATH.relative_to(PROJECT))
    else:
        print("dry run — nothing written (pass --apply)")


def _mirror(src: Path, p: dict, shipped_st: dict, dlog: dict,
            frame, match=None) -> None:
    """Apply the shipped frame's wanted scale to a source master,
    clamped by the source's own margins."""
    rec = {"frame": frame, "src": str(src)}
    if match:
        rec["match_dist"] = match
    if not src.exists():
        dlog["source_skips"].append({**rec, "why": "missing"})
        return
    sim = Image.open(src)
    sst = frame_stats(sim)
    if sst["canvas"] != shipped_st["canvas"]:
        dlog["source_skips"].append(
            {**rec, "why": f"source canvas {sst['canvas']} != "
                           f"{shipped_st['canvas']}"})
        return
    s = clamp_scale(p["s_wanted"], sst)
    bh = sst["bbox"][3] - sst["bbox"][1] + 1
    if round(bh * s) == bh:
        dlog["source_skips"].append({**rec, "why": "no-op at source"})
        return
    out = scale_about(sim, s, sst["pelvis_x"], sst["bbox"][3] + 1.0)
    if src.suffix == ".png":
        out.save(src, format="PNG")
    else:
        out.save(src, format="WEBP", quality=95, method=4)
    dlog["sources_scaled"] += 1


if __name__ == "__main__":
    main()
