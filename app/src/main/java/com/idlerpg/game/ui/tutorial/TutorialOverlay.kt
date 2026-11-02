package com.idlerpg.game.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idlerpg.game.R

/**
 * Screen areas (root coordinates) the overlay can point at, measured by `MainScreen`.
 * [adventure] = adventure view, [tabs] = one rect per menu bar entry in [com.idlerpg.game.ui.MainTab] order.
 */
data class TutorialAnchors(val adventure: Rect = Rect.Zero, val tabs: List<Rect> = emptyList())

/** Highlight rect for [target], or null (no highlight / not measured yet). */
fun TutorialTarget.highlight(anchors: TutorialAnchors, zoneHeightPx: Float): Rect? {
    val a = anchors.adventure
    return when (this) {
        TutorialTarget.None -> null
        // The monster stands roughly in the upper middle of the scene, the zone label sits top left.
        TutorialTarget.Enemy -> if (a.isEmpty) null else Rect(
            a.left + a.width * 0.18f, a.top + a.height * 0.2f, a.right - a.width * 0.18f, a.top + a.height * 0.62f,
        )
        TutorialTarget.Zone -> if (a.isEmpty) null else Rect(a.left, a.top, a.left + a.width * 0.58f, a.top + zoneHeightPx)
        is TutorialTarget.Tabs -> tabs
            .mapNotNull { tab -> anchors.tabs.getOrNull(tab.ordinal)?.takeUnless { it.isEmpty } }
            .takeIf { it.isNotEmpty() }
            ?.reduce { acc, r -> Rect(minOf(acc.left, r.left), minOf(acc.top, r.top), maxOf(acc.right, r.right), maxOf(acc.bottom, r.bottom)) }
    }
}

/**
 * Introduction shown over the whole app: dims everything except the highlighted area, explains it in
 * a card and blocks touches underneath. [onFinish] is called on "Los geht's" and on "Überspringen".
 */
@Composable
fun TutorialOverlay(anchors: TutorialAnchors, onFinish: () -> Unit) {
    val steps = TutorialSteps.all
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val density = LocalDensity.current
    val zoneHeightPx = with(density) { 96.dp.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val target = step.target.highlight(anchors, zoneHeightPx)?.inflate(pad)
    val pulse by animateFloatAsState(if (target == null) 0f else 1f, tween(300), label = "tutorialHighlight")

    BackHandler { if (index > 0) index-- else onFinish() }

    Box(
        Modifier
            .fillMaxSize()
            // Swallow all touches so the game underneath is not operated by accident.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = CornerRadius(18.dp.toPx())
            // Scrim with a hole (even-odd path): no offscreen layer or blend mode needed.
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (target != null) addRoundRect(RoundRect(target, radius))
            }
            drawPath(scrim, Color(0xD9080A08))
            if (target != null) {
                // Soft golden glow + crisp outline around the spotlight.
                for (ring in 1..4) {
                    val grow = ring * 3.dp.toPx()
                    drawRoundRect(
                        color = Gold.copy(alpha = 0.22f * pulse / ring),
                        topLeft = Offset(target.left - grow, target.top - grow),
                        size = Size(target.width + 2 * grow, target.height + 2 * grow),
                        cornerRadius = CornerRadius(radius.x + grow),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                drawRoundRect(Gold.copy(alpha = pulse), target.topLeft, target.size, radius, style = Stroke(2.dp.toPx()))
            }
        }

        // Card goes to the half of the screen that is not highlighted.
        val cardAtTop = target != null && target.center.y > anchors.adventure.center.y
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (cardAtTop) 24.dp else 120.dp),
            contentAlignment = when {
                target == null -> Alignment.Center
                cardAtTop -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            },
        ) {
            StepCard(
                step = step,
                index = index,
                count = steps.size,
                onBack = { if (index > 0) index-- },
                onNext = { if (index < steps.lastIndex) index++ else onFinish() },
                onSkip = onFinish,
            )
        }
    }
}

@Composable
private fun StepCard(step: TutorialStep, index: Int, count: Int, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val last = index == count - 1
    Card(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedContent(step, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "tutorialStep") { s ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(s.title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(s.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                repeat(count) { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 10.dp else 7.dp)
                            .background(
                                if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                CircleShape,
                            )
                            .align(Alignment.CenterVertically),
                    )
                }
            }
            Text(
                stringResource(R.string.tutorial_progress, index + 1, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onSkip) { Text(stringResource(R.string.tutorial_skip)) }
                Spacer(Modifier.weight(1f))
                if (index > 0) TextButton(onClick = onBack) { Text(stringResource(R.string.tutorial_back)) }
                Button(onClick = onNext) { Text(stringResource(if (last) R.string.tutorial_finish else R.string.tutorial_next)) }
            }
        }
    }
}

private val Gold = Color(0xFFE0B54A)
