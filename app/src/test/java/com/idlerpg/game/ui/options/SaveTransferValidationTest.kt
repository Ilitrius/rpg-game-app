package com.idlerpg.game.ui.options

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.GoalState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.ProgressState
import com.idlerpg.game.data.StatsState
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.character.StatType
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.domain.progress.DailyReward
import com.idlerpg.game.domain.progress.Goals
import com.idlerpg.game.domain.shop.Shop
import com.idlerpg.game.domain.world.Loot
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Import validation of the newer GameState fields (weapon damage, item level, world, shop). */
class SaveTransferValidationTest {

    private val dagger = Item(
        id = "w1", name = "Gehärteter Dolch", slot = EquipmentSlot.WEAPON, rarity = Rarity.UNCOMMON,
        bonus = Stats(dexterity = 3), price = 30, damageMin = 4, damageMax = 7, baseType = ItemBaseTypes.DAGGER, itemLevel = 5,
    )
    private val helm = Item(
        id = "h1", name = "Eisenhelm", slot = EquipmentSlot.HELMET, bonus = Stats(vitality = 2), price = 12,
        baseType = ItemBaseTypes.IRON_HELM, itemLevel = 3,
    )
    private val axe = Item(
        id = "a1", name = "Axt", slot = EquipmentSlot.WEAPON, price = 20, damageMin = 5, damageMax = 14,
        baseType = ItemBaseTypes.AXE, itemLevel = 4,
    )
    private val ring = Item(
        id = "r1", name = "Silberring", slot = EquipmentSlot.RING, bonus = Stats(luck = 2), price = 9,
        baseType = ItemBaseTypes.SILVER_RING, itemLevel = 2,
    )

    private val base = GameState(
        character = CharacterState(name = "Aria", level = 6, xp = 120, stats = Stats(8, 7, 6, 5)),
        equipment = EquipmentState(
            equipped = mapOf(EquipmentSlot.WEAPON to dagger, EquipmentSlot.HELMET to helm),
            inventory = listOf(ring, axe),
        ),
        gold = 450,
        starterWeaponGranted = true,
        world = WorldState(
            currentZoneId = Zones.WALD_ID,
            unlockedZoneIds = listOf(Zones.WIESE_ID, Zones.WALD_ID),
            totalKills = 80,
            zoneKills = mapOf(Zones.WIESE_ID to 60L, Zones.WALD_ID to 20L),
            lastActiveEpochMillis = 1_760_000_000_000L,
            heroHp = 57,
            defeatedBossIds = listOf(Zones.WIESE_ID),
            bossDefeatedAtKills = mapOf(Zones.WIESE_ID to 55L),
        ),
        shop = ShopState(
            offers = listOf(helm.copy(id = "o1"), axe.copy(id = "o2", name = "Scharfe Axt"), ring.copy(id = "o3")),
            restockedAtEpochMillis = 1_759_999_000_000L,
            stockLevel = 6,
            paidRestocks = 2,
        ),
        progress = ProgressState(
            dailyLastClaimEpochMillis = 1_759_900_000_000L,
            dailyStreak = 3,
            clockHighWaterMillis = 1_760_000_000_000L,
            goals = listOf(
                GoalState("kills_25", "kills", 25, baseline = 10, claimed = true),
                GoalState("bosses_1", "bosses", 1, baseline = 0),
            ),
            goalsCompleted = 1,
        ),
        stats = StatsState(playTimeMillis = 7_200_000L, highestHit = 1234, goldEarned = 98_765, tapCount = 321),
    )

    private val equippedWeapon = SaveLocation.EquippedItem(EquipmentSlot.WEAPON, "Gehärteter Dolch")
    private val secondInventoryItem = SaveLocation.InventoryItem(2, "Axt")
    private val secondOffer = SaveLocation.ShopOffer(2, "Scharfe Axt")

    // ------------------------------------------------------------------ helpers

    /** Runs the full import path (JSON text → decode → validate). */
    private fun rejected(state: GameState): SaveError {
        val result = SaveTransfer.parse(SaveTransfer.encode(state))
        assertTrue("expected Invalid but was $result", result is ImportResult.Invalid)
        return (result as ImportResult.Invalid).error
    }

    private fun accepted(state: GameState): ImportResult.Valid {
        val result = SaveTransfer.parse(SaveTransfer.encode(state))
        assertTrue("expected Valid but was $result", result is ImportResult.Valid)
        return result as ImportResult.Valid
    }

    private fun GameState.withWeapon(transform: (Item) -> Item) =
        copy(equipment = equipment.copy(equipped = equipment.equipped + (EquipmentSlot.WEAPON to transform(dagger))))

    private fun GameState.withSecondInventoryItem(transform: (Item) -> Item) =
        copy(equipment = equipment.copy(inventory = listOf(ring, transform(axe))))

    private fun GameState.withSecondOffer(transform: (Item) -> Item) =
        copy(shop = shop.copy(offers = shop.offers.mapIndexed { i, item -> if (i == 1) transform(item) else item }))

    private fun GameState.withWorld(transform: (WorldState) -> WorldState) = copy(world = transform(world))

    private fun GameState.withShop(transform: (ShopState) -> ShopState) = copy(shop = transform(shop))

    // ------------------------------------------------------------------ baseline

    @Test
    fun baseFixtureIsAcceptedUnchanged() {
        val valid = accepted(base)
        assertEquals(base, valid.state)
        assertTrue(valid.notes.isEmpty())
    }

    // ------------------------------------------------------------------ items: damage

    @Test
    fun negativeDamageMinIsRejected() {
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MIN, equippedWeapon),
            rejected(base.withWeapon { it.copy(damageMin = -1) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MIN, secondInventoryItem),
            rejected(base.withSecondInventoryItem { it.copy(damageMin = -3) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MIN, secondOffer),
            rejected(base.withSecondOffer { it.copy(damageMin = -3) }),
        )
    }

    @Test
    fun negativeDamageMaxIsRejected() {
        // min 0 / max -2: the sign check comes before the order check.
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MAX, equippedWeapon),
            rejected(base.withWeapon { it.copy(damageMin = 0, damageMax = -2) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MAX, secondInventoryItem),
            rejected(base.withSecondInventoryItem { it.copy(damageMax = -1) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.DAMAGE_MAX, secondOffer),
            rejected(base.withSecondOffer { it.copy(damageMax = -1) }),
        )
    }

    @Test
    fun reversedDamageRangeIsRejected() {
        assertEquals(
            SaveError.DamageRangeReversed(equippedWeapon, 9, 4),
            rejected(base.withWeapon { it.copy(damageMin = 9, damageMax = 4) }),
        )
        assertEquals(
            SaveError.DamageRangeReversed(secondInventoryItem, 3, 0),
            rejected(base.withSecondInventoryItem { it.copy(damageMin = 3, damageMax = 0) }),
        )
        assertEquals(
            SaveError.DamageRangeReversed(secondOffer, 20, 19),
            rejected(base.withSecondOffer { it.copy(damageMin = 20, damageMax = 19) }),
        )
    }

    @Test
    fun noDamageAndFixedDamageAreAccepted() {
        // 0..0 = legacy weapon / armor, 0..x = minimum coerced by CombatStats, x..x = fixed damage.
        for ((min, max) in listOf(0 to 0, 0 to 7, 5 to 5, 1 to 2)) {
            val state = base.withWeapon { it.copy(damageMin = min, damageMax = max) }
            accepted(state)
            CombatStats.of(state).rollHit(Random(1)) // must not crash on the accepted range
        }
    }

    // ------------------------------------------------------------------ items: level & price

    @Test
    fun itemLevelOutsideGeneratorRangeIsRejected() {
        val max = ItemGenerator.MAX_ITEM_LEVEL.toLong()
        assertEquals(
            SaveError.OutOfRange(SaveField.ITEM_LEVEL, equippedWeapon, 1, max, 0),
            rejected(base.withWeapon { it.copy(itemLevel = 0) }),
        )
        assertEquals(
            SaveError.OutOfRange(SaveField.ITEM_LEVEL, secondInventoryItem, 1, max, -4),
            rejected(base.withSecondInventoryItem { it.copy(itemLevel = -4) }),
        )
        assertEquals(
            SaveError.OutOfRange(SaveField.ITEM_LEVEL, secondOffer, 1, max, max + 1),
            rejected(base.withSecondOffer { it.copy(itemLevel = ItemGenerator.MAX_ITEM_LEVEL + 1) }),
        )
    }

    @Test
    fun itemLevelBoundsAreAccepted() {
        accepted(base.withWeapon { it.copy(itemLevel = 1) })
        accepted(base.withSecondOffer { it.copy(itemLevel = ItemGenerator.MAX_ITEM_LEVEL) })
    }

    @Test
    fun negativePriceNamesTheItem() {
        assertEquals(
            SaveError.NegativeValue(SaveField.PRICE, SaveLocation.EquippedItem(EquipmentSlot.HELMET, "Eisenhelm")),
            rejected(base.copy(equipment = base.equipment.copy(equipped = base.equipment.equipped + (EquipmentSlot.HELMET to helm.copy(price = -5))))),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.PRICE, secondInventoryItem),
            rejected(base.withSecondInventoryItem { it.copy(price = -1) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.PRICE, secondOffer),
            rejected(base.withSecondOffer { it.copy(price = -1) }),
        )
        accepted(base.withSecondOffer { it.copy(price = 0) })
    }

    @Test
    fun negativeValueInRawJsonIsFound() {
        // Hand-edited file: the first "damageMax" in the file belongs to the equipped weapon.
        val json = SaveTransfer.encode(base).replaceFirst("\"damageMax\": 7", "\"damageMax\": -7")
        val result = SaveTransfer.parse(json) as ImportResult.Invalid
        assertEquals(SaveError.NegativeValue(SaveField.DAMAGE_MAX, equippedWeapon), result.error)
    }

    @Test
    fun longItemNamesAreShortenedInErrors() {
        val longName = "Sagenhafter Dolch " + "der Ewigkeit ".repeat(30)
        val error = rejected(base.withWeapon { it.copy(name = longName, price = -1) }) as SaveError.NegativeValue
        val name = (error.location as SaveLocation.EquippedItem).itemName
        assertEquals(SaveTransfer.MAX_NAME_LENGTH, name.length)
        assertTrue(name, name.startsWith("Sagenhafter Dolch der") && name.endsWith("…"))
        assertEquals("Eisenhelm", SaveTransfer.shortName("  Eisenhelm "))
    }

    // ------------------------------------------------------------------ world

    @Test
    fun heroHpBelowMinusOneIsRejected() {
        assertEquals(
            SaveError.BelowMinimum(SaveField.HERO_HP, SaveLocation.World, -1, -2),
            rejected(base.withWorld { it.copy(heroHp = -2) }),
        )
        assertEquals(
            SaveError.BelowMinimum(SaveField.HERO_HP, SaveLocation.World, -1, Int.MIN_VALUE.toLong()),
            rejected(base.withWorld { it.copy(heroHp = Int.MIN_VALUE) }),
        )
    }

    @Test
    fun heroHpUnknownOrDefeatedIsAccepted() {
        accepted(base.withWorld { it.copy(heroHp = -1) }) // unknown = full
        accepted(base.withWorld { it.copy(heroHp = 0) }) // saved right after a defeat
    }

    @Test
    fun negativeWorldCountersAreRejected() {
        val world = SaveLocation.World
        assertEquals(SaveError.NegativeValue(SaveField.TOTAL_KILLS, world), rejected(base.withWorld { it.copy(totalKills = -1) }))
        assertEquals(
            SaveError.NegativeValue(SaveField.LAST_ACTIVE, world),
            rejected(base.withWorld { it.copy(lastActiveEpochMillis = -1) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.ZONE_KILLS, SaveLocation.WorldZone(Zones.WALD_ID)),
            rejected(base.withWorld { it.copy(zoneKills = it.zoneKills + (Zones.WALD_ID to -3L)) }),
        )
        assertEquals(
            SaveError.NegativeValue(SaveField.BOSS_DEFEATED_AT_KILLS, SaveLocation.WorldZone(Zones.WIESE_ID)),
            rejected(base.withWorld { it.copy(bossDefeatedAtKills = mapOf(Zones.WIESE_ID to -1L)) }),
        )
    }

    @Test
    fun lastActiveInTheFutureIsAccepted() {
        // Save from a device whose clock runs ahead: offline progress simply grants nothing.
        accepted(base.withWorld { it.copy(lastActiveEpochMillis = Long.MAX_VALUE / 2) })
    }

    @Test
    fun unknownCurrentZoneFallsBackToFirstZone() {
        val valid = accepted(base.withWorld { it.copy(currentZoneId = "atlantis") })
        assertEquals(Zones.WIESE_ID, valid.state.world.currentZoneId)
        assertEquals(listOf(ImportNote.UnknownCurrentZone("atlantis", Zones.WIESE_ID)), valid.notes)
        // Nothing else is touched.
        assertEquals(base, valid.state.copy(world = valid.state.world.copy(currentZoneId = Zones.WALD_ID)))
    }

    @Test
    fun unknownZoneIdsInProgressAreKeptWithNote() {
        val world = base.world.copy(
            unlockedZoneIds = base.world.unlockedZoneIds + "wueste",
            defeatedBossIds = base.world.defeatedBossIds + "wueste",
            zoneKills = base.world.zoneKills + ("vulkan" to 12L),
            bossDefeatedAtKills = base.world.bossDefeatedAtKills + ("Wald" to 3L),
        )
        val valid = accepted(base.copy(world = world))
        assertEquals(world, valid.state.world)
        assertEquals(listOf(ImportNote.UnknownZonesIgnored(listOf("wueste", "vulkan", "Wald"))), valid.notes)
    }

    @Test
    fun negativeKillsOfUnknownZoneAreStillRejected() {
        assertEquals(
            SaveError.NegativeValue(SaveField.ZONE_KILLS, SaveLocation.WorldZone("vulkan")),
            rejected(base.withWorld { it.copy(zoneKills = it.zoneKills + ("vulkan" to -1L)) }),
        )
    }

    // ------------------------------------------------------------------ shop

    @Test
    fun negativeShopValuesAreRejected() {
        val shop = SaveLocation.Shop
        assertEquals(
            SaveError.NegativeValue(SaveField.RESTOCKED_AT, shop),
            rejected(base.withShop { it.copy(restockedAtEpochMillis = -1) }),
        )
        assertEquals(SaveError.NegativeValue(SaveField.STOCK_LEVEL, shop), rejected(base.withShop { it.copy(stockLevel = -1) }))
        assertEquals(SaveError.NegativeValue(SaveField.PAID_RESTOCKS, shop), rejected(base.withShop { it.copy(paidRestocks = -1) }))
    }

    @Test
    fun neverStockedShopIsAccepted() {
        val valid = accepted(base.copy(shop = ShopState()))
        assertEquals(ShopState(), valid.state.shop)
    }

    // ------------------------------------------------------------------ real saves must pass

    @Test
    fun everyGeneratableItemIsAccepted() {
        val levels = listOf(-5, 0, 1, 2, 5, 12, 20, 30, 40, 100, 500, ItemGenerator.MAX_ITEM_LEVEL, 5000)
        for (baseType in ItemBaseTypes.all) for (rarity in Rarity.entries) for (level in levels) {
            val item = ItemGenerator.create(baseType, level, rarity, Random(level))
            val state = GameState(equipment = EquipmentState(inventory = listOf(item)))
            assertNull("$item", SaveTransfer.validate(state))
        }
        assertNull(SaveTransfer.validate(GameState(equipment = EquipmentState(inventory = listOf(StarterItems.RUSTY_DAGGER)))))
    }

    @Test
    fun everyShopAssortmentIsAccepted() {
        for (seed in 0 until 50) for (level in listOf(1, 5, 12, 20, 30, 40, ItemGenerator.MAX_ITEM_LEVEL)) {
            val offers = Shop.generateOffers(level, Random(seed))
            assertNull("seed $seed level $level", SaveTransfer.validate(GameState(shop = ShopState(offers = offers, stockLevel = level))))
        }
    }

    @Test
    fun realisticFullSaveIsAcceptedUnchanged() {
        for (seed in 1..5) {
            val save = realisticFullSave(seed)
            // The fixture really covers the interesting parts.
            assertEquals(Inventory.CAPACITY, save.equipment.inventory.size)
            assertEquals(EquipmentSlot.entries.size, save.equipment.equipped.size)
            assertTrue(save.shop.offers.isNotEmpty() && save.shop.paidRestocks > 0)
            assertEquals(4, save.world.defeatedBossIds.size)
            assertTrue(save.progress.dailyStreak > 0 && save.progress.goals.any { it.claimed } && save.progress.goalsCompleted > 0)
            assertTrue(save.world.lastActiveEpochMillis > 0 && save.character.level > 20)

            val valid = accepted(save)
            assertEquals(save, valid.state)
            assertTrue(valid.notes.isEmpty())
        }
    }

    @Test
    fun negativeStatsAreRejected() {
        val loc = SaveLocation.Stats
        assertEquals(SaveError.NegativeValue(SaveField.STATS_PLAY_TIME, loc), rejected(base.copy(stats = StatsState(playTimeMillis = -1))))
        assertEquals(SaveError.NegativeValue(SaveField.STATS_HIGHEST_HIT, loc), rejected(base.copy(stats = StatsState(highestHit = -1))))
        assertEquals(SaveError.NegativeValue(SaveField.STATS_GOLD_EARNED, loc), rejected(base.copy(stats = StatsState(goldEarned = -1))))
        assertEquals(SaveError.NegativeValue(SaveField.STATS_TAP_COUNT, loc), rejected(base.copy(stats = StatsState(tapCount = -1))))
        assertEquals(base.stats, accepted(base).state.stats)
    }

    @Test
    fun oldSaveWithoutStatsDecodesWithDefaults() {
        val json = com.idlerpg.game.data.GameJson.encode(base)
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        val old = kotlinx.serialization.json.JsonObject(obj - "stats").toString()
        assertEquals(StatsState(), com.idlerpg.game.data.GameJson.decode(old).stats)
    }

    // ------------------------------------------------------------------ progress

    private fun GameState.withProgress(transform: (ProgressState) -> ProgressState) = copy(progress = transform(progress))

    @Test
    fun negativeProgressCountersAreRejected() {
        val loc = SaveLocation.Progress
        assertEquals(SaveError.NegativeValue(SaveField.DAILY_LAST_CLAIM, loc), rejected(base.withProgress { it.copy(dailyLastClaimEpochMillis = -1) }))
        assertEquals(SaveError.NegativeValue(SaveField.DAILY_STREAK, loc), rejected(base.withProgress { it.copy(dailyStreak = -1) }))
        assertEquals(SaveError.NegativeValue(SaveField.CLOCK_HIGH_WATER, loc), rejected(base.withProgress { it.copy(clockHighWaterMillis = -1) }))
        assertEquals(SaveError.NegativeValue(SaveField.GOALS_COMPLETED, loc), rejected(base.withProgress { it.copy(goalsCompleted = -1) }))
    }

    @Test
    fun negativeGoalValuesNameTheGoal() {
        val goals = base.progress.goals
        val badTarget = base.withProgress { it.copy(goals = listOf(goals[0], goals[1].copy(target = -1))) }
        assertEquals(SaveError.NegativeValue(SaveField.GOAL_TARGET, SaveLocation.ProgressGoal(2, "bosses_1")), rejected(badTarget))
        val badBase = base.withProgress { it.copy(goals = listOf(goals[0].copy(baseline = -5), goals[1])) }
        assertEquals(SaveError.NegativeValue(SaveField.GOAL_BASELINE, SaveLocation.ProgressGoal(1, "kills_25")), rejected(badBase))
    }

    @Test
    fun unknownGoalsAreDroppedWithNote() {
        val goals = base.progress.goals + GoalState("wueste_9", "wueste", 9) + GoalState("kills_50", "bosses", 5)
        val valid = accepted(base.withProgress { it.copy(goals = goals) })
        assertEquals(base.progress.goals, valid.state.progress.goals)
        assertEquals(listOf<ImportNote>(ImportNote.UnknownGoalsDropped(2)), valid.notes)
    }

    @Test
    fun saveFieldKeysMatchTheJsonKeys() {
        val json = SaveTransfer.encode(realisticFullSave(1))
        SaveField.entries.forEach { assertTrue("missing key ${it.key}", json.contains("\"${it.key}\":")) }
    }

    @Test
    fun legacySaveWithoutNewFieldsIsAccepted() {
        // Format of the first app version: no world, shop, weapon damage, base type or item level.
        val legacy = """
            {
              "schemaVersion": 1,
              "character": {"name": "Held", "level": 3, "xp": 40, "unspentPoints": 2,
                "stats": {"strength": 7, "dexterity": 6, "vitality": 5, "luck": 5}},
              "equipment": {
                "equipped": {"WEAPON": {"id": "old_sword", "name": "Altes Schwert", "slot": "WEAPON", "rarity": "COMMON",
                  "bonus": {"strength": 2}, "price": 10}},
                "inventory": [{"id": "old_ring", "name": "Kupferring", "slot": "RING", "price": 4}]
              },
              "gold": 77
            }
        """.trimIndent()
        val valid = SaveTransfer.parse(legacy) as ImportResult.Valid
        assertEquals(WorldState(), valid.state.world)
        assertEquals(ShopState(), valid.state.shop)
        assertEquals(ProgressState(), valid.state.progress)
        assertEquals(1, valid.state.equipment.equipped.getValue(EquipmentSlot.WEAPON).itemLevel)
        assertTrue(valid.notes.isEmpty())
    }

    // ------------------------------------------------------------------ fixture files (also for manual emulator checks)

    private fun fixture(name: String): ImportResult {
        val text = javaClass.getResourceAsStream("/saves/$name")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return SaveTransfer.parse(text)
    }

    @Test
    fun fixtureFilesGiveTheDocumentedResults() {
        assertEquals(
            SaveError.DamageRangeReversed(SaveLocation.InventoryItem(2, "Axt"), 14, 5),
            (fixture("import_invalid_damage_range.json") as ImportResult.Invalid).error,
        )
        assertEquals(
            SaveError.BelowMinimum(SaveField.HERO_HP, SaveLocation.World, -1, -5),
            (fixture("import_invalid_hero_hp.json") as ImportResult.Invalid).error,
        )
        assertEquals(
            SaveError.OutOfRange(SaveField.ITEM_LEVEL, SaveLocation.ShopOffer(2, "Scharfe Axt"), 1, ItemGenerator.MAX_ITEM_LEVEL.toLong(), 0),
            (fixture("import_invalid_shop_level.json") as ImportResult.Invalid).error,
        )
        val unknownZone = fixture("import_unknown_zone.json") as ImportResult.Valid
        assertEquals(Zones.WIESE_ID, unknownZone.state.world.currentZoneId)
        assertEquals(
            listOf(
                ImportNote.UnknownCurrentZone("atlantis", Zones.WIESE_ID),
                ImportNote.UnknownZonesIgnored(listOf("wueste", "vulkan")),
            ),
            unknownZone.notes,
        )
    }

    /**
     * A save as the real game produces it after a long session: starter dagger, four bosses beaten,
     * monster drops until the inventory overflowed (auto-sell), one item equipped per slot, all
     * points spent, a stocked shop with paid restock and a purchase, offline progress and a hero
     * that was just defeated (heroHp = 0). One legacy item (no base type, no damage) is mixed in.
     */
    private fun realisticFullSave(seed: Int): GameState {
        val random = Random(seed)
        var now = 1_760_000_000_000L
        var s = StarterItems.ensureStarterWeapon(GameState(character = CharacterState(name = "Aria")))

        for (zone in Zones.all.take(4)) {
            s = WorldProgress.travel(s, zone.id)
            repeat(150) {
                now += 3_000
                val monster = MonsterScaling.spawn(zone, s.character.level, random)
                s = CombatRules.applyKillReward(s, monster.xp, monster.rollGold(random), zone.id, now)
                Loot.rollDrop(monster.level, CombatStats.of(s).totalStats.luck, random)?.let { s = Loot.grant(s, it).first }
            }
            val boss = MonsterScaling.boss(zone)
            s = WorldProgress.applyBossVictory(s, zone, boss.xp, boss.gold, now)
            s = Loot.grant(s, Loot.bossDrop(zone.maxLevel, random)).first
            // Like a player after each zone: equip the newest gear, sell common junk.
            for (slot in EquipmentSlot.entries) {
                s.equipment.inventory.filter { it.slot == slot }.maxByOrNull { it.itemLevel }?.let { s = Inventory.equip(s, it.id) }
            }
            s = Shop.sellCommonJunk(s).state
        }

        val statTypes = StatType.entries
        var i = 0
        while (s.character.unspentPoints > 0) s = Progression.spendPoint(s, statTypes[i++ % statTypes.size])

        // Fill the inventory and overflow it once (auto-sell).
        val lastZone = Zones.all[3]
        while (!Inventory.isFull(s)) s = Loot.grant(s, ItemGenerator.roll(lastZone.maxLevel, random)).first
        s = Loot.grant(s, ItemGenerator.roll(lastZone.maxLevel, random)).first
        val legacy = Item(id = "legacy_sword", name = "Altes Schwert", slot = EquipmentSlot.WEAPON, bonus = Stats(strength = 2), price = 10)
        s = s.copy(equipment = s.equipment.copy(inventory = s.equipment.inventory.drop(1) + legacy))

        s = Shop.ensureStock(s, now, random)
        s = Shop.paidRestock(s.copy(gold = s.gold + 1_000_000), now, random).state
        s = Shop.sell(s, s.equipment.inventory.first().id)
        s = Shop.buy(s, s.shop.offers.first().id).state

        now += 3 * 60 * 60 * 1000L
        s = OfflineProgress.apply(s, OfflineProgress.calculate(s, now, random), now)
        // Like a player: daily reward claimed, first goals set up and one claimed.
        s = DailyReward.claim(DailyReward.observe(s, now), now)
        s = Goals.ensureActive(s)
        s = Goals.claim(s.copy(world = s.world.copy(totalKills = s.world.totalKills + 1_000)), "kills_25")
        return s.copy(world = s.world.copy(heroHp = 0))
    }
}
