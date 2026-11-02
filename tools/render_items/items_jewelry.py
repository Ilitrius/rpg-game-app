"""Rings, amulet, talisman (see items.py for shared helpers)."""
from __future__ import annotations

import numpy as np

from items import brass, dark_steel, gem_mat, leather, sd_gem, fold_polar, silver
from sdf import (Material, rot_x, rot_y, rot_z, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere,
                 sd_torus, smin, union, v3)


def _ring(gold: bool):
    M_BAND, M_GEM, M_PRONG = 0, 1, 2

    def scene(p, _gold=gold):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        band = sd_torus(p, v3(0, 0, 0), 0.50, 0.075, axis="z")
        # flat outer face + engraved groove ring
        band = np.maximum(band, -np.maximum(np.abs(np.hypot(x, y) - 0.575) - 0.012, np.abs(z) - 0.03) * 0.0 - 0.0) \
            if False else band
        groove = sd_torus(p, v3(0, 0, 0), 0.575, 0.012, axis="z") if False else None
        del groove
        shoulders = np.minimum(sd_ellipsoid(p, v3(-0.10, 0.52, 0), v3(0.12, 0.10, 0.08)),
                               sd_ellipsoid(p, v3(0.10, 0.52, 0), v3(0.12, 0.10, 0.08)))
        bezel = sd_cone_y(p, v3(0, 0.57, 0), 0.10, 0.17 if _gold else 0.15, 0.06, 0.02)
        ring_body = smin(smin(band, shoulders, 0.05), bezel, 0.04)
        gem_r = 0.15 if _gold else 0.125
        gem = sd_gem(p, v3(0, 0.66, 0), gem_r)
        prongs = np.minimum.reduce([sd_capsule(p, v3(np.cos(a) * gem_r * 1.02, 0.60, np.sin(a) * gem_r * 1.02),
                                               v3(np.cos(a) * gem_r * 0.78, 0.70, np.sin(a) * gem_r * 0.78), 0.016)
                                    for a in (0.785, 2.356, 3.927, 5.498)]) if _gold else np.full(len(x), 9.0, dtype=np.float32)
        return union([(ring_body, M_BAND), (gem, M_GEM), (prongs, M_PRONG)])

    return scene


def silver_ring(rarity="common"):
    mats = [silver(), gem_mat(rarity), silver()]
    return _ring(False), mats, 0.9, rot_x(0.55) @ rot_y(0.55)


def gold_ring(rarity="common"):
    mats = [brass(), gem_mat(rarity), brass()]
    return _ring(True), mats, 0.9, rot_x(0.55) @ rot_y(0.55)


def amulet(rarity="common"):
    M_CHAIN, M_GEM, M_FRAME = 0, 1, 2
    n_links = 17
    xs = np.linspace(-0.74, 0.74, n_links)
    ys = 0.62 - 1.05 * (1 - (xs / 0.78) ** 2)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        links = []
        for i in range(n_links):
            a = np.arctan2(np.gradient(ys)[i], np.gradient(xs)[i])
            c, s = np.cos(a), np.sin(a)
            q = p - v3(xs[i], ys[i], 0)
            lx = q[:, 0] * c + q[:, 1] * s
            ly = -q[:, 0] * s + q[:, 1] * c
            lz = q[:, 2]
            if i % 2 == 0:
                d = np.hypot(np.hypot(lx / 1.55, ly) - 0.052, lz) - 0.016
            else:
                d = np.hypot(np.hypot(lx / 1.55, lz) - 0.052, ly) - 0.016
            links.append(d * 0.7)
        chain = np.minimum.reduce(links)
        pend = sd_ellipsoid(p, v3(0, -0.74, 0), v3(0.21, 0.29, 0.055))
        frame = np.maximum(pend, -sd_ellipsoid(p, v3(0, -0.74, 0.03), v3(0.15, 0.22, 0.06)))
        rim = sd_torus(p, v3(0, -0.74, 0.0), 0.19, 0.022, axis="z")
        bail = sd_torus(p, v3(0, -0.40, 0.0), 0.05, 0.015, axis="x")
        pg = np.stack([x, z, -y], axis=-1)
        gem = sd_gem(pg, v3(0, 0.05, 0.74), 0.13)
        return union([(chain, M_CHAIN), (np.minimum(frame, rim), M_FRAME), (bail, M_FRAME), (gem, M_GEM)])

    mats = [silver(), gem_mat(rarity), brass()]
    return scene, mats, 1.1, rot_x(0.30) @ rot_y(-0.30)


def talisman(rarity="common"):
    M_METAL, M_GEM, M_CORD, M_DARK = 0, 1, 2, 3

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        pm = np.stack([x, z, y], axis=-1)           # disc axis (model z) mapped to cylinder axis y
        disc = sd_cyl_y(pm, v3(0, 0, -0.0), 0.46, 0.05, 0.018)
        groove = sd_torus(p, v3(0, 0, 0.05), 0.33, 0.018, axis="z")
        disc = np.maximum(disc, -groove)
        rim = sd_torus(p, v3(0, 0, 0.0), 0.47, 0.045, axis="z")
        q = fold_polar(pm, 8)
        spike = sd_cone_y(np.stack([q[:, 1], q[:, 0] - 0.0, q[:, 2]], axis=-1), v3(0, 0.62, 0), 0.0, 0.0, 0.0001) \
            if False else None
        del spike
        pr = np.stack([x, y, z], axis=-1)
        spikes = []
        for k in range(8):
            a = np.pi * k / 4
            c, s = np.cos(a), np.sin(a)
            base = v3(0.50 * c, 0.50 * s, 0.0)
            tip = v3(0.70 * c, 0.70 * s, 0.0)
            spikes.append(sd_capsule(pr, base, tip, 0.045 - 0.0) * 0.6)
        spikes = np.minimum.reduce(spikes)
        runes = np.minimum.reduce([sd_box(p, v3(0.24 * np.cos(a), 0.24 * np.sin(a), 0.052),
                                          v3(0.008, 0.045, 0.008), 0.004)
                                   for a in np.linspace(0.2, 6.2, 8)])
        pg = np.stack([x, z, -y], axis=-1)
        gem = sd_gem(pg, v3(0, 0.07, 0.0), 0.15)
        gem_bezel = sd_torus(p, v3(0, 0, 0.05), 0.15, 0.022, axis="z")
        bail = sd_torus(p, v3(0, 0.80, 0.0), 0.075, 0.02, axis="x")
        cord = sd_torus(p, v3(0, 1.00, 0.0), 0.38, 0.020, axis="z")
        disc_all = smin(np.minimum(disc, rim), spikes, 0.02)
        return union([(disc_all, M_METAL), (runes, M_DARK), (gem, M_GEM), (gem_bezel, M_METAL),
                      (bail, M_METAL), (cord, M_CORD)])

    mats = [dark_steel(), gem_mat(rarity), leather((0.20, 0.12, 0.06)), brass()]
    return scene, mats, 1.2, rot_x(0.30) @ rot_y(-0.35)


BUILDERS = {
    "silver_ring": silver_ring,
    "gold_ring": gold_ring,
    "amulet": amulet,
    "talisman": talisman,
}
