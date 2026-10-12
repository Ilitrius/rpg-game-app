package com.idlerpg.game.ui.character.weapon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.idlerpg.game.domain.character.WeaponAction
import com.idlerpg.game.ui.character.weapon.SvgWriter.Companion.n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Geometry/plausibility checks for the first-person fist, plus an offscreen preview: the very same
 * vector data is exported as SVG to `app/build/weapon-preview/` (contact sheet of the real animation
 * poses + close-up), so the graphic can be checked without an emulator (open in a browser).
 */
class FistArtTest {

    private val grip = DaggerPainter.GRIP_CENTER
    private val view = Size(1080f, 2000f)

    @Test
    fun allPaintsAndPathsAreValid() {
        listOf(ArmArt.forearm, ArmArt.cuff, FistPainter.art).forEach { art ->
            assertTrue(art.ops.isNotEmpty())
            forEachOp(art.ops) { op ->
                val (path, paint) = when (op) {
                    is VOp.Fill -> op.path to op.paint
                    is VOp.Stroke -> {
                        assertTrue("stroke width", op.width > 0f)
                        op.path to op.paint
                    }
                    is VOp.Clip -> op.clip to null
                }
                val first = path.commands.first()
                assertTrue("path starts with moveTo/oval", first is PathCmd.MoveTo || first is PathCmd.Oval)
                val stops = when (paint) {
                    is VPaint.Linear -> {
                        assertTrue("linear gradient needs distinct ends", paint.start != paint.end)
                        paint.stops
                    }
                    is VPaint.Radial -> {
                        assertTrue("radius > 0", paint.radius > 0f)
                        paint.stops
                    }
                    else -> null
                }
                if (stops != null) {
                    assertTrue("at least two stops", stops.size >= 2)
                    stops.zipWithNext().forEach { (a, b) -> assertTrue("ascending stops", b.first > a.first) }
                    assertTrue(stops.all { it.first in 0f..1f })
                }
            }
        }
    }

    @Test
    fun knucklesFormTheFrontOfTheFist() {
        val b = FistPainter.art.bounds
        val k = FistPainter.KNUCKLES
        // Index → little finger runs left → right; the row is the top (= front, towards the monster).
        k.zipWithNext().forEach { (a, c) -> assertTrue("knuckle order", c.x > a.x) }
        assertTrue("knuckle row near the top", k.maxOf { it.y } - b.top < 40f)
        assertTrue("striking point on top", FistPainter.KNUCKLE_FRONT.y - b.top < 8f)
        assertTrue("knuckle front between index and little finger", FistPainter.KNUCKLE_FRONT.x in k.first().x..k.last().x)
        // A clenched fist is clearly wider than the wrist (cuff top 56..108).
        assertTrue("fist wider than the wrist", b.width > 1.6f * (108f - 56f))
        // Glove cuff reaches down to the vambrace (y ≈ 330).
        assertTrue(b.bottom in 330f..350f)
    }

    @Test
    fun fistIsSolidAndAttachedToTheCuff() {
        val art = FistPainter.art
        val inside = listOf(
            FistPainter.KNUCKLES[1] + Offset(0f, 6f), // middle knuckle
            Offset(82f, 250f), // back of the hand
            Offset(82f, 286f), // wrist → cuff, no gap
            Offset(82f, 320f), // cuff
            FistPainter.HAMMER_POINT + Offset(4f, -2f),
            // between folded index finger, thumb and back of the hand (no see-through gap)
            Offset(41f, 230f), Offset(41f, 238f), Offset(40f, 246f),
        )
        inside.forEach { assertTrue("covered: $it", art.coversPoint(it)) }
        val b = art.bounds
        listOf(Offset(b.left - 2f, b.top - 2f), Offset(b.right + 2f, b.center.y), Offset(b.left + 2f, b.bottom - 2f))
            .forEach { assertFalse("outside: $it", art.coversPoint(it)) }
    }

    @Test
    fun thumbLiesAcrossTheFingers() {
        val thumb = FistPainter.thumbPath.controlBounds
        val index = FistPainter.indexPath.controlBounds
        assertTrue("thumb overlaps the curled index finger", thumb.overlaps(index))
        assertTrue("thumb on the thumb side (left)", thumb.center.x < FistPainter.KNUCKLE_FRONT.x)
        assertTrue("thumb below the knuckles", thumb.top > FistPainter.KNUCKLES.minOf { it.y })
        // Drawn after fingers and knuckles → lies over them.
        val ops = FistPainter.art.ops
        val thumbAt = ops.indexOfFirst { it is VOp.Fill && it.path === FistPainter.thumbPath }
        val indexAt = ops.indexOfFirst { it is VOp.Fill && it.path === FistPainter.indexPath }
        assertTrue(indexAt >= 0 && thumbAt > indexAt)
    }

    @Test
    fun punchesLandWithTheKnucklesOnTheMonster() {
        val unit = firstPersonUnit(view)
        val pts = pointsOf(WeaponKind.FIST)
        assertEquals(FistPainter.KNUCKLE_FRONT - grip, pts.tip)
        for (action in listOf(WeaponAction.STAB, WeaponAction.SWING)) {
            val pose = posedAt(WeaponKind.FIST, action, WEAPON_IMPACT_FRACTION, 0f, unit, view)
            val knuckles = pose.toScreen(pts.tip)
            val wrist = pose.toScreen(WRIST - grip)
            // Knuckles hit near the monster center (key targets 0.47..0.53 × width, 0.48 × height).
            assertTrue("$action x", abs(knuckles.x / view.width - 0.5f) < 0.06f)
            assertTrue("$action y", abs(knuckles.y / view.height - 0.48f) < 0.03f)
            // …and lead the fist: the direction wrist → knuckles points up or left (at the monster).
            val toKnuckles = knuckles - wrist
            val dirDeg = Math.toDegrees(atan2(toKnuckles.y, toKnuckles.x).toDouble())
            assertTrue("$action knuckles point at the monster ($dirDeg°)", dirDeg in -200.0..-60.0 || dirDeg > 160.0)
        }
        // At rest the knuckles point forward (up-left towards the monster), the wrist to the corner.
        val rest = posedAt(WeaponKind.FIST, WeaponAction.STAB, 0f, 0f, unit, view)
        val k = rest.toScreen(pts.tip)
        val w = rest.toScreen(WRIST - grip)
        assertTrue(k.y < w.y && k.x < w.x)
    }

    @Test
    fun exportPreview() {
        val dir = File("build/weapon-preview")
        dir.mkdirs()
        val unit = firstPersonUnit(view)
        val frames = listOf(
            "Ruhe" to (WeaponAction.STAB to 0f),
            "Gerade (Treffer)" to (WeaponAction.STAB to WEAPON_IMPACT_FRACTION),
            "Haken (Treffer)" to (WeaponAction.SWING to WEAPON_IMPACT_FRACTION),
            "Hammerfaust (Ausholen)" to (WeaponAction.STRIKE to 0.18f),
            "Hammerfaust (Treffer)" to (WeaponAction.STRIKE to WEAPON_IMPACT_FRACTION),
            "Parade" to (WeaponAction.PARRY to WEAPON_PARRY_CONTACT_FRACTION),
        )
        val cols = 3
        val cw = view.width / 2f
        val ch = view.height / 2f
        val sheet = SvgWriter((cw * cols).toInt(), (ch * ((frames.size + cols - 1) / cols)).toInt())
        sheet.raw(
            "<linearGradient id=\"sky\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"#6f9fd8\"/>" +
                "<stop offset=\"0.55\" stop-color=\"#c9dde8\"/><stop offset=\"0.55\" stop-color=\"#5e8c3a\"/>" +
                "<stop offset=\"1\" stop-color=\"#2e4a1c\"/></linearGradient>",
        )
        frames.forEachIndexed { i, (label, spec) ->
            val (action, p) = spec
            sheet.begin("translate(${n((i % cols) * cw)} ${n((i / cols) * ch)}) scale(0.5)")
            sheet.raw("<svg x=\"0\" y=\"0\" width=\"${n(view.width)}\" height=\"${n(view.height)}\">")
            sheet.raw("<rect width=\"100%\" height=\"100%\" fill=\"url(#sky)\"/>")
            sheet.raw("<circle cx=\"${n(view.width / 2)}\" cy=\"${n(view.height / 2)}\" r=\"170\" fill=\"#50285a\" fill-opacity=\"0.55\"/>")
            val pose = posedAt(WeaponKind.FIST, action, p, 0f, unit, view)
            sheet.begin(
                "translate(${n(pose.pivot.x)} ${n(pose.pivot.y)}) rotate(${n(pose.rotation)}) " +
                    "scale(${n(pose.scale)}) translate(${n(-grip.x)} ${n(-grip.y)})",
            )
            sheet.art(ArmArt.forearm)
            sheet.art(FistPainter.art)
            sheet.end()
            val pts = pointsOf(WeaponKind.FIST)
            val hit = pose.toScreen(if (action == WeaponAction.STRIKE) pts.pommel else pts.tip)
            sheet.raw("<circle cx=\"${n(hit.x)}\" cy=\"${n(hit.y)}\" r=\"8\" fill=\"#ff3030\" fill-opacity=\"0.8\"/>")
            sheet.text(30f, 70f, 44f, "$label  p=${n(p)}")
            sheet.raw("</svg>")
            sheet.end()
        }
        File(dir, "fist_sheet.svg").writeText(sheet.document())

        // Close-up as in the rest pose, and the fitted standalone graphic (character tab, -8°).
        val b = FistPainter.art.bounds
        val s = 6f
        val w = ((b.width + 60f) * s).toInt()
        val h = ((b.height + 60f) * s).toInt()
        val close = SvgWriter(w * 2, h)
        close.raw("<rect width=\"100%\" height=\"100%\" fill=\"#26303a\"/>")
        listOf(-14f to 0, -8f to 1).forEach { (rot, i) ->
            close.begin(
                "translate(${n(w / 2f + i * w)} ${n(h / 2f)}) rotate(${n(rot)}) scale(${n(s)}) " +
                    "translate(${n(-b.center.x)} ${n(-b.center.y)})",
            )
            close.art(FistPainter.art)
            close.end()
        }
        File(dir, "fist_closeup.svg").writeText(close.document())
        assertTrue(File(dir, "fist_sheet.svg").length() > 10_000)
    }

    private fun forEachOp(ops: List<VOp>, block: (VOp) -> Unit) {
        ops.forEach { op ->
            block(op)
            if (op is VOp.Clip) forEachOp(op.ops, block)
        }
    }

    private companion object {
        /** Middle of the wrist (cuff top) in hand units. */
        val WRIST = Offset(82f, 296f)
    }
}
