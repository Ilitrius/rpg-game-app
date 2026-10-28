# Gemeinsame Erkenntnisse (alle Agenten)

## Umgebung & Werkzeuge
- [2026-10-12] Workflow-Worktrees starten auf `origin/master`, nicht auf lokalem master. Vor Arbeitsbeginn `git log --oneline -1 master` mit HEAD vergleichen, bei leerem Branch `git merge --ff-only master`. Haupt-Thread: vor parallelen Läufen pushen. — **Warum:** alle vier Agenten starteten zwei Commits zu alt (ohne Waffen, Shop, Lernspeicher).
- [2026-10-12] Worktree: `local.properties` fehlt; anlegen mit Vorwärts-Slashes `sdk.dir=D:/Android/Sdk` (Backslash = Escape in .properties), nicht committen. — **Warum:** kostete einen Build-Durchlauf.
- [2026-10-12] Bash-Tool lehnt in Worktrees komplexe Befehle ab (`cd`/`git -C` + Heredoc/mehrere Schritte). Dateien per Write/Edit, git-Befehle einzeln, Commit-Nachricht per `git commit -F <datei>` aus dem Scratchpad (auch in PowerShell; `-F -` mit Here-String scheitert). — **Warum:** mehrere Befehle abgelehnt.
- [2026-10-12] Zahlen in Dateien (SVG/JSON/CSV) mit `Locale.ROOT` formatieren. — **Warum:** deutsches System erzeugt sonst Dezimalkomma.
- [2026-10-12] Große Kotlin-Dateien nicht per Bash-Heredoc schreiben/anhängen (Git-Bash bricht bei langen Heredocs mit `unexpected EOF ... matching '` ab, nichts wird geschrieben) — Write/Edit-Tool nutzen. — **Warum:** Shop-Agent verlor dadurch einen Durchlauf.
- [2026-10-11] Screenshots nur über `cmd /c "adb exec-out screencap -p > datei.png"` — **Warum:** PowerShell-Pipes zerstören Binärdaten.
- [2026-10-11] Nav-Leiste bei 1080x2400: y=2250, Charakter x=125, Ausrüstung x=400, Shop x=680, Optionen x=950. Gebietsname oben links antippbar. — **Warum:** spart Ausprobieren.
- [2026-10-11] Clipboard über adb ist unzuverlässig; für Testdaten lieber temporären Code-Pfad nutzen. — **Warum:** Optionen-Agent konnte ungültige Eingaben nicht per adb einspielen.

## Testen auf dem Emulator
- [2026-10-12] Grafik ohne Emulator prüfen: `java.awt` fehlt in Android-Unit-Tests. Vektorgrafik als Daten bauen (Vorlage `ui/character/weapon/VectorArt.kt`), im JVM-Test als SVG exportieren (`SvgArt.kt`/`FistArtTest` → `app/build/weapon-preview/`), mit `chrome.exe --headless=new --screenshot=out.png --window-size=W,H file:///…/x.svg` rastern und ansehen. Grober Geometrie-Check geht auch mit Python/PIL. — **Warum:** deckte bei Faust und Inventar-Anzeige Fehler ohne Emulator auf.
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
- [2026-10-12] Sonderwerte von GameState-Feldern im KDoc dokumentieren (z. B. `heroHp` -1 = unbekannt/voll, 0 = nach Niederlage; `stockLevel` 0 = nie bestückt). — **Warum:** Import-Validierung leitet erlaubte Bereiche daraus ab; `heroHp` = 0 stand nur im ViewModel.
- [2026-10-12] `SaveTransferValidationTest` prüft, dass alle erzeugbaren Items und ein realistisch gespielter Spielstand importierbar bleiben. Schlägt er nach Änderungen an Item-Formeln/Loot/Shop fehl: Optionen-Agent informieren, Test nicht aufweichen. — **Warum:** Validierung und Generatoren gehören verschiedenen Agenten.
- [2026-10-12] Itemnamen nie in Satzschablonen mit Kasus einsetzen ("Vergleich mit %1$s" → "Vergleich mit Robuste Lederstiefel" ist falsch, Dativ fehlt). Neutral formulieren: "Angelegt: %1$s". Partizip-Meldungen ("%1$s gekauft") sind korrekt (Passiv). — **Warum:** generierte Namen tragen Nominativ-Adjektivendungen.

## Balance
- [2026-10-12] Wirtschaft: Online verdient der Referenz-Held pro Stunde das 2–3,5-fache des kompletten Shop-Sortiments, offline daher `OfflineProgress.EFFICIENCY` = 1/60 (Nacht = 26–46 % des Sortiments). Wer Monster-Gold (`MonsterScaling`), `Shop.markup` oder `ItemGenerator.sellPrice` ändert: `BalanceTest.offlineNightBuysSomeButNotAllShopOffers` laufen lassen, ggf. `EFFICIENCY` nachziehen. — **Warum:** 80.000 Gold nach Offline-Fortschritt, Shop wirkte billig.
- [2026-10-11] Ausrüstung wiegt ca. doppelt so viel wie Stufenaufstiege. Zonen-Multiplikatoren (Spielwelt) sind auf `ItemGenerator`-Konstanten (Ausrüstung) abgestimmt — eine Seite ändern heißt die andere neu abstimmen. — **Warum:** Balance-Analyse Spielwelt-Agent.

## Grafikqualität
- [2026-10-12] Freie HUD-Flächen in der Abenteueransicht (1080x2400): links unten über dem Heldenblock (bis 0,52 × Breite), rechts neben dem Helden-HP-Balken. Rechts unten liegt die ruhende Waffe. — **Warum:** Overlays dürfen weder Waffe noch Monster verdecken.
- [2026-10-11] Hochwertig wirkt: mehrere Verlaufsschichten, Glanzlichter, Schattierung, Bodenschatten, kleine Details (Rost, Nieten, Edelsteine), leichte Idle-Animation. Einfarbige Grundformen wirken billig. — **Warum:** User-Regel "nur hochauflösende Grafik"; diese Mittel haben bei Dolch, Monstern, Items überzeugt.
- [2026-10-11] Seltenheitsfarben einheitlich über `rarityColor()` (grau/grün/blau/lila/orange). — **Warum:** gleiche Sprache in Ausrüstung, Beute-Popup, Waffen-Glow.

## Ziele (Tagesbelohnung & Ziele)
- [2026-10-28] Neue Zähler-Ziele aus bestehenden GameState-Zählern ableiten (Zähler − `baseline`), nicht per Hook in der Kampfschleife: Offline-Fortschritt zählt automatisch. Katalog-Ziele relativ (Zuwachs) statt absolut, sonst sind sie für Veteranen sofort fertig. — **Warum:** keine Eingriffe in `spielwelt` nötig.
- [2026-10-28] Katalog-Reihenfolge nach Tiers verschränkt (je Tier ein Ziel pro Typ), "nächstes Ziel" ist der erste Katalogeintrag, der noch nicht in der Liste steht — das ist nicht zwingend derselbe Typ wie das geclaimte. Tests nicht auf "gleicher Typ rückt nach" auslegen.
- [2026-10-28] Neue Strings für Import-Validierung (Feldnamen, Orte, Notes) dürfen in der Feature-Stringdatei liegen (`strings_ziele.xml`), `OptionsStringsTest` prüft nur `strings_options.xml`.
- [2026-10-28] Cloud-Container (Linux, kein Android-SDK): SDK per `cmdline-tools` nach `/opt/android-sdk` holen (`sdkmanager "platforms;android-35" "build-tools;35.0.0"`), `local.properties` (gitignored) mit `sdk.dir=/opt/android-sdk`, Build mit `bash gradlew … -Pkotlin.compiler.execution.strategy=in-process` (Kotlin-Daemon scheitert dort), bei HTTP 429 von Maven kurz warten und wiederholen. Nach abgebrochenem Build ggf. `rm -rf app/build`. — **Warum:** kostete mehrere Fehlversuche.
- [2026-10-28] Beim Committen nie `git add app` / `-A`, wenn parallel andere Agenten im selben Arbeitsbaum Dateien ändern: Pfade einzeln angeben (Fremdänderungen landeten sonst im eigenen Commit). — **Warum:** Shop-Änderungen waren fast im Clicker-Commit.
- [2026-10-28] Bricht der Gesamtbuild wegen WIP-Dateien eines parallelen Agenten (z. B. fehlender Import in `ui/adventure`), eigene Änderungen in einer Kopie des Repos (`git ls-files | cp --parents`, fremde Dateien per `git show HEAD:` zurücksetzen) bauen, statt fremde Dateien anzufassen.

## Statistiken
- [2026-10-28] Zähler ohne Extra-Schreibzugriffe: Gold in den reinen Reward-Funktionen (`applyKillReward`, `claim`) mitzählen, flüchtige Werte (Treffer, Taps, Spielzeit) im ViewModel sammeln und als State-Transform in den nächsten Beute-/Background-`repository.update` einhängen. Neue GameState-Felder brauchen `SaveField`/`SaveLocation` + Zeilen in `OptionsScreen` (`when` ist exhaustiv). Private Composable-Namen in `CharacterScreen` (z. B. `StatsCard`) beachten, sonst Namenskollision.
