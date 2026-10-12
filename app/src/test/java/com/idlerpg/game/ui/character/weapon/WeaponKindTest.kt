package com.idlerpg.game.ui.character.weapon

import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.Rarity
import com.idlerpg.game.domain.character.StarterItems
import com.idlerpg.game.domain.equipment.ItemBaseTypes
import com.idlerpg.game.domain.character.WeaponAction
import com.idlerpg.game.domain.character.WeaponType
import org.junit.Assert.assertEquals
import org.junit.Test

class WeaponKindTest {

    private fun weapon(baseType: String, id: String = "w1", rarity: Rarity = Rarity.COMMON) =
        Item(id = id, name = "Waffe", slot = EquipmentSlot.WEAPON, rarity = rarity, baseType = baseType, damageMin = 2, damageMax = 4)

    @Test
    fun nullIsFist() {
        assertEquals(WeaponKind.FIST, weaponKindOf(null))
        assertEquals(WeaponLook.Fist, weaponLookOf(null))
        assertEquals(AttackStyle.PUNCH, weaponKindOf(null).attackStyle)
    }

    @Test
    fun starterDaggerIsRusty() {
        assertEquals(WeaponKind.RUSTY_DAGGER, weaponKindOf(StarterItems.RUSTY_DAGGER))
        val look = weaponLookOf(StarterItems.RUSTY_DAGGER)
        assertEquals(RUSTY_DAGGER_RUST, look.rust, 0f)
        assertEquals(Rarity.COMMON, look.rarity)
    }

    @Test
    fun starterIdWinsEvenWithBaseType() {
        assertEquals(WeaponKind.RUSTY_DAGGER, weaponKindOf(weapon(ItemBaseTypes.AXE, id = StarterItems.RUSTY_DAGGER_ID)))
    }

    @Test
    fun legacyWeaponWithoutBaseTypeIsRustyDagger() {
        assertEquals(WeaponKind.RUSTY_DAGGER, weaponKindOf(weapon("")))
        assertEquals(WeaponKind.RUSTY_DAGGER, weaponKindOf(weapon("  ")))
    }

    @Test
    fun baseTypesMapToKinds() {
        assertEquals(WeaponKind.DAGGER, weaponKindOf(weapon(ItemBaseTypes.DAGGER)))
        assertEquals(WeaponKind.SHORT_SWORD, weaponKindOf(weapon(ItemBaseTypes.SHORT_SWORD)))
        assertEquals(WeaponKind.MACE, weaponKindOf(weapon(ItemBaseTypes.MACE)))
        assertEquals(WeaponKind.AXE, weaponKindOf(weapon(ItemBaseTypes.AXE)))
    }

    @Test
    fun everyWeaponBaseTypeHasAKind() {
        ItemBaseTypes.forSlot(EquipmentSlot.WEAPON).forEach { bt ->
            val kind = weaponKindOf(weapon(bt.id))
            assert(kind != WeaponKind.FIST && kind != WeaponKind.RUSTY_DAGGER) { "${bt.id} -> $kind" }
        }
    }

    @Test
    fun unknownBaseTypeFallsBackToDagger() {
        assertEquals(WeaponKind.DAGGER, weaponKindOf(weapon("zweihaender")))
    }

    @Test
    fun nonWeaponItemIsFist() {
        val helm = Item(id = "h", name = "Helm", slot = EquipmentSlot.HELMET, baseType = ItemBaseTypes.IRON_HELM)
        assertEquals(WeaponKind.FIST, weaponKindOf(helm))
    }

    @Test
    fun lookCarriesRarityAndNoRustForNewWeapons() {
        val look = weaponLookOf(weapon(ItemBaseTypes.AXE, rarity = Rarity.LEGENDARY))
        assertEquals(WeaponLook(WeaponKind.AXE, Rarity.LEGENDARY, 0f), look)
    }

    @Test
    fun attackStyles() {
        assertEquals(AttackStyle.STAB, WeaponKind.DAGGER.attackStyle)
        assertEquals(AttackStyle.STAB, WeaponKind.RUSTY_DAGGER.attackStyle)
        assertEquals(AttackStyle.STAB, WeaponKind.SHORT_SWORD.attackStyle)
        assertEquals(AttackStyle.SWING, WeaponKind.MACE.attackStyle)
        assertEquals(AttackStyle.SWING, WeaponKind.AXE.attackStyle)
    }

    @Test
    fun kindsDelegateToDomainTypes() {
        assertEquals(WeaponType.DAGGER, WeaponKind.RUSTY_DAGGER.type)
        assertEquals(WeaponType.FIST, weaponKindOf(null).type)
        assertEquals(WeaponAction.STAB, defaultActionOf(WeaponKind.RUSTY_DAGGER))
        assertEquals(WeaponAction.STRIKE, defaultActionOf(WeaponKind.MACE))
        assertEquals(WeaponAction.SWING, defaultActionOf(WeaponKind.SHORT_SWORD))
    }

    @Test
    fun actionTiming() {
        assertEquals(0.38f, impactFractionOf(WeaponAction.SWING), 0f)
        assertEquals(0.38f, impactFractionOf(WeaponAction.STAB), 0f)
        assertEquals(0.38f, impactFractionOf(WeaponAction.STRIKE), 0f)
        assertEquals(0.30f, impactFractionOf(WeaponAction.PARRY), 0f)
        assertEquals(425, attackDurationMillisOf(WeaponAction.STRIKE, 340))
        assertEquals(400, attackDurationMillisOf(WeaponAction.STRIKE, 340, maxMillis = 400))
        assertEquals(306, attackDurationMillisOf(WeaponAction.STAB, 340))
        assertEquals(MIN_ATTACK_MILLIS, attackDurationMillisOf(WeaponAction.STAB, 50))
        assertEquals(129L, impactDelayMillisOf(WeaponAction.SWING, 340))
        assertEquals(102L, impactDelayMillisOf(WeaponAction.PARRY, 340))
    }

    @Test
    fun impactFractionUnchanged() {
        assertEquals(0.38f, WEAPON_IMPACT_FRACTION, 0f)
    }
}
