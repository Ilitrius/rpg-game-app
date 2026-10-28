# Play-Console-Checkliste (nach Ablehnung)

Quelle der Ablehnung: Programmrichtlinien + Play-Console-Anforderungen. Genauen Text in der Console unter "Richtlinienstatus" prüfen.

## Im Code behoben
- Spenden-Button mit externem Stripe-Link entfernt (Payments-Richtlinie: externe Zahlungslinks in der App).

- `allowBackup="false"` (Spielstand wird nicht in Google Drive gesichert → "nur lokal" in der Datenschutzerklärung stimmt).
- versionCode 10 / `0.1.0-rc9` für den Neueinreichungs-Build.
- Policy-Basis: https://play.google/developer-content-policy/ (das ist die Richtlinie, **keine** Datenschutzerklärungs-URL).

## Datenschutzerklärung
- `docs/index.html` ist öffentlich zu hosten (Repo ist privat → GitHub Pages geht nur mit öffentlichem Repo; Alternative: Google Sites / öffentlicher Gist-Host).
- URL eintragen: Play Console → Richtlinie → App-Inhalt → Datenschutzerklärung.

## App-Inhalt (alle Formulare ausfüllen & absenden)
- Datensicherheit: "Keine Daten erhoben" / "Keine Daten geteilt"; Verschlüsselung/Löschung nicht zutreffend.
- Werbung: "Enthält keine Werbung".
- Inhaltseinstufung (IARC): Fantasy-Gewalt gegen Monster, keine Glücksspiel-/Echtgeld-Elemente.
- Zielgruppe: 13+ (nicht "Kinder" wählen, sonst Families-Richtlinie).
- App-Zugriff: "Alle Funktionen ohne Anmeldung nutzbar".
- Kontaktdaten: E-Mail und ggf. Website im Store-Eintrag.
- Kategorie Spiele > Rollenspiel/Idle; keine Finanz-/Gesundheits-/Nachrichtendeklarationen nötig.

## Store-Eintrag
- Titel max. 30 Zeichen, keine Keyword-Listen, keine Wörter wie "Beste", "#1", "Gratis".
- Beschreibung ohne Vergleiche/Markennamen anderer Spiele, keine Fake-Bewertungen.
- Screenshots (in `design/screens`) zeigen echte App-Ansichten – ok. Feature-Grafik ohne irreführenden Text.

## Neues privates Entwicklerkonto
- Closed Test mit mindestens 12 Testern, 14 Tage durchgehend, dann "Produktionszugang beantragen".
- Entwicklerprofil verifizieren (Identität, Adresse, Telefon).

## Neuer Build
- versionCode erhöhen (aktuell 10), neues AAB hochladen.

## Datenschutz in der App
- Optionen → Karte "Datenschutz": Kurzfassung (Dialog, offline) + Button zur öffentlichen Erklärung `https://ilitrius.github.io/rpg-game-app/` (GitHub Pages aus `docs/`, Quelle `docs/index.html`). Dieselbe URL in der Play Console eintragen.
