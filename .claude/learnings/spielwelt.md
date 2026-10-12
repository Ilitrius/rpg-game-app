# Spielwelt — eigene Erkenntnisse

- [2026-10-11] Balance mit Referenz-Held (45 % Stärke / 30 % Geschick / 25 % Vitalität) und Monte-Carlo-Drops prüfen; Ergebnis landet in `app/build/balance.txt`, Bedingungen in `BalanceTest`. — **Warum:** reproduzierbare Abstimmung statt Bauchgefühl.
- [2026-10-11] Monster-Schaden wächst schon pro Stufe; Zonen-Schadensmultiplikatoren daher später < 1. — **Warum:** Helden-HP wächst nur linear.
- [2026-10-11] Offline-Fortschritt gibt nichts, wenn der Held einen Durchschnittskampf nicht überlebt. — **Warum:** sonst Belohnung für unmögliche Zonen.
- [2026-10-11] Offene HUD-Punkte: "Boss bereit!" steht auch während des Bosskampfs; Beute-Popup überlappt die Waffe leicht. — **Warum:** offene Punkte.
- [2026-10-11] Monster-Varianten über Paletten/Formen sind ok, müssen aber klar unterscheidbar bleiben. — **Warum:** 20 Monster in kurzer Zeit, Qualität hielt.
