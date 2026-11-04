"""Second pass on the simplest armour pieces (leather cap, leather vest, leather/plate boots): worn leather
with grain, scuffs and visible stitching, stitched panels, articulated plate lames. Overrides the builders
of the same name in items_weapons.py / items_armor.py (registered last in render.py)."""
from __future__ import annotations

import numpy as np

from items import brass, fold_polar, steel
from items_armor import _torso_d
from sdf import (Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere,
                 sd_torus, smax, smin, union, v3, value_noise)

THREAD = np.array([0.80, 0.68, 0.48], dtype=np.float32)


def worn_leather(color, stitches=None, trim=None, trim_color=None, scuff=1.0):
    """Leather with large-scale tone variation, darker grime, lighter scuffs, fine grain and creases.
    stitches(p) -> mask 0..1 of thread pixels; trim(p) -> mask of a darker edge binding."""
    col = np.array(color, dtype=np.float32)
    tcol = None if trim_color is None else np.array(trim_color, dtype=np.float32)

    def tint(p, a):
        tone = 0.68 + 0.55 * fbm(p, 3.5, 3, 41)
        grime = np.clip((fbm(p, 9.0, 3, 43) - 0.55) * 2.5, 0, 1) * 0.30
        sc = value_noise(p * np.array([1.0, 6.0, 1.0], dtype=np.float32), 22.0, 45)
        scuffs = np.clip((sc - 0.78) * 4.0, 0, 1) * 0.35 * scuff
        out = a * (tone * (1 - grime))[:, None]
        out = out + (np.array([0.75, 0.58, 0.40], dtype=np.float32) - out) * scuffs[:, None]
        if trim is not None:
            m = trim(p)[:, None]
            out = out * (1 - m) + tcol * (0.8 + 0.4 * fbm(p, 6.0, 2, 47))[:, None] * m
        if stitches is not None:
            m = stitches(p)[:, None]
            out = out * (1 - m) + THREAD * m
        return out

    def bump(p):
        grain = (fbm(p, 120.0, 2, 51) - 0.5) * 0.0022
        crease = (np.abs(fbm(p * np.array([1.0, 3.0, 1.0], dtype=np.float32), 14.0, 3, 53) - 0.5) - 0.25) * 0.004
        b = grain + crease
        if stitches is not None:
            b = b + stitches(p) * 0.0025
        return b

    return Material(col, metal=0.0, rough=0.40, spec=0.60, bump=bump, tint_fn=tint)


def dashes(u, along, width, freq, d, duty=0.25):
    """Thread mask: within `width` of the seam line (signed distance d) and on the dashes along `along`."""
    on = (np.sin(along * freq) > duty).astype(np.float32)
    return np.clip(1.0 - np.abs(d) / width, 0, 1) ** 0.5 * on * u


# --------------------------------------------------------------------------- leather cap

def leather_cap(rarity="common"):
    M_PANEL, M_DARK, M_BRASS, M_THONG = range(4)
    N = 6

    def seam_q(p):
        q = fold_polar(p, N)
        return q

    def stitches(p):
        q = seam_q(p)
        y = p[:, 1]
        up = ((y > -0.09) & (y < 0.44)).astype(np.float32)
        # two rows of stitches, one on each side of every panel seam
        s = np.maximum(dashes(up, y, 0.0075, 95.0, np.abs(q[:, 2]) - 0.036),
                       0.0)
        return s

    def band_stitches(p):
        y = p[:, 1]
        ang = np.arctan2(p[:, 2], p[:, 0])
        return np.maximum(dashes(1.0, ang, 0.008, 160.0, y + 0.115), dashes(1.0, ang, 0.008, 160.0, y + 0.235))

    def flap_stitches(p):
        y, z = p[:, 1], p[:, 2]
        e = np.hypot((z - 0.08) / 0.17, (y + 0.36) / 0.21) - 1.0  # flap outline (seen from the side)
        side = (np.abs(p[:, 0]) > 0.40).astype(np.float32)
        return dashes(side, y * 3 + z * 3, 0.012, 34.0, e * 0.19 + 0.03)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        q = seam_q(p)
        dome = sd_ellipsoid(p, v3(0, -0.02, 0), v3(0.50, 0.50, 0.53))
        # raised, rolled seams where the six panels meet + each panel slightly puffed
        seam = np.exp(-(q[:, 2] / 0.018) ** 2) * (y > -0.12)
        puff = np.clip(np.abs(q[:, 2]) / 0.25, 0, 1) * 0.008
        dome = dome - seam * 0.013 - puff
        dome = np.maximum(dome, -(y + 0.20))
        dome = np.maximum(dome, -sd_ellipsoid(p, v3(0, -0.05, 0), v3(0.45, 0.46, 0.48)))
        button = smin(sd_sphere(p, v3(0, 0.495, 0), 0.050), sd_cyl_y(p, v3(0, 0.46, 0), 0.03, 0.03, 0.01), 0.02)
        # thick rolled brim band
        band = sd_cyl_y(p, v3(0, -0.175, 0), 0.535, 0.080, 0.035)
        band = np.maximum(band, -sd_cyl_y(p, v3(0, -0.175, 0), 0.455, 0.2, 0.0))
        # curved ear flaps hanging from the band, cut from a slightly larger shell
        shell = np.abs(sd_ellipsoid(p, v3(0, -0.10, 0), v3(0.515, 0.58, 0.545))) - 0.018
        outline = np.hypot((z - 0.08) / 0.17, (y + 0.36) / 0.21) - 1.0
        flaps = np.maximum(shell, np.maximum(outline * 0.19, np.maximum(-np.abs(x) + 0.30, y + 0.20)))
        # leather ties at the bottom of the flaps
        ties = np.minimum.reduce([sd_capsule(p, v3(s * 0.36, -0.54, 0.10), v3(s * 0.33, -0.80, 0.17), 0.016)
                                  for s in (-1, 1)])
        ties = np.minimum(ties, np.minimum.reduce([sd_capsule(p, v3(s * 0.36, -0.54, 0.10), v3(s * 0.38, -0.76, 0.03), 0.014)
                                                   for s in (-1, 1)]))
        rivets = np.minimum.reduce([sd_sphere(p, v3(s * 0.505, -0.205, 0.18), 0.024) for s in (-1, 1)] +
                                   [sd_sphere(p, v3(s * 0.505, -0.205, -0.02), 0.024) for s in (-1, 1)])
        return union([(dome, M_PANEL), (button, M_DARK), (band, M_DARK), (flaps, M_DARK), (ties, M_THONG),
                      (rivets, M_BRASS)])

    mats = [worn_leather((0.40, 0.22, 0.10), stitches=stitches),
            worn_leather((0.22, 0.12, 0.05), stitches=lambda p: np.maximum(band_stitches(p), flap_stitches(p))),
            brass(),
            worn_leather((0.24, 0.13, 0.06), scuff=0.3)]
    view = rot_x(0.30) @ rot_y(-0.55)
    return scene, mats, 0.95, view


# --------------------------------------------------------------------------- leather vest (jerkin)

def leather_vest(rarity="common"):
    M_LEATHER, M_DARK, M_BRASS, M_LACE = range(4)

    def trim(p):
        x, y = p[:, 0], p[:, 1]
        ax = np.abs(x)
        neck = np.abs(np.hypot(x / 0.19, (y - 0.64) / 0.15) - 1.0) < 0.22
        arm = np.abs(np.hypot((ax - 0.52) / 0.20, (y - 0.30) / 0.32) - 1.0) < 0.14
        hem = y < -0.645
        opening = (ax < 0.11) & (y < 0.52)
        return (neck | arm | hem | opening).astype(np.float32)

    def stitches(p):
        x, y = p[:, 0], p[:, 1]
        ax = np.abs(x)
        panel = np.maximum(dashes(((y > -0.62) & (y < 0.40)).astype(np.float32), y, 0.007, 80.0, np.abs(ax - 0.27) - 0.028), 0)
        hem = dashes((ax < 0.5).astype(np.float32), x, 0.007, 80.0, y + 0.625)
        opening = dashes(((y < 0.50) & (y > -0.62)).astype(np.float32), y, 0.007, 80.0, ax - 0.125)
        return np.maximum(np.maximum(panel, hem), opening)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        ax = np.abs(x)
        body = _torso_d(p)
        # stitched panel seams (grooves) and a padded, slightly puffed chest
        groove = np.exp(-((ax - 0.27) / 0.014) ** 2) * (y < 0.42) * 0.010
        puff = np.exp(-((ax - 0.40) / 0.10) ** 2) * 0.012 + np.exp(-((ax - 0.15) / 0.07) ** 2) * 0.008
        body = body + groove - puff * (y < 0.42)
        opening = np.maximum(ax - 0.05, y + 0.62) * 0.7
        body = np.maximum(body, -np.maximum(opening, -(y - 0.50)))
        belt = np.maximum(_torso_d(p) - 0.018, np.abs(y + 0.30) - 0.055)
        buckle = sd_box(p, v3(0.0, -0.30, 0.262), v3(0.068, 0.058, 0.020), 0.012)
        buckle = np.maximum(buckle, -sd_box(p, v3(0.0, -0.30, 0.27), v3(0.038, 0.032, 0.03), 0.0))
        prong = sd_box(p, v3(0.0, -0.30, 0.282), v3(0.010, 0.034, 0.006), 0.004)
        # layered shoulder guards
        guards = np.minimum.reduce([np.maximum(np.abs(sd_ellipsoid(p, v3(s * 0.37, 0.57 - k * 0.07, 0.02), v3(0.12, 0.045, 0.19))) - 0.011,
                                               -(y - (0.46 - k * 0.07)))
                                    for s in (-1, 1) for k in (0, 1)])
        laces = np.minimum.reduce([sd_capsule(p, v3(-0.078, yy, 0.118), v3(0.078, yy - 0.10, 0.118), 0.011)
                                   for yy in np.linspace(0.42, -0.35, 7)] +
                                  [sd_capsule(p, v3(0.078, yy, 0.118), v3(-0.078, yy - 0.10, 0.118), 0.011)
                                   for yy in np.linspace(0.42, -0.35, 7)])
        eyelets = np.minimum.reduce([sd_torus(p - v3(s * 0.088, yy, 0.125), v3(0, 0, 0), 0.015, 0.006, axis="z")
                                     for s in (-1, 1) for yy in np.linspace(0.42, -0.5, 8)])
        rivets = np.minimum.reduce([sd_sphere(p, v3(s * 0.40, 0.55, 0.21), 0.020) for s in (-1, 1)])
        return union([(body, M_LEATHER), (belt, M_DARK), (guards, M_DARK), (buckle, M_BRASS), (prong, M_BRASS),
                      (laces, M_LACE), (eyelets, M_BRASS), (rivets, M_BRASS)])

    mats = [worn_leather((0.38, 0.21, 0.10), stitches=stitches, trim=trim, trim_color=(0.16, 0.08, 0.04)),
            worn_leather((0.21, 0.12, 0.05), scuff=0.6),
            brass(),
            worn_leather((0.62, 0.48, 0.30), scuff=0.0)]
    view = rot_x(0.18) @ rot_y(-0.30)
    return scene, mats, 1.0, view


# --------------------------------------------------------------------------- boots

def _foot(p):
    toe = sd_ellipsoid(p, v3(0.22, -0.43, 0), v3(0.46, 0.17, 0.205))
    heel_block = sd_ellipsoid(p, v3(-0.22, -0.30, 0), v3(0.26, 0.30, 0.215))
    return smin(toe, heel_block, 0.10)


def plate_boots(rarity="common"):
    """Sabaton + greave: overlapping steel lames over the foot, creased greave, leather ankle strap."""
    M_STEEL, M_DARK, M_BRASS, M_LEATHER = range(4)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        foot = _foot(p)
        # articulated lames: a sawtooth step along the foot, each lame overlapping the next towards the toe
        w = 0.115
        t = (x + 0.06) / w
        frac = t - np.floor(t)
        on = ((x > -0.06) & (x < 0.62) & (y > -0.56)).astype(np.float32)
        foot = foot - on * (0.020 * frac + 0.006)
        toe_cap = sd_ellipsoid(p, v3(0.56, -0.47, 0), v3(0.15, 0.13, 0.18))
        foot = smin(foot, toe_cap, 0.03)
        # greave: slightly flared cone with a crease down the front
        c = v3(-0.17, 0.14, 0)
        qx, qz = x - c[0], z - c[2]
        ang = np.arctan2(qz, qx)
        shaft = sd_cone_y(p, c, 0.225, 0.262, 0.54, 0.04) - 0.028 * np.exp(-(ang / 0.16) ** 2)
        shaft = np.maximum(shaft, -sd_cone_y(p, v3(-0.17, 0.22, 0), 0.18, 0.225, 0.60, 0.0))
        body = smin(foot, shaft, 0.06)
        rim = sd_torus(p, v3(-0.17, 0.675, 0), 0.258, 0.030, axis="y")
        ankle_band = sd_torus(p, v3(-0.17, -0.06, 0), 0.250, 0.026, axis="y")
        rivets = np.minimum.reduce([sd_sphere(p, v3(-0.17 + 0.262 * np.cos(a), 0.60, 0.262 * np.sin(a)), 0.020)
                                    for a in (-1.2, -0.4, 0.4, 1.2)] +
                                   [sd_sphere(p, v3(xx, -0.36, s * 0.205), 0.018) for xx in (0.02, 0.14, 0.26, 0.38) for s in (-1, 1)])
        strap = sd_torus(p, v3(-0.17, 0.20, 0), 0.262, 0.034, axis="y")
        buckle = sd_box(p, v3(-0.17, 0.20, 0.290), v3(0.055, 0.052, 0.014), 0.010)
        buckle = np.maximum(buckle, -sd_box(p, v3(-0.17, 0.20, 0.30), v3(0.030, 0.028, 0.03), 0.0))
        sole = sd_box(p, v3(0.07, -0.61, 0), v3(0.57, 0.035, 0.225), 0.03)
        heel = sd_box(p, v3(-0.28, -0.665, 0), v3(0.16, 0.06, 0.22), 0.025)
        return union([(body, M_STEEL), (rim, M_STEEL), (ankle_band, M_STEEL), (rivets, M_BRASS), (strap, M_LEATHER),
                      (buckle, M_BRASS), (sole, M_DARK), (heel, M_DARK)])

    plate = steel(0.30)
    base_tint = plate.tint_fn

    def worn(p, a):
        if base_tint is not None:
            a = base_tint(p, a)
        k = 0.82 + 0.25 * fbm(p, 5.0, 3, 61)
        return a * k[:, None]
    plate.tint_fn = worn
    mats = [plate, worn_leather((0.18, 0.11, 0.06), scuff=0.4), brass(), worn_leather((0.30, 0.17, 0.08))]
    view = rot_x(0.28) @ rot_y(-0.55)
    return scene, mats, 1.1, view


def leather_boots(rarity="common"):
    M_LEATHER, M_DARK, M_BRASS = range(3)

    def stitches(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        # vamp seam across the instep and a welt seam just above the sole
        vamp = dashes(((x > 0.0) & (x < 0.45)).astype(np.float32), z, 0.008, 70.0, (x - 0.05) + (y + 0.15) * 0.3)
        welt = dashes((y < -0.45).astype(np.float32), x + z, 0.008, 70.0, y + 0.545)
        back = dashes((y > -0.30).astype(np.float32), y, 0.008, 70.0, np.abs(z) * 0 + (x + 0.17 + 0.235))
        return np.maximum(np.maximum(vamp, welt), back * (np.abs(z) < 0.03))

    def scene(p):
        foot = _foot(p)
        shaft = sd_cone_y(p, v3(-0.17, 0.12, 0), 0.215, 0.250, 0.55, 0.05)
        shaft = np.maximum(shaft, -sd_cone_y(p, v3(-0.17, 0.20, 0), 0.17, 0.210, 0.60, 0.0))
        # a few soft folds around the ankle
        y = p[:, 1]
        folds = 0.010 * np.sin(y * 38.0) * np.exp(-((y + 0.05) / 0.16) ** 2)
        body = smin(foot, shaft, 0.08) - folds
        cuff = smax(sd_cone_y(p, v3(-0.17, 0.60, 0), 0.262, 0.290, 0.09, 0.03),
                    -sd_cone_y(p, v3(-0.17, 0.62, 0), 0.20, 0.235, 0.2, 0.0), 0.01)
        sole = sd_box(p, v3(0.07, -0.60, 0), v3(0.56, 0.035, 0.225), 0.03)
        heel = sd_box(p, v3(-0.28, -0.65, 0), v3(0.16, 0.06, 0.22), 0.025)
        strap = sd_torus(p, v3(-0.17, 0.18, 0), 0.252, 0.024, axis="y")
        buckle = sd_box(p, v3(-0.17, 0.18, 0.255), v3(0.050, 0.048, 0.012), 0.010)
        buckle = np.maximum(buckle, -sd_box(p, v3(-0.17, 0.18, 0.26), v3(0.028, 0.026, 0.03), 0.0))
        return union([(body, M_LEATHER), (cuff, M_DARK), (sole, M_DARK), (heel, M_DARK), (strap, M_DARK), (buckle, M_BRASS)])

    mats = [worn_leather((0.36, 0.20, 0.09), stitches=stitches), worn_leather((0.18, 0.10, 0.05), scuff=0.4), brass()]
    view = rot_x(0.30) @ rot_y(-0.55)
    return scene, mats, 1.1, view


BUILDERS = {
    "leather_cap": leather_cap,
    "leather_vest": leather_vest,
    "plate_boots": plate_boots,
    "leather_boots": leather_boots,
}
