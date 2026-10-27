package com.idlerpg.game.ui.adventure

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlerpg.game.IdleRpgApp
import com.idlerpg.game.data.EquipmentSlot
import com.idlerpg.game.data.GameState
import com.idlerpg.game.data.Item
import com.idlerpg.game.data.WorldState
import com.idlerpg.game.domain.character.CombatStats
import com.idlerpg.game.domain.character.Hit
import com.idlerpg.game.domain.character.Progression
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.world.BossOutcome
import com.idlerpg.game.domain.world.Loot
import com.idlerpg.game.domain.world.LootDrop
import com.idlerpg.game.domain.world.MonsterScaling
import com.idlerpg.game.domain.world.MonsterStats
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.OfflineReward
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.character.weapon.WEAPON_IMPACT_FRACTION
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class MonsterPhase { APPROACHING, FIGHTING, DYING, GONE }

/** The monster currently on screen. [spawnId] changes for every new monster. */
data class MonsterUi(val spawnId: Long, val stats: MonsterStats, val hp: Long)

data class AdventureUiState(
    val zone: Zone = Zones.WIESE,
    val heroName: String = "",
    val heroLevel: Int = 1,
    val monster: MonsterUi? = null,
    val phase: MonsterPhase = MonsterPhase.GONE,
    val heroHp: Int = 1,
    val heroMaxHp: Int = 1,
    /** XP within the current level and XP needed for the next level. */
    val heroXp: Long = 0,
    val heroXpNeeded: Long = 1,
    val defeated: Boolean = false,
    val welcomeBack: OfflineReward? = null,
    /** Duration of one dagger stab, never longer than the attack interval. */
    val attackDurationMillis: Int = DEFAULT_STAB_MILLIS,
    /** Persistent world progress (for the zone sheet and the boss button). */
    val world: WorldState = WorldState(),
    /** True while the current monster is the zone boss. */
    val bossFight: Boolean = false,
    /** Remaining boss time (only meaningful while [bossFight]). */
    val bossTimeLeftMillis: Long = WorldProgress.BOSS_TIME_LIMIT_MILLIS,
    /** Non-null while the travel transition to this zone is running. */
    val travelingTo: Zone? = null,
    /** Currently equipped weapon (null = fists), drawn in first person. */
    val equippedWeapon: Item? = null,
) {
    val killsTowardsBoss: Long get() = WorldProgress.killsTowardsBoss(world, zone)

    /**
     * True while a boss is visible (approaching, fighting or fading out). Covers the moments around
     * [bossFight] (e.g. the boss's death before the victory is saved) so the HUD never offers the
     * boss again while it is still on screen.
     */
    val bossOnScreen: Boolean get() = monster?.stats?.type?.isBoss == true && phase != MonsterPhase.GONE

    /** What the zone label says about the zone boss. */
    val bossHudStatus: BossHudStatus
        get() = when {
            bossFight || bossOnScreen -> BossHudStatus.FIGHTING
            killsTowardsBoss >= WorldProgress.BOSS_KILLS_REQUIRED -> BossHudStatus.READY
            else -> BossHudStatus.PROGRESS
        }

    val canChallengeBoss: Boolean
        get() = !bossFight && !bossOnScreen && travelingTo == null && !defeated && WorldProgress.canChallengeBoss(world, zone)
}

/** Boss line of the zone label: kill progress, "Boss bereit!" or the running boss fight. */
enum class BossHudStatus { PROGRESS, READY, FIGHTING }

/** One-shot combat events for visual effects. */
sealed interface AdventureEvent {
    /** The hero starts a stab; the damage lands [impactDelayMillis] later. */
    data class HeroAttack(val hit: Hit) : AdventureEvent
    data class MonsterHit(val damage: Int) : AdventureEvent
    data class Kill(val xp: Long, val gold: Long) : AdventureEvent
    /** The kill reward raised the hero's level (online only, never emitted for offline progress). */
    data object LevelUp : AdventureEvent
    /** An item dropped (already stored in the inventory or auto-sold). */
    data class Loot(val drop: LootDrop) : AdventureEvent
    /** The boss of [zone] was defeated; [unlocked] is the newly opened zone (null if none/already open). */
    data class BossVictory(val zone: Zone, val unlocked: Zone?) : AdventureEvent
    data class BossFailed(val outcome: BossOutcome) : AdventureEvent
}

private const val DEFAULT_STAB_MILLIS = 340

/** Impact point of the first-person attack animation (same for every weapon kind). */
private val STAB_IMPACT_FRACTION = WEAPON_IMPACT_FRACTION.toDouble()

/** Duration of the fade-out/fade-in when travelling to another zone. */
const val TRAVEL_TRANSITION_MILLIS = 1400L

/** Hero HP is saved at most this often while playing (plus on every kill and in the background). */
private const val HP_SAVE_INTERVAL_MILLIS = 5_000L

/**
 * The "Willkommen zurück" summary only appears after at least this long away; shorter absences
 * (switching apps) are credited silently — offline yields only ~1 minute of fighting per hour.
 */
const val WELCOME_BACK_MIN_MILLIS = 30L * 60 * 1000

/** True if [reward] is worth a "Willkommen zurück" dialog (long enough away and something to tell). */
fun shouldShowWelcomeBack(reward: OfflineReward?): Boolean =
    reward != null && reward.awayMillis >= WELCOME_BACK_MIN_MILLIS && (reward.kills > 0 || reward.heroTooWeak)

private enum class FightResult { KILL, DEFEAT, TIMEOUT }

/**
 * Idle combat loop: hero and monster attack at their own rates; kills grant XP + gold + item drops
 * (persisted via [com.idlerpg.game.data.GameRepository]); boss challenges and zone travel restart
 * the loop. The loop pauses in the background ([onBackground]) and offline progress is granted on
 * [onForeground]. Hero HP is persisted (throttled) in [WorldState.heroHp].
 */
class AdventureViewModel(app: Application) : AndroidViewModel(app) {

    private val idleApp = app as IdleRpgApp
    private val repository = idleApp.repository

    private val _ui = MutableStateFlow(AdventureUiState())
    val ui: StateFlow<AdventureUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<AdventureEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AdventureEvent> = _events.asSharedFlow()

    private var stats: CombatStats? = null
    private val statsReady = CompletableDeferred<Unit>()
    private var heroHp: Double = 0.0
    private var lastSavedHp = Int.MIN_VALUE
    private var spawnCounter = 0L
    private var loopJob: Job? = null
    private var hpSaveJob: Job? = null
    private var offlineJob: Deferred<Unit>? = null
    private var foreground = false
    private var bossRequested = false

    /** Zone the hero travels to until the repository confirms it (avoids flicker back). */
    private var pendingZoneId: String? = null

    init {
        viewModelScope.launch {
            repository.state.collect { onGameState(it) }
        }
    }

    private fun onGameState(s: GameState) {
        val newStats = CombatStats.of(s)
        val old = stats
        if (old == null) {
            heroHp = CombatRules.restoreHeroHp(s.world.heroHp, newStats.maxHp).toDouble()
            lastSavedHp = s.world.heroHp
        } else if (newStats.maxHp != old.maxHp) {
            // Gaining max HP (vitality, level-up) also heals by the difference.
            heroHp = (heroHp + (newStats.maxHp - old.maxHp)).coerceIn(1.0, newStats.maxHp.toDouble())
        }
        stats = newStats
        if (pendingZoneId == s.world.currentZoneId) pendingZoneId = null
        val zone = Zones.byId(pendingZoneId ?: s.world.currentZoneId)
        val stab = minOf(DEFAULT_STAB_MILLIS.toLong(), (newStats.attackIntervalMillis * 0.85).toLong()).toInt()
        _ui.update {
            it.copy(
                zone = if (it.travelingTo != null) it.zone else zone,
                heroName = s.character.name,
                heroLevel = s.character.level,
                heroXp = s.character.xp,
                heroXpNeeded = Progression.xpForNextLevel(s.character.level),
                heroMaxHp = newStats.maxHp,
                heroHp = ceil(heroHp).toInt(),
                attackDurationMillis = stab,
                world = s.world,
                equippedWeapon = s.equipment.equipped[EquipmentSlot.WEAPON],
            )
        }
        statsReady.complete(Unit)
    }

    /** Call when the screen becomes visible (lifecycle ON_START). */
    fun onForeground() {
        if (foreground) return
        foreground = true
        offlineJob = grantOfflineProgress()
        startLoop()
        hpSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(HP_SAVE_INTERVAL_MILLIS)
                saveHeroHp()
            }
        }
    }

    /** Call when the app goes to the background (lifecycle ON_STOP). */
    fun onBackground() {
        if (!foreground) return
        foreground = false
        loopJob?.cancel()
        loopJob = null
        hpSaveJob?.cancel()
        hpSaveJob = null
        // A zone change interrupted by pausing the loop is completed right away.
        _ui.update { st -> st.travelingTo?.let { st.copy(zone = it, travelingTo = null, monster = null, phase = MonsterPhase.GONE) } ?: st }
        if (_ui.value.bossFight) endBossFight()
        val now = System.currentTimeMillis()
        val hp = currentHp()
        lastSavedHp = hp
        idleApp.appScope.launch {
            runCatching {
                repository.update { it.copy(world = it.world.copy(lastActiveEpochMillis = now, heroHp = hp)) }
            }
        }
    }

    fun dismissWelcomeBack() = _ui.update { it.copy(welcomeBack = null) }

    /** Starts the boss fight of the current zone (if the kill requirement is met). */
    fun challengeBoss() {
        if (!_ui.value.canChallengeBoss) return
        bossRequested = true
        _ui.update { it.copy(monster = null, phase = MonsterPhase.GONE) }
        startLoop()
    }

    /** Travels to [zoneId] with a short transition; locked zones are ignored. */
    fun travelTo(zoneId: String) {
        val cur = _ui.value
        val zone = Zones.all.firstOrNull { it.id == zoneId } ?: return
        if (!WorldProgress.isUnlocked(cur.world, zone) || cur.travelingTo != null) return
        if (zone.id == cur.zone.id && !cur.bossFight) return
        loopJob?.cancel()
        bossRequested = false
        pendingZoneId = zone.id
        _ui.update {
            it.copy(travelingTo = zone, bossFight = false, bossTimeLeftMillis = WorldProgress.BOSS_TIME_LIMIT_MILLIS)
        }
        idleApp.appScope.launch { runCatching { repository.update { WorldProgress.travel(it, zone.id) } } }
        loopJob = viewModelScope.launch {
            waitWithRegen(TRAVEL_TRANSITION_MILLIS / 2)
            _ui.update { it.copy(zone = zone, monster = null, phase = MonsterPhase.GONE) }
            waitWithRegen(TRAVEL_TRANSITION_MILLIS / 2)
            _ui.update { it.copy(travelingTo = null) }
            runLoop()
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        if (foreground) loopJob = viewModelScope.launch { runLoop() }
    }

    private fun grantOfflineProgress(): Deferred<Unit> {
        val now = System.currentTimeMillis()
        return idleApp.appScope.async {
            var reward: OfflineReward? = null
            var restoredHp = -1
            runCatching {
                repository.update { s ->
                    reward = OfflineProgress.calculate(s, now)
                    OfflineProgress.apply(s, reward, now).also { restoredHp = it.world.heroHp }
                }
            }
            val max = stats?.maxHp
            if (restoredHp >= 0 && max != null) {
                heroHp = CombatRules.restoreHeroHp(restoredHp, max).toDouble()
                lastSavedHp = restoredHp
                publishHeroHp()
            }
            reward?.takeIf { shouldShowWelcomeBack(it) }?.let { r -> _ui.update { it.copy(welcomeBack = r) } }
        }
    }

    private fun currentHp(): Int = ceil(heroHp.coerceAtLeast(0.0)).roundToInt()

    private fun saveHeroHp() {
        val hp = currentHp()
        if (hp == lastSavedHp) return
        lastSavedHp = hp
        idleApp.appScope.launch {
            runCatching { repository.update { it.copy(world = it.world.copy(heroHp = hp)) } }
        }
    }

    // ---------------------------------------------------------------- combat loop

    private suspend fun runLoop() {
        statsReady.await()
        offlineJob?.await()
        if (_ui.value.defeated) recoverFromDefeat()
        while (true) {
            if (bossRequested) {
                bossRequested = false
                runBossFight()
                continue
            }
            val cur = _ui.value
            when {
                cur.monster == null || cur.monster.stats.type.isBoss ||
                    cur.phase == MonsterPhase.GONE || cur.phase == MonsterPhase.DYING -> spawnNext()
                cur.phase == MonsterPhase.APPROACHING -> _ui.update { it.copy(phase = MonsterPhase.FIGHTING) }
            }
            when (fight(deadline = null)) {
                FightResult.KILL -> onKill()
                else -> onDefeat()
            }
        }
    }

    private suspend fun spawnNext() {
        val s = _ui.value
        spawn(MonsterScaling.spawn(s.zone, s.heroLevel))
    }

    private suspend fun spawn(monster: MonsterStats) {
        _ui.update {
            it.copy(monster = MonsterUi(++spawnCounter, monster, monster.maxHp), phase = MonsterPhase.APPROACHING)
        }
        waitWithRegen(CombatRules.APPROACH_MILLIS)
        _ui.update { it.copy(phase = MonsterPhase.FIGHTING) }
    }

    private suspend fun runBossFight() {
        val zone = _ui.value.zone
        _ui.update { it.copy(bossFight = true, bossTimeLeftMillis = WorldProgress.BOSS_TIME_LIMIT_MILLIS) }
        spawn(MonsterScaling.boss(zone))
        val deadline = SystemClock.elapsedRealtime() + WorldProgress.BOSS_TIME_LIMIT_MILLIS
        when (fight(deadline)) {
            FightResult.KILL -> onBossKill(zone)
            FightResult.TIMEOUT -> {
                _events.tryEmit(AdventureEvent.BossFailed(BossOutcome.TIMEOUT))
                endBossFight()
                _ui.update { it.copy(phase = MonsterPhase.DYING) }
                waitWithRegen(CombatRules.DEATH_MILLIS)
                _ui.update { it.copy(monster = null, phase = MonsterPhase.GONE) }
            }
            FightResult.DEFEAT -> {
                _events.tryEmit(AdventureEvent.BossFailed(BossOutcome.DEFEAT))
                endBossFight()
                onDefeat()
            }
        }
    }

    private fun endBossFight() {
        _ui.update { it.copy(bossFight = false, bossTimeLeftMillis = WorldProgress.BOSS_TIME_LIMIT_MILLIS) }
    }

    /**
     * Runs one fight against the current monster until it dies, the hero falls or [deadline]
     * (elapsedRealtime, boss fights only) passes.
     */
    private suspend fun fight(deadline: Long?): FightResult {
        val monster = _ui.value.monster ?: return FightResult.KILL
        val start = SystemClock.elapsedRealtime()
        var last = start
        var heroNext = last + 300
        var monsterNext = last + (monster.stats.attackIntervalMillis * 0.9).toLong()
        var pending: Hit? = null
        var pendingAt = 0L
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val hero = stats ?: return FightResult.DEFEAT
            regen(hero, now - last)
            last = now

            val p = pending
            if (p != null && now >= pendingAt) {
                pending = null
                _ui.update { st ->
                    val m = st.monster ?: return@update st
                    st.copy(monster = m.copy(hp = (m.hp - p.damage).coerceAtLeast(0)))
                }
            }
            if (deadline != null) {
                val limit = WorldProgress.BOSS_TIME_LIMIT_MILLIS
                val elapsed = now - (deadline - limit)
                val outcome = WorldProgress.bossOutcome(_ui.value.monster?.hp ?: 0L, heroHp, elapsed, limit)
                _ui.update { it.copy(bossTimeLeftMillis = WorldProgress.bossTimeLeft(elapsed, limit)) }
                when (outcome) {
                    BossOutcome.VICTORY -> return FightResult.KILL
                    BossOutcome.DEFEAT -> return FightResult.DEFEAT
                    BossOutcome.TIMEOUT -> return FightResult.TIMEOUT
                    BossOutcome.ONGOING -> Unit
                }
            } else if ((_ui.value.monster?.hp ?: 0L) <= 0L) {
                return FightResult.KILL
            }
            if (pending == null && now >= heroNext) {
                val hit = hero.rollHit()
                _events.tryEmit(AdventureEvent.HeroAttack(hit))
                pending = hit
                pendingAt = now + (_ui.value.attackDurationMillis * STAB_IMPACT_FRACTION).toLong()
                heroNext = now + hero.attackIntervalMillis
            }
            if (now >= monsterNext) {
                val dmg = monster.stats.rollDamage()
                heroHp -= dmg
                monsterNext = now + monster.stats.attackIntervalMillis
                _events.tryEmit(AdventureEvent.MonsterHit(dmg))
                publishHeroHp()
                if (heroHp <= 0) return FightResult.DEFEAT
            }
            var next = minOf(heroNext, monsterNext, if (pending != null) pendingAt else Long.MAX_VALUE)
            if (deadline != null) next = minOf(next, now + 100)
            delay((next - now).coerceIn(8L, 100L))
        }
    }

    private suspend fun onKill() {
        val st = _ui.value
        val m = st.monster ?: return
        val gold = m.stats.rollGold()
        val xp = m.stats.xp
        val item = Loot.rollDrop(m.stats.level, stats?.totalStats?.luck ?: 0)
        _ui.update { it.copy(phase = MonsterPhase.DYING) }
        _events.tryEmit(AdventureEvent.Kill(xp, gold))
        val zoneId = st.zone.id
        val now = System.currentTimeMillis()
        val hp = currentHp()
        lastSavedHp = hp
        // App scope: the reward must be saved even if the loop is cancelled right now.
        idleApp.appScope.launch {
            var drop: LootDrop? = null
            var leveledUp = false
            runCatching {
                repository.update { s ->
                    var n = CombatRules.applyKillReward(s, xp, gold, zoneId, now)
                    leveledUp = n.character.level > s.character.level
                    if (item != null) {
                        val (withItem, d) = Loot.grant(n, item)
                        n = withItem
                        drop = d
                    }
                    n.copy(world = n.world.copy(heroHp = hp))
                }
            }
            if (leveledUp) _events.tryEmit(AdventureEvent.LevelUp)
            drop?.let { _events.tryEmit(AdventureEvent.Loot(it)) }
        }
        waitWithRegen(CombatRules.DEATH_MILLIS)
        _ui.update { it.copy(phase = MonsterPhase.GONE) }
    }

    private suspend fun onBossKill(zone: Zone) {
        val m = _ui.value.monster ?: return
        val gold = m.stats.rollGold()
        val xp = m.stats.xp
        val item = Loot.bossDrop(m.stats.level)
        val wasOpen = Zones.next(zone)?.let { WorldProgress.isUnlocked(_ui.value.world, it) } ?: true
        _ui.update { it.copy(phase = MonsterPhase.DYING) }
        _events.tryEmit(AdventureEvent.Kill(xp, gold))
        endBossFight()
        val now = System.currentTimeMillis()
        val hp = currentHp()
        lastSavedHp = hp
        idleApp.appScope.launch {
            var drop: LootDrop? = null
            var leveledUp = false
            runCatching {
                repository.update { s ->
                    val won = WorldProgress.applyBossVictory(s, zone, xp, gold, now)
                    leveledUp = won.character.level > s.character.level
                    val (withItem, d) = Loot.grant(won, item)
                    drop = d
                    withItem.copy(world = withItem.world.copy(heroHp = hp))
                }
            }
            _events.tryEmit(AdventureEvent.BossVictory(zone, if (wasOpen) null else Zones.next(zone)))
            if (leveledUp) _events.tryEmit(AdventureEvent.LevelUp)
            drop?.let { _events.tryEmit(AdventureEvent.Loot(it)) }
        }
        waitWithRegen(CombatRules.DEATH_MILLIS * 2)
        _ui.update { it.copy(monster = null, phase = MonsterPhase.GONE) }
    }

    private suspend fun onDefeat() {
        heroHp = 0.0
        publishHeroHp()
        _ui.update { it.copy(defeated = true, phase = MonsterPhase.GONE) }
        delay(CombatRules.DEFEAT_MILLIS)
        recoverFromDefeat()
    }

    private fun recoverFromDefeat() {
        heroHp = (stats?.maxHp ?: 1).toDouble()
        publishHeroHp()
        _ui.update { it.copy(defeated = false, phase = MonsterPhase.GONE, monster = null) }
    }

    private suspend fun waitWithRegen(millis: Long) {
        val end = SystemClock.elapsedRealtime() + millis
        var last = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            stats?.let { regen(it, now - last) }
            last = now
            if (now >= end) return
            delay((end - now).coerceAtMost(100L))
        }
    }

    private fun regen(hero: CombatStats, millis: Long) {
        if (millis <= 0 || heroHp >= hero.maxHp) return
        val before = ceil(heroHp).toInt()
        heroHp = (heroHp + CombatRules.regen(hero.maxHp, millis)).coerceAtMost(hero.maxHp.toDouble())
        if (ceil(heroHp).toInt() != before) publishHeroHp()
    }

    private fun publishHeroHp() {
        _ui.update { it.copy(heroHp = currentHp()) }
    }
}
