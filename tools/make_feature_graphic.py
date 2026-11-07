"""Feature graphic of the Play Store listing (1024x500): forest backdrop, spider, legendary sword, title. Uses the in-game renders
(app/src/main/res/drawable-nodpi). Run: python tools/make_feature_graphic.py (paths below are absolute for this checkout)."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
import numpy as np, os

R = r"D:/AI_KI/rpg-game-app/.claude/worktrees/synchronisierung-9eed30/app/src/main/res/drawable-nodpi/"
OUT = r"D:/AI_KI/rpg-game-app/.claude/worktrees/synchronisierung-9eed30/design/feature-graphic-2026-11-1024x500.png"
W, H = 1024, 500
SS = 2  # supersample, downsample at the end
w, h = W * SS, H * SS


def load(name):
    return Image.open(R + name + ".webp").convert("RGBA")


def paste_fit(canvas, im, cx, base_y, height, flip=False, rot=0):
    s = height / im.height
    im2 = im.resize((max(1, int(im.width * s)), int(height)), Image.LANCZOS)
    if flip:
        im2 = im2.transpose(Image.FLIP_LEFT_RIGHT)
    if rot:
        im2 = im2.rotate(rot, resample=Image.BICUBIC, expand=True)
    x = int(cx - im2.width / 2)
    y = int(base_y - im2.height)
    canvas.alpha_composite(im2, (x, y))
    return im2


def shadow(canvas, cx, y, rw, rh, alpha=120):
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(sh)
    d.ellipse((cx - rw, y - rh, cx + rw, y + rh), fill=(0, 0, 0, alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(rh * 0.6))
    canvas.alpha_composite(sh)


# --- background: forest scene band, darkened to the left for the title
bg = Image.open(R + "scene_forest.webp").convert("RGB")
band_top = 330
band = bg.crop((0, band_top, 1080, band_top + int(1080 * H / W)))
band = band.resize((w, h), Image.LANCZOS).convert("RGBA")

arr = np.asarray(band).astype(np.float32)
xs = np.linspace(0, 1, w)[None, :, None]
ys = np.linspace(0, 1, h)[:, None, None]
left_dark = np.clip(1.0 - xs / 0.62, 0, 1) ** 1.2          # strong on the left
vign = np.clip((np.abs(xs - 0.5) * 2) ** 3, 0, 1) * 0.25 + np.clip(ys - 0.75, 0, 1) * 0.5
dark = np.clip(left_dark * 0.82 + vign, 0, 0.9)
arr[..., :3] = arr[..., :3] * (1 - dark) + np.array([10, 14, 10]) * dark
band = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
canvas = band

# warm light behind the monsters
glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse((int(w * 0.52), int(h * 0.12), int(w * 0.98), int(h * 0.92)), fill=(255, 190, 90, 70))
glow = glow.filter(ImageFilter.GaussianBlur(70 * SS))
canvas.alpha_composite(glow)

# --- monsters (right half)
ground = int(h * 0.86)
spider = load("monster_forest_spider")
wolf = load("monster_wolf")
toad = load("monster_mire_toad")

shadow(canvas, int(w * 0.745), ground + 24 * SS, 235 * SS, 24 * SS, 150)
paste_fit(canvas, spider, int(w * 0.745), ground + 28 * SS, 310 * SS)

# --- title block
def gold_text(text, font, pos, shade=True):
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).text(pos, text, font=font, fill=255)
    bbox = mask.getbbox()
    grad = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ga = np.zeros((h, w, 4), dtype=np.uint8)
    y0, y1 = bbox[1], bbox[3]
    t = np.clip((np.arange(h) - y0) / max(1, (y1 - y0)), 0, 1)[:, None]
    top = np.array([255, 237, 179]); mid = np.array([226, 182, 80]); bot = np.array([154, 107, 30])
    col = np.where(t < 0.45, top + (mid - top) * (t / 0.45)[..., None] if False else 0, 0)
    c = np.zeros((h, 1, 3))
    for i in range(h):
        tt = t[i, 0]
        c[i, 0] = top + (mid - top) * (tt / 0.45) if tt < 0.45 else mid + (bot - mid) * ((tt - 0.45) / 0.55)
    ga[..., :3] = np.broadcast_to(c, (h, w, 3)).astype(np.uint8)
    ga[..., 3] = 255
    grad = Image.fromarray(ga, "RGBA")
    if shade:
        sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        smask = ImageChops.offset(mask, 4 * SS, 5 * SS).filter(ImageFilter.GaussianBlur(3 * SS))
        sh.paste((0, 0, 0, 200), (0, 0), smask)
        canvas.alpha_composite(sh)
    layer.paste(grad, (0, 0), mask)
    canvas.alpha_composite(layer)
    return bbox


f_title = ImageFont.truetype("C:/Windows/Fonts/cambriab.ttf", 104 * SS)
f_sub = ImageFont.truetype("C:/Windows/Fonts/cambria.ttc", 28 * SS)
bb = gold_text("IDLE RPG", f_title, (58 * SS, 160 * SS))
d = ImageDraw.Draw(canvas)
d.line([(bb[0], bb[3] + 22 * SS), (bb[2], bb[3] + 22 * SS)], fill=(201, 162, 39, 200), width=2 * SS)
d.text((bb[0] + 2 * SS, bb[3] + 38 * SS), "Dein Held kämpft von allein.", font=f_sub, fill=(214, 228, 232, 255))

sword = load("item_short_sword_legendary")
bbx = sword.getbbox()
sword = sword.crop(bbx)
sw = sword.resize((int(sword.width * 150 * SS / sword.height), 150 * SS), Image.LANCZOS)
glow2 = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
ImageDraw.Draw(glow2).ellipse((30 * SS, 350 * SS, 330 * SS, 500 * SS), fill=(255, 170, 60, 55))
canvas.alpha_composite(glow2.filter(ImageFilter.GaussianBlur(30 * SS)))
canvas.alpha_composite(sw.rotate(-8, resample=Image.BICUBIC, expand=True), (58 * SS, 345 * SS))

out = canvas.convert("RGB").resize((W, H), Image.LANCZOS)
out.save(OUT, "PNG", optimize=True)
print(out.size, os.path.getsize(OUT) // 1024, "KB")
