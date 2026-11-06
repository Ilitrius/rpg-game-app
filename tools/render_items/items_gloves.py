"""Gloves, second pass: an anatomical glove hand (palm, jointed and slightly curled fingers, thumb, flared cuff)
in worn, stitched leather; the gauntlets add articulated steel finger lames, a knuckle guard, a domed back plate
and a bell cuff with lames and rivets. Overrides leather_gloves / gauntlets of items_armor.py (registered last)."""
from __future__ import annotations

import numpy as np

from items import brass, steel
from items_armor2 import dashes, worn_leather
from monsters import ell, limb
from sdf import fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_sphere, smax, smin, union, v3

# right hand, back of the hand towards the viewer (+z), fingers up, thumb on the left (-x)
FINGERS = [  # x at the knuckle, length, base radius
    (-0.165, 0.40, 0.056),
    (-0.055, 0.45, 0.058),
    (0.055, 0.42, 0.055),
    (0.160, 0.33, 0.049),
]
KNUCKLE_Y = 0.20


def _finger_pts(x, length):
    s = x * 0.20  # slight splay
    y0 = KNUCKLE_Y
    return [(x, y0 - 0.03, 0.005),
            (x + s * 0.30, y0 + length * 0.42, 0.010),
            (x + s * 0.60, y0 + length * 0.74, -0.012),
            (x + s * 0.78, y0 + length, -0.055)]


def _finger_radii(r):
    return [r, r * 0.93, r * 0.86, r * 0.78]


THUMB = [(-0.17, -0.07, -0.01), (-0.31, 0.05, 0.02), (-0.40, 0.17, 0.025), (-0.44, 0.28, 0.0)]
THUMB_R = [0.080, 0.064, 0.056, 0.048]


def _hand(p):
    """Palm + fingers + thumb + wrist (one smooth glove surface)."""
    palm = ell(p, (0.0, 0.02, 0.0), (0.245, 0.235, 0.088), rz=0.0)
    palm = smin(palm, ell(p, (0.0, 0.18, 0.0), (0.235, 0.07, 0.078)), 0.05)       # knuckle line
    palm = smin(palm, ell(p, (-0.14, -0.08, -0.015), (0.12, 0.13, 0.085)), 0.06)  # thumb ball
    d = palm
    for x, L, r in FINGERS:
        d = smin(d, limb(p, _finger_pts(x, L), _finger_radii(r)), 0.035)
    d = smin(d, limb(p, THUMB, THUMB_R), 0.05)
    wrist = ell(p, (0.0, -0.25, 0.0), (0.20, 0.12, 0.085))
    return smin(d, wrist, 0.08)


def _cuff(p, y_top, y_bot, r_top, r_bot, flat=1.65, wall=0.014):
    """Flared, hollow cuff (elliptical cross section, open at the bottom)."""
    q = p.copy()
    q[:, 2] = q[:, 2] * flat
    c = v3(0, (y_top + y_bot) / 2, 0)
    h = (y_top - y_bot) / 2
    outer = sd_cone_y(q, c, r_bot, r_top, h, 0.02) / flat
    inner = sd_cone_y(q, c + v3(0, -0.02, 0), r_bot - wall * 2.2, r_top - wall * 2.2, h + 0.03, 0.0) / flat
    return smax(outer, -inner, 0.006)


def leather_gloves(rarity="common"):
    M_GLOVE, M_CUFF, M_STRAP, M_BRASS = range(4)

    def stitches(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        back = (z > 0.03).astype(np.float32)
        # three decorative "points" on the back of the hand
        pts = np.zeros_like(x)
        for xx in (-0.11, 0.0, 0.11):
            m = back * ((y > -0.12) & (y < 0.17)).astype(np.float32)
            pts = np.maximum(pts, dashes(m, y, 0.0065, 110.0, x - xx))
        # seam around the knuckle padding
        pad = dashes(back * (np.abs(x) < 0.23).astype(np.float32), x, 0.0065, 110.0, np.abs(y - 0.205) - 0.045)
        return np.maximum(pts, pad)

    def cuff_stitches(p):
        y = p[:, 1]
        ang = np.arctan2(p[:, 2] * 1.65, p[:, 0])
        return np.maximum(dashes(1.0, ang, 0.008, 70.0, y + 0.575), dashes(1.0, ang, 0.008, 70.0, y + 0.345))

    def scene(p):
        hand = _hand(p)
        # raised padding over the knuckles
        pad = ell(p, (0.0, 0.205, 0.035), (0.215, 0.050, 0.065))
        hand = smin(hand, pad, 0.02)
        cuff = _cuff(p, -0.30, -0.62, 0.225, 0.315)
        # wrist strap with a small buckle on the back of the hand
        q = p.copy(); q[:, 2] *= 1.55
        strap = smax(sd_cone_y(q, v3(0, -0.27, 0), 0.212, 0.205, 0.028, 0.01) / 1.55,
                     -sd_cone_y(q, v3(0, -0.27, 0), 0.17, 0.165, 0.06, 0.0) / 1.55, 0.004)
        buckle = sd_box(p, v3(0.0, -0.27, 0.142), v3(0.048, 0.042, 0.012), 0.008)
        buckle = np.maximum(buckle, -sd_box(p, v3(0.0, -0.27, 0.15), v3(0.026, 0.022, 0.03), 0.0))
        prong = sd_capsule(p, v3(0.0, -0.27, 0.155), v3(-0.035, -0.27, 0.158), 0.006)
        return union([(hand, M_GLOVE), (cuff, M_CUFF), (strap, M_STRAP), (buckle, M_BRASS), (prong, M_BRASS)])

    mats = [worn_leather((0.40, 0.23, 0.11), stitches=stitches),
            worn_leather((0.30, 0.17, 0.08), stitches=cuff_stitches),
            worn_leather((0.17, 0.10, 0.05), scuff=0.4),
            brass()]
    view = rot_x(0.22) @ rot_y(-0.32)
    return scene, mats, 1.1, view


def gauntlets(rarity="common"):
    M_GLOVE, M_STEEL, M_BRASS, M_LEATHER = range(4)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        glove = _hand(p)
        parts = [(glove, M_GLOVE)]
        # articulated lames on the back of every finger: three overlapping shells, each one a bit wider at its base
        lames = None
        for fx, L, r in FINGERS:
            pts = _finger_pts(fx, L)
            rad = _finger_radii(r)
            for i in range(3):
                a, b = np.array(pts[i]), np.array(pts[i + 1])
                a2 = a + (b - a) * 0.06
                b2 = a + (b - a) * 0.92
                shell = limb(p, [a2, b2], [rad[i] + 0.016, rad[i + 1] + 0.010])
                shell = smax(shell, -(z - (a[2] + b[2]) / 2 + 0.004), 0.006)  # only the back half (towards +z)
                lames = shell if lames is None else np.minimum(lames, shell)
        # thumb plates
        for i in range(3):
            a, b = np.array(THUMB[i]), np.array(THUMB[i + 1])
            shell = limb(p, [a + (b - a) * 0.08, a + (b - a) * 0.9], [THUMB_R[i] + 0.016, THUMB_R[i + 1] + 0.010])
            shell = smax(shell, -(z - (a[2] + b[2]) / 2 - 0.01), 0.006)
            lames = np.minimum(lames, shell)
        # knuckle guard: a ridged bar across the knuckles
        knuckle = ell(p, (0.0, 0.205, 0.045), (0.245, 0.055, 0.070))
        knuckle = knuckle - 0.010 * np.clip(np.cos((x + 0.11) * np.pi / 0.11), 0, 1) * (z > 0.05)
        # domed back plate with a central ridge, cut to the back half
        back = ell(p, (0.0, 0.0, 0.035), (0.250, 0.185, 0.090)) - 0.012 * np.exp(-(x / 0.035) ** 2)
        back = smax(back, -(z - 0.03), 0.01)
        # bell cuff with two lames + rolled rim
        cuff = _cuff(p, -0.26, -0.62, 0.230, 0.345, wall=0.016)
        lame1 = _cuff(p, -0.30, -0.40, 0.250, 0.282, wall=0.016)
        lame2 = _cuff(p, -0.40, -0.50, 0.282, 0.315, wall=0.016)
        rim = _cuff(p, -0.585, -0.630, 0.350, 0.360, wall=0.028)
        steel_parts = np.minimum.reduce([lames, knuckle, back, cuff, lame1, lame2])
        parts.append((steel_parts, M_STEEL))
        parts.append((rim, M_BRASS))
        rivets = np.minimum.reduce(
            [sd_sphere(p, v3(xx, -0.02 + yy, 0.118), 0.017) for xx in (-0.19, 0.19) for yy in (-0.10, 0.10)] +
            [sd_sphere(p, v3(0.25 * np.sin(a), -0.35, 0.25 * np.cos(a) / 1.65 + 0.005), 0.016) for a in (-0.9, 0.0, 0.9)] +
            [sd_sphere(p, v3(0.30 * np.sin(a), -0.45, 0.30 * np.cos(a) / 1.65 + 0.005), 0.016) for a in (-0.9, 0.0, 0.9)])
        parts.append((rivets, M_BRASS))
        # leather strap peeking out between hand and cuff
        q = p.copy(); q[:, 2] *= 1.55
        strap = smax(sd_cone_y(q, v3(0, -0.235, 0), 0.215, 0.208, 0.024, 0.01) / 1.55,
                     -sd_cone_y(q, v3(0, -0.235, 0), 0.17, 0.165, 0.06, 0.0) / 1.55, 0.004)
        parts.append((strap, M_LEATHER))
        return union(parts)

    plate = steel(0.28)
    base_tint = plate.tint_fn

    def worn(p, a):
        if base_tint is not None:
            a = base_tint(p, a)
        k = 0.80 + 0.26 * fbm(p, 6.0, 3, 67)
        return a * k[:, None]
    plate.tint_fn = worn
    mats = [worn_leather((0.16, 0.10, 0.06), scuff=0.5), plate, brass(), worn_leather((0.30, 0.17, 0.08))]
    view = rot_x(0.22) @ rot_y(-0.32)
    return scene, mats, 1.1, view


BUILDERS = {
    "leather_gloves": leather_gloves,
    "gauntlets": gauntlets,
}
