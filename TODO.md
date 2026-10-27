# TODO

Legende: `[ ]` offen · `[x]` erledigt

## Offen
- [ ] Update-Hinweis: App merkt sich, wenn es ein Update gibt (Play In-App Updates API, `app-update`-Bibliothek über Version Catalog), und benachrichtigt den Nutzer (Hinweis/Snackbar mit "Aktualisieren"). Texte in `values/` + `values-de/`; Datenschutz/Data Safety prüfen (Play-Dienste-Abfrage). Gilt für alle Apps (Kind Words, RPCT, Idle RPG).
- [x] Sprachumschaltung geprüft (System/DE/EN): String-Parität vollständig. Behoben: Itemnamen in Shop (Kauf/Verkauf/Details/Vergleich) und Beute-Popup nutzten den deutschen Fallback-Namen statt der lokalisierten Variante; Import-Dialog formatierte Gold immer deutsch.
- [ ] Build/Emulator-Test der Sprachumschaltung in allen Menüfeldern (Charakter, Ausrüstung, Shop, Optionen, Abenteuer); `SaveTransfer`-Fehlermeldungen nennen Items noch mit deutschem Namen (qa-tester)
