"""Body plans 5-8 of the generated zones: humanoid, winged, serpent, wraith (see monsters_gen.py)."""
from __future__ import annotations

import numpy as np

from items import fold_polar
from monsters import bone_material, ell, limb, part, rc, rot_local, wet_black
from monsters_gen import GLOW, _u, glow_mat, horn, skin_mat
from sdf import Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_ellipsoid, sd_sphere, smax, smin, v3, value_noise


def cloth_mat():
    return Material((0.50, 0.50, 0.50), rough=0.92, spec=0.06, sss=0.1,
                    bump=lambda p: (fbm(p, 45.0, 3, 4) - 0.5) * 0.008,
                    tint_fn=lambda p, a: a * (0.6 + 0.8 * fbm(p, 5.0, 3, 6))[:, None])


def plate_mat():
    return Material((0.50, 0.50, 0.52), metal=0.7, rough=0.42, spec=0.8,
                    bump=lambda p: (fbm(p, 35.0, 3, 9) - 0.5) * 0.006)


# --------------------------------------------------------------------------- humanoid

def gen_humanoid(boss=False):
    s = 1.2 if boss else 1.0
    mats = [cloth_mat(), glow_mat(), bone_material((0.86, 0.86, 0.86)), wet_black(), plate_mat(), skin_mat(0.6, 0.62)]
    GLOW["gen_humanoid_boss" if boss else "gen_humanoid"] = 1

    def scene(p):
        q = p / s
        legs = np.minimum.reduce([limb(q, [(sx * 0.14, 0.92, 0.0), (sx * 0.15, 0.50, 0.04), (sx * 0.16, 0.07, 0.07)], [0.115, 0.085, 0.065]) for sx in (-1, 1)])
        boots = np.minimum(ell(q, (0.16, 0.05, 0.12), (0.085, 0.05, 0.15)), ell(q, (-0.16, 0.05, 0.12), (0.085, 0.05, 0.15)))
        torso = rc(q, (0, 0.92, 0.0), (0, 1.40, 0.0), 0.25, 0.28)
        torso = smin(torso, ell(q, (0, 1.12, 0.0), (0.27, 0.34, 0.20)), 0.1)
        arms = np.minimum.reduce([limb(q, [(sx * 0.36, 1.36, 0.0), (sx * 0.45, 1.02, 0.08), (sx * 0.41, 0.72, 0.18)], [0.085, 0.068, 0.058]) for sx in (-1, 1)])
        hands = np.minimum(ell(q, (0.41, 0.66, 0.21), (0.06, 0.08, 0.05)), ell(q, (-0.41, 0.66, 0.21), (0.06, 0.08, 0.05)))
        claws = part(q, (0, 0.62, 0.24), 0.8, lambda t: np.minimum.reduce([
            rc(t, (sx * 0.41 + dx, 0.60, 0.22), (sx * 0.41 + dx * 1.4, 0.50, 0.27), 0.014, 0.003) for sx in (-1, 1) for dx in (-0.03, 0.0, 0.03)]))
        cloak = rc(q, (0, 1.38, -0.16), (0, 0.22, -0.30), 0.27, 0.40) - (fbm(q, 6.0, 3, 3) - 0.5) * 0.06
        cloak = smax(cloak, -ell(q, (0, 0.8, 0.12), (0.5, 1.2, 0.26)), 0.05)
        head = ell(q, (0, 1.64, 0.04), (0.15, 0.18, 0.16))
        hood = ell(q, (0, 1.67, -0.01), (0.215, 0.25, 0.23))
        hood = smax(hood, -ell(q, (0, 1.62, 0.16), (0.125, 0.15, 0.13)), 0.04)
        void = ell(q, (0, 1.62, 0.12), (0.11, 0.14, 0.08))
        eyes = np.minimum(ell(q, (0.05, 1.64, 0.20), (0.028, 0.016, 0.014), rz=0.3), ell(q, (-0.05, 1.64, 0.20), (0.028, 0.016, 0.014), rz=-0.3))
        chest = sd_box(q, v3(0, 1.14, 0.19), v3(0.23, 0.20, 0.05), 0.04)
        pauld = np.minimum(ell(q, (0.38, 1.40, 0.0), (0.16, 0.10, 0.17), rz=-0.3), ell(q, (-0.38, 1.40, 0.0), (0.16, 0.10, 0.17), rz=0.3))
        rivets = np.minimum.reduce([sd_sphere(q, v3(x, y, 0.245), 0.013) for x in (-0.16, 0.16) for y in (1.0, 1.28)])
        cloth = np.minimum.reduce([legs, torso, arms, cloak, hood])
        parts = [(cloth, 0), (np.minimum(boots, hands), 5), (void, 3), (eyes, 1), (np.minimum(claws, 0 * claws + 9), 2),
                 (np.minimum(chest, np.minimum(pauld, rivets)), 4), (head, 5)]
        parts[4] = (claws, 2)
        if boss:
            crown = np.minimum.reduce([horn(q, (sx * 0.10, 1.84, 0.0), (sx * 0.20, 2.18, -0.04), 0.045) for sx in (-1, 1)]
                                      + [horn(q, (0.0, 1.88, -0.04), (0.0, 2.25, -0.08), 0.05)])
            spikes = np.minimum.reduce([horn(q, (sx * 0.40, 1.46, 0.0), (sx * 0.52, 1.80, -0.04), 0.06) for sx in (-1, 1)])
            parts += [(crown, 2), (spikes, 2)]
        return _u(parts)

    return scene, mats, 2.2 * s, rot_x(0.14) @ rot_y(0.16)


# --------------------------------------------------------------------------- winged

def _membrane(q, sx, boss):
    k = 1.15 if boss else 1.0
    pts = [(0.18, 1.00), (1.00 * k, 1.58 * k), (1.52 * k, 1.16 * k), (1.46 * k, 0.80 * k), (1.18 * k, 0.52 * k), (0.20, 0.60)]
    x = q[:, 0] * sx
    y = q[:, 1]
    z = q[:, 2] + 0.06 * x * x + 0.10
    d = np.full(q.shape[0], -1e3, dtype=np.float32)
    for i in range(len(pts)):
        a = np.array(pts[i], dtype=np.float32)
        b = np.array(pts[(i + 1) % len(pts)], dtype=np.float32)
        e = b - a
        nrm = np.array([-e[1], e[0]], dtype=np.float32) / np.linalg.norm(e)
        d = np.maximum(d, (x - a[0]) * nrm[0] + (y - a[1]) * nrm[1])
    return np.maximum(d, np.abs(z) - 0.007) * 0.7


def gen_winged(boss=False):
    s = 1.1 if boss else 1.0
    k = 1.15 if boss else 1.0
    mem = Material((0.52, 0.52, 0.52), rough=0.7, spec=0.12, sss=0.9, edge=((1.0, 1.0, 1.0), 2.0, 0.4),
                   bump=lambda p: (value_noise(p * np.array([1.0, 1.0, 4.0], dtype=np.float32), 30.0, 5) - 0.5) * 0.004,
                   tint_fn=lambda p, a: a * (0.55 + 0.7 * fbm(p, 7.0, 3, 12))[:, None])
    mats = [skin_mat(0.55, 0.58), glow_mat(), bone_material((0.86, 0.86, 0.86)), mem]
    GLOW["gen_winged_boss" if boss else "gen_winged"] = 1

    def scene(p):
        q = p / s
        body = ell(q, (0, 0.92, 0), (0.26, 0.36, 0.22))
        head = ell(q, (0, 1.30, 0.12), (0.15, 0.15, 0.17))
        snout = rc(q, (0, 1.25, 0.20), (0, 1.16, 0.40), 0.085, 0.045)
        body = smin(smin(body, head, 0.08), snout, 0.05)
        horns = np.minimum(horn(q, (0.08, 1.40, 0.08), (0.13, 1.66, -0.02), 0.042), horn(q, (-0.08, 1.40, 0.08), (-0.13, 1.66, -0.02), 0.042))
        legs = np.minimum.reduce([limb(q, [(sx * 0.10, 0.68, 0.0), (sx * 0.13, 0.38, 0.08), (sx * 0.10, 0.14, 0.15)], [0.075, 0.05, 0.034]) for sx in (-1, 1)])
        talons = part(q, (0, 0.1, 0.17), 0.5, lambda t: np.minimum.reduce([
            rc(t, (sx * 0.10 + dx, 0.12, 0.16), (sx * 0.10 + dx * 1.5, 0.04, 0.23), 0.014, 0.003) for sx in (-1, 1) for dx in (-0.03, 0.0, 0.03)]))
        arms = part(q, (0, 1.2, -0.1), 2.2, lambda t: np.minimum.reduce([
            limb(t, [(sx * 0.20, 1.05, 0.0), (sx * 0.62 * k, 1.30 * k, -0.05), (sx * 1.00 * k, 1.58 * k, -0.10)], [0.052, 0.040, 0.030]) for sx in (-1, 1)]
            + [limb(t, [(sx * 1.00 * k, 1.58 * k, -0.10), (sx * 1.52 * k, 1.16 * k, -0.16)], [0.026, 0.012]) for sx in (-1, 1)]
            + [limb(t, [(sx * 1.00 * k, 1.58 * k, -0.10), (sx * 1.46 * k, 0.80 * k, -0.16)], [0.026, 0.012]) for sx in (-1, 1)]
            + [limb(t, [(sx * 1.00 * k, 1.58 * k, -0.10), (sx * 1.18 * k, 0.52 * k, -0.14)], [0.026, 0.012]) for sx in (-1, 1)]), margin=0.2)
        membrane = np.minimum(_membrane(q, 1.0, boss), _membrane(q, -1.0, boss))
        eyes = np.minimum(ell(q, (0.07, 1.34, 0.255), (0.030, 0.018, 0.016), rz=0.35), ell(q, (-0.07, 1.34, 0.255), (0.030, 0.018, 0.016), rz=-0.35))
        fangs = np.minimum(rc(q, (0.03, 1.15, 0.38), (0.03, 1.07, 0.40), 0.014, 0.003), rc(q, (-0.03, 1.15, 0.38), (-0.03, 1.07, 0.40), 0.014, 0.003))
        tail = limb(q, [(0, 0.74, -0.10), (0, 0.50, -0.45), (0, 0.62, -0.80)], [0.06, 0.04, 0.02])
        parts = [(np.minimum.reduce([body, legs, arms, tail]), 0), (eyes, 1), (np.minimum(np.minimum(horns, talons), fangs), 2), (membrane, 3)]
        if boss:
            barb = horn(q, (0, 0.62, -0.80), (0, 0.80, -1.02), 0.05)
            parts.append((barb, 2))
        return _u(parts)

    return scene, mats, 2.1 * s, rot_x(0.10) @ rot_y(0.14)


# --------------------------------------------------------------------------- serpent

def gen_serpent(boss=False):
    s = 1.2 if boss else 1.0
    scale = Material((0.58, 0.58, 0.58), rough=0.45, spec=0.5,
                     bump=lambda p: (np.abs(value_noise(p * 1.0, 38.0, 4) - 0.5)) * 0.012,
                     tint_fn=lambda p, a: a * (0.55 + 0.9 * np.abs(np.sin(p[:, 1] * 22.0 + p[:, 0] * 6.0)) * 0.6 + 0.2 * fbm(p, 12.0, 3, 5))[:, None])
    mats = [scale, glow_mat(), bone_material((0.9, 0.9, 0.9)), wet_black()]
    GLOW["gen_serpent_boss" if boss else "gen_serpent"] = 1
    coil = []
    for i in range(24):
        th = i * 0.62
        r = 0.62 - 0.010 * i
        coil.append((r * np.cos(th), 0.15 + i * 0.026, r * np.sin(th) * 0.9, 0.165 - 0.0012 * i))
    neck = [(0.10, 0.78, 0.18, 0.14), (0.0, 1.02, 0.30, 0.125), (-0.12, 1.28, 0.32, 0.112), (-0.10, 1.52, 0.38, 0.10)]
    chain = coil + neck

    def scene(p):
        q = p / s
        body = part(q, (0, 0.8, 0.1), 1.4, lambda t: np.minimum.reduce([
            rc(t, chain[i][:3], chain[i + 1][:3], chain[i][3], chain[i + 1][3]) for i in range(len(chain) - 1)]), margin=0.25)
        head = ell(q, (-0.10, 1.64, 0.50), (0.14, 0.10, 0.20))
        jaw = rc(q, (-0.10, 1.58, 0.50), (-0.10, 1.50, 0.66), 0.07, 0.03)
        body = smin(body, head, 0.05)
        fangs = np.minimum(rc(q, (-0.045, 1.58, 0.64), (-0.045, 1.46, 0.67), 0.016, 0.003), rc(q, (-0.155, 1.58, 0.64), (-0.155, 1.46, 0.67), 0.016, 0.003))
        eyes = np.minimum(ell(q, (-0.02, 1.69, 0.60), (0.028, 0.018, 0.02), rz=0.4), ell(q, (-0.18, 1.69, 0.60), (0.028, 0.018, 0.02), rz=-0.4))
        parts = [(np.minimum(body, jaw), 0), (eyes, 1), (fangs, 2)]
        if boss:
            hood = ell(q, (-0.10, 1.52, 0.38), (0.34, 0.30, 0.045))
            rimsp = part(q, (-0.10, 1.5, 0.4), 0.5, lambda t: np.minimum.reduce([
                horn(t, (-0.10 + 0.30 * np.cos(a), 1.52 + 0.26 * np.sin(a), 0.38), (-0.10 + 0.40 * np.cos(a), 1.52 + 0.35 * np.sin(a), 0.36), 0.03)
                for a in np.linspace(0.2, np.pi - 0.2, 6)]))
            parts += [(hood, 0), (rimsp, 2)]
        return _u(parts)

    return scene, mats, 1.85 * s, rot_x(0.20) @ rot_y(0.20)


# --------------------------------------------------------------------------- wraith

def gen_wraith(boss=False):
    s = 1.2 if boss else 1.0
    ecto = Material((0.55, 0.55, 0.55), rough=0.85, spec=0.08, sss=0.6, edge=((1.0, 1.0, 1.0), 1.8, 0.55),
                    bump=lambda p: (fbm(p, 30.0, 3, 14) - 0.5) * 0.010,
                    tint_fn=lambda p, a: a * (0.45 + 0.9 * fbm(p * np.array([1.0, 0.5, 1.0], dtype=np.float32), 4.0, 4, 7))[:, None])
    mats = [ecto, glow_mat(2.0), bone_material((0.88, 0.88, 0.88)), wet_black()]
    GLOW["gen_wraith_boss" if boss else "gen_wraith"] = 1

    def scene(p):
        q = p / s
        x, y, z = q[:, 0], q[:, 1], q[:, 2]
        ang = np.arctan2(z, x)
        height = np.clip(y / 1.75, 0.0, 1.0)
        rad = 0.17 + (0.34 + 0.07 * np.sin(7.0 * ang + 3.0 * fbm(q, 3.0, 2, 3))) * (1.0 - height) ** 1.4
        hem = 0.12 + 0.16 * np.sin(5.0 * ang + 1.0) * 0.5 + 0.08 * (fbm(q, 5.0, 2, 4) - 0.5)
        cloak = np.maximum(np.hypot(x, z) - rad, np.maximum(hem - y, y - 1.78)) * 0.7
        hood = ell(q, (0, 1.62, 0.02), (0.20, 0.25, 0.22))
        void = ell(q, (0, 1.60, 0.15), (0.115, 0.15, 0.085))
        eyes = np.minimum(ell(q, (0.05, 1.62, 0.215), (0.030, 0.016, 0.014), rz=0.3), ell(q, (-0.05, 1.62, 0.215), (0.030, 0.016, 0.014), rz=-0.3))
        sleeves = np.minimum.reduce([limb(q, [(sx * 0.22, 1.40, 0.0), (sx * 0.42, 1.14, 0.22), (sx * 0.48, 1.02, 0.38)], [0.085, 0.095, 0.05]) for sx in (-1, 1)])
        fingers = part(q, (0, 1.0, 0.4), 0.8, lambda t: np.minimum.reduce([
            sd_capsule(t, v3(sx * 0.48, 1.02, 0.38), v3(sx * 0.48 + dx, 0.93 + dy, 0.50), 0.011)
            for sx in (-1, 1) for dx, dy in ((-0.05, 0.0), (-0.02, -0.02), (0.02, -0.02), (0.05, 0.0), (sx * 0.08, 0.04))]))
        wisps = part(q, (0, 1.0, 0.0), 1.4, lambda t: np.minimum.reduce([
            sd_sphere(t, v3(0.55 * np.cos(a), 0.4 + 0.18 * i, 0.55 * np.sin(a)), 0.035) for i, a in enumerate(np.linspace(0, 6.0, 6))]))
        parts = [(np.minimum(np.minimum(cloak, hood), sleeves), 0), (void, 3), (eyes, 1), (fingers, 2)]
        if boss:
            crown = part(q, (0, 1.9, 0), 0.5, lambda t: np.minimum.reduce([
                horn(t, (0.22 * np.cos(a), 1.80, 0.22 * np.sin(a)), (0.27 * np.cos(a), 2.12, 0.27 * np.sin(a)), 0.04) for a in np.linspace(0, 6.28, 8, endpoint=False)]))
            parts += [(crown, 2), (wisps, 1)]
        return _u(parts)

    return scene, mats, 2.3 * s, rot_x(0.10) @ rot_y(0.12)


BUILDERS = {
    "gen_humanoid": lambda: gen_humanoid(False), "gen_humanoid_boss": lambda: gen_humanoid(True),
    "gen_winged": lambda: gen_winged(False), "gen_winged_boss": lambda: gen_winged(True),
    "gen_serpent": lambda: gen_serpent(False), "gen_serpent_boss": lambda: gen_serpent(True),
    "gen_wraith": lambda: gen_wraith(False), "gen_wraith_boss": lambda: gen_wraith(True),
}
