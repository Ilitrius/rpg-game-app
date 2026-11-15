"""Downloads the CC0 photo textures used by the monster renderer (ambientCG, 1K JPG) into ./textures/<Name>/.

Run once: python tools/render_items/fetch_textures.py
Licence: CC0 1.0 (https://docs.ambientcg.com/license/), no attribution required, free for commercial use.
"""
from __future__ import annotations

import io
import os
import urllib.request
import zipfile

NAMES = ["Bark013", "Rock064", "Rock051", "Leather008", "Leather026", "Moss002"]
OUT = os.path.join(os.path.dirname(__file__), "textures")


def main():
    for n in NAMES:
        dest = os.path.join(OUT, n)
        if os.path.isdir(dest) and any(f.endswith("_Color.jpg") for f in os.listdir(dest)):
            print(n, "already there")
            continue
        os.makedirs(dest, exist_ok=True)
        req = urllib.request.Request(f"https://ambientcg.com/get?file={n}_1K-JPG.zip", headers={"User-Agent": "Mozilla/5.0"})
        data = urllib.request.urlopen(req, timeout=120).read()
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for m in z.namelist():
                if m.endswith(".jpg"):
                    z.extract(m, dest)
        print(n, len(data) // 1024, "KB")


if __name__ == "__main__":
    main()
