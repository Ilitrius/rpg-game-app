"""Forest monsters (Wald): wolf, forest spider, mushroom creature, treant boss. Same style/tools as monsters.py."""
from __future__ import annotations

import numpy as np

from monsters import bone_material, ell, flesh_material, fur_material, limb, part, rc, shaggy, wet_black  # noqa: F401
from sdf import Material, fbm, rot_x, rot_y, sd_sphere, smax, smin, union, v3, value_noise


def pair(f):
    """Union of f(+1) and f(-1) (mirror pair over the x axis)."""
    return np.minimum(f(1), f(-1))


def wolf(rarity="common"):
    M_FUR, M_BELLY, M_NOSE, M_EYE, M_TOOTH, M_CLAW, M_FLESH = range(7)
    fur = fur_material((0.30, 0.28, 0.27), belly=1.5, dirt=0.35, y_ref=0.9, strand=0.004, clump=0.006)
    belly = fur_material((0.62, 0.58, 0.52), belly=1.0, dirt=0.2, y_ref=0.9)
    nose = Material((0.03, 0.03, 0.03), rough=0.15, spec=1.4)
    eye = Material((0.95, 0.65, 0.08), rough=0.03, spec=3.0, emissive=0.55)
    tooth = bone_material((0.92, 0.88, 0.74))
    claw = Material((0.09, 0.08, 0.07), rough=0.35, spec=0.7)
    flesh = flesh_material((0.70, 0.30, 0.32))
    DY = 0.10

    def scene(p):
        pb = p - v3(0, DY, 0)
        body = rc(pb, (0, 0.62, -0.60), (0, 0.66, 0.16), 0.20, 0.25)
        body = smin(body, ell(pb, (0, 0.52, -0.10), (0.19, 0.20, 0.60)), 0.14)
        body = smin(body, rc(pb, (0, 0.66, 0.16), (0, 0.68, 0.46), 0.25, 0.20), 0.10)
        body = smin(body, ell(pb, (0, 0.72, 0.40), (0.26, 0.26, 0.20)), 0.12)          # ruff
        head = ell(pb, (0, 0.74, 0.62), (0.20, 0.19, 0.22))
        muzzle = rc(pb, (0, 0.68, 0.70), (0, 0.60, 1.06), 0.14, 0.065)
        body = smin(smin(body, head, 0.09), muzzle, 0.08)
        body = smin(body, ell(pb, (0, 0.84, 0.70), (0.13, 0.07, 0.12)), 0.06)
        lowjaw = rc(pb, (0, 0.55, 0.78), (0, 0.46, 1.02), 0.08, 0.04)

        def leg(sx, zf, th):
            return limb(p, [(sx * 0.22, 0.72, zf), (sx * 0.24, 0.40, zf + 0.06), (sx * 0.23, 0.08, zf + 0.02)],
                        [th, th * 0.62, th * 0.42])
        legs = np.minimum.reduce([leg(1, 0.28, 0.12), leg(-1, 0.28, 0.12), leg(1, -0.46, 0.14), leg(-1, -0.46, 0.14)])
        tail = limb(pb, [(0, 0.74, -0.70), (0.0, 0.52, -1.00), (0.0, 0.26, -1.16)], [0.07, 0.11, 0.07])
        fur_d = np.minimum.reduce([body, legs, tail]) - shaggy(p, 0.009, 30.0, stretch=(1, 2.2, 1))
        ears = pair(lambda s: ell(pb, (s * 0.15, 1.00, 0.56), (0.055, 0.14, 0.035), rz=-s * 0.18))
        bel = ell(pb, (0, 0.36, -0.08), (0.12, 0.12, 0.48))
        nosed = sd_sphere(pb, v3(0, 0.64, 1.07), 0.04)
        eyes = pair(lambda s: ell(pb, (s * 0.115, 0.80, 0.80), (0.032, 0.024, 0.026), rz=s * 0.35))

        def teeth(q):
            out = None
            for s in (-1, 1):
                for x, h in ((0.050, 0.07), (0.030, 0.04)):
                    d = np.minimum(rc(q, (s * x, 0.60, 1.00), (s * x, 0.60 - h, 1.01), 0.014, 0.002),
                                   rc(q, (s * x, 0.47, 0.99), (s * x, 0.47 + h * 0.8, 1.00), 0.012, 0.002))
                    out = d if out is None else np.minimum(out, d)
            return out
        td = part(pb, (0, 0.55, 1.0), 0.25, teeth)
        tongue = ell(pb, (0, 0.50, 0.90), (0.04, 0.012, 0.10))
        claws = part(p, (0, 0.04, 0.0), 0.9, lambda q: np.minimum.reduce([
            rc(q, (sx * 0.23 + dx, 0.03, zf + 0.10), (sx * 0.23 + dx * 1.3, 0.012, zf + 0.20), 0.016, 0.003)
            for sx in (-1, 1) for zf in (0.28, -0.46) for dx in (-0.03, 0.0, 0.03)]))
        return union([(fur_d, M_FUR), (bel, M_BELLY), (ears, M_FUR), (nosed, M_NOSE), (eyes, M_EYE),
                      (td, M_TOOTH), (np.minimum(tongue, lowjaw), M_FLESH), (claws, M_CLAW)])
    return scene, [fur, belly, nose, eye, tooth, claw, flesh], 1.5, rot_x(0.16) @ rot_y(0.50)


def forest_spider(rarity="common"):
    M_BODY, M_LEG, M_EYE, M_FANG, M_MARK = range(5)
    st = np.array([1.0, 3.0, 1.0], dtype=np.float32)

    def hairy(p, a):
        return a * (0.8 + 0.4 * value_noise(p * st, 80.0, 4))[:, None]
    body_m = Material((0.10, 0.075, 0.05), rough=0.55, spec=0.45, tint_fn=hairy,
                      bump=lambda p: (value_noise(p * st, 90.0, 2) - 0.5) * 0.008)
    leg_m = Material((0.14, 0.10, 0.06), rough=0.5, spec=0.5, tint_fn=hairy,
                     bump=lambda p: (value_noise(p * st, 110.0, 5) - 0.5) * 0.006)
    eye = Material((0.9, 0.05, 0.03), rough=0.03, spec=3.0, emissive=0.8)
    fang = Material((0.06, 0.04, 0.03), rough=0.25, spec=1.0)
    mark = Material((0.70, 0.55, 0.12), rough=0.6, spec=0.2)

    def scene(p):
        ab = ell(p, (0, 0.52, -0.52), (0.46, 0.42, 0.56), rx=0.35) - shaggy(p, 0.014, 30.0)
        ceph = ell(p, (0, 0.42, 0.18), (0.30, 0.26, 0.34))
        head = ell(p, (0, 0.40, 0.44), (0.20, 0.20, 0.17))
        body = smin(smin(ab, ceph, 0.12), head, 0.08) - shaggy(p, 0.008, 40.0, seed=7)
        mk = np.maximum(ell(p, (0, 0.80, -0.52), (0.18, 0.04, 0.32), rx=0.2), -ell(p, (0, 0.84, -0.52), (0.08, 0.05, 0.14), rx=0.2))
        legs = None
        for s in (-1, 1):
            for i, (z, spread, up) in enumerate(((0.30, 0.40, 0.55), (0.16, 0.62, 0.70), (0.02, 0.66, 0.70), (-0.14, 0.60, 0.60))):
                k = 1.5 - i
                d = limb(p, [(s * 0.26, 0.46, z), (s * (0.26 + spread * 0.55), 0.46 + up, z + 0.06 * k),
                             (s * (0.26 + spread * 1.05), 0.55, z + 0.08 * k), (s * (0.26 + spread * 1.18), 0.02, z + 0.12 * k)],
                         [0.065, 0.05, 0.036, 0.016])
                legs = d if legs is None else np.minimum(legs, d)
        legs = legs - shaggy(p, 0.006, 50.0, seed=9)
        eyes = None
        for s in (-1, 1):
            for x, y, z, r in ((0.07, 0.46, 0.57, 0.034), (0.13, 0.43, 0.53, 0.025), (0.06, 0.51, 0.55, 0.020), (0.15, 0.49, 0.50, 0.018)):
                d = sd_sphere(p, v3(s * x, y, z), r)
                eyes = d if eyes is None else np.minimum(eyes, d)
        fangs = pair(lambda s: limb(p, [(s * 0.075, 0.34, 0.55), (s * 0.085, 0.22, 0.66), (s * 0.06, 0.10, 0.68)], [0.04, 0.028, 0.006]))
        palps = pair(lambda s: limb(p, [(s * 0.13, 0.36, 0.52), (s * 0.17, 0.30, 0.68)], [0.025, 0.018]))
        return union([(body, M_BODY), (np.minimum(legs, palps), M_LEG), (eyes, M_EYE), (fangs, M_FANG), (mk, M_MARK)])
    return scene, [body_m, leg_m, eye, fang, mark], 1.5, rot_x(0.30) @ rot_y(0.20)


def mushroom(rarity="common"):
    M_CAP, M_SPOT, M_STEM, M_EYE, M_GILL, M_MOUTH = range(6)
    cap = Material((0.72, 0.10, 0.08), rough=0.35, spec=0.7, sss=0.4,
                   tint_fn=lambda p, a: a * (0.85 + 0.3 * fbm(p, 6.0, 3, 2))[:, None],
                   bump=lambda p: (fbm(p, 40.0, 3, 6) - 0.5) * 0.005)
    spot = Material((0.95, 0.92, 0.82), rough=0.5, spec=0.3)
    stem = Material((0.86, 0.78, 0.60), rough=0.6, spec=0.2, sss=0.6,
                    tint_fn=lambda p, a: a * (0.8 + 0.4 * fbm(p, 12.0, 3, 4))[:, None],
                    bump=lambda p: (fbm(p, 60.0, 3, 8) - 0.5) * 0.004)
    eye = Material((0.98, 0.85, 0.15), rough=0.05, spec=2.0, emissive=0.7)
    gill = Material((0.55, 0.40, 0.30), rough=0.7, spec=0.1)
    mouth = Material((0.20, 0.03, 0.03), rough=0.4, spec=0.5)

    def scene(p):
        capd = np.maximum(ell(p, (0, 1.06, 0.0), (0.66, 0.38, 0.64)), 0.80 - p[:, 1])
        capd = capd - (fbm(p, 5.0, 3, 3) - 0.5) * 0.03
        gills = np.maximum(np.maximum(ell(p, (0, 0.88, 0.0), (0.60, 0.10, 0.58)), p[:, 1] - 0.86), 0.74 - p[:, 1])
        body = ell(p, (0, 0.50, 0.0), (0.30, 0.40, 0.28))
        body = smin(body, ell(p, (0, 0.82, 0.0), (0.26, 0.14, 0.24)), 0.10) - (fbm(p, 7.0, 3, 5) - 0.5) * 0.02
        arms = pair(lambda s: limb(p, [(s * 0.26, 0.60, 0.04), (s * 0.42, 0.40, 0.16), (s * 0.44, 0.22, 0.30)], [0.075, 0.06, 0.05]))
        feet = pair(lambda s: ell(p, (s * 0.17, 0.06, 0.10), (0.13, 0.065, 0.17)))
        body = smin(smin(body, arms, 0.06), feet, 0.08)

        def spots(q):
            out = None
            for x, z, r in ((0.30, 0.45, 0.10), (-0.34, 0.30, 0.13), (0.0, 0.18, 0.08), (0.12, -0.42, 0.11),
                            (-0.20, 0.55, 0.08), (0.42, -0.10, 0.09), (-0.40, -0.25, 0.10)):
                y = 1.06 + 0.38 * np.sqrt(max(0.0, 1 - (x / 0.66) ** 2 - (z / 0.64) ** 2))
                d = sd_sphere(q, v3(x, y - r * 0.2, z), r)
                out = d if out is None else np.minimum(out, d)
            return out
        sp = part(p, (0, 1.2, 0), 0.9, spots)
        eyes = pair(lambda s: ell(p, (s * 0.13, 0.62, 0.26), (0.055, 0.07, 0.03), rz=s * 0.30))
        brows = pair(lambda s: ell(p, (s * 0.14, 0.72, 0.26), (0.08, 0.02, 0.03), rz=s * 0.45))
        mouthd = ell(p, (0, 0.44, 0.27), (0.12, 0.05, 0.03))
        return union([(capd, M_CAP), (sp, M_SPOT), (gills, M_GILL), (np.minimum(body, brows), M_STEM),
                      (eyes, M_EYE), (mouthd, M_MOUTH)])
    return scene, [cap, spot, stem, eye, gill, mouth], 1.35, rot_x(0.22) @ rot_y(0.10)


def treant(rarity="common"):
    M_BARK, M_LEAF, M_EYE, M_MOSS = range(4)
    st = np.array([1.0, 0.12, 1.0], dtype=np.float32)

    def bark_bump(p):
        return (value_noise(p * st, 22.0, 3) - 0.5) * 0.05 + (value_noise(p * st, 70.0, 6) - 0.5) * 0.012

    def bark_tint(p, a):
        return a * (0.65 + 0.7 * value_noise(p * np.array([1, 0.15, 1], dtype=np.float32), 20.0, 2))[:, None]
    bark = Material((0.28, 0.19, 0.12), rough=0.85, spec=0.15, bump=bark_bump, tint_fn=bark_tint)
    leaf = Material((0.20, 0.50, 0.12), rough=0.55, spec=0.35, sss=0.8,
                    tint_fn=lambda p, a: a * (0.6 + 0.8 * fbm(p, 14.0, 3, 5))[:, None],
                    bump=lambda p: (fbm(p, 60.0, 2, 3) - 0.5) * 0.01)
    eye = Material((1.0, 0.7, 0.1), rough=0.05, spec=2.0, emissive=0.9)
    moss = Material((0.22, 0.42, 0.14), rough=0.9, spec=0.1,
                    tint_fn=lambda p, a: a * (0.7 + 0.6 * fbm(p, 20.0, 3, 8))[:, None])

    def scene(p):
        trunk = rc(p, (0, 0.55, 0.0), (0, 1.45, 0.0), 0.42, 0.36)
        trunk = smin(trunk, ell(p, (0, 1.55, 0.0), (0.50, 0.34, 0.44)), 0.14)
        legs = np.minimum.reduce([
            limb(p, [(sx * 0.18, 0.72, zf * 0.2), (sx * 0.42, 0.34, zf * 0.4), (sx * 0.56, 0.04, zf * 0.5)], [0.20, 0.14, 0.08])
            for sx, zf in ((1, 1.0), (-1, 1.0), (0.2, -1.0), (-0.2, -1.0))])
        trunk = smin(trunk, legs, 0.12)
        arms = np.minimum(limb(p, [(0.38, 1.40, 0.04), (0.78, 1.18, 0.20), (0.92, 0.82, 0.48), (0.86, 0.60, 0.72)], [0.15, 0.12, 0.09, 0.06]),
                          limb(p, [(-0.38, 1.40, 0.04), (-0.80, 1.24, 0.14), (-1.00, 1.02, 0.28), (-1.10, 0.70, 0.30)], [0.15, 0.12, 0.09, 0.06]))
        fingers = None
        for bx, by, bz in ((0.86, 0.60, 0.72), (-1.10, 0.70, 0.30)):
            for dx, dz in ((-0.10, 0.10), (0.0, 0.15), (0.10, 0.10)):
                d = rc(p, (bx, by, bz), (bx + dx, by - 0.20, bz + dz), 0.045, 0.012)
                fingers = d if fingers is None else np.minimum(fingers, d)
        horns = pair(lambda s: limb(p, [(s * 0.22, 1.86, 0.0), (s * 0.40, 2.10, -0.05), (s * 0.32, 2.36, -0.05)], [0.07, 0.05, 0.012]))
        body = smin(smin(smin(trunk, arms, 0.10), fingers, 0.04), horns, 0.06)
        body = smin(body, pair(lambda s: ell(p, (s * 0.17, 1.62, 0.38), (0.15, 0.05, 0.10), rz=s * 0.30)), 0.05)   # brow
        body = smin(body, ell(p, (0, 1.40, 0.42), (0.07, 0.15, 0.08)), 0.06)                                        # nose
        body = smax(body, -ell(p, (0, 1.12, 0.40), (0.17, 0.10, 0.10)), 0.04)                                       # mouth
        body = smax(body, -pair(lambda s: ell(p, (s * 0.17, 1.52, 0.37), (0.075, 0.05, 0.06))), 0.03)               # eye sockets
        eyes = pair(lambda s: sd_sphere(p, v3(s * 0.17, 1.52, 0.37), 0.04))
        body = body - (fbm(p, 4.0, 3, 4) - 0.5) * 0.04
        crown = None
        for x, y, z, r in ((0, 2.15, 0, 0.42), (0.40, 1.95, 0.05, 0.30), (-0.42, 1.98, 0, 0.30), (0.2, 2.30, -0.2, 0.28),
                           (-0.2, 2.28, -0.15, 0.26), (0.9, 0.82, 0.48, 0.14), (-1.0, 0.95, 0.30, 0.14),
                           (0.62, 1.60, 0.0, 0.18), (-0.62, 1.64, 0.0, 0.18)):
            d = ell(p, (x, y, z), (r * 1.15, r * 0.8, r)) - (fbm(p, 9.0, 3, 5) - 0.5) * 0.06
            crown = d if crown is None else smin(crown, d, 0.05)
        mossd = np.maximum(ell(p, (0, 0.8, 0.05), (0.46, 0.14, 0.40)), p[:, 1] - 1.0)
        return union([(body, M_BARK), (crown, M_LEAF), (eyes, M_EYE), (mossd, M_MOSS)])
    return scene, [bark, leaf, eye, moss], 2.1, rot_x(0.14) @ rot_y(0.08)


BUILDERS = {"wolf": wolf, "forest_spider": forest_spider, "mushroom": mushroom, "treant": treant}
