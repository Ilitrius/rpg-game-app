# Gemeinsame Erkenntnisse (alle Agenten)

## Umgebung & Werkzeuge
- [2026-10-11] Screenshots nur über `cmd /c "adb exec-out screencap -p > datei.png"` — **Warum:** PowerShell-Pipes zerstören Binärdaten.
- [2026-10-11] Nav-Leiste bei 1080x2400: y=2250, Charakter x=125, Ausrüstung x=400, Shop x=680, Optionen x=950. Gebietsname oben links antippbar. — **Warum:** spart Ausprobieren.
- [2026-10-11] Clipboard über adb ist unzuverlässig; für Testdaten lieber temporären Code-Pfad nutzen. — **Warum:** Optionen-Agent konnte ungültige Eingaben nicht per adb einspielen.

## Testen auf dem Emulator
- [2026-10-11] Emulator und Spielstand sind geteilt; Altlasten früherer Tests (Items, Stufen, Gold) möglich. Für saubere Tests zuerst `adb shell pm clear com.idlerpg.game`. — **Warum:** Mehrere Agenten sahen "fremde" Items/Werte und hielten es für Bugs.
- [2026-10-11] Das Idle-Spiel läuft weiter, während du testest (Kills, Level-ups, Drops verändern den Stand). Werte vor/nach einer Aktion daher sofort vergleichen. — **Warum:** verwirrte Auswertungen.
- [2026-10-11] Testdaten über temporären Code mit Marker `TEMP` einspielen, prüfen, dann restlos entfernen und neu bauen. Geseedete Daten bleiben im Spielstand! Nie ersetzte Items wegwerfen — ins Inventar legen. — **Warum:** Ein Seeding hat eine angelegte Waffe gelöscht.
- [2026-10-11] Erste Renderings sind oft fehlerhaft: Grafik zu klein (Axt/Streitkolben), Text abgeschnitten (Buttons), Elemente in Statusleiste (Wolken), Overlays zu hoch (Schadenszahlen). Immer Screenshot ansehen und mindestens eine Korrekturrunde einplanen. — **Warum:** passierte bei fast jedem Agenten.

## Code & Zusammenarbeit
- [2026-10-11] Strings in eigene Datei `res/values/strings_<agent>.xml`. — **Warum:** parallele Änderungen an `strings.xml` überschreiben sich.
- [2026-10-11] `GameState` nur mit Default-Werten erweitern und danach Optionen-`SaveTransferTest` + einen Alt-Spielstand-Decode-Test laufen lassen. — **Warum:** Import/Export und alte Saves müssen weiter funktionieren.
- [2026-10-11] Neue Felder auch beim Import validieren lassen (Optionen-Agent informieren). — **Warum:** `damageMin/Max` und `world`-Felder wurden anfangs nicht geprüft.
- [2026-10-11] Treffer-Zeitpunkt aller Waffenanimationen = `WEAPON_IMPACT_FRACTION` (0.38); Kampfschleife hängt daran. — **Warum:** Schaden, Blitz und Zahl müssen synchron zur Animation sein.
- [2026-10-11] Belohnungen (EP, Gold, Item) in **einem** `repository.update` anwenden und im `appScope` speichern. — **Warum:** atomar, geht nicht verloren, wenn die Schleife abbricht.
- [2026-10-11] Änderungen in fremden Dateien: nur minimal, nur mit Auftrag, im Bericht markieren. — **Warum:** Besitz-Regeln halten das Projekt konsistent.

## Balance
- [2026-10-11] Ausrüstung wiegt ca. doppelt so viel wie Stufenaufstiege. Zonen-Multiplikatoren (Spielwelt) sind auf `ItemGenerator`-Konstanten (Ausrüstung) abgestimmt — eine Seite ändern heißt die andere neu abstimmen. — **Warum:** Balance-Analyse Spielwelt-Agent.

## Grafikqualität
- [2026-10-11] Hochwertig wirkt: mehrere Verlaufsschichten, Glanzlichter, Schattierung, Bodenschatten, kleine Details (Rost, Nieten, Edelsteine), leichte Idle-Animation. Einfarbige Grundformen wirken billig. — **Warum:** User-Regel "nur hochauflösende Grafik"; diese Mittel haben bei Dolch, Monstern, Items überzeugt.
- [2026-10-11] Seltenheitsfarben einheitlich über `rarityColor()` (grau/grün/blau/lila/orange). — **Warum:** gleiche Sprache in Ausrüstung, Beute-Popup, Waffen-Glow.
