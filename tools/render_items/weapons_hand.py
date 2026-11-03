"""First-person weapon-in-hand sprites: leather-gloved right hand + armored forearm holding a weapon.

The animation system (FirstPersonWeapon.kt) poses a "hand space" with the grip centre at the origin, the
weapon pointing to -y and the forearm towards +y. The sprite is rendered with the weapon upright; the
metadata (`weapon_held_meta.json`) maps sprite pixels to that hand space: grip anchor in pixels and hand
units per pixel (see HeldWeaponSprites.kt).

Usage:  python weapons_hand.py <kind|all> [--rarity common|all] [--size 1500] [--height 1100] [--out DIR]
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import items  # noqa: E402
import items_weapons  # noqa: E402
from items import GEMS, steel  # noqa: E402
from items import leather as leather_mat  # noqa: E402
from monsters import limb, rc, rot_local  # noqa: E402
from sdf import Material, Renderer, fbm, rot_x, rot_y, sd_box, sd_ellipsoid, sd_sphere, sd_torus, smin, v3  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

# kind: (builder name, hand-space top y, hand-space bottom y, model top y, model bottom y, mirror x).
# Hand space = the vector weapons' geometry (grip centre at origin, weapon to -y); model = our SDF weapon.
KINDS = {
    "dagger": ("dagger", -250.0, 68.0, 1.07, -0.69, False),
    "dagger_rusty": ("dagger_rusty", -250.0, 68.0, 1.07, -0.69, False),
    "short_sword": ("short_sword", -318.0, 74.0, 1.53, -0.96, False),
    "mace": ("mace", -323.0, 72.0, 1.42, -1.085, False),
    "axe": ("axe", -328.0, 54.0, 1.26, -1.0, True),
    "fist": ("fist", -60.0, 40.0, 0.30, -0.337, False),   # bare fist: knuckle top (hand y -60) and a point 100 units lower
}
GEM_KINDS = {"short_sword", "mace", "axe"}
VIEW = rot_x(-0.16) @ rot_y(-0.22)


def glove_material():
    def bump(p):
        return (fbm(p, 70.0, 3, 21) - 0.5) * 0.0028

    def tint(p, a):
        k = 0.80 + 0.40 * fbm(p, 6.0, 3, 5)
        wear = 0.88 + 0.24 * fbm(p, 18.0, 2, 9)
        return a * (k * wear)[:, None]

    return Material((0.21, 0.125, 0.065), rough=0.55, spec=0.42, bump=bump, tint_fn=tint, sss=0.12)


def _weapon(kind, rarity):
    name = KINDS[kind][0]
    fn = getattr(items, name, None) or getattr(items_weapons, name)
    scene, mats, _bounds, _view = fn(rarity)
    return scene, mats


def _merge(dw, mw, extras):
    d, m = dw, mw.astype(np.int16)
    for dd, mm in extras:
        sel = dd < d
        d = np.where(sel, dd, d)
        m = np.where(sel, mm, m)
    return d, m


def build_fist():
    """Clenched gloved fist (back of the hand towards the viewer, knuckle guard with steel studs) + bracer."""
    _name, h_top, h_bot, m_top, m_bot, _mirror = KINDS["fist"]
    s = (h_bot - h_top) / (m_top - m_bot)
    gc = m_bot + h_bot / s
    ox = -21.0 / s                                   # knuckle front sits at hand-space x = 21

    def scene(p):
        f = p + v3(ox, gc, 0)
        dorsum = sd_box(f, v3(0.02, -0.03, 0.0), v3(0.25, 0.27, 0.12), 0.09)
        for x in (-0.19, -0.065, 0.06, 0.18):
            dorsum = smin(dorsum, sd_sphere(f, v3(x, 0.255, 0.05), 0.088), 0.03)
        guard = sd_box(f, v3(0.0, 0.215, 0.085), v3(0.27, 0.075, 0.07), 0.04)
        thumb = limb(f, [(-0.30, -0.16, 0.05), (-0.22, 0.00, 0.15), (-0.02, 0.07, 0.19)], [0.068, 0.062, 0.052])
        wrist = rc(f, (0.05, -0.18, 0.0), (0.17, -0.44, 0.02), 0.21, 0.155)
        fist = smin(smin(smin(dorsum, guard, 0.04), thumb, 0.03), wrist, 0.07)
        studs = np.minimum.reduce([sd_sphere(f, v3(x, 0.30, 0.10), 0.024) for x in (-0.19, -0.065, 0.06, 0.18)])
        q = rot_local(f, (0.34, -0.80, 0.14), rz=0.52)
        plate = sd_box(q, v3(0, 0, 0), v3(0.10, 0.34, 0.022), 0.022)
        ribs = np.minimum(sd_box(q, v3(-0.045, 0, 0.026), v3(0.012, 0.31, 0.010), 0.008),
                          sd_box(q, v3(0.045, 0, 0.026), v3(0.012, 0.31, 0.010), 0.008))
        rivets = np.minimum.reduce([sd_sphere(q, v3(0, yy, 0.030), 0.016) for yy in (-0.27, -0.09, 0.09, 0.27)])
        cuff = sd_torus(rot_local(f, (0.13, -0.40, 0.01), rz=0.52), v3(0, 0, 0), 0.155, 0.04, axis="y")
        sleeve = limb(f, [(0.20, -0.52, 0.03), (0.70, -1.40, 0.07), (1.6, -2.8, 0.12)], [0.135, 0.15, 0.22])
        far = np.full(p.shape[0], 1e3, dtype=np.float32)
        return _merge(far, np.zeros(p.shape[0], dtype=np.int16), [
            (fist, 0), (np.minimum(cuff, sleeve), 1), (np.minimum(np.minimum(plate, ribs), np.minimum(rivets, studs)), 2),
        ])

    return scene, [glove_material(), leather_mat((0.14, 0.085, 0.05)), steel(0.30)], s, gc


def build(kind, rarity):
    _name, h_top, h_bot, m_top, m_bot, mirror = KINDS[kind]
    wscene, wmats = _weapon(kind, rarity)
    s = (h_bot - h_top) / (m_top - m_bot)                 # hand units per model unit
    gc = m_bot + h_bot / s                                # grip centre in model y
    nmat = len(wmats)

    def scene(p):
        pw = p + v3(0, gc, 0)
        if mirror:
            pw = pw * np.array([-1.0, 1.0, 1.0], dtype=np.float32)
        dw, mw = wscene(pw)
        # fingers: four wrapped rings, knuckles towards -x
        fingers = None
        for y in (0.135, 0.047, -0.041, -0.129):
            ring = smin(sd_torus(p, v3(0, y, 0.0), 0.094, 0.043, axis="y"), sd_sphere(p, v3(-0.098, y, 0.035), 0.050), 0.03)
            fingers = ring if fingers is None else np.minimum(fingers, ring)
        back = rc(p, (0.10, 0.0, -0.01), (0.34, -0.03, -0.01), 0.17, 0.15)
        back = smin(back, sd_ellipsoid(p, v3(0.20, 0.02, 0.0), v3(0.17, 0.22, 0.10)), 0.05)
        thumb = limb(p, [(0.13, 0.17, 0.06), (0.02, 0.215, 0.11), (-0.085, 0.185, 0.10)], [0.058, 0.050, 0.042])
        wrist = rc(p, (0.30, -0.06, -0.01), (0.60, -0.58, 0.03), 0.125, 0.135)
        hand = smin(smin(smin(fingers, back, 0.05), thumb, 0.03), wrist, 0.06)
        # armored forearm: steel bracer on the camera side, leather cuff and sleeve running off-screen
        q = rot_local(p, (0.55, -0.50, 0.15), rz=0.52)
        plate = sd_box(q, v3(0, 0, 0), v3(0.10, 0.34, 0.022), 0.022)
        ribs = np.minimum(sd_box(q, v3(-0.045, 0, 0.026), v3(0.012, 0.31, 0.010), 0.008),
                          sd_box(q, v3(0.045, 0, 0.026), v3(0.012, 0.31, 0.010), 0.008))
        rivets = np.minimum.reduce([sd_sphere(q, v3(0, yy, 0.030), 0.016) for yy in (-0.27, -0.09, 0.09, 0.27)])
        cuff = sd_torus(rot_local(p, (0.385, -0.235, 0.015), rz=0.52), v3(0, 0, 0), 0.122, 0.034, axis="y")
        sleeve = limb(p, [(0.56, -0.52, 0.03), (0.95, -1.18, 0.07), (1.7, -2.5, 0.12)], [0.118, 0.145, 0.22])
        return _merge(dw, mw, [
            (hand, nmat), (np.minimum(cuff, sleeve), nmat + 1), (np.minimum(np.minimum(plate, ribs), rivets), nmat + 2),
        ])

    mats = list(wmats) + [glove_material(), leather_mat((0.14, 0.085, 0.05)), steel(0.30)]
    return scene, mats, s, gc


def render_one(job):
    kind, rarity, size, out_dir, final_h = job
    t0 = time.time()
    _name, _ht, _hb, m_top, m_bot, _mirror = KINDS[kind]
    scene, mats, s, gc = build_fist() if kind == "fist" else build(kind, rarity)
    rd = Renderer(scene, mats, 2.6, size=size, view_rot=VIEW, ground_shadow=False, light_dir=(-0.45, 0.70, 0.55),
                  ambient_scale=0.60, key_scale=1.35, rim=0.8, gamma=1.75, scurve=0.5)
    rd.margin = 0.97
    ref = os.path.join(out_dir, f".view_{kind}.json")
    if os.path.exists(ref):                      # all rarities of a kind share one framing (same anchors)
        v = json.load(open(ref))
        rd.bounds, rd.center = v["bounds"], np.array(v["center"], dtype=np.float32)
    else:
        rd.autofit(probe=160, passes=3)
        json.dump({"bounds": float(rd.bounds), "center": [float(rd.center[0]), float(rd.center[1])]}, open(ref, "w"))
    img = rd.render()
    S = size
    b, cx, cy = rd.bounds, float(rd.center[0]), float(rd.center[1])

    def px(model_pt):
        cam = VIEW @ np.array(model_pt, dtype=np.float32)
        return ((cam[0] - cx) / b + 1) / 2 * S - 0.5, (1 - (cam[1] - cy) / b) / 2 * S - 0.5

    gx, gy = px((0.0, 0.0, 0.0))                 # grip centre = scene origin
    _tx, ty = px((0.0, m_top - gc, 0.0))
    _bx, by = px((0.0, m_bot - gc, 0.0))
    ys, xs = np.nonzero(img[..., 3] > 0.02)
    pad = 6
    x0, y0 = max(xs.min() - pad, 0), max(ys.min() - pad, 0)
    x1, y1 = min(xs.max() + pad + 1, S), min(ys.max() + pad + 1, S)
    crop = img[y0:y1, x0:x1]
    scale = final_h / S
    pre = crop.copy()
    pre[..., :3] *= pre[..., 3:4]
    new_w, new_h = max(1, round(crop.shape[1] * scale)), max(1, round(crop.shape[0] * scale))
    im = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((new_w, new_h), Image.LANCZOS)
    r = np.asarray(im, dtype=np.float32) / 255.0
    r[..., :3] = np.clip(r[..., :3] / np.maximum(r[..., 3:4], 1e-4), 0, 1)
    out = Image.fromarray(np.clip(r * 255, 0, 255).astype(np.uint8), "RGBA")
    name = f"weapon_held_{kind}_{rarity}.webp" if kind in GEM_KINDS else f"weapon_held_{kind}.webp"
    path = os.path.join(out_dir, name)
    out.save(path, "WEBP", quality=88, method=6)
    px_per_model_unit = abs(by - ty) * scale / abs(m_top - m_bot)      # output px per model unit
    meta = {
        "kind": kind, "file": name, "w": new_w, "h": new_h,
        "gripX": float((gx - x0) * scale), "gripY": float((gy - y0) * scale),
        "handUnitsPerPx": float(s / px_per_model_unit),
    }
    return path, time.time() - t0, os.path.getsize(path), meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("kind")
    ap.add_argument("--rarity", default="common")
    ap.add_argument("--size", type=int, default=1500)
    ap.add_argument("--height", type=int, default=1100, help="px size of the framed render square after downscaling")
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    kinds = list(KINDS) if a.kind == "all" else [a.kind]
    rarities = list(GEMS) if a.rarity == "all" else [a.rarity]
    jobs = []
    for k in kinds:
        jobs += [(k, r, a.size, a.out, a.height) for r in (rarities if k in GEM_KINDS else ["common"])]
    first = {}
    for j in jobs:                               # the first job of a kind writes the shared framing
        first.setdefault(j[0], j)
    metas = []
    with ProcessPoolExecutor(max_workers=int(os.environ.get("RENDER_WORKERS", "8"))) as ex:
        for path, dt, sz, meta in ex.map(render_one, list(first.values())):
            metas.append(meta)
            print(f"{os.path.basename(path)}  {dt:.0f}s  {sz/1024:.0f} KB")
        rest = [j for j in jobs if j not in first.values()]
        for path, dt, sz, meta in ex.map(render_one, rest):
            print(f"{os.path.basename(path)}  {dt:.0f}s  {sz/1024:.0f} KB")
    with open(os.path.join(a.out, "weapon_held_meta.json"), "w") as f:
        json.dump(metas, f, indent=1)
    for m in metas:
        print(m)


if __name__ == "__main__":
    main()
