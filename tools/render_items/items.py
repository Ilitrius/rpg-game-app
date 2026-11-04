"""Item models for the offline renderer (signed distance fields, model space: y up, +z to camera)."""
from __future__ import annotations

import numpy as np

from sdf import (Material, fbm, norm, rot_x, rot_y, rot_z, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid,
                 sd_sphere, sd_torus, smax, smin, union, v3, value_noise)

# --------------------------------------------------------------------------- materials

GEMS = {  # rarity -> (body colour, glow)
    "common": ((0.78, 0.76, 0.66), 0.05),
    "uncommon": ((0.10, 0.75, 0.30), 0.35),
    "rare": ((0.10, 0.38, 0.95), 0.35),
    "epic": ((0.65, 0.18, 0.88), 0.40),
    "legendary": ((1.00, 0.50, 0.08), 0.55),
}


def steel(rough=0.20):
    # fine brushed finish: many thin, soft streaks (coarse streaks used to show as bands in the reflection)
    streak = lambda p: (fbm(p * np.array([1.0, 0.0, 1.0], dtype=np.float32), 260.0, 2, 3) - 0.5) * 0.00035
    return Material((0.80, 0.82, 0.85), metal=1.0, rough=rough, spec=0.9, bump=streak)


def dark_steel():
    streak = lambda p: (fbm(p * np.array([1.0, 0.0, 1.0], dtype=np.float32), 220.0, 2, 5) - 0.5) * 0.00045
    return Material((0.55, 0.58, 0.62), metal=1.0, rough=0.38, spec=0.8, bump=streak)


def brass():
    n = lambda p: (value_noise(p, 90.0, 7) - 0.5) * 0.0010
    return Material((1.0, 0.76, 0.34), metal=1.0, rough=0.28, spec=1.0, bump=n)


def silver():
    n = lambda p: (value_noise(p, 120.0, 11) - 0.5) * 0.0006
    return Material((0.93, 0.94, 0.96), metal=1.0, rough=0.2, spec=1.0, bump=n)


def leather(color=(0.42, 0.24, 0.12)):
    def bump(p):
        return (fbm(p, 55.0, 3, 21) - 0.5) * 0.004

    def tint(p, a):
        k = 0.8 + 0.4 * fbm(p, 9.0, 3, 5)
        return a * k[:, None]

    return Material(color, metal=0.0, rough=0.62, spec=0.35, bump=bump, tint_fn=tint)


def gem_material(rarity):
    col, glow = GEMS[rarity]
    return Material(col, metal=0.0, rough=0.04, spec=1.4, emissive=glow, sss=1.1)


# --------------------------------------------------------------------------- sword

def short_sword(rarity="common"):
    M_STEEL, M_BRASS, M_LEATHER, M_GEM = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        # blade: tapered, lens section with central fuller, pointed tip
        w = np.where(y < 0.95, 0.165, 0.165 * np.clip((1.52 - y) / 0.57, 0.0, 1.0) ** 0.85)
        w = np.maximum(w, 0.004)
        ax = np.abs(x)
        # flat faces + ground bevels towards the edge (distinct normals -> real steel look)
        edge = np.clip((w - ax) / np.maximum(w, 1e-4), 0.0, 1.0)
        tz = np.minimum(0.040, 0.004 + (w - ax) * 0.34)
        groove = 0.020 * np.exp(-(x / 0.050) ** 4) * ((y > 0.10) & (y < 1.08)) * np.clip((1.12 - y) * 14, 0, 1)
        tz = tz - groove
        del edge
        d_blade = np.maximum(ax - w, np.maximum(np.abs(z) - tz, np.maximum(-y + 0.0, y - 1.53))) * 0.7
        # crossguard (slightly bent down at the ends)
        pg = p.copy()
        pg[:, 1] = pg[:, 1] + 0.55 * (pg[:, 0] ** 2)
        d_guard = sd_box(pg, v3(0, -0.035, 0), v3(0.40, 0.045, 0.060), 0.030)
        d_guard = smin(d_guard, sd_ellipsoid(p, v3(0, -0.035, 0), v3(0.085, 0.085, 0.085)), 0.03)
        d_end = np.minimum(sd_sphere(pg, v3(0.40, -0.035, 0), 0.062), sd_sphere(pg, v3(-0.40, -0.035, 0), 0.062))
        d_guard = np.minimum(d_guard, d_end)
        # grip with spiral wrap
        ang = np.arctan2(z, x)
        helix = np.sin(46.0 * y + 3.0 * ang)
        r_grip = 0.052 + 0.006 * helix
        d_grip = np.maximum(np.hypot(x, z) - r_grip, np.maximum(y + 0.08, -0.80 - y)) * 0.7
        # pommel
        d_pommel = smin(sd_ellipsoid(p, v3(0, -0.86, 0), v3(0.098, 0.088, 0.098)),
                        sd_cyl_y(p, v3(0, -0.76, 0), 0.07, 0.025, 0.012), 0.025)
        # gem in the guard centre + pommel cap
        d_gem = sd_ellipsoid(p, v3(0, -0.035, 0.06), v3(0.050, 0.050, 0.030))
        d_gem = np.minimum(d_gem, sd_ellipsoid(p, v3(0, -0.86, 0.075), v3(0.036, 0.036, 0.026)))
        return union([(d_blade, M_STEEL), (d_guard, M_BRASS), (d_grip, M_LEATHER),
                      (d_pommel, M_BRASS), (d_gem * 0.8, M_GEM)])

    mats = [steel(), brass(), leather(), gem_material(rarity)]
    view = rot_x(0.42) @ rot_y(-0.30) @ rot_z(-0.80)
    return scene, mats, 1.30, view


# --------------------------------------------------------------------------- shared helpers

def fold_polar(p, n):
    """Repeat space n times around the y axis (first sector centred on +x)."""
    a = np.arctan2(p[:, 2], p[:, 0])
    sec = 2 * np.pi / n
    a2 = np.mod(a + sec / 2, sec) - sec / 2
    r = np.hypot(p[:, 0], p[:, 2])
    q = p.copy()
    q[:, 0] = r * np.cos(a2)
    q[:, 2] = r * np.sin(a2)
    return q


def sd_gem(p, c, r, top=0.34, n=8):
    """Brilliant-cut gem, axis y: table, crown facets, pavilion facets, girdle (flat shaded polytope)."""
    q = p - c
    d = np.hypot(q[:, 0], q[:, 2]) - r
    d = np.maximum(d, q[:, 1] - top * r)
    for i in range(n):
        a = 2 * np.pi * i / n
        nc = norm(np.array([[np.cos(a) * 0.72, 0.69, np.sin(a) * 0.72]], dtype=np.float32))[0]
        d = np.maximum(d, q @ nc - 0.80 * r)
        a2 = a + np.pi / n
        npv = norm(np.array([[np.cos(a2) * 0.62, -0.78, np.sin(a2) * 0.62]], dtype=np.float32))[0]
        d = np.maximum(d, q @ npv - 0.52 * r)
    return d * 0.8


def gem_mat(rarity):
    col, glow = GEMS[rarity]
    return Material(tuple(min(1.0, c * 1.25) for c in col), metal=0.55, rough=0.03, spec=1.6,
                    emissive=glow * 0.8, sss=0.8)


def rusty(base):
    """Turns a Material into a rusty one (orange-brown patches, pitting)."""
    def tint(p, a):
        m = fbm(p * np.array([1.0, 0.7, 1.0], dtype=np.float32), 7.0, 4, 3)
        k = np.clip((m - 0.42) * 3.2, 0.0, 1.0)[:, None]
        rust = np.array([0.42, 0.20, 0.08], dtype=np.float32)
        return a * (1 - k) + rust * k

    base.tint_fn = tint
    base.bump = lambda p: (fbm(p, 45.0, 3, 9) - 0.5) * 0.006
    base.rough = 0.6
    base.metal = 0.75
    return base


BUILDERS = {
    "short_sword": short_sword,
}
