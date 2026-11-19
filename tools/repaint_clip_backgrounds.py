#!/usr/bin/env python3
"""Repaint off-key green-screen backgrounds to the pack's standard green.

Why (v1.0.72 measurement): the Amy pack's clips were generated in
several batches and their backdrops are NOT one green. Run through
the stage shader's exact key math (chroma distance from #0af20a,
smoothstep 0.20-0.34), most clips key to alpha 0, but six leak:
fast-01 (median alpha 0.70), slow-08 (0.19), fast-07 (0.13),
slow-02 (0.09), slow-06 (0.04) and slow-05 (35% of its background
pixels leak). The figure pixels of every clip sit at chroma
distance >= 0.42 (p0.1), so the fix is in the DATA, not the
shader: repaint each leaky clip's backdrop to the pack's modal
green and keep the proven ramp.

The repaint works in native YUV420 space (no colourspace
round-trip): a soft mask from the pixel's (U,V) distance to the
clip's OWN measured background triplet blends every channel
toward the standard triplet STD_YUV. Figure chroma is far from
every backdrop (skin/hair/white cloth sit >= 50 (U,V)-counts
away), so a 16..40-count feather band separates cleanly.

Usage:
    python3 tools/repaint_clip_backgrounds.py fast-01 slow-08 ...
With no arguments it repaints the six clips measured leaky in
v1.0.72. Each repainted file is verified (frame count, duration,
new background alpha under the shader math) before it replaces
the shipped asset; the pack masters under dancers/ are never
touched.
"""
import json, subprocess, sys
import numpy as np
from PIL import Image

ASSET_DIR = 'app/src/main/assets/amy_video'
STD_YUV = (140.0, 64.0, 40.0)   # the pack's modal green: decodes (BT.709) to ~(0,204,5), key distance ~0.065
MASK_INNER = 16.0               # (U,V) distance below which a pixel is fully backdrop
MASK_OUTER = 40.0               # (U,V) distance above which a pixel is fully figure
ENC_ARGS = ['-an', '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p',
            '-color_primaries', 'bt709', '-color_trc', 'bt709',
            '-colorspace', 'bt709', '-color_range', 'tv',
            '-movflags', '+faststart']
KEY = np.array([0.039, 0.949, 0.039])

def chroma(c):
    y = c[..., 0] * 0.299 + c[..., 1] * 0.587 + c[..., 2] * 0.114
    return np.stack([(c[..., 2] - y) * 0.564, (c[..., 0] - y) * 0.713], axis=-1)

KEY_CHROMA = chroma(KEY.reshape(1, 1, 3).astype(np.float64)).reshape(2)

def shader_alpha(rgb):
    d = np.linalg.norm(chroma(rgb) - KEY_CHROMA, axis=-1)
    t = np.clip((d - 0.20) / 0.14, 0.0, 1.0)
    return t * t * (3 - 2 * t)

def probe(path):
    r = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
                        '-show_entries', 'stream=width,height,nb_frames,duration',
                        '-of', 'json', path], capture_output=True, text=True)
    s = json.loads(r.stdout)['streams'][0]
    return int(s['width']), int(s['height']), int(s['nb_frames']), float(s['duration'])

def stream_frames(path, w, h, pix_fmt):
    cmd = ['ffmpeg', '-v', 'error', '-i', path, '-map', '0:v:0', '-an',
           '-f', 'rawvideo', '-pix_fmt', pix_fmt, '-']
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    n = w * h * 3 if pix_fmt == 'rgb24' else w * h * 3 // 2
    try:
        while True:
            buf = p.stdout.read(n)
            if len(buf) < n:
                break
            yield np.frombuffer(buf, dtype=np.uint8)
    finally:
        p.stdout.close(); p.wait()

def measure_bg_yuv(path, w, h):
    B = 48
    ys, us, vs = [], [], []
    for raw in stream_frames(path, w, h, 'yuv420p'):
        Y = raw[:w * h].reshape(h, w)
        U = raw[w * h:w * h + (w // 2) * (h // 2)].reshape(h // 2, w // 2)
        V = raw[w * h + (w // 2) * (h // 2):].reshape(h // 2, w // 2)
        for (y0, x0) in [(0, 0), (0, w - B), (h - B, 0), (h - B, w - B)]:
            ys.append(Y[y0:y0 + B, x0:x0 + B].reshape(-1))
            us.append(U[y0 // 2:(y0 + B) // 2, x0 // 2:(x0 + B) // 2].reshape(-1))
            vs.append(V[y0 // 2:(y0 + B) // 2, x0 // 2:(x0 + B) // 2].reshape(-1))
    return (float(np.median(np.concatenate(ys))),
            float(np.median(np.concatenate(us))),
            float(np.median(np.concatenate(vs))))

def verify(path, w, h):
    """Background alpha of the (possibly repainted) file under the shader math."""
    B = 48
    samples = []
    cmd = ['ffmpeg', '-v', 'error', '-i', path, '-map', '0:v:0', '-an',
           '-vf', 'fps=2', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    n = w * h * 3
    try:
        while True:
            buf = p.stdout.read(n)
            if len(buf) < n:
                break
            f = np.frombuffer(buf, dtype=np.uint8).reshape(h, w, 3)
            blocks = [f[:B, :B], f[:B, -B:], f[-B:, :B], f[-B:, -B:],
                      f[:B, w // 2 - B // 2:w // 2 + B // 2]]
            samples.append(np.concatenate([b.reshape(-1, 3) for b in blocks]))
    finally:
        p.stdout.close(); p.wait()
    px = np.concatenate(samples).astype(np.float64) / 255.0
    a = shader_alpha(px.reshape(-1, 1, 3)).reshape(-1)
    med = np.median(px, axis=0)
    return med * 255.0, float(np.median(a)), float((a <= 0.02).mean())

def repaint(name):
    path = f'{ASSET_DIR}/{name}.mp4'
    w, h, n_frames, dur = probe(path)
    bg = measure_bg_yuv(path, w, h)
    print(f'{name}: bg YUV={tuple(round(v,1) for v in bg)} -> STD {STD_YUV}', flush=True)
    tmp = f'/tmp/repaint-{name}.mp4'
    # The setparams filter is load-bearing: rawvideo frames carry
    # no colour metadata, and without it ffmpeg's auto-inserted
    # scaler treats the samples as BT.601 and CONVERTS them on
    # the way to the BT.709 encoder (measured: (140,64,40) came
    # out as (164,52,33), tags dropped). setparams declares the
    # frames already-BT.709 without touching a single sample —
    # the encode is then sample-exact (verified on a synthetic
    # solid-colour clip).
    enc = subprocess.Popen(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo',
                            '-pix_fmt', 'yuv420p', '-s', f'{w}x{h}', '-r', '24',
                            '-i', '-',
                            '-vf', 'setparams=color_primaries=bt709:color_trc=bt709:'
                                   'colorspace=bt709:range=limited']
                           + ENC_ARGS + [tmp], stdin=subprocess.PIPE)
    y0, u0, v0 = bg
    count = 0
    for raw in stream_frames(path, w, h, 'yuv420p'):
        Y = raw[:w * h].reshape(h, w).astype(np.float64)
        U = raw[w * h:w * h + (w // 2) * (h // 2)].reshape(h // 2, w // 2).astype(np.float64)
        V = raw[w * h + (w // 2) * (h // 2):].reshape(h // 2, w // 2).astype(np.float64)
        dist = np.sqrt((U - u0) ** 2 + (V - v0) ** 2)
        wgt = np.clip((MASK_OUTER - dist) / (MASK_OUTER - MASK_INNER), 0.0, 1.0)
        wgt = wgt * wgt * (3 - 2 * wgt)
        U2 = wgt * STD_YUV[1] + (1 - wgt) * U
        V2 = wgt * STD_YUV[2] + (1 - wgt) * V
        wfull = np.asarray(Image.fromarray((wgt * 255).astype(np.uint8))
                           .resize((w, h), Image.BILINEAR), dtype=np.float64) / 255.0
        Y2 = wfull * STD_YUV[0] + (1 - wfull) * Y
        out = np.concatenate([np.clip(Y2, 0, 255).astype(np.uint8).reshape(-1),
                              np.clip(U2, 0, 255).astype(np.uint8).reshape(-1),
                              np.clip(V2, 0, 255).astype(np.uint8).reshape(-1)])
        enc.stdin.write(out.tobytes())
        count += 1
    enc.stdin.close(); enc.wait()
    if enc.returncode != 0:
        raise RuntimeError(f'{name}: encoder failed')
    w2, h2, n2, dur2 = probe(tmp)
    med, a_med, frac = verify(tmp, w, h)
    print(f'{name}: frames {count}->{n2} (orig {n_frames}), duration {dur:.2f}->{dur2:.2f}, '
          f'new bg RGB={np.round(med,1)} alpha_med={a_med:.4f} frac<=0.02={frac:.4f}', flush=True)
    assert n2 == n_frames, f'{name}: frame count changed {n_frames} -> {n2}'
    assert abs(dur2 - dur) < 0.05, f'{name}: duration changed {dur} -> {dur2}'
    assert a_med <= 0.02 and frac >= 0.99, f'{name}: repaint did not key (alpha {a_med}, frac {frac})'
    subprocess.run(['mv', tmp, path], check=True)
    print(f'{name}: REPLACED', flush=True)

if __name__ == '__main__':
    names = sys.argv[1:] or ['fast-01', 'fast-07', 'slow-02', 'slow-05', 'slow-06', 'slow-08']
    for name in names:
        repaint(name)
    print('ALLDONE')
