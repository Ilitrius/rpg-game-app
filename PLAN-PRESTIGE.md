# Plan: Wiedergeburt (Prestige) – Entwurf 2026-11-02

Status: **Entwurf zur Freigabe, nichts umgesetzt.** Zahlen sind Startwerte; vor Freigabe per Simulationstest (wie `ZoneCalibrationTest`) kalibrieren.

## 1. Ziel
Spätes Spiel (Level ~100+, Zone 30+) bekommt einen Neustart mit dauerhaftem Gewinn. Held startet bei Level 1/Wiese, wird aber spürbar schneller. Spiel bleibt ohne Prestige voll spielbar (Opt-in).

## 2. Was wird zurückgesetzt / bleibt
| Zurückgesetzt | Bleibt |
|---|---|
| `character` (Level, XP, Punkte, Stats) | `prestige` (neu) inkl. Seelen, Talente |
| `equipment` (angelegt + Inventar) | `stats` (Lifetime-Statistik) |
| `gold`, `shop`, `starterWeaponGranted` → false (neuer Startdolch) | `progress` (Tagesbelohnung, Streak, Uhr-Schutz) |
| `world` (Zone, Freischaltungen, Kills, Bosse) | Geräteeinstellungen (`GameSettings`) |

Ziele-Baselines (`Goals`) werden beim Reset neu gesetzt, damit Zähler-Ziele nicht rückwärts laufen.

## 3. Datenmodell (nur additiv, Defaults)
```kotlin
@Serializable data class PrestigeState(
    val rebirths: Int = 0,            // Anzahl Wiedergeburten
    val souls: Long = 0,              // ungenutzte Seelen
    val soulsSpentTotal: Long = 0,    // fuer Respec/Statistik
    val talents: Map<String, Int> = emptyMap(), // Talent-ID -> Stufe (String-Keys, unbekannte IDs ignorieren)
    val bestZoneOrder: Int = 0,       // hoechste je erreichte Zone (Order)
    val bestLevel: Int = 0,
)
```
`GameState.prestige: PrestigeState = PrestigeState()`; `SCHEMA_VERSION` bleibt 1 (Alt-Saves laden mit Default). Talente als Map mit String-Schlüsseln (Lehre aus `Goals`: unbekannte Enum-Werte würden den ganzen Save zerstören).

## 4. Seelen-Formel
Seelen beim Reset (Vorschau live im Dialog):
`souls = floor( (level / 50)^1.5 + zoneOrder^1.3 / 4 )` über den **aktuellen** Lauf, minus bereits ausgezahlte Seelen des Laufs nicht nötig (Reset beendet den Lauf).
- Sperre: Wiedergeburt erst ab Level 50 **und** Zone 20 (verhindert Spam, Mindestgewinn ≥ 3 Seelen).
- Gedeckelt durch `MAX_LEVEL`/100 Zonen, Overflow-sicher (`Long`, saturierend).
- Diminishing: Seelen-Bonus wirkt als `1 + 0.02 × Seelen-Talentstufen` additiv, nicht multiplikativ → kein Exponentialschuss.

## 5. Talentbaum (Seelen ausgeben, 3 Äste, ~12 Talente)
Kosten pro Stufe: `base × (1 + stufe)^1.4`, Max-Stufen begrenzt.
- **Fortschritt:** +5 % XP/Stufe (max 20), +5 % Gold/Stufe (max 20), Start-Level (+2/Stufe, max 10), schnelleres Rasten nach Niederlage.
- **Kampf:** +3 % Schaden/Stufe (max 30), +2 % Angriffstempo (max 15), +4 % Max-HP (max 20), Krit-Multiplikator +0,05/Stufe.
- **Beute/Klick:** +Drop-Qualität (`rarityBoost`), +Inventarplätze (+2/Stufe), Klick-Schaden +5 %/Stufe, Combo-Dauer.
Alle Boni sind **Multiplikatoren auf die Endwerte**, angewendet an genau einer Stelle (`PrestigeBonuses.of(state)`), damit Balance prüfbar bleibt.
Respec: gratis 1× pro Wiedergeburt (Seelen zurück, nur außerhalb des Kampfes im Dialog).

## 6. Einbindung in bestehenden Code
- `domain/prestige/Prestige.kt` (pure): `canRebirth`, `soulsPreview`, `rebirth(state, now)` → neuer `GameState`, `buyTalent`, `respec`. Zeit/Zufall injiziert (testbar).
- `PrestigeBonuses` fließt ein in: `CombatStats.of` (Schaden/Tempo/HP/Krit), `CombatRules.applyKillReward` (XP/Gold), `Loot` (Drop), `Inventory` (Kapazität statt Konstante 40 → `capacity(state)`), `OfflineProgress` (nutzt dieselben Funktionen, keine eigene Formel).
- Start-Level: `rebirth()` ruft `Progression.applyXp` für das Level auf, Punkte inklusive (3/Level).
- Offline-Fortschritt: bleibt auf Zone/Level des neuen Laufs; `lastActiveEpochMillis` = Reset-Zeit.
- Import (`optionen`): `SaveField` für `prestige.*`: `rebirths/souls/... ≥ 0`, Talentstufen ≤ Max, unbekannte Talent-IDs verwerfen mit `ImportNote`, `bestZoneOrder ≤ 100`, `bestLevel ≤ 999`.
- Backup-Hinweis: Dialog bietet vor dem Reset „Spielstand exportieren“ an (bestehender Export), Bestätigung per 2 Stufen (Dialog + Halten/Texteingabe).

## 7. UI (Agent `charakter`, Paket `ui/character/prestige/`)
- Neue Karte „Wiedergeburt“ im Charakter-Tab (sichtbar ab Level 30, gesperrt/ausgegraut bis Bedingung erfüllt, zeigt Fortschritt zur Freischaltung).
- Dialog: Seelen-Vorschau, was verloren geht/bleibt, Backup-Button, Bestätigung.
- Talentbaum als eigener Screen/Sheet (Vektorgrafik, keine Platzhalter, `FilterQuality.High`).
- HUD: kleines Seelen-Symbol nur wenn Seelen ausgebbar sind (wie Hinweispunkt).
- Strings: `strings_prestige.xml` (de/en).

## 8. Verteilung auf Agenten
1. **Haupt-Thread:** `PrestigeState` in `GameState` (Vertrag), Version/Doku.
2. **charakter:** `domain/prestige`, `PrestigeBonuses` in `CombatStats`, Karte + Dialog + Talentbaum.
3. **spielwelt:** Reset der Welt, Boni in Belohnung/Loot/Offline, Zonen-Auto-Wechsel nach Reset prüfen, Kalibrierungstest für Lauf 2 und 3.
4. **ausruestung:** Kapazität über `capacity(state)`, Banner/Füllstand anpassen.
5. **shop:** Reset von `ShopState`, Test dass Neubestückung nach Reset korrekt ist.
6. **optionen:** Import-Validierung + `SaveTransferTest` + Alt-Save-Decode-Test.

## 9. Tests (Pflicht)
- Alt-Save ohne `prestige` lädt mit Default.
- `rebirth()` ist idempotent-sicher (zweiter Aufruf direkt danach: gesperrt).
- Reset behält `progress`, `stats`, `prestige`; setzt alles andere zurück; Startdolch wird neu vergeben.
- Seelenformel monoton, overflow-sicher, Vorschau == Auszahlung.
- Simulation: Lauf 2 erreicht Zone 20 in ≈ 50–60 % der Zeit von Lauf 1; Lauf 5 ≈ 25 %; kein Lauf trivialisiert Boss-Gate (`AutoBossGate`).
- Import: manipulierte Talentstufen/Seelen werden abgelehnt bzw. begrenzt.
- Atomar: Reset in **einem** `repository.update`; Kampfschleife pausiert währenddessen (kein Beute-Save zwischen Reset und Neustart).

## 10. Risiken
- Balance (größtes Risiko) → Simulationstest vor UI; Werte in einer Datei (`PrestigeConfig`).
- Save-Verlust durch Fehlklick → Backup-Angebot + Zwei-Stufen-Bestätigung.
- Ziele/Tagesbelohnung dürfen durch Reset nicht doppelt auszahlbar werden → `progress` bleibt unberührt, Baselines neu.
- Cloud-Umgebung hat kein Gerät: UI-Prüfung auf Emulator vor Release nötig.

## 11. Offene Entscheidungen (bitte klären)
1. Mindestanforderung (Level 50 + Zone 20) und Seelenkurve so ok, oder späteres Endgame-Gate (z. B. Zone 50)?
2. Soll Gold-Anteil (Seelen gegen Gold kaufen) rein, oder nur Seelen als Währung?
3. Respec gratis oder gegen Gold (Gold-Senke, Plan C)?
4. Reihenfolge: PLAN.md empfiehlt C/B vor A – A trotzdem jetzt?
