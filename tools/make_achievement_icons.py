"""Google Play Games achievement icons (512x512 PNG): round medallions with a gold rim, an in-game render in the
middle and a small label ribbon. Output: design/play-games-icons/<id>.png. Run from the repo root:
python tools/make_achievement_icons.py"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "design", "play-games-icons")
os.makedirs(OUT, exist_ok=True)
S = 512
SS = 2


def load(name):
    return Image.open(os.path.join(RES, name + ".webp")).convert("RGBA")


def medallion(bg_top, bg_bottom):
    w = S * SS
    yy, xx = np.mgrid[0:w, 0:w].astype(np.float32)
    c = (w - 1) / 2
    r = np.hypot(xx - c, yy - c) / (w / 2)
    t = np.clip(yy / w, 0, 1)[..., None]
    col = np.array(bg_top) * (1 - t) + np.array(bg_bottom) * t
    glow = np.clip(1 - r * 1.15, 0, 1)[..., None] ** 2 * 38
    col = np.clip(col + glow, 0, 255)
    alpha = (r <= 1.0).astype(np.float32)
    # soft antialiased edge
    alpha = np.clip((1.0 - r) * (w / 2) / 1.5, 0, 1)
    img = np.dstack([col, alpha * 255]).astype(np.uint8)
    im = Image.fromarray(img, "RGBA")
    d = ImageDraw.Draw(im)
    # gold rim: outer dark line, bright gold, inner thin line
    for k, (wd, colr) in enumerate(((10, (70, 46, 12, 255)), (8, (214, 170, 62, 255)), (3, (255, 236, 170, 255)))):
        rad = w / 2 - 2 - (0, 3, 6)[k] * SS
        d.ellipse((c - rad, c - rad, c + rad, c + rad), outline=colr, width=wd * SS // 2)
    rad = w / 2 - 22 * SS
    d.ellipse((c - rad, c - rad, c + rad, c + rad), outline=(120, 90, 30, 160), width=2 * SS)
    return im


def fit(im, box):
    bb = im.getbbox()
    im = im.crop(bb)
    s = min(box[0] / im.width, box[1] / im.height)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


def shadow(canvas, cx, cy, rw, rh, a=120):
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse((cx - rw, cy - rh, cx + rw, cy + rh), fill=(0, 0, 0, a))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(rh * 0.7)))


def font(size):
    for f in ("cambriab.ttf", "georgiab.ttf", "timesbd.ttf", "arialbd.ttf"):
        p = os.path.join("C:/Windows/Fonts", f)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def ribbon(canvas, text):
    w = S * SS
    f = font(70 * SS)
    d = ImageDraw.Draw(canvas)
    bb = d.textbbox((0, 0), text, font=f)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    pad = 26 * SS
    x0, x1 = (w - tw) / 2 - pad, (w + tw) / 2 + pad
    y0, y1 = w - 150 * SS, w - 150 * SS + th + 34 * SS
    rb = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(rb)
    rd.rounded_rectangle((x0, y0, x1, y1), radius=18 * SS, fill=(24, 18, 10, 235), outline=(214, 170, 62, 255), width=4 * SS)
    canvas.alpha_composite(rb)
    d = ImageDraw.Draw(canvas)
    d.text(((w - tw) / 2 - bb[0], y0 + 17 * SS - bb[1]), text, font=f, fill=(255, 232, 160, 255))


def build(name, sprite, label, bg=((38, 52, 44), (12, 16, 12)), size=330, rot=0, scene=None, dy=-14):
    canvas = medallion(*bg)
    w = S * SS
    if scene is not None:
        sc = Image.open(os.path.join(RES, scene + ".webp")).convert("RGB")
        sq = sc.crop((0, 330, 1080, 1410)).resize((w, w), Image.LANCZOS).convert("RGBA")
        mask = Image.new("L", (w, w), 0)
        ImageDraw.Draw(mask).ellipse((22 * SS, 22 * SS, w - 22 * SS, w - 22 * SS), fill=255)
        canvas.paste(sq, (0, 0), mask)
        canvas.alpha_composite(Image.new("RGBA", canvas.size, (0, 0, 0, 70)), (0, 0)) if False else None
    if sprite:
        im = fit(load(sprite), (size * SS, size * SS))
        if rot:
            im = im.rotate(rot, resample=Image.BICUBIC, expand=True)
        cx, cy = w // 2, int(w / 2 + dy * SS)
        shadow(canvas, cx, cy + im.height // 2 - 6 * SS, im.width // 3, 14 * SS, 110)
        canvas.alpha_composite(im, (cx - im.width // 2, cy - im.height // 2))
    # re-draw the rim over scene images so they stay inside the ring
    if scene is not None:
        rim = medallion((0, 0, 0), (0, 0, 0))
        rr = np.asarray(rim).copy()
        rr[..., :3] = rr[..., :3]
        ringmask = Image.fromarray(((np.hypot(*(np.mgrid[0:w, 0:w].astype(np.float32) - (w - 1) / 2)) / (w / 2)) > 0.94).astype(np.uint8) * 255, "L")
        canvas.paste(rim, (0, 0), ringmask)
    ribbon(canvas, label)
    out = canvas.resize((S, S), Image.LANCZOS)
    bg_solid = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg_solid.alpha_composite(out)
    # Play wants a full square PNG: put the medallion on a dark square
    sq = Image.new("RGBA", (S, S), (14, 14, 12, 255))
    sq.alpha_composite(bg_solid)
    sq.convert("RGB").save(os.path.join(OUT, name + ".png"), "PNG", optimize=True)
    return os.path.join(OUT, name + ".png")


ICONS = [
    ("kills_100", "item_dagger", "100", dict(rot=-25, size=300)),
    ("kills_1000", "item_short_sword_epic", "1.000", dict(rot=-25, size=330)),
    ("kills_10000", "item_axe_legendary", "10.000", dict(rot=-10, size=330)),
    ("level_10", "item_amulet_common", "10", dict(bg=((40, 46, 62), (12, 14, 22)), size=290)),
    ("level_50", "item_amulet_rare", "50", dict(bg=((34, 52, 86), (10, 16, 30)), size=290)),
    ("level_100", "item_amulet_legendary", "100", dict(bg=((92, 56, 22), (26, 14, 8)), size=300)),
    ("boss_first", "monster_slime_king", "Boss", dict(bg=((30, 62, 36), (8, 18, 10)), size=310)),
    ("boss_10", "monster_dragon", "10 Bosse", dict(bg=((84, 28, 20), (22, 8, 6)), size=360, dy=-24)),
    ("zones_10", None, "10 Gebiete", dict(scene="scene_biome_heide")),
    ("gold_1m", "item_gold_ring_legendary", "1 Mio.", dict(bg=((86, 66, 22), (24, 18, 6)), size=300)),
]

if __name__ == "__main__":
    for name, sprite, label, kw in ICONS:
        print(build(name, sprite, label, **kw))
