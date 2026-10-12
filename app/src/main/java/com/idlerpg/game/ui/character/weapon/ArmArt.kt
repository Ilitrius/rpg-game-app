package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/**
 * Hero's right forearm (sleeve, leather vambrace with steel plate) and the flared glove cuff, in
 * dagger unit space (grip center (50, 252), forearm towards +y). Shared by the weapon hand
 * ([HandPainter]) and the bare fist ([FistPainter]); pure data, see [VArt].
 */
internal object ArmArt {

    val leatherDark = Color(0xFF2A1A10)
    val seam = Color(0xFF1A0F08)
    val stitch = Color(0xFFC8A27A)

    /** Sleeve, vambrace, straps, steel plate and rivets (everything below the cuff). */
    val forearm: VArt = vart {
        // Sleeve (cloth) far down the arm.
        fill(
            vpath {
                moveTo(40f, 470f)
                cubicTo(30f, 560f, 20f, 660f, 10f, 820f)
                lineTo(230f, 820f)
                cubicTo(205f, 660f, 175f, 560f, 150f, 455f)
                close()
            },
            horizontal(
                20f, 220f,
                0f to Color(0xFF1C0B0E),
                0.3f to Color(0xFF5A2229),
                0.55f to Color(0xFF7A3039),
                0.8f to Color(0xFF3E161B),
                1f to Color(0xFF14070A),
            ),
        )
        listOf(
            vpath { moveTo(70f, 500f); cubicTo(80f, 560f, 70f, 640f, 75f, 800f) },
            vpath { moveTo(120f, 490f); cubicTo(130f, 580f, 140f, 680f, 150f, 800f) },
            vpath { moveTo(165f, 520f); cubicTo(175f, 600f, 185f, 700f, 200f, 800f) },
        ).forEach {
            stroke(it, Color.Black.copy(alpha = 0.35f), 5f, cap = StrokeCap.Round)
            stroke(it, Color(0xFFB0505C).copy(alpha = 0.18f), 1.5f, cap = StrokeCap.Round)
        }

        // Leather vambrace.
        val vambrace = vpath {
            moveTo(54f, 330f)
            cubicTo(48f, 380f, 42f, 430f, 36f, 485f)
            cubicTo(80f, 500f, 120f, 498f, 156f, 470f)
            cubicTo(146f, 420f, 132f, 370f, 120f, 322f)
            cubicTo(100f, 334f, 74f, 336f, 54f, 330f)
            close()
        }
        fill(
            vambrace,
            horizontal(
                36f, 156f,
                0f to Color(0xFF1E120A),
                0.25f to Color(0xFF4E321F),
                0.5f to Color(0xFF6B4630),
                0.8f to Color(0xFF3A2416),
                1f to Color(0xFF160D07),
            ),
        )
        stroke(vambrace, seam, 1.4f, join = StrokeJoin.Round)
        listOf(
            vpath { moveTo(48f, 372f); cubicTo(80f, 384f, 110f, 382f, 134f, 366f) },
            vpath { moveTo(42f, 440f); cubicTo(80f, 452f, 118f, 450f, 148f, 432f) },
        ).forEach {
            stroke(it, Color(0xFF1A0F08), 9f)
            stroke(it, Color(0xFF5A3A24), 7f)
            stroke(it, stitch.copy(alpha = 0.6f), 0.8f, dash = floatArrayOf(3f, 2.5f))
        }

        // Steel plate with ridge and rivets.
        val plate = vpath {
            moveTo(66f, 346f)
            cubicTo(62f, 385f, 58f, 425f, 56f, 468f)
            cubicTo(84f, 478f, 110f, 476f, 134f, 460f)
            cubicTo(128f, 420f, 120f, 382f, 112f, 342f)
            cubicTo(96f, 350f, 80f, 350f, 66f, 346f)
            close()
        }
        fill(
            plate,
            horizontal(
                56f, 134f,
                0f to Color(0xFF4B555D),
                0.22f to Color(0xFFA9B4BC),
                0.38f to Color(0xFFE7EDF1),
                0.55f to Color(0xFF8C98A1),
                0.8f to Color(0xFF59646D),
                1f to Color(0xFF353D44),
            ),
        )
        fill(
            plate,
            linearOf(
                Offset(0f, 340f), Offset(0f, 478f),
                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            ),
        )
        stroke(plate, Color(0xFF1E2328), 1.4f, join = StrokeJoin.Round)
        line(Color.White.copy(alpha = 0.55f), Offset(86f, 352f), Offset(84f, 470f), 1.2f, StrokeCap.Round)
        line(Color.Black.copy(alpha = 0.35f), Offset(90f, 352f), Offset(89f, 470f), 1.2f, StrokeCap.Round)
        listOf(
            Offset(70f, 356f), Offset(108f, 352f), Offset(66f, 400f), Offset(118f, 396f),
            Offset(62f, 450f), Offset(126f, 446f),
        ).forEach { c -> rivet(c, 3.6f) }
    }

    /** Flared glove cuff between hand and vambrace. */
    val cuff: VArt = vart {
        val cuff = vpath {
            moveTo(56f, 282f)
            cubicTo(52f, 300f, 50f, 318f, 50f, 338f)
            cubicTo(76f, 346f, 102f, 344f, 124f, 330f)
            cubicTo(120f, 312f, 114f, 296f, 108f, 278f)
            close()
        }
        fill(
            cuff,
            horizontal(
                50f, 124f,
                0f to Color(0xFF24150C),
                0.3f to Color(0xFF6A4329),
                0.55f to Color(0xFF8A5B3A),
                1f to Color(0xFF2A190E),
            ),
        )
        stroke(cuff, seam, 1.2f, join = StrokeJoin.Round)
        line(stitch.copy(alpha = 0.5f), Offset(54f, 330f), Offset(120f, 324f), 0.9f)
    }

    /** Domed steel rivet: dark rim of radius [r], polished head of radius `r × 0.8`. */
    fun VArtBuilder.rivet(c: Offset, r: Float) {
        circle(Color(0xFF22282D), c, r)
        circle(
            radialOf(c - Offset(1f, 1f) * (r / 3.6f), r * 0.945f, listOf(Color(0xFFF2F6F8), Color(0xFF7E8A93), Color(0xFF2E363C))),
            c, r * 0.806f,
        )
    }
}
