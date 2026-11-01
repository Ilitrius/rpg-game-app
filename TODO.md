# TODO

Legende: `[ ]` offen · `[x]` erledigt

## Offen
- [ ] Alles Folgende wurde ohne baubares Android-SDK umgesetzt (dl.google.com gesperrt): `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` laufen lassen, Fehler beheben, dann auf dem Emulator prüfen: Sprachwechsel (System/DE/EN, alle Tabs), Sounds (Pegel/Latenz anhören), Offline-Dialog nach zurückdatiertem `lastActiveEpochMillis`, Update-Hinweis (nur mit Play-Store-Installation, Internal Test).
- [ ] Offline-Anzeige: Ursache der falschen Zeitanzeige nicht eindeutig reproduziert (Berechnung/Kappung/Stempel im Code und per Unit-Test korrekt). Behoben: Dauer ab 24 h wird in Tagen angezeigt statt "960 Std.". Falls auf dem Gerät weiter falsch: Screenshot des Dialogs + Zeit der letzten Sitzung notieren.
- [ ] Alle Apps: Update-Hinweis, Sprachprüfung, Akkuschonung analog in Kind Words und RPCT umsetzen (hier nur Idle RPG).

## Später geplant (Ideen, noch nicht umgesetzt)
- [ ] **Charakter-Reset / Wiedergeburt (Prestige):** Held zurücksetzen (Level, Zonen, Gold, Inventar) gegen permanente Boni und neue Eigenschaften. Vorschlag: Währung „Seelen“ aus Level/höchster Zone; permanente Boni (+% XP, +% Gold, +% Schaden, Start-Level, schnelleres Rasten) und freischaltbare Eigenschaften (Talentbaum, z. B. Krit-Spezialisierung, Beute-Glück, Klick-Meister). Greift stark in `GameState`/Zonen/Import ein: erst Konzept (Formeln, Balance, Save-Migration, Import-Validierung), dann Umsetzung durch `charakter` + `spielwelt`. Spielstand-Backup vor dem Reset anbieten.
- [ ] **Buff-System:** zeitlich begrenzte oder kampfgebundene Buffs (z. B. +Schaden, +XP, +Gold, +Dropchance, Regeneration, Klick-Boost). Quellen: Shop (Tränke/Elixiere), Tagesbelohnung, Ziele, Bosssiege. Anzeige als Buff-Leiste im HUD mit Restzeit; Laufzeit nur in Spielzeit zählen (nicht offline missbrauchbar), Stapelregeln, Persistenz in `GameState` (Felder mit Default).
- [ ] **Gold-Senken (Geld sinnvoll ausgeben):** Buff-Elixiere im Shop, Ausrüstung verzaubern/aufwerten (Gold → +Stufe, steigende Kosten), Inventar-Erweiterung (mehr als 40 Plätze), Reroll von Item-Werten, Shop-Nachschub (gibt es schon, `paidRestock`), Rast/Heilung nach Niederlage, Zonen-Schnellreise, Prestige-Boni mit Gold-Anteil kaufen, kosmetische Waffen-/Rahmen-Skins.
- [ ] **Google Play Games Anmeldung** (mit Kontoauswahl): Punkte/Bestenliste (Level, höchste Zone, Kills), Erfolge (aus den Zielen ableitbar), **Cloud-Spielstand** über Play Games „Saved Games“ (Konflikt: neuerer Stand/Zeitstempel, vorher manuelles Backup). Folgen: INTERNET-Berechtigung und Play-Games-Services-SDK, Konsolen-Einrichtung (Play Games Services-Projekt, OAuth-Client, Erfolge/Bestenlisten anlegen), Datenschutzerklärung und Datensicherheitsformular ändern (Konto-ID/Spieldaten werden an Google übertragen), Consent-Dialog (Opt-in, Spiel muss ohne Anmeldung voll nutzbar bleiben), `allowBackup=false` bleibt. Anmeldung optional unter Optionen → Konto.

## Erledigt (Idle RPG)
- [x] Update-Hinweis (Play In-App Updates, flexibler Flow, `update/InAppUpdater`, `app-update` über Version Catalog, `strings_update.xml`, Datenschutz-Seite/In-App-Kurzfassung/Checkliste angepasst).
- [x] Sprachumschaltung geprüft: Auswahl System/DE/EN in Optionen vorhanden; Lücke behoben: "System" setzte `Locale.getDefault()` nach vorheriger Auswahl nicht zurück; Waffenname im Charakter-Tab war unlokalisiert.
- [x] Itemnamen lokalisiert (Shop, Beute-Popup, Charakter), Import-Dialog `Locale.getDefault()`, `SaveTransfer`-Meldungen mit lokalisiertem Itemnamen.
- [x] Akkuschonend: `collectAsStateWithLifecycle`, Shop-Ticker nur sichtbar, Kampfschleife/Ton im Hintergrund aus, Offline-Berechnung einmalig beim Start (kein Dauerlauf); keine Wakelocks/Alarme/Jobs im Projekt.
- [x] Sound (SoundPool, synthetisiert in `tools/generate_sounds.py`, Lizenz in `tools/SOUNDS.md`, Ein/Aus + Lautstärke in Optionen). Hintergrundmusik pro Zone bewusst nicht umgesetzt.
- [x] Offline-Fortschritt geprüft + Test ergänzt (siehe oben).
