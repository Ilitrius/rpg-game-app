package com.idlerpg.game.domain.world

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Stats
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemGenerator
import com.idlerpg.game.domain.shop.Shop
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Balance checks with a reference hero: stat points spread 45 % Stärke / 30 % Geschick / 25 % Vitalität,
 * either with only the starter dagger or with randomly rolled drops (Monte Carlo over many seeds).
 * Covers fights, bosses and the economy (online/offline gold and XP vs. shop prices).
 * Also writes a table to `build/balance.txt` for manual tuning.
 */
class BalanceTest {

    private companion object {
        const val T0 = 1_000_000_000L
        const val NIGHT_MILLIS = 8L * 3_600_000L
    }

    private data class Fight(val killSeconds: Double, val damageShare: Double, val sustain: Double)

    private fun heroState(level: Int, gearLevel: Int?, slots: Int, seed: Int): GameState {
        val pts = Progression.STAT_POINTS_PER_LEVEL * (level - 1)
        val str = (pts * 0.45).toInt()
        val dex = (pts * 0.30).toInt()
        val vit = pts - str - dex
        var s = StarterItems.ensureStarterWeapon(GameState())
        s = s.copy(character = s.character.copy(level = level, stats = Stats(5 + str, 5 + dex, 5 + vit, 5)))
        if (gearLevel != null) {
            val r = Random(seed)
            val order = listOf(EquipmentSlot.WEAPON) + EquipmentSlot.entries.filter { it != EquipmentSlot.WEAPON }.shuffled(r)
            val eq = order.take(slots).associateWith { ItemGenerator.roll(gearLevel, r, slot = it) }
            s = s.copy(equipment = s.equipment.copy(equipped = s.equipment.equipped + eq))
        }
        return s
    }

    private fun hero(level: Int, gearLevel: Int?, slots: Int, seed: Int): CombatStats =
        CombatStats.of(heroState(level, gearLevel, slots, seed))

    /** Reference hero standing in [zone] (all zones up to it unlocked), last active at [T0]. */
    private fun offlineState(zone: Zone, level: Int, gearLevel: Int?, slots: Int, seed: Int): GameState {
        val s = heroState(level, gearLevel, slots, seed)
        val open = Zones.all.filter { it.order <= zone.order }.map { it.id }
        return s.copy(world = WorldState(currentZoneId = zone.id, unlockedZoneIds = open, lastActiveEpochMillis = T0))
    }

    /** Economy numbers of one situation (averaged over seeds). */
    private data class Economy(
        val onlineGoldPerHour: Double,
        val onlineXpPerHour: Double,
        val nightGold: Double,
        val nightXp: Double,
        val nightLevels: Double,
        val nightItems: Double,
        /** Night gold when the inventory is already full (every offline drop is auto-sold). */
        val nightGoldFullInventory: Double,
        val assortment: Double,
        val cheapestOffer: Double,
        val medianOffer: Double,
    ) {
        val nightShare: Double get() = nightGold / assortment
        val nightShareFullInventory: Double get() = nightGoldFullInventory / assortment
    }

    /** One balance situation: hero (and monster) [level] in [zone] wearing [slots] items of [gear] level. */
    private data class Situation(val zone: Zone, val level: Int, val gear: Int, val slots: Int) {
        override fun toString() = "${zone.id} L$level gear $gear×$slots"
    }

    /**
     * Online gold/XP per hour, offline gold/XP of a full night ([NIGHT_MILLIS]) and the expected
     * price of the shop's complete assortment for the reference hero in [sit].
     */
    private fun economy(sit: Situation, seeds: Int = 200): Economy {
        val zone = sit.zone
        var goldH = 0.0; var xpH = 0.0; var nGold = 0.0; var nXp = 0.0; var nLvl = 0.0; var nItems = 0.0
        var nGoldFull = 0.0; var full = 0.0; var cheap = 0.0; var median = 0.0
        val junk = List(Inventory.CAPACITY) { Item("junk$it", "X", EquipmentSlot.RING) }
        repeat(seeds) { seed ->
            val s = offlineState(zone, sit.level, sit.gear, sit.slots, seed)
            val h = CombatStats.of(s)
            val avg = MonsterScaling.average(zone, sit.level.coerceIn(zone.minLevel, zone.maxLevel))
            val perKill = avg.hp / h.dps + CombatRules.TRAVEL_MILLIS / 1000.0
            goldH += 3600.0 / perKill * avg.gold / seeds
            xpH += 3600.0 / perKill * avg.xp / seeds
            val r = OfflineProgress.calculate(s, T0 + NIGHT_MILLIS, Random(seed))
            if (r != null) {
                nGold += r.totalGold.toDouble() / seeds
                nXp += r.xp.toDouble() / seeds
                nLvl += r.levelsGained.toDouble() / seeds
                nItems += r.items.size.toDouble() / seeds
            }
            val fullInv = s.copy(equipment = s.equipment.copy(inventory = junk))
            OfflineProgress.calculate(fullInv, T0 + NIGHT_MILLIS, Random(seed))?.let { nGoldFull += it.totalGold.toDouble() / seeds }
            val prices = Shop.generateOffers(Shop.shopLevel(s), Random(seed)).map { Shop.buyPrice(it).toDouble() }.sorted()
            full += prices.sum() / seeds
            cheap += prices.first() / seeds
            median += (prices[prices.size / 2 - 1] + prices[prices.size / 2]) / 2 / seeds
        }
        return Economy(goldH, xpH, nGold, nXp, nLvl, nItems, nGoldFull, full, cheap, median)
    }

    /** Per zone: entering with the previous zone's drops, mid-zone and zone end with the zone's drops. */
    private fun situations(): List<Situation> = Zones.all.flatMap { z ->
        val prev = Zones.previous(z)
        buildList {
            if (prev != null) add(Situation(z, z.minLevel, gearOf(prev), 5))
            add(Situation(z, gearOf(z), gearOf(z), 7))
            add(Situation(z, z.maxLevel, gearOf(z), 7))
        }
    }

    private fun fight(h: CombatStats, m: MonsterStats): Fight {
        val secs = m.maxHp / h.dps
        val dmg = secs * m.dps
        val regen = CombatRules.regen(h.maxHp, ((secs * 1000) + CombatRules.TRAVEL_MILLIS).toLong())
        return Fight(secs, dmg / h.maxHp, regen / dmg.coerceAtLeast(1e-9))
    }

    /** Weighted average fight vs. the zone's regular monsters at [level], averaged over seeds. */
    private fun zoneFight(zone: Zone, level: Int, heroLevel: Int, gearLevel: Int?, slots: Int = 7): Fight {
        val seeds = if (gearLevel == null) 1 else 300
        var k = 0.0; var d = 0.0; var su = 0.0
        val total = zone.monsters.sumOf { it.spawnWeight }.toDouble()
        repeat(seeds) { seed ->
            val h = hero(heroLevel, gearLevel, slots, seed)
            zone.monsters.forEach { t ->
                val f = fight(h, MonsterScaling.stats(t, zone, level))
                val w = t.spawnWeight / total / seeds
                k += f.killSeconds * w; d += f.damageShare * w; su += f.sustain * w
            }
        }
        return Fight(k, d, su)
    }

    private fun bossFight(zone: Zone, heroLevel: Int, gearLevel: Int?, slots: Int = 7): Fight {
        val seeds = if (gearLevel == null) 1 else 300
        var k = 0.0; var d = 0.0
        repeat(seeds) { seed ->
            val f = fight(hero(heroLevel, gearLevel, slots, seed), MonsterScaling.boss(zone))
            k += f.killSeconds / seeds; d += f.damageShare / seeds
        }
        return Fight(k, d, 0.0)
    }

    private fun gearOf(zone: Zone) = (zone.minLevel + zone.maxLevel) / 2

    @Test
    fun writeBalanceTable() {
        val sb = StringBuilder()
        fun f(x: Fight) = String.format("%5.1fs %4.0f%%HP sus%4.1f", x.killSeconds, x.damageShare * 100, x.sustain)
        Zones.all.forEach { z ->
            val prev = Zones.previous(z)
            val prevGear = prev?.let { gearOf(it) }
            sb.appendLine("== ${z.id} L${z.minLevel}-${z.maxLevel}")
            for (lvl in listOf(z.minLevel, gearOf(z), z.maxLevel)) {
                sb.appendLine(
                    "  L$lvl starter: ${f(zoneFight(z, lvl, lvl, null))} | prevGear($prevGear,5): " +
                        "${prevGear?.let { f(zoneFight(z, lvl, lvl, it, 5)) } ?: "-"} | zoneGear(${gearOf(z)},7): ${f(zoneFight(z, lvl, lvl, gearOf(z)))}",
                )
            }
            val b = MonsterScaling.boss(z)
            sb.appendLine(
                "  BOSS L${b.level} hp=${b.maxHp} dmg=${b.damageMin}-${b.damageMax}/${b.attackIntervalMillis}ms xp=${b.xp} gold=${b.gold}: " +
                    "starter ${f(bossFight(z, z.maxLevel, null))} | prevGear ${prevGear?.let { f(bossFight(z, z.maxLevel, it, 5)) } ?: "-"} | zoneGear ${f(bossFight(z, z.maxLevel, gearOf(z)))}",
            )
            z.monsters.forEach { t ->
                val m = MonsterScaling.stats(t, z, z.minLevel)
                sb.appendLine("    ${t.id} L${m.level}: hp=${m.maxHp} dmg=${m.damageMin}-${m.damageMax}/${m.attackIntervalMillis}ms xp=${m.xp} gold=${m.gold}")
            }
        }
        sb.appendLine()
        sb.appendLine(
            "== Economy (reference hero; night = ${NIGHT_MILLIS / 3_600_000} h offline at ${"%.1f".format(OfflineProgress.EFFICIENCY * 100)} %; " +
                "shop = expected price of all ${Shop.OFFER_COUNT} offers)",
        )
        sb.appendLine("   zone    lvl gear | gold/h online  xp/h online | night gold (inv full)  night xp  +lvl items | shop all  cheapest median | night/shop (inv full)")
        situations().forEach { sit ->
            val e = economy(sit)
            sb.appendLine(
                String.format(
                    "   %-7s %3d %2d×%d | %13.0f %12.0f | %10.0f %10.0f %9.0f %5.1f %5.1f | %8.0f %8.0f %6.0f | %6.0f%% %6.0f%%",
                    sit.zone.id, sit.level, sit.gear, sit.slots, e.onlineGoldPerHour, e.onlineXpPerHour, e.nightGold,
                    e.nightGoldFullInventory, e.nightXp, e.nightLevels, e.nightItems, e.assortment, e.cheapestOffer,
                    e.medianOffer, e.nightShare * 100, e.nightShareFullInventory * 100,
                ),
            )
        }
        File("build").mkdirs()
        File("build/balance.txt").writeText(sb.toString())
    }

    /**
     * Offline gold of a full night buys some, but not all, of the shop's offers at the current level:
     * at least two typical (median) offers, at most 60 % of the whole assortment — even when every
     * offline drop is auto-sold because the inventory is full.
     */
    @Test
    fun offlineNightBuysSomeButNotAllShopOffers() {
        situations().forEach { sit ->
            val e = economy(sit)
            assertTrue("$sit some: night ${e.nightGold} vs median offer ${e.medianOffer}", e.nightGold >= 2 * e.medianOffer)
            assertTrue("$sit not all: ${e.nightShare}", e.nightShare in 0.2..0.6)
            assertTrue("$sit not all (inventory full): ${e.nightShareFullInventory}", e.nightShareFullInventory <= 0.6)
        }
    }

    /** A night offline is a boost (a few levels and items), not a skip of the zone. */
    @Test
    fun offlineNightGivesAFewLevelsAndItems() {
        situations().forEach { sit ->
            val e = economy(sit, seeds = 60)
            assertTrue("$sit levels ${e.nightLevels}", e.nightLevels in 1.0..4.0)
            assertTrue("$sit items ${e.nightItems}", e.nightItems in 3.0..Loot.MAX_OFFLINE_ITEMS.toDouble())
            // Offline is clearly slower than playing: a night is worth less than 15 minutes online.
            assertTrue("$sit night xp ${e.nightXp} vs ${e.onlineXpPerHour}/h", e.nightXp < e.onlineXpPerHour / 4)
        }
    }

    @Test
    fun zoneTwoIsHardWithStarterGearButFineWithZoneOneDrops() {
        val wald = Zones.WALD
        val starter = zoneFight(wald, wald.minLevel, wald.minLevel, null)
        val geared = zoneFight(wald, wald.minLevel, wald.minLevel, gearOf(Zones.WIESE), slots = 5)
        assertTrue("starter $starter", starter.killSeconds > 9.0 && starter.damageShare > 0.45)
        assertTrue("geared $geared", geared.killSeconds in 2.0..7.0 && geared.damageShare < 0.35)
    }

    @Test
    fun everyZoneIsPlayableWithDropsOfThePreviousZone() {
        Zones.all.drop(1).forEach { z ->
            val f = zoneFight(z, z.minLevel, z.minLevel, gearOf(Zones.previous(z)!!), slots = 5)
            assertTrue("${z.id} $f", f.killSeconds < 9.0 && f.damageShare < 0.6)
        }
    }

    @Test
    fun bossesNeedZoneGearToBeBeatenInTime() {
        val limit = WorldProgress.BOSS_TIME_LIMIT_MILLIS / 1000.0
        Zones.all.forEach { z ->
            val geared = bossFight(z, z.maxLevel, gearOf(z))
            assertTrue("${z.id} geared $geared", geared.killSeconds < limit * 0.75 && geared.damageShare < 0.9)
            val starter = bossFight(z, z.maxLevel, null)
            assertTrue("${z.id} starter $starter", starter.killSeconds > limit || starter.damageShare > 1.0)
        }
    }
}
