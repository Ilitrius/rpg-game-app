package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Clenched, gloved right fist seen from behind in first person: back of the hand towards the viewer,
 * knuckle row (with steel studs) pointing forward/up at the monster, curled index and middle finger
 * on the thumb side, thumb locked across them, glove cuff at the wrist. Same unit space as
 * [HandPainter] (grip center (50, 252), forearm towards +y), so it sits on the shared [ArmArt]
 * forearm. Pure vector data ([VArt]) — previewable in JVM tests.
 */
internal object FistPainter {

    /** Centers of the four main knuckles (index → little finger). */
    val KNUCKLES: List<Offset> = listOf(Offset(50f, 205f), Offset(71f, 199f), Offset(92f, 202f), Offset(111f, 211f))

    /** Front of the knuckle row (middle knuckle) — the striking point of a punch. */
    val KNUCKLE_FRONT = Offset(71f, 192f)

    /** Low thumb-side edge of the fist — leads the downward hammer fist (STRIKE). */
    val HAMMER_POINT = Offset(26f, 262f)

    private val leatherHi = Color(0xFFD2A47C)
    private val leatherLight = Color(0xFFB5835C)
    private val leatherMid = Color(0xFF8A5B3A)
    private val leather = Color(0xFF6A4329)
    private val leatherDeep = Color(0xFF3E2616)
    private val leatherShadow = Color(0xFF26160C)
    private val outline = ArmArt.leatherDark
    private val stitch = ArmArt.stitch

    // ---------------------------------------------------------------- shapes

    /** Back of the hand incl. the knuckle ridge (top edge bumps over the four knuckles). */
    internal val dorsumPath = vpath {
        moveTo(56f, 292f)
        cubicTo(52f, 270f, 46f, 246f, 44f, 226f)
        cubicTo(43f, 216f, 42f, 208f, 44f, 203f)
        // index knuckle
        cubicTo(46f, 197f, 53f, 195f, 58f, 197f)
        cubicTo(59.5f, 197.6f, 60.5f, 198f, 61.5f, 197.6f)
        // middle knuckle
        cubicTo(64f, 192f, 72f, 189.5f, 78f, 192.5f)
        cubicTo(80f, 193.5f, 81.5f, 195f, 82.5f, 195.5f)
        // ring knuckle
        cubicTo(86f, 192f, 94f, 192f, 99f, 196f)
        cubicTo(100.5f, 197.2f, 101.5f, 198.6f, 102.5f, 199.5f)
        // little-finger knuckle and the rounded outer edge of the hand
        cubicTo(107f, 199f, 114f, 201f, 118f, 207f)
        cubicTo(123f, 214f, 126f, 226f, 125.5f, 238f)
        cubicTo(125f, 256f, 119f, 274f, 111f, 292f)
        cubicTo(92f, 299f, 72f, 299f, 56f, 292f)
        close()
    }

    /** Lower edge of the padded knuckle guard. */
    private val guardEdge = vpath { moveTo(43f, 224f); cubicTo(64f, 214f, 100f, 214f, 125f, 228f) }
    private val guardStitch = vpath { moveTo(45f, 229f); cubicTo(66f, 219.5f, 100f, 219.5f, 124.5f, 233f) }
    private val guard = vpath {
        moveTo(43f, 224f)
        cubicTo(42.5f, 214f, 42f, 207f, 44f, 203f)
        cubicTo(60f, 190f, 100f, 188f, 118f, 207f)
        cubicTo(121f, 211f, 124f, 219f, 125f, 228f)
        cubicTo(100f, 214f, 64f, 214f, 43f, 224f)
        close()
    }

    /** Curled index finger: proximal phalanx out to the left, middle phalanx folded back down. */
    internal val indexPath = vpath {
        moveTo(56f, 200f)
        cubicTo(48f, 195.5f, 37f, 195f, 28.5f, 198f)
        cubicTo(20f, 201f, 15.5f, 208f, 15.5f, 217f)
        cubicTo(15.5f, 229f, 17f, 239f, 21.5f, 246f)
        cubicTo(26f, 252f, 32.5f, 253f, 37.5f, 249.5f)
        cubicTo(36f, 241f, 35.5f, 231f, 37f, 223f)
        cubicTo(42f, 219f, 50f, 218.5f, 57f, 219.5f)
        close()
    }

    /** Dark hollow of the palm, visible between the folded fingers, the thumb and the hand. */
    private val palmHollow = vpath {
        moveTo(34f, 219f)
        lineTo(54f, 216f)
        lineTo(62f, 292f)
        lineTo(30f, 274f)
        lineTo(19f, 250f)
        close()
    }

    /** Outer (little-finger) edge of the hand — gets the rim light. */
    private val outerEdge = vpath {
        moveTo(116f, 205f)
        cubicTo(123f, 214f, 126f, 226f, 125.5f, 238f)
        cubicTo(125f, 256f, 119f, 274f, 111f, 292f)
    }

    /** Middle finger's folded middle phalanx peeking out below the index finger. */
    internal val middlePath = vpath {
        moveTo(46f, 236f)
        cubicTo(36f, 236f, 24f, 240f, 20f, 248f)
        cubicTo(16f, 256f, 18f, 266f, 26f, 270f)
        cubicTo(33f, 273f, 42f, 271f, 48f, 266f)
        close()
    }

    /** Thumb locked across the folded fingers; its base (thenar) merges into the hand at the wrist. */
    internal val thumbPath = vpath {
        moveTo(64f, 292f)
        cubicTo(54f, 290f, 44f, 282f, 36f, 274f)
        cubicTo(30f, 268f, 23f, 264f, 17.5f, 258f)
        cubicTo(12f, 252f, 13f, 242.5f, 20.5f, 240.5f)
        cubicTo(26.5f, 239f, 32f, 240.5f, 38f, 243.5f)
        cubicTo(44f, 246f, 50f, 248f, 56f, 253f)
        cubicTo(63f, 259f, 69f, 266f, 74f, 276f)
        cubicTo(73f, 283f, 69f, 289f, 64f, 292f)
        close()
    }

    /** Steel plate riveted over the thumbnail. */
    private val thumbPlate = vpath {
        moveTo(17.5f, 245f)
        cubicTo(16.5f, 250f, 19.5f, 254.5f, 24.5f, 256f)
        cubicTo(29.5f, 257.5f, 34.5f, 255.5f, 35.5f, 251f)
        cubicTo(34f, 246f, 29f, 242.5f, 24f, 242.5f)
        cubicTo(21f, 242.5f, 18.5f, 243.5f, 17.5f, 245f)
        close()
    }

    // ---------------------------------------------------------------- art

    /** The fist incl. glove cuff (no forearm), back to front. */
    val art: VArt = vart {
        art(ArmArt.cuff)
        fill(palmHollow, vertical(216f, 292f, 0f to Color(0xFF2A190E), 1f to Color(0xFF140A05)))

        // ---- folded fingers on the thumb side (behind the back of the hand)
        fill(middlePath, linear(Offset(20f, 240f), Offset(40f, 272f), 0f to leatherMid, 0.55f to leather, 1f to leatherShadow))
        stroke(middlePath, outline, 1.1f, join = StrokeJoin.Round)
        // PIP knuckle of the middle finger
        fill(middlePath, radial(Offset(22f, 252f), 11f, 0f to leatherHi.copy(alpha = 0.55f), 1f to leatherHi.copy(alpha = 0f)))

        fill(indexPath, linear(Offset(20f, 198f), Offset(42f, 252f), 0f to Color(0xFFA77550), 0.45f to Color(0xFF7E5236), 1f to leatherDeep))
        clip(indexPath) {
            // second knuckle (PIP) at the front corner + lit top of the proximal phalanx
            fill(indexPath, radial(Offset(22f, 206f), 14f, 0f to leatherHi.copy(alpha = 0.9f), 1f to leatherHi.copy(alpha = 0f)))
            fill(indexPath, vertical(195f, 206f, 0f to Color.White.copy(alpha = 0.14f), 1f to Color.White.copy(alpha = 0f)))
            // fold on the palm side where the phalanges meet + leather crease over the joint
            stroke(vpath { moveTo(37.5f, 222.5f); cubicTo(34f, 222.5f, 31f, 223.5f, 29f, 225.5f) }, Color.Black.copy(alpha = 0.5f), 2.4f, cap = StrokeCap.Round)
            stroke(vpath { moveTo(17.5f, 211f); cubicTo(19.5f, 207f, 22.5f, 204.5f, 26.5f, 203.5f) }, leatherShadow.copy(alpha = 0.35f), 0.9f, cap = StrokeCap.Round)
            // shadow cast by the back of the hand onto the finger root
            fill(indexPath, horizontal(34f, 56f, 0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.45f)))
        }
        stroke(indexPath, outline, 1.1f, join = StrokeJoin.Round)

        // ---- back of the hand
        stroke(dorsumPath, Color.Black.copy(alpha = 0.22f), 4f, join = StrokeJoin.Round) // soft contact shadow
        fill(dorsumPath, linear(Offset(48f, 196f), Offset(118f, 292f), 0f to Color(0xFF9A6A47), 0.5f to leather, 1f to Color(0xFF2E1B0F)))
        clip(dorsumPath) {
            // broad leather sheen
            fill(dorsumPath, radial(Offset(70f, 238f), 46f, 0f to Color.White.copy(alpha = 0.13f), 1f to Color.White.copy(alpha = 0f)))
            // occlusion towards the wrist and on the little-finger side
            fill(dorsumPath, vertical(258f, 298f, 0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.42f)))
            fill(dorsumPath, horizontal(100f, 127f, 0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.3f)))
            // glove "points": three stitched seams fanning to the wrist
            listOf(
                vpath { moveTo(62f, 226f); cubicTo(64f, 248f, 68f, 268f, 72f, 292f) },
                vpath { moveTo(83f, 224f); cubicTo(84f, 248f, 84f, 270f, 84f, 294f) },
                vpath { moveTo(103f, 228f); cubicTo(101f, 250f, 99f, 270f, 96f, 292f) },
            ).forEach { seam ->
                stroke(seam, Color.Black.copy(alpha = 0.24f), 2.2f, cap = StrokeCap.Round)
                stroke(seam, leatherHi.copy(alpha = 0.18f), 0.9f, cap = StrokeCap.Round)
                stroke(seam, stitch.copy(alpha = 0.45f), 0.7f, cap = StrokeCap.Round, dash = floatArrayOf(2.4f, 2.2f))
            }

            // ---- padded knuckle guard
            fill(guard, linear(Offset(50f, 190f), Offset(110f, 228f), 0f to Color(0xFF8E6040), 0.55f to Color(0xFF5E3A22), 1f to Color(0xFF2E1B0F)))
            // knuckle heads: soft highlights (light from the upper left) and shadowed valleys between
            KNUCKLES.forEachIndexed { i, k ->
                val r = if (i == 3) 10f else 13f
                fill(dorsumPath, radial(Offset(k.x - 3f, k.y - 3f), r, 0f to leatherHi.copy(alpha = 0.75f), 0.55f to leatherLight.copy(alpha = 0.25f), 1f to leatherLight.copy(alpha = 0f)))
            }
            listOf(Offset(61f, 199f), Offset(82f, 197f), Offset(102f, 201f)).forEach { v ->
                stroke(vpath { moveTo(v.x, v.y - 2f); cubicTo(v.x + 0.5f, v.y + 5f, v.x + 1f, v.y + 10f, v.x + 1.5f, v.y + 15f) }, Color.Black.copy(alpha = 0.32f), 2.4f, cap = StrokeCap.Round)
            }
            // raised lower edge of the guard
            stroke(guardEdge, Color.Black.copy(alpha = 0.4f), 2.4f, cap = StrokeCap.Round)
            stroke(vpath { moveTo(43f, 222.6f); cubicTo(64f, 212.6f, 100f, 212.6f, 125f, 226.6f) }, leatherHi.copy(alpha = 0.3f), 0.9f, cap = StrokeCap.Round)
            stroke(guardStitch, stitch.copy(alpha = 0.55f), 0.8f, cap = StrokeCap.Round, dash = floatArrayOf(2.6f, 2.2f))
            // cool rim light along the outer edge of the hand
            stroke(outerEdge, Color(0xFFBFD3E6).copy(alpha = 0.18f), 3.2f, cap = StrokeCap.Round)
        }
        stroke(dorsumPath, outline, 1.1f, join = StrokeJoin.Round)

        // ---- steel studs on the knuckles
        with(ArmArt) {
            KNUCKLES.forEachIndexed { i, k -> rivet(Offset(k.x - 0.5f, k.y - 1.5f), if (i == 3) 2.5f else 3.1f) }
        }

        // ---- thumb across the fingers
        stroke(thumbPath, Color.Black.copy(alpha = 0.26f), 4.5f, join = StrokeJoin.Round)
        fill(thumbPath, linear(Offset(44f, 245f), Offset(31f, 268f), 0f to Color(0xFFC28F66), 0.35f to leatherMid, 1f to Color(0xFF2E1B0F)))
        clip(thumbPath) {
            // thenar bulge and joint highlights
            fill(thumbPath, radial(Offset(58f, 266f), 16f, 0f to leatherHi.copy(alpha = 0.35f), 1f to leatherHi.copy(alpha = 0f)))
            fill(thumbPath, radial(Offset(38f, 247f), 9f, 0f to leatherHi.copy(alpha = 0.45f), 1f to leatherHi.copy(alpha = 0f)))
            // joint creases
            stroke(vpath { moveTo(40f, 245f); cubicTo(38f, 251f, 37f, 256f, 37.5f, 262f) }, leatherShadow.copy(alpha = 0.45f), 1f, cap = StrokeCap.Round)
            stroke(vpath { moveTo(56f, 255f); cubicTo(53f, 262f, 52f, 268f, 52f, 276f) }, leatherShadow.copy(alpha = 0.3f), 1f, cap = StrokeCap.Round)
            // seam along the thumb
            stroke(vpath { moveTo(40f, 250f); cubicTo(50f, 254f, 60f, 262f, 68f, 276f) }, stitch.copy(alpha = 0.5f), 0.75f, cap = StrokeCap.Round, dash = floatArrayOf(2.6f, 2.2f))
            // occlusion where the thumb disappears into the cuff
            fill(thumbPath, vertical(276f, 293f, 0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.35f)))
        }
        fill(thumbPlate, linear(Offset(19f, 243f), Offset(33f, 256f), 0f to Color(0xFFE7EDF1), 0.45f to Color(0xFF9AA6AF), 1f to Color(0xFF3B444B)))
        stroke(vpath { moveTo(20f, 245.5f); cubicTo(23f, 244f, 28f, 244.5f, 31f, 247f) }, Color.White.copy(alpha = 0.7f), 0.8f, cap = StrokeCap.Round)
        stroke(thumbPlate, Color(0xFF1E2328), 0.8f, join = StrokeJoin.Round)
        stroke(thumbPath, outline, 1.1f, join = StrokeJoin.Round)
    }

    /** Bounds of [art] (fist + cuff) — for fitting the standalone fist graphic. */
    val BOUNDS: Rect get() = art.bounds

    /** Draws the fist incl. cuff (no forearm) in hand units. */
    fun draw(scope: DrawScope) = scope.drawArt(art)

    /** Forearm + fist, as seen in first person. */
    fun drawWithForearm(scope: DrawScope) {
        scope.drawArt(ArmArt.forearm)
        scope.drawArt(art)
    }
}
