package com.idlerpg.game.domain.world

import com.idlerpg.game.data.GameState

/**
 * Race protection for the rebirth: the rebirth is one atomic `repository.update` made from the character
 * tab, while the adventure view still has rewards / HP saves in flight (they run in the app scope). Such a
 * save must never be applied on top of the reset state (old kill, old zone counters, old max HP).
 *
 * Every in-flight save remembers [PrestigeState.rebirths] at the time it was created; [guarded] runs its
 * body only if the state still has that count, inside the same atomic update (so no interleaving).
 */
object RebirthGuard {
    /**
     * Transform for `repository.update`: [body] only while `prestige.rebirths == rebirths`, otherwise the
     * state stays untouched except for [always] (statistics are lifetime values and survive a rebirth).
     */
    fun guarded(
        rebirths: Int,
        always: (GameState) -> GameState = { it },
        body: (GameState) -> GameState,
    ): (GameState) -> GameState = { s ->
        if (s.prestige.rebirths != rebirths) always(s) else always(body(s))
    }
}
