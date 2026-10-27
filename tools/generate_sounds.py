#!/usr/bin/env python3
"""Synthesizes all Idle RPG sound effects (own work, generated from math - no samples, no third-party audio).

License: the generated WAV files and this script are original works of the project author; they carry
no third-party license obligations and may be shipped with the app (see tools/SOUNDS.md).

Usage:  python tools/generate_sounds.py            (writes app/src/main/res/raw/sfx_*.wav)
Pure standard library, deterministic (fixed random seed) - re-running yields identical files.
Format: 16-bit PCM, mono, 22050 Hz (small; SoundPool decodes it without latency).
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")


def n(sec):
    return int(sec * RATE)


def mix(*tracks):
    length = max(len(t) for t in tracks)
    out = [0.0] * length
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def offset(track, sec):
    return [0.0] * n(sec) + track


def env(i, total, attack=0.004, decay=4.0):
    """Fast attack, exponential decay."""
    t = i / RATE
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * math.exp(-decay * i / total)


def tone(freq, sec, decay=5.0, attack=0.004, harmonics=((1, 1.0),), vibrato=0.0, gain=1.0):
    total = n(sec)
    out = []
    phase = [0.0] * len(harmonics)
    for i in range(total):
        f = freq * (1 + vibrato * math.sin(2 * math.pi * 6 * i / RATE))
        s = 0.0
        for k, (mult, amp) in enumerate(harmonics):
            phase[k] += 2 * math.pi * f * mult / RATE
            s += amp * math.sin(phase[k])
        out.append(gain * s * env(i, total, attack, decay))
    return out


def sweep(f0, f1, sec, decay=4.0, attack=0.003, gain=1.0, wobble=0.0):
    total = n(sec)
    out = []
    phase = 0.0
    for i in range(total):
        x = i / total
        f = f0 * (f1 / f0) ** x
        f *= 1 + wobble * math.sin(2 * math.pi * 28 * i / RATE)
        phase += 2 * math.pi * f / RATE
        out.append(gain * math.sin(phase) * env(i, total, attack, decay))
    return out


def noise(sec, decay=8.0, lowpass=0.5, gain=1.0, seed=1):
    rnd = random.Random(seed)
    total = n(sec)
    out = []
    y = 0.0
    for i in range(total):
        y += lowpass * (rnd.uniform(-1, 1) - y)
        out.append(gain * y * env(i, total, 0.001, decay))
    return out


BELL = ((1, 1.0), (2, 0.45), (3.01, 0.22), (4.2, 0.1))


def save(name, samples, peak=0.8):
    m = max(abs(v) for v in samples) or 1.0
    scale = peak / m
    path = os.path.join(OUT, name + ".wav")
    os.makedirs(OUT, exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v * scale)) * 32767)) for v in samples))
    print("wrote", os.path.normpath(path), len(samples) / RATE, "s")


def main():
    save("sfx_hit", mix(noise(0.10, 30, 0.6, 0.9, 11), sweep(220, 70, 0.16, 18, gain=1.0)))
    save("sfx_crit", mix(noise(0.12, 25, 0.8, 0.9, 12), sweep(300, 80, 0.18, 14), tone(1320, 0.30, 9, harmonics=BELL, gain=0.5)))
    save("sfx_hurt", mix(sweep(150, 45, 0.24, 11, gain=1.0), noise(0.14, 22, 0.35, 0.7, 13)))
    save("sfx_monster_die", mix(sweep(420, 70, 0.5, 5, wobble=0.12, gain=0.9), noise(0.45, 7, 0.25, 0.5, 14)))
    save("sfx_loot", mix(*[offset(tone(f, 0.28, 8, harmonics=BELL, gain=0.7), d) for f, d in ((880, 0), (1175, 0.07), (1568, 0.14))]))
    notes = (523.25, 659.25, 783.99, 1046.5)
    save("sfx_levelup", mix(*[offset(tone(f, 0.45, 5, harmonics=((1, 1), (2, 0.35), (3, 0.15)), gain=0.7), 0.11 * k) for k, f in enumerate(notes)],
                            offset(tone(1046.5, 0.5, 4, harmonics=BELL, gain=0.5), 0.45)))
    save("sfx_boss_start", mix(tone(65, 0.9, 2.2, 0.05, ((1, 1), (2, 0.6), (3, 0.3)), vibrato=0.02, gain=1.0),
                               tone(98, 0.9, 2.4, 0.08, ((1, 1), (2, 0.4)), vibrato=0.02, gain=0.6),
                               offset(sweep(90, 300, 0.45, 3.0, 0.05, 0.5), 0.35), offset(noise(0.2, 10, 0.15, 0.5, 15), 0.45)))
    fan = ((392.0, 0), (392.0, 0.14), (392.0, 0.28), (523.25, 0.42), (659.25, 0.66))
    save("sfx_boss_victory", mix(*[offset(tone(f, 0.3 if d < 0.6 else 0.7, 3.5, 0.01, ((1, 1), (2, 0.5), (3, 0.25), (4, 0.1)), gain=0.7), d) for f, d in fan],
                                 offset(tone(1318.5, 0.6, 5, harmonics=BELL, gain=0.4), 0.66)))
    save("sfx_coin", mix(tone(1976, 0.22, 14, 0.001, ((1, 1), (2.76, 0.5), (5.4, 0.25)), gain=0.8),
                         offset(tone(2637, 0.30, 11, 0.001, ((1, 1), (2.76, 0.5), (5.4, 0.25)), gain=0.9), 0.07)))
    save("sfx_click", mix(tone(1500, 0.03, 90, 0.0005, gain=1.0), noise(0.012, 200, 0.9, 0.3, 16)), peak=0.6)


if __name__ == "__main__":
    main()
