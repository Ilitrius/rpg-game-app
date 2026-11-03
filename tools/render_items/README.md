# Item-Renderer (realistische Item-Grafik)

Offline-Raytracer (Python, numpy + Pillow), der die Item-Bilder erzeugt. Die Ergebnisse liegen als
512-px-WebP mit Alpha in `app/src/main/res/drawable-nodpi/item_*.webp` und werden von
`ui/equipment/ItemSprites.kt` / `ItemIcon` verwendet (Vektor-Zeichner als Fallback).

## Aufruf

```bash
python -I tools/render_items/render.py all --rarity all --size 704   # alle 45 Bilder (ca. 70 s, parallel)
python -I tools/render_items/render.py mace --rarity epic --out <ordner>   # einzelnes Item
```

- `--size`: Render-Auflösung (Supersampling), `--out-size`: Ausgabegröße (Standard 512).
- Items mit Edelstein (`short_sword`, `mace`, `axe`, `silver_ring`, `gold_ring`, `amulet`, `talisman`)
  entstehen je Seltenheit (`item_<name>_<common|uncommon|rare|epic|legendary>.webp`), alle anderen einmal.

## Aufbau

- `sdf.py`: Renderer (orthografisches Sphere-Tracing von Signed-Distance-Feldern, weiche Schatten,
  Umgebungsverdunkelung, Studio-Umgebung für Spiegelungen, Fresnel, Material-Bumps, Drop-Shadow, Auto-Bildausschnitt).
- `items.py`: Materialien (Stahl, Messing, Silber, Leder, Edelstein, Rost), Hilfsfunktionen, Kurzschwert.
- `items_weapons.py`, `items_armor.py`, `items_jewelry.py`: Modelle je Typ.
- Neues Item: Builder schreiben (`(scene, materials, bounds, view_rotation)`), im `BUILDERS` des Moduls
  eintragen, in `ItemSprites.resFor` zuordnen.

## Monster und Hintergründe

- `render_monsters.py <name|all> [--size 1100] [--height 720]`: Monster (`monsters.py`: Schleim, Schleimkönig, Riesenratte, Wildschwein) als eng beschnittene WebPs `monster_<name>.webp` (keine Bodenschatten, Füße am unteren Rand); Licht: wenig Umgebungslicht, starkes Hauptlicht, kühles Randlicht. Dauer ca. 5 min pro Monster bei 1100 px (parallel).
- `scene_meadow.py [--width 1080 --height 1800]`: perspektivische Wiesen-Landschaft (Himmel, Wolken, 3 Hügelketten, Boden mit Gras/Blumen/Feldweg, Bäume aus dem SDF-Renderer) nach `scene_meadow.webp`; Horizont bei 40 % der Höhe (Kotlin: `SceneBackdrops.HORIZON_FRACTION`). Dauer ca. 3,5 min.
- `weapons_hand.py <kind|all> [--rarity all] [--size 2200] [--height 1650]`: Waffe in der Hand für die Ich-Perspektive (Handschuh, Armschiene, Waffe aufrecht) als `weapon_held_<kind>[_<rarity>].webp` plus `weapon_held_meta.json` (Griffpunkt, Hand-Einheiten je Pixel). Waffenlängen entsprechen den Vektor-Waffen (Spitze/Knauf in Hand-Einheiten, Tabelle `KINDS`), damit die Animation passt. Nach dem Rendern die Zahlen in `HeldWeaponSprites.kt` übernehmen. Dauer ca. 2 min je Bild; `RENDER_WORKERS` begrenzt die Prozesse.
- Einbindung: `ui/adventure/MonsterSprites.kt` (Sprites + Backdrops, Cache, Vorladen), neue Monster in `MonsterSprites.resFor`, neue Zonen in `SceneBackdrops.resFor`.

- `scene_biome.py <biom|all> [--width 1080 --height 1800]`: Hintergründe der 19 Biome (`scene_<biom>.webp`, ca. 70 KB, 1 min je Bild). Parameter je Biom in der Tabelle `BIOMES` (Farben und Formen wie `BiomeStyles.kt`); neue Formen in `profile()`, neue Böden im Boden-Block.
- `render_monsters.py gen [--size 1100 --height 720]`: 8 Körperformen (`monsters_gen.py`: Blob, Hund, Insekt, Golem; `monsters_gen2.py`: Humanoid, Geflügelt, Schlange, Geist), je Normal und Boss, neutral grau (`monster_gen_<plan>[_boss].webp`) plus Leuchtebene `_glow.webp` (Material-ID aus `GLOW`, Maske über `Renderer.mat_map`). Kotlin färbt per Farbverlauf-Abbildung ein (`GeneratedSprites`). Dauer 2-12 min je Bild; `RENDER_WORKERS=8` begrenzt RAM.

- Zonen 2-5: `scene_biome.py wald|sumpf|gebirge|hoehle` (eigene Horizonte `HORIZON_OF`) und `monsters_gen3.py` (`render_monsters.py gen_<name>`: Pilz, Kröte, Irrlicht, Fledermaus, Baumhüter, Hydra, Yeti, Drache, je neutral + `_glow`). Paletten der benannten Monster stehen in `GeneratedSprites.named`.

## Hinweise

- Alle Bilder sind selbst erzeugt (keine fremden Quellen, keine Lizenzfragen).
- Größe: ca. 1,6 MB für alle 45 Bilder. Laufzeit: einmal off-Main decodiert, LRU-Cache 24 MB.
- Nicht enthalten: Waffen in der Hand (Ich-Perspektive, Paper-Doll, Charakter-Tab) sind noch Vektor.
