#!/usr/bin/env python3
"""Sample key-gate pixels from the shipped Amy video assets.

Writes app/src/test/resources/amy_key_samples.json, the fixture
AmyVideoKeyGateTest runs the stage shader's key math against.
For every shipped clip the fixture records:

    sha256 / size     of the MP4 — the gate fails on ANY
                      re-encode until this script is re-run,
                      so encode/key drift can never slip in
                      silently;
    bg_samples        64 background RGB pixels (corner blocks
                      of every 3rd frame at 4fps, decoded with
                      the stream's tagged BT.709 matrix — what
                      the stage's texture sampling sees).
                      Pixels under the packaging figure mask
                      are excluded: a tall-framed clip's hair
                      can graze the top-centre block (the
                      v1.0.75 invite does, at 0.75s and 9.75s),
                      and a hair pixel is not a backdrop sample
                      — the gate judges the backdrop, so the
                      pool is background-only by construction;
    bg_median_rgb     their median;
    figure_samples    96 figure RGB pixels (eroded figure
                      mask, packaging definition).

Sampling is fully deterministic (fixed frame stride, fixed
linspace picks, no RNG), so re-running on unchanged assets
reproduces the fixture byte-for-byte apart from ordering.

    python3 tools/sample_key_pixels.py
"""
import glob, hashlib, json, os, subprocess
import numpy as np

ASSET_DIR = 'app/src/main/assets/amy_video'
OUT = 'app/src/test/resources/amy_key_samples.json'
BG_PER_CLIP = 64
FIG_PER_CLIP = 96

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

def erode(m, k=3):
    out = m.copy()
    for dy in range(-k, k + 1):
        for dx in range(-k, k + 1):
            out &= np.roll(np.roll(m, dy, axis=0), dx, axis=1)
    out[:k + 1, :] = False; out[-k - 1:, :] = False
    out[:, :k + 1] = False; out[:, -k - 1:] = False
    return out

def pick(pool, count):
    idx = np.linspace(0, len(pool) - 1, count).astype(int)
    return [[int(v) for v in px] for px in pool[idx]]

if __name__ == '__main__':
    clips = {}
    for path in sorted(glob.glob(f'{ASSET_DIR}/*.mp4')):
        name = os.path.basename(path)[:-len('.mp4')]
        w, h = probe_wh(path)
        B = 48
        bg_pool, fig_pool = [], []
        for i, f in enumerate(stream_frames(path, w, h, fps=4)):
            if i % 3 != 0:
                continue
            m = figure_mask(f.astype(int))
            regions = [(slice(0, B), slice(0, B)), (slice(0, B), slice(w - B, w)),
                       (slice(h - B, h), slice(0, B)), (slice(h - B, h), slice(w - B, w)),
                       (slice(0, B), slice(w // 2 - B // 2, w // 2 + B // 2))]
            bg_pool.append(np.concatenate(
                [f[r][~m[r]].reshape(-1, 3) for r in regions]))
            fig_pool.append(f[erode(m, 3)])
        bg_pool = np.concatenate(bg_pool)
        fig_pool = np.concatenate(fig_pool)
        digest = hashlib.sha256(open(path, 'rb').read()).hexdigest()
        clips[name] = {
            'sha256': digest,
            'size': os.path.getsize(path),
            'bg_median_rgb': [int(v) for v in np.median(bg_pool, axis=0)],
            'bg_samples': pick(bg_pool, BG_PER_CLIP),
            'figure_samples': pick(fig_pool, FIG_PER_CLIP),
        }
        print(f"{name}: sha {digest[:12]} bg_median {clips[name]['bg_median_rgb']} "
              f"pools bg={len(bg_pool)} fig={len(fig_pool)}", flush=True)
    fixture = {
        'version': 1,
        'generator': 'tools/sample_key_pixels.py',
        'note': 'Re-run the generator after ANY re-encode of the assets; '
                'AmyVideoKeyGateTest fails on hash mismatch by design.',
        'clips': clips,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as fh:
        json.dump(fixture, fh, indent=1, ensure_ascii=False)
        fh.write('\n')
    print(f'wrote {OUT} ({os.path.getsize(OUT)} bytes) for {len(clips)} clips')
    print('ALLDONE')
