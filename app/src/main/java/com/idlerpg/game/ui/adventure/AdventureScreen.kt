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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.idlerpg.game.domain.world.LootDrop
import com.idlerpg.game.domain.world.WorldProgress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.OfflineProgress
import com.idlerpg.game.domain.world.OfflineReward
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.adventure.MonsterPainters.drawMonster
import com.idlerpg.game.ui.character.weapon.FirstPersonWeapon
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is AdventureEvent.HeroAttack -> {
                    pendingHits.addLast(e.hit)
                    while (pendingHits.size > 3) pendingHits.removeFirst()
                    attacks++
                }
                is AdventureEvent.MonsterHit -> {
                    floats += FloatText(
                        nextFloatId++, FloatKind.HERO_DAMAGE, e.damage.toLong(), 0,
                        -0.45f + Random.nextFloat() * 0.1f, 0.74f,
                    )
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

    val monsterName = monster?.let { stringResource(it.stats.type.nameRes()) } ?: ""
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
        Canvas(Modifier.fillMaxSize()) {
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

        Hud(ui, monsterName, onZoneClick = { showZones = true }, onChallengeBoss = vm::challengeBoss)

        loot.firstOrNull()?.let { drop ->
            key(drop.item.id) { LootPopup(drop, Modifier.align(BiasAlignment(0f, 0.5f))) { loot.remove(drop) } }
        }
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
}

fun Zone.nameRes(): Int = when (id) {
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
}

// ------------------------------------------------------------------ HUD

internal val hudShadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f), 6f)

@Composable
private fun Hud(ui: AdventureUiState, monsterName: String, onZoneClick: () -> Unit, onChallengeBoss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(12.dp)) {
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
                val ready = kills >= WorldProgress.BOSS_KILLS_REQUIRED
                Text(
                    if (ready) {
                        stringResource(R.string.zone_boss_ready)
                    } else {
                        stringResource(R.string.zone_boss_progress, kills.toInt(), WorldProgress.BOSS_KILLS_REQUIRED.toInt())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) Color(0xFFFFD54F) else Color(0xFFB9B3A2),
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

        // Hero HP (bottom-left; the dagger occupies the bottom-right)
        Column(Modifier.align(Alignment.BottomStart).width(170.dp)) {
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

private enum class FloatKind { DAMAGE, CRIT, HERO_DAMAGE, REWARD }

/** [x]/[y] are alignment biases (-1..1) relative to the scene. */
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
        FloatKind.HERO_DAMAGE -> 40f
        else -> 110f
    } * LinearOutSlowInEasing.transform(t)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(BiasAlignment(f.x, f.y))
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

@Composable
private fun WelcomeBackDialog(reward: OfflineReward, onDismiss: () -> Unit) {
    val totalMinutes = reward.awayMillis / 60_000
    val away = if (totalMinutes >= 60) {
        stringResource(R.string.duration_hours_minutes, (totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
    } else {
        stringResource(R.string.duration_minutes, totalMinutes.toInt())
    }
    val fmt = { n: Long -> String.format(Locale.GERMANY, "%,d", n) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.welcome_back_ok)) } },
        title = { Text(stringResource(R.string.welcome_back_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.welcome_back_away, away))
                if (reward.awayMillis > reward.countedMillis) {
                    Text(
                        stringResource(R.string.welcome_back_capped, (OfflineProgress.MAX_OFFLINE_MILLIS / 3_600_000).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
        },
    )
}
