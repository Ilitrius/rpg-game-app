# TODO Bugfix - Code-Review Idle-RPG (rc28)

Stand: nach Review-Runde 7. Ergebnis eines reinen Code-Reviews (nichts kompiliert oder ausgefuehrt). Hypothesen sind als solche markiert.

Status: `[ ]` offen, `[x]` erledigt, `[~]` verworfen / kein Bug.

**Zahlen:** offen 56 (K 6, F 35, P 15), erledigt/verworfen 13.

**Prioritaet:** K1, K2, K4, K5, K6 -> F15, F16, F30 -> P1/P2/P3/P4 -> F3, F1/F2, F32, F31, F37 -> F23, F26, F29 -> F38, F36 -> P5/P6/P9/P10/P13/P14 -> Rest.

## Geprueft (38 Dateien)

GameRepository, AdventureViewModel, MainActivity, AdventureScreen, Inventory, Shop, MainScreen, ItemGenerator, CombatStats, SaveTransfer, OptionsScreen, OfflineProgress, Loot, PlayGamesManager, CombatRules, WorldProgress, Buffs, GameSettings, build.gradle.kts, proguard-rules.pro, AndroidManifest, GameState, Elixirs, Prestige, PrestigeBonuses, Ranks, Goals, DailyReward, Progression, Monsters, Zones, ZoneScaling, Rest, AutoBossGate, RebirthGuard, Stats (domain), TapStats, Enchanting.

## Noch nicht geprueft

Biome, DurationParts, WeaponAction, ComboTracker, ItemNames, ItemBaseTypes, StarterItems, SoundManager, Achievement, res (games_ids.xml, keep.xml), Equipment/Shop/Character/Prestige/Rank-Screens, ItemIcon/ItemSprites, InAppUpdater, BackupReminder/DonationHint, TutorialOverlay, Biome-/Monster-/Weapon-Painter, FirstPerson*.

Naechste Kandidaten: Biome (Zones-Init: Biome.entries.size * STAGES >= 95?), ItemNames, ItemBaseTypes, StarterItems, WeaponAction, ComboTracker, DurationParts, res, SoundManager, Achievement, BackupReminder/DonationHint (F27), PrestigeScreen (F31), Equipment/Shop/Character-Screens, InAppUpdater, TutorialOverlay, ItemIcon/ItemSprites, Painter.

## Kritisch

- [ ] **K1** GameRepository: Decode-Fehler -> leeres `GameState()` -> naechstes `update()` ueberschreibt echten Save. Fix: Backup behalten, nicht ueberschreiben, Fehler von 'kein Save' unterscheiden.
- [ ] **K2** `GameRepository.state`: kein `.catch` fuer IOException/CorruptionException -> Absturz im viewModelScope-Collector.
- [ ] **K3** AdventureViewModel (und OptionsScreen Export/Import): `runCatching` um `repository.update` schluckt alle Fehler inkl. CancellationException, kein Logging (auch kein Crash-Logging im Projekt) -> stiller Belohnungsverlust.
- [ ] **K4** SaveTransfer-Import: keine Obergrenzen (Level, Stats, Gold, XP, Kills, Item-Bonus/Preis/Schaden/enchantLevel, Souls). Int/Long-Ueberlauf (`Stats.plus`, vitality*10, gold+x in applyKillReward/Goals.claim/DailyReward.claim/Inventory) -> negative HP/Gold, kaputter Save; Play-Games-Erfolge per manipuliertem Save sofort freischaltbar. `Item.bonus` nicht validiert. Level nur >= 1. Buffs aus Import bis +100 %/4 h. `prestige.soulsSpentTotal/souls/talents/goldTalentLevels` ungeprueft -> Respec erstattet manipuliertes soulsSpentTotal (Souls-Cheat); `Prestige.rebirth` rechnet (rebirths+1) als Int vor coerceAtMost.
- [ ] **K5** AdventureScreen: `pointerInput(vm)` haelt veraltetes `approach`-Animatable. Fix: `val currentApproach by rememberUpdatedState(approach)`.
- [ ] **K6** Int-Ueberlauf in Anzeigen: Long per `.toInt()` (Monster-HP max, XP/Gold kill_reward). Naeherung: Boss-Belohnung XP/Gold ueber Int.MAX ab ca. 100 Endlos-Tiers, normale Monster ab ca. 130-140, Boss-HP ab ca. 225 Tiers. Level 999 allein reicht nicht. Fix: Strings auf Long (%d) / NumberFormat.

## Fehler (mittel/niedrig)

- [ ] **F1** AdventureViewModel: Race - grantOfflineProgress (appScope) schreibt heroHp/lastSavedHp/_ui parallel zum Fight-Loop (Main); nicht @Volatile/synchronisiert.
- [ ] **F2** grantOfflineProgress liest `stats?.maxHp` ohne `statsReady.await()`.
- [ ] **F3** healNow(): Doppel-Tap bezahlt 2x (kein Guard; DefeatOverlay-Button ohne Debounce; `Rest.pay` selbst ist ok).
- [ ] **F4** Exploit: App waehrend Niederlage-Rast in den Hintergrund -> recoverFromDefeat() sofort.
- [ ] **F5** `claimable` (Tagesbelohnung) nur bei State-Aenderung neu berechnet.
- [ ] **F6** AdventureScreen: Tap-Schwung (attacks++) ohne pendingHit -> onStabImpact verbraucht Auto-Treffer.
- [ ] **F7** Loot-Popups: ab 4 in der Liste werden Drops nur visuell verworfen.
- [ ] **F8** SaveTransfer: currentZoneId muss in unlockedZoneIds liegen UND in Zones.all existieren (`Zones.byId` faellt still auf Wiese zurueck, currentZoneId bleibt aber unbekannt -> inkonsistent). Rang/Mythos nicht kreuzvalidiert: Rang >= 1 braucht bestLevel >= 999, Rang >= 11 braucht rebirths >= `Ranks.requiredRebirths(rank)`. bestLevel/bestZoneOrder Bereiche (0..999 / 0..100).
- [ ] **F9** SaveTransfer: doppelte Item-IDs nicht geprueft (nur Import-Risiko; `Enchanting.mapItem` trifft nur ersten Treffer).
- [x] **F10** Import-Lesen ok (readText begrenzt). Rest: kein Checksum im Export (niedrig).
- [ ] **F11** `CombatStats.rollHit` / `MonsterStats.rollDamage`: `nextInt(min, max+1)` Ueberlauf bei Int.MAX. Niedrig, nur Import.
- [ ] **F12** SaveTransfer: KDoc von DamageRangeReversed steht ueber DecimalOutOfRange (kosmetisch).
- [ ] **F13** Uhr-Exploits: Offline-Zeit (System.currentTimeMillis) wiederholbar durch Uhr vorstellen, Gratis-Shop-Restock; Tagesbelohnung: High-Water blockiert nur Zurueckdrehen, Vorstellen um 20 h je Claim unbegrenzt; im Code als 'rein lokal' dokumentiert.
- [ ] **F14** MainScreen: NavigationBarItem icon contentDescription = label (doppelt vorgelesen) -> null.
- [ ] **F15** Import ersetzt State, aber AdventureViewModel resettet Laufzeit-Zustand nur bei geaenderter Rebirth-Zahl (knownRebirths). Import mit gleicher Rebirth-Zahl behaelt Monster/Boss/pendingZone/heroHp des alten Laufs; RebirthGuard schuetzt nur bei geaenderter Rebirth-Zahl.
- [ ] **F16** PlayGamesManager.sync: unlock() asynchron, aber 'submitted' wird sofort gesetzt -> bei Netzfehler nie erneut gesendet. games_ids.xml Platzhalter pruefen; showAchievements ohne Failure-Listener.
- [ ] **F17** OptionsScreen: pendingImport per rememberSaveable (bis 1 MB -> TransactionTooLargeException).
- [ ] **F18** OptionsScreen: Clipboard-Export setzt lastExportEpochMillis wie Datei-Export -> Backup-Erinnerung verstummt. Relevanter wegen allowBackup=false (F28).
- [ ] **F19** OptionsScreen/GameSettings: Switch-Zeilen ohne toggleable-Zuordnung, Slider ohne contentDescription; Lautstaerke-Slider schreibt bei JEDEM Drag-Schritt -> nur bei onValueChangeFinished persistieren.
- [~] **F20** Erledigt/gering: `Elixirs.buy` prueft `blocker()` VOR dem Bezahlen -> kein Goldverlust. Rest harmlos (nur Boss +10 %/5 min wird ersetzt).
- [ ] **F21** `CombatRules.restoreHeroHp`: nach Niederlage gespeicherter heroHp=0 -> bei Neustart 1 HP; pruefen ob beabsichtigt.
- [ ] **F22** `WorldProgress.applyBossVictory`: erneuter Sieg ueber alten Boss setzt currentZone auf dessen Nachfolger; Design pruefen.
- [ ] **F23** build.gradle.kts: targetSdk 36 > compileSdk 35 mit AGP 8.7.3. compileSdk 36 + AGP >= 8.9.1.
- [ ] **F24** build.gradle.kts: isShrinkResources fehlt. Beim Aktivieren getIdentifier/ItemSprites.resFor/MonsterSprites per keep.xml pruefen.
- [ ] **F25** versionCode (28) und appVersionName (rc28) manuell doppelt; Release ohne keystore.properties wird still unsigniert gebaut.
- [ ] **F26** Release-Build (R8, isMinifyEnabled=true) ungetestet: kein Roundtrip-Test Save/Load/Import auf minified Build.
- [ ] **F27** DonationHint standardmaessig bei jedem Start + Stripe-Link in der App: Play-Richtlinien pruefen (Hypothese, nicht verifiziert). Auch pruefen: `resolveActivity` ohne `<queries>` liefert ab API 30 null.
- [ ] **F28** AndroidManifest: `allowBackup=false` -> weder Cloud-Backup noch Geraete-Uebernahme; Save weg bei Neuinstallation/Geraetewechsel, nur manueller Export (F18). Design entscheiden: dataExtractionRules fuer DataStore oder Play-Games-Cloud-Save. Zusatz: kein `<queries>`, keine explizite INTERNET-Permission (evtl. per Library-Merge: gemergtes Manifest pruefen), kein `android:localeConfig` (Komfort). exported nur Launcher: ok.
- [ ] **F29** Manifest/targetSdk 36: `screenOrientation=portrait` wird nach meinem Kenntnisstand ab Android 16 auf Displays >= 600 dp ignoriert -> Layout dort ungetestet, Recreate bei Rotation (F17). Auf Tablet/Foldable testen.
- [ ] **F30** GameState: Enums EquipmentSlot/Rarity im Save -> unbekannter Wert laesst gesamtes Decode scheitern (-> K1). Fix: coerceInputValues/Fallback oder String-IDs. SCHEMA_VERSION=1: Migration/Pruefung beim Laden pruefen.
- [ ] **F31** `Prestige.rebaselineGoals`: bei Rebirth neue Baselines fuer ALLE unclaimed Ziele -> fertige, nicht abgeholte Ziele verlieren Belohnung still. Pruefen, ob UI warnt/auto-claimt (PrestigeScreen ungelesen). Buffs und Gold werden ebenfalls geloescht (dokumentiert).
- [ ] **F32** `Elixirs.price` nutzt currentZone + auf Zone geklemmtes Level -> Reise in Wiese = fast gratis +25 % Buffs. Fix: hoechste freigeschaltete Zone/Level. Umgekehrt DailyReward/Goals-Reward ueber currentZone (Top-Zone vor Claim maximiert). Zusatz: `Rest.healCost` nutzt heroLevel ungeklemmt (anders als DailyReward/Elixirs) -> inkonsistent.
- [~] **F33** Herabgestuft: `MonsterScaling` `.roundToLong().toInt()` wrappt nur bei manipulierten Werten; regulaerer Monsterschaden bleibt <= ca. 2 Mio. Nur defensives Coerce empfohlen. `rollType`: nextInt(total) nur bei Datenfehler (Zone.init prueft nicht auf Gewicht > 0).
- [ ] **F34** Goals: Katalog endlich; geclaimte IDs nie neu angeboten -> nach Katalogende/Rebirth keine neuen Ziele (Design). `Buffs.SOURCE_DAILY/SOURCE_GOAL` evtl. ungenutzt.
- [ ] **F35** Wartbarkeit (niedrig): PrestigeBonuses-KDoc 'Wiring status' veraltet; 999 doppelt (RankConfig.UNLOCK_LEVEL vs Progression.MAX_LEVEL); data.Stats-Klasse vs domain.stats.Stats-Objekt; Elixirs.blocker dupliziert Buffs.add; Listen statt Sets. Zones.effective-KDoc sagt HP x1.10 / Schaden x1.06, Konstanten sind 1.05 / 1.03; AutoBossReason.LAST_ZONE nie verwendet; TapStats-KDoc 'API for combat loop (spielwelt)' veraltet.
- [ ] **F36** Balance-Knick Zone 5 -> 6 (Hypothese, zone-economy/Calibration-Test pruefen): handmade Zone 5 reward/gold 2.2, HP 4.55; generierte Zone 6: reward 1.91, gold 1.92, HP 5.5 -> weniger XP/Gold pro Kill und langsamere Kills als in Zone 5 (Gold erst ab Zone 7 > 2.2).
- [ ] **F37** AutoBossGate: `blockedUntilKills` ist nicht zonen-spezifisch (Kills der Zone X, gilt aber in Zone Y nach manueller Reise -> bis zu hunderte Kills Cooldown) und wird bei Rebirth/Import nur zurueckgesetzt, wenn das ViewModel `reset()` ruft (`observeLevel` resettet nur bei Level-Anstieg, Rebirth senkt Level). Pruefen ob AdventureViewModel bei Reise/Rebirth/Import `gate.reset()` ruft; sonst Zone-ID speichern.
- [ ] **F38** Enchanting: `effectiveBonus` rundet je Stat (roundToInt) -> kleine Werte profitieren nicht: Stat 1 nie, 2 erst ab +7, 3 ab +5, 5 ab +3, 10 ab +2; Gold wird bezahlt, Wert aendert sich evtl. nicht (gleiches bei kleinen Waffen-damageMin/Max). Fix: Vorschau/Warnung in UI oder ceil/min +1. Hypothese: nameSpec-Suffix (ItemNames) passt nach `reroll()` evtl. nicht mehr zur Stat-Verteilung.

## Performance

- [ ] **P1** GameRepository: jedes `update()` decodiert+encodiert den gesamten State; `prettyPrint=true` aus.
- [ ] **P2** `state` ist kalter Flow -> jeder Collector dekodiert einzeln. `stateIn`/`shareIn`.
- [ ] **P3** Buff-Tick jede Sekunde: `repository.update` (Decode+Encode+Disk+Emit); `Buffs.tick` legt jedes Mal neue Liste an.
- [ ] **P4** MainActivity: `playGames.sync(it)` bei jedem State-Emit, auch im Hintergrund. distinctUntilChanged + debounce + repeatOnLifecycle(STARTED).
- [ ] **P5** AdventureScreen: ganzer AdventureUiState an Hud/HeroBlock; bossTimeLeftMillis alle 8-100 ms.
- [ ] **P6** Zwei infiniteTransitions zeichnen Szene+Monster dauerhaft mit 60 fps, auch wenn ein Tab-Panel die Szene verdeckt.
- [ ] **P7** `floats`-Liste ohne Obergrenze, remove O(n). Cap ca. 20.
- [ ] **P8** death-LaunchedEffect laeuft auch bei monster==null; `createZoneScene` in remember auf Main-Thread.
- [ ] **P9** `Inventory.equipBest`: Score mehrfach, bis 3 Durchlaeufe, tiefer State-Vergleich; `Item.effectiveBonus` allokiert je Aufruf ein Stats-Objekt.
- [ ] **P10** `Inventory.isBetterThanEquipped` O(n) mehrfach pro Recomposition. Cachen.
- [ ] **P11** `ItemGenerator.rollStats`: Punkte einzeln verteilt (ca. 2100 Iterationen). Niedrig.
- [ ] **P12** (niedrig) `PrestigeBonuses.of(state)` billig; Mehrfachberechnung je Reward/Tap unkritisch. Teurer: `CombatStats.live()` je State-Emit; `Prestige.hasSpendableSouls` (12 pow) je Emit.
- [ ] **P13** OptionsScreen Import: `SaveTransfer.parse` 2x auf Main-Thread, bis 1 MB -> ANR-Risiko.
- [ ] **P14** Kein Baseline Profile / androidx.profileinstaller.
- [ ] **P15** (niedrig) `Prestige.runZoneOrder` O(n*m) via `Zones.all.firstOrNull` -> `Zones.byId(id).order` nutzen (Map existiert, Fallback Order 1 identisch); `Goals.needsRefill/pending` allokiert Set je Aufruf; `DailyReward.goldPerKillAt` berechnet alle Monster-Stats je Aufruf (UI) -> remember.

## Verworfen / OK bei Review

- [~] V1 'onBackground nie bei Tab-Wechsel': kein Bug.
- [~] V2 Item-ID-Kollision im Normalspiel: UUID; nur Import-Risiko (F9).
- [~] V3 Shop-Doppelkauf: sicher; kein Arbitrage.
- [~] V4 Shop shopLevel coerceIn: ok.
- [~] V5 OfflineProgress: Logik sauber; nur F13.
- [~] V6 Loot: konsistent mit Doku.
- [~] V7 OptionsScreen errorMessage deckt alle SaveError-Faelle ab.
- [~] V8 CombatRules/WorldProgress Kernlogik ok.
- [~] V9 ProGuard: kotlinx.serialization-Regeln reichen.
- [~] V10 GameSettings: Singleton thread-safe.
- [~] V11 Progression, Ranks, PrestigeBonuses, Prestige, DailyReward, Monsters, Manifest-exported: logisch ok.
- [~] V12 domain/Stats saturierend ok; RebirthGuard sauber (atomar, 'always' fuer Lifetime-Stats); TapStats Formeln/Caps ok (Klick max 4.0, Combo max 1.5); Rest.pay ok; Zones/ZoneScaling Katalog-Invarianten konsistent; Enchanting enchant/reroll atomar, Kosten ohne Compounding.
