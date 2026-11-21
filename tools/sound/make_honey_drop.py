#!/usr/bin/env python3
"""v2.1.1 (owner's 21 Nov correction): res/raw/honey_drop.wav, the Honey-Drop bar's tap sound, synthesized here (no
sample from anywhere else): 16-bit PCM, 44.1 kHz, mono, about 0.3 s, peak -3 dBFS.

  1. a resonant water-drop chirp: a sine gliding up 520 Hz -> 1180 Hz (the "plink" of a drop's air bubble), 1.5 ms
     attack, ~35 ms decay;
  2. a warm viscous honey body: 180 Hz -> 95 Hz with a soft second harmonic, gently saturated, ~70 ms decay;
  3. a soft shimmering wave-ripple tail of 140 ms: a few high partials with a slow tremolo over band-limited noise.

Usage: make_honey_drop.py <out.wav>
"""
import sys
import wave

import numpy as np

RATE = 44100


def glide(f0, f1, glide_s, n):
    t = np.arange(n) / RATE
    f = np.where(t < glide_s, f0 * (f1 / f0) ** (t / glide_s), f1)
    return 2 * np.pi * np.cumsum(f) / RATE


def envelope(n, attack_s, decay_s):
    t = np.arange(n) / RATE
    a = np.clip(t / attack_s, 0, 1)
    a = a * a * (3 - 2 * a)
    return a * np.exp(-np.maximum(t - attack_s, 0) / decay_s)


def main(out):
    rng = np.random.default_rng(208)
    total = int(0.31 * RATE)
    mix = np.zeros(total)

    # 1. water-drop chirp
    n = int(0.12 * RATE)
    ph = glide(520.0, 1180.0, 0.05, n)
    chirp = (np.sin(ph) + 0.08 * np.sin(2 * ph)) * envelope(n, 0.0015, 0.035)
    mix[:n] += 0.55 * chirp

    # 2. honey body (starts a breath later, under the chirp)
    start = int(0.006 * RATE)
    n = int(0.22 * RATE)
    ph = glide(180.0, 95.0, 0.12, n)
    body = np.tanh(1.6 * (np.sin(ph) + 0.18 * np.sin(2 * ph))) / np.tanh(1.6)
    mix[start:start + n] += 0.5 * body * envelope(n, 0.006, 0.07)

    # 3. wave-ripple tail: 140 ms from 60 ms
    start = int(0.06 * RATE)
    n = int(0.14 * RATE)
    t = np.arange(n) / RATE
    shimmer = np.zeros(n)
    for f, a in ((2300, 1.0), (3150, 0.8), (4200, 0.6), (5600, 0.4)):
        shimmer += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    tremolo = 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * 28 * t)) * (0.6 + 0.4 * np.sin(2 * np.pi * 41 * t))
    noise = rng.standard_normal(n)
    # a resonant two-pole band-pass around 3 kHz
    w0 = 2 * np.pi * 3000 / RATE
    r = 0.97
    y1 = y2 = 0.0
    band = np.zeros(n)
    for i in range(n):
        y = noise[i] + 2 * r * np.cos(w0) * y1 - r * r * y2
        band[i] = y
        y2, y1 = y1, y
    band /= np.max(np.abs(band))
    fade = np.clip(t / 0.015, 0, 1) * np.exp(-t / 0.06) * (0.5 + 0.5 * np.cos(np.pi * t / t[-1]))
    tail = (shimmer / 2.8 * tremolo + 0.5 * band) * fade
    mix[start:start + n] += 0.13 * tail

    # the last 8 ms fade to silence; peak at -3 dBFS
    end = int(0.008 * RATE)
    mix[-end:] *= np.linspace(1, 0, end)
    mix[0] = 0.0
    mix *= 0.708 / np.max(np.abs(mix))
    pcm = np.round(mix * 32767).astype("<i2")
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print(out, len(pcm), "samples", round(len(pcm) / RATE, 3), "s")


if __name__ == "__main__":
    main(sys.argv[1])
