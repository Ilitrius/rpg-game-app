"""Elixir bottles for the shop (one per buff type). The renderer has no refraction, so a filled bottle is
modelled as the coloured liquid seen through glass: below the fill line a glossy, glowing, translucent liquid
material (swirls + bubbles), above it a pale, very glossy glass shell. Cork/caps/strings are separate parts."""
from __future__ import annotations

import numpy as np

from items import brass, gem_mat, leather, fold_polar, sd_gem
from sdf import (Material, fbm, rot_x, rot_y, sd_box, sd_capsule, sd_cone_y, sd_cyl_y, sd_ellipsoid, sd_sphere,
                 sd_torus, smax, smin, union, v3, value_noise)

M_LIQ, M_GLASS, M_CORK, M_TIE, M_CAP, M_SEAL = range(6)


def liquid(color, glow, flecks=None):
    col = np.array(color, dtype=np.float32)

    def tint(p, a):
        # slow swirls (lighter streaks) + darker depth towards the bottom
        sw = fbm(p * np.array([1.0, 2.2, 1.0], dtype=np.float32), 6.0, 4, 31)
        k = (0.72 + 0.55 * np.clip((sw - 0.35) * 2.0, 0, 1)) * (0.80 + 0.30 * np.clip(p[:, 1] + 0.5, 0, 1))
        out = a * k[:, None]
        if flecks is not None:
            f = value_noise(p, 70.0, 47)
            m = np.clip((f - 0.86) * 9.0, 0, 1)[:, None]
            out = out * (1 - m) + np.array(flecks, dtype=np.float32) * m
        return out

    return Material(col, metal=0.0, rough=0.05, spec=1.5, emissive=glow, sss=1.4, tint_fn=tint,
                    edge=((min(1, col[0] * 1.6 + 0.2), min(1, col[1] * 1.6 + 0.2), min(1, col[2] * 1.6 + 0.2)), 2.2, 0.55))


def glass():
    return Material((0.42, 0.50, 0.53), metal=0.0, rough=0.03, spec=1.9, emissive=0.0, sss=0.2,
                    edge=((0.80, 0.92, 1.0), 2.4, 0.80))


HIDE_GLASS = False  # second pass: everything behind the glass shell


def _glass(d):
    return np.full_like(d, 9.0) if HIDE_GLASS else d


def see_through_glass(img, rd):
    """Composites the glass shell over a second render without it, so the liquid surface, the inside of the
    neck and the background show through; reflections and the rim stay opaque."""
    global HIDE_GLASS
    m = rd.mat_img == M_GLASS
    if not m.any():
        return img
    HIDE_GLASS = True
    try:
        behind = rd.render()
    finally:
        HIDE_GLASS = False
    rgb = img[..., :3]
    lum = rgb @ np.array([0.30, 0.55, 0.15], dtype=np.float32)
    med = float(np.median(lum[m]))
    t = np.clip((lum - med - 0.05) / 0.35, 0, 1)
    a = (0.16 + 0.84 * t * t * (3 - 2 * t))[..., None]
    # glass over behind ("over" operator, straight alpha)
    ab = behind[..., 3:4]
    out_a = a + ab * (1 - a)
    out_rgb = (rgb * a + behind[..., :3] * ab * (1 - a)) / np.maximum(out_a, 1e-4)
    comp = np.concatenate([out_rgb, out_a], axis=-1)
    mm = m[..., None]
    return np.where(mm, comp, img).astype(np.float32)


def cork():
    def tint(p, a):
        k = 0.75 + 0.45 * fbm(p, 40.0, 3, 13)
        return a * k[:, None]
    return Material((0.62, 0.44, 0.26), rough=0.85, spec=0.15, tint_fn=tint,
                    bump=lambda p: (fbm(p, 90.0, 2, 17) - 0.5) * 0.004)


def wax(color):
    return Material(color, rough=0.32, spec=0.7, sss=0.7, bump=lambda p: (fbm(p, 25.0, 3, 5) - 0.5) * 0.006)


def _bubbles(p, centre, radius, n, seed, fill_y):
    """Small bubbles sitting on the liquid surface inside the bottle (slightly raised, read as highlights)."""
    rng = np.random.default_rng(seed)
    d = np.full(len(p), 9.0, dtype=np.float32)
    for _ in range(n):
        a = rng.uniform(-1.2, 1.2)
        h = rng.uniform(-0.75, 0.55)
        y = centre[1] + h * radius
        if y > fill_y - 0.02:
            continue
        rr = radius * np.sqrt(max(0.0, 1 - h * h)) + 0.004
        c = v3(centre[0] + np.sin(a) * rr, y, centre[2] + np.cos(a) * rr)
        d = np.minimum(d, sd_sphere(p, c, rng.uniform(0.008, 0.020)))
    return d


def _split(body, p, fill_y, meniscus=0.012):
    """Splits a bottle body at the fill line: liquid below, glass shell above."""
    y = p[:, 1]
    liq = np.maximum(body, y - fill_y)
    gl = np.maximum(body, fill_y + meniscus - y)
    return liq, gl


def _neck(p, y0, y1, r):
    neck = sd_cyl_y(p, v3(0, (y0 + y1) / 2, 0), r, (y1 - y0) / 2, 0.01)
    lip = sd_torus(p, v3(0, y1 - 0.01, 0), r + 0.005, 0.026)
    return smin(neck, lip, 0.02)


def _string(p, y, r):
    """Twine wound around the neck with a little knot and hanging ends."""
    rings = np.minimum(sd_torus(p, v3(0, y, 0), r + 0.012, 0.012), sd_torus(p, v3(0, y - 0.03, 0), r + 0.012, 0.012))
    knot = sd_sphere(p, v3(r * 0.7, y - 0.015, r * 0.75), 0.024)
    ends = np.minimum(sd_capsule(p, v3(r * 0.7, y - 0.02, r * 0.78), v3(r * 1.00, y - 0.09, r * 1.00), 0.010),
                      sd_capsule(p, v3(r * 0.7, y - 0.02, r * 0.78), v3(r * 0.55, y - 0.10, r * 1.08), 0.010))
    return np.minimum(np.minimum(rings, knot), ends)


def _view():
    return rot_x(0.22) @ rot_y(-0.35)


# --------------------------------------------------------------------------- the five elixirs

def damage(_r="common"):
    """Wrath draught: round-bellied flask, deep red, cork with twine."""
    def scene(p):
        body = sd_sphere(p, v3(0, 0, 0), 0.44)
        body = smin(body, _neck(p, 0.30, 0.66, 0.11), 0.06)
        liq, gl = _split(body, p, 0.14)
        liq = np.minimum(liq, _bubbles(p, (0, 0, 0), 0.44, 14, 3, 0.14))
        crk = sd_cone_y(p, v3(0, 0.72, 0), 0.090, 0.112, 0.085, 0.02)
        return union([(liq, M_LIQ), (_glass(gl), M_GLASS), (crk, M_CORK), (_string(p, 0.53, 0.11), M_TIE)])
    mats = [liquid((0.82, 0.08, 0.05), 0.28), glass(), cork(), leather((0.55, 0.40, 0.24)), brass(), wax((0.6, 0.1, 0.1))]
    return scene, mats, 0.85, _view()


def xp(_r="common"):
    """Insight tonic: tall vial, bright blue, brass cap, small star flecks in the liquid."""
    def scene(p):
        body = sd_cyl_y(p, v3(0, -0.05, 0), 0.20, 0.48, 0.12)
        shoulder = sd_cone_y(p, v3(0, 0.50, 0), 0.20, 0.085, 0.10, 0.03)
        body = smin(smin(body, shoulder, 0.05), _neck(p, 0.55, 0.74, 0.085), 0.03)
        liq, gl = _split(body, p, 0.30)
        liq = np.minimum(liq, _bubbles(p, (0, -0.05, 0), 0.20, 10, 5, 0.30))
        cap = sd_cyl_y(p, v3(0, 0.80, 0), 0.105, 0.065, 0.02)
        cap = smin(cap, sd_sphere(p, v3(0, 0.88, 0), 0.05), 0.03)
        ring = sd_torus(p, v3(0, 0.22, 0), 0.205, 0.016)
        return union([(liq, M_LIQ), (_glass(gl), M_GLASS), (np.minimum(cap, ring), M_CAP)])
    mats = [liquid((0.10, 0.48, 0.95), 0.34, flecks=(0.85, 0.95, 1.0)), glass(), cork(), leather(), brass(), wax((0.2, 0.2, 0.6))]
    return scene, mats, 0.85, _view()


def gold(_r="common"):
    """Fortune elixir: squat, flat-sided flask with liquid gold full of glittering flecks, brass stopper."""
    def scene(p):
        body = sd_ellipsoid(p, v3(0, -0.02, 0), v3(0.46, 0.40, 0.26))
        body = smin(body, _neck(p, 0.28, 0.58, 0.10), 0.06)
        liq, gl = _split(body, p, 0.18)
        liq = np.minimum(liq, _bubbles(p, (0, -0.02, 0), 0.30, 10, 9, 0.18))
        stop = sd_cyl_y(p, v3(0, 0.62, 0), 0.085, 0.06, 0.015)
        stop = smin(stop, sd_ellipsoid(p, v3(0, 0.72, 0), v3(0.10, 0.07, 0.10)), 0.03)
        return union([(liq, M_LIQ), (_glass(gl), M_GLASS), (stop, M_CAP)])
    mats = [liquid((1.0, 0.68, 0.10), 0.30, flecks=(1.0, 0.95, 0.65)), glass(), cork(), leather(), brass(), wax((0.6, 0.4, 0.1))]
    return scene, mats, 0.85, _view()


def drop(_r="common"):
    """Seeker's essence: faceted octagonal bottle, violet, gem stopper."""
    def scene(p):
        q = fold_polar(p, 8)
        body = np.maximum(q[:, 0] - 0.30, np.abs(p[:, 1] + 0.02) - 0.40)
        body = smax(body, (q[:, 0] - 0.36) + (np.abs(p[:, 1] + 0.02) - 0.26) * 0.9, 0.02)  # bevelled shoulders
        body = smin(body, _neck(p, 0.30, 0.58, 0.085), 0.04)
        liq, gl = _split(body, p, 0.20)
        liq = np.minimum(liq, _bubbles(p, (0, -0.02, 0), 0.31, 10, 11, 0.20))
        collar = sd_cyl_y(p, v3(0, 0.62, 0), 0.075, 0.04, 0.015)
        gem = sd_gem(p, v3(0, 0.74, 0), 0.10)
        return union([(liq, M_LIQ), (_glass(gl), M_GLASS), (collar, M_CAP), (gem, M_SEAL)])
    mats = [liquid((0.55, 0.16, 0.90), 0.32), glass(), cork(), leather(), brass(), gem_mat("epic")]
    return scene, mats, 0.85, _view()


def regen(_r="common"):
    """Healing salve: round flask, green, cork sealed with red wax."""
    def scene(p):
        body = sd_ellipsoid(p, v3(0, -0.04, 0), v3(0.42, 0.40, 0.42))
        body = smin(body, _neck(p, 0.26, 0.58, 0.10), 0.06)
        liq, gl = _split(body, p, 0.12)
        liq = np.minimum(liq, _bubbles(p, (0, -0.04, 0), 0.42, 14, 13, 0.12))
        crk = sd_cone_y(p, v3(0, 0.63, 0), 0.085, 0.105, 0.06, 0.02)
        seal = smin(sd_ellipsoid(p, v3(0, 0.70, 0), v3(0.13, 0.05, 0.13)),
                    sd_ellipsoid(p, v3(0.05, 0.60, 0.09), v3(0.04, 0.07, 0.03)), 0.03)  # dripping wax
        return union([(liq, M_LIQ), (_glass(gl), M_GLASS), (crk, M_CORK), (seal, M_SEAL), (_string(p, 0.46, 0.10), M_TIE)])
    mats = [liquid((0.12, 0.72, 0.30), 0.30), glass(), cork(), leather((0.55, 0.40, 0.24)), brass(), wax((0.62, 0.08, 0.06))]
    return scene, mats, 0.85, _view()


BUILDERS = {
    "elixir_damage": damage,
    "elixir_xp": xp,
    "elixir_gold": gold,
    "elixir_drop": drop,
    "elixir_regen": regen,
}
