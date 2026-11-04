"""Monsters of the generated zones (6..100): eight body plans (see Body in BiomeStyles.kt), each in a calm and a
fearsome variant (horns, spikes, extra eyes, glowing cracks). They are rendered in neutral grey and split into
layers by material role, so the app can colour them per biome without re-rendering:

    skin  -> gradient-mapped to the biome skin palette (dark .. light)
    eye   -> tinted with the biome eye colour (emissive)
    glow  -> tinted with the biome accent colour (emissive cracks, runes)
    fixed -> drawn as rendered (teeth, claws, horns, pupils)

Each builder returns (scene, materials, roles, bounds, view_rot).
"""
from __future__ import annotations

import numpy as np

from monsters import _gel_mass, _teeth, bone_material, ell, fur_material, limb, part, rc, shaggy, wet_black
from sdf import Material, fbm, rot_x, rot_y, sd_sphere, sd_torus, smax, smin, union, v3, value_noise

GREY = (0.70, 0.70, 0.70)


def pair(f):
    return np.minimum(f(1), f(-1))


# --------------------------------------------------------------------------- shared materials

def skin_scaly():
    return Material(GREY, rough=0.38, spec=0.75, sss=0.35,
                    tint_fn=lambda p, a: a * (0.62 + 0.62 * fbm(p, 6.0, 4, 2))[:, None],
                    bump=lambda p: (value_noise(p, 70.0, 7) - 0.5) * 0.009 + (fbm(p, 16.0, 3, 9) - 0.5) * 0.014)


def skin_hide():
    return Material(GREY, rough=0.55, spec=0.45, sss=0.5,
                    tint_fn=lambda p, a: a * (0.60 + 0.65 * fbm(p, 5.0, 4, 4))[:, None],
                    bump=lambda p: (fbm(p, 40.0, 3, 5) - 0.5) * 0.012 + (fbm(p, 9.0, 3, 6) - 0.5) * 0.02)


def skin_fur():
    return fur_material(GREY, belly=1.30, dirt=0.25, strand=0.0035, clump=0.0045, y_ref=1.0)


def skin_rock():
    return Material(GREY, rough=0.88, spec=0.12,
                    tint_fn=lambda p, a: a * (0.52 + 0.85 * fbm(p, 5.0, 4, 3))[:, None],
                    bump=lambda p: (value_noise(p, 14.0, 3) - 0.5) * 0.045 + (value_noise(p, 55.0, 6) - 0.5) * 0.012)


def skin_gel():
    def tint(p, a):
        cloud = 0.55 + 0.6 * fbm(p, 3.2, 4, 2)
        return a * cloud[:, None]
    return Material(GREY, rough=0.035, spec=1.9, sss=1.3, tint_fn=tint,
                    edge=((0.95, 0.95, 0.95), 2.2, 0.7), bump=lambda p: (fbm(p, 5.0, 3, 8) - 0.5) * 0.012)


def skin_cloth():
    return Material(GREY, rough=0.85, spec=0.08, sss=0.6,
                    tint_fn=lambda p, a: a * (0.55 + 0.6 * fbm(p * np.array([3.0, 0.6, 3.0], dtype=np.float32), 6.0, 4, 3))[:, None],
                    bump=lambda p: (value_noise(p * np.array([4.0, 0.5, 4.0], dtype=np.float32), 20.0, 4) - 0.5) * 0.006)


EYE = Material((1.0, 1.0, 1.0), rough=0.03, spec=3.0, emissive=1.0)
GLOW = Material((1.0, 1.0, 1.0), rough=0.3, spec=0.4, emissive=1.2)


def tooth():
    return bone_material((0.92, 0.89, 0.76))


def horn_mat():
    return Material((0.16, 0.13, 0.11), rough=0.38, spec=0.6,
                    tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p * np.array([1, 4, 1], dtype=np.float32), 12.0, 3, 4))[:, None],
                    bump=lambda p: (np.sin(p[:, 1] * 160.0) * 0.5) * 0.002)


def claw_mat():
    return Material((0.08, 0.07, 0.06), rough=0.3, spec=0.8)


# --------------------------------------------------------------------------- fear additions

def horns(p, base, spread=0.16, length=0.42, back=-0.2, thick=0.06, curl=1.0):
    """Two curved horns rising from `base` (head top), sweeping back and outwards."""
    bx, by, bz = base
    return pair(lambda s: limb(p, [(bx + s * spread, by, bz), (bx + s * (spread + 0.10 * curl), by + length * 0.55, bz + back * 0.4),
                                   (bx + s * (spread + 0.22 * curl), by + length * 0.85, bz + back), (bx + s * (spread + 0.18 * curl), by + length, bz + back * 1.6)],
                                [thick, thick * 0.78, thick * 0.45, thick * 0.08]))


def spine_row(p, pts, height=0.16, r=0.035):
    """Spikes standing on the given (x, y, z) points, leaning back."""
    out = None
    for (x, y, z) in pts:
        d = rc(p, (x, y, z), (x, y + height, z - height * 0.35), r, 0.003)
        out = d if out is None else np.minimum(out, d)
    return out


def crack_glow(p, body_d, scale=4.0, width=0.012):
    """Glowing cracks just under the skin surface (returned as a thin shell inside narrow noise veins)."""
    veins = np.abs(fbm(p, scale, 3, 13) - 0.5)
    patch = fbm(p, scale * 0.35, 2, 29)                      # only some regions crack open
    shell = np.abs(body_d + 0.004) - 0.010
    return np.maximum(np.maximum(shell, veins - width * 0.45), 0.52 - patch)


# --------------------------------------------------------------------------- bodies

def blob(fear=0):
    M_SKIN, M_EYE, M_FIX, M_TOOTH, M_GLOW = range(5)
    s = 1.0

    def scene(p):
        body = _gel_mass(p, s)
        eyes = pair(lambda k: sd_sphere(p, v3(k * 0.22, 0.66, 0.48), 0.085))
        pupils = pair(lambda k: ell(p, (k * 0.22, 0.66, 0.565), (0.018, 0.055, 0.02)))
        extra = None
        if fear:
            extra = np.minimum.reduce([sd_sphere(p, v3(x, y, z), r) for x, y, z, r in
                                       ((0.0, 0.82, 0.38, 0.06), (0.40, 0.52, 0.42, 0.05), (-0.42, 0.50, 0.42, 0.05))])
            eyes = np.minimum(eyes, extra)
        th = part(p, (0, 0.25, 0.64), 0.40, lambda q: _teeth(q, s, 11 if fear else 9, 0.365, 0.27, 0.64, 1.4 if fear else 1.0))
        parts = [(body, M_SKIN), (eyes, M_EYE), (pupils, M_FIX), (th, M_TOOTH)]
        if fear:
            sp = spine_row(p, [(x, 0.92 - 0.6 * x * x, -0.05 + 0.1 * x) for x in np.linspace(-0.45, 0.45, 7)], 0.18, 0.04)
            parts.append((sp, M_FIX))
            parts.append((crack_glow(p, body), M_GLOW))
        return union(parts)
    mats = [skin_gel(), EYE, wet_black(), tooth(), GLOW]
    return scene, mats, ["skin", "eye", "fixed", "fixed", "glow"], 1.35, rot_x(0.26) @ rot_y(0.12)


def hound(fear=0):
    M_SKIN, M_BELLY, M_NOSE, M_EYE, M_TOOTH, M_CLAW, M_HORN, M_GLOW = range(8)
    DY = 0.20

    def scene(p):
        pb = p - v3(0, DY, 0)
        body = rc(pb, (0, 0.64, -0.62), (0, 0.66, 0.14), 0.16, 0.23)
        body = smin(body, ell(pb, (0, 0.55, -0.12), (0.15, 0.17, 0.58)), 0.14)
        body = smin(body, rc(pb, (0, 0.66, 0.16), (0, 0.68, 0.46), 0.25, 0.20), 0.10)
        body = smin(body, ell(pb, (0, 0.56, 0.30), (0.22, 0.30, 0.30)), 0.12)
        body = smin(body, ell(pb, (0, 0.76, 0.42), (0.27, 0.27, 0.21)), 0.12)
        head = ell(pb, (0, 0.76, 0.62), (0.21, 0.19, 0.22))
        muzzle = rc(pb, (0, 0.70, 0.70), (0, 0.60, 1.14), 0.14, 0.055)
        body = smin(smin(body, head, 0.09), muzzle, 0.08)
        body = smin(body, ell(pb, (0, 0.86, 0.70), (0.14, 0.07, 0.12)), 0.06)
        jaw = rc(pb, (0, 0.55, 0.78), (0, 0.44, 1.04), 0.08, 0.04)
        body = smin(body, jaw, 0.03)

        def leg(sx, zf, th):
            return limb(p, [(sx * 0.22, 0.72, zf), (sx * 0.24, 0.40, zf + 0.06), (sx * 0.23, 0.08, zf + 0.02)], [th, th * 0.62, th * 0.42])

        def hind(sx):
            return limb(p, [(sx * 0.20, 0.82, -0.44), (sx * 0.22, 0.56, -0.30), (sx * 0.22, 0.28, -0.56), (sx * 0.22, 0.07, -0.46)],
                        [0.15, 0.09, 0.055, 0.04])
        legs = np.minimum.reduce([leg(1, 0.28, 0.105), leg(-1, 0.28, 0.105), hind(1), hind(-1)])
        tail = limb(pb, [(0, 0.76, -0.70), (0.0, 0.62, -1.05), (0.0, 0.40, -1.25)], [0.06, 0.09, 0.05])
        skin_d = np.minimum.reduce([body, legs, tail]) - shaggy(p, 0.006, 32.0, stretch=(1, 2.2, 1))
        ears = pair(lambda s: ell(pb, (s * 0.15, 1.00, 0.56), (0.055, 0.14, 0.035), rz=-s * 0.18))
        skin_d = np.minimum(skin_d, ears)
        nose = sd_sphere(pb, v3(0, 0.62, 1.15), 0.038)
        eyes = pair(lambda s: ell(pb, (s * 0.12, 0.83, 0.84), (0.046, 0.030, 0.032), rz=s * 0.35))
        if fear:
            eyes = np.minimum(eyes, pair(lambda s: ell(pb, (s * 0.09, 0.90, 0.78), (0.026, 0.018, 0.02), rz=s * 0.35)))

        def teeth(q):
            out = None
            for s in (-1, 1):
                for x, h in ((0.052, 0.09 if fear else 0.07), (0.030, 0.045)):
                    d = np.minimum(rc(q, (s * x, 0.61, 1.02), (s * x, 0.61 - h, 1.03), 0.015, 0.002),
                                   rc(q, (s * x, 0.46, 1.00), (s * x, 0.46 + h * 0.8, 1.01), 0.013, 0.002))
                    out = d if out is None else np.minimum(out, d)
            return out
        td = part(pb, (0, 0.55, 1.0), 0.25, teeth)
        claws = part(p, (0, 0.04, 0.0), 0.95, lambda q: np.minimum.reduce([
            rc(q, (sx * 0.23 + dx, 0.03, zf + 0.10), (sx * 0.23 + dx * 1.3, 0.012, zf + 0.20), 0.016, 0.003)
            for sx in (-1, 1) for zf in (0.28, -0.40) for dx in (-0.03, 0.0, 0.03)]))
        parts = [(skin_d, M_SKIN), (nose, M_NOSE), (eyes, M_EYE), (td, M_TOOTH), (claws, M_CLAW)]
        if fear:
            parts.append((horns(pb, (0, 0.92, 0.60), spread=0.10, length=0.40, back=-0.30, thick=0.05), M_HORN))
            parts.append((spine_row(pb, [(0, 0.90 - 0.04 * i, 0.40 - 0.18 * i) for i in range(7)], 0.14, 0.03), M_HORN))
            parts.append((crack_glow(p, skin_d, 5.0), M_GLOW))
        return union(parts)
    mats = [skin_fur(), skin_fur(), wet_black(), EYE, tooth(), claw_mat(), horn_mat(), GLOW]
    return scene, mats, ["skin", "skin", "fixed", "eye", "fixed", "fixed", "fixed", "glow"], 1.5, rot_x(0.16) @ rot_y(0.50)


def humanoid(fear=0):
    """Hunched ghoul/brute: long arms with claws, heavy jaw, ragged loincloth."""
    M_SKIN, M_EYE, M_TOOTH, M_CLAW, M_CLOTH, M_HORN, M_GLOW = range(7)

    def scene(p):
        torso = rc(p, (0, 0.95, -0.10), (0, 1.45, 0.05), 0.24, 0.32)
        torso = smin(torso, ell(p, (0, 1.42, 0.02), (0.46, 0.22, 0.28)), 0.12)                 # shoulders
        torso = smin(torso, pair(lambda s: ell(p, (s * 0.15, 1.30, 0.18), (0.16, 0.14, 0.10))), 0.08)  # pecs
        torso = smin(torso, ell(p, (0, 1.05, 0.08), (0.24, 0.22, 0.20)), 0.10)                 # belly
        neck = rc(p, (0, 1.50, 0.05), (0, 1.66, 0.20), 0.14, 0.12)
        head = ell(p, (0, 1.76, 0.26), (0.17, 0.19, 0.18))
        brow = ell(p, (0, 1.84, 0.38), (0.17, 0.05, 0.08))
        jaw = rc(p, (0, 1.66, 0.26), (0, 1.60, 0.42), 0.12, 0.09)
        body = smin(smin(smin(smin(torso, neck, 0.08), head, 0.07), brow, 0.04), jaw, 0.05)
        body = smax(body, -ell(p, (0, 1.63, 0.47), (0.08, 0.04, 0.06)), 0.02)                  # mouth
        body = smax(body, -pair(lambda s: ell(p, (s * 0.07, 1.78, 0.42), (0.045, 0.03, 0.04))), 0.02)
        arms = pair(lambda s: limb(p, [(s * 0.44, 1.40, 0.0), (s * 0.58, 1.05, 0.10), (s * 0.62, 0.66, 0.26), (s * 0.60, 0.42, 0.34)],
                                   [0.13, 0.10, 0.085, 0.07]))
        biceps = pair(lambda s: ell(p, (s * 0.52, 1.20, 0.05), (0.11, 0.17, 0.11), rz=s * 0.3))
        hands = pair(lambda s: ell(p, (s * 0.60, 0.36, 0.38), (0.09, 0.10, 0.07)))
        legs = pair(lambda s: limb(p, [(s * 0.18, 0.92, 0.0), (s * 0.24, 0.52, 0.14), (s * 0.22, 0.10, 0.02)], [0.15, 0.11, 0.08]))
        feet = pair(lambda s: ell(p, (s * 0.22, 0.05, 0.12), (0.09, 0.05, 0.16)))
        body = np.minimum.reduce([body, smin(arms, biceps, 0.06), hands, legs, feet])
        body = body - (fbm(p, 9.0, 3, 4) - 0.5) * 0.012
        eyes = pair(lambda s: sd_sphere(p, v3(s * 0.07, 1.78, 0.40), 0.03))
        if fear:
            eyes = np.minimum(eyes, pair(lambda s: sd_sphere(p, v3(s * 0.11, 1.86, 0.37), 0.018)))
        fang = pair(lambda s: rc(p, (s * 0.05, 1.60, 0.47), (s * 0.055, 1.70 if fear else 1.67, 0.48), 0.018, 0.003))
        fang = np.minimum(fang, pair(lambda s: rc(p, (s * 0.025, 1.66, 0.49), (s * 0.025, 1.61, 0.49), 0.012, 0.002)))
        claws = part(p, (0, 0.30, 0.45), 1.0, lambda q: np.minimum.reduce([
            rc(q, (s * (0.60 + dx), 0.30, 0.42), (s * (0.60 + dx * 1.3), 0.16, 0.50), 0.018, 0.003) for s in (-1, 1) for dx in (-0.05, 0.0, 0.05)]))
        cloth = np.maximum(ell(p, (0, 0.84, 0.02), (0.30, 0.20, 0.25)), -(p[:, 1] - 0.62) - (fbm(p, 12.0, 2, 3) - 0.5) * 0.12)
        cloth = np.maximum(cloth, p[:, 1] - 0.96)
        parts = [(body, M_SKIN), (eyes, M_EYE), (fang, M_TOOTH), (claws, M_CLAW), (cloth, M_CLOTH)]
        if fear:
            parts.append((horns(p, (0, 1.90, 0.20), spread=0.10, length=0.46, back=-0.25, thick=0.055), M_HORN))
            parts.append((spine_row(p, [(s * 0.40, 1.52, -0.05) for s in (-1, 1)] + [(s * 0.30, 1.56, -0.12) for s in (-1, 1)], 0.16, 0.035), M_HORN))
            parts.append((crack_glow(p, body, 4.5), M_GLOW))
        return union(parts)
    mats = [skin_hide(), EYE, tooth(), claw_mat(), skin_cloth(), horn_mat(), GLOW]
    return scene, mats, ["skin", "eye", "fixed", "fixed", "skin", "fixed", "glow"], 2.0, rot_x(0.10) @ rot_y(0.18)


def insect(fear=0):
    """Armoured beetle/mantis: plated abdomen, pincers, six legs."""
    M_SKIN, M_LEG, M_EYE, M_FIX, M_GLOW = range(5)

    def scene(p):
        ab = ell(p, (0, 0.55, -0.55), (0.42, 0.34, 0.58), rx=0.25)
        rings = np.abs(np.sin((p[:, 2] + 0.55) * 22.0)) * 0.012
        ab = ab + rings * (p[:, 2] < -0.05)
        thor = ell(p, (0, 0.55, 0.10), (0.30, 0.24, 0.28))
        head = ell(p, (0, 0.52, 0.42), (0.20, 0.17, 0.16))
        shell = smin(smin(ab, thor, 0.08), head, 0.06)
        # wing-case seam
        shell = smax(shell, -np.maximum(np.abs(p[:, 0]) - 0.008, -(p[:, 2] + 0.0)), 0.006)
        legs = None
        for s in (-1, 1):
            for i, z in enumerate((0.20, 0.02, -0.18)):
                k = 1 - i
                d = limb(p, [(s * 0.24, 0.50, z), (s * 0.62, 0.78, z + 0.10 * k), (s * 0.86, 0.36, z + 0.16 * k), (s * 0.94, 0.02, z + 0.20 * k)],
                         [0.055, 0.045, 0.030, 0.012])
                legs = d if legs is None else np.minimum(legs, d)
        mand = pair(lambda s: limb(p, [(s * 0.08, 0.44, 0.54), (s * 0.20, 0.40, 0.72), (s * 0.06, 0.38, 0.86)], [0.04, 0.03, 0.006]))
        ant = pair(lambda s: limb(p, [(s * 0.07, 0.64, 0.50), (s * 0.22, 0.95, 0.70), (s * 0.34, 1.10, 0.62)], [0.012, 0.009, 0.004]))
        eyes = pair(lambda s: ell(p, (s * 0.13, 0.58, 0.50), (0.06, 0.07, 0.05)))
        parts = [(shell, M_SKIN), (legs, M_LEG), (eyes, M_EYE), (np.minimum(mand, ant), M_FIX)]
        if fear:
            blades = pair(lambda s: limb(p, [(s * 0.22, 0.62, 0.30), (s * 0.40, 1.05, 0.50), (s * 0.30, 0.70, 0.86)], [0.05, 0.035, 0.008]))
            parts.append((blades, M_FIX))
            parts.append((spine_row(p, [(s * x, 0.82 - 0.1 * x, -0.2 - 0.5 * x) for s in (-1, 1) for x in (0.1, 0.25)], 0.18, 0.035), M_FIX))
            parts.append((crack_glow(p, shell, 5.0), M_GLOW))
        return union(parts)
    mats = [skin_scaly(), skin_scaly(), EYE, horn_mat(), GLOW]
    return scene, mats, ["skin", "skin", "eye", "fixed", "glow"], 1.5, rot_x(0.30) @ rot_y(0.22)


def winged(fear=0):
    """Wyvern/dragon: long low body, S-curved neck, wedge head with open jaw, membranous wings."""
    from monsters_rock import sheet
    M_SKIN, M_WING, M_EYE, M_TOOTH, M_HORN, M_GLOW = range(6)

    def scene(p):
        body = ell(p, (0, 0.72, -0.20), (0.40, 0.36, 0.80))
        body = smin(body, ell(p, (0, 0.80, 0.42), (0.42, 0.42, 0.38)), 0.16)
        body = smin(body, pair(lambda s: ell(p, (s * 0.34, 0.74, -0.62), (0.22, 0.32, 0.34))), 0.12)
        neck = limb(p, [(0, 0.95, 0.55), (0, 1.30, 0.80), (0, 1.62, 0.92), (0, 1.86, 1.12)], [0.30, 0.24, 0.20, 0.19])
        head = rc(p, (0, 1.98, 1.16), (0, 1.84, 1.86), 0.27, 0.12)
        head = smin(head, ell(p, (0, 2.08, 1.30), (0.24, 0.09, 0.20)), 0.06)
        head = smin(head, pair(lambda s: ell(p, (s * 0.20, 1.92, 1.22), (0.08, 0.10, 0.14))), 0.05)
        jaw = rc(p, (0, 1.74, 1.24), (0, 1.52, 1.78), 0.15, 0.07)
        body = smin(smin(smin(body, neck, 0.12), head, 0.08), jaw, 0.05)
        body = smax(body, -rc(p, (0, 1.80, 1.36), (0, 1.66, 1.92), 0.07, 0.06), 0.03)
        front = pair(lambda s: limb(p, [(s * 0.36, 0.78, 0.48), (s * 0.54, 0.44, 0.62), (s * 0.50, 0.20, 0.70), (s * 0.52, 0.05, 0.86)],
                                    [0.17, 0.12, 0.09, 0.07]))
        hind = pair(lambda s: limb(p, [(s * 0.40, 0.72, -0.60), (s * 0.52, 0.42, -0.42), (s * 0.50, 0.24, -0.76), (s * 0.50, 0.05, -0.62)],
                                   [0.20, 0.13, 0.09, 0.07]))
        tail = limb(p, [(0, 0.74, -0.95), (0.35, 0.50, -1.45), (0.95, 0.28, -1.50), (1.35, 0.14, -1.05), (1.45, 0.08, -0.55)],
                    [0.26, 0.18, 0.12, 0.07, 0.02])
        body = smin(smin(smin(body, front, 0.08), hind, 0.08), tail, 0.10) - (fbm(p, 12.0, 3, 5) - 0.5) * 0.012

        def wing_s(s):
            arm = limb(p, [(s * 0.30, 1.05, 0.10), (s * 0.85, 1.85, -0.10), (s * 1.45, 2.35, -0.35)], [0.08, 0.055, 0.03])
            fing = np.minimum.reduce([limb(p, [(s * 1.45, 2.35, -0.35), (s * (1.85 + 0.08 * k), 1.85 - 0.38 * k, -0.40), (s * (1.5 + 0.25 * k), 1.10 - 0.2 * k, -0.40)],
                                           [0.03, 0.022, 0.008]) for k in (0, 1, 2)])
            return np.minimum(arm, fing)
        bones = pair(wing_s)
        memb = pair(lambda s: sheet(p, [(s * 0.30, 1.10), (s * 1.45, 2.35), (s * 2.0, 1.75), (s * 1.95, 1.05), (s * 1.5, 0.80), (s * 0.6, 0.85)], -0.38))
        eyes = pair(lambda s: ell(p, (s * 0.17, 2.02, 1.40), (0.050, 0.032, 0.040), rz=s * 0.35))
        if fear:
            eyes = np.minimum(eyes, pair(lambda s: ell(p, (s * 0.13, 2.09, 1.30), (0.030, 0.020, 0.025), rz=s * 0.35)))

        def teeth(q):
            out = None
            for s in (-1, 1):
                for k in range(5):
                    z = 1.40 + 0.09 * k
                    up = rc(q, (s * (0.11 - 0.012 * k), 1.80 - 0.01 * k, z), (s * (0.11 - 0.012 * k), 1.71 - 0.01 * k, z + 0.01), 0.016, 0.002)
                    dn = rc(q, (s * (0.10 - 0.012 * k), 1.58 - 0.035 * k, z + 0.03), (s * (0.10 - 0.012 * k), 1.66 - 0.035 * k, z + 0.04), 0.014, 0.002)
                    d = np.minimum(up, dn)
                    out = d if out is None else np.minimum(out, d)
            return out
        td = part(p, (0, 1.70, 1.60), 0.5, teeth)
        claws = part(p, (0, 0.05, 0.1), 1.3, lambda q: np.minimum.reduce([
            rc(q, (s * 0.52 + dx, 0.05, zf + 0.06), (s * 0.52 + dx * 1.4, 0.01, zf + 0.20), 0.022, 0.003)
            for s in (-1, 1) for zf in (0.86, -0.62) for dx in (-0.05, 0.0, 0.05)]))
        hn = pair(lambda s: limb(p, [(s * 0.13, 2.14, 1.22), (s * 0.28, 2.34, 1.00), (s * 0.34, 2.46, 0.72), (s * 0.30, 2.50, 0.52)],
                                 [0.060, 0.045, 0.025, 0.006]))
        hn = np.minimum(hn, claws)
        parts = [(np.minimum(body, bones), M_SKIN), (memb, M_WING), (eyes, M_EYE), (td, M_TOOTH)]
        if fear:
            hn = np.minimum(hn, spine_row(p, [(0, 1.08 - 0.02 * i, 0.42 - 0.24 * i) for i in range(7)], 0.20, 0.045))
            hn = np.minimum(hn, spine_row(p, [(0, 1.46, 0.80), (0, 1.74, 0.94), (0, 2.12, 1.12)], 0.15, 0.04))
            parts.append((crack_glow(p, body, 4.0), M_GLOW))
            parts.append((sd_sphere(p, v3(0, 1.70, 1.62), 0.06), M_GLOW))
        parts.append((hn, M_HORN))
        return union(parts)
    wing = Material(GREY, rough=0.55, spec=0.3, sss=1.2, tint_fn=lambda p, a: a * (0.45 + 0.5 * fbm(p, 10.0, 3, 5))[:, None])
    mats = [skin_scaly(), wing, EYE, tooth(), horn_mat(), GLOW]
    return scene, mats, ["skin", "skin", "eye", "fixed", "fixed", "glow"], 3.0, rot_x(0.16) @ rot_y(0.45)


def serpent(fear=0):
    """Rearing cobra-like serpent with a hood; coils on the ground."""
    M_SKIN, M_BELLY, M_EYE, M_TOOTH, M_HORN, M_GLOW = range(6)

    def scene(p):
        # ground coils: two tori, then a rising neck
        coil1 = sd_torus(p, v3(0, 0.14, -0.10), 0.52, 0.14, axis="y")
        coil2 = sd_torus(p, v3(0.06, 0.38, -0.08), 0.36, 0.12, axis="y")
        neck = limb(p, [(0.36, 0.38, -0.08), (0.24, 0.70, 0.12), (0.06, 1.10, 0.20), (0.0, 1.45, 0.22), (0.0, 1.62, 0.30)],
                    [0.13, 0.12, 0.11, 0.10, 0.09])
        hood = ell(p, (0, 1.40, 0.18), (0.30, 0.30, 0.05))
        head = rc(p, (0, 1.66, 0.26), (0, 1.62, 0.58), 0.12, 0.07)
        body = smin(smin(smin(coil1, coil2, 0.08), neck, 0.08), hood, 0.06)
        body = smin(body, head, 0.05)
        body = smin(body, pair(lambda s: ell(p, (s * 0.07, 1.72, 0.38), (0.06, 0.03, 0.08))), 0.03)       # brows
        body = smax(body, -ell(p, (0, 1.60, 0.56), (0.06, 0.02, 0.08)), 0.01)
        tail = limb(p, [(-0.50, 0.12, 0.10), (-0.70, 0.10, 0.40), (-0.55, 0.07, 0.70)], [0.10, 0.06, 0.015])
        body = smin(body, tail, 0.06)
        belly = np.maximum(ell(p, (0, 1.20, 0.26), (0.10, 0.45, 0.05)), -(p[:, 2] - 0.22))
        eyes = pair(lambda s: ell(p, (s * 0.075, 1.70, 0.44), (0.032, 0.025, 0.025)))
        fangs = pair(lambda s: rc(p, (s * 0.035, 1.60, 0.56), (s * 0.035, 1.50 if fear else 1.53, 0.58), 0.012, 0.002))
        tongue = limb(p, [(0, 1.60, 0.60), (0, 1.58, 0.72), (0.03, 1.55, 0.80)], [0.010, 0.008, 0.003])
        parts = [(body, M_SKIN), (belly, M_BELLY), (eyes, M_EYE), (np.minimum(fangs, tongue), M_TOOTH)]
        if fear:
            parts.append((horns(p, (0, 1.74, 0.30), spread=0.07, length=0.30, back=-0.25, thick=0.035), M_HORN))
            parts.append((spine_row(p, [(np.cos(a) * 0.30, 1.40 + np.sin(a) * 0.28, 0.12) for a in np.linspace(0.3, np.pi - 0.3, 7)], 0.12, 0.025), M_HORN))
            parts.append((crack_glow(p, body, 5.0), M_GLOW))
        return union(parts)
    belly = Material((0.88, 0.86, 0.80), rough=0.4, spec=0.5, sss=0.6,
                     bump=lambda p: (np.abs(np.sin(p[:, 1] * 60.0)) - 0.5) * 0.004)
    mats = [skin_scaly(), belly, EYE, tooth(), horn_mat(), GLOW]
    return scene, mats, ["skin", "skin", "eye", "fixed", "fixed", "glow"], 1.4, rot_x(0.12) @ rot_y(0.20)


def wraith(fear=0):
    """Hooded spectre: tattered cloak, skeletal hands, glowing eyes in a dark hood."""
    M_CLOAK, M_EYE, M_BONE, M_DARK, M_GLOW = range(5)

    def scene(p):
        y = p[:, 1]
        hem = (fbm(p * np.array([3.0, 0.5, 3.0], dtype=np.float32), 5.0, 3, 6) - 0.5) * 0.35
        cloak = rc(p, (0, 0.20, 0.0), (0, 1.55, 0.05), 0.48, 0.22)
        cloak = np.maximum(cloak, 0.10 + hem - y)                                                   # ragged hem
        hood = ell(p, (0, 1.70, 0.05), (0.26, 0.28, 0.27))
        cloak = smin(cloak, hood, 0.10)
        cloak = smax(cloak, -ell(p, (0, 1.66, 0.26), (0.16, 0.20, 0.16)), 0.04)                    # hood opening
        sleeves = pair(lambda s: limb(p, [(s * 0.30, 1.40, 0.0), (s * 0.55, 1.05, 0.22), (s * 0.62, 0.86, 0.40)], [0.12, 0.14, 0.17]))
        sleeves = np.maximum(sleeves, -ell(p, (0, 0.86, 0.46), (0.9, 0.06, 0.08)))
        cloak = smin(cloak, sleeves, 0.06)
        cloak = cloak - (fbm(p * np.array([2.5, 0.6, 2.5], dtype=np.float32), 6.0, 3, 3) - 0.5) * 0.04
        face = ell(p, (0, 1.66, 0.16), (0.14, 0.17, 0.12))                                         # shadowed face
        eyes = pair(lambda s: ell(p, (s * 0.06, 1.70, 0.27), (0.035, 0.022, 0.02)))

        def hand(q, s):
            out = rc(q, (s * 0.62, 0.84, 0.44), (s * 0.64, 0.78, 0.50), 0.035, 0.03)
            for k, dx in enumerate((-0.04, -0.013, 0.013, 0.04)):
                d = limb(q, [(s * 0.64 + dx, 0.78, 0.50), (s * 0.66 + dx * 1.4, 0.66, 0.56), (s * 0.66 + dx * 1.6, 0.56, 0.54)], [0.012, 0.010, 0.004])
                out = np.minimum(out, d)
            return out
        hands = part(p, (0, 0.72, 0.5), 0.9, lambda q: np.minimum(hand(q, 1), hand(q, -1)))
        parts = [(cloak, M_CLOAK), (face, M_DARK), (eyes, M_EYE), (hands, M_BONE)]
        if fear:
            parts.append((horns(p, (0, 1.90, 0.02), spread=0.14, length=0.42, back=-0.25, thick=0.045), M_BONE))
            chains = sd_torus(p, v3(0, 1.05, 0.0), 0.36, 0.014, axis="y")
            parts.append((chains, M_DARK))
            runes = crack_glow(p, cloak, 3.0)
            parts.append((runes, M_GLOW))
        return union(parts)
    dark = Material((0.02, 0.02, 0.025), rough=0.9, spec=0.05)
    mats = [skin_cloth(), EYE, bone_material((0.82, 0.78, 0.66)), dark, GLOW]
    return scene, mats, ["skin", "eye", "fixed", "fixed", "glow"], 1.6, rot_x(0.06) @ rot_y(0.10)


def golem(fear=0):
    from sdf import sd_box
    M_ROCK, M_EYE, M_CRYS, M_GLOW = range(4)

    def blocky(p, c, h, rad=0.04):
        return sd_box(p, np.array(c, dtype=np.float32), np.array(h, dtype=np.float32), rad)

    def scene(p):
        torso = blocky(p, (0, 1.20, 0.0), (0.46, 0.50, 0.34), 0.12)
        torso = smin(torso, blocky(p, (0, 0.62, 0.0), (0.30, 0.18, 0.24), 0.08), 0.10)
        head = blocky(p, (0, 1.86, 0.10), (0.24, 0.20, 0.22), 0.08)
        torso = smin(torso, head, 0.08)
        shoulders = pair(lambda s: ell(p, (s * 0.60, 1.52, 0.0), (0.26, 0.24, 0.26)))
        arms = pair(lambda s: limb(p, [(s * 0.62, 1.46, 0.02), (s * 0.80, 0.92, 0.14), (s * 0.78, 0.42, 0.30)], [0.22, 0.20, 0.17]))
        fists = pair(lambda s: blocky(p, (s * 0.78, 0.28, 0.34), (0.20, 0.18, 0.18), 0.07))
        legs = pair(lambda s: limb(p, [(s * 0.24, 0.62, 0.0), (s * 0.28, 0.30, 0.05), (s * 0.28, 0.08, 0.10)], [0.24, 0.20, 0.17]))
        feet = pair(lambda s: blocky(p, (s * 0.28, 0.07, 0.14), (0.22, 0.07, 0.24), 0.05))
        body = np.minimum.reduce([torso, shoulders, arms, fists, legs, feet])
        body = smax(body, -blocky(p, (0, 1.30, 0.44), (0.18, 0.02, 0.10), 0.02), 0.02)
        body = body + (value_noise(p, 9.0, 3) - 0.5) * 0.06
        brow = blocky(p, (0, 1.97, 0.28), (0.20, 0.025, 0.04), 0.01)
        body = smin(body, brow, 0.03)
        eyes = pair(lambda s: ell(p, (s * 0.11, 1.90, 0.30), (0.060, 0.032, 0.03)))
        core = sd_sphere(p, v3(0, 1.28, 0.36), 0.09)
        parts = [(body, M_ROCK), (eyes, M_EYE), (core, M_GLOW)]
        if fear:
            cr = None
            for (x, y, z, hgt, tilt) in ((0.55, 1.80, 0.0, 0.50, 0.3), (-0.58, 1.84, 0.0, 0.46, -0.3), (0.10, 2.08, -0.10, 0.42, 0.1),
                                         (0.34, 1.62, -0.28, 0.40, 0.2), (-0.34, 1.60, -0.28, 0.44, -0.2), (0.80, 0.70, 0.10, 0.30, 0.5),
                                         (-0.82, 0.74, 0.10, 0.32, -0.5), (0.0, 1.70, -0.34, 0.55, 0.0)):
                d = rc(p, (x, y, z), (x + tilt, y + hgt, z - 0.05), 0.085, 0.004)
                cr = d if cr is None else np.minimum(cr, d)
            parts.append((cr, M_CRYS))
            parts.append((crack_glow(p, body, 3.5, 0.016), M_GLOW))
        return union(parts)
    crys = Material((0.20, 0.20, 0.22), rough=0.08, spec=2.0, sss=0.6)
    mats = [skin_rock(), EYE, crys, GLOW]
    return scene, mats, ["skin", "eye", "fixed", "glow"], 2.1, rot_x(0.12) @ rot_y(0.12)


BODIES = {"blob": blob, "hound": hound, "humanoid": humanoid, "insect": insect,
          "winged": winged, "serpent": serpent, "wraith": wraith, "golem": golem}
