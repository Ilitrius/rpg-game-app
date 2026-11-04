"""Swamp monsters (Sumpf): toxic slime, mire toad, wisp, hydra boss. Same style/tools as monsters.py."""
from __future__ import annotations

import numpy as np

from monsters import (_eye_parts, _gel_mass, _teeth, bone_material, ell, gel_material, limb, part, rc, wet_black)
from sdf import Material, fbm, rot_x, rot_y, sd_sphere, sd_torus, smax, smin, union, v3, value_noise


def pair(f):
    return np.minimum(f(1), f(-1))


def toxic_slime(rarity="common"):
    M_GEL, M_SCLERA, M_IRIS, M_PUPIL, M_LID, M_TOOTH, M_BUB = range(7)
    gel = gel_material((0.52, 0.72, 0.08), (0.95, 1.0, 0.20))
    sclera = Material((0.92, 0.95, 0.55), rough=0.05, spec=1.6, sss=0.6)
    iris = Material((0.65, 0.10, 0.75), rough=0.05, spec=1.6, emissive=0.5)
    teeth = bone_material((0.75, 0.80, 0.45))
    bub = Material((0.60, 0.85, 0.25), rough=0.03, spec=2.2, emissive=0.2, sss=0.8)

    def scene(p):
        body = _gel_mass(p)
        e1 = _eye_parts(p, (0.21, 0.67, 0.50), 0.088)
        e2 = _eye_parts(p, (-0.23, 0.60, 0.50), 0.066)
        tooth = part(p, (0, 0.25, 0.64), 0.40, lambda q: _teeth(q, 1.0, 9, 0.365, 0.26, 0.64, 1.0))
        # toxic bubbles rising off the top
        bubbles = None
        for x, y, z, r in ((0.10, 1.0, 0.0, 0.07), (-0.25, 0.95, -0.1, 0.05), (0.32, 0.92, 0.1, 0.045), (-0.05, 1.12, 0.1, 0.04)):
            d = sd_sphere(p, v3(x, y, z), r)
            bubbles = d if bubbles is None else np.minimum(bubbles, d)
        return union([(body, M_GEL), (np.minimum(e1[0], e2[0]), M_SCLERA), (np.minimum(e1[1], e2[1]), M_IRIS),
                      (np.minimum(e1[2], e2[2]), M_PUPIL), (np.minimum(e1[3], e2[3]), M_GEL), (tooth, M_TOOTH), (bubbles, M_BUB)])
    return scene, [gel, sclera, iris, wet_black(), gel, teeth, bub], 1.3, rot_x(0.26) @ rot_y(0.12)


def mire_toad(rarity="common"):
    M_SKIN, M_BELLY, M_EYE, M_PUPIL, M_WART, M_MOUTH = range(6)

    def skin_tint(p, a):
        return a * (0.55 + 0.9 * fbm(p, 5.0, 3, 4))[:, None] * (1.0 - 0.35 * np.clip(0.4 - p[:, 1], 0, 1))[:, None]
    skin = Material((0.22, 0.30, 0.12), rough=0.30, spec=0.9, sss=0.5, tint_fn=skin_tint,
                    bump=lambda p: (fbm(p, 55.0, 3, 6) - 0.5) * 0.010)
    belly = Material((0.62, 0.60, 0.38), rough=0.35, spec=0.6, sss=0.6)
    eye = Material((0.95, 0.70, 0.08), rough=0.03, spec=3.0, emissive=0.5)
    pupil = wet_black()
    wart = Material((0.38, 0.30, 0.14), rough=0.4, spec=0.6)
    mouth = Material((0.55, 0.15, 0.15), rough=0.4, spec=0.5, sss=0.8)

    def scene(p):
        body = ell(p, (0, 0.45, -0.05), (0.60, 0.42, 0.55))
        body = smin(body, ell(p, (0, 0.40, 0.30), (0.50, 0.34, 0.40)), 0.14)
        head = ell(p, (0, 0.50, 0.55), (0.46, 0.24, 0.34))
        body = smin(body, head, 0.10)
        body = smax(body, -ell(p, (0, 0.40, 0.88), (0.36, 0.045, 0.30)), 0.03)          # wide mouth line
        # eye bulges on top
        eyes_b = pair(lambda s: ell(p, (s * 0.24, 0.74, 0.56), (0.12, 0.10, 0.12)))
        body = smin(body, eyes_b, 0.05)
        eyes = pair(lambda s: ell(p, (s * 0.24, 0.76, 0.64), (0.088, 0.075, 0.07)))
        pup = pair(lambda s: ell(p, (s * 0.24, 0.775, 0.70), (0.065, 0.014, 0.012)))
        # throat sac, front and back legs, feet
        throat = ell(p, (0, 0.34, 0.76), (0.22, 0.14, 0.16))
        front = pair(lambda s: limb(p, [(s * 0.42, 0.42, 0.30), (s * 0.58, 0.20, 0.52), (s * 0.58, 0.05, 0.74)], [0.11, 0.075, 0.055]))
        hind = pair(lambda s: limb(p, [(s * 0.48, 0.45, -0.25), (s * 0.78, 0.24, -0.05), (s * 0.66, 0.06, 0.38)], [0.20, 0.12, 0.07]))
        feet = pair(lambda s: np.minimum(ell(p, (s * 0.60, 0.04, 0.84), (0.14, 0.04, 0.12)), ell(p, (s * 0.66, 0.04, 0.48), (0.17, 0.04, 0.14))))
        body = smin(smin(smin(body, front, 0.08), hind, 0.10), throat, 0.10)
        body = smin(body, feet, 0.04)
        body = body - (fbm(p, 14.0, 3, 5) - 0.5) * 0.014

        def warts(q):
            out = None
            rng = np.random.default_rng(5)
            for _ in range(26):
                a = rng.uniform(-1.2, 1.2); z = rng.uniform(-0.45, 0.50)
                x = 0.53 * np.sin(a) * (1 - 0.3 * abs(z)); y = 0.45 + 0.385 * np.cos(a)
                d = sd_sphere(q, v3(x, y, z), rng.uniform(0.014, 0.028))
                out = d if out is None else np.minimum(out, d)
            return out
        wd = part(p, (0, 0.5, 0.0), 0.95, warts)
        return union([(body, M_SKIN), (eyes, M_EYE), (pup, M_PUPIL), (wd, M_WART)])
    return scene, [skin, belly, eye, pupil, wart, mouth], 1.4, rot_x(0.30) @ rot_y(0.16)


def wisp(rarity="common", core_c=(0.55, 0.95, 0.85), halo_c=(0.30, 0.85, 0.65)):
    M_CORE, M_HALO, M_EYE, M_FLAME = range(4)
    core = Material(core_c, rough=0.1, spec=1.0, emissive=1.1, sss=1.2,
                    tint_fn=lambda p, a: a * (0.7 + 0.5 * fbm(p, 6.0, 3, 3))[:, None])
    halo = Material(halo_c, rough=0.2, spec=0.6, emissive=0.55, sss=1.0)
    eye = Material((0.02, 0.10, 0.10), rough=0.05, spec=1.5)
    flame = Material((0.40, 1.0, 0.80), rough=0.2, spec=0.5, emissive=0.9, sss=1.0)

    def scene(p):
        body = ell(p, (0, 0.70, 0.0), (0.34, 0.40, 0.32))
        body = smin(body, rc(p, (0, 0.40, 0.0), (0, 0.05, 0.0), 0.20, 0.01), 0.18)      # teardrop tail
        body = body - (fbm(p, 6.0, 3, 8) - 0.5) * 0.05
        tips = None
        for i, (x, h, z) in enumerate(((0.0, 0.50, 0.0), (0.16, 0.34, 0.04), (-0.18, 0.30, -0.02), (0.08, 0.26, 0.10), (-0.07, 0.22, -0.06))):
            d = rc(p, (x * 0.8, 1.02 + 0.02 * i, z), (x * 1.5, 1.02 + h + 0.1, z), 0.09 - 0.012 * i, 0.004)
            tips = d if tips is None else np.minimum(tips, d)
        body = smin(body, tips, 0.10)
        ring = sd_torus(p, v3(0, 0.74, 0), 0.52, 0.018, axis="y")
        eyes = pair(lambda s: ell(p, (s * 0.12, 0.76, 0.28), (0.07, 0.10, 0.04), rz=s * 0.20))
        mouth = ell(p, (0, 0.58, 0.31), (0.07, 0.04, 0.03))
        return union([(body, M_CORE), (ring, M_HALO), (np.minimum(eyes, mouth), M_EYE)])
    return scene, [core, halo, eye, flame], 1.2, rot_x(0.12) @ rot_y(0.06)


def hydra(rarity="common"):
    M_SKIN, M_BELLY, M_EYE, M_TOOTH, M_SPIKE = range(5)

    def tint(p, a):
        return a * (0.6 + 0.8 * fbm(p, 6.0, 3, 2))[:, None]
    skin = Material((0.12, 0.30, 0.20), rough=0.35, spec=0.8, sss=0.5, tint_fn=tint,
                    bump=lambda p: (value_noise(p, 90.0, 3) - 0.5) * 0.008 + (fbm(p, 35.0, 3, 9) - 0.5) * 0.012)
    belly = Material((0.70, 0.66, 0.42), rough=0.4, spec=0.5, sss=0.7)
    eye = Material((1.0, 0.75, 0.05), rough=0.03, spec=3.0, emissive=0.8)
    tooth = bone_material((0.92, 0.90, 0.75))
    spike = Material((0.28, 0.22, 0.12), rough=0.5, spec=0.5)

    def neck(sx, tilt, h):
        pts = [(sx * 0.30, 0.55, 0.05), (sx * (0.40 + tilt * 0.4), 1.05, 0.15), (sx * (0.46 + tilt), h * 0.8 + 0.2, 0.30), (sx * (0.46 + tilt), h, 0.46)]
        return pts

    def scene(p):
        body = ell(p, (0, 0.45, -0.20), (0.78, 0.44, 0.78))
        body = smin(body, ell(p, (0, 0.52, 0.15), (0.55, 0.40, 0.45)), 0.14)
        legs = pair(lambda s: limb(p, [(s * 0.55, 0.50, 0.15), (s * 0.70, 0.22, 0.30), (s * 0.72, 0.04, 0.46)], [0.18, 0.11, 0.08]))
        legs = np.minimum(legs, pair(lambda s: limb(p, [(s * 0.58, 0.46, -0.50), (s * 0.78, 0.20, -0.40), (s * 0.80, 0.04, -0.28)], [0.20, 0.12, 0.08])))
        body = smin(body, legs, 0.10)
        tail = limb(p, [(0, 0.40, -0.80), (0.2, 0.30, -1.20), (0.5, 0.15, -1.45)], [0.22, 0.14, 0.05])
        body = smin(body, tail, 0.12)
        heads = None; eyes = None; teeth = None; necks = None
        specs = ((-1, 0.10, 1.90), (0, 0.0, 2.15), (1, 0.10, 1.80))
        for sx, tilt, h in specs:
            x0 = sx * 0.34
            pts = [(x0 * 0.9, 0.62, 0.20), (x0 * 1.3, 1.10, 0.28), (x0 * 1.5 + sx * tilt, h * 0.70, 0.36), (x0 * 1.6 + sx * tilt, h, 0.50)]
            nk = limb(p, pts, [0.20, 0.15, 0.12, 0.11])
            necks = nk if necks is None else np.minimum(necks, nk)
            hc = np.array([pts[-1][0], pts[-1][1] + 0.02, pts[-1][2] + 0.10], dtype=np.float32)
            hd = rc(p, tuple(hc + np.array([0, 0.02, -0.02], dtype=np.float32)), tuple(hc + np.array([0, -0.06, 0.34], dtype=np.float32)), 0.15, 0.08)
            heads = hd if heads is None else np.minimum(heads, hd)
            for ex in (-1, 1):
                e = ell(p, tuple(hc + np.array([ex * 0.10, 0.08, 0.14], dtype=np.float32)), (0.045, 0.040, 0.035))
                eyes = e if eyes is None else np.minimum(eyes, e)
            for ex in (-1, 1):
                for k in range(3):
                    t = rc(p, tuple(hc + np.array([ex * (0.04 + 0.025 * k), -0.09, 0.22 + 0.05 * k], dtype=np.float32)),
                           tuple(hc + np.array([ex * (0.04 + 0.025 * k), -0.17, 0.23 + 0.05 * k], dtype=np.float32)), 0.014, 0.002)
                    teeth = t if teeth is None else np.minimum(teeth, t)
        body = smin(body, necks, 0.08)
        body = smin(body, heads, 0.06)
        body = body - (fbm(p, 12.0, 3, 5) - 0.5) * 0.015
        belly = np.maximum(ell(p, (0, 0.40, 0.45), (0.28, 0.30, 0.20)), -(p[:, 2] - 0.30))
        spikes = part(p, (0, 0.9, -0.2), 1.1, lambda q: np.minimum.reduce([
            rc(q, (0, 0.80 - 0.05 * i + 0.18 * np.sin(i * 0.6), -0.55 + 0.20 * i), (0, 0.80 + 0.14 - 0.05 * i + 0.18 * np.sin(i * 0.6), -0.58 + 0.20 * i), 0.04, 0.003)
            for i in range(5)]))
        return union([(body, M_SKIN), (eyes, M_EYE), (teeth, M_TOOTH), (spikes, M_SPIKE)])
    return scene, [skin, belly, eye, tooth, spike], 2.3, rot_x(0.16) @ rot_y(0.14)


BUILDERS = {"toxic_slime": toxic_slime, "mire_toad": mire_toad, "wisp": wisp, "hydra": hydra}
