# Optionen — eigene Erkenntnisse

- [2026-10-11] Datei-Import akzeptiert `application/json`, `text/plain` und `application/octet-stream`. — **Warum:** manche Dateimanager melden .json als octet-stream.
- [2026-10-11] Eingabe auf ~1 MB begrenzen, Parsen wirft nie, sondern liefert Ergebnis/Fehler. — **Warum:** keine Abstürze, kein Überschreiben bei Müll.
- [2026-10-11] Validierung fehlt noch für `Item.damageMin/Max` und `world`-Felder (negativ). — **Warum:** neue Felder anderer Agenten.
- [2026-10-11] Android 12+ zeigt eigenen "eingefügt"-Hinweis neben unserer Snackbar — normal. — **Warum:** nicht als Bug melden.
