"""Torso armor, gloves, boots (see items.py for shared helpers)."""
from __future__ import annotations

import numpy as np

from items import brass, dark_steel, leather, steel
from sdf import (Material, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere,
                 sd_torus, smin, union, v3, value_noise)


def _torso_d(p, with_sleeves=False):
    """Front-facing torso: 2D silhouette extruded with a curved chest, neck hole and arm holes."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    ax = np.abs(x)
    w = 0.50 - 0.07 * np.clip((y + 0.1) / 0.7, 0, 1) * 0 + 0.02 * np.sin((y + 0.7) * 2.4)
    w = np.where(y > 0.25, 0.50 - (y - 0.25) * 0.55, np.where(y < -0.25, 0.46 + (-0.25 - y) * 0.12, 0.45 + (0.25 - y) * 0.0 + 0.05 * (y + 0.25) / 0.5))
    d2 = np.maximum(ax - w, np.maximum(y - 0.64, -0.72 - y))
    neck = np.hypot(x / 0.19, (y - 0.64) / 0.15) - 1.0
    d2 = np.maximum(d2, -neck * 0.15)
    if not with_sleeves:
        arm = np.hypot((ax - 0.52) / 0.20, (y - 0.30) / 0.32) - 1.0
        d2 = np.maximum(d2, -arm * 0.18)
    zt = 0.075 + 0.17 * np.sqrt(np.clip(1 - (x / 0.56) ** 2, 0, 1))
    return np.maximum(d2, np.abs(z) - zt) * 0.7


def leather_vest(rarity="common"):
    M_LEATHER, M_DARK, M_BRASS, M_STITCH = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        body = _torso_d(p)
        # centre opening with lacing
        opening = np.maximum(np.abs(x) - 0.05, y + 0.62) * 0.7
        body = np.maximum(body, -np.maximum(opening, -(y - 0.50)))
        belt = np.maximum(_torso_d(p) - 0.016, np.maximum(np.abs(y + 0.30) - 0.055, 0.0))
        belt = np.maximum(belt, np.abs(y + 0.30) - 0.055)
        buckle = sd_box(p, v3(0.0, -0.30, 0.255), v3(0.065, 0.055, 0.020), 0.012)
        buckle = np.maximum(buckle, -sd_box(p, v3(0.0, -0.30, 0.26), v3(0.036, 0.030, 0.03), 0.0))
        straps = np.minimum(sd_box(p, v3(-0.30, 0.52, 0.12), v3(0.07, 0.14, 0.20), 0.03),
                            sd_box(p, v3(0.30, 0.52, 0.12), v3(0.07, 0.14, 0.20), 0.03))
        straps = np.maximum(straps, _torso_d(p) - 0.03)
        laces = np.minimum.reduce([sd_capsule(p, v3(-0.075, yy, 0.115), v3(0.075, yy - 0.10, 0.115), 0.011)
                                   for yy in np.linspace(0.42, -0.35, 7)] +
                                  [sd_capsule(p, v3(0.075, yy, 0.115), v3(-0.075, yy - 0.10, 0.115), 0.011)
                                   for yy in np.linspace(0.42, -0.35, 7)])
        eyelets = np.minimum.reduce([sd_sphere(p, v3(s * 0.085, yy, 0.12), 0.016)
                                     for s in (-1, 1) for yy in np.linspace(0.42, -0.5, 8)])
        rivets = np.minimum.reduce([sd_sphere(p, v3(s * 0.30, 0.60, 0.215), 0.020) for s in (-1, 1)] +
                                   [sd_sphere(p, v3(s * 0.30, 0.46, 0.26), 0.020) for s in (-1, 1)])
        return union([(body, M_LEATHER), (belt, M_DARK), (straps, M_DARK), (buckle, M_BRASS), (laces, M_STITCH),
                      (eyelets, M_BRASS), (rivets, M_BRASS)])

    mats = [leather((0.44, 0.25, 0.12)), leather((0.26, 0.15, 0.07)), brass(),
            Material((0.85, 0.72, 0.52), rough=0.7, spec=0.2)]
    view = rot_x(0.18) @ rot_y(-0.28)
    return scene, mats, 1.0, view


def chainmail(rarity="common"):
    M_MAIL, M_DARK, M_BRASS = 0, 1, 2

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        body = _torso_d(p, with_sleeves=True)
        ax = np.abs(x)
        sleeve_l = sd_capsule(p, v3(-0.50, 0.44, 0.0), v3(-0.78, 0.12, 0.0), 0.17)
        sleeve_r = sd_capsule(p, v3(0.50, 0.44, 0.0), v3(0.78, 0.12, 0.0), 0.17)
        sleeves = np.minimum(sleeve_l, sleeve_r)
        # hollow sleeve ends (cut along the capsule axis)
        cut_l = sd_capsule(p, v3(-0.60, 0.32, 0.0), v3(-0.95, -0.12, 0.0), 0.11)
        cut_r = sd_capsule(p, v3(0.60, 0.32, 0.0), v3(0.95, -0.12, 0.0), 0.11)
        sleeves = np.maximum(sleeves, -np.minimum(cut_l, cut_r))
        torso = smin(body, sleeves, 0.04)
        coif = sd_torus(p, v3(0, 0.62, 0.03), 0.20, 0.06, axis="y")
        coif = np.maximum(coif, 0.0 - (0.0 - coif))
        belt = np.maximum(np.abs(y + 0.28) - 0.04, _torso_d(p, True) - 0.012)
        buckle = sd_box(p, v3(0.0, -0.28, 0.24), v3(0.05, 0.05, 0.02), 0.012)
        return union([(torso, M_MAIL), (coif, M_MAIL), (belt, M_DARK), (buckle, M_BRASS)])

    def ring_bump(p):
        f = 78.0
        a = np.abs(np.sin(f * (p[:, 0] + p[:, 1])))
        b = np.abs(np.sin(f * (p[:, 0] - p[:, 1])))
        return (a * b) * 0.012

    def ring_tint(p, alb):
        f = 78.0
        a = np.abs(np.sin(f * (p[:, 0] + p[:, 1])))
        b = np.abs(np.sin(f * (p[:, 0] - p[:, 1])))
        return alb * (0.30 + 0.85 * (a * b) ** 0.5)[:, None]

    mail = Material((0.70, 0.73, 0.77), metal=1.0, rough=0.30, spec=0.9, bump=ring_bump, tint_fn=ring_tint)
    mats = [mail, leather((0.25, 0.15, 0.07)), brass()]
    view = rot_x(0.18) @ rot_y(-0.28)
    return scene, mats, 1.1, view


def _hand(plated: bool):
    M_LEATHER, M_DARK, M_STEEL, M_BRASS = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        palm = sd_box(p, v3(0, 0.0, 0.0), v3(0.30, 0.28, 0.07), 0.07)
        parts = [(palm, M_LEATHER)]
        tips = [0.62, 0.74, 0.70, 0.58]
        xs = [-0.225, -0.075, 0.075, 0.225]
        for i, (fx, ty) in enumerate(zip(xs, tips)):
            splay = (fx) * 0.25
            a = v3(fx, 0.22, 0.0)
            m = v3(fx + splay * 0.6, 0.22 + (ty - 0.22) * 0.55, 0.012)
            b = v3(fx + splay, ty, -0.012)
            f = np.minimum(sd_capsule(p, a, m, 0.058), sd_capsule(p, m, b, 0.052))
            parts.append((f, M_LEATHER))
            if plated:
                for t in (0.32, 0.62, 0.9):
                    c = a + (b - a) * t
                    pl = sd_box(p, c + v3(0, 0, 0.035), v3(0.050, 0.075, 0.025), 0.02)
                    parts.append((pl, M_STEEL))
        thumb = np.minimum(sd_capsule(p, v3(-0.28, -0.02, 0.0), v3(-0.52, 0.22, 0.02), 0.068),
                           sd_capsule(p, v3(-0.52, 0.22, 0.02), v3(-0.60, 0.44, 0.0), 0.058))
        parts.append((thumb, M_LEATHER))
        # cuff (flared)
        pc = p.copy()
        pc[:, 2] = pc[:, 2] * 1.8
        cuff = sd_cone_y(pc, v3(0, -0.52, 0), 0.30, 0.40, 0.26, 0.04)
        parts.append((cuff, M_STEEL if plated else M_DARK))
        if plated:
            plate = sd_box(p, v3(0, 0.02, 0.10), v3(0.27, 0.26, 0.022), 0.05)
            parts.append((plate, M_STEEL))
            rim = sd_torus(pc, v3(0, -0.78, 0), 0.305, 0.034, axis="y")
            parts.append((rim, M_STEEL))
            rv = np.minimum.reduce([sd_sphere(p, v3(xx, 0.12 - k * 0.14, 0.126), 0.020)
                                    for k in range(3) for xx in (-0.17, 0.17)])
            parts.append((rv, M_BRASS))
        else:
            seam = np.minimum.reduce([sd_capsule(p, v3(xx, 0.28, 0.095), v3(xx, -0.18, 0.095), 0.010)
                                      for xx in (-0.15, 0.0, 0.15)])
            parts.append((seam, M_DARK))
        return union(parts)

    if plated:
        mats = [leather((0.30, 0.18, 0.09)), leather((0.22, 0.13, 0.06)), steel(0.26), brass()]
    else:
        mats = [leather((0.45, 0.26, 0.13)), leather((0.28, 0.16, 0.08)), steel(), brass()]
    view = rot_x(0.30) @ rot_y(-0.30)
    return scene, mats, 1.1, view


def leather_gloves(rarity="common"):
    return _hand(False)


def gauntlets(rarity="common"):
    return _hand(True)


def _boot(plated: bool):
    M_LEATHER, M_DARK, M_STEEL, M_BRASS = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        toe = sd_ellipsoid(p, v3(0.22, -0.43, 0), v3(0.46, 0.17, 0.205))
        heel_block = sd_ellipsoid(p, v3(-0.22, -0.30, 0), v3(0.26, 0.30, 0.215))
        foot = smin(toe, heel_block, 0.10)
        shaft = sd_cone_y(p, v3(-0.17, 0.12, 0), 0.215, 0.250, 0.55, 0.05)
        shaft = np.maximum(shaft, -sd_cone_y(p, v3(-0.17, 0.20, 0), 0.17, 0.210, 0.60, 0.0))
        body = smin(foot, shaft, 0.08)
        sole = sd_box(p, v3(0.07, -0.60, 0), v3(0.56, 0.035, 0.225), 0.03)
        heel = sd_box(p, v3(-0.28, -0.65, 0), v3(0.16, 0.06, 0.22), 0.025)
        cuff = sd_torus(p, v3(-0.17, 0.66, 0), 0.232, 0.042, axis="y")
        strap = sd_torus(p, v3(-0.17, 0.18, 0), 0.252, 0.026, axis="y")
        buckle = sd_box(p, v3(0.115, 0.18, 0.0), v3(0.030, 0.050, 0.060), 0.01)
        buckle = np.maximum(buckle, -sd_box(p, v3(0.12, 0.18, 0.0), v3(0.016, 0.028, 0.08), 0.0))
        parts = [(body, M_STEEL if plated else M_LEATHER), (sole, M_DARK), (heel, M_DARK)]
        if plated:
            bands = np.minimum.reduce([sd_torus(p, v3(-0.17, yy, 0), 0.240, 0.028, axis="y")
                                       for yy in (0.52, 0.34, 0.02)])
            toeplates = np.minimum.reduce([sd_ellipsoid(p, v3(0.18 + k * 0.17, -0.40 + k * -0.02, 0), v3(0.12, 0.18, 0.225))
                                           for k in range(3)])
            parts += [(bands, M_STEEL), (toeplates, M_STEEL), (cuff, M_STEEL), (strap, M_DARK), (buckle, M_BRASS)]
        else:
            parts += [(cuff, M_DARK), (strap, M_DARK), (buckle, M_BRASS)]
        return union(parts)

    if plated:
        mats = [leather(), leather((0.20, 0.12, 0.06)), steel(0.26), brass()]
        mats[0] = steel(0.26)
    else:
        mats = [leather((0.42, 0.24, 0.12)), leather((0.22, 0.13, 0.06)), steel(), brass()]
    view = rot_x(0.30) @ rot_y(-0.55)
    return scene, mats, 1.1, view


def leather_boots(rarity="common"):
    return _boot(False)


def plate_boots(rarity="common"):
    return _boot(True)


BUILDERS = {
    "leather_vest": leather_vest,
    "chainmail": chainmail,
    "leather_gloves": leather_gloves,
    "gauntlets": gauntlets,
    "leather_boots": leather_boots,
    "plate_boots": plate_boots,
}
