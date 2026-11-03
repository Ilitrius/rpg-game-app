"""Mini path tracer free renderer: orthographic sphere tracing of signed distance fields (numpy).

Scenes are functions ``scene(p) -> (d, mat)`` over arrays ``p`` of shape (N, 3) in model space
(x right, y up, z towards the camera). ``mat`` is an int array of material ids. Lighting: one soft
key light with soft shadows, ambient occlusion, a procedural studio environment for reflections,
fresnel, GGX-like specular, fake subsurface for gems.
"""
from __future__ import annotations

import numpy as np

# ----------------------------------------------------------------------------- vector helpers


def v3(x, y, z):
    return np.array([x, y, z], dtype=np.float32)


def norm(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), 1e-9)


def rot_x(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=np.float32)


def rot_y(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=np.float32)


def rot_z(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=np.float32)


# ----------------------------------------------------------------------------- SDF primitives
# All take points already translated by the caller unless a centre is given.


def sd_sphere(p, c, r):
    return np.linalg.norm(p - c, axis=-1) - r


def sd_ellipsoid(p, c, r):
    q = (p - c) / r
    k0 = np.linalg.norm(q, axis=-1)
    k1 = np.linalg.norm(q / r, axis=-1)
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-9)


def sd_box(p, c, half, rad=0.0):
    q = np.abs(p - c) - (half - rad)
    return (np.linalg.norm(np.maximum(q, 0.0), axis=-1)
            + np.minimum(np.max(q, axis=-1), 0.0) - rad)


def sd_capsule(p, a, b, r):
    pa, ba = p - a, b - a
    h = np.clip(np.sum(pa * ba, axis=-1) / np.dot(ba, ba), 0.0, 1.0)
    return np.linalg.norm(pa - h[:, None] * ba, axis=-1) - r


def sd_cyl_y(p, c, r, h, rad=0.0):
    """Capped cylinder along y, radius r, half height h, edge rounding rad."""
    q = p - c
    d0 = np.hypot(q[:, 0], q[:, 2]) - (r - rad)
    d1 = np.abs(q[:, 1]) - (h - rad)
    return np.minimum(np.maximum(d0, d1), 0.0) + np.hypot(np.maximum(d0, 0.0), np.maximum(d1, 0.0)) - rad


def sd_cone_y(p, c, r0, r1, h, rad=0.0):
    """Truncated cone along y: radius r0 at y=-h, r1 at y=+h."""
    q = p - c
    t = np.clip((q[:, 1] + h) / (2 * h), 0.0, 1.0)
    r = r0 + (r1 - r0) * ((q[:, 1] + h) / (2 * h))
    d0 = np.hypot(q[:, 0], q[:, 2]) - (r - rad)
    d1 = np.abs(q[:, 1]) - (h - rad)
    del t
    return np.minimum(np.maximum(d0, d1), 0.0) + np.hypot(np.maximum(d0, 0.0), np.maximum(d1, 0.0)) - rad


def sd_torus(p, c, R, r, axis="y"):
    q = p - c
    if axis == "y":
        a, b, h = q[:, 0], q[:, 2], q[:, 1]
    elif axis == "z":
        a, b, h = q[:, 0], q[:, 1], q[:, 2]
    else:
        a, b, h = q[:, 1], q[:, 2], q[:, 0]
    return np.hypot(np.hypot(a, b) - R, h) - r


def sd_round_cone(p, a, b, r1, r2):
    """Capsule with different end radii (r1 at a, r2 at b), exact distance."""
    ba = b - a
    l2 = float(np.dot(ba, ba))
    rr = r1 - r2
    a2 = l2 - rr * rr
    il2 = 1.0 / l2
    pa = p - a
    y = pa @ ba
    z = y - l2
    w = pa * l2 - np.outer(y, ba)
    x2 = np.sum(w * w, axis=-1)
    y2 = y * y * l2
    z2 = z * z * l2
    k = np.sign(rr) * rr * rr * x2
    d_far = np.sqrt(x2 + z2) * il2 - r2
    d_near = np.sqrt(x2 + y2) * il2 - r1
    d_mid = (np.sqrt(x2 * a2 * il2) + y * rr) * il2 - r1
    return np.where(np.sign(z) * a2 * z2 > k, d_far, np.where(np.sign(y) * a2 * y2 < k, d_near, d_mid))


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h - k * h * (1.0 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


def union(parts):
    """parts: list of (d, material_id). Returns (d, mat)."""
    d = parts[0][0]
    m = np.full(d.shape, parts[0][1], dtype=np.int16)
    for dd, mm in parts[1:]:
        sel = dd < d
        d = np.where(sel, dd, d)
        m = np.where(sel, mm, m)
    return d, m


# ----------------------------------------------------------------------------- noise


def _hash3(i, j, k, seed=0):
    n = (i * 374761393 + j * 668265263 + k * 2147483647 + seed * 1274126177) & 0x7FFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def value_noise(p, freq, seed=0):
    q = p * freq
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = ((f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1])
                     * (f[:, 2] if dz else 1 - f[:, 2]))
                out = out + w * _hash3(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
    return out


def fbm(p, freq, octaves=4, seed=0):
    amp, tot, out = 0.5, 0.0, 0.0
    for o in range(octaves):
        out = out + amp * value_noise(p, freq * (2 ** o), seed + o)
        tot += amp
        amp *= 0.5
    return out / tot


# ----------------------------------------------------------------------------- environment


def environment(d):
    """Procedural studio with large softboxes. d: unit directions (N,3)."""
    y, z = d[:, 1], d[:, 2]
    up = np.clip(y, 0.0, 1.0)[:, None]
    base = np.where(y[:, None] > 0,
                    np.array([0.16, 0.17, 0.20]) + up * np.array([0.35, 0.38, 0.44]),
                    np.array([0.05, 0.05, 0.06]) + np.clip(-y, 0, 1)[:, None] * np.array([0.03, 0.03, 0.03]))
    base = base + np.clip(z, 0.0, 1.0)[:, None] ** 2 * np.array([0.22, 0.22, 0.24])
    def lobe(v, power, col):
        v = norm(np.array([v], dtype=np.float32))
        return (np.maximum(np.sum(d * v, axis=-1), 0.0) ** power)[:, None] * np.array(col)
    base = base + lobe((-0.55, 0.65, 0.55), 10, (2.2, 2.1, 1.9))     # warm key softbox
    base = base + lobe((-0.30, 0.55, 0.80), 4, (0.75, 0.74, 0.70))   # big front panel
    base = base + lobe((0.85, 0.25, 0.40), 8, (0.55, 0.70, 1.00))    # cool fill
    base = base + lobe((0.15, 0.35, -0.90), 6, (0.55, 0.55, 0.60))   # rim
    base = base + lobe((0.0, 1.0, 0.1), 3, (0.35, 0.36, 0.40))       # sky
    strip = np.exp(-((d[:, 0] + 0.30) / 0.10) ** 2) * (y > -0.2) * np.clip(z + 0.2, 0, 1)
    strip2 = np.exp(-((d[:, 0] - 0.55) / 0.06) ** 2) * (y > -0.2) * np.clip(z + 0.2, 0, 1)
    base = base + strip[:, None] * np.array([2.4, 2.35, 2.2]) + strip2[:, None] * np.array([0.9, 1.1, 1.5])
    return base.astype(np.float32)


# ----------------------------------------------------------------------------- materials


class Material:
    def __init__(self, albedo, metal=0.0, rough=0.5, spec=0.5, emissive=0.0, bump=None, tint_fn=None,
                 sss=0.0, aniso=0.0, edge=None):
        self.edge = edge          # (rgb, power, strength): light edge glow for gel/translucent skin
        self.albedo = np.array(albedo, dtype=np.float32)
        self.metal = metal
        self.rough = rough
        self.spec = spec
        self.emissive = emissive
        self.bump = bump          # callable(p)->(N,) height field in model units, or None
        self.tint_fn = tint_fn    # callable(p, albedo(N,3))->albedo(N,3)
        self.sss = sss
        self.aniso = aniso


# ----------------------------------------------------------------------------- renderer


class Renderer:
    def __init__(self, scene, materials, bounds, size=768, light_dir=(-0.5, 0.75, 0.6), steps=120,
                 view_rot=None, ground_shadow=True, bg_alpha=True, ambient_scale=1.0, key_scale=1.0, rim=0.0,
                 gamma=1.9, scurve=0.0):
        """bounds: half extent of the visible square in model units (x and y), centred on the origin.
        view_rot: 3x3 matrix applied to model space points (object rotation relative to the camera)."""
        self.scene = scene
        self.materials = materials
        self.ambient_scale, self.key_scale, self.rim = ambient_scale, key_scale, rim
        self.gamma, self.scurve = gamma, scurve
        self.bounds = bounds
        self.size = size
        self.light = norm(np.array(light_dir, dtype=np.float32))
        self.steps = steps
        self.R = np.eye(3, dtype=np.float32) if view_rot is None else view_rot.astype(np.float32)
        self.Rinv = self.R.T
        self.ground_shadow = ground_shadow
        self.center = np.zeros(2, dtype=np.float32)
        self.margin = 0.9

    def autofit(self, probe=112, passes=2):
        for _ in range(passes):
            self._autofit_once(probe)

    def _autofit_once(self, probe):
        """Measures the silhouette at low resolution and frames it (square, centred, with margin)."""
        old = (self.size, self.ground_shadow)
        self.size, self.ground_shadow = probe, False
        a = self.render()[..., 3] > 0.5
        self.size, self.ground_shadow = old
        ys, xs = np.nonzero(a)
        if xs.size == 0:
            return
        S = probe
        x0, x1 = xs.min(), xs.max() + 1
        y0, y1 = ys.min(), ys.max() + 1
        cx = ((x0 + x1) / 2 / S * 2 - 1) * self.bounds + self.center[0]
        cy = (1 - (y0 + y1) / 2 / S * 2) * self.bounds + self.center[1]
        half = max(x1 - x0, y1 - y0) / S * self.bounds
        self.center = np.array([cx, cy], dtype=np.float32)
        self.bounds = float(half / self.margin)

    # model <-> camera: scene() receives points in model space = Rinv @ camera_point
    def _sdf(self, p_cam):
        p = p_cam @ self.R  # row-vector form of Rinv @ p
        return self.scene(p)

    def _normal(self, p_cam):
        e = self.bounds * 0.0016
        ex, ey, ez = v3(e, 0, 0), v3(0, e, 0), v3(0, 0, e)
        dx = self._sdf(p_cam + ex)[0] - self._sdf(p_cam - ex)[0]
        dy = self._sdf(p_cam + ey)[0] - self._sdf(p_cam - ey)[0]
        dz = self._sdf(p_cam + ez)[0] - self._sdf(p_cam - ez)[0]
        return norm(np.stack([dx, dy, dz], axis=-1))

    def render(self):
        S = self.size
        xs = (np.arange(S, dtype=np.float32) + 0.5) / S * 2 - 1
        ys = 1 - (np.arange(S, dtype=np.float32) + 0.5) / S * 2
        X, Y = np.meshgrid(xs * self.bounds + self.center[0], ys * self.bounds + self.center[1])
        n = S * S
        z_far = self.bounds * 1.6
        o = np.stack([X.ravel(), Y.ravel(), np.full(n, z_far, dtype=np.float32)], axis=-1)
        dirv = v3(0, 0, -1)
        t = np.zeros(n, dtype=np.float32)
        hit = np.zeros(n, dtype=bool)
        active = np.arange(n)
        for _ in range(self.steps):
            if active.size == 0:
                break
            p = o[active] + t[active, None] * dirv
            d, _m = self._sdf(p)
            eps = self.bounds * 0.0004
            h = d < eps
            hit[active[h]] = True
            t[active] += d * 0.9
            alive = (~h) & (t[active] < z_far * 2.0)
            active = active[alive]
        idx = np.nonzero(hit)[0]
        color = np.zeros((n, 3), dtype=np.float32)
        alpha = np.zeros(n, dtype=np.float32)
        mat_map = np.full(n, -1, dtype=np.int16)
        if idx.size:
            p = o[idx] + t[idx, None] * dirv
            color[idx] = self._shade(p, idx)
            mat_map[idx] = self._last_mat
            alpha[idx] = 1.0
        self.mat_map = mat_map.reshape(S, S)          # material id per pixel (-1 = background), e.g. for glow layers
        img = np.concatenate([color, alpha[:, None]], axis=-1).reshape(S, S, 4)
        if self.ground_shadow:
            img = self._drop_shadow(img)
        return img

    def _drop_shadow(self, img):
        """Soft contact/drop shadow behind the object, composited under it (premultiplied-safe)."""
        from PIL import Image, ImageFilter
        S = img.shape[0]
        a = Image.fromarray((img[..., 3] * 255).astype(np.uint8))
        sh = a.filter(ImageFilter.GaussianBlur(S * 0.022))
        sh = np.asarray(sh, dtype=np.float32) / 255.0 * 0.55
        off = int(S * 0.018)
        sh = np.roll(np.roll(sh, off, axis=0), off, axis=1)
        out = img.copy()
        a0 = img[..., 3:4]
        sh_a = sh[..., None] * (1 - a0)
        total = a0 + sh_a
        rgb = (img[..., :3] * a0) / np.maximum(total, 1e-6)
        out[..., :3] = np.where(total > 1e-6, rgb, 0.0)
        out[..., 3:4] = total
        return out

    # ------------------------------------------------------------------ shading
    def _soft_shadow(self, p, n, k=14.0):
        L = self.light
        res = np.ones(p.shape[0], dtype=np.float32)
        t = np.full(p.shape[0], self.bounds * 0.01, dtype=np.float32)
        for _ in range(28):
            q = p + n * self.bounds * 0.004 + L * t[:, None]
            d, _m = self._sdf(q)
            res = np.minimum(res, np.clip(k * d / np.maximum(t, 1e-4), 0.0, 1.0))
            t += np.clip(d, self.bounds * 0.012, self.bounds * 0.12)
        return res * res * (3 - 2 * res)

    def _ao(self, p, n):
        occ = np.zeros(p.shape[0], dtype=np.float32)
        sca = 1.0
        for i in range(1, 6):
            h = self.bounds * 0.012 * i * 1.6
            d, _m = self._sdf(p + n * h)
            occ += (h - d) * sca
            sca *= 0.78
        return np.clip(1.0 - occ / (self.bounds * 0.1), 0.0, 1.0)

    def _shade(self, p_cam, idx):
        n = self._normal(p_cam)
        d0, mat = self._sdf(p_cam)
        self._last_mat = mat
        pm = p_cam @ self.R
        view = v3(0, 0, 1)
        out = np.zeros_like(p_cam)
        ao = self._ao(p_cam, n)
        sh = self._soft_shadow(p_cam, n)
        for mid in np.unique(mat):
            m = self.materials[int(mid)]
            sel = mat == mid
            ps, ns = pm[sel], n[sel]
            albedo = np.tile(m.albedo, (ps.shape[0], 1))
            if m.tint_fn is not None:
                albedo = m.tint_fn(ps, albedo)
            nn = ns
            if m.bump is not None:
                e = 0.004
                hx = m.bump(ps + v3(e, 0, 0)) - m.bump(ps - v3(e, 0, 0))
                hy = m.bump(ps + v3(0, e, 0)) - m.bump(ps - v3(0, e, 0))
                hz = m.bump(ps + v3(0, 0, e)) - m.bump(ps - v3(0, 0, e))
                g_model = np.stack([hx, hy, hz], axis=-1) / (2 * e)
                g_cam = g_model @ self.Rinv.T  # model-space gradient to camera space
                g_cam = g_cam - np.sum(g_cam * ns, axis=-1, keepdims=True) * ns
                nn = norm(ns - g_cam)
            L = self.light
            ndl = np.maximum(np.sum(nn * L, axis=-1), 0.0)
            key = ndl * sh[sel]
            hv = norm(L + view)
            ndh = np.maximum(np.sum(nn * hv, axis=-1), 0.0)
            shin = 2.0 / (m.rough ** 2 + 1e-3)
            spec = (ndh ** np.minimum(shin, 600.0)) * m.spec * (shin + 8) / 25.0 * key
            fres = 0.04 + 0.96 * (1 - np.maximum(nn @ view, 0.0)) ** 5
            refl_dir = norm(2 * np.sum(nn * view, axis=-1, keepdims=True) * nn - view)
            if m.rough > 0.0:
                # cheap roughness: blur the environment by mixing with a wide-lobe lookup
                refl = 0.5 * environment(refl_dir) + 0.5 * environment(norm(refl_dir * (1 - m.rough) + nn * m.rough))
            else:
                refl = environment(refl_dir)
            amb_c = np.array([0.34, 0.37, 0.43], dtype=np.float32)
            amb = amb_c * (0.45 + 0.55 * (nn[:, 1:2] * 0.5 + 0.5)) * ao[sel][:, None] * self.ambient_scale
            diffuse_col = albedo * (amb + key[:, None] * np.array([1.25, 1.18, 1.05]) * self.key_scale)
            metal_col = albedo * (refl * (0.35 + 0.65 * ao[sel][:, None]) * (0.45 + 0.55 * sh[sel][:, None] + 0.0)
                                  + spec[:, None] * np.array([1.0, 0.97, 0.9]) * 0.8)
            if m.sss > 0:
                back = np.maximum(-np.sum(nn * L, axis=-1), 0.0)
                diffuse_col = diffuse_col + albedo * (back ** 2)[:, None] * m.sss
            dielectric = diffuse_col * (1 - fres[:, None] * m.spec) + refl * fres[:, None] * m.spec * ao[sel][:, None] \
                + spec[:, None] * np.array([1.0, 0.97, 0.9]) * 0.5
            col = dielectric * (1 - m.metal) + metal_col * m.metal
            col = col + albedo * m.emissive
            ndv = np.clip(nn @ view, 0.0, 1.0)
            if m.edge is not None:
                ecol, epow, estr = m.edge
                w = (estr * (1.0 - ndv) ** epow)[:, None]
                col = col * (1 - w) + np.array(ecol, dtype=np.float32) * w * (0.40 + 0.60 * key[:, None] + 0.4 * amb)
            if self.rim > 0:
                rim_dir = norm(np.array([[0.85, 0.35, -0.25]], dtype=np.float32))[0]
                lit = np.clip(nn @ rim_dir, 0.0, 1.0)
                rimterm = self.rim * (1.0 - ndv) ** 2.2 * lit * (0.5 + 0.5 * ao[sel])
                col = col + rimterm[:, None] * np.array([0.55, 0.68, 0.95], dtype=np.float32)
            out[sel] = col
        # gentle tone mapping + gamma
        out = out * 1.0
        out = out / (1.0 + out * 0.22)
        v = np.clip(out, 0.0, 1.0) ** (1 / self.gamma)
        if self.scurve:
            v = v + self.scurve * (v * v * (3 - 2 * v) - v)
        return v


def save_webp(img, path, out_size=512, quality=92):
    from PIL import Image
    a = np.clip(img * 255, 0, 255).astype(np.uint8)
    im = Image.fromarray(a, "RGBA")
    if out_size != im.size[0]:
        # premultiplied resize to avoid dark fringes
        arr = np.asarray(im, dtype=np.float32) / 255.0
        pre = arr.copy()
        pre[..., :3] *= pre[..., 3:4]
        imp = Image.fromarray(np.clip(pre * 255, 0, 255).astype(np.uint8), "RGBA").resize((out_size, out_size), Image.LANCZOS)
        r = np.asarray(imp, dtype=np.float32) / 255.0
        rgb = r[..., :3] / np.maximum(r[..., 3:4], 1e-4)
        r[..., :3] = np.clip(rgb, 0, 1)
        im = Image.fromarray(np.clip(r * 255, 0, 255).astype(np.uint8), "RGBA")
    im.save(path, "WEBP", quality=quality, method=6)
    return im
