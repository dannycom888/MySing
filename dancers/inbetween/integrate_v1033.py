#!/usr/bin/env python3
"""v1.0.33 integration generator for the in-between frames.

Reads the assets-phase output in dancers/inbetween/<key>/ (see
MANIFEST.md), cross-checks the on-disk gap sets against the
MANIFEST tables, then derives everything the app integration
needs:

* frame-index assignments — the 40 in-betweens per dancer become
  frames 60..99, assigned walking gaps in ascending orderIndex,
  within a double gap in playback order t1 -> MID -> t2;
* the extended 100-step dance orders — the v1.0.17/22 60-step
  tour with the in-between indices spliced in right after their
  gap's step (originals keep their exact relative order);
* copies of the WebPs into app/src/main/res/drawable-nodpi as
  dancer_<prefix>_61 .. dancer_<prefix>_100.webp (the naming
  continues the existing frame sequence);
* dancers/inbetween/inbetween_orders.json — the durable record
  (also what dancers/make_anim.py samples the phone cards from).

Kotlin literals for DancerPoseOrder.kt / Dancers.kt are printed
to stdout for pasting. Robot is not touched (no in-betweens by
design — his stepped motion is his character).

Run from the project root:  python3 dancers/inbetween/integrate_v1033.py
"""

import json
import re
import shutil
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent.parent
SRC_DIR = PROJECT / "dancers" / "inbetween"
RES_DIR = PROJECT / "app/src/main/res/drawable-nodpi"
MANIFEST = SRC_DIR / "MANIFEST.md"
ORDERS_JSON = PROJECT / "dancers" / "resequence_orders.json"
OUT_JSON = SRC_DIR / "inbetween_orders.json"

# dancer key -> (asset dir, drawable prefix)
DANCERS = {
    "modern": "dancer_modern_woman",
    "ancient": "dancer_ancient_woman",
    "sumo": "dancer_sumo",
    "ancient_girl": "dancer_ancient_girl",
    "baby": "dancer_baby",
}
SECTION_OF = {  # MANIFEST section header name -> dancer key
    "春丽": "modern",
    "林语嫣": "ancient",
    "姿三四郎": "sumo",
    "小蛮": "ancient_girl",
    "哪吒": "baby",
}


def manifest_gaps() -> dict:
    """key -> (single+double gap index set, double gap index set),
    parsed from the MANIFEST tables (first column of each row)."""
    text = MANIFEST.read_text(encoding="utf-8")
    gaps, doubles = {}, {}
    current = None
    for line in text.splitlines():
        m = re.match(r"## (\S+?)（(\w+)）", line)
        if m:
            current = m.group(2)
            gaps[current], doubles[current] = set(), set()
            continue
        m = re.search(r"双补间隙（距离前 5）：([0-9, ]+)", line)
        if m and current:
            doubles[current] = {int(x) for x in m.group(1).split(",")}
            continue
        m = re.match(r"\| (\d+) \|", line)
        if m and current:
            gaps[current].add(int(m.group(1)))
    return gaps, doubles


def disk_gaps(key: str) -> dict:
    """orderIndex -> {'mid': file, 't1': file, 't2': file} from disk."""
    out = {}
    for f in sorted((SRC_DIR / key).glob("after_*.webp")):
        m = re.fullmatch(r"after_(\d+)(?:_(t1|t2))?\.webp", f.name)
        idx, kind = int(m.group(1)), m.group(2) or "mid"
        out.setdefault(idx, {})[kind] = f
    return out


def main() -> None:
    orders = {k: v["order"] for k, v in
              json.loads(ORDERS_JSON.read_text()).items()}
    m_gaps, m_doubles = manifest_gaps()
    record = {}
    for key, prefix in DANCERS.items():
        old_order = orders[key]
        assert len(old_order) == 60, key
        disk = disk_gaps(key)
        # Cross-check disk against MANIFEST before trusting either.
        assert set(disk) == m_gaps[key], (key, set(disk) ^ m_gaps[key])
        disk_doubles = {i for i, kinds in disk.items() if "t1" in kinds}
        assert disk_doubles == m_doubles[key], (key, disk_doubles, m_doubles[key])
        for kinds in disk.values():
            assert "mid" in kinds
            assert ("t1" in kinds) == ("t2" in kinds)

        # Assign new frame indices 60.. in gap order; playback
        # order inside a double gap is t1 -> MID -> t2.
        assign = {}   # (gap, kind) -> new frame index
        nxt = 60
        for gap in sorted(disk):
            kinds = disk[gap]
            seq = ["t1", "mid", "t2"] if "t1" in kinds else ["mid"]
            for kind in seq:
                assign[(gap, kind)] = nxt
                nxt += 1
        assert nxt == 100, (key, nxt)

        # Splice into the tour: after step `gap`, its in-betweens.
        new_order = []
        insertions = {}
        for pos, frame in enumerate(old_order):
            new_order.append(frame)
            if pos in disk:
                kinds = disk[pos]
                seq = ["t1", "mid", "t2"] if "t1" in kinds else ["mid"]
                idxs = [assign[(pos, kind)] for kind in seq]
                insertions[str(pos)] = idxs
                new_order.extend(idxs)
        assert len(new_order) == 100, (key, len(new_order))
        assert sorted(new_order) == list(range(100)), key
        assert [f for f in new_order if f < 60] == old_order, key
        assert new_order[0] == 0, key

        # Copy the artwork into the app resources, continuing the
        # existing numbering: frame index i -> _<i+1:02d>.webp.
        for (gap, kind), idx in sorted(assign.items(), key=lambda kv: kv[1]):
            dst = RES_DIR / f"{prefix}_{idx + 1:02d}.webp"
            shutil.copyfile(disk[gap][kind], dst)

        record[key] = {
            "order": new_order,
            "insertions": insertions,
            "double_gaps": sorted(disk_doubles),
        }
        print(f"== {key} ({prefix}) ==")
        print("Kotlin order literal:")
        for i in range(0, 100, 10):
            print("            " + ", ".join(str(n) for n in new_order[i:i + 10]) + ",")
        print("Drawable refs 61..100:")
        refs = [f"R.drawable.{prefix}_{i + 1:02d}" for i in range(60, 100)]
        for i in range(0, 40, 2):
            print("                " + ", ".join(refs[i:i + 2]) + ",")
        print()

    OUT_JSON.write_text(json.dumps(record, indent=1), encoding="utf-8")
    print(f"wrote {OUT_JSON.relative_to(PROJECT)}")


if __name__ == "__main__":
    main()
