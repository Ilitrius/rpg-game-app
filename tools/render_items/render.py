"""Renders item sprites with the SDF renderer.

Usage:  python render.py <name|all> [--rarity common] [--size 640] [--out DIR] [--preview]
Sprites are written as WebP (RGBA, 512 px) into --out (default: app/src/main/res/drawable-nodpi).
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

from items import BUILDERS as _B1, GEMS  # noqa: E402
import items_armor  # noqa: E402
import items_jewelry  # noqa: E402
import items_potions  # noqa: E402
import items_armor2  # noqa: E402
import items_gloves  # noqa: E402
import badges  # noqa: E402
import items_weapons  # noqa: E402

BUILDERS = {}
for _m in (_B1, items_weapons.BUILDERS, items_armor.BUILDERS, items_jewelry.BUILDERS, items_potions.BUILDERS, items_armor2.BUILDERS, items_gloves.BUILDERS, badges.BUILDERS):
    BUILDERS.update(_m)
from sdf import Renderer, save_webp  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GEM_ITEMS = {"short_sword", "mace", "axe", "silver_ring", "gold_ring", "amulet", "talisman"}
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")


GLINT = {"rare": 0.07, "epic": 0.095, "legendary": 0.13}  # star size as a fraction of the image


def add_glint(img, mask, size_frac):
    """Adds a soft four-point star (plus a smaller diagonal cross) on the brightest pixel of the gem."""
    import numpy as np
    if not mask.any():
        return img
    S = img.shape[0]
    lum = img[..., :3].sum(-1) * mask
    cy, cx = np.unravel_index(np.argmax(lum), lum.shape)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    dx, dy = (xx - cx) / S, (yy - cy) / S
    L = size_frac
    w = 0.0035
    star = (np.exp(-(dy / w) ** 2) * np.exp(-np.abs(dx) / (L * 0.35))
            + np.exp(-(dx / w) ** 2) * np.exp(-np.abs(dy) / (L * 0.35)))
    u, v = (dx + dy) * 0.7071, (dx - dy) * 0.7071
    star = star + 0.5 * (np.exp(-(v / w) ** 2) * np.exp(-np.abs(u) / (L * 0.16))
                         + np.exp(-(u / w) ** 2) * np.exp(-np.abs(v) / (L * 0.16)))
    star = star + 1.2 * np.exp(-(dx * dx + dy * dy) / (L * 0.09) ** 2)
    star = np.clip(star, 0, 1.6)[..., None]
    out = img.copy()
    a = out[..., 3:4]
    rgb = out[..., :3] * a + star * np.array([1.0, 0.98, 0.92], dtype=np.float32)
    na = np.clip(np.maximum(a, np.clip(star, 0, 1)), 1e-4, 1)
    out[..., :3] = np.clip(rgb / na, 0, 1)
    out[..., 3:4] = na
    return out


def render_one(job):
    name, rarity, size, out_dir, out_size = job
    t0 = time.time()
    scene, mats, bounds, view = BUILDERS[name](rarity or "common")
    rd = Renderer(scene, mats, bounds * 1.5, size=size, view_rot=view)
    rd.autofit()
    img = rd.render()
    if rarity in GLINT:
        gems = [i for i, m in enumerate(mats) if m.emissive >= 0.2]
        if gems:
            import numpy as np
            m = np.isin(rd.mat_img, gems)
            img = add_glint(img, m, GLINT[rarity])
    if name.startswith("elixir_"):
        img = items_potions.see_through_glass(img, rd)
    path = os.path.join(out_dir, f"item_{name}_{rarity}.webp" if rarity else f"item_{name}.webp")
    save_webp(img, path, out_size=out_size)
    return path, time.time() - t0, os.path.getsize(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--rarity", default="common")
    ap.add_argument("--size", type=int, default=768)
    ap.add_argument("--out-size", type=int, default=512)
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    names = list(BUILDERS) if a.name == "all" else [a.name]
    rarities = list(GEMS) if a.rarity == "all" else [a.rarity]
    jobs = []
    for n in names:
        if n in GEM_ITEMS:
            jobs += [(n, r, a.size, a.out, a.out_size) for r in rarities]
        else:
            jobs.append((n, "", a.size, a.out, a.out_size))
    with ProcessPoolExecutor() as ex:
        for path, dt, sz in ex.map(render_one, jobs):
            print(f"{os.path.basename(path)}  {dt:.1f}s  {sz/1024:.0f} KB")


if __name__ == "__main__":
    main()
