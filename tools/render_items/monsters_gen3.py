"""Neutral body plans for the handmade zones 2-5: mushroom, toad, wisp, bat + bosses treant, hydra, yeti, dragon.

Same pipeline as monsters_gen.py (grey sprite + glow layer, tinted by a fixed palette in the app).
"""
from __future__ import annotations

import numpy as np

from monsters import bone_material, ell, fur_material, limb, part, rc, rot_local, shaggy, wet_black
from monsters_gen import GLOW, _u, glow_mat, horn, skin_mat
from sdf import Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_ellipsoid, sd_sphere, smax, smin, v3, value_noise


def _poly_membrane(q, sx, pts, bend=0.06, z0=0.10):
    x = q[:, 0] * sx
    y = q[:, 1]
    z = q[:, 2] + bend * x * x + z0
    d = np.full(q.shape[0], -1e3, dtype=np.float32)
    for i in range(len(pts)):
        a = np.array(pts[i], dtype=np.float32)
        b = np.array(pts[(i + 1) % len(pts)], dtype=np.float32)
        e = b - a
        nrm = np.array([-e[1], e[0]], dtype=np.float32) / np.linalg.norm(e)
        d = np.maximum(d, (x - a[0]) * nrm[0] + (y - a[1]) * nrm[1])
    return np.maximum(d, np.abs(z) - 0.008) * 0.7


def _membrane_mat():
    return Material((0.52, 0.52, 0.52), rough=0.7, spec=0.12, sss=0.9, edge=((1.0, 1.0, 1.0), 2.0, 0.4),
                    bump=lambda p: (value_noise(p * np.array([1.0, 1.0, 4.0], dtype=np.float32), 30.0, 5) - 0.5) * 0.004,
                    tint_fn=lambda p, a: a * (0.55 + 0.7 * fbm(p, 7.0, 3, 12))[:, None])


def bark_mat():
    st = np.array([1.0, 0.25, 1.0], dtype=np.float32)
    return Material((0.55, 0.55, 0.55), rough=0.92, spec=0.08,
                    bump=lambda p: (value_noise(p * st, 22.0, 4) - 0.5) * 0.045 + (fbm(p, 60.0, 2, 8) - 0.5) * 0.008,
                    tint_fn=lambda p, a: a * (0.55 + 0.8 * value_noise(p * st, 9.0, 6))[:, None])


# --------------------------------------------------------------------------- mushroom

def gen_mushroom():
    mats = [skin_mat(0.55, 0.60), glow_mat(), bone_material((0.90, 0.90, 0.90)), wet_black()]
    GLOW["gen_mushroom"] = 1

    def scene(p):
        stem = rc(p, (0, 0.06, 0), (0, 0.72, 0), 0.25, 0.20)
        stem = smin(stem, ell(p, (0, 0.30, 0.0), (0.30, 0.30, 0.28)), 0.12)
        feet = np.minimum(ell(p, (0.13, 0.05, 0.12), (0.11, 0.065, 0.15)), ell(p, (-0.13, 0.05, 0.12), (0.11, 0.065, 0.15)))
        arms = np.minimum.reduce([limb(p, [(sx * 0.23, 0.50, 0.0), (sx * 0.40, 0.38, 0.10), (sx * 0.43, 0.20, 0.13)], [0.065, 0.055, 0.05]) for sx in (-1, 1)])
        hands = np.minimum(ell(p, (0.43, 0.15, 0.14), (0.06, 0.07, 0.06)), ell(p, (-0.43, 0.15, 0.14), (0.06, 0.07, 0.06)))
        cap = ell(p, (0, 0.98, 0), (0.60, 0.34, 0.60))
        cap = smax(cap, -(p[:, 1] - 0.70) * -1.0 - 0.0, 0.0) if False else np.maximum(cap, 0.70 - p[:, 1])
        cap = cap - (fbm(p, 6.0, 3, 5) - 0.5) * 0.04
        spots = part(p, (0, 1.1, 0), 0.7, lambda q: np.minimum.reduce([
            sd_sphere(q, v3(x, y, z), r) for (x, y, z, r) in
            ((0.26, 1.18, 0.22, 0.09), (-0.30, 1.10, 0.12, 0.10), (0.05, 1.28, -0.22, 0.08), (0.12, 1.14, 0.42, 0.075), (-0.18, 1.20, -0.30, 0.07), (0.38, 1.02, -0.10, 0.065))]))
        eyes = np.minimum(ell(p, (0.085, 0.47, 0.215), (0.036, 0.046, 0.022)), ell(p, (-0.085, 0.47, 0.215), (0.036, 0.046, 0.022)))
        mouth = limb(p, [(-0.09, 0.30, 0.23), (0.0, 0.265, 0.245), (0.09, 0.30, 0.23)], [0.018, 0.018, 0.018])
        return _u([(np.minimum.reduce([stem, feet, arms, hands, cap]), 0), (eyes, 1), (spots, 2), (mouth, 3)])

    return scene, mats, 1.35, rot_x(0.22) @ rot_y(0.18)


# --------------------------------------------------------------------------- toad

def gen_toad():
    mats = [Material((0.58, 0.58, 0.58), rough=0.35, spec=0.7, sss=0.5,
                     bump=lambda p: (value_noise(p, 24.0, 4) - 0.5) * 0.034 + (fbm(p, 70.0, 2, 6) - 0.5) * 0.004,
                     tint_fn=lambda p, a: a * (0.55 + 0.9 * fbm(p, 5.0, 3, 3))[:, None]), glow_mat(), wet_black()]
    GLOW["gen_toad"] = 1

    def scene(p):
        body = ell(p, (0, 0.42, -0.08), (0.54, 0.38, 0.52))
        head = ell(p, (0, 0.54, 0.38), (0.42, 0.27, 0.34))
        sac = ell(p, (0, 0.33, 0.58), (0.26, 0.17, 0.20))
        body = smin(smin(body, head, 0.14), sac, 0.10)
        front = np.minimum.reduce([limb(p, [(sx * 0.34, 0.34, 0.30), (sx * 0.40, 0.14, 0.45)], [0.13, 0.085]) for sx in (-1, 1)])
        hind = np.minimum.reduce([limb(p, [(sx * 0.44, 0.36, -0.16), (sx * 0.66, 0.14, -0.02), (sx * 0.52, 0.045, 0.34)], [0.19, 0.12, 0.065]) for sx in (-1, 1)])
        feet = np.minimum.reduce([ell(p, (sx * 0.42, 0.035, 0.52), (0.13, 0.035, 0.11)) for sx in (-1, 1)]
                                 + [ell(p, (sx * 0.52, 0.035, 0.42), (0.16, 0.035, 0.13)) for sx in (-1, 1)])
        eyes_ball = np.minimum(sd_sphere(p, v3(0.23, 0.80, 0.42), 0.125), sd_sphere(p, v3(-0.23, 0.80, 0.42), 0.125))
        lids = np.minimum(ell(p, (0.23, 0.84, 0.40), (0.15, 0.09, 0.15)), ell(p, (-0.23, 0.84, 0.40), (0.15, 0.09, 0.15)))
        slits = np.minimum(ell(p, (0.23, 0.80, 0.545), (0.012, 0.075, 0.02)), ell(p, (-0.23, 0.80, 0.545), (0.012, 0.075, 0.02)))
        mouth = limb(p, [(-0.34, 0.47, 0.62), (-0.17, 0.43, 0.70), (0.17, 0.43, 0.70), (0.34, 0.47, 0.62)], [0.02, 0.02, 0.02, 0.02])
        skin = np.minimum.reduce([body, front, hind, feet, lids])
        return _u([(skin, 0), (eyes_ball, 1), (slits, 2), (mouth, 2)])

    return scene, mats, 1.35, rot_x(0.30) @ rot_y(0.20)


# --------------------------------------------------------------------------- wisp

def gen_wisp():
    wisp = Material((0.78, 0.78, 0.78), rough=0.3, spec=0.5, emissive=0.75, sss=1.2, edge=((1.0, 1.0, 1.0), 1.4, 0.85),
                    tint_fn=lambda p, a: a * (0.55 + 0.7 * fbm(p * np.array([1.0, 0.6, 1.0], dtype=np.float32), 5.0, 3, 9))[:, None])
    mats = [wisp, glow_mat(2.2), wet_black()]
    GLOW["gen_wisp"] = 1

    def scene(p):
        body = ell(p, (0, 0.86, 0), (0.30, 0.40, 0.28))
        tail = limb(p, [(0, 0.62, 0), (0.06, 0.38, -0.08), (-0.05, 0.16, -0.16), (0.03, 0.02, -0.30)], [0.22, 0.15, 0.085, 0.02])
        flame = part(p, (0, 1.1, 0), 0.6, lambda q: np.minimum.reduce([
            rc(q, (0.10 * k, 1.05, 0.0), (0.16 * k + 0.02, 1.38 + 0.05 * abs(k), -0.05), 0.07, 0.006) for k in (-1.4, -0.7, 0.0, 0.7, 1.4)]))
        skin = smin(smin(body, tail, 0.12), flame, 0.05)
        eyes_hollow = np.minimum(ell(p, (0.10, 0.92, 0.22), (0.055, 0.085, 0.04), rz=0.2), ell(p, (-0.10, 0.92, 0.22), (0.055, 0.085, 0.04), rz=-0.2))
        core = sd_sphere(p, v3(0, 0.84, 0.0), 0.15)
        orbs = part(p, (0, 0.9, 0), 1.0, lambda q: np.minimum.reduce([
            sd_sphere(q, v3(0.50 * np.cos(a), 0.55 + 0.17 * i, 0.50 * np.sin(a)), 0.035) for i, a in enumerate(np.linspace(0.4, 5.6, 5))]))
        return _u([(skin, 0), (np.minimum(core * 1.0 + 0.0, orbs), 1), (eyes_hollow, 2)])

    return scene, mats, 1.1, rot_x(0.10) @ rot_y(0.10)


# --------------------------------------------------------------------------- bat

def gen_bat():
    mats = [fur_material((0.55, 0.55, 0.55), belly=1.2, dirt=0.3, y_ref=0.8), glow_mat(), bone_material((0.88, 0.88, 0.88)), _membrane_mat()]
    GLOW["gen_bat"] = 1
    pts = [(0.12, 0.88), (0.80, 1.34), (1.30, 1.00), (1.24, 0.66), (0.98, 0.40), (0.14, 0.52)]

    def scene(p):
        body = ell(p, (0, 0.86, 0), (0.20, 0.27, 0.18))
        head = ell(p, (0, 1.16, 0.10), (0.15, 0.14, 0.15))
        snout = rc(p, (0, 1.12, 0.18), (0, 1.06, 0.32), 0.07, 0.04)
        ears = np.minimum(ell(p, (0.11, 1.38, 0.06), (0.06, 0.20, 0.016), rz=-0.18), ell(p, (-0.11, 1.38, 0.06), (0.06, 0.20, 0.016), rz=0.18))
        fur = smin(smin(body, head, 0.07), snout, 0.04) - shaggy(p, 0.010, 30.0)
        fur = np.minimum(fur, ears)
        legs = np.minimum.reduce([limb(p, [(sx * 0.08, 0.64, 0.0), (sx * 0.11, 0.42, 0.06), (sx * 0.09, 0.26, 0.10)], [0.055, 0.04, 0.026]) for sx in (-1, 1)])
        claws = part(p, (0, 0.24, 0.12), 0.4, lambda t: np.minimum.reduce([
            rc(t, (sx * 0.09 + dx, 0.27, 0.10), (sx * 0.09 + dx * 1.4, 0.19, 0.16), 0.012, 0.003) for sx in (-1, 1) for dx in (-0.025, 0.0, 0.025)]))
        arms = part(p, (0, 1.0, -0.05), 1.8, lambda t: np.minimum.reduce(
            [limb(t, [(sx * 0.16, 0.95, 0.0), (sx * 0.52, 1.14, -0.04), (sx * 0.80, 1.34, -0.08)], [0.040, 0.030, 0.024]) for sx in (-1, 1)]
            + [limb(t, [(sx * 0.80, 1.34, -0.08), (sx * 1.30, 1.00, -0.14)], [0.020, 0.010]) for sx in (-1, 1)]
            + [limb(t, [(sx * 0.80, 1.34, -0.08), (sx * 1.24, 0.66, -0.14)], [0.020, 0.010]) for sx in (-1, 1)]
            + [limb(t, [(sx * 0.80, 1.34, -0.08), (sx * 0.98, 0.40, -0.12)], [0.020, 0.010]) for sx in (-1, 1)]), margin=0.2)
        mem = np.minimum(_poly_membrane(p, 1.0, pts), _poly_membrane(p, -1.0, pts))
        eyes = np.minimum(ell(p, (0.065, 1.20, 0.235), (0.026, 0.026, 0.016)), ell(p, (-0.065, 1.20, 0.235), (0.026, 0.026, 0.016)))
        fangs = np.minimum(rc(p, (0.028, 1.07, 0.30), (0.028, 1.00, 0.32), 0.012, 0.003), rc(p, (-0.028, 1.07, 0.30), (-0.028, 1.00, 0.32), 0.012, 0.003))
        return _u([(np.minimum(fur, np.minimum(legs, arms)), 0), (eyes, 1), (np.minimum(fangs, claws), 2), (mem, 3)])

    return scene, mats, 1.65, rot_x(0.10) @ rot_y(0.14)


# --------------------------------------------------------------------------- treant (boss)

def gen_treant():
    leaf = Material((0.58, 0.58, 0.58), rough=0.8, spec=0.1, sss=1.0,
                    bump=lambda p: (fbm(p, 40.0, 3, 17) - 0.5) * 0.05,
                    tint_fn=lambda p, a: a * (0.5 + 0.9 * fbm(p, 4.5, 3, 5))[:, None])
    mats = [bark_mat(), glow_mat(2.0), leaf, wet_black()]
    GLOW["gen_treant"] = 1
    rng = np.random.default_rng(7)
    blobs = [(rng.uniform(-0.6, 0.6), rng.uniform(1.95, 2.55), rng.uniform(-0.5, 0.5), rng.uniform(0.34, 0.52)) for _ in range(16)]

    def scene(p):
        trunk = rc(p, (0, 0.05, 0), (0.02, 1.35, 0.0), 0.44, 0.34)
        trunk = smin(trunk, ell(p, (0, 0.20, 0.0), (0.52, 0.22, 0.50)), 0.15)
        trunk = trunk - (fbm(p, 5.0, 3, 4) - 0.5) * 0.06
        head = ell(p, (0.0, 1.55, 0.06), (0.34, 0.32, 0.30))
        trunk = smin(trunk, head, 0.12)
        arms = np.minimum.reduce([limb(p, [(sx * 0.32, 1.30, 0.0), (sx * 0.68, 1.05, 0.22), (sx * 0.92, 0.62, 0.36)], [0.16, 0.115, 0.08]) for sx in (-1, 1)])
        twigs = part(p, (0, 0.8, 0.3), 1.2, lambda q: np.minimum.reduce([
            limb(q, [(sx * 0.92, 0.62, 0.36), (sx * (0.98 + 0.06 * k), 0.46 - 0.03 * abs(k), 0.42 + 0.05 * k), (sx * (1.02 + 0.10 * k), 0.34, 0.46 + 0.08 * k)], [0.04, 0.03, 0.012])
            for sx in (-1, 1) for k in (-1, 0, 1)]), margin=0.2)
        roots = part(p, (0, 0.05, 0.2), 1.3, lambda q: np.minimum.reduce([
            limb(q, [(sx * 0.32, 0.16, 0.18 * z), (sx * 0.70, 0.07, 0.36 * z), (sx * 0.98, 0.015, 0.50 * z)], [0.16, 0.10, 0.03]) for sx in (-1, 1) for z in (-0.6, 0.6, 1.2)]), margin=0.2)
        wood = np.minimum.reduce([trunk, arms, twigs, roots])
        crown = part(p, (0, 2.2, 0), 1.0, lambda q: _crown(q, blobs))
        eye_hollow = np.minimum(ell(p, (0.14, 1.62, 0.28), (0.10, 0.065, 0.08), rz=0.3), ell(p, (-0.14, 1.62, 0.28), (0.10, 0.065, 0.08), rz=-0.3))
        eyes = np.minimum(ell(p, (0.14, 1.62, 0.33), (0.058, 0.032, 0.03), rz=0.3), ell(p, (-0.14, 1.62, 0.33), (0.058, 0.032, 0.03), rz=-0.3))
        mouth = ell(p, (0.0, 1.38, 0.33), (0.13, 0.055, 0.05))
        return _u([(wood, 0), (crown, 2), (np.minimum(eye_hollow, mouth), 3), (eyes, 1)])

    return scene, mats, 2.7, rot_x(0.12) @ rot_y(0.16)


def _crown(q, blobs):
    d = None
    for (bx, by, bz, br) in blobs:
        s = sd_sphere(q, v3(bx, by, bz), br)
        d = s if d is None else smin(d, s, 0.22)
    return d - (fbm(q, 8.0, 3, 5) - 0.5) * 0.26


# --------------------------------------------------------------------------- hydra (boss)

def gen_hydra():
    scale = Material((0.58, 0.58, 0.58), rough=0.45, spec=0.5,
                     bump=lambda p: np.abs(value_noise(p, 36.0, 4) - 0.5) * 0.014,
                     tint_fn=lambda p, a: a * (0.55 + 0.9 * np.abs(np.sin(p[:, 1] * 20.0 + p[:, 0] * 5.0)) * 0.6 + 0.2 * fbm(p, 10.0, 3, 5))[:, None])
    mats = [scale, glow_mat(), bone_material((0.9, 0.9, 0.9)), wet_black()]
    GLOW["gen_hydra"] = 1
    necks = [
        [(-0.28, 0.45, 0.0), (-0.46, 0.95, 0.14), (-0.50, 1.38, 0.30), (-0.44, 1.62, 0.52)],
        [(0.0, 0.50, 0.10), (0.04, 1.10, 0.28), (0.0, 1.55, 0.48), (0.0, 1.84, 0.70)],
        [(0.28, 0.45, 0.0), (0.48, 0.92, 0.16), (0.54, 1.30, 0.34), (0.46, 1.54, 0.56)],
    ]

    def scene(p):
        body = ell(p, (0, 0.34, -0.12), (0.58, 0.30, 0.52))
        legs = part(p, (0, 0.1, 0.0), 1.0, lambda q: np.minimum.reduce([ell(q, (sx * 0.40, 0.07, z), (0.13, 0.07, 0.17)) for sx in (-1, 1) for z in (0.20, -0.30)]))
        tail = limb(p, [(0, 0.30, -0.55), (0.10, 0.20, -0.90), (-0.12, 0.10, -1.20)], [0.20, 0.12, 0.03])
        chain = []
        for n in necks:
            chain.append(limb(p, n, [0.16, 0.12, 0.11, 0.115]))
        heads = []
        for n in necks:
            hx, hy, hz = n[-1]
            heads.append(ell(p, (hx, hy + 0.06, hz + 0.06), (0.14, 0.12, 0.20)))
            heads.append(rc(p, (hx, hy + 0.03, hz + 0.14), (hx, hy - 0.04, hz + 0.34), 0.085, 0.045))
            heads.append(rc(p, (hx, hy - 0.04, hz + 0.12), (hx, hy - 0.12, hz + 0.30), 0.06, 0.03))
        skin = smin(np.minimum.reduce([body, legs, tail] + chain), np.minimum.reduce(heads), 0.05)
        eyes = np.minimum.reduce([ell(p, (n[-1][0] + sx * 0.075, n[-1][1] + 0.12, n[-1][2] + 0.17), (0.028, 0.02, 0.02), rz=sx * 0.35) for n in necks for sx in (-1, 1)])
        fangs = part(p, (0, 1.5, 0.6), 1.2, lambda q: np.minimum.reduce([
            rc(q, (n[-1][0] + sx * 0.03, n[-1][1] - 0.05, n[-1][2] + 0.30), (n[-1][0] + sx * 0.03, n[-1][1] - 0.17, n[-1][2] + 0.32), 0.014, 0.003)
            for n in necks for sx in (-1, 1)]), margin=0.2)
        frills = part(p, (0, 1.4, 0.4), 1.2, lambda q: np.minimum.reduce([
            horn(q, (n[-1][0] + sx * 0.09, n[-1][1] + 0.12, n[-1][2] - 0.02), (n[-1][0] + sx * 0.18, n[-1][1] + 0.34, n[-1][2] - 0.10), 0.035)
            for n in necks for sx in (-1, 1)]), margin=0.2)
        return _u([(skin, 0), (eyes, 1), (np.minimum(fangs, frills), 2)])

    return scene, mats, 2.1, rot_x(0.16) @ rot_y(0.16)


# --------------------------------------------------------------------------- yeti (boss)

def gen_yeti():
    mats = [fur_material((0.62, 0.62, 0.62), belly=1.15, dirt=0.25, strand=0.011, clump=0.02, y_ref=1.6),
            glow_mat(), bone_material((0.92, 0.92, 0.92)), Material((0.20, 0.20, 0.20), rough=0.5, spec=0.3)]
    GLOW["gen_yeti"] = 1

    def scene(p):
        body = rc(p, (0, 0.62, -0.05), (0, 1.30, 0.06), 0.52, 0.46)
        body = smin(body, ell(p, (0, 1.30, 0.0), (0.72, 0.30, 0.38)), 0.14)          # shoulders
        belly = ell(p, (0, 0.80, 0.18), (0.40, 0.45, 0.34))
        head = ell(p, (0, 1.64, 0.20), (0.30, 0.28, 0.28))
        brow = ell(p, (0, 1.74, 0.34), (0.30, 0.09, 0.14))
        body = smin(smin(smin(body, belly, 0.10), head, 0.12), brow, 0.08)
        arms = np.minimum.reduce([limb(p, [(sx * 0.62, 1.30, 0.0), (sx * 0.86, 0.82, 0.14), (sx * 0.84, 0.30, 0.30)], [0.24, 0.20, 0.19]) for sx in (-1, 1)])
        fists = np.minimum(ell(p, (0.84, 0.22, 0.34), (0.20, 0.17, 0.20)), ell(p, (-0.84, 0.22, 0.34), (0.20, 0.17, 0.20)))
        legs = np.minimum.reduce([limb(p, [(sx * 0.28, 0.62, 0.0), (sx * 0.31, 0.30, 0.06), (sx * 0.30, 0.07, 0.12)], [0.24, 0.20, 0.17]) for sx in (-1, 1)])
        feet = np.minimum(ell(p, (0.30, 0.06, 0.20), (0.20, 0.07, 0.26)), ell(p, (-0.30, 0.06, 0.20), (0.20, 0.07, 0.26)))
        fur = np.minimum.reduce([body, arms, fists, legs, feet]) - shaggy(p, 0.040, 16.0, stretch=(1.0, 2.5, 1.0), seed=21)
        face = ell(p, (0, 1.58, 0.38), (0.19, 0.17, 0.10))
        eyes = np.minimum(ell(p, (0.09, 1.68, 0.44), (0.035, 0.02, 0.02), rz=0.3), ell(p, (-0.09, 1.68, 0.44), (0.035, 0.02, 0.02), rz=-0.3))
        fangs = part(p, (0, 1.5, 0.45), 0.4, lambda q: np.minimum.reduce([
            rc(q, (x, 1.50, 0.46), (x * 1.1, 1.38 if abs(x) < 0.1 else 1.42, 0.50), 0.026, 0.004) for x in (-0.11, -0.05, 0.05, 0.11)]))
        claws = part(p, (0, 0.2, 0.4), 1.2, lambda q: np.minimum.reduce([
            rc(q, (sx * 0.84 + dx, 0.12, 0.46), (sx * 0.84 + dx * 1.3, 0.06, 0.56), 0.030, 0.005) for sx in (-1, 1) for dx in (-0.08, 0.0, 0.08)]))
        return _u([(fur, 0), (eyes, 1), (np.minimum(fangs, claws), 2), (face, 3)])

    return scene, mats, 2.1, rot_x(0.16) @ rot_y(0.14)


# --------------------------------------------------------------------------- dragon (boss)

def gen_dragon():
    scale = Material((0.58, 0.58, 0.58), rough=0.42, spec=0.55,
                     bump=lambda p: np.abs(value_noise(p, 34.0, 4) - 0.5) * 0.016,
                     tint_fn=lambda p, a: a * (0.5 + 0.9 * fbm(p, 8.0, 3, 6) + 0.25 * np.abs(np.sin(p[:, 2] * 24.0)))[:, None])
    mats = [scale, glow_mat(2.0), bone_material((0.9, 0.9, 0.9)), _membrane_mat()]
    GLOW["gen_dragon"] = 1
    wing = [(0.30, 1.05), (1.00, 1.95), (1.80, 1.62), (1.74, 1.22), (1.46, 0.90), (0.34, 0.78)]

    def scene(p):
        body = rc(p, (0, 0.78, -0.62), (0, 0.98, 0.14), 0.36, 0.40)
        body = smin(body, ell(p, (0, 0.80, -0.30), (0.40, 0.40, 0.55)), 0.14)
        neck = limb(p, [(0, 1.02, 0.14), (0, 1.30, 0.44), (0, 1.58, 0.52)], [0.25, 0.19, 0.16])
        head = ell(p, (0, 1.70, 0.62), (0.17, 0.16, 0.22))
        snout = rc(p, (0, 1.66, 0.70), (0, 1.58, 1.00), 0.12, 0.075)
        jaw = rc(p, (0, 1.58, 0.70), (0, 1.43, 0.94), 0.07, 0.034)
        legs = part(p, (0, 0.4, 0.0), 1.4, lambda q: np.minimum.reduce(
            [limb(q, [(sx * 0.34, 0.80, 0.20), (sx * 0.40, 0.46, 0.34), (sx * 0.38, 0.07, 0.40)], [0.14, 0.10, 0.07]) for sx in (-1, 1)]
            + [limb(q, [(sx * 0.38, 0.78, -0.62), (sx * 0.48, 0.42, -0.50), (sx * 0.42, 0.07, -0.46)], [0.17, 0.12, 0.07]) for sx in (-1, 1)]
            + [ell(q, (sx * 0.38, 0.045, 0.52), (0.09, 0.045, 0.13)) for sx in (-1, 1)]
            + [ell(q, (sx * 0.42, 0.045, -0.32), (0.10, 0.045, 0.14)) for sx in (-1, 1)]), margin=0.2)
        tail = limb(p, [(0, 0.74, -1.05), (0.16, 0.50, -1.50), (-0.12, 0.30, -1.90), (0.10, 0.20, -2.30)], [0.22, 0.14, 0.08, 0.02])
        skin = smin(np.minimum.reduce([body, neck, legs, tail]), np.minimum.reduce([head, snout, jaw]), 0.06)
        horns = np.minimum.reduce([limb(p, [(sx * 0.10, 1.82, 0.54), (sx * 0.20, 2.02, 0.28), (sx * 0.26, 2.04, 0.04)], [0.050, 0.035, 0.008]) for sx in (-1, 1)])
        spikes = part(p, (0, 1.2, -0.4), 1.6, lambda q: np.minimum.reduce([
            horn(q, (0, 1.12 - 0.05 * k + (0.2 if k < 3 else 0) * 0, 0.30 - 0.30 * k), (0, 1.34 - 0.04 * k, 0.26 - 0.30 * k), 0.045) for k in range(6)]), margin=0.2)
        teeth = part(p, (0, 1.5, 0.9), 0.5, lambda q: np.minimum.reduce([
            rc(q, (x, 1.60, 0.96 - 0.05 * abs(x) * 6), (x, 1.50, 0.98 - 0.05 * abs(x) * 6), 0.016, 0.003) for x in (-0.06, -0.03, 0.03, 0.06)]
            + [rc(q, (x, 1.47, 0.92), (x, 1.54, 0.94), 0.013, 0.003) for x in (-0.04, 0.0, 0.04)]))
        claws = part(p, (0, 0.06, 0.1), 1.4, lambda q: np.minimum.reduce([
            rc(q, (sx * 0.38 + dx, 0.06, 0.52 + z), (sx * 0.38 + dx * 1.3, 0.02, 0.60 + z), 0.022, 0.004) for sx in (-1, 1) for dx in (-0.05, 0.0, 0.05) for z in (0.0, -0.84)]), margin=0.2)
        eyes = np.minimum(ell(p, (0.09, 1.76, 0.78), (0.040, 0.025, 0.025), rz=0.4), ell(p, (-0.09, 1.76, 0.78), (0.040, 0.025, 0.025), rz=-0.4))
        wings = part(p, (0, 1.4, -0.4), 2.6, lambda q: np.minimum.reduce(
            [limb(q, [(sx * 0.28, 1.05, -0.30), (sx * 0.98, 1.95, -0.40), (sx * 1.80, 1.62, -0.50)], [0.06, 0.045, 0.03]) for sx in (-1, 1)]
            + [limb(q, [(sx * 1.00, 1.95, -0.40), (sx * 1.74, 1.22, -0.46)], [0.03, 0.012]) for sx in (-1, 1)]
            + [limb(q, [(sx * 1.00, 1.95, -0.40), (sx * 1.46, 0.90, -0.44)], [0.03, 0.012]) for sx in (-1, 1)]), margin=0.2)
        mem = np.minimum(_poly_membrane(p + v3(0, 0, 0.42), 1.0, wing, bend=0.05, z0=0.0), _poly_membrane(p + v3(0, 0, 0.42), -1.0, wing, bend=0.05, z0=0.0))
        return _u([(np.minimum(skin, wings), 0), (eyes, 1), (np.minimum.reduce([horns, spikes, teeth, claws]), 2), (mem, 3)])

    return scene, mats, 2.7, rot_x(0.18) @ rot_y(0.30)


BUILDERS = {
    "gen_mushroom": gen_mushroom, "gen_toad": gen_toad, "gen_wisp": gen_wisp, "gen_bat": gen_bat,
    "gen_treant": gen_treant, "gen_hydra": gen_hydra, "gen_yeti": gen_yeti, "gen_dragon": gen_dragon,
}
