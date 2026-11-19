#!/usr/bin/env python3
"""Measure per-clip figure heights and write playback scale factors.

Why (v1.0.72): the pack pipeline's detrend flattened each clip's
slow size drift around its OWN median — it never unified the
absolute figure height ACROSS clips. Measured on the shipped
512x800 assets (packaging approach: figure = green does not
dominate, bbox height fraction, ~4s median-filtered trend,
median of the trend), the 20 dance clips span 0.703 (slow-08)
to 0.881 (slow-06) — a 25% spread. With v1.0.71's chaining, a
song switches clips mid-song, so Amy visibly changed size at
every switch （忽大忽小）.

This tool measures every clip's trend median on the FINAL
assets and writes two fields into each packaged accents JSON:

    figure_height  the measured trend median (frame fraction)
    figure_scale   ANCHOR / figure_height, 3 decimals

The anchor is the IDLE clip's median: v1.0.71's
VIDEO_FIGURE_SCALE was tuned against idle's standing height,
so idle's factor is exactly 1.000 and the stage anchor does
not move; every other clip is scaled at playback so its
figure lands on the same on-stage height
(figure_height x figure_scale == anchor for all clips).

Run after any re-encode of the assets:
    python3 tools/measure_figure_scales.py
"""
import json, subprocess, sys
import numpy as np

ASSET_DIR = 'app/src/main/assets/amy_video'
ANCHOR_CLIP = 'idle'

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
    g = a[:, :, 1]
    return (g <= a[:, :, 0] + 25) | (g <= a[:, :, 2] + 25)

def medfilt(x, w):
    n = len(x); half = w // 2
    return np.array([np.median(x[max(0, i - half):min(n, i + half + 1)]) for i in range(n)])

def height_trend_median(path):
    w, h = probe_wh(path)
    hs = []
    for f in stream_frames(path, w, h, fps=4):
        m = figure_mask(f.astype(int))
        ys, _ = np.where(m)
        hs.append((ys.max() - ys.min() + 1) / h if len(ys) else np.nan)
    hs = np.array(hs)
    idx = np.arange(len(hs)); good = ~np.isnan(hs)
    hs[~good] = np.interp(idx[~good], idx[good], hs[good])
    return float(np.median(medfilt(hs, 17)))

if __name__ == '__main__':
    import glob, os
    files = sorted(glob.glob(f'{ASSET_DIR}/*.accents.json'))
    names = [os.path.basename(f)[:-len('.accents.json')] for f in files]
    medians = {}
    for name in names:
        medians[name] = height_trend_median(f'{ASSET_DIR}/{name}.mp4')
        print(f'{name}: height trend median {medians[name]:.4f}', flush=True)
    anchor = medians[ANCHOR_CLIP]
    print(f'anchor ({ANCHOR_CLIP}): {anchor:.4f}')
    for name, path in zip(names, files):
        data = json.load(open(path))
        data['figure_height'] = round(medians[name], 4)
        data['figure_scale'] = round(anchor / medians[name], 3)
        with open(path, 'w') as fh:
            json.dump(data, fh, indent=1, ensure_ascii=False)
            fh.write('\n')
        implied = data['figure_height'] * data['figure_scale']
        print(f"{name}: figure_height={data['figure_height']} figure_scale={data['figure_scale']} "
              f"implied={implied:.4f} (anchor {anchor:.4f}, off {abs(implied/anchor-1)*100:.2f}%)")
    print('ALLDONE')
