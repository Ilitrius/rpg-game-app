import sys
import cv2
import numpy as np
from PIL import Image

SRC = "D:/AI_KI/rpg-game-app/.claude/worktrees/synchronisierung-9eed30/design/new img/"
OUT = "C:/Users/renep/AppData/Local/Temp/claude/e817f36fcf075f93/83c552a3-6e23-40ca-a65e-f53600fd94f3/scratchpad/cut/"
import os
os.makedirs(OUT, exist_ok=True)


def border_flood(cond):
    """Connected components of `cond` (bool HxW) that touch the image border."""
    n, lab = cv2.connectedComponents(cond.astype(np.uint8), connectivity=4)
    b = set(lab[0, :]) | set(lab[-1, :]) | set(lab[:, 0]) | set(lab[:, -1])
    b.discard(0)
    return np.isin(lab, list(b))


def grab(img, sure_bg, iters=6, sure_fg=None):
    mask = np.full(img.shape[:2], cv2.GC_PR_FGD, np.uint8)
    mask[sure_bg] = cv2.GC_BGD
    if sure_fg is not None:
        mask[sure_fg] = cv2.GC_FGD
    bgd = np.zeros((1, 65), np.float64)
    fgd = np.zeros((1, 65), np.float64)
    cv2.grabCut(img, mask, None, bgd, fgd, iters, cv2.GC_INIT_WITH_MASK)
    return (mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD)


def finish(img, fg, name, feather=1.2, height=720, min_rel=0.04):
    fg = fg.astype(np.uint8)
    # keep the largest component(s), fill small holes
    n, lab, stats, _ = cv2.connectedComponentsWithStats(fg, connectivity=8)
    if n > 1:
        keep = np.zeros_like(fg)
        big = stats[1:, cv2.CC_STAT_AREA].max()
        for i in range(1, n):
            if stats[i, cv2.CC_STAT_AREA] >= min_rel * big:
                keep[lab == i] = 1
        fg = keep
    fg = cv2.morphologyEx(fg, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    a = cv2.GaussianBlur(fg.astype(np.float32) * 255, (0, 0), feather)
    # slight erosion of the soft edge to drop halos
    a = np.clip((a - 90) * (255.0 / 165.0), 0, 255).astype(np.uint8)
    rgba = np.dstack([cv2.cvtColor(img, cv2.COLOR_BGR2RGB), a])
    ys, xs = np.where(a > 8)
    rgba = rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    im = Image.fromarray(rgba, "RGBA")
    s = height / im.height
    im = im.resize((max(1, round(im.width * s)), height), Image.LANCZOS)
    im.save(OUT + name + ".png")
    return im


def load(n):
    return cv2.imread(SRC + n + ".jpg")


def light_bg(img, thr=222, sat=40):
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    return (img.min(axis=2) > thr) & (hsv[..., 1] < sat)


def run(which):
    if which == "riesenratte":
        img = load(which)
        bg = border_flood(light_bg(img, 228, 30))
        fg = grab(img, bg)
        return finish(img, fg, which)
    if which == "grauwolf":
        img = load(which)
        bg = border_flood(light_bg(img, 205, 45))
        # the printed pattern across the top is part of the picture, keep the fur there
        fg = grab(img, bg)
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        h, w = img.shape[:2]
        yy = np.arange(h)[:, None] * np.ones((1, w))
        shadow = (hsv[..., 0] > 92) & (hsv[..., 0] < 128) & (hsv[..., 1] > 15) & (hsv[..., 1] < 95) & (hsv[..., 2] > 140) & (hsv[..., 2] < 205) & (yy > 0.62 * h)
        # blue-grey shadow only: not the warm/white paw fur
        shadow &= (img[..., 0].astype(int) >= img[..., 2].astype(int) - 4)
        shadow = cv2.morphologyEx(shadow.astype(np.uint8), cv2.MORPH_OPEN, np.ones((9, 9), np.uint8)).astype(bool)
        shadow = cv2.dilate(shadow.astype(np.uint8), np.ones((13, 13), np.uint8)).astype(bool)
        fg &= ~shadow
        return finish(img, fg, which, min_rel=0.3)
    if which == "schleim":
        img = load(which)
        bg = border_flood(light_bg(img, 218, 28))
        fg = grab(img, bg)
        return finish(img, fg, which)
    if which == "wildschwein":
        img = load(which)
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        dark_bg = border_flood((hsv[..., 2] < 95) & (hsv[..., 1] < 70))
        fg0 = ~dark_bg
        # the white sticker outline
        white = (img.min(axis=2) > 185) & (hsv[..., 1] < 45)
        dist = cv2.distanceTransform(dark_bg.astype(np.uint8) ^ 1, cv2.DIST_L2, 3)
        outline = white & (dist < 14)
        fg = fg0 & ~outline
        # re-run grabCut around the cleaned mask for a smooth boundary
        sure_bg = ~cv2.dilate(fg.astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
        sure_fg = cv2.erode(fg.astype(np.uint8), np.ones((15, 15), np.uint8)).astype(bool)
        fg2 = grab(img, sure_bg | outline & (dist < 6), sure_fg=sure_fg)
        S2, V2 = hsv[..., 1], hsv[..., 2]
        yy = np.arange(img.shape[0])[:, None] * np.ones((1, img.shape[1]))
        gray = ((S2 < 48) & (V2 > 70) & (V2 < 215) & (yy < 0.30 * img.shape[0])).astype(np.uint8)
        gray = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
        n, lab, st, _ = cv2.connectedComponentsWithStats(gray, connectivity=8)
        junk = np.zeros_like(gray, dtype=bool)
        for k in range(1, n):
            if st[k, cv2.CC_STAT_AREA] > 1500:
                junk |= lab == k
        edge = cv2.distanceTransform(fg2.astype(np.uint8), cv2.DIST_L2, 3) < 7
        junk |= edge & (V2 > 185) & (S2 < 45)
        junk = cv2.dilate(junk.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
        fg2 = fg2 & ~junk
        return finish(img, fg2, which, min_rel=0.5)
    if which == "waldspinne":
        img = load(which)
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        H_, S_, V_ = hsv[..., 0].astype(int), hsv[..., 1].astype(int), hsv[..., 2].astype(int)
        orange = (H_ >= 3) & (H_ <= 24) & (S_ > 95) & (V_ > 95)
        near = cv2.dilate(orange.astype(np.uint8), np.ones((15, 15), np.uint8)).astype(bool)
        core = cv2.morphologyEx(orange.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8)).astype(bool)
        dark = (V_ < 60) & near
        seed = core | dark
        seed_fg = cv2.erode(seed.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
        sure_bg = ~cv2.dilate(seed.astype(np.uint8), np.ones((41, 41), np.uint8)).astype(bool)
        fg = grab(img, sure_bg, iters=8, sure_fg=seed_fg)
        outside = border_flood(~fg)
        fg = ~outside
        return finish(img, fg, which, min_rel=0.5)


if __name__ == "__main__":
    names = sys.argv[1:] or ["schleim", "riesenratte", "wildschwein", "grauwolf", "waldspinne"]
    sheet = Image.new("RGB", (len(names) * 430, 760), (70, 96, 62))
    for i, n in enumerate(names):
        im = run(n)
        t = im.copy()
        t.thumbnail((420, 740))
        sheet.paste(t, (i * 430 + 5, 10), t)
        print(n, im.size)
    sheet.save(OUT + "sheet.png")
