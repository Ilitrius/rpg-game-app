"""Realistic monster models for the offline renderer (front view, +z towards the camera, feet on y = 0).

Style: anatomy from round cones (muscle masses, tapering limbs), shaggy silhouettes via displacement,
fine strand bump for fur, wet glossy gel/eyes, claws and teeth. Lighting is dramatic (see
render_monsters.py: low ambient, strong key, cool rim light).
"""
from __future__ import annotations

import numpy as np

from items import brass, fold_polar
from sdf import (Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_round_cone,
                 sd_sphere, sd_torus, smax, smin, union, v3, value_noise)

SHARP = np.array([1.0, 1.0, 1.0], dtype=np.float32)


# --------------------------------------------------------------------------- helpers

def rc(p, a, b, r1, r2):
    return sd_round_cone(p, np.array(a, dtype=np.float32), np.array(b, dtype=np.float32), r1, r2)


def limb(p, pts, radii):
    """Tapering limb through pts (list of (x, y, z)) with one radius per point."""
    out = None
    for i in range(len(pts) - 1):
        d = rc(p, pts[i], pts[i + 1], radii[i], radii[i + 1])
        out = d if out is None else np.minimum(out, d)
    return out


def part(p, center, radius, fn, margin=0.25):
    """Evaluates the (expensive) distance function `fn` only near the part's bounding sphere."""
    c = np.array(center, dtype=np.float32)
    d = np.linalg.norm(p - c, axis=-1) - radius
    out = d.copy()
    idx = np.nonzero(d < margin)[0]
    if idx.size:
        out[idx] = fn(p[idx])
    return out


def rot_local(p, center, rz=0.0, ry=0.0, rx=0.0):
    """Points in the local frame of a part rotated by (rx, ry, rz) around `center`."""
    q = (p - np.array(center, dtype=np.float32))
    m = np.eye(3, dtype=np.float32)
    if rz:
        c, s = np.cos(rz), np.sin(rz)
        m = m @ np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float32)
    if ry:
        c, s = np.cos(ry), np.sin(ry)
        m = m @ np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float32)
    if rx:
        c, s = np.cos(rx), np.sin(rx)
        m = m @ np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float32)
    return q @ m


def ell(p, center, radii, rz=0.0, ry=0.0, rx=0.0):
    return sd_ellipsoid(rot_local(p, center, rz, ry, rx), np.zeros(3, dtype=np.float32), np.array(radii, dtype=np.float32))


def shaggy(p, amp, freq, stretch=(1.0, 2.5, 1.0), seed=3):
    """Silhouette roughness (fur tufts)."""
    st = np.array(stretch, dtype=np.float32)
    return (value_noise(p * st, freq, seed) - 0.5) * amp + (value_noise(p, freq * 2.3, seed + 1) - 0.5) * amp * 0.5


def fur_material(color, belly=1.35, dirt=0.45, strand=0.0065, clump=0.010, rough=0.80, spec=0.20, y_ref=0.7):
    st = np.array([1.0, 5.0, 1.0], dtype=np.float32)

    def bump(p):
        return (value_noise(p * st, 55.0, 5) - 0.5) * strand * 2.2 + (value_noise(p * st, 150.0, 8) - 0.5) * strand \
            + (value_noise(p, 16.0, 2) - 0.5) * clump

    def tint(p, a):
        patch = fbm(p, 5.0, 3, 4)
        mud = np.clip((patch - 0.45) * 2.4, 0.0, 1.0)[:, None]
        under = (belly - (belly - 0.62) * np.clip(p[:, 1] / y_ref, 0.0, 1.0))[:, None]
        fleck = (0.82 + 0.36 * value_noise(p * st, 90.0, 6))[:, None]
        base = a * under * fleck
        return base * (1 - dirt * mud) + np.array([0.05, 0.035, 0.025], dtype=np.float32) * dirt * mud

    return Material(color, metal=0.0, rough=rough, spec=spec, bump=bump, tint_fn=tint, sss=0.18)


def bone_material(color=(0.86, 0.80, 0.62)):
    def tint(p, a):
        k = 0.75 + 0.35 * fbm(p, 30.0, 3, 5)
        return a * k[:, None]
    return Material(color, rough=0.38, spec=0.55, tint_fn=tint, sss=0.25,
                    bump=lambda p: (fbm(p, 60.0, 3, 9) - 0.5) * 0.003)


def wet_black():
    return Material((0.015, 0.012, 0.012), rough=0.025, spec=3.2)


def flesh_material(color=(0.78, 0.42, 0.42)):
    return Material(color, rough=0.55, spec=0.35, sss=1.0,
                    bump=lambda p: (fbm(p, 80.0, 3, 12) - 0.5) * 0.002)


# --------------------------------------------------------------------------- gel (slimes)

def _mouth_dark(p, a, s=1.0):
    q = p / s
    dn = np.sqrt((q[:, 0] / 0.32) ** 2 + ((q[:, 1] - 0.25) / 0.125) ** 2 + ((q[:, 2] - 0.62) / 0.24) ** 2)
    k = np.clip((dn - 1.02) / 0.28, 0.0, 1.0)
    k = k * k * (3 - 2 * k)
    return a * (0.08 + 0.92 * k)[:, None].astype(np.float32)


def gel_material(color, edge, scale=1.0):
    sc = scale
    def tint(p, a):
        q = p / scale
        cloud = 0.45 + 0.75 * fbm(q, 3.2, 4, 2)
        cells = value_noise(q, 9.0, 5)
        speck = np.clip((cells - 0.80) * 6.0, 0.0, 1.0)
        dark = np.clip((0.22 - cells) * 4.0, 0.0, 1.0)
        shade = (0.55 + 0.55 * np.clip(q[:, 1] / 0.9, 0.0, 1.0))
        out = a * (cloud * shade)[:, None]
        out = out * (1 - 0.5 * dark[:, None]) + speck[:, None] * np.array(edge, dtype=np.float32) * 0.5
        return _mouth_dark(p, out, sc)

    return Material(color, metal=0.0, rough=0.035, spec=1.9, emissive=0.04, sss=1.3, tint_fn=tint,
                    edge=(edge, 2.2, 0.80),
                    bump=lambda p: (fbm(p / scale, 5.0, 3, 8) - 0.5) * 0.012 * scale)


def _gel_mass(p, s=1.0):
    q = p / s
    x, y, z = q[:, 0], q[:, 1], q[:, 2]
    wob = (fbm(q, 2.0, 3, 1) - 0.5) * 0.12 + (fbm(q, 6.0, 2, 4) - 0.5) * 0.03
    body = ell(q, (0, 0.46, 0), (0.70, 0.46, 0.62))
    body = smin(body, ell(q, (0.46, 0.28, 0.40), (0.34, 0.26, 0.30)), 0.14)
    body = smin(body, ell(q, (-0.52, 0.26, 0.32), (0.30, 0.24, 0.28)), 0.14)
    body = smin(body, ell(q, (0.0, 0.42, -0.36), (0.56, 0.40, 0.42)), 0.16)
    body = body + wob
    body = smax(body, -(y - 0.0), 0.10)
    skirt = ell(q, (0, 0.06, 0.02), (0.90, 0.075, 0.82)) + 0.05 * (fbm(q, 3.0, 3, 6) - 0.5)
    body = smin(body, skirt, 0.14)
    # pseudopods reaching towards the viewer + stringy goo between lobes
    body = smin(body, rc(q, (0.36, 0.14, 0.50), (0.64, 0.05, 0.86), 0.12, 0.05), 0.10)
    body = smin(body, rc(q, (-0.34, 0.12, 0.52), (-0.58, 0.05, 0.80), 0.10, 0.045), 0.10)
    strands = np.minimum(rc(q, (0.14, 0.70, 0.50), (0.40, 0.30, 0.66), 0.022, 0.016),
                         rc(q, (-0.10, 0.62, 0.52), (-0.30, 0.26, 0.66), 0.018, 0.014))
    body = smin(body, strands, 0.05)
    # open maw: carved cavity
    body = smax(body, -ell(q, (0.0, 0.25, 0.62), (0.32, 0.125, 0.24)), 0.05)
    return body * s


def _eye_parts(p, c, r):
    """Reptilian eye in a gel lid: returns (sclera, iris, pupil, lid) distances."""
    c = np.array(c, dtype=np.float32)
    sclera = sd_sphere(p, c, r)
    iris = ell(p, c + np.array([0, 0, r * 0.62], dtype=np.float32), (r * 0.62, r * 0.62, r * 0.50))
    pupil = ell(p, c + np.array([0, 0, r * 0.92], dtype=np.float32), (r * 0.12, r * 0.50, r * 0.12))
    lid = sd_torus(p, c + np.array([0, 0, r * 0.05], dtype=np.float32), r * 1.04, r * 0.30, axis="z")
    return sclera, iris, pupil, lid


def _teeth(p, s, count, y_top, spread, z0, size, bite=0.0):
    """Upper teeth hang from y_top, lower teeth stand on y_top - 0.22 s; lengths in model units."""
    xs = np.linspace(-spread, spread, count)
    jit = np.array([1.0, 0.7, 1.2, 0.85, 1.0, 0.75, 1.15, 0.9, 1.0, 0.8, 1.1, 0.9])
    out = None
    for i, x in enumerate(xs):
        k = jit[i % len(jit)]
        h = 0.034 * s * k * size
        z = z0 - 0.14 * s * (x / spread) ** 2
        up = sd_cone_y(p, np.array([x * s, y_top - h, z], dtype=np.float32), 0.0015 * s, 0.020 * s, h)
        base = y_top - 0.22 * s
        hl = h * 0.8
        dn = sd_cone_y(p, np.array([x * s * 0.95, base + hl, z - 0.02 * s], dtype=np.float32), 0.018 * s, 0.0015 * s, hl)
        d = np.minimum(up, dn)
        out = d if out is None else np.minimum(out, d)
    return out


def slime(rarity="common"):
    M_GEL, M_SCLERA, M_IRIS, M_PUPIL, M_LID, M_TOOTH = 0, 1, 2, 3, 4, 5
    gel = gel_material((0.26, 0.58, 0.16), (0.78, 1.0, 0.42))
    sclera = Material((0.85, 0.78, 0.40), rough=0.05, spec=1.6, sss=0.6)
    iris = Material((0.95, 0.45, 0.05), rough=0.05, spec=1.6, emissive=0.35)
    teeth = bone_material((0.88, 0.82, 0.58))

    def scene(p):
        body = _gel_mass(p)
        e1 = _eye_parts(p, (0.21, 0.67, 0.50), 0.088)
        e2 = _eye_parts(p, (-0.23, 0.60, 0.50), 0.066)
        sc = np.minimum(e1[0], e2[0])
        ir = np.minimum(e1[1], e2[1])
        pu = np.minimum(e1[2], e2[2])
        lid = np.minimum(e1[3], e2[3])
        tooth = part(p, (0, 0.25, 0.64), 0.40, lambda q: _teeth(q, 1.0, 9, 0.365, 0.26, 0.64, 1.0))
        return union([(body, M_GEL), (sc, M_SCLERA), (ir, M_IRIS), (pu, M_PUPIL), (lid, M_GEL), (tooth, M_TOOTH)])

    return scene, [gel, sclera, iris, wet_black(), gel, teeth], 1.25, rot_x(0.26) @ rot_y(0.12)


def slime_king(rarity="common"):
    M_GEL, M_SCLERA, M_IRIS, M_PUPIL, M_GOLD, M_TOOTH, M_RUBY, M_SAPH = 0, 1, 2, 3, 4, 5, 6, 7
    s = 1.30
    gel = gel_material((0.18, 0.48, 0.52), (0.55, 0.95, 1.0), scale=s)
    sclera = Material((0.90, 0.85, 0.50), rough=0.05, spec=1.6, sss=0.6)
    iris = Material((0.95, 0.20, 0.05), rough=0.05, spec=1.6, emissive=0.45)
    teeth = bone_material((0.90, 0.85, 0.62))
    ruby = Material((0.90, 0.06, 0.12), metal=0.4, rough=0.03, spec=2.0, emissive=0.5)
    saph = Material((0.10, 0.25, 0.95), metal=0.4, rough=0.03, spec=2.0, emissive=0.45)

    def scene(p):
        body = _gel_mass(p, s)
        e1 = _eye_parts(p, (0.28 * s, 0.67 * s, 0.50 * s), 0.088 * s)
        e2 = _eye_parts(p, (-0.30 * s, 0.60 * s, 0.50 * s), 0.066 * s)
        sc = np.minimum(e1[0], e2[0])
        ir = np.minimum(e1[1], e2[1])
        pu = np.minimum(e1[2], e2[2])
        lid = np.minimum(e1[3], e2[3])
        tooth = part(p, (0, 0.25 * s, 0.64 * s), 0.40 * s, lambda q: _teeth(q, s, 9, 0.365 * s, 0.26, 0.64 * s, 1.0))
        # crown, slightly tilted and sunk into the gel
        q = rot_local(p, (0.0, 1.10, 0.10), rz=0.10, rx=-0.10)
        band = sd_cyl_y(q, v3(0, 0, 0), 0.46, 0.085, 0.015)
        band = np.maximum(band, -sd_cyl_y(q, v3(0, 0, 0), 0.40, 0.2, 0.0))
        pr = fold_polar(q, 6)
        prongs = sd_cone_y(pr, v3(0.46, 0.24, 0), 0.085, 0.0, 0.20, 0.012)
        orbs = sd_sphere(pr, v3(0.46, 0.46, 0), 0.045)
        gems_r = sd_sphere(fold_polar(q, 6), v3(0.465, 0.0, 0), 0.045)
        gems_b = sd_sphere(fold_polar(rot_local(p, (0.0, 1.10, 0.10), rz=0.10, rx=-0.10) @ np.array(
            [[np.cos(0.52), 0, np.sin(0.52)], [0, 1, 0], [-np.sin(0.52), 0, np.cos(0.52)]], dtype=np.float32), 6),
            v3(0.465, 0.0, 0), 0.04)
        rim = sd_torus(q, v3(0, 0.075, 0), 0.46, 0.022, axis="y")
        gold = np.minimum(np.minimum(band, prongs), np.minimum(orbs, rim))
        return union([(body, M_GEL), (sc, M_SCLERA), (ir, M_IRIS), (pu, M_PUPIL), (lid, M_GEL), (tooth, M_TOOTH),
                      (gold, M_GOLD), (gems_r, M_RUBY), (gems_b, M_SAPH)])

    return scene, [gel, sclera, iris, wet_black(), brass(), teeth, ruby, saph], 1.75, rot_x(0.24) @ rot_y(0.10)


# --------------------------------------------------------------------------- giant rat

def giant_rat(rarity="common"):
    M_FUR, M_FLESH, M_EYE, M_TOOTH, M_CLAW, M_TAIL, M_WHISK = 0, 1, 2, 3, 4, 5, 6
    fur = fur_material((0.34, 0.29, 0.25), belly=1.45, dirt=0.55, y_ref=0.55)
    flesh = flesh_material((0.74, 0.40, 0.40))
    tooth = bone_material((0.90, 0.78, 0.45))
    claw = Material((0.10, 0.08, 0.07), rough=0.35, spec=0.7)
    ring = lambda p: (np.abs(np.sin((p[:, 2] * 1.0 + 0.0) * 120.0)) - 0.5) * 0.0045 + (fbm(p, 60.0, 2, 3) - 0.5) * 0.003
    tail_m = Material((0.62, 0.45, 0.42), rough=0.6, spec=0.35, sss=0.5, bump=ring)
    whisk = Material((0.80, 0.78, 0.70), rough=0.4, spec=0.5)

    def scene(p):
        # torso: hunched, shoulders high
        body = rc(p, (0, 0.33, -0.34), (0, 0.42, 0.14), 0.27, 0.235)
        body = smin(body, ell(p, (0, 0.34, -0.40), (0.30, 0.27, 0.30)), 0.10)
        body = smin(body, rc(p, (0, 0.42, 0.14), (0, 0.42, 0.34), 0.235, 0.19), 0.08)
        # head: cranium + tapering muzzle
        head = ell(p, (0, 0.44, 0.40), (0.175, 0.17, 0.20))
        muzzle = rc(p, (0, 0.40, 0.42), (0, 0.31, 0.78), 0.14, 0.032)
        body = smin(smin(body, head, 0.09), muzzle, 0.08)
        body = smin(body, ell(p, (0.0, 0.52, 0.46), (0.12, 0.07, 0.12)), 0.07)       # brow bulge
        # limbs
        haunch = np.minimum(ell(p, (0.24, 0.30, -0.32), (0.15, 0.20, 0.22), rz=0.2), ell(p, (-0.24, 0.30, -0.32), (0.15, 0.20, 0.22), rz=-0.2))
        hind = np.minimum(limb(p, [(0.25, 0.22, -0.30), (0.27, 0.07, -0.18)], [0.09, 0.05]),
                          limb(p, [(-0.25, 0.22, -0.30), (-0.27, 0.07, -0.18)], [0.09, 0.05]))
        hfoot = np.minimum(ell(p, (0.27, 0.025, -0.07), (0.055, 0.03, 0.15)), ell(p, (-0.27, 0.025, -0.07), (0.055, 0.03, 0.15)))
        front = np.minimum(limb(p, [(0.17, 0.36, 0.18), (0.24, 0.19, 0.30), (0.20, 0.07, 0.50)], [0.085, 0.062, 0.045]),
                           limb(p, [(-0.17, 0.36, 0.18), (-0.24, 0.19, 0.30), (-0.20, 0.07, 0.50)], [0.085, 0.062, 0.045]))
        fpaw = np.minimum(ell(p, (0.20, 0.04, 0.54), (0.05, 0.03, 0.07)), ell(p, (-0.20, 0.04, 0.54), (0.05, 0.03, 0.07)))
        fur_d = np.minimum.reduce([body, haunch, hind, hfoot, front, fpaw])
        fur_d = fur_d - shaggy(p, 0.016, 26.0)
        # ears (thin, translucent) + inner
        ear_l = ell(p, (0.15, 0.62, 0.34), (0.105, 0.125, 0.014), rz=-0.45, ry=-0.3)
        ear_r = ell(p, (-0.15, 0.62, 0.34), (0.105, 0.125, 0.014), rz=0.45, ry=0.3)
        ears = np.minimum(ear_l, ear_r)
        # toes + claws
        def toes(q):
            out = None
            for sx in (-1, 1):
                for k, dx in enumerate((-0.036, -0.012, 0.012, 0.036)):
                    base = np.array([sx * 0.20 + dx, 0.035, 0.585], dtype=np.float32)
                    tip = np.array([sx * 0.20 + dx * 1.5, 0.018, 0.655], dtype=np.float32)
                    d = rc(q, base, tip, 0.016, 0.011)
                    out = d if out is None else np.minimum(out, d)
            return out

        def claws(q):
            out = None
            for sx in (-1, 1):
                for dx in (-0.036, -0.012, 0.012, 0.036):
                    tip = np.array([sx * 0.20 + dx * 1.5, 0.018, 0.655], dtype=np.float32)
                    d = rc(q, tip, tip + np.array([dx * 0.4, -0.012, 0.045], dtype=np.float32), 0.011, 0.002)
                    out = d if out is None else np.minimum(out, d)
            return out

        toe_d = part(p, (0, 0.03, 0.62), 0.30, toes)
        claw_d = part(p, (0, 0.02, 0.66), 0.30, claws)
        # open mouth: lower jaw, tongue, incisors
        jaw = rc(p, (0, 0.29, 0.50), (0, 0.205, 0.74), 0.065, 0.028)
        tongue = ell(p, (0, 0.265, 0.64), (0.04, 0.014, 0.09))
        incis = np.minimum(rc(p, (0.022, 0.30, 0.755), (0.025, 0.185, 0.765), 0.020, 0.011), rc(p, (-0.022, 0.30, 0.755), (-0.025, 0.185, 0.765), 0.020, 0.011))
        lower_inc = np.minimum(rc(p, (0.018, 0.225, 0.745), (0.020, 0.275, 0.752), 0.012, 0.008), rc(p, (-0.018, 0.225, 0.745), (-0.020, 0.275, 0.752), 0.012, 0.008))
        nose = sd_sphere(p, v3(0, 0.33, 0.80), 0.034)
        eyes = np.minimum(ell(p, (0.105, 0.485, 0.50), (0.034, 0.034, 0.030)), ell(p, (-0.105, 0.485, 0.50), (0.034, 0.034, 0.030)))
        lids = np.minimum(sd_torus(p, v3(0.105, 0.485, 0.495), 0.040, 0.012, axis="z"), sd_torus(p, v3(-0.105, 0.485, 0.495), 0.040, 0.012, axis="z"))
        # tail with scale rings
        tail = limb(p, [(0, 0.28, -0.60), (0.07, 0.24, -0.82), (0.26, 0.15, -0.98), (0.48, 0.07, -0.96), (0.62, 0.035, -0.84)],
                    [0.050, 0.040, 0.030, 0.022, 0.012])
        wh = part(p, (0, 0.34, 0.85), 0.5, lambda q: np.minimum.reduce([
            limb(q, [(sx * 0.05, 0.345 + dy, 0.74), (sx * 0.18, 0.35 + dy * 2.0, 0.82 + dz), (sx * 0.32, 0.33 + dy * 3.0, 0.84 + dz)],
                 [0.0040, 0.0030, 0.0016])
            for sx in (-1, 1) for dy, dz in ((-0.025, 0.0), (0.0, 0.03), (0.025, -0.02))]))
        return union([(fur_d, M_FUR), (ears, M_FLESH), (np.minimum(np.minimum(nose, tongue), jaw), M_FLESH),
                      (np.minimum(incis, lower_inc), M_TOOTH), (eyes, M_EYE), (lids, M_FUR), (toe_d, M_FLESH),
                      (claw_d, M_CLAW), (tail, M_TAIL), (wh, M_WHISK)])

    return scene, [fur, flesh, wet_black(), tooth, claw, tail_m, whisk], 1.1, rot_x(0.30) @ rot_y(0.42)


# --------------------------------------------------------------------------- boar

def boar(rarity="common"):
    M_FUR, M_MANE, M_SNOUT, M_TUSK, M_EYE, M_HOOF = 0, 1, 2, 3, 4, 5
    fur = fur_material((0.20, 0.145, 0.11), belly=1.3, dirt=0.80, strand=0.0075, clump=0.010, rough=0.88, spec=0.14, y_ref=1.1)
    mane = fur_material((0.065, 0.05, 0.04), belly=1.0, dirt=0.3, strand=0.012, clump=0.02, rough=0.95, spec=0.1, y_ref=1.1)
    snout_m = Material((0.32, 0.19, 0.18), rough=0.35, spec=0.6, sss=0.5,
                       bump=lambda p: (fbm(p, 90.0, 3, 15) - 0.5) * 0.004)
    tusk = bone_material((0.92, 0.86, 0.68))
    eye = Material((0.10, 0.04, 0.03), rough=0.03, spec=3.0, emissive=0.20)
    hoof = Material((0.07, 0.06, 0.05), rough=0.4, spec=0.55)
    DY = 0.18

    def scene(p):
        pb = p - v3(0, DY, 0)                   # body group sits on longer legs
        # barrel with raised shoulder hump
        body = rc(pb, (0, 0.60, -0.58), (0, 0.70, 0.18), 0.33, 0.39)
        body = smin(body, ell(pb, (0, 0.48, -0.18), (0.34, 0.31, 0.52)), 0.14)
        body = smin(body, rc(pb, (0, 0.70, 0.18), (0, 0.62, 0.50), 0.38, 0.32), 0.12)
        # large wedge head, long snout
        head = rc(pb, (0, 0.60, 0.46), (0, 0.42, 1.14), 0.31, 0.15)
        body = smin(body, head, 0.12)
        body = smin(body, ell(pb, (0.0, 0.72, 0.60), (0.24, 0.12, 0.24)), 0.10)
        body = smin(body, ell(pb, (0.0, 0.50, 0.90), (0.20, 0.17, 0.28)), 0.10)          # jowls
        disc = ell(pb, (0, 0.415, 1.235), (0.145, 0.125, 0.055))
        nostril = np.minimum(ell(pb, (0.056, 0.415, 1.285), (0.028, 0.038, 0.03)), ell(pb, (-0.056, 0.415, 1.285), (0.028, 0.038, 0.03)))
        disc = smax(disc, -nostril, 0.012)

        def leg(sx, zf, thick):
            return limb(p, [(sx * 0.27, 0.66, zf), (sx * 0.27, 0.36, zf + 0.07), (sx * 0.27, 0.09, zf + 0.05)],
                        [thick, thick * 0.66, thick * 0.42])
        legs = np.minimum.reduce([leg(1, 0.22, 0.14), leg(-1, 0.22, 0.14), leg(1, -0.42, 0.16), leg(-1, -0.42, 0.16)])
        tail = limb(pb, [(0, 0.80, -0.78), (0.10, 0.66, -0.92), (0.07, 0.46, -0.92)], [0.03, 0.022, 0.018])
        fur_d = np.minimum(np.minimum(body, legs), tail)
        fur_d = fur_d - shaggy(p, 0.011, 24.0, stretch=(1.0, 2.0, 1.0))
        mane_d = limb(pb, [(0, 1.04, 0.36), (0, 1.09, 0.02), (0, 1.03, -0.32), (0, 0.90, -0.64)], [0.06, 0.075, 0.065, 0.045])
        mane_d = mane_d - shaggy(p, 0.05, 24.0, stretch=(1.0, 0.5, 1.0), seed=11)
        ears = np.minimum(ell(pb, (0.21, 0.88, 0.44), (0.075, 0.15, 0.03), rz=-0.5, ry=-0.4), ell(pb, (-0.21, 0.88, 0.44), (0.075, 0.15, 0.03), rz=0.5, ry=0.4))
        tusks = np.minimum.reduce([limb(pb, [(sx * 0.13, 0.33, 1.00), (sx * 0.20, 0.27, 1.14), (sx * 0.27, 0.48, 1.24), (sx * 0.25, 0.72, 1.22)],
                                        [0.056, 0.048, 0.034, 0.012]) for sx in (-1, 1)])
        eyes = np.minimum(ell(pb, (0.175, 0.665, 0.80), (0.026, 0.024, 0.022)), ell(pb, (-0.175, 0.665, 0.80), (0.026, 0.024, 0.022)))
        brows = np.minimum(ell(pb, (0.175, 0.70, 0.79), (0.055, 0.020, 0.040), rz=0.35), ell(pb, (-0.175, 0.70, 0.79), (0.055, 0.020, 0.040), rz=-0.35))
        hoof_d = part(p, (0, 0.06, 0.0), 0.95, lambda q: np.minimum.reduce([
            ell(q, (sx * 0.27 + dx, 0.045, zf + 0.12), (0.034, 0.05, 0.064))
            for sx in (-1, 1) for zf in (0.22, -0.42) for dx in (-0.038, 0.038)]))
        return union([(np.minimum(fur_d, brows), M_FUR), (mane_d, M_MANE), (disc, M_SNOUT), (ears, M_FUR), (tusks, M_TUSK),
                      (eyes, M_EYE), (hoof_d, M_HOOF)])

    return scene, [fur, mane, snout_m, tusk, eye, hoof], 1.5, rot_x(0.20) @ rot_y(0.55)


BUILDERS = {
    "slime": slime,
    "slime_king": slime_king,
    "giant_rat": giant_rat,
    "boar": boar,
}
