#!/usr/bin/env python3
"""v1.0.62 dancer size fix, phase 2 of the size audit.

The audit (dancers/size-audit/AUDIT.md + stats.json) proved the
v1.0.43 normalisation scaled by the WRONG quantity (alpha bbox
height -- mostly pose, not body size) and damaged 31 frames by
proxy 1 (42 by proxy 2): frames whose body size was already
within +-3% got pushed >=3 points off. This script:

  1. Rolls the damaged frames (union of the audit's two harmed
     lists in stats.json, 67 frames) back to their pre-v1.0.43
     state (git 5137910, the v1.0.42 commit), shipped webp and
     source masters alike.
  2. Re-scales every tour frame by HEAD scale instead of bbox
     height, toward the dancer's reliable-frame median:
       - Xiuying (ancient): face height only (her sleeves and
         bun poison every other measure).
       - everyone else: head height + sqrt(head-band area) as
         dual proxies, combined geometrically; when the two
         disagree by >18% the conservative (smaller) correction
         wins -- disagreement means one measure slipped (the
         audit's robot-34 case: neck line slid into the torso).
     Scale is capped at +-12%, pivots on (pelvis x, foot
     baseline) exactly like v1.0.41/43, growth is clamped to the
     canvas margins so nothing is ever cropped.
  3. Leaves frames no honest number can steer on the manual
     list (audit-unreliable frames: Xiuying's sleeve-hidden
     faces, the sumo's fused head/torso frames, the robot's
     raised-arm frames; plus four frames the audit's numbers
     alone could not settle and this pass checked by eye --
     see MANUAL_SKIP below).

Mirroring follows v1.0.43: frames 1-60 mirror into
dancers/poses/frames_png by name; the in-betweens (61-100)
mirror into dancers/inbetween/<key>/ via content matching
against the pre-scale base frames (same acceptance thresholds
as v1.0.43: best RMS < 0.03 and < 0.6x the runner-up).

Run from the project root:
    python3 dancers/fix_size_v1062.py           # dry run
    python3 dancers/fix_size_v1062.py --apply   # write
"""
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
DANCERS_DIR = PROJECT / "dancers"
RES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
PNG_DIR = DANCERS_DIR / "poses/frames_png"
INBETWEEN_DIR = DANCERS_DIR / "inbetween"
STATS_JSON = DANCERS_DIR / "size-audit/stats.json"
LOG_PATH = DANCERS_DIR / "fix_v1062_log.json"
WORK = Path("/tmp/v1062")
PRE43_COMMIT = "5137910"

CAP = 1.12               # +-12% scale cap (the brief)
NOOP_LOG = 0.008         # below this |log s| the resample is a no-op
DISAGREE_LOG = 0.18      # dual-proxy disagreement threshold

sys.path.insert(0, str(DANCERS_DIR))
sys.path.insert(0, str(DANCERS_DIR / "size-audit"))
import measure_audit as MA          # audit measurement (same method)
import normalize_size_v1043 as N43  # scale_about / clamp_scale
from measure_size_v1043 import frame_stats

APPLY = "--apply" in sys.argv

# Frames the numbers alone cannot settle; checked by eye against
# their tour neighbours in this pass (see BUILD_REPORT v1.0.62):
#  - modern 9/20: the audit called them whole-frame shrunk
#    exports from their bbox heights (328/355 vs tour median
#    483), but they sit in a deep-squat segment (tour positions
#    38/39; neighbours' bboxes 355-459 are all squats), their
#    face heights measure normal (221/278 vs segment 144-233),
#    and the head heights the claim rests on are the audit's own
#    unreliable full-neck+capped reads (hair massif: head_w
#    219/235 vs neighbours' 113-117). NOT whole-frame shrinks.
#  - robot 9: deep crouch, head detection failed outright
#    (37 px); by eye the dome is proportional to the crouched
#    body. NOT a whole-frame shrink.
#  - ancient 23: face genuinely drawn +28% (the audit verified
#    by eye) while the body is normal -- a proportion error no
#    uniform scale can fix.
MANUAL_SKIP = {
    ("modern", 9): "audit shrink claim rests on slipped head "
                   "measure; squat segment, face normal (by eye)",
    ("modern", 20): "audit shrink claim rests on slipped head "
                    "measure; squat segment, face normal (by eye)",
    ("robot", 9): "deep crouch; head detection failed; dome "
                  "proportional by eye -- not a whole shrink",
    ("ancient", 23): "face drawn +28% with normal body -- "
                     "proportion error, uniform scale cannot fix",
}


def git_show(commit: str, relpath: str) -> bytes:
    out = subprocess.run(
        ["git", "-C", str(PROJECT), "show", f"{commit}:{relpath}"],
        capture_output=True, check=True)
    return out.stdout


def rel_res(prefix: str, n: int) -> str:
    return f"app/src/main/res/drawable-nodpi/{prefix}_{n:02d}.webp"


# ------------------------------------------------------------------ audit gate
def gate_and_medians(measures: dict, key: str, order: list) -> dict:
    """Replicates measure_audit.main's gate/refine exactly:
    median of primary proxies -> [0.70, 1.42] gate -> capped
    refine for head/none kinds -> unreliable set; medians over
    reliable frames for both proxies."""
    vals = [MA.primary_proxy(key, measures[i])[0] for i in order]
    med_all = float(np.median([v for v in vals if v is not None]))
    unreliable = set()
    for i in order:
        m = measures[i]
        v, kind = MA.primary_proxy(key, m)
        if v is None or not (MA.GATE_LO * med_all <= v
                             <= MA.GATE_HI * med_all):
            if kind in ("head", "none"):
                got = MA.refine_head(m, MA.CAP_MULT * med_all)
                if got["head_h"] is not None:
                    m.update(got)
                    m["head_method"] += "+capped"
            v, _ = MA.primary_proxy(key, m)
            if v is None or not (MA.GATE_LO * med_all <= v
                                 <= MA.GATE_HI * med_all):
                unreliable.add(i + 1)
    rel = [i for i in order if (i + 1) not in unreliable]
    ref1 = float(np.median([MA.primary_proxy(key, measures[i])[0]
                            for i in rel
                            if MA.primary_proxy(key, measures[i])[0]
                            is not None]))
    p2s = [MA.proxy2(key, measures[i]) for i in rel]
    ref2 = float(np.median([v for v in p2s if v is not None])) \
        if any(v is not None for v in p2s) else None
    return {"unreliable": unreliable, "ref1": ref1, "ref2": ref2,
            "med_all": med_all}


# ------------------------------------------------------------------ main plan
def main() -> None:
    stats = json.loads(STATS_JSON.read_text())
    harmed = set()
    for k, v in stats["dancers"].items():
        for e in v["harmed_list"]:
            harmed.add((k, e["frame"]))
        for e in v["proxy2"]["harmed_list"]:
            harmed.add((k, e["frame"]))
    orders = MA.parse_orders()
    base_dir = WORK / "base"
    fixed_dir = WORK / "fixed"
    log = {"cap": CAP, "rollback_frames": len(harmed), "dancers": {}}

    plans = {}   # (key, file_no) -> plan dict
    bases = {}   # (key, file_no) -> base image path (work dir)
    for key, (prefix, _name) in MA.DANCERS.items():
        order = orders[key]
        bdir = base_dir / key
        bdir.mkdir(parents=True, exist_ok=True)
        measures = {}
        for i in order:
            n = i + 1
            dst = bdir / f"{prefix}_{n:02d}.webp"
            if (key, n) in harmed:
                dst.write_bytes(git_show(PRE43_COMMIT,
                                         rel_res(prefix, n)))
            else:
                dst.write_bytes((RES_DIR / dst.name).read_bytes())
            bases[(key, n)] = dst
            measures[i] = MA.full_measure(Image.open(dst))
        g = gate_and_medians(measures, key, order)
        dlog = {"ref1": g["ref1"], "ref2": g["ref2"],
                "unreliable": sorted(g["unreliable"]),
                "scaled": {}, "noop": [], "manual": {},
                "clamped": {}, "disagree": {}}
        for i in order:
            n = i + 1
            m = measures[i]
            tag = (key, n)
            if tag in MANUAL_SKIP:
                dlog["manual"][n] = MANUAL_SKIP[tag]
                plans[tag] = {"action": "manual"}
                continue
            if n in g["unreliable"]:
                dlog["manual"][n] = "audit-unreliable (no honest " \
                                    "head/face measure)"
                plans[tag] = {"action": "manual"}
                continue
            if key == "ancient":
                face = m.get("face_h")
                if not face:
                    dlog["manual"][n] = "face not measurable"
                    plans[tag] = {"action": "manual"}
                    continue
                s_wanted = g["ref1"] / face
                basis = "face"
            else:
                head = m.get("head_h")
                p2 = MA.proxy2(key, m)
                s1 = g["ref1"] / head if head else None
                s2 = g["ref2"] / p2 if (p2 and g["ref2"]) else None
                if s1 and s2:
                    if abs(math.log(s1) - math.log(s2)) <= DISAGREE_LOG:
                        s_wanted = math.exp(
                            (math.log(s1) + math.log(s2)) / 2)
                        basis = "dual"
                    else:
                        s_wanted = s1 if abs(math.log(s1)) \
                            < abs(math.log(s2)) else s2
                        basis = "dual-disagree->%s" % (
                            "head" if s_wanted == s1 else "area")
                        dlog["disagree"][n] = {
                            "s_head": round(s1, 4),
                            "s_area": round(s2, 4),
                            "picked": basis}
                elif s1 or s2:
                    s_wanted = s1 or s2
                    basis = "head" if s1 else "area"
                else:
                    dlog["manual"][n] = "no proxy measurable"
                    plans[tag] = {"action": "manual"}
                    continue
            s_capped = min(CAP, max(1.0 / CAP, s_wanted))
            if abs(math.log(s_capped)) < NOOP_LOG:
                dlog["noop"].append(n)
                plans[tag] = {"action": "noop"}
                continue
            st = frame_stats(Image.open(bases[tag]))
            s_applied = N43.clamp_scale(s_capped, st)
            bh = st["bbox"][3] - st["bbox"][1] + 1
            if round(bh * s_applied) == bh \
                    or abs(math.log(s_applied)) < NOOP_LOG:
                # Growth the canvas cannot hold (the figure
                # already fills it): re-encoding at ~1.0 would
                # only cost quality. On the manual list instead.
                dlog["manual"][n] = "canvas full -- growth " \
                                    "clamped to a no-op (wanted " \
                                    f"{s_capped:.3f})"
                plans[tag] = {"action": "clamp_noop"}
                continue
            if s_applied < s_capped - 1e-9:
                dlog["clamped"][n] = {"wanted": round(s_capped, 4),
                                      "applied": round(s_applied, 4)}
            dlog["scaled"][n] = {"s_wanted": round(s_wanted, 4),
                                 "s_capped": round(s_capped, 4),
                                 "s_applied": round(s_applied, 4),
                                 "basis": basis}
            plans[tag] = {"action": "scale", "s_wanted": s_wanted,
                          "s_capped": s_capped,
                          "s_applied": s_applied, "stats": st}
        log["dancers"][key] = dlog
        print(f"{key:14s} ref1={g['ref1']:.1f} ref2="
              f"{(g['ref2'] or 0):.1f} scaled={len(dlog['scaled'])} "
              f"noop={len(dlog['noop'])} manual={len(dlog['manual'])} "
              f"clamped={len(dlog['clamped'])} "
              f"disagree={len(dlog['disagree'])}")

    if not APPLY:
        WORK.mkdir(parents=True, exist_ok=True)
        (WORK / "plan.json").write_text(json.dumps(log, indent=1))
        print("dry run -- plan at", WORK / "plan.json")
        return

    # ---- write fixed shipped frames (work dir first)
    for (key, n), p in plans.items():
        if p["action"] != "scale":
            continue
        prefix = MA.DANCERS[key][0]
        out = Image.open(bases[(key, n)])
        fixed = N43.scale_about(out, p["s_applied"],
                                p["stats"]["pelvis_x"],
                                p["stats"]["bbox"][3] + 1.0)
        fdir = fixed_dir / key
        fdir.mkdir(parents=True, exist_ok=True)
        fpath = fdir / f"{prefix}_{n:02d}.webp"
        fixed.save(fpath, format="WEBP", quality=95, method=4)
        shutil.copyfile(fpath, RES_DIR / fpath.name)

    # ---- mirror masters
    mirror(log, plans, bases, harmed)
    LOG_PATH.write_text(json.dumps(log, indent=1))
    print("wrote", LOG_PATH.relative_to(PROJECT))


def _mirror_one(src: Path, s_wanted: float, rec: dict) -> str:
    """v1.0.43 _mirror semantics: wanted scale clamped by the
    source's own margins; no-op if bbox height would not move."""
    if not src.exists():
        return "missing"
    im = Image.open(src)
    sst = frame_stats(im)
    s = N43.clamp_scale(s_wanted, sst)
    bh = sst["bbox"][3] - sst["bbox"][1] + 1
    if round(bh * s) == bh:
        return "no-op at source"
    out = N43.scale_about(im, s, sst["pelvis_x"],
                          sst["bbox"][3] + 1.0)
    if src.suffix == ".png":
        out.save(src, format="PNG")
    else:
        out.save(src, format="WEBP", quality=95, method=4)
    rec["s_mirror"] = round(s, 4)
    return "scaled"


def mirror(log: dict, plans: dict, bases: dict, harmed: set) -> None:
    """Mirror the shipped fix into the source masters. Rolled-back
    frames first get their pre-v1.0.43 master restored from git,
    so a master always equals its shipped frame's base + scale."""
    # content-match in-betweens against BASE shipped frames
    matches = {}
    for key in MA.DANCERS:
        idxs = [n for (k, n) in plans
                if k == key and n >= 61
                and plans[(k, n)]["action"] == "scale"]
        if not idxs:
            continue
        srcs = {}
        idir = INBETWEEN_DIR / key
        cand = {}
        for n in idxs:
            rolled = (key, n) in harmed
            if rolled:
                tmp = WORK / "pre43_inbetween" / key
                if not tmp.exists():
                    tmp.mkdir(parents=True, exist_ok=True)
                    names = subprocess.run(
                        ["git", "-C", str(PROJECT), "ls-tree",
                         "--name-only", PRE43_COMMIT,
                         f"dancers/inbetween/{key}/"],
                        capture_output=True, check=True,
                        text=True).stdout.split()
                    for nm in names:
                        (tmp / Path(nm).name).write_bytes(git_show(
                            PRE43_COMMIT,
                            f"dancers/inbetween/{key}/"
                            f"{Path(nm).name}"))
                cand[n] = tmp
            else:
                cand[n] = idir
        pool = {}
        for n in idxs:
            for f in sorted(cand[n].glob("after_*.webp")):
                if f not in pool:
                    pool[f] = N43._rep(Image.open(f))
        for n in idxs:
            rep = N43._rep(Image.open(bases[(key, n)]))
            dists = sorted(
                ((float(np.sqrt(np.mean((rep - srep) ** 2))), sp)
                 for sp, srep in pool.items()
                 if sp.parent == cand[n]),
                key=lambda t: t[0])
            best, second = dists[0], dists[1]
            if best[0] < 0.03 and best[0] < 0.6 * second[0]:
                real = INBETWEEN_DIR / key / best[1].name
                matches[(key, n)] = (real,
                                     (round(best[0], 5),
                                      round(second[0], 5)),
                                     (key, n) in harmed,
                                     best[1])
    for key, (prefix, _n) in MA.DANCERS.items():
        dlog = log["dancers"][key]
        dlog["mirror"] = {"scaled": 0, "skips": []}
        for (k, n), p in sorted(plans.items()):
            if k != key or p["action"] != "scale":
                continue
            rolled = (key, n) in harmed
            if n <= 60:
                src = PNG_DIR / f"{prefix}_{n:02d}.png"
                if rolled:
                    src.write_bytes(git_show(
                        PRE43_COMMIT,
                        f"dancers/poses/frames_png/{src.name}"))
                rec = {"frame": n, "src": str(src)}
                why = _mirror_one(src, p["s_wanted"], rec)
                if why == "scaled":
                    dlog["mirror"]["scaled"] += 1
                else:
                    dlog["mirror"]["skips"].append({**rec, "why": why})
            else:
                if (key, n) not in matches:
                    dlog["mirror"]["skips"].append(
                        {"frame": n, "why": "no confident "
                                            "in-between match"})
                    continue
                src, dist, was_rolled, pre_file = matches[(key, n)]
                if was_rolled:
                    src.write_bytes(git_show(
                        PRE43_COMMIT,
                        f"dancers/inbetween/{key}/{pre_file.name}"))
                rec = {"frame": n, "src": str(src),
                       "match_dist": dist}
                why = _mirror_one(src, p["s_wanted"], rec)
                if why == "scaled":
                    dlog["mirror"]["scaled"] += 1
                else:
                    dlog["mirror"]["skips"].append({**rec, "why": why})


if __name__ == "__main__":
    main()
