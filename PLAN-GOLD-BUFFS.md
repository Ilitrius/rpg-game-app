# Plan: Gold-Senken (C) + Buff-System (B) – Entwurf 2026-11-02

Status: **Entwurf zur Freigabe.** Reihenfolge: B zuerst (Basis), dann C (nutzt Buffs als Elixiere).
Vertrag: nur additive Felder mit Default, `SCHEMA_VERSION` bleibt 1. Zahlen sind Startwerte, per Test kalibrieren.

## B. Buff-System

### Datenmodell
```kotlin
@Serializable data class BuffState(
    val active: List<ActiveBuff> = emptyList(),
)
@Serializable data class ActiveBuff(
    val type: String,          // "damage","xp","gold","drop","regen","click" (String: unbekannte werden ignoriert)
    val magnitude: Double,     // z. B. 0.25 = +25 %
    val remainingMillis: Long, // verbleibende AKTIVE Spielzeit
    val source: String = "",   // "elixir","daily","goal","boss"
)
```
`GameState.buffs: BuffState = BuffState()`.

### Regeln (`domain/buffs/Buffs.kt`, pure)
- Laufzeit zählt nur in aktiver Spielzeit: Abzug über die vom `AdventureViewModel` gemessenen Tick-Deltas, **nicht** über Wanduhr; offline und im Hintergrund läuft nichts ab (nicht missbrauchbar, kein Uhr-Trick).
- Stapeln: gleicher Typ → Laufzeit der stärkeren Stufe wird aufgefrischt (kein Addieren der Stärke); verschiedene Typen laufen parallel. Summenobergrenze je Typ: +100 %.
- `Buffs.bonus(state, type): Double` = Multiplikator 1 + Σ; Anwendung an genau drei Stellen: `CombatStats.of` (damage, regen/click-Faktor), `CombatRules.applyKillReward` (xp, gold), `Loot` (drop).
- Offline-Fortschritt: Buffs werden **nicht** angewendet und laufen nicht ab.
- Max. 6 gleichzeitig, Restzeit auf 4 h begrenzt.

### Quellen
- Shop-Elixiere (Teil von C): +25 % XP/Gold/Schaden/Drop, 15 min, Preis ≈ 10–20 Kills der aktuellen Zone.
- Tagesbelohnung: ab Streak-Tag 3 zusätzlich ein 15-min-Buff. Ziele: einzelne Katalogeinträge mit Buff-Belohnung. Bosssieg: 5 min +10 % Schaden.

### UI
- Buff-Leiste im HUD (`ui/adventure`, Icons als Vektor, Restzeit-Ring), antippen → Detail-Sheet.
- Strings `strings_buffs.xml` (de/en).

## C. Gold-Senken

| Senke | Umsetzung | Agent |
|---|---|---|
| Elixiere | Shop-Reiter „Tränke“, kaufen → `Buffs.add` | shop |
| Inventar-Erweiterung | `Inventory.capacity(state)` statt `CAPACITY=40`; Stufen 40→60→80→100, Preis ×3 je Stufe; neues Feld `inventoryExtraSlots: Int = 0` | ausruestung |
| Verzaubern | Item `+1…+10` (`enchantLevel: Int = 0` im `Item`), Bonus +4 % Werte/Stufe, Kosten `price × 1.6^stufe`, kein Verlust bei Fehlschlag (Idle, kein Frust) | ausruestung |
| Reroll | Bonus-Verteilung eines Items neu würfeln, Kosten `price × 2`, Gesamtstärke bleibt, nur Verteilung ändert sich | ausruestung |
| Rast/Heilung | Nach Niederlage sofort heilen gegen Gold (Zone-Level-abhängig), Alternative: kostenlos warten | spielwelt |
| Schnellreise | Freigeschaltete Zonen direkt wechseln ist schon möglich → stattdessen **Boss-Skip-Ticket** entfällt; Punkt gestrichen, falls nicht gewünscht | – |
| Skins | Waffen-/Rahmen-Skins (rein kosmetisch), später | – |

Gold-Bilanz: `BalanceTest.offlineNightBuysSomeButNotAllShopOffers` weiterlaufen lassen; neue Senken dürfen Gold nicht als Währung entwerten (Preise an Zonen-Gold gekoppelt, nicht fix).

## Verteilung
1. Haupt-Thread: `BuffState`, `Item.enchantLevel`, `inventoryExtraSlots` im Vertrag.
2. charakter/spielwelt: `Buffs` + Anwendung in Kampf/Belohnung/Loot, HUD-Leiste, Regen/Heilung.
3. ausruestung: Kapazität, Verzaubern, Reroll (+ UI im Ausrüstungs-Tab).
4. shop: Tränke-Reiter, Preise.
5. optionen: Import-Validierung (`buffs.*`, `enchantLevel` 0..10, `inventoryExtraSlots` 0..60, unbekannte Buff-Typen verwerfen mit `ImportNote`).

## Tests
Alt-Save lädt; Buffs laufen nur mit Delta ab; Stapel- und Deckelregeln; Elixier-Kauf atomar in einem `repository.update`; Verzaubern deterministisch mit seeded Random; Kapazität steigt/Füllstand korrekt; `SaveTransferValidationTest` mit Buffs/Enchants.

## Offene Entscheidungen
1. Verzaubern ohne Fehlschlag-Risiko okay (Empfehlung) oder mit Risiko?
2. Reroll und Skins jetzt oder später (Empfehlung: Reroll jetzt, Skins später)?
3. Boss-Skip/Schnellreise streichen (Empfehlung: ja)?
