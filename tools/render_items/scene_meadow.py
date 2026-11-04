"""Realistic first-person meadow backdrop (zone 1 "Wiese"): perspective ground, sky, hills, trees.

Output: one portrait image whose horizon sits at 40 % of the height (the app's `horizonY`), with the
dirt path running through the picture centre where monsters stand (`monsterGroundY` = 68 %).

Usage:  python scene_meadow.py [--width 1080] [--height 1800] [--out FILE]
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

from items import fold_polar  # noqa: E402
from sdf import (Material, Renderer, fbm, rot_x, rot_y, sd_ellipsoid, sd_round_cone, sd_sphere, smin, union, v3,  # noqa: E402
                 value_noise)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "scene_meadow.webp")

HORIZON = 0.40

arr = lambda *v: np.array(v, dtype=np.float32)

THEMES = {
    "meadow": dict(
        out="scene_meadow.webp", sky=(arr(0.13, 0.37, 0.74), arr(0.37, 0.62, 0.90), arr(0.80, 0.89, 0.93)), sun_glow=1.0,
        cloud_lo=0.50, cloud_hi=0.78, haze=arr(0.80, 0.86, 0.84), fog=0.030,
        ridges=((arr(0.50, 0.60, 0.72), arr(0.62, 0.72, 0.80)), (arr(0.30, 0.46, 0.42), arr(0.48, 0.64, 0.56)), (arr(0.20, 0.37, 0.16), arr(0.36, 0.52, 0.24))),
        ground=(arr(0.10, 0.25, 0.06), arr(0.22, 0.42, 0.10), arr(0.46, 0.60, 0.20), arr(0.55, 0.52, 0.24)),
        flowers=0.90, dirt=(arr(0.46, 0.34, 0.22), arr(0.64, 0.52, 0.36)), grade=arr(1.04, 1.0, 0.93),
        leaf=(0.20, 0.38, 0.10), tree_shadow=0.42,
        trees=[(-5.6, 9.0, 4.4, 1), (6.2, 10.5, 4.8, 2), (-8.8, 14.5, 5.2, 3), (9.8, 16.0, 5.2, 4),
               (-12.5, 22.0, 5.8, 5), (13.2, 25.0, 5.8, 6), (-3.8, 33.0, 6.0, 7), (4.8, 39.0, 6.0, 8),
               (-18.0, 31.0, 6.4, 9), (19.0, 35.0, 6.4, 10)],
    ),
    "forest": dict(
        out="scene_forest.webp", sky=(arr(0.22, 0.33, 0.34), arr(0.42, 0.56, 0.52), arr(0.68, 0.78, 0.70)), sun_glow=0.45,
        cloud_lo=0.30, cloud_hi=0.62, haze=arr(0.55, 0.66, 0.58), fog=0.050,
        ridges=((arr(0.30, 0.42, 0.40), arr(0.42, 0.54, 0.48)), (arr(0.14, 0.28, 0.16), arr(0.26, 0.42, 0.24)), (arr(0.08, 0.20, 0.07), arr(0.18, 0.32, 0.12))),
        ground=(arr(0.04, 0.10, 0.03), arr(0.11, 0.22, 0.06), arr(0.24, 0.34, 0.10), arr(0.34, 0.25, 0.12)),
        flowers=2.0, dirt=(arr(0.30, 0.22, 0.14), arr(0.44, 0.34, 0.22)), grade=arr(0.97, 1.0, 0.92),
        leaf=(0.10, 0.27, 0.07), tree_shadow=0.60,
        trees=[(-3.6, 5.5, 5.0, 1), (4.0, 6.5, 5.4, 2), (-6.6, 8.0, 5.8, 3), (7.2, 9.5, 6.0, 4), (-9.0, 12.0, 6.2, 5),
               (9.6, 13.5, 6.4, 6), (-5.2, 16.0, 6.4, 7), (5.6, 18.0, 6.6, 8), (-12.0, 18.0, 6.8, 9), (13.0, 20.0, 6.8, 10),
               (-2.6, 24.0, 6.8, 11), (3.0, 27.0, 7.0, 12), (-9.0, 27.0, 7.0, 13), (10.0, 30.0, 7.2, 14), (-15.0, 33.0, 7.4, 15),
               (0.0, 38.0, 7.6, 16), (16.0, 38.0, 7.6, 17)],
    ),
    "swamp": dict(
        out="scene_swamp.webp", sky=(arr(0.25, 0.30, 0.24), arr(0.42, 0.50, 0.38), arr(0.62, 0.68, 0.50)), sun_glow=0.35,
        cloud_lo=0.25, cloud_hi=0.55, haze=arr(0.50, 0.56, 0.40), fog=0.075,
        ridges=((arr(0.34, 0.40, 0.32), arr(0.44, 0.50, 0.38)), (arr(0.18, 0.26, 0.16), arr(0.28, 0.36, 0.22)), (arr(0.10, 0.16, 0.09), arr(0.18, 0.26, 0.13))),
        ground=(arr(0.05, 0.08, 0.04), arr(0.13, 0.18, 0.07), arr(0.24, 0.30, 0.12), arr(0.30, 0.26, 0.14)),
        flowers=2.0, dirt=(arr(0.12, 0.15, 0.09), arr(0.22, 0.26, 0.15)), grade=arr(0.95, 1.0, 0.88),
        leaf=(0.13, 0.20, 0.07), tree_shadow=0.55, water=True,
        trees=[(-4.0, 6.0, 4.6, 21), (4.4, 7.5, 4.8, 22), (-7.5, 10.0, 5.4, 23), (8.0, 12.0, 5.6, 24), (-5.0, 15.0, 5.8, 25),
               (6.0, 17.0, 6.0, 26), (-11.0, 19.0, 6.4, 27), (12.0, 22.0, 6.6, 28), (-3.0, 26.0, 6.6, 29), (3.0, 30.0, 6.8, 30),
               (-14.0, 32.0, 7.0, 31), (15.0, 35.0, 7.2, 32)],
    ),
    "mountain": dict(
        out="scene_mountain.webp", sky=(arr(0.16, 0.34, 0.70), arr(0.44, 0.64, 0.90), arr(0.84, 0.91, 0.97)), sun_glow=1.0,
        cloud_lo=0.52, cloud_hi=0.80, haze=arr(0.84, 0.90, 0.96), fog=0.022, amp_k=3.2,
        ridges=((arr(0.80, 0.86, 0.94), arr(0.90, 0.94, 0.98)), (arr(0.56, 0.63, 0.73), arr(0.80, 0.85, 0.92)), (arr(0.38, 0.43, 0.50), arr(0.68, 0.73, 0.80))),
        ground=(arr(0.58, 0.62, 0.68), arr(0.80, 0.84, 0.90), arr(0.95, 0.97, 1.0), arr(0.46, 0.43, 0.41)),
        flowers=2.0, dirt=(arr(0.40, 0.38, 0.36), arr(0.60, 0.58, 0.55)), grade=arr(0.97, 1.0, 1.05),
        leaf=(0.09, 0.24, 0.15), tree_shadow=0.30,
        trees=[(-9.0, 20.0, 5.5, 41), (10.0, 24.0, 5.5, 42), (-16.0, 30.0, 6.5, 43), (17.0, 34.0, 6.5, 44)],
    ),
    "cave": dict(
        out="scene_cave.webp", sky=(arr(0.07, 0.06, 0.10), arr(0.14, 0.11, 0.17), arr(0.34, 0.26, 0.30)), sun_glow=0.0,
        cloud_lo=0.99, cloud_hi=1.0, haze=arr(0.32, 0.25, 0.30), fog=0.045, amp_k=1.5,
        ridges=((arr(0.26, 0.20, 0.22), arr(0.34, 0.26, 0.28)), (arr(0.20, 0.15, 0.16), arr(0.28, 0.21, 0.21)), (arr(0.14, 0.10, 0.11), arr(0.22, 0.16, 0.15))),
        ground=(arr(0.09, 0.08, 0.09), arr(0.18, 0.15, 0.15), arr(0.32, 0.27, 0.26), arr(0.38, 0.28, 0.20)),
        flowers=2.0, dirt=(arr(0.22, 0.18, 0.16), arr(0.40, 0.33, 0.28)), grade=arr(1.0, 0.95, 1.0),
        leaf=(0.10, 0.10, 0.12), tree_shadow=0.2, trees=[],
    ),
}
T = THEMES["meadow"]


# --------------------------------------------------------------------------- 2D noise

def hash2(ix, iy, seed=0):
    n = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + seed * 1274126177) & 0x7FFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0x7FFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise2(x, y, seed=0):
    ix, iy = np.floor(x), np.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash2(ix, iy, seed), hash2(ix + 1, iy, seed)
    c, d = hash2(ix, iy + 1, seed), hash2(ix + 1, iy + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm2(x, y, octaves=6, seed=0, footprint=None):
    """Fractal noise; octaves finer than the pixel footprint (world units per pixel) fade out (no aliasing)."""
    tot, amp, out, fr = 0.0, 0.5, 0.0, 1.0
    for o in range(octaves):
        w = 1.0
        if footprint is not None:
            w = np.clip(1.0 - footprint * fr * 1.6, 0.0, 1.0)
        out = out + amp * w * vnoise2(x * fr, y * fr, seed + o)
        tot = tot + amp * w
        amp *= 0.5
        fr *= 2.0
    return out / np.maximum(tot, 1e-6)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) == np.ndim(a) - 1 else t
    return a * (1 - t) + b * t


# --------------------------------------------------------------------------- trees (sprites from the SDF renderer)

def oak_scene(seed):
    lc = T["leaf"]
    leaf_tint = lambda p, a: a * (0.55 + 0.9 * fbm(p, 4.0, 3, seed))[:, None] * (0.65 + 0.5 * np.clip(p[:, 1] / 1.8, 0.0, 1.0))[:, None]
    leaf = Material(lc, rough=0.8, spec=0.12, sss=1.1, tint_fn=leaf_tint,
                    bump=lambda p: (fbm(p, 38.0, 3, seed + 3) - 0.5) * 0.05)
    bark = Material((0.22, 0.16, 0.11), rough=0.9, spec=0.1,
                    tint_fn=lambda p, a: a * (0.6 + 0.8 * fbm(p * np.array([3.0, 0.8, 3.0], dtype=np.float32), 9.0, 3, 7))[:, None],
                    bump=lambda p: (value_noise(p * np.array([1.0, 0.25, 1.0], dtype=np.float32), 40.0, 3) - 0.5) * 0.03)
    rng = np.random.default_rng(seed)
    blobs = []
    for _ in range(26):
        ang = rng.uniform(0, 2 * np.pi)
        rad = rng.uniform(0.0, 0.85)
        blobs.append((rad * np.cos(ang), rng.uniform(1.25, 2.25), rad * np.sin(ang) * 0.8, rng.uniform(0.34, 0.55)))

    def scene(p):
        trunk = sd_round_cone(p, np.array([0.0, 0.0, 0.0], dtype=np.float32), np.array([0.03, 1.5, 0.0], dtype=np.float32), 0.17, 0.09)
        trunk = smin(trunk, sd_round_cone(p, np.array([0.0, 1.2, 0.0], dtype=np.float32), np.array([-0.5, 1.85, 0.1], dtype=np.float32), 0.07, 0.04), 0.06)
        trunk = smin(trunk, sd_round_cone(p, np.array([0.0, 1.3, 0.0], dtype=np.float32), np.array([0.55, 1.9, -0.05], dtype=np.float32), 0.07, 0.04), 0.06)
        # root flare
        trunk = np.minimum(trunk, sd_ellipsoid(p, v3(0, 0.04, 0), v3(0.28, 0.10, 0.28)))
        crown = None
        for (bx, by, bz, br) in blobs:
            d = sd_sphere(p, v3(bx, by, bz), br)
            crown = d if crown is None else smin(crown, d, 0.22)
        crown = crown - (fbm(p, 7.0, 3, seed + 5) - 0.5) * 0.30
        return union([(trunk, 0), (crown, 1)])

    return scene, [bark, leaf]


def render_tree(seed, size=640):
    scene, mats = oak_scene(seed)
    rd = Renderer(scene, mats, 3.4, size=size, view_rot=rot_x(-0.06) @ rot_y(0.2 + 0.4 * (seed % 3)),
                  light_dir=(0.55, 0.65, 0.45), ground_shadow=False, ambient_scale=0.85, key_scale=1.25, gamma=1.8, scurve=0.35)
    rd.margin = 0.95
    rd.autofit()
    img = rd.render()
    a = img[..., 3] > 0.02
    ys, xs = np.nonzero(a)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    return img[box[1]:box[3], box[0]:box[2]]


# --------------------------------------------------------------------------- landscape

def render(W, H, seed=7, theme="meadow"):
    global T
    T = THEMES[theme]
    yh = HORIZON * H
    f = 1.10 * W                     # focal length in px
    E = 1.0                          # eye height
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    img = np.zeros((H, W, 3), dtype=np.float32)
    sun = np.array([0.80 * W, 0.085 * H], dtype=np.float32)
    haze_col = T["haze"]

    # ---------------------------------------------------------------- sky
    t = np.clip(ys / yh, 0.0, 1.0)
    top, mid, hor = T["sky"]
    sky = np.where((t < 0.55)[..., None], top + (mid - top) * (t / 0.55)[..., None],
                   mid + (hor - mid) * (np.clip((t - 0.55) / 0.45, 0, 1) ** 1.3)[..., None])
    d_sun = np.hypot(xs - sun[0], ys - sun[1]) / W
    sky = sky + np.exp(-d_sun * 5.5)[..., None] * np.array([0.55, 0.45, 0.28], dtype=np.float32) * T["sun_glow"]
    sky = sky + (np.exp(-d_sun * 60.0) * 1.3 * T["sun_glow"])[..., None] * np.array([1.0, 0.95, 0.80], dtype=np.float32)
    # clouds on a plane above the viewer (perspective-correct, lit towards the sun)
    dy = np.maximum(yh - ys, 1.0)
    u = (xs - W / 2) / dy * 0.9
    v = f / dy * 0.9 + 3.0

    def cloud_density(uu, vv):
        base = fbm2(uu * 0.55, vv * 0.55, 6, seed + 10)
        return smooth(T["cloud_lo"], T["cloud_hi"], base)

    dens = cloud_density(u, v)
    dens_l = cloud_density(u + 0.12 * np.sign(sun[0] - W / 2) * 1.0, v + 0.05)   # sampled towards the light
    light = np.clip(0.55 + (dens - dens_l) * 3.5, 0.25, 1.15)
    ccol = np.array([0.99, 0.98, 0.96], dtype=np.float32) * light[..., None] + (1 - light)[..., None] * np.array([0.52, 0.60, 0.74], dtype=np.float32)
    fade = smooth(0.0, 0.10, (yh - ys) / H) * (1.0 - 0.0)
    cloud_a = np.clip(dens * 1.05, 0, 1) * fade * 0.96
    sky = sky * (1 - cloud_a[..., None]) + ccol * cloud_a[..., None]
    img[:] = sky

    # ---------------------------------------------------------------- hills (three ridges)
    def ridge(base, amp, freq, sd, col_top, col_bot, tree_scale, haze, tex):
        x = xs[0] / W
        h1 = np.zeros(W, dtype=np.float32)
        for o in range(5):
            h1 += (0.5 ** o) * vnoise2(x * freq * 2 ** o, np.zeros_like(x) + sd + o, sd)
        h1 = h1 / (1 - 0.5 ** 5)
        top_smooth = yh - base * H - amp * H * (h1 - 0.5)
        top_y = top_smooth - tree_scale * H * (vnoise2(x * 380.0, np.zeros_like(x) + sd, sd + 3) - 0.3) * (1 if tex else 0)
        top_row = top_y[None, :]
        inside = ys >= top_row
        depth = np.clip((ys - top_smooth[None, :]) / (0.10 * H), 0, 1)
        slope = np.gradient(top_smooth) * (W / 400.0) / T.get("amp_k", 1.0)
        lit = np.clip(0.62 + 0.9 * (-slope)[None, :] * 0.35, 0.4, 1.0)
        col = col_top[None, None, :] * (1 - depth[..., None]) + col_bot[None, None, :] * depth[..., None]
        col = col * lit[..., None]
        if tex:
            tx = fbm2(xs / W * 140.0, ys / H * 90.0, 4, sd + 20)
            col = col * (0.72 + 0.55 * tx)[..., None]
            blob = fbm2(xs / W * 14.0, ys / H * 9.0, 3, sd + 30)
            col = col * (0.85 + 0.3 * blob)[..., None]
        col = col * (1 - haze) + haze_col[None, None, :] * haze
        m = inside & (ys < yh + 6)
        return col, m

    rc_ = T["ridges"]
    for (base, amp, fr, sd, (ct, cb), ts, hz, tex) in (
        (0.062, 0.050 * T.get("amp_k", 1.0), 1.6, 3, rc_[0], 0.0, 0.50, False),
        (0.036, 0.045 * T.get("amp_k", 1.0), 2.4, 11, rc_[1], 0.004, 0.34, True),
        (0.014, 0.030 * T.get("amp_k", 1.0), 3.2, 23, rc_[2], 0.006, 0.18, True),
    ):
        col, m = ridge(base, amp, fr, sd, ct, cb, ts, hz, tex)
        img = np.where(m[..., None], col, img)

    # ---------------------------------------------------------------- ground (perspective)
    gy = ys - yh
    ground = gy > 0
    gyc = np.maximum(gy, 1.0)
    Z = E * f / gyc
    X = (xs - W / 2) * Z / f
    fw = Z / f * 1.2                       # world size of ~1 px (x direction)
    near = np.clip((gy / (H - yh)), 0, 1)

    n1 = fbm2(X * 0.45, Z * 0.45, 5, seed + 40, fw)
    n2 = fbm2(X * 2.4, Z * 2.4, 5, seed + 50, fw)
    n3 = fbm2(X * 0.18 + 5.0, Z * 0.18, 4, seed + 60, fw)
    dark, midg, lightg, dry = T["ground"]
    g = dark + (midg - dark) * smooth(0.3, 0.62, n1)[..., None]
    g = g + (lightg - g) * (smooth(0.45, 0.8, n2) * 0.7)[..., None]
    g = g + (dry - g) * (smooth(0.62, 0.78, n3) * 0.55)[..., None]
    # grass blades: vertical streaks, strong near the viewer
    blade = vnoise2(xs * 0.32, ys * 0.035, seed + 70) * 0.6 + vnoise2(xs * 0.9, ys * 0.12, seed + 71) * 0.4
    g = g * (1.0 + ((blade - 0.5) * (0.55 * near ** 1.2) * 1.6)[..., None])
    tips = smooth(0.62, 0.9, vnoise2(xs * 0.6, ys * 0.06, seed + 72))
    g = g + tips[..., None] * np.array([0.10, 0.12, 0.02], dtype=np.float32) * near[..., None] ** 1.5

    # wildflowers (world-space cells)
    cell = 0.42
    cx, cz = np.floor(X / cell), np.floor(Z / cell)
    r1, r2, r3 = hash2(cx, cz, 91), hash2(cx, cz, 92), hash2(cx, cz, 93)
    fxp, fzp = (cx + 0.2 + 0.6 * r1) * cell, (cz + 0.2 + 0.6 * r2) * cell
    dist = np.hypot(X - fxp, Z - fzp)
    has = (r3 > 0.90) & (T["flowers"] < 1.0)
    rad = 0.026 + 0.018 * hash2(cx, cz, 94)
    flower_mask = has & (dist < rad) & (Z < 14.0)
    pick = hash2(cx, cz, 95)
    fcol = np.where((pick < 0.45)[..., None], np.array([0.97, 0.93, 0.80], dtype=np.float32),
                    np.where((pick < 0.75)[..., None], np.array([0.98, 0.82, 0.18], dtype=np.float32),
                             np.array([0.62, 0.38, 0.85], dtype=np.float32)))
    fcol = fcol * (1.0 - 0.35 * smooth(0.0, rad, dist))[..., None]
    g = np.where(flower_mask[..., None], fcol, g)
    if T.get("water"):
        wn = fbm2(X * 0.16 + 9.0, Z * 0.16 + 3.0, 4, seed + 160, fw)
        wm = smooth(0.60, 0.64, wn) * smooth(0.10, 0.28, np.abs(X) / np.maximum(Z, 0.5)) * smooth(3.5, 7.0, Z)
        wm = wm * (1.0 - smooth(0.0, 1.0, np.zeros_like(Z)))
        refl = np.clip(Z / 9.0, 0.30, 0.95)[..., None]
        ripple = 0.5 + 0.5 * np.sin(Z * 9.0 + 3.0 * fbm2(X * 1.2, Z * 1.2, 3, seed + 170, fw))
        water = np.array([0.05, 0.09, 0.07], dtype=np.float32) * (1 - refl) + T["haze"] * 1.25 * refl
        water = water * (0.92 + 0.12 * ripple)[..., None]
        # algae / lily film and muddy bank
        film = smooth(0.55, 0.8, fbm2(X * 1.6, Z * 1.6, 4, seed + 180, fw))
        water = water * (1 - 0.55 * film[..., None]) + np.array([0.18, 0.30, 0.08], dtype=np.float32) * (0.55 * film[..., None])
        bank = smooth(0.56, 0.60, wn) * (1 - smooth(0.60, 0.64, wn))
        g = g * (1 - 0.45 * bank[..., None])
        g = g * (1 - wm[..., None]) + water * wm[..., None]
    # clover / short grass tufts: dark centre dots for depth
    tuft = smooth(0.74, 0.92, fbm2(X * 5.5, Z * 5.5, 3, seed + 80, fw))
    g = g * (1.0 - 0.28 * tuft[..., None])

    # dirt path winding through the picture centre (X = 0 at Z = 2.4)
    Zc = np.maximum(Z, 0.2)
    center = 1.15 * np.sin(0.52 * (Zc - 2.4)) * smooth(0.0, 6.0, Zc) + 0.0
    wpath = 0.86 + 0.0 * Z
    edge_n = (fbm2(Z * 1.6, X * 0.0 + 3.0, 4, seed + 100, fw) - 0.5) * 0.16
    dx = np.abs(X - center) - wpath + edge_n
    on_path = 1.0 - smooth(-0.05, 0.10, dx)
    dirt_base, dirt_light = T["dirt"]
    dn = fbm2(X * 3.0, Z * 3.0, 5, seed + 110, fw)
    dirt = dirt_base + (dirt_light - dirt_base) * smooth(0.35, 0.7, dn)[..., None]
    # wheel ruts (darker, slightly damp), and a grassy centre strip
    rut = np.zeros_like(X)
    for side in (-1, 1):
        rx = np.abs(X - center - side * 0.42) - 0.075 + 0.02 * (fbm2(Z * 3.0, X * 0.0, 3, seed + 120, fw) - 0.5)
        rut = np.maximum(rut, 1.0 - smooth(-0.02, 0.05, rx))
    dirt = dirt * (1.0 - 0.38 * rut)[..., None]
    strip = (1.0 - smooth(0.0, 0.16, np.abs(X - center))) * smooth(0.45, 0.7, fbm2(X * 5.0, Z * 2.0, 3, seed + 130, fw))
    strip = strip * 0.0
    dirt = dirt * (1 - strip[..., None]) + g * (0.9) * strip[..., None]
    # pebbles
    pc = 0.16
    px_, pz_ = np.floor(X / pc), np.floor(Z / pc)
    pr = hash2(px_, pz_, 140)
    pxc, pzc = (px_ + 0.25 + 0.5 * hash2(px_, pz_, 141)) * pc, (pz_ + 0.25 + 0.5 * hash2(px_, pz_, 142)) * pc
    pd = np.hypot(X - pxc, Z - pzc)
    peb = (pr > 0.82) & (pd < 0.012 + 0.012 * hash2(px_, pz_, 143))
    pcol = np.array([0.50, 0.46, 0.40], dtype=np.float32) * (0.7 + 0.6 * hash2(px_, pz_, 144))[..., None]
    shade_dir = np.clip((X - pxc) * 6.0, -0.5, 0.5)
    pcol = pcol * (1.0 + 0.5 * shade_dir)[..., None]
    dirt = np.where((peb & (Z < 9.0))[..., None], pcol, dirt)
    # path edge: darker rim where grass overhangs
    rim = smooth(-0.10, 0.0, dx) * (1.0 - smooth(0.0, 0.12, dx))
    g2 = g * (1 - on_path[..., None]) + dirt * on_path[..., None]
    g2 = g2 * (1.0 - 0.25 * rim)[..., None]

    # sunlight: warm key from the right, soft large-scale variation (cloud shadows)
    cloud_sh = 0.86 + 0.28 * fbm2(X * 0.12 + 20.0, Z * 0.12, 4, seed + 150, fw)
    g2 = g2 * cloud_sh[..., None]

    # tree shadows on the ground + trees as sprites
    trees = [] if os.environ.get("NOTREES") else T["trees"]
    _unused = [  # (X, Z, height, seed)
        (-5.6, 9.0, 4.4, 1), (6.2, 10.5, 4.8, 2), (-8.8, 14.5, 5.2, 3), (9.8, 16.0, 5.2, 4),
        (-12.5, 22.0, 5.8, 5), (13.2, 25.0, 5.8, 6), (-3.8, 33.0, 6.0, 7), (4.8, 39.0, 6.0, 8),
        (-18.0, 31.0, 6.4, 9), (19.0, 35.0, 6.4, 10),
    ]
    shadow = np.zeros((H, W), dtype=np.float32)
    for (tx, tz, th, _s) in trees:
        sx0, sz0 = tx - 0.55 * th, tz - 0.30 * th        # shadow falls left/towards the viewer
        sw, sl = 0.55 * th, 0.80 * th
        shadow = np.maximum(shadow, np.clip(1.0 - np.hypot((X - sx0) / sw, (Z - sz0) / sl), 0, 1) ** 0.6)
    g2 = g2 * (1.0 - T["tree_shadow"] * shadow[..., None])

    # aerial perspective on the ground
    fog = 1.0 - np.exp(-Z * T["fog"])
    g2 = g2 * (1 - fog[..., None] * 0.85) + haze_col * (fog[..., None] * 0.85)
    img = np.where(ground[..., None], g2, img)

    # horizon haze blend
    hz = np.exp(-((ys - yh) / (0.022 * H)) ** 2)
    img = img * (1 - hz[..., None] * 0.40) + haze_col * (hz[..., None] * 0.40)

    # trees (back to front)
    for (tx, tz, th, ts) in sorted(trees, key=lambda t: -t[1]):
        spr = render_tree(ts)
        sh, sw_ = spr.shape[:2]
        px = (W / 2 + tx * f / tz)
        py = yh + E * f / tz
        hp = th * f / tz
        wp = hp * sw_ / sh
        if wp < 6 or px + wp / 2 < 0 or px - wp / 2 > W:
            continue
        # footprint: trunk base at sprite bottom
        pre = spr.copy()
        pre[..., :3] *= pre[..., 3:4]
        im = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((max(int(wp), 2), max(int(hp), 2)), Image.LANCZOS)
        r = np.asarray(im, dtype=np.float32) / 255.0
        a = r[..., 3:4]
        rgb = r[..., :3] / np.maximum(a, 1e-4)
        fogt = float(1.0 - np.exp(-tz * T["fog"])) * 0.85
        rgb = rgb * (1 - fogt) + haze_col * fogt
        x0, y0 = int(px - rgb.shape[1] / 2), int(py - rgb.shape[0])
        xa, ya = max(x0, 0), max(y0, 0)
        xb, yb = min(x0 + rgb.shape[1], W), min(y0 + rgb.shape[0], H)
        if xb <= xa or yb <= ya:
            continue
        sub = (slice(ya, yb), slice(xa, xb))
        src = (slice(ya - y0, yb - y0), slice(xa - x0, xb - x0))
        img[sub] = img[sub] * (1 - a[src]) + rgb[src] * a[src]

    # grade: warm sun, gentle contrast, vignette, film grain
    img = img * T["grade"]
    img = np.clip(img, 0, 1.2)
    img = img / (1.0 + img * 0.10)
    v = np.clip(img, 0, 1) ** (1 / 1.04)
    v = v + 0.35 * (v * v * (3 - 2 * v) - v)
    rng = np.random.default_rng(seed)
    v = v + rng.normal(0, 0.010, size=v.shape).astype(np.float32)
    return np.clip(v, 0, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--width", type=int, default=1080)
    ap.add_argument("--height", type=int, default=1800)
    ap.add_argument("--theme", default="meadow", choices=list(THEMES))
    ap.add_argument("--out", default=None)
    ap.add_argument("--quality", type=int, default=86)
    a = ap.parse_args()
    a.out = a.out or os.path.join(os.path.dirname(DEFAULT_OUT), THEMES[a.theme]["out"])
    img = render(a.width, a.height, theme=a.theme)
    im = Image.fromarray((img * 255).astype(np.uint8), "RGB")
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    if a.out.lower().endswith(".webp"):
        im.save(a.out, "WEBP", quality=a.quality, method=6)
    else:
        im.save(a.out)
    print(a.out, os.path.getsize(a.out) // 1024, "KB")


if __name__ == "__main__":
    main()
