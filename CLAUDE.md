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

## Datenvertrag (Stand)
- `GameState`: `character`, `equipment`, `gold`, `starterWeaponGranted`, `world: WorldState` (Zone, freigeschaltete Zonen, Kills, `lastActiveEpochMillis`).
- `Item.damageMin/damageMax`: Grundschaden für Waffen.
- `IdleRpgApp.onCreate` gibt einmalig den Startdolch (`StarterItems.ensureStarterWeapon`).
- Kampf-API (`charakter`): `CombatStats.of(state)`, `rollHit()`, `attackIntervalMillis`, `Progression.applyXp`, `spendPoint`.
- Dolch-Grafik (`charakter`): `ui/character/weapon` — `DaggerImage`, `FirstPersonDagger(attackTrigger, onStabImpact)`.
- `Item.baseType` (Grafik/Name-Stamm, "" = Altbestand → Waffe=Dolch), `Item.itemLevel`, `Item.isNew` ("Neu"-Badge).
- Beute-API (`ausruestung`, `domain/equipment`): `ItemGenerator.roll(itemLevel, random, minRarity, rarityBoost, slot?)`, `Inventory.addToInventory(state, item)` (voll bei 40 → Auto-Verkauf für `price`; `tryAdd` liefert Info), `equip/unequip/sell/compare`. Grafik: `ui/equipment/ItemIcon(item)`.
- Kampfschleife/Welt (`spielwelt`): `domain/world`, `domain/combat`, `ui/adventure/AdventureViewModel`.
- `WorldState` (spielwelt): `heroHp` (-1 = voll/unbekannt), `defeatedBossIds`, `bossDefeatedAtKills` (Zone→Kills beim letzten Bosssieg); Zonen: wiese/wald/sumpf/gebirge/hoehle, nächste Zone via Bosssieg (`WorldProgress`), Drops via `Loot` (nutzt `ItemGenerator`/`Inventory.tryAdd`).

## Stack
- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material3
- AGP 8.7.3, Gradle 8.11.1 (Wrapper), JDK 17
- compileSdk/targetSdk 35, minSdk 26, Portrait
- Persistenz: DataStore + kotlinx.serialization (vorgesehen)
- Package: `com.idlerpg.game`

## Umgebung (Windows)
- JDK: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` (User-Env `JAVA_HOME`)
- Android SDK: `D:\Android\Sdk` (User-Env `ANDROID_HOME`, in `local.properties`)
- Emulator AVD: `Pixel_API35` (Android 35, google_apis x86_64)
- Kein Android Studio installiert — Build/Run per CLI.

## Repo
- GitHub (privat): https://github.com/Ilitrius/rpg-game-app, Branch `master`
- Git-Identität nur repo-lokal gesetzt (Ilitrius / rene.puf@gmail.com)

## Befehle
- Build: `.\gradlew.bat assembleDebug`
- Emulator: `D:\Android\Sdk\emulator\emulator.exe -avd Pixel_API35`
- Install + Start: `adb install -r app\build\outputs\apk\debug\app-debug.apk; adb shell am start -n com.idlerpg.game/.MainActivity`
- Screenshot: `adb exec-out screencap -p > shot.png` (in cmd, nicht PowerShell — PS zerstört Binär-Pipe)
