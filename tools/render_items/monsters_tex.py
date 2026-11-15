"""Monsters with real photo textures (CC0, see textures.py): the base models come from the other monster modules,
here only the skin materials are replaced by textured ones. Same builder signature as everywhere:
`builder() -> (scene, materials, bounds, view_rotation)`."""
from __future__ import annotations

from functools import partial

import numpy as np

import monsters_forest
import monsters_rock
import monsters_swamp
from sdf import fbm, value_noise
from textures import Tex, bump_fn, tint_fn


def _retex(builder, mats):
    """Calls the base builder and patches materials: mats = {index: dict(tint=fn, bump=fn, rough=.., spec=.., albedo=..)}."""
    def build(*a, **k):
        scene, materials, bounds, view = builder(*a, **k)
        for idx, over in mats.items():
            m = materials[idx]
            if "tint" in over:
                m.tint_fn = over["tint"]
            if "bump" in over:
                m.bump = over["bump"]
            for key in ("rough", "spec", "sss", "emissive", "metal"):
                if key in over:
                    setattr(m, key, over[key])
            if "albedo" in over:
                m.albedo = np.array(over["albedo"], dtype=np.float32)
        return scene, materials, bounds, view
    return build


# ---- crystal golem: weathered cliff rock (Rock051) with violet tint, mossy cracks stay in the photo
_rock = Tex("Rock051", tile=1.5)
crystal_golem = _retex(
    partial(monsters_rock.golem, stone=(0.22, 0.22, 0.30), crystal=(0.45, 0.30, 0.90), glow=(0.7, 0.4, 1.0)),
    {0: dict(albedo=(0.78, 0.74, 0.95), tint=tint_fn(_rock, "color", gain=1.15, gamma=1.0),
               bump=bump_fn(_rock, 0.07, fine=lambda p: (value_noise(p, 55.0, 6) - 0.5) * 0.008), rough=0.85)},
)

# ---- treant: mossy bark (Bark013) for trunk, moss photo for the moss patch and the crown
_bark = Tex("Bark013", tile=2.4, shear=0.2)
_moss = Tex("Moss002", tile=1.6)
treant = _retex(
    monsters_forest.treant,
    {0: dict(albedo=(0.95, 0.72, 0.52), tint=tint_fn(_bark, "color", gain=1.0), bump=bump_fn(_bark, 0.045), rough=0.9),
       1: dict(albedo=(0.55, 0.85, 0.40), tint=tint_fn(_moss, "color", gain=1.2), bump=bump_fn(_moss, 0.03)),
       3: dict(albedo=(0.85, 1.0, 0.75), tint=tint_fn(_moss, "color", gain=1.0), bump=bump_fn(_moss, 0.04))},
)

# ---- dragon: crocodile-leather scales (Leather008), inverted so the scales are the bright, raised cells
_croc = Tex("Leather008", tile=1.25, shear=0.3)


def _scale_tint(base):
    base = np.array(base, dtype=np.float32)

    def f(p, a):
        c = _croc.color(p)
        lum = (c @ np.array([0.299, 0.587, 0.114], dtype=np.float32))
        groove = np.clip((lum - 0.10) * 3.2, 0.0, 1.0)            # 1 in the grooves between the scales
        cell = (0.62 + 0.55 * fbm(p, 6.0, 3, 2))[:, None]
        return base * cell * (1.0 - 0.75 * groove)[:, None] + groove[:, None] * 0.05
    return f


dragon = _retex(
    monsters_rock.dragon,
    {0: dict(tint=_scale_tint((0.50, 0.10, 0.07)), bump=lambda p: -(_croc.height(p) - 0.5) * 0.020,
               rough=0.38, spec=1.0)},
)

# ---- bat: fine leathery wrinkles on the wing skin
_leather = Tex("Leather026", tile=0.9, shear=0.1)
bat = _retex(
    monsters_rock.bat,
    {1: dict(bump=bump_fn(_leather, 0.012), rough=0.5)},
)

# ---- mire toad: warty, wet skin; the leather photo gives the dense skin structure
_toad = Tex("Leather008", tile=0.75, shear=0.2)


def _toad_tint(p, a):
    c = _toad.color(p)
    lum = (c @ np.array([0.299, 0.587, 0.114], dtype=np.float32))
    groove = np.clip((lum - 0.10) * 3.0, 0.0, 1.0)
    base = a * (0.55 + 0.9 * fbm(p, 5.0, 3, 4))[:, None] * (1.0 - 0.35 * np.clip(0.4 - p[:, 1], 0, 1))[:, None]
    return base * (1.0 - 0.55 * groove)[:, None]


mire_toad = _retex(
    monsters_swamp.mire_toad,
    {0: dict(tint=_toad_tint, bump=lambda p: -(_toad.height(p) - 0.5) * 0.022)},
)


# ---- mushroom sprite: fine skin pores (Leather037 height) on cap and stem
_pores = Tex("Leather037", tile=0.7, shear=0.1)
mushroom = _retex(
    monsters_forest.mushroom,
    {0: dict(bump=bump_fn(_pores, 0.018, fine=lambda p: (fbm(p, 40.0, 3, 6) - 0.5) * 0.004)),
     2: dict(bump=bump_fn(_pores, 0.012))},
)

# ---- cave spider: hard glossy chitin plates (Leather008 grooves) over the hairy base
_chitin = Tex("Leather008", tile=0.5, shear=0.3)
cave_spider = _retex(
    partial(monsters_forest.forest_spider, body_c=(0.22, 0.20, 0.26), leg_c=(0.30, 0.28, 0.34), eye_col=(0.3, 0.9, 0.5), mark_c=(0.35, 0.80, 0.55)),
    {0: dict(bump=lambda p: -(_chitin.height(p) - 0.5) * 0.018, rough=0.30, spec=0.9),
     1: dict(bump=lambda p: -(_chitin.height(p) - 0.5) * 0.012, rough=0.32, spec=0.9)},
)

BUILDERS = {
    "mushroom": mushroom,
    "cave_spider": cave_spider,
    "crystal_golem": crystal_golem,
    "treant": treant,
    "dragon": dragon,
    "bat": bat,
    "mire_toad": mire_toad,
}
