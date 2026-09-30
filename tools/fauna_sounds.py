"""Procedural sound effects for Abyssia's fauna (no recorded or vanilla audio).

The real animals are close to silent, so every sound is a quiet, underwater-coloured cue: bubbles (Minnaert
resonances with a rising pitch), filtered water noise, chitin clicks and jaw snaps, all low-passed and given a
short diffuse tail as if heard through water.  Output: mono 44.1 kHz Ogg Vorbis (mono so the game attenuates it
with distance), encoded with ffmpeg; deterministic per sound name.
"""
from __future__ import annotations

import math
import os
import subprocess
import tempfile
import wave
import zlib

import numpy as np

SR = 44100


def rng(name):
    return np.random.default_rng(zlib.crc32(name.encode()))


# ================================================================ primitives

def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env_exp(dur, attack, decay):
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay)


def biquad(x, kind, f, q=0.707, gain_db=0.0):
    """RBJ cookbook biquad (lowpass, highpass, bandpass, peak)."""
    w0 = 2 * math.pi * f / SR
    cw, sw = math.cos(w0), math.sin(w0)
    alpha = sw / (2 * q)
    A = 10 ** (gain_db / 40)
    if kind == "lowpass":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "highpass":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    elif kind == "bandpass":
        b = [alpha, 0, -alpha]
        a = [1 + alpha, -2 * cw, 1 - alpha]
    else:  # peak
        b = [1 + alpha * A, -2 * cw, 1 - alpha * A]
        a = [1 + alpha / A, -2 * cw, 1 - alpha / A]
    b = [v / a[0] for v in b]
    a1, a2 = a[1] / a[0], a[2] / a[0]
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    b0, b1, b2 = b
    for i, xi in enumerate(x):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1, y2, y1 = x1, xi, y1, yi
    return y


def onepole(x, f):
    """Cheap one-pole low-pass (vectorised with a cumulative trick is not exact; a short loop is fine here)."""
    k = math.exp(-2 * math.pi * f / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - k) * v + k * acc
        y[i] = acc
    return y


def bubble(dur, f0, rise, decay, r):
    """A single bubble: a damped sine whose pitch rises as it forms (Minnaert resonance)."""
    t = t_axis(dur)
    f = f0 * (1 + rise * t / dur)
    phase = 2 * math.pi * np.cumsum(f) / SR
    return np.sin(phase + r.random() * 6.28) * np.exp(-t / decay) * np.clip(t / 0.002, 0, 1)


def place(buf, sig, at):
    i = int(at * SR)
    n = min(len(sig), len(buf) - i)
    if n > 0:
        buf[i:i + n] += sig[:n]
    return buf


def bubbles(dur, count, fmin, fmax, r, level=0.35, spread=None):
    buf = np.zeros(int(SR * dur))
    spread = spread or dur * 0.8
    for _ in range(count):
        f0 = math.exp(r.uniform(math.log(fmin), math.log(fmax)))
        d = r.uniform(0.04, 0.12)
        place(buf, bubble(d, f0, r.uniform(0.3, 1.2), d * 0.35, r) * r.uniform(0.4, 1.0) * level, r.uniform(0, spread))
    return buf


def noise(dur, r):
    return r.standard_normal(int(SR * dur))


def water(dur, r, lo=120, hi=900, level=0.3):
    """Band-limited water rush."""
    x = noise(dur, r)
    x = biquad(biquad(x, "lowpass", hi, 0.7), "highpass", lo, 0.7)
    return x * level / (np.abs(x).max() + 1e-9)


def click(dur, freq, r, q=6.0):
    x = noise(dur, r) * env_exp(dur, 0.0005, dur * 0.18)
    return biquad(x, "bandpass", freq, q)


def tail(x, amount=0.25, length=0.18):
    """Diffuse underwater tail: a few low-passed, spread echoes."""
    out = np.concatenate([x, np.zeros(int(SR * length))])
    for d, g in ((0.023, 0.5), (0.041, 0.35), (0.067, 0.25), (0.097, 0.15)):
        i = int(d * SR)
        out[i:i + len(x)] += x * g * amount
    return onepole(out, 2600)


def finish(x, peak=0.8, fade=0.01):
    x = x - np.mean(x)
    n = int(fade * SR)
    if n and len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    m = np.abs(x).max()
    return x * (peak / m) if m > 0 else x


# ================================================================ sounds

def anglerfish(kind, r):
    if kind == "ambient":          # a slow exhalation through the gills: soft water and a few low bubbles
        x = water(1.1, r, 80, 500, 0.25) * env_exp(1.1, 0.25, 0.35) + bubbles(1.1, 4, 180, 420, r, 0.3)
        return finish(tail(x), 0.45)
    if kind == "hurt":
        thump = np.sin(2 * math.pi * 70 * t_axis(0.3)) * env_exp(0.3, 0.003, 0.06)
        x = thump + water(0.3, r, 100, 1400, 0.4) * env_exp(0.3, 0.002, 0.07) + bubbles(0.3, 6, 300, 900, r, 0.3)
        return finish(tail(x), 0.8)
    if kind == "death":
        t = t_axis(1.4)
        low = np.sin(2 * math.pi * (60 - 20 * t) * t) * env_exp(1.4, 0.01, 0.5)
        x = low * 0.6 + bubbles(1.4, 18, 150, 700, r, 0.35) + water(1.4, r, 80, 700, 0.2) * env_exp(1.4, 0.05, 0.6)
        return finish(tail(x, 0.35), 0.75)
    if kind == "threat":           # the gape: jaw joints creak, water is drawn in
        t = t_axis(0.9)
        creak = biquad(np.sign(np.sin(2 * math.pi * (38 + 12 * t) * t)) * 0.3, "bandpass", 420, 3.0) * env_exp(0.9, 0.1, 0.4)
        rush = water(0.9, r, 90, 700, 0.5) * np.clip(t / 0.5, 0, 1) * env_exp(0.9, 0.4, 0.3)
        return finish(tail(creak + rush + bubbles(0.9, 5, 160, 380, r, 0.2)), 0.7)
    if kind == "snap":             # the strike: suction whoosh, then the jaws clap shut
        whoosh = water(0.35, r, 200, 2600, 0.6) * env_exp(0.35, 0.03, 0.06)
        clap = click(0.08, 1300, r, 3.0) * 1.6
        x = place(np.zeros(int(SR * 0.4)), whoosh, 0.0)
        x = place(x, clap, 0.07)
        return finish(tail(x + bubbles(0.4, 5, 400, 1200, r, 0.25)), 0.9)
    if kind == "flop":
        slap = biquad(noise(0.12, r), "lowpass", 1800) * env_exp(0.12, 0.001, 0.025)
        return finish(slap + np.sin(2 * math.pi * 110 * t_axis(0.12)) * env_exp(0.12, 0.002, 0.03) * 0.5, 0.7)
    raise KeyError(kind)


def giant_isopod(kind, r):
    if kind == "ambient":          # mouthparts and leg joints: a few dry chitin ticks
        x = np.zeros(int(SR * 0.7))
        for _ in range(r.integers(3, 6)):
            place(x, click(0.03, r.uniform(1800, 3200), r, 8.0) * r.uniform(0.4, 1.0), r.uniform(0, 0.6))
        return finish(tail(x, 0.2), 0.35)
    if kind == "step":
        x = click(0.05, r.uniform(1500, 2500), r, 6.0) + click(0.05, r.uniform(700, 1100), r, 4.0) * 0.4
        x = np.concatenate([x, np.zeros(int(SR * 0.05))])
        return finish(onepole(x, 3000), 0.3)
    if kind == "hurt":             # a knock on the calcareous shell
        t = t_axis(0.25)
        knock = sum(np.sin(2 * math.pi * f * t) * math.exp(-i) for i, f in enumerate((520, 1130, 1870))) * env_exp(0.25, 0.001, 0.05)
        return finish(tail(place(knock, click(0.05, 2400, r, 3.0), 0.0)), 0.8)
    if kind == "death":
        x = np.zeros(int(SR * 0.9))
        for i in range(9):
            place(x, click(0.05, r.uniform(900, 2600), r, 5.0) * (1 - i / 10), 0.05 + i * 0.06 + r.uniform(0, 0.03))
        x += bubbles(0.9, 8, 300, 900, r, 0.2)
        return finish(tail(x, 0.3), 0.75)
    if kind == "curl":             # tergites sliding over each other, then locking
        x = np.zeros(int(SR * 0.55))
        for i in range(6):
            place(x, click(0.04, 1400 + i * 180, r, 5.0) * (0.5 + i / 10), i * 0.05 + r.uniform(0, 0.015))
        place(x, click(0.06, 900, r, 3.0) * 1.3, 0.38)
        return finish(tail(x, 0.25), 0.6)
    if kind == "eat":              # tearing and crunching
        x = np.zeros(int(SR * 0.5))
        for _ in range(r.integers(4, 7)):
            place(x, click(0.06, r.uniform(700, 1600), r, 2.0) * r.uniform(0.5, 1.0), r.uniform(0, 0.42))
        x += water(0.5, r, 150, 900, 0.08)
        return finish(tail(x, 0.2), 0.6)
    raise KeyError(kind)


def gulper_eel(kind, r):
    if kind == "ambient":          # the whip tail stirring the water
        t = t_axis(1.2)
        x = water(1.2, r, 60, 380, 0.4) * (0.5 + 0.5 * np.sin(2 * math.pi * 1.6 * t)) * env_exp(1.2, 0.3, 0.5)
        return finish(tail(x), 0.4)
    if kind == "gulp":             # jaws thrown open: a rush of water pulled into the pouch, then a low thump
        t = t_axis(0.8)
        rush = water(0.8, r, 70, 900, 0.8) * np.clip(t / 0.12, 0, 1) * np.exp(-np.maximum(t - 0.12, 0) / 0.18)
        thump = np.sin(2 * math.pi * (55 + 25 * np.exp(-t * 10)) * t) * env_exp(0.8, 0.005, 0.12)
        x = rush + place(np.zeros(len(t)), thump, 0.18) * 0.8 + bubbles(0.8, 6, 200, 600, r, 0.2)
        return finish(tail(x, 0.3), 0.8)
    if kind == "inflate":          # the pouch swelling like a balloon
        t = t_axis(1.0)
        swell = water(1.0, r, 60, 500, 0.6) * np.clip(t / 0.7, 0, 1) ** 2 * env_exp(1.0, 0.7, 0.15)
        tone = np.sin(2 * math.pi * (45 + 35 * t) * t) * np.clip(t / 0.8, 0, 1) * env_exp(1.0, 0.8, 0.12) * 0.4
        return finish(tail(swell + tone), 0.65)
    if kind == "deflate":
        t = t_axis(0.7)
        out = water(0.7, r, 120, 1500, 0.7) * env_exp(0.7, 0.01, 0.2) + bubbles(0.7, 14, 250, 900, r, 0.35)
        return finish(tail(out), 0.7)
    if kind == "hurt":
        x = water(0.3, r, 90, 1200, 0.5) * env_exp(0.3, 0.002, 0.06) + np.sin(2 * math.pi * 90 * t_axis(0.3)) * env_exp(0.3, 0.003, 0.05)
        return finish(tail(x + bubbles(0.3, 5, 300, 800, r, 0.3)), 0.75)
    if kind == "death":
        x = water(1.2, r, 60, 600, 0.4) * env_exp(1.2, 0.02, 0.4) + bubbles(1.2, 14, 150, 600, r, 0.3)
        return finish(tail(x, 0.35), 0.7)
    if kind == "flop":
        return anglerfish("flop", r)
    if kind == "snap":             # thin jaws closing on a shrimp (snipe eel)
        return anglerfish("snap", r)
    raise KeyError(kind)


def shark(kind, r):
    if kind == "ambient":          # a slow sweep of the tail: a deep, soft push of water
        t = t_axis(1.4)
        x = water(1.4, r, 40, 260, 0.5) * (0.4 + 0.6 * np.sin(math.pi * t / 1.4) ** 2) * env_exp(1.4, 0.4, 0.5)
        return finish(tail(x, 0.3), 0.4)
    if kind == "bite":             # jaws thrown forward: a heavy suction rush and a dull clamp
        rush = water(0.45, r, 90, 1400, 0.7) * env_exp(0.45, 0.02, 0.09)
        clamp = np.sin(2 * math.pi * 85 * t_axis(0.2)) * env_exp(0.2, 0.002, 0.04) + click(0.2, 700, r, 2.0) * 0.8
        x = place(np.zeros(int(SR * 0.5)), rush, 0.0)
        x = place(x, clamp, 0.1)
        return finish(tail(x + bubbles(0.5, 4, 200, 600, r, 0.2), 0.3), 0.9)
    if kind == "hurt":
        x = np.sin(2 * math.pi * 55 * t_axis(0.35)) * env_exp(0.35, 0.003, 0.08) + water(0.35, r, 70, 900, 0.5) * env_exp(0.35, 0.002, 0.08)
        return finish(tail(x + bubbles(0.35, 6, 180, 500, r, 0.25)), 0.8)
    if kind == "death":
        t = t_axis(1.6)
        x = np.sin(2 * math.pi * (45 - 15 * t) * t) * env_exp(1.6, 0.01, 0.6) * 0.6 + bubbles(1.6, 16, 120, 500, r, 0.3)
        return finish(tail(x + water(1.6, r, 50, 500, 0.25) * env_exp(1.6, 0.05, 0.7), 0.35), 0.75)
    if kind == "flop":
        return anglerfish("flop", r)
    raise KeyError(kind)


def squid(kind, r):
    if kind == "ambient":          # the mantle pumping water through the funnel, slow and deep
        t = t_axis(1.8)
        pulses = np.clip(np.sin(2 * math.pi * 0.9 * t), 0, 1) ** 3
        x = water(1.8, r, 40, 300, 0.6) * pulses * env_exp(1.8, 0.2, 0.8)
        return finish(tail(x, 0.35), 0.4)
    if kind == "jet":              # a hard jet from the funnel
        t = t_axis(0.9)
        x = water(0.9, r, 60, 1200, 0.9) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.3) + bubbles(0.9, 12, 150, 500, r, 0.3)
        return finish(tail(x, 0.3), 0.85)
    if kind == "ink":              # a muffled puff as the ink cloud is squirted
        x = biquad(noise(0.6, r), "lowpass", 400) * env_exp(0.6, 0.01, 0.15) * 1.5 + bubbles(0.6, 5, 120, 300, r, 0.2)
        return finish(tail(x, 0.4), 0.7)
    if kind == "grab":             # tentacles shooting out, then suckers taking hold: wet pops
        x = place(np.zeros(int(SR * 0.9)), water(0.3, r, 150, 2000, 0.8) * env_exp(0.3, 0.01, 0.06), 0.0)
        for _ in range(r.integers(6, 10)):
            place(x, bubble(0.05, r.uniform(500, 1100), 1.4, 0.012, r) * r.uniform(0.4, 0.9), r.uniform(0.2, 0.75))
        return finish(tail(x, 0.25), 0.85)
    if kind == "hurt":
        x = water(0.4, r, 60, 900, 0.6) * env_exp(0.4, 0.002, 0.1) + bubbles(0.4, 8, 150, 450, r, 0.3)
        return finish(tail(x), 0.8)
    if kind == "death":
        x = water(2.0, r, 40, 500, 0.4) * env_exp(2.0, 0.05, 0.8) + bubbles(2.0, 20, 100, 400, r, 0.3)
        return finish(tail(x, 0.4), 0.75)
    if kind == "flop":             # stranded: the same wet slapping as a fish
        return anglerfish("flop", r)
    raise KeyError(kind)


def shrimp(kind, r):
    if kind == "ambient":          # swimmerets beating: a faint, fast ticking
        x = np.zeros(int(SR * 0.6))
        for i in range(r.integers(5, 9)):
            place(x, click(0.015, r.uniform(3500, 5500), r, 10.0) * r.uniform(0.2, 0.5), i * 0.06 + r.uniform(0, 0.02))
        return finish(tail(x, 0.15), 0.25)
    if kind == "flick":            # the tail flip: a sharp snap and a small rush
        x = click(0.04, r.uniform(2500, 3500), r, 4.0) * 1.2
        x = np.concatenate([x, np.zeros(int(SR * 0.2))]) + water(0.24, r, 400, 3000, 0.3) * env_exp(0.24, 0.005, 0.05)
        return finish(tail(x, 0.15), 0.6)
    if kind == "spew":             # a squirt of glowing secretion
        x = water(0.4, r, 600, 4000, 0.6) * env_exp(0.4, 0.005, 0.08) + bubbles(0.4, 10, 900, 2200, r, 0.3)
        return finish(tail(x, 0.2), 0.55)
    if kind == "hurt":
        x = click(0.05, 2800, r, 3.0) + water(0.2, r, 500, 3000, 0.3)[:int(SR * 0.05)] * 0.5
        return finish(tail(np.concatenate([x, np.zeros(int(SR * 0.1))]), 0.2), 0.6)
    if kind == "death":
        x = np.zeros(int(SR * 0.5))
        for i in range(5):
            place(x, click(0.03, r.uniform(2000, 4000), r, 5.0) * (1 - i / 6), i * 0.07)
        return finish(tail(x + bubbles(0.5, 4, 700, 1500, r, 0.15), 0.25), 0.5)
    raise KeyError(kind)


def crab(kind, r):
    if kind == "snap":             # claws clacked together
        x = np.zeros(int(SR * 0.3))
        place(x, click(0.04, r.uniform(1800, 2400), r, 5.0) * 1.2, 0.0)
        place(x, click(0.04, r.uniform(1500, 2000), r, 5.0), 0.09)
        return finish(tail(x, 0.2), 0.6)
    if kind == "flick":
        return shrimp("flick", r)
    return giant_isopod(kind, r)


def snail(kind, r):
    if kind == "step":             # a slow foot over rock: a faint wet slide
        x = biquad(noise(0.3, r), "bandpass", 900, 1.5) * env_exp(0.3, 0.1, 0.1) * 0.5
        return finish(x, 0.15)
    if kind == "retract":          # the mineral-scaled foot drawn in, the shell settling on rock
        x = biquad(noise(0.4, r), "bandpass", 1400, 2.0) * env_exp(0.4, 0.02, 0.12) * 0.6
        x = place(x, click(0.06, 1100, r, 3.0) * 1.2, 0.3)
        return finish(tail(x, 0.2), 0.6)
    if kind == "hurt":             # a knock on the iron sulfide shell: brighter, more metallic than chitin
        t = t_axis(0.3)
        ring = sum(np.sin(2 * math.pi * f * t) * math.exp(-i * 0.7) for i, f in enumerate((1250, 2900, 4700))) * env_exp(0.3, 0.001, 0.06)
        return finish(tail(ring, 0.2), 0.7)
    if kind == "death":
        x = snail("retract", r) * 0.8
        return finish(tail(place(x, bubbles(0.4, 4, 400, 900, r, 0.15), 0.1), 0.3), 0.6)
    raise KeyError(kind)


def tubeworm(kind, r):
    if kind == "retract":          # plumes whipped into the tubes: a soft rush and a muffled thud
        t = t_axis(0.45)
        x = water(0.45, r, 150, 1500, 0.6) * env_exp(0.45, 0.005, 0.07) + np.sin(2 * math.pi * 120 * t) * env_exp(0.45, 0.01, 0.05) * 0.4
        return finish(tail(x, 0.25), 0.6)
    if kind == "hurt":
        x = click(0.06, 900, r, 2.0) + water(0.06, r, 200, 1200, 0.4) * env_exp(0.06, 0.002, 0.02)
        return finish(tail(np.concatenate([x, np.zeros(int(SR * 0.2))]), 0.25), 0.6)
    if kind == "death":
        x = water(1.0, r, 80, 600, 0.3) * env_exp(1.0, 0.02, 0.4) + bubbles(1.0, 10, 200, 600, r, 0.25)
        return finish(tail(x, 0.35), 0.6)
    raise KeyError(kind)


def cucumber(kind, r):
    if kind == "ambient":          # the veil beating slowly, like a soft wing
        t = t_axis(1.6)
        x = water(1.6, r, 50, 350, 0.5) * np.clip(np.sin(2 * math.pi * 0.7 * t), 0, 1) ** 2 * env_exp(1.6, 0.2, 0.6)
        return finish(tail(x, 0.35), 0.3)
    if kind == "swim":             # lifting off the sediment: a puff of mud and a few strokes
        x = biquad(noise(0.9, r), "lowpass", 500) * env_exp(0.9, 0.01, 0.2) + cucumber("ambient", r)[:int(SR * 0.9)] * 0.8
        return finish(tail(x, 0.3), 0.5)
    if kind == "hurt":             # a soft squish
        x = biquad(noise(0.3, r), "lowpass", 700) * env_exp(0.3, 0.01, 0.07) + bubbles(0.3, 5, 250, 700, r, 0.25)
        return finish(tail(x, 0.2), 0.6)
    if kind == "death":
        x = biquad(noise(1.2, r), "lowpass", 400) * env_exp(1.2, 0.05, 0.4) * 0.7 + bubbles(1.2, 12, 200, 600, r, 0.25)
        return finish(tail(x, 0.35), 0.6)
    raise KeyError(kind)


def jelly(kind, r):
    if kind in ("ambient", "pulse"):  # a bell stroke: a soft low push of water, a slow release
        dur = 1.4 if kind == "ambient" else 0.9
        t = t_axis(dur)
        x = water(dur, r, 40, 260, 0.5) * env_exp(dur, 0.08, 0.35 if kind == "pulse" else 0.6)
        x *= 0.6 + 0.4 * np.clip(np.sin(math.pi * t / dur), 0, 1)
        return finish(tail(x, 0.35), 0.25 if kind == "ambient" else 0.4)
    if kind == "hurt":               # a wet flinch and a few small bubbles
        x = biquad(noise(0.35, r), "lowpass", 600) * env_exp(0.35, 0.01, 0.08) + bubbles(0.35, 4, 300, 800, r, 0.2)
        return finish(tail(x, 0.2), 0.5)
    if kind == "death":              # the bell stops and sags
        x = biquad(noise(1.4, r), "lowpass", 300) * env_exp(1.4, 0.05, 0.5) * 0.6 + bubbles(1.4, 8, 200, 500, r, 0.2)
        return finish(tail(x, 0.35), 0.5)
    raise KeyError(kind)


SYNTHS = {"anglerfish": anglerfish, "giant_isopod": giant_isopod, "gulper_eel": gulper_eel, "shark": shark, "squid": squid, "shrimp": shrimp,
          "crab": crab, "snail": snail, "tubeworm": tubeworm, "cucumber": cucumber, "jelly": jelly}


def write_ogg(samples, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    try:
        with wave.open(tmp, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    finally:
        os.remove(tmp)


def generate(voice, kind, variants, folder, name):
    """Writes <folder>/<kind><n>.ogg for n in 1..variants with the ``voice`` synth (species of one body plan share a
    voice; each species still gets its own takes); returns the sound names for sounds.json."""
    names = []
    for n in range(1, variants + 1):
        sig = SYNTHS[voice](kind, rng(f"{name}/{kind}/{n}"))
        write_ogg(sig, os.path.join(folder, f"{kind}{n}.ogg"))
        names.append(f"abyssia:entity/{name}/{kind}{n}")
    return names
