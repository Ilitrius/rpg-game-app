"""Renders monster sprites (tight alpha crop, no baked ground shadow, feet at the bottom edge).

Usage:  python render_monsters.py <name|all> [--size 1100] [--height 720] [--out DIR]
Output: `monster_<name>.webp` in --out (default: app/src/main/res/drawable-nodpi).
The app draws the sprite inside the 200-unit monster box (see MonsterSprites.kt), so only the
aspect ratio matters; the sprite is scaled to the box height.
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import monsters  # noqa: E402
from sdf import Renderer  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")


def render_one(job):
    name, size, height, out_dir = job
    t0 = time.time()
    scene, mats, bounds, view = monsters.BUILDERS[name]()
    rd = Renderer(scene, mats, bounds * 1.5, size=size, view_rot=view, ground_shadow=False,
                  light_dir=(-0.60, 0.72, 0.50), ambient_scale=0.50, key_scale=1.45, rim=1.0, gamma=1.75, scurve=0.55)
    rd.margin = 0.97
    rd.autofit()
    img = rd.render()
    a = img[..., 3] > 0.02
    ys, xs = np.nonzero(a)
    pad = 4
    box = (max(xs.min() - pad, 0), max(ys.min() - pad, 0), min(xs.max() + pad + 1, size), min(ys.max() + pad + 1, size))
    cropped = img[box[1]:box[3], box[0]:box[2]]
    # premultiplied resize (no dark fringes)
    pre = cropped.copy()
    pre[..., :3] *= pre[..., 3:4]
    h, w = cropped.shape[:2]
    new_h = height
    new_w = max(1, round(w * new_h / h))
    im = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((new_w, new_h), Image.LANCZOS)
    r = np.asarray(im, dtype=np.float32) / 255.0
    r[..., :3] = np.clip(r[..., :3] / np.maximum(r[..., 3:4], 1e-4), 0, 1)
    out = Image.fromarray(np.clip(r * 255, 0, 255).astype(np.uint8), "RGBA")
    path = os.path.join(out_dir, f"monster_{name}.webp")
    out.save(path, "WEBP", quality=90, method=6)
    return path, time.time() - t0, os.path.getsize(path), (new_w, new_h)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--size", type=int, default=1100)
    ap.add_argument("--height", type=int, default=720)
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    names = list(monsters.BUILDERS) if a.name == "all" else [a.name]
    jobs = [(n, a.size, a.height, a.out) for n in names]
    with ProcessPoolExecutor() as ex:
        for path, dt, sz, dim in ex.map(render_one, jobs):
            print(f"{os.path.basename(path)}  {dim[0]}x{dim[1]}  {dt:.1f}s  {sz/1024:.0f} KB")


if __name__ == "__main__":
    main()
