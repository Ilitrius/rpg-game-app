"""Renders the generated-zone body plans (monsters_gen.py) as colourable layer sprites.

Usage:  python render_gen.py <body|all> [--fear 0|1|both] [--size 1100] [--height 720] [--out DIR] [--preview]
Output: gen_<body>_<fear>_<skin|eye|glow|fixed>.webp (all four share one crop, feet at the bottom edge).
--preview additionally writes gen_<body>_<fear>_preview.png coloured with a sample palette.
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

import monsters_gen  # noqa: E402
from sdf import Renderer  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
LAYERS = ("skin", "eye", "glow", "fixed")


def _resize(rgba, w, h):
    pre = rgba.copy()
    pre[..., :3] *= pre[..., 3:4]
    im = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((w, h), Image.LANCZOS)
    r = np.asarray(im, dtype=np.float32) / 255.0
    r[..., :3] = np.clip(r[..., :3] / np.maximum(r[..., 3:4], 1e-4), 0, 1)
    return r


def render_one(job):
    body, fear, size, height, out_dir, preview = job
    t0 = time.time()
    scene, mats, roles, bounds, view = monsters_gen.BODIES[body](fear)
    rd = Renderer(scene, mats, bounds * 1.5, size=size, view_rot=view, ground_shadow=False,
                  light_dir=(-0.60, 0.72, 0.50), ambient_scale=0.50, key_scale=1.45, rim=1.0, gamma=1.75, scurve=0.55)
    rd.margin = 0.97
    rd.autofit()
    img = rd.render()
    mat = rd.mat_img
    a = img[..., 3] > 0.02
    ys, xs = np.nonzero(a)
    pad = 4
    x0, y0 = max(xs.min() - pad, 0), max(ys.min() - pad, 0)
    x1, y1 = min(xs.max() + pad + 1, size), min(ys.max() + pad + 1, size)
    img, mat = img[y0:y1, x0:x1], mat[y0:y1, x0:x1]
    h, w = img.shape[:2]
    new_h = height
    new_w = max(1, round(w * new_h / h))
    out = {}
    for layer in LAYERS:
        ids = [i for i, r in enumerate(roles) if r == layer]
        m = np.isin(mat, ids).astype(np.float32)
        lay = img.copy()
        lay[..., 3] = img[..., 3] * m
        if layer == "skin":
            lum = lay[..., 0] * 0.299 + lay[..., 1] * 0.587 + lay[..., 2] * 0.114
            lay[..., :3] = lum[..., None]
        elif layer in ("eye", "glow"):
            v = np.max(lay[..., :3], axis=-1)
            lay[..., :3] = np.clip(v, 0, 1)[..., None]
        r = _resize(lay, new_w, new_h)
        out[layer] = r
        path = os.path.join(out_dir, f"gen_{body}_{fear}_{layer}.webp")
        Image.fromarray(np.clip(r * 255, 0, 255).astype(np.uint8), "RGBA").save(path, "WEBP", quality=90, method=6)
    if preview:
        dark, light = np.array([0.10, 0.06, 0.14]), np.array([0.85, 0.55, 0.95])
        eye, acc = np.array([1.0, 0.9, 0.3]), np.array([0.3, 1.0, 0.85])
        bg = np.zeros((new_h, new_w, 3)) + np.array([0.28, 0.30, 0.34])
        def over(base, rgb, al):
            return base * (1 - al[..., None]) + rgb * al[..., None]
        s = out["skin"]
        col = dark + (light - dark) * s[..., :1]
        bg = over(bg, col, s[..., 3])
        bg = over(bg, out["fixed"][..., :3], out["fixed"][..., 3])
        bg = over(bg, out["eye"][..., :1] * eye, out["eye"][..., 3])
        bg = over(bg, out["glow"][..., :1] * acc, out["glow"][..., 3])
        Image.fromarray(np.clip(bg * 255, 0, 255).astype(np.uint8)).save(os.path.join(out_dir, f"gen_{body}_{fear}_preview.png"))
    sizes = sum(os.path.getsize(os.path.join(out_dir, f"gen_{body}_{fear}_{l}.webp")) for l in LAYERS)
    return f"gen_{body}_{fear}", time.time() - t0, sizes, (new_w, new_h)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("body")
    ap.add_argument("--fear", default="both")
    ap.add_argument("--size", type=int, default=1100)
    ap.add_argument("--height", type=int, default=720)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--preview", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    bodies = list(monsters_gen.BODIES) if a.body == "all" else [a.body]
    fears = [0, 1] if a.fear == "both" else [int(a.fear)]
    jobs = [(b, f, a.size, a.height, a.out, a.preview) for b in bodies for f in fears]
    with ProcessPoolExecutor() as ex:
        for name, dt, sz, dim in ex.map(render_one, jobs):
            print(f"{name}  {dim[0]}x{dim[1]}  {dt:.1f}s  {sz/1024:.0f} KB")


if __name__ == "__main__":
    main()
