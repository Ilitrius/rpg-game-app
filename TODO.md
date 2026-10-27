# TODO

Legende: `[ ]` offen · `[x]` erledigt

## Offen
- [ ] Update-Hinweis: App merkt sich, wenn es ein Update gibt (Play In-App Updates API, `app-update`-Bibliothek über Version Catalog), und benachrichtigt den Nutzer (Hinweis/Snackbar mit "Aktualisieren"). Texte in `values/` + `values-de/`; Datenschutz/Data Safety prüfen (Play-Dienste-Abfrage). Gilt für alle Apps (Kind Words, RPCT, Idle RPG).
- [ ] Systemsprache umschalten ergänzen bzw. prüfen: Auswahl System/Deutsch/Englisch in den Optionen, und kontrollieren, dass wirklich alle Menüfelder und In-App-Texte (inkl. Benachrichtigungen, Datum/Zahlen) auf die gewählte Sprache wechseln. Gilt für alle Apps.
- [ ] Idle RPG, bei der Sprachprüfung gefunden: Shop (Kauf/Verkauf/Details/Vergleich) und Beute-Popup zeigen `item.name` (deutscher Fallback) statt `itemDisplayName`/`localizedName`; Import-Dialog formatiert Gold mit `Locale.GERMANY`; `SaveTransfer`-Meldungen nennen Items deutsch.
- [ ] Akkuschonend: App soll möglichst wenig Energie verbrauchen — keine unnötigen Hintergrund-Jobs/Wakelocks/Polling, Alarme nur wenn nötig (inexakt statt exakt), Coroutines/Flows nur bei sichtbarer UI sammeln (`collectAsStateWithLifecycle`), Animationen/Render-Schleifen im Hintergrund stoppen, Wiederholungen und Neuzeichnen minimieren. Per Profiler/Battery Historian prüfen. Gilt für alle Apps.
- [ ] Sound hinzufügen, wo es Sinn macht: Kampfgeräusche (Treffer, Monstertod), Beute-/Levelup-/Boss-Sounds, Shop-Kauf/Verkauf, UI-Klicks; ggf. leise Hintergrundmusik pro Zone. Lautstärke/Aus-Schalter in den Optionen (neues Feld in `GameSettings`, Import-Validierung beachten); bei App im Hintergrund stoppen (Akku); Offline-Fortschritt ohne Ton; hochwertige, lizenzfreie Audiodateien, Lizenz dokumentieren (Agent: spielwelt/optionen).
