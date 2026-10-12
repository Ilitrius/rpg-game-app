# Ausrüstung — eigene Erkenntnisse

- [2026-10-11] Deutsche Itemnamen: Adjektiv nach Genus deklinieren ("Gehärtetes Kurzschwert", "Runenverzierte Axt"); Suffixe "der Stärke / des Drachen" sind genusneutral. — **Warum:** Grammatikfehler fallen sofort auf.
- [2026-10-11] Leeres `baseType` + WEAPON = Altbestand → Dolch-Grafik. Neue Basistypen immer mit Grafik-Mapping anlegen. — **Warum:** alte Saves dürfen nicht ohne Grafik dastehen.
- [2026-10-11] Lange Button-Texte ("Verkaufen (252 Gold)") untereinander statt nebeneinander. — **Warum:** wurden abgeschnitten.
- [2026-10-11] Balance-Vorschlag offen: `statBudget`-Steigung 0.6→0.4 oder Waffenschaden 0.25→0.18 pro Stufe, falls Ausrüstung weniger dominant sein soll — nur zusammen mit Spielwelt-Neuabstimmung. — **Warum:** Spielwelt-Analyse.
- [2026-10-11] Auto-Verkauf bei vollem Inventar hat keine UI-Meldung im Ausrüstungsfenster; Spielwelt zeigt sie per `tryAdd(...).autoSoldGold`. — **Warum:** offener Punkt.
