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
import items_weapons  # noqa: E402

BUILDERS = {}
for _m in (_B1, items_weapons.BUILDERS, items_armor.BUILDERS, items_jewelry.BUILDERS):
    BUILDERS.update(_m)
from sdf import Renderer, save_webp  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GEM_ITEMS = {"short_sword", "mace", "axe", "silver_ring", "gold_ring", "amulet", "talisman"}
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")


def render_one(job):
    name, rarity, size, out_dir, out_size = job
    t0 = time.time()
    scene, mats, bounds, view = BUILDERS[name](rarity or "common")
    rd = Renderer(scene, mats, bounds * 1.5, size=size, view_rot=view)
    rd.autofit()
    img = rd.render()
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
    with ProcessPoolExecutor(max_workers=int(os.environ.get("RENDER_WORKERS", "8"))) as ex:
        for path, dt, sz in ex.map(render_one, jobs):
            print(f"{os.path.basename(path)}  {dt:.1f}s  {sz/1024:.0f} KB")


if __name__ == "__main__":
    main()
