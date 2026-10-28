# Idle RPG (Android)

Idle-RPG für Android. Charakter zieht durch Lande, tötet Monster automatisch, dargestellt aus Ich-Perspektive (First Person).
Kills geben XP (→ Stat-Punkte frei verteilbar; Stats bestimmen, wie schnell Monster sterben) und Gold (→ Ausrüstung im Shop).

## Grafik-Regel
- **Nur hochauflösende Grafik.** Keine Pixel-Art, keine Low-Res-Sprites, keine primitiven Platzhalter als finale Grafik.
- Vektor (VectorDrawable/SVG) bevorzugt; Bitmaps nur in xxxhdpi als WebP; Rendering mit `FilterQuality.High`. Zielauflösung 1080x2400+.

## Agenten pro Menü-Feld
Jedes Feld im Menüband hat eigenen Agenten in `.claude/agents/`: `charakter`, `ausruestung`, `shop`, `optionen`.
Zusätzlich `spielwelt`: Abenteuer-Ansicht (Ich-Perspektive), Gebiete, Monster, Kampfschleife, Belohnungen, Offline-Fortschritt.
- Jeder Agent besitzt sein Paket `ui/<feld>/` und nur seinen Zweig in `MainScreen.TabPanel` (`spielwelt`: nur `AdventureView`).
- Wert-Formeln (Schaden, Angriffstempo, HP, Krit) gehören `charakter`; `spielwelt` nutzt sie.
- Gemeinsamer Vertrag: `data/GameState.kt`, `data/GameRepository.kt` (`GameJson`). Nur abwärtskompatibel erweitern (Felder mit Default), nie umbenennen/entfernen.
- Strings pro Feld in eigener Datei (`res/values/strings_<feld>.xml`), damit parallele Agenten nicht kollidieren.
- Theme (`ui/theme`), Datenmodell und `IdleRpgApp` gehören dem Haupt-Thread, nicht einem Feld-Agenten.

## Lernende Agenten
- Lernspeicher: `.claude/learnings/` — `shared.md` (für alle, voneinander lernen) + `<agent>.md` (eigene). Regeln in `README.md` dort.
- Jeder Agent liest vor dem Auftrag `shared.md` + eigene Datei, trägt danach neue Erkenntnisse ein und darf die eigene Definition in `.claude/agents/` weiterentwickeln.
- Haupt-Thread: Agenten-Aufträge immer mit Hinweis auf die Definition starten; nach jedem Bericht Lern-Änderungen prüfen; regelmäßig kuratieren (Duplikate zusammenführen, Bewährtes in Definitionen/CLAUDE.md übernehmen).

## Datenvertrag (Stand)
- `GameState`: `character`, `equipment`, `gold`, `starterWeaponGranted`, `world: WorldState` (Zone, freigeschaltete Zonen, Kills, `lastActiveEpochMillis`), `shop: ShopState`.
- `stats: StatsState` (`playTimeMillis`, `highestHit`, `goldEarned`, `tapCount`; Defaults, Alt-Saves laden): hochgezählt über `domain/stats/Stats` (monoton, saturierend). Gold in `CombatRules.applyKillReward`/`DailyReward.claim`/`Goals.claim`; Treffer/Taps/Spielzeit sammelt `AdventureViewModel` im Speicher und schreibt sie mit dem nächsten Beute-/Background-Save. Anzeige `ui/character/StatisticsCard`; Import prüft >= 0.
- `Item.damageMin/damageMax`: Grundschaden für Waffen.
- `IdleRpgApp.onCreate` gibt einmalig den Startdolch (`StarterItems.ensureStarterWeapon`).
- Kampf-API (`charakter`): `CombatStats.of(state)`, `rollHit()`, `attackIntervalMillis`, `Progression.applyXp`, `spendPoint`.
- Waffen-Grafik (`charakter`): `ui/character/weapon` — `weaponKindOf(item)`/`weaponLookOf(item)` (null→Faust, Startdolch/""→rostiger Dolch, dolch/kurzschwert/streitkolben/axt), `WeaponImage(item, rotationDegrees)`, `drawWeaponFitted`, `drawWeaponInHand` (Paper-Doll), `FirstPersonWeapon(weapon, attackTrigger, attackDurationMillis, onStabImpact)` (Stich/Hieb/Faustschlag, Treffer immer bei `WEAPON_IMPACT_FRACTION` = 0.38). `FirstPersonDagger`/`DaggerImage` bleiben als Kompatibilität. Faust = `FistPainter` (Vektordaten `VArt` in `VectorArt.kt`, Unterarm/Stulpe gemeinsam in `ArmArt`); Offscreen-Vorschau ohne Emulator: `FistArtTest` → `app/build/weapon-preview/*.svg`. Alle Waffen: gleiche Griffgeometrie (16 Einheiten breit, `gripCenter`), damit eine Hand alle hält.
- `Item.baseType` (Grafik/Name-Stamm, "" = Altbestand → Waffe=Dolch), `Item.itemLevel`, `Item.isNew` ("Neu"-Badge).
- Beute-API (`ausruestung`, `domain/equipment`): `ItemGenerator.roll(itemLevel, random, minRarity, rarityBoost, slot?)`, `Inventory.addToInventory(state, item)` (voll bei 40 → Auto-Verkauf für `price`; `tryAdd` liefert Info), `equip/unequip/sell/compare`, Füllstand `Inventory.fill()/freeSlots()/fillFraction()` → `InventoryFill` (OK/NEARLY_FULL ab ≤5 frei/FULL). Grafik: `ui/equipment/ItemIcon(item)`, `InventoryCapacityBanner`.
- Kampfschleife/Welt (`spielwelt`): `domain/world`, `domain/combat`, `ui/adventure/AdventureViewModel`. Offline: `OfflineProgress.EFFICIENCY` = 1/60, max. 8 h, Willkommen-Dialog erst ab 30 min (`OfflineReward.capped/heroTooWeak`).
- `WorldState` (spielwelt): `heroHp` (-1 = voll/unbekannt, 0 = nach Niederlage), `defeatedBossIds`, `bossDefeatedAtKills` (Zone→Kills beim letzten Bosssieg); Zonen: 100 (`Zones.all`): 5 handgemachte (wiese/wald/sumpf/gebirge/hoehle, IDs stabil) + 95 generierte aus 19 Biomen x 5 Stufen (`Biome`, ID `<biom>_<1..5>`, Level 40..990, `ZoneScaling` kalibriert mit `ZoneCalibrationTest`, `Zone.goldMultiplier`); Bosssieg schaltet die nächste Zone frei und wechselt automatisch dorthin (`WorldProgress.applyBossVictory`, letzte Zone `ende_5` bleibt); Grafik prozedural (`BiomeStyles` → `BiomeScene`, `ProceduralMonsters`), Namen aus `tools/gen_welten.py` in `strings_spielwelt_welten.xml` (de/en) + `WorldNames.kt`; Zonenliste `ZoneSheet` (LazyColumn, Biom-Header, `ZoneSheetModel`). Tippen auf den Gegner: `AdventureViewModel.onEnemyTapped()` = zusätzlicher `rollHit()`-Treffer (Timer unberührt, `applyTapHit`), Drops via `Loot` (nutzt `ItemGenerator`/`Inventory.tryAdd`).
- Shop-API (`shop`, `domain/shop/Shop`): `ShopState` (`offers`, `restockedAtEpochMillis`, `stockLevel`, `paidRestocks`); Kaufpreis berechnet (`Shop.buyPrice` = `price` × `markup(itemLevel)`, nie gespeichert); `ensureStock` (gratis alle 30 min / bei 3 Stufen Vorsprung, nur bei offenem Tab), `paidRestock`, `buy`, `isUpgrade`, `bulkSellCandidates`.
- Import-Validierung (`optionen`, `ui/options/SaveTransfer`): prüft alle GameState-Felder über `SaveField`/`SaveLocation`; neue Felder dem Optionen-Agenten melden. Unbekannte Zonen-IDs werden normalisiert statt abgelehnt.
- `progress: ProgressState` (Ziele, `domain/progress`, UI `ui/character/GoalsCard`, Strings `strings_ziele.xml`): `DailyReward` (pure, Zeit injiziert: 20-h-Fenster, Uhr-Rückstellung über `clockHighWaterMillis` -5 min gesperrt, Streak-Bruch >48 h, Gold ≈ 150 Kills der aktuellen Zone, +10 %/Tag bis Tag 7, ab Tag 7 Wochenbonus; nur lokaler Schutz) und `Goals` (Katalog `Goals.catalog` ~38 Ziele, Typen `kills/bosses/level/zones` als String, Fortschritt = Zähler − `baseline`, immer 3 aktive, geclaimte bleiben als `claimed` in der Liste; unbekannte Typen/IDs werden übersprungen bzw. beim Import mit `ImportNote.UnknownGoalsDropped` entfernt). Abholen immer ein `repository.update`.

## Stack
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material3
- AGP 8.7.3, Gradle 8.11.1 (Wrapper), JDK 17
- compileSdk 35, targetSdk 36, minSdk 26, Portrait
- Persistenz: DataStore + kotlinx.serialization (vorgesehen)
- Package (Quellcode/namespace): `com.idlerpg.game`; applicationId: `com.rp.IDLEGAMERP`

## Umgebung (Windows)
- JDK: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (User-Env `JAVA_HOME`)
- Android SDK: `D:\Android\Sdk` (User-Env `ANDROID_HOME`, in `local.properties`)
- Emulator AVD: `Pixel_API35` (Android 35, google_apis x86_64)
- Kein Android Studio installiert — Build/Run per CLI.

## Repo
- GitHub (öffentlich): https://github.com/Ilitrius/rpg-game-app, Branch `master`; Datenschutzerklärung via GitHub Pages (`docs/`) unter https://ilitrius.github.io/rpg-game-app/ (in Optionen verlinkt, `options_privacy_url`)
- Git-Identität nur repo-lokal gesetzt (Ilitrius / rene.puf@gmail.com)

## Befehle
- Build: `.\gradlew.bat assembleDebug`
- Emulator: `D:\Android\Sdk\emulator\emulator.exe -avd Pixel_API35`
- Install + Start: `adb install -r app\build\outputs\apk\debug\app-debug.apk; adb shell am start -n com.rp.IDLEGAMERP/com.idlerpg.game.MainActivity`
- Screenshot: `adb exec-out screencap -p > shot.png` (in cmd, nicht PowerShell — PS zerstört Binär-Pipe)
