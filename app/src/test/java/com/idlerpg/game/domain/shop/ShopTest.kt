package com.idlerpg.game.domain.shop

import com.idlerpg.game.data.CharacterState
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.EquipmentState
import com.idlerpg.game.data.GameJson
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.data.ShopState
import com.idlerpg.game.data.Stats
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.equipment.Inventory
import com.idlerpg.game.domain.equipment.ItemGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopTest {

    private val now = 1_000_000_000L

    private fun stocked(gold: Long = 0, level: Int = 1, seed: Int = 1): GameState =
        Shop.ensureStock(GameState(gold = gold, character = CharacterState(level = level)), now, Random(seed))

    private fun filler(n: Int) = List(n) { Item(id = "f$it", name = "Füller", slot = EquipmentSlot.RING, price = 3) }

    // ------------------------------------------------------------------ prices

    @Test
    fun buyPriceAlwaysAboveSellPrice() {
        for (rarity in Rarity.entries) for (lvl in listOf(1, 2, 5, 12, 30, 40, 200, 999)) {
            val item = ItemGenerator.roll(lvl, Random(lvl), minRarity = rarity).copy(rarity = rarity, price = ItemGenerator.sellPrice(lvl, rarity))
            assertTrue("$rarity L$lvl", Shop.buyPrice(item) > item.price)
        }
        // Even a zero-price legacy item costs something.
        assertTrue(Shop.buyPrice(Item(id = "x", name = "Alt", slot = EquipmentSlot.WEAPON, price = 0)) >= 2)
    }

    @Test
    fun markupGrowsWithLevel() {
        assertEquals(4.0, Shop.markup(1), 1e-9)
        assertTrue(Shop.markup(30) > Shop.markup(10))
        assertEquals(Shop.markup(ItemGenerator.MAX_ITEM_LEVEL), Shop.markup(5000), 1e-9)
    }

    @Test
    fun earlyCommonItemIsAffordableAfterAFewKills() {
        // Level 1 common: sell 4 gold → buy 16 gold (~5 Wiese kills).
        val item = Item(id = "c", name = "Lederkappe", slot = EquipmentSlot.HELMET, price = ItemGenerator.sellPrice(1, Rarity.COMMON), itemLevel = 1)
        assertEquals(16L, Shop.buyPrice(item))
    }

    // ------------------------------------------------------------------ assortment

    @Test
    fun firstOpenStocksOnePerSlotPlusRareHighlight() {
        val s = stocked()
        val offers = s.shop.offers
        assertEquals(Shop.OFFER_COUNT, offers.size)
        assertTrue(offers.first().rarity >= Rarity.RARE)
        assertEquals(EquipmentSlot.entries.toSet(), offers.drop(1).map { it.slot }.toSet())
        assertEquals(now, s.shop.restockedAtEpochMillis)
        assertEquals(1, s.shop.stockLevel)
        assertTrue(offers.none { it.isNew })
        assertEquals(offers.size, offers.map { it.id }.toSet().size)
    }

    @Test
    fun offerLevelsFollowShopLevel() {
        val s = stocked(level = 20)
        assertEquals(20, Shop.shopLevel(s.copy(world = WorldState(currentZoneId = "sumpf", unlockedZoneIds = listOf("wiese", "wald", "sumpf")))))
        val offers = Shop.generateOffers(20, Random(3))
        assertTrue(offers.all { it.itemLevel in 19..21 })
    }

    @Test
    fun shopLevelClampedByZones() {
        // Level-1 hero in the forest still gets forest gear.
        val forest = GameState(world = WorldState(currentZoneId = "wald", unlockedZoneIds = listOf("wiese", "wald")))
        assertEquals(5, Shop.shopLevel(forest))
        // Overlevelled hero in the meadow is capped at the highest unlocked zone.
        val overlevelled = GameState(character = CharacterState(level = 50), world = WorldState(unlockedZoneIds = listOf("wiese", "wald")))
        assertEquals(12, Shop.shopLevel(overlevelled))
    }

    @Test
    fun noRestockBeforeTimerExpires() {
        val s = stocked()
        assertSame(s, Shop.ensureStock(s, now + Shop.RESTOCK_INTERVAL_MILLIS - 1, Random(9)))
        assertFalse(Shop.isRestockDue(s, now + 1000))
    }

    @Test
    fun timedRestockRenewsGoodsAndResetsPaidCounter() {
        val s = stocked().let { it.copy(shop = it.shop.copy(paidRestocks = 3, offers = it.shop.offers.drop(5))) }
        val later = now + Shop.RESTOCK_INTERVAL_MILLIS
        val r = Shop.ensureStock(s, later, Random(9))
        assertEquals(Shop.OFFER_COUNT, r.shop.offers.size)
        assertEquals(later, r.shop.restockedAtEpochMillis)
        assertEquals(0, r.shop.paidRestocks)
        assertNotEquals(s.shop.offers.map { it.id }, r.shop.offers.map { it.id })
    }

    @Test
    fun outgrownStockRestocksEarly() {
        val s = stocked()
        val grown = s.copy(character = s.character.copy(level = 4), world = WorldState(unlockedZoneIds = listOf("wiese")))
        val r = Shop.ensureStock(grown, now + 1, Random(2))
        assertEquals(4, r.shop.stockLevel)
        assertTrue(r.shop.offers.all { it.itemLevel >= 3 })
    }

    @Test
    fun clockGoingBackwardsOnlyResetsTimer() {
        val s = stocked()
        val r = Shop.ensureStock(s, now - 10_000, Random(2))
        assertEquals(s.shop.offers, r.shop.offers)
        assertEquals(now - 10_000, r.shop.restockedAtEpochMillis)
    }

    @Test
    fun paidRestockCostsGoldAndEscalates() {
        val s = stocked(gold = 10_000)
        val cost1 = Shop.restockCost(s)
        assertEquals(24L, cost1) // 1.5 × 16
        val r1 = Shop.paidRestock(s, now + 5, Random(4))
        assertTrue(r1.success)
        assertEquals(10_000 - cost1, r1.state.gold)
        assertEquals(1, r1.state.shop.paidRestocks)
        assertEquals(now, r1.state.shop.restockedAtEpochMillis) // timer unchanged
        assertEquals(Shop.OFFER_COUNT, r1.state.shop.offers.size)
        assertEquals(cost1 * 2, Shop.restockCost(r1.state))
        // Factor is capped.
        val many = r1.state.copy(shop = r1.state.shop.copy(paidRestocks = 50))
        assertEquals(cost1 * Shop.MAX_RESTOCK_FACTOR, Shop.restockCost(many))
    }

    @Test
    fun paidRestockWithoutGoldFails() {
        val s = stocked(gold = 5)
        val r = Shop.paidRestock(s, now, Random(4))
        assertFalse(r.success)
        assertSame(s, r.state)
    }

    // ------------------------------------------------------------------ buying

    @Test
    fun buyMovesItemToInventoryAndDeductsGold() {
        val s = stocked(gold = 100_000)
        val offer = s.shop.offers[3]
        val r = Shop.buy(s, offer.id)
        assertEquals(Shop.BuyOutcome.BOUGHT, r.outcome)
        assertEquals(100_000 - Shop.buyPrice(offer), r.state.gold)
        val inInv = r.state.equipment.inventory.single { it.id == offer.id }
        assertTrue(inInv.isNew)
        assertTrue(r.state.shop.offers.none { it.id == offer.id })
        assertEquals(Shop.OFFER_COUNT - 1, r.state.shop.offers.size)
        // Buying the same offer again: sold out.
        assertEquals(Shop.BuyOutcome.SOLD_OUT, Shop.buy(r.state, offer.id).outcome)
    }

    @Test
    fun buyWithoutEnoughGoldIsRejected() {
        val s = stocked(gold = 1)
        val r = Shop.buy(s, s.shop.offers.first().id)
        assertEquals(Shop.BuyOutcome.NOT_ENOUGH_GOLD, r.outcome)
        assertSame(s, r.state)
    }

    @Test
    fun buyWithFullInventoryIsBlocked() {
        val s = stocked(gold = 1_000_000).let { it.copy(equipment = EquipmentState(inventory = filler(Inventory.CAPACITY))) }
        val offer = s.shop.offers.first()
        val r = Shop.buy(s, offer.id)
        assertEquals(Shop.BuyOutcome.INVENTORY_FULL, r.outcome)
        assertSame(s, r.state)
        assertEquals(1_000_000L, r.state.gold)
    }

    @Test
    fun buyUnknownIdIsSoldOut() {
        val s = stocked(gold = 1000)
        assertEquals(Shop.BuyOutcome.SOLD_OUT, Shop.buy(s, "gibt-es-nicht").outcome)
    }

    // ------------------------------------------------------------------ selling

    @Test
    fun sellCreditsSellPrice() {
        val item = Item(id = "s", name = "Ring", slot = EquipmentSlot.RING, price = 42)
        val s = GameState(gold = 8, equipment = EquipmentState(inventory = listOf(item)))
        val r = Shop.sell(s, "s")
        assertEquals(50L, r.gold)
        assertTrue(r.equipment.inventory.isEmpty())
    }

    @Test
    fun buyThenSellLosesGold() {
        val s = stocked(gold = 100_000)
        val offer = s.shop.offers.first()
        val bought = Shop.buy(s, offer.id).state
        val sold = Shop.sell(bought, offer.id)
        assertTrue(sold.gold < s.gold)
    }

    @Test
    fun sellWhereSellsOnlyMatchingInventoryItems() {
        val inv = listOf(
            Item(id = "a", name = "A", slot = EquipmentSlot.RING, rarity = Rarity.COMMON, price = 4),
            Item(id = "b", name = "B", slot = EquipmentSlot.RING, rarity = Rarity.RARE, price = 30),
            Item(id = "c", name = "C", slot = EquipmentSlot.BOOTS, rarity = Rarity.COMMON, price = 6),
        )
        val equippedCommon = Item(id = "e", name = "E", slot = EquipmentSlot.WEAPON, rarity = Rarity.COMMON, price = 99)
        val s = GameState(equipment = EquipmentState(equipped = mapOf(EquipmentSlot.WEAPON to equippedCommon), inventory = inv))
        val r = Shop.sellWhere(s) { it.rarity == Rarity.COMMON }
        assertEquals(2, r.count)
        assertEquals(10L, r.gold)
        assertEquals(listOf("b"), r.state.equipment.inventory.map { it.id })
        assertEquals(equippedCommon, r.state.equipment.equipped[EquipmentSlot.WEAPON])
    }

    @Test
    fun bulkSellKeepsUpgradesAndRareItems() {
        val equippedRing = Item(id = "eq", name = "Ring", slot = EquipmentSlot.RING, bonus = Stats(luck = 5), price = 10)
        val weakerRing = Item(id = "w", name = "Schwach", slot = EquipmentSlot.RING, bonus = Stats(luck = 2), price = 4)
        val betterRing = Item(id = "b", name = "Besser", slot = EquipmentSlot.RING, bonus = Stats(vitality = 9), price = 4)
        val emptySlotBoots = Item(id = "s", name = "Stiefel", slot = EquipmentSlot.BOOTS, bonus = Stats(dexterity = 1), price = 4)
        val rare = Item(id = "r", name = "Selten", slot = EquipmentSlot.RING, rarity = Rarity.RARE, price = 30)
        val s = GameState(
            equipment = EquipmentState(
                equipped = mapOf(EquipmentSlot.RING to equippedRing),
                inventory = listOf(weakerRing, betterRing, emptySlotBoots, rare),
            ),
        )
        assertFalse(Shop.isUpgrade(s, weakerRing))
        assertTrue(Shop.isUpgrade(s, betterRing)) // vitality: no DPS, but more attribute points
        assertTrue(Shop.isUpgrade(s, emptySlotBoots))
        assertEquals(listOf("w"), Shop.bulkSellCandidates(s).map { it.id })
        val r = Shop.sellCommonJunk(s)
        assertEquals(1, r.count)
        assertEquals(4L, r.gold)
        assertEquals(listOf("b", "s", "r"), r.state.equipment.inventory.map { it.id })
    }

    // ------------------------------------------------------------------ persistence

    @Test
    fun oldSaveWithoutShopDecodesToEmptyShop() {
        val old = """{"schemaVersion":1,"gold":7,"equipment":{"equipped":{},"inventory":[]},"world":{"currentZoneId":"wald"}}"""
        val decoded = GameJson.decode(old)
        assertEquals(ShopState(), decoded.shop)
        assertEquals(7L, decoded.gold)
        assertTrue(Shop.isRestockDue(decoded, now))
    }

    @Test
    fun stockedShopRoundTrips() {
        val s = stocked(gold = 3)
        assertEquals(s, GameJson.decode(GameJson.encode(s)))
    }
}
