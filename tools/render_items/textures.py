"""Photo textures (CC0, ambientCG) for the offline monster renderer.

The texture files are NOT part of the repository (about 50 MB); download them once with
`python tools/render_items/fetch_textures.py` into `tools/render_items/textures/<Name>/` (CC0, no attribution needed).
Only the rendered WebP sprites end up in the app.

Mapping is planar on the model's x/y plane with a little z shear (the renderer looks along -z, so most visible
surface faces the camera). `tile` = model units covered by one texture repeat.
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image

TEX_DIR = os.path.join(os.path.dirname(__file__), "textures")
_cache: dict = {}


def _load(name: str, kind: str, size: int):
    key = (name, kind, size)
    if key in _cache:
        return _cache[key]
    folder = os.path.join(TEX_DIR, name)
    cand = [f for f in os.listdir(folder) if f.endswith(f"_{kind}.jpg")]
    if not cand:
        raise FileNotFoundError(f"{name}: no {kind} map in {folder}")
    im = Image.open(os.path.join(folder, cand[0])).convert("RGB").resize((size, size), Image.LANCZOS)
    arr = np.asarray(im, dtype=np.float32) / 255.0
    _cache[key] = arr
    return arr


def _sample(arr, u, v):
    """Bilinear lookup with wrap-around; u, v in texture repeats."""
    h, w = arr.shape[:2]
    x = (u % 1.0) * w
    y = (v % 1.0) * h
    x0 = np.floor(x).astype(np.int32); y0 = np.floor(y).astype(np.int32)
    fx = (x - x0)[:, None]; fy = (y - y0)[:, None]
    x0 %= w; y0 %= h
    x1 = (x0 + 1) % w; y1 = (y0 + 1) % h
    a = arr[y0, x0] * (1 - fx) + arr[y0, x1] * fx
    b = arr[y1, x0] * (1 - fx) + arr[y1, x1] * fx
    return a * (1 - fy) + b * fy


class Tex:
    def __init__(self, name: str, tile: float = 1.0, size: int = 1024, shear: float = 0.45, offset=(0.0, 0.0)):
        self.name, self.tile, self.size, self.shear, self.offset = name, tile, size, shear, offset

    def uv(self, p):
        return ((p[:, 0] + self.shear * p[:, 2]) / self.tile + self.offset[0],
                (p[:, 1] + 0.5 * self.shear * p[:, 2]) / self.tile + self.offset[1])

    def color(self, p):
        u, v = self.uv(p)
        return _sample(_load(self.name, "Color", 512), u, v)

    def height(self, p):
        u, v = self.uv(p)
        return _sample(_load(self.name, "Displacement", 192), u, v)[:, 0]   # low-pass: no aliasing in the bump

    def rough(self, p):
        u, v = self.uv(p)
        return _sample(_load(self.name, "Roughness", self.size), u, v)[:, 0]


def tint_fn(tex: Tex, mode: str = "color", gain: float = 1.0, mix: float = 1.0, gamma: float = 1.0, extra=None):
    """Albedo from the photo texture.
    mode 'color': photo colour (x material albedo as a tint, albedo ~ (1,1,1) keeps the photo as is);
    mode 'lum'  : photo luminance (detail only) x material albedo (re-colours the photo, e.g. green scales).
    `mix` blends towards the plain material albedo, `extra(p, a)` can post-process."""
    def f(p, a):
        c = tex.color(p)
        if gamma != 1.0:
            c = np.power(np.clip(c, 0, 1), gamma)
        if mode == "lum":
            lum = (c @ np.array([0.299, 0.587, 0.114], dtype=np.float32))[:, None]
            out = a * np.clip(lum * 2.0 * gain, 0, 2.5)
        else:
            out = c * a * gain
        out = a * (1 - mix) + out * mix
        return extra(p, out) if extra is not None else out
    return f


def bump_fn(tex: Tex, amp: float = 0.02, fine=None):
    """Height field (model units) from the displacement map; `fine` = optional extra callable(p)->height."""
    def f(p):
        h = (tex.height(p) - 0.5) * amp
        return h + fine(p) if fine is not None else h
    return f
