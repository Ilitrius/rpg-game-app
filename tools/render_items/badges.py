"""Hero rank badges (ranks 0..15) for the Charakter tab: a heater shield in the metal of the rank tier with an
enamel field, raised chevrons (rank 1-5), stars (rank 6-10) and gems (Mythos, rank 11-15).
Builders are registered as rank_badge_<n> (render.py -> item_rank_badge_<n>.webp)."""
from __future__ import annotations

import numpy as np

from items import gem_mat, sd_gem
from sdf import Material, fbm, rot_x, rot_y, union, v3

SHIELD = [(-0.80, 0.85), (0.80, 0.85), (0.84, 0.15), (0.60, -0.45), (0.0, -1.0), (-0.60, -0.45), (-0.84, 0.15)]


def sd_poly(px, py, verts):
    """Signed distance of points (px, py) to the polygon (Inigo Quilez' polygon sdf, vectorised)."""
    n = len(verts)
    d = (px - verts[0][0]) ** 2 + (py - verts[0][1]) ** 2
    s = np.ones_like(px)
    j = n - 1
    for i in range(n):
        vi, vj = verts[i], verts[j]
        ex, ey = vj[0] - vi[0], vj[1] - vi[1]
        wx, wy = px - vi[0], py - vi[1]
        t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0.0, 1.0)
        bx, by = wx - ex * t, wy - ey * t
        d = np.minimum(d, bx * bx + by * by)
        c1, c2, c3 = py >= vi[1], py < vj[1], ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
        j = i
    return s * np.sqrt(d)


def sd_seg2(px, py, a, b):
    ex, ey = b[0] - a[0], b[1] - a[1]
    wx, wy = px - a[0], py - a[1]
    t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0.0, 1.0)
    return np.hypot(wx - ex * t, wy - ey * t)


def extrude(d2, z, half, rnd=0.012):
    """3D distance of a 2D shape distance d2 extruded to |z| <= half (rounded edge)."""
    w0 = d2 + rnd
    w1 = np.abs(z) - half + rnd
    return np.minimum(np.maximum(w0, w1), 0.0) + np.hypot(np.maximum(w0, 0.0), np.maximum(w1, 0.0)) - rnd


def star2(px, py, c, r):
    verts = []
    for i in range(10):
        ang = np.pi / 2 + i * np.pi / 5
        rad = r if i % 2 == 0 else r * 0.45
        verts.append((c[0] + np.cos(ang) * rad, c[1] + np.sin(ang) * rad))
    return sd_poly(px, py, verts)


def chevron2(px, py, y, half_w=0.40, drop=0.16, width=0.045):
    a, b, c = (-half_w, y - drop), (0.0, y), (half_w, y - drop)
    return np.minimum(sd_seg2(px, py, a, b), sd_seg2(px, py, b, c)) - width


GEM_SPOTS = [(0.0, -0.78), (-0.69, 0.70), (0.69, 0.70), (-0.74, -0.06), (0.74, -0.06)]


def metal(color, rough=0.30):
    """Polished metal with large soft tone variation and a very fine brushed grain."""
    col = np.array(color, dtype=np.float32)
    return Material(col, metal=1.0, rough=rough, spec=1.0,
                    tint_fn=lambda p, a: a * (0.72 + 0.46 * fbm(p, 3.0, 3, 71))[:, None],
                    bump=lambda p: (fbm(p * np.array([1.0, 0.3, 1.0], dtype=np.float32), 200.0, 2, 73) - 0.5) * 0.0006)


def tier(rank):
    """(frame metal, field enamel colour) of a rank."""
    if rank <= 0:
        return metal((0.22, 0.22, 0.25), 0.50), (0.09, 0.09, 0.11)
    if rank <= 3:
        return metal((0.55, 0.30, 0.12), 0.32), (0.30, 0.05, 0.04)
    if rank <= 6:
        return metal((0.78, 0.80, 0.84), 0.24), (0.04, 0.10, 0.30)
    if rank <= 9:
        return metal((0.85, 0.62, 0.18), 0.28), (0.04, 0.22, 0.12)
    if rank == 10:
        return metal((0.85, 0.62, 0.18), 0.28), (0.18, 0.03, 0.26)
    return metal((0.45, 0.32, 0.80), 0.26), (0.07, 0.03, 0.18)


def enamel(color):
    col = np.array(color, dtype=np.float32)
    return Material(col, metal=0.0, rough=0.28, spec=0.9,
                    tint_fn=lambda p, a: a * (0.82 + 0.30 * fbm(p, 6.0, 3, 91))[:, None],
                    bump=lambda p: (fbm(p, 60.0, 2, 93) - 0.5) * 0.0015)


def badge(rank):
    frame, field = tier(rank)
    M_FRAME, M_FIELD, M_ORN, M_GEM = range(4)

    def scene(p):
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        outline = sd_poly(x, y, SHIELD) - 0.05
        plate = extrude(outline, z, 0.07)
        # enamel field inside, a raised rim (frame metal) around it
        inner = outline + 0.13
        field_d = extrude(inner, z - 0.002, 0.082)
        rim = np.maximum(extrude(outline, z, 0.098), -(inner + 0.02))
        parts = [(plate, M_FRAME), (field_d, M_FIELD), (rim, M_FRAME)]
        if rank >= 1:
            n_chev = min(rank, 5)
            y0 = 0.32 if rank <= 5 else 0.24
            ch = None
            for i in range(n_chev):
                c = extrude(chevron2(x, y, y0 - 0.125 * i), z - 0.02, 0.10)
                ch = c if ch is None else np.minimum(ch, c)
            ornaments = ch
            if rank >= 6:
                stars = min(rank - 5, 5)
                for i in range(stars):
                    cx = (i - (stars - 1) / 2.0) * 0.27
                    ornaments = np.minimum(ornaments, extrude(star2(x, y, (cx, 0.60), 0.115), z - 0.02, 0.10))
            parts.append((ornaments, M_ORN))
        if rank >= 11:
            pr = np.stack([x, z, -y], axis=-1)  # gem axis y of sd_gem -> out of the shield (+z)
            gems = None
            for k in range(min(rank - 10, 5)):
                gx, gy = GEM_SPOTS[k]
                g = sd_gem(pr, v3(gx, 0.13, -gy), 0.125)
                gems = g if gems is None else np.minimum(gems, g)
            parts.append((gems, M_GEM))
        d, m = union(parts)
        return d, m

    gem_rarity = "rare" if rank <= 12 else ("epic" if rank <= 14 else "legendary")
    mats = [frame, enamel(field), frame, gem_mat(gem_rarity)]
    view = rot_x(0.10) @ rot_y(-0.20)
    return scene, mats, 1.15, view


def _make(n):
    def builder(rarity="common"):
        return badge(n)
    return builder


BUILDERS = {f"rank_badge_{n}": _make(n) for n in range(0, 16)}
