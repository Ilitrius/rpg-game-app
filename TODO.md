# TODO

Legende: `[ ]` offen · `[x]` erledigt

## Offen
- [ ] Alles Folgende wurde ohne baubares Android-SDK umgesetzt (dl.google.com gesperrt): `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` laufen lassen, Fehler beheben, dann auf dem Emulator prüfen: Sprachwechsel (System/DE/EN, alle Tabs), Sounds (Pegel/Latenz anhören), Offline-Dialog nach zurückdatiertem `lastActiveEpochMillis`, Update-Hinweis (nur mit Play-Store-Installation, Internal Test).
- [ ] Offline-Anzeige: Ursache der falschen Zeitanzeige nicht eindeutig reproduziert (Berechnung/Kappung/Stempel im Code und per Unit-Test korrekt). Behoben: Dauer ab 24 h wird in Tagen angezeigt statt "960 Std.". Falls auf dem Gerät weiter falsch: Screenshot des Dialogs + Zeit der letzten Sitzung notieren.
- [ ] Alle Apps: Update-Hinweis, Sprachprüfung, Akkuschonung analog in Kind Words und RPCT umsetzen (hier nur Idle RPG).

## Offen: Neue, realistischere Grafik (Wunsch des Nutzers, Stand 2026-11-02)
Ziel: Die Grafik soll deutlich authentischer und realistischer aussehen. Items sind umgesetzt (siehe unten); für Monster und Hintergründe vor Beginn erst einen Plan zur Freigabe erstellen.
- [x] **Items, Inventar-/Shop-/Ausrüstungs-Symbole** (2026-11-02): alle 16 Item-Typen als Raytracing-Bilder (45 WebP, 1,6 MB, `tools/render_items`), `ItemSprites` + `ItemIcon` mit Vektor-Fallback. Emulator-Messung beim Inventar-Scrollen: 0 % statt 17 % ruckelnde Frames, +21 MB Speicher.
- [ ] **Items, Rest:** Waffen in der Hand (Ich-Perspektive `FirstPersonWeapon`, Paper-Doll, Charakter-Tab `WeaponImage`) noch Vektor; Elixier-Symbole im Shop; Feinschliff Lederkappe, Lederwams, Plattenstiefel (wirken noch einfach); Edelstein-Funkeln.
- [ ] **Monster**: `ProceduralMonsters` und Boss-Grafiken deutlich realistischer statt Cartoon-/Vektor-Stil.
- [ ] **Hintergründe**: `BiomeStyles` / `BiomeScene` (100 Zonen, 19 Biome) deutlich realistischer.
- Offene Fragen für den Plan: Bisherige Grafik-Regel (CLAUDE.md) bevorzugt Vektor und prozedurale Zeichnung; für Realismus sind vermutlich Bitmaps nötig (nur xxxhdpi, WebP, `FilterQuality.High`). Dann Quelle der Bilder klären (Lizenz, KI-generiert, Stil einheitlich), APK-/Bundle-Größe (aktuell ca. 6,7 MB) und Ladezeit/Speicher prüfen, Reihenfolge festlegen (z. B. erst eine Zone + ein Monster + eine Waffe als Stilprobe).

## Offen: Tester-Feedback (Testers Community, Closed Test, Stand 2026-11-02; Testende ca. 2026-11-12)
Quelle: bezahlter Testanbieter, Reports `rp_feedback_de.pdf` + `rp_production_de.pdf`. Ergebnis: keine Abstürze/Fehler auf allen getesteten Geräten. Alle vier Punkte sind seit rc16 umgesetzt (Tutorial, „App bewerten“, „App teilen“, ASO-Text); aktueller Stand ist rc18 (siehe CLAUDE.md).

**Hinweis:** Die vorformulierten Antworten 3, 8, 10 im Produktionszugangs-Fragebogen (Tutorial, "App bewerten", "App teilen") stimmen seit rc16. Trotzdem vor dem Absenden mit dem Stand der hochgeladenen Version abgleichen (falsche Angaben gefährden Produktionszugang/Konto). Antwort 1 darf den bezahlten Testanbieter ehrlich nennen.

- [x] **Tutorial / Onboarding** (rc16: `ui/tutorial`, 6 Schritte mit Hervorhebung, überspringbar, Optionen → Hilfe → „Einführung erneut ansehen“; `GameSettings.tutorialSeen`, alle Spieler sehen es einmal; Spendenbitte/Backup-Erinnerung nicht beim selben Start. Offen: auf Gerät prüfen, ob die Monster-Hervorhebung (geschätzte Fläche) gut sitzt) (Einführung beim ersten Start, überspringbar, in Optionen erneut aufrufbar). Analyse: wichtigster Punkt, weil Idle-RPG mit Stat-Punkten, Ausrüstung, Shop und Tippen für neue Nutzer nicht selbsterklärend ist. Umfang: kurze Schritte (Kampf läuft automatisch, Tippen = Schaden, Stat-Punkte verteilen, Ausrüstung, Shop, Offline-Fortschritt), Hervorhebung der Nav-Leiste. Neues Feld `tutorialSeen` in `GameState`/Settings mit Default `false` (abwärtskompatibel; Import-Validierung `SaveTransfer` + Alt-Spielstand-Test beachten, oder besser in `GameSettings` statt `GameState`). Nicht mit Start-Dialogen kollidieren lassen (Spendenbitte `DonationHint`, Offline-Willkommen, Backup-Erinnerung `BackupReminder`): Tutorial zuerst, Spendenbitte erst danach. Strings in `strings_tutorial.xml` (DE+EN). Aufwand: mittel.
- [x] **"App bewerten"** (rc16, `ui/options/AppLinks`, Karte „Bewerten & teilen“; Build/Emulator-Check offen) in Optionen. Analyse: Intent auf `market://details?id=com.rp.IDLEGAMERP`, Fallback `https://play.google.com/store/apps/details?id=com.rp.IDLEGAMERP`. Keine Berechtigung, kein SDK, keine Datenübertragung, Datenschutztexte bleiben. Optional später In-App-Review-API (`play-review`), erst nach Meilenstein (z. B. erste Zone abgeschlossen), nie als Pflicht. Strings in `strings_options.xml` (DE+EN). Aufwand: klein.
- [x] **"App teilen"** (rc16, wie oben) in Optionen. Analyse: `Intent.ACTION_SEND` (`text/plain`) mit Store-Link, Chooser; keine Berechtigung. Optional "Link kopieren". Ermunternder Hinweis nach Meilenstein nur sparsam, um nicht mit Spendenbitte zu konkurrieren. Aufwand: klein.
- [x] **ASO (Store-Beschreibung)**: Text DE+EN in `docs/store-listing.md` (in die Play Console einfügen).
  Analyse: nur Play-Console-Text, kein Code. Keywords natürlich einbauen (Idle RPG, Idle-Rollenspiel, Ich-Perspektive, Beute, Ausrüstung, offline), ausführlicher auf Gameplay/Charakter/Alleinstellung eingehen. Regeln aus `docs/play-console-checkliste.md` beachten: keine Keyword-Listen, keine Wörter wie "Beste"/"#1"/"Gratis", keine Vergleiche mit anderen Spielen. DE + EN.
- [ ] rc18 (signiertes Bundle gebaut, `Idle-RPG-0.1.0-rc18-release.aab`) auf Gerät prüfen und in den Closed Test hochladen. Dann Fragebogen-Antworten gegenprüfen, `docs/play-console-checkliste.md` + `PLAN.md` (Anforderungstabelle) nachziehen.

**Zusätzliche Empfehlungen des Reports (unverbindlich, Einordnung):**
- [ ] Feedback-Kanal in der App (z. B. Mail-Intent "Problem melden" mit Geräte-/Versionsinfo): klein, ohne Berechtigung; sinnvoll.
- [ ] Saisonale Events / regelmäßige Inhalts-Updates: Roadmap-Material (siehe Prestige, Buffs, Gold-Senken unten); noch nicht konzipiert.
- [ ] Community (Discord/Forum): kein Code; erst nach Produktionsfreigabe sinnvoll, Link dann in Optionen.
- [ ] Visuelle Verbesserungen: laufend, Grafik-Regel aus `CLAUDE.md` gilt.

## Offen: Lint
- [x] `lintDebug` grün (rc16): `androidx.fragment:fragment-ktx` 1.8.5 ergänzt (Play app-update zog altes Fragment, `InAppUpdater` braucht >= 1.3.0 für die ActivityResult-API); `app_name` als `translatable="false"` (Markenname, gleich in DE/EN).
- [x] rc16: Sprach-Split im App Bundle abgeschaltet (`bundle.language.enableSplit = false`), sonst fehlt bei Play-Installationen die zweite Sprache für die Sprachwahl in den Optionen (Lint `AppBundleLocaleChanges`).

## Später geplant (Ideen, noch nicht umgesetzt)
- [x] (erledigt, siehe unten) **Charakter-Reset / Wiedergeburt (Prestige):** Held zurücksetzen (Level, Zonen, Gold, Inventar) gegen permanente Boni und neue Eigenschaften. Vorschlag: Währung „Seelen“ aus Level/höchster Zone; permanente Boni (+% XP, +% Gold, +% Schaden, Start-Level, schnelleres Rasten) und freischaltbare Eigenschaften (Talentbaum, z. B. Krit-Spezialisierung, Beute-Glück, Klick-Meister). Greift stark in `GameState`/Zonen/Import ein: erst Konzept (Formeln, Balance, Save-Migration, Import-Validierung), dann Umsetzung durch `charakter` + `spielwelt`. Spielstand-Backup vor dem Reset anbieten.
- [x] (erledigt 2026-11-02, siehe CLAUDE.md) **Buff-System:** zeitlich begrenzte oder kampfgebundene Buffs (z. B. +Schaden, +XP, +Gold, +Dropchance, Regeneration, Klick-Boost). Quellen: Shop (Tränke/Elixiere), Tagesbelohnung, Ziele, Bosssiege. Anzeige als Buff-Leiste im HUD mit Restzeit; Laufzeit nur in Spielzeit zählen (nicht offline missbrauchbar), Stapelregeln, Persistenz in `GameState` (Felder mit Default).
- [x] (erledigt 2026-11-02, Skins bewusst später, Schnellreise gestrichen) **Gold-Senken (Geld sinnvoll ausgeben):** Buff-Elixiere im Shop, Ausrüstung verzaubern/aufwerten (Gold → +Stufe, steigende Kosten), Inventar-Erweiterung (mehr als 40 Plätze), Reroll von Item-Werten, Shop-Nachschub (gibt es schon, `paidRestock`), Rast/Heilung nach Niederlage, Zonen-Schnellreise, Prestige-Boni mit Gold-Anteil kaufen, kosmetische Waffen-/Rahmen-Skins.
- [ ] **Google Play Games**: umgesetzt sind Anmeldung und 10 Erfolge (`games/PlayGamesManager`, Optionen-Karte); **offen:** Play-Console-Einrichtung (Projekt-ID und Erfolgs-IDs in `games_ids.xml` sind Platzhalter), Bestenliste, Cloud-Spielstand. Ursprüngliche Planung: Punkte/Bestenliste (Level, höchste Zone, Kills), Erfolge (aus den Zielen ableitbar), **Cloud-Spielstand** über Play Games „Saved Games“ (Konflikt: neuerer Stand/Zeitstempel, vorher manuelles Backup). Folgen: INTERNET-Berechtigung und Play-Games-Services-SDK, Konsolen-Einrichtung (Play Games Services-Projekt, OAuth-Client, Erfolge/Bestenlisten anlegen), Datenschutzerklärung und Datensicherheitsformular ändern (Konto-ID/Spieldaten werden an Google übertragen), Consent-Dialog (Opt-in, Spiel muss ohne Anmeldung voll nutzbar bleiben), `allowBackup=false` bleibt. Anmeldung optional unter Optionen → Konto.

## Erledigt (Idle RPG)
- [x] Prestige/Wiedergeburt umgesetzt 2026-11-02 (siehe CLAUDE.md), auf Gerät zu prüfen: Reset im Charakter-Tab auslösen und zurück zur Abenteuer-Ansicht, Dialoge/Talentbaum, Backup-Export.
- [x] Buff-System (B) und Gold-Senken (C: Inventar-Erweiterung, Verzaubern, Reroll, Rast/Heilung, Elixiere) – umgesetzt 2026-11-02, auf Gerät zu prüfen; Skins bewusst später, Schnellreise gestrichen.
- [x] Update-Hinweis (Play In-App Updates, flexibler Flow, `update/InAppUpdater`, `app-update` über Version Catalog, `strings_update.xml`, Datenschutz-Seite/In-App-Kurzfassung/Checkliste angepasst).
- [x] Sprachumschaltung geprüft: Auswahl System/DE/EN in Optionen vorhanden; Lücke behoben: "System" setzte `Locale.getDefault()` nach vorheriger Auswahl nicht zurück; Waffenname im Charakter-Tab war unlokalisiert.
- [x] Itemnamen lokalisiert (Shop, Beute-Popup, Charakter), Import-Dialog `Locale.getDefault()`, `SaveTransfer`-Meldungen mit lokalisiertem Itemnamen.
- [x] Akkuschonend: `collectAsStateWithLifecycle`, Shop-Ticker nur sichtbar, Kampfschleife/Ton im Hintergrund aus, Offline-Berechnung einmalig beim Start (kein Dauerlauf); keine Wakelocks/Alarme/Jobs im Projekt.
- [x] Sound (SoundPool, synthetisiert in `tools/generate_sounds.py`, Lizenz in `tools/SOUNDS.md`, Ein/Aus + Lautstärke in Optionen). Hintergrundmusik pro Zone bewusst nicht umgesetzt.
- [x] Offline-Fortschritt geprüft + Test ergänzt (siehe oben).
