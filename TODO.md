# TODO

Legende: `[ ]` offen · `[x]` erledigt

## Offen
- [ ] Alles Folgende wurde ohne baubares Android-SDK umgesetzt (dl.google.com gesperrt): `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` laufen lassen, Fehler beheben, dann auf dem Emulator prüfen: Sprachwechsel (System/DE/EN, alle Tabs), Sounds (Pegel/Latenz anhören), Offline-Dialog nach zurückdatiertem `lastActiveEpochMillis`, Update-Hinweis (nur mit Play-Store-Installation, Internal Test).
- [ ] Offline-Anzeige: Ursache der falschen Zeitanzeige nicht eindeutig reproduziert (Berechnung/Kappung/Stempel im Code und per Unit-Test korrekt). Behoben: Dauer ab 24 h wird in Tagen angezeigt statt "960 Std.". Falls auf dem Gerät weiter falsch: Screenshot des Dialogs + Zeit der letzten Sitzung notieren.
- [ ] Alle Apps: Update-Hinweis, Sprachprüfung, Akkuschonung analog in Kind Words und RPCT umsetzen (hier nur Idle RPG).

## Erledigt (Idle RPG)
- [x] Update-Hinweis (Play In-App Updates, flexibler Flow, `update/InAppUpdater`, `app-update` über Version Catalog, `strings_update.xml`, Datenschutz-Seite/In-App-Kurzfassung/Checkliste angepasst).
- [x] Sprachumschaltung geprüft: Auswahl System/DE/EN in Optionen vorhanden; Lücke behoben: "System" setzte `Locale.getDefault()` nach vorheriger Auswahl nicht zurück; Waffenname im Charakter-Tab war unlokalisiert.
- [x] Itemnamen lokalisiert (Shop, Beute-Popup, Charakter), Import-Dialog `Locale.getDefault()`, `SaveTransfer`-Meldungen mit lokalisiertem Itemnamen.
- [x] Akkuschonend: `collectAsStateWithLifecycle`, Shop-Ticker nur sichtbar, Kampfschleife/Ton im Hintergrund aus, Offline-Berechnung einmalig beim Start (kein Dauerlauf); keine Wakelocks/Alarme/Jobs im Projekt.
- [x] Sound (SoundPool, synthetisiert in `tools/generate_sounds.py`, Lizenz in `tools/SOUNDS.md`, Ein/Aus + Lautstärke in Optionen). Hintergrundmusik pro Zone bewusst nicht umgesetzt.
- [x] Offline-Fortschritt geprüft + Test ergänzt (siehe oben).
