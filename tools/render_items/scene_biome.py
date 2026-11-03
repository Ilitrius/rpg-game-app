"""Parametric first-person backdrops of the 19 biomes of the generated zones (zones 6..100).

Same camera model as scene_meadow.py (perspective ground plane, horizon at 44 % of the height, the path
through the picture centre where monsters stand). Per biome: sky + orb (+ stars/clouds), three far layers
(shape family from BiomeStyle.far), ground material (BiomeStyle.ground), fog and glow colours. The five
stages of a biome share the image; the app darkens/fogs it a little more per stage.

Usage:  python scene_biome.py <biome|all> [--width 1080] [--height 1800] [--out DIR] [--quality 84]
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from scene_meadow import fbm2, hash2, smooth, vnoise2  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
HORIZON = 0.44


def hexc(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=np.float32) / 255.0


# name: (skyTop, skyHorizon, farNear, farFar, groundTop, groundBottom, glow, fog, far, ground, orb, stars, clouds)
# stars: density 0..1; clouds: (amount 0..1, colour hex or None)
BIOMES = {
    "heide": (0x2B2740, 0x8A6A7A, 0x3A2F44, 0x6A5468, 0x3C4A34, 0x151D14, 0xB36BFF, 0x6A5578, "crags", "grass", "moon", 0.3, (0.5, 0x5A4560)),
    "wueste": (0x3A2A1A, 0xF0B060, 0x8A5A30, 0xC48A4C, 0xD0A060, 0x7A5430, 0xFFC247, 0xD9A066, "dunes", "sand", "sun", 0.0, (0.15, 0xF5D8A8)),
    "vulkan": (0x1A0A08, 0xC2410C, 0x2A1410, 0x5A2418, 0x35221A, 0x0E0705, 0xFF5A14, 0x6A2410, "peaks", "lava", "red_sun", 0.0, (0.7, 0x3A1810)),
    "gruft": (0x0A0F14, 0x2C4A4A, 0x14201F, 0x2A3C3A, 0x2E3432, 0x0E1110, 0x5FFFB0, 0x2A4640, "columns", "tiles", "moon", 0.35, (0.3, 0x1E3A38)),
    "frost": (0x0B1A2E, 0xBFE3F5, 0x6C93AD, 0xA8CBE0, 0xDDEFF8, 0x6C93AD, 0x7FD9FF, 0xBFDDEE, "peaks", "ice", "moon", 0.25, (0.45, 0xDDEEFA)),
    "dschungel": (0x0C2A14, 0x6BBF59, 0x123A1C, 0x2A6A30, 0x1E5A2A, 0x0A2410, 0xC6FF3B, 0x3F7A3A, "trees", "grass", "sun", 0.0, (0.55, 0x9CD08A)),
    "ruinen": (0x1E2630, 0x7C8CA0, 0x2C3440, 0x59667A, 0x5A5F58, 0x23261F, 0xE8C46A, 0x66707E, "ruins", "tiles", "sun", 0.0, (0.55, 0x9AA6B8)),
    "glutpilz": (0x0B0820, 0x3A1F5C, 0x2A1448, 0x5A2C80, 0x2A2148, 0x0C0818, 0x3DFFC4, 0x3A2468, "tendrils", "rock", "none", 0.5, (0.25, 0x2A1A50)),
    "kueste": (0x0E1C2B, 0x6C8AA3, 0x1C3446, 0x3C5C72, 0x1F4D63, 0x0A1C28, 0x7FE6FF, 0x5C7C90, "waves", "water", "none", 0.0, (0.7, 0x7A98B0)),
    "knochen": (0x2A1E14, 0xC9B48A, 0x6A5A40, 0x9A8660, 0xB7A98A, 0x5C513C, 0xE8DDBA, 0xB8A682, "crags", "bones", "eclipse", 0.0, (0.35, 0xD8C8A0)),
    "blutgarten": (0x1A0508, 0x7A1426, 0x3A0C14, 0x62172A, 0x5A1A24, 0x1E070B, 0xFF2A4A, 0x6A1A2A, "tendrils", "flesh", "red_sun", 0.0, (0.6, 0x5A1424)),
    "himmel": (0x10204A, 0x9FC8FF, 0x5A78B0, 0x93B0DC, 0xC7D2E8, 0x5B6C94, 0xFFF27A, 0xB8CCF0, "islands", "tiles", "sun", 0.0, (0.55, 0xF4F8FF)),
    "unterwelt": (0x1C0402, 0xB3180A, 0x3A0A06, 0x6A1008, 0x4A1812, 0x120403, 0xFF3B0A, 0x7A1A0E, "spires", "lava", "eclipse", 0.0, (0.6, 0x4A0E08)),
    "schatten": (0x050509, 0x2A1F44, 0x120E20, 0x241B3A, 0x15121E, 0x050409, 0x9D6BFF, 0x2A2046, "spires", "void", "eclipse", 0.5, (0.3, 0x201838)),
    "uhrwerk": (0x1A1410, 0x8A6A3A, 0x2E2414, 0x5A4424, 0x6B5328, 0x2A200E, 0xFFB347, 0x6A5430, "gears", "tiles", "none", 0.0, (0.3, 0x6A5430)),
    "traum": (0x2A0F3A, 0xFF7AC8, 0x4A1A66, 0x8A3A9A, 0x4A2A66, 0x140A24, 0xFF6AD5, 0xA05AA8, "tendrils", "void", "moon", 0.4, (0.55, 0xFFB0E0)),
    "astral": (0x02030F, 0x1B2A66, 0x0E1640, 0x243478, 0x1A2250, 0x04061A, 0x6AA8FF, 0x1E2C6A, "islands", "void", "planet", 0.9, (0.0, None)),
    "leere": (0x000000, 0x3A0A4A, 0x14001E, 0x2A0A3A, 0x1A0828, 0x040008, 0xFF2BD6, 0x2A0A3A, "tendrils", "void", "rift", 0.6, (0.0, None)),
    "ende": (0x050003, 0xFFB23A, 0x2A1608, 0x6A3A10, 0x3A2A10, 0x0A0604, 0xFFD23A, 0x7A4A18, "towers", "tiles", "eclipse", 0.1, (0.3, 0x5A3010)),
}

# handmade zones 2-5 (zone scenes use their own horizon heights)
BIOMES.update({
    "wald": (0x1B3B28, 0xA8C888, 0x16301C, 0x3E6A42, 0x2E4A24, 0x0F1C0C, 0xF0F0A8, 0x6C8A66, "trees", "grass", "sun", 0.0, (0.30, 0x9ABF98)),
    "sumpf": (0x1E2A1C, 0x7A8A5A, 0x1A2214, 0x3E4A2C, 0x3A4228, 0x141808, 0xC8F060, 0x5E6A44, "tendrils", "water", "moon", 0.1, (0.60, 0x4A5A38)),
    "gebirge": (0x2A4A7A, 0xC8DCF0, 0x6A7E96, 0xA6B8CC, 0xB8BCC0, 0x5A5E64, 0xFFF0D0, 0xB8C8DC, "peaks", "rock", "sun", 0.0, (0.50, 0xFFFFFF)),
    "hoehle": (0x0C0A10, 0x2A2030, 0x14101C, 0x2A2236, 0x3A3240, 0x0C0A10, 0x8A6AFF, 0x2A2236, "spires", "rock", "none", 0.0, (0.0, None)),
})
HORIZON_OF = {"wald": 0.42, "gebirge": 0.45}
TREE_BIOMES = {"dschungel"}


def mixc(a, b, t):
    return a + (b - a) * t[..., None]


# --------------------------------------------------------------------------- 1D profiles (0..1) over x in 0..1

def fbm1(x, seed, octaves=4):
    y = np.zeros_like(x) + seed * 3.7
    return fbm2(x, y, octaves, seed)


def profile(kind, x, seed):
    if kind == "dunes":
        return 0.5 + 0.28 * np.sin(x * 2 * np.pi * 1.3 + seed) + 0.14 * np.sin(x * 2 * np.pi * 3.1 + 2 * seed) + 0.2 * (fbm1(x * 3, seed) - 0.5)
    if kind == "peaks":
        n = fbm1(x * 3.0, seed, 5)
        return (1.0 - np.abs(2 * n - 1)) ** 1.6
    if kind == "crags":
        n = fbm1(x * 2.5, seed, 4)
        return np.floor(n * 7) / 7 + 0.04 * fbm1(x * 40, seed + 5, 2)
    if kind == "trees":
        return 0.50 * fbm1(x * 2.2, seed, 3) + 0.30 * vnoise2(x * 70, x * 0 + seed, seed) + 0.12 * vnoise2(x * 240, x * 0 + seed, seed + 3) + 0.15
    if kind == "spires":
        h = 0.18 * fbm1(x * 3, seed, 3)
        rng = np.random.default_rng(seed)
        for _ in range(16):
            c, w, hg = rng.uniform(0, 1), rng.uniform(0.008, 0.03), rng.uniform(0.4, 1.0)
            h = np.maximum(h, np.clip(1 - np.abs(x - c) / w, 0, 1) ** 1.2 * hg)
        return h
    if kind == "ruins":
        cell = np.floor(x * 26)
        hh = hash2(cell, cell * 0 + seed, seed + 1)
        broken = vnoise2(x * 80, x * 0 + seed, seed + 2) * 0.25
        return np.where(hh > 0.45, 0.25 + 0.65 * hash2(cell, cell * 0 + 7, seed + 3) - broken, 0.10 + 0.1 * hh)
    if kind == "columns":
        cell = (x * 22) % 1.0
        idx = np.floor(x * 22)
        tall = hash2(idx, idx * 0 + seed, seed + 1) > 0.25
        return np.where((cell > 0.18) & (cell < 0.82) & tall, 0.45 + 0.5 * hash2(idx, idx * 0 + 3, seed + 2), 0.05)
    if kind == "towers":
        h = 0.06 + 0.04 * fbm1(x * 5, seed, 3)
        rng = np.random.default_rng(seed)
        for _ in range(7):
            c, w, hg = rng.uniform(0.03, 0.97), rng.uniform(0.02, 0.05), rng.uniform(0.5, 1.0)
            body = (np.abs(x - c) < w) * hg
            tip = np.clip(1 - np.abs(x - c) / (w * 1.4), 0, 1) * 0.25 + hg
            h = np.maximum(h, np.where(np.abs(x - c) < w * 1.4, np.where(np.abs(x - c) < w, hg, tip * (np.abs(x - c) < w * 1.4)), 0) * (1 if True else 0))
            h = np.maximum(h, body)
        return h
    return 0.05 + 0.03 * np.sin(x * 40 + seed)


AMP = {"dunes": 0.07, "peaks": 0.20, "crags": 0.16, "trees": 0.10, "spires": 0.24, "ruins": 0.14, "columns": 0.22,
       "towers": 0.30, "waves": 0.012}
AMP["trees"] = 0.17


# --------------------------------------------------------------------------- scene

def render(name, W, H, seed=11):
    (sky_top, sky_hor, far_n, far_f, g_top, g_bot, glow, fog, far_kind, ground_kind, orb, stars, clouds) = BIOMES[name]
    sky_top, sky_hor, far_n, far_f, g_top, g_bot, glow, fog = (hexc(c) for c in (sky_top, sky_hor, far_n, far_f, g_top, g_bot, glow, fog))
    seed = (sum(map(ord, name)) * 31) % 997
    yh = HORIZON_OF.get(name, HORIZON) * H
    f = 1.10 * W
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    nx = xs / W
    img = np.zeros((H, W, 3), dtype=np.float32)

    # ------------------------------------------------------------ sky
    t = np.clip(ys / yh, 0.0, 1.0)
    sky = sky_top + (sky_hor - sky_top) * (t ** 1.5)[..., None]
    sky = sky + glow * (np.exp(-((yh - ys) / (0.10 * H)) ** 2) * 0.18 * (ys < yh))[..., None]
    ox, oy = 0.70 * W, 0.17 * H
    rad = 0.075 * W
    dd = np.hypot(xs - ox, ys - oy)
    ocol = {"sun": np.array([1.0, 0.95, 0.80]), "red_sun": np.array([1.0, 0.42, 0.18]), "moon": np.array([0.88, 0.92, 1.0]),
            "eclipse": glow, "planet": np.array([0.7, 0.8, 1.0]), "rift": glow, "none": glow}[orb].astype(np.float32)
    if orb in ("sun", "red_sun"):
        sky = sky + (np.exp(-dd / (rad * 3.5)) * 0.55)[..., None] * ocol
        sky = np.where((dd < rad)[..., None], mixc(np.ones_like(sky) * ocol, np.ones_like(sky), smooth(rad * 0.4, rad, dd) * 0 + 0.35), sky)
        sky = sky + (np.exp(-((dd - rad) / (rad * 0.25)) ** 2) * 0.35)[..., None] * ocol
    elif orb == "moon":
        sky = sky + (np.exp(-dd / (rad * 3.0)) * 0.30)[..., None] * ocol
        crat = 0.80 + 0.28 * fbm2(xs / rad * 1.6, ys / rad * 1.6, 4, seed + 4)
        lit = np.clip(0.75 + 0.35 * (-(xs - ox) / rad), 0.4, 1.1)
        sky = np.where((dd < rad)[..., None], (ocol * (crat * lit)[..., None]), sky)
    elif orb == "eclipse":
        corona = np.exp(-((dd - rad * 1.08) / (rad * 0.20)) ** 2) * 1.1 + np.exp(-dd / (rad * 2.6)) * 0.30
        sky = sky + corona[..., None] * glow
        sky = np.where((dd < rad)[..., None], np.array([0.01, 0.01, 0.015], dtype=np.float32), sky)
    elif orb == "planet":
        pr = 0.15 * W
        pcx, pcy = 0.28 * W, 0.20 * H
        pd = np.hypot(xs - pcx, ys - pcy)
        band = 0.55 + 0.30 * np.sin((ys - pcy) / pr * 9 + 2 * fbm2(xs / pr * 2, ys / pr * 2, 3, seed))
        shade = np.clip(0.2 + 1.0 * ((pcx - xs) * 0.6 + (pcy - ys) * 0.8) / pr * 0.5 + 0.55, 0.05, 1.2)
        pcol = (np.array([0.35, 0.45, 0.9], dtype=np.float32) * band[..., None]) * shade[..., None]
        sky = sky + (np.exp(-pd / (pr * 1.6)) * 0.25)[..., None] * glow
        sky = np.where((pd < pr)[..., None], pcol + 0.12 * glow, sky)
    elif orb == "rift":
        cx = 0.5 * W + 40 * np.sin(ys / H * 18 + seed) + 22 * np.sin(ys / H * 47)
        w = 10 + 18 * np.clip((yh - ys) / yh, 0, 1) * 0.0 + 8 * np.sin(ys / H * 9) ** 2
        rift = np.exp(-((xs - cx) / np.maximum(w, 3)) ** 2) * (ys < yh)
        sky = sky + (np.exp(-np.abs(xs - cx) / 110.0) * 0.28 * (ys < yh))[..., None] * glow
        sky = sky + rift[..., None] * (glow * 1.6 + 0.5)
    if stars > 0:
        sr = hash2(np.floor(xs / 3.0), np.floor(ys / 3.0), seed + 21)
        star = (sr > 1.0 - 0.018 * stars) & (ys < yh * 0.97)
        bright = hash2(np.floor(xs / 3.0), np.floor(ys / 3.0), seed + 22)
        sky = np.where(star[..., None], np.maximum(sky, (0.5 + 0.5 * bright)[..., None] * np.array([1.0, 0.97, 0.9], dtype=np.float32) * (1 - 0.6 * t)[..., None]), sky)
    amount, ccol = clouds
    if amount > 0 and ccol is not None:
        ccol = hexc(ccol)
        dy = np.maximum(yh - ys, 1.0)
        u, v = (xs - W / 2) / dy * 0.9, f / dy * 0.9 + 3.0
        dens = smooth(0.62 - 0.17 * amount, 0.85 - 0.12 * amount, fbm2(u * 0.55, v * 0.55, 6, seed + 30))
        dl = smooth(0.62 - 0.17 * amount, 0.85 - 0.12 * amount, fbm2(u * 0.55 + 0.12, v * 0.55 + 0.05, 6, seed + 30))
        light = np.clip(0.55 + (dens - dl) * 3.5, 0.25, 1.15)
        cc = ccol * light[..., None] + (1 - light)[..., None] * sky_top * 0.7
        ca = np.clip(dens, 0, 1) * smooth(0.0, 0.10, (yh - ys) / H) * 0.9 * (ys < yh)
        sky = sky * (1 - ca[..., None]) + cc * ca[..., None]
    img[:] = sky

    if name == "hoehle":
        xr = nx[0]
        ceil = (0.05 + 0.20 * profile("spires", xr * 1.3, seed + 9) * 0.9 + 0.05 * fbm1(xr * 4, seed + 3, 3)) * H
        cmask = ys <= ceil[None, :]
        rock = np.array([0.10, 0.085, 0.12], dtype=np.float32) * (0.5 + 0.9 * fbm2(xs / W * 40, ys / H * 28, 4, seed + 41))[..., None]
        rim = cmask & ~np.roll(cmask, -4, axis=0)
        rock = rock + rim[..., None] * glow * 0.35
        sky = np.where(cmask[..., None], rock, sky)
        img[:] = sky
    # ------------------------------------------------------------ far layers
    haze_c = fog
    layers = [(0, 0.060, 0.62), (1, 0.034, 0.40), (2, 0.012, 0.20)]
    for (li, base, hz) in layers:
        col_l = mixc(np.broadcast_to(far_f, (H, W, 3)).copy(), np.broadcast_to(far_n, (H, W, 3)).copy(), np.full((H, W), li / 2.0, dtype=np.float32))
        mask = np.zeros((H, W), dtype=bool)
        sd = seed + li * 17
        x1 = nx[0]
        if far_kind in ("dunes", "peaks", "crags", "trees", "spires", "ruins", "columns", "towers", "waves"):
            amp = AMP[far_kind] * (0.7 + 0.25 * li)
            top = yh - (base + amp * profile(far_kind, x1 * (1.0 + 0.4 * li), sd)) * H
            smooth_top = top
            mask = ys >= top[None, :]
            depth = np.clip((ys - smooth_top[None, :]) / (0.12 * H), 0, 1)
        elif far_kind == "islands":
            depth = np.zeros((H, W), dtype=np.float32)
            rng = np.random.default_rng(sd)
            for k in range(4 + li):
                cx, cy = rng.uniform(0.05, 0.95) * W, yh - rng.uniform(0.05, 0.30) * H * (1 - 0.2 * li)
                rx = rng.uniform(0.07, 0.16) * W * (1.0 - 0.15 * li)
                under = rng.uniform(0.04, 0.09) * H
                top_cap = ((xs - cx) / rx) ** 2 + ((ys - cy) / (0.022 * H)) ** 2 < 1.0
                cone = (ys >= cy) & (ys <= cy + under) & (np.abs(xs - cx) < rx * (1 - (ys - cy) / under) ** 0.7)
                m = top_cap | cone
                mask |= m
                depth = np.where(m, np.clip((ys - cy) / under, 0, 1), depth)
        elif far_kind == "gears":
            depth = np.zeros((H, W), dtype=np.float32)
            rng = np.random.default_rng(sd)
            for k in range(4 + li * 2):
                cx, cy = rng.uniform(0.0, 1.0) * W, yh - rng.uniform(0.0, 0.10) * H
                R = rng.uniform(0.10, 0.20) * W * (1 - 0.12 * li)
                teeth = int(rng.integers(10, 18))
                ang = np.arctan2(ys - cy, xs - cx)
                rr = np.hypot(xs - cx, ys - cy)
                outer = R * (1.0 + 0.09 * np.sign(np.cos(teeth * ang)))
                m = (rr < outer) & (rr > R * 0.28) & (ys <= yh + 4)
                spokes = (np.abs(np.sin(ang * 3)) < 0.18) & (rr > R * 0.28) & (rr < R * 0.82)
                m = m & ((rr > R * 0.80) | spokes | (rr < R * 0.40))
                mask |= m
                depth = np.where(m, np.clip(rr / R * 0.6, 0, 1), depth)
        elif far_kind == "tendrils":
            depth = np.zeros((H, W), dtype=np.float32)
            rng = np.random.default_rng(sd)
            for k in range(9 + 2 * li):
                x0 = rng.uniform(0, 1) * W
                hgt = rng.uniform(0.15, 0.42) * H
                A, fr, ph = rng.uniform(0.01, 0.05) * W, rng.uniform(4, 11) / H, rng.uniform(0, 6.28)
                w0 = rng.uniform(0.008, 0.02) * W
                yy = yh - ys
                cx = x0 + A * np.sin(fr * yy * 6.0 + ph)
                width = w0 * np.clip(1 - yy / hgt, 0, 1) ** 0.6 + 1.5
                m = (yy > -4) & (yy < hgt) & (np.abs(xs - cx) < width)
                mask |= m
                depth = np.where(m, np.clip(yy / hgt, 0, 1), depth)
        else:
            depth = np.zeros((H, W), dtype=np.float32)
        mask &= ys < yh + 8
        if far_kind == "trees":
            fa = fbm2(xs / W * 160.0, ys / H * 110.0, 4, sd + 20)
            col_l = col_l * (0.62 + 0.75 * fa)[..., None]
        else:
            fa = fbm2(xs / W * 60.0, ys / H * 40.0, 4, sd + 20)
            col_l = col_l * (0.72 + 0.60 * fa)[..., None]
        # light: rim from the orb side + vertical shading
        edge = mask & ~np.roll(mask, 4, axis=0)
        edge_x = mask & ~np.roll(mask, -3 if ox > W / 2 else 3, axis=1)
        col_l = col_l * (0.75 + 0.45 * (1 - depth))[..., None]
        col_l = col_l + (edge * 0.45 + edge_x * 0.35)[..., None] * glow * (0.8 - 0.2 * li)
        col_l = col_l * (1 - hz) + haze_c * hz
        img = np.where(mask[..., None], col_l, img)

    # ------------------------------------------------------------ ground
    gy = ys - yh
    ground = gy > 0
    gyc = np.maximum(gy, 1.0)
    Z = f / gyc
    X = (xs - W / 2) * Z / f
    fw = Z / f * 1.2
    near = np.clip(gy / (H - yh), 0, 1)
    base_g = mixc(np.broadcast_to(g_top, (H, W, 3)).copy(), np.broadcast_to(g_bot, (H, W, 3)).copy(), np.clip(near ** 0.7, 0, 1))
    n1 = fbm2(X * 0.5, Z * 0.5, 5, seed + 40, fw)
    n2 = fbm2(X * 2.6, Z * 2.6, 5, seed + 50, fw)
    var = (0.62 + 0.8 * n1) * (0.85 + 0.3 * n2)
    g = base_g * var[..., None]
    emissive = np.zeros((H, W), dtype=np.float32)
    path_strength = {"grass": 1.0, "sand": 0.9, "rock": 0.9, "bones": 1.0, "tiles": 0.5, "flesh": 0.8, "lava": 0.55, "ice": 0.35, "water": 0.0, "void": 0.0}[ground_kind]
    if ground_kind == "grass":
        blade = vnoise2(xs * 0.32, ys * 0.035, seed + 70) * 0.6 + vnoise2(xs * 0.9, ys * 0.12, seed + 71) * 0.4
        g = g * (1.0 + ((blade - 0.5) * (0.55 * near ** 1.2) * 1.6)[..., None])
        g = g + (smooth(0.62, 0.9, vnoise2(xs * 0.6, ys * 0.06, seed + 72)) * near ** 1.5 * 0.10)[..., None] * glow
    elif ground_kind == "sand":
        rip = np.sin(X * 5.5 + 3 * fbm2(X * 0.6, Z * 0.6, 3, seed + 60, fw)) * 0.5 + 0.5
        g = g * (0.80 + 0.35 * rip * (1 - np.clip(fw * 6, 0, 1)) + 0.1)[..., None]
        g = g + (smooth(0.82, 0.97, vnoise2(X * 40, Z * 40, seed + 61)) * (1 - np.clip(fw * 20, 0, 1)) * 0.25)[..., None]
    elif ground_kind == "rock":
        cells = fbm2(X * 1.2, Z * 1.2, 4, seed + 62, fw)
        crack = np.clip(1.0 - np.abs(cells - 0.5) * 10.0, 0, 1)
        g = g * (1 - 0.5 * crack)[..., None]
        spot = smooth(0.80, 0.93, vnoise2(X * 8.0, Z * 8.0, seed + 63)) * (1 - np.clip(fw * 8, 0, 1))
        emissive += spot * 0.9
    elif ground_kind == "bones":
        sc = hash2(np.floor(X / 0.5), np.floor(Z / 0.5), seed + 64)
        bx, bz = (np.floor(X / 0.5) + 0.2 + 0.6 * hash2(np.floor(X / 0.5), np.floor(Z / 0.5), seed + 65)) * 0.5, (np.floor(Z / 0.5) + 0.5) * 0.5
        bone = (sc > 0.80) & (np.abs(X - bx) < 0.20) & (np.abs(Z - bz) < 0.03) & (Z < 12)
        g = np.where(bone[..., None], np.array([0.86, 0.82, 0.70], dtype=np.float32) * (0.8 + 0.4 * n2)[..., None], g)
        skull = (sc > 0.93) & (np.hypot(X - bx, Z - bz) < 0.10) & (Z < 10)
        g = np.where(skull[..., None], np.array([0.90, 0.86, 0.74], dtype=np.float32), g)
    elif ground_kind == "flesh":
        veins = np.clip(1.0 - np.abs(fbm2(X * 1.0, Z * 1.0, 4, seed + 66, fw) - 0.5) * 9.0, 0, 1)
        g = g * (1 - 0.45 * veins)[..., None] + (veins * 0.30)[..., None] * glow * (1 - np.clip(fw * 6, 0, 1))[..., None]
        g = g * (0.8 + 0.5 * smooth(0.5, 0.8, n2))[..., None]
    elif ground_kind == "tiles":
        tw, td = 1.1, 0.8
        ix, iz = np.floor(X / tw), np.floor(Z / td)
        fxp, fzp = X / tw - ix, Z / td - iz
        joint = (np.minimum(fxp, 1 - fxp) < 0.035) | (np.minimum(fzp, 1 - fzp) < 0.05)
        tone = 0.78 + 0.4 * hash2(ix, iz, seed + 67)
        crack = np.clip(1.0 - np.abs(fbm2(X * 2.2, Z * 2.2, 4, seed + 68, fw) - 0.5) * 12.0, 0, 1) * 0.5
        g = g * (tone * (1 - crack))[..., None]
        g = np.where((joint & (Z < 24))[..., None], g * 0.35, g)
    elif ground_kind == "lava":
        cells = fbm2(X * 0.9, Z * 0.9, 4, seed + 69, fw)
        veins = np.clip(1.0 - np.abs(cells - 0.5) * 7.5, 0, 1) ** 1.6
        veins2 = np.clip(1.0 - np.abs(fbm2(X * 2.4 + 3, Z * 2.4, 4, seed + 70, fw) - 0.5) * 11.0, 0, 1) ** 1.8
        lava = np.clip(veins + 0.6 * veins2, 0, 1) * (1 - 0.0)
        emissive += lava * 1.3
        g = g * (1 - 0.35 * lava)[..., None]
    elif ground_kind == "ice":
        crack = np.clip(1.0 - np.abs(fbm2(X * 0.8, Z * 0.8, 5, seed + 71, fw) - 0.5) * 10.0, 0, 1)
        g = g * (0.85 + 0.25 * n2)[..., None] + (crack * 0.25)[..., None] * np.array([0.7, 0.9, 1.0], dtype=np.float32)
        refl = np.exp(-gy / (0.10 * H)) * 0.22
        g = g + refl[..., None] * sky_hor * (0.6 + 0.4 * n1)[..., None]
    elif ground_kind == "water":
        wave = fbm2(X * 0.9 + 3 * fbm2(Z * 0.4, X * 0.0, 2, seed + 72), Z * 0.45, 5, seed + 73, fw)
        sx = (fbm2(X * 0.9 + 0.05, Z * 0.45, 4, seed + 73, fw) - wave) * 40.0
        sz = (fbm2(X * 0.9, Z * 0.45 + 0.05, 4, seed + 73, fw) - wave) * 40.0
        fres = np.clip(0.30 + 0.55 * np.exp(-near * 3.0) + 0.25 * sz, 0.05, 0.95)
        reflc = mixc(np.broadcast_to(sky_hor, (H, W, 3)).copy(), np.broadcast_to(sky_top, (H, W, 3)).copy(), np.clip(near * 1.2 + 0.2 * sx, 0, 1))
        g = g * (0.55 + 0.7 * wave)[..., None] * (1 - fres)[..., None] + reflc * fres[..., None]
        g = g + (smooth(0.62, 0.78, wave) * (1 - np.clip(fw * 8, 0, 1)) * 0.35)[..., None] * np.array([1.0, 1.0, 1.0], dtype=np.float32)
        # sun/moon glitter path
        gl = np.exp(-((xs - ox) / (W * 0.10)) ** 2) * (smooth(0.55, 0.75, wave)) * np.exp(-near * 1.5)
        g = g + gl[..., None] * ocol * 0.6
    elif ground_kind == "void":
        ex = np.minimum((X / 1.2) % 1.0, 1 - (X / 1.2) % 1.0)
        ez = np.minimum((Z / 1.2) % 1.0, 1 - (Z / 1.2) % 1.0)
        grid = np.maximum(np.clip(1 - ex / (0.020 + 0.5 * fw), 0, 1), np.clip(1 - ez / (0.020 + 0.5 * fw), 0, 1))
        grid = grid * np.exp(-Z / 14.0)
        emissive += grid * 0.65
        dots = smooth(0.93, 0.995, vnoise2(X * 9, Z * 9, seed + 74)) * np.exp(-Z / 18.0)
        emissive += dots * 0.8
    # worn path through the centre (X = 0 at Z = 2.4), calmer and a bit lighter
    if path_strength > 0:
        Zc = np.maximum(Z, 0.2)
        center = 1.1 * np.sin(0.52 * (Zc - 2.4)) * smooth(0.0, 6.0, Zc)
        edge_n = (fbm2(Z * 1.6, X * 0.0 + 3.0, 4, seed + 100, fw) - 0.5) * 0.18
        dx = np.abs(X - center) - 0.85 + edge_n
        on = (1.0 - smooth(-0.05, 0.12, dx)) * path_strength
        pcol = g * 0.55 + base_g * 0.55
        pcol = pcol * (0.85 + 0.3 * fbm2(X * 3, Z * 3, 4, seed + 110, fw))[..., None]
        rut = np.zeros_like(X)
        for side in (-1, 1):
            rx = np.abs(X - center - side * 0.42) - 0.075
            rut = np.maximum(rut, 1.0 - smooth(-0.02, 0.05, rx))
        pcol = pcol * (1.0 - 0.28 * rut)[..., None]
        g = g * (1 - on[..., None]) + pcol * on[..., None]
        emissive = emissive * (1 - on * 0.9)
    g = g + emissive[..., None] * glow * 1.4
    # lighting: the orb side is brighter; fog + horizon glow
    sidelit = 0.85 + 0.3 * np.clip(1 - np.abs(xs - ox) / W, 0, 1)
    g = g * sidelit[..., None]
    fogk = 1.0 - np.exp(-Z * 0.032)
    g = g * (1 - fogk[..., None] * 0.80) + fog * (fogk[..., None] * 0.80)
    g = g + (np.exp(-gy / (0.05 * H)) * 0.18)[..., None] * glow
    img = np.where(ground[..., None], g, img)
    hzb = np.exp(-((ys - yh) / (0.018 * H)) ** 2)
    img = img * (1 - hzb[..., None] * 0.40) + (fog * 0.7 + glow * 0.3) * (hzb[..., None] * 0.40)

    # ------------------------------------------------------------ grade
    img = np.clip(img, 0, 1.4)
    img = img / (1.0 + img * 0.10)
    v = np.clip(img, 0, 1) ** (1 / 1.04)
    v = v + 0.35 * (v * v * (3 - 2 * v) - v)
    v = v + np.random.default_rng(seed).normal(0, 0.008, size=v.shape).astype(np.float32)
    return np.clip(v, 0, 1)


def render_job(job):
    name, W, H, out_dir, quality = job
    t0 = time.time()
    img = render(name, W, H)
    path = os.path.join(out_dir, f"scene_{name}.webp")
    Image.fromarray((img * 255).astype(np.uint8), "RGB").save(path, "WEBP", quality=quality, method=6)
    return path, time.time() - t0, os.path.getsize(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("biome")
    ap.add_argument("--width", type=int, default=1080)
    ap.add_argument("--height", type=int, default=1800)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--quality", type=int, default=84)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    names = list(BIOMES) if a.biome == "all" else [a.biome]
    jobs = [(n, a.width, a.height, a.out, a.quality) for n in names]
    with ProcessPoolExecutor(max_workers=int(os.environ.get("RENDER_WORKERS", "8"))) as ex:
        for path, dt, sz in ex.map(render_job, jobs):
            print(f"{os.path.basename(path)}  {dt:.0f}s  {sz/1024:.0f} KB")


if __name__ == "__main__":
    main()
