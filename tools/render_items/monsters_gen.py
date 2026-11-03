"""Neutral (grey) body plans of the generated zones; the app tints them with the biome palette.

Eight plans x (regular, boss): blob, hound, humanoid, insect, winged, serpent, wraith, golem. Every builder
returns (scene, materials, bounds, view); GLOW[name] is the material id of the emissive parts (eyes, cracks)
that render_monsters.py also writes as a separate layer so the app can colour it with the biome's eye colour.
"""
from __future__ import annotations

import numpy as np

from items import fold_polar
from monsters import (_eye_parts, _gel_mass, _teeth, bone_material, ell, fur_material, gel_material, limb, part, rc,
                      rot_local, shaggy, wet_black)
from sdf import (Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere,
                 sd_torus, smax, smin, union, v3, value_noise)

GLOW: dict[str, int] = {}


def glow_mat(strength=1.7):
    return Material((1.0, 1.0, 1.0), rough=0.25, spec=0.6, emissive=strength)


def skin_mat(rough=0.55, tone=0.60):
    return Material((tone, tone, tone), rough=rough, spec=0.28, sss=0.20,
                    bump=lambda p: (fbm(p, 60.0, 3, 31) - 0.5) * 0.004,
                    tint_fn=lambda p, a: a * (0.75 + 0.5 * fbm(p, 6.0, 3, 2))[:, None])


def stone_mat():
    def tint(p, a):
        v = 0.65 + 0.7 * fbm(p, 5.0, 4, 11)
        crack = np.clip((fbm(p, 14.0, 3, 17) - 0.55) * 5.0, 0.0, 1.0)
        return a * (v * (1 - 0.55 * crack))[:, None]

    return Material((0.60, 0.60, 0.60), rough=0.88, spec=0.10, tint_fn=tint,
                    bump=lambda p: (fbm(p, 26.0, 4, 3) - 0.5) * 0.020)


def chitin_mat():
    return Material((0.52, 0.52, 0.54), metal=0.25, rough=0.38, spec=0.9,
                    bump=lambda p: (fbm(p, 70.0, 3, 5) - 0.5) * 0.004,
                    tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p, 9.0, 3, 8))[:, None])


def horn(p, base, tip, r):
    return rc(p, base, tip, r, 0.004)


# --------------------------------------------------------------------------- blob

def gen_blob(boss=False):
    s = 1.28 if boss else 1.0
    gel = gel_material((0.50, 0.50, 0.50), (1.0, 1.0, 1.0), scale=s)
    mats = [gel, glow_mat(), wet_black(), bone_material((0.85, 0.85, 0.85))]
    GLOW["gen_blob_boss" if boss else "gen_blob"] = 1

    def scene(p):
        body = _gel_mass(p, s)
        eyes = None
        spots = [(0.20, 0.67, 0.52, 0.075), (-0.24, 0.60, 0.50, 0.060), (0.34, 0.44, 0.48, 0.045)]
        if boss:
            spots += [(-0.06, 0.82, 0.38, 0.050), (0.02, 0.54, 0.58, 0.040)]
        for (x, y, z, r) in spots:
            e = sd_sphere(p, v3(x * s, y * s, z * s), r * s)
            eyes = e if eyes is None else np.minimum(eyes, e)
        slits = np.minimum.reduce([ell(p, (x * s, y * s, (z + r * 0.9) * s), (r * 0.12 * s, r * 0.55 * s, r * 0.12 * s)) for (x, y, z, r) in spots])
        tooth = part(p, (0, 0.25 * s, 0.64 * s), 0.40 * s, lambda q: _teeth(q, s, 9, 0.365 * s, 0.26, 0.64 * s, 1.0))
        parts = [(body, 0), (eyes, 1), (slits, 2), (tooth, 3)]
        if boss:
            q = fold_polar(p - v3(0, 1.05 * s, 0.0), 7)
            spikes = rc(q, (0.30 * s, -0.02, 0), (0.34 * s, 0.30 * s, 0), 0.05 * s, 0.006)
            parts.append((spikes, 3))
        return union(parts)

    return scene, mats, 1.75 if boss else 1.25, rot_x(0.26) @ rot_y(0.12)


# --------------------------------------------------------------------------- hound

def gen_hound(boss=False):
    s = 1.18 if boss else 1.0
    mats = [fur_material((0.60, 0.60, 0.60), belly=1.3, dirt=0.35, y_ref=0.7), glow_mat(), bone_material((0.88, 0.88, 0.88)),
            Material((0.12, 0.12, 0.12), rough=0.4, spec=0.5)]
    GLOW["gen_hound_boss" if boss else "gen_hound"] = 1

    def scene(p):
        q = p / s
        body = rc(q, (0, 0.62, -0.46), (0, 0.70, 0.10), 0.27, 0.31)
        body = smin(body, ell(q, (0, 0.62, 0.08), (0.32, 0.35, 0.30)), 0.10)
        body = smin(body, ell(q, (0, 0.60, -0.46), (0.28, 0.30, 0.30)), 0.10)
        neck = rc(q, (0, 0.74, 0.10), (0, 0.84, 0.46), 0.23, 0.17)
        head = ell(q, (0, 0.86, 0.58), (0.17, 0.16, 0.20))
        muzzle = rc(q, (0, 0.80, 0.62), (0, 0.69, 0.94), 0.12, 0.07)
        body = smin(smin(smin(body, neck, 0.10), head, 0.08), muzzle, 0.06)
        ears = np.minimum(ell(q, (0.11, 1.01, 0.52), (0.055, 0.12, 0.025), rz=-0.25), ell(q, (-0.11, 1.01, 0.52), (0.055, 0.12, 0.025), rz=0.25))
        legs = np.minimum.reduce([
            limb(q, [(sx * 0.20, 0.58, 0.12), (sx * 0.22, 0.34, 0.24), (sx * 0.20, 0.06, 0.28)], [0.105, 0.072, 0.052]) for sx in (-1, 1)
        ] + [limb(q, [(sx * 0.22, 0.56, -0.46), (sx * 0.25, 0.30, -0.32), (sx * 0.21, 0.05, -0.38)], [0.13, 0.075, 0.052]) for sx in (-1, 1)])
        paws = np.minimum.reduce([ell(q, (sx * 0.20, 0.035, 0.31), (0.06, 0.035, 0.09)) for sx in (-1, 1)]
                                 + [ell(q, (sx * 0.21, 0.035, -0.34), (0.065, 0.035, 0.10)) for sx in (-1, 1)])
        tail = limb(q, [(0, 0.66, -0.70), (0.02, 0.84, -0.90), (0.0, 0.98, -0.92)], [0.06, 0.045, 0.02])
        fur = np.minimum.reduce([body, legs, paws, tail]) - shaggy(q, 0.014, 26.0)
        fur = np.minimum(fur, ears)
        jaw = rc(q, (0, 0.72, 0.62), (0, 0.55, 0.86), 0.065, 0.03)
        teeth = part(q, (0, 0.70, 0.86), 0.3, lambda t: np.minimum.reduce([
            sd_cone_y(t, v3(x, 0.68, 0.88 + dz), 0.020, 0.0015, 0.04) for x, dz in ((-0.05, 0.0), (0.0, 0.03), (0.05, 0.0), (-0.025, -0.04), (0.025, -0.04))]
            + [sd_cone_y(t, v3(x, 0.60, 0.84 + dz), 0.0015, 0.018, 0.035) for x, dz in ((-0.04, 0.0), (0.04, 0.0), (0.0, 0.03))]))
        eyes = np.minimum(ell(q, (0.085, 0.90, 0.72), (0.030, 0.020, 0.025), rz=0.3), ell(q, (-0.085, 0.90, 0.72), (0.030, 0.020, 0.025), rz=-0.3))
        claws = part(q, (0, 0.03, 0.3), 0.6, lambda t: np.minimum.reduce([
            rc(t, (sx * 0.20 + dx, 0.03, 0.37), (sx * 0.20 + dx, 0.012, 0.43), 0.012, 0.003) for sx in (-1, 1) for dx in (-0.035, 0, 0.035)]))
        parts = [(fur, 0), (eyes, 1), (np.minimum(teeth, claws), 2), (jaw, 0)]
        if boss:
            hn = np.minimum(horn(q, (0.09, 1.0, 0.58), (0.16, 1.28, 0.40), 0.035), horn(q, (-0.09, 1.0, 0.58), (-0.16, 1.28, 0.40), 0.035))
            spikes = part(q, (0, 0.8, -0.2), 0.8, lambda t: np.minimum.reduce([
                rc(t, (0, 0.84 + 0.02 * k, 0.28 - 0.17 * k), (0, 1.00 + 0.03 * k, 0.24 - 0.17 * k), 0.035, 0.004) for k in range(6)]))
            parts += [(hn, 2), (spikes, 2)]
        return union([(d, m) for d, m in parts]) if False else _u(parts)

    return scene, mats, 1.5 * s, rot_x(0.20) @ rot_y(0.42)


def _u(parts):
    d, m = parts[0]
    m = np.full(d.shape, m, dtype=np.int16)
    for dd, mm in parts[1:]:
        sel = dd < d
        d = np.where(sel, dd, d)
        m = np.where(sel, mm, m)
    return d, m


# --------------------------------------------------------------------------- insect (spider)

def gen_insect(boss=False):
    s = 1.22 if boss else 1.0
    mats = [chitin_mat(), glow_mat(), bone_material((0.88, 0.88, 0.88))]
    GLOW["gen_insect_boss" if boss else "gen_insect"] = 1
    z0 = [0.26, 0.10, -0.06, -0.22]
    dz = [0.34, 0.14, -0.12, -0.34]

    def scene(p):
        q = p / s
        abdomen = ell(q, (0, 0.62, -0.50), (0.38, 0.34, 0.50))
        abdomen = abdomen - (np.abs(np.sin(q[:, 2] * 14.0)) * 0.012)
        thorax = ell(q, (0, 0.56, 0.08), (0.23, 0.20, 0.28))
        head = ell(q, (0, 0.54, 0.40), (0.16, 0.14, 0.14))
        body = smin(smin(abdomen, thorax, 0.12), head, 0.06)
        legs = part(q, (0, 0.5, 0.0), 1.6, lambda t: np.minimum.reduce([
            limb(t, [(sx * 0.17, 0.56, z0[i]), (sx * 0.58, 0.84, z0[i] + dz[i] * 0.55), (sx * 0.98, 0.46, z0[i] + dz[i] * 0.95), (sx * 1.14, 0.03, z0[i] + dz[i] * 1.05)],
                 [0.052, 0.040, 0.030, 0.014]) for i in range(4) for sx in (-1, 1)]), margin=0.2)
        fangs = np.minimum(rc(q, (0.06, 0.48, 0.48), (0.05, 0.28, 0.56), 0.05, 0.010), rc(q, (-0.06, 0.48, 0.48), (-0.05, 0.28, 0.56), 0.05, 0.010))
        eyes = part(q, (0, 0.58, 0.50), 0.2, lambda t: np.minimum.reduce([
            sd_sphere(t, v3(x, y, 0.505 - 0.01 * abs(x) * 4), r) for (x, y, r) in
            ((0.055, 0.575, 0.040), (-0.055, 0.575, 0.040), (0.10, 0.60, 0.026), (-0.10, 0.60, 0.026), (0.03, 0.625, 0.022), (-0.03, 0.625, 0.022), (0.075, 0.54, 0.018), (-0.075, 0.54, 0.018))]))
        parts = [(np.minimum(body, legs), 0), (eyes, 1), (fangs, 2)]
        if boss:
            spk = part(q, (0, 0.8, -0.5), 0.7, lambda t: np.minimum.reduce([
                rc(t, (x, 0.88 - 0.03 * abs(z + 0.5), z), (x * 1.1, 1.12 - 0.05 * abs(z + 0.5), z), 0.045, 0.004)
                for x in (-0.12, 0.12) for z in (-0.78, -0.58, -0.38, -0.18)]))
            hn = np.minimum(horn(q, (0.08, 0.64, 0.46), (0.16, 0.86, 0.50), 0.035), horn(q, (-0.08, 0.64, 0.46), (-0.16, 0.86, 0.50), 0.035))
            parts += [(spk, 2), (hn, 2)]
        return _u(parts)

    return scene, mats, 1.9 * s, rot_x(0.34) @ rot_y(0.25)


# --------------------------------------------------------------------------- golem

def gen_golem(boss=False):
    s = 1.22 if boss else 1.0
    mats = [stone_mat(), glow_mat(2.0), Material((0.20, 0.20, 0.20), rough=0.9, spec=0.1)]
    GLOW["gen_golem_boss" if boss else "gen_golem"] = 1

    def scene(p):
        q = p / s
        rough = (fbm(q, 9.0, 3, 21) - 0.5) * 0.06
        torso = sd_box(q, v3(0, 1.02, 0), v3(0.40, 0.40, 0.27), 0.10)
        pelvis = sd_box(q, v3(0, 0.56, 0), v3(0.30, 0.13, 0.20), 0.07)
        head = sd_box(q, v3(0, 1.58, 0.05), v3(0.19, 0.17, 0.17), 0.07)
        shoulders = np.minimum(sd_sphere(q, v3(0.56, 1.28, 0), 0.20), sd_sphere(q, v3(-0.56, 1.28, 0), 0.20))
        arms = np.minimum(
            limb(q, [(0.58, 1.22, 0), (0.68, 0.80, 0.08)], [0.16, 0.15]), limb(q, [(-0.58, 1.22, 0), (-0.68, 0.80, 0.08)], [0.16, 0.15]))
        fore = np.minimum(limb(q, [(0.68, 0.80, 0.08), (0.66, 0.40, 0.20)], [0.15, 0.17]), limb(q, [(-0.68, 0.80, 0.08), (-0.66, 0.40, 0.20)], [0.15, 0.17]))
        fists = np.minimum(sd_box(q, v3(0.66, 0.24, 0.22), v3(0.15, 0.14, 0.15), 0.06), sd_box(q, v3(-0.66, 0.24, 0.22), v3(0.15, 0.14, 0.15), 0.06))
        legs = np.minimum(sd_box(q, v3(0.19, 0.28, 0.0), v3(0.14, 0.28, 0.16), 0.06), sd_box(q, v3(-0.19, 0.28, 0.0), v3(0.14, 0.28, 0.16), 0.06))
        feet = np.minimum(sd_box(q, v3(0.19, 0.06, 0.05), v3(0.17, 0.06, 0.22), 0.04), sd_box(q, v3(-0.19, 0.06, 0.05), v3(0.17, 0.06, 0.22), 0.04))
        rock = smin(torso, np.minimum.reduce([pelvis, head, shoulders, arms, fore, fists, legs, feet]), 0.04) + rough
        rock = np.minimum(rock, np.minimum.reduce([pelvis, legs, feet, fists]) + rough)
        eyes = np.minimum(sd_box(q, v3(0.07, 1.60, 0.225), v3(0.050, 0.014, 0.012), 0.006), sd_box(q, v3(-0.07, 1.60, 0.225), v3(0.050, 0.014, 0.012), 0.006))
        cracks = part(q, (0, 1.0, 0.27), 0.6, lambda t: np.minimum.reduce([
            sd_capsule(t, v3(*a), v3(*b), 0.012) for a, b in (
                ((0.0, 1.30, 0.265), (0.05, 1.10, 0.272)), ((0.05, 1.10, 0.272), (-0.06, 0.95, 0.272)), ((-0.06, 0.95, 0.272), (0.04, 0.78, 0.268)),
                ((0.05, 1.10, 0.272), (0.20, 1.02, 0.268)), ((-0.06, 0.95, 0.272), (-0.22, 0.88, 0.268)))]))
        parts = [(rock, 0), (np.minimum(eyes, cracks), 1)]
        if boss:
            sp = np.minimum.reduce([horn(q, (sx * 0.58, 1.40, 0.0), (sx * 0.70, 1.78, -0.04), 0.07) for sx in (-1, 1)]
                                   + [horn(q, (sx * 0.10, 1.72, 0.05), (sx * 0.16, 2.05, 0.0), 0.05) for sx in (-1, 1)]
                                   + [horn(q, (0.0, 1.44, -0.20), (0.0, 1.82, -0.40), 0.08)])
            parts.append((sp, 2))
        return _u(parts)

    return scene, mats, 2.0 * s, rot_x(0.16) @ rot_y(0.18)


BUILDERS = {
    "gen_blob": lambda: gen_blob(False), "gen_blob_boss": lambda: gen_blob(True),
    "gen_hound": lambda: gen_hound(False), "gen_hound_boss": lambda: gen_hound(True),
    "gen_insect": lambda: gen_insect(False), "gen_insect_boss": lambda: gen_insect(True),
    "gen_golem": lambda: gen_golem(False), "gen_golem_boss": lambda: gen_golem(True),
}
