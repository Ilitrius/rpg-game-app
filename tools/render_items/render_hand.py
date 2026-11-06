"""First-person arm + held weapons for the adventure view (replaces the vector hand of ui/character/weapon).

All sprites live in the app's hand space: units of the 2D weapon art, origin = grip centre, weapon pointing up,
forearm towards the bottom right. Every sprite is rendered at the same scale (PPU pixels per unit), so the app
only needs the pixel position of the grip centre (written to hand_sprites.json and FirstPersonSprites.kt).

    hand_back.webp   sleeve, vambrace, glove cuff, back of the hand (behind the weapon)
    hand_front.webp  curled fingers + thumb (in front of the grip)
    hand_fist.webp   bare fist with forearm (no weapon)
    fp_<weapon>[_<rarity>].webp  the weapon itself, grip centre at the origin

Usage:  python render_hand.py [all|hand|fist|weapons|cards|fist_card|<weapon>] [--ppu 2.4] [--out DIR] [--preview]
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import items  # noqa: E402
import items_weapons  # noqa: E402
from monsters import ell, limb, rc  # noqa: E402
from sdf import Material, Renderer, fbm, rot_x, rot_y, sd_sphere, sd_torus, smax, smin, union, v3, value_noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
U = 0.01  # model units per hand-space unit (the renderer works in model units ~ 1)

RARITIES = ("common", "uncommon", "rare", "epic", "legendary")

# weapon id -> (builder, grip centre y (item model), item model units -> hand units, mirror x, render centre y, half extent)
WEAPONS = {
    "dagger": (items_weapons.dagger, -0.31, 181.0, False, 0.70, 1.95),
    "dagger_rusty": (items_weapons.dagger_rusty, -0.31, 181.0, False, 0.70, 1.95),
    "short_sword": (items.short_sword, -0.44, 160.0, False, 1.17, 2.10),
    "mace": (items_weapons.mace, -0.465, 171.0, False, 1.08, 2.25),
    "axe": (items_weapons.axe, -0.51, 185.0, True, 1.18, 2.20),
}
GEMMED = {"short_sword", "mace", "axe"}


# --------------------------------------------------------------------------- materials

def glove():
    return Material((0.27, 0.15, 0.08), rough=0.50, spec=0.55, sss=0.10,
                    tint_fn=lambda p, a: a * (0.62 + 0.60 * fbm(p, 9.0, 4, 5))[:, None],
                    bump=lambda p: (fbm(p, 70.0, 3, 21) - 0.5) * 0.006 + (fbm(p, 18.0, 3, 4) - 0.5) * 0.005)


def leather_dark():
    return Material((0.16, 0.09, 0.05), rough=0.60, spec=0.35,
                    tint_fn=lambda p, a: a * (0.70 + 0.45 * fbm(p, 7.0, 4, 6))[:, None],
                    bump=lambda p: (fbm(p, 60.0, 3, 22) - 0.5) * 0.004)


def plate():
    return Material((0.34, 0.35, 0.38), metal=1.0, rough=0.66, spec=0.5,
                    bump=lambda p: (value_noise(p, 90.0, 4) - 0.5) * 0.0012 + (fbm(p, 20.0, 3, 9) - 0.5) * 0.0015,
                    tint_fn=lambda p, a: a * (0.62 + 0.50 * fbm(p, 12.0, 4, 2))[:, None])


def cloth():
    st = np.array([3.0, 0.4, 3.0], dtype=np.float32)
    return Material((0.26, 0.07, 0.09), rough=0.85, spec=0.08, sss=0.4,
                    tint_fn=lambda p, a: a * (0.60 + 0.55 * fbm(p * st, 4.0, 4, 7))[:, None],
                    bump=lambda p: (value_noise(p * np.array([60.0, 60.0, 60.0], dtype=np.float32), 1.0, 3) - 0.5) * 0.002
                    + (fbm(p * st, 6.0, 3, 3) - 0.5) * 0.01)


def stitch_brass():
    return Material((1.0, 0.78, 0.40), metal=1.0, rough=0.3, spec=1.0)


# --------------------------------------------------------------------------- arm (hand units, origin grip centre, y up)

def forearm(q):
    """Sleeve, vambrace with straps + steel plate, glove cuff. Returns (cloth, leather, strap, steel, rivets, cuff)."""
    # forearm axis from the wrist down towards the elbow (down/right, widening: close to the camera)
    wrist, elbow = np.array([40.0, -78.0, -8.0]), np.array([92.0, -620.0, -40.0])
    d = elbow - wrist
    L = np.linalg.norm(d)
    dn = d / L
    rel = q - wrist.astype(np.float32)
    t = np.clip(rel @ dn.astype(np.float32), -50.0, L + 50.0) / L
    radial = np.linalg.norm(rel - (t * L)[:, None] * dn.astype(np.float32), axis=-1)
    r_arm = 36.0 + 70.0 * t
    sleeve_start = 0.29
    # vambrace (leather, slightly bigger than the arm) on 0.0..sleeve_start
    vam = np.maximum(radial - (r_arm + 4.0), np.maximum(-t * L + 4.0, (t - sleeve_start) * L))
    # sleeve: cloth, wider, with folds
    folds = 6.0 * np.sin(np.arctan2(rel[:, 2], rel[:, 0]) * 7.0 + t * 9.0) + 8.0 * (fbm(q * 0.01, 3.0, 3, 5) - 0.5)
    slv = np.maximum(radial - (r_arm + 14.0 + folds * 0.6), (sleeve_start - 0.02 - t) * L)
    # straps: two rings around the vambrace
    straps = None
    for ts in (0.09, 0.20):
        s = np.maximum(radial - (r_arm + 6.5), np.abs(t - ts) * L - 6.0)
        straps = s if straps is None else np.minimum(straps, s)
    # steel plate on the top (towards the viewer, +z) of the vambrace
    pl = np.maximum(np.maximum(radial - (r_arm + 8.5), np.abs(t - 0.145) * L - 64.0), -(rel[:, 2] + 4.0 - 0.0) + 0.0)
    xperp = rel[:, 0] - (t * L) * dn[0]
    pl = np.maximum(pl, np.abs(xperp - 4.0) - (18.0 + 14.0 * t))
    rivets = None
    for ts in (0.06, 0.14, 0.23):
        for ang in (-0.55, 0.55):
            c = wrist + dn * ts * L
            perp = np.array([np.cos(ang + 1.0), 0.0, 0.0])
            pos = c + np.array([np.sin(ang) * (36 + 70 * ts + 9.0), 0.0, np.cos(ang) * (36 + 70 * ts + 9.0)])
            r = sd_sphere(q, pos.astype(np.float32), 3.2)
            rivets = r if rivets is None else np.minimum(rivets, r)
    # flared glove cuff at the wrist
    tcuff = (rel @ dn.astype(np.float32))
    cuff = np.maximum(radial - (40.0 + 0.25 * np.clip(-tcuff + 30, 0, 60)), np.abs(tcuff + 6.0) - 26.0)
    return slv, vam, straps, pl, rivets, cuff


def hand_parts(q, fist=False):
    """Back of the hand + palm, four curled fingers, thumb (hand units)."""
    gr = 6.0 if fist else 8.6                     # radius the fingers wrap around (grip or nothing)
    ry = 46.0 if fist else 0.0                    # fist: the whole hand sits higher (knuckles up)
    palm = ell(q, (40.0, -6.0 + ry, -12.0), (42.0, 52.0, 22.0), rz=0.22)
    palm = smin(palm, ell(q, (50.0, -48.0 + ry, -12.0), (36.0, 26.0, 22.0)), 12.0)          # heel of the hand
    wrist = limb(q, [(44.0, -86.0, -10.0), (46.0, -50.0 + ry * 0.5, -12.0), (44.0, -24.0 + ry, -12.0)], [30.0, 31.0, 32.0])
    palm = smin(palm, wrist, 14.0)
    knuck = ell(q, (16.0, 24.0 + ry, -8.0), (16.0, 12.0, 14.0)) if fist else None
    fingers = None
    for i in range(4):
        y = 24.0 - 17.5 * i + ry
        rr = gr + 10.5
        rad = (10.6, 10.4, 10.0, 9.0)[i]
        pts = []
        for a in (-40.0, 15.0, 70.0, 125.0, 175.0, 205.0):
            ar = np.radians(a)
            pts.append((rr * np.cos(ar) + 4.0, y - 0.04 * a, rr * np.sin(ar)))
        if fist:
            pts = [(x * 0.95 + 14.0, yy, z) for (x, yy, z) in pts]
        f = limb(q, pts, [rad, rad, rad * 0.97, rad * 0.94, rad * 0.9, rad * 0.85])
        fingers = f if fingers is None else smin(fingers, f, 2.0)
    # knuckle bumps on the back side
    kn = None
    for i in range(4):
        y = 24.0 - 17.5 * i + ry
        k = sd_sphere(q, v3(gr + 16.0 + (12.0 if fist else 0.0), y, -6.0), 10.5)
        kn = k if kn is None else np.minimum(kn, k)
    thumb = limb(q, [(62.0, -4.0 + ry, 0.0), (44.0, 22.0 + ry, 14.0), (18.0, 36.0 + ry, gr + 11.0), (-6.0, 34.0 + ry, gr + 10.0)],
                 [15.0, 12.5, 10.5, 9.0])
    back = smin(palm, kn, 6.0)
    if knuck is not None:
        back = smin(back, knuck, 8.0)
    # leather glove seams
    return back, fingers, thumb


def arm_scene(fist=False, front=True, back=True, grip_proxy=True):
    M_GLOVE, M_LEATHER, M_STRAP, M_PLATE, M_RIVET, M_CLOTH, M_GRIP, M_FINGER = range(8)

    def scene(p):
        q = p / U
        slv, vam, straps, pl, rivets, cuff = forearm(q)
        bk, fingers, thumb = hand_parts(q, fist)
        hand_back = smin(bk, cuff, 8.0)
        parts = [(hand_back, M_GLOVE), (vam, M_LEATHER), (straps, M_STRAP), (pl, M_PLATE), (rivets, M_RIVET), (slv, M_CLOTH),
                 (smin(fingers, thumb, 2.0), M_FINGER)]
        if grip_proxy and not fist:
            grip = np.maximum(np.hypot(q[:, 0], q[:, 2]) - 8.0, np.abs(q[:, 1]) - 46.0)
            parts.append((grip, M_GRIP))
        d, m = union(parts)
        return d * U, m

    mats = [glove(), leather_dark(), leather_dark(), plate(), stitch_brass(), cloth(),
            Material((0.2, 0.15, 0.1), rough=0.6, spec=0.3), glove()]
    return scene, mats


# --------------------------------------------------------------------------- render helpers

def _render(scene, mats, center, half, ppu, view):
    size = int(round(2 * half / U * ppu))
    rd = Renderer(scene, mats, half, size=size, view_rot=view, ground_shadow=False,
                  light_dir=(-0.55, 0.70, 0.62), ambient_scale=0.62, key_scale=1.35, rim=0.7, gamma=1.8, scurve=0.45)
    rd.center = np.array(center, dtype=np.float32)
    img = rd.render()
    return img, rd.mat_img, size


def _grip_px(center, half, size):
    """Pixel position of the hand-space origin (grip centre)."""
    px = (0.0 - (center[0] - half)) / (2 * half) * size
    py = ((center[1] + half) - 0.0) / (2 * half) * size
    return px, py


def _save(img, path, keep=None, mat=None):
    out = img.copy()
    if keep is not None:
        out[..., 3] = out[..., 3] * np.isin(mat, keep)
    a = out[..., 3] > 0.01
    ys, xs = np.nonzero(a)
    x0, y0, x1, y1 = max(xs.min() - 2, 0), max(ys.min() - 2, 0), xs.max() + 3, ys.max() + 3
    crop = out[y0:y1, x0:x1]
    Image.fromarray(np.clip(crop * 255, 0, 255).astype(np.uint8), "RGBA").save(path, "WEBP", quality=90, method=6)
    return x0, y0


ARM_VIEW = rot_x(-0.10) @ rot_y(-0.12)


def job_hand(args):
    out, ppu = args
    scene, mats = arm_scene(fist=False)
    center, half = (0.62, -2.55), 3.45
    img, mat, size = _render(scene, mats, center, half, ppu, ARM_VIEW)
    gx, gy = _grip_px(center, half, size)
    res = {}
    # back layer: arm + back of the hand; front layer: the visible parts of fingers + thumb (the grip proxy hides
    # whatever wraps behind the grip, the real weapon sprite is drawn in between)
    fingers = mat == 7
    back_keep = np.isin(mat, [0, 1, 2, 3, 4, 5])
    b = img.copy(); b[..., 3] *= back_keep
    f = img.copy(); f[..., 3] *= fingers
    x0, y0 = _save(b, os.path.join(out, "hand_back.webp"))
    res["hand_back"] = (gx - x0, gy - y0)
    x0, y0 = _save(f, os.path.join(out, "hand_front.webp"))
    res["hand_front"] = (gx - x0, gy - y0)
    return res


def job_fist(args):
    out, ppu = args
    scene, mats = arm_scene(fist=True)
    center, half = (0.62, -2.30), 3.55
    img, mat, size = _render(scene, mats, center, half, ppu, ARM_VIEW)
    gx, gy = _grip_px(center, half, size)
    x0, y0 = _save(img, os.path.join(out, "hand_fist.webp"))
    return {"hand_fist": (gx - x0, gy - y0)}


CARD_PPU = 4.0   # character-tab weapon pictures: rendered at 2 x CARD_PPU, downsampled for clean edges


def job_weapon(args):
    out, ppu, wid, rarity = args[:4]
    card = len(args) > 4 and args[4]
    if card:
        ppu = CARD_PPU * 2
    builder, gc, s, mirror, cy, half = WEAPONS[wid]
    scene0, mats, _b, _v = builder(rarity)

    def scene(p):
        q = p / U / s
        q = q.copy()
        q[:, 1] = q[:, 1] + gc
        if mirror:
            q[:, 0] = -q[:, 0]
        d, m = scene0(q)
        return d * s * U, m

    view = rot_y(0.22)
    img, mat, size = _render(scene, mats, (0.0, cy), half, ppu, view)
    gx, gy = _grip_px((0.0, cy), half, size)
    name = ("wc_" if card else "fp_") + wid + (f"_{rarity}" if wid in GEMMED else "")
    if card:
        a = img[..., 3] > 0.01
        ys, xs = np.nonzero(a)
        x0, y0 = max(xs.min() - 4, 0) & ~1, max(ys.min() - 4, 0) & ~1
        x1, y1 = xs.max() + 6, ys.max() + 6
        crop = np.clip(img[y0:y1, x0:x1] * 255, 0, 255).astype(np.uint8)
        im = Image.fromarray(crop, "RGBA")
        im = im.resize((im.width // 2, im.height // 2), Image.LANCZOS)
        im.save(os.path.join(out, f"{name}.webp"), "WEBP", quality=90, method=6)
        return {name: ((gx - x0) / 2, (gy - y0) / 2)}
    x0, y0 = _save(img, os.path.join(out, f"{name}.webp"))
    return {name: (gx - x0, gy - y0)}


def job_fist_card(args):
    """Character-tab picture of the bare (gloved) fist: hand, cuff and the start of the vambrace, cut below the
    first strap and faded out, rendered at 2 x CARD_PPU and downsampled like the weapon cards."""
    out, _ppu = args
    scene0, mats = arm_scene(fist=True)
    cut = -150.0

    def scene(p):
        d, m = scene0(p)
        return np.maximum(d, -(p[:, 1] / U - cut) * U), m

    ppu = CARD_PPU * 2
    center, half = (0.40, -0.10), 1.55
    img, mat, size = _render(scene, mats, center, half, ppu, ARM_VIEW)
    gx, gy = _grip_px(center, half, size)
    # soft fade over the last 30 units above the cut so the arm does not end in a hard edge
    ys = np.arange(size, dtype=np.float32)
    yu = (center[1] + half) / U - ys / ppu
    fade = np.clip((yu - cut) / 30.0, 0, 1)
    img = img.copy()
    img[..., 3] *= fade[:, None]
    a = img[..., 3] > 0.01
    yy, xx = np.nonzero(a)
    x0, y0 = max(xx.min() - 4, 0) & ~1, max(yy.min() - 4, 0) & ~1
    x1, y1 = xx.max() + 6, yy.max() + 6
    crop = np.clip(img[y0:y1, x0:x1] * 255, 0, 255).astype(np.uint8)
    im = Image.fromarray(crop, "RGBA")
    im = im.resize((im.width // 2, im.height // 2), Image.LANCZOS)
    im.save(os.path.join(out, "wc_fist.webp"), "WEBP", quality=90, method=6)
    return {"wc_fist": ((gx - x0) / 2, (gy - y0) / 2)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("what", default="all", nargs="?")
    ap.add_argument("--ppu", type=float, default=2.4)
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    jobs = []
    if a.what in ("all", "hand"):
        jobs.append((job_hand, (a.out, a.ppu)))
    if a.what in ("all", "fist"):
        jobs.append((job_fist, (a.out, a.ppu)))
    for wid in WEAPONS:
        if a.what in ("all", "weapons", wid):
            for r in (RARITIES if wid in GEMMED else ("common",)):
                jobs.append((job_weapon, (a.out, a.ppu, wid, r)))
        if a.what in ("all", "cards"):
            for r in (RARITIES if wid in GEMMED else ("common",)):
                jobs.append((job_weapon, (a.out, a.ppu, wid, r, True)))
    if a.what in ("all", "cards", "fist_card"):
        jobs.append((job_fist_card, (a.out, a.ppu)))
    meta_path = os.path.join(a.out, "..", "..", "..", "..", "..", "tools", "render_items", "hand_sprites.json") \
        if a.out == DEFAULT_OUT else os.path.join(a.out, "hand_sprites.json")
    meta_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "hand_sprites.json")) if a.out == DEFAULT_OUT else meta_path
    meta = {}
    if os.path.exists(meta_path):
        meta = json.load(open(meta_path))
    if a.what not in ("cards", "fist_card"):
        meta["ppu"] = a.ppu
    meta["card_ppu"] = CARD_PPU
    with ProcessPoolExecutor() as ex:
        futs = [ex.submit(fn, arg) for fn, arg in jobs]
        for f in futs:
            for k, v in f.result().items():
                meta[k] = [round(float(v[0]), 2), round(float(v[1]), 2)]
                print(k, meta[k])
    json.dump(meta, open(meta_path, "w"), indent=1, sort_keys=True)
    print("meta ->", meta_path)


if __name__ == "__main__":
    main()
