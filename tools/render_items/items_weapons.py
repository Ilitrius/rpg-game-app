"""Dagger, mace, axe, helmets (see items.py for the shared helpers)."""
from __future__ import annotations

import numpy as np

from items import (brass, dark_steel, fold_polar, gem_mat, leather, rusty, sd_gem, steel)
from sdf import (rot_x, rot_y, rot_z, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere, smin,
                 union, v3)


def _grip(x, y, z, r0, wob, freq, y_top, y_bot):
    ang = np.arctan2(z, x)
    r_grip = r0 + wob * np.sin(freq * y + 3.0 * ang)
    return np.maximum(np.hypot(x, z) - r_grip, np.maximum(y - y_top, y_bot - y)) * 0.7


# --------------------------------------------------------------------------- dagger

def dagger(rarity="common", rust=False):
    M_STEEL, M_BRASS, M_LEATHER = 0, 1, 2

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        w = np.where(y < 0.6, 0.095, 0.095 * np.clip((1.06 - y) / 0.46, 0.0, 1.0) ** 0.9)
        w = np.maximum(w, 0.004)
        ax = np.abs(x)
        tz = np.minimum(0.032, 0.003 + (w - ax) * 0.30)
        d_blade = np.maximum(ax - w, np.maximum(np.abs(z) - tz, np.maximum(-y, y - 1.07))) * 0.7
        d_guard = sd_box(p, v3(0, -0.035, 0), v3(0.21, 0.035, 0.050), 0.022)
        d_end = np.minimum(sd_sphere(p, v3(0.21, -0.035, 0), 0.050), sd_sphere(p, v3(-0.21, -0.035, 0), 0.050))
        d_guard = np.minimum(d_guard, d_end)
        d_grip = _grip(x, y, z, 0.043, 0.005, 50.0, -0.07, -0.55)
        d_pommel = sd_ellipsoid(p, v3(0, -0.62, 0), v3(0.07, 0.065, 0.07))
        return union([(d_blade, M_STEEL), (d_guard, M_BRASS), (d_grip, M_LEATHER), (d_pommel, M_BRASS)])

    blade = rusty(steel()) if rust else steel()
    mats = [blade, rusty(brass()) if rust else brass(), leather((0.30, 0.17, 0.09))]
    view = rot_x(0.42) @ rot_y(-0.30) @ rot_z(-0.80)
    return scene, mats, 0.98, view


def dagger_rusty(rarity="common"):
    return dagger(rarity, rust=True)


# --------------------------------------------------------------------------- mace

def mace(rarity="common"):
    M_STEEL, M_WOOD, M_BRASS, M_GEM = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        d_shaft = np.maximum(np.hypot(x, z) - 0.050, np.maximum(-0.95 - y, y - 1.0)) * 0.8
        d_wrap = _grip(x, y, z, 0.056, 0.006, 40.0, -0.05, -0.88)
        core = sd_ellipsoid(p, v3(0, 1.12, 0), v3(0.20, 0.23, 0.20))
        q = fold_polar(p, 6)
        fl = sd_box(q, v3(0.20, 1.12, 0), v3(0.13, 0.23, 0.040), 0.018)
        fl = np.maximum(fl, q[:, 0] - 0.36 + np.abs(q[:, 1] - 1.12) * 0.5)
        head = smin(core, fl * 0.8, 0.02)
        collar = sd_cyl_y(p, v3(0, 0.90, 0), 0.085, 0.05, 0.015)
        pommel = sd_ellipsoid(p, v3(0, -1.0, 0), v3(0.085, 0.075, 0.085))
        cap = sd_cone_y(p, v3(0, 1.36, 0), 0.10, 0.03, 0.06, 0.01)
        gem = sd_gem(p, v3(0, 1.30, 0), 0.075)
        return union([(head, M_STEEL), (d_shaft, M_WOOD), (d_wrap, M_WOOD), (collar, M_BRASS), (pommel, M_BRASS),
                      (cap, M_BRASS), (gem, M_GEM)])

    mats = [dark_steel(), leather((0.34, 0.20, 0.10)), brass(), gem_mat(rarity)]
    view = rot_x(0.34) @ rot_y(-0.28) @ rot_z(-0.74)
    return scene, mats, 1.42, view


# --------------------------------------------------------------------------- axe

def axe(rarity="common"):
    M_STEEL, M_WOOD, M_BRASS, M_GEM = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        d_shaft = np.maximum(np.hypot(x, z) - 0.050, np.maximum(-1.0 - y, y - 1.18)) * 0.8
        d_wrap = _grip(x, y, z, 0.057, 0.006, 40.0, -0.10, -0.92)
        bx, by = x - 0.02, y - 0.82
        ell = np.hypot(bx / 0.56, by / 0.50) - 1.0
        cut = np.hypot((x - 0.26) / 0.20, (y - 0.38) / 0.20) - 1.0
        d2 = np.maximum(ell * 0.50, -cut * 0.20)
        d2 = np.maximum(d2, -x - 0.02)
        tz = np.minimum(0.046, 0.004 + np.maximum(-d2, 0.0) * 0.55)
        d_blade = np.maximum(d2, np.abs(z) - tz) * 0.75
        spike = sd_capsule(p, v3(-0.03, 0.82, 0), v3(-0.30, 0.86, 0), 0.032)
        socket = sd_box(p, v3(0.0, 0.82, 0), v3(0.075, 0.30, 0.075), 0.03)
        gem = sd_ellipsoid(p, v3(0.075, 0.82, 0.06), v3(0.04, 0.05, 0.03))
        return union([(d_blade, M_STEEL), (spike, M_STEEL), (socket, M_BRASS), (d_shaft, M_WOOD), (d_wrap, M_WOOD),
                      (sd_sphere(p, v3(0, 1.20, 0), 0.058), M_BRASS), (gem, M_GEM)])

    mats = [steel(0.24), leather((0.30, 0.18, 0.09)), brass(), gem_mat(rarity)]
    view = rot_x(0.30) @ rot_y(0.30) @ rot_z(-0.55)
    return scene, mats, 1.35, view


# --------------------------------------------------------------------------- helmets

def leather_cap(rarity="common"):
    M_LEATHER, M_DARK, M_BRASS = 0, 1, 2

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        dome = sd_ellipsoid(p, v3(0, 0.05, 0), v3(0.52, 0.46, 0.55))
        dome = np.maximum(dome, -(y + 0.20) * 0.5)
        inner = sd_ellipsoid(p, v3(0, 0.0, 0), v3(0.46, 0.42, 0.49))
        dome = np.maximum(dome, -inner)
        band = sd_cyl_y(p, v3(0, -0.16, 0), 0.545, 0.075, 0.03)
        band = np.maximum(band, -sd_cyl_y(p, v3(0, -0.16, 0), 0.47, 0.2, 0.0))
        ridge = sd_box(p, v3(0, 0.50, 0), v3(0.02, 0.05, 0.50), 0.015)
        ridge = np.maximum(ridge, sd_ellipsoid(p, v3(0, 0.05, 0), v3(0.56, 0.52, 0.58)))
        flap_l = sd_box(p, v3(-0.47, -0.36, 0.10), v3(0.05, 0.20, 0.17), 0.045)
        flap_r = sd_box(p, v3(0.47, -0.36, 0.10), v3(0.05, 0.20, 0.17), 0.045)
        rivets = np.minimum.reduce([sd_sphere(p, v3(0.55 * np.sin(a), -0.16, 0.57 * np.cos(a)), 0.026)
                                    for a in np.linspace(-1.2, 1.2, 7)])
        return union([(dome, M_LEATHER), (band, M_DARK), (np.minimum(flap_l, flap_r), M_DARK),
                      (rivets, M_BRASS)])

    mats = [leather((0.46, 0.27, 0.14)), leather((0.28, 0.16, 0.08)), brass()]
    view = rot_x(0.28) @ rot_y(-0.45)
    return scene, mats, 0.95, view


def iron_helm(rarity="common"):
    M_STEEL, M_DARK, M_BRASS = 0, 1, 2

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        shell = sd_ellipsoid(p, v3(0, 0.05, 0), v3(0.50, 0.58, 0.54))
        shell = np.maximum(shell, -(y + 0.46) * 0.6)
        inner = sd_ellipsoid(p, v3(0, 0.0, 0), v3(0.455, 0.56, 0.49))
        shell = np.maximum(shell, -inner)
        face = sd_box(p, v3(0, -0.12, 0.56), v3(0.26, 0.20, 0.16), 0.05)
        shell = np.maximum(shell, -face)
        nose = sd_box(p, v3(0, -0.05, 0.50), v3(0.028, 0.20, 0.035), 0.012)
        brow = sd_box(p, v3(0, 0.09, 0.505), v3(0.27, 0.03, 0.04), 0.014)
        brow = np.maximum(brow, sd_ellipsoid(p, v3(0, 0.05, 0), v3(0.55, 0.60, 0.6)))
        crest = sd_box(p, v3(0, 0.62, -0.02), v3(0.022, 0.07, 0.42), 0.012)
        crest = np.maximum(crest, sd_ellipsoid(p, v3(0, 0.05, 0), v3(0.56, 0.68, 0.60)))
        band = sd_cyl_y(p, v3(0, 0.12, 0), 0.535, 0.045, 0.02)
        band = np.maximum(band, -sd_cyl_y(p, v3(0, 0.12, 0), 0.46, 0.2, 0.0))
        band = np.maximum(band, -sd_box(p, v3(0, 0.12, 0.56), v3(0.28, 0.2, 0.2), 0.0))
        cheek_l = sd_box(p, v3(-0.40, -0.22, 0.20), v3(0.07, 0.24, 0.20), 0.05)
        cheek_r = sd_box(p, v3(0.40, -0.22, 0.20), v3(0.07, 0.24, 0.20), 0.05)
        q = fold_polar(p, 14)
        rv = sd_sphere(q, v3(0.545, 0.12, 0), 0.024)
        return union([(shell, M_STEEL), (np.minimum(nose, brow), M_STEEL), (crest, M_DARK), (band, M_DARK),
                      (np.minimum(cheek_l, cheek_r), M_STEEL), (rv, M_BRASS)])

    mats = [steel(0.26), dark_steel(), brass()]
    view = rot_x(0.22) @ rot_y(-0.5)
    return scene, mats, 1.0, view


BUILDERS = {
    "dagger": dagger,
    "dagger_rusty": dagger_rusty,
    "mace": mace,
    "axe": axe,
    "leather_cap": leather_cap,
    "iron_helm": iron_helm,
}
