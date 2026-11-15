import sys, numpy as np, cv2
from PIL import Image
import cutout_monster as C

def lab_dist_bg(img, thr, cache={}):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    h, w = img.shape[:2]
    ring = np.concatenate([lab[:6].reshape(-1, 3), lab[-6:].reshape(-1, 3), lab[:, :6].reshape(-1, 3), lab[:, -6:].reshape(-1, 3)])
    med = np.median(ring, axis=0)
    # local gradient background: blur the image heavily from the border colours
    d = np.linalg.norm(lab - med, axis=2)
    return d < thr

def run(n):
    img = C.load(n)
    if n in ("frostyeti",):
        bg = C.border_flood(C.light_bg(img, 232, 40)); fg = C.grab(img, bg); return C.finish(img, fg, n, min_rel=0.5)
    if n == "schneewolf":
        bg = C.border_flood(C.light_bg(img, 225, 34)); fg = C.grab(img, bg); return C.finish(img, fg, n, min_rel=0.5)
    if n == "steingolem":
        bg = C.border_flood(C.light_bg(img, 236, 28)); fg = C.grab(img, bg); return C.finish(img, fg, n, min_rel=0.5)
    if n == "icegeist":
        bg = C.border_flood(lab_dist_bg(img, 17)); fg = C.grab(img, bg, iters=8); return C.finish(img, fg, n, min_rel=0.5)
    if n == "sumpfhydra":
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        m = (hsv[..., 1] > 13).astype(np.uint8)
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, np.ones((11, 11), np.uint8))
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
        outside = C.border_flood(m == 0)
        m = (~outside).astype(np.uint8)
        sure_fg = cv2.erode(m, np.ones((9, 9), np.uint8)).astype(bool)
        sure_bg = ~cv2.dilate(m, np.ones((13, 13), np.uint8)).astype(bool)
        fg = C.grab(img, sure_bg, iters=6, sure_fg=sure_fg)
        return C.finish(img, fg, n, min_rel=0.4)


if __name__ == "__main__":
    names = sys.argv[1:] or ["frostyeti", "icegeist", "schneewolf", "steingolem", "sumpfhydra"]
    sheet = Image.new("RGB", (len(names) * 430, 760), (70, 96, 62))
    for i, n in enumerate(names):
        im = run(n); t = im.copy(); t.thumbnail((420, 740)); sheet.paste(t, (i * 430 + 5, 10), t); print(n, im.size)
    sheet.save(C.OUT + "sheet.png")
