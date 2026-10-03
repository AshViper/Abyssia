"""Procedural running loops for Abyssia's machines and generators (no recorded or vanilla audio).

Each sound is one seamless loop (LOOP seconds) that the client repeats while the machine works: tones use whole
cycles per loop, noise beds are cross-faded tail-into-head. Everything is low-passed a little, as if heard through
water / a hull. Mono 44.1 kHz Ogg Vorbis (mono so the game attenuates it with distance), deterministic.

    python tools/machine_sounds.py        # writes assets/abyssia/sounds/machine/*.ogg and merges sounds.json

gen_fauna.py rewrites sounds.json from scratch, so it merges entries() back in (keep both in sync by running either).
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fauna_sounds import SR, biquad, bubbles, click, noise, onepole, rng, write_ogg  # noqa: E402

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "abyssia")
LOOP = 4.0
XFADE = 0.5

# event name (machine.<id>) -> (english subtitle, japanese subtitle)
SOUNDS = {
    "crusher": ("Crusher grinds", "粉砕機が砕く"),
    "furnace": ("Furnace roars", "炉が唸る"),
    "separator": ("Separator pumps", "分離機が脈動する"),
    "turbine": ("Turbine whirs", "海流タービンが回る"),
    "geothermal": ("Geothermal generator rumbles", "地熱発電機が唸る"),
    "biofuel": ("Biofuel generator chugs", "バイオ燃料発電機が駆動する"),
}


def t_axis(dur=LOOP):
    return np.arange(int(SR * dur)) / SR


def tone(f, level, harmonics=(1.0,), dur=LOOP):
    """Sum of harmonics of f, each rounded to a whole number of cycles per loop so the loop is seamless."""
    t = t_axis(dur)
    out = np.zeros_like(t)
    for k, g in enumerate(harmonics, start=1):
        fk = round(f * k * dur) / dur
        out += g * np.sin(2 * math.pi * fk * t)
    return out * level


def pulse_env(rate, duty, sharp, dur=LOOP):
    """Periodic 0..1 envelope at a rate rounded to whole cycles per loop."""
    rate = round(rate * dur) / dur
    ph = (t_axis(dur) * rate) % 1.0
    return np.where(ph < duty, np.clip(np.sin(math.pi * ph / duty), 0, 1) ** sharp, 0.0)


def looped(make, r):
    """Builds a LOOP + XFADE signal with make(dur, r) and folds the tail over the head (equal-power cross-fade)."""
    n, m = int(SR * LOOP), int(SR * XFADE)
    x = make(LOOP + XFADE, r)
    out = x[:n].copy()
    w = np.linspace(0, 1, m)
    out[:m] = x[:m] * np.sqrt(w) + x[n:n + m] * np.sqrt(1 - w)
    return out


def band(dur, r, lo, hi):
    x = noise(dur, r)
    x = biquad(biquad(x, "lowpass", hi, 0.7), "highpass", lo, 0.7)
    return x / (np.abs(x).max() + 1e-9)


# ================================================================ voices

def crusher(r):
    hum = tone(48, 0.22, (1.0, 0.5, 0.3, 0.15))
    grind = looped(lambda d, rr: band(d, rr, 300, 2400), r) * (0.25 + 0.75 * pulse_env(3.0, 0.7, 1.5))
    crunch = looped(lambda d, rr: sum_clicks(d, rr, 26, 900, 2600), r)
    return hum + grind * 0.35 + crunch * 0.5


def sum_clicks(dur, r, count, fmin, fmax):
    buf = np.zeros(int(SR * dur))
    for _ in range(count):
        c = click(0.03, r.uniform(fmin, fmax), r, q=4.0) * r.uniform(0.3, 1.0)
        i = int(r.uniform(0, dur - 0.03) * SR)
        buf[i:i + len(c)] += c
    return buf / (np.abs(buf).max() + 1e-9)


def furnace(r):
    hum = tone(60, 0.16, (1.0, 0.4, 0.2))
    roar = looped(lambda d, rr: band(d, rr, 40, 380), r)
    flicker = looped(lambda d, rr: 0.75 + 0.25 * onepole(rr.standard_normal(int(SR * d)), 6) * 40, r)
    crackle = looped(lambda d, rr: sum_clicks(d, rr, 14, 1500, 3500), r)
    return hum + roar * 0.6 * np.clip(flicker, 0.4, 1.2) + crackle * 0.12


def separator(r):
    hum = tone(55, 0.14, (1.0, 0.6, 0.25))
    pump = tone(70, 0.5, (1.0, 0.4)) * pulse_env(1.5, 0.35, 2.0)
    slosh = looped(lambda d, rr: band(d, rr, 150, 900), r) * (0.3 + 0.7 * pulse_env(1.5, 0.6, 1.0))
    bub = looped(lambda d, rr: bubbles(d, int(d * 10), 300, 1100, rr, level=0.6, spread=d - 0.15), r)
    return hum + pump + slosh * 0.25 + bub * 0.35


def turbine(r):
    hum = tone(36, 0.18, (1.0, 0.5, 0.2))
    # blade passing: 3 blades at ~0.75 rev/s
    swish = looped(lambda d, rr: band(d, rr, 120, 700), r) * (0.25 + 0.75 * pulse_env(2.25, 0.8, 2.0))
    whine = tone(180, 0.05, (1.0, 0.3))
    return hum + swish * 0.55 + whine


def geothermal(r):
    rumble = looped(lambda d, rr: band(d, rr, 25, 160), r)
    hiss = looped(lambda d, rr: band(d, rr, 1200, 5000), r)
    boil = looped(lambda d, rr: bubbles(d, int(d * 22), 150, 700, rr, level=0.7, spread=d - 0.15), r)
    hum = tone(42, 0.15, (1.0, 0.5, 0.25))
    return rumble * 0.6 + hiss * 0.06 + boil * 0.45 + hum


def biofuel(r):
    # 4-stroke chug ~6.5 Hz with an exhaust thump each cycle
    thump = tone(32, 0.6, (1.0, 0.6, 0.3, 0.15)) * pulse_env(6.5, 0.45, 1.5)
    hum = tone(65, 0.12, (1.0, 0.5))
    exhaust = looped(lambda d, rr: band(d, rr, 200, 1400), r) * (0.2 + 0.8 * pulse_env(6.5, 0.3, 2.0))
    return thump + hum + exhaust * 0.3


VOICES = {"crusher": crusher, "furnace": furnace, "separator": separator,
          "turbine": turbine, "geothermal": geothermal, "biofuel": biofuel}


def finish_loop(x, peak=0.7):
    x = x - np.mean(x)
    # gentle hull / water low-pass, applied on two concatenated copies so the filter has no start-up seam
    y = onepole(np.concatenate([x, x]), 3200)[len(x):]
    return y * (peak / (np.abs(y).max() + 1e-9))


# ================================================================ output

def entries():
    """sounds.json entries (no synthesis)."""
    return {f"machine.{k}": {"sounds": [{"name": f"abyssia:machine/{k}", "stream": False}],
                             "subtitle": f"subtitles.abyssia.machine.{k}"} for k in SOUNDS}


def lang():
    """(en, ja) subtitle keys."""
    return ({f"subtitles.abyssia.machine.{k}": v[0] for k, v in SOUNDS.items()},
            {f"subtitles.abyssia.machine.{k}": v[1] for k, v in SOUNDS.items()})


def main():
    folder = os.path.join(ASSETS, "sounds", "machine")
    for k in SOUNDS:
        write_ogg(finish_loop(VOICES[k](rng(f"machine/{k}"))), os.path.join(folder, f"{k}.ogg"))
        print(f"  machine/{k}.ogg")
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        table = json.load(f)
    table.update(entries())
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(table, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"  sounds.json: {len(entries())} machine events merged")


if __name__ == "__main__":
    main()
