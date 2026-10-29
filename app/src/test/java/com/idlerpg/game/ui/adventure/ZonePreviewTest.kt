package com.idlerpg.game.ui.adventure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.idlerpg.game.domain.world.MonsterType
import com.idlerpg.game.domain.world.Zone
import com.idlerpg.game.domain.world.Zones
import com.idlerpg.game.ui.adventure.MonsterPainters.drawMonster
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Offscreen previews without device (Robolectric native graphics): for every biome the first and last
 * stage as full scene with a monster ("zone-preview/<id>_scene.png") and a monster sheet with the three
 * regular monsters and the boss ("<id>_monsters.png"). Look at them after changing BiomeScene,
 * ProceduralMonsters or BiomeStyles. Set ZONE_PREVIEW_ALL=1 to render all 95 generated zones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZonePreviewTest {

    private fun render(w: Int, h: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) { block() }
        return bmp
    }

    private fun save(bmp: ImageBitmap, name: String) {
        val dir = File("build/zone-preview"); dir.mkdirs()
        File(dir, name).outputStream().use { bmp.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun DrawScope.monster(zone: Zone, type: MonsterType, ground: Offset, height: Float, time: Float = 3f) =
        drawMonster(type, ground, height, time, 0f, 0f, 1f, zone = zone)

    @Test
    fun writePreviews() {
        val all = System.getenv("ZONE_PREVIEW_ALL") != null
        val zones = Zones.all.filter { it.biome != null && (all || it.stage == 0 || it.stage == 4) }
        zones.forEach { z ->
            val w = 540; val h = 1200
            val scene = BiomeScene(z, Size(w.toFloat(), h.toFloat()))
            val type = z.monsters[1]
            val sceneImg = render(w, h) {
                drawRect(Color.Black)
                scene.drawBackground(this, 0.5f, 10f)
                val base = minOf(w * 0.66f, h * 0.38f) * 1.0f
                monster(z, type, Offset(w / 2f, scene.monsterGroundY), base)
                scene.drawForeground(this, 10f)
            }
            save(sceneImg, "${z.id}_scene.png")
            val types = z.monsters + z.boss
            val sheet = render(1600, 420) {
                drawRect(Color(0xFF30343A))
                types.forEachIndexed { i, t ->
                    val cx = 200f + i * 400f
                    drawRect(Color(0xFF3A3F46), Offset(cx - 200f, 0f), Size(400f, 420f))
                    monster(z, t, Offset(cx, 400f), 380f)
                }
            }
            save(sheet, "${z.id}_monsters.png")
        }
    }
}
