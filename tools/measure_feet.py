#!/usr/bin/env python3
"""Measure per-clip foot baselines and write feet_v into the accents JSONs.

Why (v1.0.73): the wide-shot clips keep a green margin BELOW the
boots — the feet sit above the texture's bottom edge. The GL stage
used to map the full texture onto the figure box and mirror the
reflection from the texture's bottom row, so the boots floated
feetV above the stage baseline and their reflection started a
further feetV below it: a visible gap at the feet, "body floating
in the air" (user, v1.0.72 device test).

feet_v = 1 - p95(bbox bottom fraction from top), measured with
the same figure mask as measure_figure_scales.py on the FINAL
512x800 assets. p95 (not max) keeps a stray mask pixel from
moving the floor; on this pack p95 and max agree within 0.005
on every clip. Measured range: 0.035 (fast-03) .. 0.120
(slow-08), median ~0.06 — per-clip values, not one global.

The stage anchors on it: the figure samples v in [feet_v,
feet_v + 1] so the feet row lands exactly on the zone baseline,
and the reflection samples [feet_v, feet_v + depth] downward
from the same row — feet and reflection meet at the baseline.

Run after any re-encode of the assets (and after
measure_figure_scales.py — this tool preserves its fields):
    python3 tools/measure_feet.py
"""
import glob, json, os, subprocess
import numpy as np

ASSET_DIR = 'app/src/main/assets/amy_video'


def probe_wh(path):
    r = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
                        '-show_entries', 'stream=width,height', '-of', 'csv=p=0', path],
                       capture_output=True, text=True)
    w, h = r.stdout.strip().split(',')
    return int(w), int(h)


def stream_frames(path, w, h, fps):
    cmd = ['ffmpeg', '-v', 'error', '-i', path, '-map', '0:v:0', '-an',
           '-vf', f'fps={fps}', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    n = w * h * 3
    try:
        while True:
            buf = p.stdout.read(n)
            if len(buf) < n:
                break
            yield np.frombuffer(buf, dtype=np.uint8).reshape(h, w, 3)
    finally:
        p.stdout.close(); p.wait()


def figure_mask(a):
    g = a[:, :, 1].astype(np.int16)
    return (g <= a[:, :, 0].astype(np.int16) + 25) | (g <= a[:, :, 2].astype(np.int16) + 25)


def main():
    for path in sorted(glob.glob(os.path.join(ASSET_DIR, '*.mp4'))):
        name = os.path.basename(path)[:-4]
        w, h = probe_wh(path)
        bottoms = []
        for fr in stream_frames(path, w, h, 4):
            rows = np.where(figure_mask(fr).any(axis=1))[0]
            if len(rows):
                bottoms.append((rows.max() + 1) / h)
        feet_v = round(1.0 - float(np.percentile(np.array(bottoms), 95)), 3)
        jpath = os.path.join(ASSET_DIR, name + '.accents.json')
        with open(jpath) as f:
            data = json.load(f)
        data['feet_v'] = feet_v
        with open(jpath, 'w') as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        print(f'{name:9s} feet_v={feet_v:.3f} (frames={len(bottoms)})')


if __name__ == '__main__':
    main()
