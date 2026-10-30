# Sound-Effekte (Idle RPG)

Alle Effekte in `app/src/main/res/raw/sfx_*.wav` werden von `tools/generate_sounds.py` rein mathematisch
synthetisiert (Sinus-/Rauschsynthese, Python-Standardbibliothek, feste Zufallsseeds - Ergebnis reproduzierbar).
Es werden keine Samples, Aufnahmen oder fremden Audiodateien verwendet.

**Lizenz:** Skript und erzeugte WAV-Dateien sind eigene Werke des Projektautors; es bestehen keine
Drittanbieter-Lizenzpflichten (keine Namensnennung nötig). Format: 16 Bit, Mono, 22 050 Hz (zusammen ca. 230 KB).

Neu erzeugen: `python tools/generate_sounds.py`

| Datei | Verwendung |
|---|---|
| `sfx_hit` / `sfx_crit` | Heldentreffer / kritischer Treffer (zum Aufprallzeitpunkt der Waffenanimation) |
| `sfx_hurt` | Held wird getroffen |
| `sfx_monster_die` | Monster besiegt |
| `sfx_loot` | Beute-Popup |
| `sfx_levelup` | Stufenaufstieg (nur online, nie bei Offline-Fortschritt) |
| `sfx_boss_start` / `sfx_boss_victory` | Bosskampf-Start / Bosssieg |
| `sfx_coin` | Shop-Kauf/-Verkauf, Lautstärke-Vorschau |
| `sfx_click` | Menüband, Gebietswechsel |

Wiedergabe: `audio/SoundManager` (SoundPool, nur sichtbar, Pool wird im Hintergrund freigegeben; Ein/Aus und
Lautstärke in `GameSettings`, Optionen-Karte "Ton"). Die Einstellungen sind geräteweit und nicht Teil des
Spielstands, deshalb keine Import-Validierung nötig.
