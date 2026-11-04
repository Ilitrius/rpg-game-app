"""Mountain + cave monsters: stone/crystal golems, yeti, bat, dragon; snow wolf, ice wisp, cave spider are recolours
of the forest/swamp models. Same tools as monsters.py."""
from __future__ import annotations

from functools import partial

import numpy as np

from monsters import bone_material, ell, flesh_material, fur_material, limb, part, rc, shaggy
from monsters_forest import forest_spider, wolf
from monsters_swamp import wisp
from sdf import Material, fbm, rot_x, rot_y, sd_sphere, smax, smin, union, v3, value_noise


def pair(f):
    return np.minimum(f(1), f(-1))


def sheet(p, pts, z, half=0.012):
    """Thin extruded convex polygon in the plane z (wing membrane); pts are (x, y) in order around the outline."""
    pts = [np.array(q, dtype=np.float32) for q in pts]
    cx = sum(q[0] for q in pts) / len(pts); cy = sum(q[1] for q in pts) / len(pts)
    d = None
    for a, b in zip(pts, pts[1:] + pts[:1]):
        e = b - a
        n = np.array([e[1], -e[0]], dtype=np.float32); n = n / np.linalg.norm(n)
        if n[0] * (cx - a[0]) + n[1] * (cy - a[1]) > 0:
            n = -n
        h = (p[:, 0] - a[0]) * n[0] + (p[:, 1] - a[1]) * n[1]
        d = h if d is None else np.maximum(d, h)
    return np.maximum(d, np.abs(p[:, 2] - z) - half)


def golem(rarity="common", stone=(0.34, 0.33, 0.31), crystal=None, glow=(1.0, 0.55, 0.10)):
    """Blocky rock giant. With `crystal` (colour) the body grows glassy crystal spikes instead of moss."""
    M_ROCK, M_EYE, M_CRYS, M_MOSS = range(4)

    def bump(p):
        return (value_noise(p, 14.0, 3) - 0.5) * 0.045 + (value_noise(p, 55.0, 6) - 0.5) * 0.012

    rock = Material(stone, rough=0.88, spec=0.12, bump=bump,
                    tint_fn=lambda p, a: a * (0.55 + 0.9 * fbm(p, 5.0, 4, 3))[:, None] * (0.8 + 0.3 * np.clip(p[:, 1] / 1.5, 0, 1))[:, None])
    eye = Material(glow, rough=0.05, spec=2.0, emissive=1.0)
    ccol = crystal if crystal is not None else (0.30, 0.55, 0.85)
    crys = Material(ccol, rough=0.04, spec=2.2, emissive=0.35, sss=1.0)
    moss = Material((0.22, 0.38, 0.12), rough=0.9, spec=0.1, tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p, 18.0, 3, 8))[:, None])

    def blocky(p, c, h, rad=0.04):
        from sdf import sd_box
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
        body = smax(body, -blocky(p, (0, 1.30, 0.44), (0.18, 0.02, 0.10), 0.02), 0.02)      # chest crack
        body = body + (value_noise(p, 9.0, 3) - 0.5) * 0.06
        eyes = pair(lambda s: ell(p, (s * 0.11, 1.90, 0.30), (0.060, 0.032, 0.03)))
        brow = blocky(p, (0, 1.97, 0.28), (0.20, 0.025, 0.04), 0.01)
        body = smin(body, brow, 0.03)
        if crystal is not None:
            cr = None
            rng = np.random.default_rng(11)
            for (x, y, z, hgt, tilt) in ((0.55, 1.80, 0.0, 0.50, 0.3), (-0.58, 1.84, 0.0, 0.46, -0.3), (0.10, 2.08, -0.10, 0.42, 0.1),
                                         (0.34, 1.62, -0.28, 0.40, 0.2), (-0.34, 1.60, -0.28, 0.44, -0.2), (0.80, 0.70, 0.10, 0.30, 0.5),
                                         (-0.82, 0.74, 0.10, 0.32, -0.5), (0.0, 1.70, -0.34, 0.55, 0.0)):
                d = rc(p, (x, y, z), (x + tilt, y + hgt, z - 0.05), 0.085, 0.004)
                cr = d if cr is None else np.minimum(cr, d)
            return union([(body, M_ROCK), (eyes, M_EYE), (cr, M_CRYS)])
        mossd = np.maximum(ell(p, (0, 1.72, -0.05), (0.62, 0.12, 0.40)), -(p[:, 1] - 1.55))
        return union([(body, M_ROCK), (eyes, M_EYE), (mossd, M_MOSS)])
    return scene, [rock, eye, crys, moss], 2.1, rot_x(0.12) @ rot_y(0.12)


def yeti(rarity="common"):
    M_FUR, M_FACE, M_EYE, M_TOOTH, M_CLAW = range(5)
    fur = fur_material((0.86, 0.88, 0.92), belly=1.1, dirt=0.15, strand=0.005, clump=0.008, y_ref=1.6, rough=0.85)
    face = Material((0.34, 0.42, 0.55), rough=0.5, spec=0.4, sss=0.6)
    eye = Material((0.45, 0.85, 1.0), rough=0.03, spec=3.0, emissive=0.9)
    tooth = bone_material((0.95, 0.93, 0.85))
    claw = Material((0.18, 0.16, 0.15), rough=0.35, spec=0.6)

    def scene(p):
        torso = rc(p, (0, 0.90, -0.05), (0, 1.55, 0.05), 0.46, 0.52)
        torso = smin(torso, ell(p, (0, 1.50, 0.0), (0.68, 0.34, 0.42)), 0.14)
        belly = ell(p, (0, 0.95, 0.12), (0.46, 0.46, 0.40))
        torso = smin(torso, belly, 0.1)
        head = ell(p, (0, 1.95, 0.20), (0.34, 0.32, 0.32))
        torso = smin(torso, head, 0.10)
        arms = pair(lambda s: limb(p, [(s * 0.62, 1.55, 0.0), (s * 0.92, 1.05, 0.12), (s * 0.90, 0.40, 0.28)], [0.22, 0.20, 0.16]))
        hands = pair(lambda s: ell(p, (s * 0.90, 0.30, 0.30), (0.17, 0.20, 0.15)))
        legs = pair(lambda s: limb(p, [(s * 0.28, 0.80, 0.0), (s * 0.34, 0.40, 0.08), (s * 0.34, 0.10, 0.14)], [0.26, 0.20, 0.16]))
        feet = pair(lambda s: ell(p, (s * 0.34, 0.07, 0.24), (0.20, 0.08, 0.30)))
        body = np.minimum.reduce([torso, arms, hands, legs, feet]) - shaggy(p, 0.022, 20.0, stretch=(1, 2.4, 1))
        brow = ell(p, (0, 2.05, 0.46), (0.28, 0.07, 0.10))
        body = smin(body, brow, 0.05)
        face_d = ell(p, (0, 1.88, 0.40), (0.24, 0.22, 0.12))
        eyes = pair(lambda s: ell(p, (s * 0.12, 1.98, 0.50), (0.048, 0.036, 0.030), rz=s * 0.3))
        mouthcut = ell(p, (0, 1.72, 0.50), (0.15, 0.08, 0.08))
        face_d = smax(face_d, -mouthcut, 0.02)

        def teeth(q):
            out = None
            for x in (-0.09, -0.045, 0.0, 0.045, 0.09):
                for up in (1, -1):
                    d = rc(q, (x, 1.72 + up * 0.07, 0.54), (x, 1.72 + up * 0.015, 0.56), 0.016, 0.003)
                    out = d if out is None else np.minimum(out, d)
            return out
        td = part(p, (0, 1.72, 0.55), 0.3, teeth)
        claws = part(p, (0, 0.2, 0.4), 1.4, lambda q: np.minimum.reduce([
            rc(q, (s * 0.90 + dx, 0.14, 0.34), (s * 0.90 + dx * 1.3, 0.02, 0.44), 0.024, 0.004) for s in (-1, 1) for dx in (-0.08, 0.0, 0.08)]))
        return union([(body, M_FUR), (face_d, M_FACE), (eyes, M_EYE), (td, M_TOOTH), (claws, M_CLAW)])
    return scene, [fur, face, eye, tooth, claw], 2.0, rot_x(0.12) @ rot_y(0.10)


def bat(rarity="common"):
    M_FUR, M_WING, M_EYE, M_TOOTH, M_EAR = range(5)
    fur = fur_material((0.16, 0.12, 0.13), belly=1.3, dirt=0.3, strand=0.003, clump=0.004, y_ref=1.0)
    wing = Material((0.26, 0.13, 0.17), rough=0.55, spec=0.3, sss=1.2,
                    tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p, 12.0, 3, 5))[:, None])
    eye = Material((1.0, 0.15, 0.08), rough=0.03, spec=3.0, emissive=1.0)
    tooth = bone_material((0.95, 0.93, 0.85))
    ear = Material((0.55, 0.30, 0.32), rough=0.5, spec=0.3, sss=1.0)

    def scene(p):
        body = ell(p, (0, 0.70, 0.0), (0.26, 0.34, 0.24))
        head = ell(p, (0, 1.05, 0.12), (0.20, 0.19, 0.19))
        body = smin(body, head, 0.08)
        body = smin(body, rc(p, (0, 1.0, 0.24), (0, 0.93, 0.36), 0.09, 0.05), 0.05)          # snout
        body = body - shaggy(p, 0.006, 40.0)
        ears = pair(lambda s: limb(p, [(s * 0.09, 1.16, 0.10), (s * 0.20, 1.42, 0.08), (s * 0.18, 1.52, 0.06)], [0.05, 0.035, 0.008]))
        eyes = pair(lambda s: ell(p, (s * 0.085, 1.07, 0.29), (0.036, 0.036, 0.026)))
        fangs = pair(lambda s: rc(p, (s * 0.034, 0.90, 0.39), (s * 0.034, 0.80, 0.40), 0.014, 0.002))
        # wings: arm + finger bones spread, membrane as thin ellipsoid sheets between them
        def wing_s(s):
            arm = limb(p, [(s * 0.22, 0.86, 0.0), (s * 0.70, 1.15, -0.05), (s * 1.12, 0.96, -0.05)], [0.05, 0.035, 0.025])
            fingers = np.minimum.reduce([limb(p, [(s * 1.12, 0.96, -0.05), (s * (1.32 + 0.1 * k), 0.72 - 0.30 * k, -0.04), (s * (1.20 + 0.2 * k), 0.34 - 0.30 * k + 0.20, -0.03)],
                                              [0.02, 0.014, 0.006]) for k in (0, 1, 2)])
            return np.minimum(arm, fingers)
        bones = pair(wing_s)
        def membrane_s(s):
            m = ell(p, (s * 0.72, 0.72, -0.04), (0.56, 0.38, 0.012), rz=s * 0.45)
            return m
        memb = pair(membrane_s)
        return union([(body, M_FUR), (np.minimum(bones, 1e3), M_FUR), (memb, M_WING), (eyes, M_EYE), (fangs, M_TOOTH), (ears, M_EAR)])
    return scene, [fur, wing, eye, tooth, ear], 1.9, rot_x(0.10) @ rot_y(0.0)


def dragon(rarity="common"):
    M_SCALE, M_BELLY, M_EYE, M_TOOTH, M_WING, M_HORN, M_FIRE = range(7)
    scale = Material((0.30, 0.07, 0.05), rough=0.32, spec=1.0, sss=0.3,
                     tint_fn=lambda p, a: a * (0.6 + 0.8 * fbm(p, 7.0, 3, 2))[:, None],
                     bump=lambda p: (value_noise(p, 55.0, 7) - 0.5) * 0.010 + (fbm(p, 14.0, 3, 9) - 0.5) * 0.012)
    belly = Material((0.80, 0.65, 0.38), rough=0.4, spec=0.5, sss=0.6)
    eye = Material((1.0, 0.85, 0.1), rough=0.03, spec=3.0, emissive=1.0)
    tooth = bone_material((0.95, 0.93, 0.80))
    wing = Material((0.34, 0.07, 0.07), rough=0.55, spec=0.3, sss=1.0,
                    tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p, 10.0, 3, 5))[:, None])
    horn = Material((0.20, 0.17, 0.15), rough=0.4, spec=0.5)
    fire = Material((1.0, 0.55, 0.1), rough=0.3, spec=0.2, emissive=1.4, sss=1.0)

    def scene(p):
        # long, low, muscular body: deep chest, slim waist, heavy haunches
        body = ell(p, (0, 0.72, -0.20), (0.40, 0.36, 0.80))
        body = smin(body, ell(p, (0, 0.80, 0.42), (0.42, 0.42, 0.38)), 0.16)                    # chest
        body = smin(body, pair(lambda s: ell(p, (s * 0.34, 0.74, -0.62), (0.22, 0.32, 0.34))), 0.12)  # haunches
        # S-curved neck up and forward, big wedge head with an open jaw
        neck = limb(p, [(0, 0.95, 0.55), (0, 1.30, 0.80), (0, 1.62, 0.92), (0, 1.86, 1.12)], [0.30, 0.24, 0.20, 0.19])
        head = rc(p, (0, 1.98, 1.16), (0, 1.84, 1.86), 0.27, 0.12)
        head = smin(head, ell(p, (0, 2.08, 1.30), (0.24, 0.09, 0.20)), 0.06)                       # brow ridge
        head = smin(head, pair(lambda s: ell(p, (s * 0.20, 1.92, 1.22), (0.08, 0.10, 0.14))), 0.05)  # cheeks
        jaw = rc(p, (0, 1.74, 1.24), (0, 1.52, 1.78), 0.15, 0.07)
        body = smin(smin(smin(body, neck, 0.12), head, 0.08), jaw, 0.05)
        body = smax(body, -rc(p, (0, 1.80, 1.36), (0, 1.66, 1.92), 0.07, 0.06), 0.03)              # mouth cavity
        # digitigrade legs with claws
        front = pair(lambda s: limb(p, [(s * 0.36, 0.78, 0.48), (s * 0.54, 0.44, 0.62), (s * 0.50, 0.20, 0.70), (s * 0.52, 0.05, 0.86)],
                                    [0.17, 0.12, 0.09, 0.07]))
        hind = pair(lambda s: limb(p, [(s * 0.40, 0.72, -0.60), (s * 0.52, 0.42, -0.42), (s * 0.50, 0.24, -0.76), (s * 0.50, 0.05, -0.62)],
                                   [0.20, 0.13, 0.09, 0.07]))
        # tail sweeping round to the front so it stays in the picture
        tail = limb(p, [(0, 0.74, -0.95), (0.35, 0.50, -1.45), (0.95, 0.28, -1.50), (1.35, 0.14, -1.05), (1.45, 0.08, -0.55)],
                    [0.26, 0.18, 0.12, 0.07, 0.02])
        body = smin(smin(smin(body, front, 0.08), hind, 0.08), tail, 0.10) - (fbm(p, 12.0, 3, 5) - 0.5) * 0.012
        claws = part(p, (0, 0.05, 0.1), 1.3, lambda q: np.minimum.reduce([
            rc(q, (s * 0.52 + dx, 0.05, zf + 0.06), (s * 0.52 + dx * 1.4, 0.01, zf + 0.20), 0.022, 0.003)
            for s in (-1, 1) for zf in (0.86, -0.62) for dx in (-0.05, 0.0, 0.05)]))
        spines = part(p, (0, 1.2, -0.4), 2.0, lambda q: np.minimum.reduce([
            rc(q, (0, y0, z0), (0, y0 + h0, z0 - 0.08), 0.045, 0.004)
            for y0, z0, h0 in ((1.08, 0.42, 0.20), (1.06, 0.18, 0.22), (1.06, -0.06, 0.22), (1.04, -0.30, 0.20), (1.00, -0.54, 0.18),
                               (0.92, -0.80, 0.16), (1.46, 0.80, 0.16), (1.74, 0.94, 0.15), (2.12, 1.12, 0.14))]))
        horns = pair(lambda s: limb(p, [(s * 0.13, 2.14, 1.22), (s * 0.28, 2.34, 1.00), (s * 0.34, 2.46, 0.72), (s * 0.30, 2.50, 0.52)],
                                    [0.060, 0.045, 0.025, 0.006]))
        eyes = pair(lambda s: ell(p, (s * 0.17, 2.02, 1.40), (0.050, 0.032, 0.040), rz=s * 0.35))

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
        fire_d = sd_sphere(p, v3(0, 1.70, 1.62), 0.06)                                             # ember glow in the throat
        belly_d = np.maximum(ell(p, (0, 0.62, 0.30), (0.26, 0.30, 0.60)), -(p[:, 1] - 0.0) + 0.0 - 10.0)
        belly_d = np.maximum(belly_d, p[:, 1] - 0.70)

        def wing_s(s):
            arm = limb(p, [(s * 0.30, 1.05, 0.10), (s * 0.85, 1.85, -0.10), (s * 1.45, 2.35, -0.35)], [0.08, 0.055, 0.03])
            fing = np.minimum.reduce([limb(p, [(s * 1.45, 2.35, -0.35), (s * (1.85 + 0.08 * k), 1.85 - 0.38 * k, -0.40), (s * (1.5 + 0.25 * k), 1.10 - 0.2 * k, -0.40)],
                                           [0.03, 0.022, 0.008]) for k in (0, 1, 2)])
            return np.minimum(arm, fing)
        bones = pair(wing_s)
        memb = pair(lambda s: sheet(p, [(s * 0.30, 1.10), (s * 1.45, 2.35), (s * 2.0, 1.75), (s * 1.95, 1.05), (s * 1.5, 0.80), (s * 0.6, 0.85)], -0.38))
        return union([(body, M_SCALE), (bones, M_SCALE), (memb, M_WING), (np.minimum(horns, spines), M_HORN),
                      (claws, M_HORN), (eyes, M_EYE), (td, M_TOOTH), (fire_d, M_FIRE)])
    return scene, [scale, belly, eye, tooth, wing, horn, fire], 3.0, rot_x(0.16) @ rot_y(0.45)


BUILDERS = {
    "stone_golem": partial(golem),
    "crystal_golem": partial(golem, stone=(0.22, 0.22, 0.30), crystal=(0.45, 0.30, 0.90), glow=(0.7, 0.4, 1.0)),
    "yeti": yeti,
    "snow_wolf": partial(wolf, coat=(0.88, 0.90, 0.94), under=(0.95, 0.96, 0.98), eye_col=(0.35, 0.80, 1.0)),
    "ice_wisp": partial(wisp, core_c=(0.65, 0.85, 1.0), halo_c=(0.55, 0.75, 1.0)),
    "bat": bat,
    "cave_spider": partial(forest_spider, body_c=(0.22, 0.20, 0.26), leg_c=(0.30, 0.28, 0.34), eye_col=(0.3, 0.9, 0.5), mark_c=(0.35, 0.80, 0.55)),
    "dragon": dragon,
}
