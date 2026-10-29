package com.idlerpg.game.ui.adventure

import androidx.compose.ui.graphics.Color
import com.idlerpg.game.domain.world.Biome

/** Silhouette family of the far landscape layers. */
internal enum class Far { DUNES, PEAKS, SPIRES, RUINS, TREES, CRAGS, ISLANDS, GEARS, TENDRILS, COLUMNS, TOWERS, WAVES }

/** Ground surface and its detail strokes. */
internal enum class GroundKind { GRASS, SAND, ROCK, LAVA, ICE, WATER, BONES, FLESH, TILES, VOID }

/** Big celestial object (or rift) in the sky. */
internal enum class Orb { SUN, MOON, RED_SUN, ECLIPSE, RIFT, PLANET, NONE }

/** Ambient particles drifting through the scene. */
internal enum class Particle { NONE, EMBERS, ASH, SNOW, SPORES, SAND, RAIN, MOTES, STARS, PETALS }

/** Body plan of a procedurally painted creature (see [ProceduralMonsters]). */
internal enum class Body { BLOB, HOUND, HUMANOID, INSECT, WINGED, SERPENT, WRAITH, GOLEM }

/**
 * Visual template of a biome: scene colors and shapes plus the body plans and skin palette of its
 * monsters. A zone is "biome template + intensity" (stage 0..4 and overall threat 0..1).
 */
internal class BiomeStyle(
    val skyTop: Color, val skyHorizon: Color,
    val farNear: Color, val farFar: Color,
    val groundTop: Color, val groundBottom: Color,
    val glow: Color, val fog: Color,
    val far: Far, val ground: GroundKind, val orb: Orb, val particle: Particle,
    /** Body plans of the three regular roles and the boss. */
    val bodies: List<Body>,
    val skinDark: Color, val skinMid: Color, val skinLight: Color,
    val accent: Color, val eye: Color,
) {
    fun bodyOf(roleIndex: Int, boss: Boolean): Body = if (boss) bodies[3] else bodies[roleIndex.coerceIn(0, 2)]
}

private fun c(rgb: Long) = Color(0xFF000000 or rgb)

private fun style(
    sky: Pair<Long, Long>, far: Pair<Long, Long>, ground: Pair<Long, Long>, glow: Long, fog: Long,
    shape: Far, kind: GroundKind, orb: Orb, particle: Particle, bodies: List<Body>,
    skin: Triple<Long, Long, Long>, accent: Long, eye: Long,
) = BiomeStyle(
    c(sky.first), c(sky.second), c(far.first), c(far.second), c(ground.first), c(ground.second), c(glow), c(fog),
    shape, kind, orb, particle, bodies, c(skin.first), c(skin.second), c(skin.third), c(accent), c(eye),
)

internal object BiomeStyles {
    private val table: Map<Biome, BiomeStyle> = mapOf(
        Biome.HEIDE to style(0x2B2740 to 0x8A6A7A, 0x3A2F44 to 0x6A5468, 0x3C4A34 to 0x151D14, 0xB36BFF, 0x6A5578,
            Far.CRAGS, GroundKind.GRASS, Orb.MOON, Particle.SPORES, listOf(Body.INSECT, Body.HOUND, Body.GOLEM, Body.HUMANOID),
            Triple(0x2A1F2E, 0x5B3B52, 0x9C6A8A), 0xC77DFF, 0xFFE066),
        Biome.WUESTE to style(0x3A2A1A to 0xF0B060, 0x8A5A30 to 0xC48A4C, 0xD0A060 to 0x7A5430, 0xFFC247, 0xD9A066,
            Far.DUNES, GroundKind.SAND, Orb.SUN, Particle.SAND, listOf(Body.INSECT, Body.HOUND, Body.HUMANOID, Body.SERPENT),
            Triple(0x4A3418, 0x9A7040, 0xE0B878), 0xFFC247, 0xFF5A1F),
        Biome.VULKAN to style(0x1A0A08 to 0xC2410C, 0x2A1410 to 0x5A2418, 0x35221A to 0x0E0705, 0xFF5A14, 0x6A2410,
            Far.PEAKS, GroundKind.LAVA, Orb.RED_SUN, Particle.EMBERS, listOf(Body.BLOB, Body.HOUND, Body.GOLEM, Body.WINGED),
            Triple(0x241210, 0x5A2A1A, 0xB0501E), 0xFF7A18, 0xFFD23F),
        Biome.GRUFT to style(0x0A0F14 to 0x2C4A4A, 0x14201F to 0x2A3C3A, 0x2E3432 to 0x0E1110, 0x5FFFB0, 0x2A4640,
            Far.COLUMNS, GroundKind.TILES, Orb.MOON, Particle.MOTES, listOf(Body.HUMANOID, Body.HOUND, Body.GOLEM, Body.WRAITH),
            Triple(0x3B3A33, 0x8C8A78, 0xD8D4BC), 0x59FFB4, 0x59FFB4),
        Biome.FROST to style(0x0B1A2E to 0xBFE3F5, 0x6C93AD to 0xA8CBE0, 0xDDEFF8 to 0x6C93AD, 0x7FD9FF, 0xBFDDEE,
            Far.PEAKS, GroundKind.ICE, Orb.MOON, Particle.SNOW, listOf(Body.HOUND, Body.WRAITH, Body.GOLEM, Body.WINGED),
            Triple(0x20384F, 0x5F93B8, 0xDDF1FF), 0x8CE6FF, 0xE6FBFF),
        Biome.DSCHUNGEL to style(0x0C2A14 to 0x6BBF59, 0x123A1C to 0x2A6A30, 0x1E5A2A to 0x0A2410, 0xC6FF3B, 0x3F7A3A,
            Far.TREES, GroundKind.GRASS, Orb.SUN, Particle.SPORES, listOf(Body.SERPENT, Body.BLOB, Body.HOUND, Body.INSECT),
            Triple(0x13361C, 0x2F8A3C, 0x9BE15D), 0xC6FF3B, 0xFFEA2E),
        Biome.RUINEN to style(0x1E2630 to 0x7C8CA0, 0x2C3440 to 0x59667A, 0x5A5F58 to 0x23261F, 0xE8C46A, 0x66707E,
            Far.RUINS, GroundKind.TILES, Orb.SUN, Particle.MOTES, listOf(Body.HUMANOID, Body.GOLEM, Body.WINGED, Body.GOLEM),
            Triple(0x2C3036, 0x6B7480, 0xA9B4BE), 0xFFC857, 0xFFC857),
        Biome.GLUTPILZ to style(0x0B0820 to 0x3A1F5C, 0x2A1448 to 0x5A2C80, 0x2A2148 to 0x0C0818, 0x3DFFC4, 0x3A2468,
            Far.TENDRILS, GroundKind.ROCK, Orb.NONE, Particle.SPORES, listOf(Body.BLOB, Body.HOUND, Body.INSECT, Body.GOLEM),
            Triple(0x2A1448, 0x7A3FA6, 0xD48CFF), 0x44FFD1, 0xFFF04A),
        Biome.KUESTE to style(0x0E1C2B to 0x6C8AA3, 0x1C3446 to 0x3C5C72, 0x1F4D63 to 0x0A1C28, 0x7FE6FF, 0x5C7C90,
            Far.WAVES, GroundKind.WATER, Orb.NONE, Particle.RAIN, listOf(Body.INSECT, Body.HUMANOID, Body.HOUND, Body.SERPENT),
            Triple(0x13303F, 0x2F7A8C, 0x8CE0E6), 0x6EF3FF, 0xFFF0A0),
        Biome.KNOCHEN to style(0x2A1E14 to 0xC9B48A, 0x6A5A40 to 0x9A8660, 0xB7A98A to 0x5C513C, 0xE8DDBA, 0xB8A682,
            Far.CRAGS, GroundKind.BONES, Orb.ECLIPSE, Particle.ASH, listOf(Body.HOUND, Body.WINGED, Body.INSECT, Body.WINGED),
            Triple(0x4A4232, 0xA89A78, 0xEFE6C8), 0xFFE9A8, 0xFF4A2A),
        Biome.BLUTGARTEN to style(0x1A0508 to 0x7A1426, 0x3A0C14 to 0x62172A, 0x5A1A24 to 0x1E070B, 0xFF2A4A, 0x6A1A2A,
            Far.TENDRILS, GroundKind.FLESH, Orb.RED_SUN, Particle.PETALS, listOf(Body.SERPENT, Body.BLOB, Body.HUMANOID, Body.HUMANOID),
            Triple(0x3A0A14, 0x9A1C34, 0xE0556A), 0xFF3355, 0xFFE9A0),
        Biome.HIMMEL to style(0x10204A to 0x9FC8FF, 0x5A78B0 to 0x93B0DC, 0xC7D2E8 to 0x5B6C94, 0xFFF27A, 0xB8CCF0,
            Far.ISLANDS, GroundKind.TILES, Orb.SUN, Particle.MOTES, listOf(Body.WRAITH, Body.WINGED, Body.GOLEM, Body.WINGED),
            Triple(0x2A3A66, 0x6C8CD8, 0xE0EBFF), 0xFFEE55, 0xFFFFFF),
        Biome.UNTERWELT to style(0x1C0402 to 0xB3180A, 0x3A0A06 to 0x6A1008, 0x4A1812 to 0x120403, 0xFF3B0A, 0x7A1A0E,
            Far.SPIRES, GroundKind.LAVA, Orb.ECLIPSE, Particle.EMBERS, listOf(Body.WINGED, Body.HOUND, Body.HUMANOID, Body.HUMANOID),
            Triple(0x2A0808, 0x7A1410, 0xD6402A), 0xFF4A1C, 0xFFE14A),
        Biome.SCHATTEN to style(0x050509 to 0x2A1F44, 0x120E20 to 0x241B3A, 0x15121E to 0x050409, 0x9D6BFF, 0x2A2046,
            Far.SPIRES, GroundKind.VOID, Orb.ECLIPSE, Particle.MOTES, listOf(Body.WRAITH, Body.HOUND, Body.HUMANOID, Body.WRAITH),
            Triple(0x0C0A14, 0x2B2347, 0x6A5AA0), 0xB48CFF, 0xE8D2FF),
        Biome.UHRWERK to style(0x1A1410 to 0x8A6A3A, 0x2E2414 to 0x5A4424, 0x6B5328 to 0x2A200E, 0xFFB347, 0x6A5430,
            Far.GEARS, GroundKind.TILES, Orb.NONE, Particle.EMBERS, listOf(Body.INSECT, Body.HOUND, Body.GOLEM, Body.GOLEM),
            Triple(0x2B2118, 0x8A6A32, 0xE8C76A), 0xFFB02E, 0xFF6A2E),
        Biome.TRAUM to style(0x2A0F3A to 0xFF7AC8, 0x4A1A66 to 0x8A3A9A, 0x4A2A66 to 0x140A24, 0xFF6AD5, 0xA05AA8,
            Far.TENDRILS, GroundKind.VOID, Orb.MOON, Particle.MOTES, listOf(Body.BLOB, Body.HUMANOID, Body.INSECT, Body.SERPENT),
            Triple(0x34154A, 0xA03CA8, 0xFFA0E8), 0xFF5AD0, 0xFFFFFF),
        Biome.ASTRAL to style(0x02030F to 0x1B2A66, 0x0E1640 to 0x243478, 0x1A2250 to 0x04061A, 0x6AA8FF, 0x1E2C6A,
            Far.ISLANDS, GroundKind.VOID, Orb.PLANET, Particle.STARS, listOf(Body.WRAITH, Body.HOUND, Body.GOLEM, Body.WINGED),
            Triple(0x0E1640, 0x3D5BD0, 0xA8C4FF), 0x7DF3FF, 0xFFFFFF),
        Biome.LEERE to style(0x000000 to 0x3A0A4A, 0x14001E to 0x2A0A3A, 0x1A0828 to 0x040008, 0xFF2BD6, 0x2A0A3A,
            Far.TENDRILS, GroundKind.VOID, Orb.RIFT, Particle.MOTES, listOf(Body.BLOB, Body.INSECT, Body.WRAITH, Body.SERPENT),
            Triple(0x0A0010, 0x4B1170, 0xB04BFF), 0x2BFFE0, 0x2BFFE0),
        Biome.ENDE to style(0x050003 to 0xFFB23A, 0x2A1608 to 0x6A3A10, 0x3A2A10 to 0x0A0604, 0xFFD23A, 0x7A4A18,
            Far.TOWERS, GroundKind.TILES, Orb.ECLIPSE, Particle.EMBERS, listOf(Body.HUMANOID, Body.GOLEM, Body.WINGED, Body.HUMANOID),
            Triple(0x1A1208, 0x7A5A1E, 0xFFD76A), 0xFFE27A, 0xFFFFFF),
    )

    fun of(biome: Biome): BiomeStyle = table.getValue(biome)
}
