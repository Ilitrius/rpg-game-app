package com.idlerpg.game.ui.adventure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlerpg.game.R
import com.idlerpg.game.domain.character.Hit
import com.idlerpg.game.domain.combat.CombatRules
import com.idlerpg.game.domain.world.LootDrop
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.OfflineReward
import com.idlerpg.game.domain.world.WorldProgress
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.adventure.MonsterPainters.drawMonster
import com.idlerpg.game.ui.character.weapon.FirstPersonWeapon
import java.util.Locale
import kotlin.math.PI
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * First-person adventure view: painted zone landscape, the current monster (or boss) in front, the
 * hero's dagger stabbing on every attack, HUD with HP bars, floating damage numbers, kill rewards,
 * loot popups, boss challenge and zone switching.
 */
@Composable
fun AdventureScreen(modifier: Modifier = Modifier, vm: AdventureViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    // Pause the combat loop in the background; offline progress covers the gap.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onForeground()
                Lifecycle.Event.ON_STOP -> vm.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    var attacks by remember { mutableIntStateOf(0) }
    val pendingHits = remember { ArrayDeque<Hit>() }
    val floats = remember { mutableStateListOf<FloatText>() }
    var nextFloatId by remember { mutableLongStateOf(0L) }
    val hitFlash = remember { Animatable(0f) }
    val hurtFlash = remember { Animatable(0f) }
    val loot = remember { mutableStateListOf<LootDrop>() }
    var banner by remember { mutableStateOf<BannerMessage?>(null) }
    var showZones by remember { mutableStateOf(false) }
    // Last tap position as alignment bias (-1..1) for the floating damage number.
    var tapPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is AdventureEvent.HeroAttack -> {
                    pendingHits.addLast(e.hit)
                    while (pendingHits.size > 3) pendingHits.removeFirst()
                    attacks++
                }
                is AdventureEvent.TapHit -> {
                    val pos = tapPoint
                    floats += FloatText(
                        nextFloatId++, if (e.hit.critical) FloatKind.CRIT else FloatKind.DAMAGE, e.hit.damage, 0,
                        pos?.x ?: 0f, pos?.y ?: -0.1f,
                    )
                    if (e.combo >= 2) floats += FloatText(nextFloatId++, FloatKind.COMBO, e.combo.toLong(), 0, pos?.x ?: 0f, (pos?.y ?: -0.1f) + 0.09f)
                    attacks++ // short weapon swing as feedback; no pending hit queued, damage is already applied
                    scope.launch {
                        hitFlash.snapTo(1f)
                        hitFlash.animateTo(0f, tween(if (e.hit.critical) 380 else 260))
                    }
                }
                is AdventureEvent.MonsterHit -> {
                    // x = horizontal jitter (0..1) next to the hero HP bar, see FloatingLabel.
                    floats += FloatText(nextFloatId++, FloatKind.HERO_DAMAGE, e.damage.toLong(), 0, Random.nextFloat(), 0f)
                    scope.launch {
                        hurtFlash.snapTo(1f)
                        hurtFlash.animateTo(0f, tween(450))
                    }
                }
                is AdventureEvent.Kill -> {
                    floats += FloatText(nextFloatId++, FloatKind.REWARD, e.xp, e.gold, 0f, -0.30f)
                }
                is AdventureEvent.Loot -> {
                    if (loot.size < 4) loot += e.drop
                }
                is AdventureEvent.BossVictory -> banner = BannerMessage.Victory(e.zone, e.unlocked)
                is AdventureEvent.BossFailed -> banner = BannerMessage.Failed(e.outcome)
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "adventure")
    val time by infinite.animateFloat(
        0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "time",
    )
    val drift by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(300_000, easing = LinearEasing), RepeatMode.Restart), label = "clouds",
    )

    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val scene = remember(sceneSize, ui.zone.id) {
        if (sceneSize.width > 0 && sceneSize.height > 0) createZoneScene(ui.zone, sceneSize.toSize()) else null
    }

    val monster = ui.monster
    val approach = remember(monster?.spawnId) {
        Animatable(if (ui.phase == MonsterPhase.APPROACHING) 0f else 1f)
    }
    LaunchedEffect(monster?.spawnId) {
        if (approach.value < 1f) approach.animateTo(1f, tween(CombatRules.APPROACH_MILLIS.toInt(), easing = LinearOutSlowInEasing))
    }
    val death = remember(monster?.spawnId) { Animatable(0f) }
    val dying = ui.phase == MonsterPhase.DYING || ui.phase == MonsterPhase.GONE
    LaunchedEffect(monster?.spawnId, dying) {
        if (dying) death.animateTo(1f, tween(CombatRules.DEATH_MILLIS.toInt(), easing = FastOutSlowInEasing))
    }

    val monsterName = monster?.let { stringResource(it.stats.type.nameRes(ui.zone)) } ?: ""
    val sceneDescription = stringResource(R.string.adventure_scene_description, monsterName)

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF10140E))
            .onSizeChanged { sceneSize = it }
            .semantics { contentDescription = sceneDescription },
    ) {
        // Background landscape.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { scene?.drawBackground(this, drift, time) },
        )

        // Monster.
        val currentScene by rememberUpdatedState(scene)
        val currentMonster by rememberUpdatedState(monster)
        val tapDescription = stringResource(R.string.adventure_tap_description)
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = tapDescription }
                .pointerInput(vm) {
                    detectTapGestures { p ->
                        val sc = currentScene ?: return@detectTapGestures
                        val m = currentMonster ?: return@detectTapGestures
                        val h = min(size.width * 0.66f, size.height * 0.38f) * m.stats.type.sizeFactor()
                        val inX = abs(p.x - size.width / 2f) <= h * 0.55f
                        val inY = p.y in (sc.monsterGroundY - h * 1.05f)..(sc.monsterGroundY + h * 0.08f)
                        if (inX && inY) {
                            tapPoint = Offset(p.x / size.width * 2f - 1f, p.y / size.height * 2f - 1f - 0.08f)
                            vm.onEnemyTapped()
                        }
                    }
                },
        ) {
            val sc = scene ?: return@Canvas
            val m = monster ?: return@Canvas
            val a = approach.value
            val d = death.value
            val depth = lerp(0.14f, 1f, a)
            val groundY = sc.horizonY + (sc.monsterGroundY - sc.horizonY) * depth
            val baseHeight = min(size.width * 0.66f, size.height * 0.38f) * m.stats.type.sizeFactor()
            val hop = if (a < 1f) abs(sin(time * PI.toFloat() * 4.5f)) * (1f - a) * baseHeight * 0.05f else 0f
            val flash = hitFlash.value
            val knock = flash * baseHeight * (if (m.stats.type.isBoss) 0.012f else 0.025f)
            drawMonster(
                type = m.stats.type,
                ground = Offset(size.width * 0.5f, groundY - hop - knock + d * baseHeight * 0.06f),
                heightPx = baseHeight * depth * (1f - 0.05f * flash),
                time = time,
                breath = sin(time * PI.toFloat()),
                flash = flash,
                alpha = (a * 2.5f).coerceAtMost(1f) * (1f - d),
                squash = d * 0.7f,
                zone = ui.zone,
            )
        }

        // Nearest foliage + vignette in front of the monster.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind { scene?.drawForeground(this, time) },
        )

        FirstPersonWeapon(
            weapon = ui.equippedWeapon,
            attackTrigger = attacks,
            modifier = Modifier.fillMaxSize(),
            attackDurationMillis = ui.attackDurationMillis,
            onStabImpact = {
                val hit = pendingHits.removeFirstOrNull()
                if (hit != null) {
                    floats += FloatText(
                        nextFloatId++, if (hit.critical) FloatKind.CRIT else FloatKind.DAMAGE, hit.damage, 0,
                        (Random.nextFloat() - 0.5f) * 0.4f, -0.02f + Random.nextFloat() * 0.08f,
                    )
                    scope.launch {
                        hitFlash.snapTo(1f)
                        hitFlash.animateTo(0f, tween(if (hit.critical) 380 else 260))
                    }
                }
            },
        )

        // Red edge flash when the hero is hit.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val f = hurtFlash.value
                    if (f > 0f) {
                        drawRect(
                            Brush.radialGradient(
                                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xFFB00000).copy(alpha = 0.45f * f),
                                center = center, radius = size.maxDimension * 0.7f,
                            ),
                        )
                    }
                },
        )

        floats.forEach { f -> key(f.id) { FloatingLabel(f) { floats.remove(f) } } }

        val density = LocalDensity.current
        val lootMaxWidth = if (sceneSize.width > 0) with(density) { (sceneSize.width * LOOT_MAX_WIDTH_FRACTION).toDp() } else 214.dp
        Hud(
            ui, monsterName,
            onZoneClick = { showZones = true },
            onChallengeBoss = vm::challengeBoss,
            onToggleAutoBoss = vm::toggleAutoBoss,
            loot = {
                // Bottom-left above the hero block: clear of the weapon (right) and the monster (above).
                loot.firstOrNull()?.let { drop ->
                    key(drop.item.id) {
                        LootPopup(drop, Modifier.padding(bottom = 10.dp).widthIn(max = lootMaxWidth)) { loot.remove(drop) }
                    }
                }
            },
        )

        banner?.let { b -> key(b) { BossBanner(b) { if (banner == b) banner = null } } }

        AnimatedVisibility(ui.defeated, enter = fadeIn(tween(300)), exit = fadeOut(tween(500))) {
            DefeatOverlay()
        }
        TravelOverlay(ui.travelingTo)
    }

    if (showZones) {
        ZoneSheet(
            world = ui.world,
            current = ui.zone,
            onSelect = { zone ->
                showZones = false
                vm.travelTo(zone.id)
            },
            onDismiss = { showZones = false },
        )
    }
    ui.welcomeBack?.let { WelcomeBackDialog(it, vm::dismissWelcomeBack) }
}

// ------------------------------------------------------------------ mapping

fun MonsterType.nameRes(): Int = when (this) {
    MonsterType.SLIME -> R.string.monster_schleim
    MonsterType.GIANT_RAT -> R.string.monster_riesenratte
    MonsterType.BOAR -> R.string.monster_wildschwein
    MonsterType.WOLF -> R.string.monster_wolf
    MonsterType.FOREST_SPIDER -> R.string.monster_waldspinne
    MonsterType.MUSHROOM -> R.string.monster_pilzling
    MonsterType.TOXIC_SLIME -> R.string.monster_giftschleim
    MonsterType.MIRE_TOAD -> R.string.monster_moorkroete
    MonsterType.WISP -> R.string.monster_irrlicht
    MonsterType.STONE_GOLEM -> R.string.monster_steingolem
    MonsterType.SNOW_WOLF -> R.string.monster_schneewolf
    MonsterType.ICE_WISP -> R.string.monster_eisgeist
    MonsterType.BAT -> R.string.monster_fledermaus
    MonsterType.CAVE_SPIDER -> R.string.monster_hoehlenspinne
    MonsterType.CRYSTAL_GOLEM -> R.string.monster_kristallgolem
    MonsterType.SLIME_KING -> R.string.monster_schleimkoenig
    MonsterType.TREANT -> R.string.monster_baumhueter
    MonsterType.HYDRA -> R.string.monster_sumpfhydra
    MonsterType.YETI -> R.string.monster_yeti
    MonsterType.DRAGON -> R.string.monster_hoehlendrache
    MonsterType.GEN_SWARM, MonsterType.GEN_BEAST, MonsterType.GEN_BRUTE, MonsterType.GEN_BOSS -> R.string.monster_generic
}

/** Name of this monster type as it appears in [zone] (generated zones name by biome, stage and role). */
fun MonsterType.nameRes(zone: Zone): Int {
    val biome = zone.biome ?: return nameRes()
    return when {
        !isGenerated -> nameRes()
        isBoss -> WorldNames.boss[biome.ordinal][zone.stage]
        else -> WorldNames.monster[biome.ordinal][zone.monsters.indexOf(this).coerceAtLeast(0)]
    }
}

fun Zone.nameRes(): Int = if (biome != null) WorldNames.zone[biome.ordinal][stage] else when (id) {
    Zones.WALD_ID -> R.string.zone_wald
    Zones.SUMPF_ID -> R.string.zone_sumpf
    Zones.GEBIRGE_ID -> R.string.zone_gebirge
    Zones.HOEHLE_ID -> R.string.zone_hoehle
    else -> R.string.zone_wiese
}

/** Relative on-screen size of each monster type. */
internal fun MonsterType.sizeFactor(): Float = when (this) {
    MonsterType.SLIME -> 0.82f
    MonsterType.GIANT_RAT -> 0.92f
    MonsterType.BOAR -> 1.08f
    MonsterType.WOLF, MonsterType.SNOW_WOLF -> 1.02f
    MonsterType.FOREST_SPIDER -> 0.9f
    MonsterType.CAVE_SPIDER -> 0.95f
    MonsterType.MUSHROOM -> 0.95f
    MonsterType.TOXIC_SLIME -> 0.88f
    MonsterType.MIRE_TOAD -> 0.98f
    MonsterType.WISP, MonsterType.ICE_WISP -> 0.9f
    MonsterType.STONE_GOLEM, MonsterType.CRYSTAL_GOLEM -> 1.12f
    MonsterType.BAT -> 0.95f
    MonsterType.SLIME_KING -> 1.3f
    MonsterType.TREANT, MonsterType.HYDRA, MonsterType.YETI -> 1.38f
    MonsterType.DRAGON -> 1.25f
    MonsterType.GEN_SWARM -> 0.88f
    MonsterType.GEN_BEAST -> 1.0f
    MonsterType.GEN_BRUTE -> 1.15f
    MonsterType.GEN_BOSS -> 1.38f
}

// ------------------------------------------------------------------ HUD

internal val hudShadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f), 6f)

/** Inner padding of the HUD. */
private val HudPadding = 12.dp

/** Width of the hero block (name, HP, XP bar) in the bottom-left corner. */
private val HeroBlockWidth = 170.dp

/** Bottom edge of the hero HP bar above the HUD's bottom edge (XP bar 12 dp + 4 dp gap). */
private val HeroHpBarBottom = 16.dp

/**
 * Maximum loot popup width as share of the scene width. The popup sits bottom-left directly above
 * the hero block (≈ 78–150 dp above the scene's bottom edge). Measured for 1080×2400 (scene
 * ≈ 411×786 dp below the status bar / above the menu bar, weapon unit 3 px): in that band every
 * weapon at rest incl. idle sway starts at x ≥ 238 dp (dagger 238, fist 239, sword 245, mace/axe
 * 273 dp — the long ones lean out over the right edge), and monsters incl. ground shadow and
 * death animation stay ≥ 190 dp above the bottom. 12 dp + 0.52 × 411 dp = 226 dp keeps the card
 * clear of both. All weapon geometry scales with the width, so the fraction holds on other
 * portrait screens. (Swing follow-throughs of sword/axe sweep through this corner for ~0.2 s.)
 */
internal const val LOOT_MAX_WIDTH_FRACTION = 0.52f

@Composable
private fun Hud(
    ui: AdventureUiState,
    monsterName: String,
    onZoneClick: () -> Unit,
    onChallengeBoss: () -> Unit,
    onToggleAutoBoss: () -> Unit,
    loot: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(HudPadding)) {
        // Zone label (top-left), opens the zone sheet.
        val changeHint = stringResource(R.string.zone_change_hint)
        Row(
            Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xCC1B1F1A))
                .clickable(onClickLabel = changeHint, onClick = onZoneClick)
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    stringResource(ui.zone.nameRes()),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.zone_number, ui.zone.order),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD8D2C0),
                )
                val kills = ui.killsTowardsBoss.coerceAtMost(WorldProgress.BOSS_KILLS_REQUIRED)
                val (bossLine, bossColor) = when (ui.bossHudStatus) {
                    BossHudStatus.FIGHTING -> stringResource(R.string.zone_boss_fighting) to Color(0xFFFF8A65)
                    BossHudStatus.READY -> stringResource(R.string.zone_boss_ready) to Color(0xFFFFD54F)
                    BossHudStatus.PROGRESS -> stringResource(
                        R.string.zone_boss_progress, kills.toInt(), WorldProgress.BOSS_KILLS_REQUIRED.toInt(),
                    ) to Color(0xFFB9B3A2)
                }
                Text(
                    bossLine,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (ui.bossHudStatus == BossHudStatus.PROGRESS) FontWeight.Normal else FontWeight.Bold,
                    ),
                    color = bossColor,
                )
            }
            Icon(
                Icons.Filled.ExpandMore, contentDescription = null,
                tint = Color(0xFFD8D2C0), modifier = Modifier.padding(start = 2.dp).size(22.dp),
            )
        }

        // Boss challenge (top-right).
        AnimatedVisibility(
            visible = ui.canChallengeBoss,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(300)), exit = fadeOut(tween(200)),
        ) {
            BossChallengeButton(onChallengeBoss)
        }
        AutoBossChip(ui.autoBoss, onToggleAutoBoss, Modifier.align(Alignment.TopEnd).padding(top = if (ui.canChallengeBoss) 52.dp else 0.dp))

        // Monster name + level + HP (top center)
        val m = ui.monster
        AnimatedVisibility(
            visible = m != null && ui.phase != MonsterPhase.GONE,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            if (m != null) {
                val boss = m.stats.type.isBoss
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (boss) {
                            Text(
                                stringResource(R.string.boss_badge),
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF2B1D05), letterSpacing = 1.5.sp),
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(listOf(Color(0xFFFFE69A), Color(0xFFE0A82E), Color(0xFFA8741A))),
                                        RoundedCornerShape(5.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            monsterName,
                            style = TextStyle(
                                fontSize = if (boss) 22.sp else 20.sp, fontWeight = FontWeight.Bold,
                                color = if (boss) Color(0xFFFFE9B0) else Color.White, shadow = hudShadow,
                            ),
                        )
                    }
                    Text(
                        stringResource(R.string.monster_level, m.stats.level),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFE08A), shadow = hudShadow),
                    )
                    Spacer(Modifier.height(4.dp))
                    HpBar(
                        current = m.hp, max = m.stats.maxHp,
                        fill = if (boss) {
                            listOf(Color(0xFFFFF1B8), Color(0xFFF2B935), Color(0xFF9A6410))
                        } else {
                            listOf(Color(0xFFFF6B5A), Color(0xFFD32F2F), Color(0xFF8E1414))
                        },
                        modifier = Modifier.width(if (boss) 270.dp else 230.dp).height(if (boss) 20.dp else 16.dp),
                        frame = if (boss) Color(0xFFFFE08A) else Color(0xFFC9A227),
                    )
                    if (boss && ui.bossFight) {
                        Spacer(Modifier.height(6.dp))
                        BossTimer(ui.bossTimeLeftMillis, Modifier.width(270.dp))
                    }
                }
            }
        }

        // Loot popup + hero HP (bottom-left; the weapon occupies the bottom-right)
        Column(Modifier.align(Alignment.BottomStart)) {
            loot()
            HeroBlock(ui)
        }
    }
}

@Composable
private fun HeroBlock(ui: AdventureUiState) {
    Column(Modifier.width(HeroBlockWidth)) {
        Text(
            stringResource(R.string.hero_label, ui.heroName, ui.heroLevel),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = hudShadow),
        )
        Spacer(Modifier.height(4.dp))
        HpBar(
            current = ui.heroHp.toLong(), max = ui.heroMaxHp.toLong(),
            fill = listOf(Color(0xFF9CE07A), Color(0xFF43A047), Color(0xFF1B5E20)),
            modifier = Modifier.fillMaxWidth().height(16.dp),
        )
        Spacer(Modifier.height(4.dp))
        HpBar(
            current = ui.heroXp, max = ui.heroXpNeeded,
            fill = listOf(Color(0xFFB9A8FF), Color(0xFF7E57C2), Color(0xFF3F1F87)),
            modifier = Modifier.fillMaxWidth().height(12.dp),
            label = stringResource(R.string.xp_value, ui.heroXp, ui.heroXpNeeded),
        )
    }
}

/** Animated resource bar (HP, XP). [label] defaults to "current / max". */
@Composable
private fun HpBar(
    current: Long,
    max: Long,
    fill: List<Color>,
    modifier: Modifier = Modifier,
    frame: Color = Color(0xFFC9A227),
    label: String? = null,
) {
    val target = if (max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
    val shown = remember { Animatable(target) }
    val lag = remember { Animatable(target) }
    LaunchedEffect(target) {
        launch { shown.animateTo(target, tween(160)) }
        if (target < lag.value) {
            kotlinx.coroutines.delay(250)
            lag.animateTo(target, tween(400))
        } else {
            lag.snapTo(target)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            drawRoundRect(Color(0xE6120E0C), cornerRadius = r)
            val inset = 2.dp.toPx()
            val inner = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val ir = androidx.compose.ui.geometry.CornerRadius(inner.height / 2f)
            if (lag.value > shown.value) {
                drawRoundRect(
                    Color(0xFFFFE9A8).copy(alpha = 0.75f), Offset(inset, inset),
                    inner.copy(width = inner.width * lag.value), ir,
                )
            }
            if (shown.value > 0f) {
                drawRoundRect(
                    Brush.verticalGradient(fill, startY = inset, endY = inset + inner.height),
                    Offset(inset, inset), inner.copy(width = (inner.width * shown.value).coerceAtLeast(inner.height)), ir,
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), startY = inset, endY = inset + inner.height * 0.55f),
                    Offset(inset + 2f, inset + 1f),
                    androidx.compose.ui.geometry.Size(((inner.width * shown.value) - 4f).coerceAtLeast(0f), inner.height * 0.45f), ir,
                )
            }
            drawRoundRect(frame.copy(alpha = 0.85f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        }
        Text(
            label ?: stringResource(R.string.hp_value, current.toInt(), max.toInt()),
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = hudShadow),
        )
    }
}

// ------------------------------------------------------------------ floating texts

private enum class FloatKind { DAMAGE, CRIT, HERO_DAMAGE, REWARD, COMBO }

/** [x]/[y] are alignment biases (-1..1) relative to the scene; for [FloatKind.HERO_DAMAGE] [x] is a 0..1 jitter. */
private data class FloatText(val id: Long, val kind: FloatKind, val a: Long, val b: Long, val x: Float, val y: Float)

@Composable
private fun FloatingLabel(f: FloatText, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val duration = when (f.kind) {
        FloatKind.REWARD -> 1600
        FloatKind.CRIT -> 1100
        else -> 850
    }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(duration, easing = LinearEasing))
        onDone()
    }
    val t = p.value
    val pop = if (t < 0.12f) lerp(1.5f, 1f, t / 0.12f) else 1f
    val alpha = if (t > 0.65f) 1f - (t - 0.65f) / 0.35f else 1f
    val rise = when (f.kind) {
        FloatKind.REWARD -> 70f
        FloatKind.HERO_DAMAGE -> 22f // stays below the loot popup
        else -> 110f
    } * LinearOutSlowInEasing.transform(t)

    Box(Modifier.fillMaxSize()) {
        val place = if (f.kind == FloatKind.HERO_DAMAGE) {
            // Right next to the hero HP bar (left of the resting weapon), x = jitter 0..1.
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = HudPadding + HeroBlockWidth + 6.dp + 8.dp * f.x, bottom = HudPadding + HeroHpBarBottom - 4.dp)
        } else {
            Modifier.align(BiasAlignment(f.x, f.y))
        }
        Column(
            place
                .graphicsLayer {
                    translationY = -rise * density
                    scaleX = pop
                    scaleY = pop
                    this.alpha = alpha
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (f.kind) {
                FloatKind.DAMAGE -> Text(
                    f.a.toString(),
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFF6E8), shadow = hudShadow),
                )
                FloatKind.CRIT -> {
                    Text(
                        stringResource(R.string.crit_label),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF9800), shadow = hudShadow),
                    )
                    Text(
                        f.a.toString(),
                        style = TextStyle(
                            fontSize = 46.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD54F),
                            shadow = Shadow(Color(0xFF7A3A00), Offset(0f, 3f), 10f),
                        ),
                    )
                }
                FloatKind.HERO_DAMAGE -> Text(
                    stringResource(R.string.damage_taken, f.a.toInt()),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF5A4A), shadow = hudShadow),
                )
                FloatKind.COMBO -> Text(
                    stringResource(R.string.combo_label, f.a.toInt()),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFB74D), shadow = hudShadow),
                )
                FloatKind.REWARD -> Text(
                    stringResource(R.string.kill_reward, f.a.toInt(), f.b.toInt()),
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFE08A), shadow = hudShadow),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ overlays

@Composable
private fun DefeatOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0x99200000), 1f to Color(0xEE0A0000),
                        center = center, radius = size.maxDimension * 0.7f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.defeated_title),
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color(0xFFE53935), shadow = hudShadow),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.defeated_subtitle),
                style = TextStyle(fontSize = 16.sp, color = Color(0xFFE8DCCB), shadow = hudShadow),
            )
        }
    }
}

/** "8 Std. 12 Min.", "8 Std." or "45 Min." */
@Composable
private fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_minutes, m)
        m == 0 -> stringResource(R.string.duration_hours, h)
        else -> stringResource(R.string.duration_hours_minutes, h, m)
    }
}

@Composable
private fun WelcomeBackDialog(reward: OfflineReward, onDismiss: () -> Unit) {
    val fmt = { n: Long -> String.format(Locale.getDefault(), "%,d", n) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.welcome_back_ok)) } },
        title = { Text(stringResource(R.string.welcome_back_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.welcome_back_away, formatDuration(reward.awayMillis)))
                CountedTime(reward)
                if (reward.heroTooWeak) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.welcome_back_too_weak), color = Color(0xFFFF8A65))
                } else {
                    Text(
                        stringResource(R.string.welcome_back_rate, (OfflineProgress.EFFICIENCY * 60).roundToInt().coerceAtLeast(1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.welcome_back_kills, fmt(reward.kills)), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.welcome_back_xp, fmt(reward.xp)), color = Color(0xFF9CE07A))
                    Text(stringResource(R.string.welcome_back_gold, fmt(reward.gold)), color = MaterialTheme.colorScheme.primary)
                    if (reward.levelsGained > 0) {
                        Text(stringResource(R.string.welcome_back_levels, reward.levelsGained), color = Color(0xFFFFE08A))
                    }
                    if (reward.items.isNotEmpty()) {
                        Text(stringResource(R.string.welcome_back_items, reward.items.size), color = Color(0xFF4AA3F5))
                        if (reward.autoSoldItems > 0) {
                            Text(
                                stringResource(R.string.welcome_back_items_sold, reward.autoSoldItems, fmt(reward.autoSoldGold)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Counted offline time against the maximum ([OfflineProgress.MAX_OFFLINE_MILLIS]) with a bar; highlights the cap. */
@Composable
private fun CountedTime(reward: OfflineReward) {
    val capColor = Color(0xFFFFB74D)
    val fraction = (reward.countedMillis.toFloat() / OfflineProgress.MAX_OFFLINE_MILLIS).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.welcome_back_counted_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        R.string.welcome_back_counted_value,
                        formatDuration(reward.countedMillis),
                        formatDuration(OfflineProgress.MAX_OFFLINE_MILLIS),
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (reward.capped) capColor else MaterialTheme.colorScheme.onSurface,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (reward.capped) capColor else MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round,
            )
            if (reward.capped) {
                Text(
                    stringResource(R.string.welcome_back_capped),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = capColor,
                )
            }
        }
    }
}
