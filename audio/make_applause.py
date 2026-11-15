#!/usr/bin/env python3
"""MySing applause v2 — offline synthesis of realistic crowd applause.

Replaces the v1.0.0 applause (metronome-like band-passed noise bursts,
harsh and fake). This model builds the sound the way a real room makes
it: hundreds of individual handclaps, each a short band-passed noise
burst with a fast attack and an exponential decay, laid down by an
inhomogeneous Poisson process whose rate follows a crowd envelope —
a ~0.4 s swell as people join in, a full middle, and a last ~1 s where
the clapping thins out naturally instead of being cut.

Single clap model:
  * white noise -> Butterworth band-pass (per-clap jittered edges
    inside ~900-2500 Hz; a few "front row" claps are brighter)
  * envelope: ~1 ms attack, exponential decay with tau 9-18 ms
    (audible length ~40-80 ms)
  * ~40% of claps get a quiet "flam" tap 8-20 ms off the main hit
    (two palms never land at exactly the same instant)
  * ~12% of events are double claps: a second hit 90-160 ms later
    at 55-80% amplitude

Post: gentle 6.8 kHz low-pass to take the edge off, tanh soft limiting,
RMS normalized to about -15 dBFS (in line with normalized song level),
peak capped at -1 dBFS, and a 120 ms raised-cosine fade at the very
end so the tail is never chopped.

Output: 44.1 kHz, 16-bit, mono WAV, exactly 3.8 s. The app's
celebration timing constant APPLAUSE_MS (MainActivity) is 3800 to
match: with a next song queued the 5 s interlude still covers the
sound with room to spare; with an empty queue the celebration stays
on screen for APPLAUSE_MS + 300 ms, so the tail plays out in full.

Deterministic (fixed seed) so rebuilds are byte-stable.

Usage: python3 audio/make_applause.py
Writes app/src/main/assets/applause.wav and ./applause-v2.wav.
"""

import math
import wave
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt

SAMPLE_RATE = 44100
DURATION_S = 3.8
SEED = 20261115

ROOT = Path(__file__).resolve().parent.parent
ASSET_PATH = ROOT / "app" / "src" / "main" / "assets" / "applause.wav"
PREVIEW_PATH = ROOT / "applause-v2.wav"

rng = np.random.default_rng(SEED)


# ---------------------------------------------------------------- claps

def make_clap_template(bright: bool) -> np.ndarray:
    """One handclap: band-passed noise burst, fast attack, exp decay."""
    length = int(0.12 * SAMPLE_RATE)  # 120 ms canvas, energy dies by ~80 ms
    noise = rng.standard_normal(length)
    low = rng.uniform(850, 1050)
    high = rng.uniform(2900, 3300) if bright else rng.uniform(2300, 2700)
    sos = butter(3, [low, high], btype="bandpass", fs=SAMPLE_RATE, output="sos")
    burst = sosfilt(sos, noise)

    t = np.arange(length) / SAMPLE_RATE
    attack_tau = 0.0008  # ~1 ms attack
    decay_tau = rng.uniform(0.009, 0.018)
    env = (1.0 - np.exp(-t / attack_tau)) * np.exp(-t / decay_tau)

    clap = burst * env
    # Flam: a second, quieter palm tap slightly off the main hit.
    if rng.random() < 0.40:
        offset = int(rng.uniform(0.008, 0.020) * SAMPLE_RATE)
        tap = np.zeros(length)
        tap[offset:] = burst[: length - offset] * env[: length - offset]
        clap = clap + tap * rng.uniform(0.30, 0.50)
    clap /= np.max(np.abs(clap)) + 1e-12
    return clap


TEMPLATES = [make_clap_template(bright=False) for _ in range(18)]
TEMPLATES_BRIGHT = [make_clap_template(bright=True) for _ in range(6)]


# ------------------------------------------------------- crowd envelope

def crowd_rate(t: float) -> float:
    """Expected claps per second at time t (crowd density envelope)."""
    peak = 175.0
    if t < 0.4:  # swell: people join in over the first ~0.4 s
        return peak * (0.34 + 0.66 * (t / 0.4))
    if t < 2.7:  # full middle, with a gentle natural wobble
        return peak * (1.0 + 0.07 * math.sin(2 * math.pi * t / 1.7 + 0.8))
    # last ~1.1 s: the room thins out exponentially, never guillotined
    return peak * math.exp(-(t - 2.7) / 0.5)


def schedule_events() -> list[tuple[float, float, bool]]:
    """Inhomogeneous Poisson process via thinning against the peak rate."""
    r_max = 190.0
    last_start = DURATION_S - 0.22  # leave room for the final tails
    events: list[tuple[float, float, bool]] = []
    t = 0.0
    while True:
        t += rng.exponential(1.0 / r_max)
        if t > last_start:
            break
        if rng.random() > crowd_rate(t) / r_max:
            continue
        bright = rng.random() < 0.06  # a few front-row claps
        gain = float(np.exp(rng.normal(-0.15, 0.38)))  # log-normal variety
        if bright:
            gain *= rng.uniform(1.6, 2.2)
        events.append((t, gain, bright))
        # Double clap: same person hits again a beat later.
        if rng.random() < 0.12:
            t2 = t + rng.uniform(0.09, 0.16)
            if t2 <= last_start:
                events.append((t2, gain * rng.uniform(0.55, 0.80), bright))
    events.sort(key=lambda e: e[0])
    return events


# ----------------------------------------------------------------- mix

def render() -> np.ndarray:
    n = int(DURATION_S * SAMPLE_RATE)
    mix = np.zeros(n, dtype=np.float64)
    events = schedule_events()
    for start_s, gain, bright in events:
        template = rng.choice(TEMPLATES_BRIGHT if bright else TEMPLATES)
        start = int(start_s * SAMPLE_RATE)
        end = min(n, start + len(template))
        mix[start:end] += template[: end - start] * gain
    print(f"clap events scheduled: {len(events)}")

    # Gentle high-frequency softening so nothing feels harsh.
    sos_lp = butter(2, 6800, btype="lowpass", fs=SAMPLE_RATE, output="sos")
    mix = sosfilt(sos_lp, mix)

    # Soft limiting (tanh) for density without harsh peaks.
    peak = np.max(np.abs(mix))
    mix = np.tanh(1.15 * mix / peak) / np.tanh(1.15)

    # Level: RMS ~ -15 dBFS (song-like), peak never above -1 dBFS.
    target_rms = 10 ** (-15.0 / 20.0)
    peak_cap = 10 ** (-1.0 / 20.0)
    rms = float(np.sqrt(np.mean(mix**2)))
    mix *= target_rms / rms
    peak = np.max(np.abs(mix))
    if peak > peak_cap:
        mix *= peak_cap / peak

    # Final 120 ms raised-cosine fade: the tail lands on true zero.
    fade = int(0.120 * SAMPLE_RATE)
    ramp = 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fade))
    mix[-fade:] *= ramp
    # 8 ms safety fade-in against a first-sample click.
    fade_in = int(0.008 * SAMPLE_RATE)
    mix[:fade_in] *= np.linspace(0.0, 1.0, fade_in)
    return mix


def write_wav(path: Path, samples: np.ndarray) -> None:
    pcm = np.clip(samples, -1.0, 1.0)
    pcm16 = (pcm * 32767.0).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(pcm16.tobytes())


def main() -> None:
    mix = render()
    peak_db = 20 * math.log10(float(np.max(np.abs(mix))))
    rms_db = 20 * math.log10(float(np.sqrt(np.mean(mix**2))))
    print(f"duration {len(mix) / SAMPLE_RATE:.3f} s @ {SAMPLE_RATE} Hz mono")
    print(f"peak {peak_db:.2f} dBFS, RMS {rms_db:.2f} dBFS")
    for path in (ASSET_PATH, PREVIEW_PATH):
        write_wav(path, mix)
        print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
