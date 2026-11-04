"""Realistic backdrops for the 19 biomes of the generated zones (zones 6..100).

Same camera as scene_meadow.py (portrait, horizon at 40 % of the height, perspective ground, aerial haze) but
driven by the biome style (colours mirror BiomeStyles.kt): sky + celestial object, three silhouette layers of the
biome's far shape, a ground material and a worn track where the monsters stand. The five stages of a biome reuse
one image; the app darkens and adds particles per stage.

Usage:  python scene_biomes.py <biome|all> [--width 1080 --height 1800] [--out DIR]
Output: scene_biome_<id>.webp
"""
from __future__ import annotations

import argparse
import os
import sys
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import scene_meadow as sm  # noqa: E402
from scene_meadow import fbm2, hash2, smooth, vnoise2  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
HORIZON = 0.40


def hx(v):
    return np.array([((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0], dtype=np.float32)


# id: sky(top, horizon), far(near, far), ground(top, bottom), glow, fog, far shape, ground kind, orb, skin(light)
BIOMES = {
    "heide": dict(sky=(0x2B2740, 0x8A6A7A), far=(0x3A2F44, 0x6A5468), ground=(0x3C4A34, 0x151D14), glow=0xB36BFF, fog=0x6A5578, shape="crags", kind="heath", orb="moon"),
    "wueste": dict(sky=(0x3A6AA8, 0xF0C080), far=(0x8A5A30, 0xC48A4C), ground=(0xD0A060, 0x7A5430), glow=0xFFC247, fog=0xD9A066, shape="dunes", kind="sand", orb="sun"),
    "vulkan": dict(sky=(0x1A0A08, 0xC2410C), far=(0x2A1410, 0x5A2418), ground=(0x35221A, 0x0E0705), glow=0xFF5A14, fog=0x6A2410, shape="peaks", kind="lava", orb="red_sun"),
    "gruft": dict(sky=(0x0A0F14, 0x2C4A4A), far=(0x14201F, 0x2A3C3A), ground=(0x2E3432, 0x0E1110), glow=0x5FFFB0, fog=0x2A4640, shape="columns", kind="tiles", orb="moon"),
    "frost": dict(sky=(0x0B1A2E, 0xBFE3F5), far=(0x6C93AD, 0xA8CBE0), ground=(0xDDEFF8, 0x6C93AD), glow=0x7FD9FF, fog=0xBFDDEE, shape="peaks", kind="ice", orb="moon"),
    "dschungel": dict(sky=(0x2A5A3A, 0x9BCF7F), far=(0x123A1C, 0x2A6A30), ground=(0x1E5A2A, 0x0A2410), glow=0xC6FF3B, fog=0x3F7A3A, shape="trees", kind="jungle", orb="sun"),
    "ruinen": dict(sky=(0x1E2630, 0x7C8CA0), far=(0x2C3440, 0x59667A), ground=(0x5A5F58, 0x23261F), glow=0xE8C46A, fog=0x66707E, shape="ruins", kind="tiles", orb="sun"),
    "glutpilz": dict(sky=(0x0B0820, 0x3A1F5C), far=(0x2A1448, 0x5A2C80), ground=(0x2A2148, 0x0C0818), glow=0x3DFFC4, fog=0x3A2468, shape="tendrils", kind="fungus", orb="none"),
    "kueste": dict(sky=(0x0E1C2B, 0x6C8AA3), far=(0x1C3446, 0x3C5C72), ground=(0x1F4D63, 0x0A1C28), glow=0x7FE6FF, fog=0x5C7C90, shape="waves", kind="water", orb="none"),
    "knochen": dict(sky=(0x2A1E14, 0xC9B48A), far=(0x6A5A40, 0x9A8660), ground=(0xB7A98A, 0x5C513C), glow=0xE8DDBA, fog=0xB8A682, shape="crags", kind="bones", orb="eclipse"),
    "blutgarten": dict(sky=(0x1A0508, 0x7A1426), far=(0x3A0C14, 0x62172A), ground=(0x5A1A24, 0x1E070B), glow=0xFF2A4A, fog=0x6A1A2A, shape="tendrils", kind="flesh", orb="red_sun"),
    "himmel": dict(sky=(0x10204A, 0x9FC8FF), far=(0x5A78B0, 0x93B0DC), ground=(0xC7D2E8, 0x5B6C94), glow=0xFFF27A, fog=0xB8CCF0, shape="islands", kind="clouds", orb="sun"),
    "unterwelt": dict(sky=(0x1C0402, 0xB3180A), far=(0x3A0A06, 0x6A1008), ground=(0x4A1812, 0x120403), glow=0xFF3B0A, fog=0x7A1A0E, shape="spires", kind="lava", orb="eclipse"),
    "schatten": dict(sky=(0x050509, 0x2A1F44), far=(0x120E20, 0x241B3A), ground=(0x15121E, 0x050409), glow=0x9D6BFF, fog=0x2A2046, shape="spires", kind="void", orb="eclipse"),
    "uhrwerk": dict(sky=(0x1A1410, 0x8A6A3A), far=(0x2E2414, 0x5A4424), ground=(0x6B5328, 0x2A200E), glow=0xFFB347, fog=0x6A5430, shape="gears", kind="brass", orb="none"),
    "traum": dict(sky=(0x2A0F3A, 0xFF7AC8), far=(0x4A1A66, 0x8A3A9A), ground=(0x4A2A66, 0x140A24), glow=0xFF6AD5, fog=0xA05AA8, shape="tendrils", kind="dream", orb="moon"),
    "astral": dict(sky=(0x02030F, 0x1B2A66), far=(0x0E1640, 0x243478), ground=(0x1A2250, 0x04061A), glow=0x6AA8FF, fog=0x1E2C6A, shape="islands", kind="void", orb="planet"),
    "leere": dict(sky=(0x000000, 0x3A0A4A), far=(0x14001E, 0x2A0A3A), ground=(0x1A0828, 0x040008), glow=0xFF2BD6, fog=0x2A0A3A, shape="tendrils", kind="void", orb="rift"),
    "ende": dict(sky=(0x050003, 0xFFB23A), far=(0x2A1608, 0x6A3A10), ground=(0x3A2A10, 0x0A0604), glow=0xFFD23A, fog=0x7A4A18, shape="towers", kind="tiles", orb="eclipse"),
}
STARRY = {"heide", "gruft", "frost", "schatten", "astral", "leere", "ende", "glutpilz", "traum", "unterwelt"}
CLOUDY = {"heide", "wueste", "frost", "dschungel", "ruinen", "kueste", "himmel", "knochen", "traum"}


# --------------------------------------------------------------------------- sky

def sky_layer(W, H, b, seed, xs, ys, yh):
    top, hor = hx(b["sky"][0]), hx(b["sky"][1])
    glow, fog = hx(b["glow"]), hx(b["fog"])
    t = np.clip(ys / yh, 0.0, 1.0)
    img = top + (hor - top) * (t ** 1.6)[..., None]
    sun = np.array([0.72 * W, 0.12 * H], dtype=np.float32)
    d_sun = np.hypot(xs - sun[0], ys - sun[1]) / W
    if b["id"] in STARRY:
        r = hash2(np.floor(xs / 2.0), np.floor(ys / 2.0), seed + 1)
        star = (r > 0.9965) * (1.0 - t) ** 1.5 * (0.5 + 0.5 * hash2(np.floor(xs / 2.0), np.floor(ys / 2.0), seed + 2))
        neb = smooth(0.55, 0.85, fbm2(xs / W * 3.0, ys / H * 3.0, 5, seed + 3)) * (1 - t)
        img = img + star[..., None] * 1.1 + neb[..., None] * glow * 0.18
    orb = b["orb"]
    if orb in ("sun", "red_sun"):
        col = np.array([1.0, 0.92, 0.70], dtype=np.float32) if orb == "sun" else np.array([1.0, 0.32, 0.10], dtype=np.float32)
        img = img + np.exp(-d_sun * 5.0)[..., None] * col * 0.55 + np.exp(-d_sun * 28.0)[..., None] * col * 0.6
        disc = 1.0 - smooth(0.040, 0.044, d_sun)
        img = img * (1 - disc[..., None]) + (col * 0.6 + 0.6) * disc[..., None]
    elif orb == "moon":
        mc = np.array([0.25 * W, 0.13 * H], dtype=np.float32)
        dm = np.hypot(xs - mc[0], ys - mc[1]) / W
        disc = 1.0 - smooth(0.050, 0.053, dm)
        crater = 0.75 + 0.35 * fbm2(xs / W * 40.0, ys / W * 40.0, 4, seed + 7)
        shade = np.clip(1.0 - ((xs - mc[0]) + (ys - mc[1])) / W * 6.0, 0.55, 1.15)
        mcol = np.array([0.88, 0.90, 0.96], dtype=np.float32)
        img = img + np.exp(-dm * 9.0)[..., None] * mcol * 0.22
        img = img * (1 - disc[..., None]) + (mcol * (crater * shade)[..., None]) * disc[..., None]
    elif orb == "eclipse":
        mc = np.array([0.5 * W, 0.15 * H], dtype=np.float32)
        dm = np.hypot(xs - mc[0], ys - mc[1]) / W
        corona = np.exp(-np.maximum(dm - 0.07, 0) * 22.0) * (0.75 + 0.5 * fbm2(np.arctan2(ys - mc[1], xs - mc[0]) * 6.0, dm * 20.0, 3, seed + 9))
        img = img + corona[..., None] * glow * 0.9
        ring = np.exp(-((dm - 0.071) / 0.0025) ** 2)
        img = img + ring[..., None] * (glow * 0.6 + 0.6)
        disc = 1.0 - smooth(0.068, 0.071, dm)
        img = img * (1 - disc[..., None]) + np.array([0.01, 0.005, 0.01], dtype=np.float32) * disc[..., None]
    elif orb == "planet":
        mc = np.array([0.30 * W, 0.16 * H], dtype=np.float32)
        dx, dy = (xs - mc[0]) / W, (ys - mc[1]) / W
        dm = np.hypot(dx, dy)
        bands = 0.6 + 0.4 * np.sin(dy * 90.0 + 3.0 * fbm2(xs / W * 8.0, ys / W * 30.0, 3, seed + 11))
        shade = np.clip(1.15 - (dx + dy) * 7.0, 0.15, 1.2)
        pcol = (hx(b["far"][1]) * 1.6 + 0.15) * (bands * shade)[..., None]
        disc = 1.0 - smooth(0.11, 0.113, dm)
        img = img + np.exp(-np.maximum(dm - 0.11, 0) * 30.0)[..., None] * glow * 0.35
        img = img * (1 - disc[..., None]) + pcol * disc[..., None]
        rr = np.hypot(dx / 1.0, dy / 0.18)
        ring = np.exp(-((rr - 0.19) / 0.012) ** 2) * ((dy > 0) | (dm > 0.113))
        img = img + ring[..., None] * (glow * 0.5 + 0.25)
    elif orb == "rift":
        x0 = 0.5 * W + (fbm2(ys / H * 6.0, 1.0, 4, seed + 13) - 0.5) * 0.25 * W
        dist = np.abs(xs - x0) / W
        width = 0.010 + 0.02 * (1 - np.abs(ys / yh - 0.35) * 2.2).clip(0, 1)
        core = np.exp(-(dist / np.maximum(width * 0.4, 1e-3)) ** 2) * (ys < yh * 0.85)
        halo = np.exp(-dist / (width * 3.0)) * (ys < yh * 0.9)
        img = img + halo[..., None] * glow * 0.7 + core[..., None] * 1.2
    if b["id"] in CLOUDY:
        dy = np.maximum(yh - ys, 1.0)
        f = 1.10 * W
        u, v = (xs - W / 2) / dy * 0.9, f / dy * 0.9 + 3.0
        dens = smooth(0.52, 0.80, fbm2(u * 0.55, v * 0.55, 6, seed + 20))
        dl = smooth(0.52, 0.80, fbm2(u * 0.55 + 0.06, v * 0.55 + 0.05, 6, seed + 20))
        light = np.clip(0.55 + (dens - dl) * 3.5, 0.3, 1.15)
        ccol = (hor * 0.5 + 0.55) * light[..., None] + (1 - light)[..., None] * top * 0.8
        a = np.clip(dens, 0, 1) * smooth(0.0, 0.10, (yh - ys) / H) * 0.9
        img = img * (1 - a[..., None]) + ccol * a[..., None]
    return img


# --------------------------------------------------------------------------- silhouettes

_LAT = {}


def far_profile(shape, x, sd, base, amp):
    """Height (fraction of H above horizon) of a silhouette layer for each column x in 0..1.
    Cell shapes also leave a lateral light factor in _LAT["lat"] (lit left flank, shaded right flank)."""
    _LAT["lat"] = None
    n = np.zeros_like(x)
    for o in range(5):
        n += (0.5 ** o) * vnoise2(x * 2.4 * 2 ** o, np.zeros_like(x) + sd + o, sd)
    n = n / (1 - 0.5 ** 5)
    if shape == "dunes":
        return base + amp * (0.5 + 0.5 * np.sin(x * 9.0 + n * 4.0)) * 0.8 * (0.6 + 0.6 * n)
    if shape in ("peaks", "crags"):
        sharp = 2.0 if shape == "peaks" else 1.2
        tri = np.abs(((x * (5 + sd % 3) + n * 0.6) % 1.0) - 0.5) * 2
        return base + amp * ((1 - tri) ** sharp * 1.3 + n * 0.5) * (1.0 if shape == "peaks" else 0.6)
    if shape == "spires":
        k = 14 + sd % 5
        cell = np.floor(x * k)
        fx = x * k - cell
        hgt = 0.4 + 1.2 * hash2(cell, cell * 0 + sd, sd + 40)
        sp = np.clip(1.0 - np.abs(fx - 0.5) * (5.0 + 3 * hash2(cell, cell * 0 + 1, sd)), 0, 1)
        _LAT["lat"] = 1.25 - 0.9 * np.clip((fx - 0.3) / 0.4, 0, 1)
        return base + amp * 0.35 * n + amp * hgt * sp ** 0.6 * 1.4
    if shape in ("columns", "ruins", "towers"):
        k = {"columns": 18, "ruins": 10, "towers": 7}[shape] + sd % 4
        cell = np.floor(x * k)
        fx = x * k - cell
        filled = hash2(cell, cell * 0 + sd, sd + 50) > (0.45 if shape != "towers" else 0.35)
        hgt = 0.5 + 1.1 * hash2(cell, cell * 0 + 3, sd)
        w = 0.55 if shape == "columns" else 0.8
        block = (np.abs(fx - 0.5) < w * 0.5) & filled
        _LAT["lat"] = 1.2 - 0.75 * np.clip((fx - (0.5 - w * 0.5)) / w, 0, 1) ** 1.5
        broken = 1.0 - (shape == "ruins") * 0.35 * vnoise2(x * 30.0, np.zeros_like(x) + sd, sd + 2)
        top = hgt * broken * (1.5 if shape == "towers" else 1.0)
        if shape == "towers":
            spire = np.clip(1 - np.abs(fx - 0.5) * 8, 0, 1) * 0.5
            top = top + spire * filled
        return base + amp * 0.25 * n + amp * np.where(block, top, 0.0)
    if shape == "trees":
        k = 60
        cell = np.floor(x * k)
        fx = x * k - cell
        crown = np.sqrt(np.clip(1 - ((fx - 0.5) * 2) ** 2, 0, 1)) * (0.5 + 0.5 * hash2(cell, cell * 0 + sd, sd))
        return base + amp * (0.6 + 0.5 * n) + amp * 0.5 * crown
    if shape == "waves":
        return base + amp * 0.4 * (0.5 + 0.5 * np.sin(x * 30.0 + n * 6.0)) * (0.3 + n)
    if shape == "tendrils":
        k = 9 + sd % 4
        cell = np.floor(x * k)
        fx = x * k - cell
        hgt = 0.6 + 1.4 * hash2(cell, cell * 0 + sd, sd + 60)
        stalk = np.clip(1 - np.abs(fx - 0.5 - 0.15 * np.sin(cell)) * 7, 0, 1)
        _LAT["lat"] = 1.25 - 0.9 * np.clip((fx - 0.35 - 0.15 * np.sin(cell)) / 0.3, 0, 1)
        return base + amp * 0.3 * n + amp * hgt * stalk ** 0.5
    if shape in ("gears", "islands"):
        return base + amp * 0.25 * n
    return base + amp * n


def silhouettes(img, W, H, b, seed, xs, ys, yh):
    near, farc = hx(b["far"][0]), hx(b["far"][1])
    fog = hx(b["fog"])
    glow = hx(b["glow"])
    shape = b["shape"]
    x = xs[0] / W
    layers = ((0.020, 0.10, 0.62, 3), (0.012, 0.075, 0.38, 11), (0.004, 0.050, 0.16, 23))
    for i, (base, amp, haze, sd) in enumerate(layers):
        k = i / 2.0
        col_top = farc * (1 - k) + near * k
        prof = far_profile(shape, x, seed + sd, base, amp * (1.6 if shape in ("peaks", "spires", "towers", "tendrils") else 1.0))
        lat = _LAT["lat"]
        if lat is None:
            lat = np.clip(1.0 - np.gradient(prof) * W * 1.2, 0.6, 1.3)
        top = yh - prof * H
        inside = (ys >= top[None, :]) & (ys < yh + 4)
        depth = np.clip((ys - top[None, :]) / (0.08 * H), 0, 1)
        tex = 0.82 + 0.30 * fbm2(xs / W * (18 + 12 * i), ys / H * (30 + 20 * i), 4, seed + 70 + i)
        rim = np.exp(-np.maximum(ys - top[None, :], 0) / (0.004 * H)) * (1 - k * 0.5)
        col = col_top * (1.05 - 0.35 * depth[..., None]) * tex[..., None] * (lat[None, :, None] * (1 - k * 0.3) + k * 0.3)
        col = col + rim[..., None] * (glow * 0.25 + fog * 0.2)
        col = col * (1 - haze) + fog * haze
        img = np.where(inside[..., None], col, img)
        if shape == "gears" and i < 2:
            rng = np.random.default_rng(seed + i)
            for _ in range(5 - i * 2):
                cx, cy = rng.uniform(0.05, 0.95) * W, yh - rng.uniform(0.02, 0.09 - 0.03 * i) * H
                R = rng.uniform(0.05, 0.10) * W * (1.0 - 0.3 * i)
                d = np.hypot(xs - cx, ys - cy)
                ang = np.arctan2(ys - cy, xs - cx)
                teeth = R * (1 + 0.12 * (np.sin(ang * (10 + int(R / W * 120))) > 0.2))
                gear = (d < teeth) & (d > R * 0.35) & ~((d < R * 0.75) & (np.sin(ang * 5) > 0.4))
                gcol = col_top * 1.4 * (0.85 + 0.3 * np.cos(ang - 2.3))[..., None]
                gcol = gcol * (1 - haze) + fog * haze
                img = np.where(gear[..., None], gcol, img)
        if shape == "islands" and i < 2:
            rng = np.random.default_rng(seed + 5 + i)
            for _ in range(4 - i):
                cx, cy = rng.uniform(0.08, 0.92) * W, yh - rng.uniform(0.10, 0.26) * H
                R = rng.uniform(0.06, 0.13) * W * (1.0 - 0.35 * i)
                ex, ey = (xs - cx) / R, (ys - cy) / R
                topm = (ey > -0.18 + 0.08 * vnoise2(xs / W * 60, ys * 0, seed)) & (ey < 0.0)
                under = (ey >= 0.0) & (ey < (1 - np.abs(ex)) * 1.1 + 0.15 * vnoise2(xs / W * 40, ys * 0, seed + 1))
                body = ((topm | under) & (np.abs(ex) < 1.0))
                shade = np.where(ey < 0.02, 1.15, 0.7 - 0.25 * np.clip(ey, 0, 1))
                icol = (col_top * 1.3 * shade[..., None]) * (1 - haze) + fog * haze
                grass = (ey < 0.02) & body
                icol = np.where(grass[..., None], icol * 0.6 + hx(b["ground"][0]) * 0.5, icol)
                img = np.where(body[..., None], icol, img)
    return img


# --------------------------------------------------------------------------- ground

def ground_layer(W, H, b, seed, xs, ys, yh):
    f, E = 1.10 * W, 1.0
    gy = ys - yh
    gyc = np.maximum(gy, 1.0)
    Z = E * f / gyc
    X = (xs - W / 2) * Z / f
    fw = Z / f * 1.2
    near = np.clip(gy / (H - yh), 0, 1)
    top, bot = hx(b["ground"][0]), hx(b["ground"][1])
    glow, fog = hx(b["glow"]), hx(b["fog"])
    kind = b["kind"]
    n1 = fbm2(X * 0.45, Z * 0.45, 5, seed + 40, fw)
    n2 = fbm2(X * 2.4, Z * 2.4, 5, seed + 50, fw)
    n3 = fbm2(X * 7.0, Z * 7.0, 4, seed + 55, fw)
    base = bot + (top - bot) * (0.35 + 0.65 * smooth(0.25, 0.75, n1))[..., None]
    g = base * (0.80 + 0.40 * n2)[..., None]
    emis = np.zeros_like(X)
    if kind in ("heath", "jungle"):
        blade = vnoise2(xs * 0.32, ys * 0.035, seed + 70) * 0.6 + vnoise2(xs * 0.9, ys * 0.12, seed + 71) * 0.4
        g = g * (1.0 + ((blade - 0.5) * 0.9 * near ** 1.2)[..., None])
        if kind == "heath":
            heather = smooth(0.58, 0.75, fbm2(X * 1.2, Z * 1.2, 4, seed + 60, fw))
            g = g * (1 - 0.5 * heather[..., None]) + hx(0x8A4A9A) * 0.55 * heather[..., None]
        else:
            fern = smooth(0.55, 0.70, n3)
            g = g * (1 + 0.35 * fern[..., None])
    elif kind == "sand":
        rip = 0.5 + 0.5 * np.sin(Z * 6.0 + X * 1.2 + 4.0 * fbm2(X * 0.5, Z * 0.5, 3, seed + 61, fw))
        rip = rip * smooth(0.0, 1.0, 1.0 - fw * 30)
        g = g * (0.86 + 0.24 * rip)[..., None]
        g = g * (0.9 + 0.2 * smooth(0.4, 0.8, n1))[..., None]
    elif kind in ("lava",):
        crust = 0.55 + 0.6 * n3
        g = bot * 1.4 * crust[..., None] + top * 0.4 * (1 - n1)[..., None]
        vein = np.abs(fbm2(X * 0.9, Z * 0.9, 5, seed + 62, fw) - 0.5)
        cracks = 1.0 - smooth(0.0, 0.035, vein)
        pools = smooth(0.66, 0.72, fbm2(X * 0.25 + 3, Z * 0.25, 4, seed + 63, fw))
        emis = np.maximum(cracks * 0.9, pools)
    elif kind == "ice":
        cr = np.abs(fbm2(X * 1.4, Z * 1.4, 5, seed + 64, fw) - 0.5)
        crack = 1.0 - smooth(0.0, 0.02, cr)
        g = g * (1 - 0.35 * crack[..., None])
        snow = smooth(0.45, 0.70, fbm2(X * 0.6, Z * 0.6, 4, seed + 65, fw))
        g = g * (1 - snow[..., None]) + np.array([0.94, 0.97, 1.0], dtype=np.float32) * snow[..., None]
        sheen = np.exp(-((xs - 0.72 * W) / (0.10 * W)) ** 2) * (1 - snow) * 0.35
        g = g + sheen[..., None]
    elif kind == "water":
        waves = 0.5 + 0.5 * np.sin(Z * 4.0 + 2.5 * fbm2(X * 0.6, Z * 0.6, 4, seed + 66, fw))
        refl = np.clip(Z / 14.0, 0.2, 0.9)
        g = top * (1 - refl)[..., None] + hx(b["sky"][1]) * 0.9 * refl[..., None]
        g = g * (0.85 + 0.25 * waves)[..., None]
        foam = smooth(0.70, 0.78, fbm2(X * 2.0, Z * 2.0, 4, seed + 67, fw)) * (1 - refl)
        g = g + foam[..., None] * 0.35
        # wet sand bank in the foreground, where the monsters stand
        bank = smooth(0.0, 1.0, (near - 0.25) * 3.0)
        sand = np.array([0.40, 0.36, 0.28], dtype=np.float32) * (0.8 + 0.3 * n2)[..., None]
        g = g * (1 - bank[..., None]) + sand * bank[..., None]
    elif kind == "bones":
        cell = 0.35
        cx, cz = np.floor(X / cell), np.floor(Z / cell)
        r1, r2, r3 = hash2(cx, cz, 91), hash2(cx, cz, 92), hash2(cx, cz, 93)
        ang = r1 * np.pi
        lx, lz = X - (cx + 0.5) * cell, Z - (cz + 0.5) * cell
        u = lx * np.cos(ang) + lz * np.sin(ang)
        v = -lx * np.sin(ang) + lz * np.cos(ang)
        bone = (r3 > 0.55) & (np.abs(u) < 0.12) & (np.abs(v) < 0.016 + 0.012 * (np.abs(u) > 0.10)) & (Z < 16)
        bcol = np.array([0.90, 0.86, 0.74], dtype=np.float32) * (0.75 + 0.3 * r2)[..., None] * (1.0 + 0.4 * np.clip(-v * 40, -0.5, 0.5))[..., None]
        g = np.where(bone[..., None], bcol, g)
    elif kind == "flesh":
        vein = np.abs(fbm2(X * 1.3, Z * 1.3, 5, seed + 68, fw) - 0.5)
        v1 = 1.0 - smooth(0.0, 0.03, vein)
        g = g * (1 - 0.5 * v1[..., None])
        wet = smooth(0.6, 0.8, n3)
        g = g + wet[..., None] * 0.10
        emis = v1 * smooth(0.5, 0.7, n1) * 0.6
    elif kind in ("tiles", "brass"):
        ts = 0.8
        tx, tz = X / ts, Z / ts
        off = (np.floor(tz) % 2) * 0.5
        fx, fz = np.abs(((tx + off) % 1.0) - 0.5), np.abs((tz % 1.0) - 0.5)
        mort = smooth(0.46, 0.49, np.maximum(fx, fz)) * smooth(0.0, 1.0, 1.0 - fw * 8)
        tilev = 0.75 + 0.45 * hash2(np.floor(tx + off), np.floor(tz), seed + 69)
        g = g * tilev[..., None] * (1 - 0.6 * mort[..., None])
        crack = (1.0 - smooth(0.0, 0.015, np.abs(fbm2(X * 2.2, Z * 2.2, 4, seed + 71, fw) - 0.5))) * 0.5
        g = g * (1 - crack[..., None])
        if kind == "brass":
            g = g * np.array([1.2, 1.0, 0.65], dtype=np.float32) + (smooth(0.7, 0.8, n3) * 0.15)[..., None]
        else:
            moss = smooth(0.62, 0.78, fbm2(X * 1.1, Z * 1.1, 4, seed + 72, fw)) * mort
            g = g * (1 - moss[..., None]) + np.array([0.18, 0.28, 0.12], dtype=np.float32) * moss[..., None]
    elif kind == "fungus":
        cap = smooth(0.74, 0.79, fbm2(X * 5.0, Z * 5.0, 4, seed + 73, fw)) * (Z < 18)
        emis = cap * 0.55
        g = g * (0.8 + 0.3 * n3)[..., None]
    elif kind == "clouds":
        puff = fbm2(X * 0.6, Z * 0.6, 5, seed + 74, fw)
        g = top * (0.75 + 0.45 * smooth(0.3, 0.8, puff))[..., None]
        g = g + (smooth(0.6, 0.8, puff) * 0.12)[..., None]
    elif kind == "dream":
        swirl = 0.5 + 0.5 * np.sin(X * 2.0 + Z * 1.2 + 6.0 * fbm2(X * 0.4, Z * 0.4, 4, seed + 75, fw))
        g = g * (0.7 + 0.5 * swirl)[..., None]
        emis = smooth(0.80, 0.95, swirl) * 0.35
    elif kind == "void":
        grid = np.minimum(np.abs((X % 1.6) - 0.8), np.abs((Z % 1.6) - 0.8))
        lines = 0.0
        vein = np.abs(fbm2(X * 0.7, Z * 0.7, 5, seed + 76, fw) - 0.5)
        emis = (1.0 - smooth(0.0, 0.02, vein)) * 0.8 + lines
        g = g * 0.6 + np.clip(Z / 30.0, 0, 1)[..., None] * hx(b["sky"][1]) * 0.35     # glassy reflection
    # worn track where the monsters stand (not on water / clouds)
    if kind not in ("water", "clouds", "void"):
        Zc = np.maximum(Z, 0.2)
        center = 0.9 * np.sin(0.45 * (Zc - 2.4)) * smooth(0.0, 6.0, Zc)
        dx = np.abs(X - center) - 0.75 + (fbm2(Z * 1.6, 3.0 + 0 * X, 4, seed + 100, fw) - 0.5) * 0.18
        worn = (1.0 - smooth(-0.05, 0.12, dx)) * 0.55
        g = g * (1 - worn[..., None] * 0.35) + (bot * 0.6 + top * 0.2) * worn[..., None] * 0.35
    g = g + emis[..., None] * (glow * 1.1 + 0.15)
    # emissive glow bleeding into the ground fog
    fogk = 1.0 - np.exp(-Z * 0.035)
    g = g * (1 - fogk[..., None] * 0.80) + fog * (fogk[..., None] * 0.80)
    return g, Z


# --------------------------------------------------------------------------- render

def render(W, H, bid, seed=7):
    b = dict(BIOMES[bid])
    b["id"] = bid
    yh = HORIZON * H
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    img = sky_layer(W, H, b, seed, xs, ys, yh)
    img = silhouettes(img, W, H, b, seed, xs, ys, yh)
    g, Z = ground_layer(W, H, b, seed, xs, ys, yh)
    img = np.where((ys > yh)[..., None], g, img)
    fog = hx(b["fog"])
    hz = np.exp(-((ys - yh) / (0.025 * H)) ** 2)
    img = img * (1 - hz[..., None] * 0.45) + fog * (hz[..., None] * 0.45)
    # trees (jungle: dense, heath: a few gnarled ones)
    trees = []
    if bid == "dschungel":
        trees = [(-3.4, 5.5, 5.4, 61), (3.8, 6.5, 5.8, 62), (-6.4, 8.0, 6.2, 63), (7.0, 9.5, 6.4, 64), (-9.0, 12.0, 6.6, 65),
                 (9.5, 13.5, 6.8, 66), (-4.8, 16.0, 6.8, 67), (5.4, 18.0, 7.0, 68), (-12.0, 18.0, 7.2, 69), (13.0, 20.0, 7.2, 70)]
    elif bid == "heide":
        trees = [(-6.0, 11.0, 3.6, 71), (7.5, 15.0, 3.8, 72), (-12.0, 22.0, 4.2, 73)]
    if trees:
        sm.T = dict(sm.THEMES["forest"])
        sm.T["leaf"] = tuple(float(c) for c in (hx(b["ground"][0]) * (1.0 if bid == "dschungel" else 0.8)))
        f, E = 1.10 * W, 1.0
        for (tx, tz, th, ts) in sorted(trees, key=lambda t: -t[1]):
            spr = sm.render_tree(ts)
            sh, sw_ = spr.shape[:2]
            px, py, hp = W / 2 + tx * f / tz, yh + E * f / tz, th * f / tz
            wp = hp * sw_ / sh
            pre = spr.copy()
            pre[..., :3] *= pre[..., 3:4]
            im = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((max(int(wp), 2), max(int(hp), 2)), Image.LANCZOS)
            r = np.asarray(im, dtype=np.float32) / 255.0
            a = r[..., 3:4]
            rgb = r[..., :3] / np.maximum(a, 1e-4)
            fogt = float(1.0 - np.exp(-tz * 0.035)) * 0.8
            rgb = rgb * (1 - fogt) + fog * fogt
            x0, y0 = int(px - rgb.shape[1] / 2), int(py - rgb.shape[0])
            xa, ya, xb, yb = max(x0, 0), max(y0, 0), min(x0 + rgb.shape[1], W), min(y0 + rgb.shape[0], H)
            if xb <= xa or yb <= ya:
                continue
            sub, src = (slice(ya, yb), slice(xa, xb)), (slice(ya - y0, yb - y0), slice(xa - x0, xb - x0))
            img[sub] = img[sub] * (1 - a[src]) + rgb[src] * a[src]
    # grade: gentle contrast, vignette, grain
    img = np.clip(img, 0, 1.4)
    img = img / (1.0 + img * 0.10)
    v = np.clip(img, 0, 1) ** (1 / 1.04)
    v = v + 0.30 * (v * v * (3 - 2 * v) - v)
    vig = 1.0 - 0.28 * (((xs / W - 0.5) * 1.6) ** 2 + ((ys / H - 0.55) * 1.2) ** 2)
    v = v * np.clip(vig, 0.55, 1.0)[..., None]
    rng = np.random.default_rng(seed)
    v = v + rng.normal(0, 0.010, size=v.shape).astype(np.float32)
    return np.clip(v, 0, 1)


def render_job(job):
    bid, W, H, out = job
    img = render(W, H, bid)
    path = os.path.join(out, f"scene_biome_{bid}.webp")
    Image.fromarray((img * 255).astype(np.uint8), "RGB").save(path, "WEBP", quality=86, method=6)
    return path, os.path.getsize(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("biome")
    ap.add_argument("--width", type=int, default=1080)
    ap.add_argument("--height", type=int, default=1800)
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    ids = list(BIOMES) if a.biome == "all" else a.biome.split(",")
    with ProcessPoolExecutor(max_workers=4) as ex:
        for path, size in ex.map(render_job, [(i, a.width, a.height, a.out) for i in ids]):
            print(os.path.basename(path), size // 1024, "KB")


if __name__ == "__main__":
    main()
